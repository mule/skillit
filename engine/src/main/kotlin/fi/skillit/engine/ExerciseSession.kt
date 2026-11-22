package fi.skillit.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** [value] is the number a math choice stands for, so packs can check `correctId`; music and nature leave it null. */
@Serializable
data class Choice(val id: String, val label: String, val value: Int? = null)

/**
 * One exercise. Music builds it from [choices] and [correctId] exactly as before; other subjects pass
 * [answer] (see [Question.of]) and leave the choice fields empty. [spec] is the single source of truth.
 */
data class Question(
    val id: String,
    val level: Int,
    val prompt: String,
    val choices: List<Choice>,
    val correctId: String,
    val hint: String,
    val explanation: String,
    val answer: AnswerSpec? = null,
) {
    val spec: AnswerSpec get() = answer ?: AnswerSpec.Choice(choices, correctId)

    companion object {
        fun of(id: String, level: Int, prompt: String, spec: AnswerSpec, hint: String, explanation: String): Question =
            if (spec is AnswerSpec.Choice) Question(id, level, prompt, spec.choices, spec.correctId, hint, explanation)
            else Question(id, level, prompt, emptyList(), "", hint, explanation, spec)
    }
}
data class Attempt(val question: Question, val number: Int = 0) {
    init { require(number >= 0) }
    val retry: Boolean get() = number > 0
    val token: String get() = "${question.id}:$number"
}
/** [choiceId] is empty for non-choice answers; [check] carries the per-cell result for those. */
data class Feedback(val correct: Boolean, val xp: Int, val choiceId: String, val check: CheckResult? = null)
data class Result(val questionId: String, val retry: Boolean, val correct: Boolean, val xp: Int, val check: CheckResult? = null)

enum class Action { HINT, ANSWER, NEXT }

/**
 * A replayable event. For a choice answer [choiceId] is the choice id (unchanged since M1, so existing saves
 * replay as before). For any other answer it holds the canonical JSON of the [AnswerInput.Cells].
 */
data class SessionEvent(val action: Action, val token: String, val choiceId: String = "")

/** Pure Kotlin session. Callers pass the visible attempt token, making stale taps harmless. */
class ExerciseSession(val questions: List<Question>) {
    init {
        require(questions.isNotEmpty() && questions.size <= 100)
        require(questions.map { it.id }.distinct().size == questions.size)
        require(questions.zipWithNext().all { (a, b) -> a.level <= b.level })
        questions.forEach { q ->
            require(q.id.isNotBlank() && ':' !in q.id)
            if (q.answer != null) require(q.answer !is AnswerSpec.Choice && q.choices.isEmpty() && q.correctId.isEmpty()) {
                "Pass a choice spec through Question.of"
            }
            q.spec.validate()
        }
    }
    private val history = mutableListOf<SessionEvent>()
    val events: List<SessionEvent> get() = history.toList()
    var hintVisible: Boolean = false
        private set
    val answeredOriginals: Int get() = results.count { !it.retry }
    val retryCount: Int get() = results.filter { it.retry }.map { it.questionId }.distinct().size
    val resolvedCount: Int get() = results.count { it.correct }
    val outcomes: List<Result> get() = results.toList()
    private val pending = questions.map { Attempt(it) }.toMutableList()
    private val results = mutableListOf<Result>()
    val current: Attempt? get() = pending.firstOrNull()
    var feedback: Feedback? = null
        private set
    val complete: Boolean get() = pending.isEmpty()
    /** True only when continuing a miss will show this same question again. */
    val retryIsNext: Boolean get() = feedback?.correct == false &&
        pending.drop(1).none { it.question.level == current?.question?.level }
    val xp: Int get() = results.sumOf { it.xp }
    val firstTryCorrect: Int get() = results.count { !it.retry && it.correct }

    /** Multiple-choice answer; false for any other answer type. */
    fun answer(token: String, choiceId: String): Boolean =
        current?.question?.spec is AnswerSpec.Choice && submit(token, AnswerInput.Choice(choiceId))

    /** Any answer type. Ignored (false) for a stale token, after feedback, or when [input] does not fit the spec. */
    fun submit(token: String, input: AnswerInput): Boolean {
        val attempt = current ?: return false
        if (token != attempt.token || feedback != null) return false
        val check = attempt.question.spec.check(input) ?: return false
        val correct = check.correct
        val award = if (correct) { if (attempt.retry) 5 else 10 } else 0
        val choiceId = (input as? AnswerInput.Choice)?.id ?: ""
        history += SessionEvent(Action.ANSWER, token, choiceId.ifEmpty { encode(input) })
        feedback = Feedback(correct, award, choiceId, check)
        results += Result(attempt.question.id, attempt.retry, correct, award, check)
        return true
    }

    fun next(token: String): Boolean {
        if (current?.token != token || feedback == null) return false
        history += SessionEvent(Action.NEXT, token)
        val attempt = pending.removeAt(0)
        if (feedback?.correct == false) {
            val afterLevel = pending.indexOfLast { it.question.level == attempt.question.level } + 1
            pending.add(afterLevel, Attempt(attempt.question, number = attempt.number + 1))
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

    private fun replayAnswer(event: SessionEvent): Boolean {
        if (current?.question?.spec is AnswerSpec.Choice) return answer(event.token, event.choiceId)
        val input = try { JsonDepth.require(event.choiceId); inputJson.decodeFromString<AnswerInput>(event.choiceId) } catch (_: Exception) { return false }
        // Only the canonical encoding is accepted, so a replayed history is byte-for-byte what was saved.
        return input is AnswerInput.Cells && encode(input) == event.choiceId && submit(event.token, input)
    }

    companion object {
        private val inputJson = Json { classDiscriminator = "type" }
        private fun encode(input: AnswerInput) = inputJson.encodeToString(AnswerInput.serializer(), input)

        fun restore(questions: List<Question>, events: List<SessionEvent>): ExerciseSession {
            return ExerciseSession(questions).also { session ->
                events.forEach { event ->
                    val accepted = when (event.action) {
                        Action.HINT -> event.choiceId.isEmpty() && session.showHint(event.token)
                        Action.ANSWER -> session.replayAnswer(event)
                        Action.NEXT -> event.choiceId.isEmpty() && session.next(event.token)
                    }
                    require(accepted) { "Invalid session history" }
                }
            }
        }
    }
}
