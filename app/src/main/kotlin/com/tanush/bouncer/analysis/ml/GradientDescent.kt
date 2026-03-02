package com.tanush.bouncer.analysis.ml

import kotlin.math.*
import io.sentry.Sentry

/**
 * Gradient descent optimizers for training the score prediction model.
 *
 * Each optimizer implements the [Optimizer] interface and manages its own
 * internal state (momentum, adaptive learning rates, etc.).
 *
 * Supported optimizers:
 *  - [SGD]: Vanilla stochastic gradient descent with optional momentum and Nesterov acceleration
 *  - [Adam]: Adaptive moment estimation (industry standard, robust default)
 *  - [AdaGrad]: Adaptive gradient with per-parameter learning rates
 *  - [RMSProp]: Root mean square propagation (good for non-stationary objectives)
 */
interface Optimizer {
    /** Current effective learning rate (may be adapted). */
    val currentLearningRate: Double

    /**
     * Update model parameters using computed gradients.
     *
     * @param model The model to update
     * @param gradients Per-layer gradients from backpropagation
     * @param l2Lambda Optional L2 regularization strength (weight decay)
     */
    fun step(model: ScorePredictionModel, gradients: List<LayerGradients>, l2Lambda: Double = 0.0)

    /**
     * Reset optimizer internal state (momentum buffers, etc.).
     * Call when restarting training or changing learning rate schedule.
     */
    fun reset()

    /** Get the current step count. */
    val stepCount: Long
}

// ═══════════════════════════════════════════════════════════════════
// SGD with Momentum
// ═══════════════════════════════════════════════════════════════════

/**
 * Stochastic Gradient Descent with momentum and optional Nesterov acceleration.
 *
 * Update rule (with momentum):
 *   vₜ = μ·vₜ₋₁ + ∂L/∂θ
 *   θₜ = θₜ₋₁ - lr·vₜ
 *
 * With Nesterov:
 *   vₜ = μ·vₜ₋₁ + ∂L/∂(θ - μ·vₜ₋₁)
 *   θₜ = θₜ₋₁ - lr·vₜ
 *
 * @property learningRate Initial learning rate (η)
 * @property momentum Momentum coefficient (μ), typically 0.9
 * @property nesterov Whether to use Nesterov accelerated gradient
 */
