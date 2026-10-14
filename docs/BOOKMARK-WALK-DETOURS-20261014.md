# 19:05--19:22 bookmarked Walk detours

Status: **user-confirmed doing pretty well; the 752-test checkpoint passed with
zero failures/errors/skips.** The subsequent user report supersedes the initial
pending status. The [newest seventeen-marker review](BOOKMARK-LATEST-AUTHORITY-20261014.md)
is a separate follow-up with its own verification and confirmation scope.

This review is limited to movement leading into and immediately after the supplied
bookmarks. Earlier confirmations, including the separately reported native Walk
observation/startup fixes and 652-test build, remain valid for their tested scope.
This is a newly reported routing regression, not a reopening of historical bugs.

## Evidence reviewed

Seven markers were found in the managed rotating traces, all in session
`1791964863471`, world 390, scene generation 6, view 0, plane 0, base `(3120,3432)`.
Timestamps below use the captured UTC offset, not the chat's minute precision.
The review used the nearby clicks, authority/destination changes, rendered points
and route snapshots. Most markers follow the objectionable click by one or more
seconds, so the stationary marker itself is not the offending movement.

| Bookmark | Captured timestamp (+11:00) | Relevant nearby movement |
| --- | --- | --- |
| #12051 | 19:05, from chat only | Already rotated out before the review; no route finding can be assigned from the submission message alone. |
| #51513 | 19:18:11.925 | Reciprocal knight/Walk replacements near clicks #51286/#51317 use the preceding corridor's construction/middle anchors. The marker is after arrival. |
| #53455 | 19:18:50.526 | Near #53303/#53382, diagonal/knight side-anchor joins turn back through old construction geometry; the local cubic at #53384 retains an initially westbound control on a newer east/north departure. |
| #53918 | 19:18:59.682 | At #53715, a southbound cardinal fraction receives a northwest destination `(6848,9920)` but first finishes the old corner `(7232,9536)`. The next diagonal endpoint is `(7104,9664)`. |
| #55606 | 19:19:32.175 | Repeated knight/cardinal changes near #55484/#55509 reuse preceding chord origins before continuing north or reversing south. The final display is already at `(6720,9664)` when marked. |
| #59669 | 19:20:53.034 | Diagonal/knight-to-cardinal and return joins near #59378/#59447 show old-side construction connectors; the final return reaches `(6720,9536)` before marking. |
| #61676 | 19:21:32.425 | #61562 reverses a southeast knight toward `(6720,9920)`, exposing old-side connector selection with both arrival and ahead slowdown off. |
| #63114 | 19:22:00.935 | #62819 and later reversals show the same connector family with both slowdowns off, ending with the confirmed return to `(6720,9408)`. |

The first five retained markers have movement speed **5.9**, catch-up **on / 15%**,
ahead slowdown **on / 13%**, arrival slowdown **on**, first stage **0.2 tiles / 50%**,
final stage **1 tile / 49.8%**, and click smoothing **0 ms**. The last two keep those
values but turn both ahead and arrival slowdown **off**. Original player when aligned
is off, interaction-arrival facing and adaptive camera are on.

This rules out arrival slowdown being necessary for the reported connector symptom.
Pacing changes alter the fractional point at which a new click meets an old route;
the construction-anchor and tangent rules must remain sound at those different points.
The traces do not establish the exact commit at which the regression first appeared.

Live logs continued rotating during the review. The two off-setting windows were
preserved as compact click/authority/destination-transition fixtures in
`src/test/resources/com/responsivemovement/bookmark-walk-detours-20261014.txt`.
Their complete 11-by-11 collision crop (scene X 48--58, Y 72--82) agrees across all
24 retained clicks; cells outside it are blocked/unavailable in the replays.
Native positions in that compact fixture are observations at transitions, held by
the API double between transitions. They are not a complete native-frame replay.
The earlier windows' local departure regressions use explicit open geometry and
representative fractions, not an assertion of a fully preserved native timeline.

## Correction

