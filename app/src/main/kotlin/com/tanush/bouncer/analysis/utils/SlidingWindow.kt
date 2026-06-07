package com.tanush.bouncer.analysis.utils

class SlidingWindow<T : Number>(val maxSize: Int) {

    private val _timestamps = ArrayDeque<Long>(maxSize + 1)
    private val _values = ArrayDeque<T>(maxSize + 1)

    val size: Int get() = _values.size
    val isEmpty: Boolean get() = _values.isEmpty()

    fun add(timestamp: Long, value: T) {
        if (_values.size >= maxSize) {
            _timestamps.removeFirst()
            _values.removeFirst()
        }
        _timestamps.addLast(timestamp)
        _values.addLast(value)
    }

    fun values(): List<T> = _values.toList()
    fun timestamps(): List<Long> = _timestamps.toList()

    /** Replace the most-recently added value in-place (timestamp stays the same). No-op if empty. */
    fun replaceLast(value: T) {
        if (_values.isEmpty()) return
        _values[_values.size - 1] = value
    }

    fun average(): Double = if (_values.isEmpty()) 0.0
        else _values.sumOf { it.toDouble() } / _values.size
}
