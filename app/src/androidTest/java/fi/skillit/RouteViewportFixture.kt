package fi.skillit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * Owns the regression viewport and text scale instead of inheriting the CI device's.
 * Activity recreation drops this wrapper, so restoration tests must use the production content.
 */
@Composable
fun OwnedRouteViewport(repository: ProgressRepository) {
    val density = LocalDensity.current.density
    CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.5f)) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.requiredSize(360.dp, 640.dp).testTag("route-viewport")) {
                SkillitTheme { SkillitApp(repository) }
            }
        }
    }
}
