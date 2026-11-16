package org.omarchy.music

import com.sun.jna.Function
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.ptr.PointerByReference

/** Windows' default output, asked of Windows itself (its sound devices' own interface, the one the Sound panel's
 *  green tick comes from). Before, the app worked it out from the registry (the device most recently made the default
 *  by hand), which is wrong for a device Windows chose by itself as it was plugged in: with a FiiO KA17 as the
 *  default, the app took a monitor's speakers for the output and used its EQ. Null when Windows can't be asked (then
 *  the registry is gone by, as before). */
object WinDefaultOutput {
    private const val ENUMERATOR = "{BCDE0395-E52F-467C-8E3D-C4579291692E}"    // MMDeviceEnumerator
    private const val IENUMERATOR = "{A95664D2-9614-4F35-A746-DE8DB63617E6}"   // IMMDeviceEnumerator
    private const val CLSCTX_ALL = 23

    /** A method of a COM object, by its place in the object's table. */
    private fun call(obj: Pointer, index: Int, vararg args: Any?): Int {
        val table = obj.getPointer(0)
        val f = Function.getFunction(table.getPointer(index.toLong() * Native.POINTER_SIZE), Function.ALT_CONVENTION)
        return f.invokeInt(arrayOf<Any?>(obj, *args))
    }

    /** The default output's own id as the registry names its key ("{guid}", small letters), for music (else for
     *  everything); null when there is none or Windows can't be asked. */
    fun guid(): String? = runCatching {
        if (!System.getProperty("os.name").lowercase().contains("win")) return null
        val ole = Ole32.INSTANCE
        ole.CoInitializeEx(null, 0)   // this thread may talk to Windows' objects (already able to: fine)
        val made = PointerByReference()
        val hr = ole.CoCreateInstance(Guid.CLSID(ENUMERATOR), null, CLSCTX_ALL, Guid.IID(IENUMERATOR), made)
        val enumerator = made.value
        if (hr.toInt() != 0 || enumerator == null) return null
        try {
            // IMMDeviceEnumerator: 3 EnumAudioEndpoints, 4 GetDefaultAudioEndpoint(flow: 0 render; role: 1 multimedia, 0 console)
            val dev = PointerByReference()
            if (call(enumerator, 4, 0, 1, dev) != 0 || dev.value == null) {
                if (call(enumerator, 4, 0, 0, dev) != 0 || dev.value == null) return null
            }
            val device = dev.value
            try {
                // IMMDevice: 3 Activate, 4 OpenPropertyStore, 5 GetId
                val id = PointerByReference()
                if (call(device, 5, id) != 0 || id.value == null) return null
                val text = id.value.getWideString(0)
                ole.CoTaskMemFree(id.value)
                WinOutputRules.guidOf(text)
            } finally { call(device, 2) }   // Release
        } finally { call(enumerator, 2) }
    }.getOrNull()
}