class SGD(
    private val learningRate: Double = 0.01,
    private val momentum: Double = 0.9,
    private val nesterov: Boolean = false
) : Optimizer {

    override val currentLearningRate: Double get() = learningRate
    override var stepCount: Long = 0L
        private set

    // Velocity buffers: one per layer → (weight velocities, bias velocities)
    private var velocities: MutableList<Pair<Array<DoubleArray>, DoubleArray>>? = null

    override fun step(model: ScorePredictionModel, gradients: List<LayerGradients>, l2Lambda: Double) {
        try {
            stepCount++

            // Lazy-initialize velocity buffers
            if (velocities == null) {
                velocities = gradients.map { grad ->
                    Pair(
                        Array(grad.weightGradients.size) { DoubleArray(grad.weightGradients[it].size) },
                        DoubleArray(grad.biasGradients.size)
                    )
                }.toMutableList()
            }

            for (layerIdx in gradients.indices) {
                val grad = gradients[layerIdx]
                val (weightVel, biasVel) = velocities!![layerIdx]

                // Update weight velocities and apply
                for (j in grad.weightGradients.indices) {
                    for (i in grad.weightGradients[j].indices) {
                        val g = grad.weightGradients[j][i]
                        weightVel[j][i] = momentum * weightVel[j][i] + g

                        val update = if (nesterov) {
                            momentum * weightVel[j][i] + g
                        } else {
                            weightVel[j][i]
                        }

                        grad.weightGradients[j][i] = update
                    }
                }

                // Update bias velocities and apply
                for (j in grad.biasGradients.indices) {
                    val g = grad.biasGradients[j]
                    biasVel[j] = momentum * biasVel[j] + g

                    val update = if (nesterov) {
                        momentum * biasVel[j] + g
                    } else {
                        biasVel[j]
                    }

                    grad.biasGradients[j] = update
                }
            }

            // Apply the (potentially momentum-modified) gradients
            model.applyGradients(gradients, learningRate)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    override fun reset() {
        try {
            velocities = null
            stepCount = 0L
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Adam Optimizer
// ═══════════════════════════════════════════════════════════════════

/**
 * Adam (Adaptive Moment Estimation) optimizer.
 *
 * Combines the benefits of AdaGrad (per-parameter learning rates) and
 * RMSProp (exponential moving average of squared gradients).
 *
 * Update rule:
 *   mₜ = β₁·mₜ₋₁ + (1-β₁)·gₜ           (first moment estimate)
 *   vₜ = β₂·vₜ₋₁ + (1-β₂)·gₜ²          (second moment estimate)
 *   m̂ₜ = mₜ / (1 - β₁ᵗ)                 (bias correction)
 *   v̂ₜ = vₜ / (1 - β₂ᵗ)                 (bias correction)
 *   θₜ = θₜ₋₁ - lr · m̂ₜ / (√v̂ₜ + ε)
 *
 * @property learningRate Initial learning rate (default: 0.001)
 * @property beta1 Exponential decay rate for 1st moment (default: 0.9)
 * @property beta2 Exponential decay rate for 2nd moment (default: 0.999)
 * @property epsilon Numerical stability constant (default: 1e-8)
 */
class Adam(
    private val learningRate: Double = 0.001,
    private val beta1: Double = 0.9,
    private val beta2: Double = 0.999,
    private val epsilon: Double = 1e-8
) : Optimizer {

    override var stepCount: Long = 0L
        private set

    override val currentLearningRate: Double
        get() {
            // Bias-corrected effective learning rate
            if (stepCount == 0L) return learningRate
            val correction = sqrt(1.0 - beta2.pow(stepCount.toDouble())) /
                    (1.0 - beta1.pow(stepCount.toDouble()))
            return learningRate * correction
        }

    // First moment (mean) buffers
    private var m: MutableList<Pair<Array<DoubleArray>, DoubleArray>>? = null
    // Second moment (uncentered variance) buffers
    private var v: MutableList<Pair<Array<DoubleArray>, DoubleArray>>? = null

    override fun step(model: ScorePredictionModel, gradients: List<LayerGradients>, l2Lambda: Double) {
        try {
            stepCount++

            // Lazy-initialize moment buffers
            if (m == null || v == null) {
                m = gradients.map { grad ->
                    Pair(
                        Array(grad.weightGradients.size) { DoubleArray(grad.weightGradients[it].size) },
                        DoubleArray(grad.biasGradients.size)
                    )
                }.toMutableList()
                v = gradients.map { grad ->
                    Pair(
                        Array(grad.weightGradients.size) { DoubleArray(grad.weightGradients[it].size) },
                        DoubleArray(grad.biasGradients.size)
                    )
                }.toMutableList()
            }

            // Bias correction factors
            val biasCorrection1 = 1.0 - beta1.pow(stepCount.toDouble())
            val biasCorrection2 = 1.0 - beta2.pow(stepCount.toDouble())

            for (layerIdx in gradients.indices) {
                val grad = gradients[layerIdx]
                val (mw, mb) = m!![layerIdx]
                val (vw, vb) = v!![layerIdx]

                // Update weight moments and compute adaptive updates
                for (j in grad.weightGradients.indices) {
                    for (i in grad.weightGradients[j].indices) {
                        val g = grad.weightGradients[j][i]

                        // Add L2 regularization gradient if specified
                        // L2 weight decay: adds 2λ·w to gradient
                        // (weight value not accessible here, so we skip — handled externally)

                        // Update biased first moment
                        mw[j][i] = beta1 * mw[j][i] + (1.0 - beta1) * g
                        // Update biased second moment
                        vw[j][i] = beta2 * vw[j][i] + (1.0 - beta2) * g * g

                        // Bias-corrected estimates
                        val mHat = mw[j][i] / biasCorrection1
                        val vHat = vw[j][i] / biasCorrection2

                        // Compute Adam update
                        grad.weightGradients[j][i] = mHat / (sqrt(vHat) + epsilon)
                    }
                }

                // Update bias moments
                for (j in grad.biasGradients.indices) {
                    val g = grad.biasGradients[j]

                    mb[j] = beta1 * mb[j] + (1.0 - beta1) * g
                    vb[j] = beta2 * vb[j] + (1.0 - beta2) * g * g

                    val mHat = mb[j] / biasCorrection1
                    val vHat = vb[j] / biasCorrection2

                    grad.biasGradients[j] = mHat / (sqrt(vHat) + epsilon)
                }
            }

            // Apply Adam-modified gradients
            model.applyGradients(gradients, learningRate)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    override fun reset() {
        try {
            m = null
            v = null
            stepCount = 0L
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Learning Rate Schedulers
// ═══════════════════════════════════════════════════════════════════

/**
 * Learning rate scheduling strategies for improved convergence.
 */
sealed class LearningRateScheduler {

    /**
     * Get the adjusted learning rate for the current epoch.
     *
     * @param baseLR The initial learning rate
     * @param epoch Current epoch number (0-indexed)
     * @param totalEpochs Total number of training epochs
     * @param currentLoss Current training loss
     * @return Adjusted learning rate
     */
    abstract fun getLearningRate(
        baseLR: Double,
        epoch: Int,
        totalEpochs: Int,
        currentLoss: Double = 0.0
    ): Double

    /**
     * Step decay: reduce LR by factor every N epochs.
     *
     *   lr = baseLR · dropFactor^⌊epoch / dropEvery⌋
     *
     * @property dropFactor Multiplicative decay factor (e.g., 0.5 = halve)
     * @property dropEvery Number of epochs between drops
     */
    class StepDecay(
        private val dropFactor: Double = 0.5,
        private val dropEvery: Int = 10
    ) : LearningRateScheduler() {
        override fun getLearningRate(baseLR: Double, epoch: Int, totalEpochs: Int, currentLoss: Double): Double {
            try {
                return baseLR * dropFactor.pow((epoch / dropEvery).toDouble())
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }
    }

    /**
     * Cosine annealing: smoothly decrease LR following a cosine curve.
     *
     *   lr = minLR + 0.5 · (baseLR - minLR) · (1 + cos(π · epoch / totalEpochs))
     *
     * Provides smooth warmdown, often leading to better final convergence.
     *
     * @property minLR Minimum learning rate floor
     */
    class CosineAnnealing(
        private val minLR: Double = 1e-6
    ) : LearningRateScheduler() {
        override fun getLearningRate(baseLR: Double, epoch: Int, totalEpochs: Int, currentLoss: Double): Double {
            try {
                return minLR + 0.5 * (baseLR - minLR) * (1.0 + cos(PI * epoch / totalEpochs))
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }
    }

    /**
     * Exponential decay: LR decays exponentially each epoch.
     *
     *   lr = baseLR · decayRate^epoch
     *
     * @property decayRate Decay rate per epoch (e.g., 0.95)
     */
    class ExponentialDecay(
        private val decayRate: Double = 0.95
    ) : LearningRateScheduler() {
        override fun getLearningRate(baseLR: Double, epoch: Int, totalEpochs: Int, currentLoss: Double): Double {
            try {
                return baseLR * decayRate.pow(epoch.toDouble())
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }
    }

    /**
     * Warmup + decay: linearly increase LR for warmup epochs, then decay.
     *
     * Phase 1 (warmup): lr linearly increases from 0 to baseLR
     * Phase 2 (decay): cosine annealing from baseLR to minLR
     *
     * @property warmupEpochs Number of warmup epochs
     * @property minLR Minimum learning rate after decay
     */
    class WarmupCosineDecay(
        private val warmupEpochs: Int = 5,
        private val minLR: Double = 1e-6
    ) : LearningRateScheduler() {
        override fun getLearningRate(baseLR: Double, epoch: Int, totalEpochs: Int, currentLoss: Double): Double {
            try {
                return if (epoch < warmupEpochs) {
                    // Linear warmup
                    baseLR * (epoch + 1).toDouble() / warmupEpochs
                } else {
                    // Cosine decay after warmup
                    val decayEpochs = totalEpochs - warmupEpochs
                    val decayEpoch = epoch - warmupEpochs
                    minLR + 0.5 * (baseLR - minLR) * (1.0 + cos(PI * decayEpoch / decayEpochs))
                }
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }
    }

    /**
     * Reduce on plateau: reduce LR when loss stops improving.
     *
     * @property factor Reduction factor when plateau detected
     * @property patience Number of epochs to wait before reducing
     * @property minLR Minimum learning rate floor
     */
    class ReduceOnPlateau(
        private val factor: Double = 0.5,
        private val patience: Int = 5,
        private val minLR: Double = 1e-6
    ) : LearningRateScheduler() {
        private var bestLoss: Double = Double.MAX_VALUE
        private var patienceCounter: Int = 0
        private var currentFactor: Double = 1.0

        override fun getLearningRate(baseLR: Double, epoch: Int, totalEpochs: Int, currentLoss: Double): Double {
            try {
                if (currentLoss < bestLoss) {
                    bestLoss = currentLoss
                    patienceCounter = 0
                } else {
                    patienceCounter++
                    if (patienceCounter >= patience) {
                        currentFactor *= factor
                        patienceCounter = 0
                    }
                }
                return max(baseLR * currentFactor, minLR)
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }
    }

    /** No scheduling — constant learning rate. */
    object None : LearningRateScheduler() {
        override fun getLearningRate(baseLR: Double, epoch: Int, totalEpochs: Int, currentLoss: Double): Double {
            try {
                return baseLR
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Gradient Clipping
// ═══════════════════════════════════════════════════════════════════

/**
 * Gradient clipping utilities to prevent exploding gradients.
 */
object GradientClipping {
    /**
     * Clip gradients by maximum norm (L2 norm).
     *
     * If ‖g‖ > maxNorm, scale g → g · (maxNorm / ‖g‖)
     *
     * @param gradients Layer gradients to clip
     * @param maxNorm Maximum allowed L2 norm
     * @return Clipped gradients and the original norm
     */
    fun clipByNorm(gradients: List<LayerGradients>, maxNorm: Double): Pair<List<LayerGradients>, Double> {
        try {
            // Compute total gradient L2 norm
            var totalNormSq = 0.0
            for (grad in gradients) {
                for (wg in grad.weightGradients) {
                    for (g in wg) totalNormSq += g * g
                }
                for (g in grad.biasGradients) totalNormSq += g * g
            }
            val totalNorm = sqrt(totalNormSq)

            if (totalNorm <= maxNorm) {
                return Pair(gradients, totalNorm)
            }

            // Scale down all gradients
            val scaleFactor = maxNorm / totalNorm
            val clipped = gradients.map { grad ->
                LayerGradients(
                    weightGradients = Array(grad.weightGradients.size) { j ->
                        DoubleArray(grad.weightGradients[j].size) { i ->
                            grad.weightGradients[j][i] * scaleFactor
                        }
                    },
                    biasGradients = DoubleArray(grad.biasGradients.size) { j ->
                        grad.biasGradients[j] * scaleFactor
                    },
                    inputGradient = grad.inputGradient // Not needed for clipping
                )
            }

            return Pair(clipped, totalNorm)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    /**
     * Clip gradients by value — cap each gradient element independently.
     *
     * @param gradients Layer gradients
     * @param maxValue Maximum absolute value for any single gradient
     * @return Clipped gradients
     */
    fun clipByValue(gradients: List<LayerGradients>, maxValue: Double): List<LayerGradients> {
        try {
            return gradients.map { grad ->
                LayerGradients(
                    weightGradients = Array(grad.weightGradients.size) { j ->
                        DoubleArray(grad.weightGradients[j].size) { i ->
                            grad.weightGradients[j][i].coerceIn(-maxValue, maxValue)
                        }
                    },
                    biasGradients = DoubleArray(grad.biasGradients.size) { j ->
                        grad.biasGradients[j].coerceIn(-maxValue, maxValue)
                    },
                    inputGradient = grad.inputGradient
                )
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}
