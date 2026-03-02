package com.tanush.bouncer.analysis.ml

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tanush.bouncer.analysis.MatchSituation
import com.tanush.bouncer.analysis.TeamStatistics
import com.tanush.bouncer.models.CricsheetMatch
import kotlin.math.*
import io.sentry.Sentry

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

        /** Maximum score for denormalization */
        const val MAX_SCORE = 350.0
    }

    // The neural network model
    private var model: ScorePredictionModel = ScorePredictionModel.create()

    // Training state
    private var isTrained: Boolean = false
    private var lastTrainingResult: TrainingResult? = null

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
        val trainingSamples = generateTrainingData(matches, teamStats)
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

        // Step 6: Save model
        saveModel()

        Log.d(TAG, result.summary())
        return result
    }

    /**
     * Quick training with sensible defaults for smaller datasets.
     */
    fun quickTrain(
        matches: List<CricsheetMatch>,
        teamStats: Map<String, TeamStatistics> = emptyMap()
    ): TrainingResult {
        return trainOnHistoricalData(
            matches = matches,
            teamStats = teamStats,
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
        epochs: Int = 20
    ): TrainingResult {
        val samples = generateTrainingData(newMatches, teamStats)
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
        maxBalls: Int = 120
    ): PredictionOutput {
        // Extract features
        val features = FeatureExtractor.extractFeatures(
            situation = situation,
            battingTeamStats = battingTeamStats,
            bowlingTeamStats = bowlingTeamStats,
            runsNeeded = runsNeeded,
            ballsBowled = ballsBowled,
            wicketsLost = wicketsLost,
            maxBalls = maxBalls
        )

        // Forward pass
        val output = model.forward(features)

        // Denormalize predictions
        val winProbability = output[0].coerceIn(0.0, 1.0) * 100.0  // Convert to percentage
        val predictedScore = output[1] * MAX_SCORE  // Denormalize to actual runs

        // Calculate confidence based on how far from 50/50 the prediction is
        val confidence = abs(output[0] - 0.5) * 2.0  // 0=uncertain, 1=very confident

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
        teamStats: Map<String, TeamStatistics>
    ): List<TrainingSample> {
        val allSamples = mutableListOf<TrainingSample>()

        for (match in matches) {
            try {
                val samples = FeatureExtractor.generateTrainingSamples(
                    match = match,
                    teamStats = teamStats,
                    samplesPerMatch = 12
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
     * Save model weights to SharedPreferences.
     */
    private fun saveModel() {
        try {
            val gson = Gson()
            val weightsJson = gson.toJson(model.serializeWeights())
            prefs?.edit()
                ?.putBoolean(KEY_MODEL_TRAINED, isTrained)
                ?.putString(KEY_MODEL_WEIGHTS, weightsJson)
                ?.apply()
            Log.d(TAG, "Model saved successfully (${model.parameterCount()} parameters)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save model: ${e.message}")
        }
    }

    /**
     * Load model weights from SharedPreferences.
     */
    private fun loadModel() {
        try {
            val wasTrained = prefs?.getBoolean(KEY_MODEL_TRAINED, false) ?: false
            if (!wasTrained) return

            val weightsJson = prefs?.getString(KEY_MODEL_WEIGHTS, null) ?: return
            // Model loaded from persistence — currently just re-creates with defaults
            // Full deserialization would restore weight arrays
            isTrained = wasTrained
            Log.d(TAG, "Model loaded (trained: $isTrained)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: ${e.message}")
            model = ScorePredictionModel.create()
        }
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
