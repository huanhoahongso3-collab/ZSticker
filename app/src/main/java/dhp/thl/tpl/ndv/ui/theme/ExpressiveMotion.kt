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

    private const val TAG_SCALE_X = 0x7A5C0001
    private const val TAG_SCALE_Y = 0x7A5C0002
    private const val TAG_TRANS_Y = 0x7A5C0003

    /**
     * One reusable SpringAnimation per view+property. Starting a new spring while an older one is
     * still running on the same property used to leave both fighting, so a view could settle at the
     * pressed scale / offset and look randomly misaligned. Reusing a single spring retargets it instead.
     */
    private fun springFor(
        view: View,
        tagKey: Int,
        property: DynamicAnimation.ViewProperty,
        target: Float
    ): SpringAnimation {
        val existing = view.getTag(tagKey) as? SpringAnimation
        if (existing != null) return existing
        return SpringAnimation(view, property, target).also {
            it.spring = SpringForce(target)
            view.setTag(tagKey, it)
        }
    }

    private fun retarget(
        anim: SpringAnimation,
        target: Float,
        dampingRatio: Float,
        stiffness: Float
    ) {
        val force = anim.spring ?: SpringForce(target).also { anim.spring = it }
        force.dampingRatio = dampingRatio
        force.stiffness = stiffness
        anim.animateToFinalPosition(target)
    }

    fun springScale(
        view: View,
        targetScale: Float,
        dampingRatio: Float = SPATIAL_DAMPING,
        stiffness: Float = SPATIAL_STIFFNESS
    ) {
        retarget(springFor(view, TAG_SCALE_X, DynamicAnimation.SCALE_X, targetScale), targetScale, dampingRatio, stiffness)
        retarget(springFor(view, TAG_SCALE_Y, DynamicAnimation.SCALE_Y, targetScale), targetScale, dampingRatio, stiffness)
    }

    /** Immediately stop any running springs and snap the view to its resting transform. */
    fun resetToRest(view: View) {
        (view.getTag(TAG_SCALE_X) as? SpringAnimation)?.cancel()
        (view.getTag(TAG_SCALE_Y) as? SpringAnimation)?.cancel()
        (view.getTag(TAG_TRANS_Y) as? SpringAnimation)?.cancel()
        view.scaleX = 1f
        view.scaleY = 1f
        view.translationY = 0f
    }

    fun springPopIn(view: View, initialScale: Float = 0.76f) {
        (view.getTag(TAG_SCALE_X) as? SpringAnimation)?.cancel()
        (view.getTag(TAG_SCALE_Y) as? SpringAnimation)?.cancel()
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
        retarget(springFor(view, TAG_TRANS_Y, DynamicAnimation.TRANSLATION_Y, targetY), targetY, dampingRatio, stiffness)
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
