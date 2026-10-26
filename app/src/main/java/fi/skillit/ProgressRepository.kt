package fi.skillit

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fi.skillit.engine.Action
import fi.skillit.engine.ExerciseSession
import fi.skillit.engine.SessionEvent
import fi.skillit.music.Adventure
import fi.skillit.music.IslandState
import fi.skillit.music.MusicPack
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

val Context.skillitDataStore by preferencesDataStore(name = "skillit-progress", corruptionHandler = ProgressRepository.corruptionHandler)
data class SavedProgress(
    val started: Boolean = false, val muted: Boolean = false,
    val events: List<SessionEvent> = emptyList(), val volume: Float = .6f,
    val practice: Map<Int, List<SessionEvent>> = emptyMap(),
)

class ProgressRepository(private val dataStore: DataStore<Preferences>) {
    suspend fun load(): SavedProgress {
        val raw = dataStore.data.first()[key] ?: return SavedProgress()
        val json = JSONObject(raw)
        val schema = json.getInt("schema")
        require(schema in 1..2 && json.getString("pack") == MusicPack.id && json.getInt("version") == MusicPack.version)
        val events = readEvents(json.getJSONArray("events"))
        val volume = if (schema == 1 && !json.has("volume")) .6f else json.getDouble("volume").toFloat()
        val practice = if (schema == 1) emptyMap() else buildMap {
            val runs = json.getJSONArray("practice")
            require(runs.length() <= MusicPack.levels.size)
            repeat(runs.length()) { i ->
                val run = runs.getJSONObject(i)
                val level = run.getInt("level")
                require(level !in this)
                put(level, readEvents(run.getJSONArray("events")))
            }
        }
        val progress = SavedProgress(json.getBoolean("started"), json.getBoolean("muted"), events, volume, practice)
        validate(progress)
        // Upgrade only a fully validated v1 history, atomically and without changing event identities.
        if (schema == 1) dataStore.edit {
            check(it[key] == raw) { "Progress changed during migration" }
            it[key] = encode(progress)
        }
        return progress
    }

    suspend fun save(progress: SavedProgress) {
        validate(progress)
        val raw = encode(progress)
        dataStore.edit { it[key] = raw }
    }

    suspend fun reset(muted: Boolean) = save(SavedProgress(muted = muted))

    private fun validate(progress: SavedProgress) {
        require(progress.volume.isFinite() && progress.volume in 0f..1f)
        val adventure = ExerciseSession.restore(MusicPack.questions, progress.events)
        require(progress.started || progress.events.isEmpty())
        val islands = Adventure.islands(adventure)
        progress.practice.forEach { (level, events) ->
            require(level in islands.indices && islands[level] == IslandState.COMPLETED)
            ExerciseSession.restore(Adventure.questions(level), events)
        }
    }
    private fun readEvents(array: JSONArray): List<SessionEvent> = (0 until array.length()).map { i ->
        val event = array.getJSONObject(i)
        SessionEvent(Action.valueOf(event.getString("action")), event.getString("token"), event.getString("choice"))
    }
    private fun writeEvents(events: List<SessionEvent>) = JSONArray().apply {
        events.forEach { event -> put(JSONObject().apply {
            put("action", event.action.name); put("token", event.token); put("choice", event.choiceId)
        }) }
    }
    private fun encode(progress: SavedProgress): String = JSONObject().apply {
        put("schema", 2); put("pack", MusicPack.id); put("version", MusicPack.version)
        put("started", progress.started); put("muted", progress.muted); put("volume", progress.volume.toDouble())
        put("events", writeEvents(progress.events))
        put("practice", JSONArray().apply { progress.practice.toSortedMap().forEach { (level, events) ->
            put(JSONObject().put("level", level).put("events", writeEvents(events)))
        } })
    }.toString()

    companion object {
        // Retain the installed M1 key and file; schema is inside the value.
        val key = stringPreferencesKey("session-v1")
        val corruptionHandler = ReplaceFileCorruptionHandler<Preferences> { preferencesOf(key to "corrupt") }
    }
}
