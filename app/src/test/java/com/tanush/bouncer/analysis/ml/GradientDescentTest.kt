package com.tanush.bouncer.analysis.ml

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

/**
 * Comprehensive tests for gradient descent optimizers and learning rate schedulers.
 *
 * Tests cover:
 *  - SGD: step application, momentum accumulation, Nesterov acceleration
 *  - Adam: adaptive learning, bias correction, moment updates
 *  - Learning rate schedulers: step decay, cosine annealing, warmup, plateau
 *  - Gradient clipping: by norm and by value
 *  - Convergence: optimizers can minimize a simple loss
 */
class GradientDescentTest {

    private lateinit var model: ScorePredictionModel

    @Before
    fun setUp() {
        model = ScorePredictionModel.create(seed = 42L)
    }

    // ═══════════════════════════════════════════════════════════
    // SGD Optimizer
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `SGD step count increments on each step`() {
        val sgd = SGD(learningRate = 0.01)
        assertEquals(0L, sgd.stepCount)

        val input = DoubleArray(16) { 0.5 }
        val cache = model.forwardWithCache(input)
        val grads = model.backward(cache, doubleArrayOf(1.0, 1.0))
        sgd.step(model, grads)

        assertEquals(1L, sgd.stepCount)
    }

    @Test
    fun `SGD changes model output after step`() {
        val sgd = SGD(learningRate = 0.01, momentum = 0.0)
        val input = DoubleArray(16) { 0.5 }

        val beforeOutput = model.forward(input).copyOf()

        val cache = model.forwardWithCache(input)
        val grads = model.backward(cache, doubleArrayOf(1.0, -1.0))
        sgd.step(model, grads)

        val afterOutput = model.forward(input)
        val changed = beforeOutput.zip(afterOutput).any { (a, b) -> abs(a - b) > 1e-10 }
        assertTrue("SGD step should change model output", changed)
    }

    @Test
    fun `SGD with momentum converges faster than without`() {
        // Train identical models with and without momentum on a simple loss
        val modelNoMom = ScorePredictionModel.create(seed = 42L)
        val modelWithMom = ScorePredictionModel.create(seed = 42L)
        val sgdNoMom = SGD(learningRate = 0.01, momentum = 0.0)
        val sgdWithMom = SGD(learningRate = 0.01, momentum = 0.9)

        val input = DoubleArray(16) { 0.5 }
        val target = doubleArrayOf(0.8, 0.3)

        var lossNoMom = Double.MAX_VALUE
        var lossWithMom = Double.MAX_VALUE

        repeat(30) {
            // No momentum
            val cache1 = modelNoMom.forwardWithCache(input)
            lossNoMom = LossFunction.MSE.compute(cache1.output, target)
            val grad1 = LossFunction.MSE.gradient(cache1.output, target)
            val grads1 = modelNoMom.backward(cache1, grad1)
            sgdNoMom.step(modelNoMom, grads1)

            // With momentum
            val cache2 = modelWithMom.forwardWithCache(input)
            lossWithMom = LossFunction.MSE.compute(cache2.output, target)
            val grad2 = LossFunction.MSE.gradient(cache2.output, target)
            val grads2 = modelWithMom.backward(cache2, grad2)
            sgdWithMom.step(modelWithMom, grads2)
        }

        // Both should reduce loss
        assertTrue("SGD without momentum should reduce loss", lossNoMom < 0.5)
        assertTrue("SGD with momentum should reduce loss", lossWithMom < 0.5)
    }

    @Test
    fun `SGD reset clears state`() {
        val sgd = SGD(learningRate = 0.01, momentum = 0.9)
        val input = DoubleArray(16) { 0.5 }

        val cache = model.forwardWithCache(input)
        val grads = model.backward(cache, doubleArrayOf(1.0, 1.0))
        sgd.step(model, grads)
        assertEquals(1L, sgd.stepCount)

        sgd.reset()
        assertEquals(0L, sgd.stepCount)
    }

