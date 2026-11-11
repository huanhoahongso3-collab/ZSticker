package dhp.thl.tpl.ndv.ui.theme

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.google.android.material.card.MaterialCardView

/**
 * Material Design 3 Expressive physics-based motion utilities:
 * - Spatial springs (bouncy, tactile response for touch, pop-ins, and shape morphing)
 * - Effects springs (smooth, non-bouncy settling for opacity and color transitions)
 */
object ExpressiveMotion {

    const val SPATIAL_DAMPING = 0.56f
    const val SPATIAL_STIFFNESS = 430f

    const val FAST_SPATIAL_DAMPING = 0.62f
    const val FAST_SPATIAL_STIFFNESS = 680f

    val emphasizedDecelerate = PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f)

    fun springScale(
        view: View,
        targetScale: Float,
        dampingRatio: Float = SPATIAL_DAMPING,
        stiffness: Float = SPATIAL_STIFFNESS
    ) {
        SpringAnimation(view, DynamicAnimation.SCALE_X, targetScale).apply {
            spring.dampingRatio = dampingRatio
            spring.stiffness = stiffness
            start()
        }
        SpringAnimation(view, DynamicAnimation.SCALE_Y, targetScale).apply {
            spring.dampingRatio = dampingRatio
            spring.stiffness = stiffness
            start()
        }
    }

    fun springPopIn(view: View, initialScale: Float = 0.76f) {
        view.scaleX = initialScale
        view.scaleY = initialScale
        springScale(view, 1f, dampingRatio = 0.52f, stiffness = 460f)
    }

    fun springTranslationY(
        view: View,
        targetY: Float,
        dampingRatio: Float = 0.72f,
        stiffness: Float = 380f
    ) {
        SpringAnimation(view, DynamicAnimation.TRANSLATION_Y, targetY).apply {
            spring = SpringForce(targetY).apply {
                this.dampingRatio = dampingRatio
                this.stiffness = stiffness
            }
            start()
        }
    }

    /**
     * Attaches M3 Expressive interactive press physics + optional MaterialCardView corner-radius
     * shape morphing (e.g., 22dp resting -> 32dp pressed squircle/pill morph) without interfering
     * with normal click or long-press listeners.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun attachCardTouchMorph(
        targetView: View,
        cardView: MaterialCardView? = targetView as? MaterialCardView,
        restingCornerDp: Float = 22f,
        pressedCornerDp: Float = 32f,
        pressedScale: Float = 0.93f
    ) {
        val density = targetView.resources.displayMetrics.density
        val restPx = restingCornerDp * density
        val pressPx = pressedCornerDp * density
        var cornerAnimator: ValueAnimator? = null

        fun morphCorners(toPx: Float, durationMs: Long) {
            val card = cardView ?: return
            cornerAnimator?.cancel()
            cornerAnimator = ValueAnimator.ofFloat(card.radius, toPx).apply {
                duration = durationMs
                interpolator = emphasizedDecelerate
                addUpdateListener { anim ->
                    card.radius = anim.animatedValue as Float
                }
                start()
            }
        }

        targetView.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    springScale(v, pressedScale, FAST_SPATIAL_DAMPING, FAST_SPATIAL_STIFFNESS)
                    morphCorners(pressPx, 150L)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    springScale(v, 1f, SPATIAL_DAMPING, SPATIAL_STIFFNESS)
                    morphCorners(restPx, 260L)
                }
            }
            false
        }
    }

    /**
     * Attaches bouncy spatial spring press feedback to buttons, FABs, or segmented rows.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun attachPressSpring(view: View, pressedScale: Float = 0.95f) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    springScale(v, pressedScale, FAST_SPATIAL_DAMPING, FAST_SPATIAL_STIFFNESS)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    springScale(v, 1f, SPATIAL_DAMPING, SPATIAL_STIFFNESS)
                }
            }
            false
        }
    }
}
