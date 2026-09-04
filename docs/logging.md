# Logging standard

libGDX exposes exactly three levels through `Application`: `error`, `log` (info), and `debug`.
There is no WARN. Every call goes through `com.prpg.util.Log`, which is null-safe for headless tests
(`Gdx.app == null`) and gives the three levels consistent meaning.

## Levels

A warning is an `error`-level call that does **not** throw and whose message names the degradation
and the fallback taken. Recovery is encoded in the message and the absence of a rethrow, not in a
separate level.

| Situation | Level |
|---|---|
| Swallowed exception (caught, recovered, not rethrown): corrupt save, unreadable registry, mount failure with bundled fallback | `error` |
| Missing required content (no such knot, bundled file absent, no `pack.yaml`) | `error` |
| Missing optional content / graceful fallback (unknown item id ignored, i18n key fallthrough) | `error`, phrased as a warning, naming the fallback |
| Significant state change (act advanced, save written, area transition, session start) | `info` |
| High-frequency / per-frame diagnostics (flag sets, lookahead skips, per-tick movement) | `debug` |

Rule of thumb: `error` = a developer needs to know this happened (recovered or not); `info` = the
normal story of a session; `debug` = only while debugging that subsystem.

## Tag and message

- Tag = the class's simple name, as a literal string (survives obfuscation, reads cleanly in static
  helpers).
- The message names the offending id/path and, for warnings, the fallback taken:
  `"no such knot '" + knot + "' in " + actId`. Pass the `Throwable` as the last argument when one
  exists. Bare `"failed"` or `"error"` is not acceptable.

## Level per build

The launchers set the level: desktop runs at `LOG_INFO` unless `-Dprpg.debug=true` is passed
(`./gradlew lwjgl3:run -Dprpg.debug=true` needs the property forwarded, or set it in the run task);
Android runs at `LOG_DEBUG` for debuggable builds and `LOG_INFO` otherwise. The level is a build
concern, not content: `config/game.yaml` ships in packs and a content pack must not be able to
silence error logging.

## Anti-patterns

- Per-frame logging at `error` or `info`.
- Log-then-rethrow at every level; log where the exception is finally swallowed (or once before a
  fail-fast rethrow that no enclosing handler logs).
- `error` for normal control flow ("no save present, starting a new game" is `info` or nothing).
- `debug` for anything a shipped build must surface; release runs at `LOG_INFO` and drops it.
- Raw `Gdx.app.*` calls; they reintroduce the null-guard footgun. The two `Gdx.app != null` checks in
  `ContentRoot` are platform detection, not logging.
