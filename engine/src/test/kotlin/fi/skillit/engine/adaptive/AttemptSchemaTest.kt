package fi.skillit.engine.adaptive

import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import fi.skillit.engine.AnswerInput
import fi.skillit.engine.AnswerSpec
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AttemptSchemaTest {
    private val mapper = ObjectMapper()
    private val schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
        .getSchema(File(System.getProperty("contentSchemaDir"), "attempt.v1.json").readText())
    private fun event(correct: Boolean, method: InputMethod) = AttemptEvent.record("math", 1, "item", "fact",
        JsonObject(emptyMap()), AnswerSpec.Numeric(63), AnswerInput.number(if (correct) 63 else 62), 0, false,
        method, if (method == InputMethod.PEN) 0.4 else null, 123)

    @Test fun canonicalRecordsValidateAndRoundTripIncludingExplicitNulls() {
        for (correct in listOf(true, false)) for (method in InputMethod.entries) {
            val e = event(correct, method)
            val encoded = AttemptCodec.encode(e)
            assertEquals(e, AttemptCodec.decode(encoded))
            assertTrue(schema.validate(mapper.readTree(encoded)).toString(), schema.validate(mapper.readTree(encoded)).isEmpty())
            assertTrue(encoded.contains("\"schema\":1"))
            assertTrue(encoded.contains("recognizerConfidence"))
        }
    }
    @Test fun schemaRejectsMissingFieldsUnknownFieldsAndInconsistentMetadata() {
        val node = mapper.readTree(AttemptCodec.encode(event(true, InputMethod.PAD))) as com.fasterxml.jackson.databind.node.ObjectNode
        val missing = node.deepCopy().apply { remove("params") }
        assertFalse(schema.validate(missing).isEmpty())
        val unknown = node.deepCopy().apply { put("strokes", "private") }
        assertFalse(schema.validate(unknown).isEmpty())
        val wrong = node.deepCopy().apply { put("errorClass", "other") }
        assertFalse(schema.validate(wrong).isEmpty())
        val confidence = node.deepCopy().apply { put("recognizerConfidence", 0.9) }
        assertFalse(schema.validate(confidence).isEmpty())
    }
}
