package com.tanush.bouncer.analysis.ml

import com.tanush.bouncer.analysis.MatchSituation
import com.tanush.bouncer.analysis.TeamStatistics
import com.tanush.bouncer.models.*
import org.junit.Test
import java.util.Date
import kotlin.math.*
import kotlin.random.Random

/**
 * Full training runner for the Bouncer score prediction neural network.
 *
 * Anti-Overfitting Improvements (v2):
 *  - 12 match archetypes (up from 6) for better coverage
 *  - Soft labels that vary with match progress (not binary 0/1)
 *  - Randomized sample positions with strategic anchors
 *  - Per-match probability perturbation (Gaussian noise on probabilities)
 *  - Feature noise injection (data augmentation)
 *  - Label smoothing (factor=0.05)
 *  - Sample deduplication (L2 distance < 0.01)
 *  - Overfit diagnostic with train/val/test split
 *  - Larger dataset (500 matches, 20 teams, 25 venues)
 *  - Projected final score for score labels
 *  - Tighter training config (fewer epochs, larger batches, stronger regularization)
 *
 * Run with:
 *   ./gradlew testDebugUnitTest --tests "com.tanush.bouncer.analysis.ml.MLTrainingRunner"
 */
class MLTrainingRunner {

    // ═══════════════════════════════════════════════════════════════
    //  CONSTANTS
    // ═══════════════════════════════════════════════════════════════

    companion object {
        private const val LABEL_SMOOTHING_FACTOR = 0.05
        private const val FEATURE_NOISE_STD = 0.02
        private const val PROB_PERTURBATION_STD = 0.04
        private const val DEDUP_THRESHOLD = 0.01
        private const val MAX_TARGET = 350.0
    }

    // ═══════════════════════════════════════════════════════════════
    //  MAIN TRAINING RUN
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `train score prediction model on synthetic cricket data`() {
        val seed = 42
        val rng = Random(seed)

        println()
        println("╔══════════════════════════════════════════════════════════════╗")
        println("║          BOUNCER ML — FULL TRAINING RUN (v2)               ║")
        println("║          Neural Network Score & Win Predictor               ║")
        println("║          Anti-Overfitting Edition                           ║")
        println("╚══════════════════════════════════════════════════════════════╝")
        println()

        // ——— Step 1: Generate synthetic match data ———
        println("━━━ STEP 1: Generating synthetic cricket match data ━━━")
        val allMatches = generateSyntheticDataset(matchCount = 500, rng = rng)
        println("  Generated ${allMatches.size} matches (12 archetypes)")

        // Split into train / test
        val shuffled = allMatches.shuffled(rng)
        val splitIdx = (shuffled.size * 0.85).toInt()
        val trainMatches = shuffled.subList(0, splitIdx)
        val testMatches = shuffled.subList(splitIdx, shuffled.size)
        println("  Train: ${trainMatches.size} matches | Test: ${testMatches.size} matches")

        // Build team statistics from training set
        val teamStats = buildTeamStats(trainMatches)
        println("  Team stats computed for ${teamStats.size} teams")

        // ——— Step 2: Extract training samples with anti-overfit improvements ———
        println()
        println("━━━ STEP 2: Extracting training samples (anti-overfit pipeline) ━━━")
        var trainSamples = mutableListOf<TrainingSample>()
        for ((idx, match) in trainMatches.withIndex()) {
            val matchRng = Random(seed + idx)
            trainSamples.addAll(
                generateAntiOverfitSamples(match, teamStats, matchRng)
            )
        }
        var testSamples = mutableListOf<TrainingSample>()
        for ((idx, match) in testMatches.withIndex()) {
            val matchRng = Random(seed + trainMatches.size + idx)
            testSamples.addAll(
                generateAntiOverfitSamples(match, teamStats, matchRng)
            )
        }

        println("  Train samples (before dedup): ${trainSamples.size}")
        println("  Test samples (before dedup):  ${testSamples.size}")

        // Deduplicate samples
        trainSamples = deduplicateSamples(trainSamples).toMutableList()
        testSamples = deduplicateSamples(testSamples).toMutableList()

        println("  Train samples (after dedup):  ${trainSamples.size}")
        println("  Test samples (after dedup):   ${testSamples.size}")

        // Print feature distribution
        printFeatureStats(trainSamples)

        // ——— Step 3: Create model ———
        println()
        println("━━━ STEP 3: Creating neural network ━━━")
        val model = ScorePredictionModel.create(seed = seed.toLong())
        println("  Architecture: 16 → 32 (ReLU) → 16 (ReLU) → 2 (Sigmoid)")
        println("  Parameters:   ${model.parameterCount()}")

        // ——— Step 4: Configure training (anti-overfit tuned) ———
        println()
        println("━━━ STEP 4: Training configuration (anti-overfit tuned) ━━━")
        val config = TrainingConfig(
            epochs = 150,
            batchSize = 64,
            learningRate = 0.0015,
            validationSplit = 0.15,
            l2Lambda = 0.0005,
            gradientClipNorm = 3.0,
            earlyStoppingPatience = 20,
            convergenceWindow = 15,
            convergenceThreshold = 5e-5,
            stopOnConvergence = true,
            lrScheduler = LearningRateScheduler.WarmupCosineDecay(
                warmupEpochs = 10,
                minLR = 5e-6
            ),
            verbose = true,
            logEvery = 1,
            logger = { msg -> println("  $msg") }
        )
        println("  Epochs:          ${config.epochs}")
        println("  Batch size:      ${config.batchSize}")
        println("  Learning rate:   ${config.learningRate}")
        println("  LR scheduler:    WarmupCosineDecay (warmup=10, minLR=5e-6)")
        println("  Loss function:   MultiTask (BCE 0.6 + MSE 0.3 + Huber 0.1)")
        println("  Optimizer:       Adam (β1=0.9, β2=0.999)")
        println("  Regularization:  L2 λ=${config.l2Lambda}")
        println("  Gradient clip:   norm ≤ ${config.gradientClipNorm}")
        println("  Early stopping:  patience=${config.earlyStoppingPatience}")
        println("  Convergence:     window=${config.convergenceWindow}, threshold=${config.convergenceThreshold}")
        println("  Label smoothing: factor=$LABEL_SMOOTHING_FACTOR")
        println("  Feature noise:   σ=$FEATURE_NOISE_STD")
        println("  Prob perturb:    σ=$PROB_PERTURBATION_STD")

        // ——— Step 5: TRAIN ———
        println()
        println("━━━ STEP 5: Training (gradient descent with backpropagation) ━━━")
        println("  ┌────────┬──────────────┬──────────────┬──────────┬──────────┬──────────┬────────────┐")
        println("  │ Epoch  │  Train Loss  │   Val Loss   │ Train Acc│ Val Acc  │    LR    │ Grad Norm  │")
        println("  ├────────┼──────────────┼──────────────┼──────────┼──────────┼──────────┼────────────┤")

        val optimizer = Adam(learningRate = config.learningRate)
        val lossFunction = LossFunction.MultiTask(
            winLossWeight = 0.6,
            scoreWeight = 0.3,
            robustnessWeight = 0.1
        )

        val pipeline = TrainingPipeline(config)
        val result = pipeline.train(model, trainSamples, optimizer, lossFunction)

        println("  └────────┴──────────────┴──────────────┴──────────┴──────────┴──────────┴────────────┘")
        println()

        // ——— Step 6: Training summary ———
        println("━━━ STEP 6: Training results ━━━")
        println(result.summary())

        // Print loss curve (ASCII visualization)
        printLossCurve(result.history)

        // ——— Step 7: Evaluate on test set ———
        println()
        println("━━━ STEP 7: Evaluating on held-out test set ━━━")
        val testEval = pipeline.evaluate(result.bestModel, testSamples, lossFunction)
        println("  Test Loss:     ${"%.6f".format(testEval.avgLoss)}")
        println("  Test Accuracy: ${"%.2f".format(testEval.accuracy * 100)}%")

        // Detailed evaluation
        evaluateDetailed(result.bestModel, testSamples)

        // ——— Step 8: Sample predictions ———
        println()
        println("━━━ STEP 8: Sample match predictions ━━━")
        runSamplePredictions(result.bestModel, teamStats)

        // ——— Step 9: Convergence analysis ———
        println()
        println("━━━ STEP 9: Convergence analysis ━━━")
        analyzeConvergence(result)

        // ——— Step 10: Overfit diagnostic ———
        println()
        println("━━━ STEP 10: Overfit diagnostic ━━━")
        checkOverfitting(trainSamples, lossFunction, seed)

        println()
        println("╔══════════════════════════════════════════════════════════════╗")
        println("║                   TRAINING COMPLETE (v2)                    ║")
        println("╚══════════════════════════════════════════════════════════════╝")
        println()
    }

