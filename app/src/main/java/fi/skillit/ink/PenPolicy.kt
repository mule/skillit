package fi.skillit.ink

import android.view.MotionEvent
import kotlin.math.hypot

/**
 * Decides which pointers may write. Pure logic over MotionEvent constants so it runs in JVM tests.
 *
 * - Before any pen is seen, fingers write (finger fallback for a tablet without a pen).
 * - Once a pen touches or hovers, the surface is stylus-only: fingers and palms are ignored
 *   until [forgetPen] (called when the screen is re-entered).
 * - The S Pen side button, or a pen's eraser end, erases.
 */
class PenPolicy {
    var stylusSeen = false
        private set

    fun isPen(toolType: Int) = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER

    /** Hover (pen near the glass) also switches to stylus-only, so a resting palm never writes first. */
    fun onHover(toolType: Int) { if (isPen(toolType)) stylusSeen = true }

    fun accepts(toolType: Int): Boolean {
        if (isPen(toolType)) { stylusSeen = true; return true }
        return !stylusSeen && (toolType == MotionEvent.TOOL_TYPE_FINGER || toolType == MotionEvent.TOOL_TYPE_UNKNOWN)
    }

    fun isEraser(toolType: Int, buttonState: Int) =
        toolType == MotionEvent.TOOL_TYPE_ERASER ||
            (toolType == MotionEvent.TOOL_TYPE_STYLUS && buttonState and SIDE_BUTTONS != 0)

    /** The system cancels a pointer it decides was a palm (ACTION_CANCEL, or POINTER_UP with FLAG_CANCELED on API 33+). */
    fun isCanceled(actionMasked: Int, flags: Int) =
        actionMasked == MotionEvent.ACTION_CANCEL ||
            ((actionMasked == MotionEvent.ACTION_POINTER_UP || actionMasked == MotionEvent.ACTION_UP) && flags and FLAG_CANCELED != 0)

    fun forgetPen() { stylusSeen = false }

    companion object {
        /** MotionEvent.FLAG_CANCELED (API 33). Older systems never set it, so the literal is safe on minSdk 26. */
        const val FLAG_CANCELED = 0x20
        /** Samsung reports the S Pen side button as STYLUS_PRIMARY; some firmware also sets SECONDARY. */
        const val SIDE_BUTTONS = MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_SECONDARY
    }
}

object StrokeEraser {
    /** True when the eraser at ([x], [y]) with [radius] px touches any point or segment of [stroke]. */
    fun hits(stroke: InkStroke, x: Float, y: Float, radius: Float): Boolean {
        val p = stroke.points
        if (p.size == 1) return hypot(p[0].x - x, p[0].y - y) <= radius
        return p.zipWithNext().any { (a, b) -> segmentDistance(x, y, a.x, a.y, b.x, b.y) <= radius }
    }

    private fun segmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }
}
