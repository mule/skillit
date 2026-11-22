package fi.skillit.math

import fi.skillit.engine.pack.ContentPack
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class MathFixtureTest {
    private val asset = File("src/main/assets/${MathCourse.FIXTURE_ASSET}")

    @Test fun bundledFixtureIsExactlyTheGeneratedPackAndPassesMathgenImport() {
        val expected = ContentPack.encode(MathFixture.build()) + "\n"
        if (System.getenv("UPDATE_MATH_FIXTURE") == "1") { asset.parentFile.mkdirs(); asset.writeText(expected) }
        assertEquals("Run with UPDATE_MATH_FIXTURE=1 to regenerate", expected, asset.readText())
        val course = MathCourse.parse(asset.readText())
        assertEquals(listOf(14, 18, 19, 20, 21, 22, 26, 27), course.stops.map { it.lesson })
        assertEquals(listOf(false, false, false, false, false, false, false, true), course.stops.map { it.test })
    }
}
