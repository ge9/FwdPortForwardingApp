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
package com.elixsr.portforwarder.ui.rules

import android.content.DialogInterface
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.elixsr.core.common.widgets.SwitchBar
import com.elixsr.portforwarder.R
import com.elixsr.portforwarder.db.RuleContract
import com.elixsr.portforwarder.db.RuleDbHelper
import com.elixsr.portforwarder.models.RuleModel
import com.elixsr.portforwarder.ui.MainActivity
import com.elixsr.portforwarder.util.InterfaceHelper.correctInterfaceNameAndAddr
import com.elixsr.portforwarder.util.RuleHelper
import com.elixsr.portforwarder.util.RuleHelper.cursorToRuleModel
import com.elixsr.portforwarder.util.RuleHelper.getRuleProtocolFromModel
import com.elixsr.portforwarder.util.RuleHelper.ruleModelToContentValues
import com.google.android.material.textfield.TextInputEditText

/**
 * Created by Niall McShane on 02/03/2016.
 */
class EditRuleActivity : BaseRuleActivity() {
    private lateinit var ruleModel: RuleModel
    private var ruleModelId: Long = 0
    private lateinit var db: SQLiteDatabase
    private lateinit var switchBar: SwitchBar
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If we can't locate the id, then we can't continue
        if (!intent.extras!!.containsKey(RuleHelper.RULE_MODEL_ID)) {

            /// Show toast containing message to the user
            Toast.makeText(this, NO_RULE_ID_FOUND_TOAST_MESSAGE,
                    Toast.LENGTH_SHORT).show()
            Log.e(TAG, NO_RULE_ID_FOUND_LOG_MESSAGE)
            onBackPressed()

            // Return from the method - ensure we don't continue
            return
        }
        ruleModelId = intent.extras!!.getLong(RuleHelper.RULE_MODEL_ID)
        setContentView(R.layout.edit_rule_activity)

        // Set up toolbar
        val toolbar = actionBarToolbar
        setSupportActionBar(toolbar)
        toolbar!!.setNavigationIcon(R.drawable.ic_close_24dp)
        toolbar.setNavigationOnClickListener { v: View? -> onBackPressed() }


        // Use the base class to construct the common UI
        constructDetailUi()

        //TODO: move this
        db = RuleDbHelper(this).readableDatabase
        val cursor = db.query(
                RuleContract.RuleEntry.TABLE_NAME,
                RuleDbHelper.generateAllRowsSelection(),
                RuleContract.RuleEntry.COLUMN_NAME_RULE_ID + "=?", arrayOf(ruleModelId.toString()),
                null,
                null,
                null
        )
        cursor.moveToFirst()
        ruleModel = cursorToRuleModel(cursor)
        Log.i(TAG, java.lang.Boolean.toString(ruleModel.isEnabled))
        // Close the DB
        cursor.close()
        db.close()

        // Set up the switchBar for enabling/disabling
        switchBar = findViewById(R.id.switch_bar)
        switchBar.show()
        switchBar.isChecked = ruleModel.isEnabled
        /*
        Set the text fields content
         */
        val newRuleNameEditText = findViewById<TextInputEditText>(R.id.new_rule_name)
        newRuleNameEditText.setText(ruleModel.name)
        val newRuleFromPortEditText = findViewById<TextInputEditText>(R.id.new_rule_from_port)
        newRuleFromPortEditText.setText(ruleModel.fromPort.toString())
        val newRuleTargetIpAddressEditText = findViewById<TextInputEditText>(R.id.new_rule_target_ip_address)
        newRuleTargetIpAddressEditText.setText(ruleModel.targetIpAddress)
        val newRuleTargetPortEditText = findViewById<TextInputEditText>(R.id.new_rule_target_port)
        newRuleTargetPortEditText.setText(ruleModel.targetPort.toString())

        /*
        Set the spinners content
         */
        //from interface spinner
        Log.i(TAG, "FROM SPINNER : $fromInterfaceSpinner")
        Log.i(TAG, "FROM INTERFACE : " + ruleModel.fromInterfaceName)
        fromInterfaceSpinner.setSelection(fromSpinnerAdapter!!.getPosition(correctInterfaceNameAndAddr(ruleModel.fromInterfaceName!!)))

        // Protocol spinner
        protocolSpinner.setSelection(protocolAdapter!!.getPosition(getRuleProtocolFromModel(ruleModel)))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_edit_rule, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        when (item.itemId) {
            R.id.action_save_rule -> {
                Log.i(TAG, "Save Menu Button Clicked")


                // Set the item to disabled while saving
                item.isEnabled = false
                saveEditedRule()
                item.isEnabled = true
            }

            R.id.action_delete_rule -> deleteRule()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun saveEditedRule() {
        ruleModel = generateNewRule()
        if (ruleModel.isValid) {
            // Determine if rule is enabled
            ruleModel.isEnabled = switchBar.isChecked
            Log.i(TAG, "Rule " + ruleModel.name + " is valid, time to update.")
            val db = RuleDbHelper(this).readableDatabase
            Log.i(TAG, "Is enabled is: " + ruleModel.isEnabled)

            // New model to store
            val values = ruleModelToContentValues(ruleModel)

            // Which row to update, based on the ID
            val selection = RuleContract.RuleEntry.COLUMN_NAME_RULE_ID + "=?"
            val selectionArgs = arrayOf(ruleModelId.toString())
            this.db = RuleDbHelper(this).readableDatabase
            db.update(
                    RuleContract.RuleEntry.TABLE_NAME,
                    values,
                    selection,
                    selectionArgs)

            // Close db
            db.close()

            // Move to main activity
            val mainActivityIntent = Intent(this, MainActivity::class.java)
            mainActivityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(mainActivityIntent)
            finish()
        } else {
            Toast.makeText(this, R.string.toast_error_rule_not_valid_text,
                    Toast.LENGTH_LONG).show()
        }
    }

    private fun deleteRule() {
        AlertDialog.Builder(this)
                .setTitle(R.string.alert_dialog_delete_entry_title)
                .setMessage(R.string.alert_dialog_delete_entry_text)
                .setPositiveButton(android.R.string.yes) { dialog: DialogInterface?, which: Int ->
                    // Continue with delete

                    // TODO: add exception handling
                    // TODO: add db delete
                    // MainActivity.RULE_MODELS.remove(ruleModelLocation);
                    // MainActivity.ruleListAdapter.notifyItemRemoved(ruleModelLocation);

                    //construct the db
                    db = RuleDbHelper(baseContext).readableDatabase

                    // Define 'where' part of query.
                    val selection = RuleContract.RuleEntry.COLUMN_NAME_RULE_ID + "=?"
                    // Specify arguments in placeholder order.
                    val selectionArgs = arrayOf(ruleModelId.toString())
                    // Issue SQL statement.
                    db.delete(RuleContract.RuleEntry.TABLE_NAME, selection, selectionArgs)

                    // Close the db
                    db.close()

                    // Move to main activity
                    val mainActivityIntent = Intent(baseContext, MainActivity::class.java)
                    finish()
                    startActivity(mainActivityIntent)
                }
                .setNegativeButton(android.R.string.no) { dialog: DialogInterface?, which: Int -> }
                .show()
    }

    companion object {
        private const val TAG = "EditRuleActivity"
        private const val NO_RULE_ID_FOUND_LOG_MESSAGE = "No ID was supplied to EditRuleActivity"
        private const val NO_RULE_ID_FOUND_TOAST_MESSAGE = "Could not locate rule"
    }
}