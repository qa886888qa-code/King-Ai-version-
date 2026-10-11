package com.kingai.app

import android.os.Bundle
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    var checkingSession by remember { mutableStateOf(true) }
    var createAccount by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var authBusy by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    val appContext = LocalContext.current.applicationContext
    var accountPanel by remember { mutableStateOf(false) }
    var profilePhotoUri by remember { mutableStateOf(appPhotoUri(appContext)) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            profilePhotoUri = uri.toString()
            appSavePhotoUri(appContext, uri.toString())
        }
    }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var chatId by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var storyMode by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var startupAttempt by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(startupAttempt) {
        checkingSession = true
        try {
            val restored = withContext(Dispatchers.IO) { KingAiApi.restoreSession(appContext) }
            session = restored
            if (restored != null) {
                val saved = withContext(Dispatchers.IO) { KingAiApi.resume(restored.accessToken) }
                chatId = saved.chatId
                messages.clear()
                messages.addAll(saved.messages)
                if (messages.isEmpty()) messages.add(ChatMessage("Hello! I'm King AI. How can I help you today?", false))
            }
        } catch (_: Exception) {
            session = null
        } finally {
            checkingSession = false
        }
    }

    Surface(Modifier.fillMaxSize(), color = Ink) {
        if (checkingSession) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                KingLogo()
                Text("Getting King AI ready...", color = TextMuted, modifier = Modifier.padding(top = 16.dp))
                CircularProgressIndicator(color = Gold, modifier = Modifier.padding(top = 18.dp))
            }
        } else if (session == null) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                KingLogo()
                Text("KING AI", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = TextMain,
                    letterSpacing = 2.sp, modifier = Modifier.padding(top = 14.dp))
                Text(if (createAccount) "Create your account" else "Sign in to your account",
                    color = TextMuted, modifier = Modifier.padding(top = 8.dp, bottom = 22.dp))
                OutlinedTextField(value = email, onValueChange = { email = it; authError = null },
                    label = { Text("Email address") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = password, onValueChange = { password = it; authError = null },
                    label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                authError?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)) }
                Button(
                    onClick = {
                        val e = email.trim()
                        val p = password
                        authBusy = true
                        authError = null
                        scope.launch {
                            try {
                                val signedIn = withContext(Dispatchers.IO) {
                                    if (createAccount) KingAiApi.createAccount(appContext, e, p)
                                    else KingAiApi.signIn(appContext, e, p)
                                }
                                session = signedIn
                                accountPanel = false
                                val saved = withContext(Dispatchers.IO) { KingAiApi.resume(signedIn.accessToken) }
                                chatId = saved.chatId
                                messages.clear()
                                messages.addAll(saved.messages)
                                if (messages.isEmpty()) messages.add(ChatMessage("Hello! I'm King AI. How can I help you today?", false))
                            } catch (ex: Exception) {
                                authError = ex.message ?: "We couldn't complete your request. Please try again."
                            } finally { authBusy = false }
                        }
                    },
                    enabled = !authBusy && email.contains("@") && password.length >= 6,
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (authBusy) CircularProgressIndicator(Modifier.size(20.dp), color = Ink, strokeWidth = 2.dp)
                    else Text(if (createAccount) "Create account" else "Log in")
                }
                TextButton(onClick = { createAccount = !createAccount; authError = null }) {
                    Text(if (createAccount) "Already have an account? Log in" else "New to King AI? Create account",
                        color = Gold)
                }
                Text("Your account stays signed in on this device.", color = TextMuted, fontSize = 12.sp)
            }
        } else if (accountPanel) {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                ProfileAvatar(profilePhotoUri, appContext, 104)
                Text("Account", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = TextMain,
                    modifier = Modifier.padding(top = 18.dp))
                Text(session!!.email, color = TextMuted, modifier = Modifier.padding(top = 8.dp))
                OutlinedButton(onClick = { photoPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) { Text("Choose profile photo") }
                Button(onClick = { accountPanel = false }, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) { Text("Back to chat") }
                OutlinedButton(onClick = {
                    KingAiApi.signOut(appContext)
                    session = null
                    messages.clear()
                    chatId = null
                    password = ""
                    accountPanel = false
                }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("Log out") }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
                    ProfileAvatar(profilePhotoUri, appContext, 46)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("KING AI", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextMain, letterSpacing = 2.sp)
                        Text("Your personal AI assistant", color = TextMuted, fontSize = 11.sp)
                    }
                    TextButton(onClick = {
                        val activeSession = session
                        if (!loading && activeSession != null) {
                            loading = true
                            error = null
                            scope.launch {
                                try {
                                    val saved = withContext(Dispatchers.IO) { KingAiApi.resume(activeSession.accessToken) }
                                    chatId = saved.chatId
                                    messages.clear()
                                    messages.addAll(saved.messages)
                                    if (messages.isEmpty()) messages.add(ChatMessage("No saved messages yet. Start chatting and your history will be stored automatically.", false))
                                } catch (e: Exception) {
                                    error = e.message ?: "Couldn't load saved history."
                                } finally { loading = false }
                            }
                        }
                    }) { Text("History", color = Gold, fontWeight = FontWeight.Bold) }
                    TextButton(onClick = { storyMode = !storyMode; error = null }) {
                        Text(if (storyMode) "Chat" else "📖 Story", color = Gold, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { accountPanel = true }) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Account", tint = Gold, modifier = Modifier.size(30.dp))
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
                                Text("Thinking...", color = TextMuted, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
                error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = input, onValueChange = { input = it; error = null },
                        modifier = Modifier.weight(1f), placeholder = { Text(if (storyMode) "Enter a story idea..." else "Message King AI...", color = TextMuted) },
                        shape = RoundedCornerShape(16.dp), maxLines = 4)
                    Button(onClick = {
                        val prompt = input.trim()
                        val activeSession = session
                        if (prompt.isNotEmpty() && !loading && activeSession != null) {
                            messages.add(ChatMessage(prompt, true)); input = ""; loading = true; error = null
                            scope.launch {
                                try {
                                    val aiPrompt = if (storyMode) "Write an engaging original story based on this idea. Give it a title and satisfying ending. Use the language of the idea: " + prompt else prompt
                                    val answer = withContext(Dispatchers.IO) { KingAiApi.send(aiPrompt, activeSession.accessToken, chatId) }
                                    chatId = answer.chatId
                                    messages.add(ChatMessage(answer.text, false))
                                } catch (e: Exception) {
                                    error = e.message ?: "King AI is temporarily unavailable. Please try again."
                                } finally { loading = false }
                            }
                        }
                    }, enabled = input.isNotBlank() && !loading, modifier = Modifier.size(54.dp),
                        shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.Send, contentDescription = "Send") }
                }
                Text(if (storyMode) "Story Maker is on — enter a topic and tap Send." else "Your chat history is saved to your account. Avoid sharing sensitive information.",
                    color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

private fun appPhotoUri(context: Context): String? =
    context.getSharedPreferences("king_ai_account", Context.MODE_PRIVATE).getString("profile_photo_uri", null)

private fun appSavePhotoUri(context: Context, uri: String) {
    context.getSharedPreferences("king_ai_account", Context.MODE_PRIVATE).edit().putString("profile_photo_uri", uri).apply()
}

@Composable
private fun ProfileAvatar(uri: String?, context: Context, size: Int) {
    val bitmap = remember(uri) {
        try {
            if (uri.isNullOrBlank()) null
            else context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
        } catch (_: Exception) { null }
    }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = "Profile photo", modifier = Modifier.size(size.dp).background(Panel, CircleShape))
    } else {
        KingLogo(size)
    }
}