`MovementPath.retargetWalk` still constructs straight-first logical itineraries and
bounded forecasts through the existing pipeline. Its Walk connector candidates can
now join a first cardinal/diagonal step through a completely checked local square,
instead of unnecessarily reaching a previous click's corner first. A crossing from
an occupied knight/diagonal corridor retains a separate proof for each subsegment.
Both diagonals and all four perimeter edges must be clear in both directions.

For a reversal, an available checked candidate whose endpoints stay between display
and the latest goal can beat an obsolete side anchor within the existing one-tile
connector allowance. The established authority-square departure and still-forward
cardinal policies retain their guards. Unsupported/blocked joins keep checked corners
or recovery; this is not permission to ignore collision or draw an arbitrary chord.

A local **square** cubic whose endpoint is already in that direct display/goal
interval must also keep its controls there. It cannot borrow the preceding click's
tangent to introduce a new lateral excursion. Existing whole-knight curve behavior
and the prior circle cadence/collision/deadline regressions remain covered.

All fractions, route/confirmation indices, original deadlines, pending construction
origin, stale-tick handling and movement time remain in MovementPath. No input,
server route, camera, primary action clock or model-provider change is introduced by
this correction. Existing interaction/door work in the checkout was preserved.

## Verification

`BookmarkedWalkDetourTest` includes regression assertions for immediate direction
and connector shape, plus all twelve directed square-edge proofs, collision changes,
scene translation, timeout recovery, rotations/reflections and same-time cadence
comparisons. The initial cardinal/diagonal and knight/cardinal regressions failed
before the correction; the additional square-tangent case also reproduced an
old-direction excursion during development.

`WalkDetourSettingsSweepTest` adds **45,312 settings scenarios**:

- **41,472 local departures:** all 96 speed slider increments (0.5--10.0), all eight
  catch-up/ahead/arrival toggle combinations, three representative pacing profiles
  (captured strengths/stages, zero strengths/skipped stages, maximum strengths and
  oversized 1%/100% stages), three departure geometries, walk/run forecasts, and
  8/20/33-ms cadences. Assertions include route shape, exact retained fraction,
  movement budget, collision proofs and unchanged prediction deadline.
- **3,072 captured-timing replays:** both preserved windows at all 96 speeds, all
  eight pacing toggle combinations, and scene/minimap input entry points. Slow
  displays are allowed to drain real confirmed debt; final alignment and holding
  are exact. The captured-speed marked departures additionally check route bounds.
- **768 live-setting/native-only replays:** both windows, all pacing toggle
  combinations, three cadences, scene/minimap inputs, smoothing 0/50/150/300 ms,
  responsive starts on/off, live 0.5/10 speed changes, and aligned-native/arrival-facing
  toggles. Zero-time setting application cannot reset position or spend another frame.

The full `.\gradlew.bat build --offline --console=plain --no-daemon` completed
successfully.
Fresh test XML reports **752 tests, zero failures/errors/skips**. Earlier movement,
arrival integration/settings, circles/knights, combat, interactions, doors, model
lifetime and lifecycle suites remain included. `git diff --check` passes.

These are automated geometry/timing checks, not every possible combined setting,
collision layout or native-renderer visual confirmation.

## User retest

Launch `./gradlew run` (`.\gradlew.bat run` on Windows), following RuneLite's
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
development-login guide. Only the user performs gameplay.

Recheck rapid reciprocal scene/minimap clicks, cardinal-to-diagonal changes and
knight-to-cardinal reversals in the same area. Start at speed 5.9 / catch-up 15% /
ahead 13% / arrival 0.2--1 tiles at 50--49.8%, then try both slowdowns off. Toggle
catch-up/ahead/arrival independently and change speed while moving. Look for removal
of unnecessary trips through old click corners, no new wrong-way square-join nudge,
continuous circle/knight turns, normal obstacle corners and exact true-tile arrival.
The user subsequently reports this correction is doing pretty well. #12051 requires an archived
trace if exact review of its movement is still wanted.
