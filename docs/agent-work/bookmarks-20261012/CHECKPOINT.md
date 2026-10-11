# 2026-10-12 bookmarked Walk departures

## Evidence and scope

Reviewed the eleven `[RESPONSIVE-MOVEMENT-BOOKMARK]` entries in session
`1791728735542`, recorded **2026-10-12 01:25–01:28 (+11:00)**. The managed logs at
review were `movement.previous.4.log`, `.3.log` and `.2.log`. The third bookmark
chronologically (**01:26:24.423, seq 2501**) is excluded as requested.

Settings: speed 5.3/5.4, turning 32, Walk smoothing 0, catch-up 15%, ahead slowdown
13%, Original player when aligned off, adaptive camera on. The relevant publications
show Walk input without primary actions or spot effects, in world view 0/plane 0.
Markers follow symptoms; the state at the marker itself is often already settled.
They are not timestamps of the exact first bad rendered frame. The final native
renderer, input-menu opening, camera perception and native facing targets are not
fully captured by these traces.

The collision crop X40..64/Y46..68 agrees across all **190** observed clicks through
the last bookmark. The four retained controller windows use the exact relevant
X45..59/Y52..61 crop; other cells are blocked. Native fractional coordinates are
held at retained native destination/authority/equality publications. Native facing,
model availability and pose clocks are API doubles, not game-renderer execution.

## Findings for every retained bookmark

| Bookmark | Local time (+11:00) / sequence | Relevant preceding evidence |
| --- | --- | --- |
| 1 | 01:25:50.921 / 786 | At 01:25:48.151, Walk to `(6592,7360)` selects old middle `(6464,7104)` while authority is `(6592,6976)` and display is near `(6542,7003)`. It retreats laterally before joining the new northward route. The checked first-step square can retain the native-authority column. |
| 2 | 01:26:02.547 / 1378 | At 01:26:00.650, eastward Walk to `(6336,6976)` is observed with display around `(6080,7017)` and authority `(6080,6976)`. The old cardinal connector returns to the row before starting east. A fully proven square allows both components of that departure under one clock. |
| 4 | 01:26:51.139 / 3855 | At 01:26:47.310, the new southward goal `(6720,6976)` is routed through old middle `(6592,7104)` instead of authority `(6720,7232)`, despite display already being near `(6699,7221)`. The sideways dip and corner are unnecessary. |
| 5 | 01:26:59.448 / 4281 | Walk at 01:26:57.073 names `(6080,7488)`, scene `(47,58)`, with captured flags `0x20100` blocking movement. Exact continuation search fails; checked movement continues through authority `(6080,7360)` and the flag disappears. The intermediate confirmed final-leg reserve slows while awaiting that endpoint, but no idle/retrace or unproved onward tile is established in these publications. Predicting the blocked destination would be incorrect. |
| 6 | 01:27:22.215 / 5450 | At 01:27:20.612, Walk to `(6080,6848)` connects display near `(6171,7269)` back to authority corner `(6208,7232)` before the southward legs. Two completely checked adjacent squares can share a fractional crossing, reducing the corner return. A reversed goal must not inherit an away-moving local blend. Some lateral alignment can still be required by checked straight-first geometry. |
| 7 | 01:27:27.310 / 5709 | At 01:27:25.109, Walk to `(6464,7488)` selects old middle `(6208,7104)` from near `(6302,6993)`, with authority `(6336,6976)`. It uses the parallel old column until 01:27:25.611 authority alignment. The departure can instead start on the correct native-authority route. |
| 8 | 01:27:33.205 / 6011 | The preceding 01:27:31.332 westward Walk to `(6848,7488)` selects old middle `(6976,7360)` near arrival at `(7104,7488)`, dipping south and then returning to the goal row. Its following 01:27:31.832 knight click has its existing checked chord. The first-step-square policy removes the unnecessary preceding dip. |
| 9 | 01:27:39.284 / 6322 | At 01:27:37.429, southward Walk to `(6976,7360)` uses old middle `(6848,7616)` near arrival at `(6976,7744)`. The lateral departure survives until the 01:27:37.610 forward-authority join. The authority-based departure avoids that old-column excursion. |
| 10 | 01:28:27.968 / 8819 | The 01:28:25.589 return toward `(6976,7488)` retains hard deadline `170063643 us`, only about **16 ms** after the publication. The preceding endpoint `(6848,7232)` arrives at `170068710 us`, just beyond that bound. The preview retires and waits until actual onward authority at 01:28:26.210. The trace contains a small return and roughly 580 ms of stationary display. Extending its original chain on another click or opposite stale tick would violate the tested finite anticipation policy. A dedicated short-budget guard covers this condition; this work does not claim to remove that bounded wait. |
| 11 | 01:28:32.153 / 9032 | At 01:28:30.650, southward Walk to `(6720,7232)` selects old middle `(6848,7488)` from near `(6761,7596)`, with authority `(6720,7616)`. It adds an eastward excursion until 01:28:31.011 authority alignment. The checked authority-departure square supports the direct join. |

## Shared correction

