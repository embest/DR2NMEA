package net.embest.gps.dr2nmea.utils

import android.os.Handler
import android.util.Log
import java.io.IOException
import java.net.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class UdpSocket(private val handler: Handler) {
    private val TAG = "UdpSocket"

    private var mThreadPool: ExecutorService? = null
    private var socket: DatagramSocket? = null
    private var receivePacket: DatagramPacket? = null
    private val BUFFER_LENGTH = 256
    private val receiveByte = ByteArray(BUFFER_LENGTH)

    private val UDP_PORT_GPSD_RX   = 48250
    private val UDP_PORT_GPSD_TX   = 48251

    private var isThreadRunning = false
    private lateinit var clientThread: Thread

    init {
        //根據CPU數量建立執行續池
        val cpuNumbers = Runtime.getRuntime().availableProcessors()
        mThreadPool = Executors.newFixedThreadPool(cpuNumbers * 5)
    }

    fun startUDPSocket() {
        if (socket != null) return
        try {
            socket = DatagramSocket(UDP_PORT_GPSD_TX)
            if (receivePacket == null)
            // 接收數據封包
                receivePacket = DatagramPacket(receiveByte, BUFFER_LENGTH)
            startSocketThread()
        } catch (e: SocketException) {
            e.printStackTrace()
        }

    }

    private fun startSocketThread() {
        clientThread = Thread(Runnable {
            Log.d(TAG, "clientThread is running...")
            receiveMessage()
        })
        isThreadRunning = true
        clientThread.start()
    }

    private fun receiveMessage() {
        while (isThreadRunning) {
            try {
                socket?.receive(receivePacket)

                if (receivePacket == null || receivePacket?.length == 0)
                    continue

                //multi thread to handle multi packets
                mThreadPool?.execute {
                    val strReceive = String(receivePacket!!.data, receivePacket!!.offset, receivePacket!!.length)
                    Log.d(TAG, strReceive + " from " + receivePacket!!.address.hostAddress + ":" + receivePacket!!.port)

                    handler.sendMessage(handler.obtainMessage(1,strReceive))
                    receivePacket?.length = BUFFER_LENGTH
                }
            } catch (e: IOException) {
                stopUDPSocket()
                e.printStackTrace()
                return
            }
        }
    }

    fun sendMessage(message: String) {
        mThreadPool?.execute {
            try {
                // 廣播封包
                val targetAddress = InetAddress.getByName("127.0.0.1") //依照環境而變
                val packet = DatagramPacket(message.toByteArray(), message.length, targetAddress, UDP_PORT_GPSD_RX)
                socket?.send(packet)
            } catch (e: UnknownHostException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun stopUDPSocket() {
        isThreadRunning = false
        receivePacket = null
        clientThread.interrupt()
        if (socket != null) {
            socket?.close()
            socket = null
        }
    }

}