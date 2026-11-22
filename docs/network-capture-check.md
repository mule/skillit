# Network capture check (Release A, for #30)

**Rule (epic #31):** in Release A the only network use is the one-time ML Kit handwriting-model
download. Handwriting never leaves the tablet.

**What the build does (#26):**
- `INTERNET` is used by ML Kit's `RemoteModelManager` to download the `en-US` digital-ink model, Wi-Fi only
  (`DownloadConditions.requireWifi()`).
- ML Kit has no supported switch for its usage logging. The app removes the datatransport **CCT backend**
  (`backend:com.google.android.datatransport.cct.CctBackendFactory`) in `app/src/main/AndroidManifest.xml`,
  so logged events have no backend that can upload them. On the Tab S9 (2026-11-22) logcat shows
  `TransportRuntime: Transport backend 'cct' is not registered` and recognition still works.
- `RECEIVE_BOOT_COMPLETED` and `FOREGROUND_SERVICE` are removed. A fresh download still finished with the
  screen open on the Tab S9.
- CI runs `scripts/check-release-privacy.py`. It fails if the merged release manifest drifts from
  `app/release-manifest-allowlist.txt`, if the CCT backend comes back, or if `app/src/main` imports a network client.

## Capture procedure (run on the child's Tab S7+ before release, and after any ML Kit upgrade)

Tool: **PCAPdroid** (F-Droid or Play; VPN-based, no root). In its settings, set *Target apps* = Skillit (`fi.skillit`).
Turn on *Dump mode: none* or *pcap file*. TLS decryption is not needed, because host names (SNI/DNS) are enough.

1. **Fresh install:** uninstall Skillit, install the release APK. Start PCAPdroid capture.
2. **Model download:** on Wi-Fi, open the math screen (or the Ink lab on a debug build) and wait for
   "Malli valmis". Expected hosts: Google model download only (e.g. `dl.google.com`,
   `*.gvt1.com`, `redirector.gvt1.com`). Note every host.
3. **Offline use:** turn on airplane mode. Write and recognise at least 20 digits. Expect recognition to keep working.
4. **Back online:** turn off airplane mode, keep the app open for 5 minutes, then background it for 15 minutes.
5. **Reboot:** restart the tablet and don't open the app for 15 minutes.
6. Stop the capture and export the connection list.

**Pass:** the only connections are in step 2, to model-download hosts. **Fail:** any connection in steps 3–5, or
any host such as `firebaselogging-pa.googleapis.com`, `firebaselogging.googleapis.com`,
`play.googleapis.com/log` or `app-measurement.com`. On a failure, block the path in the manifest (or drop the
dependency), add it to `scripts/check-release-privacy.py`, and repeat.

Record the result (date, device, APK version, hosts seen) in #30.
