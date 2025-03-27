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
package com.elixsr.portforwarder.models

import android.util.Log
import com.elixsr.portforwarder.util.RuleHelper
import com.elixsr.portforwarder.util.RuleHelper.getRuleProtocolFromModel
import com.google.gson.annotations.Expose
import java.io.Serializable
import java.net.InetSocketAddress

/**
 * The [RuleModel] class represents a Forwarding Rule.
 *
 * @author Niall McShane
 */
class RuleModel : Serializable {
    @Expose(serialize = false, deserialize = false)
    var id: Long = 0

    @JvmField
    @Expose
    var isTcp = false

    @JvmField
    @Expose
    var isUdp = false

    @JvmField
    @Expose
    var name: String? = null

    //TODO: create a class? - worth the effort?
    @JvmField
    @Expose
    var fromInterfaceName: String? = null

    @JvmField
    @Expose
    var fromPort = 0

    @JvmField
    @Expose
    var target: InetSocketAddress? = null

    @JvmField
    @Expose
    var isEnabled = true

    // Null constructor - for object building
    constructor()
    constructor(isTcp: Boolean, isUdp: Boolean, name: String?, fromInterfaceName: String?, fromPort: Int, target: InetSocketAddress?) {
        this.isTcp = isTcp
        this.isUdp = isUdp
        this.name = name
        this.fromInterfaceName = fromInterfaceName
        this.fromPort = fromPort
        this.target = target
    }

    constructor(isTcp: Boolean, isUdp: Boolean, name: String?, fromInterfaceName: String?, fromPort: Int, targetIp: String?, targetPort: Int) : this(isTcp, isUdp, name, fromInterfaceName, fromPort, InetSocketAddress(targetIp, targetPort))

    fun protocolToString(): String {
        return getRuleProtocolFromModel(this)
    }

    val targetIpAddress: String?
        /**
         * Return a string of the target IPv4 address
         *
         * @return the IPv4 address as a String
         */
        get() = target!!.address.hostAddress
    val targetPort: Int
        /**
         * Return the target port as an integer
         *
         * @return the target port integer.
         */
        get() = target!!.port
    val isValid: Boolean
        /**
         * Validate all data held within the model.
         *
         *
         * Validation rules:   * Name should not be null & greater than 0 characters
         *  * Either TCP or UDP should be true  * From Interface should not be null & greater
         * than 0 characters  * From port should be greater than minimum port and smaller than
         * max  * Target port should be greater than minimum port and smaller than max  * Target IP address should not be null & greater than 0 characters
         *
         * @return true if valid, false if not valid.
         */
        get() {

            // Ensure the rule has a name
            if (name.isNullOrEmpty()) {
                return false
            }

            // It must either be one or the other, or even both
            if (!isTcp && !isUdp) {
                return false
            }
            if (fromInterfaceName.isNullOrEmpty()) {
                return false
            }
            if (fromPort < RuleHelper.MIN_PORT_VALUE || fromPort > RuleHelper.MAX_PORT_VALUE) {
                return false
            }
            try {
                // Ensure that the value is greater than the minimum, and smaller than max
                if (targetPort <= 0 || targetPort < RuleHelper.TARGET_MIN_PORT || targetPort > RuleHelper.MAX_PORT_VALUE) {
                    return false
                }
            } catch (e: NullPointerException) {
                Log.e(TAG, "Target object was null.", e)
                return false
            }


            // The new rule activity should take care of IP address validation
            return !targetIpAddress.isNullOrEmpty()
        }

    companion object {
        private const val TAG = "RuleModel"
    }
}