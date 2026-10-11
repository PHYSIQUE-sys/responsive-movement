# Standalone in-game validation

Build and launch from **C:\Projects\ResponsiveMovementPlugin**:

```powershell
.\gradlew.bat build
.\gradlew.bat run
```

`run-project.cmd` launches this same project. Use the
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
guide for development-client login. Only the user performs gameplay.

Enable **Responsive Movement**, with GPU or 117 HD. The previous **True Tile
Movement** plugin must be disabled; the new plugin declares that conflict.

## Current status (supersedes dated pending notes below)

**2026-10-11 profile-list top-spacing follow-up: implemented; 671 tests pass; awaiting confirmation of the spacing correction.**
The user reports the compact sidebar is good, with the only reported problem being
roughly two profile rows of empty space above their three saved profiles. The empty-state
text was hidden, but its scroll-pane column-header viewport still reserved height.
The populated list now removes that viewport entirely, putting the first row directly
inside the top border. An empty list restores the explanatory message normally.
The existing layout regression now checks the exact first-row position with three
profiles and empty/populated transitions. Full
`.\gradlew.bat build --offline --console=plain --no-daemon` passes **671 tests,
zero failures/errors/skips**. The sidebar's preceding user-confirmed behaviour is
the baseline; this small spacing change awaits its own visual check. Launch
`.\gradlew.bat run` using
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
and confirm the three profiles start at the top of the list without the blank gap.

**2026-10-11 compact profiles-only sidebar: user-confirmed working well apart from the top-spacing issue addressed above; 671 tests passed.**
The user explicitly requests a sidebar containing a section/list of profiles, with
Load, Save and Delete at the bottom. Selecting a row must not apply a profile. Load
must show the standard-settings refresh reminder, Delete must ask for confirmation,
and the panel should fit RuneLite's styling. This supersedes the earlier native-only
UI request and the rejected full duplicated-settings sidebar below.

The new panel uses dark RuneLite colours, a padded single-selection Profiles list,
count/empty state, and bottom-aligned Load/Save/Delete buttons. Load submits a guarded
client-thread batch through ConfigManager, immediately updating the live settings.
Its visible message states that the plugin settings page may need reopening to
refresh displayed values and that no game restart is needed. Save prefills the last
loaded/saved name and supports same-name updates or new saves; Cancel makes no writes.
Delete defaults to Cancel and requires "Are you sure?" confirmation, preserves the
applied settings/other saves, and clears the loaded-name metadata only when deleting
that entry. Existing schema-1 saves remain readable. Debugging keys stay independent.

Lifecycle/scope guards reject queued actions after shutdown or RuneLite configuration
profile changes, including when a modal dialog was open. Nine new regressions verify
selection versus explicit loading and its visible notice, prefill/overwrite/Cancel,
delete confirmation/cancellation/live-setting preservation, stale action/dialog guards,
invalid loads with no partial writes, and list/empty-state/button layout. All earlier
profile, pacing and movement checks remain. Full
`.\gradlew.bat build --offline --console=plain --no-daemon` passes **671 tests,
zero failures/errors/skips**. These API-double and headless Swing tests do not establish
native RuneLite look-and-feel or in-game visual continuity by themselves.

User check: launch `.\gradlew.bat run` using
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).
Open **Responsive Movement profiles** (stacked-cards icon), save two different settings
profiles, and select each without loading: game/settings must not change. Press Load
while walking/running; check immediate settings application and continuous position,
then back out of/reopen standard Responsive Movement settings to see refreshed controls.
Check the visible refresh/no-restart notice. Save again and verify name prefill,
Cancel preservation and same-name update. Test Delete's Cancel/confirmation on loaded
and other profiles; movement settings must remain applied. Disable/re-enable/restart
for persistence and toolbar/dialog cleanup, and switch RuneLite configuration profiles
for scope. Judge panel readability, list scrolling and bottom buttons in the actual
client. Only the user performs gameplay. The user subsequently confirms the panel is
good apart from the reported spacing, addressed by the follow-up above; individual
scope/lifecycle variants are not separately established by that general report.

**2026-10-11 rejected full sidebar removed: historical 662-test checkpoint; superseded by the requested compact panel above.**
The user rejects the separate sidebar and reiterates the exact layout: a named
profile dropdown at the top of the existing plugin settings, Save underneath,
an explicit Load button, and a Save dialog prefilled with the loaded name. They
also report that choosing a profile did not visibly update the standard settings.
The previous sidebar and its toolbar/dialog/config-refresh wiring are removed.
Previously saved ConfigManager profile data is preserved; the backend is not wired
to an active UI. No native dropdown/Save/Load feature is claimed complete.

The blocker is a RuneLite settings-panel API limitation: the native configuration
screen exposes fixed enum selectors but no supported dynamic named-profile/button
extension. The project prohibits reflection and unsupported UI modification.
Implementing the requested native controls requires supported RuneLite settings-panel
extension capability; another sidebar is not an accepted substitute.

Debugging remains at the bottom of the standard settings with stable persisted keys.
The manual bookmark hotkey remains available while recording; its old sidebar button
is removed. It submits timestamped value-only evidence through the existing ordered
asynchronous Filepath writer without movement/model preparation or prediction renewal.
The earlier 666-test build included four tests for the now-deleted sidebar; the current
build passes **662 tests, zero failures/errors/skips**. Preserved profile backend tests
are not evidence of functioning native UI loading or control refresh.

User check: launch `.\gradlew.bat run` using
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).
Confirm the extra sidebar is absent after reloading this build, Debugging is last in
the standard settings, and the configured hotkey produces one trace bookmark per
press while recording. Stop recording and check bookmark timestamps/feedback. Native
profile controls remain an implementation blocker, not an in-game validation task.

**2026-10-11 code-review fixes A–E: user-confirmed working well for the tested situations; 651 tests passed.**
The user subsequently reports **"all those fixes seem to be working well"**, superseding
the review fixes' initial pending status. The user chooses native overhead handoff for
ambiguous crowded hitsplats and confirms the resulting behaviour along with A/B/C/E.
The latest preceding user report says everything is working well, including native
Walk observation and startup timing; the reported build passed **652 tests**. That
confirmation is newer than older pending notes below. Source scope matters: the
checkout reviewed here contains the earlier 639-test suite and lacks that reported
native Walk observation implementation/tests. This build verifies those 639 checks
plus 12 new regressions; it does not reproduce the separate reported 652-test source.

- **A:** Movement speed retains fine precision after conversion: 5.1 is 1.02×,
  5.4 is 1.08×. Construction, forecasts, live changes and diagnostics no longer
  round the multiplier to 0.1. Clamp/fallback behaviour is retained.
- **B:** Horizontal camera following uses exact exponential decay calibrated to
  its former 60-FPS response, with double-precision retained world focus. Cadence,
  repeated preparation, scene-rebase and native camera-mode checks pass.
- **C:** Head overhead projection uses the actual world view. Supported Perspective
  projection/raster checks cover a sub-world view, missing projection and mismatch.
- **D:** Four custom hitsplat slots remain stable. At ambiguous overflow, native
  overhead rendering selects the actual visible hits at the native player position,
  as chosen by the user. Custom body movement continues; the entire custom UI stays
  suppressed until all potentially visible burst hits expire. Slot, expiry, cleanup
  and renderer-publication checks pass. Supported APIs expose no native slot or
  replacement-priority field, so custom rendering does not guess overflow winners.
- **E:** First combat engagement cannot revive a click at/after its 1.8-second
  expiry when preparation skips frames. Engagement observed before expiry retains
  its existing lifetime/invalidation rules. Boundary and controller tests pass.

The full `.\gradlew.bat build --offline --console=plain --no-daemon` command
passes **651 tests, zero failures/errors/skips**. Historical pace assertions that
depended on quantization now use exact timing; knight/circle replays retain both
the old effective pace's geometric checks and corrected fine-pace coverage.
These tests do not execute RuneScape's native renderer or establish visual feel.

User check: launch `.\gradlew.bat run` with GPU or 117 HD using
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).
Compare speed 5.0/5.1/5.2/5.3/5.4 while walking/running, including live changes,
circles/knights and true-tile pacing. Compare camera follow at different FPS and
through region changes, then zoom, minimap and right-click input. Check overhead
alignment in an actual non-top-level world view if available; missing projections
should not draw in the wrong location. Check ordinary and crowded hitsplat bursts:
stable custom slots, native-UI handoff without duplicate UI, and custom UI resumption.
Recheck combat engagement/Walk escape and plugin disable/re-enable. Only the user
performs gameplay and confirms these changes in-game.

**2026-10-09 ahead slowdown, pacing section and true-tile highlight: implemented; 639 tests pass; awaiting in-game confirmation.**
The user reports **"thats working well"** for the previous catch-up addition,
then requests equivalent slowdown ahead of authority and dedicated config sections.
**Movement pacing** now groups both independent toggles and strengths; existing
catch-up keys/group remain unchanged. **Slow down ahead of true tile** defaults
on with **Ahead slowdown (%)** at 10 (0–50). Checked agreed previews progressively
reduce base displayed speed over the first tile ahead of true-tile progress.
Confirmed travel retains catch-up; stale reversal/awaiting-origin gaps, combat
tracking, rejection recovery and close NPC startup easing retain their own policies.
Exact integration spends the same single frame budget, including leftover time
across a confirmed-to-preview boundary. Prediction geometry/credit/deadlines,
native primary actions/models and capped facing remain unchanged.

The independent **True tile** section adds **Show true tile** (default off),
**Highlight tracking**, alpha-capable fill/border colours, **Border thickness**
(0–10 px, default 2) and **Edge feather (px)** (0–20, default 0). Default **Server
true tile** uses native `getWorldLocation()`, matching the authoritative tile even
when the plugin's display differs. Server running updates can still skip a tile.
Optional **Native movement tiles** samples the hidden native player's local
position each overlay frame and highlights its containing tile, allowing observed
intermediate run tiles. It is native interpolation rather than server authority;
no missing route is reconstructed or custom display position substituted. Low
render cadence/loading/teleports can still skip observed intermediate tiles.
The overlay is stateless and independently registered/removed with the plugin;
feathering uses bounded screen-space bands with isolated graphics state.

Eleven new pacing tests cover bounded/zero strengths, walking/running, exact cadence
parity through corners/knights, partial confirmed chords, shared catch-up/slowdown
boundary timing, stale reversals, timeout/recovery, rebasing, repeated preparation,
combat isolation and live scene/minimap toggles. Nine highlight tests cover server
versus native/display identity, intermediate running tiles, view/plane/bounds checks,
colour/opacity/width/feather raster output, graphics-state isolation and section/key
stability. Two further capture tests replay the circle and marked-knight sequences
at three cadences with both adjustments set to 10% and 50%.
`.\gradlew.bat build --offline --console=plain --no-daemon` passes **639 tests**,
zero failures/ignored tests, including all 617 previous checks. These API doubles
and AWT/replay tests do not execute the native renderer or establish visual feel.

User check: launch `.\gradlew.bat run` with 117 HD or GPU using
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).
Compare ahead slowdown off versus 10/25/50% during early starts and longer
walking/running. Check gentle response and no added snap/retrace at server catch-up,
then recheck circles/knights, scene/minimap clicks and interactions/combat/Follow.
Enable the true tile with server tracking and compare an authoritative indicator
while display leads/trails it. Try both tracking modes while running to observe
native intermediate tiles, then walking, teleports, region/view transitions and
native fallback. Adjust fill/border opacity and colours, border 0/2/10 and feather
0/5/20, and disable/re-enable the plugin. Only the user can confirm in-game rendering;
report any remaining symptom with a trace timestamp and UTC offset.

**2026-10-09 configurable true-tile catch-up: user-confirmed working well for the tested situations; 617 tests passed at that checkpoint.**
The user subsequently reports **"thats working well"**. This supersedes its initial
pending runtime status; the new slowdown/highlight above require their own checks.
Movement now offers **Catch up to true tile** (default on) and **Catch-up speed
boost (%)** (default 10, range 0–50). A positive strength gently speeds up queued
confirmed travel, tapering over its last tile. The percentage is relative to the
configured walk/run pace; off or zero restores the earlier pacing immediately
without resetting displayed position. MovementPath retains its one clock and
checked route, with no new deadline, forecast distance or prediction credit.
Speculative travel, pending reversals, unconfirmed chord endpoints and rejection
recovery keep their prior pace. Slow melee pursuit and the continuous-run final
100-ms reserve retain their existing easing.

Seventeen new pacing/controller checks cover bounded strengths, walk/run speed
scaling, taper/arrival, frame-splitting independence through corners/knights/local
curves, live toggle changes, confirmed-prefix to speculative-tail frame accounting,
reversals, partial confirmation, timeout, closing collision, rebasing, repeated
preparation and melee-trail parity. A sustained native-run-pair double demonstrates
reduced accumulated lag at the default boost. Two additional capture tests replay
the circle and all 81 marked-knight clicks/pauses at 8.333/20/33.333-ms cadences
with both 10% and 50%, checking arrivals, held stops and single movement/turn
budgets. Earlier timing fixtures explicitly retain disabled catch-up to preserve
their original base-rate assertions. These are API-double/replay checks, not
native renderer execution or in-game visual confirmation.

`.\gradlew.bat build --offline --console=plain --no-daemon` passes **617 tests**,
zero failures/ignored tests, including all 598 previous checks. Click diagnostics
now record both catch-up settings; their effective speed remains the base pace.

User check: launch `.\gradlew.bat run` with 117 HD or GPU using
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).
Display a true-tile indicator and compare longer walking/running at your normal
Movement speed with catch-up off, then on at 5/10/25/50%. Check that trailing
movement closes the gap gently, stays on the checked route and settles without
an overshoot or arrival snap. Change the toggle/strength mid-run, then recheck
scene/minimap replacements, reciprocal knights/circles, objects/pickups/NPCs,
combat/Walk escape, Follow and region crossings. Record a trace and report the
local timestamp with UTC offset for any uneven catch-up. Only the user confirms
the visual feel; the preceding marked-pause correction also retains its pending
runtime status below.

**2026-10-08 23:33 marked knight handoffs: implemented; 598 tests pass; awaiting in-game confirmation.**
The user reports **"the running in circles is a lot better now"** and that most knight
back-and-forths work really well, then supplies session `1791462766789`,
**23:32:46.789–23:33:47.462 (+11:00)**. It contains 81 Walk clicks and five substantial
post-movement pauses at **23:32:59.062 / 23:33:05.002 / 11.644 / 19.964 / 44.480**.
The two-second neighborhoods around every pause were reviewed. Exact right-button
presses are not recorded; pauses are markers, not reconstructed menu events.
Settings are **speed 5.4 / turn 30 / smoothing 0 / Original player when aligned off**.

Near **23:32:58.483**, **23:33:18.903** and **23:33:43.420**, late authority still names
the preceding endpoint, so the old knight handoff can choose an intermediate staging
corner. A fresh click now also considers the nearby occupied leg's forward endpoint
as a checked whole-knight construction origin, retaining the same authority-gap and
reversible recovery proof. It does not prefer a backstep through the leg's old start.

At **23:33:10.600–10.701**, authority alignment briefly sends display back toward the
old origin before continuing. Where the old and new proven convex corridors share a
valid crossing, the visual join now passes straight through it instead. Both queue
subsegments retain their own collision proof, and the shared endpoint can be sub-tile.
Unavailable overlap/blocked alternatives keep checked alignment or corners.

The **23:33:03.362** visible-idle return click also inherits confirmation because its
new goal already equals the old true tile; the delayed **03.403** endpoint then adds a
return chord. `pendingWalkConfirmation` now shares the already-true-goal rule between
moving replacements and fresh idle scene/minimap Walk starts. It grants no extra time
or movement credit, and normal matching authority/timeout/recovery remain decisive.

Eight new tests cover all 81 observed clicks at **8.333/20/33.333-ms** cadences, holding
each marked stop, no retrace in the late-origin window, pending idle returns, full-chord
construction, scene/minimap parity, blocked alternatives, closing incoming collision,
rotated/reflected geometry and rebasing. The three initial focused checks fail before
the correction and pass after it. The full
`.\gradlew.bat build --offline --console=plain --no-daemon` passes **598 tests**, zero
failures/ignored tests, retaining all 590 prior checks. Click-time collision crops agree
across all 81 clicks; native fractions are held between retained publications, unknown
cells are blocked, and native rendering/menu presses are not executed. See
[the checkpoint](agent-work/marked-knights-2333/CHECKPOINT.md).