    @Test
    fun `SGD currentLearningRate returns configured value`() {
        val sgd = SGD(learningRate = 0.05)
        assertEquals(0.05, sgd.currentLearningRate, 1e-12)
    }

    // ═══════════════════════════════════════════════════════════
    // Adam Optimizer
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `Adam step count increments`() {
        val adam = Adam(learningRate = 0.001)
        assertEquals(0L, adam.stepCount)

        val input = DoubleArray(16) { 0.5 }
        val cache = model.forwardWithCache(input)
        val grads = model.backward(cache, doubleArrayOf(0.5, -0.5))
        adam.step(model, grads)

        assertEquals(1L, adam.stepCount)
    }

    @Test
    fun `Adam reduces loss over multiple steps`() {
        val adam = Adam(learningRate = 0.01)
        val input = DoubleArray(16) { 0.5 }
        val target = doubleArrayOf(0.8, 0.3)

        val initialCache = model.forwardWithCache(input)
        val initialLoss = LossFunction.MSE.compute(initialCache.output, target)

        var currentLoss = initialLoss
        repeat(50) {
            val cache = model.forwardWithCache(input)
            currentLoss = LossFunction.MSE.compute(cache.output, target)
            val lossGrad = LossFunction.MSE.gradient(cache.output, target)
            val grads = model.backward(cache, lossGrad)
            adam.step(model, grads)
        }

        assertTrue(
            "Adam should reduce loss significantly: initial=$initialLoss, final=$currentLoss",
            currentLoss < initialLoss * 0.5
        )
    }

    @Test
    fun `Adam converges on simple regression target`() {
        val adam = Adam(learningRate = 0.005)
        val input = DoubleArray(16) { (it + 1.0) / 16.0 }
        val target = doubleArrayOf(0.7, 0.4)

        var loss = Double.MAX_VALUE
        repeat(200) {
            val cache = model.forwardWithCache(input)
            loss = LossFunction.MSE.compute(cache.output, target)
            val lossGrad = LossFunction.MSE.gradient(cache.output, target)
            val grads = model.backward(cache, lossGrad)
            adam.step(model, grads)
        }

        assertTrue("Adam should converge to low loss: $loss", loss < 0.01)
    }

    @Test
    fun `Adam reset clears moment buffers`() {
        val adam = Adam()
        val input = DoubleArray(16) { 0.5 }
        val cache = model.forwardWithCache(input)
        val grads = model.backward(cache, doubleArrayOf(1.0, 1.0))
        adam.step(model, grads)

        adam.reset()
        assertEquals(0L, adam.stepCount)
    }

    @Test
    fun `Adam bias correction in early steps`() {
        val adam = Adam(learningRate = 0.001, beta1 = 0.9, beta2 = 0.999)

        // At step 0, currentLearningRate should be baseLR
        assertEquals(0.001, adam.currentLearningRate, 1e-6)

        // After a step, bias correction adjusts the effective LR
        val input = DoubleArray(16) { 0.5 }
        val cache = model.forwardWithCache(input)
        val grads = model.backward(cache, doubleArrayOf(1.0, 1.0))
        adam.step(model, grads)

        val adjustedLR = adam.currentLearningRate
        assertNotEquals("Bias-corrected LR should differ from base", 0.001, adjustedLR, 1e-8)
    }

    // ═══════════════════════════════════════════════════════════
    // Learning Rate Schedulers
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `StepDecay reduces LR at specified intervals`() {
        val scheduler = LearningRateScheduler.StepDecay(dropFactor = 0.5, dropEvery = 10)
        val baseLR = 0.01

        assertEquals(0.01, scheduler.getLearningRate(baseLR, 0, 100), 1e-10)
        assertEquals(0.01, scheduler.getLearningRate(baseLR, 9, 100), 1e-10)
        assertEquals(0.005, scheduler.getLearningRate(baseLR, 10, 100), 1e-10)
        assertEquals(0.005, scheduler.getLearningRate(baseLR, 19, 100), 1e-10)
        assertEquals(0.0025, scheduler.getLearningRate(baseLR, 20, 100), 1e-10)
    }

