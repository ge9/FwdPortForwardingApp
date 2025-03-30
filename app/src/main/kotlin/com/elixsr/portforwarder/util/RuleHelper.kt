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
package com.elixsr.portforwarder.util

import android.content.ContentValues
import android.database.Cursor
import com.elixsr.portforwarder.db.RuleContract
import com.elixsr.portforwarder.models.RuleModel
import java.net.InetSocketAddress

/**
 * The [RuleModel] class provides static objects and methods related to rules.
 *
 *
 * The class provides functions to convert [RuleModel] object to and from other common object
 * types.
 */
object RuleHelper {
    const val RULE_MODEL_ID = "RuleModelId"

    /**
     * The minimum from port value.
     *
     *
     * This is a result of not having root permissions.
     */
    const val MIN_PORT_VALUE = 1

    /**
     * The minimum target port value.
     */
    const val TARGET_MIN_PORT = 1

    /**
     * The maximum from and target port value.
     */
    const val MAX_PORT_VALUE = 65535

    /**
     * Convert a [RuleModel] object to a [ContentValues] object.
     *
     * @param ruleModel The [RuleModel] object to be converted.
     * @return a [ContentValues] object based off the input [RuleModel] object.
     */
    @JvmStatic
    fun ruleModelToContentValues(ruleModel: RuleModel): ContentValues {

        // Create a new map of values, where column names are the keys
        val contentValues = ContentValues()
        contentValues.put(RuleContract.RuleEntry.COLUMN_NAME_NAME, ruleModel.name)
        contentValues.put(RuleContract.RuleEntry.COLUMN_NAME_IS_TCP, ruleModel.isTcp)
        contentValues.put(RuleContract.RuleEntry.COLUMN_NAME_IS_UDP, ruleModel.isUdp)
        contentValues.put(RuleContract.RuleEntry.COLUMN_NAME_FROM_INTERFACE_NAME, ruleModel.fromInterfaceName)
        contentValues.put(RuleContract.RuleEntry.COLUMN_NAME_FROM_PORT, ruleModel.fromPort)
        contentValues.put(RuleContract.RuleEntry.COLUMN_NAME_TARGET_IP_ADDRESS, ruleModel.targetIpAddress)
        contentValues.put(RuleContract.RuleEntry.COLUMN_NAME_TARGET_PORT, ruleModel.targetPort)
        contentValues.put(RuleContract.RuleEntry.COLUMN_NAME_IS_ENABLED, ruleModel.isEnabled)
        return contentValues
    }

    /**
     * Convert a [Cursor] object to a [RuleModel] object.
     *
     * @param cursor The [Cursor] object to be converted.
     * @return a [RuleModel] based off the input [Cursor]
     */
    @JvmStatic
    fun cursorToRuleModel(cursor: Cursor): RuleModel {
        val ruleModel = RuleModel()
        ruleModel.id = cursor.getLong(0)
        ruleModel.name = cursor.getString(1)

        //dirty conversion hack
        ruleModel.isTcp = cursor.getInt(2) != 0
        ruleModel.isUdp = cursor.getInt(3) != 0
        ruleModel.fromInterfaceName = cursor.getString(4)
        ruleModel.fromPort = cursor.getInt(5)
        ruleModel.target = InetSocketAddress(cursor.getString(6), cursor.getInt(7))
        ruleModel.isEnabled = cursor.getInt(8) != 0
        return ruleModel
    }

    /**
     * Function to find the relevant Protocol based of a [RuleModel] object.
     *
     * @param ruleModel The source [RuleModel] object.
     * @return A String describing the protocol. Can be; "TCP", "UDP" or "BOTH".
     */
    @JvmStatic
    fun getRuleProtocolFromModel(ruleModel: RuleModel): String {
        var result = ""
        if (ruleModel.isTcp) {
            result = NetworkHelper.TCP
        }
        if (ruleModel.isUdp) {
            result = NetworkHelper.UDP
        }
        if (ruleModel.isTcp && ruleModel.isUdp) {
            result = NetworkHelper.BOTH
        }
        return result
    }
}