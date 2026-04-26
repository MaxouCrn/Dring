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
import fr.dring.app.Config
import fr.dring.app.ui.theme.DringTheme

class SmallWidgetConfigActivity : ComponentActivity() {

    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Par défaut on annule (si user fait back, le widget n'est pas ajouté)
        setResult(RESULT_CANCELED)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            DringTheme {
                SmallConfigScreen(
                    onConfirm = { value ->
                        WidgetConfigStore.setSmallMessage(applicationContext, widgetId, value)
                        DringWidgetProvider.refreshAll(applicationContext)
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
private fun SmallConfigScreen(onConfirm: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GradientBg)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "Choisis ton Dring",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Le tap du widget enverra ce message.",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(20.dp))

            ConfigOptionRow(
                emoji = "🎲",
                label = "Aléatoire",
                subtitle = "Un message au hasard parmi tes Drings",
                onClick = { onConfirm(WidgetConfigStore.RANDOM_VALUE) },
            )
            Spacer(Modifier.height(10.dp))

            Config.QUICK_MESSAGES.forEach { msg ->
                ConfigOptionRow(
                    emoji = msg.emoji,
                    label = msg.text,
                    subtitle = null,
                    onClick = { onConfirm(msg.body()) },
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun ConfigOptionRow(
    emoji: String,
    label: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(emoji, fontSize = 28.sp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    label,
                    color = Color(0xFFAD1457),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        color = Color.Gray,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}
