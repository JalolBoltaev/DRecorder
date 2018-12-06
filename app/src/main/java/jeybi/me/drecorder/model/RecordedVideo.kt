package jeybi.me.drecorder.model

import android.graphics.Bitmap

data class RecordedVideo(
    val title : String,
    val path : String,
    val size : String,
    val duration : String,
    val thumbnail : Bitmap
)