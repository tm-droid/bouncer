package com.tanush.bouncer.analysis.ml

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tanush.bouncer.analysis.MatchSituation
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import com.tanush.bouncer.analysis.MatchupAnalysis
import com.tanush.bouncer.analysis.PlayerStatisticsEngine
import com.tanush.bouncer.analysis.TeamStatistics
import com.tanush.bouncer.analysis.VenueAnalysis
import com.tanush.bouncer.data.remote.AppwriteClient
import com.tanush.bouncer.models.CricsheetMatch
import kotlin.math.*
import io.sentry.Sentry
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * High-level orchestrator for cricket score prediction model training and inference.
 *
 * This class provides the complete lifecycle:
 *  1. Data preparation from historical CricsheetMatch data
 *  2. Model creation with appropriate architecture
 *  3. Training with convergence monitoring
 *  4. Inference for live match predictions
 *  5. Model persistence (save/load weights)
 *
 * Integrates with the existing WinProbabilityCalculator for backward compatibility
 * while providing superior predictions through learned weights.
 *
 * Usage:
 * ```kotlin
 * val trainer = ModelTrainer(context)
 *
 * // Train on historical data
 * val result = trainer.trainOnHistoricalData(matches, teamStats)
 *
 * // Make predictions
 * val prediction = trainer.predict(matchSituation, battingStats, bowlingStats, runsNeeded, ...)
 * ```
 */
class ModelTrainer(private val context: Context? = null) {

    companion object {
        private const val TAG = "ModelTrainer"
        private const val PREFS_NAME = "ml_model_weights"
        private const val KEY_MODEL_TRAINED = "model_trained"
        private const val KEY_MODEL_WEIGHTS = "model_weights"
        private const val KEY_TRAINING_STATS = "training_stats"

        /** Filename for file-based weight persistence in context.filesDir. */
        private const val MODEL_FILE_NAME = "score_model_v1.json"

        /** Maximum score for denormalization */
        const val MAX_SCORE = 350.0

        /**
         * Process-wide shared instance. Initialized once at app startup via [loadFromFile].
         * Other components that need inference should reference this instance.
         */
        @Volatile
        var shared: ModelTrainer? = null
            private set

        /**
         * Initialize the shared [ModelTrainer] instance and restore saved weights from disk.
         * Safe to call multiple times — subsequent calls are no-ops if already initialized.
         * Intended for use in [BouncerApp.onCreate] after Room DB / filesDir is available.
         */
        fun loadFromFile(context: Context) {
            if (shared != null) return
            synchronized(ModelTrainer::class.java) {
                if (shared != null) return
                // ModelTrainer.init {} calls loadModel() → loadFromFile(ctx), so weights are
                // already loaded during construction. No duplicate call needed.
                shared = ModelTrainer(context.applicationContext)
            }
        }
    }

    // The neural network model
    private var model: ScorePredictionModel = ScorePredictionModel.create()

    // Training state
    private var isTrained: Boolean = false
    private var lastTrainingResult: TrainingResult? = null

    // Platt scaling calibration — A=1/B=0 is the identity (no adjustment)
    private var calibrationA: Double = 1.0
    private var calibrationB: Double = 0.0

