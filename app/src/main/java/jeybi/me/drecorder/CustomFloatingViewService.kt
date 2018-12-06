package jeybi.me.drecorder

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.preference.PreferenceManager
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import android.text.TextUtils
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import jp.co.recruit_lifestyle.android.floatingview.FloatingViewListener
import jp.co.recruit_lifestyle.android.floatingview.FloatingViewManager

import android.app.Notification.PRIORITY_MIN
import android.content.res.Configuration
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.CamcorderProfile
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * FloatingViewのカスタマイズを行います。
 * サンプルとしてクリック時にはメールアプリを起動します。
 */
class CustomFloatingViewService : Service(), FloatingViewListener {


    private var mFloatingViewManager: FloatingViewManager? = null


    private// Get the best camera profile available. We assume MediaRecorder supports the highest.
    val recordingInfo: RecordingSession.RecordingInfo
        get() {
            val displayMetrics = DisplayMetrics()
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.defaultDisplay.getRealMetrics(displayMetrics)
            val displayWidth = displayMetrics.widthPixels
            val displayHeight = displayMetrics.heightPixels
            val displayDensity = displayMetrics.densityDpi

            val configuration = resources.configuration
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH)
            val cameraWidth = camcorderProfile?.videoFrameWidth ?: -1
            val cameraHeight = camcorderProfile?.videoFrameHeight ?: -1
            val cameraFrameRate = camcorderProfile?.videoFrameRate ?: 30

            val sizePercentage = 100

