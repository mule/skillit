package fi.skillit.ink

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import com.google.mlkit.vision.digitalink.RecognitionContext
import com.google.mlkit.vision.digitalink.WritingArea
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Recognises the strokes of one box. Implemented by ML Kit on the device; tests use a fake. */
interface DigitRecognizer {
    val modelTag: String
    val state: StateFlow<ModelState>
    /** Checks for the on-device model and downloads it once if missing. Safe to call again after a failure. */
    suspend fun prepare()
    /** [strokes] are relative to the box's top-left corner; [width]×[height] is the box in px. */
    suspend fun recognize(strokes: List<InkStroke>, width: Float, height: Float): List<RawCandidate>
}

sealed interface ModelState {
    data object Checking : ModelState
    data object Downloading : ModelState
    data object Ready : ModelState
    data class Failed(val message: String) : ModelState
}

const val OFFLINE_MODEL_MESSAGE = "Käsialan tunnistusmalli ladataan kerran ja sen jälkeen kaikki toimii ilman verkkoa. " +
    "Yhdistä tabletti Wi-Fi-verkkoon ja yritä uudelleen. Siihen asti voit vastata numeronäppäimillä."

/**
 * ML Kit Digital Ink Recognition with the on-device model. The model (~20 MB) is downloaded once;
 * after that recognition is fully offline. Strokes are never sent anywhere: recognition runs locally.
 */
class MlKitDigitRecognizer(override val modelTag: String = "en-US") : DigitRecognizer {
    private val model: DigitalInkRecognitionModel = DigitalInkRecognitionModel.builder(
        requireNotNull(DigitalInkRecognitionModelIdentifier.fromLanguageTag(modelTag)) { "Unknown ML Kit model $modelTag" },
    ).build()
    private val manager = RemoteModelManager.getInstance()
    private var client: DigitalInkRecognizer? = null
    private val _state = MutableStateFlow<ModelState>(ModelState.Checking)
    override val state: StateFlow<ModelState> = _state

    override suspend fun prepare() {
        if (_state.value == ModelState.Ready) return
        _state.value = ModelState.Checking
        try {
            if (!manager.isModelDownloaded(model).await()) {
                _state.value = ModelState.Downloading
                manager.download(model, DownloadConditions.Builder().build()).await()
            }
            client = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
            _state.value = ModelState.Ready
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { _state.value = ModelState.Failed(OFFLINE_MODEL_MESSAGE) }
    }

    override suspend fun recognize(strokes: List<InkStroke>, width: Float, height: Float): List<RawCandidate> {
        val c = client ?: return emptyList()
        if (strokes.isEmpty()) return emptyList()
        val ink = Ink.builder().apply {
            strokes.forEach { s ->
                addStroke(Ink.Stroke.builder().apply { s.points.forEach { addPoint(Ink.Point.create(it.x, it.y, it.t)) } }.build())
            }
        }.build()
        val context = RecognitionContext.builder().setPreContext("").setWritingArea(WritingArea(width, height)).build()
        return c.recognize(ink, context).await().candidates.map { RawCandidate(it.text, it.score) }
    }

    fun close() { client?.close(); client = null }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

/** Moves strokes so (left, top) becomes the origin, as ML Kit expects for one writing area. */
fun List<InkStroke>.relativeTo(left: Float, top: Float) =
    map { s -> InkStroke(s.points.map { it.copy(x = it.x - left, y = it.y - top) }) }
