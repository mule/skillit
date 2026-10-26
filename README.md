# Skillit – Nuottiseikkailu

Finnish-first, native Android music practice for grade 4. M1 covers four levels of
five questions: Nuottininja, Sävelsalapoliisi, Rytmimestari and Musiikkimestari.
No account, network permission, ads, analytics or backend. All content ships in the APK.

The `music-grade4-fi` pack is original content based on the topics in issues #1–#8.
The three original worksheets were unavailable; this pack has **not** been compared
with them. M2 audio, interactive rhythm and adventure maps, and M3 mastery are deferred.

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
- Toggle mute and reopen; confirm persistence. M1 has no audio playback.
- Reset requires confirmation and clears progress; mute remains a preference.
- Check notation on a narrow phone, landscape and large font: C4 ledger line,
  H naming, stems, hollow note heads, eighth flags/beams, rests and stacked meters.
- Use TalkBack: answer buttons, hint, feedback, progress and reset are reachable;
  notation descriptions describe geometry without revealing the pitch-name answer.
- Verify 48 dp tap targets, text feedback in addition to colour and readable contrast.

## M1 verification checkpoint (2026-10-26)

Local verification: debug APK build, lint, 10 JVM tests and the full 12-test Android
instrumentation suite pass on an API 35 emulator in airplane mode. All seven
gameplay tests also pass at 360 dp portrait / 640 dp landscape with 150% text size.
Coverage includes multiple unresolved questions, accurate queued/immediate retry
copy, post-answer choice semantics and saved navigation/dialog state, plus the
complete 20-question session, repeated misses, user-controlled
retry, free hints, 195-XP mixed scoring, activity recreation, completion/reset/mute,
accessible choice targets, position semantics, version/identity rejection and
explicit recovery from corrupt session/protobuf data.

The storage test persists and reopens a save larger than 100 KB after 1,000
misses, preserves unresolved questions, and eventually completes with the correct
5-XP review awards. Engine tests replay after each of 80 consecutive misses and
reject stale attempts. Action-history storage grows with attempts; no fixed
attempt limit is imposed. Performance beyond this fixture has not been measured.

An earlier force-stop/cold-start check at `46babfc` restored second-miss feedback unchanged.
The child then chose a third attempt, used a free hint and earned 5 XP; the next
level remained unavailable until the correct answer. APK signature verification
passed; APK permissions have no Internet.

The screenshot test captures 21 notation examples at 320 dp width: all eight
pitches, four note/rest durations, both beam directions, flags and both meters.
To preserve captures, run instrumentation with Android's APK-retention flag, then
retrieve them. The flag also keeps the app installed for inspection; see the
[AndroidX rationale](https://android.googlesource.com/platform/frameworks/support/+/eb74b1a4b3527414639994574a201f125aff90f5).

```sh
./gradlew :app:connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
adb pull /sdcard/Android/data/fi.skillit/files/notation tmp/verify/notation
```

These captures support visual review; there is no automated pixel-golden comparison.
The initial implementation's captures were visually inspected, along with the
review-fix feedback/answer-marking captures. Earlier GitHub checks and independent
review/QA passed at `ff3c1c2`; fresh review of these fixes is pending. The parent
verified installation/launch of the earlier APK on a Samsung SM-X710, not full
physical gameplay. The current APK has not been tested on a physical device.
Other Android API levels and spoken TalkBack navigation remain unverified. Lint has no errors; it reports newer dependency/target versions.
Independent review/QA remains a delivery gate. This checkpoint does not close the epic.

Bravura music glyphs are bundled under the SIL Open Font License 1.1; the full
license and source information are in `app/src/main/assets/licenses/`.

Version-1 question/choice IDs retain their original label mappings. Neighbouring-pitch
distractors are deferred because changing the labels behind saved choice IDs would
misrepresent historical selections; that needs a planned pack migration.
