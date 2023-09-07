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
package com.elixsr.portforwarder.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.percentlayout.widget.PercentRelativeLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.elixsr.portforwarder.R
import com.elixsr.portforwarder.adapters.RuleListAdapter
import com.elixsr.portforwarder.dao.RuleDao
import com.elixsr.portforwarder.db.RuleDbHelper
import com.elixsr.portforwarder.forwarding.ForwardingManager
import com.elixsr.portforwarder.forwarding.ForwardingService
import com.elixsr.portforwarder.models.RuleModel
import com.elixsr.portforwarder.ui.intro.MainIntro
import com.elixsr.portforwarder.ui.preferences.HelpActivity
import com.elixsr.portforwarder.ui.preferences.NetworkActivity
import com.elixsr.portforwarder.ui.preferences.SettingsActivity
import com.elixsr.portforwarder.ui.rules.NewRuleActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar


class MainActivity : BaseActivity() {
    private lateinit var ruleModels: MutableList<RuleModel>
    private lateinit var mRecyclerView: RecyclerView
    private lateinit var forwardingManager: ForwardingManager
    private lateinit var coordinatorLayout: CoordinatorLayout
    private lateinit var fab: FloatingActionButton
    private lateinit var forwardingServiceIntent: Intent
    private lateinit var ruleDao: RuleDao
    private lateinit var mRuleListEmptyView: PercentRelativeLayout
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        forwardingManager = ForwardingManager.instance
        setContentView(R.layout.activity_main)
        setSupportActionBar(actionBarToolbar)
        // getActionBarToolbar().setTitle(R.string.app_tag);
        actionBarToolbar!!.title = ""
        supportActionBar!!.setDisplayShowHomeEnabled(true)
        supportActionBar!!.setIcon(R.drawable.ic_nav_logo)

        // Determine if this is first start - and whether to show app intro
        onFirstStart()
        val newRuleIntent = Intent(this, NewRuleActivity::class.java)

        // Move to the new rule activity
        fab = findViewById(R.id.fab)
        fab.setOnClickListener { startActivity(newRuleIntent) }

        // Hide the fab if forwarding is enabled
        // the user should not be able to add/delete rules
        if (forwardingManager.isEnabled) {
            //if the forwarding service is enabled, then we should ensure not to show the fab
            fab.hide()
        } else {
            fab.show()
        }

        // Get all models from the data store
        ruleDao = RuleDao(RuleDbHelper(this))
        ruleModels = ruleDao.allRuleModels

        // Set up rule list and empty view
        mRecyclerView = findViewById(R.id.rule_recycler_view)
        mRuleListEmptyView = findViewById(R.id.rule_list_empty_view)

        // Use this setting to improve performance if you know that changes
        // In content do not change the layout size of the RecyclerView
        mRecyclerView.setHasFixedSize(true)

        // Use a linear layout manager
        val mLayoutManager: RecyclerView.LayoutManager = LinearLayoutManager(this)
        mRecyclerView.layoutManager = mLayoutManager

        // Specify an adapter (see also next example)
        ruleListAdapter = RuleListAdapter(ruleModels, forwardingManager)
        mRecyclerView.adapter = ruleListAdapter

        // Store the coordinator layout for snackbar
        coordinatorLayout = findViewById(R.id.main_coordinator_layout)
        forwardingServiceIntent = Intent(this, ForwardingService::class.java)


        /*
            Service stuff
         */

        // Handle intents
        val mStatusIntentFilter = IntentFilter(
                ForwardingService.BROADCAST_ACTION)

        // Instantiates a new ForwardingServiceResponseReceiver
        val forwardingServiceResponseReceiver = ForwardingServiceResponseReceiver()

        // Registers the ForwardingServiceResponseReceiver and its intent filters
        LocalBroadcastManager.getInstance(this).registerReceiver(
                forwardingServiceResponseReceiver,
                mStatusIntentFilter)
        val getPrefs = PreferenceManager
                .getDefaultSharedPreferences(baseContext)

