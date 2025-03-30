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

import android.content.Intent
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import com.elixsr.portforwarder.R
import com.elixsr.portforwarder.exceptions.RuleValidationException
import com.elixsr.portforwarder.models.RuleModel
import com.elixsr.portforwarder.ui.BaseActivity
import com.elixsr.portforwarder.ui.MainActivity
import com.elixsr.portforwarder.util.InterfaceHelper.generateInterfaceNamesList
import com.elixsr.portforwarder.util.NetworkHelper
import com.elixsr.portforwarder.validators.RuleModelValidator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.net.InetSocketAddress
import java.net.SocketException

/**
 * The BaseRuleActivity class  provides logic for subclasses which utilise the shared Rule detail
 * layout.
 *
 *
 * This class provides functionality to set up the core GUI components, and provides shared logic
 * for the validation of user input.
 *
 *
 * This class also provides a function to generate a [String] [List] of Network
 * Interfaces available on the device.
 *
 * @author Niall McShane
 */
abstract class BaseRuleActivity : BaseActivity() {
    protected lateinit var protocolSpinner: Spinner
    protected lateinit var fromInterfaceSpinner: Spinner
    protected var fromSpinnerAdapter: ArrayAdapter<String?>? = null
    protected var protocolAdapter: ArrayAdapter<CharSequence>? = null

    /**
     * Generate a user interface for all shared activities that use the [ .portforwarder.R.layout.rule_detail_view][com.elixsr] layout.
     *
     *
     * This will pre-populate the [Spinner] Objects.
     */
    protected fun constructDetailUi() {

        // Set up protocol spinner/dropdown
        protocolSpinner = findViewById(R.id.protocol_spinner)

        // Create an ArrayAdapter using the string array and a default spinner layout
        protocolAdapter = ArrayAdapter.createFromResource(this,
                R.array.rule_protocol_array, R.layout.my_spinner)

        // Specify the layout to use when the list of choices appears
        protocolAdapter!!.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        // Apply the protocolAdapter to the spinner
        protocolSpinner.adapter = protocolAdapter

        // Generate interfaces
        var interfaces: List<String?>? = null
        interfaces = try {
            generateInterfaceList()
        } catch (e: SocketException) {
            Log.i(TAG, "Error generating Interface list", e)

            // Show toast and move to main screen
            Toast.makeText(this, "Problem locating network interfaces. Please refer to 'help' to " +
                    "assist with troubleshooting.",
                    Toast.LENGTH_LONG).show()
            val mainActivityIntent = Intent(this, MainActivity::class.java)
            startActivity(mainActivityIntent)
            return
        }

        // Check to ensure we have some interface to show!
        if (interfaces.isNullOrEmpty()) {
            Toast.makeText(this, "Could not locate any network interfaces. Please refer to 'help'" +
                    " to assist with troubleshooting.",
                    Toast.LENGTH_LONG).show()
            val mainActivityIntent = Intent(this, MainActivity::class.java)
            startActivity(mainActivityIntent)
            return
        }


        // Set up protocol spinner/dropdown
        fromInterfaceSpinner = findViewById(R.id.from_interface_spinner)

        // Create an ArrayAdapter using the string array and a default spinner layout
        fromSpinnerAdapter = ArrayAdapter(this, R.layout.my_spinner, interfaces)

        // Specify the layout to use when the list of choices appears
        fromSpinnerAdapter!!.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        // Apply the protocolAdapter to the spinner
        fromInterfaceSpinner.adapter = fromSpinnerAdapter
    }

    /**
     * Returns a list of all Network interfaces located on the device.
     *
     * @return a String list containing the name of the network interfaces on the device.
     * @throws SocketException
     */
    @Throws(SocketException::class)
    fun generateInterfaceList(): List<String?> {
        return generateInterfaceNamesList()
    }

    /**
     * Constructs a [RuleModel] object based on the data held inside the shared layout.
     *
     *
     * This method will also check to ensure that all inputs are valid, and will show the user an
     * error message if the user has not entered valid data.
     *
     * @return a [RuleModel] object as a result of the users input.
     */
    fun generateNewRule(): RuleModel {

        // Create the blank rule object
        val ruleModel = RuleModel()


        /*
            Protocol
         */
        val protocolSpinner = findViewById<Spinner>(R.id.protocol_spinner)
        when (protocolSpinner.selectedItem.toString()) {
            NetworkHelper.TCP -> ruleModel.isTcp = true
            NetworkHelper.UDP -> ruleModel.isUdp = true
            NetworkHelper.BOTH -> {
                ruleModel.isTcp = true
                ruleModel.isUdp = true
            }

            else -> {
                ruleModel.isTcp = true
                ruleModel.isUdp = true
            }
        }

        /*
            Rule Name
         */
        val ruleNameText = findViewById<TextInputEditText>(R.id.new_rule_name)
        val ruleNameTextInputLayout = findViewById<TextInputLayout>(R.id.new_rule_name_input_layout)
        try {
            if (RuleModelValidator.validateRuleName(ruleNameText.text.toString())) {
                // If everything is correct, set the name
                ruleModel.name = ruleNameText.text.toString()
                ruleNameTextInputLayout.isErrorEnabled = false
            }
        } catch (e: RuleValidationException) {
            ruleNameTextInputLayout.isErrorEnabled = true
            // Alternate error style above line
            // ruleNameText.setError(e.getMessage());
            ruleNameTextInputLayout.error = getString(R.string.text_input_error_enter_name_text)
            Log.w(TAG, "No rule name was included")
        }

        /*
            From port
         */
        val fromPortText = findViewById<TextInputEditText>(R.id.new_rule_from_port)

        // Validate the input, and show error message if wrong
        try {
            if (RuleModelValidator.validateRuleFromPort(fromPortText.text.toString())) {
                ruleModel.fromPort = fromPortText.text.toString().toInt()
            }
        } catch (e: RuleValidationException) {
            fromPortText.error = e.message
        }

        /*
            Target
         */
        var targetIpAddress: String? = null
        var targetPort = 0

        /*
            Target IP Address
         */
        val targetIpAddressText = findViewById<TextInputEditText>(R.id.new_rule_target_ip_address)

        // Validate the input, and show error message if wrong
        try {
            if (RuleModelValidator.validateRuleTargetIpAddress(targetIpAddressText.text.toString())) {
                targetIpAddress = targetIpAddressText.text.toString()
            }
        } catch (e: RuleValidationException) {
            targetIpAddressText.error = e.message
        }

        /*
            Target port
         */
        val targetPortText = findViewById<TextInputEditText>(R.id.new_rule_target_port)

        // Validate the input, and show error message if wrong
        try {
            if (RuleModelValidator.validateRuleTargetPort(targetPortText.text.toString())) {
                targetPort = targetPortText.text.toString().toInt()
            }
        } catch (e: RuleValidationException) {
            targetPortText.error = e.message
        }
        if (!targetIpAddress.isNullOrEmpty() && targetPort >= 0) {
            // Create a InetSocketAddress object using data
            val target = InetSocketAddress(targetIpAddress, targetPort)
            ruleModel.target = target
        } else {
            Log.w(TAG, "Could not create Target InetSocketAddress Object")
        }
        val fromInterfaceSpinner = findViewById<Spinner>(R.id.from_interface_spinner)
        val selectedFromInterface = fromInterfaceSpinner.selectedItem.toString()
        ruleModel.fromInterfaceName = selectedFromInterface.substringBefore(" ")
        return ruleModel
    }

    companion object {
        private const val TAG = "BaseRuleActivity"
    }
}