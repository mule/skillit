package fi.skillit.engine

data class Choice(val id: String, val label: String)
data class Question(
    val id: String,
    val level: Int,
    val prompt: String,
    val choices: List<Choice>,
    val correctId: String,
    val hint: String,
    val explanation: String,
)
data class Attempt(val question: Question, val retry: Boolean = false) {
    val token: String get() = "${question.id}:${if (retry) 1 else 0}"
}
data class Feedback(val correct: Boolean, val xp: Int, val choiceId: String)
data class Result(val questionId: String, val retry: Boolean, val correct: Boolean, val xp: Int)

enum class Action { HINT, ANSWER, NEXT }
data class SessionEvent(val action: Action, val token: String, val choiceId: String = "")

/** Pure Kotlin session. Callers pass the visible attempt token, making stale taps harmless. */
class ExerciseSession(val questions: List<Question>) {
    init {
        require(questions.isNotEmpty() && questions.size <= 100)
        require(questions.map { it.id }.distinct().size == questions.size)
        require(questions.zipWithNext().all { (a, b) -> a.level <= b.level })
        questions.forEach { q ->
            require(q.id.isNotBlank() && ':' !in q.id)
            require(q.choices.size >= 2 && q.choices.map { it.id }.distinct().size == q.choices.size)
            require(q.choices.count { it.id == q.correctId } == 1)
        }
    }
    private val history = mutableListOf<SessionEvent>()
    val events: List<SessionEvent> get() = history.toList()
    var hintVisible: Boolean = false
        private set
    val answeredOriginals: Int get() = results.count { !it.retry }
    val retryCount: Int get() = results.count { it.retry }
    val outcomes: List<Result> get() = results.toList()
    private val pending = questions.map { Attempt(it) }.toMutableList()
    private val results = mutableListOf<Result>()
    val current: Attempt? get() = pending.firstOrNull()
    var feedback: Feedback? = null
        private set
    val complete: Boolean get() = pending.isEmpty()
    val xp: Int get() = results.sumOf { it.xp }
    val firstTryCorrect: Int get() = results.count { !it.retry && it.correct }

    fun answer(token: String, choiceId: String): Boolean {
        val attempt = current ?: return false
        if (token != attempt.token || feedback != null || attempt.question.choices.none { it.id == choiceId }) return false
        val correct = choiceId == attempt.question.correctId
        val award = if (correct) { if (attempt.retry) 5 else 10 } else 0
        history += SessionEvent(Action.ANSWER, token, choiceId)
        feedback = Feedback(correct, award, choiceId)
        results += Result(attempt.question.id, attempt.retry, correct, award)
        return true
    }

    fun next(token: String): Boolean {
        if (current?.token != token || feedback == null) return false
        history += SessionEvent(Action.NEXT, token)
        val attempt = pending.removeAt(0)
        if (feedback?.correct == false && !attempt.retry) {
            val afterLevel = pending.indexOfLast { it.question.level == attempt.question.level } + 1
            pending.add(afterLevel, Attempt(attempt.question, retry = true))
        }
        feedback = null
        hintVisible = false
        return true
    }

    fun showHint(token: String): Boolean {
        if (current?.token != token || feedback != null || hintVisible) return false
        hintVisible = true
        history += SessionEvent(Action.HINT, token)
        return true
    }

    companion object {
        fun restore(questions: List<Question>, events: List<SessionEvent>): ExerciseSession {
            require(events.size <= questions.size * 6) { "Too many session events" }
            return ExerciseSession(questions).also { session ->
                events.forEach { event ->
                    val accepted = when (event.action) {
                        Action.HINT -> event.choiceId.isEmpty() && session.showHint(event.token)
                        Action.ANSWER -> session.answer(event.token, event.choiceId)
                        Action.NEXT -> event.choiceId.isEmpty() && session.next(event.token)
                    }
                    require(accepted) { "Invalid session history" }
                }
            }
        }
    }
}
