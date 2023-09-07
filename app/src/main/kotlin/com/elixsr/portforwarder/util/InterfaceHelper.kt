package com.elixsr.portforwarder.util

import android.util.Log
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException

/**
 * Created by Cathan on 06/03/2017.
 */
object InterfaceHelper {
    private const val TAG = "InterfaceHelper"

    /**
     * Returns a list of all Network interfaces located on the device.
     *
     * @return a String list containing the name of the network interfaces on the device.
     * @throws SocketException
     */
    @JvmStatic
    @Throws(SocketException::class)
    fun generateInterfaceNamesList(): List<String> {

        // Create an empty list
        val interfaces: MutableList<String> = ArrayList()
        var address: String?
        val en = NetworkInterface.getNetworkInterfaces()
        while (en.hasMoreElements()) {
            val intf = en.nextElement()

            // While we have more elements
            val enumIpAddr = intf.inetAddresses
            while (enumIpAddr.hasMoreElements()) {


                // Get the next address in from the iterator
                val inetAddress = enumIpAddr.nextElement()
                address = inetAddress.hostAddress
                if (!address.isNullOrEmpty() && inetAddress is Inet4Address) {
                    Log.i(TAG, intf.displayName + " " + address)
                    interfaces.add(intf.displayName)
                }
            }
        }
        interfaces.add("INADDR_ANY")
        return interfaces
    }

    @JvmStatic
    @Throws(SocketException::class)
    fun generateInterfaceModelList(): List<InterfaceModel> {

        // Create an empty list
        val interfaces: MutableList<InterfaceModel> = ArrayList()
        var address: String? = null
        val en = NetworkInterface.getNetworkInterfaces()
        while (en.hasMoreElements()) {
            val intf = en.nextElement()

            // While we have more elements
            val enumIpAddr = intf.inetAddresses
            while (enumIpAddr.hasMoreElements()) {


                // Get the next address in from the iterator
                val inetAddress = enumIpAddr.nextElement()
                address = inetAddress.hostAddress
                if (!address.isNullOrEmpty() && inetAddress is Inet4Address) {
                    interfaces.add(InterfaceModel(intf.displayName, inetAddress))
                }
            }
        }
        return interfaces
    }

    class InterfaceModel(@JvmField var name: String, @JvmField var inetAddress: InetAddress)
}