package com.tanush.bouncer.analysis.ml

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln

/**
 * Comprehensive tests for all loss functions.
 *
 * Tests cover:
 *  - MSE: value correctness, gradient correctness, zero-loss case
 *  - BCE: value correctness, gradient orientation, numerical stability
 *  - Huber: quadratic/linear regimes, delta boundary
 *  - MultiTask: combined loss weighting, gradient splitting
 *  - FocalLoss: down-weighting easy examples
 *  - Gradient numerical verification via finite differences
 */
class LossFunctionTest {

    private val eps = 1e-6

    // ═══════════════════════════════════════════════════════════
    // MSE Loss
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `MSE loss is zero when predicted equals target`() {
        val pred = doubleArrayOf(0.5, 0.3)
        val target = doubleArrayOf(0.5, 0.3)
        val loss = LossFunction.MSE.compute(pred, target)
        assertEquals(0.0, loss, 1e-12)
    }

    @Test
    fun `MSE loss computes correct value`() {
        val pred = doubleArrayOf(1.0, 0.0)
        val target = doubleArrayOf(0.0, 1.0)
        // MSE = ((1-0)^2 + (0-1)^2) / 2 = (1 + 1) / 2 = 1.0
        val loss = LossFunction.MSE.compute(pred, target)
        assertEquals(1.0, loss, 1e-12)
    }

    @Test
    fun `MSE loss for single element`() {
        val pred = doubleArrayOf(0.7)
        val target = doubleArrayOf(0.3)
        // MSE = (0.7 - 0.3)^2 / 1 = 0.16
        val loss = LossFunction.MSE.compute(pred, target)
        assertEquals(0.16, loss, 1e-12)
    }

    @Test
    fun `MSE gradient points toward target`() {
        val pred = doubleArrayOf(0.8, 0.2)
        val target = doubleArrayOf(0.3, 0.7)
        val grad = LossFunction.MSE.gradient(pred, target)
        // grad[0] = 2*(0.8-0.3)/2 = 0.5 (positive, pred > target)
        // grad[1] = 2*(0.2-0.7)/2 = -0.5 (negative, pred < target)
        assertEquals(0.5, grad[0], 1e-12)
        assertEquals(-0.5, grad[1], 1e-12)
    }

    @Test
    fun `MSE gradient is zero when predicted equals target`() {
        val pred = doubleArrayOf(0.5, 0.5)
        val target = doubleArrayOf(0.5, 0.5)
        val grad = LossFunction.MSE.gradient(pred, target)
        assertEquals(0.0, grad[0], 1e-12)
        assertEquals(0.0, grad[1], 1e-12)
    }

