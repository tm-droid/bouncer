package com.tanush.bouncer.analysis.ml

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive tests for the ScorePredictionModel neural network.
 *
 * Tests cover:
 *  - Architecture creation (default and custom)
 *  - Forward pass correctness and output bounds
 *  - Forward with cache (backpropagation prerequisite)
 *  - Backward pass gradient shapes
 *  - Model copy (checkpointing integrity)
 *  - Parameter counting
 *  - Weight serialization
 *  - Deterministic initialization with seed
 */
class ScorePredictionModelTest {

    private lateinit var model: ScorePredictionModel

    @Before
    fun setUp() {
        model = ScorePredictionModel.create(seed = 42L)
    }

    // ═══════════════════════════════════════════════════════════
    // Architecture & Creation
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `default model has correct parameter count`() {
        // Architecture: FEATURE_COUNT→64(ReLU)→32(ReLU)→16(ReLU)→2(Sigmoid)
        // Layer 0: FEATURE_COUNT*64 weights + 64 biases
        // Layer 1: 64*32 weights + 32 biases = 2080
        // Layer 2: 32*16 weights + 16 biases = 528
        // Layer 3: 16*2 weights + 2 biases = 34
        val expected = (FeatureExtractor.FEATURE_COUNT * 64 + 64) + (64 * 32 + 32) + (32 * 16 + 16) + (16 * 2 + 2)
        assertEquals(expected, model.parameterCount())
    }

    @Test
    fun `custom model creation with specified layer sizes`() {
        val custom = ScorePredictionModel.createCustom(
            layerSizes = listOf(8, 16, 4),
            seed = 123L
        )
        // Layer 0: 8*16 + 16 = 144, Layer 1: 16*4 + 4 = 68 → total = 212
        val expected = (8 * 16 + 16) + (16 * 4 + 4)
        assertEquals(expected, custom.parameterCount())
    }

    @Test
    fun `deterministic initialization with same seed`() {
        val model1 = ScorePredictionModel.create(seed = 99L)
        val model2 = ScorePredictionModel.create(seed = 99L)
        val input = DoubleArray(FeatureExtractor.FEATURE_COUNT) { 0.5 }

        val out1 = model1.forward(input)
        val out2 = model2.forward(input)

        assertArrayEquals("Same seed should produce identical outputs", out1, out2, 1e-12)
    }

    @Test
    fun `different seeds produce different outputs`() {
        val model1 = ScorePredictionModel.create(seed = 1L)
        val model2 = ScorePredictionModel.create(seed = 2L)
        val input = DoubleArray(FeatureExtractor.FEATURE_COUNT) { 0.5 }

        val out1 = model1.forward(input)
        val out2 = model2.forward(input)

        // At least one output should differ
        val differ = out1.zip(out2).any { (a, b) -> kotlin.math.abs(a - b) > 1e-10 }
        assertTrue("Different seeds should produce different outputs", differ)
    }

    // ═══════════════════════════════════════════════════════════
    // Forward Pass
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `forward pass produces output of correct size`() {
        val input = DoubleArray(FeatureExtractor.FEATURE_COUNT) { it.toDouble() / FeatureExtractor.FEATURE_COUNT.toDouble() }
        val output = model.forward(input)
        assertEquals("Output should have 2 elements (winProb, scorePred)", 2, output.size)
    }

