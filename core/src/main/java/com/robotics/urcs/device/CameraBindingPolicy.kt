package com.robotics.urcs.device

enum class CameraFacing { FRONT, BACK }

fun selectCameraFacing(requested: CameraFacing, hasFront: Boolean, hasBack: Boolean): CameraFacing? = when {
    requested == CameraFacing.FRONT && hasFront -> CameraFacing.FRONT
    requested == CameraFacing.BACK && hasBack -> CameraFacing.BACK
    hasBack -> CameraFacing.BACK
    hasFront -> CameraFacing.FRONT
    else -> null
}

object CameraBindingPolicy {
    fun shouldBind(isScreenActive: Boolean, callbackGeneration: Int, currentGeneration: Int): Boolean =
        isScreenActive && callbackGeneration == currentGeneration
}
