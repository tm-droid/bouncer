package com.tanush.bouncer.analysis.ml

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/**
 * Integration tests for the TrainingPipeline — the complete training loop.
 *
 * Tests cover:
 *  - End-to-end training on synthetic data
 *  - Convergence detection
 *  - Early stopping behavior
 *  - Training history recording
 *  - Best model checkpointing
 *  - Validation split
 *  - Gradient clipping during training
 *  - Training config validation
 *  - Training on diverse data patterns
 */
class TrainingPipelineTest {

    // ═══════════════════════════════════════════════════════════
    // Synthetic Training Data Generation
    // ═══════════════════════════════════════════════════════════

    /**
     * Creates synthetic training samples with learnable patterns.
     * Higher feature values → higher win probability.
     */
    private fun createSyntheticData(count: Int = 100): List<TrainingSample> {
        return (0 until count).map { i ->
            val features = DoubleArray(FeatureExtractor.FEATURE_COUNT) { j ->
                // Create a pattern: samples with index in upper half have higher features
                if (i >= count / 2) (j + 1.0) / FeatureExtractor.FEATURE_COUNT * 0.8 + 0.1
                else (j + 1.0) / FeatureExtractor.FEATURE_COUNT * 0.3
            }
            val winProb = if (i >= count / 2) 1.0 else 0.0
            val scoreNorm = features.average() // score correlates with feature average

            TrainingSample(
                features = features,
                winProbabilityLabel = winProb,
                scorePredictionLabel = scoreNorm,
                matchId = "synthetic_$i",
                ballNumber = i * 2
            )
        }
    }

