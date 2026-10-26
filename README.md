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
- Wrong answers are retried after the original questions in the same level.
  A second miss reveals the explanation and moves on with 0 XP; no endless loop.
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
