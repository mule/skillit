# Local math attempts v1

`attempt.v1.json` defines one UTF-8 JSON-lines record. Release B uploads these
records unchanged; transport batching/acknowledgments must live separately.
No strokes, scans, child identity or device identifiers are included.

Use `AttemptEvent.record(...)` with the exact pack id/version, item id, answer
spec and input accepted by `ExerciseSession`. Copy `generator.family` and
`generator.params` unchanged; for expression-only items use a stable caller
family and an empty params object. Retain each immutable pack version for
future explanations: the pack supplies the original prompt and expected answer.
The log records the complete submitted per-cell map, including explicit nulls
and optional carry cells. Choice answers retain the choice id.

Retry numbers are zero-based. `hintUsed` means a hint was used before this
submission. Pen confidence is nullable, in [0,1]; pad confidence must be null.
`timestamp` is UTC Unix milliseconds. A correct attempt has null `errorClass`.
Error labels describe hypotheses, not proven causes; they never change XP.
The classifier uses low pen confidence (<0.65) first, then a power-of-ten shift,
then evidence of a lost carry, then table-fact errors; ambiguous errors are
`other`. Missing optional carries alone are not mistakes. No AI or mathgen
module runs on this path.

Create one UUID per accepted submission and reuse it when retrying persistence.
`AttemptLog.open(context)` writes only under `noBackupFilesDir/math-attempts`.
Call synchronous store methods on an IO dispatcher and surface IO failures;
do not tell the caller a write succeeded before `append` returns. Identical
UUID replays are no-ops, conflicting replays are rejected. A process monitor
and OS file lock serialize store instances and processes. Each append is
fsynced. A newline is the commit boundary: after a crash the store truncates
only the unterminated, uncommitted suffix. Malformed committed lines stop
reads/writes with IOException and are preserved for recovery. There is no
reset, overwrite, network, or compaction API. Reads currently scan the log;
indexing/retention is future work before very large histories are deployed.

No migration of music/nature progress is needed: this is a separate file and
format. Existing music v1/v2 fixtures still replay with the same XP/events.
Nature currently uses Compose rememberSaveable rather than a disk repository;
its code/state keys stay unchanged. JVM compatibility checks also seed legacy
save sentinels and verify that math-log operations leave their bytes intact.
No device backup/restore or process-kill test is claimed by those JVM checks.

Pass one lesson pool, the local history, an explicit `now` and a seed to
`AdaptiveSelector.select`. It returns up to 12 unique items, a Finnish parent
explanation, and due review ids. Up to one third of slots (at least one) are
reserved for the oldest due misses; the rest are sampled without replacement.
A miss is due after 1 day; independent unhinted first-try successes increase
spacing to 3, 7, then 14 days (at most one success per UTC day). Retried/hinted
successes restart the clock without extending the interval. Remaining weights
use the latest 40 relevant family/item records, with extra weight for matching
opaque params. Other pack versions, other packs and future-dated attempts do
not contribute. Reasons use answer specs and error classes, never guessed
parameter meanings. The UI caller (#27) displays `Selection.explanation`.