    // ═══════════════════════════════════════════════════════════════
    //  ADDITIONAL TEST SCENARIOS
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun testMiddleOversStallPrediction() {
        val seed = 123
        val rng = Random(seed)

        println()
        println("━━━ Test: Middle Overs Stall Prediction ━━━")
        println("  Scenario: Team scoring well in powerplay then stalls in middle overs")

        // Generate dataset
        val allMatches = generateSyntheticDataset(matchCount = 200, rng = rng)
        val teamStats = buildTeamStats(allMatches)

        val model = ScorePredictionModel.create(seed = seed.toLong())

        val config = TrainingConfig(
            epochs = 80,
            batchSize = 64,
            learningRate = 0.0015,
            validationSplit = 0.15,
            l2Lambda = 0.0005,
            gradientClipNorm = 3.0,
            earlyStoppingPatience = 15,
            convergenceWindow = 10,
            convergenceThreshold = 5e-5,
            stopOnConvergence = true,
            lrScheduler = LearningRateScheduler.WarmupCosineDecay(warmupEpochs = 5, minLR = 5e-6),
            verbose = false
        )

        var samples = mutableListOf<TrainingSample>()
        for ((idx, match) in allMatches.withIndex()) {
            samples.addAll(generateAntiOverfitSamples(match, teamStats, Random(seed + idx)))
        }
        samples = deduplicateSamples(samples).toMutableList()

        val pipeline = TrainingPipeline(config)
        val optimizer = Adam(learningRate = config.learningRate)
        val lossFunction = LossFunction.MultiTask(winLossWeight = 0.6, scoreWeight = 0.3, robustnessWeight = 0.1)
        val result = pipeline.train(model, samples, optimizer, lossFunction)

        // Test middle-overs stall scenario:
        // Team was 55/1 after 6 overs (9.17 RR), now 85/3 after 12 overs (7.08 RR), chasing 175
        val stallSituation = MatchSituation(
            currentRunRate = 7.08,
            requiredRunRate = 11.25,
            oversRemaining = 8.0,
            wicketsInHand = 7,
            lastFiveOversRuns = 30,
            isPowerPlay = false,
            isDeathOvers = false
        )
        val battingStats = teamStats["India"] ?: TeamStatistics()
        val bowlingStats = teamStats["Australia"] ?: TeamStatistics()

        val features = FeatureExtractor.extractFeatures(
            situation = stallSituation,
            battingTeamStats = battingStats,
            bowlingTeamStats = bowlingStats,
            runsNeeded = 90,
            ballsBowled = 72,
            wicketsLost = 3,
            maxBalls = 120
        )
        val output = result.bestModel.forward(features)

        println("  India chasing 175, at 85/3 after 12 overs (stalled from 9.17 to 7.08 RR)")
        println("  Win Probability: ${"%.1f".format(output[0] * 100)}%")
        println("  Predicted Score: ${"%.0f".format(output[1] * 350)} runs")
        println("  Training epochs: ${result.totalEpochs}, Final val loss: ${"%.6f".format(result.finalValLoss)}")
        println("  ✓ Middle overs stall prediction test complete")
        println()
    }

    @Test
    fun testSpinSqueezePrediction() {
        val seed = 456
        val rng = Random(seed)

        println()
        println("━━━ Test: Spin Squeeze Prediction ━━━")
        println("  Scenario: Spin bowling restricting scoring on a turning pitch")

        val allMatches = generateSyntheticDataset(matchCount = 200, rng = rng)
        val teamStats = buildTeamStats(allMatches)

        val model = ScorePredictionModel.create(seed = seed.toLong())

        val config = TrainingConfig(
            epochs = 80,
            batchSize = 64,
            learningRate = 0.0015,
            validationSplit = 0.15,
            l2Lambda = 0.0005,
            gradientClipNorm = 3.0,
            earlyStoppingPatience = 15,
            convergenceWindow = 10,
            convergenceThreshold = 5e-5,
            stopOnConvergence = true,
            lrScheduler = LearningRateScheduler.WarmupCosineDecay(warmupEpochs = 5, minLR = 5e-6),
            verbose = false
        )

        var samples = mutableListOf<TrainingSample>()
        for ((idx, match) in allMatches.withIndex()) {
            samples.addAll(generateAntiOverfitSamples(match, teamStats, Random(seed + idx)))
        }
        samples = deduplicateSamples(samples).toMutableList()

        val pipeline = TrainingPipeline(config)
        val optimizer = Adam(learningRate = config.learningRate)
        val lossFunction = LossFunction.MultiTask(winLossWeight = 0.6, scoreWeight = 0.3, robustnessWeight = 0.1)
        val result = pipeline.train(model, samples, optimizer, lossFunction)

        // Test spin squeeze scenario:
        // Low scoring, spin-heavy pitch. Chasing 155, at 62/4 after 10 overs
        // Last 5 overs only 22 runs (spin choking)
        val spinSqueeze = MatchSituation(
            currentRunRate = 6.2,
            requiredRunRate = 9.3,
            oversRemaining = 10.0,
            wicketsInHand = 6,
            lastFiveOversRuns = 22,
            isPowerPlay = false,
            isDeathOvers = false
        )
        val battingStats = teamStats["Sri Lanka"] ?: TeamStatistics()
        val bowlingStats = teamStats["India"] ?: TeamStatistics()

        val features = FeatureExtractor.extractFeatures(
            situation = spinSqueeze,
            battingTeamStats = battingStats,
            bowlingTeamStats = bowlingStats,
            runsNeeded = 93,
            ballsBowled = 60,
            wicketsLost = 4,
            maxBalls = 120
        )
        val output = result.bestModel.forward(features)

        println("  Sri Lanka chasing 155, at 62/4 after 10 overs (spin squeeze: 22 runs in last 5)")
        println("  Win Probability: ${"%.1f".format(output[0] * 100)}%")
        println("  Predicted Score: ${"%.0f".format(output[1] * 350)} runs")
        println("  Training epochs: ${result.totalEpochs}, Final val loss: ${"%.6f".format(result.finalValLoss)}")
        println("  ✓ Spin squeeze prediction test complete")
        println()
    }

