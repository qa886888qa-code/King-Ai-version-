package com.kingai.app

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

private val Ink = Color(0xFF0B1020)
private val Panel = Color(0xFF151D31)
private val Gold = Color(0xFFFFC857)
private val TextMain = Color(0xFFF4F6FC)
private val TextMuted = Color(0xFF9CA8C2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Gold, onPrimary = Ink, background = Ink,
                surface = Panel, onSurface = TextMain, secondary = TextMuted
            )) { KingAiScreen() }
        }
    }
}

private data class ChatMessage(val text: String, val fromUser: Boolean)
private data class AuthSession(val accessToken: String, val email: String, val refreshToken: String)
private data class SavedChat(val chatId: String?, val messages: List<ChatMessage>)
private data class ChatReply(val text: String, val chatId: String)

@Composable
private fun KingAiScreen() {
    var session by remember { mutableStateOf<AuthSession?>(null) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var chatId by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var startupError by remember { mutableStateOf<String?>(null) }
    var startupAttempt by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext

    LaunchedEffect(startupAttempt) {
        startupError = null
        try {
            val signedIn = withContext(Dispatchers.IO) { KingAiApi.restoreOrCreateGuest(appContext) }
            session = signedIn
            val saved = withContext(Dispatchers.IO) { KingAiApi.resume(signedIn.accessToken) }
            chatId = saved.chatId
            messages.clear()
            messages.addAll(saved.messages)
            if (messages.isEmpty()) messages.add(ChatMessage("Assalam-o-alaikum! Main King AI hoon. Aap kis cheez mein madad chahte hain?", false))
        } catch (e: Exception) {
            startupError = e.message ?: "King AI shuru nahi ho saka."
        }
    }

    Surface(Modifier.fillMaxSize(), color = Ink) {
        if (session == null) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(66.dp).background(Gold, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Ink, modifier = Modifier.size(34.dp))
                }
                Text("KING AI", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = TextMain, letterSpacing = 2.sp, modifier = Modifier.padding(top = 16.dp))
                if (startupError == null) {
                    CircularProgressIndicator(color = Gold, modifier = Modifier.padding(top = 24.dp))
                    Text("King AI tayyar ho raha hai…", color = TextMuted, modifier = Modifier.padding(top = 12.dp))
                } else {
                    Text(startupError!!, color = Color(0xFFFF8A80), fontSize = 13.sp, modifier = Modifier.padding(top = 18.dp))
                    Button(onClick = { startupAttempt++ }, modifier = Modifier.padding(top = 12.dp)) { Text("Dobara koshish karein") }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
                    Box(Modifier.size(46.dp).background(Gold, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Ink, modifier = Modifier.size(26.dp))
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("KING AI", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextMain, letterSpacing = 2.sp)
                        Text("Guest mode · bina login", color = TextMuted, fontSize = 11.sp, maxLines = 1)
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(messages) { message ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
                                Card(colors = CardDefaults.cardColors(containerColor = if (message.fromUser) Color(0xFF34415D) else Color(0xFF202B42)),
                                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(0.88f)) {
                                    Text(message.text, Modifier.padding(14.dp), color = TextMain, fontSize = 15.sp, lineHeight = 22.sp)
                                }
                            }
                        }
                        if (loading) item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), color = Gold, strokeWidth = 2.dp)
                                Text(" King AI soch raha hai…", color = TextMuted, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
                error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = input, onValueChange = { input = it; error = null },
                        modifier = Modifier.weight(1f), placeholder = { Text("King AI se kuch poochhein…", color = TextMuted) },
                        shape = RoundedCornerShape(16.dp), maxLines = 4)
                    Button(onClick = {
                        val prompt = input.trim()
                        val activeSession = session
                        if (prompt.isNotEmpty() && !loading && activeSession != null) {
                            messages.add(ChatMessage(prompt, true)); input = ""; loading = true; error = null
                            scope.launch {
                                try {
                                    val answer = withContext(Dispatchers.IO) { KingAiApi.send(prompt, activeSession.accessToken, chatId) }
                                    chatId = answer.chatId
                                    messages.add(ChatMessage(answer.text, false))
                                } catch (e: Exception) {
                                    error = e.message ?: "King AI service abhi available nahi hai."
                                } finally { loading = false }
                            }
                        }
                    }, enabled = input.isNotBlank() && !loading, modifier = Modifier.size(54.dp),
                        shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.Send, contentDescription = "Send") }
                }
                Text("Chat history server par save hoti hai. Sensitive maloomat share na karein.",
                    color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

private object KingAiApi {
    private val client = OkHttpClient.Builder().callTimeout(35, TimeUnit.SECONDS).build()
    private val baseUrl get() = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val apiKey get() = BuildConfig.SUPABASE_ANON_KEY.trim()

    private fun ensureConfig() {
        if (baseUrl.isBlank() || apiKey.isBlank()) throw IOException("Supabase URL/API key configure nahi hain.")
    }

    private fun execute(url: String, body: JSONObject, accessToken: String? = null): JSONObject {
        ensureConfig()
        val requestBody = body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val builder = Request.Builder().url(url).post(requestBody)
            .header("apikey", apiKey).header("Accept", "application/json")
        if (accessToken != null) builder.header("Authorization", "Bearer $accessToken")
        client.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
            if (!response.isSuccessful) {
                val message = json.optString("msg").ifBlank {
                    json.optString("message").ifBlank {
                        json.optString("error_description").ifBlank {
                            json.optString("error").ifBlank { "Request failed (HTTP " + response.code + ")." }
                        }
                    }
                }
                throw IOException(message)
            }
            return json
        }
    }

    fun restoreOrCreateGuest(context: Context): AuthSession {
        val prefs = context.getSharedPreferences("king_ai_guest", Context.MODE_PRIVATE)
        val savedRefreshToken = prefs.getString("refresh_token", null)
        if (!savedRefreshToken.isNullOrBlank()) {
            try {
                val refreshed = execute(
                    "$baseUrl/auth/v1/token?grant_type=refresh_token",
                    JSONObject().put("refresh_token", savedRefreshToken)
                )
                val access = refreshed.optString("access_token")
                val refresh = refreshed.optString("refresh_token").ifBlank { savedRefreshToken }
                if (access.isNotBlank()) {
                    prefs.edit().putString("refresh_token", refresh).apply()
                    return AuthSession(access, "Guest mode", refresh)
                }
            } catch (_: Exception) {
                // An expired guest session is replaced with a fresh anonymous identity below.
            }
        }

        // Supabase creates a private guest identity without showing a login screen.
        val json = execute("$baseUrl/auth/v1/signup", JSONObject())
        val token = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        if (token.isBlank() || refresh.isBlank()) {
            throw IOException("Guest mode enable nahi hai. Supabase Auth settings mein Anonymous Sign-Ins enable karein.")
        }
        prefs.edit().putString("refresh_token", refresh).apply()
        return AuthSession(token, "Guest mode", refresh)
    }

    fun resume(token: String): SavedChat {
        val json = execute(BuildConfig.KING_AI_CHAT_FUNCTION_URL, JSONObject().put("action", "resume_latest"), token)
        val id = json.optString("chatId").takeIf { it.isNotBlank() && it != "null" }
        val saved = mutableListOf<ChatMessage>()
        val array = json.optJSONArray("messages")
        if (array != null) {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                saved.add(ChatMessage(item.optString("content"), item.optString("role") == "user"))
            }
        }
        return SavedChat(id, saved)
    }

    fun send(prompt: String, token: String, chatId: String?): ChatReply {
        val body = JSONObject().put("message", prompt)
        if (!chatId.isNullOrBlank()) body.put("chatId", chatId)
        val json = execute(BuildConfig.KING_AI_CHAT_FUNCTION_URL, body, token)
        val answer = json.optString("reply")
        if (answer.isBlank()) throw IOException(json.optString("error").ifBlank { "Server response mein reply nahi mila." })
        val id = json.optString("chatId")
        if (id.isBlank()) throw IOException("Server ne chat ID return nahi ki.")
        return ChatReply(answer, id)
    }
}
