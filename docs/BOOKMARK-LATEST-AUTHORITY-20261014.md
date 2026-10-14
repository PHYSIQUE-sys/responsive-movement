# Latest 20:27--20:33 bookmarked movement

Status: **user-confirmed working well; full build passes 763 tests with zero
failures/errors/skips.**

The user reports both the preceding 19:05--19:22 Walk-detour changes and this
20:27--20:33 follow-up are working well. The newer confirmation supersedes this
review's initial pending status. It confirms the user's tested experience, not every
possible collision layout, setting combination or renderer. This review is limited
to the newest marked movement, with the earlier confirmed changes as baseline.

## Evidence and scope

All **17** new bookmarks found at initial inspection belong to session
`1791969910513`, world 390, world view 0, scene generation 1, plane 0, base
`(3120,3456)`. This differs from the earlier review's scene/base; its collision
crop must not be reused as if it described these positions. Times below retain
the captured **+11:00** offset.

| Bookmark | Timestamp | Nearby evidence |
| --- | --- | --- |
| #7407 | 20:27:35.411 | #7046 publishes a new west destination, then #7051's delayed northwest authority selects a return through the preceding northern corner before travelling south to the latest goal. |
| #8770 | 20:28:02.020 | #8640/#8644 show a new southbound approach rejoined through old horizontal construction geometry after preceding-click authority arrives. |
| #10085 | 20:28:27.382 | #10004/#10011: new west goal, followed by perpendicular old south authority; the newer preview is dropped, returns to that old endpoint, stops, then restarts on the next real endpoint. |
| #10262 | 20:28:30.833 | #10189/#10195: the eastward counterpart of that perpendicular-old-step cancellation. The final #10208 east start itself is observed promptly. |
| #10455 | 20:28:34.557 | #10375/#10381 show a Walk join redirected through preceding diagonal authority, followed by the #10389 return. Native/displayed positions are materially different in this window. |
| #11409 | 20:28:52.941 | #11341 replaces a southeast goal with west; the preceding southeast endpoint arrives before final northward authority. The off-route join can introduce an obsolete corner. |
| #11580 | 20:28:56.291 | #11498/#11500 have a new east goal followed by perpendicular preceding south authority, another stop/return/restart case. |
| #11905 | 20:29:02.571 | #11836's latest goal shares the new X coordinate with the preceding endpoint but not its Y; authority reconciliation can add a vertical excursion. |
| #12194 | 20:29:08.283 | #12056's knight return is accepted promptly, with a bounded checked connection and later real confirmation; no separate late destination-observation failure is established. |
| #12977 | 20:29:23.810 | Reciprocal knights and the shorter #12853 diagonal return include legitimate new-click reversals and small construction connectors; turning remains capped. |
| #14154 | 20:29:46.672 | Repeated #13844--#14066 knight changes expose construction/middle-tile joins. #14066's new northeast goal starts with a small southward segment before its onward diagonal. |
| #14424 | 20:29:51.939 | #14348/#14352 show a latest northwest goal joined to delayed southeast authority through preceding corridor/construction geometry. |
| #15894 | 20:30:20.259 | #15780 approaches the old true goal just before prediction expiry, then preceding authority arrives after the expired forecast. Recovery and the next authoritative restart remain bounded behavior. |
| #19075 | 20:31:22.171 | #18928/#18933: a new west/north destination is rejoined through a preceding southeast endpoint, producing a side-corner excursion. |
| #19352 | 20:31:27.522 | #19247's whole-knight cubic starts at `(7588.822,6235.178)` with a first control at `(7611.478,6212.522)`, initially south of a newer northeast goal. |
| #24822 | 20:33:13.694 | Repeated knight/diagonal joins and returns with arrival slowdown off and ahead strength 10%; the last #24740 return confirms normally. |
| #25579 | 20:33:28.444 | #25337's northeast replacement still uses a prior knight middle connector with arrival slowdown off, matching the authority-join geometry family. |

Most bookmarks are submitted after the symptom/arrival. Their stationary marker
positions are not themselves evidence of a stalled route. The surrounding input,
native publication, route and position samples were examined instead.

The first fifteen markers use speed **5.9**, catch-up **on / 15%**, ahead slowdown
**on / 13%**, arrival slowdown **on** with **0.2 tiles / 50%** then **1 tile / 49.8%**,
and click smoothing **0 ms**. The last two turn arrival slowdown off and use ahead
strength **10%**. This is further evidence that the route family is not restricted
to arrival slowdown. The captured explicit destination changes are generally seen
within approximately one client frame; perceived delayed responses can occur later
when a preview is rejected or a checked construction connector is traversed.

Click and authority/destination transition times for all seventeen windows were
preserved in `src/test/resources/com/responsivemovement/bookmark-authority-20261014.txt`.
Overlapping sequences share fourteen settled-start warmups, including preceding
clicks needed to reproduce their route/deadline state. Native coordinates are sparse
observations at transitions, held between transitions by the API double. They are
not a complete interpolation/facing/pose/model-renderer replay.

