package com.coolpi.androidai

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {

    private lateinit var endpointInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var keyInput: EditText
    private lateinit var messageInput: EditText
    private lateinit var chatLayout: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var statusText: TextView

    private val history = JSONArray()
    private val prefs by lazy {
        getSharedPreferences("coolpi_settings", MODE_PRIVATE)
    }

    private val dark = Color.rgb(24, 28, 38)
    private val accent = Color.rgb(64, 125, 255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildInterface()
        addMessage("Cool Pi AI", "Hello! I'm ready when you are. Save your TokenBOM API settings to start chatting.")
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 12, 18, 12)
            setBackgroundColor(Color.rgb(245, 247, 251))
        }

        val header = TextView(this).apply {
            text = "Cool Pi AI"
            textSize = 26f
            setTextColor(dark)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 4)
        }
        root.addView(header)

        statusText = TextView(this).apply {
            text = "Your personal AI assistant"
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, 10)
        }
        root.addView(statusText)

        val settingsTitle = TextView(this).apply {
            text = "API SETTINGS"
            textSize = 12f
            setTextColor(dark)
        }
        root.addView(settingsTitle)

        endpointInput = field(
            "Full chat completions URL",
            prefs.getString("endpoint", "") ?: ""
        )
        modelInput = field(
            "Model ID",
            prefs.getString("model", "deepseek-v4-flash") ?: "deepseek-v4-flash"
        )
        keyInput = field(
            "TokenBOM API key",
            prefs.getString("api_key", "") ?: "",
            secret = true
        )

        root.addView(endpointInput)
        root.addView(modelInput)
        root.addView(keyInput)

        val saveButton = Button(this).apply {
            text = "Save API Settings"
            setOnClickListener { saveSettings() }
        }
        root.addView(saveButton)

        val chatTitle = TextView(this).apply {
            text = "CHAT"
            textSize = 12f
            setTextColor(dark)
            setPadding(0, 8, 0, 4)
        }
        root.addView(chatTitle)

        chatScroll = ScrollView(this)
        chatLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(2, 4, 2, 8)
        }
        chatScroll.addView(
            chatLayout,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            chatScroll,
            LinearLayout.LayoutParams( 
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        messageInput = EditText(this).apply {
            hint = "Message Cool Pi AI..."
            textSize = 16f
            minLines = 1
            maxLines = 4
        }
        root.addView(messageInput)

        val sendButton = Button(this).apply {
            text = "Send Message"
            setOnClickListener { sendMessage() }
        }
        root.addView(sendButton)

        setContentView(root)
    }

    private fun field(
        hintText: String,
        initial: String,
        secret: Boolean = false
    ): EditText {
        return EditText(this).apply {
            hint = hintText
            setText(initial)
            textSize = 14f
            singleLine = true
            if (secret) {
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }
    }

    private fun saveSettings() {
        val endpoint = endpointInput.text.toString().trim()
        val model = modelInput.text.toString().trim()
        val key = keyInput.text.toString().trim()

        if (endpoint.isBlank() || model.isBlank() || key.isBlank()) {
            Toast.makeText(
                this,
                "Enter the API URL, model ID and API key first.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (!endpoint.startsWith("https://")) {
            Toast.makeText(
                this,
                "Use a valid HTTPS API URL.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        prefs.edit()
            .putString("endpoint", endpoint)
            .putString("model", model)
            .putString("api_key", key)
            .apply()

        Toast.makeText(this, "API settings saved on this device.", Toast.LENGTH_SHORT).show()
    }

    private fun addMessage(sender: String, message: String) {
        val bubble = TextView(this).apply {
            text = "$sender\n$message"
            textSize = 15f
            setTextColor(if (sender == "You") Color.WHITE else dark)
            setPadding(14, 10, 14, 10)
            setBackgroundColor(
                if (sender == "You") accent else Color.WHITE
            )
        }

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 4, 0, 8)
        chatLayout.addView(bubble, params)
        chatScroll.post { chatScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun sendMessage() {
        val prompt = messageInput.text.toString().trim()
        if (prompt.isBlank()) return

        val endpoint = endpointInput.text.toString().trim()
        val model = modelInput.text.toString().trim()
        val key = keyInput.text.toString().trim()

        if (endpoint.isBlank() || model.isBlank() || key.isBlank()) {
            Toast.makeText(this, "Complete and save your API settings first.", Toast.LENGTH_LONG).show()
            return
        }

        messageInput.setText("")
        addMessage("You", prompt)
        history.put(
            JSONObject()
                .put("role", "user")
                .put("content", prompt)
        )
        statusText.text = "Thinking..."

        Thread {
            var reply: String
            try {
                val connection = URL(endpoint).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 20000
                connection.readTimeout = 60000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $key")

                val body = JSONObject()
                    .put("model", model)
                    .put("messages", history)
                    .put("temperature", 0.7)

                connection.outputStream.use {
                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                }

                val code = connection.responseCode
                val stream = if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val responseText = stream?.bufferedReader()?.use { it.readText() } ?: ""
                connection.disconnect()

                if (code !in 200..299) {
                    throw Exception("API HTTP $code: ${responseText.take(500)}")
                }

                val json = JSONObject(responseText)
                reply = json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .optString("content", "The API returned an empty reply.")

                history.put(
                    JSONObject()
                        .put("role", "assistant")
                        .put("content", reply)
                )
            } catch (e: Exception) {
                if (history.length() > 0) {
                    val last = history.optJSONObject(history.length() - 1)
                    if (last?.optString("role") == "user") {
                        history.remove(history.length() - 1)
                    }
                }
                reply = "Request failed: ${e.message ?: "Unknown error"}"
            }

            runOnUiThread {
                addMessage("Cool Pi AI", reply)
                statusText.text = "Your personal AI assistant"
            }
        }.start()
    }
}
