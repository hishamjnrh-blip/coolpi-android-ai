package com.coolpi.androidai

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
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

    private var screen = "chat"
    private var selectedProvider = "tokenbom"
    private var busy = false

    private lateinit var root: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var status: TextView
    private lateinit var usage: TextView
    private lateinit var messages: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var input: EditText

    private lateinit var providerSpinner: Spinner
    private lateinit var endpointField: EditText
    private lateinit var keyField: EditText
    private lateinit var modelField: EditText
    private lateinit var limitField: EditText
    private lateinit var appSearch: EditText
    private lateinit var appList: LinearLayout

    private val bg = Color.rgb(12, 14, 18)
    private val panel = Color.rgb(27, 31, 38)
    private val panel2 = Color.rgb(38, 43, 52)
    private val accent = Color.rgb(160, 200, 255)
    private val white = Color.rgb(245, 247, 250)
    private val muted = Color.rgb(165, 173, 185)
    private val userBubble = Color.rgb(38, 67, 105)

    private data class LaunchableApp(val label: String, val packageName: String, val icon: android.graphics.drawable.Drawable?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedProvider = prefs.getString("active_provider", "tokenbom") ?: "tokenbom"
        loadHistory()
        buildShell()
        showScreen("chat")
    }

    private fun buildShell() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setBackgroundColor(bg)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = "Cool Pi AI"
                textSize = 25f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(white)
            })
            status = TextView(this@MainActivity).apply {
                text = "Your personal AI assistant"
                textSize = 12f
                setTextColor(muted)
            }
            addView(status)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(navButton("Settings", "settings"))
        root.addView(header)
        root.addView(space(8))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        nav.addView(navButton("Chat", "chat"), LinearLayout.LayoutParams(0, dp(44), 1f))
        nav.addView(navButton("App Access", "apps"), LinearLayout.LayoutParams(0, dp(44), 1f))
        nav.addView(navButton("Settings", "settings"), LinearLayout.LayoutParams(0, dp(44), 1f))
        root.addView(nav)
        root.addView(space(8))

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
    }

    private fun navButton(textValue: String, target: String): Button =
        Button(this).apply {
            text = textValue
            textSize = 12f
            isAllCaps = false
            setTextColor(white)
            setBackgroundColor(panel2)
            setOnClickListener { showScreen(target) }
        }

    private fun showScreen(target: String) {
        screen = target
        content.removeAllViews()
        when (target) {
            "settings" -> buildSettings()
            "apps" -> buildApps()
            else -> buildChat()
        }
    }

    private fun buildChat() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        usage = TextView(this).apply {
            textSize = 12f
            setTextColor(accent)
        }
        top.addView(usage, LinearLayout.LayoutParams(0, dp(36), 1f))
        top.addView(smallButton("New chat") { confirmClear() })
        page.addView(top)

        chatScroll = ScrollView(this)
        messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(4), dp(2), dp(8))
        }
        chatScroll.addView(messages)
        page.addView(chatScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        input = EditText(this).apply {
            hint = "Message Cool Pi AI…"
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 15f
            setBackgroundColor(panel)
            minLines = 1
            maxLines = 4
            setPadding(dp(12), dp(8), dp(12), dp(8))
            imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendMessage()
                    true
                } else false
            }
        }
        composer.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        composer.addView(smallButton("Send") { sendMessage() })
        page.addView(composer)
        content.addView(page)
        renderHistory()
        updateUsage()
    }

    private fun buildSettings() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = ScrollView(this)
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(4), dp(2), dp(12))
        }
        form.addView(sectionTitle("PROVIDER SETTINGS"))
        providerSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("TokenBOM", "Vyce AI")
            )
            setSelection(if (selectedProvider == "vyce") 1 else 0)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedProvider = if (position == 1) "vyce" else "tokenbom"
                    loadProviderFields()
                }
            }
        }
        form.addView(providerSpinner)
        endpointField = makeField("HTTPS chat completions URL")
        keyField = makeField("API key", true)
        modelField = makeField("Model ID")
        form.addView(endpointField)
        form.addView(keyField)
        form.addView(modelField)
        form.addView(smallButton("Save provider settings") { saveProviderSettings() })
        form.addView(space(14))
        form.addView(sectionTitle("USAGE LIMIT"))
        limitField = makeField("Daily message limit")
        limitField.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        limitField.setText(prefs.getInt("daily_limit", 50).toString())
        form.addView(limitField)
        form.addView(smallButton("Save daily limit") { saveDailyLimit() })
        form.addView(space(14))
        form.addView(sectionTitle("PRIVACY & DEVICE"))
        form.addView(TextView(this).apply {
            text = "Provider keys are stored in this app's private preferences. App launches require an enabled app and your approval."
            textSize = 13f
            setTextColor(muted)
            setPadding(0, 0, 0, dp(8))
        })
        form.addView(smallButton("Open overlay permission settings") {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } catch (_: Exception) {
                toast("Overlay settings are unavailable on this Android version.")
            }
        })
        form.addView(smallButton("Manage allowed apps") { showScreen("apps") })
        scroll.addView(form)
        page.addView(scroll)
        content.addView(page)
        loadProviderFields()
    }

    private fun buildApps() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        page.addView(TextView(this).apply {
            text = "Choose which apps Cool Pi AI may request to open. Apps start disabled. You must approve every launch."
            textSize = 13f
            setTextColor(muted)
            setPadding(0, dp(4), 0, dp(8))
        })
        page.addView(smallButton("Enable Accessibility") {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (_: Exception) {
                toast("Accessibility Settings could not be opened.")
            }
        })

        page.addView(smallButton("Read current screen") {
            val service = CoolPiAccessibilityService.instance
            if (service == null) {
                toast("Enable Cool Pi AI Screen Access in Android Accessibility Settings first.")
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Visible screen text")
                    .setMessage(service.readVisibleText())
                    .setPositiveButton("Close", null)
                    .show()
            }
        })

        val actionTextField = makeField("Exact visible text to tap")
        page.addView(actionTextField)
        page.addView(smallButton("Request tap (confirm first)") {
            val target = actionTextField.text.toString().trim()
            if (target.isBlank()) {
                toast("Enter the exact text of a visible control.")
            } else if (CoolPiAccessibilityService.instance == null) {
                toast("Enable Cool Pi AI Screen Access in Android Accessibility Settings first.")
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Approve screen action?")
                    .setMessage("Allow Cool Pi AI to tap the visible control matching: \"$target\"?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Approve tap") { _, _ ->
                        val service = CoolPiAccessibilityService.instance
                        val success = service?.clickVisibleText(target) == true
                        toast(if (success) "Tap action performed." else "No matching clickable control was found.")
                    }
                    .show()
            }
        })

        page.addView(smallButton("Scroll screen (confirm first)") {
            if (CoolPiAccessibilityService.instance == null) {
                toast("Enable Cool Pi AI Screen Access in Android Accessibility Settings first.")
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Approve scrolling?")
                    .setMessage("Allow Cool Pi AI to scroll the current app screen down?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Approve scroll") { _, _ ->
                        val success = CoolPiAccessibilityService.instance?.scrollForward() == true
                        toast(if (success) "Scroll action performed." else "No scrollable area was found.")
                    }
                    .show()
            }
        })

        page.addView(space(8))
        appSearch = makeField("Search installed apps")
        page.addView(appSearch)
        val scroll = ScrollView(this)
        appList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(appList)
        page.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        appSearch.addTextChangedListener(SimpleTextWatcher { refreshAppList(it) })
        content.addView(page)
        refreshAppList("")
    }

    private fun refreshAppList(query: String) {
        if (!::appList.isInitialized || screen != "apps") return
        appList.removeAllViews()
        val apps = getLaunchableApps().filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
        for (app in apps) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            if (app.icon != null) {
                row.addView(ImageView(this).apply {
                    setImageDrawable(app.icon)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }, LinearLayout.LayoutParams(dp(40), dp(40)))
            }
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = app.label
                    textSize = 14f
                    setTextColor(white)
                })
                addView(TextView(this@MainActivity).apply {
                    text = app.packageName
                    textSize = 10f
                    setTextColor(muted)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val key = appPrefKey(app.packageName)
            val toggle = Switch(this).apply {
                text = "Allow"
                setTextColor(white)
                isChecked = prefs.getBoolean(key, false)
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean(key, checked).apply()
                }
            }
            row.addView(toggle)
            appList.addView(row)
            appList.addView(View(this).apply { setBackgroundColor(panel2) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        }
        if (apps.isEmpty()) {
            appList.addView(TextView(this).apply {
                text = "No launchable apps match this search."
                setTextColor(muted)
                setPadding(0, dp(16), 0, dp(16))
            })
        }
    }

    private fun getLaunchableApps(): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolved = packageManager.queryIntentActivities(intent, 0)
        return resolved.mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == packageName) return@mapNotNull null
            val label = info.loadLabel(packageManager)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
            LaunchableApp(label, pkg, try { info.loadIcon(packageManager) } catch (_: Exception) { null })
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase(Locale.ROOT) }
    }

    private fun appPrefKey(packageName: String) = "app_allowed_$packageName"

    private fun loadProviderFields() {
        if (!::endpointField.isInitialized || !::keyField.isInitialized || !::modelField.isInitialized) return
        val id = selectedProvider
        endpointField.setText(prefs.getString("${id}_endpoint", "") ?: "")
        keyField.setText(prefs.getString("${id}_key", "") ?: "")
        modelField.setText(prefs.getString("${id}_model", if (id == "tokenbom") "deepseek-v4-flash" else "") ?: "")
    }

    private fun saveProviderSettings() {
        val url = endpointField.text.toString().trim()
        val key = keyField.text.toString().trim()
        val modelId = modelField.text.toString().trim()
        if (!url.startsWith("https://", true) || key.isBlank() || modelId.isBlank()) {
            toast("Enter a valid HTTPS endpoint, API key and model ID.")
            return
        }
        prefs.edit()
            .putString("${selectedProvider}_endpoint", url)
            .putString("${selectedProvider}_key", key)
            .putString("${selectedProvider}_model", modelId)
            .putString("active_provider", selectedProvider)
            .apply()
        toast("${if (selectedProvider == "vyce") "Vyce AI" else "TokenBOM"} settings saved on this device.")
    }

    private fun saveDailyLimit() {
        val value = limitField.text.toString().trim().toIntOrNull()
        if (value == null || value !in 1..10000) {
            toast("Choose a daily limit from 1 to 10000.")
            return
        }
        prefs.edit().putInt("daily_limit", value).apply()
        toast("Daily limit saved.")
    }

    private fun sendMessage() {
        if (busy) {
            toast("Please wait for the current response.")
            return
        }
        val prompt = input.text.toString().trim()
        if (prompt.isBlank()) return
        if (usedToday() >= prefs.getInt("daily_limit", 50)) {
            toast("Daily message limit reached.")
            return
        }
        val url = prefs.getString("${selectedProvider}_endpoint", "") ?: ""
        val key = prefs.getString("${selectedProvider}_key", "") ?: ""
        val modelId = prefs.getString("${selectedProvider}_model", "") ?: ""
        if (!url.startsWith("https://", true) || key.isBlank() || modelId.isBlank()) {
            AlertDialog.Builder(this)
                .setTitle("Provider settings needed")
                .setMessage("Save your HTTPS endpoint, API key, and model ID in Settings first.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Settings") { _, _ -> showScreen("settings") }
                .show()
            return
        }
        input.setText("")
        history.put(JSONObject().put("role", "user").put("content", prompt))
        saveHistory()
        addBubble("You", prompt)
        busy = true
        status.text = "Thinking…"
        updateUsage()
        val systemPrompt = "You are Cool Pi AI, a helpful assistant. Reply in the user's language when possible. " +
            "You cannot directly control the device. If the user explicitly asks to open an app, and that app appears in this allowed list, " +
            "you may finish your response with exactly one marker [[OPEN_APP:EXACT_APP_LABEL]] using the app label exactly as listed. " +
            "Allowed apps: " + getLaunchableApps().filter { prefs.getBoolean(appPrefKey(it.packageName), false) }
                .joinToString(", ") { it.label } +
            ". Never request opening an app unless the user explicitly asked for it."
        executor.execute {
            var reply: String
            var successful = false
            try {
                val bodyMessages = JSONArray()
                bodyMessages.put(JSONObject().put("role", "system").put("content", systemPrompt))
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
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val response = stream?.bufferedReader()?.use { it.readText() } ?: ""
                connection.disconnect()
                if (code !in 200..299) throw Exception("HTTP $code: ${response.take(300)}")
                reply = JSONObject(response).optJSONArray("choices")
                    ?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                    ?.takeIf { it.isNotBlank() }
                    ?: throw Exception("The provider returned an unsupported or empty response.")
                successful = true
            } catch (e: Exception) {
                reply = "Request failed: ${e.message ?: "Unknown error"}"
            }

            val finalReply = reply
            val requestSucceeded = successful
            runOnUiThread {
                if (requestSucceeded) {
                    history.put(JSONObject().put("role", "assistant").put("content", finalReply))
                    saveHistory()
                    prefs.edit().putString("usage_date", today()).putInt("daily_used", usedToday() + 1).apply()
                    processLaunchMarker(finalReply)
                }
                addBubble("Cool Pi AI", finalReply.replace(Regex("\\[\\[OPEN_APP:.*?]]"), "").trim().ifBlank { finalReply })
                busy = false
                status.text = "Your personal AI assistant"
                updateUsage()
            }
        }
    }

    private fun processLaunchMarker(reply: String) {
        val match = Regex("\\[\\[OPEN_APP:([^\\]]+)]]").find(reply) ?: return
        val requestedLabel = match.groupValues[1].trim()
        val app = getLaunchableApps().firstOrNull {
            it.label.equals(requestedLabel, true) &&
                prefs.getBoolean(appPrefKey(it.packageName), false)
        } ?: run {
            toast("App launch blocked: app is not enabled or does not match an installed app.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Approve app launch?")
            .setMessage("Cool Pi AI requests permission to open “${app.label}”. Allow this one launch?")
            .setNegativeButton("Deny", null)
            .setPositiveButton("Allow") { _, _ -> launchApp(app) }
            .show()
    }

    private fun launchApp(app: LaunchableApp) {
        if (!prefs.getBoolean(appPrefKey(app.packageName), false)) {
            toast("This app is no longer enabled.")
            return
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (launchIntent == null) {
            toast("Android could not open this app.")
            return
        }
        try {
            startActivity(launchIntent)
        } catch (_: Exception) {
            toast("Could not launch ${app.label}.")
        }
    }

    private fun renderHistory() {
        if (!::messages.isInitialized) return
        messages.removeAllViews()
        for (i in 0 until history.length()) {
            val item = history.optJSONObject(i) ?: continue
            val role = item.optString("role")
            if (role == "user" || role == "assistant") {
                addBubble(if (role == "user") "You" else "Cool Pi AI", item.optString("content").replace(Regex("\\[\\[OPEN_APP:.*?]]"), "").trim())
            }
        }
        if (history.length() == 0) {
            addBubble("Cool Pi AI", "Hello! Configure a provider in Settings to start chatting.")
        }
    }

    private fun addBubble(sender: String, contentText: String) {
        if (!::messages.isInitialized) return
        val bubble = TextView(this).apply {
            text = "$sender\n$contentText"
            textSize = 15f
            setTextColor(white)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(if (sender == "You") userBubble else panel)
        }
        val params = LinearLayout.LayoutParams(
            if (sender == "You") (resources.displayMetrics.widthPixels * 0.82f).toInt() else ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        if (sender == "You") params.gravity = Gravity.END
        params.setMargins(0, dp(3), 0, dp(6))
        messages.addView(bubble, params)
        if (::chatScroll.isInitialized) chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("Start a new chat?")
            .setMessage("This clears the saved conversation on this device. Provider settings remain saved.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ ->
                while (history.length() > 0) history.remove(0)
                saveHistory()
                renderHistory()
            }
            .show()
    }

    private fun loadHistory() {
        try {
            val saved = JSONArray(prefs.getString("chat_history", "[]") ?: "[]")
            for (i in 0 until saved.length()) history.put(saved.getJSONObject(i))
        } catch (_: Exception) {}
    }

    private fun saveHistory() {
        prefs.edit().putString("chat_history", history.toString()).apply()
    }

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun usedToday(): Int {
        if (prefs.getString("usage_date", "") != today()) {
            prefs.edit().putString("usage_date", today()).putInt("daily_used", 0).apply()
            return 0
        }
        return prefs.getInt("daily_used", 0)
    }

    private fun updateUsage() {
        if (::usage.isInitialized) usage.text = "Today: ${usedToday()} / ${prefs.getInt("daily_limit", 50)} messages"
    }

    private fun makeField(hintText: String, secret: Boolean = false): EditText =
        EditText(this).apply {
            hint = hintText
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 14f
            setSingleLine(true)
            setPadding(dp(10), dp(5), dp(10), dp(5))
            setBackgroundColor(panel)
            if (secret) inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

    private fun sectionTitle(textValue: String) = TextView(this).apply {
        text = textValue
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(accent)
        setPadding(0, dp(8), 0, dp(6))
    }

    private fun smallButton(textValue: String, action: () -> Unit) = Button(this).apply {
        text = textValue
        textSize = 12f
        isAllCaps = false
        setTextColor(white)
        setBackgroundColor(panel2)
        setOnClickListener { action() }
    }

    private fun space(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    private class SimpleTextWatcher(val onChanged: (String) -> Unit) : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            onChanged(s?.toString() ?: "")
        }
        override fun afterTextChanged(s: android.text.Editable?) {}
    }
}
