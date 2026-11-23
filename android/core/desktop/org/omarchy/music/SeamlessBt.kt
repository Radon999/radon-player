package org.omarchy.music

/** Ignore Bluetooth devices: no stutter at all is the phone's (Shizuku is an Android app); the PC's Settings don't
 *  show it. */
object SeamlessBt {
    val on: Boolean get() = false
    val supported: Boolean get() = false
    val status: String get() = ""
    val step: String get() = ""
    val autoButton: String get() = ""
    fun setOn(v: Boolean) {}
    fun nextStep() {}
    fun toggleAuto() {}
}
