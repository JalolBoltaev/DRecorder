package jeybi.me.drecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.display.VirtualDisplay
import android.media.CamcorderProfile
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.*
import android.support.annotation.RequiresApi
import android.util.DisplayMetrics
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast

import java.io.File
import java.io.IOException
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import android.app.PendingIntent.FLAG_CANCEL_CURRENT
import android.content.Context.*
import android.content.Intent.ACTION_SEND
import android.content.Intent.ACTION_VIEW
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
import android.media.MediaRecorder.OutputFormat.MPEG_4
import android.media.MediaRecorder.VideoEncoder.H264
import android.media.MediaRecorder.VideoSource.SURFACE
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.M
import android.os.Environment.DIRECTORY_MOVIES
import android.support.v4.content.ContextCompat.getSystemService
import android.widget.Toast.LENGTH_SHORT

internal class RecordingSession(
    private val context: Context, private val listener: Listener, private val resultCode: Int, private val data: Intent?,
    showCountDown: Boolean?, videoSizePercentage: Int?
) {

    private val mainThread = Handler(Looper.getMainLooper())


    private val outputRoot: File
    private val fileFormat = SimpleDateFormat("'DRecorder'yyyy-MM-dd-HH-mm-ss'.mp4'", Locale.US)

    private val notificationManager: NotificationManager
    private val windowManager: WindowManager
    private val projectionManager: MediaProjectionManager

    private var overlayView: OverlayView? = null
    private var recorder: MediaRecorder? = null
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var outputFile: String? = null
    private var running: Boolean = false
    private var recordingStartNanos: Long = 0

    var showCountDown: Boolean? = true
    var videoSizePercentage: Int? = 100

    private// Get the best camera profile available. We assume MediaRecorder supports the highest.
    val recordingInfo: RecordingInfo
        get() {
            val displayMetrics = DisplayMetrics()
            val wm = context.getSystemService(WINDOW_SERVICE) as WindowManager
            wm.defaultDisplay.getRealMetrics(displayMetrics)
            val displayWidth = displayMetrics.widthPixels
            val displayHeight = displayMetrics.heightPixels
            val displayDensity = displayMetrics.densityDpi

            val configuration = context.resources.configuration
            val isLandscape = configuration.orientation == ORIENTATION_LANDSCAPE
            val camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH)
            val cameraWidth = camcorderProfile?.videoFrameWidth ?: -1
            val cameraHeight = camcorderProfile?.videoFrameHeight ?: -1
            val cameraFrameRate = camcorderProfile?.videoFrameRate ?: 30

            val sizePercentage = 100

            return calculateRecordingInfo(
                displayWidth, displayHeight, displayDensity, isLandscape,
                cameraWidth, cameraHeight, cameraFrameRate, sizePercentage
            )
        }

    internal interface Listener {
        /** Invoked before [.onStart] to prepare UI before recording.  */
        fun onPrepare()

        /** Invoked immediately prior to the start of recording.  */
        fun onStart()

        /** Invoked immediately after the end of recording.  */
        fun onStop()

        /** Invoked after all work for this session has completed.  */
        fun onEnd()
    }

    init {

        this.showCountDown = showCountDown
        this.videoSizePercentage = videoSizePercentage

        val picturesDir = Environment.getExternalStoragePublicDirectory(DIRECTORY_MOVIES)
        outputRoot = File(picturesDir, "Telecine")

        notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        windowManager = context.getSystemService(WINDOW_SERVICE) as WindowManager
        projectionManager = context.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    fun showOverlay() {

        val overlayListener = object : OverlayView.Listener {
            override fun onCancel() {
                cancelOverlay()
            }

            override fun onPrepare() {
                listener.onPrepare()
            }

            override fun onStart() {
                startRecording()
            }

            override fun onStop() {
                stopRecording()
            }

            override fun onResize() {
                windowManager.updateViewLayout(overlayView, overlayView!!.layoutParams)
            }
        }
        overlayView = OverlayView.create(context, overlayListener, showCountDown!!)




        windowManager.addView(overlayView, OverlayView.createLayoutParams(context))


    }





    private fun hideOverlay() {
        if (overlayView != null) {
            windowManager.removeView(overlayView)
            overlayView = null

        }
    }

    private fun cancelOverlay() {
        hideOverlay()
        listener.onEnd()


    }

    private fun startRecording() {

        if (!outputRoot.exists() && !outputRoot.mkdirs()) {
            Toast.makeText(
                context, "Unable to create output directory.\nCannot record screen.",
                LENGTH_SHORT
            ).show()
            return
        }

        val recordingInfo = recordingInfo

        recorder = MediaRecorder()
        recorder!!.setVideoSource(SURFACE)
        recorder!!.setOutputFormat(MPEG_4)
        recorder!!.setVideoFrameRate(recordingInfo.frameRate)
        recorder!!.setVideoEncoder(H264)
        recorder!!.setVideoSize(recordingInfo.width, recordingInfo.height)
        recorder!!.setVideoEncodingBitRate(8 * 1000 * 1000)

        val outputName = fileFormat.format(Date())
        outputFile = File(outputRoot, outputName).absolutePath
        recorder!!.setOutputFile(outputFile)

        try {
            recorder!!.prepare()
        } catch (e: IOException) {
            throw RuntimeException("Unable to prepare MediaRecorder.", e)
        }

        projection = projectionManager.getMediaProjection(resultCode, data)

        val surface = recorder!!.surface
        display = projection!!.createVirtualDisplay(
            DISPLAY_NAME, recordingInfo.width, recordingInfo.height,
            recordingInfo.density, VIRTUAL_DISPLAY_FLAG_PRESENTATION, surface, null, null
        )

        recorder!!.start()
        running = true
        recordingStartNanos = System.nanoTime()
        listener.onStart()

    }

    private fun stopRecording() {

        if (!running) {
            throw IllegalStateException("Not running.")
        }
        running = false

        hideOverlay()

        var propagate = false
        try {
            // Stop the projection in order to flush everything to the recorder.
            projection!!.stop()
            // Stop the recorder which writes the contents to the file.
            recorder!!.stop()

            propagate = true
        } finally {
            try {
                // Ensure the listener can tear down its resources regardless if stopping crashes.
                listener.onStop()
            } catch (e: RuntimeException) {
                if (propagate) {

                    throw e // Only allow listener exceptions to propagate if stopped successfully.
                }
            }

        }

        val recordingStopNanos = System.nanoTime()

        recorder!!.release()
        display!!.release()



        MediaScannerConnection.scanFile(
            context, arrayOf<String>(outputFile!!), null
        ) { path, uri ->
            if (uri == null) throw NullPointerException("uri == null")

            mainThread.post { showNotification(uri, null) }
        }
    }

    private fun showNotification(uri: Uri?, bitmap: Bitmap?) {
        val viewIntent = Intent(ACTION_VIEW, uri)
        val pendingViewIntent = PendingIntent.getActivity(context, 0, viewIntent, FLAG_CANCEL_CURRENT)

        var shareIntent = Intent(ACTION_SEND)
        shareIntent.type = MIME_TYPE
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
        shareIntent = Intent.createChooser(shareIntent, null)
        val pendingShareIntent = PendingIntent.getActivity(context, 0, shareIntent, FLAG_CANCEL_CURRENT)

        val deleteIntent = Intent(context, DeleteRecordingBroadcastReceiver::class.java)
        deleteIntent.data = uri
        val pendingDeleteIntent = PendingIntent.getBroadcast(context, 0, deleteIntent, FLAG_CANCEL_CURRENT)

        val title = context.getText(R.string.notification_captured_title)
        val subtitle = context.getText(R.string.notification_captured_subtitle)
        val share = context.getText(R.string.notification_captured_share)
        val delete = context.getText(R.string.notification_captured_delete)
        val builder = Notification.Builder(context) //
            .setContentTitle(title)
            .setContentText(subtitle)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setSmallIcon(R.drawable.ic_videocam_white_24dp)
            .setColor(context.resources.getColor(R.color.primary_normal))
            .setContentIntent(pendingViewIntent)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_share_white_24dp, share, pendingShareIntent)
            .addAction(R.drawable.ic_delete_white_24dp, delete, pendingDeleteIntent)

        if (bitmap != null) {
            builder.setLargeIcon(createSquareBitmap(bitmap)).style = Notification.BigPictureStyle() //
                .setBigContentTitle(title) //
                .setSummaryText(subtitle) //
                .bigPicture(bitmap)
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())

        if (bitmap != null) {
            listener.onEnd()
            return
        }

        object : AsyncTask<Void, Void, Bitmap>() {
            override fun doInBackground(vararg none: Void): Bitmap {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                return retriever.frameAtTime
            }

            override fun onPostExecute(bitmap: Bitmap?) {
                if (bitmap != null && !notificationDismissed()) {
                    showNotification(uri, bitmap)
                } else {
                    listener.onEnd()
                }
            }

            private fun notificationDismissed(): Boolean {
                return SDK_INT >= M && notificationManager.activeNotifications.size == 0
            }
        }.execute()
    }

    internal class RecordingInfo(val width: Int, val height: Int, val frameRate: Int, val density: Int)

    fun destroy() {
        if (running) {
            stopRecording()
        }
    }





    companion object {
        val NOTIFICATION_ID = 522592

        val DISPLAY_NAME = "telecine"
        val MIME_TYPE = "video/mp4"


        fun calculateRecordingInfo(
            displayWidth: Int, displayHeight: Int,
            displayDensity: Int, isLandscapeDevice: Boolean, cameraWidth: Int, cameraHeight: Int,
            cameraFrameRate: Int, sizePercentage: Int
        ): RecordingInfo {
            var displayWidth = displayWidth
            var displayHeight = displayHeight
            // Scale the display size before any maximum size calculations.
            displayWidth = displayWidth * sizePercentage / 100
            displayHeight = displayHeight * sizePercentage / 100

            if (cameraWidth == -1 && cameraHeight == -1) {
                // No cameras. Fall back to the display size.
                return RecordingInfo(displayWidth, displayHeight, cameraFrameRate, displayDensity)
            }

            var frameWidth = if (isLandscapeDevice) cameraWidth else cameraHeight
            var frameHeight = if (isLandscapeDevice) cameraHeight else cameraWidth
            if (frameWidth >= displayWidth && frameHeight >= displayHeight) {
                // Frame can hold the entire display. Use exact values.
                return RecordingInfo(displayWidth, displayHeight, cameraFrameRate, displayDensity)
            }

            // Calculate new width or height to preserve aspect ratio.
            if (isLandscapeDevice) {
                frameWidth = displayWidth * frameHeight / displayHeight
            } else {
                frameHeight = displayHeight * frameWidth / displayWidth
            }
            return RecordingInfo(frameWidth, frameHeight, cameraFrameRate, displayDensity)
        }

        fun createSquareBitmap(bitmap: Bitmap): Bitmap {
            var x = 0
            var y = 0
            var width = bitmap.width
            var height = bitmap.height
            if (width > height) {
                x = (width - height) / 2

                width = height
            } else {
                y = (height - width) / 2

                height = width
            }
            return Bitmap.createBitmap(bitmap, x, y, width, height, null, true)
        }
    }
}
