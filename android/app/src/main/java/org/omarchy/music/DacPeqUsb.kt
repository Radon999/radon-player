package org.omarchy.music

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build

/** The phone's half of [DacPeq]: a FiiO DAC's HID interface, over the phone's USB. Each command opens the DAC for a
 *  moment, takes the HID interface from Android (its own HID driver is given it back after), sends the command and
 *  reads the reply. The DAC's sound isn't touched, so it works with bit-perfect streaming or without. */
object DacPeqUsb : DacPeq.Link {
    private const val FIIO = 0x2972
    private const val ACTION = "org.omarchy.music.USB_PEQ_PERMISSION"
    private lateinit var app: Context
    private val usb get() = app.getSystemService(Context.USB_SERVICE) as UsbManager

    fun init(ctx: Context) {
        app = ctx.applicationContext
        DacPeq.link = this
        val f = IntentFilter().apply {
            addAction(ACTION); addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED); addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { DacPeq.refresh() }
        }
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(r, f, Context.RECEIVER_EXPORTED) else app.registerReceiver(r, f)
        DacPeq.refresh()
    }

    private fun hidOf(d: UsbDevice): UsbInterface? {
        val all = (0 until d.interfaceCount).map { d.getInterface(it) }.filter { it.interfaceClass == UsbConstants.USB_CLASS_HID }
        fun hasOut(i: UsbInterface) = (0 until i.endpointCount).any { i.getEndpoint(it).direction == UsbConstants.USB_DIR_OUT }
        return all.firstOrNull { hasOut(it) } ?: all.firstOrNull()
    }

    private fun device(): UsbDevice? = runCatching {
        usb.deviceList.values.firstOrNull { it.vendorId == FIIO && hidOf(it) != null }
    }.getOrNull()

    override fun dac(): String? = device()?.let { d -> d.productName?.trim()?.ifEmpty { null } ?: "FiiO DAC" }

    override fun allowed(): Boolean = device()?.let { usb.hasPermission(it) } ?: false

    override fun ask() {
        val d = device() ?: return
        val pi = PendingIntent.getBroadcast(app, 7, Intent(ACTION).setPackage(app.packageName),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        runCatching { usb.requestPermission(d, pi) }
    }

    override fun send(packet: ByteArray, reportId: Int, reply: Boolean): ByteArray? = synchronized(this) {
        val d = device() ?: return null
        if (!usb.hasPermission(d)) return null
        val intf = hidOf(d) ?: return null
        val conn = usb.openDevice(d) ?: return null
        try {
            if (!conn.claimInterface(intf, true)) return null
            try {
                val eps = (0 until intf.endpointCount).map { intf.getEndpoint(it) }
                val inEp = eps.firstOrNull { it.direction == UsbConstants.USB_DIR_IN && it.type == UsbConstants.USB_ENDPOINT_XFER_INT }
                val outEp = eps.firstOrNull { it.direction == UsbConstants.USB_DIR_OUT && it.type == UsbConstants.USB_ENDPOINT_XFER_INT }
                // The output report's size, from the interface's report descriptor
                val desc = ByteArray(1024)
                val dn = conn.controlTransfer(0x81, 6, 0x2200, intf.id, desc, desc.size, 500)
                val rep = if (dn > 0) DacPeq.outputBytes(desc.copyOf(dn), reportId) else null
                val ids = rep?.first ?: true
                val len = maxOf(rep?.second ?: ((outEp?.maxPacketSize ?: 64) - 1), packet.size)
                val buf = ByteArray((if (ids) 1 else 0) + len)
                if (ids) buf[0] = reportId.toByte()
                packet.copyInto(buf, if (ids) 1 else 0)
                // Anything the DAC said before, out of the way
                val r = ByteArray(maxOf(inEp?.maxPacketSize ?: 64, 64))
                if (inEp != null) for (k in 0 until 8) if (conn.bulkTransfer(inEp, r, r.size, 15) <= 0) break
                val sent = if (outEp != null) conn.bulkTransfer(outEp, buf, buf.size, 500)
                    else conn.controlTransfer(0x21, 0x09, 0x0200 or reportId, intf.id, buf, buf.size, 500)
                if (sent < 0) return null
                if (!reply || inEp == null) return ByteArray(0)
                val out = java.io.ByteArrayOutputStream()
                val until = System.currentTimeMillis() + 900
                while (System.currentTimeMillis() < until) {
                    val n = conn.bulkTransfer(inEp, r, r.size, 200)
                    if (n > 0) { out.write(r, 0, n); if (DacPeq.presetIn(out.toByteArray()) != null) break }
                }
                out.toByteArray()
            } finally {
                runCatching { conn.releaseInterface(intf) }
                runCatching { if (UsbNative.loaded) UsbNative.reattach(conn.fileDescriptor, intf.id) }   // Android's HID driver back
            }
        } catch (e: Exception) { null } finally { conn.close() }
    }
}