    // ═══════════════════════════════════════════════════════════════
    //  ANTI-OVERFIT SAMPLE GENERATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Generate training samples with anti-overfitting improvements:
     * - Randomized sample positions (with strategic anchors)
     * - Soft labels varying with match progress
     * - Feature noise injection
     * - Label smoothing
     * - Projected final score for score labels
     */
    private fun generateAntiOverfitSamples(
        match: CricsheetMatch,
        teamStats: Map<String, TeamStatistics>,
        rng: Random
    ): List<TrainingSample> {
        val samples = mutableListOf<TrainingSample>()

        if (match.innings.size < 2) return samples

        val firstInnings = match.innings[0]
        val secondInnings = match.innings[1]
        val target = firstInnings.total + 1

        val battingTeam = secondInnings.team
        val bowlingTeam = firstInnings.team

        val battingStats = teamStats[battingTeam] ?: TeamStatistics()
        val bowlingStats = teamStats[bowlingTeam] ?: TeamStatistics()

        val battingTeamWon = match.outcome?.winner == battingTeam

        val totalBalls = secondInnings.overs.sumOf { it.balls.size }
        val maxBalls = if (totalBalls > 120) 300 else 120

        if (totalBalls <= 1) return samples

        // Strategic anchor positions
        val positions = mutableListOf<Int>()
        val ppEnd = minOf(36, totalBalls - 1)            // Powerplay end (~ball 36)
        val middleTransition = minOf(60, totalBalls - 1) // Middle overs transition
        val deathStart = minOf(maxBalls - 30, totalBalls - 1) // Death overs start

        if (ppEnd > 0) positions.add(ppEnd)
        if (middleTransition > 0 && middleTransition != ppEnd) positions.add(middleTransition)
        if (deathStart > 0 && deathStart != middleTransition) positions.add(deathStart)

        // Add 12-18 random positions
        val numRandomPositions = rng.nextInt(12, 19)
        for (i in 0 until numRandomPositions) {
            val pos = rng.nextInt(1, maxOf(2, totalBalls))
            positions.add(pos)
        }

        // Deduplicate and sort positions
        val samplePositions = positions.distinct().sorted()

        for (atBall in samplePositions) {
            if (atBall <= 0 || atBall >= totalBalls) continue

            // Extract features using FeatureExtractor
            val features = FeatureExtractor.extractTrainingFeatures(
                innings = secondInnings,
                atBall = atBall,
                target = target,
                battingTeamStats = battingStats,
                bowlingTeamStats = bowlingStats,
                maxBalls = maxBalls
            )

            // Add feature noise injection (data augmentation)
            val noisyFeatures = injectFeatureNoise(features, rng)

            // Soft label based on match progress
            val progress = atBall.toDouble() / maxBalls.toDouble()
            val softWinLabel = computeSoftLabel(battingTeamWon, progress, rng)

            // Apply label smoothing
            val smoothedLabel = applyLabelSmoothing(softWinLabel)

            // Projected final score for score label
            var runsAtPoint = 0
            var ballCount = 0
            for (over in secondInnings.overs) {
                for (ball in over.balls) {
                    if (ballCount >= atBall) break
                    runsAtPoint += ball.runs + ball.extras
                    ballCount++
                }
                if (ballCount >= atBall) break
            }

            val projectedFinalScore = if (atBall > 0) {
                runsAtPoint.toDouble() * maxBalls.toDouble() / atBall.toDouble()
            } else {
                runsAtPoint.toDouble()
            }
            val scorePredictionLabel = (projectedFinalScore / MAX_TARGET).coerceIn(0.0, 1.0)

            samples.add(
                TrainingSample(
                    features = noisyFeatures,
                    winProbabilityLabel = smoothedLabel,
                    scorePredictionLabel = scorePredictionLabel,
                    matchId = match.matchId,
                    ballNumber = atBall
                )
            )
        }

        return samples
    }

    /**
     * Compute soft labels based on match progress.
     * Early overs: closer to 0.5 (uncertain)
     * Death overs: closer to true outcome (0 or 1)
     */
    private fun computeSoftLabel(battingTeamWon: Boolean, progress: Double, rng: Random): Double {
        // progress: 0.0 (start) → 1.0 (end)
        // Quadratic ramp — starts slow, accelerates toward certainty
        val certainty = (progress * progress).coerceIn(0.0, 1.0)

        return if (battingTeamWon) {
            // Winner label: starts at 0.55-0.65, ramps to 0.85-0.95
            val earlyLabel = 0.55 + rng.nextDouble() * 0.10
            val lateLabel = 0.85 + rng.nextDouble() * 0.10
            earlyLabel + (lateLabel - earlyLabel) * certainty
        } else {
            // Loser label: starts at 0.35-0.45, ramps down to 0.05-0.15
            val earlyLabel = 0.35 + rng.nextDouble() * 0.10
            val lateLabel = 0.05 + rng.nextDouble() * 0.10
            earlyLabel + (lateLabel - earlyLabel) * certainty
        }
    }

    /** Apply label smoothing: label = label * (1 - factor) + 0.5 * factor */
    private fun applyLabelSmoothing(label: Double): Double {
        return label * (1.0 - LABEL_SMOOTHING_FACTOR) + 0.5 * LABEL_SMOOTHING_FACTOR
    }

