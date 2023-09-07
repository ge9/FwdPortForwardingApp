/*
 * Fwd: the port forwarding app
 * Copyright (C) 2016  Elixsr Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.elixsr.portforwarder.ui.preferences


import android.view.View
import android.widget.ListView
import java.net.SocketException
import com.elixsr.portforwarder.R;
import com.elixsr.portforwarder.forwarding.ForwardingService;
import com.elixsr.portforwarder.ui.preferences.SettingsFragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;

import android.util.Log;
import android.widget.Toast;
import com.elixsr.portforwarder.ui.BaseActivity
import com.elixsr.portforwarder.util.InterfaceHelper
import com.elixsr.portforwarder.models.NetItemAdapter

/**
 * Created by Niall McShane on 29/02/2016.
 */
class NetworkActivity : BaseActivity() {
    private val TAG = "NetworkActivity"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.network_activity)
        val toolbar = actionBarToolbar
        setSupportActionBar(toolbar)
        toolbar?.setNavigationIcon(R.drawable.ic_arrow_back_24dp)
        toolbar?.setNavigationOnClickListener(View.OnClickListener { onBackPressed() })
    }

    protected override fun onStart() {
        super.onStart()
        try {
            val list: List<InterfaceHelper.InterfaceModel> = InterfaceHelper.generateInterfaceModelList()
            val itemAdapter = NetItemAdapter(this@NetworkActivity, R.layout.netinterface_item, list)
            val listView = findViewById<View>(R.id.netInfo) as ListView
            listView.adapter = itemAdapter
        } catch (e: SocketException) {
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
    }
}