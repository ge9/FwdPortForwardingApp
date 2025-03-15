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

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.preference.Preference
import android.preference.Preference.OnPreferenceClickListener
import android.preference.PreferenceFragment
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.elixsr.portforwarder.R
import com.elixsr.portforwarder.adapters.RuleListJsonValidator
import com.elixsr.portforwarder.adapters.RuleListTargetJsonSerializer
import com.elixsr.portforwarder.dao.RuleDao
import com.elixsr.portforwarder.db.RuleContract
import com.elixsr.portforwarder.db.RuleDbHelper
import com.elixsr.portforwarder.forwarding.ForwardingManager
import com.elixsr.portforwarder.models.RuleModel
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.net.InetSocketAddress

/**
 * Created by Niall McShane on 29/02/2016.
 */
class SettingsFragment : PreferenceFragment() {
    private lateinit var localBroadcastManager: LocalBroadcastManager
    private lateinit var forwardingManager: ForwardingManager
    private lateinit var clearRulesButton: Preference
    private lateinit var versionNamePreference: Preference
    private lateinit var importRulesPreference: Preference
    private lateinit var sharedPreferencesListener: OnSharedPreferenceChangeListener
    private lateinit var gson: Gson
    private lateinit var ruleDao: RuleDao
    private lateinit var toast: Toast
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.preferences)
        ruleDao = RuleDao(RuleDbHelper(activity))
        gson = GsonBuilder()
                .registerTypeAdapter(InetSocketAddress::class.java, RuleListTargetJsonSerializer())
                .registerTypeAdapter(RuleModel::class.java, RuleListJsonValidator())
                .excludeFieldsWithoutExposeAnnotation()
                .create()
        forwardingManager = ForwardingManager.instance
        localBroadcastManager = LocalBroadcastManager.getInstance(activity.baseContext)
        toast = Toast.makeText(activity, "", Toast.LENGTH_SHORT)
        clearRulesButton = findPreference(getString(R.string.pref_clear_rules))
        clearRulesButton.onPreferenceClickListener = OnPreferenceClickListener { preference: Preference? ->
            // Code for what you want it to do
            AlertDialog.Builder(activity)
                    .setTitle(R.string.alert_dialog_delete_all_rules_title)
                    .setMessage(R.string.alert_dialog_delete_all_rules_text)
                    .setPositiveButton(android.R.string.yes) { dialog: DialogInterface?, which: Int ->

                        // Set up the database
                        val db = RuleDbHelper(activity).readableDatabase
                        db.delete(RuleContract.RuleEntry.TABLE_NAME, null, null)
                        db.close()
                        clearRulesButton.isEnabled = false
                        Toast.makeText(activity, CLEAR_RULES_COMPLETE_MESSAGE,
                                Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(android.R.string.no) { dialog: DialogInterface?, which: Int -> }
                    .show()
            true
        }
        versionNamePreference = findPreference(getString(R.string.pref_version))
        versionNamePreference.onPreferenceClickListener = object : OnPreferenceClickListener {
            var versionPrefClicks = 0
            override fun onPreferenceClick(preference: Preference): Boolean {
                if (versionPrefClicks in 2..3) {
                    toast.setText((4 - versionPrefClicks).toString() + " more...")
                    toast.show()
                }
                if (++versionPrefClicks == 5) {
                    versionPrefClicks = 0
                    toast.setText("...")
                    toast.show()
                    val advancedSettingsActivity = Intent(activity, AdvancedSettingsActivity::class.java)
                    startActivity(advancedSettingsActivity)
                    return true
                }
                return false
            }
        }
        importRulesPreference = findPreference(getString(R.string.pref_import))
        importRulesPreference.onPreferenceClickListener = OnPreferenceClickListener { preference: Preference? ->
            importRules()
            false
        }
        val exportRulesPreference = findPreference(getString(R.string.pref_export))
        exportRulesPreference.onPreferenceClickListener = OnPreferenceClickListener { preference: Preference? ->
            exportRules()
            false
        }


        // Set up click of about elixsr button - show webview
        val aboutElixsrButton = findPreference(getString(R.string.pref_about_link))
        aboutElixsrButton.onPreferenceClickListener = OnPreferenceClickListener { preference: Preference? ->
            //code for what you want it to do
            val aboutActivityIntent = Intent(activity, AboutElixsrActivity::class.java)
            startActivity(aboutActivityIntent)
            true
        }
        val sourceCodeButton = findPreference("pref_source_code")
        sourceCodeButton.onPreferenceClickListener = OnPreferenceClickListener { preference: Preference? ->
            val sourceCodeIntent = Intent(activity, SourceCodeActivity::class.java)
            startActivity(sourceCodeIntent)
            true
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        // Recreate our activity if we changed to dark theme
        sharedPreferencesListener = OnSharedPreferenceChangeListener { _: SharedPreferences?, key: String? ->
            if (key == "pref_dark_theme") {
                val intent = Intent()
                intent.action = DARK_MODE_BROADCAST
                localBroadcastManager.sendBroadcast(intent)
            }
        }

        // Prevent garbage collection
        preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferencesListener)
    }

    override fun onStart() {
        super.onStart()
        if (forwardingManager.isEnabled) {
            clearRulesButton.isEnabled = false
            importRulesPreference.isEnabled = false
        } else {
            clearRulesButton.isEnabled = true
            importRulesPreference.isEnabled = true
        }
        var versionName = "Version "
        versionName = try {
            versionName + activity.baseContext.packageManager
                    .getPackageInfo(activity.baseContext.packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            Log.i(TAG, "Application Version could not be found.", e)
            versionName + "not found"
        }
        versionNamePreference.title = versionName
    }

    override fun onDestroy() {
        super.onDestroy()

        // Ensure we unregister our previous listener - as it now points to a null activity
        preferenceScreen.sharedPreferences.unregisterOnSharedPreferenceChangeListener(sharedPreferencesListener)
    }

    private fun importRules() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "application/json"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        try {
            startActivityForResult(Intent.createChooser(intent, "Select a rule list to import"), RULE_LIST_CODE)
        } catch (ex: ActivityNotFoundException) {
            Toast.makeText(activity, "A file manager is required to import rule lists.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportRules() {
        if (ruleDao.allRuleModels.size > 0) {
            // Lets create out file to store our data
            val outputDir = activity.cacheDir
            val ruleList = ruleListToJsonString()
            try {
                val outputFile = File.createTempFile("fwd_rule_list", ".json", outputDir)
                outputFile.createNewFile()
                val writer = FileWriter(outputFile)
                writer.append(ruleList)

                // Ensure the writer is closed - assuming its blocking
                writer.close()

                // Everything good, lets send an intent
                val intent = Intent(Intent.ACTION_SEND)
                intent.type = "application/json"
                intent.putExtra(Intent.EXTRA_SUBJECT, "Fwd Rule List")
                intent.putExtra(Intent.EXTRA_TEXT, "Your fwd rules have been attached with the name '" + outputFile.name + "'.")
                intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(activity.applicationContext, activity.applicationContext.packageName + ".util.provider", outputFile))
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(Intent.createChooser(intent, getString(R.string.export_rules_action_title)))
                Log.i(TAG, "onDataChange: URI " + Uri.fromFile(outputFile).toString())
            } catch (e: IOException) {
                Log.e(TAG, "onDataChange: error trying to create file to store exported data", e)
                Toast.makeText(activity.baseContext, "Error when trying to export dreams.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(activity.baseContext, "No rules to export.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun ruleListToJsonString(): String {
        val ruleModels = ruleDao.allRuleModels
        return gson.toJson(ruleModels)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (resultCode != Activity.RESULT_CANCELED && requestCode == RULE_LIST_CODE && data.data != null) {
            val importRulesActivityIntent = Intent(activity, ImportRulesActivity::class.java)
            importRulesActivityIntent.putExtra(ImportRulesActivity.IMPORTED_RULE_DATA, data.data.toString())
            startActivity(importRulesActivityIntent)
        }
    }

    companion object {
        private const val TAG = "SettingsFragment"
        private const val CLEAR_RULES_COMPLETE_MESSAGE = "All rules have been removed"
        const val DARK_MODE_BROADCAST = "com.elixsr.DARK_MODE_TOGGLE"
        private const val RULE_LIST_CODE = 1
    }
}