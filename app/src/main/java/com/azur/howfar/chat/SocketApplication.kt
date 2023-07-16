package com.azur.howfar.chat

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.client.SocketIOException
import io.socket.emitter.Emitter
import io.socket.engineio.client.EngineIOException

class SocketApplication {
    lateinit var socket: Socket
    private val onConnect = Emitter.Listener { Log.d("Socket ********************************", "Socket Connected!") }
    private val onConnectError = Emitter.Listener { args->
        try {
            if (args[0] is EngineIOException){
                Log.d("Socket ******************************** ${(args[0] as EngineIOException).printStackTrace()}", "Socket error!")
            } else if (args[0] is SocketIOException) {
                Log.d("Socket ******************************** ${(args[0] as SocketIOException).printStackTrace()}", "Socket error!")
            }
        } catch (e: Exception){

        }
    }
    private val onDisconnect = Emitter.Listener {}
    init {
        try{ socket = IO.socket("http://192.168.56.1:3000")
            val op = IO.Options()
            op.timeout = 10000
            socket = IO.socket("http://192.168.14.199:3000/", op)
            socket.on(Socket.EVENT_CONNECT_ERROR, onConnectError)
            socket.on(Socket.EVENT_CONNECT, onConnect)
            socket.on(Socket.EVENT_DISCONNECT, onDisconnect)
        } catch (e:Exception){
            println("Socket exception **************************************** ${e.printStackTrace()}")
        }
    }
}