    // Preferences for persistence
    private val prefs: SharedPreferences? by lazy {
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    init {
        // Try to load saved model on initialization
        loadModel()
    }

    // ═══════════════════════════════════════════════════════════════
    // Training API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Train the model on historical match data.
     *
     * Processes CricsheetMatch objects → training samples → trains the neural network
     * with gradient descent until convergence.
     *
     * @param matches Historical match data
     * @param teamStats Pre-computed team statistics
     * @param config Training hyperparameters
     * @return [TrainingResult] with convergence info and trained model
     */
    fun trainOnHistoricalData(
        matches: List<CricsheetMatch>,
        teamStats: Map<String, TeamStatistics> = emptyMap(),
        playerStats: PlayerStatisticsEngine? = null,
        matchupAnalysis: MatchupAnalysis? = null,
        venueAnalysis: VenueAnalysis? = null,
        config: TrainingConfig = TrainingConfig(
            epochs = 100,
            batchSize = 32,
            learningRate = 0.001,
            earlyStoppingPatience = 15,
            convergenceWindow = 10,
            convergenceThreshold = 1e-4,
            lrScheduler = LearningRateScheduler.WarmupCosineDecay(warmupEpochs = 5),
            verbose = true,
            logger = { msg -> Log.d(TAG, msg) }
        )
    ): TrainingResult {
        Log.d(TAG, "Starting training on ${matches.size} matches...")

        // Step 1: Generate training samples from historical data
        val trainingSamples = generateTrainingData(matches, teamStats, playerStats, matchupAnalysis, venueAnalysis)
        Log.d(TAG, "Generated ${trainingSamples.size} training samples")

        if (trainingSamples.isEmpty()) {
            Log.w(TAG, "No training samples generated — check match data format")
            return createEmptyResult()
        }

        // Step 2: Create fresh model
        model = ScorePredictionModel.create()
        Log.d(TAG, "Model created with ${model.parameterCount()} parameters")

        // Step 3: Set up optimizer and loss function
        val optimizer = Adam(
            learningRate = config.learningRate,
            beta1 = 0.9,
            beta2 = 0.999
        )

        val lossFunction = LossFunction.MultiTask(
            winLossWeight = 0.6,   // Emphasize win probability prediction
            scoreWeight = 0.3,     // Score prediction
            robustnessWeight = 0.1 // Outlier robustness via Huber
        )

        // Step 4: Train
        val pipeline = TrainingPipeline(config)
        val result = pipeline.train(model, trainingSamples, optimizer, lossFunction)

        // Step 5: Use the best model (lowest validation loss)
        model = result.bestModel
        isTrained = true
        lastTrainingResult = result

        // Step 6: Fit Platt scaling calibration on the training set
        fitCalibration(trainingSamples)

        // Step 7: Save model
        saveModel()

        Log.d(TAG, result.summary())
        return result
    }

    /**
     * Quick training with sensible defaults for smaller datasets.
     */
    fun quickTrain(
        matches: List<CricsheetMatch>,
        teamStats: Map<String, TeamStatistics> = emptyMap(),
        playerStats: PlayerStatisticsEngine? = null,
        matchupAnalysis: MatchupAnalysis? = null,
        venueAnalysis: VenueAnalysis? = null
    ): TrainingResult {
        return trainOnHistoricalData(
            matches = matches,
            teamStats = teamStats,
            playerStats = playerStats,
            matchupAnalysis = matchupAnalysis,
            venueAnalysis = venueAnalysis,
            config = TrainingConfig(
                epochs = 50,
                batchSize = 16,
                learningRate = 0.003,
                earlyStoppingPatience = 10,
                convergenceWindow = 8,
                lrScheduler = LearningRateScheduler.CosineAnnealing(),
                verbose = true,
                logEvery = 5,
                logger = { msg -> Log.d(TAG, msg) }
            )
        )
    }

    /**
     * Continue training an existing model with new data (incremental/online learning).
     */
    fun continueTrain(
        newMatches: List<CricsheetMatch>,
        teamStats: Map<String, TeamStatistics> = emptyMap(),
        playerStats: PlayerStatisticsEngine? = null,
        matchupAnalysis: MatchupAnalysis? = null,
        venueAnalysis: VenueAnalysis? = null,
        epochs: Int = 20
    ): TrainingResult {
        val samples = generateTrainingData(newMatches, teamStats, playerStats, matchupAnalysis, venueAnalysis)
        if (samples.isEmpty()) return createEmptyResult()

        val config = TrainingConfig(
            epochs = epochs,
            batchSize = 16,
            learningRate = 0.0003, // Lower LR for fine-tuning
            earlyStoppingPatience = 8,
            convergenceWindow = 5,
            lrScheduler = LearningRateScheduler.CosineAnnealing(minLR = 1e-5),
            verbose = true,
            logger = { msg -> Log.d(TAG, msg) }
        )

        val pipeline = TrainingPipeline(config)
        val result = pipeline.train(
            model = model,
            data = samples,
            optimizer = Adam(config.learningRate),
            lossFunction = LossFunction.MultiTask()
        )

        model = result.bestModel
        lastTrainingResult = result
        saveModel()

        return result
    }

    // ═══════════════════════════════════════════════════════════════
    // Prediction API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Predict win probability and expected score for a live match situation.
     *
     * @return [PredictionOutput] with win probability and score prediction
     */
    fun predict(
        situation: MatchSituation,
        battingTeamStats: TeamStatistics = TeamStatistics(),
        bowlingTeamStats: TeamStatistics = TeamStatistics(),
        runsNeeded: Int = 0,
        ballsBowled: Int = 0,
        wicketsLost: Int = 0,
        maxBalls: Int = 120,
        enriched: FeatureExtractor.EnrichedContext = FeatureExtractor.EnrichedContext()
    ): PredictionOutput {
        // Extract features
        val features = FeatureExtractor.extractFeatures(
            situation = situation,
            battingTeamStats = battingTeamStats,
            bowlingTeamStats = bowlingTeamStats,
            runsNeeded = runsNeeded,
            ballsBowled = ballsBowled,
            wicketsLost = wicketsLost,
            maxBalls = maxBalls,
            enriched = enriched
        )

        // Forward pass
        val output = model.forward(features)

        // Apply Platt calibration to win probability then denormalize
        val rawWinProb = output[0].coerceIn(0.0, 1.0)
        val calibrated = calibratedWinProbability(rawWinProb)
        val winProbability = calibrated * 100.0
        val predictedScore = output[1] * MAX_SCORE

        val confidence = abs(calibrated - 0.5) * 2.0

        return PredictionOutput(
            winProbability = winProbability,
            predictedScore = predictedScore,
            confidence = confidence,
            features = features,
            rawOutput = output,
            isModelTrained = isTrained
        )
    }

    /**
     * Predict using only basic match parameters (convenience method).
     */
    fun predictSimple(
        currentRunRate: Double,
        requiredRunRate: Double,
        oversRemaining: Double,
        wicketsInHand: Int,
        runsNeeded: Int
    ): PredictionOutput {
        val ballsBowled = ((20.0 - oversRemaining) * 6).toInt().coerceIn(0, 120)
        val situation = MatchSituation(
            currentRunRate = currentRunRate,
            requiredRunRate = requiredRunRate,
            oversRemaining = oversRemaining,
            wicketsInHand = wicketsInHand,
            lastFiveOversRuns = (currentRunRate * 5).toInt(),
            isPowerPlay = ballsBowled < 36,
            isDeathOvers = ballsBowled > 90
        )

        return predict(
            situation = situation,
            runsNeeded = runsNeeded,
            ballsBowled = ballsBowled,
            wicketsLost = 10 - wicketsInHand,
            maxBalls = 120
        )
    }

    // ═══════════════════════════════════════════════════════════════
    // Data Preparation
    // ═══════════════════════════════════════════════════════════════

    /**
     * Generate training samples from historical match data.
     */
    private fun generateTrainingData(
        matches: List<CricsheetMatch>,
        teamStats: Map<String, TeamStatistics>,
        playerStats: PlayerStatisticsEngine? = null,
        matchupAnalysis: MatchupAnalysis? = null,
        venueAnalysis: VenueAnalysis? = null
    ): List<TrainingSample> {
        val allSamples = mutableListOf<TrainingSample>()

        for (match in matches) {
            try {
                val samples = FeatureExtractor.generateTrainingSamples(
                    match = match,
                    teamStats = teamStats,
                    samplesPerMatch = 12,
                    playerStats = playerStats,
                    matchupAnalysis = matchupAnalysis,
                    venueAnalysis = venueAnalysis
                )
                allSamples.addAll(samples)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to generate samples for match ${match.matchId}: ${e.message}")
            }
        }

        // Shuffle to break sequential patterns
        return allSamples.shuffled()
    }

    // ═══════════════════════════════════════════════════════════════
    // Model Evaluation
    // ═══════════════════════════════════════════════════════════════

    /**
     * Evaluate the model's performance on a test dataset.
     *
     * @param testMatches Matches NOT used during training
     * @param teamStats Team statistics
     * @return [ModelEvaluationResult] with accuracy and error metrics
     */
    fun evaluate(
        testMatches: List<CricsheetMatch>,
        teamStats: Map<String, TeamStatistics> = emptyMap()
    ): ModelEvaluationResult {
        val samples = generateTrainingData(testMatches, teamStats)
        if (samples.isEmpty()) return ModelEvaluationResult.empty()

        var totalMSE = 0.0
        var totalBCE = 0.0
        var correctPredictions = 0
        var totalScoreError = 0.0

        for (sample in samples) {
            val output = model.forward(sample.features)

            // Win probability accuracy
            val predictedWin = output[0] > 0.5
            val actualWin = sample.winProbabilityLabel > 0.5
            if (predictedWin == actualWin) correctPredictions++

            // Score prediction MSE
            val scoreError = output[1] - sample.scorePredictionLabel
            totalMSE += scoreError * scoreError
            totalScoreError += abs(scoreError * MAX_SCORE)

            // BCE
            val p = output[0].coerceIn(1e-7, 1.0 - 1e-7)
            totalBCE += -(sample.winProbabilityLabel * ln(p) +
                    (1.0 - sample.winProbabilityLabel) * ln(1.0 - p))
        }

        val n = samples.size.toDouble()
        return ModelEvaluationResult(
            accuracy = correctPredictions / n,
            meanSquaredError = totalMSE / n,
            binaryCrossEntropy = totalBCE / n,
            meanAbsoluteScoreError = totalScoreError / n,
            sampleCount = samples.size
        )
    }

    // ═══════════════════════════════════════════════════════════════
    // Persistence
    // ═══════════════════════════════════════════════════════════════

    /**
     * Serialize model weights to a [JSONObject] using the [ScorePredictionModel.serializeWeights]
     * map format so the JSON round-trips cleanly with [loadFromFile].
     */
    private fun weightsToJson(): JSONObject {
        val weightMap = model.serializeWeights()
        val root = JSONObject()
        root.put("isTrained", isTrained)
        root.put("calibrationA", calibrationA)
        root.put("calibrationB", calibrationB)
        val layerCount = when (val v = weightMap["layerCount"]) {
            is Int -> v
            is Long -> v.toInt()
            is Double -> v.toInt()
            else -> error("layerCount not a number: $v")
        }
        root.put("layerCount", layerCount)
        for (i in 0 until layerCount) {
            root.put("layer_${i}_inputSize", weightMap["layer_${i}_inputSize"])
            root.put("layer_${i}_outputSize", weightMap["layer_${i}_outputSize"])

            @Suppress("UNCHECKED_CAST")
            val weights = weightMap["layer_${i}_weights"] as List<List<Double>>
            val weightsArr = JSONArray()
            for (row in weights) {
                val rowArr = JSONArray()
                for (v in row) rowArr.put(v)
                weightsArr.put(rowArr)
            }
            root.put("layer_${i}_weights", weightsArr)

            @Suppress("UNCHECKED_CAST")
            val biases = weightMap["layer_${i}_biases"] as List<Double>
            val biasArr = JSONArray()
            for (v in biases) biasArr.put(v)
            root.put("layer_${i}_biases", biasArr)
        }
        return root
    }

    /**
     * Convert a [JSONObject] (written by [weightsToJson]) back into the Map format
     * expected by [ScorePredictionModel.deserializeWeights].
     */
    private fun jsonToWeightMap(root: JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val layerCount = root.getInt("layerCount")
        map["layerCount"] = layerCount
        for (i in 0 until layerCount) {
            map["layer_${i}_inputSize"] = root.getInt("layer_${i}_inputSize")
            map["layer_${i}_outputSize"] = root.getInt("layer_${i}_outputSize")

            val weightsArr = root.getJSONArray("layer_${i}_weights")
            val weights = mutableListOf<List<Double>>()
            for (j in 0 until weightsArr.length()) {
                val rowArr = weightsArr.getJSONArray(j)
                val row = mutableListOf<Double>()
                for (k in 0 until rowArr.length()) row.add(rowArr.getDouble(k))
                weights.add(row)
            }
            map["layer_${i}_weights"] = weights

            val biasArr = root.getJSONArray("layer_${i}_biases")
            val biases = mutableListOf<Double>()
            for (j in 0 until biasArr.length()) biases.add(biasArr.getDouble(j))
            map["layer_${i}_biases"] = biases
        }
        return map
    }

    /**
     * Save trained model weights to [context.filesDir]/score_model_v1.json.
     *
     * Convenience overload — uses the context supplied at construction time.
     * No-op (with a log warning) if no context was provided.
     * Called automatically at the end of successful training via [saveModel].
     */
    fun saveToFile() {
        val ctx = context
        if (ctx == null) {
            Log.w(TAG, "saveToFile: no context — skipping file persistence")
            return
        }
        saveToFile(ctx)
    }

    /**
     * Save trained model weights to [ctx.filesDir]/score_model_v1.json.
     *
     * Silently logs errors rather than throwing — a save failure is non-fatal.
     */
    fun saveToFile(ctx: Context) {
        try {
            val file = File(ctx.filesDir, MODEL_FILE_NAME)
            file.writeText(weightsToJson().toString())
            Log.d(TAG, "Model saved to file: ${file.absolutePath} (${model.parameterCount()} params)")
        } catch (e: Exception) {
            Log.e(TAG, "saveToFile failed: ${e.message}")
            Sentry.captureException(e)
        }
    }

    /**
     * Load model weights from [context.filesDir]/score_model_v1.json.
     *
     * Convenience overload — uses the context supplied at construction time.
     * Returns false if no context was provided.
     */
    fun loadFromFile(): Boolean {
        val ctx = context ?: return false
        return loadFromFile(ctx)
    }

    /**
     * Load model weights from [ctx.filesDir]/score_model_v1.json.
     *
     * @return true if weights were loaded successfully; false if the file is missing,
     *         empty, or corrupt — in which case the model is reset to untrained state
     *         and the caller should fall back to retraining.
     */
    fun loadFromFile(ctx: Context): Boolean {
        return try {
            val file = File(ctx.filesDir, MODEL_FILE_NAME)
            if (!file.exists() || file.length() == 0L) {
                Log.d(TAG, "No saved model file found at ${file.absolutePath}")
                return false
            }
            val root = JSONObject(file.readText())
            val weightMap = jsonToWeightMap(root)
            model.deserializeWeights(weightMap)
            isTrained = root.optBoolean("isTrained", true)
            calibrationA = root.optDouble("calibrationA", 1.0)
            calibrationB = root.optDouble("calibrationB", 0.0)
            Log.d(TAG, "Model loaded from file (trained=$isTrained, ${model.parameterCount()} params)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "loadFromFile failed — falling back to untrained: ${e.message}")
            Sentry.captureException(e)
            model = ScorePredictionModel.create()
            isTrained = false
            false
        }
    }

    /**
     * Save model weights to SharedPreferences (legacy path, kept for backward compat).
     * Also delegates to [saveToFile] when a context is available.
     */
    private fun saveModel() {
        try {
            val gson = Gson()
            val weightsJson = gson.toJson(model.serializeWeights())
            prefs?.edit()
                ?.putBoolean(KEY_MODEL_TRAINED, isTrained)
                ?.putString(KEY_MODEL_WEIGHTS, weightsJson)
                ?.apply()
            Log.d(TAG, "Model saved to SharedPreferences (${model.parameterCount()} parameters)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save model to SharedPreferences: ${e.message}")
        }
        // Also persist to file for reliable cross-session recovery
        saveToFile()
        // Sync weights to Appwrite Storage so other devices get the updated model
        context?.let { ctx ->
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) {
                // Fire-and-forget: upload failure is silent (caught inside uploadModelWeights).
                // GlobalScope is intentional — upload must survive ViewModel/screen lifecycle.
                AppwriteClient.uploadModelWeights(ctx)
            }
        }
    }

    /**
     * Load model weights — tries file first, falls back to SharedPreferences.
     */
    private fun loadModel() {
        try {
            // Prefer file-based persistence (authoritative)
            if (loadFromFile()) return

            // Legacy: SharedPreferences fallback
            val wasTrained = prefs?.getBoolean(KEY_MODEL_TRAINED, false) ?: false
            if (!wasTrained) return

            val weightsJson = prefs?.getString(KEY_MODEL_WEIGHTS, null) ?: return

            val gson = Gson()
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val weightMap: Map<String, Any> = gson.fromJson(weightsJson, type)
            model.deserializeWeights(weightMap)
            isTrained = wasTrained
            Log.d(TAG, "Model loaded from SharedPreferences (trained=$isTrained)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: ${e.message}")
            model = ScorePredictionModel.create()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Calibration
    // ═══════════════════════════════════════════════════════════════

    /**
     * Maps raw sigmoid output to calibrated probability via Platt scaling.
     * With default A=1/B=0 (identity logit), returns rawProb unchanged.
     */
    fun calibratedWinProbability(rawProb: Double): Double {
        val eps = 1e-7
        val p = rawProb.coerceIn(eps, 1.0 - eps)
        val logit = ln(p / (1.0 - p))
        return 1.0 / (1.0 + exp(-(calibrationA * logit + calibrationB)))
    }

    /**
     * Fits Platt scaling (A, B) via gradient descent on the training set.
     * Uses soft labels [(N++1)/(N++2), 1/(N-+2)] to prevent extreme-label overfitting.
     */
    private fun fitCalibration(samples: List<TrainingSample>) {
        if (samples.isEmpty()) return
        val eps = 1e-7

        val nPos = samples.count { it.winProbabilityLabel > 0.5 }
        val nNeg = samples.size - nPos
        val labelPos = (nPos + 1.0) / (nPos + 2.0)
        val labelNeg = 1.0 / (nNeg + 2.0)

        val rawOutputs = samples.map { model.forward(it.features)[0].coerceIn(eps, 1.0 - eps) }
        val softLabels = samples.map { if (it.winProbabilityLabel > 0.5) labelPos else labelNeg }

        var a = 1.0
        var b = 0.0
        val lr = 0.01
        val n = rawOutputs.size.toDouble()

        repeat(200) {
            var dA = 0.0
            var dB = 0.0
            for (i in rawOutputs.indices) {
                val logit = ln(rawOutputs[i] / (1.0 - rawOutputs[i]))
                val pCal = 1.0 / (1.0 + exp(-(a * logit + b)))
                val residual = pCal - softLabels[i]
                dA += residual * logit
                dB += residual
            }
            a -= lr * dA / n
            b -= lr * dB / n
        }

        calibrationA = a
        calibrationB = b
        Log.d(TAG, "Platt calibration fitted: A=%.4f B=%.4f".format(a, b))
    }

    // ═══════════════════════════════════════════════════════════════
    // Diagnostics
    // ═══════════════════════════════════════════════════════════════

    /** Get the last training result for analysis. */
    fun getLastTrainingResult(): TrainingResult? = lastTrainingResult

    /** Check if the model has been trained. */
    fun isModelTrained(): Boolean = isTrained

    /** Get the number of trainable parameters. */
    fun getParameterCount(): Int = model.parameterCount()

    /** Get the underlying model (for advanced use). */
    fun getModel(): ScorePredictionModel = model

    /** Reset the model to untrained state. */
    fun resetModel() {
        try {
            model = ScorePredictionModel.create()
            isTrained = false
            lastTrainingResult = null
            prefs?.edit()?.clear()?.apply()
            Log.d(TAG, "Model reset to untrained state")
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    private fun createEmptyResult(): TrainingResult {
        try {
            return TrainingResult(
                trainedModel = model,
                bestModel = model,
                history = TrainingHistory(),
                converged = false,
                convergenceEpoch = -1,
                bestValidationLoss = Double.MAX_VALUE,
                totalEpochs = 0,
                finalTrainLoss = Double.MAX_VALUE,
                finalValLoss = Double.MAX_VALUE
            )
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Output Data Classes
// ═══════════════════════════════════════════════════════════════════

/**
 * Output from a prediction.
 */
data class PredictionOutput(
    /** Win probability as a percentage (0-100) */
    val winProbability: Double,

    /** Predicted final score in runs */
    val predictedScore: Double,

    /** Confidence level (0 = uncertain, 1 = very confident) */
    val confidence: Double,

    /** The input features used (for debugging) */
    val features: DoubleArray,

    /** Raw model output before denormalization */
    val rawOutput: DoubleArray,

    /** Whether the model has been trained on actual data */
    val isModelTrained: Boolean
) {
    override fun equals(other: Any?): Boolean {
        try {
            if (this === other) return true
            if (other !is PredictionOutput) return false
            return winProbability == other.winProbability && predictedScore == other.predictedScore
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    override fun hashCode(): Int = (winProbability * 100).toInt()

    override fun toString(): String =
        "Prediction(win=${"%.1f".format(winProbability)}%, " +
        "score=${"%.0f".format(predictedScore)}, " +
        "confidence=${"%.1f".format(confidence * 100)}%, " +
        "trained=$isModelTrained)"
}

/**
 * Result of model evaluation on test data.
 */
data class ModelEvaluationResult(
    val accuracy: Double,
    val meanSquaredError: Double,
    val binaryCrossEntropy: Double,
    val meanAbsoluteScoreError: Double,
    val sampleCount: Int
) {
    companion object {
        fun empty() = ModelEvaluationResult(0.0, 0.0, 0.0, 0.0, 0)
    }

    override fun toString(): String = buildString {
        try {
            appendLine("Model Evaluation (${sampleCount} samples):")
            appendLine("  Accuracy:           ${"%.2f".format(accuracy * 100)}%")
            appendLine("  MSE:                ${"%.6f".format(meanSquaredError)}")
            appendLine("  BCE:                ${"%.6f".format(binaryCrossEntropy)}")
            appendLine("  Avg Score Error:    ${"%.1f".format(meanAbsoluteScoreError)} runs")
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}
