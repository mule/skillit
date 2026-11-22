package fi.skillit.ink

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke

/**
 * A row of one-digit boxes the child writes into.
 *
 * Live strokes go through androidx.ink's [InProgressStrokesView] (front-buffered, low latency) when
 * [lowLatency] is on. Emulators turn it off: their software renderer crashes hwui's RenderThread when a
 * front-buffered layer is torn down mid-frame (seen on the API 35 CI emulator), so there the live stroke is
 * drawn on the normal Canvas instead. Finished strokes are drawn here with [CanvasStrokeRenderer]. In parallel the view records its own
 * [InkStroke] points from the same MotionEvents: those feed recognition and local storage and do not
 * depend on ink's asynchronous finish callback.
 */
@SuppressLint("ViewConstructor")
class DigitInkView(context: Context, boxCount: Int, lowLatency: Boolean = !isEmulator()) : FrameLayout(context), InProgressStrokesFinishedListener {
    var boxCount: Int = boxCount
        set(value) { field = value; requestLayout(); invalidate() }

    /** Called on the main thread after every finished, erased or cleared stroke. */
    var onInkChanged: (List<InkStroke>) -> Unit = {}

    /** Increases on every change to the ink. A recognition result for an older revision is stale. */
    var revision = 0
        private set
    private fun changed() { revision++; onInkChanged(strokes) }

    val policy = PenPolicy()
    private val live: InProgressStrokesView? = if (lowLatency) InProgressStrokesView(context) else null
    private val renderer = CanvasStrokeRenderer.create()
    private val identity = Matrix()
    private val brush = Brush.createWithColorIntArgb(StockBrushes.pressurePen(), Color.rgb(0x16, 0x29, 0x27), 9f * resources.displayMetrics.density / 2f, 0.1f)

    private class Written(val ink: InkStroke, var rendered: Stroke? = null)
    private val written = mutableListOf<Written>()
    private val pending = HashMap<InProgressStrokeId, Written>()

