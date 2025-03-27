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
package com.elixsr.portforwarder.forwarding

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.elixsr.portforwarder.R
import com.elixsr.portforwarder.dao.RuleDao
import com.elixsr.portforwarder.db.RuleDbHelper
import com.elixsr.portforwarder.exceptions.ObjectNotFoundException
import com.elixsr.portforwarder.ui.MainActivity
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.concurrent.CompletionService
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit


/**
 * The [ForwardingService] class acts as a controller of all all forwarding.
 *
 *
 * The class is responsible for starting forwarding for all rules found within the SQLite database.
 *
 *
 * The class creates a new thread for each Forwarding rule.
 */
class ForwardingService : Service {
    private var runService = false

    //change the magic number
    private var executorService: ExecutorService
    private var executorService0: ExecutorService

    //wake lock
    //private lateinit var wakeLock: WakeLock

    /**
     * Default constructor for [ForwardingService].#
     *
     *
     * Creates a new instance of ForwardingService and initialises an [ExecutorService]
     * with a fixed thread pool of 30 threads.
     */
    constructor() : super() {
        executorService = Executors.newFixedThreadPool(30)
        executorService0 = Executors.newSingleThreadExecutor()
    }

    constructor(executorService: ExecutorService) : super() {
        this.executorService = executorService
        this.executorService0 = Executors.newSingleThreadExecutor()
    }
    override fun onBind(intent: Intent): IBinder? = null
    override fun onCreate() {
        super.onCreate()

        /*
        Sourced from: https://developer.android.com/intl/ja/training/scheduling/wakelock.html
         */
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
//        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
//            PORT_FORWARD_SERVICE_WAKE_LOCK_TAG)
//        wakeLock.acquire()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: called")
        if (intent != null && intent.action == "ACTION_DISCONNECT") {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!runService) {
            runService = true
            executorService0.submit { handleIntent(intent) }
        }
        Log.i(TAG, "Service started with START_STICKY")
        return START_STICKY
    }
    /**
     * Starts forwarding based on rules found in database.
     *
     *
     * Acquires an instance of the Forwarding Manager to turn forwarding flag on.
     *
     *
     * Creates a list off callbacks for each forward thread, and handle exceptions as they come.
     *
     *
     * If an exception is thrown, the service immediately stops, and the #onDestroy method is
     * called.
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun handleIntent(intent: Intent?) {

        // Gets data from the incoming Intent
//        String dataString = intent.getDataString();
        Log.i(TAG, "Ran the service")
        ForwardingManager.instance.enableForwarding()
        runService = true

        /*
         * Creates a new Intent containing a Uri object
         * BROADCAST_ACTION is a custom Intent action
         */
        var localIntent: Intent = Intent(BROADCAST_ACTION) // Puts the status into the Intent
                .putExtra(PORT_FORWARD_SERVICE_STATE, ForwardingManager.instance.isEnabled)
        // Broadcasts the Intent to receivers in this app.
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent)
        showForwardingEnabledNotification()

        //load the rules from the datastore
        //TODO: inject the rules as extras
        val ruleDao = RuleDao(RuleDbHelper(this))
        val ruleModels = ruleDao.allEnabledRuleModels
        val ruleModelForwarders: MutableList<Forwarder> = ArrayList()
        var from: InetSocketAddress

        // how many futures there are to check
        var remainingFutures = 0
        for (ruleModel in ruleModels) {

            // Something has killed the runService, no point in looping anymore
            if (!runService) {
                break
            }
            try {
                from = generateFromIpUsingInterface(ruleModel.fromInterfaceName, ruleModel.fromPort)
                if (ruleModel.isTcp && runService) {
                    ruleModelForwarders.add(TcpForwarder(from, ruleModel.target, ruleModel.name))
                    remainingFutures++
                }
                if (ruleModel.isUdp && runService) {
                    ruleModelForwarders.add(UdpForwarder(from, ruleModel.target, ruleModel.name))
                    remainingFutures++
                }
            } catch (e: SocketException) {
                Log.e(TAG, "Error generating IP Address for FROM interface with rule '" + ruleModel.name + "'", e)

                // graceful UI Exception handling - broadcast this to ui - it will deal with display something to the user e.g. a Toast
                localIntent = Intent(BROADCAST_ACTION) // Puts the status into the Intent
                        .putExtra(PORT_FORWARD_SERVICE_ERROR_MESSAGE, getString(R.string.start_rule_error_message) + " '" + ruleModel.name + "'")
                // Broadcasts the Intent to receivers in this app.
                LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent)
            } catch (e: ObjectNotFoundException) {
                Log.e(TAG, "Error generating IP Address for FROM interface with rule '" + ruleModel.name + "'", e)
                localIntent = Intent(BROADCAST_ACTION)
                        .putExtra(PORT_FORWARD_SERVICE_ERROR_MESSAGE, getString(R.string.start_rule_error_message) + " '" + ruleModel.name + "'")
                LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent)
            }
        }
        executorService = Executors.newFixedThreadPool(ruleModelForwarders.size)

        /*
         Sourced from: http://stackoverflow.com/questions/19348248/waiting-on-a-list-of-future
         */
        val completionService: CompletionService<Void> = ExecutorCompletionService(executorService)
        for (ruleForwarder in ruleModelForwarders) {
            completionService.submit(ruleForwarder)
        }
        var completedFuture: Future<*>

        // loop through each callback, and handle an exception
        while (remainingFutures > 0 && runService) {

            // block until a callable completes
            try {
                completedFuture = completionService.take()
                remainingFutures--
                completedFuture.get()
            } catch (e: ExecutionException) {
                Log.e(TAG, "Error when forwarding port.", e)
                localIntent = Intent(BROADCAST_ACTION) // Puts the status into the Intent
                        .putExtra(PORT_FORWARD_SERVICE_ERROR_MESSAGE, e.cause!!.message)
                // Broadcasts the Intent to receivers in this app.
                LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent)
                break
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    @Throws(SocketException::class, ObjectNotFoundException::class)
    private fun generateFromIpUsingInterface(interfaceName: String?, port: Int): InetSocketAddress {
        var address: String?
        val inetSocketAddress: InetSocketAddress
        val en = NetworkInterface.getNetworkInterfaces()
        if (interfaceName.equals("INADDR_ANY")) {
            inetSocketAddress = InetSocketAddress("0.0.0.0", port)
            return inetSocketAddress
        }
        while (en.hasMoreElements()) {
            val intf = en.nextElement()
            Log.d(TAG, intf.displayName + " vs " + interfaceName)
            if (intf.displayName == interfaceName) {
                Log.i(TAG, "Found the relevant Interface. Will attempt to fetch IP Address")
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    address = inetAddress.hostAddress
                    if (!address.isNullOrEmpty() && inetAddress is Inet4Address) {
                        inetSocketAddress = InetSocketAddress(address, port)
                        return inetSocketAddress
                    }
                }
            }
        }
        throw ObjectNotFoundException("Could not find IP Address for Interface $interfaceName")
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "onTaskRemoved: called")
        //onDestroy()
    }

    override fun onDestroy() {
        super.onDestroy()
        runService = false

        // Reject any new tasks
        executorService.shutdown()
        try {
            // Shutdown any existing tasks
            executorService.shutdownNow()
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                Log.e(TAG, "onDestroy: Pool did not terminate")
            }
        } catch (ie: InterruptedException) {
            // (Re-)Cancel if current thread also interrupted
            executorService.shutdownNow()
            // Preserve interrupt status
            Thread.currentThread().interrupt()
        }
        ForwardingManager.instance.disableForwarding()
        hideForwardingEnabledNotification()

        //update the main activity
        val localIntent: Intent = Intent(BROADCAST_ACTION) // Puts the status into the Intent
                .putExtra(PORT_FORWARD_SERVICE_STATE, ForwardingManager.instance.isEnabled)
        // Broadcasts the Intent to receivers in this app.
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent)
//        if (wakeLock.isHeld) {
//            wakeLock.release()
//        }
        Log.i(TAG, "Ended the ForwardingService. Cleanup finished.")
    }

    private fun hideForwardingEnabledNotification() {
        val notifManager = NotificationManagerCompat.from(this)
        notifManager.cancel(NOTIFICATION_ID)
    }

    /**
     * Construct a notification
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showForwardingEnabledNotification() {
        val notifManager = NotificationManagerCompat.from(this)
        val channelId = "persistent"
        var channel = notifManager.getNotificationChannel(channelId)
        if (channel == null) {
            channel = NotificationChannel(channelId, "Background",
                    NotificationManager.IMPORTANCE_HIGH)
            notifManager.createNotificationChannel(channel)
        }

        // Creates an explicit intent for an Activity in your app
        val resultIntent = Intent(this, MainActivity::class.java)

        // The stack builder object will contain an artificial back stack for the
        // started Activity.
        // This ensures that navigating backward from the Activity leads out of
        // your application to the Home screen.
        val stackBuilder = TaskStackBuilder.create(this)

        // Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(MainActivity::class.java)

        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent)
        val resultPendingIntent = stackBuilder.getPendingIntent(
                0,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val mBuilder = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_fwd_24dp)
                .setContentTitle(getString(R.string.notification_forwarding_active_title))
                .setContentText(getString(R.string.notification_forwarding_touch_disable_text))
                .setColor(ContextCompat.getColor(this, R.color.colorPrimaryDark))
                .setContentIntent(resultPendingIntent)
        val notification = mBuilder.build()
        notification.flags = Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT or Notification.DEFAULT_LIGHTS
        notifManager.notify(NOTIFICATION_ID, notification)
        startForeground(NOTIFICATION_ID, notification)
    }

    companion object {
        // Defines a custom Intent action
        const val BROADCAST_ACTION = "com.elixsr.portforwarder.forwarding.ForwardingService.BROADCAST"
        const val PORT_FORWARD_SERVICE_STATE = "com.elixsr.portforwarder.forwarding.ForwardingService.PORT_FORWARD_STATE"
        const val PORT_FORWARD_SERVICE_ERROR_MESSAGE = "com.elixsr.portforwarder.forwarding.ForwardingService.PORT_FORWARD_ERROR_MESSAGE"
        private const val PORT_FORWARD_SERVICE_WAKE_LOCK_TAG = "PortForwardServiceWakeLockTag:"
        private const val TAG = "ForwardingService"
        private const val NOTIFICATION_ID = 1
    }
}