User check: launch `.\gradlew.bat run` with 117 HD or GPU using
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).
Repeat the marked sequence at the recorded settings: alternate both knights near
arrivals, stop on odd movement and mark it with right clicks. Check no tiny rollback,
smooth straight chord travel, and holding the latest tile. Include idle return clicks,
minimap, walking, walls, scene transitions and existing interactions/combat/Follow.
Only the user establishes visual feel. Record a trace and report any remaining local
timestamp with UTC offset; this newest correction awaits its own confirmation.

**2026-10-08 22:29/22:37 repeated knight and marked-circle follow-up: user-confirmed improved, with remaining cases reported; 590 tests passed at that checkpoint.**
The user subsequently reports circles are **"a lot better"** and that most knight
back-and-forths work **"really well"**. This supersedes the blanket pending status;
the separate remaining handoff/idle-start cases are addressed by the newer entry above.
The user reports the preceding circle-continuity change **"seems to be working well"**,
then supplies two newer sessions. The second newest is `1791458898008`, **22:28:18.008–
22:29:58.956 (+11:00)**, with circles, marked pauses and repeated knights at its end.
The newest is `1791459413609`, **22:36:53.609–22:37:23.975**, with repeated reciprocal
and alternating knights. Both use speed **5.4**, Turning speed **30**, smoothing **0**
and Original player when aligned **off**. Right-button menu-opening presses are not
recorded as clicks; their exact timing is not reconstructed.

At **22:29:53.416**, **22:37:06.657** and **22:37:19.876**, late preceding-click
endpoints are appended behind fresh reversals, producing the extra displayed return
trip. The stale-endpoint guard had required collinearity with the first logical step;
a knight's endpoint lies off that cardinal line. It now uses the whole latest route's
direction, preserving the checked forecast and original bounds through opposite stale
progress. Real authority still updates; checked forward logical/construction-origin
progress confirms normally, including necessary detours away from the geometric goal.

Eligible fresh knight clicks now retain the complete authority-anchored, both-order
checked chord when its proven corridor contains the display. A fraction outside it
may finish only a proven connector within a quarter tile of authority, then traverse
that same chord. This removes the captured cardinal/diagonal staging split and its
uneven-looking pacing. Curve facing also follows a monotonic blend between its intended
incoming/terminal headings, avoiding overshoot-and-correction sweeps; the true spatial
derivative remains available for continuity. Facing stays under its single turn cap.

Eleven new tests cover both knight timelines and a marked circle segment at
8.333/20/33.333-ms cadences, no extra return debt, general knight reversal directions,
whole-chord handoffs, monotonic curve-facing, timeout/recovery, matching authority,
rebase/collision, run/both-order guards and genuine detour confirmation. The initial
four positional checks and the curve-facing guard fail before their corrections.
Native fractions are held between retained events and model/facing state uses API
doubles. The collision crops agree across all 226/27 recorded clicks; unknown space
is blocked. These replays do not execute the native renderer or prove every click pattern.
The full `.\gradlew.bat build --offline --console=plain --no-daemon` passes **590 tests**,
zero failures/ignored tests, retaining all 579 prior checks. See
[the checkpoint](agent-work/repeated-knights-2237/CHECKPOINT.md).

User check: launch `.\gradlew.bat run` with 117 HD or GPU using
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).
Repeat the marked circles/pauses and reciprocal/alternating knights at the recorded
settings. Stop after the final click: check direct arrival at the latest target without
an extra out/back, steady complete knight travel and no unnecessary left/right sweep.
Include other reversals, walking, walls, scene/minimap, interactions, combat and Follow.
Only the user confirms visual feel. Record a trace and report any remaining timestamp
with UTC offset. The user subsequently confirms improvement for the tested situations;
the remaining marked-pause cases and their latest correction are described above.

**2026-10-08 21:43 spam-click circle continuity: user-confirmed working well for the tested situations; 579 tests passed at that checkpoint.**
The user subsequently reports **"that change seems to be working well"**. This supersedes
the initial pending runtime status; the separate follow-up above addresses the newer cases.
The capture at that checkpoint is session `1791456214521`, **21:43:34.521–21:43:55.575
(+11:00)**. It contains 48 scene Walk clicks, speed **5.4**, Turning speed **25** and
Walk-click smoothing **0**. Several fractional joins/short connectors change travel
direction abruptly (nine sampled heading changes above 35 degrees, including about
72 degrees). The inspected sequence has no large per-sample positional budget outlier;
the trace does not record the final native renderer's bones or camera perception.

Fresh Walk candidate selection now favours a checked forward tangent within the
existing one-tile travel allowance. Eligible fractional Walk joins can use a short
cubic blend wholly inside an already proven reversible knight/diagonal corridor.
Both controls and the blend endpoint stay inside its convex region and forecast gap;
the curve spends exact max-axis arc length from MovementPath's existing frame budget,
then rejoins the same leg. Logical routes, authority, deadlines and checked recovery
remain in that pipeline. Reversals/unavailable geometry retain their checked connectors.
MovementFacing also eases the last few degrees under the existing turn cap.

Seven new checks cover the full click/authority/publication sequence at
8.333/20/33.333-ms cadences, aggregate travel continuity, fractional curve bounds,
repeated clicks, cadence-independent curve pacing, active-curve scene translation,
collision rejection/recovery and facing settlement. At the 20-ms replay grid, the sum
of squared frame-to-frame velocity changes falls from **6.0925 to 4.83** (about 21%).
That is an aggregate replay measure, not a claim that every turn or largest reversal
is improved. The capture check fails with the continuity correction disabled and passes
with it enabled. Native fractions are held between retained authority/destination
events; unknown collision cells are blocked. The replay does not execute RuneScape.
The full `.\gradlew.bat build --offline --console=plain --no-daemon` passes **579 tests**,
zero failures/ignored tests. See [the checkpoint](agent-work/circle-2143/CHECKPOINT.md).

User check: launch `.\gradlew.bat run` with 117 HD or GPU using
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).
Repeat the circle first at speed 5.4, turn 25, smoothing 0, then try smaller circles,
sharp reversals, scene/minimap replacement clicks, walls and final arrivals. Recheck
ordinary object/NPC approaches, combat, Follow and region crossings. Record a trace,
stop recording to flush, and report the local time/UTC offset of any remaining kick.
Only the user confirms in-game visual feel. This circle follow-up is now user-confirmed
for the tested situations; its newer repeated-knight/facing refinement above remains pending.

**2026-10-08 19:49 refinement continuity and tick-aware smoothing: user-confirmed working well for the tested situations; 572 tests pass.**
The user subsequently reports **"that change worked well"** for the stop/start
correction and tick-aware 0–300-ms smoothing follow-up. This supersedes their pending
runtime status. The preceding arrival-facing option is also user-confirmed working
well. These reports confirm the tested behavior; broader scenario checks below remain
useful regressions.
The latest session (`1791449319927`, ending **19:49:07.962 +11:00**) contains repeated
Use on the same object. At **19:49:00.825**, the native anchor `(8384,4544)` refines to
the actual approach `(8384,4416)` as authority advances to `(9024,4416)`. A repeated
click at **00.841** republishes the blocked anchor while the displayed position still
owes confirmed movement. The old refinement connector cannot replace that prefix,
so it cancels the speculative tail. Display stops at `(9024,4416)` from **01.041**
until **01.442**, then resumes on the next server endpoint; shorter run/idle gaps follow.
There is no primary action or effect in those frames.

Native approach refinements now queue a collision-checked replacement forecast behind
unfinished confirmed debt, preserving its fraction, geometry and the original response/
chain deadlines. Object-style blocked anchors retain adjacent search; ordinary ground
items retain exact-goal search, and NPC footprints/reserves remain scoped. Unsupported
connectors, timeout and collision changes still reconcile through the existing pipeline.
The captured ending and prefix test both fail before the correction and pass after it;
the ending runs at 8.333/20/33.333-ms cadences. Five additional guards cover knights,
exact-vs-adjacent goals, repeated refinement expiry, expired fallback rejection and
NPC/rebase continuity. The occupied-edge fallback also retains both original deadlines.

**Walk-click smoothing (ms)** now ranges from **0–300**, default **50**. For a nonzero
setting, the next game tick ends the wait early; otherwise the timer releases it.
The description explicitly states this. Scene and minimap share that rule, with normal
native destination evidence, first-publication latching, input gates, deadlines and
one render-frame movement budget. Zero and red-click timing retain their existing
handling. Five new input tests and four controller checks cover timer/tick ordering,
walking/running parity, native authority, stale flags, replacement, expiry and gates.

`.\gradlew.bat build --offline --console=plain --no-daemon` passes **572 tests**, zero
failures/ignored tests, including all 556 previous checks. The capture replay retains
actual click/destination/authority timing and collision columns X=63..74/Y=30..40;
unknown space is blocked and native fractions are held between retained samples.
It does not execute the native renderer or establish every object's interaction reach.
See [the checkpoint](agent-work/object-refinement-1949/CHECKPOINT.md).

User check: launch `.\gradlew.bat run` with 117 HD or GPU using
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).
Repeat the final object Use approach and repeated clicks at speed 6.0, smoothing 60,
arrival-facing on, and Original player when aligned on. Check no intermediate stop/run
pulse or retrace. Include other object/NPC approaches, exact pickups, doors and combat.
Then test smoothing 0/50/150/300 with scene/minimap clicks just before a tick: a tick
should release a nonzero wait early, with no snap or duplicate movement. Only the user
performs gameplay; record a trace and report local time/UTC offset for any remaining case.

**2026-10-08 interaction arrival-facing option: user-confirmed working well for the tested situations.**
The user reports **"thats working well"** before requesting the separate stop/start
and smoothing follow-up above. This supersedes the option's earlier pending status.
Movement → **Face interactions on arrival** is off by default. Its description is
"Instantly turn to NPCs/objects the moment you arrive at them". When enabled, turning
begins on the displayed arrival frame using the existing Turning speed cap, before
the native player/tick finishes catching up. Click-scoped NPC/immutable object evidence
survives destination withdrawal and ordinary arrival actions, without owning a route
or changing native actions/frames. Distant forecast exhaustion and recovery do not
qualify; input, target, scene, toggle and lifecycle invalidation release ownership.
Ground-item, combat and explicit Follow policies retain their existing behavior.

Eight new API-double controller checks cover 8/20/33-ms arrival cadence, single-turn
budgets, option-off parity, single-/multi-tile objects, late actions/flag withdrawal,
native handoff, Walk cancellation, invalidation/cleanup and distant forecast recovery.
The full `.\gradlew.bat build --offline --console=plain --no-daemon` passes
**556 tests**, zero failures/ignored tests. These checks use synthetic target/native
timing; they do not replay the reported evening logs or execute the game renderer.
The user's arrival-facing confirmation is now part of the runtime baseline; the newer
refinement/smoothing changes above are also user-confirmed working well.

User check: launch `.\gradlew.bat run` using
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).
Enable the option and approach offset NPCs, trees, single-tile objects and doors/gates;
check the turn starts at visible arrival and blends smoothly from travel into facing.
Try walking/running, different Turning speed values, late native action starts,
repeated clicks and Walk/minimap cancellation. Compare option off and recheck combat,
Follow, region crossings and plugin disable/re-enable. Only the user performs gameplay;
record a trace and report local time/UTC offset for any remaining issue.

**2026-10-08 01:32 route/action continuity: implemented; 548 tests pass; awaiting in-game confirmation.**
The latest trace (`1791383551670`, 01:32:31.670–01:33:37.056 +11:00) shows
solid-object previews choosing a geometrically near side that requires an unnecessary
southern leg, then reversing when the exact native approach arrives. Rectangular
object staging now selects a reachable cardinal perimeter using the existing bounded,
reversible MovementRoute search. Native destinations outside the footprint remain exact;
this does not establish permitted interaction sides or universal interaction reach.

Walk click 1388 at 01:32:59.136 also starts correctly, but a delayed native action
at 59.479 retires its route agreement. The forecast stops at `(6080,5952)` from
01:33:00.274 to 01:33:01.280 despite matching forward authority. An eligible explicit
Walk now retains input ownership through delayed non-location actions, including during
destination observation/smoothing, without changing native primary actions or granting
new prediction time. Missing/unchanged destination evidence, unrelated effects, native
location actions, replacement world input, scenes, disabled starts and cleanup retain
their gates. A newly clicked Walk during an existing non-combat action remains conservative.

Both reported failure categories fail replay assertions before the fix and pass after it
at 8.333/20/33.333-ms cadences. Twelve new replay/geometry/lifetime guards plus all 536
earlier checks pass: **548 tests, zero failures/errors/skips**. The prior 117 HD user
confirmation remains the runtime baseline; this correction needs its own user check.
Launch `.\gradlew.bat run` using the
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
guide. At speed 5.8 and smoothing 60 ms, repeat the northern/offset tree approaches and
Walk away just before an action arrives; check no extra detour, reversal or intermediate
run stop. Include minimap Walk, other rectangular objects and ordinary combat/door
regressions. Record a short trace and report the local time/UTC offset of any remaining
case. Only the user performs gameplay. See
[the 01:32 checkpoint](agent-work/early-0132/CHECKPOINT.md) for evidence and test limits.

**Filepath review follow-up: managed diagnostics implemented; 536 tests pass.**
Startup now passes `getPluginDirectory()` to the asynchronous trace writer. All
filesystem operations use the managed Filepath capability; the descriptor sets
`legacyDataDirectory="responsive-movement"` so RuneLite can migrate the former
folder to `.runelite/plugin-data/responsive-movement` on first recorded write.
Resolution and migration stay off the client thread. The new isolated writer
checks cover disabled/empty recording, off-thread resolution and ordered flush,
bounded archive rotation, and recovery after directory errors.
The full build passes **536 tests**, zero failures/errors/skips, including all
532 earlier regressions and four new writer checks. The packaged BSD license,
icon, metadata, and user/reviewer documentation links also pass verification.

User check: launch `.\gradlew.bat run` using the
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
guide, record a short trace, stop it, and confirm new lines in the managed folder.
If the old folder exists and the managed folder does not, check the existing logs
move together. If both exist, RuneLite preserves the old folder without merging.
Confirm plugin disable/re-enable still flushes normally. This follow-up changes
diagnostic ownership/location and requires the user's filesystem/runtime confirmation;
the previous 117 HD movement confirmation below remains the tested baseline.

**Publication preparation (2026-10-05): automated checks pass; user confirms expected runtime behavior.**
After the preparation changes, the user reports **"everything seems to be working
how it should"**, and identifies **117 HD** as the renderer used. This confirms
the behavior they tested in-game. Settings and individual scenarios were not
specified; the report does not independently confirm GPU or every historical edge case.
The user also reran `.\gradlew.bat build --console=plain --no-daemon` and supplied
a **BUILD SUCCESSFUL** result: all six tasks were up-to-date, reusing the existing
successful compile, package, and test results.

The default build now follows RuneLite `latest.release`; the current Hub target
and inspected client/API/injected-client/jshell dependencies are **1.13.1**.
The complete build passes **532 tests**, zero failures/errors/skips, including
three new cleanup regressions for expired effects/native handoff, the fixed
optional orb's removal, and GPU loss between render and tick.
An isolated standard-build check also confirms Java 11 production-only packaging,
all 35 overhead PNGs, and the full BSD notice. See
[PUBLISHING.md](PUBLISHING.md) for the review and runtime test scope.

Recommended regression checks: GPU off/on, plugin disable/re-enable, repeated spot effects,
marker off/alignment/teleport, and diagnostic recording in the existing folder.
The hidden arbitrary marker-model settings have been retired in favor of the
original fixed orb. Normal persisted settings remain available. Older pending
gate/region/minimap/interaction notes below retain their scenario-specific evidence;
the user's general confirmation is the latest overall status.

**Explicit player/NPC Follow continuity: user-confirmed working well for the tested situation.**
Session `1791193390639`, Follow click **373** at **2026-10-05 20:43:18.079 (+11:00)**,
uses player index 623, speed **5.4**, smoothing **0**, Original player when aligned
**off**. No native Walk destination appears. Initial authority advances from local
`(6208,8000)` to `(5952,8256)` at **18.279**, then `(5952,8384)` at **18.878**.
Subsequent authority alternates those one-tile endpoints about every 600 ms.
The custom path retains running pace, arrives in about 300 ms, and publishes idle
while native motion is still catching up. At **19.178**, for example, display is
`(5952,8384)` but native remains `(5952,8308)`; the next reversal arrives **19.478**.
The capture contains player Follow, not a quest NPC or live per-frame target evidence.

Explicit Follow now retains only the selected player/NPC identity and native
engagement. At an exact positional and compatible facing boundary, rendering hands
off to the original player and stays native for that Follow, including with aligned
native handoff disabled in config. An already aligned click may transfer before its
first native step. Moving input first cancels obsolete prediction through the checked
shared path and preserves confirmed debt/fraction until alignment. Native Follow owns
its subsequent gait, turning and endpoints; Walk/other world actions and target/scene
invalidation release that ownership. Repeated pending Follow does not extend its 1.8-s
engagement window. Ordinary Talk-to/Trade/Attack do not qualify.

