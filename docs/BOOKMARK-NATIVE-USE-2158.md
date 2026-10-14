# Native Use continuity: bookmark 45478 onward

**Subsequent user confirmation:** this correction fixed stopping/starting mid-run.
The separately reported startup delay and moving-click wait are addressed in
[the 23:59 follow-up](BOOKMARK-NATIVE-INTENT-2359.md), which supersedes the earlier
native-evidence-only startup policy for eligible target forecasts.

Session **1791974586944**, 2026-10-14, local timestamps **+11:00**.
The review starts with the movement marked by **21:58:10.246, #45478**,
including its preceding Use click, and covers the retained subsequent inputs
and bookmarks through **22:09:32.469, #79611**. Logs were read from RuneLite's
managed `plugin-data/responsive-movement` directory; no gameplay was automated.

## Recorded findings

| Marker / input | Native evidence and observed behavior |
| --- | --- |
| #45478 / click 45264, Use on Perdu | First authority/destination appears about **341 ms** after click, beyond the 100-ms input window. Confirmed-only pairs finish early; display holds about **83 / 111 ms** before later steps. |
| #45915 / click 45734, Use on player | First destination appears about **602 ms** after click. An approximately **80-ms** intermediate hold occurs before the final authority. |
| #46431 / click 46303, Use on GE clerk | First native publication is about **102 ms** after click, just outside the input window. Holds of approximately **83 / 76 ms** occur between later steps. |
| Click 46979, moving Use on clerk | No new native flag is published. Real authority first finishes the preceding Walk debt, then turns toward the interaction. The display holds roughly **283 ms** before that new authority; missing route/range evidence must not be replaced by a guessed NPC approach. |
| #47501 / click 47394, moving Use on clerk | The previous Walk preview continues until the new native endpoint/flag arrives about **117 ms** after click, then reconnects. A roughly **41-ms** intermediate hold follows. |
| Object Use clicks 56871 / 57210 / 57782 | Immediate flags and subsequent native refinements already use checked approach handling. These are retained as parity regressions alongside actor targeting. |
| #62560 / click 62384, minimap Walk then Use on clerk | The old eastbound preview continues for roughly **422 ms** before new northbound authority. A later confirmed-only pair produces an approximately **104-ms** hold. |
| #73931 / #79493 / #79611 | All reference Walk click 71589, already settled at `(6976,7104)`. Retained samples show no new input or intermediate stop/restart at these marker times. They do not establish a new movement bug. |

The Use actions in this recording arrive as **WIDGET_TARGET_ON_NPC/PLAYER**,
with option **Use**. Legacy ITEM_USE opcodes and Cast targeting are checked
separately with API doubles; they are not claimed to be additional recorded clicks.

## Shared correction

`NativeApproach` captures click-scoped world-view/plane, native destination
baseline and, for actor input, one directly indexed actor identity. It has no
position, route queue, or movement clock. It never infers item/spell reach from
the actor's coordinates. Ordinary NPC/combat captures keep their existing reserves
and evidence rules rather than falling through to an unreserved native approach.

`MovementController` now:

- Routes native-only actor/widget/player/world-entity destination refinements
  through the existing checked interaction joins and confirmed-prefix queues.
- Can accept a first late native destination only alongside **new checked player
  authority**, a changed destination baseline, valid target/view/plane, an eligible
  original click and its bounded **1.8-s** evidence lifetime. The input window is
  not extended; unchanged authority/flags cannot manufacture another click.
- Shares existing confirmed-publication pacing and bounded gait continuity across
  native-only world inputs, including walking and absent flags. This only paces
  already confirmed travel; it grants no speculative step or second clock.
- Cancels an obsolete Walk preview while native-only targeting has no new
  destination, preserving real confirmed debt and fractional position. A native
  input that supplies no route evidence can still legitimately wait for authority.
- Reconciles on native flag withdrawal, action/effect start, target invalidation,
  replacement input, scene changes and shutdown. Missing/blocked routes and expired
  predictions retain checked recovery. Final confirmed arrival remains exact.

Diagnostics use `observed-native-approach-authority` for successful late-evidence
route construction, `native-approach-invalidated` for target invalidation, and
`completed-native-approach` for generic native-only completion. Existing dedicated
NPC/object decisions remain distinct. The asynchronous trace schema is unchanged.

## Automated coverage

`NativeUseContinuityTest` adds **14 tests**, executing matrix/replay variants:

- **27 captured replays:** six idle Use/object timelines and three moving Use
  timelines with their preceding scene/minimap Walk, each at roughly 120/50/30 FPS.
  They check movement/turn budgets, retirement of obsolete previews, preservation
  of legitimate confirmed prefixes, intermediate holds and final true-tile alignment.
- **144 confirmed-input cases:** all **36 supported world-interaction opcodes**,
  walking/running, native flags present/absent, with responsive previews disabled
  to exercise the shared fallback independently of special approach geometry.
- **2,304 pacing cases:** every **0.1 speed increment from 0.5 through 10.0**,
  walking/running, 0/15/50% strengths and 8/20/33/100-ms cadences. Slow displays
  drain legitimate confirmed debt; none may invent travel beyond authority.
- **336 live-change cases:** all 21 native-only opcode variants, walking/running,
  four cadences and changes toward minimum/maximum speed/pacing settings. Repeated
  preparation at the same timestamp leaves position unchanged; arrival is exact.
- Native endpoint refinements and moving-target identity, first late publication,
  repeated preparation, missing/stale flags, expired/ineligible clicks, Control,
  disabled starts, blocked boundaries, confirmed corners, despawn/death/definition/
  plane changes, actions/effects, replacement Walk and scene/lifecycle cleanup.
  Use/Cast at native ranged stopping points retains primary action IDs/frames;
  repeated targeted input cannot renew unconfirmed travel from an unchanged flag.

Three initial focused checks fail against the pre-correction source: recorded idle
Use continuity, old-preview retirement and shared confirmed-input pacing.
The completed full `./gradlew build --offline --console=plain --no-daemon` passes
**777 tests, zero failures/errors/skips**. `git diff --check` also passes.

The two compact resources preserve recorded authority/destination timing and a
click-time collision crop (**X 45..68, Y 50..65**). Full collision maps agree across
the nine reviewed Use/object clicks; uncaptured cells are blocked. Native fractions
are held between retained observations. Actor/model behavior uses API doubles;
player-target coordinates and native intermediate route queues are not reconstructed.
These tests do not execute the game's renderer or establish every in-game interaction.

## User verification

Run `./gradlew run` (Windows: `.\gradlew.bat run`) with GPU or 117 HD, using
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
for development-client login. At the captured speed **5.9**, catch-up **15%**,
ahead slowdown **10%**, and arrival stages **0.2 / 50%, 1 / 49.8%**, check:

1. Use an item on an NPC and player from idle, then during scene/minimap walking
   and running, including when the destination appears late or never appears.
2. Check that intermediate server steps do not create repeated idle pulses and an
   old Walk preview does not continue away from the newly selected action.
3. Recheck item/spell targeting, ordinary player options, object/ground-item actions,
   blocked approaches and rapid replacements. Change speed/pacing while moving.
4. Recheck Bank/Exchange, combat/Walk release, Follow, arrival gait, scene transitions
   and disable/re-enable. Native actions must retain their own timing and range.

Only the user performs gameplay. **The mid-run correction is now user-confirmed.**
Earlier user-confirmed fixes remain the baseline; this report records the newly
supplied native-Use failure and its scoped correction rather than reopening history.