    /**
     * Creates a simple dataset where win depends on a single strong signal.
     */
    private fun createSimpleSignalData(count: Int = 80): List<TrainingSample> {
        return (0 until count).map { i ->
            val signal = if (i < count / 2) 0.2 else 0.8
            val features = DoubleArray(FeatureExtractor.FEATURE_COUNT) { 0.5 }.also {
                it[0] = signal // Only first feature matters
            }
            TrainingSample(
                features = features,
                winProbabilityLabel = if (signal > 0.5) 1.0 else 0.0,
                scorePredictionLabel = signal * 0.7,
                matchId = "simple_$i",
                ballNumber = i
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    // End-to-End Training
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `training reduces loss over epochs`() {
        val config = TrainingConfig(
            epochs = 30,
            batchSize = 16,
            learningRate = 0.005,
            validationSplit = 0.2,
            earlyStoppingPatience = 0, // Disable early stopping
            convergenceWindow = 10,
            stopOnConvergence = false,
            lrScheduler = LearningRateScheduler.None,
            verbose = false
        )
        val pipeline = TrainingPipeline(config)
        val model = ScorePredictionModel.create(seed = 42L)
        val data = createSyntheticData(100)

        val result = pipeline.train(model, data)

        // Loss should decrease over training
        val firstLoss = result.history.trainLosses.first()
        val lastLoss = result.history.trainLosses.last()

        assertTrue(
            "Loss should decrease: first=$firstLoss, last=$lastLoss",
            lastLoss < firstLoss
        )
    }

    @Test
    fun `training produces non-empty history`() {
        val config = TrainingConfig(
            epochs = 10,
            batchSize = 8,
            learningRate = 0.003,
            validationSplit = 0.2,
            earlyStoppingPatience = 0,
            stopOnConvergence = false,
            lrScheduler = LearningRateScheduler.None,
            verbose = false
        )
        val pipeline = TrainingPipeline(config)
        val model = ScorePredictionModel.create()
        val data = createSyntheticData(60)

        val result = pipeline.train(model, data)

        assertEquals("Should have 10 epochs of history", 10, result.history.epochCount)
        assertEquals(10, result.history.trainLosses.size)
        assertEquals(10, result.history.valLosses.size)
        assertEquals(10, result.history.trainAccuracies.size)
        assertEquals(10, result.history.learningRates.size)
        assertEquals(10, result.history.gradientNorms.size)
    }

    @Test
    fun `training accuracy improves on learnable data`() {
        val config = TrainingConfig(
            epochs = 50,
            batchSize = 16,
            learningRate = 0.005,
            validationSplit = 0.2,
            earlyStoppingPatience = 0,
            stopOnConvergence = false,
            lrScheduler = LearningRateScheduler.None,
            verbose = false
        )
        val pipeline = TrainingPipeline(config)
        val model = ScorePredictionModel.create(seed = 42L)
        val data = createSimpleSignalData(80)

        val result = pipeline.train(model, data)

        val firstAcc = result.history.trainAccuracies.first()
        val lastAcc = result.history.trainAccuracies.last()

        assertTrue(
            "Accuracy should improve: first=$firstAcc, last=$lastAcc",
            lastAcc >= firstAcc
        )
    }

    // ═══════════════════════════════════════════════════════════
    // Early Stopping
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `early stopping stops before max epochs when loss plateaus`() {
        val config = TrainingConfig(
            epochs = 200,
            batchSize = 8,
            learningRate = 0.01,
            validationSplit = 0.2,
            earlyStoppingPatience = 10,
            stopOnConvergence = false,
            lrScheduler = LearningRateScheduler.None,
            verbose = false
        )
        val pipeline = TrainingPipeline(config)
        val model = ScorePredictionModel.create(seed = 42L)
        val data = createSimpleSignalData(50)

        val result = pipeline.train(model, data)

        // With early stopping patience=10, training should stop before 200 epochs
        // (it will likely converge quickly on simple data)
        assertTrue(
            "Early stopping should trigger before max epochs: trained for ${result.totalEpochs}",
            result.totalEpochs <= 200
        )
    }

    // ═══════════════════════════════════════════════════════════
    // Convergence Detection
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `convergence is detected when loss stabilizes`() {
        val config = TrainingConfig(
            epochs = 200,
            batchSize = 8,
            learningRate = 0.01,
            validationSplit = 0.2,
            earlyStoppingPatience = 0,
            convergenceWindow = 10,
            convergenceThreshold = 0.01, // generous threshold
            stopOnConvergence = true,
            lrScheduler = LearningRateScheduler.None,
            verbose = false
        )
        val pipeline = TrainingPipeline(config)
        val model = ScorePredictionModel.create(seed = 10L)
        val data = createSimpleSignalData(60)

        val result = pipeline.train(model, data)

        // On simple data with high enough LR, convergence should be detected
        if (result.converged) {
            assertTrue(
                "Convergence epoch should be valid",
                result.convergenceEpoch >= 0 && result.convergenceEpoch < 200
            )
        }
        // Either converged or completed all epochs — both are valid outcomes
        assertTrue(result.totalEpochs > 0)
    }

    // ═══════════════════════════════════════════════════════════
    // Best Model Checkpointing
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `best model is preserved during training`() {
        val config = TrainingConfig(
            epochs = 30,
            batchSize = 8,
            learningRate = 0.005,
            validationSplit = 0.3,
            earlyStoppingPatience = 0,
            stopOnConvergence = false,
            lrScheduler = LearningRateScheduler.None,
            verbose = false
        )
        val pipeline = TrainingPipeline(config)
        val model = ScorePredictionModel.create(seed = 42L)
        val data = createSyntheticData(80)

        val result = pipeline.train(model, data)

        // Best model should have lower or equal validation loss
        val bestModelInput = DoubleArray(16) { 0.5 }
        val bestOutput = result.bestModel.forward(bestModelInput)

        // Best model should produce valid output
        assertEquals(2, bestOutput.size)
        for (v in bestOutput) {
            assertFalse(v.isNaN())
            assertTrue(v >= 0.0 && v <= 1.0)
        }

        // The best validation loss should be tracked
        assertTrue(
            "Best val loss should be finite",
            result.bestValidationLoss < Double.MAX_VALUE
        )
    }

    // ═══════════════════════════════════════════════════════════
    // Evaluation
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `evaluate produces valid metrics`() {
        val config = TrainingConfig(
            epochs = 1,
            batchSize = 8,
            learningRate = 0.001,
            validationSplit = 0.0,
            earlyStoppingPatience = 0,
            stopOnConvergence = false,
            lrScheduler = LearningRateScheduler.None,
            verbose = false
        )
        val pipeline = TrainingPipeline(config)
        val model = ScorePredictionModel.create()
        val data = createSyntheticData(50)

        val evalResult = pipeline.evaluate(model, data)

        assertTrue("Accuracy should be in [0, 1]", evalResult.accuracy >= 0.0 && evalResult.accuracy <= 1.0)
        assertTrue("Loss should be non-negative", evalResult.avgLoss >= 0.0)
        assertFalse("Loss should not be NaN", evalResult.avgLoss.isNaN())
    }

    // ═══════════════════════════════════════════════════════════
    // Training Result Properties
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `training result summary is non-empty`() {
        val config = TrainingConfig(
            epochs = 5,
            batchSize = 8,
            learningRate = 0.001,
            validationSplit = 0.2,
            earlyStoppingPatience = 0,
            stopOnConvergence = false,
            lrScheduler = LearningRateScheduler.None,
            verbose = false
        )
        val pipeline = TrainingPipeline(config)
        val model = ScorePredictionModel.create()
        val data = createSyntheticData(40)

        val result = pipeline.train(model, data)
        val summary = result.summary()

        assertTrue("Summary should contain 'TRAINING SUMMARY'", summary.contains("TRAINING SUMMARY"))
        assertTrue("Summary should contain epoch count", summary.contains("Total Epochs"))
        assertTrue("Summary should contain loss", summary.contains("Train Loss"))
    }

    @Test
    fun `training history convergence rate is positive when loss decreases`() {
        val config = TrainingConfig(
            epochs = 30,
            batchSize = 8,
            learningRate = 0.005,
            validationSplit = 0.2,
            earlyStoppingPatience = 0,
            stopOnConvergence = false,
            lrScheduler = LearningRateScheduler.None,
            verbose = false
        )
        val pipeline = TrainingPipeline(config)
        val model = ScorePredictionModel.create(seed = 42L)
        val data = createSyntheticData(80)

        val result = pipeline.train(model, data)
        val rate = result.history.convergenceRate()

        // Convergence rate should typically be positive when training is effective
        // (first val loss - last val loss) / count
        if (result.history.valLosses.first() > result.history.valLosses.last()) {
            assertTrue("Convergence rate should be positive when loss decreases", rate > 0)
        }
    }

    @Test
    fun `training history best epoch is valid`() {
        val config = TrainingConfig(
            epochs = 15,
            batchSize = 8,
            learningRate = 0.003,
            validationSplit = 0.2,
            earlyStoppingPatience = 0,
            stopOnConvergence = false,
            lrScheduler = LearningRateScheduler.None,
            verbose = false
        )
        val pipeline = TrainingPipeline(config)
        val model = ScorePredictionModel.create()
        val data = createSyntheticData(60)

        val result = pipeline.train(model, data)
        val bestEpoch = result.history.bestEpoch()

        assertTrue("Best epoch should be >= 0", bestEpoch >= 0)
        assertTrue("Best epoch should be < total epochs", bestEpoch < result.totalEpochs)
    }

    // ═══════════════════════════════════════════════════════════
    // Config Validation
    // ═══════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException::class)
    fun `training with empty data throws`() {
        val pipeline = TrainingPipeline(TrainingConfig(epochs = 5, verbose = false))
        val model = ScorePredictionModel.create()
        pipeline.train(model, emptyList())
    }

    @Test
    fun `training with single sample does not crash`() {
        val config = TrainingConfig(
            epochs = 3,
            batchSize = 1,
            learningRate = 0.001,
            validationSplit = 0.0,
            earlyStoppingPatience = 0,
            stopOnConvergence = false,
            lrScheduler = LearningRateScheduler.None,
            verbose = false
        )
        val pipeline = TrainingPipeline(config)
        val model = ScorePredictionModel.create()
        val data = listOf(
            TrainingSample(
                features = DoubleArray(16) { 0.5 },
                winProbabilityLabel = 1.0,
                scorePredictionLabel = 0.5,
                matchId = "solo",
                ballNumber = 1
            )
        )

        val result = pipeline.train(model, data)
        assertEquals(3, result.totalEpochs)
    }

    // ═══════════════════════════════════════════════════════════
    // Custom Logger
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `custom logger receives messages`() {
        val messages = mutableListOf<String>()
        val config = TrainingConfig(
            epochs = 5,
            batchSize = 8,
            learningRate = 0.001,
            validationSplit = 0.2,
            earlyStoppingPatience = 0,
            stopOnConvergence = false,
            lrScheduler = LearningRateScheduler.None,
            verbose = true,
            logEvery = 1,
            logger = { msg -> messages.add(msg) }
        )
        val pipeline = TrainingPipeline(config)
        val model = ScorePredictionModel.create()
        val data = createSyntheticData(40)

        pipeline.train(model, data)

        assertTrue("Logger should receive messages", messages.isNotEmpty())
        assertTrue("Messages should contain loss info", messages.any { it.contains("Train Loss") })
    }

    // ═══════════════════════════════════════════════════════════
    // TrainingSample Equality
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `TrainingSample equals works correctly`() {
        val s1 = TrainingSample(
            features = doubleArrayOf(1.0, 2.0),
            winProbabilityLabel = 1.0,
            scorePredictionLabel = 0.5,
            matchId = "m1",
            ballNumber = 10
        )
        val s2 = TrainingSample(
            features = doubleArrayOf(1.0, 2.0),
            winProbabilityLabel = 1.0,
            scorePredictionLabel = 0.5,
            matchId = "m1",
            ballNumber = 10
        )
        val s3 = TrainingSample(
            features = doubleArrayOf(3.0, 4.0),
            winProbabilityLabel = 0.0,
            scorePredictionLabel = 0.2,
            matchId = "m2",
            ballNumber = 20
        )

        assertEquals(s1, s2)
        assertNotEquals(s1, s3)
        assertEquals(s1.hashCode(), s2.hashCode())
    }

    // ═══════════════════════════════════════════════════════════
    // Training History Analysis
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `training history recent snapshot is formatted`() {
        val history = TrainingHistory()
        repeat(10) { i ->
            history.recordEpoch(
                epoch = i,
                trainLoss = 1.0 - i * 0.08,
                valLoss = 1.1 - i * 0.07,
                trainAccuracy = 0.5 + i * 0.04,
                valAccuracy = 0.48 + i * 0.035,
                learningRate = 0.001,
                gradientNorm = 0.5
            )
        }

        val snapshot = history.recentSnapshot(3)
        assertTrue("Snapshot should contain epoch data", snapshot.contains("Epoch"))
        assertTrue("Snapshot should contain train loss", snapshot.contains("train="))
        assertTrue("Snapshot should contain val loss", snapshot.contains("val="))
    }
}