Verification: five of six initial controller regressions fail before the correction.
The final **12-test** suite covers player/NPC parity at 8/20/33-ms cadences, recorded
endpoint geometry with representative native fractions, delayed engagement, moving
confirmed-prefix handoff, position/facing gates, repeats/expiry, cancellation from
native fractional movement, target/lifecycle invalidation, option isolation and native
selector/body/effect cleanup. Engagement/quest-NPC behavior, intervening native
fractions and open collision are explicit API doubles; this is not a full renderer
replay. `.\gradlew.bat build --offline --console=plain --no-daemon` passes **529 tests**,
zero failures/errors/skips, including all previous 517 regressions. The user
subsequently reports **"that change worked well"** for the tested Follow behavior;
quest-NPC Follow and other variants are not separately confirmed.

User check: launch `.\gradlew.bat run` with GPU or 117 HD using the
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
guide. Start at speed **5.4**, smoothing **0**, Original player when aligned **off**,
with tracing. Follow a player through short reversals, stops and longer runs; optionally
try an NPC's actual quest **Follow** option. Check no added run/idle pulse or handoff
snap, including a Follow selected while already moving. Walk/minimap away, then
recheck combat and ordinary object/NPC interactions. Native pacing applies while
Follow owns presentation. Disable tracing to flush and report local time/UTC offset
for any remaining case. Only the user performs gameplay. See
[the Follow checkpoint](agent-work/follow-2043/CHECKPOINT.md).

**Bank-counter accessible-side corner arrival: user-confirmed working well for the tested situation.**
Session `1791188705958` contains one recorded click, Bank at **2026-10-05
19:25:13.446 (+11:00)**, sequence **375**. The clicked banker is at world
`(3163,3489)`; speed **5.4**, smoothing **0**, Original player when aligned **off**.
The click starts at world `(3161,3485)` / local `(6336,7872)`. At **19:25:14.180**,
authority advances one run pair to `(3161,3487)` / `(6336,8128)` and never advances
again in this approach. No native destination, primary action or effect is published.

The counter forecast correctly retains the first pair but assumes an additional
pair to `(3162,3489)` / `(6464,8384)`, reaching it at **14.625**. It waits there
until recovery starts at **15.064**, then returns to the actual stop by **15.643**.
The earlier rounded whole-pair stopping envelope excludes this `(2,2)` corner,
although the newer fresh-click boundary already recognizes that it can interact
without moving. The inconsistency is in counter arrival prediction; this capture
does not implicate the separate gate or combat/model corrections.

The existing one-sided counter plan now retains an accessible-side corner reserve
at a whole-run-pair endpoint. It keeps the same ordered logical route and separate
native flag, but caps the forecast at that corner. A matching newly observed native
flag can release the checked remainder at normal pace with the same fraction/clock
and original deadlines. Real onward authority still confirms or appends movement
normally. Corners on the blocked side retain the earlier rounded route, and fresh
clicks already inside the two-tile square retain the no-invented-start gate.
Scope remains Bank/Exchange and cache-qualified bank-service Talk-to at the existing
one-sided geometry. No NPC-ID/world-coordinate rule or new movement pipeline is used.

Verification: the recorded arrival and no-authority guard fail before the fix.
`CounterCornerArrivalTest` adds **10 tests**: the Bank capture at **8.333/20/33.333 ms**
with synthetic Exchange/service Talk-to parity; late/immediate native release,
actual onward authority, timeout, rotated geometry/blocked-side isolation, collision
and unchanged deadlines, queued confirmed-prefix preservation, moving fractional
handoff, and rebase/snapshot continuity. The fixture copies actual collision columns
X=46..55, click/authority timing and sampled native points; unknown space is blocked.
Native fractions between samples are held by API doubles. It does not execute the
game renderer or establish a universal counter reach formula.

The focused counter suites and full
`.\gradlew.bat build --offline --console=plain --no-daemon` pass **517 tests**, zero
failures/errors/skips, retaining all 507 prior regressions including the **57** GE
approaches, twelve banker Talk-to replays and previous diagonal boundary guards.
See [the checkpoint](agent-work/counter-corner-1925/CHECKPOINT.md).

In-game check: launch `.\gradlew.bat run` with GPU or 117 HD using the
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
development-login guide. Start with speed **5.4**, smoothing **0**, Original player
when aligned **off**, and tracing. Repeat the same Bank click from `(3161,3485)`:
expect a normal-paced pair to `(3161,3487)` and holding there, with no extra diagonal
trip/wait/return. Also check Bank/Talk-to while already at that corner, nearby
longer/moving approaches, Exchange, and approaches that really continue after
publishing a native flag. Only the user performs gameplay. The user subsequently
reports **"that change is working well"** for this correction. This confirms the
tested situation; other counter layouts and option/geometry combinations remain
useful checks. Disable tracing to flush and report any remaining case's local
time/UTC offset.

**Door/gate stopping and forced-crossing handoff: implemented; awaiting in-game confirmation.**
The newest session is `1791164523185`, **2026-10-05 12:42:03.185–12:45:26.699
(+11:00)**, spanning `movement.previous.log` and `movement.log` at investigation.
It contains repeated Open/Walk/Close on gate IDs 1558/1559, world view 0,
base `(3168,3208)`, speed **5.5**, smoothing **0** and Original player when aligned
**off**. Relevant frames have no primary action or spot effect.

- **12:43:39.578 / 12:43:55.859:** Close publishes local hinge goal `(3904,9408)`
  from authority near `(3904,9664)`, but the game stops at `(3904,9536)`. The
  preview continues to 9408 after the flag disappears. At **12:43:40.319 /
  12:43:57.121**, invalidation selects native rendering around Y=9608/9603:
  roughly **200/195 local units** of displayed jump, followed by catch-up.
- **12:44:39.519:** from confirmed/displayed Y=9536, with the hidden native actor
  still at Y=9483, Close starts a new unsupported trip toward 9408. Native fallback
  at **39.742** interrupts it. The checked approach should hold beside the wall
  while native movement catches up.
- **12:45:15.081:** while Walk is fractionally heading from 9408 to 9536, Close
  publishes the old true tile 9408. An unnecessary recovery starts before the
  actual crossing endpoint arrives **60 ms** later. The boundary must not treat
  that hinge publication as evidence to return to the old construction anchor.
- **12:45:20.281:** the preceding Walk flag to 9408 remains unchanged for Close,
  so input observation never starts a replacement. The old preview finishes its
  tile anyway, then jumps **128 local units** to unchanged native/authority 9536
  at **20.539**. It now retires through checked recovery at that new wall click.

The focused correction extends `ObjectApproach` only for ordinary Open/Close on
a matched native wall object, using the existing perimeter goal and shared path.
Confirmed prefixes and exact fractional position are retained; genuine server
steps still decide any crossing. New hinge refinements share that boundary.
Other native destinations remain exact. No route engine, animation/model, camera,
input timing, configuration, prediction bound or collision exemption is added.

Verification: four initial capture-based tests failed before implementation; nine
new tests now cover stationary and longer Close starts, a confirmed-prefix handoff,
the occupied-boundary crossing, unchanged Walk flags, late native refinement,
scope/native-goal/collision guards and cleanup. Key cases run at **8/20/33 ms**
cadences. The copied open-gate crop is X=28..32/Y=71..77; unknown cells are blocked.
The capture does not record wall type/configuration or collision every frame:
wall lookup and closing-time updates are explicit API doubles, and some native
fractions/timings are simplified rather than full timeline replays. The final
already-offset Walk case permits a small (at most 24-unit) native handoff when its
occupied edge closes before checked recovery finishes, instead of permitting an
unchecked crossing. This is not a claim that every forced-movement snap is removed.
`.\gradlew.bat build --offline --console=plain --no-daemon` passes **507 tests**,
zero failures/errors/skips, retaining all preceding
498 regressions. See [the checkpoint](agent-work/door-gate-1245/CHECKPOINT.md).

In-game check: launch `.\gradlew.bat run` using the
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
development-login guide, with GPU or 117 HD. Start with speed **5.5**, smoothing
**0**, Original player when aligned **off**, and tracing. Repeat the same gate
Open/Walk/Close from both sides, Close just before/after a crossing update, and
one/two-tile approaches. Check the correct stopping side without an extra far-side
trip, large snap-back or construction-anchor reversal. Include another door,
walking, repeated clicks and a new Walk replacement. Only the user performs
gameplay; disable tracing to flush and report local time/UTC offset for any
remaining handoff. This correction needs the user's in-game confirmation.

**Fresh Walk confirmation / post-click oscillation: user-confirmed working well.**
The latest recordings are `1791121217442` (**2026-10-05 00:40:17.442–00:41:21.166**)
and `1791121454072` (**00:44:14.072–00:46:27.928**, +11:00). Their last repeated
yellow reversals reproduce the extra itinerary after input ends. The relevant frames
have no primary action, spot effect or combat-facing owner.

- **00:41:16.327**, final click 3023: new native goal `(6848,6720)` already equals
  authority while display is around `(6564,6720)`. The fresh visual route is marked
  fully confirmed. At **16.386**, a delayed preceding-Walk endpoint `(6592,6720)`
  gets appended as real return debt behind that new route. The next endpoint at
  **16.989** appends another forward trip, so display continues out/back/out until
  **18.168** although no additional clicks were observed.
- **00:46:22.945**, final click 6569: goal `(7104,6720)` equals authority while
  display is around `(7283,6720)`. At **23.028**, the late older endpoint `(7360,6720)`
  appends a return; at **23.626**, authority returns to `(7104,6720)` and appends
  another leg pair. Display finishes the redundant loop at **24.567**.

This is the confirmation edge case in fresh-click retargeting: equality with the
old true tile does not prove the new visual itinerary has been confirmed by this
click's movement. MovementPath now retains its exact checked fractional route as
pending in that case. Existing bounded stale-reversal handling updates real authority
without appending the old return queue or renewing the deadline. A new matching
endpoint can confirm the latest route. Pure endpoint connectors, including checked
knights, use the same pending rule even with a singleton logical goal. Timeout and
disagreement still recover normally; ordinary authority without fresh input keeps
its real confirmed debt. Combat effects, native actions/frames, gait, camera and
input smoothing changes remain intact.

Verification: both captured-ending tests and the confirmation guard fail before the
correction. Seven new tests now pass: the two endings at **8.333/20/33.333 ms** plus
five guards for cardinal/diagonal/single-step/knight confirmation, finite timeout,
re-click horizon retention, ordinary real debt and closed connectors. The fixture
retains actual click/publication/authority timing and copied collision crops for
X=45..62/Y=50..54; unknown space is blocked and native fractions between retained
changes are held by API doubles. The full
`.\gradlew.bat build --offline --console=plain --no-daemon` passes **498 tests**,
zero failures/errors/skips, retaining all 491 preceding regressions. Source change
is confined to MovementPath. See [the checkpoint](agent-work/walk-confirmation-0041/CHECKPOINT.md).

The delayed-click report is not clearly reproduced by these sets: no observed Walk
has a first displayed departure over **100 ms** before the next click. Moving clicks
are distinguished from stopped clicks, and a legal nearby connector can still be
necessary. This finding does not establish that every click was visually instant;
any remaining delay needs its own local timestamp/context.

In-game check: retain captured **speed 5.5**, **smoothing 0**, **Original player when
aligned off**, GPU or 117 HD and tracing. Repeat the last short alternating yellow
clicks, stop immediately after the final reversal, and check direct arrival/holding
at that latest target without an extra out/back itinerary. Include diagonal/knight
arrivals, scene/minimap, idle/longer routes, and the confirmed melee/range/magic effect
and gait behavior. Launch `.\gradlew.bat run` with the
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
guide. Only the user performs gameplay; disable tracing afterward to flush and give
the local timestamp/UTC offset of any remaining oscillation or delayed click.

The user subsequently reports **"that change works well"** for this yellow-click
confirmation/oscillation correction. This confirms the tested movement behavior
while retaining the preceding combat continuity improvements. The separate delayed-
click symptom was not clearly reproduced by these recordings and remains unconfirmed.

**All-attack effect release and native gait continuity: user-confirmed working well.**
Session `1791109040484`, **2026-10-04 21:17:20.484–21:18:24.531 (+11:00)**,
contains 71 clicks and 3,204 displayed samples, with speed **5.5**, smoothing **0**
and Original player when aligned **off**. The user requests smooth visual continuity
across **all melee/ranged/magic attacks and any weapon**, not a bow-only rule.

- **Effect-dependent click-away:** all 22 observed Walk clicks made during the
  native bow action with `spot=true` are rejected as `ineligible-click`. Examples
  **21:17:26.689 / 21:18:10.832** see native destinations within about **22/17 ms**
  but wait about **362/640 ms** for displayed departure. The contrast at
  **21:18:18.570** has the same native action with no active graphic and departs
  after approximately **39 ms**. The existing generic spot-effect gate explains
  this inconsistency. A shot/effect beginning just after an eligible Walk can also
  retire its pending observation, as at **21:17:54.649**.
- **Native gait versus display:** during actions/effects, PlayerPresentation restores
  the hidden actor's locomotion selectors. The log shows run pose `824` while the
  displayed player is stationary for approximately **300–601 ms**, and custom running
  with native walking/turning poses during **300–622 ms** intervals. These are not the
  existing at-most-100-ms gait bridge: the long examples have real attack/block actions.
  The pose metadata supports the reported running-on-the-spot / sliding mismatch;
  the log does not directly record the renderer's final bone blending.

The correction uses generic combat input/engagement evidence, not a weapon or attack-
animation whitelist. `CombatEffectCarry` captures up to eight active effect identities
on explicit Walk release, or once within the 100-ms observation window if their
publication follows the click. Source/ID/hash/start-cycle matching and a **900-ms**
lifetime prevent replacement effects or repeated clicks renewing that same evidence.
Recent native Attack/Cast input is bounded to **1.8 s**; manual spell targeting still
requires native approach/range evidence. Ctrl, disabled starts, unsupported native-
location actions, scene/profile changes and missing/new effects retain their gates.
MovementPath keeps the same checked route, clock, distance credit and prediction bounds.

PlayerPresentation now supplies native walk/run/idle selectors from displayed gait
through primary attack/cast/block poses and spot effects. Primary action IDs and frames
are never changed. For anticipated motion or catch-up while the native actor is idle,
the native tick idle selector still matches gait so its pose clock advances. At each
client-thread availability probe/draw-time native build, NativeModelObject temporarily
supplies the real idle selector to let the native builder blend that secondary gait
with the primary action; `finally` restores the tick selector immediately. This keeps
RuneLite 1.13's fresh-model contract, no shared-mesh cache or second animation controller.

Verification: five of eight initial regressions fail before implementation. The final
suite adds **19** continuity/lifetime/style guards, **3** model-selector/provider checks
and **5** captured multi-click window replays at **8.333/20/33.333 ms** cadences. The
style matrix covers melee/unarmed behavior, halberd, known/unknown ranged weapons,
magic/unknown profiles, arbitrary native action IDs, manual Cast and lingering effects.
Input scope, smoothing, timeout, replacement effects, profile/scene cleanup, primary
frame preservation, secondary native-clock progression and worker rejection are covered.
`.\gradlew.bat build --offline --console=plain --no-daemon` passes **491 tests**,
zero failures/errors/skips, retaining all 464 preceding regressions.

Evidence boundary: `bow-2117.txt` retains actual input/authority/destination/action/
spot timing and copied collision crops. Native engagement, effect identities and
primary-frame progression are explicit API doubles because the old capture omits them;
native ticks read the prepared gait selectors in the doubles. Model tests check the
native-builder contract, not rendered bones. Broader melee/magic/unknown-weapon coverage
is automated scope coverage, not a separate live capture. New diagnostics record native
`actionFrame`, effect identity tuples and `combatEffectCarry`. The audit found no position
budget outliers; small sampled turning differences do not establish an actual turn snap.
See [the checkpoint](agent-work/combat-visual-2117/CHECKPOINT.md) and [model handling](RUNELITE-1.13.md).

In-game check: start with speed **5.5**, smoothing **0**, Original player when aligned
**off**, GPU or 117 HD and tracing. Attack/cast, then Walk during the primary pose and
its graphic, before/after the graphic disappears, and just before the first hit. Check
prompt scene/minimap release and continuous running/walking, then a clean idle stop
without borrowed native run/turn poses. Repeat melee, ranged, autocast and manually
selected spells with different weapons; include blocks, repeated clicks, pickups and
target death. Toggle Animation Smoothing and Original player when aligned, then disable/
re-enable the plugin. Launch `.\gradlew.bat run` with the
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
development-login guide. Only the user performs gameplay. Turn tracing off to flush
and report local timestamps/UTC offsets for any remaining visual case.

