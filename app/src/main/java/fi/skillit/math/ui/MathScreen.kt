package fi.skillit.math.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.skillit.engine.ExerciseSession
import fi.skillit.engine.adaptive.AttemptEvent
import fi.skillit.engine.adaptive.InputMethod
import fi.skillit.engine.pack.PackExercise
import fi.skillit.math.*
import fi.skillit.coach.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.flow.Flow
import fi.skillit.ink.InputMode
import fi.skillit.ink.InputModeSettings
import fi.skillit.ink.InputModeSwitch
import fi.skillit.ink.MlKitDigitRecognizer
import fi.skillit.ink.inkSettingsDataStore

/** Where attempts go. On the device: [AttemptLog] under noBackupFilesDir, on an IO thread. */
interface AttemptSink {
    suspend fun read(): List<AttemptEvent>
    suspend fun append(event: AttemptEvent)
}

/**
 * Everything the math area needs from outside, so UI tests can pass in-memory fakes.
 * [inputMode] is #26's pen / number-pad setting (pen by default); only the parent area calls [setInputMode].
 * [penInput] makes a fresh pen input per lesson, so a replayed lesson never shows old ink.
 */
class MathEnvironment(
    val loadCourse: suspend () -> MathCourse,
    val store: MathStore,
    val attempts: AttemptSink,
    val inputMode: Flow<InputMode>,
    val setInputMode: suspend (InputMode) -> Unit,
    val penInput: () -> DigitInput,
    val clock: () -> Long = System::currentTimeMillis,
    /** The handwriting coach (#28): Numeropaja, the in-lesson clarity moment and the parent trend. Null = off. */
    val coach: () -> CoachEnvironment? = { null },
) {
    companion object {
        fun android(context: Context): MathEnvironment {
            val app = context.applicationContext
            val log by lazy { AttemptLog.open(app) }
            val settings = InputModeSettings(app.inkSettingsDataStore)
            val recognizer by lazy { MlKitDigitRecognizer() }
            val coach by lazy { CoachEnvironment(LegibilityStore.open(app), recognizer, ink = HandwritingData.attemptInk(app)) }
            return MathEnvironment(
                loadCourse = { withContext(Dispatchers.IO) {
                    val text = runCatching { app.assets.open(MathCourse.PACK_ASSET) }.getOrNull()
                        ?: app.assets.open(MathCourse.FIXTURE_ASSET)
                    MathCourse.parse(text.bufferedReader(Charsets.UTF_8).use { it.readText() })
                } },
                store = MathRepository(app.mathDataStore),
                attempts = object : AttemptSink {
                    override suspend fun read() = withContext(Dispatchers.IO) { log.read() }
                    override suspend fun append(event: AttemptEvent) { withContext(Dispatchers.IO) { log.append(event) } }
                },
                inputMode = settings.mode,
                setInputMode = settings::setMode,
                penInput = { PenInput(recognizer) },
                coach = { coach },
            )
        }
    }
}

private enum class Pane { MAP, LESSON, PIN, PARENT, SET_PIN, STUDIO;
    /** The parent area is open only for this foreground visit: never restored, never kept across ON_STOP. */
    val unlocked get() = this == PIN || this == PARENT || this == SET_PIN
}
private val PaneSaver = androidx.compose.runtime.saveable.Saver<MutableState<Pane>, String>(
    save = { (if (it.value.unlocked) Pane.MAP else it.value).name },
    restore = { mutableStateOf(Pane.valueOf(it)) },
)

