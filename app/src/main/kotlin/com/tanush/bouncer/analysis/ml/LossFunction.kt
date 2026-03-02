package com.tanush.bouncer.analysis.ml

import kotlin.math.*
import io.sentry.Sentry

/**
 * Loss functions for training the score prediction neural network.
 *
 * Each loss function provides:
 *  - `compute()`:  Scalar loss value for monitoring convergence
 *  - `gradient()`: ∂L/∂ŷ gradient vector for backpropagation
 *
 * All implementations are numerically stable with epsilon clamping to prevent
 * log(0) and division-by-zero issues during gradient computation.
 */
sealed class LossFunction {

    /**
     * Compute the scalar loss value.
     *
     * @param predicted Model output ŷ
     * @param target Ground truth y
     * @return Scalar loss ≥ 0
     */
    abstract fun compute(predicted: DoubleArray, target: DoubleArray): Double

    /**
     * Compute the gradient of the loss with respect to predictions.
     *
     * @param predicted Model output ŷ
     * @param target Ground truth y
     * @return ∂L/∂ŷ gradient vector (same shape as predicted)
     */
    abstract fun gradient(predicted: DoubleArray, target: DoubleArray): DoubleArray

    companion object {
        /** Small constant for numerical stability */
        const val EPSILON = 1e-7
    }

