package fi.skillit.engine.adaptive

import fi.skillit.engine.AnswerInput
import fi.skillit.engine.AnswerSpec
import fi.skillit.engine.JsonDepth
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.UUID

@Serializable
enum class InputMethod { @SerialName("pen") PEN, @SerialName("pad") PAD }

@Serializable
enum class ErrorClass {
    @SerialName("wrong-table-fact") WRONG_TABLE_FACT,
    @SerialName("dropped-carry") DROPPED_CARRY,
    @SerialName("place-value-shift") PLACE_VALUE_SHIFT,
    @SerialName("misread-digit") MISREAD_DIGIT,
    @SerialName("other") OTHER,
}

/** Upload unchanged in Release B. No pen strokes, child identity or device identifiers. */
@Serializable
data class AttemptEvent(
    val eventId: String,
    val packId: String,
    val packVersion: Int,
    val itemId: String,
    val family: String,
    val params: JsonObject,
    val answer: AnswerInput,
    val correct: Boolean,
    val retryNumber: Int,
    val hintUsed: Boolean,
    val inputMethod: InputMethod,
    val recognizerConfidence: Double?,
    val errorClass: ErrorClass?,
    /** UTC Unix epoch milliseconds. */
    val timestamp: Long,
    val schema: Int = 1,
) {
    fun validate() {
        require(schema == 1)
        require(UUID.fromString(eventId).toString() == eventId) { "eventId must be a canonical UUID" }
        require(listOf(packId, itemId, family).all { it.length in 1..80 && it.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9._-]*")) })
        require(packVersion > 0 && retryNumber >= 0 && timestamp >= 0)
        require(recognizerConfidence == null || recognizerConfidence.isFinite() && recognizerConfidence in 0.0..1.0)
        require(inputMethod == InputMethod.PEN || recognizerConfidence == null)
        require(correct == (errorClass == null))
        require(params.size <= 20 && params.toString().length <= 1000)
        JsonDepth.require(params.toString(), 32)
        when (answer) {
            is AnswerInput.Choice -> require(answer.id.length in 1..32)
            is AnswerInput.Cells -> require(answer.values.size in 1..400 && answer.values.keys.all { it.length in 1..32 })
        }
    }

    companion object {
        /** Derive correctness and diagnosis from the same spec used by ExerciseSession. */
        fun record(
            packId: String, packVersion: Int, itemId: String, family: String, params: JsonObject,
            spec: AnswerSpec, answer: AnswerInput, retryNumber: Int, hintUsed: Boolean,
            inputMethod: InputMethod, recognizerConfidence: Double?, timestamp: Long,
            eventId: String = UUID.randomUUID().toString(),
        ): AttemptEvent {
            spec.validate()
            val check = requireNotNull(spec.check(answer)) { "Answer does not belong to this item" }
            return AttemptEvent(eventId, packId, packVersion, itemId, family, params, answer, check.correct,
                retryNumber, hintUsed, inputMethod, recognizerConfidence,
                ErrorClassifier.classify(spec, answer, inputMethod, recognizerConfidence), timestamp).also { it.validate() }
        }
    }
}

/** One canonical line per event, including defaults and explicit nulls. */
object AttemptCodec {
    private val json = Json { encodeDefaults = true }
    fun encode(event: AttemptEvent): String { event.validate(); return json.encodeToString(AttemptEvent.serializer(), event) }
    fun decode(line: String): AttemptEvent {
        require(line.length <= 65536)
        JsonDepth.require(line, 40)
        return json.decodeFromString(AttemptEvent.serializer(), line).also { it.validate() }
    }
}