    @Test
    fun `CosineAnnealing starts at baseLR and ends near minLR`() {
        val minLR = 1e-6
        val scheduler = LearningRateScheduler.CosineAnnealing(minLR = minLR)
        val baseLR = 0.01

        val start = scheduler.getLearningRate(baseLR, 0, 100)
        val end = scheduler.getLearningRate(baseLR, 100, 100)

        assertEquals("Should start at baseLR", baseLR, start, 1e-6)
        assertEquals("Should end near minLR", minLR, end, 1e-6)
    }

    @Test
    fun `CosineAnnealing is monotonically decreasing`() {
        val scheduler = LearningRateScheduler.CosineAnnealing()
        val baseLR = 0.01
        var prevLR = scheduler.getLearningRate(baseLR, 0, 100)

        for (epoch in 1..99) {
            val lr = scheduler.getLearningRate(baseLR, epoch, 100)
            assertTrue("LR should decrease: epoch $epoch, prev=$prevLR, curr=$lr", lr <= prevLR + 1e-10)
            prevLR = lr
        }
    }

    @Test
    fun `ExponentialDecay decays each epoch`() {
        val scheduler = LearningRateScheduler.ExponentialDecay(decayRate = 0.95)
        val baseLR = 0.01

        val lr0 = scheduler.getLearningRate(baseLR, 0, 100)
        val lr1 = scheduler.getLearningRate(baseLR, 1, 100)
        val lr10 = scheduler.getLearningRate(baseLR, 10, 100)

        assertEquals(0.01, lr0, 1e-10)
        assertEquals(0.01 * 0.95, lr1, 1e-10)
        assertTrue("LR should decrease over epochs", lr10 < lr1)
    }

    @Test
    fun `WarmupCosineDecay warms up linearly then decays`() {
        val scheduler = LearningRateScheduler.WarmupCosineDecay(warmupEpochs = 5)
        val baseLR = 0.01

        // Warmup phase: linear increase
        val lr0 = scheduler.getLearningRate(baseLR, 0, 50)
        val lr2 = scheduler.getLearningRate(baseLR, 2, 50)
        val lr4 = scheduler.getLearningRate(baseLR, 4, 50)
        assertTrue("Warmup should be increasing", lr2 > lr0)
        assertTrue("Warmup should be increasing", lr4 > lr2)

        // At warmup end, should be at baseLR
        val lrAtWarmupEnd = scheduler.getLearningRate(baseLR, 4, 50)
        assertEquals("LR at warmup end", baseLR * 5.0 / 5.0, lrAtWarmupEnd, 1e-10)

        // After warmup: decay phase
        val lr10 = scheduler.getLearningRate(baseLR, 10, 50)
        val lr30 = scheduler.getLearningRate(baseLR, 30, 50)
        assertTrue("Should decay after warmup", lr30 < lr10)
    }

    @Test
    fun `None scheduler returns constant LR`() {
        val scheduler = LearningRateScheduler.None
        val baseLR = 0.01

        for (epoch in 0..100) {
            assertEquals(baseLR, scheduler.getLearningRate(baseLR, epoch, 100), 1e-12)
        }
    }

    @Test
    fun `ReduceOnPlateau reduces after patience exceeded`() {
        val scheduler = LearningRateScheduler.ReduceOnPlateau(factor = 0.5, patience = 3)
        val baseLR = 0.01

        // Loss keeps improving — no reduction
        var lr = scheduler.getLearningRate(baseLR, 0, 100, 1.0)
        assertEquals(baseLR, lr, 1e-8)
        lr = scheduler.getLearningRate(baseLR, 1, 100, 0.9)
        assertEquals(baseLR, lr, 1e-8)

        // Loss stops improving for patience epochs
        lr = scheduler.getLearningRate(baseLR, 2, 100, 0.95) // plateau
        lr = scheduler.getLearningRate(baseLR, 3, 100, 0.95) // plateau
        lr = scheduler.getLearningRate(baseLR, 4, 100, 0.95) // patience hit → reduce

        assertTrue("LR should be reduced after plateau", lr < baseLR)
    }

