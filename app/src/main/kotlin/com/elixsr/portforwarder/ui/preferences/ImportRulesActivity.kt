package com.elixsr.portforwarder.ui.preferences

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.elixsr.portforwarder.R
import com.elixsr.portforwarder.adapters.RuleListJsonValidator
import com.elixsr.portforwarder.adapters.RuleListTargetJsonSerializer
import com.elixsr.portforwarder.dao.RuleDao
import com.elixsr.portforwarder.db.RuleDbHelper
import com.elixsr.portforwarder.exceptions.RuleValidationException
import com.elixsr.portforwarder.models.RuleModel
import com.elixsr.portforwarder.ui.BaseActivity
import com.elixsr.portforwarder.ui.MainActivity
import com.elixsr.portforwarder.util.InterfaceHelper.generateInterfaceNamesList
import com.elixsr.portforwarder.validators.RuleModelValidator
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.SocketException
import java.util.LinkedList

class ImportRulesActivity : BaseActivity() {
    private val TAG = "ImportRulesActivity"
    protected lateinit var fromInterfaceSpinner: Spinner
    protected var fromSpinnerAdapter: ArrayAdapter<String>? = null
    private lateinit var ruleDao: RuleDao
    private lateinit var gson: Gson
    private lateinit var ruleModels: MutableList<RuleModel>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import_rules)
        val toolbar = actionBarToolbar
        setSupportActionBar(toolbar)
        toolbar!!.setNavigationIcon(R.drawable.ic_close_24dp)
        toolbar.setNavigationOnClickListener { v: View? -> onBackPressed() }
        val importRulesText = findViewById<TextView>(R.id.import_rules_count_text)
        val importRulesButton = findViewById<Button>(R.id.import_rules_button)
        val helpButton = findViewById<ImageView>(R.id.help_button)
        ruleModels = LinkedList()
        constructDetailUi()
        ruleDao = RuleDao(RuleDbHelper(this))
        val extras = intent.extras
        val data: Uri
        gson = GsonBuilder()
                .registerTypeAdapter(InetSocketAddress::class.java, RuleListTargetJsonSerializer())
                .registerTypeAdapter(RuleModel::class.java, RuleListJsonValidator())
                .create()
        if (extras != null) {
            data = Uri.parse(extras.getString(IMPORTED_RULE_DATA))
            parseRules(data)
        }

        // We shouldn't continue if we don't have any rules.
        if (ruleModels.size == 0) {
            val mainActivityIntent = Intent(this, MainActivity::class.java)
            mainActivityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(mainActivityIntent)
            finish()
            return
        }
        importRules()
        return
        // TODO: Expose as localised strings
//        var importText = "You are about to import <b>" + ruleModels.size + "</b> rule, configure the interface and target address below"
//        if (ruleModels.size > 1) {
//            importText = "You are about to import <b>" + ruleModels.size + "</b> rules, configure the interface and target address below"
//        }
//        importRulesText.text = Html.fromHtml(importText)
//        importRulesButton.text = "IMPORT " + ruleModels.size + " RULES"
//        importRulesButton.setOnClickListener { importRules() }
//        helpButton.setOnClickListener { v: View ->
//            val mainActivityIntent = Intent(v.context, SupportSiteActivity::class.java)
//            startActivity(mainActivityIntent)
//        }
    }

    fun parseRules(data: Uri) {
        var ruleFailedValidation = false
        var successfulRuleAdditions = 0
        val reader: JsonReader
        val collectionType = object : TypeToken<Collection<RuleModel?>?>() {}.type
        try {
            val fileContentStream = contentResolver.openInputStream(data)
            reader = JsonReader(InputStreamReader(fileContentStream))
            val allRuleModels = gson.fromJson<List<RuleModel>>(reader, collectionType)
            for (ruleModel in allRuleModels) {
                try {
                    if (RuleModelValidator.validateRule(ruleModel)) {
                        successfulRuleAdditions++
                        ruleModels.add(ruleModel)
                    }
                } catch (e: RuleValidationException) {
                    ruleFailedValidation = true
                }
            }
            if (ruleFailedValidation) {
                Toast.makeText(applicationContext, "Some rules failed validation. Imported $successfulRuleAdditions rules.", Toast.LENGTH_LONG).show()
            }
        } catch (e: FileNotFoundException) {
            Toast.makeText(applicationContext, "Error importing rules - No valid file found.", Toast.LENGTH_LONG).show()
        } catch (e: JsonSyntaxException) {
            Toast.makeText(applicationContext, "Error importing rules - JSON file is malformed.", Toast.LENGTH_LONG).show()
        } catch (e: JsonParseException) {
            Toast.makeText(applicationContext, "Error importing rules - Rule list is invalid.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun importRules() {
        var validationError = false
        var targetIpAddress: String? = null
        val targetIpAddressText = findViewById<TextInputEditText>(R.id.new_rule_target_ip_address)

        // Validate the input, and show error message if wrong
//        try {
//            if (RuleModelValidator.validateRuleTargetIpAddress(targetIpAddressText.text.toString())) {
//                targetIpAddress = targetIpAddressText.text.toString()
//            }
//        } catch (e: RuleValidationException) {
//            targetIpAddressText.error = e.message
//            validationError = true
//        }
//        if (validationError) {
//            return
//        }
        for (ruleModel in ruleModels) {

            // Create an InetSocketAddress object using data
            //val target = InetSocketAddress(targetIpAddress, ruleModel.targetPort)
            //ruleModel.target = target
            val fromInterfaceSpinner = findViewById<Spinner>(R.id.from_interface_spinner)
            //val selectedFromInterface = fromInterfaceSpinner.selectedItem.toString()
            //ruleModel.fromInterfaceName = selectedFromInterface
            ruleDao.insertRule(ruleModel)
        }
        Toast.makeText(applicationContext, "Imported " + ruleModels.size + " rules.", Toast.LENGTH_LONG).show()
        val mainActivityIntent = Intent(this, MainActivity::class.java)
        mainActivityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(mainActivityIntent)
        finish()
    }

    protected fun constructDetailUi() {

        // Generate interfaces
        val interfaces: List<String> = try {
            generateInterfaceNamesList()
        } catch (e: SocketException) {
            Log.i(TAG, "Error generating Interface list", e)

            // Show toast and move to main screen
            Toast.makeText(this, "Problem locating network interfaces. Please refer to 'help' to " +
                    "assist with troubleshooting.",
                    Toast.LENGTH_LONG).show()
            val mainActivityIntent = Intent(this, MainActivity::class.java)
            startActivity(mainActivityIntent)
            finish()
            return
        }

        // Check to ensure we have some interface to show!
        if (interfaces.isEmpty()) {
            Toast.makeText(this, "Could not locate any network interfaces. Please refer to 'help'" +
                    " to assist with troubleshooting.",
                    Toast.LENGTH_LONG).show()
            val mainActivityIntent = Intent(this, MainActivity::class.java)
            startActivity(mainActivityIntent)
            finish()
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

    companion object {
        const val IMPORTED_RULE_DATA = "imported_rule_data"
    }
}