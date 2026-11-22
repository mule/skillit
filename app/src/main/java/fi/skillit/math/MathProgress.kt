package fi.skillit.math

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fi.skillit.engine.Action
import fi.skillit.engine.SessionEvent
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*

/** How the child writes answers. The pad is unlocked only from the parent area. */
enum class MathInputMode { PEN, PAD }

data class LessonRecord(val completions: Int, val bestXp: Int, val rewardWord: String? = null)

data class ActiveLesson(val plan: MathCourse.Plan, val events: List<SessionEvent> = emptyList())

/**
 * Math progress. Lives in its own DataStore file, apart from the music save (`skillit-progress`), so
 * math XP is separate and nothing here can touch music or nature state.
 */
data class MathProgress(
    val packId: String? = null,
    val packVersion: Int = 0,
    /** Total math XP of completed lessons; music XP is never added here. */
    val xp: Int = 0,
    val lessons: Map<String, LessonRecord> = emptyMap(),
    val active: ActiveLesson? = null,
    val input: MathInputMode = MathInputMode.PEN,
    val pin: ParentPin? = null,
    /** AdaptiveSelector's Finnish explanation of the latest lesson, shown in the parent area. */
    val lastExplanation: String? = null,
) {
    init {
        require(xp >= 0 && lessons.values.all { it.completions >= 0 && it.bestXp >= 0 })
    }
}

interface MathStore {
    suspend fun load(): MathProgress
    suspend fun save(progress: MathProgress)
}

val Context.mathDataStore by preferencesDataStore(name = "skillit-math", corruptionHandler = MathRepository.corruptionHandler)

class MathRepository(private val dataStore: DataStore<Preferences>) : MathStore {
    override suspend fun load(): MathProgress = dataStore.data.first()[key]?.let(MathProgressCodec::decode) ?: MathProgress()
    override suspend fun save(progress: MathProgress) {
        val raw = MathProgressCodec.encode(progress)
        dataStore.edit { it[key] = raw }
    }

    companion object {
        val key = stringPreferencesKey("math-v1")
        val corruptionHandler = ReplaceFileCorruptionHandler<Preferences> { preferencesOf(key to "corrupt") }
    }
}

/** Plain JSON, schema 1. Unknown schema or malformed data throws, and the UI offers a fresh start. */
object MathProgressCodec {
    private const val SCHEMA = 1

    fun encode(p: MathProgress): String = buildJsonObject {
        put("schema", SCHEMA)
        p.packId?.let { put("pack", it) }
        put("packVersion", p.packVersion)
        put("xp", p.xp)
        put("input", p.input.name)
        p.lastExplanation?.let { put("explanation", it) }
        putJsonArray("lessons") {
            p.lessons.toSortedMap().forEach { (id, r) -> addJsonObject {
                put("id", id); put("completions", r.completions); put("bestXp", r.bestXp)
                r.rewardWord?.let { put("reward", it) }
            } }
        }
        p.active?.let { a -> putJsonObject("active") {
            put("stop", a.plan.stopId)
            put("explanation", a.plan.explanation)
            putJsonArray("items") { a.plan.itemIds.forEach { add(it) } }
            putJsonArray("events") { a.events.forEach { e -> addJsonObject {
                put("action", e.action.name); put("token", e.token); put("choice", e.choiceId)
            } } }
        } }
        p.pin?.let { pin -> putJsonObject("pin") {
            put("salt", pin.salt); put("hash", pin.hash); put("failures", pin.failures); put("lockedUntil", pin.lockedUntil)
        } }
    }.toString()

    fun decode(raw: String): MathProgress {
        val json = try { Json.parseToJsonElement(raw).jsonObject } catch (e: Exception) { throw IllegalArgumentException("Corrupt math save", e) }
        require(json.int("schema") == SCHEMA) { "Unknown math save schema" }
        val lessons = json.getValue("lessons").jsonArray.associate { element ->
            val o = element.jsonObject
            o.string("id") to LessonRecord(o.int("completions"), o.int("bestXp"), o["reward"]?.jsonPrimitive?.content)
        }
        val active = json["active"]?.jsonObject?.let { a ->
            ActiveLesson(
                MathCourse.Plan(a.string("stop"), a.getValue("items").jsonArray.map { it.jsonPrimitive.content }, a.string("explanation")),
                a.getValue("events").jsonArray.map { element ->
                    val e = element.jsonObject
                    SessionEvent(Action.valueOf(e.string("action")), e.string("token"), e.string("choice"))
                },
            )
        }
        val pin = json["pin"]?.jsonObject?.let { ParentPin(it.string("salt"), it.string("hash"), it.int("failures"), it.getValue("lockedUntil").jsonPrimitive.long) }
        return MathProgress(
            json["pack"]?.jsonPrimitive?.content, json.int("packVersion"), json.int("xp"), lessons, active,
            MathInputMode.valueOf(json.string("input")), pin, json["explanation"]?.jsonPrimitive?.content,
        )
    }

    private fun JsonObject.int(key: String) = getValue(key).jsonPrimitive.int
    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
}