            return RecordingSession.calculateRecordingInfo(
                displayWidth, displayHeight, displayDensity, isLandscape,
                cameraWidth, cameraHeight, cameraFrameRate, sizePercentage
            )
        }


    private val mainThread = Handler(Looper.getMainLooper())


    lateinit var outputRoot: File
    private val fileFormat = SimpleDateFormat("'DRecorder'yyyy-MM-dd-HH-mm-ss'.mp4'", Locale.US)
    lateinit var notificationManager: NotificationManager
    lateinit var windowManager: WindowManager
    lateinit var projectionManager: MediaProjectionManager
    lateinit var recorder: MediaRecorder
    private var projection: MediaProjection? = null
    lateinit var display: VirtualDisplay
    lateinit var outputFile: String
    private var running: Boolean = false
    private var recordingStartNanos: Long = 0


    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        // 既にManagerが存在していたら何もしない



        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        outputRoot = File(picturesDir, "DRecorder")

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)

        projection = projectionManager.getMediaProjection(resultCode, data)


        if (mFloatingViewManager != null) {
            return Service.START_STICKY
        }

        val metrics = DisplayMetrics()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getMetrics(metrics)
        val inflater = LayoutInflater.from(this)

        val iconView = inflater.inflate(R.layout.widget_record, null, false) as FrameLayout
        val textViewCounter = iconView.findViewById<TextView>(R.id.textViewCounter)

        iconView.setOnClickListener {




            if(!running) {

                val preferences = getSharedPreferences("SETTINGS", Context.MODE_PRIVATE)
                val isCountDownEnabled = preferences.getBoolean("countdown",true)

                if (isCountDownEnabled) {
                    object : CountDownTimer(4000, 1000) {
                        override fun onFinish() {
                            textViewCounter.visibility = View.GONE
                            startRecording()
                            iconView.findViewById<ImageView>(R.id.imageViewRecord)
                                .setImageResource(R.drawable.ic_stop_rec)
                        }

                        override fun onTick(p0: Long) {
                            textViewCounter.text = "${p0 / 1000}"
                        }

                    }.start()
                }else{

                    textViewCounter.visibility = View.GONE
                    startRecording()
                    iconView.findViewById<ImageView>(R.id.imageViewRecord)
                        .setImageResource(R.drawable.ic_stop_rec)

                }

            }else{
                stopRecording()
                onDestroy()

                Toast.makeText(applicationContext,"Video recorded successfully",Toast.LENGTH_LONG).show()

                startActivity(Intent(applicationContext,MainActivity::class.java))
            }
        }



        mFloatingViewManager = FloatingViewManager(this, this)
        mFloatingViewManager!!.setFixedTrashIconImage(R.drawable.ic_trash_fixed)
        mFloatingViewManager!!.setActionTrashIconImage(R.drawable.ic_trash_action)
        mFloatingViewManager!!.setSafeInsetRect(intent.getParcelableExtra<Parcelable>(EXTRA_CUTOUT_SAFE_AREA) as Rect)
        // Setting Options(you can change options at any time)
        loadDynamicOptions()
        // Initial Setting Options (you can't change options after created.)
        val options = loadOptions(metrics)
        mFloatingViewManager!!.addViewToWindow(iconView, options)

        // 常駐起動
        startForeground(NOTIFICATION_ID, createNotification(this))





        return Service.START_REDELIVER_INTENT
    }


    private fun startRecording() {

        if (!outputRoot.exists() && !outputRoot.mkdirs()) {
            Toast.makeText(
                applicationContext, "Unable to create output directory.\nCannot record screen.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val recordingInfo = recordingInfo

        recorder = MediaRecorder()
        recorder!!.setVideoSource(MediaRecorder.VideoSource.SURFACE)

        val preferences = getSharedPreferences("SETTINGS", Context.MODE_PRIVATE)
        val isSoundEnabled = preferences.getBoolean("sound",true)

        if (isSoundEnabled) {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder!!.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        }else{
            recorder!!.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        }


        recorder!!.setVideoFrameRate(recordingInfo.frameRate)
        recorder!!.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
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





        val surface = recorder!!.surface
        display = projection!!.createVirtualDisplay(
            RecordingSession.DISPLAY_NAME, recordingInfo.width, recordingInfo.height,
            recordingInfo.density, DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION, surface, null, null
        )

        recorder!!.start()
        running = true
        recordingStartNanos = System.nanoTime()


    }

    private fun stopRecording() {

        if (!running) {
            throw IllegalStateException("Not running.")
        }
        running = false


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
//                listener.onStop()
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
            applicationContext, arrayOf<String>(outputFile!!), null
        ) { path, uri ->


        }
    }




    override fun onDestroy() {
        destroy()
        super.onDestroy()
    }


    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onFinishFloatingView() {
        stopSelf()
    }


    override fun onTouchFinished(isFinishing: Boolean, x: Int, y: Int) {
        if (!isFinishing) {
            // Save the last position
            val editor = PreferenceManager.getDefaultSharedPreferences(this).edit()
            editor.putInt(PREF_KEY_LAST_POSITION_X, x)
            editor.putInt(PREF_KEY_LAST_POSITION_Y, y)
            editor.apply()
        }
    }


    private fun destroy() {

        if (mFloatingViewManager != null) {
            mFloatingViewManager!!.removeAllViewToWindow()
            mFloatingViewManager = null
        }
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            notificationManager.deleteNotificationChannel("my_service")
//        }
        projection!!.stop()
        notificationManager.cancelAll()
        stopForeground(true)
    }

    private fun createNotification(context: Context): Notification {

        var channelId = ""

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channelId = createNotificationChannel("my_service", "My Background Service")
        } else {
            channelId = ""
        }

        val builder = NotificationCompat.Builder(context, channelId)
        builder.setWhen(System.currentTimeMillis())
        builder.setSmallIcon(R.mipmap.ic_launcher)
        builder.setContentTitle("Title here")
        builder.setContentText("Context here")
        builder.setOngoing(true)
        builder.priority = NotificationCompat.PRIORITY_MIN
        builder.setCategory(NotificationCompat.CATEGORY_SERVICE)


        return builder.build()
    }

    private fun createNotificationChannel(channelId: String, channelName: String): String {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                channelId,
                channelName, NotificationManager.IMPORTANCE_NONE
            )
            chan.lightColor = Color.BLUE
            chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            service.createNotificationChannel(chan)
        }

        return channelId
    }


    private fun loadDynamicOptions() {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)

        val displayModeSettings = sharedPref.getString("settings_display_mode", "")
        if ("Always" == displayModeSettings) {
            mFloatingViewManager!!.setDisplayMode(FloatingViewManager.DISPLAY_MODE_SHOW_ALWAYS)
        } else if ("FullScreen" == displayModeSettings) {
            mFloatingViewManager!!.setDisplayMode(FloatingViewManager.DISPLAY_MODE_HIDE_FULLSCREEN)
        } else if ("Hide" == displayModeSettings) {
            mFloatingViewManager!!.setDisplayMode(FloatingViewManager.DISPLAY_MODE_HIDE_ALWAYS)
        }

    }


    private fun loadOptions(metrics: DisplayMetrics): FloatingViewManager.Options {
        val options = FloatingViewManager.Options()
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)

        // Shape
        val shapeSettings = sharedPref.getString("settings_shape", "")
        if ("Circle" == shapeSettings) {
            options.shape = FloatingViewManager.SHAPE_CIRCLE
        } else if ("Rectangle" == shapeSettings) {
            options.shape = FloatingViewManager.SHAPE_RECTANGLE
        }

        // Margin
        val marginSettings = sharedPref.getString("settings_margin", options.overMargin.toString())
        options.overMargin = Integer.parseInt(marginSettings)

        // MoveDirection
        val moveDirectionSettings = sharedPref.getString("settings_move_direction", "")
        if ("Default" == moveDirectionSettings) {
            options.moveDirection = FloatingViewManager.MOVE_DIRECTION_DEFAULT
        } else if ("Left" == moveDirectionSettings) {
            options.moveDirection = FloatingViewManager.MOVE_DIRECTION_LEFT
        } else if ("Right" == moveDirectionSettings) {
            options.moveDirection = FloatingViewManager.MOVE_DIRECTION_RIGHT
        } else if ("Nearest" == moveDirectionSettings) {
            options.moveDirection = FloatingViewManager.MOVE_DIRECTION_NEAREST
        } else if ("Fix" == moveDirectionSettings) {
            options.moveDirection = FloatingViewManager.MOVE_DIRECTION_NONE
        } else if ("Thrown" == moveDirectionSettings) {
            options.moveDirection = FloatingViewManager.MOVE_DIRECTION_THROWN
        }

        options.usePhysics = sharedPref.getBoolean("settings_use_physics", true)

        // Last position
        val isUseLastPosition = sharedPref.getBoolean("settings_save_last_position", false)
        if (isUseLastPosition) {
            val defaultX = options.floatingViewX
            val defaultY = options.floatingViewY
            options.floatingViewX = sharedPref.getInt(PREF_KEY_LAST_POSITION_X, defaultX)
            options.floatingViewY = sharedPref.getInt(PREF_KEY_LAST_POSITION_Y, defaultY)
        } else {
            // Init X/Y
            val initXSettings = sharedPref.getString("settings_init_x", "1")
            val initYSettings = sharedPref.getString("settings_init_y", "0.25")
            if (!TextUtils.isEmpty(initXSettings) && !TextUtils.isEmpty(initYSettings)) {
                val offset = (48 + 8 * metrics.density).toInt()
                options.floatingViewX =
                        (metrics.widthPixels * java.lang.Float.parseFloat(initXSettings) - offset).toInt()
                options.floatingViewY =
                        (metrics.heightPixels * java.lang.Float.parseFloat(initYSettings) - offset).toInt()
            }
        }

        // Initial Animation
        val animationSettings = sharedPref.getBoolean("settings_animation", options.animateInitialMove)
        options.animateInitialMove = animationSettings

        return options
    }

    companion object {

        /**
         * Intent key (Cutout safe area)
         */
        val EXTRA_CUTOUT_SAFE_AREA = "cutout_safe_area"

        /**
         * 通知ID
         */
        private val NOTIFICATION_ID = 908114

        /**
         * Prefs Key(Last position X)
         */
        private val PREF_KEY_LAST_POSITION_X = "last_position_x"

        /**
         * Prefs Key(Last position Y)
         */
        private val PREF_KEY_LAST_POSITION_Y = "last_position_y"


        private val EXTRA_RESULT_CODE = "result-code"
        private val EXTRA_DATA = "data"


        private val DISPLAY_NAME = "telecine"
        private val MIME_TYPE = "video/mp4"

    }
}
