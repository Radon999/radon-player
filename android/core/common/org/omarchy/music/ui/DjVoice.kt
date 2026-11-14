package org.omarchy.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.omarchy.music.DjVoices
import org.omarchy.music.Music
import org.omarchy.music.Spoken
import org.omarchy.music.Voice

/** The natural voice's download, while it runs ("Getting the voice… 40%"). */
private var naturalStatus by mutableStateOf<String?>(null)

/** The DJ's voice settings, on the DJ screen: on the PC the natural voice (Kokoro: fetched once when you ask, then
 *  a choice of voices), and on both an ElevenLabs voice with your own API key. */
@Composable
fun DjVoiceLines(ui: Ui) {
    var open by remember { mutableStateOf(false) }
    // The PC: the natural voice, fetched once on request; Windows' own voices sound robotic.
    if (Voice.needsNatural || naturalStatus != null) Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val st = naturalStatus
        if (st != null) Text(st, style = T.sub)
        else Pill("Get the natural voice (${Voice.naturalSize})", quiet = true) {
            ui.confirm = "Download the DJ's natural voice? It's Pocket TTS, a free voice that runs on your $DEVICE and sounds like a " +
                "real radio host (${Voice.naturalSize}, once; Wi-Fi is best). It's the same voice on the phone and the PC." to {
                naturalStatus = "Starting…"
                Music.bg({ Voice.getNatural { naturalStatus = it } }) { _, err ->
                    naturalStatus = null
                    if (err != null) Music.toast("Couldn't get the voice: ${err.message?.take(100)}")
                    else { Music.toast("The DJ has its natural voice now"); Voice.say(Spoken.humanize(DjVoices.SAMPLE, 0.0)) }
                }
            }
        }
    }
    Pill(if (open) "Voice settings  ▴" else "Voice settings  ▾", quiet = true) { open = !open }
    if (!open) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Why the natural voice didn't speak last time (stays here so it can be read, unlike the pop-up)
        val err = Voice.lastError
        if (err.isNotEmpty()) Text("The natural voice couldn't speak last time: $err", style = T.sub.copy(color = C.red))
        // The phone: the natural voice (the PC's) or the phone's own Google voice, and the download can go again
        if (Voice.canChoose) {
            Text("Voice", style = T.body)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Natural (as on the PC)", Voice.useNatural) { Voice.setUseNatural(true) }
                Chip("The phone's own", !Voice.useNatural) { Voice.setUseNatural(false) }
            }
            Pill("Remove the natural voice (${Voice.naturalSize})", quiet = true) {
                ui.confirm = "Remove the natural voice from this phone? It frees ${Voice.naturalSize}; the DJ uses the phone's own voice, and you can get it again any time." to {
                    Voice.removeNatural(); Music.toast("The natural voice is removed") }
            }
        }
        if (Voice.canSpeak) Pill("Hear the DJ", quiet = true) { Voice.say(Spoken.humanize(DjVoices.SAMPLE, 0.0)) }
    }
}
