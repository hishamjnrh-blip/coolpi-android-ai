package com.coolpi.androidai

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("coolpi_settings", MODE_PRIVATE) }
    private val history = JSONArray()

    private lateinit var provider: Spinner
    private lateinit var endpoint: EditText
    private lateinit var apiKey: EditText
    private lateinit var model: EditText
    private lateinit var dailyLimit: EditText
    private lateinit var approval: CheckBox
    private lateinit var messages: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var usage: TextView

    private val bg = Color.rgb(17, 24, 39)
    private val panel = Color.rgb(31, 41, 55)
    private val accent = Color.rgb(96, 165, 250)
    private val white = Color.rgb(243, 244, 246)
    private val muted = Color.rgb(156, 163, 175)

    private fun providerId(): String =
        if (provider.selectedItemPosition == 1) "vyce" else "tokenbom"

    private fun setting(suffix: String) = "${providerId()}_$suffix"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadHistory()
        buildUi()
        showHistory()
        updateUsage()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 10, 16, 12)
            setBackgroundColor(bg)
        }

        root.addView(TextView(this).apply {
            text = "Cool Pi AI"
            textSize = 27f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
        })

        status = TextView(this).apply {
            text = "Your personal AI assistant"
            textSize = 13f
            setTextColor(muted)
            setPadding(0, 2, 0, 8)
        }
        root.addView(status)

        val settings = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 8, 12, 8)
            setBackgroundColor(panel)
        }
        settings.addView(label("API SETTINGS"))

        provider = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("TokenBOM", "Vyce AI")
            )
            setSelection(if (prefs.getString("active_provider", "tokenbom") == "vyce") 1 else 0)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    loadProviderFields()
                }
            }
        }
        settings.addView(provider)

        endpoint = field("HTTPS chat completions URL")
        apiKey = field("API key", secret = true)
        model = field("Model ID")
        settings.addView(endpoint)
        settings.addView(apiKey)
        settings.addView(model)

        settings.addView(Button(this).apply {
            text = "Save provider settings"
            setOnClickListener { saveProviderSettings() }
        })

        val limits = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        dailyLimit = field("Daily message limit").apply {
            setText(prefs.getInt("daily_limit", 50).toString())
        }
        limits.addView(dailyLimit, LinearLayout.LayoutParams(0, dp(48), 1f))
        limits.addView(Button(this).apply {
            text = "Save limit"
            setOnClickListener { saveDailyLimit() }
        })
        settings.addView(limits)

        approval = CheckBox(this).apply {
            text = "Require confirmation for supported actions"
            setTextColor(white)
            isChecked = prefs.getBoolean("approval_required", true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("approval_required", checked).apply()
            }
        }
        settings.addView(approval)

        val settingsScroll = ScrollView(this).apply {
            addView(settings)
        }
        root.addView(
            settingsScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(230)
            )
        )

        root.addView(label("CHAT"))

        usage = TextView(this).apply {
            setTextColor(accent)
            textSize = 12f
            setPadding(0, 4, 0, 6)
        }
        root.addView(usage)

        scroll = ScrollView(this)
        messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(2, 4, 2, 8)
        }
        scroll.addView(messages)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        input = EditText(this).apply {
            hint = "Message Cool Pi AI..."
            setHintTextColor(muted)
            setTextColor(white)
            setBackgroundColor(panel)
            minLines = 1
            maxLines = 4
            setPadding(12, 8, 12, 8)
        }
        root.addView(input)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        actions.addView(Button(this).apply {
            text = "Send"
            setOnClickListener { sendMessage() }
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        actions.addView(Button(this).apply {
            text = "Clear chat"
            setOnClickListener { confirmClear() }
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(actions)

        setContentView(root)
        loadProviderFields()
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(accent)
        setPadding(0, 6, 0, 6)
    }

    private fun field(hintText: String, secret: Boolean = false) =
        EditText(this).apply {
            hint = hintText
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 14f
            setSingleLine(true)
            setPadding(10, 4, 10, 4)
            if (secret) {
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun loadProviderFields() {
        if (!::provider.isInitialized || !::endpoint.isInitialized) return
        endpoint.setText(prefs.getString(setting("endpoint"), "") ?: "")
        apiKey.setText(prefs.getString(setting("key"), "") ?: "")
        model.setText(
            prefs.getString(
                setting("model"),
                if (providerId() == "tokenbom") "deepseek-v4-flash" else ""
            ) ?: ""
        )
    }

    private fun saveProviderSettings() {
        val url = endpoint.text.toString().trim()
        val key = apiKey.text.toString().trim()
        val modelId = model.text.toString().trim()

        if (!url.startsWith("https://") || key.isBlank() || modelId.isBlank()) {
            toast("Enter a valid HTTPS endpoint, API key and model ID.")
            return
        }

        prefs.edit()
            .putString(setting("endpoint"), url)
            .putString(setting("key"), key)
            .putString(setting("model"), modelId)
            .putString("active_provider", providerId())
            .apply()

        toast("${provider.selectedItem} settings saved on this device.")
    }

    private fun saveDailyLimit() {
        val value = dailyLimit.text.toString().toIntOrNull()
        if (value == null || value !in 1..10000) {
            toast("Choose a daily limit from 1 to 10000.")
            return
        }
        prefs.edit().putInt("daily_limit", value).apply()
        updateUsage()
        toast("Daily limit saved.")
    }

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun usedToday(): Int {
        val date = today()
        if (prefs.getString("usage_date", "") != date) {
            prefs.edit().putString("usage_date", date).putInt("daily_used", 0).apply()
            return 0
        }
        return prefs.getInt("daily_used", 0)
    }

    private fun updateUsage() {
        if (!::usage.isInitialized) return
        usage.text = "Daily messages: ${usedToday()}/${prefs.getInt("daily_limit", 50)}"
    }

    private fun loadHistory() {
        val saved = prefs.getString("chat_history", "[]") ?: "[]"
        try {
            val array = JSONArray(saved)
            for (i in 0 until array.length()) history.put(array.getJSONObject(i))
        } catch (_: Exception) {}
    }

    private fun saveHistory() {
        prefs.edit().putString("chat_history", history.toString()).apply()
    }

    private fun showHistory() {
        for (i in 0 until history.length()) {
            val item = history.optJSONObject(i) ?: continue
            val role = item.optString("role")
            if (role == "user" || role == "assistant") {
                addBubble(if (role == "user") "You" else "Cool Pi AI", item.optString("content"))
            }
        }
        if (history.length() == 0) {
            addBubble("Cool Pi AI", "Hello! Choose a provider, save your API settings, and start chatting.")
        }
    }

    private fun addBubble(sender: String, content: String) {
        val bubble = TextView(this).apply {
            text = "$sender\n$content"
            textSize = 15f
            setTextColor(white)
            setPadding(12, 10, 12, 10)
            setBackgroundColor(if (sender == "You") Color.rgb(37, 76, 130) else panel)
        }
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, dp(3), 0, dp(6))
        messages.addView(bubble, params)
        if (::scroll.isInitialized) scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun sendMessage() {
        val prompt = input.text.toString().trim()
        if (prompt.isBlank()) return

        val limit = prefs.getInt("daily_limit", 50)
        if (usedToday() >= limit) {
            toast("Daily message limit reached.")
            return
        }

        val url = endpoint.text.toString().trim()
        val key = apiKey.text.toString().trim()
        val modelId = model.text.toString().trim()
        if (!url.startsWith("https://") || key.isBlank() || modelId.isBlank()) {
            toast("Select a provider and save valid API settings first.")
            return
        }

        val risky = listOf("open app", "launch app", "open browser", "search the web")
            .any { prompt.lowercase(Locale.ROOT).contains(it) }
        if (risky && approval.isChecked) {
            AlertDialog.Builder(this)
                .setTitle("Confirm supported action")
                .setMessage("This request may involve an external action. Continue sending it to the AI provider?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue") { _, _ -> performRequest(prompt, url, key, modelId) }
                .show()
        } else {
            performRequest(prompt, url, key, modelId)
        }
    }

    private fun performRequest(prompt: String, url: String, key: String, modelId: String) {
        input.setText("")
        addBubble("You", prompt)
        history.put(JSONObject().put("role", "user").put("content", prompt))
        saveHistory()
        status.text = "Thinking..."
        updateUsage()

        executor.execute {
            var reply: String
            try {
                val bodyMessages = JSONArray()
                bodyMessages.put(
                    JSONObject().put("role", "system")
                        .put("content", "You are Cool Pi AI, a helpful assistant. Reply in the user's language when possible.")
                )
                synchronized(history) {
                    for (i in 0 until history.length()) bodyMessages.put(history.getJSONObject(i))
                }

                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 60000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $key")
                }

                val payload = JSONObject()
                    .put("model", modelId)
                    .put("messages", bodyMessages)
                    .put("temperature", 0.7)

                connection.outputStream.use {
                    it.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val response = stream?.bufferedReader()?.use { it.readText() } ?: ""
                connection.disconnect()

                if (code !in 200..299) throw Exception("HTTP $code: ${response.take(400)}")

                val json = JSONObject(response)
                reply = json.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?.takeIf { it.isNotBlank() }
                    ?: throw Exception("The provider returned an unsupported or empty response.")

                history.put(JSONObject().put("role", "assistant").put("content", reply))
                saveHistory()
                prefs.edit().putInt("daily_used", usedToday() + 1).putString("usage_date", today()).apply()
            } catch (e: Exception) {
                reply = "Request failed: ${e.message ?: "Unknown error"}"
            }

            runOnUiThread {
                addBubble("Cool Pi AI", reply)
                status.text = "Your personal AI assistant"
                updateUsage()
            }
        }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("Clear chat history?")
            .setMessage("This deletes the saved conversation from this device.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ ->
                while (history.length() > 0) history.remove(0)
                saveHistory()
                messages.removeAllViews()
                addBubble("Cool Pi AI", "Chat cleared. Your provider settings remain saved.")
            }
            .show()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}
