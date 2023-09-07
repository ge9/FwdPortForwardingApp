package com.elixsr.portforwarder.models

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.elixsr.portforwarder.R
import com.elixsr.portforwarder.util.InterfaceHelper.InterfaceModel

class NetItemAdapter(context: Context?, private val layoutId: Int, list: List<InterfaceModel?>?) : ArrayAdapter<InterfaceModel?>(context!!, layoutId, list!!) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val item = getItem(position)
        val view = LayoutInflater.from(context).inflate(layoutId, parent, false)
        val nameView = view.findViewById<View>(R.id.netName) as TextView
        val ipView = view.findViewById<View>(R.id.ipAddr) as TextView
        if (item != null) {
            nameView.text = item.name
        }
        if (item != null) {
            ipView.text = item.inetAddress.hostAddress
        }
        return view
    }
}