`MovementPath.retargetWalk` considers `retargetAuthorityDeparture` after the existing
whole-knight candidates. This uses the same `anticipate` forecast from actual
authority and replaces only its first cardinal presentation leg with a separately
proven local square. It does not replace the logical first tile or manufacture native
route evidence. Both diagonals and each perimeter edge must be clear in both directions.
Authority must belong to the occupied checked edge/corridor, and the visible fraction
must be within one tile of authority. A shared-boundary connector additionally requires
being within half a tile. Each segment retains its own proof.

The native-authority candidate is preferred only within the existing one-tile
connector travel allowance. Missing/blocked/unsupported proof falls back to existing
selection. The queue, clock, facing cap, forecast gap and hard chain remain shared.
The local cubic helper also rejects carrying a tangent opposed to the latest goal.
No object/NPC-specific route engine, model, primary animation, camera mode, config
key, input generation or trace schema is introduced.

## Verification and limits

- Four initial focused tests fail before the production correction.
- Eleven new tests cover recorded windows for bookmarks 4/7/9/11 at three cadences;
  approximate captured-fraction geometry for 1/2/4/6/7/8/9/11; all square-edge directions;
  closure of an occupied shared square; repeated-click hard bounds and timeout;
  translation, matching authority and pacing; rotated/reflected departures; and the
  blocked-goal and nearly exhausted-chain conditions in 5/10.
- `.\gradlew.bat build --offline --console=plain --no-daemon` passes **682 tests**,
  zero failures/ignored tests, including all 671 preceding checks. JDK 17 compiles
  production for Java 11.
- `git diff --check` passes. No game was launched or gameplay input automated.
- These are API-double/replay checks, not proof of every collision layout or final
  renderer feel. The user subsequently confirms the correction works well in-game.

Launch `.\gradlew.bat run` using RuneLite's
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
development-login guide. Recheck the marked near-arrival changes at the captured
settings with 117 HD, including scene/minimap, walking, wall-adjacent alternatives,
circles, repeated knights and plugin lifecycle. For marks 5/10, a new symptom description
and bookmark can distinguish undesirable feel from their evidenced collision/time limit.

## Settings sweep follow-up

The user requests stronger evidence that movement-speed/catch-up changes do not
reintroduce the corrected departures. Four matrix tests in `BookmarkSettingsSweepTest`
pass with **686 total JUnit tests**, including all 682 earlier checks. No additional
production-code change was needed.

| Sweep | Coverage | Executed variants |
| --- | --- | --- |
| Fractional departure geometry | All 96 user-facing speed increments, 0.5–10.0; catch-up off/on 0/1/10/15/25/50%; ahead slowdown off/on 13/50%; eight bookmark geometries; walk/run; 8.333/20/33.333-ms preparation | 96,768 |
| Captured controller timelines | Speeds 0.5/1/2.5/4/4.9/5/5.1/5.3/5.4/6/7.5/10; same seven catch-up and three slowdown policies; bookmarks 4/7/9/11; three cadences | 3,024 |
| Live settings changes | Scene/minimap entry, four windows, three cadences; 0.5/off/off to 10/50/50, reverse, and 5.3/15/13 to 5.4/10/off | 72 |
| Blocked destination and near-expired chain | Both constraints tested for every speed, seven catch-up and three slowdown policies | 2,016 paired cases |

The first matrix warms the old itinerary at baseline to reproduce its fractional
departure geometry, then varies settings at that same point. It checks authority-based
logical travel, collision-valid queues, rate-bounded movement, no old side-anchor
excursion, straight crossing of the proven adjacent squares, arrival and holding.
Walking gets synthetic one-tile authority per 600 ms; running gets whole-pair authority.
Matching authority grants only the existing shared pipeline's normal confirmation.

The controller matrix preserves the original captured click/authority/destination times
and held native fractions. Slower settings naturally place display behind the recorded
fraction, sometimes preserving a different confirmed prefix or using native fallback.
Their assertions check movement/turn budgets during custom ownership and eventual final
convergence/holding after real confirmed debt drains. The fixed-fraction matrix separately
checks the specific geometric failure without discarding legitimate debt merely to
force the recorded screen trajectory at a different speed. Native handoff is allowed by
the existing policy; its native interpolation is not treated as custom movement budget.

Live changes occur at the final destination publication and reprepare at zero elapsed
time. The exact displayed point must survive both application and repeated preparation.
Minimap coverage calls the supported controller entry with the same retained destination
publications; actual interface conversion is an API-double extension, not captured menu
or game input execution. The bound sweep keeps the blocked destination inaccessible and
the 16-ms chain unchanged until genuine authority allows confirmed movement.

An initial bound-fixture failure attempted a three-tile authoritative update, which is
outside the supported native one/two-tile policy. The fixture now uses the capture's
two-tile step to `(6080,7232)` and subsequent one-tile stop at `(6080,7360)`. This was a
test-input correction, not a plugin defect.

Verification: `.\gradlew.bat build --offline --console=plain --no-daemon` passes
**686 tests, zero failures/ignored tests**. `git diff --check` passes. The sweep covers
representative catch-up strengths, not every integer percentage or every possible
settings combination/collision layout. The user subsequently reports that the corrected
behavior works well in-game; this is not blanket confirmation of every native rendering
or pacing combination. Further checks can use `.\gradlew.bat run`, following the Jagex
Accounts guide linked above.
