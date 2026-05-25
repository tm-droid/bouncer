package com.tanush.bouncer.analysis.ml

import kotlin.math.*
import kotlin.random.Random
import io.sentry.Sentry

/**
 * Multi-layer perceptron (neural network) for cricket score prediction.
 *
 * Architecture:
 *   Input(32) → Dense(64, ReLU) → Dense(32, ReLU) → Dense(16, ReLU) → Dense(outputSize, Sigmoid)
 *
 * Supports full forward pass and backpropagation for gradient-based training.
 * Weights are initialized with He initialization for ReLU layers and
 * Xavier initialization for the sigmoid output layer.
 *
 * Thread Safety: read (forward) is lock-free; write (applyGradients) takes the
 * weightLock so concurrent forward passes during training can't see torn weights.
 * forwardWithCache + backward must run under the same lock when training.
 */
class ScorePredictionModel private constructor(
    private val layers: List<DenseLayer>,
    private val activations: List<Activation>
) {
    private val weightLock = Any()

    /**
     * Forward pass through the network.
     *
     * @param input Feature vector of size [inputSize]
     * @return Output predictions
     */
    fun forward(input: DoubleArray): DoubleArray {
        try {
            synchronized(weightLock) {
                var current = input
                for (i in layers.indices) {
                    current = layers[i].forward(current)
                    current = activations[i].forward(current)
                }
                return current
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    /**
     * Forward pass that caches all intermediate values for backpropagation.
     *
     * @param input Feature vector
     * @return [ForwardCache] containing all layer outputs and pre-activations
     */
    fun forwardWithCache(input: DoubleArray): ForwardCache {
        try {
            val preActivations = mutableListOf<DoubleArray>()
            val postActivations = mutableListOf<DoubleArray>()

            var current = input
            postActivations.add(current.copyOf()) // input is activation[0]

            for (i in layers.indices) {
                val z = layers[i].forward(current)
                preActivations.add(z.copyOf())

                val a = activations[i].forward(z)
                postActivations.add(a.copyOf())

                current = a
            }

            return ForwardCache(
                input = input,
                preActivations = preActivations,
                postActivations = postActivations,
                output = current
            )
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    /**
     * Backward pass — computes gradients for all parameters.
     *
     * @param cache Cached forward pass values
     * @param outputGradient ∂L/∂output from the loss function
     * @return List of [LayerGradients] for each layer (in layer order)
     */
    fun backward(cache: ForwardCache, outputGradient: DoubleArray): List<LayerGradients> {
        try {
            val allGradients = mutableListOf<LayerGradients>()
            var currentGradient = outputGradient

            // Backpropagate through layers in reverse
            for (i in layers.indices.reversed()) {
                // Gradient through activation
                val activationGrad = activations[i].backward(
                    cache.preActivations[i], currentGradient
                )

                // Gradient through linear layer
                val inputToLayer = cache.postActivations[i] // activation from previous layer
                val layerGrad = layers[i].backward(inputToLayer, activationGrad)

                allGradients.add(0, layerGrad)

                // Pass gradient to previous layer
                currentGradient = layerGrad.inputGradient
            }

            return allGradients
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    /**
     * Apply gradient updates to all layers.
     *
     * @param gradients List of gradients per layer
     * @param learningRate Step size for the update
     */
    fun applyGradients(gradients: List<LayerGradients>, learningRate: Double) {
        try {
            synchronized(weightLock) {
                for (i in layers.indices) {
                    layers[i].applyGradients(gradients[i], learningRate)
                }
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    /** Get all model parameters as a flat list (for serialization / inspection). */
    fun getParameters(): List<DoubleArray> {
        try {
            return layers.flatMap { listOf(it.weights.flatten(), it.biases.copyOf()) }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    /** Get the total number of trainable parameters in the model. */
    fun parameterCount(): Int {
        try {
            return layers.sumOf { it.weights.size * it.weights[0].size + it.biases.size }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    /** Deep-copy the entire model (useful for checkpointing best model during training). */
    fun copy(): ScorePredictionModel {
        try {
            val copiedLayers = layers.map { it.copy() }
            return ScorePredictionModel(copiedLayers, activations)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    /** Save model weights to a map for persistence. */
    fun serializeWeights(): Map<String, Any> {
        try {
            val data = mutableMapOf<String, Any>()
            data["layerCount"] = layers.size
            for (i in layers.indices) {
                data["layer_${i}_weights"] = layers[i].weights.map { it.toList() }
                data["layer_${i}_biases"] = layers[i].biases.toList()
                data["layer_${i}_inputSize"] = layers[i].inputSize
                data["layer_${i}_outputSize"] = layers[i].outputSize
            }
            return data
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    /**
     * Restore model weights from a map previously produced by [serializeWeights].
     *
     * The map must contain the exact keys written by serializeWeights:
     *   "layerCount", "layer_N_weights", "layer_N_biases",
     *   "layer_N_inputSize", "layer_N_outputSize"
     *
     * Layer count and dimensions must match the current model architecture.
     * Throws [IllegalArgumentException] on structural mismatch.
     */
    fun deserializeWeights(map: Map<String, Any>) {
        try {
            val layerCount = when (val v = map["layerCount"]) {
                is Int -> v
                is Long -> v.toInt()
                is Double -> v.toInt()
                else -> throw IllegalArgumentException(
                    "deserializeWeights: missing or invalid 'layerCount'"
                )
            }
            require(layerCount == layers.size) {
                "deserializeWeights: layerCount=$layerCount but model has ${layers.size} layers"
            }

            synchronized(weightLock) {
                for (i in layers.indices) {
                    val inputSize = when (val v = map["layer_${i}_inputSize"]) {
                        is Int -> v
                        is Long -> v.toInt()
                        is Double -> v.toInt()
                        else -> throw IllegalArgumentException(
                            "deserializeWeights: missing 'layer_${i}_inputSize'"
                        )
                    }
                    val outputSize = when (val v = map["layer_${i}_outputSize"]) {
                        is Int -> v
                        is Long -> v.toInt()
                        is Double -> v.toInt()
                        else -> throw IllegalArgumentException(
                            "deserializeWeights: missing 'layer_${i}_outputSize'"
                        )
                    }
                    require(inputSize == layers[i].inputSize && outputSize == layers[i].outputSize) {
                        "deserializeWeights: layer $i dimension mismatch — " +
                        "expected (${layers[i].inputSize}→${layers[i].outputSize}), " +
                        "got ($inputSize→$outputSize)"
                    }

                    @Suppress("UNCHECKED_CAST")
                    val rawWeights = map["layer_${i}_weights"] as? List<List<*>>
                        ?: throw IllegalArgumentException(
                            "deserializeWeights: missing or wrong type for 'layer_${i}_weights'"
                        )
                    require(rawWeights.size == outputSize) {
                        "deserializeWeights: layer $i weights row count ${rawWeights.size} != outputSize $outputSize"
                    }
                    for (j in 0 until outputSize) {
                        val row = rawWeights[j]
                        require(row.size == inputSize) {
                            "deserializeWeights: layer $i weights[$j] size ${row.size} != inputSize $inputSize"
                        }
                        for (k in 0 until inputSize) {
                            layers[i].weights[j][k] = when (val v = row[k]) {
                                is Double -> v
                                is Float -> v.toDouble()
                                is Int -> v.toDouble()
                                is Long -> v.toDouble()
                                else -> throw IllegalArgumentException(
                                    "deserializeWeights: unexpected weight type ${v?.javaClass}"
                                )
                            }
                        }
                    }

                    @Suppress("UNCHECKED_CAST")
                    val rawBiases = map["layer_${i}_biases"] as? List<*>
                        ?: throw IllegalArgumentException(
                            "deserializeWeights: missing or wrong type for 'layer_${i}_biases'"
                        )
                    require(rawBiases.size == outputSize) {
                        "deserializeWeights: layer $i biases size ${rawBiases.size} != outputSize $outputSize"
                    }
                    for (j in 0 until outputSize) {
                        layers[i].biases[j] = when (val v = rawBiases[j]) {
                            is Double -> v
                            is Float -> v.toDouble()
                            is Int -> v.toDouble()
                            is Long -> v.toDouble()
                            else -> throw IllegalArgumentException(
                                "deserializeWeights: unexpected bias type ${v?.javaClass}"
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    companion object {
        /**
         * Creates the default score prediction model architecture.
         *
         * Architecture: 32 → 64 (ReLU) → 32 (ReLU) → 16 (ReLU) → 2 (Sigmoid)
         * Output[0] = win probability, Output[1] = normalized predicted score
         *
         * @param seed Random seed for reproducible weight initialization
         */
        fun create(seed: Long = 42L): ScorePredictionModel {
            try {
                val rng = Random(seed)
                return ScorePredictionModel(
                    layers = listOf(
                        DenseLayer.heInit(FeatureExtractor.FEATURE_COUNT, 64, rng),
                        DenseLayer.heInit(64, 32, rng),
                        DenseLayer.heInit(32, 16, rng),
                        DenseLayer.xavierInit(16, 2, rng) // output: [winProb, scoreNorm]
                    ),
                    activations = listOf(
                        Activation.RELU,
                        Activation.RELU,
                        Activation.RELU,
                        Activation.SIGMOID
                    )
                )
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }

        /**
         * Creates a custom architecture model.
         *
         * @param layerSizes List of layer sizes including input and output
         * @param hiddenActivation Activation for hidden layers
         * @param outputActivation Activation for the output layer
         * @param seed Random seed
         */
        fun createCustom(
            layerSizes: List<Int>,
            hiddenActivation: Activation = Activation.RELU,
            outputActivation: Activation = Activation.SIGMOID,
            seed: Long = 42L
        ): ScorePredictionModel {
            require(layerSizes.size >= 2) { "Need at least input and output sizes" }
            val rng = Random(seed)
            val layers = mutableListOf<DenseLayer>()
            val activations = mutableListOf<Activation>()

            for (i in 0 until layerSizes.size - 1) {
                val isOutput = (i == layerSizes.size - 2)
                layers.add(
                    if (isOutput) DenseLayer.xavierInit(layerSizes[i], layerSizes[i + 1], rng)
                    else DenseLayer.heInit(layerSizes[i], layerSizes[i + 1], rng)
                )
                activations.add(if (isOutput) outputActivation else hiddenActivation)
            }

            return ScorePredictionModel(layers, activations)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Dense (Fully Connected) Layer
// ═══════════════════════════════════════════════════════════════════

/**
 * A single fully-connected (dense) layer: y = Wx + b
 */
class DenseLayer(
    val weights: Array<DoubleArray>,  // [outputSize][inputSize]
    val biases: DoubleArray,          // [outputSize]
    val inputSize: Int,
    val outputSize: Int
) {
    /** Forward: compute z = W·x + b */
    fun forward(input: DoubleArray): DoubleArray {
        try {
            require(input.size == inputSize) {
                "Input size ${input.size} doesn't match expected $inputSize"
            }
            val output = DoubleArray(outputSize)
            for (j in 0 until outputSize) {
                var sum = biases[j]
                for (i in 0 until inputSize) {
                    sum += weights[j][i] * input[i]
                }
                output[j] = sum
            }
            return output
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    /**
     * Backward: compute gradients for weights, biases, and input.
     *
     * @param input The input to this layer during forward pass
     * @param outputGradient ∂L/∂z for this layer's output
     * @return [LayerGradients] containing weight, bias, and input gradients
     */
    fun backward(input: DoubleArray, outputGradient: DoubleArray): LayerGradients {
        try {
            // ∂L/∂W = outputGradient ⊗ input^T
            val weightGradients = Array(outputSize) { j ->
                DoubleArray(inputSize) { i ->
                    outputGradient[j] * input[i]
                }
            }

            // ∂L/∂b = outputGradient
            val biasGradients = outputGradient.copyOf()

            // ∂L/∂x = W^T · outputGradient
            val inputGradient = DoubleArray(inputSize)
            for (i in 0 until inputSize) {
                var sum = 0.0
                for (j in 0 until outputSize) {
                    sum += weights[j][i] * outputGradient[j]
                }
                inputGradient[i] = sum
            }

            return LayerGradients(weightGradients, biasGradients, inputGradient)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    /** Apply gradient updates: W -= lr * ∂L/∂W, b -= lr * ∂L/∂b */
    fun applyGradients(gradients: LayerGradients, learningRate: Double) {
        try {
            for (j in 0 until outputSize) {
                for (i in 0 until inputSize) {
                    weights[j][i] -= learningRate * gradients.weightGradients[j][i]
                }
                biases[j] -= learningRate * gradients.biasGradients[j]
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    /** Deep copy of this layer. */
    fun copy(): DenseLayer {
        try {
            return DenseLayer(
                weights = weights.map { it.copyOf() }.toTypedArray(),
                biases = biases.copyOf(),
                inputSize = inputSize,
                outputSize = outputSize
            )
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    companion object {
        /** He initialization: weights ~ N(0, sqrt(2/fanIn)). Best for ReLU. */
        fun heInit(inputSize: Int, outputSize: Int, rng: Random): DenseLayer {
            try {
                val std = sqrt(2.0 / inputSize)
                return DenseLayer(
                    weights = Array(outputSize) {
                        DoubleArray(inputSize) { rng.nextGaussian() * std }
                    },
                    biases = DoubleArray(outputSize) { 0.0 },
                    inputSize = inputSize,
                    outputSize = outputSize
                )
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }

        /** Xavier initialization: weights ~ N(0, sqrt(2/(fanIn + fanOut))). Best for Sigmoid/Tanh. */
        fun xavierInit(inputSize: Int, outputSize: Int, rng: Random): DenseLayer {
            try {
                val std = sqrt(2.0 / (inputSize + outputSize))
                return DenseLayer(
                    weights = Array(outputSize) {
                        DoubleArray(inputSize) { rng.nextGaussian() * std }
                    },
                    biases = DoubleArray(outputSize) { 0.0 },
                    inputSize = inputSize,
                    outputSize = outputSize
                )
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }
    }
}

/** Extension to produce Gaussian random from Kotlin Random. */
private fun Random.nextGaussian(): Double {
    try {
        // Box-Muller transform
        val u1 = nextDouble()
        val u2 = nextDouble()
        return sqrt(-2.0 * ln(max(u1, 1e-10))) * cos(2.0 * PI * u2)
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

/** Flatten a 2D array to a 1D array. */
private fun Array<DoubleArray>.flatten(): DoubleArray {
    try {
        val total = sumOf { it.size }
        val result = DoubleArray(total)
        var idx = 0
        for (row in this) {
            row.copyInto(result, idx)
            idx += row.size
        }
        return result
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

// ═══════════════════════════════════════════════════════════════════
// Activation Functions
// ═══════════════════════════════════════════════════════════════════

/**
 * Activation functions with forward and backward passes.
 */
enum class Activation {
    RELU {
        override fun forward(z: DoubleArray): DoubleArray =
            DoubleArray(z.size) { max(0.0, z[it]) }

        override fun backward(z: DoubleArray, gradient: DoubleArray): DoubleArray =
            DoubleArray(z.size) { if (z[it] > 0.0) gradient[it] else 0.0 }
    },

    LEAKY_RELU {
        private val alpha = 0.01

        override fun forward(z: DoubleArray): DoubleArray =
            DoubleArray(z.size) { if (z[it] > 0.0) z[it] else alpha * z[it] }

        override fun backward(z: DoubleArray, gradient: DoubleArray): DoubleArray =
            DoubleArray(z.size) { if (z[it] > 0.0) gradient[it] else alpha * gradient[it] }
    },

    SIGMOID {
        override fun forward(z: DoubleArray): DoubleArray =
            DoubleArray(z.size) { sigmoid(z[it]) }

        override fun backward(z: DoubleArray, gradient: DoubleArray): DoubleArray {
            try {
                return DoubleArray(z.size) {
                    val s = sigmoid(z[it])
                    gradient[it] * s * (1.0 - s)
                }
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }
    },

    TANH {
        override fun forward(z: DoubleArray): DoubleArray =
            DoubleArray(z.size) { tanh(z[it]) }

        override fun backward(z: DoubleArray, gradient: DoubleArray): DoubleArray {
            try {
                return DoubleArray(z.size) {
                    val t = tanh(z[it])
                    gradient[it] * (1.0 - t * t)
                }
            } catch (e: Exception) {
                Sentry.captureException(e)
                throw e
            }
        }
    },

    LINEAR {
        override fun forward(z: DoubleArray): DoubleArray = z.copyOf()
        override fun backward(z: DoubleArray, gradient: DoubleArray): DoubleArray = gradient.copyOf()
    };

    abstract fun forward(z: DoubleArray): DoubleArray
    abstract fun backward(z: DoubleArray, gradient: DoubleArray): DoubleArray
}

/** Numerically stable sigmoid. */
private fun sigmoid(x: Double): Double {
    try {
        return if (x >= 0) {
            1.0 / (1.0 + exp(-x))
        } else {
            val ez = exp(x)
            ez / (1.0 + ez)
        }
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

// ═══════════════════════════════════════════════════════════════════
// Cache and Gradient Data Structures
// ═══════════════════════════════════════════════════════════════════

/**
 * Cached values from forward pass, needed for backpropagation.
 */
data class ForwardCache(
    val input: DoubleArray,
    val preActivations: List<DoubleArray>,   // z values before activation
    val postActivations: List<DoubleArray>,  // a values after activation (includes input)
    val output: DoubleArray
) {
    override fun equals(other: Any?): Boolean {
        try {
            if (this === other) return true
            if (other !is ForwardCache) return false
            return input.contentEquals(other.input) && output.contentEquals(other.output)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    override fun hashCode(): Int = input.contentHashCode() * 31 + output.contentHashCode()
}

/**
 * Gradients computed for a single dense layer during backpropagation.
 */
data class LayerGradients(
    val weightGradients: Array<DoubleArray>,  // ∂L/∂W
    val biasGradients: DoubleArray,           // ∂L/∂b
    val inputGradient: DoubleArray            // ∂L/∂x (passed to previous layer)
) {
    override fun equals(other: Any?): Boolean {
        try {
            if (this === other) return true
            if (other !is LayerGradients) return false
            return biasGradients.contentEquals(other.biasGradients) &&
                    inputGradient.contentEquals(other.inputGradient)
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }

    override fun hashCode(): Int = biasGradients.contentHashCode()
}