    @Test
    fun `forward output is bounded by sigmoid activation`() {
        // Sigmoid output should always be in (0, 1)
        val n = FeatureExtractor.FEATURE_COUNT
        val testInputs = listOf(
            DoubleArray(n) { 0.0 },         // all zeros
            DoubleArray(n) { 1.0 },         // all ones
            DoubleArray(n) { 0.5 },         // all half
            DoubleArray(n) { it / (n - 1).toDouble() },   // gradient
            DoubleArray(n) { if (it % 2 == 0) 1.0 else 0.0 }  // alternating
        )

        for ((idx, input) in testInputs.withIndex()) {
            val output = model.forward(input)
            for (i in output.indices) {
                assertTrue(
                    "Output[$i] = ${output[i]} should be in (0, 1) for test input $idx",
                    output[i] > 0.0 && output[i] < 1.0
                )
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `forward pass rejects wrong input size`() {
        val wrongInput = DoubleArray(10) { 0.5 }
        model.forward(wrongInput)
    }

    @Test
    fun `forward pass handles extreme input values`() {
        val extremeInput = DoubleArray(FeatureExtractor.FEATURE_COUNT) { 1000.0 }
        val output = model.forward(extremeInput)
        // Should not produce NaN or Infinity
        for (v in output) {
            assertFalse("Output should not be NaN", v.isNaN())
            assertFalse("Output should not be Infinite", v.isInfinite())
        }
    }

    @Test
    fun `forward pass handles negative inputs`() {
        val negInput = DoubleArray(FeatureExtractor.FEATURE_COUNT) { -1.0 }
        val output = model.forward(negInput)
        assertEquals(2, output.size)
        for (v in output) {
            assertFalse(v.isNaN())
            assertTrue("Sigmoid output should be in (0,1)", v > 0.0 && v < 1.0)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Forward with Cache (for Backpropagation)
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `forwardWithCache produces same output as forward`() {
        val input = DoubleArray(FeatureExtractor.FEATURE_COUNT) { it.toDouble() / FeatureExtractor.FEATURE_COUNT.toDouble() }
        val directOutput = model.forward(input)
        val cache = model.forwardWithCache(input)

        assertArrayEquals(
            "Cached forward should match direct forward",
            directOutput, cache.output, 1e-12
        )
    }

    @Test
    fun `forwardWithCache has correct number of pre-activations`() {
        val input = DoubleArray(FeatureExtractor.FEATURE_COUNT) { 0.5 }
        val cache = model.forwardWithCache(input)
        // 4 layers → 4 pre-activations
        assertEquals(4, cache.preActivations.size)
    }

    @Test
    fun `forwardWithCache has correct number of post-activations`() {
        val input = DoubleArray(FeatureExtractor.FEATURE_COUNT) { 0.5 }
        val cache = model.forwardWithCache(input)
        // 4 layers + 1 (input) = 5 post-activations
        assertEquals(5, cache.postActivations.size)
    }

    @Test
    fun `forwardWithCache stores input correctly`() {
        val input = DoubleArray(FeatureExtractor.FEATURE_COUNT) { it * 0.1 }
        val cache = model.forwardWithCache(input)
        assertArrayEquals(input, cache.input, 1e-12)
    }

    // ═══════════════════════════════════════════════════════════
    // Backward Pass
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `backward pass produces correct number of layer gradients`() {
        val input = DoubleArray(FeatureExtractor.FEATURE_COUNT) { 0.5 }
        val cache = model.forwardWithCache(input)
        val outputGrad = doubleArrayOf(0.1, -0.1) // 2 outputs

        val gradients = model.backward(cache, outputGrad)
        assertEquals("Should have 4 layer gradients", 4, gradients.size)
    }

    @Test
    fun `backward gradients have correct shapes`() {
        val input = DoubleArray(FeatureExtractor.FEATURE_COUNT) { 0.5 }
        val cache = model.forwardWithCache(input)
        val outputGrad = doubleArrayOf(1.0, -1.0)

        val gradients = model.backward(cache, outputGrad)

        // Layer 0: FEATURE_COUNT→64 → weightGrad=[64][FEATURE_COUNT], biasGrad=[64]
        assertEquals(64, gradients[0].weightGradients.size)
        assertEquals(FeatureExtractor.FEATURE_COUNT, gradients[0].weightGradients[0].size)
        assertEquals(64, gradients[0].biasGradients.size)

        // Layer 1: 64→32 → weightGrad=[32][64], biasGrad=[32]
        assertEquals(32, gradients[1].weightGradients.size)
        assertEquals(64, gradients[1].weightGradients[0].size)
        assertEquals(32, gradients[1].biasGradients.size)

        // Layer 2: 32→16 → weightGrad=[16][32], biasGrad=[16]
        assertEquals(16, gradients[2].weightGradients.size)
        assertEquals(32, gradients[2].weightGradients[0].size)
        assertEquals(16, gradients[2].biasGradients.size)

        // Layer 3: 16→2 → weightGrad=[2][16], biasGrad=[2]
        assertEquals(2, gradients[3].weightGradients.size)
        assertEquals(16, gradients[3].weightGradients[0].size)
        assertEquals(2, gradients[3].biasGradients.size)
    }

    @Test
    fun `backward gradients are non-zero for non-zero inputs`() {
        val input = DoubleArray(FeatureExtractor.FEATURE_COUNT) { (it + 1.0) / FeatureExtractor.FEATURE_COUNT.toDouble() }
        val cache = model.forwardWithCache(input)
        val outputGrad = doubleArrayOf(1.0, -1.0)

        val gradients = model.backward(cache, outputGrad)

        // At least some gradients should be non-zero
        val hasNonZeroGrad = gradients.any { layerGrad ->
            layerGrad.weightGradients.any { row -> row.any { it != 0.0 } } ||
                    layerGrad.biasGradients.any { it != 0.0 }
        }
        assertTrue("Gradients should have non-zero values", hasNonZeroGrad)
    }

    @Test
    fun `backward gradients are finite`() {
        val input = DoubleArray(FeatureExtractor.FEATURE_COUNT) { 0.5 }
        val cache = model.forwardWithCache(input)
        val outputGrad = doubleArrayOf(1.0, 1.0)

        val gradients = model.backward(cache, outputGrad)
        for (layerGrad in gradients) {
            for (row in layerGrad.weightGradients) {
                for (g in row) {
                    assertFalse("Weight gradient should be finite", g.isNaN() || g.isInfinite())
                }
            }
            for (g in layerGrad.biasGradients) {
                assertFalse("Bias gradient should be finite", g.isNaN() || g.isInfinite())
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Model Copy & Serialization
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `model copy produces identical outputs`() {
        val copy = model.copy()
        val input = DoubleArray(FeatureExtractor.FEATURE_COUNT) { it.toDouble() / FeatureExtractor.FEATURE_COUNT.toDouble() }

        val original = model.forward(input)
        val copied = copy.forward(input)

        assertArrayEquals("Copy should produce identical output", original, copied, 1e-12)
    }

    @Test
    fun `model copy is independent — modifying copy does not affect original`() {
        val input = DoubleArray(FeatureExtractor.FEATURE_COUNT) { 0.5 }
        val originalOutput = model.forward(input).copyOf()

        // Modify via gradient application
        val copy = model.copy()
        val cache = copy.forwardWithCache(input)
        val grads = copy.backward(cache, doubleArrayOf(1.0, 1.0))
        copy.applyGradients(grads, 0.1)

        val afterModification = model.forward(input)
        assertArrayEquals("Original should be unchanged", originalOutput, afterModification, 1e-12)
    }

    @Test
    fun `serializeWeights returns expected keys`() {
        val weights = model.serializeWeights()
        assertTrue(weights.containsKey("layerCount"))
        assertEquals(4, weights["layerCount"])
        for (i in 0..3) {
            assertTrue(weights.containsKey("layer_${i}_weights"))
            assertTrue(weights.containsKey("layer_${i}_biases"))
            assertTrue(weights.containsKey("layer_${i}_inputSize"))
            assertTrue(weights.containsKey("layer_${i}_outputSize"))
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Gradient Descent Integration
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `applying gradients changes model output`() {
        val input = DoubleArray(FeatureExtractor.FEATURE_COUNT) { 0.5 }
        val beforeOutput = model.forward(input).copyOf()

        val cache = model.forwardWithCache(input)
        val grads = model.backward(cache, doubleArrayOf(1.0, -1.0))
        model.applyGradients(grads, 0.01)

        val afterOutput = model.forward(input)
        val changed = beforeOutput.zip(afterOutput).any { (a, b) -> kotlin.math.abs(a - b) > 1e-10 }
        assertTrue("Output should change after gradient update", changed)
    }
}
