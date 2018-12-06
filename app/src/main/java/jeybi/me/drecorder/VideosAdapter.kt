package jeybi.me.drecorder

import android.content.Context
import android.graphics.Color
import android.media.Image
import android.support.constraint.ConstraintLayout
import android.support.design.card.MaterialCardView
import android.support.v7.widget.RecyclerView
import android.text.Layout
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import jeybi.me.drecorder.model.RecordedVideo
import java.text.FieldPosition
import android.support.v4.content.ContextCompat.startActivity
import android.content.pm.PackageManager
import android.support.v4.content.FileProvider
import android.content.Intent
import android.net.Uri
import java.io.File
import android.support.v4.content.ContextCompat.startActivity
import java.text.DecimalFormat
import java.util.*
import java.util.concurrent.TimeUnit


class VideosAdapter(val context: Context,val videos : ArrayList<RecordedVideo>,val deleteClickListener: DeleteClickListener) :
    RecyclerView.Adapter<VideosAdapter.VideoHolder>() {


    override fun getItemViewType(position: Int): Int {
        return if (position == 0)
            0
        else
            1
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, position: Int): VideoHolder {

        val view: View? = if (position == 0)
            LayoutInflater.from(context).inflate(R.layout.item_big, viewGroup, false)
        else
            LayoutInflater.from(context).inflate(R.layout.item_small, viewGroup, false)

        return VideoHolder(view!!)
    }

    override fun getItemCount(): Int {
        return videos.size
    }

    override fun onBindViewHolder(holder: VideoHolder, position: Int) {
        val video = videos[position]

        if (position != 0) {

            when (position % 4) {
                0 -> holder.backgroundLayout.setBackgroundResource(R.drawable.bc_child_4)
                1 -> holder.backgroundLayout.setBackgroundResource(R.drawable.bc_child_1)
                2 -> holder.backgroundLayout.setBackgroundResource(R.drawable.bc_child_2)
                3 -> holder.backgroundLayout.setBackgroundResource(R.drawable.bc_child_3)
            }

            val decimalFormat = DecimalFormat("0.00")

            holder.imageViewVideo.setImageBitmap(video.thumbnail)
            holder.textViewTitle.text = video.title
            holder.textViewPath.text = "Movies/DRecorder"
            holder.textViewSize.text = "${decimalFormat.format(video.size.toFloat()/(1024*1024))} mb"
            holder.textViewDuration.text = String.format("%d min : %d sec",
                TimeUnit.MILLISECONDS.toMinutes(video.duration.toLong()),
                TimeUnit.MILLISECONDS.toSeconds(video.duration.toLong()) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(video.duration.toLong()))
            )

            holder.backgroundLayout.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW)

                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

                val uri = Uri.parse((video.path))
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setDataAndType(uri, "video/*")

                val pm = context.packageManager
                if (intent.resolveActivity(pm) != null) {
                    context.startActivity(intent)
                }
            }

            holder.imageViewShare.setOnClickListener {


                val sendIntent = Intent(Intent.ACTION_SEND)
                sendIntent.type = "video/*"
                sendIntent.putExtra(Intent.EXTRA_SUBJECT, "Video")
                sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse(video.path))
                sendIntent.putExtra(Intent.EXTRA_TEXT, "This video was recorded using DRecorder.\nGet it here : bit.ly/2E2JoDn")
                context.startActivity(Intent.createChooser(sendIntent, video.title))

            }

            holder.imageViewDelete.setOnClickListener {
                deleteClickListener.onDeleteClicked(position,video.path)
            }



        }else{
            holder.textViewVideoCount.text = "${videos.size-1} recorded videos"
            val date = Calendar.getInstance()
            val day = date.get(Calendar.DAY_OF_MONTH)
            val month = date.get(Calendar.MONTH)
            val year = date.get(Calendar.YEAR)

            holder.textViewDate.text = "${day} ${getMonthInString(month)} ${year}"
        }

    }

    fun getMonthInString(month : Int) : String{
        return when(month){
            0->"JAN"
            1->"FEB"
            2->"MAR"
            3->"APR"
            4->"MAY"
            5->"JUN"
            6->"JUL"
            7->"AUG"
            8->"SEP"
            9->"OCT"
            10->"NOV"
            11->"DEC"
            else -> ""
        }
    }

    class VideoHolder(view: View) : RecyclerView.ViewHolder(view) {

        val textViewVideoCount = view.findViewById<TextView>(R.id.textViewVideoCount)
        val textViewDate = view.findViewById<TextView>(R.id.textViewDate)

        val backgroundLayout = view.findViewById<ConstraintLayout>(R.id.layoutBackground)!!
        val imageViewVideo = view.findViewById<ImageView>(R.id.imageViewVideo)
        val textViewTitle = view.findViewById<TextView>(R.id.textViewTitle)
        val textViewPath = view.findViewById<TextView>(R.id.textViewPath)
        val textViewSize = view.findViewById<TextView>(R.id.textViewSize)
        val imageViewShare = view.findViewById<ImageView>(R.id.imageViewShare)
        val imageViewDelete = view.findViewById<ImageView>(R.id.imageViewDelete)
        val textViewDuration = view.findViewById<TextView>(R.id.textViewDuration)
    }


    interface DeleteClickListener{
        fun onDeleteClicked(position: Int,path: String)
    }

}