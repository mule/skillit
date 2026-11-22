package fi.skillit.ink

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Own DataStore file, so music/nature saves are never touched. */
val Context.inkSettingsDataStore by preferencesDataStore(name = "skillit-ink-settings")

/**
 * The pen / number-pad choice. Pen is the default. Only the parent area (#27, behind the 4-digit PIN)
 * should call [setMode]; the game screens only read [mode].
 */
class InputModeSettings(private val store: DataStore<Preferences>) {
    private val key = stringPreferencesKey("input_mode")
    val mode: Flow<InputMode> = store.data.map { p -> p[key]?.let { runCatching { InputMode.valueOf(it) }.getOrNull() } ?: InputMode.Pen }
    suspend fun setMode(mode: InputMode) { store.edit { it[key] = mode.name } }
}

/** The switch for the parent area. */
@Composable
fun InputModeSwitch(mode: InputMode, onChange: (InputMode) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.testTag("input-mode-switch"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Vastaustapa:")
        FilterChip(selected = mode == InputMode.Pen, onClick = { onChange(InputMode.Pen) }, label = { Text("Kynä") })
        FilterChip(selected = mode == InputMode.NumberPad, onClick = { onChange(InputMode.NumberPad) }, label = { Text("Numeronäppäimet") })
    }
}
