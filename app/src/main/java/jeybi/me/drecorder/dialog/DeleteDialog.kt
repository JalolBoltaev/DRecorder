package jeybi.me.drecorder.dialog

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import jeybi.me.drecorder.R
import android.content.Intent
import android.net.Uri


@SuppressLint("ValidFragment")
class DeleteDialog(val removeDialogListener: RemoveDialogListener,val path : String) : DialogFragment() {


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_delete,container,false)

        dialog.window.setBackgroundDrawableResource(android.R.color.transparent)
        isCancelable = true

        val textViewRate = view.findViewById<TextView>(R.id.textViewRate)

        textViewRate.setOnClickListener{
            removeDialogListener.onRemoveClicked(path)
            dialog.dismiss()
        }

        return view
    }

    interface RemoveDialogListener{
        fun onRemoveClicked(path: String)
    }

}