package com.robotics.urcs.device

data class TorchControlState(val isOn: Boolean = false, val isPending: Boolean = false)

fun beginTorchChange(state: TorchControlState, flashAvailable: Boolean): TorchControlState? =
    if (!flashAvailable || state.isPending) null else state.copy(isPending = true)

fun finishTorchChange(state: TorchControlState, requestedOn: Boolean, succeeded: Boolean): TorchControlState =
    state.copy(isOn = if (succeeded) requestedOn else state.isOn, isPending = false)
