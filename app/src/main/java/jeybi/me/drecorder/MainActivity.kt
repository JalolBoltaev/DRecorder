package jeybi.me.drecorder

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.app.ActivityManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Picture
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.animation.AnticipateInterpolator
import android.view.animation.AnticipateOvershootInterpolator
import butterknife.OnCheckedChanged
import kotlinx.android.synthetic.main.activity_main.*
import android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION
import android.provider.Settings.canDrawOverlays
import android.os.Build
import android.os.Handler
import android.provider.MediaStore
import android.provider.Settings
import android.support.annotation.NonNull
import android.support.v4.app.ActivityCompat
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import android.text.format.DateUtils
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.CompoundButton
import android.widget.RemoteViews
import android.widget.Toast
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.reward.RewardItem
import com.google.android.gms.ads.reward.RewardedVideoAd
import com.google.android.gms.ads.reward.RewardedVideoAdListener
import jeybi.me.drecorder.dialog.DeleteDialog
import jeybi.me.drecorder.dialog.RatingDialog
import jeybi.me.drecorder.model.RecordedVideo
import jp.co.recruit_lifestyle.android.floatingview.FloatingViewManager
import java.io.File


class MainActivity : AppCompatActivity(),VideosAdapter.DeleteClickListener,DeleteDialog.RemoveDialogListener ,
    RewardedVideoAdListener {


    override fun onRewarded(reward: RewardItem) {

        advBubble.visibility = View.GONE
    }

    override fun onRewardedVideoAdLeftApplication() {
    }

    override fun onRewardedVideoAdClosed() {
    }

    override fun onRewardedVideoAdFailedToLoad(errorCode: Int) {
        Log.d("ADVG","ERROR CODE ${errorCode}")
    }

    override fun onRewardedVideoAdLoaded() {
        advBubble.visibility = View.VISIBLE
    }

    override fun onRewardedVideoAdOpened() {
    }

    override fun onRewardedVideoStarted() {
    }

    override fun onRewardedVideoCompleted() {
    }

    override fun onPause() {
        super.onPause()
        mRewardedVideoAd.pause(this)
    }

    override fun onResume() {
        super.onResume()
        mRewardedVideoAd.resume(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        mRewardedVideoAd.destroy(this)
    }


    private lateinit var mRewardedVideoAd: RewardedVideoAd




    var isSettingsVisible = false

    private var videoSizePercentageAdapter: VideoSizePercentageAdapter? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setUpUi()
        loadSettings()

        MobileAds.initialize(this, "ca-app-pub-7562715279904690~3632102028")
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
        adView.adListener = object: AdListener() {
            override fun onAdLoaded() {
              adView.visibility = View.VISIBLE
            }

            override fun onAdFailedToLoad(errorCode : Int) {
                Log.d("ADVG","ERROR CODE BANNER ${errorCode}")
            }

            override fun onAdOpened() {
                // Code to be executed when an ad opens an overlay that
                // covers the screen.
            }

            override fun onAdLeftApplication() {
                // Code to be executed when the user has left the app.
            }

            override fun onAdClosed() {
                // Code to be executed when when the user is about to return
                // to the app after tapping on an ad.
            }
        }


        imageViewStar.setOnClickListener {
            RatingDialog().show(supportFragmentManager,"RatingDialog")
        }

        CheatSheet.setup(floatingActionButton)



        videoSizePercentageAdapter = VideoSizePercentageAdapter(this)


        mRewardedVideoAd = MobileAds.getRewardedVideoAdInstance(this)
        mRewardedVideoAd.rewardedVideoAdListener = this
        mRewardedVideoAd.loadAd("ca-app-pub-7562715279904690/1675365983",
            AdRequest.Builder().build())



    }

    private fun loadSettings() {
        val preferences = getSharedPreferences("SETTINGS", Context.MODE_PRIVATE)
        val isCountDownEnabled = preferences.getBoolean("countdown",true)
        val isSoundEnabled = preferences.getBoolean("sound",true)

        switch_show_countdown.isChecked = isCountDownEnabled
        switch_show_countdown.setOnCheckedChangeListener { p0, p1 -> preferences.edit().putBoolean("countdown",p1).apply() }

        switch_sound.isChecked = isSoundEnabled
        switch_sound.setOnCheckedChangeListener { p0, p1 -> preferences.edit().putBoolean("sound",p1).apply() }


    }

    private fun setUpUi() {
        lottiePlane.speed = 0.3f
        layoutSettings.animate().translationY(700f).scaleX(0.6f).start()


        bottomRight.setOnClickListener {
            if (!isSettingsVisible) {
                layoutSettings.animate().translationY(0f).scaleX(1f).setInterpolator(AnticipateOvershootInterpolator())
                    .setDuration(400).start()
                isSettingsVisible = true
            } else {
                layoutSettings.animate().translationY(700f).scaleX(0.6f).setInterpolator(AnticipateInterpolator())
                    .setDuration(400).start()
                isSettingsVisible = false

            }
        }

        floatingActionButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    startActivityForResult(intent, 1111)
                } else {
                    CaptureHelper.fireScreenCaptureIntent(this)
                }
            }

        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,  arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.RECORD_AUDIO),
                23)
            return
        }else{
            loadVideos()
        }

        linearMoreApps.setOnClickListener {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/dev?id=6345937546471067075"))
            startActivity(browserIntent)
        }


        imageViewAdv.setOnClickListener {
            if (mRewardedVideoAd.isLoaded) {
                mRewardedVideoAd.show()
            }
        }
    }




    fun loadVideos() {

        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val condition = MediaStore.Video.Media.DATA + " like?"
        val selectionArguments = arrayOf("%Movies/DRecorder%")
        val sortOrder = MediaStore.Video.Media.DATE_TAKEN + " DESC"
        val projection = arrayOf<String>(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.VideoColumns.DURATION,
            MediaStore.Video.VideoColumns.SIZE,
            MediaStore.Images.Media.DATA
        )
        val cursor = contentResolver.query(uri, projection, condition, selectionArguments, sortOrder)


        val idColumn = cursor!!.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
        val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.SIZE)
        val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION)



        val videos = ArrayList<RecordedVideo>()
        videos.add(RecordedVideo("","","", "",Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888)))

        if (cursor != null) {
            val resolver = applicationContext.contentResolver
            while (cursor.moveToNext()) {
                val filePath = cursor.getString(pathColumn)
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val size = cursor.getString(sizeColumn)
                val duration = cursor.getString(durationColumn)
                val bitmap = MediaStore.Video.Thumbnails.getThumbnail(
                    resolver,
                    id,
                    MediaStore.Video.Thumbnails.MICRO_KIND, null
                )

                Log.d("LLLLLL",filePath)

                videos.add(RecordedVideo(name,filePath,size,duration,bitmap))


            }

            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = VideosAdapter(this,videos,this)


            cursor.close()
        }

    }


    override fun onRemoveClicked(path : String) {
        val file = File(path)
        file.canonicalFile.delete()
        sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)))


        Handler().postDelayed({
            loadVideos()
        },1000)

    }


    override fun onDeleteClicked(position: Int, path: String) {

        DeleteDialog(this,path).show(supportFragmentManager,"DeleteDialog")

    }



    @TargetApi(Build.VERSION_CODES.M)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!CaptureHelper.handleActivityResult(this, requestCode, resultCode, data!!)) {
            super.onActivityResult(requestCode, resultCode, data)
        }

        if (requestCode == 1111) {
            if (Settings.canDrawOverlays(this)) {
                CaptureHelper.fireScreenCaptureIntent(this)
            }

        }

    }

  override  fun onRequestPermissionsResult(requestCode:Int, @NonNull permissions:Array<String>, @NonNull grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 23)
        {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            {
                loadVideos()
            }
            else
            {
                // User refused to grant permission.
            }
        }
    }



    override fun onStop() {
        super.onStop()
//        if (hideFromRecentsPreference!!.get() && !isChangingConfigurations) {
//            finishAndRemoveTask()
//        }
    }


    public fun startFloatingViewService(activity: Activity?, isCustomFloatingView: Boolean, data: Intent?) {
        // *** You must follow these rules when obtain the cutout(FloatingViewManager.findCutoutSafeArea) ***
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // 1. 'windowLayoutInDisplayCutoutMode' do not be set to 'never'
            if (activity!!.window.attributes.layoutInDisplayCutoutMode == WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER) {
                throw RuntimeException("'windowLayoutInDisplayCutoutMode' do not be set to 'never'")
            }
            // 2. Do not set Activity to landscape
            if (activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                throw RuntimeException("Do not set Activity to landscape")
            }
        }

        // launch service
        val service: Class<out Service> = CustomFloatingViewService::class.java
        val key: String = CustomFloatingViewService.EXTRA_CUTOUT_SAFE_AREA
        val intent = Intent(activity, service)
        intent.putExtra("result-code", Activity.RESULT_OK)
        intent.putExtra("data", data)
        intent.putExtra(key, FloatingViewManager.findCutoutSafeArea(activity!!))
        ContextCompat.startForegroundService(activity, intent)
    }


}
