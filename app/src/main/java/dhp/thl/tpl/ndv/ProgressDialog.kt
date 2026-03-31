package dhp.thl.tpl.ndv

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import com.kieronquinn.monetcompat.core.MonetCompat

class ProgressDialog : DialogFragment() {

    private val progressState = mutableFloatStateOf(0f)
    private val messageState = mutableStateOf("")
    var onCancel: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_ZSticker_Dialog)
        isCancelable = false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val materialColorEnabled = remember {
                    context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
                        .getBoolean("material_color_enabled", false)
                }
                val isDark = isSystemInDarkTheme()
                val monet = MonetCompat.getInstance()
                
                val colors = if (materialColorEnabled) {
                    val primary = Color(monet.getAccentColor(context))
                    val background = Color(monet.getBackgroundColor(context, isDark))
                    if (isDark) {
                        darkColorScheme(primary = primary, surface = background, surfaceContainerHigh = background)
                    } else {
                        lightColorScheme(primary = primary, surface = background, surfaceContainerHigh = background)
                    }
                } else {
                    if (isDark) darkColorScheme() else lightColorScheme()
                }

                val primaryColor = if (materialColorEnabled) {
                    Color(monet.getAccentColor(context))
                } else {
                    Color(0xFFFF9500) // orange_primary fallback
                }

                MaterialTheme(colorScheme = colors) {
                    ProgressDialogContent(
                        progressProvider = { progressState.floatValue },
                        messageProvider = { messageState.value },
                        primaryColor = primaryColor,
                        onCancel = onCancel
                    )
                }
            }
        }
    }

    fun updateProgress(progress: Float) {
        progressState.floatValue = progress.coerceIn(0f, 1f)
    }

    fun updateMessage(message: String) {
        messageState.value = message
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun ProgressDialogContent(
        progressProvider: () -> Float,
        messageProvider: () -> String,
        primaryColor: Color,
        onCancel: (() -> Unit)?
    ) {
        val progress by rememberUpdatedState(progressProvider())
        val animatedProgress by animateFloatAsState(
            targetValue = progress,
            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
            label = "ProgressAnimation"
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = messageProvider(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LinearWavyProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp),
                    color = primaryColor,
                    trackColor = primaryColor.copy(alpha = 0.2f),
                    stroke = WavyProgressIndicatorDefaults.linearIndicatorStroke,
                    trackStroke = WavyProgressIndicatorDefaults.linearTrackStroke
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = primaryColor
                )

                if (onCancel != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = { onCancel.invoke() },
                        colors = ButtonDefaults.textButtonColors(contentColor = primaryColor)
                    ) {
                        Text(context.getString(android.R.string.cancel))
                    }
                }
            }
        }
    }
    
    companion object {
        fun newInstance(message: String): ProgressDialog {
            return ProgressDialog().apply {
                updateMessage(message)
            }
        }
    }
}