The user subsequently reports **"that change is working well"** for the all-attack
effect/gait correction. The newer fresh-Walk confirmation correction above is separate
and is also user-confirmed working well for the tested situations.

**Exact ground-item destinations: user-confirmed good for the tested pickups.**
The three new recordings are **20:32:12–20:33:12**, **20:34:51–20:35:21** and
**20:37:10–20:38:04 (+11:00)** on 2026-10-04. The last was excluded as requested;
the final pickups in the first two reproduce the same adjacent-search policy error.

- Session `1791106332706`, Take at **20:33:06.893**, repeated at **07.091**:
  native destination is local `(8512,6208)`, but the forecast ends beside it at
  `(8640,6208)`. The straight-first route is blocked; generic object-style approach
  search accepts an adjacent tile. Authority reaches the item at **07.573**. The
  old alignment then rebuilds toward the obsolete adjacent goal and holds until
  **08.473**, before returning to the real endpoint.
- Session `1791106491512`, Take at **20:35:16.934**: native destination is
  `(8512,5824)`, but the checked forecast ends at `(8640,5824)`. It stops there at
  **17.532**, waits approximately **259 ms**, then restarts on final authority at
  **17.791**. Both clicked ground items have ID `21326`; the policy uses action
  classification, not that ID or these coordinates.

Ordinary ground-item options now require an **exact native-destination search**
for idle starts, fractional handoffs and queued continuations. The existing bounded,
reversible search checks an alternate route all the way to that tile; an adjacent
object staging endpoint is not a substitute. Missing/unchanged native publications
cannot start a prediction, and an inaccessible exact goal waits for authority rather
than inventing an adjacent trip. Repeats keep their original identity/deadlines;
checked confirmed prefixes and the single movement/facing budgets are preserved.
Object, NPC and item/spell-targeted input retain their existing policies. The final
reported pickup clicks have `action=-1` and no retained combat target; this correction
does not need a combat-specific movement exception.

Verification: six of seven new tests fail before the fix, including both capture
replays. All seven now pass: the two timelines at **8.333/20/33.333 ms** plus five
guards for ordinary-option scope, unavailable exact goals, native evidence, moving
post-combat handoff and confirmed-prefix continuation. `pickup-2033-2035.txt` copies
the actual click collision cells for scene X=63..72/Y=43..51; the two crops match,
and unknown cells are blocked. Replays retain click/repeat/flag/authority timings
and sampled native player points; fractions between retained changes are held by
API doubles. They do not execute the native renderer. The full
`.\gradlew.bat build --offline --console=plain --no-daemon` passes **464 tests**,
zero failures/errors/skips, including all 457 preceding regressions. See the
[pickup checkpoint](agent-work/pickup-destination-2033/CHECKPOINT.md).

In-game check: use captured **speed 5.6**, **Walk smoothing 0**, **Original player
when aligned off**, GPU or 117 HD and tracing. Repeat the two final pickups after
combat, then from idle and while running; include a repeated Take and nearby item
tiles around the same obstruction. Check continuous travel to the item, with no
adjacent stop/restart, timeout wait or return. Launch `.\gradlew.bat run` using the
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
development-login guide. Only the user performs gameplay; turn tracing off afterward
to flush and report any remaining local timestamp/UTC offset.

The user subsequently reports **"that change was good"** for the exact ground-item
correction. The newer all-attack effect/gait correction above needs its own confirmation.

**Combat Walk escape and ranged target continuity: user-confirmed working really well.**
Session `1791097816262`, **2026-10-04 18:10:16.262–18:11:30.016 (+11:00)**,
contains 29 Walk/Attack clicks and 3,689 rendered samples. It is the combat capture
following the user's approximately 18:05 report. The user confirms **magic shortbow
(i), Rapid**, and retains the previously confirmed post-arrival melee pursuit gaze.

- Walk at **18:10:23.359 / 18:10:32.398 / 18:11:05.698** is marked `ineligible-click`
  during native melee/bow poses. Destinations appear after about 16/21/21 ms, but
  displayed departure waits about 718/678/378 ms for authority. A combat-owned Walk
  now releases the target immediately and observes/releases its native destination
  through the existing smoothing and checked Walk pipeline. Remaining native
  shots/blocks do not clear that observation, retire its forecast or reclaim facing.
- Bow at **18:10:58.674** uses `npcReserveTiles=10`, reaches successive intermediate
  stops before continuing on authority, and loses its NPC capture at the shot.
  **18:11:22.659** cannot preview from ten tiles away under that reserve. RuneLite's
  supported native `ATTACK_STYLE_NAME` is **Ranging** for Accurate/Rapid, not the
  button label Rapid. `CombatApproach` now recognizes that cache style; the known
  shortbow uses its seven-tile reserve. Known ranged profiles seed a reversible
  straight-first prefix to a line-of-sight firing boundary, at normal walk/run pace
  and whole running-pair endpoints. This uses MovementPath's queue, clock, fractional
  joins and unchanged response/chain/distance limits. No speculative detour through
  blocked geometry is added. Native shots retire remaining speculation, retaining
  checked confirmed debt and real animation timing.
- In-range bow clicks at **18:10:49.438 / 18:11:05.158 / 18:11:08.196** retain no
  combat facing owner before the native shot. Every captured Attack profile now
  retains its selected NPC for capped stationary facing before, during and between
  attacks. Known ranged motion and the existing melee follow reserve have separate
  eligibility within the same path. Halberd/casting/unknown profiles retain their
  conservative positional handling. Replacement input, target/profile invalidation,
  lost native engagement and lifecycle changes retire ownership; unengaged clicks
  remain bounded. Walk suppresses a stale target reference/remaining pose. With
  auto-retaliation enabled, a newly observed native NPC engagement and pose after
  the Walk flag/observation are gone can retire its obsolete preview and restore
  native facing, retaining real confirmed movement even during catch-up.

Verification: five of the initial seven focused regressions fail before the correction.
Ten captured click/authority/action replays pass at **8.333/20/33.333 ms** cadences;
their actual collision crops are identical across all ten click contexts. Seventeen
guards cover native cache styles, stationary/drawn target facing, bounded pursuit,
normal run-pair pacing, click-away through poses, stale-target rejection, fresh native
auto-retaliation, collision/line-of-sight changes, conservative profiles, input gates,
minimap smoothing, timeout/lifecycle and immutable diagnostics. An older ranged test
now uses a three-tile discontinuity instead of rejecting an eligible observed two-tile
step. The full `.\gradlew.bat build --offline --console=plain --no-daemon` passes
**457 tests**, zero failures/errors/skips, retaining all 430 prior regressions.

Evidence boundary: old bow samples record the clicked footprint, not live native
engagement, drawn NPC fractions or cache styles. Replays explicitly use the user-
confirmed equipment/style, supported native-style API doubles, held target positions
and native-engagement doubles at recorded shots. Native player fractions between
sampled changes are held by the doubles. Auto-retaliation and moving ranged-target
checks are synthetic guards, not assertions about this capture's hidden native state.
New samples add `combatWalk`, category/style/cache-style/profile fields; click contexts
also record auto-retaliation. See the [combat checkpoint](agent-work/combat-input-1810/CHECKPOINT.md).

