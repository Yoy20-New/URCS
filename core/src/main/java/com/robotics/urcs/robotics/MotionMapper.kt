package com.robotics.urcs.robotics

data class MotionVector(val forward: Float, val turn: Float)

object MotionMapper {
    fun map(
        x: Float,
        y: Float,
        deadZone: Float,
        sensitivity: Float = 1f,
        invertX: Boolean = false,
        invertY: Boolean = false
    ): MotionVector {
        val zone = deadZone.takeIf(Float::isFinite)?.coerceIn(0f, 0.95f) ?: 0f
        val gain = sensitivity.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
        fun axis(value: Float, inverted: Boolean): Float {
            if (!value.isFinite()) return 0f
            val bounded = value.coerceIn(-1f, 1f)
            if (kotlin.math.abs(bounded) < zone) return 0f
            return (if (inverted) -bounded else bounded) * gain
        }
        return MotionVector(forward = axis(-y, invertY), turn = axis(x, invertX))
    }
}
