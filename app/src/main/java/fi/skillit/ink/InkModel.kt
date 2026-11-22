package fi.skillit.ink

/** One sampled pen position in view pixels. [t] is milliseconds since the stroke's event time base. */
data class InkPoint(val x: Float, val y: Float, val t: Long, val pressure: Float = 1f)

/** One pen-down → pen-up stroke. Kept on the device only; never uploaded. */
data class InkStroke(val points: List<InkPoint>) {
    val minX get() = points.minOf { it.x }
    val maxX get() = points.maxOf { it.x }
    val minY get() = points.minOf { it.y }
    val maxY get() = points.maxOf { it.y }
}

/** How the child answers. Pen is the default; the number pad is the parent-unlocked fallback (PIN UI: #27). */
enum class InputMode { Pen, NumberPad }

/**
 * A row of equally sized one-digit boxes, like squared workbook paper.
 * Coordinates are in the same pixel space as the strokes.
 */
data class BoxRow(val count: Int, val boxWidth: Float, val boxHeight: Float, val gap: Float = 0f, val left: Float = 0f, val top: Float = 0f) {
    init { require(count > 0 && boxWidth > 0f && boxHeight > 0f && gap >= 0f) }
    fun boxLeft(index: Int) = left + index * (boxWidth + gap)
    fun width() = count * boxWidth + (count - 1) * gap

    /** The box whose column holds [x], or the nearest box when [x] is in a gap or outside the row. */
    fun nearestBox(x: Float): Int {
        val pitch = boxWidth + gap
        val raw = ((x - left + gap / 2f) / pitch).toInt()
        return if (x < left) 0 else raw.coerceIn(0, count - 1)
    }
}

object BoxSegmentation {
    /**
     * Assigns every stroke to one box. A stroke belongs to the box that holds most of its
     * points' x positions, so a "4" whose crossbar pokes into the next box stays in its own box.
     * Ties go to the box of the stroke's horizontal centre.
     */
    fun boxOf(stroke: InkStroke, row: BoxRow): Int {
        require(stroke.points.isNotEmpty())
        val votes = IntArray(row.count)
        stroke.points.forEach { votes[row.nearestBox(it.x)]++ }
        val best = votes.max()
        val winners = votes.indices.filter { votes[it] == best }
        return if (winners.size == 1) winners.single() else row.nearestBox((stroke.minX + stroke.maxX) / 2f)
    }

    /** Box index → strokes in writing order. Boxes with no ink are absent. */
    fun segment(strokes: List<InkStroke>, row: BoxRow): Map<Int, List<InkStroke>> =
        strokes.filter { it.points.isNotEmpty() }.groupBy { boxOf(it, row) }.toSortedMap()
}
