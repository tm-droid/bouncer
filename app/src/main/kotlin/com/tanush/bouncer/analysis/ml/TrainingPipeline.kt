package com.tanush.bouncer.analysis.ml

import kotlin.math.*
import io.sentry.Sentry

/**
 * Complete training pipeline for the score prediction neural network.
 *
 * Handles:
 *  - Mini-batch training loop with configurable batch size
 *  - Train/validation split for overfitting detection
 *  - Convergence monitoring with configurable criteria
 *  - Early stopping to prevent overfitting
 *  - Model checkpointing (saves best model)
 *  - Learning rate scheduling
 *  - Gradient clipping for training stability
 *  - Detailed training history for analysis
 *
 * Usage:
 * ```kotlin
 * val config = TrainingConfig(epochs = 100, batchSize = 32, learningRate = 0.001)
 * val pipeline = TrainingPipeline(config)
 * val result = pipeline.train(model, trainingSamples)
 * ```
 */
class TrainingPipeline(private val config: TrainingConfig) {

    /**
     * Train the model on the provided dataset.
     *
     * @param model The neural network model to train
     * @param data All training samples
     * @param optimizer Gradient descent optimizer (default: Adam)
     * @param lossFunction Loss function (default: MultiTask)
     * @return [TrainingResult] with history, best model, and convergence metrics
     */
    fun train(
        model: ScorePredictionModel,
        data: List<TrainingSample>,
        optimizer: Optimizer = Adam(config.learningRate),
        lossFunction: LossFunction = LossFunction.MultiTask()
    ): TrainingResult {
        require(data.isNotEmpty()) { "Training data cannot be empty" }

        // Split data into train/validation
        val shuffled = data.shuffled()
        val splitIdx = (shuffled.size * (1.0 - config.validationSplit)).toInt()
        val trainData = shuffled.subList(0, max(1, splitIdx))
        val valData = if (splitIdx < shuffled.size) shuffled.subList(splitIdx, shuffled.size) else emptyList()

        // Training state
        val history = TrainingHistory()
        var bestModel = model.copy()
        var bestValLoss = Double.MAX_VALUE
        var patienceCounter = 0
        var converged = false
        var convergenceEpoch = -1

        // Learning rate scheduler
        val scheduler = config.lrScheduler

        // Training loop
        for (epoch in 0 until config.epochs) {
            // Get current learning rate from scheduler
            val currentLR = scheduler.getLearningRate(
                config.learningRate, epoch, config.epochs,
                history.trainLosses.lastOrNull() ?: Double.MAX_VALUE
            )

            // Train one epoch
            val epochResult = trainOneEpoch(
                model = model,
                trainData = trainData,
                optimizer = optimizer,
                lossFunction = lossFunction,
                learningRate = currentLR
            )

            // Evaluate on validation set
            val valResult = if (valData.isNotEmpty()) {
                evaluate(model, valData, lossFunction)
            } else {
                epochResult
            }

            // Record history
            history.recordEpoch(
                epoch = epoch,
                trainLoss = epochResult.avgLoss,
                valLoss = valResult.avgLoss,
                trainAccuracy = epochResult.accuracy,
                valAccuracy = valResult.accuracy,
                learningRate = currentLR,
                gradientNorm = epochResult.avgGradientNorm
            )

            // Check for best model (checkpoint)
            if (valResult.avgLoss < bestValLoss) {
                bestValLoss = valResult.avgLoss
                bestModel = model.copy()
                patienceCounter = 0
            } else {
                patienceCounter++
            }

            // Convergence check
            if (!converged && isConverged(history)) {
                converged = true
                convergenceEpoch = epoch
                if (config.stopOnConvergence) break
            }

            // Early stopping check
            if (config.earlyStoppingPatience > 0 && patienceCounter >= config.earlyStoppingPatience) {
                history.addNote(epoch, "Early stopping triggered after $patienceCounter epochs without improvement")
                break
            }

            // Log progress
            if (config.verbose && (epoch % config.logEvery == 0 || epoch == config.epochs - 1)) {
                config.logger?.invoke(
                    "Epoch ${epoch + 1}/${config.epochs} | " +
                    "Train Loss: ${"%.6f".format(epochResult.avgLoss)} | " +
                    "Val Loss: ${"%.6f".format(valResult.avgLoss)} | " +
                    "Train Acc: ${"%.2f".format(epochResult.accuracy * 100)}% | " +
                    "Val Acc: ${"%.2f".format(valResult.accuracy * 100)}% | " +
                    "LR: ${"%.2e".format(currentLR)} | " +
                    "Grad Norm: ${"%.4f".format(epochResult.avgGradientNorm)}"
                )
            }
        }

        return TrainingResult(
            trainedModel = model,
            bestModel = bestModel,
            history = history,
            converged = converged,
            convergenceEpoch = convergenceEpoch,
            bestValidationLoss = bestValLoss,
            totalEpochs = history.epochCount,
            finalTrainLoss = history.trainLosses.lastOrNull() ?: Double.MAX_VALUE,
            finalValLoss = history.valLosses.lastOrNull() ?: Double.MAX_VALUE
        )
    }

