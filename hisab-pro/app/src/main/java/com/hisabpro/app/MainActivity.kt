package com.hisabpro.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.NumberFormat
import java.util.Locale

private val http = OkHttpClient()
private val jsonType = "application/json; charset=utf-8".toMediaType()
private const val CALLBACK = "com.hisabpro.app://login-callback"

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("hisab_pro_session", MODE_PRIVATE) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleCallback(intent)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = androidx.compose.ui.graphics.Color(0xFF087F5B),
                secondary = androidx.compose.ui.graphics.Color(0xFF12304A)
            )) {
                Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color(0xFFF5F7FB)) {
                    HisabApp(
                        initialToken = prefs.getString("access_token", "") ?: "",
                        initialRefresh = prefs.getString("refresh_token", "") ?: "",
                        onLogin = { startGoogleLogin() },
                        onTokens = { access, refresh ->
                            prefs.edit().putString("access_token", access).putString("refresh_token", refresh).apply()
                        },
                        onLogout = { prefs.edit().clear().apply() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCallback(intent)
        recreate()
    }

    private fun startGoogleLogin() {
        val redirect = URLEncoder.encode(CALLBACK, "UTF-8")
        val url = "${BuildConfig.SUPABASE_URL}/auth/v1/authorize?provider=google&redirect_to=$redirect"
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun handleCallback(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "com.hisabpro.app" || uri.host != "login-callback") return
        val fragment = uri.fragment ?: return
        val values = fragment.split("&").mapNotNull { part ->
            val bits = part.split("=", limit = 2)
            if (bits.size == 2) bits[0] to Uri.decode(bits[1]) else null
        }.toMap()
        val access = values["access_token"] ?: return
        val refresh = values["refresh_token"] ?: ""
        prefs.edit().putString("access_token", access).putString("refresh_token", refresh).apply()
    }
}

private data class Customer(val id: String, val name: String, val phone: String)
private data class Tx(val id: String, val type: String, val amount: Double, val note: String, val date: String, val category: String)

private class HisabApi(private val access: String) {
    private val base = BuildConfig.SUPABASE_URL
    private fun currentUserId(): String {
        val req = Request.Builder().url("$base/auth/v1/user")
            .header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            .header("Authorization", "Bearer $access").get().build()
        http.newCall(req).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("Session invalid (${response.code}). Please sign in again.")
            return JSONObject(text).getString("id")
        }
    }
    private fun request(path: String, method: String = "GET", body: String? = null, prefer: String? = null): String {
        val builder = Request.Builder().url("$base/rest/v1/$path")
            .header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            .header("Authorization", "Bearer $access")
            .header("Accept", "application/json")
        if (prefer != null) builder.header("Prefer", prefer)
        when (method) {
            "POST" -> builder.post((body ?: "{}").toRequestBody(jsonType))
            "PATCH" -> builder.patch((body ?: "{}").toRequestBody(jsonType))
            "DELETE" -> builder.delete()
        }
        http.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("Cloud error ${response.code}: ${text.take(220)}")
            return text
        }
    }
    fun customers(): List<Customer> {
        val arr = JSONArray(request("customers?select=id,name,phone&order=created_at.desc"))
        return (0 until arr.length()).map { val o = arr.getJSONObject(it); Customer(o.getString("id"), o.getString("name"), o.optString("phone")) }
    }
    fun transactions(): List<Tx> {
        val arr = JSONArray(request("transactions?select=id,type,amount,note,date,category&order=date.desc,created_at.desc&limit=100"))
        return (0 until arr.length()).map { val o = arr.getJSONObject(it); Tx(o.getString("id"), o.getString("type"), o.getDouble("amount"), o.optString("note"), o.optString("date"), o.optString("category","Other")) }
    }
    fun addCustomer(name: String, phone: String) {
        val obj = JSONObject().put("user_id", currentUserId()).put("name", name.trim()).put("phone", phone.trim().ifBlank { JSONObject.NULL })
        request("customers", "POST", obj.toString(), "return=minimal")
    }
    fun addTransaction(type: String, amount: Double, note: String, category: String) {
        val obj = JSONObject().put("user_id", currentUserId()).put("type", type).put("amount", amount)
            .put("note", note.trim().ifBlank { JSONObject.NULL }).put("category", category.ifBlank { "Other" })
            .put("method", "Cash")
        request("transactions", "POST", obj.toString(), "return=minimal")
    }
}