In-game check: start with captured **movement speed 5.7**, **Walk smoothing 0**,
**Original player when aligned off**, GPU or 117 HD and tracing enabled. Attack a
guard with melee, then Walk away during attack/block poses; try scene and minimap,
longer routes and repeated/mixed directions. Check prompt release and no intermediate
stop/restart or turn back to the old target. With magic shortbow (i) Rapid, repeat
in-range and longer/offset Attack clicks: continuous checked approach, correct firing
stop, and continuous stationary gaze before/between shots. Keep the existing melee
pursuit gaze. Test auto-retaliation off, then on with a genuine native re-engagement;
also target changes/death, walking, Longrange and a wall. Launch `.\gradlew.bat run`
using the [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
development-login guide. Only the user performs gameplay. Disable tracing to flush
and report local timestamps/UTC offsets for any remaining case.

The user subsequently reports **"those changes are working really well"** for
this combat input/bow targeting correction. This confirms their tested situations;
the newer exact-pickup-destination correction above needs its own confirmation.

**Bank-service diagonal counter-start boundary: implemented; awaiting in-game confirmation.**
The recording `1791079804024`, **2026-10-04 13:10:04–13:10:34 (+11:00)**,
repeats Talk-to at **13:10:11.853 / 14.382** and Bank at **18.042** from world
`(3161,3487)`, two tiles diagonally from the banker at `(3163,3489)`. The native
position and authoritative tile stay at the origin, with no destination flag.
The old preview travels to `(3162,3489)`, stops, then returns on its 900 ms timeout.

A fresh click on the qualifying **one-sided bank-service counter** now treats
the two-tile square, including `(2,2)` corners, as a conservative start boundary.
It retires only an obsolete speculative tail and waits for actual authoritative
movement; a closer native flag alone cannot manufacture an approach from there.
Confirmed movement still finishes its checked prefix, and new authority is
accepted normally. This start gate is separate from the existing rounded,
whole-run-pair stop used by longer bank approaches. Bank/Exchange and Talk-to with
native Bank/Exchange service actions plus the qualifying geometry are its only
eligible options. Ordinary Talk-to/Trade/Attack retain their own approach distances.
New trace decision: `at-counter-boundary`.

Verification: the captured stationary-corner replay fails before this correction.
It now passes for all three click intervals at **8.333/20/33.333 ms** cadences using
copied collision columns. The neighbouring **13:10:26.262** Bank start still runs
its recorded pair at normal pace. Six further guards cover transformed corner
geometry, NPC/option isolation, native-flag versus authority evidence, confirmed
prefix preservation, replacement-preview recovery and continuing runs through
the corner. `.\gradlew.bat build --offline --console=plain --no-daemon` passes
**430 tests**, zero failures/ignored tests, including all 422 prior regressions.
The original capture includes Bank/Talk-to; Exchange is an API-double parity
check. See [the corner checkpoint](agent-work/bank-counter-1310/CHECKPOINT.md).

In-game check: keep movement speed **5.7**, Walk smoothing **0**, and Original
player when aligned **off** first. Repeat Bank and banker Talk-to from the reported
world tile, then Exchange from the equivalent counter corner. Check the character
stays put while performing an already-in-range interaction, without an extra
trip, wait and return. Recheck the neighbouring outside tile `(3160,3487)` and a
longer/moving bank approach. Launch `.\gradlew.bat run` using the
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
guide with GPU or 117 HD. Only the user performs gameplay; enable tracing while
checking, then switch it off to flush.

**After-midnight pickup, yew and running continuity: implemented; awaiting in-game confirmation.**
Sessions `1791036237103` (**2026-10-04 00:03:57–00:04:41**) and
`1791036292200` (**00:04:52–00:05:17**, +10:00) contain the three new reports.

- **Repeated Take, 00:04:01–00:04:03:** item `233` at scene `(39,31)` is
  clicked seven times. The flag remains `(5056,4032)`, but every repeat retires
  agreement and rearms observation for a new flag that never appears. The
  original forecast stops at `(5440,3904)` at 02.358, then resumes at 02.859.
  `InteractionTarget` now retains an identical ordinary action/ID/tile/view/plane
  as the same approach, preserving the route, clock and observation/prediction
  deadlines. Changed targets/actions and item/spell targeting retain fresh-input handling.
- **Moving yew, 00:04:17 / 00:04:27 / 00:04:37:** the clicked object's
  footprint is `(4288,3520)..(4544,3776)`; its anchor flag later refines to
  the southern endpoint `(4544,3904)`. The old handler can finish the preceding
  Walk, preserve an unconfirmed knight end, or build the refined forecast on
  the wrong row. The last case reaches `(4672,4032)`, expires, reverses to
  `(4800,3904)`, then approaches again. Object/item approaches now reuse the
  authority-anchored fractional `joinForecast` used by NPC continuity. Native
  refinements retain their original deadlines; new checked forward authority
  can align the forecast. A partially confirmed occupied knight keeps the
  still-forward confirmed debt inside its proven corridor, rather than its
  obsolete unconfirmed forward end. Unfinished confirmed prefixes remain protected.
- **Running, 00:05:05–00:05:13:** this session contains native destination and
  confirmed run updates without new recorded clicks. At effective speed `1.2`,
  a pair takes about 533 ms to display, leaving short positional waits before
  the next endpoint. `MovementGait` bridges the pose, not the position. The
  existing exponential final-leg mechanism now retains a small reserve in the
  last 100-ms portion of an eligible **confirmed** running leg. It remains on
  checked geometry, adds no tile/forecast/clock, and uses the existing response
  bound. Native arrival, action, pending replacement input or lost continuing
  evidence returns that remainder to normal pacing. Interaction/combat pacing
  keeps its own existing policy.

Verification: the initial pickup, worst-yew and running tests each fail before
the correction. Four timing replays now pass at **8.333/20/33.333 ms** cadences,
including the preceding 00:04:27 yew case. `midnight-yew-map.txt` retains the last
yew click's actual collision columns; those object replays use its bounded crop
and logged footprint with API doubles. Pickup/run identity/pacing checks use an
explicit open collision double. Native sub-tile points between recorded samples
are held by the doubles, not live native rendering. Nine more tests cover repeat
identity/expiry, scene cleanup, blocked/confirmed connectors, the 00:04:17 middle-
tile knight case, native refinements/authority, cadence and pacing release.
`.\gradlew.bat build --offline --console=plain --no-daemon` passes **422 tests**,
zero failures/ignored tests, including the previous 409 regressions.
New decisions are `repeated-world-interaction`, `retargeted-world-approach` and
`aligned-approach-authority`.

In-game check: keep the recorded **movement speed 5.8**, smoothing **60**, and
Original player when aligned **off** first. Enable tracing, repeatedly Take the
same ground item while running toward it, then vary the item/tile. Repeat moving
Chop down clicks on the same yew, including an occupied diagonal/knight and the
last reported approach. Check no extra loop, backward correction or intermediate
stop. Finally take a long run with no extra click, then test destination arrival,
replacement clicks, walking and a region crossing. Repeat ordinary NPC, bank
counter and melee interactions to check their existing behavior. Launch
`.\gradlew.bat run` using the [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
development-login guide above. Only the user performs gameplay; disable tracing
afterward to flush and report the local timestamp/UTC offset of any remaining case.

**Combat gaze lock and slower pursuit: user-confirmed working well.**
The recording `1791030239837` starts **2026-10-03 22:23:59.837 (+10:00)**; its
five Attack clicks are **22:24:09–22:24:44**. The preceding 22:23 samples are idle
after a Walk. The two moving-target timelines (clicks 755 / 2234) finish follow
legs early, then wait approximately 120–360 ms for subsequent target steps/hits.
The earlier facing policy uses travel heading whenever the path moves and samples
the target's server tile rather than its drawn sub-tile position.

Arrival now latches a combat gaze owner. It continuously aims at the directly
observed native NPC local position under the normal turn cap, including while
pursuing and during native hit/block poses. The custom provider retains facing
ownership even at aligned native idle. A repeated Attack on the same captured
target retains the lock; Walk/other interaction, native engagement loss, target
death/profile/view invalidation and lifecycle cleanup release it.

Initial approach retains normal run/walk pacing. Once locked, following uses
**65% of configured walking pace**, plus a frame-rate-independent exponential
tail on the last checked leg. This leaves positional reserve through successive
NPC steps and while waiting for the hit, without adding a clock, route, movement
budget or animation controller. A real `HitsplatApplied` on the selected NPC,
marked `Hitsplat.isMine()`, can release only the collision-checked confirmed
remainder to current player authority at normal movement pace. Incoming player
hits and other players' damage do not trigger it; no attack animation or hit is
scheduled/restarted. Prediction expiry still uses bounded recovery. Confirmed
slow debt has a fallback at the existing response bound if a hit event is missing.

Verification: `combat-2224.txt` retains actual live target bounds, engagement,
player/action timings and a copied collision crop for all five cases. These logs
do not yet record the drawn NPC fraction or owned hitsplat timing: their replays
use explicit centre-position/owned-hit event doubles, with the latter aligned to
the recorded native swing start. All five pass at three frame cadences. Eight
additional tests cover gaze ownership/curved sub-tile aiming, slower pursuit,
native hit/no-snap settling, hit ownership, repeated input, exponential cadence
and timeout. The full `.\gradlew.bat build --offline --console=plain --no-daemon`
passes **409 tests**, zero failures/errors/skips, including all previous movement
and combat regressions. New samples record `combatLocked`, `combatFaceTarget`,
accepted `combatHit` evidence and `combatPacing=normal|trailing`.

In-game check: use speed **6.5** and smoothing **60**, with tracing. Reach a guard,
then watch continuous turning toward its moving drawn position while stationary,
following and in hit/block poses. Have it walk several tiles before the first
hit; check slower continuous pursuit, then a smooth move onto the confirmed tile
during the real combat pose. Include misses, incoming damage, repeated Attack,
Walk/Talk-to cancellation and target death. Launch/login instructions are above;
only the user performs gameplay. Disable tracing afterward to flush. The user
subsequently reports this refinement **worked well** for the tested situations.

**Adjacent-melee combat continuity: user-confirmed working well.**
Session `1791023661057`, **2026-10-03 20:34:21–20:35:39 (+10:00)**, contains
thirteen Guard Attack clicks, using recorded weapon ID `26484`, movement speed
**6.5** and smoothing **60**. The old handler can retire the target when its
footprint changes (`combat-profile-changed`), and its two-tile melee reserve can
finish a preview short of adjacency. The 20:35:13.586 approach publishes two
intermediate stops before native animation `1658`; other clicks lose their
approach around an NPC/profile change or a native combat pose.

`CombatApproach` now distinguishes cache-identified adjacent melee from halberd,
ranged, casting and unknown profiles. `CombatContinuity` keeps click/target/native
engagement metadata only: it refreshes one directly indexed NPC's immutable
footprint after checked one/two-tile changes and ends on target/profile/view
invalidation, lost established engagement, replacement input or lifecycle changes.
An unengaged click remains time-bounded. The same `MovementPath` approaches a
checked adjacent side, joins changed target/player authority from the visible
fraction, and holds when that target is stationary. Confirmed prefixes and
collision-checked corridors remain intact; the path owns the single frame budget.

Movement credit is finite. An actual click or new checked forward player progress
supplies one reserve; an engaged, reconciled arrival can retain that credit while
waiting beside a stationary NPC. Its next observed target step spends the reserve
under the existing 900 ms response / 1.8 s chain / distance limits. Repeated NPC
motion without player progress cannot renew it. Real native combat poses are
retained by the native builder, not synthesized, restarted or accelerated by the
plugin. Facing keeps its normal cap; a short eligible confirmed-leg pose gap can
use the existing at-most-100-ms gait bridge. Hit timing and the server route stay native.

Evidence boundary: old samples retain only the clicked NPC's original footprint,
not its live movement or native interaction each frame. Weapon/style cache details
also require explicit API doubles. `combat-2034.txt` preserves the actual player,
destination, action and collision timing for three stop/start cases, with clearly
labelled synthetic target-motion/engagement scenarios. Those three replays pass at
three cadences; twelve additional controller guards cover holding, observed motion,
finite budgets, fractional joins, native poses, walking, collision, invalidation
and immutable diagnostics. The full
`.\gradlew.bat build --offline --console=plain --no-daemon` passes **396 tests**,
zero failures/errors/skips, including every previous counter and NPC regression.
New samples include refreshed combat target bounds, `combatPhase` and
`combatEngaged`, so the next real capture can distinguish target motion from a
native combat delay. The user subsequently reports the initial combat continuity
**is working well**, then requests the gaze/pursuit refinements described above.

In-game check: use the captured adjacent-melee equipment/style, speed **6.5** and
smoothing **60**, with tracing. Attack a moving guard once and let the game pursue
it; check one continuous displayed approach, holding without extra tiles while
the NPC stands still, and a smooth checked follow when it moves before the hit.
Try repeated Attack clicks, a click during a native hit/block pose, a target moving
away twice, a blocked corner, walking, Walk replacement, weapon/style switching,
and target death. Also repeat ordinary NPC and Bank/Exchange/Talk-to approaches.
Launch/login instructions are above. Only the user performs gameplay; disable
tracing afterward to flush. The user subsequently confirmed the gaze/pursuit refinement
works well for the tested situations.

**Bank-service Talk-to counter parity: user-confirmed worked well.**
Session `1791020727336`, **2026-10-03 19:45:27–19:46:30 (+10:00)**, contains
twelve Talk-to clicks on the banker at `(3163,3489)`. Eight start at `(3157,3487)`;
four start at `(3157,3488)`. All finish at authoritative `(3161,3488)`, although
the native flag names the closer `(3162,3489)`. The old ordinary-NPC policy can
replenish a last predicted step toward that flag. The late final authority in
clicks **1933 / 2265 / 2961** then requires a backward corrective leg. The other
nine final updates arrive early enough to retire the extra tail before overshoot.

This is the already supported one-sided bank-counter geometry and stopping
envelope. Talk-to now shares `counterRun` / `anticipateCounterRun` only when the
clicked NPC's supported native cache actions identify a Bank/Exchange service
**and** its footprint has the qualifying one-sided counter geometry. The role
evidence is captured once using the transformed composition, with the raw
composition as a fallback. Unsupported/missing evidence retains ordinary handling.
No NPC-ID/name/coordinate table or new movement pipeline is introduced; exact
checked route order, whole run pairs, pace, native/stopping goals and existing
prediction bounds stay in the counter policy.

Verification: `banker-talk-1945.txt` retains the twelve click/authority/flag timings
and starting tiles. All **23** click collision crops match the earlier fixture.
The capture records the banker identity, not its cache actions; the service-action
gate is covered with explicit API doubles. All twelve constant-pace/no-overshoot
replays pass at three cadences. Six guards cover service/ordinary scope, unsupported
geometry/cache data, read-once lifetime, timeout and start gates. The full
`.\gradlew.bat build --offline --console=plain --no-daemon` passes **381 tests**,
zero failures/errors/skips, including the prior counter and continuity regressions.

In-game check: use the captured movement speed **6.5** and Walk smoothing **60**.
Repeat Talk-to from both reported tiles, especially slower server confirmations;
check normal run pace through to the correct stop with no extra step/backtrack.
Also try Bank on that banker, a moving Talk-to replacement, walking, an already
in-range click, and ordinary Emblem Trader/Perdu interactions. Use the launch/login
instructions above and tracing, then disable tracing to flush. Only the user
performs gameplay. The user subsequently reports this extension **worked well**;
that confirmation does not validate the newer melee-follow implementation above.

**Ordinary NPC continuity follow-up: user-confirmed good for the tested situations.**
The two new sessions are `1791018087494` (**19:01:27–19:02:39**) and
`1791018327163` (**19:05:27–19:05:53**), 2026-10-03 **+10:00**. They contain
32 Emblem Trader Talk-to / Perdu Trade approaches. The remaining issues include:

- A delayed preceding-Walk endpoint undoes a freshly retargeted NPC preview
  (19:01:49 / 19:01:53), causing a correction and an intermediate wait.
- A distant late flag still identifies the old Walk, not this NPC (19:02:26 /
  19:02:34). The 19:02:26 Perdu approach waits approximately **541 ms** mid-route.
- The provisional diagonal ring differs from the actual cardinal-side arrival
  (19:05:39 / 19:05:51), producing a small retrace and turn reversal.
- An already adjacent authoritative position or a diagonal-adjacent one-step
  approach can leave the old preview running or cap the new preview at its origin
  (19:05:35 / 19:05:45).

Ordinary non-Attack, non-Bank/Exchange options now choose a collision-checked
cardinal-side staging goal, with a checked one-step exception inside their adjacent
ring. An already adjacent authoritative position retires only the obsolete Walk
preview. Distant flags cannot supersede this captured ordinary target. A moving
retarget can retain its clipped forecast through an opposite/perpendicular stale
Walk endpoint without renewing either deadline; the endpoint still updates real
authority. Genuine checked forward NPC progress can re-anchor the forecast (or
finish its checked join to an already confirmed arrival) from the same visible
fraction and movement clock. Confirmed prefixes, collision proofs and bounded
recovery remain in the single pipeline. Bank/Exchange and Attack keep their own policies.

Verification: the two `npc-continuity-1901/1905.txt` timelines retain preceding
mixed input and authority/flag timing. Their collision crop matches the earlier
fixture across all **84** new click contexts. All **32** approach replays pass
at 8.333/20/33.333 ms cadences, checking intermediate stops, arrival retraces,
position/turn budgets and eventual isolated arrival. Six new guards cover side
geometry, inner-ring starts, stale evidence/deadlines, already adjacent cancellation
and collision-checked authority joins. The full
`.\gradlew.bat build --offline --console=plain --no-daemon` passes **363 tests**,
zero failures/errors/skips. API doubles and sampled native positions do not execute
the game renderer or establish every NPC's hidden reach/route selection.

In-game check: use movement speed **6.0**, smoothing **0**, and tracing first.
Repeat moving Emblem Trader/Perdu clicks after a Walk away from them, diagonal
nearby starts, already-adjacent clicks and short arrivals. Check uninterrupted
travel and no extra near-arrival turn out/back; also repeat the prior moving and
idle Bank/Exchange approaches. Launch/login instructions are above. Only the user
performs gameplay; disable tracing afterward to flush. The user subsequently reports
this continuity follow-up was **also good**; that confirmation does not validate
the newer banker Talk-to counter extension above.

**Moving red clicks and ordinary NPC isolation: user-confirmed good for the tested situations.**
Session `1791015637884`, **2026-10-03 18:20–18:22 (+10:00)**, contains six moving
Bank clicks, three Emblem Trader Talk-to clicks and one Perdu Trade click. The
moving failures can expire the input observation after 100 ms while the old Walk
forecast still runs; its later disagreement/endpoint produces a backtrack or
stop/restart. The ordinary NPCs also inherit the two-tile staging policy added for
bankers/clerks. At 18:22:01.466 the Emblem Trader's native refinement explicitly
builds a backward connector; Perdu's occupied knight refinement falls into recovery.

The extended reserve, startup easing and five-tile final-pair exception are now
gated to Bank/Exchange options. Ordinary non-Attack options retain a one-tile
adjacent boundary. `retargetNpcApproach` joins a moving red click's checked forecast
from authority to the exact visible fraction; existing unfinished confirmed prefixes
still use queued continuation. `retargetNpcDestination` applies the same checked
join to ordinary NPC native refinements without renewing their deadlines. Eligible
counter runs retain their exact logical route and native/stopping goals.

Verification: `red-click-1820.txt` retains click/authority/flag timing and a copied
collision crop, verified identical across all 28 click contexts. Ten replay cases
run at 8.333/20/33.333 ms cadences, checking bounded connectors, no intermediate
stop, one pacing budget and eventual authoritative arrival. They use API doubles
and sampled native locations, not the game's renderer. Five additional guards cover
option isolation, adjacent starts, prediction deadlines and unavailable/confirmed
connectors. `.\gradlew.bat build --offline --console=plain --no-daemon` passes
**325 tests**, zero failures/errors/skips, including the previous 57 counter replays.

In-game check: keep the capture's movement speed **6.0** and smoothing **0** first.
Walk/run, then red-click Bank/Exchange before the Walk finishes; try both ahead
and across the current route. Repeat the Emblem Trader and Perdu idle approaches,
including offsets and short two/three-tile clicks. Check normal pace, no extra
backtracking or staged stop/restart, and the correct native stopping tile. Also
check settled counter starts, walking and Walk replacement. Use the development
launch/login instructions above and Record movement trace; only the user performs
gameplay. The user subsequently reports this preceding correction was **good**;
that confirmation does not validate the newer 19:01/19:05 follow-up above.

**GE one-sided counter routes: implemented; user-confirmed working.**
The user's 23:14–23:22 capture contains 57 Bank approaches with complete collision
snapshots. The previous implementation fails 53 of their route/constant-pace
replays: it can start toward the wrong side of the footprint, rebuild from a
different row/column when a native flag appears, or stop at its generic square
staging boundary before the actual approach finishes.

A geometry-based Bank/Exchange rule now searches to the NPC's sole accessible
adjacent side and retains complete run pairs to the supported stopping envelope.
All 57 recorded server endpoint sequences agree; the new controller replays pass
at three frame cadences for Bank and Exchange using the copied map and recorded
authority/flag timing. The capture itself contains **Bank only**; Exchange and
rotated/mirrored layouts have automated coverage, not separate in-game confirmation.
The user subsequently confirmed the correction works in-game for their tested
situations. No per-tile GE route table is used. See the
[counter checkpoint](agent-work/ge-counter-2314/CHECKPOINT.md) for scope and evidence.

Verification: `.\gradlew.bat build --offline --console=plain --no-daemon` passes
**310 tests**, zero failures/ignored tests: all 243 prior regressions, 57 capture
replays and ten counter-policy guard/geometry tests. Earlier user confirmations
do not validate this subsequent correction.

Launch `.\gradlew.bat run` using the
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
development-login guide, with GPU or 117 HD. Enable **Record movement trace**
before clicking. Repeat the same troublesome starts using the same speed settings,
then try Exchange and the other sides of the GE counter. Check continuous normal
run pace, the correct approach side and stopping tile, with no extra backstep,
zig-zag or staged stop/restart. Include a short already-in-range click, a moving
interaction click, Walk replacement, and walking. Switch tracing off to flush.
Keep the current log and its rotated archives together, since a click's collision
context can precede the file containing its arrival. Only the user performs gameplay.

The user confirms the turning and scene-click smoothing feel substantially
smoother, and reports the 2026-09-28 evening route changes **worked really well**
for the stalled/backtracking behavior they tested. Arbitrary-direction scene
retargeting, the reported tree-footprint routes, and the herb-run
plant/compost footprint correction also have user confirmation. These are
reports for the tested situations, not a blanket guarantee for every route or
interaction. UG launcher idle-alert
compatibility remains unresolved. The user reports both the NPC provisional-start
and extended-range stopping corrections work well for the tested interactions;
the user also reports the close-range stop/start correction works well, while
reserving close Bank/Exchange clicks as a possible later follow-up (below).
The preceding guard/Attack reserve attempt made no visible reported difference.
Adjacent-melee continuity and the subsequent gaze/pursuit refinement are user-confirmed.
The subsequent combat input/ranged targeting work is also user-confirmed;
unknown reach and unsupported blocked-layout handling remain conservative.
The route-fix build (2026-09-28) passed 133 tests. The subsequent
RuneLite 1.13.0 model-provider migration passed 139 tests, and the user reports
the run issue is fixed. The earlier confirmations apply to the movement work;
broader rendering edge cases remain useful checks.

**True-tile route alignment: user-confirmed working really well for scene clicks.** The
18:42/18:43 trace shows scene-click forecasts continuing along a parallel route
despite forward true-tile updates, then reversing when the preview expires.
An authority-anchored checked join now passes the full **204-test** build,
zero failures/errors/skips, including nine new route/controller regressions.
The user subsequently reports **“that worked really well”** for this correction.
That scene-click confirmation does not validate the new minimap extension below.

**Minimap/scene Walk parity: awaiting in-game confirmation.** Minimap clicks now
share the same smoothing, fractional retargeting, true-tile alignment, start gates
and bounded prediction as scene Walk clicks. Native destinations are latched after
minimap interface processing. The full **237-test** build passes with zero
failures/errors/skips, including ten new controller parity/timing regressions.

**New area-crossing correction: awaiting in-game confirmation.** The two reported
23:51/23:52 captures show preview cancellation at an ordinary scene rebase,
followed by backward movement and a stop. Checked preview resumption and retained
confirmed-prefix recovery now pass the full **149-test** build (zero
failures/errors/skips). Previous user confirmations above do not validate this
new change or establish that native loading freezes are removed.

**NPC early starts: user-confirmed working well for the tested interactions.**
The user subsequently reported overshoot for bankers/GE clerks with an extended
interaction radius. The 15:25/15:26 captures confirm a native approach flag can
name a closer tile than the eventual server stop. The new NPC staging/arrival
correction reserves the final two-tile approach for authority, including after
native refinement and passed a **175-test** build. **The user subsequently
confirmed this stopping correction works well.** The later close-range captures
show a one-tile idle preview reaching the reserved ring, stopping, then restarting
on the first server step. A narrow pacing correction now passes **181 tests**,
zero failures/errors/skips, including six new regressions. **The user reports
this close-range change works well**, but may return to close Bank/Exchange
clicks at another time. Earlier confirmations apply
to the tested situations, not every NPC's reach or this new pacing change.

**Five-tile NPC arrival: user-confirmed working for the tested case.** The
user rejected the preceding arrival easing; the 2026-10-01 11:12 banker captures
confirm `eased-npc-arrival` slowed down before the destination. That rule is now
replaced by a checked final-run-pair release to the adjacent native goal, using
normal pacing in the exact five-tile cardinal case. The full **216-test** build
passes with zero failures/errors/skips, including constant-speed replays of all
three captures and the earlier overshoot regressions. The user subsequently
reported **“okay that worked”**, then supplied new short/offset approaches below.

**Short Bank/Exchange run pairs: awaiting in-game confirmation.** The 12:48/12:49
captures show remaining three-tile approaches with zero/one/two-tile offsets
using the old one-tile startup easing. The new checked whole-pair start uses
normal run pace to the selected reachable endpoint. The full **227-test** build
passes, zero failures/errors/skips, including eleven new regressions and the
confirmed five-tile cases. This subsequent extension needs its own confirmation.

The following dated sections retain their original reproduction steps and
evidence. Where they say “awaiting confirmation”, consult this current status
and the linked checkpoint's later confirmation first.

## RuneLite 1.13.0 compatibility — 2026-09-29

The dependency now targets **1.13.0**, replacing the outdated 1.12.39 workaround.
`NativeModelObject` supplies the native body/spot mesh at draw time through
`RuneLiteObjectController.getModel()`. The camera marker uses a static cache
model. Missing preparation models select native presentation instead of retaining
an old shared mesh. **The user reports the 1.13.0 run fix worked in-game.**
The full 1.13.0 build on 2026-09-29 passed **139 tests with zero failures/errors/skips**;
development runtime resolution also confirms the client and injected client at 1.13.0.

That report confirms the reported failure was resolved; it does not establish
that every model handoff, effect or renderer combination was tested. The following
checks are useful if a presentation issue arises.

See [RUNELITE-1.13.md](RUNELITE-1.13.md) for the supported API, model lifetime,
tests and focused checks. Launch with `.\gradlew.bat run` and the login guide
above; check walk/run poses, equipment, spot effects, Animation Smoothing,
native/custom handoff, the optional marker, and scene/plugin lifecycle with
your GPU renderer. The build verifies the API migration; the user confirms the
run fix, while the detailed appearance checks depend on what was exercised.

## Movement

### Minimap and scene Walk parity — 2026-10-02

Status: **awaiting in-game confirmation**. The user confirms the preceding
scene-click true-tile correction worked really well, then requests the same
behavior for minimap clicks and general parity with regular Walk clicks.

Both click sources already fed the same path engine, but minimap input bypassed
the settling window and was excluded from the forward authority-alignment call.
It also waited for preparation to capture a destination that native interface
processing could already have published before `ClientTick`.

The minimap entry now uses the same `clickSmoothingMs` setting and immediately
latches the observed native destination against the preceding tick's flag. A
repeat can reuse an unchanged native flag when it replaces a different visual
route, because native minimap processing has already completed at observation.
The common yellow-Walk gate applies the same `alignWalkAuthority` correction to
both sources; the trace retains `WALK`/`MINIMAP` labels for diagnosis only.
Fractional position, facing, collision proof, movement clock and prediction
deadlines stay in the existing pipeline. Same-target repeats preserve motion,
and missing/expired input or lifecycle changes cannot replay a stale click.
Already-consumed primary presses are ignored and queued presses are cleared on
game-state changes and native menu actions, preventing a closed menu selection
from becoming a second minimap Walk. Native camera restoration and minimap coordinate conversion
remain authoritative.

The config UI label is **Walk-click smoothing (ms)**, default **50**, range
**0–60**. Its persisted `responsive-movement` / `clickSmoothingMs` key is unchanged;
existing saved values apply to both sources. The window starts at native click
observation; rendering and native destination availability still affect total
visible latency. Zero releases the preview immediately once its target is known.

Verification: `.\gradlew.bat build --offline --console=plain --no-daemon` passed
**237 tests**, zero failures/errors/skips. Ten new real-controller/API-double
regressions compare idle walking/running at 0/50/60 ms, moving cardinal/diagonal/
knight retargets, forward true-tile alignment, pre-render old-flag replacement,
unchanged native flags, active and pending same-target repeats, alternating scene
and minimap clicks, start/collision gates, and expired/scene-invalidated input.
Earlier scene-route, camera/input, NPC and model-provider tests also pass. These
checks model native publications; they do not execute the game's minimap UI.

Launch `.\gradlew.bat run` using the
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
development-login guide, with GPU or 117 HD. Compare scene and minimap clicks
from idle and while walking/running, then rapid retargets/reversals, repeated
targets, mixed input and routes around obstacles with True Tile Player Indicators
visible. Check continuous movement and facing without a timeout backstep or
stop/restart. Also check fixed/resizable layouts, a rotated/zoomed minimap, and
right-click menus with the adaptive camera enabled. Try smoothing 0 and 50 ms.
Only the user performs gameplay. Record a fresh trace for any discrepancy,
turn recording off to flush it, and report the timestamp and layout used.

### Short straight/offset Bank and Exchange starts — 2026-10-01

Status: **awaiting in-game confirmation**. The user confirms the preceding
five-tile finish worked, then reports remaining spots around bankers/GE clerks.
Session `1790822916958`, **+10:00**, records these shorter Bank approaches:

| Click time | Origin | NPC footprint | First/final authority | Step age |
| --- | --- | --- | --- | --- |
| 12:48:41.343 | `(7232,6208)` | `(7616,6336)` | `(7488,6336)` at 41.962 | ~619 ms |
| 12:48:46.361 | `(7232,6336)` | `(7616,6336)` | `(7488,6336)` at 46.761 | ~400 ms |
| 12:48:52.421 | `(7232,6336)` | `(7616,6464)` | `(7488,6464)` at 52.760 | ~339 ms |
| 12:49:06.540 | `(7360,5952)` | `(7616,6336)` | `(7360,6208)` at 07.181 | ~641 ms |
| 12:49:17.642 | `(7488,5952)` | `(7616,6336)` | `(7488,6208)` at 17.963 | ~321 ms |

All begin three tiles away on their major axis, with zero, one or two tiles of
offset. They initially queue one staged leg with `started-npc-approach` and ease
before authority arrives. Offset previews can also aim toward a different
diagonal staging point from the real endpoint. For example, 12:48:41 previews
`(7488,6208)` but the actual run ends at `(7488,6336)`; 12:49:06 ends on the
original column using extended interaction reach, rather than moving closer
laterally to the NPC. No earlier five-tile confirmation covered these patterns.

The new exception is limited to fresh idle Bank/Exchange starts, active run,
a matched single-tile NPC, major distance three and minor distance at most two.
It chooses the adjacent major-axis side for offsets zero/one when both route
edges are reversible and collision clear. For offset two it retains the current
minor coordinate, matching the recorded extended-reach stop. A blocked adjacent
side likewise falls back to a clear straight pair before the counter. It does
not predict a detour around an object or enter an unvalidated side.

`MovementPath.anticipateNpcRunPair` then seeds the entire two-step forecast at
ordinary run pace. Both-order collision proof is still required to merge a
knight chord; one valid ordering keeps its logical corner. The existing startup
distance and deadline must allow both steps. The whole pair remains speculative
until actual movement confirms it. New target/mode/action/destination evidence,
an unexpected one-tile first update or timeout retire that assumption through
the existing recovery pipeline. A native flag disappearing just before the
first server endpoint cannot cancel toward the still-idle origin. The trace
decision is `started-npc-run-pair`; normal five-tile finishes still use
`released-npc-arrival`.

Verification: `.\gradlew.bat build --offline --console=plain --no-daemon` passed
**227 tests**, zero failures/errors/skips. Eleven new regressions cover all five
capture timings for both Bank and Exchange, rotated/mirrored offset patterns,
counter fallback, incomplete knight-corridor proof, blocked/irreversible/unsupported
starts, ordinary input and run gates, response expiry, immediate native publication,
early flag withdrawal, and retirement on target/run/action changes. Reworked close
capture checks now expect normal two-step motion rather than easing. The earlier
five-tile and overshoot tests also pass. Collision fixtures are synthetic; the
logs contain endpoint/footprint evidence, not the original collision maps.

Launch `.\gradlew.bat run` with the
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
development-login guide. Repeat the recorded short approaches and their mirrored
positions for Bank/Exchange, including the counter side. Look for normal run pace
through the full approach with no extra staged slowdown, ending at the actual
reachable interaction tile. Recheck the confirmed five-tile case and an already-
in-range click. Turn tracing off to flush and report timestamps for any remaining
slowdown, changed endpoint or added backstep.

### Five-tile NPC normal-speed finish — 2026-10-01

Status: **subsequently confirmed working by the user for the tested case**.
The user explicitly rejected easing:
in this exact situation the character should run at normal pace to the adjacent
destination, rather than slow at the two-tile staging ring. The prior pacing
attempt below is superseded.

Session `1790817139083`, **2026-10-01 +10:00**, contains three matching banker
clicks from `(4928,7360)` toward NPC `(5568,7360)`, with native goal `(5440,7360)`:

- **11:12:24.063**, seq 249: first authority `(5184,7360)` and the native goal
  arrive at seq 271 (**24.498**). `eased-npc-arrival` activates. By seq 299
  (**25.060**) the display is only at `(5282,7360)` and has progressively slowed.
  The final authoritative goal arrives at seq 301 (**25.157**), after which the
  old display resumes normal pace and reaches the goal at **25.438**.
- **11:12:33.304**, seq 709: first confirmation/goal at seq 718 (**33.482**),
  final endpoint at seq 750 (**34.161**). The easing similarly leaves the display
  at `(5254,7360)` just before final authority, then makes it catch up.
- **11:12:41.618**, seq 1124: first confirmation/goal at seq 1137 (**41.878**),
  final endpoint at seq 1168 (**42.542**). The same five-tile geometry and
  rejected arrival-easing decision are recorded.

`MovementPath.releaseNpcArrival` now uses those exact structural conditions:
single-tile NPC five cardinal tiles from the origin, four straight checked
logical steps to its adjacent native goal, run enabled, first matching two-tile
server update, and sufficient unexpired prediction budget. Both final edges
are rechecked forward and backward. The missing final tile is appended as a
prediction, preserving the confirmed prefix and exact fractional position;
the existing movement clock runs through all four steps at normal pace.
This is a limited exception to the two-tile staging cap, not another arrival
easing function or a change to server-visible routing.

The same geometry rule applies to ordinary NPC options, including Bank,
Exchange and Talk-to. Attack remains deferred and is excluded. Walking, other
distances, offsets, blocked final pairs and unsupported footprints keep the
generic policy. The release grants no fresh deadline; changes in target,
destination or run mode retire it. Actions, rejection, scene validation and
timeout still reconcile with authority through the existing path. A withdrawn
native flag retains the established final-endpoint reconciliation ordering.
The decision is now `released-npc-arrival`; `eased-npc-arrival` is historical.

Verification: `.\gradlew.bat build --offline --console=plain --no-daemon` passed
**216 tests**, zero failures/errors/skips. Reworked tests assert constant normal
pacing through the old staging tile and the exact final goal, rather than merely
non-idle motion. Added checks cover all three 11:12 timings, blocked/unexpired
pair checks, retirement on mode/target/destination changes and rebase. Earlier
overshoot cases and close-idle-start easing remain covered. Collision fixtures
are synthetic: the capture records endpoints/footprints, not collision maps.

Launch `.\gradlew.bat run` with the
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
development-login guide. From the same five-tile row/column positions, repeat
Bank and Exchange at the capture's speed settings. Check that the player keeps
normal run pace across the old staging tile and finishes adjacent to the NPC,
without a slowed last approach or an extra stop/step. Repeat from the opposite
side, then recheck a previous six-tile overshoot case and a close click. Record
any remaining problem with its timestamp and turn tracing off to flush.

### Five-tile Bank/Exchange finish continuity — 2026-09-30

Status: **superseded and rejected by the user** after the 2026-10-01 11:12
captures. The easing avoided an idle publication in tests but retained the
unwanted slowdown before the goal. See the normal-speed replacement above.
The user deferred the guard/Attack
follow-up and requested a narrow fix for banker/clerk runs after approximately
19:42. Session `1790761419817` is recorded at **19:43 (+10:00)**, split between
`movement.previous.log` and `movement.log`:

- **19:43:41.097**, seq 64: Bank from `(5952,8384)`, NPC `(6592,8384)`,
  native/provisional goal `(6464,8384)`. First matching two-tile authority
  `(6208,8384)` and that native goal appear at seq 80, **19:43:41.416**.
  The display reaches its reserved-ring endpoint `(6336,8384)` and publishes
  idle at seq 108, **19:43:41.973**. The flag clears at seq 111 (42.035);
  authority advances to `(6464,8384)` and the display resumes at seq 112,
  **19:43:42.096**: approximately **123 ms** after the first idle sample.
- **19:43:52.317**, seq 624: Exchange from `(6720,7616)`, NPC `(6720,8256)`,
  goal `(6720,8128)`. Matching authority `(6720,7872)` and the native goal
  appear at seq 650, **19:43:52.834**. Idle starts at `(6720,8000)`, seq 668,
  **19:43:53.192**; final authority `(6720,8128)` resumes motion at seq 680,
  **19:43:53.437**: approximately **245 ms** later.

Both are single-tile NPCs five tiles away along a cardinal line, with a checked
four-step logical route and a first confirmed two-tile run. The earlier idle-seed
easing only covered the closer one-preview-tile start, so these longer forecasts
still finished their last staged leg early.

`NpcApproach` now identifies Bank/Exchange options once at click time.
`MovementPath.easeNpcArrival` enables pacing only for this five-tile, four-step
straight route, when the first two steps are newly confirmed, run is active,
and the newly observed native destination matches the original goal. Confirmed
prefix legs keep ordinary pacing. Only the final unconfirmed staged leg eases
within its original rate cap/time budget, preserving movement until the final
authority arrives. Its endpoint remains outside the reserved region; no new
tile, route or prediction deadline is added. New authority, replacement, action,
withdrawal or timeout use the existing normal pacing/recovery. Trace decision
`eased-npc-arrival` identifies activation.

Verification: `.\gradlew.bat build --offline --console=plain --no-daemon` passed
**211 tests**, zero failures/errors/skips. Seven new regressions cover both
capture timings, Bank/Exchange/native-evidence gating, all four cardinal
directions, reserved-ring arrival/cancellation/timeout, excluded distances and
offset/walk cases, and one frame-independent budget through the confirmed prefix.
Collision maps are synthetic; positions, footprints and timing come from the logs.

Launch `.\gradlew.bat run` using the
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
development-login guide. Repeat Bank and Exchange from five tiles away in the
same row/column, using the settings from the capture. Check for a continuous run
to the correct final tile without the interim idle and extra step. Recheck the
previous overshoot cases, a nearer click, walking and a diagonal approach. Record
any remaining pause/overshoot timestamp and turn tracing off to flush afterward.

### Guard Attack follow-up — deferred, 2026-09-30

The user reports **no visible in-game improvement** from the preceding attack
approach change and explicitly asks to return to guards/attacking later. This
remains unresolved, including the concern about ranged approach overshoot.
Passing combat-profile/state tests does not establish responsive guard starts
or correct combat stopping in-game. The current Attack attempt remains in the
source; the five-tile Bank/Exchange correction does not enable its pacing rule
for Attack. The later five-tile normal-speed replacement also excludes Attack.

Earlier evidence: session `1790751797453`, 17:03/17:04 (+10:00), contains
`NPC_SECOND_OPTION` clicks whose destination observation expires before native
movement. Examples include 17:03:29.030→29.590 (~560 ms),
17:03:55.510→55.987 (~477 ms), and 17:04:13.367→13.989 (~622 ms).
Those logs lack the clicked NPC footprint and equipped weapon/style, so they
cannot establish why the experimental combat fallback was unavailable in-game.
Use a fresh timestamped trace when the user resumes this follow-up.

### Scene-click true-tile route alignment — 2026-09-30

Status: implemented and regression-tested; the user subsequently confirms it
**worked really well** for scene clicks. The newer minimap extension above awaits
its own confirmation.
Latest session `1790757772537` in `movement.log` spans **18:42:52.537–18:43:28.360
(+10:00)**. Its `trueTileIndicator` values match `true` throughout; the field uses
the same local conversion of the player's world location as True Tile Player
Indicators. The discrepancy is in the displayed forecast's geometry:

- **18:43:08.002**, seq 772: display `(5666,6592)`, true tile `(5440,6720)`.
  The display continues east along the wrong row to `(5805,6592)` at seq 788,
  then enters recovery and moves west at **18:43:08.341**, seq 789.
- **18:43:17.600**, seq 1252: display `(4927,6720)`, true tile `(4928,6592)`.
  The preview continues on Y=6720 to `(5155,6720)` at seq 1278, then reverses
  at **18:43:18.140**, seq 1279. The next true tile `(5184,6592)` arrives at
  seq 1281, after recovery has already begun.
- **18:43:23.602**, seq 1552: display `(5476,6866)`, true tile `(5440,6720)`.
  The forecast continues to `(5643,6950)` at seq 1571, expires at
  **18:43:24.001**, seq 1572, and retraces before the next confirmed step.

The source permits fresh retarget construction from a visible edge anchor that
is not on the server's route. Its `awaitingOrigin` state previously retained
nearby off-route authority without making that progress part of the forecast.
`MovementPath.alignWalkAuthority` now rebuilds an eligible scene Walk forecast
from a newly checked forward authoritative endpoint, then selects a reversible,
collision-checked join from the exact fractional display position to that route.
It retains the frame clock and favors forward joins; it does not snap the body
to the tile marker. Unchanged/opposite updates grant no new prediction time,
and failed/expired joins use the existing bounded recovery. Native interaction
handling and the deferred minimap work remain separate. Trace decision
`aligned-walk-authority` identifies accepted joins.

Verification: `.\gradlew.bat build --offline --console=plain --no-daemon` passed
**204 tests**, zero failures/errors/skips. New regressions cover parallel-row
progress without timeout reversal, a blocked detour with authoritative bends,
both axes/directions, frame cadence, bounded timeout, unavailable connectors,
confirmed-prefix preservation and the real controller's scene-Walk integration.
The controller regression fails with the new alignment call disabled. Two
pre-existing NPC boundary assertions also failed with alignment disabled; their
expected positions were corrected to the established 4/8-units-per-20-ms pace.
Collision fixtures are synthetic: the recordings contain positions, not maps.

Launch `.\gradlew.bat run` using the
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
development-login guide. With GPU or 117 HD and True Tile Player Indicators
visible, repeat moving scene clicks across nearby rows/columns and around the
same obstacles, including quick reversals. Check that the body joins the
true-tile route continuously without a timeout backstep, sideways detour or
stop/restart. Compare a settled idle start and normal walking/running as well.
Record a fresh trace and turn recording off to flush it; report the timestamp
and any remaining route discrepancy. Only the user performs gameplay.

### Close-range NPC preview continuity — 2026-09-30

Status: the user confirms the preceding NPC stopping correction works well, then
reports a remaining close-range issue. This narrow continuity refinement is
**subsequently reported working well by the user**. Close Bank/Exchange clicks
remain a possible later follow-up at the user's request.

The latest recording after the user's approximate 15:50 report is session
`1790747842029`, **15:57–15:59 (+10:00)**, split between `movement.previous.log`
and `movement.log` by rotation:

- **15:57:31.546**, seq 476: banker click from `(6208,8384)`, NPC footprint
  `(6592,8384)`. The checked preview has one tile before the reserved boundary.
  It reaches `(6336,8384)` and publishes idle at seq 491, **15:57:31.845**,
  then holds through seq 507 (15:57:32.164). At seq 508, **15:57:32.229**,
  authority advances to `(6464,8384)` and the display resumes: approximately
  **384 ms** between the first idle sample and resumed motion.
- **15:57:40.508**, seq 923: offset start `(6208,8512)`, same NPC footprint.
  It similarly waits at `(6336,8512)` before the first authoritative endpoint
  `(6464,8384)` arrives at seq 957, **15:57:41.233**. The forecast does not have
  the final approach tile/corner available to bridge that pause speculatively.
- **15:57:48.687**, seq 1330: mirrored offset start `(6208,8256)` receives
  authority sooner, at seq 1345 (15:57:49.024), illustrating why the visible
  pause varies with confirmation timing. The stopping boundary still works.

`MovementPath` now eases only a fresh idle seed with exactly one checked tile
before the reserved ring, starting three tiles from the captured footprint.
The same leg approaches its endpoint continuously within the original speed cap
and prediction deadlines. No extra tile is forecast. A changed authority restores
normal confirmed pacing immediately, including the checked corner to its actual
endpoint; replacement or timeout retires the easing. Longer/queued routes keep
their established pacing, and clicks already in the reserved ring still require
native movement evidence.

Verification: `.\gradlew.bat build --offline --console=plain --no-daemon` passed
**181 tests**, zero failures/errors/skips. Six new regressions cover the straight
and offset capture timings, stopping at the actual reserved-ring endpoint,
frame-cadence/movement-speed independence, normal confirmed pacing and bounded
recovery, and longer/queued-route pacing. Test collision maps remain synthetic;
recorded positions/footprints and timing do not establish all original geometry.

Launch `.\gradlew.bat run` using the
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
development-login guide. Repeat close Bank/Exchange clicks, especially a roughly
three-tile start and one-tile row/column offset. Look for a prompt, continuous
approach without the brief idle/restart while retaining correct stopping. Compare
walking/running, then a longer approach and an already-in-range click. Record any
remaining pause with its timestamp and turn tracing off to flush afterward.

### Extended-range NPC stopping — 2026-09-30

Status: the user reports the NPC early starts work well, but bankers/GE clerks
overshoot. **The user subsequently confirmed the stopping refinement works well**
for the tested interactions; the newer close-range refinement above is separate.
Evidence: `movement.log`, session `1790745936283`, local offset **+10:00**:

- **15:25:48.483**, seq 609: west-side banker click, index 25398,
  start `(5824,8384)`, NPC `(6592,8384)`, logical preview goal `(6464,8384)`.
  The native flag later names that same goal. At seq 653 (15:25:49.367) it clears
  one sample before authority advances to `(6336,8384)` at seq 654. The display
  crosses that actual endpoint and reaches `(6464,8384)` at seq 681
  (15:25:49.941), holds, then returns after prediction timeout around 15:25:50.3.
- **15:26:15.163**, seq 1942: south-side GE-clerk click, index 25400,
  start `(6720,7488)`, NPC `(6720,8256)`, goal/native flag `(6720,8128)`.
  Authority stops at `(6720,8000)` as the flag clears at seq 2002
  (15:26:16.367), but the display reaches `(6720,8128)` at seq 2015
  (15:26:16.623). Both captures overshoot the server endpoint by one tile.
- **15:26:48.186**, seq 3593: diagonal banker approach from `(6208,7744)`.
  The provisional goal `(6464,8256)` refines to native `(6464,8384)`, but
  authority stops at `(6336,8256)`. The old forecast reaches the closer flag
  instead, showing that native refinement also needs the stopping guard.

The NPC footprint and native destination establish a direction/route, not proof
that the interaction requires adjacency. `MovementPath` now caps NPC speculation
at the first logical route tile within two tiles of the footprint. It retains
the full logical route for matching and accepts confirmed steps closer to the
NPC. The cap is applied before constructing/merging run chords and survives
replenishment, native refinement, queued continuation and scene translation.
Already being in that conservative staging region requires native movement
evidence. No per-NPC radius table or claimed universal interaction reach is used.

The controller retains the footprint after native publication. A withdrawn flag
retires further forecast extension, and reconciliation waits for a new endpoint
or settled native state to avoid returning to old authority on an early frame.
A completed staging preview cannot restart merely because a closer native flag
arrives later. It still expires/reconciles under the existing prediction limits.

Verification: `.\gradlew.bat build --offline --console=plain --no-daemon` passed
**175 tests**, zero failures/errors/skips. Eight new tests cover the west banker,
south clerk, diagonal refinement, immediately published native flags, delayed
flags after staging, already-in-range clicks, checked knight/corner confirmation,
and footprint rebasing. Capture endpoints/footprints are recorded; collision maps
are synthetic because the logs do not contain scene collision data.

Launch `.\gradlew.bat run` with the
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
development-login guide. With recording enabled, repeat Bank/Exchange from the
reported positions and other directions, including short already-in-range clicks.
Check that prompt starts remain, the player stops without an extra tile/return,
and ordinary adjacent NPC interactions can still finish correctly. Turn recording
off to flush; report timestamps for any remaining overshoot or added pause.

### NPC Bank/Exchange early approaches — 2026-09-30

Evidence: `movement.log`, session `1790738790364`, local offset **+10:00**.
The user subsequently confirmed the early-start improvement works well, then
reported the arrival overshoot addressed in the newer section above.
The user's approximate 13:25 report corresponds to these two recorded NPC clicks
(Bank then Exchange, as identified by the user; the old trace records option
opcode/index, not option text or NPC name):

- **13:26:41.493**, seq 556, `NPC_THIRD_OPTION`, index **25398**:
  displayed/authority/native positions are `(6080,8128)`, destination absent.
  Observation expires at seq 561 (13:26:41.593). The first changed authority
  `(6336,8256)` and displayed movement appear at seq 583, **13:26:42.076**,
  approximately **583 ms** after the click sample; the destination is still absent.
- **13:26:53.536**, seq 1157, `NPC_THIRD_OPTION`, index **25400**:
  all three positions are `(5952,8128)`, destination absent. Observation expires
  at seq 1162 (13:26:53.636). Authority `(6208,8128)` and destination `(6720,8128)`
  first appear together at seq 1181, **13:26:54.017**, approximately **481 ms**
  later. A longer observation wait alone would not provide an immediate start.

`NpcApproach` now captures the exact clicked NPC using supported menu/actor APIs.
If native input supplies no new destination, an eligible idle start or confirmed
continuation can use the reversible, collision-checked straight-first prefix
toward its footprint, rather than inventing a route through/around a bank counter.
The later stopping refinement reserves its final two-tile approach for authority.
Native destinations take
priority; the forecast uses the same MovementPath limits and reconciliation as
other movement. Moving/despawned/replaced targets invalidate the provisional
forecast. Attack and item/spell actions retain native-destination handling.

The 18 new tests include actual controller updates with a deterministic clock,
both captured timing patterns, late refinement, stale Walk destinations, queued
confirmed corners, timeout/recovery, target invalidation, scene/replacement
cleanup, collision and eligibility guards. The original captures contain no NPC
footprints or collision maps: test geometry is explicitly synthetic. New traces
include `npcMin`, `npcMax`, `approachGoal` and NPC-specific start decisions.

Verification: `.\gradlew.bat build --offline --console=plain --no-daemon` passed
**167 tests**, zero failures/errors/skips. Only the user can verify final behavior.
Launch `.\gradlew.bat run` using the
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
development-login guide, enable recording, and check:

1. Repeat Bank on the banker and Exchange on the GE clerk from the reported
   positions. Look for a prompt visible start and smooth handoff to server movement.
2. Repeat from several directions and farther away, walking and running, including
   beside a counter. Check for wall clipping, overshoot, added backsteps or pauses.
3. Already-adjacent clicks should not manufacture movement. Try replacing an NPC
   click with Walk and an ordinary moving NPC interaction; no stale target chase
   should continue after replacement or disappearance.
4. Recheck an object interaction and a scene crossing. Stop recording to flush and
   report any remaining delay's timestamp. The area-crossing change still needs
   its own visual confirmation.

### Area-crossing continuity — 2026-09-30

Evidence is in `movement.log`, dated **2026-09-29, +10:00**:

- Session `1790689845772`, seq 1121–1147: at 23:51:08.189 the southbound
  preview is at `(7104,1878)`, authority `(7104,2240)`. The scene changes
  33→34 at 23:51:08.221; the destination translates by `(0,5120)` and authority
  advances to `(7104,7104)`. The displayed player then reverses from Y=6992
  to 7104, waits, and starts south again at 23:51:08.709.
- Session `1790689937346`, seq 1193–1220: at 23:52:41.188 the preview is at
  `(6080,1838)`, authority `(6080,2112)`. Scene 35→36 translates the destination
  by `(1024,5120)` and advances authority to `(7104,6976)`. The display reverses
  from Y=6951 to 6976, then waits until forward movement at 23:52:41.729.

The old rebase cancelled speculation before recovery, which discarded the
forward forecast and constructed a return toward authority behind the visible
player. `MovementPath.resumeScene` now translates and revalidates an eligible
overworld preview before reconciling authority. It preserves the exact fractional
position, legal corridors and original bounds; loading does not grant fresh
prediction time. Confirmed recovery also keeps valid queued corners in order.
The controller limits this preview resumption to continuous non-instanced scenes;
world hops and connection loss discard the old path.

`SceneContinuityTest` covers both captured coordinate transitions using explicit
synthetic collision checks, prediction expiry, changed destinations, blocked new
geometry, confirmed corners, arrival, knight chords and world-view rejection.
`SceneCameraTest` verifies the existing world-relative camera focus across a
two-axis rebase, even if native focal coordinates change, and native-mode
restoration after drawing/before input. Trace samples contain neither camera
coordinates nor collision maps and do not measure the loading renderer's freeze.
The ~32–34 ms gaps between boundary samples cannot establish its cause or duration.

Verification: `.\gradlew.bat build --offline --console=plain --no-daemon`
passed all **149 tests**. Only the user can confirm the resulting visual behavior.
Launch with `.\gradlew.bat run` and the [development-login guide](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts):

1. With recording enabled, walk/run across the same boundaries on one click.
   Look for continuous forward movement after loading, without the old backstep,
   turn-around or extra half-second wait. Successful recovery logs `resumed-scene`.
2. Repeat in both directions and on diagonal/knight routes, including a corner
   near a boundary; confirm collision-checked routing and smooth camera follow.
3. Check a teleport, instance/POH entry, world hop and plugin toggle still hand
   off correctly. A long loading interruption may legitimately exhaust a preview
   and require recovery.
4. Report any remaining freeze separately from a backward step/stop, with its
   timestamp, renderer and settings. Turn recording off to flush the capture.

### Evening route stability — 2026-09-28

The user confirmed the turning/scene-click smoothing feels much smoother, then
reported route stalls and backtracking at 21:45:55, 21:47:10–15, about 21:48:00,
21:50:40 and 21:51:40–55 (+10:00). Session `1790595935156` covers all five
windows. The user subsequently reports the input-latching and fresh-click
connector corrections **worked really well in-game**. Detailed sequences and
the precise scope of each correction are in
[the route checkpoint](agent-work/route-stability-2145/CHECKPOINT.md).

Launch with `.\gradlew.bat run` and the development-login guide above. Keep the
turning and smoothing settings that felt good, enable recording, and test:

1. Several short east/west changes with a final click and no further input.
   The latest click should not be replaced by an old destination during settling,
   followed by a pause and a long catch-up run.
2. While moving diagonally, click a tile below/above the near side of that
   diagonal. On clear ground, the player should connect through the nearby checked
   side rather than go back to the diagonal's old starting tile.
3. While moving west, click farther west and a little south, then mirror/rotate
   the route. The player should not backstep merely to begin another knight move.
4. During a nearly completed knight-shaped run, click back along the destination
   column/row. Watch for the old long retrace through the knight's origin and for
   a brief turn-back after reaching the new target but before server confirmation.
5. Repeat beside walls, with rapid clicks, then ordinary runs, a tree/herb
   interaction and a region crossing. Necessary obstacle detours must remain legal.

Turn recording off afterward and report any remaining issue's time. The logs
contain no collision maps; the automated fixtures use recorded endpoints with
explicit synthetic collision checks, rather than asserting the entire original
scene was unobstructed.

### Turning and scene-click smoothing — 2026-09-28

Status: **confirmed smoother by the user in-game**. `turnSpeed` now caps
custom turning throughout a movement leg and at arrival. A held short arrival
finishes its turn smoothly rather than snapping or freezing before its heading.
There is one turn budget per prepared frame, shared by movement and native-facing
targets. This controls facing speed, not walk/run positional speed.

Automated verification: `.\gradlew.bat build --offline --console=plain --no-daemon`
succeeded with **123 tests, zero failures/errors/skips** (2026-09-28). Added cases
cover short-leg arrival turns, held/native heading transitions, fractional pacing,
click settling/replacement/expiry, scene cancellation, and continuous confirmed
movement with the existing replan deadline. These checks do not verify visual feel.

The new **Scene-click smoothing (ms)** setting defaults to **50**, range **0–60**.
It delays only release of scene Walk previews; existing movement and server
updates continue. The window is measured from the click. Frame timing and native
destination availability still affect visible response, particularly for an
idle start. Setting **0** restores immediate preview timing while retaining the
turn-speed cap. Minimap input timing and red-click observation are not delayed.

After restarting with `.\gradlew.bat run`, use the development-login guide above:

1. Start with movement speed **1.0**, turning speed **30**, and smoothing **50**.
   Compare smoothing **0**, **30**, and **50** during scene-click direction changes.
   Check whether the brief settling window feels responsive and less twitchy.
2. Lower turning speed to **10**, then try **30** and **60**. Test nearby clicks,
   cardinal/diagonal reversals, and a click just before arrival. All custom turns
   should visibly follow the selected rate, including tiny final legs. Check for
   sideways freezes or an unwanted return to the previous heading while idle.
3. Rapidly click different directions and repeat the same tile. Include walls,
   corners and knight routes. Confirm continuous position and legal connectors;
   the smoothing window should not cause a snap or an obsolete long detour.
4. Test ordinary confirmed running, right-click Walk here, tree/herb interactions,
   action-facing, and a scene transition. Check arrivals and native-body handoff.
5. Record 30–60 seconds if twitchiness or routing problems recur, noting the
   time and settings. `settling-click` distinguishes the intentional short wait
   from an unavailable route or missing destination; `retargeted-walk` identifies
   the ensuing moving replan. The later route corrections above address the
   specific backtracking reported after this smoothing change.

### Regression recovery check

The two recent animation/idle-alert patches have been reverted. The player
presentation again uses the original native model builder and pose clock,
including its idle-selector override. UG launcher idle-alert compatibility
remains unresolved; false idle alerts may return with the restored behavior.

After restarting the development client, first check short runs from fully
settled idle, repeated stop/start runs, and continuous running. Then check the
two narrowly corrected path cases:

- Diagonal out, diagonal back onto the original line, then return along that
  line. Repeat the last click just before arrival. Confirm the old diagonal is
  finished rather than retraced.
- Walk/run around a wall corner in both directions. A blocked diagonal may use
  a legal two-cardinal-step bend; it must not cut across the wall.

The automated route tests cover those cases, pending prediction correction, and
blocked edges. They do not verify the native character animation or UG alerts.

### Red-click approaches

The user confirmed that object-footprint handling resolved the reported 18:36
tree detours and later reported that the extension also fixed the herb-run
plant/compost loop. The 21:39/21:41/21:43 herb-run captures exposed missing
footprint handling for item/widget use on objects; the 21:40 use was
incidentally safe because the player was already cardinally adjacent to the
anchor tile. The lookup now covers both object options and object-use actions.
Details, trace evidence, tests and the user's confirmation are in
[HERB-PATCHES.md](agent-work/evening-1952/HERB-PATCHES.md). Broader checks from
different patch sides and on other multi-tile objects remain useful but were not
claimed by the user's confirmation. Adjacent object uses report
`at-object-boundary` with populated `objectMin`/`objectMax` and the current tile
as `approachGoal`.

With **Responsive starts** enabled, test a distant object interaction (such as
opening a bank), talking to an NPC, and picking up a ground item. Repeat around
an obstacle and while already running. Check that the character approaches
promptly, stops at the correct place, faces the target, and performs the normal
action. Also test an adjacent interaction, a moving NPC, switching targets,
switching back to Walk here, and a ranged action that needs no approach.

Only a new destination published by the native client can arm a red-click
preview. If none is observed, the interaction uses confirmed movement. If the
direct destination route is blocked, an interaction may predict a reachable
cardinally adjacent tile instead of requiring entry into the object itself.
This is a bounded approach prediction, not knowledge of the object's permitted
interaction sides; server confirmation still determines the final route.
Starting an action or spot effect cancels outstanding red-click prediction.
Record a trace if an approach still waits or overshoots; include which
interaction was used. New samples include `interaction=true/false`.
For tree and other object clicks, check that a later native approach destination
does not leave the displayed player going past its final side and walking back.
The trace records `trimmed-native-approach` or `redirected-native-approach` when
the forecast follows the updated endpoint; ordinary clicks and running should
remain smooth. Specifically repeat the 21:15 tree routes from north/east and
along the westbound knight chord: the revised destination can arrive with the
first server step, so the turn should continue along the occupied checked edge
without a position snap or a long turn-back.

Specifically repeat bank booths versus doors, including different sides and an
already-adjacent click. Check that the player holds its arrival heading while
the hidden native player catches up, then faces the interaction. Repeatedly
click the same yellow destination mid-run and then switch to a different tile:
same-destination clicks should retain the preview, while a genuinely changed
destination should replace it.

### Repeated reversals and minimap clicks

**Status:** The user reports that arbitrary-direction **scene clicks** now work
well. **Minimap clicks still need a separate follow-up; the user explicitly
deferred changing them.** Do not count minimap behavior as validated by the
scene-click retarget implementation.

For the 18:55:48 report, repeat short diagonal out-and-back clicks. Click the
starting tile again while visibly between tiles; the player should reverse on
that same checked diagonal instead of finishing the unwanted step first. Check
all four diagonal directions, one-tile walks and longer runs, plus walls where
the reverse edge is blocked. Knight chords and turns onto another diagonal still
require their existing collision-checked handling.

Capture `1789960172543`, cycle 61814, shows a visible horizontal reversal being
deferred because authority is one row away after a bend. Test a short diagonal
or corner followed by a horizontal reversal while the native player catches up,
then transpose it to vertical movement. The reverse should start immediately
when the nearby authoritative tile has a checked connection. Also test beside
a wall: disconnected rows must not allow a shortcut. Repeat mixed-direction
clicks for 30–60 seconds and watch for the late `waiting-for-confirmed-tail`
delay. The opening diagonal/turn-back in this capture remains visually
unconfirmed; note the approximate time if that behavior repeats.

The subsequent capture (`1789956595877`) showed an old westbound update arriving
about 80 ms after a new eastbound click. Immediate rejection caused a turn back
and pause. The regression replay now includes several clicks and server updates
in their recorded order. Test several quick reversals followed by one final click
and no further input: the final approach should continue through an old tick,
without a brief reverse/stop. Repeat north/south, and record 30–60 seconds.
An unconfirmed reversal still expires; this correction does not grant old
opposite-direction updates a fresh prediction deadline.

The 2026-09-21 capture starting at 11:38:14 exposed delayed-confirmation cases.
Repeat the back-and-forth test for 30–60 seconds, including clicking the same
opposite endpoint twice when the visible player is still going the wrong way.
Check that it does not overshoot to the previous forward endpoint before
returning, and that movement resumes as server progress approaches the reversed
route. The finite prediction gap/deadline still applies if confirmations stop.
Include minimap repeats, and verify that repeated clicks toward the route you
are already following do not interrupt it. Keep red-click bank/door behavior in
the check, since unchanged destinations are accepted only for eligible yellow
re-clicks.

1. Repeat the 22:09 back-and-forth route for at least ten changes, first east/west
   and then north/south. Click the opposite direction while visibly between
   tiles. A qualifying straight reversal should move back immediately, rather
   than finishing the old forward queue or snapping to a tile centre.
2. Use minimap clicks from idle and while moving, then alternate minimap and
   scene clicks. Repeat in fixed and resizable layouts. Re-clicking the same
   destination should retain the current preview.
3. Check nearby corners, knight-shaped moves, and blocked routes. The straight
   reversal shortcut must not skip a required corner or cross a wall.
4. Record the test and turn recording off afterward. `started-reversal` identifies
   the new fast path; `queued-continuation` identifies a click using the existing
   continuation route. Prediction still expires if the server does not confirm
   progress; repeated clicks alone do not extend that deadline.

### Obstacle prediction and tight-space direction changes

Enable **Responsive starts** (the existing `responsiveStarts` setting). Record
each check with **Record movement trace**, then turn recording off to flush it:

1. From idle, click ground beyond a pillar or around a wall corner. The visible
   player should start following a legal detour before the next server update.
2. Repeat the tight-space routes from the 19:40 and 19:43 captures, re-clicking
   while moving. A new preview follows the already-confirmed movement without an
   intermediate idle frame; it does not replace an unfinished confirmed corner.
3. Reverse direction and click several different destinations rapidly. Confirm
   no trip through an obsolete preview, wall clipping, position snap, or revived
   diagonal-retracing bug. Repeated unconfirmed clicks must eventually stop or
   reconcile, rather than moving indefinitely ahead of the real player.
4. Try minimap and right-click Walk here, a blocked/unreachable destination, and
   a normal object interaction. Also check short open-ground runs and their
   original animations.

The existing captures start at 19:40:26 and 19:43:45 local time on 2026-09-20.
Both contain confirmed movement only. They have no click/destination fields or
collision maps, so they cannot prove why a particular click was deferred. The
new `clickCycle`, `destination`, and `startDecision` trace fields identify that
distinction. Collision regression fixtures use synthetic walls around recorded
route coordinates, not a claimed reconstruction of the full scene.

1. Start at movement speed **1.0**. Walk and run from fully settled idle, then
   re-click while the visible player is stopped but the native player is catching
   up. Repeat with the minimap and right-click → Walk here.
2. Run long routes with only one click. Confirm that ordinary movement uses the
   same smooth pacing as a responsive start, with no switch to another animation
   system or periodic idle frame.
   Repeat with the UG launcher's walking plugin: the run pose should no longer
   flash idle at each confirmed two-tile endpoint. Check that movement actually
   stops and idles at the final tile, on an action and across a scene change.
3. Try all eight knight directions: two tiles on one axis and one on the other.
   Clear routes should be straight, with the body facing that trajectory. Try a
   wall-adjacent route as well; collision-blocked chords must retain their bend.
4. Change speed to **0.9**, **0.8**, and **1.1** while moving. Check for a continuous
   position and the expected pace change, with no snap or restart.
5. Re-click near the end of a segment, reverse direction, and make a red object/NPC
   interaction. Confirm no unnecessary trip through an old destination and no
   stationary running or late stop-facing spin.
6. Test run toggles, a Ctrl-modified route, ordinary attacks/casts, and an agility
    obstacle. Check native animation timing and attached effects.

## Camera, menus and overheads

1. Adjust zoom using the normal client/RuneLite camera controls, including a
   resized window. Confirm following height, pitch/yaw and scroll behavior.
2. Right-click objects and NPCs, select Walk here from a menu, click inventory and
   spell widgets, and walk using the minimap. Confirm expected targets/actions.
3. Enable/disable adaptive camera and the optional marker. Exercise follow speed,
   following distance, snap distance and marker offsets.
4. Check overhead prayers, skulls if applicable, overhead chat, four hitsplats and
   HP bars. Toggle Interface Styles HD health bars. Verify native/custom handoff
   displays overheads once at the correct position.

## Lifecycle

1. Cross a region boundary while moving, teleport, enter/re-enter a POH, and hop
   worlds. Confirm no stale model, wrong-scene coordinate, blank body or camera
   jump added at the handoff.
2. Toggle Animation Smoothing, change equipment, and toggle the plugin while both
   idle and moving. Confirm native pose selectors and camera are restored when
   disabled.
3. Check a crowded tile with Original player when aligned enabled.

## Capturing a problem

Enable **Movement → Record movement trace**, reproduce the problem for 30–60
seconds or leave it on for a longer test, then turn it off to flush the final
samples. Recording now continues until switched off; the former two-minute
cutoff has been removed. Re-entering after logout starts a fresh session if the
setting is still enabled.

The file is:

`%USERPROFILE%\.runelite\responsive-movement\movement.log`

The writer retains `movement.previous.log`, then `movement.previous.2.log`
through `movement.previous.7.log`, about 64 MiB total including the current file.
Retention is size-based, not a guaranteed time window; turn recording off after
testing so old incidents are not eventually rotated away. Lines include local
`time=` with UTC offset, `clickAction=`, and `clickTarget=` to distinguish scene,
minimap, and object/NPC interactions. Report the time and visible behavior.

### 2026-09-21 evening report coverage

Session `1789980943765` in the old recorder contains 18:55:43.765 through
18:57:43.755 (+10:00) only. The recorder stopped despite the toggle remaining on.

| Reported time | Available evidence / status |
| --- | --- |
| 18:55:48 strange movement | Captured: diagonal return waited for the old leg; focused reversal patch awaits visual confirmation. |
| 18:58:15 strange movement | Not recorded; needs new capture. |
| 18:59:15 stopping/starting | Not recorded; needs new capture. |
| 19:00:55 zig-zag movement | Not recorded; needs new capture. |
| 19:02:15 character bug | Not recorded; needs new capture. |
| 19:03:30 Forester fire red clicks | Not recorded; needs new capture with interaction details. |
| 19:04:45 unspecified issue | Not recorded; needs new capture and description if it repeats. |
| 19:09:30 click delay | Not recorded; needs new capture. |
| 19:11:25 strange route | Not recorded; needs new capture. |

A successful build or JVM launch is not visual confirmation of this extraction.

### 2026-09-22 evening report coverage

Session `1790070725637` spans two rotating files from 19:52:05.637 to
19:59:00.6 (+10:00). One sample per ~20 ms client cycle.

| Reported time | Outcome |
| --- | --- |
| 19:52:25 movement not fluid | A perpendicular click was deferred while the old movement continued. The bounded redirect below targets eligible unconfirmed previews and is pending visual confirmation. The endpoint stop while the hidden native player catches up is expected, not by itself the bug. |
| 19:53:20 onwards red clicks not instant | **Unresolved.** NPC third-option clicks (targets 25398/25399) have `destination=-1,-1`, even in samples where confirmed/native movement is occurring. The destination-based predictor therefore has no observed approach tile. The 2026-09-27 Bank follow-up below identifies one use of target 25398; it does not establish every earlier target/action or why native movement begins late. |
| 19:54:25 strange route/movement | Stale preview continued west ~2.5 tiles after an opposite click, then reversed. The bounded redirect targets this. |
| ~19:56 corner-click delay | `continuation-unavailable`; the redirect helps eligible unconfirmed non-collinear previews, but confirmed prefixes and unsupported connectors still defer. Needs retest. |
| 19:57:45 clicks not instant | No deferred input found near that second; the nearest click started in ~17 ms. Inconclusive. |
| 19:58:30 clicks not instant | An opposite click was deferred to `waiting-for-confirmed-tail` then `continuation-unavailable`; the redirect targets the eligible non-collinear case. Needs retest. |

Collision maps are not recorded, so the trace cannot prove a specific route was
collision-free or collision-blocked.

### 2026-09-27 banker Bank follow-up

The user first clicked one ground tile, then left-clicked Bank on a banker and
reported a pause. In session `1790496242718` (+10:00), ground WALK at
18:04:05.397 (seq 133) published a destination and started a preview by
18:04:05.421 (seq 134). Banker `NPC_THIRD_OPTION`, target 25398, was armed at
18:04:08.259 (seq 276) from settled idle. `spot=false`, `action=-1`, and
`inputPending=true`: this was not an ineligible click. The destination stayed
`-1,-1` until the 100 ms observation expired at 18:04:08.362 (seq 281).
The first destination, `6464,8384`, appeared at 18:04:08.598 (seq 293),
**together with** the first authoritative step and rendered movement, ~339 ms
after the click. The resulting movement was confirmed, not an early preview.
This accounts for the missing instant *plugin* start, but cannot establish why
native movement itself began then or that extending the observation deadline
would help. See `agent-work/evening-1952/CHECKPOINT.md` for the full timeline.

The follow-up session `1790497053287` (18:17:33.287–18:18:10.163, split
across `movement.previous.log` and `movement.log`) supports the user's
object-versus-NPC comparison in that recording. Object first-option clicks at
18:17:35.734, 18:17:39.956 and 18:17:49.029 started `phase=preview` as
soon as their native destinations were observed; visible movement followed in
~15–20 ms. Three NPC first-option clicks on target 17956 had no destination
during the 100 ms observation. Their destinations arrived with the first
confirmed movement ~237, ~179 and ~461 ms after the respective clicks.
The final click was at 18:18:05.208 (seq 1581); the position remained fixed
through 18:18:05.646, then destination and movement appeared at
18:18:05.669 (seq 1604). Hover-label colors are a user observation, not a
trace field. These samples do not prove a universal object/NPC rule or a safe
early NPC route; a longer observation window alone would not have preceded
the first server step in these three cases.

### Historical bounded-preview redirect (superseded by general scene retargeting)

A newly observed yellow click that is neither an exact reverse nor an appendable
continuation could redirect an unconfirmed preview: the player travels the
occupied checked edge to a checked endpoint, then follows a freshly checked
route. Interaction approaches keep their existing handling, the exact sub-tile
position is preserved, and the hard deadline is not renewed.
Confirmed prefixes remain intact. Both directions of the occupied connector are
checked so timeout/rejection can recover, and the current gap to authority is
bounded. Route choice measures actual edge distance, including two-tile knight
chords, rather than counting queued legs.

Test: while running, click a destination behind or perpendicular to the current
preview (not a straight reverse). Confirm the player turns from the exact visible
sub-tile position without a snap, does not continue to the old destination, and
reaches the new one when the server confirms. Repeat with rapid alternating
clicks, a blocked reverse edge, a destination with no legal connector, and a
stale server tick in the old direction. In these older captures,
`startDecision=redirected` identifies that path. Current fresh moving scene
clicks use `retargeted-walk` where eligible; see the current status and
[architecture](ARCHITECTURE.md).