        if (getPrefs.getBoolean("pref_start_on_app_start", false)) {

            Snackbar.make(coordinatorLayout, R.string.snackbar_port_forwarding_started_text, Snackbar.LENGTH_LONG)
                    .setAction("Stop", null).show()
            fab.hide()
            startService(forwardingServiceIntent)
            Log.i(TAG, "Automatically starting service")
        }
        Log.i(TAG, "Finished onCreate")
    }

    public override fun onResume() {
        super.onResume()
        ruleModels.clear()
        ruleModels.addAll(ruleDao.allRuleModels)
        ruleListAdapter!!.notifyDataSetChanged()
        invalidateOptionsMenu()

        // Decide whether to show the rule list or the empty view
        if (ruleModels.isEmpty()) {
            mRecyclerView.visibility = View.GONE
            mRuleListEmptyView.visibility = View.VISIBLE
        } else {
            mRecyclerView.visibility = View.VISIBLE
            mRuleListEmptyView.visibility = View.GONE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        menu.findItem(R.id.action_toggle_forwarding).title = generateForwardingActionMenuText(forwardingManager.isEnabled)

        // Setup the start forwarding button
        val toggleForwarding = menu.findItem(R.id.action_toggle_forwarding)
        var enabledRuleModels = 0
        for (ruleModel in ruleModels) {
            if (ruleModel.isEnabled) {
                ++enabledRuleModels
            }
        }

        // It should not be able to start if there are no rules
        toggleForwarding.isVisible = enabledRuleModels > 0
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        when (item.itemId) {
            R.id.action_settings -> {
                val prefIntent = Intent(this, SettingsActivity::class.java)
                startActivity(prefIntent)
            }

            R.id.action_toggle_forwarding -> handleForwardingButton(item)
            R.id.action_help -> {
                val helpActivityIntent = Intent(this, HelpActivity::class.java)
                startActivity(helpActivityIntent)
            }

            R.id.action_network -> {
            val networkActivityIntent = Intent(this, NetworkActivity::class.java)
                startActivity(networkActivityIntent)
        }

        }
        return super.onOptionsItemSelected(item)
    }

    private fun handleForwardingButton(item: MenuItem) {
        if (!forwardingManager.isEnabled) {
            // startPortForwarding();
            Snackbar.make(coordinatorLayout, R.string.snackbar_port_forwarding_started_text, Snackbar.LENGTH_LONG)
                    .setAction("Stop", null).show()
            fab.hide()
            startService(forwardingServiceIntent)
        } else {
            // Stop forwarding
            fab.show()
            Snackbar.make(coordinatorLayout, R.string.snackbar_port_forwarding_stopped_text, Snackbar.LENGTH_LONG).show()
            stopService(forwardingServiceIntent)
        }
        Log.i(TAG, "Forwarding Enabled: " + forwardingManager.isEnabled)
        item.title = generateForwardingActionMenuText(forwardingManager.isEnabled)
    }

    private fun generateForwardingActionMenuText(forwardingFlag: Boolean): String {
        return if (forwardingFlag) {
            this.getString(R.string.action_stop_forwarding)
        } else this.getString(R.string.action_start_forwarding)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(FORWARDING_MANAGER_KEY, forwardingManager)
    }

    override fun onDestroy() {
        super.onDestroy()

        // stopService(forwardingServiceIntent);
        Log.i(TAG, "Destroyed")
    }

    // Broadcast receiver for receiving status updates from the IntentService
    private inner class ForwardingServiceResponseReceiver  // Prevents instantiation
        : BroadcastReceiver() {
        // Called when the BroadcastReceiver gets an Intent it's registered to receive
        override fun onReceive(context: Context, intent: Intent) {
            /*
             * Handle Intents here.
             */
            if (intent.extras!!.containsKey(ForwardingService.PORT_FORWARD_SERVICE_STATE)) {
                Log.i(TAG, "Response from ForwardingService, Forwarding status has changed.")
                Log.i(TAG, "Forwarding status has changed to " + intent.extras!!.getBoolean(ForwardingService.PORT_FORWARD_SERVICE_STATE))
                invalidateOptionsMenu()
            }
            if (intent.extras!!.containsKey(ForwardingService.PORT_FORWARD_SERVICE_ERROR_MESSAGE)) {
                Toast.makeText(context, intent.extras!!.getString(ForwardingService.PORT_FORWARD_SERVICE_ERROR_MESSAGE),
                        Toast.LENGTH_SHORT).show()
                Snackbar.make(coordinatorLayout, R.string.snackbar_port_forwarding_stopped_text, Snackbar.LENGTH_SHORT).show()
                fab.show()
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    private fun onFirstStart() {
        // Declare a new thread to do a preference check
        val t = Thread {

            // Initialize SharedPreferences
            val getPrefs = PreferenceManager
                    .getDefaultSharedPreferences(baseContext)

            // Create a new boolean and preference and set it to true
            val isFirstStart = getPrefs.getBoolean("firstStart", true)

            // If the activity has never started before...
            if (isFirstStart) {

                // Launch app intro
                val i = Intent(this@MainActivity, MainIntro::class.java)
                startActivity(i)

                // Make a new preferences editor
                val e = getPrefs.edit()

                // Edit preference to make it false because we don't want this to run again
                e.putBoolean("firstStart", false)

                // Apply changes
                e.apply()
            }
        }

        // Start the thread
        t.start()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val FORWARDING_MANAGER_KEY = "ForwardingManager"
        private var ruleListAdapter: RuleListAdapter? = null
    }
}