/** Kertolaskuseikkailu: lesson map, lessons with boards, and the parent area behind a long press + PIN. */
@Composable
fun MathScreen(env: MathEnvironment, onScreenChanged: () -> Unit = {}, onExit: () -> Unit) {
    val scope = rememberCoroutineScope()
    var course by remember { mutableStateOf<MathCourse?>(null) }
    var progress by remember { mutableStateOf<MathProgress?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var warning by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pane by rememberSaveable(saver = PaneSaver) { mutableStateOf(Pane.MAP) }
    var corrupt by remember { mutableStateOf<CorruptMathSave?>(null) }
    var confirmFresh by remember { mutableStateOf(false) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && pane.unlocked) pane = Pane.MAP
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var confirmSwitch by remember { mutableStateOf<String?>(null) }
    val inputMode by env.inputMode.collectAsState(initial = InputMode.Pen)

    fun load() {
        busy = true
        scope.launch {
            try { course = env.loadCourse(); progress = env.store.load(); error = null; corrupt = null }
            catch (e: CancellationException) { throw e }
            catch (e: CorruptMathSave) { corrupt = e; error = null }
            catch (_: Exception) { error = "Kertolaskuseikkailua ei voitu avata." }
            finally { busy = false }
        }
    }
    fun save(value: MathProgress, after: () -> Unit = {}) {
        if (busy) return
        busy = true
        scope.launch {
            try { env.store.save(value); progress = value; error = null; after() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Tallennus epäonnistui. Yritä uudelleen." }
            finally { busy = false }
        }
    }
    LaunchedEffect(env) { load() }
    fun startLesson(stopId: String) {
        val co = course ?: return
        if (busy) return
        busy = true
        scope.launch {
            val history = try { env.attempts.read() } catch (e: CancellationException) { throw e } catch (_: Exception) {
                warning = "Harjoitushistoriaa ei voitu lukea. Tehtävät valitaan ilman sitä."; emptyList()
            }
            val now = env.clock()
            val plan = co.plan(stopId, history, now, seed = now)
            busy = false
            val s = progress ?: return@launch
            save(s.copy(packId = co.pack.id, packVersion = co.pack.version, active = ActiveLesson(plan), lastExplanation = plan.explanation)) {
                pane = Pane.LESSON
            }
        }
    }
    /** Attempt first (so the log never misses an answer the session counted), then the session events. */
    fun commit(lesson: ActiveLesson, updated: ExerciseSession, attempt: AttemptEvent?) {
        if (busy) return
        busy = true
        scope.launch {
            if (attempt != null) try { env.attempts.append(attempt) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { warning = "Harjoitushistoriaa ei voitu tallentaa. Peli jatkuu normaalisti." }
            busy = false
            progress?.let { save(it.copy(active = lesson.copy(events = updated.events))) }
        }
    }

    val saved = progress
    val c = course
    val active = remember(c, saved?.active) {
        val a = saved?.active ?: return@remember null
        val exercises = c?.exercises(a.plan) ?: return@remember null
        runCatching { Triple(a, exercises, c.session(exercises, a.events)) }.getOrNull()
    }
    // One clarity coach per lesson, above the pane switch, so visiting the map or the studio keeps its limits.
    val clarity = remember(active?.first?.plan) { ClarityCoach() }
    // A new screen or a new question starts at the top, like music and nature.
    val screenKey = listOf(pane, active?.third?.current?.token, active?.third?.complete, saved?.pin == null)
    LaunchedEffect(screenKey) { onScreenChanged() }
    BackHandler(enabled = pane != Pane.MAP || confirmSwitch != null) {
        if (confirmSwitch != null) confirmSwitch = null else pane = Pane.MAP
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        error?.let { message ->
            Card { Column(Modifier.padding(16.dp)) {
                Text(message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (saved == null || c == null) {
                    TextButton(onClick = { load() }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Yritä uudelleen") }
                    TextButton(onClick = onExit, modifier = Modifier.heightIn(min = 48.dp)) { Text("Etusivu") }
                }
            } }
        }
        warning?.let { Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        corrupt?.let { damaged ->
            Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Kertolaskuseikkailun tallennus on vioittunut, eikä sitä voi lukea.", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Text("Voit aloittaa kertolaskut alusta. Musiikin ja luontoretken tallennukset säilyvät." +
                    if (damaged.pin == null) " Vanhempi asettaa PIN-koodin uudelleen." else " Vanhempien PIN-koodi säilyy.")
                TextButton(onClick = { load() }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Yritä uudelleen") }
                TextButton(onClick = { confirmFresh = true }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Aloita kertolaskut alusta") }
                TextButton(onClick = onExit, modifier = Modifier.heightIn(min = 48.dp)) { Text("Etusivu") }
            } }
        }
        if (saved == null || c == null) {
            if (busy) Text("Ladataan kertolaskuseikkailua…")
            return@Column
        }
        // The parent sets the PIN before the child's first lesson (the app is installed by the parent).
        if (saved.pin == null || pane == Pane.SET_PIN) {
            PinSetup(busy, first = saved.pin == null) { pin ->
                busy = true
                scope.launch {
                    val created = withContext(Dispatchers.Default) { ParentPin.create(pin) }
                    busy = false
                    save(saved.copy(pin = created)) { pane = if (pane == Pane.SET_PIN) Pane.PARENT else Pane.MAP }
                }
            }
            return@Column
        }
        when (pane) {
            Pane.PIN -> PinEntry(saved.pin, env.clock, busy, onCancel = { pane = Pane.MAP }) { result ->
                when (result) {
                    is ParentPin.Check.Ok -> save(saved.copy(pin = saved.pin.cleared())) { pane = Pane.PARENT }
                    is ParentPin.Check.Wrong -> save(saved.copy(pin = result.updated))
                    is ParentPin.Check.Locked -> Unit
                }
            }
            Pane.PARENT -> ParentArea(saved, busy,
                inputMode = inputMode,
                onInput = { mode -> scope.launch {
                    try { env.setInputMode(mode) } catch (e: CancellationException) { throw e } catch (_: Exception) { error = "Asetusta ei voitu tallentaa." }
                } },
                onChangePin = { pane = Pane.SET_PIN },
                onClose = { pane = Pane.MAP },
                coach = env.coach())
            Pane.STUDIO -> env.coach()?.let { DigitStudio(it) { pane = Pane.MAP } } ?: LaunchedEffect(Unit) { pane = Pane.MAP }
            Pane.LESSON -> if (active == null) LaunchedEffect(Unit) { pane = Pane.MAP } else {
                val (lesson, exercises, session) = active
                val pen = remember(lesson.plan) { env.penInput() }
                LessonScreen(c, lesson, exercises, session, if (inputMode == InputMode.NumberPad) PadInput else pen, busy, env.clock, env.coach(), clarity, scope,
                    onEvent = { updated, attempt -> commit(lesson, updated, attempt) },
                    onFinish = { xp, reward ->
                        val old = saved.lessons[lesson.plan.stopId]
                        val record = LessonRecord((old?.completions ?: 0) + 1, maxOf(old?.bestXp ?: 0, xp), reward ?: old?.rewardWord)
                        save(saved.copy(xp = saved.xp + xp, lessons = saved.lessons + (lesson.plan.stopId to record), active = null)) { pane = Pane.MAP }
                    },
                    onMap = { pane = Pane.MAP })
            }
            else -> MathMap(c, saved, active?.first, busy,
                onParent = { pane = Pane.PIN },
                onStudio = if (env.coach() != null) ({ pane = Pane.STUDIO }) else null,
                onExit = onExit) { stop ->
                if (active?.first?.plan?.stopId == stop.id) pane = Pane.LESSON
                else if (active != null) confirmSwitch = stop.id
                else startLesson(stop.id)
            }
        }
    }
    if (confirmFresh) AlertDialog(onDismissRequest = { confirmFresh = false },
        title = { Text("Aloitetaanko kertolaskut alusta?") },
        text = { Text("Kertolaskujen pisteet ja keskeneräinen oppitunti nollataan. Harjoitushistoria ja muut aineet säilyvät.") },
        confirmButton = { TextButton(onClick = {
            confirmFresh = false
            val keep = corrupt?.pin
            busy = true
            scope.launch {
                try { env.store.save(MathProgress(pin = keep)); busy = false; load() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { busy = false; error = "Tallennus epäonnistui. Yritä uudelleen." }
            }
        }, enabled = !busy) { Text("Aloita alusta") } },
        dismissButton = { TextButton(onClick = { confirmFresh = false }) { Text("Peruuta") } })
    confirmSwitch?.let { stopId ->
        AlertDialog(onDismissRequest = { confirmSwitch = null },
            title = { Text("Vaihdetaanko oppituntia?") },
            text = { Text("Keskeneräinen oppitunti jää kesken, eikä sen pisteitä lasketa.") },
            confirmButton = { TextButton(onClick = {
                confirmSwitch = null
                startLesson(stopId)
            }, enabled = !busy) { Text("Vaihda") } },
            dismissButton = { TextButton(onClick = { confirmSwitch = null }) { Text("Peruuta") } })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MathMap(course: MathCourse, progress: MathProgress, active: ActiveLesson?, busy: Boolean,
                    onParent: () -> Unit, onStudio: (() -> Unit)?, onExit: () -> Unit, open: (MathCourse.Stop) -> Unit) {
    var parentHint by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Kertolaskuseikkailu", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f).semantics { heading() })
        Box(Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
            .combinedClickable(onClickLabel = "Näytä ohje", onLongClickLabel = "Avaa vanhempien alue",
                onClick = { parentHint = true }, onLongClick = { parentHint = false; onParent() })
            .padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text("Vanhemmille")
        }
    }
    if (parentHint) Text("Vanhempi: pidä painiketta pohjassa.", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    Text("Kappale 2: Kertolaskua. Matematiikan pisteet: ${progress.xp} XP (erillään musiikin pisteistä).")
    onStudio?.let {
        OutlinedButton(onClick = it, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Numeropaja · harjoittele numeroiden kirjoittamista") }
    }
    course.stops.forEach { stop ->
        val record = progress.lessons[stop.id]
        val isActive = active?.plan?.stopId == stop.id
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${stop.label}: ${stop.title}", style = MaterialTheme.typography.titleLarge)
            Text(when {
                isActive -> "Kesken · jatka siitä, mihin jäit."
                record != null -> "Valmis ${record.completions}× · paras ${record.bestXp} XP" + (record.rewardWord?.let { " · palkintosana $it" } ?: "")
                else -> "Noin ${MathCourse.SESSION_SIZE} tehtävää omaan tahtiin."
            })
            Button(onClick = { open(stop) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(when {
                    isActive -> "Jatka: ${stop.label}"
                    record != null -> "Harjoittele uudelleen: ${stop.label}"
                    else -> "Aloita: ${stop.label}"
                })
            }
        } }
    }
    TextButton(onClick = onExit, modifier = Modifier.heightIn(min = 48.dp)) { Text("Etusivu") }
}

@Composable
private fun LessonScreen(
    course: MathCourse, lesson: ActiveLesson, exercises: List<PackExercise>, session: ExerciseSession,
    input: DigitInput, busy: Boolean, clock: () -> Long, coach: CoachEnvironment?, clarity: ClarityCoach,
    /** Outlives this screen, so an answer's handwriting data is still saved if the lesson closes right away. */
    background: kotlinx.coroutines.CoroutineScope,
    onEvent: (ExerciseSession, AttemptEvent?) -> Unit, onFinish: (Int, String?) -> Unit, onMap: () -> Unit,
) {
    val stop = course.stop(lesson.plan.stopId)
    val byId = exercises.associateBy { it.id }
    fun act(action: (ExerciseSession) -> Boolean) {
        if (busy) return
        val updated = course.session(exercises, lesson.events)
        if (action(updated)) onEvent(updated, null)
    }
    if (session.complete) {
        val reward = exercises.firstNotNullOfOrNull { MathCourse.rewardWord(it) }
        Text("${stop.label} valmis!", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        reward?.let {
            Text("Palkintosana", style = MaterialTheme.typography.titleMedium)
            Text(it, fontSize = 48.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { contentDescription = "Palkintosana $it" })
        }
        Text("${session.xp} XP", style = MaterialTheme.typography.headlineMedium)
        Text("Oikein ensimmäisellä yrityksellä: ${session.firstTryCorrect}/${session.questions.size}")
        Text("Harjoittelit uudelleen ${session.retryCount} tehtävää.")
        Button(onClick = { onFinish(session.xp, reward) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Takaisin kartalle") }
        return
    }
    val attempt = session.current ?: return
    val exercise = byId.getValue(attempt.question.id)
    // One draft per item, kept for its retry: the child fixes the marked cell, not the whole board.
    val drafts = remember(lesson.plan) { mutableMapOf<String, AnswerDraft>() }
    // Seeded from the last submitted answer, so feedback and a retry show the child's work after resuming.
    val draft = drafts.getOrPut(exercise.id) {
        AnswerDraft(BoardKind.of(exercise)).also { d -> session.outcomes.lastOrNull { it.questionId == exercise.id }?.check?.let(d::restore) }
    }
    val feedback = session.feedback
    // Handwriting coach (#28): measured after an answer is checked; may offer an optional clarity moment.
    // Neither touches the session, so XP and progress depend only on the math.
    var prompt by remember(attempt.token) { mutableStateOf<ClarityPrompt?>(null) }
    val previous = if (feedback == null && attempt.retry) session.outcomes.lastOrNull { it.questionId == exercise.id }?.check else null

    Text(stop.label, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    Text("${session.resolvedCount}/${session.questions.size} ratkaistu · ${session.xp} XP")
    LinearProgressIndicator(progress = { session.resolvedCount.toFloat() / session.questions.size }, modifier = Modifier.fillMaxWidth().semantics {
        contentDescription = "Edistyminen, ${session.resolvedCount} ratkaistua tehtävää, yhteensä ${session.questions.size}"
    })
    if (attempt.retry) Text("Uusi yritys · voit saada 5 XP")
    Text(exercise.prompt, style = MaterialTheme.typography.titleLarge)
    MathBoard(exercise, draft, input, feedback?.check ?: previous, enabled = !busy && feedback == null)
    if (feedback == null) {
        Button(onClick = {
            if (busy) return@Button
            val answer = draft.input()
            val updated = course.session(exercises, lesson.events)
            val hinted = updated.hintVisible
            if (updated.submit(attempt.token, answer)) {
                val gen = exercise.generator
                val confidence = if (input.method == InputMethod.PEN) draft.confidence()?.coerceIn(0.0, 1.0) else null
                // Same spec and input the session just accepted; the classifier never affects XP.
                val event = AttemptEvent.record(course.pack.id, course.pack.version, exercise.id, gen?.family ?: "expression",
                    gen?.params ?: JsonObject(emptyMap()), exercise.answer, answer, attempt.number, hinted, input.method,
                    confidence, clock())
                onEvent(updated, event)
                if (coach != null) {
                    val written = input.written(exercise.id)
                    prompt = clarity.afterAnswer(written)
                    // Strokes are kept per attempt (pruned to AttemptInkStore.KEEP_ATTEMPTS); only aggregates may ever sync.
                    val generation = HandwritingData.generation // taken now: a reset after this drops the answer's data
                    background.launch { coach.recordAnswer(written, attemptKey = event.eventId, generation = generation) }
                }
            }
        }, enabled = !busy && draft.complete, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Tarkista") }
        if (draft.values.values.any { it.pending }) Text("Luetaan käsialaa…", style = MaterialTheme.typography.bodyMedium)
        else if (!draft.complete) Text("Täytä vastausruudut, niin voit tarkistaa.", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { act { it.showHint(attempt.token) } }, enabled = !busy && !session.hintVisible,
            modifier = Modifier.heightIn(min = 48.dp)) { Text("Vihje") }
        if (session.hintVisible) Text(exercise.hint, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    } else {
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (feedback.correct) "Hienoa! +${feedback.xp} XP" else if (session.retryIsNext) "Harjoitellaan vielä. Voit yrittää uudelleen." else "Harjoitellaan vielä. Saat uuden yrityksen oppitunnin lopussa.",
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (!feedback.correct) Text(exercise.hint)
        } }
        if (coach != null) prompt?.let { p -> ClarityMoment(p, coach, onClose = { prompt = null }) }
        Button(onClick = { act { it.next(attempt.token) } }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(if (session.retryIsNext) "Yritä uudelleen" else "Jatka")
        }
    }
    TextButton(onClick = onMap, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Kartalle") }
}

@Composable
private fun ParentArea(progress: MathProgress, busy: Boolean, inputMode: InputMode, onInput: (InputMode) -> Unit, onChangePin: () -> Unit, onClose: () -> Unit,
                       coach: CoachEnvironment? = null) {
    Text("Vanhempien alue", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
    Text("Oletuksena lapsi kirjoittaa kynällä. Numeronäppäimet ovat varakeino.")
    InputModeSwitch(inputMode, onInput)
    Text("Matematiikan pisteet: ${progress.xp} XP · valmiita oppitunteja ${progress.lessons.size}")
    progress.lastExplanation?.let {
        Text("Viimeisimmän oppitunnin tehtävävalinta", style = MaterialTheme.typography.titleMedium)
        Text(it)
    }
    coach?.let { LegibilityParentCard(it) }
    OutlinedButton(onClick = onChangePin, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Vaihda PIN-koodi") }
    Button(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Sulje vanhempien alue") }
}

@Composable
private fun PinSetup(busy: Boolean, first: Boolean, onDone: (String) -> Unit) {
    var firstPin by rememberSaveable { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    Text(if (first) "Vanhemmalle: aseta PIN-koodi" else "Uusi PIN-koodi", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    Text(if (firstPin == null) "Valitse 4-numeroinen koodi. Sillä avataan vanhempien alue (pitkä painallus “Vanhemmille”)." else "Kirjoita sama koodi uudelleen.")
    message?.let { Text(it, color = CellStyle.wrong, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    PinPad(enabled = !busy, key = firstPin ?: "") { pin ->
        val chosen = firstPin
        when {
            chosen == null -> { firstPin = pin; message = null }
            chosen == pin -> onDone(pin)
            else -> { firstPin = null; message = "Koodit eivät täsmää. Aloita alusta." }
        }
    }
}

@Composable
private fun PinEntry(pin: ParentPin, clock: () -> Long, busy: Boolean, onCancel: () -> Unit, onResult: (ParentPin.Check) -> Unit) {
    var message by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Text("Vanhempien alue", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    Text("Kirjoita PIN-koodi.")
    message?.let { Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    // One guess at a time: the next guess is checked against the saved failure count.
    PinPad(enabled = !busy && !checking) { typed ->
        checking = true
        scope.launch {
            val result = try { withContext(Dispatchers.Default) { pin.check(typed, clock()) } } finally { checking = false }
            message = when (result) {
                is ParentPin.Check.Ok -> null
                is ParentPin.Check.Wrong -> if (result.updated.lockedUntil > 0) "Väärä koodi. Odota hetki ennen uutta yritystä." else "Väärä koodi."
                is ParentPin.Check.Locked -> "Odota vielä ${result.secondsLeft} s."
            }
            onResult(result)
        }
    }
    TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Peruuta") }
}

/** Four dots and its own digit keys; never the number pad of the game, never the system keyboard. */
@Composable
private fun PinPad(enabled: Boolean, key: String = "", onComplete: (String) -> Unit) {
    var typed by remember(key) { mutableStateOf("") }
    Text("●".repeat(typed.length) + "○".repeat(ParentPin.LENGTH - typed.length), fontSize = 32.sp,
        modifier = Modifier.semantics { contentDescription = "PIN, ${typed.length}/${ParentPin.LENGTH} numeroa" })
    listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(null, 0, -1)).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { k ->
                when (k) {
                    null -> Spacer(Modifier.size(72.dp, 56.dp))
                    -1 -> OutlinedButton(onClick = { typed = typed.dropLast(1) }, enabled = enabled, modifier = Modifier.size(72.dp, 56.dp)) { Text("⌫") }
                    else -> OutlinedButton(onClick = {
                        typed += k
                        if (typed.length == ParentPin.LENGTH) { val pin = typed; typed = ""; onComplete(pin) }
                    }, enabled = enabled, modifier = Modifier.size(72.dp, 56.dp).semantics { contentDescription = "PIN $k" }) { Text("$k", fontSize = 22.sp) }
                }
            }
        }
    }
}