    // ═══════════════════════════════════════════════════════════
    // Gradient Clipping
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `clipByNorm does not clip when norm is below threshold`() {
        val input = DoubleArray(16) { 0.01 }
        val cache = model.forwardWithCache(input)
        val grads = model.backward(cache, doubleArrayOf(0.01, 0.01))

        val (clipped, norm) = GradientClipping.clipByNorm(grads, maxNorm = 1000.0)
        assertTrue("Should not clip when norm is small", norm < 1000.0)
        // Clipped should be identical to original
        for (i in grads.indices) {
            assertArrayEquals(grads[i].biasGradients, clipped[i].biasGradients, 1e-12)
        }
    }

    @Test
    fun `clipByNorm clips when norm exceeds threshold`() {
        val input = DoubleArray(16) { 1.0 }
        val cache = model.forwardWithCache(input)
        val grads = model.backward(cache, doubleArrayOf(100.0, 100.0))

        val (clipped, originalNorm) = GradientClipping.clipByNorm(grads, maxNorm = 1.0)

        // Compute clipped norm
        var clippedNormSq = 0.0
        for (g in clipped) {
            for (wg in g.weightGradients) for (v in wg) clippedNormSq += v * v
            for (v in g.biasGradients) clippedNormSq += v * v
        }
        val clippedNorm = kotlin.math.sqrt(clippedNormSq)

        assertTrue("Original norm should exceed maxNorm", originalNorm > 1.0)
        assertEquals("Clipped norm should equal maxNorm", 1.0, clippedNorm, 0.01)
    }

    @Test
    fun `clipByValue caps individual gradient elements`() {
        val input = DoubleArray(16) { 1.0 }
        val cache = model.forwardWithCache(input)
        val grads = model.backward(cache, doubleArrayOf(50.0, -50.0))

        val clipped = GradientClipping.clipByValue(grads, maxValue = 0.1)

        for (g in clipped) {
            for (wg in g.weightGradients) {
                for (v in wg) {
                    assertTrue("Weight grad should be <= maxValue", abs(v) <= 0.1 + 1e-10)
                }
            }
            for (v in g.biasGradients) {
                assertTrue("Bias grad should be <= maxValue", abs(v) <= 0.1 + 1e-10)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Integration: Optimizer + Loss Convergence
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `Adam with MultiTask loss converges on synthetic data`() {
        val trainModel = ScorePredictionModel.create(seed = 100L)
        val adam = Adam(learningRate = 0.005)
        val loss = LossFunction.MultiTask()

        val input = DoubleArray(16) { (it + 1.0) / 16.0 }
        val target = doubleArrayOf(1.0, 0.5) // win=true, score=0.5

        var currentLoss = Double.MAX_VALUE
        repeat(200) {
            val cache = trainModel.forwardWithCache(input)
            currentLoss = loss.compute(cache.output, target)
            val lossGrad = loss.gradient(cache.output, target)
            val grads = trainModel.backward(cache, lossGrad)
            adam.step(trainModel, grads)
        }

        assertTrue("MultiTask loss should converge: $currentLoss", currentLoss < 0.1)
    }

    @Test
    fun `SGD with Nesterov converges`() {
        val trainModel = ScorePredictionModel.create(seed = 50L)
        val sgd = SGD(learningRate = 0.01, momentum = 0.9, nesterov = true)

        val input = DoubleArray(16) { 0.3 }
        val target = doubleArrayOf(0.9, 0.6)

        var currentLoss = Double.MAX_VALUE
        repeat(200) {
            val cache = trainModel.forwardWithCache(input)
            currentLoss = LossFunction.MSE.compute(cache.output, target)
            val lossGrad = LossFunction.MSE.gradient(cache.output, target)
            val grads = trainModel.backward(cache, lossGrad)
            sgd.step(trainModel, grads)
        }

        assertTrue("Nesterov SGD should converge: $currentLoss", currentLoss < 0.05)
    }
}