    private var activePointer = -1
    private var activeIsPen = false
    private var activeId: InProgressStrokeId? = null
    /** A stroke is being written (not erasing). Independent of [activeId], which is null without [live]. */
    private var writing = false
    private var activePoints = mutableListOf<InkPoint>()
    private var erasing = false
    private var hoverX = Float.NaN
    private var hoverY = Float.NaN
    private var hoverEraser = false

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.rgb(0x9C, 0xB8, 0xB0); strokeWidth = resources.displayMetrics.density }
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.rgb(0x31, 0x5D, 0x82); strokeWidth = resources.displayMetrics.density * 1.5f }

    private val thin = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = brush.size; color = brush.colorIntArgb; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

    val strokes: List<InkStroke> get() = written.map { it.ink }
    val row: BoxRow get() = BoxRow(boxCount, width.coerceAtLeast(1) / boxCount.toFloat(), height.coerceAtLeast(1).toFloat())
    private val eraserRadius get() = 14f * resources.displayMetrics.density

    init {
        setWillNotDraw(false)
        live?.let {
            addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            it.addFinishedStrokesListener(this)
        }
        contentDescription = "Kirjoitusruudukko"
    }

    fun clear() {
        live?.cancelUnfinishedStrokes()
        activePointer = -1; activeId = null; writing = false; activePoints = mutableListOf(); erasing = false
        written.clear(); pending.clear()
        invalidate(); changed()
    }

    /**
     * Removes the finished strokes in [done] (e.g. a saved sample) and leaves everything else alone, including
     * a stroke that is being written right now (Codex re-check should-fix 2).
     */
    fun removeFinished(done: List<InkStroke>) {
        val gone = written.filter { it.ink in done }.toSet()
        if (gone.isEmpty()) return
        written.removeAll(gone); pending.values.removeAll(gone)
        invalidate(); changed()
    }

    /** Replaces the ink, e.g. when restoring a saved attempt. Restored strokes are drawn as thin lines. */
    fun restore(ink: List<InkStroke>) { clear(); ink.forEach { written += Written(it) }; invalidate(); changed() }

    override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
        strokes.forEach { (id, stroke) -> pending.remove(id)?.rendered = stroke }
        live?.removeFinishedStrokes(strokes.keys)
        invalidate()
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        val tool = event.getToolType(0)
        policy.onHover(tool)
        // A pen coming near while a finger is writing: that finger is a palm or resting hand. Drop its stroke now,
        // before it lifts, so it is never committed (Codex re-check should-fix 1).
        if (policy.isPen(tool) && activePointer != -1 && !activeIsPen) {
            activeId?.let { live?.cancelStroke(it, event) }
            activePointer = -1; activeId = null; writing = false; activePoints = mutableListOf(); erasing = false
        }
        if (event.actionMasked == MotionEvent.ACTION_HOVER_EXIT || !policy.isPen(tool)) { hoverX = Float.NaN }
        else { hoverX = event.x; hoverY = event.y; hoverEraser = policy.isEraser(tool, event.buttonState) }
        invalidate()
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val index = event.actionIndex
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val tool = event.getToolType(index)
                if (activePointer != -1) {
                    // A pen landing while a finger writes: the finger stroke was a palm or a resting hand. Drop it.
                    if (!policy.isPen(tool) || activeIsPen) return true
                    activeId?.let { live?.cancelStroke(it, event) }
                    activeId = null; writing = false; erasing = false
                }
                if (!policy.accepts(tool)) return true
                activeIsPen = policy.isPen(tool)
                // Writing must never turn into scrolling: keep scrollable parents from stealing the stroke.
                parent?.requestDisallowInterceptTouchEvent(true)
                activePointer = event.getPointerId(index)
                hoverX = Float.NaN
                if (policy.isEraser(tool, event.buttonState)) { erasing = true; eraseAt(event.getX(index), event.getY(index)) }
                else {
                    if (Build.VERSION.SDK_INT >= 30) requestUnbufferedDispatch(event)
                    activePoints = mutableListOf(point(event, index))
                    activeId = live?.startStroke(event, activePointer, brush)
                    writing = true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val i = event.findPointerIndex(activePointer)
                if (i < 0) return true
                if (erasing) {
                    for (h in 0 until event.historySize) eraseAt(event.getHistoricalX(i, h), event.getHistoricalY(i, h))
                    eraseAt(event.getX(i), event.getY(i))
                } else if (writing) {
                    for (h in 0 until event.historySize) activePoints += InkPoint(event.getHistoricalX(i, h), event.getHistoricalY(i, h), event.getHistoricalEventTime(h), event.getHistoricalPressure(i, h))
                    activePoints += point(event, i)
                    activeId?.let { live?.addToStroke(event, activePointer, it, null) } ?: invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val pointerEnded = action == MotionEvent.ACTION_CANCEL || event.getPointerId(index) == activePointer
                if (!pointerEnded || activePointer == -1) return true
                val id = activeId
                if (writing) {
                    if (policy.isCanceled(action, event.flags)) id?.let { live?.cancelStroke(it, event) }
                    else {
                        activePoints += point(event, index)
                        id?.let { live?.finishStroke(event, activePointer, it) }
                        val w = Written(InkStroke(activePoints.toList()))
                        written += w; if (id != null) pending[id] = w
                        changed()
                    }
                }
                activePointer = -1; activeId = null; writing = false; erasing = false; activePoints = mutableListOf()
                invalidate()
            }
        }
        return true
    }

    private fun point(e: MotionEvent, i: Int) = InkPoint(e.getX(i), e.getY(i), e.eventTime, e.getPressure(i))

    private fun eraseAt(x: Float, y: Float) {
        val before = written.size
        written.removeAll { StrokeEraser.hits(it.ink, x, y, eraserRadius) }
        if (written.size != before) { pending.values.retainAll(written.toSet()); invalidate(); changed() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val r = row
        for (i in 0 until r.count) {
            val l = r.boxLeft(i)
            canvas.drawRoundRect(l + 4f, 4f, l + r.boxWidth - 4f, r.boxHeight - 4f, 12f, 12f, gridPaint)
        }
        written.forEach { w ->
            val s = w.rendered
            if (s != null) renderer.draw(canvas, s, identity)
            else w.ink.points.zipWithNext().forEach { (a, b) -> canvas.drawLine(a.x, a.y, b.x, b.y, thin) }
        }
        // Without the front-buffered layer, the stroke in progress is drawn here.
        if (live == null && writing) activePoints.zipWithNext().forEach { (a, b) -> canvas.drawLine(a.x, a.y, b.x, b.y, thin) }
        if (!hoverX.isNaN()) canvas.drawCircle(hoverX, hoverY, if (hoverEraser) eraserRadius else brush.size, cursorPaint)
    }
}

/** True on Android emulators, whose software GL can't host androidx.ink's front-buffered layer reliably. */
fun isEmulator(): Boolean =
    Build.HARDWARE in setOf("ranchu", "goldfish") || Build.PRODUCT.startsWith("sdk") || Build.FINGERPRINT.startsWith("generic")