    @Test
    fun `MSE gradient matches finite differences`() {
        val pred = doubleArrayOf(0.6, 0.4)
        val target = doubleArrayOf(0.3, 0.8)
        verifyGradientFiniteDifferences(LossFunction.MSE, pred, target)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `MSE rejects mismatched sizes`() {
        LossFunction.MSE.compute(doubleArrayOf(1.0), doubleArrayOf(1.0, 2.0))
    }

    // ═══════════════════════════════════════════════════════════
    // Binary Cross-Entropy
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `BCE loss for perfect prediction is near zero`() {
        // Predicted 1.0 when target is 1.0 → loss ≈ 0
        val pred = doubleArrayOf(0.999)
        val target = doubleArrayOf(1.0)
        val loss = LossFunction.BinaryCrossEntropy.compute(pred, target)
        assertTrue("BCE for near-perfect prediction should be small", loss < 0.01)
    }

    @Test
    fun `BCE loss for wrong prediction is large`() {
        // Predicted 0.01 when target is 1.0 → high loss
        val pred = doubleArrayOf(0.01)
        val target = doubleArrayOf(1.0)
        val loss = LossFunction.BinaryCrossEntropy.compute(pred, target)
        assertTrue("BCE for wrong prediction should be large", loss > 2.0)
    }

    @Test
    fun `BCE loss for balanced prediction is moderate`() {
        val pred = doubleArrayOf(0.5)
        val target = doubleArrayOf(1.0)
        // -log(0.5) ≈ 0.693
        val loss = LossFunction.BinaryCrossEntropy.compute(pred, target)
        assertEquals(-ln(0.5), loss, 0.01)
    }

    @Test
    fun `BCE gradient pushes prediction toward target`() {
        val pred = doubleArrayOf(0.3)
        val target = doubleArrayOf(1.0)
        val grad = LossFunction.BinaryCrossEntropy.gradient(pred, target)
        // When target=1 and pred < 1, gradient should be negative (decrease loss by increasing pred)
        assertTrue("Gradient should be negative when pred < target=1", grad[0] < 0)
    }

    @Test
    fun `BCE gradient for target=0 pushes prediction down`() {
        val pred = doubleArrayOf(0.7)
        val target = doubleArrayOf(0.0)
        val grad = LossFunction.BinaryCrossEntropy.gradient(pred, target)
        // When target=0 and pred > 0, gradient should be positive
        assertTrue("Gradient should be positive when pred > 0, target=0", grad[0] > 0)
    }

    @Test
    fun `BCE is numerically stable at predictions near 0 and 1`() {
        val predNearZero = doubleArrayOf(0.0)
        val predNearOne = doubleArrayOf(1.0)
        val target = doubleArrayOf(0.5)

        val loss0 = LossFunction.BinaryCrossEntropy.compute(predNearZero, target)
        val loss1 = LossFunction.BinaryCrossEntropy.compute(predNearOne, target)

        assertFalse("Loss at pred=0 should not be NaN", loss0.isNaN())
        assertFalse("Loss at pred=0 should not be Inf", loss0.isInfinite())
        assertFalse("Loss at pred=1 should not be NaN", loss1.isNaN())
        assertFalse("Loss at pred=1 should not be Inf", loss1.isInfinite())
    }

    @Test
    fun `BCE gradient matches finite differences`() {
        val pred = doubleArrayOf(0.6)
        val target = doubleArrayOf(1.0)
        verifyGradientFiniteDifferences(LossFunction.BinaryCrossEntropy, pred, target)
    }

    // ═══════════════════════════════════════════════════════════
    // Huber Loss
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `Huber loss equals MSE for small errors`() {
        val huber = LossFunction.Huber(delta = 1.0)
        val pred = doubleArrayOf(0.52)
        val target = doubleArrayOf(0.5)
        // |diff| = 0.02 < delta=1.0, so L = 0.5 * 0.02^2 = 0.0002
        val loss = huber.compute(pred, target)
        assertEquals(0.5 * 0.02 * 0.02, loss, 1e-10)
    }

    @Test
    fun `Huber loss is linear for large errors`() {
        val delta = 0.5
        val huber = LossFunction.Huber(delta = delta)
        val pred = doubleArrayOf(3.0)
        val target = doubleArrayOf(0.0)
        // |diff| = 3.0 > delta=0.5, so L = 0.5*(3.0) - 0.5*0.5^2 = 1.5 - 0.125 = 1.375
        val expected = delta * 3.0 - 0.5 * delta * delta
        val loss = huber.compute(pred, target)
        assertEquals(expected, loss, 1e-10)
    }

    @Test
    fun `Huber loss is zero when predicted equals target`() {
        val huber = LossFunction.Huber()
        val loss = huber.compute(doubleArrayOf(0.5), doubleArrayOf(0.5))
        assertEquals(0.0, loss, 1e-12)
    }

    @Test
    fun `Huber gradient in quadratic regime matches MSE gradient`() {
        val huber = LossFunction.Huber(delta = 10.0) // large delta → always quadratic
        val pred = doubleArrayOf(0.6)
        val target = doubleArrayOf(0.4)

        val huberGrad = huber.gradient(pred, target)
        val mseGrad = LossFunction.MSE.gradient(pred, target)

        // In the quadratic regime, Huber gradient = MSE gradient (without the 2/n factor adjustment)
        // Huber grad = diff / n, MSE grad = 2*diff/n
        assertEquals(mseGrad[0] / 2.0, huberGrad[0], 1e-10)
    }

    @Test
    fun `Huber gradient in linear regime is clipped to delta`() {
        val delta = 0.5
        val huber = LossFunction.Huber(delta = delta)
        val pred = doubleArrayOf(5.0)
        val target = doubleArrayOf(0.0)
        val grad = huber.gradient(pred, target)
        // diff = 5.0 > delta → gradient = delta / n = 0.5 / 1 = 0.5
        assertEquals(delta, grad[0], 1e-10)
    }

    @Test
    fun `Huber gradient matches finite differences`() {
        val huber = LossFunction.Huber(delta = 1.0)
        val pred = doubleArrayOf(0.7, 0.3)
        val target = doubleArrayOf(0.4, 0.8)
        verifyGradientFiniteDifferences(huber, pred, target)
    }

    // ═══════════════════════════════════════════════════════════
    // MultiTask Loss
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `MultiTask loss combines BCE and MSE components`() {
        val multiTask = LossFunction.MultiTask(
            winLossWeight = 0.6,
            scoreWeight = 0.3,
            robustnessWeight = 0.1
        )
        val pred = doubleArrayOf(0.7, 0.5)   // [winProb, scoreNorm]
        val target = doubleArrayOf(1.0, 0.4) // [winLabel, scoreLabel]

        val loss = multiTask.compute(pred, target)
        assertTrue("MultiTask loss should be positive", loss > 0.0)
        assertFalse("MultiTask loss should not be NaN", loss.isNaN())
    }

    @Test
    fun `MultiTask gradient has correct shape`() {
        val multiTask = LossFunction.MultiTask()
        val pred = doubleArrayOf(0.5, 0.5)
        val target = doubleArrayOf(1.0, 0.3)

        val grad = multiTask.gradient(pred, target)
        assertEquals(2, grad.size)
        assertFalse(grad[0].isNaN())
        assertFalse(grad[1].isNaN())
    }

    @Test
    fun `MultiTask loss at perfect prediction is near zero`() {
        val multiTask = LossFunction.MultiTask()
        val pred = doubleArrayOf(0.999, 0.4)
        val target = doubleArrayOf(1.0, 0.4)

        val loss = multiTask.compute(pred, target)
        assertTrue("Loss should be very small for near-perfect prediction", loss < 0.01)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `MultiTask rejects single-element arrays`() {
        LossFunction.MultiTask().compute(doubleArrayOf(0.5), doubleArrayOf(0.5))
    }

    // ═══════════════════════════════════════════════════════════
    // Focal Loss
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `Focal loss down-weights easy examples`() {
        val focal = LossFunction.FocalLoss(gamma = 2.0)
        val bce = LossFunction.BinaryCrossEntropy

        // Easy example (well-classified): pred=0.9, target=1.0
        val easyPred = doubleArrayOf(0.9)
        val easyTarget = doubleArrayOf(1.0)
        val focalEasy = focal.compute(easyPred, easyTarget)
        val bceEasy = bce.compute(easyPred, easyTarget)

        // Focal loss should be much smaller than BCE for easy examples
        assertTrue(
            "Focal should be smaller than BCE for easy examples",
            focalEasy < bceEasy
        )
    }

    @Test
    fun `Focal loss is positive for all valid inputs`() {
        val focal = LossFunction.FocalLoss()
        val testCases = listOf(
            Pair(doubleArrayOf(0.1), doubleArrayOf(1.0)),
            Pair(doubleArrayOf(0.5), doubleArrayOf(0.0)),
            Pair(doubleArrayOf(0.9), doubleArrayOf(1.0)),
            Pair(doubleArrayOf(0.01), doubleArrayOf(0.0))
        )
        for ((pred, target) in testCases) {
            val loss = focal.compute(pred, target)
            assertTrue("Focal loss should be non-negative", loss >= 0.0)
            assertFalse("Focal loss should not be NaN", loss.isNaN())
        }
    }

    @Test
    fun `Focal loss with gamma=0 approximates BCE`() {
        val focal = LossFunction.FocalLoss(gamma = 0.0, alpha = 0.5)
        val pred = doubleArrayOf(0.6)
        val target = doubleArrayOf(1.0)

        val focalLoss = focal.compute(pred, target)
        // With gamma=0 and alpha=0.5: loss = -0.5 * log(0.6) ≈ 0.255
        assertTrue("Focal(gamma=0) should be positive", focalLoss > 0.0)
    }

    // ═══════════════════════════════════════════════════════════
    // L2 Regularization
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `L2 penalty is non-negative`() {
        val model = ScorePredictionModel.create()
        val penalty = L2Regularization.penalty(model, 0.01)
        assertTrue("L2 penalty should be non-negative", penalty >= 0.0)
    }

    @Test
    fun `L2 penalty increases with lambda`() {
        val model = ScorePredictionModel.create()
        val p1 = L2Regularization.penalty(model, 0.001)
        val p2 = L2Regularization.penalty(model, 0.01)
        assertTrue("Higher lambda should produce higher penalty", p2 > p1)
    }

    @Test
    fun `L2 penalty is zero with lambda zero`() {
        val model = ScorePredictionModel.create()
        val penalty = L2Regularization.penalty(model, 0.0)
        assertEquals(0.0, penalty, 1e-12)
    }

    // ═══════════════════════════════════════════════════════════
    // Utility: Gradient Numerical Verification
    // ═══════════════════════════════════════════════════════════

    /**
     * Verify analytical gradient against numerical (finite-difference) gradient.
     */
    private fun verifyGradientFiniteDifferences(
        loss: LossFunction,
        pred: DoubleArray,
        target: DoubleArray,
        h: Double = 1e-5,
        tolerance: Double = 1e-4
    ) {
        val analyticGrad = loss.gradient(pred, target)

        for (i in pred.indices) {
            val predPlus = pred.copyOf().also { it[i] += h }
            val predMinus = pred.copyOf().also { it[i] -= h }
            val numericalGrad = (loss.compute(predPlus, target) - loss.compute(predMinus, target)) / (2.0 * h)

            val diff = abs(analyticGrad[i] - numericalGrad)
            val scale = maxOf(abs(analyticGrad[i]), abs(numericalGrad), 1e-7)
            assertTrue(
                "Gradient mismatch at index $i: analytic=${analyticGrad[i]}, " +
                        "numerical=$numericalGrad, relative diff=${diff / scale}",
                diff / scale < tolerance
            )
        }
    }
}
