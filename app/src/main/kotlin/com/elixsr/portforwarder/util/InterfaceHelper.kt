package com.elixsr.portforwarder.util

import android.util.Log
import java.net.Inet4Address
import java.net.Inet6Address
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
            var cnt_4=0;var cnt_6=0;var cnt_L=0
            while (enumIpAddr.hasMoreElements()) {


                // Get the next address in from the iterator
                val inetAddress = enumIpAddr.nextElement()
                address = inetAddress.hostAddress
                if (!address.isNullOrEmpty()) {
                    Log.i(TAG, intf.displayName + " " + address)
                    if ( inetAddress is Inet4Address) {//split into three categories: IPv4, IPv6-linklocal, IPv6
                        interfaces.add(intf.displayName + "/4-"+(cnt_4++).toString()+ " " + address)
                    }else if (inetAddress.isLinkLocalAddress){
                        interfaces.add(intf.displayName + "/L-"+(cnt_L++).toString()+ " " + address)
                    }else{
                        interfaces.add(intf.displayName + "/6-"+(cnt_6++).toString()+ " " + address)
                    }
                }
            }
        }
        interfaces.add("INADDR_ANY *")
        return interfaces
    }
    fun correctInterfaceNameAndAddr(s: String): String {
        val s0 = s.substringBefore(" ")
        getAddrFromIntfCategIndex(s0)?.let { return "$s0 $it" } ?: return s//preserve the original if there was no matching IP
    }
    fun getAddrFromIntfCategIndex(s: String): String? {//return the address according to the interface, category and index.
        val intfnCategIndex=s
        val intfn=intfnCategIndex.substringBefore("/")
        val categIndex = intfnCategIndex.substringAfter("/").split("-")
        val categ=categIndex[0]
        val index=categIndex[1]
        var address: String?
        val en = NetworkInterface.getNetworkInterfaces()
        var candidate=""
        while (en.hasMoreElements()) {
            val intf = en.nextElement()
            val enumIpAddr = intf.inetAddresses
            if (intf.displayName != intfn) continue
            var cnt_4=0;var cnt_6=0;var cnt_L=0
            while (enumIpAddr.hasMoreElements()) {
                // Get the next address in from the iterator
                val inetAddress = enumIpAddr.nextElement()
                address = inetAddress.hostAddress
                if (!address.isNullOrEmpty()) {
                    if (categ.equals("4") && inetAddress is Inet4Address) {//split into three categories: IPv4, IPv6-linklocal, IPv6
                        if (index == (cnt_4++).toString()) return address
                    }else if (categ.equals("L") && inetAddress.isLinkLocalAddress){
                        if (index == (cnt_L++).toString()) return address
                    }else if (categ.equals("6") && inetAddress is Inet6Address){
                        if (index == (cnt_6++).toString()) return address
                    }
                }
            }
        }
        return null
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