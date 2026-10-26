# Skillit – Nuottiseikkailu

Finnish-first, native Android music practice for grade 4. M1 covers four levels of
five questions: Nuottininja, Sävelsalapoliisi, Rytmimestari and Musiikkimestari.
No account, network permission, ads, analytics or backend. All content ships in the APK.

The `music-grade4-fi` pack is original content based on the topics in issues #1–#8.
The three original worksheets were unavailable; this pack has **not** been compared
with them. M2 adds offline audio, interactive rhythm practice and a four-island map. M3 mastery and parent tools remain deferred.

## Build and install

Requirements: JDK 21, Android SDK platform 35 and build tools, accepted SDK licenses.
Set `ANDROID_HOME` or put `sdk.dir=/absolute/path/to/Android/Sdk` in `local.properties`.

```sh
./gradlew :engine:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n fi.skillit/.MainActivity
# With an emulator or device attached:
./gradlew :app:connectedDebugAndroidTest
```

The debug APK is signed with a development key; it is not a published release.
GitHub Actions builds the APK artifact and runs unit and emulator tests when this
branch is pushed. A local pass does not establish a GitHub Actions pass.

## Architecture

- `engine`: plain Kotlin exercise/session logic; no Android or music dependencies.
- `app`: Compose Material 3 UI, music domain/content, Canvas notation and local storage.
- No second-subject framework, account system or network service.

## QA checklist

- Complete all 20 questions in airplane mode; see all four Finnish level names.
- First-try correct: 10 XP; correct retry: 5 XP. Hints do not reduce XP.
- Every miss earns 0 XP and queues the question for review at the end of the same
  level. Repeated misses keep the question unresolved. The child chooses
  “Yritä uudelleen”, can use the free hint, and earns 5 XP on any later correct
  answer. There is no fixed attempt limit or automatic completion after a miss.
- Progress counts correctly resolved questions. A level and the final summary
  require every question to be answered correctly. Reviewing the same question
  several times counts as one reviewed question in the summary.
- Choices keep a deterministic order; every new attempt has a distinct token so
  stale taps cannot answer the next attempt. New games reset session XP only
  after explicit confirmation. Whole rests are excluded from authored 2/4 tasks.
- With two unresolved questions, a miss says the item returns at the end of the
  level and “Jatka” opens the next review. Only when the same item is next does
  the button say “Yritä uudelleen”. Queue order and unlimited retries are unchanged.
- Selected/correct choice text and accessibility states appear only after an answer.
  Returning home and open settings/reset dialogs survive activity recreation.
- Tap an answer twice and rotate during feedback: no duplicate award.
- Kill and reopen the process during a question, hint, feedback and retry;
  verify question, XP and retry state survive. Finish, reopen and verify summary.
- Toggle mute and adjust volume, then reopen; confirm both persist. Playback stops on navigation, settings, backgrounding and disposal.
- Reset requires confirmation and clears progress; mute remains a preference.
- Check notation on a narrow phone, landscape and large font: C4 ledger line,
  H naming, stems, hollow note heads, eighth flags/beams, rests and stacked meters.
- Use TalkBack: answer buttons, hint, feedback, progress and reset are reachable;
  notation descriptions describe geometry without revealing the pitch-name answer.
- Verify 48 dp tap targets, text feedback in addition to colour and readable contrast.

## M2 behavior and compatibility

The map has four music islands. Correctly resolving all five original questions on
an island earns its music badge and unlocks the next island. Misses never remove
progress or points. Completed islands remain available for practice. Each island
has its own resumable practice round with **separate practice XP** (10/5/0, free
hints and unlimited same-level retries). Starting a new practice round replaces
that island's practice result; it does not add to the original adventure total.
The original 20-question result and answer identities remain unchanged.