    /**
     * Train for one complete epoch using mini-batch gradient descent.
     */
    private fun trainOneEpoch(
        model: ScorePredictionModel,
        trainData: List<TrainingSample>,
        optimizer: Optimizer,
        lossFunction: LossFunction,
        learningRate: Double
    ): EpochResult {
        val shuffledTrain = trainData.shuffled()
        var totalLoss = 0.0
        var totalGradNorm = 0.0
        var correctPredictions = 0
        var totalSamples = 0
        var batchCount = 0

        // Process mini-batches
        for (batchStart in shuffledTrain.indices step config.batchSize) {
            val batchEnd = min(batchStart + config.batchSize, shuffledTrain.size)
            val batch = shuffledTrain.subList(batchStart, batchEnd)

            // Accumulate gradients over the batch
            var batchLoss = 0.0
            var accumulatedGradients: List<LayerGradients>? = null

            for (sample in batch) {
                // Forward pass with cache
                val cache = model.forwardWithCache(sample.features)

                // Compute target
                val target = doubleArrayOf(sample.winProbabilityLabel, sample.scorePredictionLabel)

                // Compute loss
                val loss = lossFunction.compute(cache.output, target)
                batchLoss += loss

                // Compute gradient of loss
                val lossGrad = lossFunction.gradient(cache.output, target)

                // Backward pass
                val gradients = model.backward(cache, lossGrad)

                // Accumulate gradients
                accumulatedGradients = if (accumulatedGradients == null) {
                    gradients
                } else {
                    accumulateGradients(accumulatedGradients, gradients)
                }

                // Track accuracy (for win prediction — threshold at 0.5)
                val predictedWin = cache.output[0] > 0.5
                val actualWin = sample.winProbabilityLabel > 0.5
                if (predictedWin == actualWin) correctPredictions++
                totalSamples++
            }

            // Average gradients over batch
            val batchSize = batch.size.toDouble()
            val averagedGradients = (accumulatedGradients ?: continue).map { grad ->
                LayerGradients(
                    weightGradients = Array(grad.weightGradients.size) { j ->
                        DoubleArray(grad.weightGradients[j].size) { i ->
                            grad.weightGradients[j][i] / batchSize
                        }
                    },
                    biasGradients = DoubleArray(grad.biasGradients.size) { j ->
                        grad.biasGradients[j] / batchSize
                    },
                    inputGradient = grad.inputGradient
                )
            }

            // Gradient clipping
            val (clippedGradients, gradNorm) = if (config.gradientClipNorm > 0) {
                GradientClipping.clipByNorm(averagedGradients, config.gradientClipNorm)
            } else {
                Pair(averagedGradients, computeGradientNorm(averagedGradients))
            }

            // Optimizer step
            optimizer.step(model, clippedGradients, config.l2Lambda)

            totalLoss += batchLoss / batchSize
            totalGradNorm += gradNorm
            batchCount++
        }

        return EpochResult(
            avgLoss = totalLoss / max(batchCount, 1),
            accuracy = correctPredictions.toDouble() / max(totalSamples, 1),
            avgGradientNorm = totalGradNorm / max(batchCount, 1)
        )
    }

