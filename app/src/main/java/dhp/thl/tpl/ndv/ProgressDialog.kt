package dhp.thl.tpl.ndv

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.animateFloatAsState
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
                
                val primaryColor = if (materialColorEnabled) {
                    Color(MonetCompat.getInstance().getAccentColor(context))
                } else {
                    Color(0xFFFF9500) // orange_primary fallback
                }

                ProgressDialogContent(
                    progressProvider = { progressState.floatValue },
                    messageProvider = { messageState.value },
                    primaryColor = primaryColor
                )
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
        messageProvider: () -> Float, // Wait, message is string
        primaryColor: Color
    ) {
        // Redefine to avoid signature conflict in my thought process
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun ProgressDialogContent(
        progressProvider: () -> Float,
        messageProvider: () -> String,
        primaryColor: Color
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
                        .height(10.dp),
                    color = primaryColor,
                    trackColor = primaryColor.copy(alpha = 0.2f),
                    stroke = ProgressIndicatorDefaults.WavyProgressIndicatorStroke,
                    trackStroke = ProgressIndicatorDefaults.WavyProgressIndicatorNoFocusStroke
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
