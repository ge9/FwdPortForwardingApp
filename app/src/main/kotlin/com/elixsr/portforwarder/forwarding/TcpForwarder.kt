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
import java.io.IOException
import java.net.BindException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.concurrent.Callable

/**
 * Created by Niall McShane on 21/02/2016.
 *
 *
 * Credit: https://alexapps.net/single-threaded-port-forwarding-utility-/
 */
class TcpForwarder(from: InetSocketAddress, to: InetSocketAddress?, ruleName: String?) : Forwarder("TCP", from, to, ruleName), Callable<Void?> {
    @Throws(IOException::class, com.elixsr.portforwarder.exceptions.BindException::class)
    override fun call(): Void? {
        Log.d(TAG, String.format(START_MESSAGE, protocol, from.port, to!!.port))
        try {
            val selector = Selector.open()
            val readBuffer = ByteBuffer.allocate(BUFFER_SIZE)
            val listening = ServerSocketChannel.open()
            listening.configureBlocking(false)
            try {
                listening.socket().bind(from, 0)
            } catch (e: BindException) {
                Log.e(TAG, String.format(BIND_FAILED_MESSAGE, from.port, protocol, ruleName), e)
                throw com.elixsr.portforwarder.exceptions.BindException(String.format(BIND_FAILED_MESSAGE, from.port, protocol, ruleName), e)
            } catch (e: SocketException) {
                Log.e(TAG, String.format(BIND_FAILED_MESSAGE, from.port, protocol, ruleName), e)
                throw com.elixsr.portforwarder.exceptions.BindException(String.format(BIND_FAILED_MESSAGE, from.port, protocol, ruleName), e)
            }
            listening.register(selector, SelectionKey.OP_ACCEPT, listening)
            while (true) {
                if (Thread.currentThread().isInterrupted) {
                    Log.i(TAG, String.format(THREAD_INTERRUPT_CLEANUP_MESSAGE, protocol))
                    listening.close()
                    break
                }
                val count = selector.select()
                if (count > 0) {
                    val it = selector.selectedKeys().iterator()
                    while (it.hasNext()) {
                        val key = it.next()
                        it.remove()
                        if (key.isValid && key.isAcceptable) {
                            processAcceptable(key, to)
                        }
                        if (key.isValid && key.isConnectable) {
                            processConnectable(key)
                        }
                        if (key.isValid && key.isReadable) {
                            processReadable(key, readBuffer)
                        }
                        if (key.isValid && key.isWritable) {
                            processWritable(key)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Problem opening Selector", e)
            throw e
        }
        return null
    }

    internal class RoutingPair {
        var from: SocketChannel? = null
        var to: SocketChannel? = null
        var writeBuffer: ByteBuffer = ByteBuffer.allocate(BUFFER_SIZE)
    }

    companion object {
        private const val TAG = "TcpForwarder"
        private const val BUFFER_SIZE = 100000

        @Throws(ClosedChannelException::class)
        private fun registerReads(
                selector: Selector,
                socket: SocketChannel,
                forwardToSocket: SocketChannel) {
            val pairFromToPair = RoutingPair()
            pairFromToPair.from = socket
            pairFromToPair.to = forwardToSocket
            pairFromToPair.from!!.register(selector, SelectionKey.OP_READ, pairFromToPair)
            val pairToFromPair = RoutingPair()
            pairToFromPair.from = forwardToSocket
            pairToFromPair.to = socket
            pairToFromPair.from!!.register(selector, SelectionKey.OP_READ, pairToFromPair)
        }

        @Throws(IOException::class)
        private fun processWritable(
                key: SelectionKey) {
            val pair = key.attachment() as RoutingPair
            pair.writeBuffer.flip()
            try{
                pair.to!!.write(pair.writeBuffer)
                if (pair.writeBuffer.remaining() > 0) {
                    pair.writeBuffer.compact()
                } else {
                    key.interestOps(SelectionKey.OP_READ)
                    pair.writeBuffer.clear()
                }
            }catch (e: IOException){
                key.cancel()
                Log.e(TAG, "TCP write failed in processWritable()", e)
            }

        }

        @Throws(IOException::class)
        private fun processReadable(
                key: SelectionKey,
                readBuffer: ByteBuffer) {
            readBuffer.clear()
            val pair = key.attachment() as RoutingPair
            var r = 0
            try {
                r = pair.from!!.read(readBuffer)
            } catch (e: IOException) {
                key.cancel()
                println("Connection closed: " + key.channel())
            }
            if (r <= 0) {
                pair.from!!.close()
                pair.to!!.close()
                key.cancel()
                println("Connection closed: " + key.channel())
            } else {
                readBuffer.flip()
                try{
                    pair.to!!.write(readBuffer)
                    if (readBuffer.remaining() > 0) {
                        pair.writeBuffer.put(readBuffer)
                        key.interestOps(SelectionKey.OP_WRITE)
                    }
                }catch (e: IOException){
                    key.cancel()
                    Log.e(TAG, "TCP write failed in processReadable()", e)
                }
            }
        }

        @Throws(IOException::class)
        private fun processConnectable(
                key: SelectionKey) {
            val from = key.attachment() as SocketChannel
            val forwardToSocket = key.channel() as SocketChannel
            try{
                forwardToSocket.finishConnect()
                forwardToSocket.socket().tcpNoDelay = true
                registerReads(key.selector(), from, forwardToSocket)
            }catch (e: SocketException){
                key.cancel()
                from.close()
                forwardToSocket.close()
                Log.e(TAG, "TCP connection failed", e)
            }
        }

        @Throws(IOException::class)
        private fun processAcceptable(
                key: SelectionKey,
                forwardToAddress: InetSocketAddress) {
            val from = (key.attachment() as ServerSocketChannel).accept()
            println("Accepted " + from.socket())
            from.socket().tcpNoDelay = true
            from.configureBlocking(false)
            val forwardToSocket = SocketChannel.open()
            forwardToSocket.configureBlocking(false)
            val connected = forwardToSocket.connect(forwardToAddress)
            if (connected) {
                forwardToSocket.socket().tcpNoDelay = true
                registerReads(key.selector(), from, forwardToSocket)
            } else {
                forwardToSocket.register(key.selector(), SelectionKey.OP_CONNECT, from)
            }
        }
    }
}