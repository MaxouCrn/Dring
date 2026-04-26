package fr.dring.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import fr.dring.app.Config
import fr.dring.app.ui.theme.DringTheme

class LargeWidgetConfigActivity : ComponentActivity() {

    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(RESULT_CANCELED)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Initialise les slots à partir des prefs (ou défauts)
        val initial = WidgetConfigStore.getLargeSlots(applicationContext, widgetId)

        setContent {
            DringTheme {
                LargeConfigScreen(
                    initial = initial,
                    onConfirm = { slots ->
                        slots.forEachIndexed { i, body ->
                            WidgetConfigStore.setLargeSlot(applicationContext, widgetId, i, body)
                        }
                        DringLargeWidgetProvider.refreshAll(applicationContext)
                        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                        setResult(Activity.RESULT_OK, result)
                        finish()
                    },
                )
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

@Composable
private fun LargeConfigScreen(
    initial: List<String>,
    onConfirm: (List<String>) -> Unit,
) {
    val slots = remember { mutableStateListOf<String>().apply { addAll(initial) } }
    var pickerForSlot by remember { mutableStateOf<Int?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GradientBg)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(
                "Configure ton widget",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Tape sur un slot pour changer le Dring associé.",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(20.dp))

            // Aperçu de la grille 4×2
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0..3, 4..7).forEach { range ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        range.forEach { slotIndex ->
                            val body = slots[slotIndex]
                            val (emoji, label) = WidgetLabels.emojiAndLabel(body)
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White,
                                modifier = Modifier.weight(1f).height(96.dp),
                                onClick = { pickerForSlot = slotIndex },
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(emoji, fontSize = 28.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        label,
                                        color = Color(0xFFAD1457),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { onConfirm(slots.toList()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFFAD1457),
                ),
            ) {
                Text("Valider", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    // Dialog de sélection
    pickerForSlot?.let { idx ->
        Dialog(onDismissRequest = { pickerForSlot = null }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Slot ${idx + 1}",
                        color = Color(0xFFAD1457),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Config.QUICK_MESSAGES.forEach { msg ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFCE4EC),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            onClick = {
                                slots[idx] = msg.body()
                                pickerForSlot = null
                            },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(msg.emoji, fontSize = 22.sp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    msg.text,
                                    color = Color(0xFFAD1457),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
