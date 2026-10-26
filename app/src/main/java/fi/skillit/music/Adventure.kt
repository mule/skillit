package fi.skillit.music

import fi.skillit.engine.ExerciseSession

enum class IslandState { LOCKED, CURRENT, COMPLETED }
object Adventure {
    fun questions(level: Int) = MusicPack.questions.filter { it.level == level }.also { require(it.isNotEmpty()) }
    fun islands(session: ExerciseSession): List<IslandState> {
        val resolved = session.outcomes.filter { it.correct }.map { it.questionId }.toSet()
        val complete = MusicPack.levels.indices.map { level -> questions(level).all { it.id in resolved } }
        return complete.indices.map { level -> when {
            complete[level] -> IslandState.COMPLETED
            level == 0 || complete.take(level).all { it } -> IslandState.CURRENT
            else -> IslandState.LOCKED
        } }
    }
}
