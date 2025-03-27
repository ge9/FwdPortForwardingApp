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

import android.util.Log
import com.elixsr.portforwarder.exceptions.BindException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Skeleton taken from: http://cs.ecs.baylor.edu/~donahoo/practical/JavaSockets2/code/UDPEchoServerSelector.java
 *
 *
 * Created by Niall McShane on 21/02/2016.
 */
class UdpForwarder(form: InetSocketAddress, to: InetSocketAddress?, ruleName: String?) : Forwarder("UDP", form, to, ruleName), Callable<Void?> {
    @Throws(IOException::class, BindException::class)
    override fun call(): Void? {
        Log.d(TAG, String.format(START_MESSAGE, protocol, from.port, to!!.port))
        try {
            val readBuffer = ByteBuffer.allocate(BUFFER_SIZE)
            val inChannel = DatagramChannel.open()
            inChannel.configureBlocking(false)
            try {
                inChannel.socket().bind(from)
            } catch (e: SocketException) {
                Log.e(TAG, String.format(BIND_FAILED_MESSAGE, from.port, protocol, ruleName), e)
                throw BindException(String.format(BIND_FAILED_MESSAGE, from.port, protocol, ruleName), e)
            }
            val selector = Selector.open()
            inChannel.register(selector, SelectionKey.OP_READ, ClientRecord(to))
            while (true) { // Run forever, receiving and echoing datagrams
                if (Thread.currentThread().isInterrupted) {
                    Log.i(TAG, String.format(THREAD_INTERRUPT_CLEANUP_MESSAGE, protocol))
                    inChannel.socket().close()
                    break
                }
                val count = selector.select()
                if (count > 0) {


                    // Get iterator on set of keys with I/O to process
                    val keyIter = selector.selectedKeys().iterator()
                    while (keyIter.hasNext()) {
                        val key = keyIter.next() // Key is bit mask

                        // Client socket channel has pending data?
                        if (key.isReadable) {
                            // Log.i(TAG, "Have Something to READ");
                            handleRead(key, readBuffer, executor)
                        }

                        // Client socket channel is available for writing and
                        // key is valid (i.e., channel not closed).
                        if (key.isValid && key.isWritable) {
                            // Log.i(TAG, "Have Something to WRITE");
                            handleWrite(key)
                        }
                        keyIter.remove()
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Problem opening Selector", e)
            throw e
        }
        return null
    }
    internal class ClientInfo(val channel:DatagramChannel, var lastActive: Long){}
    internal class ClientRecord(var toAddress: SocketAddress) {
        val clientMap = ConcurrentHashMap<SocketAddress, ClientInfo>()
        val writeBuffers = ConcurrentHashMap<SocketAddress, ByteBuffer>()
    }
    val executor: ExecutorService = Executors.newCachedThreadPool()
    companion object {
        private const val TAG = "UdpForwarder"
        private const val BUFFER_SIZE = 100000

        @Throws(IOException::class)
        fun handleRead(key: SelectionKey, readBuffer: ByteBuffer, executor: ExecutorService) {
            // Log.i("UdpForwarder", "Handling Read");
            val channel = key.channel() as DatagramChannel
            val clientRecord = key.attachment() as ClientRecord

            // Ensure the buffer is empty
            readBuffer.clear()

            // Receive the data
            val sourceAddress = channel.receive(readBuffer)
            // Get read to wrte, then send
            readBuffer.flip()
            var existing=true
            val clInfo = clientRecord.clientMap.computeIfAbsent(sourceAddress)
            {existing=false
                ClientInfo(
                DatagramChannel.open().apply {
                configureBlocking(false)
                connect(clientRecord.toAddress)},System.nanoTime()
                )
            }
            clInfo.lastActive = System.nanoTime()
            val outChannel = clInfo.channel
            outChannel.write(readBuffer)
            // If there is anything remaining in the buffer
            if (readBuffer.remaining() > 0) {
                clientRecord.writeBuffers.computeIfAbsent(sourceAddress){ByteBuffer.allocate(BUFFER_SIZE)}.put(readBuffer)
                key.interestOps(SelectionKey.OP_WRITE)
            }
            if (existing) return
            executor.submit {
                try {
                    val buffer = ByteBuffer.allocate(4096)
                    while (true) {
                        buffer.clear()
                        val sourceAddr = outChannel.receive(buffer)
                        if (sourceAddr != null) {
                            buffer.flip()
                            channel.send(buffer,sourceAddress)
                            clInfo.lastActive = System.nanoTime()
                        }
                        if (System.nanoTime() - clInfo.lastActive > 60_000_000_000) break
                    }
                } catch (e: Exception) {//can catch port unreachable here
                    e.printStackTrace()
                } finally {//a garbage collection
                    clientRecord.clientMap.remove(sourceAddress)
                    clientRecord.writeBuffers.remove(sourceAddress)
                    outChannel.close()
                }
            }

//        ClientRecord clientRecord = (ClientRecord) key.attachment();
//        clientRecord.buffer.clear();    // Prepare buffer for receiving
//        clientRecord.clientAddress = channel.receive(clientRecord.buffer);
//
//        if (clientRecord.clientAddress != null) {  // Did we receive something?
//            // Register write with the selector
//            key.interestOps(SelectionKey.OP_WRITE);
//        }
        }

        @Throws(IOException::class)
        fun handleWrite(key: SelectionKey) {
            val channel = key.channel() as DatagramChannel
            val clientRecord = key.attachment() as ClientRecord
            clientRecord.writeBuffers.forEach { (toAddress, writeBuffer) ->
                writeBuffer.flip() // Prepare buffer for sending
                channel.send(writeBuffer, clientRecord.toAddress)
                if (writeBuffer.remaining() > 0) {
                    writeBuffer.compact()
                } else {
                    key.interestOps(SelectionKey.OP_READ)
                    writeBuffer.clear()
                }
            }

//        if (bytesSent != 0) { // Buffer completely written?
//            // No longer interested in writes
//            key.interestOps(SelectionKey.OP_READ);
//        }
        }
    }
}