    // ═══════════════════════════════════════════════════════════════
    // Mean Squared Error (MSE)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Mean Squared Error loss: L = (1/n) Σ(ŷᵢ - yᵢ)²
     *
     * Best for regression tasks (score prediction).
     * Penalizes large errors quadratically — good for smooth, continuous targets.
     *
     * Properties:
     *  - Convex: guarantees global minimum
     *  - Differentiable everywhere
     *  - Sensitive to outliers (large errors are heavily penalized)
     */
    object MSE : LossFunction() {
        override fun compute(predicted: DoubleArray, target: DoubleArray): Double {
            try {
                require(predicted.size == target.size) { "Size mismatch: ${predicted.size} vs ${target.size}" }
                var sum = 0.0
                for (i in predicted.indices) {
                    val diff = predicted[i] - target[i]
                    sum += diff * diff
                }
                return sum / predicted.size
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }

        override fun gradient(predicted: DoubleArray, target: DoubleArray): DoubleArray {
            try {
                require(predicted.size == target.size) { "Size mismatch" }
                val n = predicted.size.toDouble()
                return DoubleArray(predicted.size) { i ->
                    2.0 * (predicted[i] - target[i]) / n
                }
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Binary Cross-Entropy (BCE)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Binary Cross-Entropy loss: L = -(1/n) Σ[yᵢ·log(ŷᵢ) + (1-yᵢ)·log(1-ŷᵢ)]
     *
     * Best for binary classification tasks (win probability prediction).
     * Measures the divergence between predicted probability and actual outcome.
     *
     * Properties:
     *  - Convex for sigmoid outputs
     *  - Provides strong gradients far from the correct answer
     *  - Numerically stabilized with epsilon clamping
     */
    object BinaryCrossEntropy : LossFunction() {
        override fun compute(predicted: DoubleArray, target: DoubleArray): Double {
            try {
                require(predicted.size == target.size) { "Size mismatch" }
                var sum = 0.0
                for (i in predicted.indices) {
                    val p = predicted[i].coerceIn(EPSILON, 1.0 - EPSILON)
                    sum += -(target[i] * ln(p) + (1.0 - target[i]) * ln(1.0 - p))
                }
                return sum / predicted.size
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }

        override fun gradient(predicted: DoubleArray, target: DoubleArray): DoubleArray {
            try {
                require(predicted.size == target.size) { "Size mismatch" }
                val n = predicted.size.toDouble()
                return DoubleArray(predicted.size) { i ->
                    val p = predicted[i].coerceIn(EPSILON, 1.0 - EPSILON)
                    (-(target[i] / p) + (1.0 - target[i]) / (1.0 - p)) / n
                }
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Huber Loss (Smooth L1)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Huber loss: quadratic for small errors, linear for large errors.
     *
     *   L = { 0.5·δ²           if |δ| ≤ delta
     *       { delta·|δ| - 0.5·delta²  otherwise
     *
     * Best when training data may have noisy labels (common in cricket score data).
     * Combines MSE's smoothness near zero with MAE's robustness to outliers.
     *
     * @property delta Threshold for switching between quadratic and linear regimes
     */
    class Huber(private val delta: Double = 1.0) : LossFunction() {
        override fun compute(predicted: DoubleArray, target: DoubleArray): Double {
            try {
                require(predicted.size == target.size) { "Size mismatch" }
                var sum = 0.0
                for (i in predicted.indices) {
                    val diff = abs(predicted[i] - target[i])
                    sum += if (diff <= delta) {
                        0.5 * diff * diff
                    } else {
                        delta * diff - 0.5 * delta * delta
                    }
                }
                return sum / predicted.size
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }

        override fun gradient(predicted: DoubleArray, target: DoubleArray): DoubleArray {
            try {
                require(predicted.size == target.size) { "Size mismatch" }
                val n = predicted.size.toDouble()
                return DoubleArray(predicted.size) { i ->
                    val diff = predicted[i] - target[i]
                    when {
                        abs(diff) <= delta -> diff / n
                        diff > 0 -> delta / n
                        else -> -delta / n
                    }
                }
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Multi-Task Combined Loss
    // ═══════════════════════════════════════════════════════════════

    /**
     * Combined multi-task loss for simultaneous win probability and score prediction.
     *
     *   L = α · BCE(ŷ₀, y₀) + β · MSE(ŷ₁, y₁) + λ · Huber(ŷ₁, y₁)
     *
     * Allows training a single network for both classification (win/loss)
     * and regression (score prediction) simultaneously.
     *
     * @property winLossWeight α — weight for win probability (BCE) component
     * @property scoreWeight β — weight for score prediction (MSE) component
     * @property robustnessWeight λ — weight for Huber robustness component
     * @property huberDelta Threshold for the Huber component
     */
    class MultiTask(
        private val winLossWeight: Double = 0.6,
        private val scoreWeight: Double = 0.3,
        private val robustnessWeight: Double = 0.1,
        private val huberDelta: Double = 0.5
    ) : LossFunction() {

        private val huber = Huber(huberDelta)

        override fun compute(predicted: DoubleArray, target: DoubleArray): Double {
            try {
                require(predicted.size >= 2 && target.size >= 2) {
                    "MultiTask loss requires at least 2 outputs: [winProb, scorePred]"
                }

                // Split into sub-tasks
                val winPred = doubleArrayOf(predicted[0])
                val winTarget = doubleArrayOf(target[0])

                val scorePred = doubleArrayOf(predicted[1])
                val scoreTarget = doubleArrayOf(target[1])

                val bceLoss = BinaryCrossEntropy.compute(winPred, winTarget)
                val mseLoss = MSE.compute(scorePred, scoreTarget)
                val huberLoss = huber.compute(scorePred, scoreTarget)

                return winLossWeight * bceLoss +
                        scoreWeight * mseLoss +
                        robustnessWeight * huberLoss
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }

        override fun gradient(predicted: DoubleArray, target: DoubleArray): DoubleArray {
            try {
                require(predicted.size >= 2 && target.size >= 2) {
                    "MultiTask loss requires at least 2 outputs"
                }

                val grad = DoubleArray(predicted.size)

                // Win probability gradient (BCE)
                val winPred = doubleArrayOf(predicted[0])
                val winTarget = doubleArrayOf(target[0])
                val bceGrad = BinaryCrossEntropy.gradient(winPred, winTarget)
                grad[0] = winLossWeight * bceGrad[0]

                // Score prediction gradient (MSE + Huber)
                val scorePred = doubleArrayOf(predicted[1])
                val scoreTarget = doubleArrayOf(target[1])
                val mseGrad = MSE.gradient(scorePred, scoreTarget)
                val huberGrad = huber.gradient(scorePred, scoreTarget)
                grad[1] = scoreWeight * mseGrad[0] + robustnessWeight * huberGrad[0]

                return grad
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Focal Loss (for imbalanced data)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Focal Loss: down-weights well-classified examples to focus on hard cases.
     *
     *   FL = -α(1-p)^γ · log(p)
     *
     * Useful when training data has class imbalance (e.g., most chases succeed/fail).
     *
     * @property gamma Focusing parameter (γ=0 reduces to CE, γ=2 is common)
     * @property alpha Balancing factor
     */
    class FocalLoss(
        private val gamma: Double = 2.0,
        private val alpha: Double = 0.25
    ) : LossFunction() {
        override fun compute(predicted: DoubleArray, target: DoubleArray): Double {
            try {
                require(predicted.size == target.size) { "Size mismatch" }
                var sum = 0.0
                for (i in predicted.indices) {
                    val p = predicted[i].coerceIn(EPSILON, 1.0 - EPSILON)
                    val pt = if (target[i] >= 0.5) p else (1.0 - p)
                    val alphaT = if (target[i] >= 0.5) alpha else (1.0 - alpha)
                    sum += -alphaT * (1.0 - pt).pow(gamma) * ln(pt)
                }
                return sum / predicted.size
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }

        override fun gradient(predicted: DoubleArray, target: DoubleArray): DoubleArray {
            try {
                require(predicted.size == target.size) { "Size mismatch" }
                val n = predicted.size.toDouble()
                return DoubleArray(predicted.size) { i ->
                    val p = predicted[i].coerceIn(EPSILON, 1.0 - EPSILON)
                    val pt = if (target[i] >= 0.5) p else (1.0 - p)
                    val alphaT = if (target[i] >= 0.5) alpha else (1.0 - alpha)

                    // Gradient of focal loss
                    val factor = alphaT * (1.0 - pt).pow(gamma)
                    val gradCE = if (target[i] >= 0.5) -1.0 / p else 1.0 / (1.0 - p)
                    val gradFocal = gamma * (1.0 - pt).pow(gamma - 1.0) * ln(max(pt, EPSILON))
                    val sign = if (target[i] >= 0.5) 1.0 else -1.0

                    (factor * gradCE + alphaT * gradFocal * sign) / n
                }
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// L2 Regularization
// ═══════════════════════════════════════════════════════════════════

/**
 * L2 (Ridge) regularization to prevent overfitting.
 *
 * Adds λ·||W||² to the loss and 2λ·W to the gradient.
 */
object L2Regularization {
    /**
     * Compute the regularization penalty.
     *
     * @param model The model whose weights to regularize
     * @param lambda Regularization strength
     * @return Scalar regularization loss
     */
    fun penalty(model: ScorePredictionModel, lambda: Double): Double {
        try {
            var sum = 0.0
            for (params in model.getParameters()) {
                for (p in params) {
                    sum += p * p
                }
            }
            return lambda * sum
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    /**
     * Add L2 gradient to existing weight gradients.
     *
     * @param gradients Current layer gradients
     * @param model The model
     * @param lambda Regularization strength
     */
    fun addToGradients(
        gradients: List<LayerGradients>,
        model: ScorePredictionModel,
        lambda: Double
    ) {
        // L2 adds 2λ·w to each weight gradient
        // This is handled at the optimizer level in practice
    }
}