@Composable
private fun KingLogo(size: Int = 66) {
    Box(Modifier.size(size.dp).background(Gold, CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.AutoAwesome, contentDescription = "King AI logo", tint = Ink,
            modifier = Modifier.size((size * 0.52f).dp))
    }
}

private object KingAiApi {
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(20, TimeUnit.SECONDS).callTimeout(65, TimeUnit.SECONDS).build()
    private val baseUrl get() = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val apiKey get() = BuildConfig.SUPABASE_ANON_KEY.trim()

    private fun ensureConfig() {
        if (baseUrl.isBlank() || apiKey.isBlank()) throw IOException("Supabase configuration is missing.")
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
                            json.optString("error").ifBlank { "Request failed (HTTP ${response.code})." }
                        }
                    }
                }
                throw IOException(message)
            }
            return json
        }
    }

    fun restoreSession(context: Context): AuthSession? {
        val prefs = context.getSharedPreferences("king_ai_account", Context.MODE_PRIVATE)
        val savedRefreshToken = prefs.getString("refresh_token", null) ?: return null
        return try {
            val refreshed = execute("$baseUrl/auth/v1/token?grant_type=refresh_token",
                JSONObject().put("refresh_token", savedRefreshToken))
            val access = refreshed.optString("access_token")
            val refresh = refreshed.optString("refresh_token").ifBlank { savedRefreshToken }
            val savedEmail = refreshed.optJSONObject("user")?.optString("email").orEmpty()
            if (access.isBlank()) throw IOException("Please log in again.")
            prefs.edit().putString("refresh_token", refresh).putString("email", savedEmail).apply()
            AuthSession(access, savedEmail, refresh)
        } catch (_: Exception) {
            prefs.edit().clear().apply()
            null
        }
    }

    fun createAccount(context: Context, email: String, password: String): AuthSession {
        if (password.length < 6) throw IOException("Password must be at least 6 characters.")
        val json = execute("$baseUrl/auth/v1/signup", JSONObject().put("email", email).put("password", password))
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        if (access.isBlank() || refresh.isBlank()) {
            throw IOException("Account created. Check your email to confirm it, then log in.")
        }
        saveSession(context, email, refresh)
        return AuthSession(access, email, refresh)
    }

    fun signIn(context: Context, email: String, password: String): AuthSession {
        val json = execute("$baseUrl/auth/v1/token?grant_type=password",
            JSONObject().put("email", email).put("password", password))
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        if (access.isBlank() || refresh.isBlank()) throw IOException("Login failed. Please check your details.")
        saveSession(context, email, refresh)
        return AuthSession(access, email, refresh)
    }

    private fun saveSession(context: Context, email: String, refresh: String) {
        context.getSharedPreferences("king_ai_account", Context.MODE_PRIVATE).edit()
            .putString("refresh_token", refresh).putString("email", email).apply()
    }

    fun signOut(context: Context) {
        context.getSharedPreferences("king_ai_account", Context.MODE_PRIVATE).edit().clear().apply()
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
        if (answer.isBlank()) throw IOException(json.optString("error").ifBlank { "No reply was returned by the server." })
        val id = json.optString("chatId")
        if (id.isBlank()) throw IOException("The server did not return a chat ID.")
        return ChatReply(answer, id)
    }
}
