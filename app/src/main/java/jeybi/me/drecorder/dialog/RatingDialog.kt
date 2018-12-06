package jeybi.me.drecorder.dialog

import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import jeybi.me.drecorder.R
import android.content.Intent
import android.net.Uri


class RatingDialog : DialogFragment() {


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_rate,container,false)

        dialog.window.setBackgroundDrawableResource(android.R.color.transparent)
        isCancelable = true

        val textViewRate = view.findViewById<TextView>(R.id.textViewRate)

        textViewRate.setOnClickListener{
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=jeybi.me.drecorder"))
            startActivity(browserIntent)
            dialog.dismiss()
        }

        return view
    }

}