    /**
     * Evaluate model on a dataset without training.
     */
    fun evaluate(
        model: ScorePredictionModel,
        data: List<TrainingSample>,
        lossFunction: LossFunction = LossFunction.MultiTask()
    ): EpochResult {
        var totalLoss = 0.0
        var correctPredictions = 0

        for (sample in data) {
            try {
                val output = model.forward(sample.features)
                val target = doubleArrayOf(sample.winProbabilityLabel, sample.scorePredictionLabel)

                totalLoss += lossFunction.compute(output, target)

                val predictedWin = output[0] > 0.5
                val actualWin = sample.winProbabilityLabel > 0.5
                if (predictedWin == actualWin) correctPredictions++
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }

        return EpochResult(
            avgLoss = totalLoss / max(data.size, 1),
            accuracy = correctPredictions.toDouble() / max(data.size, 1),
            avgGradientNorm = 0.0
        )
    }

    /**
     * Check if training has converged based on recent loss history.
     *
     * Convergence is detected when the validation loss improvement over the
     * last N epochs falls below a threshold.
     */
    private fun isConverged(history: TrainingHistory): Boolean {
        try {
            val window = config.convergenceWindow
            if (history.epochCount < window) return false

            val recentLosses = history.valLosses.takeLast(window)
            val firstHalf = recentLosses.subList(0, window / 2)
            val secondHalf = recentLosses.subList(window / 2, recentLosses.size)

            val firstAvg = firstHalf.average()
            val secondAvg = secondHalf.average()

            // Converged if improvement is less than threshold
            val improvement = (firstAvg - secondAvg) / max(firstAvg, LossFunction.EPSILON)
            return improvement < config.convergenceThreshold && improvement >= 0
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    /** Accumulate gradients from two backward passes. */
    private fun accumulateGradients(
        accumulated: List<LayerGradients>,
        newGradients: List<LayerGradients>
    ): List<LayerGradients> {
        return accumulated.zip(newGradients).map { (acc, new_) ->
            try {
                LayerGradients(
                    weightGradients = Array(acc.weightGradients.size) { j ->
                        DoubleArray(acc.weightGradients[j].size) { i ->
                            acc.weightGradients[j][i] + new_.weightGradients[j][i]
                        }
                    },
                    biasGradients = DoubleArray(acc.biasGradients.size) { j ->
                        acc.biasGradients[j] + new_.biasGradients[j]
                    },
                    inputGradient = acc.inputGradient // Not accumulated (not needed)
                )
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }
    }

    /** Compute the L2 norm of all gradients. */
    private fun computeGradientNorm(gradients: List<LayerGradients>): Double {
        try {
            var sumSq = 0.0
            for (grad in gradients) {
                for (wg in grad.weightGradients) {
                    for (g in wg) sumSq += g * g
                }
                for (g in grad.biasGradients) sumSq += g * g
            }
            return sqrt(sumSq)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Configuration
// ═══════════════════════════════════════════════════════════════════

/**
 * Training configuration with all hyperparameters.
 */
data class TrainingConfig(
    /** Number of training epochs */
    val epochs: Int = 100,

    /** Mini-batch size for gradient descent */
    val batchSize: Int = 32,

    /** Initial learning rate */
    val learningRate: Double = 0.001,

    /** Fraction of data reserved for validation */
    val validationSplit: Double = 0.2,

    /** L2 regularization strength (weight decay) */
    val l2Lambda: Double = 0.0001,

    /** Maximum gradient L2 norm (0 = no clipping) */
    val gradientClipNorm: Double = 5.0,

    /** Number of epochs without improvement before stopping */
    val earlyStoppingPatience: Int = 15,

    /** Number of recent epochs to check for convergence */
    val convergenceWindow: Int = 10,

    /** Minimum relative improvement to not be considered converged */
    val convergenceThreshold: Double = 1e-4,

    /** Whether to stop training when convergence is detected */
    val stopOnConvergence: Boolean = true,

    /** Learning rate scheduler */
    val lrScheduler: LearningRateScheduler = LearningRateScheduler.WarmupCosineDecay(warmupEpochs = 5),

    /** Print training progress */
    val verbose: Boolean = true,

    /** Log every N epochs */
    val logEvery: Int = 5,

    /** Custom logging callback */
    val logger: ((String) -> Unit)? = null
)

// ═══════════════════════════════════════════════════════════════════
// Results and History
// ═══════════════════════════════════════════════════════════════════

/**
 * Result of a single epoch evaluation.
 */
data class EpochResult(
    val avgLoss: Double,
    val accuracy: Double,
    val avgGradientNorm: Double
)

/**
 * Complete training result with model, history, and convergence info.
 */
data class TrainingResult(
    /** The model after final epoch of training */
    val trainedModel: ScorePredictionModel,

    /** The model checkpoint with lowest validation loss */
    val bestModel: ScorePredictionModel,

    /** Complete training history */
    val history: TrainingHistory,

    /** Whether training converged */
    val converged: Boolean,

    /** Epoch at which convergence was detected (-1 if not converged) */
    val convergenceEpoch: Int,

    /** Best validation loss achieved */
    val bestValidationLoss: Double,

    /** Total epochs trained */
    val totalEpochs: Int,

    /** Training loss at final epoch */
    val finalTrainLoss: Double,

    /** Validation loss at final epoch */
    val finalValLoss: Double
) {
    /** Check if the model is potentially overfitting (val loss >> train loss). */
    val isOverfitting: Boolean
        get() = finalValLoss > finalTrainLoss * 1.5

    /** Summary string for logging. */
    fun summary(): String = buildString {
        try {
            appendLine("══════════════════════════════════════════")
            appendLine("  TRAINING SUMMARY")
            appendLine("══════════════════════════════════════════")
            appendLine("  Total Epochs:       $totalEpochs")
            appendLine("  Converged:          $converged" + if (converged) " (epoch $convergenceEpoch)" else "")
            appendLine("  Final Train Loss:   ${"%.6f".format(finalTrainLoss)}")
            appendLine("  Final Val Loss:     ${"%.6f".format(finalValLoss)}")
            appendLine("  Best Val Loss:      ${"%.6f".format(bestValidationLoss)}")
            appendLine("  Final Train Acc:    ${"%.2f".format((history.trainAccuracies.lastOrNull() ?: 0.0) * 100)}%")
            appendLine("  Final Val Acc:      ${"%.2f".format((history.valAccuracies.lastOrNull() ?: 0.0) * 100)}%")
            appendLine("  Overfitting:        $isOverfitting")
            appendLine("  Parameters:         ${history.notes.firstOrNull()?.second ?: "N/A"}")
            appendLine("══════════════════════════════════════════")
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

/**
 * Tracks training metrics across epochs for convergence analysis and visualization.
 */
class TrainingHistory {
    private val _trainLosses = mutableListOf<Double>()
    private val _valLosses = mutableListOf<Double>()
    private val _trainAccuracies = mutableListOf<Double>()
    private val _valAccuracies = mutableListOf<Double>()
    private val _learningRates = mutableListOf<Double>()
    private val _gradientNorms = mutableListOf<Double>()
    private val _notes = mutableListOf<Pair<Int, String>>()

    val trainLosses: List<Double> get() = _trainLosses
    val valLosses: List<Double> get() = _valLosses
    val trainAccuracies: List<Double> get() = _trainAccuracies
    val valAccuracies: List<Double> get() = _valAccuracies
    val learningRates: List<Double> get() = _learningRates
    val gradientNorms: List<Double> get() = _gradientNorms
    val notes: List<Pair<Int, String>> get() = _notes
    val epochCount: Int get() = _trainLosses.size

    fun recordEpoch(
        epoch: Int,
        trainLoss: Double,
        valLoss: Double,
        trainAccuracy: Double,
        valAccuracy: Double,
        learningRate: Double,
        gradientNorm: Double
    ) {
        _trainLosses.add(trainLoss)
        _valLosses.add(valLoss)
        _trainAccuracies.add(trainAccuracy)
        _valAccuracies.add(valAccuracy)
        _learningRates.add(learningRate)
        _gradientNorms.add(gradientNorm)
    }

    fun addNote(epoch: Int, note: String) {
        try {
            _notes.add(Pair(epoch, note))
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    /** Get the epoch with the lowest validation loss. */
    fun bestEpoch(): Int = _valLosses.indexOf(_valLosses.minOrNull() ?: 0.0)

    /** Calculate the convergence rate (average loss reduction per epoch). */
    fun convergenceRate(): Double {
        try {
            if (_valLosses.size < 2) return 0.0
            val first = _valLosses.first()
            val last = _valLosses.last()
            return (first - last) / _valLosses.size
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    /** Detect if the model is in the overfitting regime (val loss increasing while train loss decreasing). */
    fun detectOverfittingOnset(): Int {
        try {
            if (_trainLosses.size < 5) return -1
            for (i in 4 until _trainLosses.size) {
                val trainTrend = _trainLosses[i] - _trainLosses[i - 4]
                val valTrend = _valLosses[i] - _valLosses[i - 4]
                if (trainTrend < 0 && valTrend > 0) return i
            }
            return -1
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    /** Get a snapshot of the last N epochs for quick inspection. */
    fun recentSnapshot(n: Int = 5): String = buildString {
        try {
            val start = max(0, epochCount - n)
            for (i in start until epochCount) {
                appendLine(
                    "  Epoch ${i + 1}: train=${"%.6f".format(_trainLosses[i])} " +
                    "val=${"%.6f".format(_valLosses[i])} " +
                    "acc=${"%.2f".format(_trainAccuracies[i] * 100)}% " +
                    "lr=${"%.2e".format(_learningRates[i])}"
                )
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}