Logs continued rotating during review. A 27-by-14 collision crop (scene X 36--62,
Y 43--56) was retained from 424 later same-scene clicks, #10208--#25337. Earlier
windows use that explicitly identified collision double, not a claim that every
early click's collision map survived. Cells outside the crop are unavailable/blocked
in replays. Focused geometry tests additionally use explicit open or closing-edge
collision doubles. Positions use local units; tile centres are `(128*x+64,128*y+64)`.
View/scene/base identity and native versus authoritative coordinates remain distinct.

## Corrections in MovementPath

1. **Forward Walk authority joins reuse checked local departure geometry.**
   A preceding-click endpoint can count as forward in one axis while its connector
   turns away from the latest goal in the other. Walk-only forward reconciliation
   now considers the same proved local squares/shared boundaries used by fresh
   Walk replacement, and prefers a contained checked candidate within the existing
   one-tile travel allowance. If the occupied knight already proves the entire
   straight subsegment, its original corridor is retained, including middle-tile
   authority/confirmed-prefix evidence. Native object/NPC refinements keep their
   own join entry point and confirmed-prefix guards.
2. **Perpendicular stale authority retains an explicitly owned fresh Walk.**
   `walkForecast` is click-scoped path evidence, not a route or clock. Matching
   logical/construction-origin authority is still recognized first. A perpendicular
   off-itinerary step can then use the existing bounded stale-reversal clipping
   instead of discarding the new Walk and adding a return trip. Authority is updated,
   but neither deadline is renewed and no extra movement credit is granted. Native
   interaction forecasts do not inherit this Walk-only rule; expiry/scene/collision
   guards remain active.
3. **Local turn curves keep useful tangent continuity without an old-axis bow.**
   For a checked square/knight leg already ending inside the displayed/latest-goal
   interval, cubic controls are projected into that interval. The retained forward
   tangent component can still produce a smooth turn. Both projected controls must
   pass the original convex corridor and forecast-gap checks, with nonzero endpoint
   tangents; otherwise the checked straight join remains available. The same exact
   arc-length integrator and capped facing clock own the result.

These corrections add no server-visible movement, inputs, primary animation/frame
writes, new prediction time, retained posed meshes or presentation owner outside
MovementPath. Model/camera/render-snapshot/logging/config/lifecycle contracts remain
the baseline. Existing interaction/door work was preserved.

## Verification

The two focused authority regressions failed before correction. The whole-knight
partial-axis test uses a representative fraction to isolate the captured first-control
policy. Additional guards cover native-interaction isolation, all twelve directed
square edges, deadline expiry, supported scene translation and unchanged fractions.

The follow-up adds **64,340 sweep/replay scenarios**:

- **55,296 local cases:** all 96 speed increments, all eight independent
  catch-up/ahead/arrival toggle combinations, captured/zero/maximum pacing strengths,
  skipped/default/oversized slow arrival stages, three mechanisms, walk/run forecasts
  and 8/20/33/100-ms cadences. Checks include route interval, collision, movement
  budget, retained fraction, zero-time preparation and deadline ownership.
- **84 captured-setting replays:** all seventeen markers in fourteen shared windows,
  three cadences and scene/minimap input APIs. Specific authority windows check for
  obsolete-corner/lateral excursions, alongside movement/facing caps and final holding.
- **8,064 replay settings cases:** all windows, twelve representative speeds including
  minimum/maximum and fine increments near normal, all eight pacing toggle combinations,
  three cadences and both input APIs. Slow confirmed debt is drained rather than
  discarded to force an artificial endpoint match.
- **896 live-setting cases:** all windows and pacing toggles, scene/minimap APIs,
  smoothing 0/50/150/300 ms, live 0.5/10 speed changes, 0/50 pacing strengths and
  aligned-native presentation toggles. Settings do not reset position or add another
  preparation budget.

The full build initially caught loss of an occupied-knight proof in the existing
#499 moving-Attack guard. The join was corrected to retain that original proof when
it contains the complete subsegment; the existing guard was preserved unchanged.
The complete `.\gradlew.bat build --offline --console=plain --no-daemon` now passes
**763 tests, zero failures/errors/skips**, including the prior circle/knight sweeps,
combat, interaction, door, pacing, native-model and cleanup suites.
`git diff --check` passes.

The expiry/recovery in #15894 is not relabelled as a repaired input-delay bug or
removed by extending speculative budgets. Some direction changes follow actual
replacement clicks or required checked corners. Native-renderer visual behavior
and every possible collision/settings combination still need user observation.

## Runtime confirmation and optional retest

Launch `./gradlew run` (`.\gradlew.bat run` on Windows), using RuneLite's
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
development-login guide. Only the user performs gameplay.

Recheck short perpendicular redirects, rapid reciprocal knights and diagonal turns
while preceding movement is still catching up. Look for prompt continuation toward
the newest click without an old-corner excursion or unnecessary return/stop/restart,
and for smooth turns without the brief old-axis bow. Repeat at the captured settings,
with arrival slowdown off, with pacing toggles changed independently and while changing
speed. Recheck ordinary circles, obstacle corners and Walk-to-Attack transitions.
The user subsequently confirms these follow-up changes are working well. The
scenarios above remain useful optional retests; they do not imply that every listed
setting/collision combination has been individually confirmed in game.
