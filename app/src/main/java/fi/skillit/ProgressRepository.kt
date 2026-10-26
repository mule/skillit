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
import fi.skillit.music.MusicPack
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

val Context.skillitDataStore by preferencesDataStore(name = "skillit-progress", corruptionHandler = ProgressRepository.corruptionHandler)
data class SavedProgress(val started: Boolean = false, val muted: Boolean = false, val events: List<SessionEvent> = emptyList())

class ProgressRepository(private val dataStore: DataStore<Preferences>) {
    suspend fun load(): SavedProgress {
        val raw = dataStore.data.first()[key] ?: return SavedProgress()
        val json = JSONObject(raw)
        require(json.getInt("schema") == 1 && json.getString("pack") == MusicPack.id && json.getInt("version") == MusicPack.version)
        val array = json.getJSONArray("events")
        val events = (0 until array.length()).map { i ->
            val event = array.getJSONObject(i)
            SessionEvent(Action.valueOf(event.getString("action")), event.getString("token"), event.getString("choice"))
        }
        ExerciseSession.restore(MusicPack.questions, events)
        val started = json.getBoolean("started")
        require(started || events.isEmpty())
        return SavedProgress(started, json.getBoolean("muted"), events)
    }

    suspend fun save(progress: SavedProgress) {
        ExerciseSession.restore(MusicPack.questions, progress.events)
        require(progress.started || progress.events.isEmpty())
        val events = JSONArray().apply {
            progress.events.forEach { event -> put(JSONObject().apply {
                put("action", event.action.name); put("token", event.token); put("choice", event.choiceId)
            }) }
        }
        val raw = JSONObject().apply {
            put("schema", 1); put("pack", MusicPack.id); put("version", MusicPack.version)
            put("started", progress.started); put("muted", progress.muted); put("events", events)
        }.toString()
        dataStore.edit { it[key] = raw }
    }

    suspend fun reset(muted: Boolean) = save(SavedProgress(muted = muted))

    companion object {
        val key = stringPreferencesKey("session-v1")
        // Keep a visible recovery state; never silently turn unreadable progress into a new game.
        val corruptionHandler = ReplaceFileCorruptionHandler<Preferences> { preferencesOf(key to "corrupt") }
    }
}
