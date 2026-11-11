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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import dhp.thl.tpl.ndv.ui.theme.ExpressiveThemeManager

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
                val palette = remember(context) {
                    ExpressiveThemeManager.resolvePalette(context)
                }
                val colorScheme = remember(palette) {
                    palette.toComposeColorScheme()
                }

                MaterialTheme(colorScheme = colorScheme) {
                    ProgressDialogContent(
                        progressProvider = { progressState.floatValue },
                        messageProvider = { messageState.value },
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

    fun showSafe(manager: androidx.fragment.app.FragmentManager, tag: String) {
        try {
            if (!isAdded) {
                manager.beginTransaction().add(this, tag).commitAllowingStateLoss()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun dismissSafe() {
        try {
            if (isAdded || fragmentManager != null) {
                dismissAllowingStateLoss()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun ProgressDialogContent(
        progressProvider: () -> Float,
        messageProvider: () -> String,
        onCancel: (() -> Unit)?
    ) {
        val progress by rememberUpdatedState(progressProvider())
        val animatedProgress by animateFloatAsState(
            targetValue = progress,
            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
            label = "ProgressAnimation"
        )
        val primaryColor = MaterialTheme.colorScheme.primary

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
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 18.dp)
                )

                LinearWavyProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp),
                    color = primaryColor,
                    trackColor = MaterialTheme.colorScheme.secondaryContainer,
                    stroke = WavyProgressIndicatorDefaults.linearIndicatorStroke,
                    trackStroke = WavyProgressIndicatorDefaults.linearTrackStroke
                )

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                if (onCancel != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    TextButton(
                        onClick = { onCancel.invoke() },
                        colors = ButtonDefaults.textButtonColors(contentColor = primaryColor)
                    ) {
                        Text(
                            text = stringResource(id = R.string.cancel),
                            fontWeight = FontWeight.Bold
                        )
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
