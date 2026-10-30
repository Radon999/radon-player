package org.omarchy.music.ui

/** Which one this is: the phone's app (Android) or the PC's. The screens are the same; a few lines differ. */
val onPhone: Boolean get() = org.omarchy.music.platformName == "android"

/** What the screens call this device: "phone" or "PC". */
val DEVICE: String get() = if (onPhone) "phone" else "PC"

/** Your own songs' place, as the source chips name it: "Phone" or "This PC". */
val DEVICE_NAME: String get() = if (onPhone) "Phone" else "This PC"
