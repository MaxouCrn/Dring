package fr.dring.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.messaging.FirebaseMessaging
import fr.dring.app.poke.PokeWorker
import fr.dring.app.ui.theme.DringTheme
import fr.dring.app.widget.WidgetStateStore
import kotlinx.coroutines.delay
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class MainActivity : ComponentActivity() {

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            DringTheme {
                DringScreen()
            }
        }
    }
}

private val GradientBg = Brush.linearGradient(
    colors = listOf(
        Color(0xFFFF80AB),
        Color(0xFFE91E63),
        Color(0xFF6A1B9A),
    )
)

@Serializable
private data class TokenUpdate(val fcm_token: String)

private const val TIP_PREFS = "dring_tips"
private const val TIP_KEY_WIDGET_DISMISSED = "widget_tip_dismissed"

@Composable
private fun DringScreen() {
    val ctx = LocalContext.current
    var current by remember { mutableStateOf(Identity.get(ctx)) }
    var tipDismissed by remember {
        mutableStateOf(
            ctx.getSharedPreferences(TIP_PREFS, android.content.Context.MODE_PRIVATE)
                .getBoolean(TIP_KEY_WIDGET_DISMISSED, false)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GradientBg),
    ) {
        if (current == null) {
            UserPicker(onPick = {
                Identity.set(ctx, it)
                current = it
            })
        } else {
            ConnectedScreen(user = current!!, tipVisible = !tipDismissed)
        }

        // Tip flottant en bas, dismissible
        if (current != null) {
            AnimatedVisibility(
                visible = !tipDismissed,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(16.dp),
            ) {
                WidgetTipCard(onDismiss = {
                    ctx.getSharedPreferences(TIP_PREFS, android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean(TIP_KEY_WIDGET_DISMISSED, true).apply()
                    tipDismissed = true
                })
            }
        }
    }
}

@Composable
private fun UserPicker(onPick: (Config.User) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Logo(size = 140.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Dring",
            color = Color.White,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Qui es-tu ?",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 18.sp,
        )
        Spacer(Modifier.height(40.dp))

        Config.ALL_USERS.forEach { user ->
            ElevatedButton(
                onClick = { onPick(user) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFFAD1457),
                ),
            ) {
                Text(
                    user.displayName,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ConnectedScreen(user: Config.User, tipVisible: Boolean) {
    val ctx = LocalContext.current
    var counts by remember { mutableStateOf(TodayCounts(0, 0)) }
    var lastReceived by remember { mutableStateOf<LastReceived?>(null) }
    val partnerName = Config.ALL_USERS.first { it.id != user.id }.displayName
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(user.id) {
        uploadFcmToken(user.id)
        counts = Stats.loadTodayCounts(user.id)
        lastReceived = LastReceivedStore.get(ctx)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                lastReceived = LastReceivedStore.get(ctx)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val now = System.currentTimeMillis()
    val showReplyCard = lastReceived?.let {
        !it.handled && (now - it.receivedAt) < LastReceivedStore.FRESH_WINDOW_MS
    } ?: false

    // Cooldown timer + détection des nouveaux Dring reçus (poll 500ms)
    var cooldownMs by remember { mutableLongStateOf(WidgetStateStore.remainingCooldownMs(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            cooldownMs = WidgetStateStore.remainingCooldownMs(ctx)
            // Détecte les Drings reçus pendant que l'app est au premier plan
            val current = LastReceivedStore.get(ctx)
            if (current?.receivedAt != lastReceived?.receivedAt) {
                lastReceived = current
            }
            delay(500L)
        }
    }
    val cooldownActive = cooldownMs > 0
    val cooldownSecs = ((cooldownMs / 1000) + 1).toInt()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top bar avec logo, nom, déconnexion
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Logo(size = 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Dring", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(user.displayName, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(24.dp))

        // Card stats
        StatsCard(partnerName = partnerName, counts = counts)

        Spacer(Modifier.height(20.dp))

        // Mode "répondre" : la card prend toute la place restante, la grille est cachée.
        // Mode "envoyer" : titre + grille de quick messages.
        if (showReplyCard) {
            ReplyCard(
                partnerName = partnerName,
                message = lastReceived!!.message,
                onReply = { reply ->
                    PokeWorker.enqueue(ctx, reply, isReply = true)
                    LastReceivedStore.markHandled(ctx)
                    lastReceived = LastReceivedStore.get(ctx)
                },
                onDismiss = {
                    LastReceivedStore.markHandled(ctx)
                    lastReceived = LastReceivedStore.get(ctx)
                },
            )
            Spacer(Modifier.weight(1f))
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Envoie un Dring spécifique",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (cooldownActive) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.18f),
                    ) {
                        Text(
                            "⏳ ${cooldownSecs}s",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            MessageGrid(
                modifier = Modifier.weight(1f),
                enabled = !cooldownActive,
            )
        }

        // Padding bas dynamique : place pour le tip flottant + nav bar
        Spacer(Modifier.height(if (tipVisible) 120.dp else 24.dp))
    }
}

@Composable
private fun StatsCard(partnerName: String, counts: TodayCounts) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Aujourd'hui",
                color = Color(0xFFAD1457),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatBlock(
                    label = "Envoyés à $partnerName",
                    value = counts.sent.toString(),
                    emoji = "💌",
                    modifier = Modifier.weight(1f),
                )
                StatBlock(
                    label = "Reçus de $partnerName",
                    value = counts.received.toString(),
                    emoji = "💖",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String, emoji: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 28.sp)
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFFAD1457))
        Text(
            label,
            fontSize = 12.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MessageGrid(modifier: Modifier = Modifier, enabled: Boolean = true) {
    val ctx = LocalContext.current
    val rows = Config.QUICK_MESSAGES.chunked(3)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { quick ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (enabled) Color.White else Color.White.copy(alpha = 0.55f),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        enabled = enabled,
                        onClick = {
                            PokeWorker.enqueue(ctx, "${quick.emoji} ${quick.text}")
                        },
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(quick.emoji, fontSize = 36.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                quick.text,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                color = Color(0xFFAD1457),
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                            )
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ReplyCard(
    partnerName: String,
    message: String,
    onReply: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val replies = remember(message) { ReplyPresets.forMessage(message) }
    var customReply by rememberSaveable { mutableStateOf("") }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "📩 $partnerName t'a envoyé",
                    color = Color(0xFFAD1457),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Text(
                        "✕",
                        fontSize = 16.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                message,
                color = Color.Black,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Réponses rapides",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            replies.forEach { reply ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFCE4EC),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    onClick = { onReply(reply) },
                ) {
                    Text(
                        reply,
                        color = Color(0xFFAD1457),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "Réponse personnalisée",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = customReply,
                    onValueChange = { customReply = it },
                    placeholder = { Text("Écris ta réponse…", fontSize = 13.sp) },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFE91E63),
                        unfocusedBorderColor = Color(0xFFE91E63).copy(alpha = 0.4f),
                        cursorColor = Color(0xFFE91E63),
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (customReply.isNotBlank()) Color(0xFFE91E63) else Color.Gray.copy(alpha = 0.3f),
                    onClick = {
                        if (customReply.isNotBlank()) {
                            onReply(customReply.trim())
                            customReply = ""
                        }
                    },
                    enabled = customReply.isNotBlank(),
                ) {
                    Text(
                        "➤",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetTipCard(onDismiss: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
            contentColor = Color(0xFFAD1457),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "💡 Astuce",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Ajoute le widget Dring sur ton écran d'accueil pour envoyer un poke en 1 tap.",
                    fontSize = 12.sp,
                    color = Color.DarkGray,
                )
            }
            IconButton(onClick = onDismiss) {
                Text(
                    "✕",
                    fontSize = 18.sp,
                    color = Color(0xFFAD1457),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun Logo(size: androidx.compose.ui.unit.Dp) {
    val ctx = LocalContext.current
    val logoId = remember {
        ctx.resources.getIdentifier("ic_dring_logo", "drawable", ctx.packageName)
    }
    val resId = if (logoId != 0) logoId else R.drawable.ic_widget_bell
    androidx.compose.foundation.Image(
        painter = painterResource(resId),
        contentDescription = "Dring logo",
        modifier = Modifier.size(size),
    )
}

private fun uploadFcmToken(userId: String) {
    FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                Supabase.client.postgrest.from("profiles")
                    .update(TokenUpdate(token)) { filter { eq("id", userId) } }
            }
        }
    }
}