Sävelpaja plays C4–D4–E4–F4–G4–A4–H4–C5 and 2/4 or 4/4 pulse examples.
One owned playback controller sends finite synthesized mono PCM16 at 24 kHz to
AudioTrack, replacing the previous sound on every play. Notes use concert A =
440 Hz and short attack/release ramps. Rests are silent. Mute and volume are local
preferences; the Android media volume also applies. No microphone or new
permissions are used. Sound stops on background, navigation, settings and disposal;
playback never resumes automatically after cancellation.

Rytmipaja generates deterministic original 2/4 and 4/4 bars. Complete the visible
prefix with notes or rests using buttons or long-press dragging into the bar.
Validation sums integer eighth-note units, accepts any exact-duration completion,
and explains underfill or overflow. Correct completions play the child's rhythm.
The example can also be read and heard without timing. Optional tap practice has
a visible three-count preparation, a visual beat pulse and non-punitive feedback
within 140 ms of note onsets; rests have no tap target. Taps closer than 80 ms are
ignored. This is informal practice using the monotonic UI clock, not calibrated
latency measurement. Timing never gates XP, unlocks or completion. Start, stop and
retry are child-controlled. Backgrounding and opening settings cancel a timing
run. The bar, meter and seed survive recreation; an active timing run is cancelled.

Storage schema 2 retains the installed M1 DataStore file/key and pack version 1.
It replays and validates v1 events before an atomic upgrade, preserving question,
choice, hint, feedback, retry queue, mute and computed XP. Earned islands are derived
from accepted correct answers, including completed v1 saves. Practice event logs
are stored separately per island; volume defaults to 60% for a v1 save. Invalid
history, unknown schemas and corrupt bytes show explicit recovery instead of a
silent reset. M1 cannot read an upgraded schema-2 save: downgrading the APK is not
supported and would show M1's recovery screen. A malformed schema-2 practice log
also requests explicit recovery; no partial history is silently discarded.
Reset clears adventure and practice with confirmation while keeping
current sound preferences. A force-stopped practice round can be resumed from its
island on the map; the original adventure resumes by default on a cold launch.

## Local verification

Use the build and instrumentation commands above. The M2 implementation report
under `.pane/m2-implementation-status.md` records the exact local head, APK hash,
commands, captures and remaining review/QA gates. `.pane/` and `tmp/verify/` are
ignored local evidence and are not part of the APK or source distribution.

Tests cover deterministic PCM/pitch/rest/frame boundaries and playback lifecycle;
generated rhythm validity, duration completion and tap-session clocks; island
unlock/replay; real DataStore v1 migration, reopen and corrupt-data recovery; and
Compose controls, dragging, accessible button alternatives, replay and the original
20-question gameplay. The AudioTrack test verifies accepted PCM, advancing playback
head and release. This is output-boundary evidence, **not proof of audible quality
on a physical device**. No physical-tablet or spoken TalkBack pass is claimed.
Original worksheets remain unavailable. Independent source review and QA remain
required before publication; local green tests do not establish CI success.

Notation instrumentation captures 21 examples (all eight pitches, four note/rest
durations, beam directions, flags and meters). There is no automated pixel-golden
comparison. To retain the app and pull screenshots after a test run:

```sh
./gradlew :app:connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
adb pull /sdcard/Android/data/fi.skillit/files/notation tmp/verify/notation
adb pull /sdcard/Android/data/fi.skillit/files/m2 tmp/verify/m2
```

Action histories grow with attempts. The storage regression persists/reopens over
100 KB after 1,000 misses and then resolves the questions; no fixed retry cap is
imposed. Performance beyond that fixture has not been measured. Other Android API
levels, output hardware and spoken accessibility behavior remain unverified.

Bravura music glyphs are bundled under the SIL Open Font License 1.1; the full
license and source information are in `app/src/main/assets/licenses/`.

Version-1 question/choice IDs retain their original label mappings. Neighbouring-pitch
distractors remain deferred because changing labels behind saved choice IDs would
misrepresent historical selections; that needs a planned pack migration.
