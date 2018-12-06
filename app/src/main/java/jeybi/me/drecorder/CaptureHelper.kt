package jeybi.me.drecorder

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager

import android.content.Context.MEDIA_PROJECTION_SERVICE
import android.util.Log

internal class CaptureHelper private constructor() {

    init {
        throw AssertionError("No instances.")
    }

    companion object {
        private val CREATE_SCREEN_CAPTURE = 4242

        fun fireScreenCaptureIntent(activity: Activity) {
            val manager = activity.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val intent = manager.createScreenCaptureIntent()
            activity.startActivityForResult(intent, CREATE_SCREEN_CAPTURE)


        }

        fun handleActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent): Boolean {
            if (requestCode != CREATE_SCREEN_CAPTURE) {
                return false
            }

            if (resultCode == Activity.RESULT_OK) {
                (activity as MainActivity).startFloatingViewService(activity,true,data)
                activity.finish()
            }

            return true
        }
    }
}
