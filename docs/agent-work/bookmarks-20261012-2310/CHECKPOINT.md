# Bookmarked delayed clicks, moving attacks and minimap run — 2026-10-12

## Evidence

The user reported delayed clicks, strange movement while attacking moving NPCs,
and stopping/starting during a minimap run after the special-case cleanup.
Thirteen bookmarks were found across sessions `1791807021971` and
`1791807324851` in the managed movement trace and its seven rotated files.
Times below use the recorded **+11:00** offset.

| Bookmark seq | Time | Latest click seq | Context |
| --- | --- | --- | --- |
| 1745 | 23:10:56.293 | 1512 | Walk replacement after an unconfirmed diagonal |
| 2984 | 23:11:20.388 | 2867 | Walk replacement followed by the old diagonal endpoint |
| 6092 | 23:12:23.331 | 5917 | Walk replacement; bookmark metadata retained |
| 6470 | 23:12:30.674 | 6349 | Walk replacement; bookmark metadata retained |
| 12158 | 23:14:24.035 | 11887 | Walk replacement discarded by forward/off-route authority |
| 1603 | 23:15:57.205 | 1299 | Moving melee approach with an ordinary scene rebase |
| 6361 | 23:17:32.173 | 6270 | Moving melee target changes approach side on the attack frame |
| 10875 | 23:19:03.086 | 10275 | Second minimap publication on the long northern run |
| 15458 | 23:20:34.179 | 15334 | Melee target moves away, then returns beside authority |
| 36638 | 23:27:37.537 | 36382 | Conservative-profile target moves into reach; action begins without player travel |
| 38266 | 23:28:10.007 | 37889 | Conservative-profile approach pauses between confirmed pairs |
| 39586 | 23:28:36.229 | 39447 | Walk observed during native action 440 without retained combat-release evidence |
| 42780 | 23:29:39.815 | 42549 | Fresh melee Attack starts from a completed recovery |

Rotation continued while reviewing the live recording. The later session was copied
to a development-only temporary snapshot; the two older middle Walk clicks had
already rotated out by then. Their bookmark metadata is recorded above, without
claiming a full replay. The first-session Walk reproductions use the observed
coordinates/ordering with explicitly synthetic open-square collision.

## Confirmed problems and corrections

### Fresh Walk discarded by off-route forward authority

Click 1512 publishes `(7104,6592)` about 21 ms after observation. The preceding
click's `(7232,6592)` server endpoint arrives about 49 ms after the click. The new
preview is discarded and the display idles at that older endpoint for **541 ms**
before accepting the new endpoint. Click 2867 has the same pattern with a **298-ms**
idle; click 11887 has a **537-ms** idle.

`alignWalkAuthority()` previously required `awaitingOrigin`, even though a fresh
retarget with its old true tile already on the constructed itinerary still needs
to join new off-route progress. It now accepts such checked forward progress for
fresh `reversalPreview` itineraries. Its view, phase, expiry, two-step collision,
goal-progress and distance gates remain. Ordinary queued confirmed prefixes do not
have that fresh-retarget flag. Old opposite steps still use the bounded stale-tick
handling without granting credit.

### Moving melee approach finishes the superseded forecast

Click 6270 starts at `(6976,6336)` toward target `(6976,6720)`. At about **379 ms**,
the target moves to `(6848,6592)`, authority becomes `(6848,6464)`, and action 390
starts. The display is already north of the new approach row. The old join prefers
the northbound speculative endpoint `(6976,6592)`, then returns southwest to
`(6848,6464)`.

`followCombat()` now supplies `joinForecast()` with direction from the exact visible
fraction to the revised goal. It cannot mistake the old authority's forward heading
for progress toward a goal already behind the display. Only checked occupied-edge
or corridor joins are used. Confirmed debt and the existing combat-credit/deadline
policy remain; action clocks and native model posing are untouched.

### Completed recovery blocks a fresh Attack

