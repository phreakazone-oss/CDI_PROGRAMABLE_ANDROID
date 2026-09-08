package id.ns200.cdir7

import android.net.Uri

data class CustomSoundTrack(
    val id: String,
    val name: String,
    val uri: Uri,
    val baseRpm: Int = 2000,
    val format: String = "AUDIO"
)
