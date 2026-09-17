# pro-parity-suite

Auto.js Pro parity catalog. `main.cjs` runs every snippet in order; each
snippet prints its own result lines and an explicit skip code when the host
provider or the Android permission behind it is missing, so the output
documents what is really available on the device.

| Example | Snippet | Required capabilities | Skip reason |
| --- | --- | --- | --- |
| Java interop | `snippets/java-interop.cjs` | `java_interop` | the provider enforces the reviewed class/member allowlist |
| Rhino install | `snippets/rhino-install.cjs` | `rhino`, `java_interop` | `rhino.install({ explicit: true })` needs the Java proxy provider |
| UI layout | `snippets/ui-layout.cjs` | `ui` | the host UI provider may be unavailable for the execution mode |
| Floaty overlay | `snippets/overlay-floaty.cjs` | `ui.overlay`, `ui.overlay.permission` | Android overlay permission may be missing |
| Tasks | `snippets/tasks-work-manager.cjs` | `work_manager` | WorkManager provider may be unavailable |
| Notifications/power | `snippets/notifications-power.cjs` | `notifications`, `notifications.settings`, `device`, `device.power` | notification or power provider may be unavailable |
| Media/recorder | `snippets/media-recorder.cjs` | `media`, `media.audio`, `media.metadata`, `media.recording` | microphone permission or MediaInfo plugin may be missing |
| Screenshot/OCR | `snippets/screenshot-ocr.cjs` | `screen_capture`, `image`, `ocr` | MediaProjection consent, image handle or OCR provider may be missing |
| Accessibility | `snippets/accessibility-selector.cjs` | `accessibility` | the accessibility service may be disabled |

Every snippet can also run on its own: it exports `run()` and executes when it
is the entry. Running the suite outside the AutoJs6 host prints a skip code for
each snippet and still completes the catalog.

`smoke.cjs` is the focused subset used by the host smoke run: the JSON UI layout
path and the WorkManager one-shot schedule/cancel path.

`overlay-floaty` is stable after real window checks on three ABIs. Run it directly
to show a draggable window for three seconds; its text and alpha change, events
are logged, and the window closes. `media-recorder` requires explicit Android
microphone permission before it records three seconds to `record.m4a` in the
workspace. The foreground notification offers a stop action. The shared recording
and MediaInfo flow passed physical-device acceptance on 2026-09-10, so this snippet
is stable. The same receipt promotes `screenshot-ocr` after MediaProjection capture
and recognition of the host UI. See the [manual acceptance record](../../../docs/nodejs/MANUAL-ACCEPTANCE-20260910.md).
Neither
snippet prints PASS for a skipped permission or missing provider. Playback and
MediaStore remain outside this recording example.
