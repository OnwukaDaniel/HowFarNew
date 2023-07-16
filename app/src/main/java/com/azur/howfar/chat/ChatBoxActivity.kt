package com.azur.howfar.chat

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity


class ChatBoxActivity : AppCompatActivity(), View.OnClickListener {
    private var username = ""
    private val application = SocketApplication()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        username = intent.getStringExtra("name")!!
        /*socket = application.socket
        try {
            socket.connect()
            socket.emit("join", username)
            socket.on("userjoinedthechat") { data ->
                runOnUiThread { Toast.makeText(this, data.toString(), Toast.LENGTH_SHORT).show(); }
            }
            socket.on("message") { args ->
                val data = args[0] as JSONObject;
                try {
                    println("Data ************************* $data --- $args")
                    val nickname = data.getString("senderNickname");
                    val message = data.getString("message");
                    val chat = TestChat(username = nickname, message = message)
                    dataset.add(chat)
                    runOnUiThread { testChatAdapter.notifyItemInserted(dataset.size) }
                } catch (e: Exception) {
                    println("Error occurred ************************* ${e.printStackTrace()}")
                }
            }
            socket.on("userdisconnect") { data ->
                runOnUiThread { Toast.makeText(this, data.toString(), Toast.LENGTH_LONG).show() }
            }
        } catch (e: Exception) {
            println("Activity socket exception ***************************************** ${e.printStackTrace()}")
        }*/
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            /*R.id.input_send -> {
                val input = binding.inputEt.text.trim().toString()
                if (input == "") return
                socket.emit("messagedetection", username, input)
                binding.inputEt.text.clear()
            }*/
        }
    }
}