    /** Inject Gaussian noise into feature values for data augmentation */
    private fun injectFeatureNoise(features: DoubleArray, rng: Random): DoubleArray {
        return DoubleArray(features.size) { i ->
            val noise = rng.nextGaussian() * FEATURE_NOISE_STD
            (features[i] + noise).coerceIn(0.0, 1.0)
        }
    }

    /**
     * Remove near-duplicate samples based on L2 distance between feature vectors.
     * If L2 distance < threshold, keep only one sample.
     */
    private fun deduplicateSamples(samples: List<TrainingSample>): List<TrainingSample> {
        if (samples.size <= 1) return samples

        val kept = mutableListOf<TrainingSample>()
        val removed = mutableSetOf<Int>()

        for (i in samples.indices) {
            if (i in removed) continue
            kept.add(samples[i])

            // Check next few samples (duplicates tend to be nearby from same match)
            val checkLimit = minOf(i + 5, samples.size)
            for (j in i + 1 until checkLimit) {
                if (j in removed) continue
                val dist = l2Distance(samples[i].features, samples[j].features)
                if (dist < DEDUP_THRESHOLD) {
                    removed.add(j)
                }
            }
        }

        return kept
    }

    /** Compute L2 (Euclidean) distance between two feature vectors */
    private fun l2Distance(a: DoubleArray, b: DoubleArray): Double {
        var sumSq = 0.0
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sumSq += diff * diff
        }
        return sqrt(sumSq)
    }

    // ═══════════════════════════════════════════════════════════════
    //  OVERFIT DIAGNOSTIC
    // ═══════════════════════════════════════════════════════════════

    /**
     * Overfit diagnostic: splits data into train (70%), val (15%), test (15%),
     * trains a fresh model, compares losses, and flags overfitting severity.
     */
    private fun checkOverfitting(
        allSamples: List<TrainingSample>,
        lossFunction: LossFunction,
        seed: Int
    ) {
        val rng = Random(seed + 999)
        val shuffled = allSamples.shuffled(rng)

        // 70/15/15 split
        val trainEnd = (shuffled.size * 0.70).toInt()
        val valEnd = (shuffled.size * 0.85).toInt()

        val trainData = shuffled.subList(0, trainEnd)
        val valData = shuffled.subList(trainEnd, valEnd)
        val testData = shuffled.subList(valEnd, shuffled.size)

        println("  Overfit diagnostic split: train=${trainData.size}, val=${valData.size}, test=${testData.size}")

        // Train a fresh model on train split only
        val diagModel = ScorePredictionModel.create(seed = (seed + 777).toLong())
        val diagConfig = TrainingConfig(
            epochs = 80,
            batchSize = 64,
            learningRate = 0.0015,
            validationSplit = 0.0,
            l2Lambda = 0.0005,
            gradientClipNorm = 3.0,
            earlyStoppingPatience = 0,
            convergenceWindow = 15,
            convergenceThreshold = 5e-5,
            stopOnConvergence = false,
            lrScheduler = LearningRateScheduler.WarmupCosineDecay(warmupEpochs = 10, minLR = 5e-6),
            verbose = false
        )

        val diagPipeline = TrainingPipeline(diagConfig)
        val diagOptimizer = Adam(learningRate = diagConfig.learningRate)
        val diagResult = diagPipeline.train(diagModel, trainData, diagOptimizer, lossFunction)

        // Evaluate on all three splits
        val trainEval = diagPipeline.evaluate(diagResult.bestModel, trainData, lossFunction)
        val valEval = diagPipeline.evaluate(diagResult.bestModel, valData, lossFunction)
        val testEval = diagPipeline.evaluate(diagResult.bestModel, testData, lossFunction)

        val trainLoss = trainEval.avgLoss
        val valLoss = valEval.avgLoss
        val testLoss = testEval.avgLoss

        println("  ┌──────────────── Overfit Diagnostic ────────────────┐")
        println("  │  Train Loss:      ${"%.6f".format(trainLoss)}")
        println("  │  Validation Loss:  ${"%.6f".format(valLoss)}")
        println("  │  Test Loss:        ${"%.6f".format(testLoss)}")
        println("  │  Train Accuracy:   ${"%.2f".format(trainEval.accuracy * 100)}%")
        println("  │  Val Accuracy:     ${"%.2f".format(valEval.accuracy * 100)}%")
        println("  │  Test Accuracy:    ${"%.2f".format(testEval.accuracy * 100)}%")
        println("  │")
        println("  │  Train/Val Loss Ratio: ${"%.4f".format(trainLoss / maxOf(valLoss, 1e-10))}")

        // Determine severity
        val severity = when {
            trainLoss < valLoss * 0.7 -> "SEVERE"
            trainLoss < valLoss * 0.8 -> "MODERATE"
            trainLoss < valLoss * 0.9 -> "MILD"
            else -> "NONE"
        }

        val indicator = when (severity) {
            "SEVERE" -> "⚠️  SEVERE OVERFITTING DETECTED"
            "MODERATE" -> "⚠  MODERATE overfitting detected"
            "MILD" -> "ℹ  Mild overfitting (acceptable)"
            else -> "✓  No significant overfitting"
        }

        println("  │  Overfitting:      $severity")
        println("  │  $indicator")
        println("  └────────────────────────────────────────────────────┘")
    }

    // ═══════════════════════════════════════════════════════════════
    //  SYNTHETIC DATA GENERATION (12 ARCHETYPES)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Generate realistic synthetic cricket matches with ball-by-ball data.
     * 12 match archetypes for diverse training coverage:
     *  - highScoring, lowScoring, dominantChase, narrowChase
     *  - collapse, standard, middleOrderRecovery, openingStandDominance
     *  - rainShortened, spinDominated, paceBowlingCarnage, counterAttackChase
     */
    private fun generateSyntheticDataset(matchCount: Int, rng: Random): List<CricsheetMatch> {
        val teams = listOf(
            "India", "Australia", "England", "South Africa",
            "New Zealand", "Pakistan", "Sri Lanka", "West Indies",
            "Bangladesh", "Afghanistan", "Ireland", "Zimbabwe",
            "Netherlands", "Nepal", "Scotland", "Namibia",
            "USA", "UAE", "Oman", "Hong Kong"
        )
        val venues = listOf(
            "Wankhede Stadium", "MCG", "Lord's", "Wanderers",
            "Eden Park", "Gaddafi Stadium", "R. Premadasa",
            "Kensington Oval", "Sher-e-Bangla", "Sharjah",
            "Galle International Stadium", "Pallekele International",
            "SuperSport Park, Centurion", "Mangaung Oval, Bloemfontein",
            "Seddon Park, Hamilton", "McLean Park, Napier",
            "Sheikh Zayed Stadium, Abu Dhabi", "Bellerive Oval, Hobart",
            "Rawalpindi Cricket Stadium", "Multan Cricket Stadium",
            "Mirpur Cricket Stadium, Dhaka", "Harare Sports Club",
            "Mahinda Rajapaksa Stadium, Hambantota", "Sophia Gardens, Cardiff",
            "Narendra Modi Stadium, Ahmedabad"
        )

        return (0 until matchCount).map { idx ->
            val team1 = teams[rng.nextInt(teams.size)]
            var team2 = teams[rng.nextInt(teams.size)]
            while (team2 == team1) team2 = teams[rng.nextInt(teams.size)]

            val venue = venues[rng.nextInt(venues.size)]
            val matchType = rng.nextDouble()

            // 12 match archetypes (roughly uniformly distributed)
            val (firstInningsTotal, secondInningsResult) = when {
                matchType < 1.0 / 12 -> generateHighScoringMatch(rng)
                matchType < 2.0 / 12 -> generateLowScoringMatch(rng)
                matchType < 3.0 / 12 -> generateDominantChase(rng)
                matchType < 4.0 / 12 -> generateNarrowChase(rng)
                matchType < 5.0 / 12 -> generateCollapseMatch(rng)
                matchType < 6.0 / 12 -> generateStandardMatch(rng)
                matchType < 7.0 / 12 -> generateMiddleOrderRecovery(rng)
                matchType < 8.0 / 12 -> generateOpeningStandDominance(rng)
                matchType < 9.0 / 12 -> generateRainShortened(rng)
                matchType < 10.0 / 12 -> generateSpinDominated(rng)
                matchType < 11.0 / 12 -> generatePaceBowlingCarnage(rng)
                else -> generateCounterAttackChase(rng)
            }

            val battingTeamWon = secondInningsResult.second
            val secondInningsTotal = secondInningsResult.first

            val firstInnings = generateInnings(team1, firstInningsTotal, 120, rng)
            val secondInnings = generateInnings(team2, secondInningsTotal, 120, rng)

            val winner = if (battingTeamWon) team2 else team1
            val margin = if (battingTeamWon) {
                10 - secondInnings.wickets
            } else {
                firstInningsTotal - secondInningsTotal
            }
            val marginType = if (battingTeamWon) "wickets" else "runs"

            CricsheetMatch(
                matchId = "synth_$idx",
                date = Date(),
                venue = venue,
                teams = CricsheetMatch.Teams(batting = team1, bowling = team2),
                innings = listOf(firstInnings, secondInnings),
                result = MatchResult(winner = winner, margin = margin, marginType = marginType),
                outcome = CricsheetMatch.Outcome(winner = winner, result = "$margin $marginType")
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  MATCH TYPE GENERATORS (12 archetypes)
    //  Return: (firstInningsTotal, Pair(secondInningsTotal, chasingTeamWon))
    // ═══════════════════════════════════════════════════════════════

    private fun generateHighScoringMatch(rng: Random): Pair<Int, Pair<Int, Boolean>> {
        val prob = perturbProbabilities(0.45, rng)
        val first = rng.nextInt(180, 240)
        val chaseSuccessful = rng.nextDouble() < prob
        val second = if (chaseSuccessful) first + rng.nextInt(1, 6) else rng.nextInt(140, first)
        return Pair(first, Pair(second, chaseSuccessful))
    }

    private fun generateLowScoringMatch(rng: Random): Pair<Int, Pair<Int, Boolean>> {
        val prob = perturbProbabilities(0.55, rng)
        val first = rng.nextInt(100, 150)
        val chaseSuccessful = rng.nextDouble() < prob
        val second = if (chaseSuccessful) first + rng.nextInt(1, 4) else rng.nextInt(70, first)
        return Pair(first, Pair(second, chaseSuccessful))
    }

    private fun generateDominantChase(rng: Random): Pair<Int, Pair<Int, Boolean>> {
        val first = rng.nextInt(130, 170)
        val second = first + rng.nextInt(1, 10) // Easy win
        return Pair(first, Pair(second, true))
    }

    private fun generateNarrowChase(rng: Random): Pair<Int, Pair<Int, Boolean>> {
        val prob = perturbProbabilities(0.50, rng)
        val first = rng.nextInt(150, 200)
        val chaseSuccessful = rng.nextDouble() < prob
        val second = if (chaseSuccessful) first + rng.nextInt(1, 3) else first - rng.nextInt(1, 6)
        return Pair(first, Pair(second, chaseSuccessful))
    }

    private fun generateCollapseMatch(rng: Random): Pair<Int, Pair<Int, Boolean>> {
        val first = rng.nextInt(160, 210)
        val second = rng.nextInt(60, 120) // Batting collapse
        return Pair(first, Pair(second, false))
    }

    private fun generateStandardMatch(rng: Random): Pair<Int, Pair<Int, Boolean>> {
        val prob = perturbProbabilities(0.50, rng)
        val first = rng.nextInt(140, 190)
        val chaseSuccessful = rng.nextDouble() < prob
        val second = if (chaseSuccessful) first + rng.nextInt(1, 5) else rng.nextInt(100, first)
        return Pair(first, Pair(second, chaseSuccessful))
    }

    /** Middle order recovery: team loses 3 early wickets but recovers (e.g., 30/3 → 180/6) */
    private fun generateMiddleOrderRecovery(rng: Random): Pair<Int, Pair<Int, Boolean>> {
        val prob = perturbProbabilities(0.48, rng)
        val first = rng.nextInt(165, 195)
        val chaseSuccessful = rng.nextDouble() < prob
        val second = if (chaseSuccessful) first + rng.nextInt(1, 4) else rng.nextInt(maxOf(first - 25, 100), first)
        return Pair(first, Pair(second, chaseSuccessful))
    }

    /** Opening stand dominance: huge opening partnership, easy chase */
    private fun generateOpeningStandDominance(rng: Random): Pair<Int, Pair<Int, Boolean>> {
        val first = rng.nextInt(140, 175)
        val second = first + rng.nextInt(1, 8) // Easy win with big opening stand
        return Pair(first, Pair(second, true))
    }

    /** Rain shortened: 30-35 overs, compressed totals */
    private fun generateRainShortened(rng: Random): Pair<Int, Pair<Int, Boolean>> {
        val prob = perturbProbabilities(0.50, rng)
        val first = rng.nextInt(100, 140)
        val chaseSuccessful = rng.nextDouble() < prob
        val second = if (chaseSuccessful) first + rng.nextInt(1, 4) else rng.nextInt(75, first)
        return Pair(first, Pair(second, chaseSuccessful))
    }

    /** Spin dominated: low scoring, lots of wickets, typical subcontinent turner */
    private fun generateSpinDominated(rng: Random): Pair<Int, Pair<Int, Boolean>> {
        val prob = perturbProbabilities(0.52, rng)
        val first = rng.nextInt(140, 180)
        val chaseSuccessful = rng.nextDouble() < prob
        val second = if (chaseSuccessful) first + rng.nextInt(1, 3) else rng.nextInt(90, maxOf(first - 10, 91))
        return Pair(first, Pair(second, chaseSuccessful))
    }

    /** Pace bowling carnage: pace destroys top order, sub-150 totals */
    private fun generatePaceBowlingCarnage(rng: Random): Pair<Int, Pair<Int, Boolean>> {
        val prob = perturbProbabilities(0.40, rng)
        val first = rng.nextInt(110, 155)
        val chaseSuccessful = rng.nextDouble() < prob
        val second = if (chaseSuccessful) first + rng.nextInt(1, 3) else rng.nextInt(55, maxOf(first - 15, 56))
        return Pair(first, Pair(second, chaseSuccessful))
    }

    /** Counter-attack chase: team behind DLS par but goes berserk in death overs to win */
    private fun generateCounterAttackChase(rng: Random): Pair<Int, Pair<Int, Boolean>> {
        val prob = perturbProbabilities(0.42, rng)
        val first = rng.nextInt(170, 210)
        val chaseSuccessful = rng.nextDouble() < prob
        val second = if (chaseSuccessful) first + rng.nextInt(1, 4) else rng.nextInt(maxOf(first - 20, 100), first)
        return Pair(first, Pair(second, chaseSuccessful))
    }

    // ═══════════════════════════════════════════════════════════════
    //  PROBABILITY PERTURBATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Perturb win probability with Gaussian noise (σ=0.03-0.05).
     * Each match simulation gets unique probabilities even within the same archetype.
     */
    private fun perturbProbabilities(baseProb: Double, rng: Random): Double {
        val noise = rng.nextGaussian() * PROB_PERTURBATION_STD
        return (baseProb + noise).coerceIn(0.01, 0.99)
    }

    // ═══════════════════════════════════════════════════════════════
    //  INNINGS GENERATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Generate a realistic innings with ball-by-ball data.
     * Uses probability distributions for dots, singles, twos, boundaries, sixes, and wickets
     * that vary by phase (powerplay/middle/death).
     */
    private fun generateInnings(
        teamName: String,
        targetTotal: Int,
        maxBalls: Int,
        rng: Random
    ): CricsheetInnings {
        val overs = mutableListOf<CricsheetOver>()
        var totalRuns = 0
        var totalWickets = 0
        val totalOvers = maxBalls / 6

        for (overNum in 0 until totalOvers) {
            if (totalWickets >= 10) break

            val balls = mutableListOf<CricsheetBall>()
            var overRuns = 0
            var overWickets = 0

            val phase = when {
                overNum < 6 -> "powerplay"
                overNum >= totalOvers - 4 -> "death"
                else -> "middle"
            }

            for (ballNum in 0 until 6) {
                if (totalWickets + overWickets >= 10) break

                val runsRemaining = max(0, targetTotal - totalRuns - overRuns)
                val ballsRemaining = max(1, maxBalls - (overNum * 6 + ballNum))

                val (runs, isWicket) = generateBallOutcome(phase, runsRemaining, ballsRemaining, rng)

                if (isWicket) overWickets++

                balls.add(
                    CricsheetBall(
                        runs = runs,
                        isWicket = isWicket,
                        wicketType = if (isWicket) listOf("bowled", "caught", "lbw", "run out", "stumped")[rng.nextInt(5)] else null,
                        extras = 0,
                        extraType = null
                    )
                )
                overRuns += runs
            }

            totalRuns += overRuns
            totalWickets += overWickets

            overs.add(
                CricsheetOver(
                    overNumber = overNum + 1,
                    balls = balls,
                    runs = overRuns,
                    wickets = overWickets
                )
            )
        }

        val oversCount = overs.size.toDouble()
        return CricsheetInnings(
            team = teamName,
            overs = overs,
            total = totalRuns,
            wickets = totalWickets,
            runRate = if (oversCount > 0) totalRuns / oversCount else 0.0
        )
    }

    /** Generate a single ball outcome based on phase and match situation */
    private fun generateBallOutcome(
        phase: String,
        runsRemaining: Int,
        ballsRemaining: Int,
        rng: Random
    ): Pair<Int, Boolean> {
        val (dotProb, singleProb, twoProb, fourProb, sixProb, wicketProb) = when (phase) {
            "powerplay" -> PhaseProbabilities(0.30, 0.25, 0.08, 0.20, 0.07, 0.10)
            "death" -> PhaseProbabilities(0.22, 0.18, 0.10, 0.22, 0.15, 0.13)
            else -> PhaseProbabilities(0.38, 0.28, 0.10, 0.14, 0.04, 0.06) // middle overs
        }

        val isWicket = rng.nextDouble() < wicketProb

        val roll = rng.nextDouble()
        val runs = when {
            roll < dotProb -> 0
            roll < dotProb + singleProb -> 1
            roll < dotProb + singleProb + twoProb -> 2
            roll < dotProb + singleProb + twoProb + fourProb -> 4
            roll < dotProb + singleProb + twoProb + fourProb + sixProb -> 6
            else -> rng.nextInt(0, 3)
        }

        return Pair(runs, isWicket)
    }

    data class PhaseProbabilities(
        val dot: Double, val single: Double, val two: Double,
        val four: Double, val six: Double, val wicket: Double
    )

    // ═══════════════════════════════════════════════════════════════
    //  TEAM STATISTICS
    // ═══════════════════════════════════════════════════════════════

    /** Build team statistics from match history */
    private fun buildTeamStats(matches: List<CricsheetMatch>): Map<String, TeamStatistics> {
        val teamRunsMap = mutableMapOf<String, MutableList<Double>>()
        val teamWinsMap = mutableMapOf<String, Int>()
        val teamMatchesMap = mutableMapOf<String, Int>()

        for (match in matches) {
            val t1 = match.teams.batting
            val t2 = match.teams.bowling

            for (team in listOf(t1, t2)) {
                teamMatchesMap[team] = (teamMatchesMap[team] ?: 0) + 1
                if (match.outcome?.winner == team) {
                    teamWinsMap[team] = (teamWinsMap[team] ?: 0) + 1
                }
            }

            for (innings in match.innings) {
                teamRunsMap.getOrPut(innings.team) { mutableListOf() }.add(innings.runRate)
            }
        }

        return teamMatchesMap.keys.associateWith { team ->
            val played = teamMatchesMap[team] ?: 1
            val wins = teamWinsMap[team] ?: 0
            val avgRunRate = teamRunsMap[team]?.average() ?: 6.0
            TeamStatistics(
                battingAverage = avgRunRate * 5.5,
                bowlingAverage = 30.0 - avgRunRate,
                winRate = wins.toDouble() / played,
                powerPlayRunRate = avgRunRate * 1.15,
                deathOversRunRate = avgRunRate * 1.35,
                matchesPlayed = played
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  DETAILED EVALUATION
    // ═══════════════════════════════════════════════════════════════

    private fun evaluateDetailed(model: ScorePredictionModel, samples: List<TrainingSample>) {
        var correctWin = 0
        var totalScoreError = 0.0
        var totalSquaredError = 0.0
        var confidentCorrect = 0
        var confidentTotal = 0

        // Confusion matrix
        var tp = 0; var fp = 0; var tn = 0; var fn = 0

        for (sample in samples) {
            val output = model.forward(sample.features)
            val predictedWin = output[0] > 0.5
            val actualWin = sample.winProbabilityLabel > 0.5

            if (predictedWin == actualWin) correctWin++

            if (predictedWin && actualWin) tp++
            else if (predictedWin && !actualWin) fp++
            else if (!predictedWin && !actualWin) tn++
            else fn++

            // Score error (denormalized)
            val scoreError = abs(output[1] - sample.scorePredictionLabel) * 350.0
            totalScoreError += scoreError
            totalSquaredError += scoreError * scoreError

            // Confident predictions (>0.7 or <0.3)
            if (output[0] > 0.7 || output[0] < 0.3) {
                confidentTotal++
                if (predictedWin == actualWin) confidentCorrect++
            }
        }

        val n = samples.size.toDouble()
        val accuracy = correctWin / n * 100
        val mae = totalScoreError / n
        val rmse = sqrt(totalSquaredError / n)
        val precision = if (tp + fp > 0) tp.toDouble() / (tp + fp) * 100 else 0.0
        val recall = if (tp + fn > 0) tp.toDouble() / (tp + fn) * 100 else 0.0
        val f1 = if (precision + recall > 0) 2 * precision * recall / (precision + recall) else 0.0
        val confidentAcc = if (confidentTotal > 0) confidentCorrect.toDouble() / confidentTotal * 100 else 0.0

        println()
        println("  ┌─────────────────── Detailed Test Metrics ───────────────────┐")
        println("  │  Win Prediction Accuracy:  ${"%.2f".format(accuracy)}%")
        println("  │  Precision (win=true):     ${"%.2f".format(precision)}%")
        println("  │  Recall (win=true):        ${"%.2f".format(recall)}%")
        println("  │  F1 Score:                 ${"%.2f".format(f1)}%")
        println("  │  Confident Acc (>70%):     ${"%.2f".format(confidentAcc)}% ($confidentTotal/${samples.size} samples)")
        println("  │")
        println("  │  Score MAE:   ${"%.1f".format(mae)} runs")
        println("  │  Score RMSE:  ${"%.1f".format(rmse)} runs")
        println("  │")
        println("  │  Confusion Matrix:")
        println("  │    TP=$tp  FP=$fp")
        println("  │    FN=$fn  TN=$tn")
        println("  └────────────────────────────────────────────────────────────┘")
    }

    // ═══════════════════════════════════════════════════════════════
    //  SAMPLE PREDICTIONS
    // ═══════════════════════════════════════════════════════════════

    private fun runSamplePredictions(model: ScorePredictionModel, teamStats: Map<String, TeamStatistics>) {
        data class Scenario(
            val description: String,
            val situation: MatchSituation,
            val runsNeeded: Int,
            val ballsBowled: Int,
            val wicketsLost: Int,
            val battingTeam: String,
            val bowlingTeam: String
        )

        val scenarios = listOf(
            Scenario(
                "Early chase — 6 overs, well set, 150 needed",
                MatchSituation(7.5, 7.14, 14.0, 9, 45, isPowerPlay = true, isDeathOvers = false),
                runsNeeded = 150, ballsBowled = 36, wicketsLost = 1,
                battingTeam = "India", bowlingTeam = "Australia"
            ),
            Scenario(
                "Death overs — 3 overs left, 35 needed, 6 wickets in hand",
                MatchSituation(8.0, 11.67, 3.0, 6, 48, isPowerPlay = false, isDeathOvers = true),
                runsNeeded = 35, ballsBowled = 102, wicketsLost = 4,
                battingTeam = "England", bowlingTeam = "South Africa"
            ),
            Scenario(
                "Batting collapse — 10 overs, 120 needed, only 3 wickets left",
                MatchSituation(5.0, 12.0, 10.0, 3, 25, isPowerPlay = false, isDeathOvers = false),
                runsNeeded = 120, ballsBowled = 60, wicketsLost = 7,
                battingTeam = "Pakistan", bowlingTeam = "New Zealand"
            ),
            Scenario(
                "Comfortable chase — 8 overs left, 50 needed, 8 wickets in hand",
                MatchSituation(8.5, 6.25, 8.0, 8, 52, isPowerPlay = false, isDeathOvers = false),
                runsNeeded = 50, ballsBowled = 72, wicketsLost = 2,
                battingTeam = "Australia", bowlingTeam = "Sri Lanka"
            ),
            Scenario(
                "Last over thriller — 6 balls, 12 needed, 4 wickets",
                MatchSituation(7.8, 12.0, 1.0, 4, 55, isPowerPlay = false, isDeathOvers = true),
                runsNeeded = 12, ballsBowled = 114, wicketsLost = 6,
                battingTeam = "West Indies", bowlingTeam = "India"
            ),
            Scenario(
                "Powerplay dominance — 4 overs, scoring fast",
                MatchSituation(10.0, 7.5, 16.0, 10, 40, isPowerPlay = true, isDeathOvers = false),
                runsNeeded = 140, ballsBowled = 24, wicketsLost = 0,
                battingTeam = "India", bowlingTeam = "Bangladesh"
            ),
            Scenario(
                "Middle overs stall — was scoring well, now stuck",
                MatchSituation(6.0, 9.5, 9.0, 6, 28, isPowerPlay = false, isDeathOvers = false),
                runsNeeded = 95, ballsBowled = 66, wicketsLost = 4,
                battingTeam = "Afghanistan", bowlingTeam = "Pakistan"
            ),
            Scenario(
                "Spin squeeze — turning pitch, slow scoring",
                MatchSituation(5.5, 8.8, 10.0, 5, 20, isPowerPlay = false, isDeathOvers = false),
                runsNeeded = 88, ballsBowled = 60, wicketsLost = 5,
                battingTeam = "Zimbabwe", bowlingTeam = "Sri Lanka"
            )
        )

        println()
        for (scenario in scenarios) {
            val battingStats = teamStats[scenario.battingTeam] ?: TeamStatistics()
            val bowlingStats = teamStats[scenario.bowlingTeam] ?: TeamStatistics()

            val features = FeatureExtractor.extractFeatures(
                situation = scenario.situation,
                battingTeamStats = battingStats,
                bowlingTeamStats = bowlingStats,
                runsNeeded = scenario.runsNeeded,
                ballsBowled = scenario.ballsBowled,
                wicketsLost = scenario.wicketsLost,
                maxBalls = 120
            )
            val output = model.forward(features)

            val winProb = output[0] * 100
            val predictedScore = output[1] * 350

            println("  ┌─ ${scenario.description}")
            println("  │  ${scenario.battingTeam} vs ${scenario.bowlingTeam}")
            println("  │  Need ${scenario.runsNeeded} off ${120 - scenario.ballsBowled} balls, " +
                    "${10 - scenario.wicketsLost} wickets in hand")
            println("  │  → Win Probability: ${"%.1f".format(winProb)}%")
            println("  │  → Predicted Score:  ${"%.0f".format(predictedScore)} runs")
            println("  │  → Confidence:       ${"%.1f".format(abs(output[0] - 0.5) * 200)}%")
            println("  └──────────────────────────────────────────────")
            println()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  CONVERGENCE ANALYSIS
    // ═══════════════════════════════════════════════════════════════

    private fun analyzeConvergence(result: TrainingResult) {
        val history = result.history

        println("  Total epochs trained:    ${result.totalEpochs}")
        println("  Converged:               ${result.converged}" +
                if (result.converged) " (epoch ${result.convergenceEpoch})" else "")
        println("  Best validation loss:    ${"%.6f".format(result.bestValidationLoss)}")
        println("  Best epoch:              ${history.bestEpoch() + 1}")
        println("  Final train loss:        ${"%.6f".format(result.finalTrainLoss)}")
        println("  Final val loss:          ${"%.6f".format(result.finalValLoss)}")
        println("  Convergence rate:        ${"%.6f".format(history.convergenceRate())} loss/epoch")
        println("  Overfitting detected:    ${result.isOverfitting}")

        val overfitOnset = history.detectOverfittingOnset()
        if (overfitOnset >= 0) {
            println("  Overfitting onset:       epoch $overfitOnset")
        }

        // Learning rate progression
        if (history.learningRates.isNotEmpty()) {
            println()
            println("  Learning rate progression:")
            val lrs = history.learningRates
            val step = max(1, lrs.size / 8)
            for (i in lrs.indices step step) {
                println("    Epoch ${i + 1}: ${"%.2e".format(lrs[i])}")
            }
            println("    Epoch ${lrs.size}: ${"%.2e".format(lrs.last())}")
        }

        // Gradient norm progression
        if (history.gradientNorms.isNotEmpty()) {
            println()
            println("  Gradient norm progression:")
            val gn = history.gradientNorms
            val step = max(1, gn.size / 8)
            for (i in gn.indices step step) {
                println("    Epoch ${i + 1}: ${"%.4f".format(gn[i])}")
            }
            println("    Epoch ${gn.size}: ${"%.4f".format(gn.last())}")
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  VISUALIZATION HELPERS
    // ═══════════════════════════════════════════════════════════════

    private fun printFeatureStats(samples: List<TrainingSample>) {
        if (samples.isEmpty()) return
        println()
        println("  Feature distribution (min / mean / max):")
        val featureNames = listOf(
            "ballProgress", "wicketPressure", "runRateRatio", "runsNeededNorm",
            "requiredRateNorm", "currentRateNorm", "recentMomentum", "powerPlayFlag",
            "deathOversFlag", "middleOversFlag", "battingStrength", "bowlingStrength",
            "battingWinRate", "bowlingWinRate", "pressureIndex", "chaseEfficiency"
        )
        for (f in 0 until 16) {
            val values = samples.map { it.features[f] }
            val mn = values.minOrNull() ?: 0.0
            val mx = values.maxOrNull() ?: 0.0
            val avg = values.average()
            println("    [${f.toString().padStart(2)}] ${featureNames[f].padEnd(18)} ${
                "%.3f".format(mn)} / ${"%.3f".format(avg)} / ${"%.3f".format(mx)}")
        }

        // Label distribution
        val winCount = samples.count { it.winProbabilityLabel > 0.5 }
        println()
        println("  Label distribution:")
        println("    Win>0.5: $winCount (${"%.1f".format(winCount * 100.0 / samples.size)}%)")
        println("    Win≤0.5: ${samples.size - winCount} (${"%.1f".format((samples.size - winCount) * 100.0 / samples.size)}%)")
        println("    Avg win label: ${"%.3f".format(samples.map { it.winProbabilityLabel }.average())}")
        println("    Avg score label: ${"%.3f".format(samples.map { it.scorePredictionLabel }.average())}")
        println("    Label range (win): [${"%.3f".format(samples.minOf { it.winProbabilityLabel })}, ${"%.3f".format(samples.maxOf { it.winProbabilityLabel })}]")
    }

    /** ASCII loss curve visualization */
    private fun printLossCurve(history: TrainingHistory) {
        if (history.epochCount < 2) return

        val trainLosses = history.trainLosses
        val valLosses = history.valLosses

        val allLosses = trainLosses + valLosses
        val maxLoss = allLosses.maxOrNull() ?: 1.0
        val minLoss = allLosses.minOrNull() ?: 0.0
        val range = max(maxLoss - minLoss, 1e-6)

        val height = 16
        val width = min(60, history.epochCount)
        val step = max(1, history.epochCount / width)

        println()
        println("  Loss Curve (T=train, V=val):")
        println("  ${"%.4f".format(maxLoss)} ┤")

        for (row in 0 until height) {
            val threshold = maxLoss - (row.toDouble() / height) * range
            val sb = StringBuilder("  ${"%.4f".format(threshold)} │")

            for (col in 0 until width) {
                val epoch = col * step
                if (epoch >= history.epochCount) break

                val tLoss = trainLosses[epoch]
                val vLoss = valLosses[epoch]
                val tNorm = (maxLoss - tLoss) / range * height
                val vNorm = (maxLoss - vLoss) / range * height

                val trainHere = tNorm >= row && tNorm < row + 1
                val valHere = vNorm >= row && vNorm < row + 1

                sb.append(
                    when {
                        trainHere && valHere -> "X"
                        trainHere -> "T"
                        valHere -> "V"
                        else -> " "
                    }
                )
            }
            println(sb.toString())
        }

        val epochLabels = StringBuilder("         └")
        for (i in 0 until width) {
            epochLabels.append("─")
        }
        println(epochLabels.toString())
        println("          1${" ".repeat(max(0, width - 3))}${history.epochCount}")
        println("                          Epoch →")
    }

    // ═══════════════════════════════════════════════════════════════
    //  UTILITY: Gaussian random for kotlin.random.Random
    // ═══════════════════════════════════════════════════════════════

    /** Box-Muller transform to generate Gaussian random values from kotlin.random.Random */
    private fun Random.nextGaussian(): Double {
        val u1 = nextDouble()
        val u2 = nextDouble()
        return sqrt(-2.0 * ln(max(u1, 1e-10))) * cos(2.0 * PI * u2)
    }
}
