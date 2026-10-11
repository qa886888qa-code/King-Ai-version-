package com.kingai.app

import android.os.Bundle
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

@Composable
private fun KingAiScreen() {
    val messages = remember { mutableStateListOf(
        ChatMessage("Assalam-o-alaikum! Main King AI hoon. Aap kis cheez mein madad chahte hain?", false)
    ) }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Surface(Modifier.fillMaxSize(), color = Ink) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
                Box(Modifier.size(46.dp).background(Gold, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Ink, modifier = Modifier.size(26.dp))
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("KING AI", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextMain, letterSpacing = 2.sp)
                    Text("Your AI assistant", color = TextMuted, fontSize = 13.sp)
                }
                Text("BETA", color = Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
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
                    if (prompt.isNotEmpty() && !loading) {
                        messages.add(ChatMessage(prompt, true)); input = ""; loading = true; error = null
                        scope.launch {
                            try {
                                val answer = withContext(Dispatchers.IO) { sendToKingAi(prompt) }
                                messages.add(ChatMessage(answer, false))
                            } catch (e: Exception) {
                                error = e.message ?: "King AI service abhi available nahi hai."
                                messages.add(ChatMessage("Maazrat, abhi AI server se rabta nahi ho saka. Backend configuration check karein aur dobara koshish karein.", false))
                            } finally { loading = false }
                        }
                    }
                }, enabled = input.isNotBlank() && !loading, modifier = Modifier.size(54.dp),
                    shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.Send, contentDescription = "Send") }
            }
            Text("AI jawab ghalat bhi ho sakta hai. Sensitive maloomat share na karein.",
                color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

private fun sendToKingAi(prompt: String): String {
    val endpoint = BuildConfig.KING_AI_CHAT_FUNCTION_URL.trim()
    if (endpoint.isBlank()) throw IOException("AI backend abhi connect nahi hua. Server function URL configure karna hoga.")
    val client = OkHttpClient()
    val body = JSONObject().put("message", prompt).toString().toRequestBody("application/json; charset=utf-8".toMediaType())
    val builder = Request.Builder().url(endpoint).post(body).header("Accept", "application/json")
    if (BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) {
        builder.header("apikey", BuildConfig.SUPABASE_ANON_KEY)
        builder.header("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
    }
    client.newCall(builder.build()).execute().use { response ->
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw IOException("AI server error (" + response.code + ").")
        val json = JSONObject(text)
        return json.optString("reply").ifBlank {
            json.optString("answer").ifBlank { throw IOException("Server response mein reply/answer field nahi mili.") }
        }
    }
}