Click 42549 begins from an empty, reconciled `recovery` path at `(6336,6208)`.
It reports `combat-follow-unavailable` and stays still until authority advances
about **401 ms** later. The old `recovering` flag blocks `followCombat()` even though
there is no correction left to finish.

`armCombat()` now clears that flag for a valid `finished()` path when a new owned
Attack arms its reserve. An unfinished/invalid recovery is not rearmed. A rejected
new click still times out and returns to actual authority.

### Long minimap detour exhausts the radial search

Clicks 10258/10275 at **23:18:50.797 / 23:18:51.113** publish goals
`(6976,10176)` and `(6848,10176)` from `(6976,6208)`. Nearby collision blocks the
direct itinerary. The 31-tile detour exceeds the useful radius of the 2,048-vertex
breadth-first search, so both starts report `route-or-deadline-unavailable`.
Subsequent travel is confirmed-only, repeatedly accelerating through a pair and
braking into its small endpoint reserve. The trace does not show a repeated empty
movement queue during this long run; the visible pace oscillation explains the
reported stopping/starting impression.

`MovementRoute` now guides a distant obstacle-search frontier by checked travel
cost plus max-axis goal distance when the goal's open-area radius exceeds the
vertex budget. Local searches and footprint-boundary searches keep FIFO ordering;
`MovementPath` still tries its straight-first direct itinerary first. The guided
frontier uses the same reversible edges, at most 64 steps, 2,048 discovered vertices
and 2,048 expansions. Exact targets and object-style cardinal boundaries retain
different reach policies. This gives the existing MovementPath forecast a legal
route, rather than changing confirmed-run pacing or adding another controller.

A temporary API-double replay with **all 104 captured collision columns** passed
both minimap publications and the subsequent northern run. That machine-local probe
was removed. The portable regression embeds the recorded x=50..58/y=47..80 crop in
an explicitly synthetic open background; it does not treat unrecorded collision as
proven clear.

### Conservative attack publishes idle between native pairs

Click 37889 uses weapon 29796 with `combatProfile=conservative`. Authority first
reaches `(6976,6080)`, while native destination remains `(7232,5824)`. The display
arrives early and publishes idle for roughly **160 ms** before the next server
endpoint. These profiles retain native reach/route handling rather than melee
pursuit prediction.

The controller now shares the existing confirmed-run reserve while that owned
conservative attack has an onward native flag, run remains enabled, and input/action/
effect/arrival gates permit it. It predicts no extra tile and releases normally on
the action or withdrawal/arrival of the flag.

The other late captures also demonstrate legitimate authority reconciliation:
36382 starts a native attack when the target moves into reach without a player
step; 39447 observes Walk while action 440 is still active without qualifying
combat-release evidence. Neither observation grants permission to invent a player
step or bypass the native primary-action gates.

## Verification and runtime follow-up

`BookmarkedMovementRegressionTest` adds **seven permanent tests**. The first four
failed before the fixes; the conservative-pair test also failed before sharing the
confirmed-run reserve. The remaining two guard long-search reversibility, route/
collision budgets and exact-target versus adjacent-object behavior. All prior tests
remain, including the complete settings sweep.

`.\gradlew.bat build --offline --console=plain --no-daemon` passes **700 tests,
zero failures/ignored tests**. `git diff --check` passes.

Only the user can confirm runtime behavior. Launch `.\gradlew.bat run` using
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).
Recheck rapid perpendicular/diagonal scene and minimap clicks, moving-NPC melee
approaches, Attack after a cancelled/timed-out preview, the long minimap run and
conservative/extended-reach attack approaches. Use the recorded 5.5 speed, 15%
catch-up and 13% ahead slowdown first. Bookmark any remaining idle/backstep/overshoot.
The user subsequently reports that moving-NPC behavior is definitely better.
The three further 01:09–01:14 bookmarks are reviewed in
[the follow-up checkpoint](../bookmarks-20261013-0109/CHECKPOINT.md); this feedback
confirms improvement in their moving-NPC tests, not every earlier behavior.