@Composable
private fun HisabApp(
    initialToken: String,
    initialRefresh: String,
    onLogin: () -> Unit,
    onTokens: (String, String) -> Unit,
    onLogout: () -> Unit
) {
    var token by remember { mutableStateOf(initialToken) }
    var refresh by remember { mutableStateOf(initialRefresh) }
    var customers by remember { mutableStateOf(emptyList<Customer>()) }
    var transactions by remember { mutableStateOf(emptyList<Tx>()) }
    var screen by remember { mutableStateOf("Overview") }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var showCustomer by remember { mutableStateOf(false) }
    var showTransaction by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val money = { n: Double -> NumberFormat.getCurrencyInstance(Locale("en", "PK")).format(n) }

    fun load() {
        if (token.isBlank()) return
        loading = true
        scope.launch {
            try {
                val pair = withContext(Dispatchers.IO) {
                    val api = HisabApi(token)
                    api.customers() to api.transactions()
                }
                customers = pair.first
                transactions = pair.second
                message = "Cloud backup synced"
            } catch (e: Exception) {
                message = e.message ?: "Cloud sync failed. Please sign in again if your session expired."
            } finally { loading = false }
        }
    }
    LaunchedEffect(token) { if (token.isNotBlank()) load() }

    if (token.isBlank()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Hisab Pro", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            Text("Smart business accounting", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Text("Apne customers aur len-den ko cloud par save karein. Dobara login karke data restore karein.")
            Spacer(Modifier.height(24.dp))
            Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text("Continue with Google") }
            Spacer(Modifier.height(8.dp))
            Text("Google sign-in ke liye Supabase Auth mein Google provider enable hona zaroori hai.", style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Hisab Pro", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Text("SMART BUSINESS MANAGER", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = { token = ""; refresh = ""; onLogout(); customers = emptyList(); transactions = emptyList() }) { Text("Sign out") }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Overview", "Transactions", "Customers").forEach { item ->
                FilterChip(selected = screen == item, onClick = { screen = item }, label = { Text(item) })
            }
        }
        Spacer(Modifier.height(8.dp))
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        when (screen) {
            "Overview" -> {
                val income = transactions.filter { it.type == "receive" }.sumOf { it.amount }
                val paid = transactions.filter { it.type == "pay" }.sumOf { it.amount }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryCard("Received", money(income), Modifier.weight(1f))
                    SummaryCard("Paid", money(paid), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                SummaryCard("Balance", money(income - paid), Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Recent transactions", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { load() }) { Text("Sync") }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(transactions.take(20), key = { it.id }) { tx -> TransactionRow(tx, money) }
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { showTransaction = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Add transaction") }
            }
            "Transactions" -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Transactions", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { load() }) { Text("Sync") }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(transactions, key = { it.id }) { tx -> TransactionRow(tx, money) }
                }
                Button(onClick = { showTransaction = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Add transaction") }
            }
            else -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Customers", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { load() }) { Text("Sync") }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(customers, key = { it.id }) { c ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text(c.name, style = MaterialTheme.typography.titleMedium)
                                if (c.phone.isNotBlank()) Text(c.phone, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Button(onClick = { showCustomer = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Add customer") }
            }
        }
    }

    if (showCustomer) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showCustomer = false },
            title = { Text("New customer") },
            text = { Column { OutlinedTextField(name, { name = it }, label = { Text("Customer name") }); OutlinedTextField(phone, { phone = it }, label = { Text("Phone (optional)") }) } },
            confirmButton = { TextButton(onClick = {
                if (name.isBlank()) { message = "Customer name zaroori hai."; return@TextButton }
                scope.launch {
                    try { withContext(Dispatchers.IO) { HisabApi(token).addCustomer(name, phone) }; showCustomer = false; message = "Customer cloud par save ho gaya."; load() }
                    catch (e: Exception) { message = e.message ?: "Save failed" }
                }
            }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showCustomer = false }) { Text("Cancel") } })
    }

    if (showTransaction) {
        var kind by remember { mutableStateOf("receive") }
        var amount by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Sales") }
        AlertDialog(onDismissRequest = { showTransaction = false },
            title = { Text("New transaction") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = kind == "receive", onClick = { kind = "receive" }, label = { Text("Received") })
                        FilterChip(selected = kind == "pay", onClick = { kind = "pay" }, label = { Text("Paid") })
                    }
                    OutlinedTextField(amount, { amount = it }, label = { Text("Amount (PKR)") })
                    OutlinedTextField(note, { note = it }, label = { Text("Description") })
                    OutlinedTextField(category, { category = it }, label = { Text("Category") })
                }
            },
            confirmButton = { TextButton(onClick = {
                val value = amount.toDoubleOrNull()
                if (value == null || value <= 0) { message = "Sahi amount enter karein."; return@TextButton }
                scope.launch {
                    try { withContext(Dispatchers.IO) { HisabApi(token).addTransaction(kind, value, note, category) }; showTransaction = false; message = "Transaction cloud par save ho gayi."; load() }
                    catch (e: Exception) { message = e.message ?: "Save failed" }
                }
            }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showTransaction = false }) { Text("Cancel") } })
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun TransactionRow(tx: Tx, money: (Double) -> String) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(tx.note.ifBlank { tx.category }, style = MaterialTheme.typography.titleSmall)
                Text("${tx.date} • ${tx.category}", style = MaterialTheme.typography.bodySmall)
            }
            Text((if (tx.type == "receive") "+" else "−") + money(tx.amount),
                color = if (tx.type == "receive") androidx.compose.ui.graphics.Color(0xFF087F5B) else androidx.compose.ui.graphics.Color(0xFFC0392B))
        }
    }
}
