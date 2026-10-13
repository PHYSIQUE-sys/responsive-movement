# Follow-up bookmark review — 2026-10-13 01:09–01:14

The user reports that the preceding fixes are definitely better for moving NPCs.
This confirms improvement in those tested situations, not every behavior in the
previous follow-up. Three new bookmarks were found in session `1791813734722`.
The eight rotating logs were copied to a development-only temporary snapshot
before examining their windows. Recorded times use **+11:00**.

| Bookmark | Recorded time | Latest click | Findings |
| --- | --- | --- | --- |
| #21240 | 01:09:19.737 | Walk #21031 at 01:09:15.593 | New goal `(6848,11072)` is observed ~18 ms after the click. The display joins from `(6591,11057)`, follows the checked connector and eastbound continuation, and reaches the goal at ~598 ms. The recorded window has no intermediate idle, backstep or discarded new itinerary. |
| #26055 | 01:10:55.776 | Attack #25814 at 01:10:50.994 | Player authority stays `(6848,5952)`. The target moves from `(6848,5696)` to `(6848,5824)`, adjacent to that authority, at ~296 ms when native action 4177 starts. The unconfirmed northward approach is withdrawn, returning from about `(6848,5830)` to authority by ~596 ms. The native drawn player was still catching up on an earlier Walk at click time. |
| #38255 | 01:14:59.305 | Attack #37941 at 01:14:53.050 | Player authority stays `(6976,6080)`. The target moves from `(7104,6208)` to `(7104,6080)` at ~39 ms. The display's small eastward startup is withdrawn; the recorded draw reaches `(6989,6080)` and returns to authority by ~84 ms. The target remains beside authority during the following held period. At ~642 ms the target moves north again and native authority advances to `(7104,6080)`; the display completes that confirmed step by ~920 ms. |

## Interpretation

The two Attack windows are close speculative starts withdrawn because the NPC
itself moves into reach before the player's predicted step is confirmed. The
predicted stopping tile becomes occupied by the target. Continuing toward that
obsolete tile would not be justified by the captured authority.

In #38255, the roughly 538-ms stationary span follows a return to a true tile that
is already beside the NPC's authoritative footprint. It is not the previous
off-route Walk bug that discarded a valid newer click. A later player step is
accepted and completed continuously once published. This capture also lacks the
old moving-melee join's excursion to a superseded speculative endpoint.

The startup reversals can still be visible, especially the larger unconfirmed
one-tile approach in #26055. After confirming the marker was #38255, the user
identified the brief forward/back nudge as the main problem, with the following
pause also noticeable. The response below adjusts startup pacing and handling of
an observed target step deferred during recovery; reach and occupied-tile rules
still determine when a prediction must be withdrawn.

## Implemented response to the clarified symptom

### Smaller cold one-tile melee starts

`followCombat()` identifies a cold, non-trailing adjacent-melee forecast with only
one logical step from settled authority. It retains that seed's ordinary walking
gait, even with run enabled, and shares the existing `easingNpcStart` exponential
reserve. This reduces the amount of unconfirmed travel that needs to return when
the NPC moves into the predicted tile. Starts still move immediately and remain
frame-rate independent. `accept()` releases the easing on real player progress;
native one/two-step evidence continues to determine normal walking/running.

Moving joins, longer approaches, ranged/conservative profiles and post-arrival
melee trailing retain their own pacing policies. The body still comes from the
native model builder; no primary action clock or server route is altered.

### Retry an observed NPC step after correction finishes

An NPC can leave reach again while the small return to authority is still in
flight. Previously `followCombat()` declined the recovering path and
`offerCombat()` consumed the target-motion observation anyway. The controller then
waited for another target/player step instead of retrying the one already observed.

`awaitingCombatRecovery()` reports only a valid, unfinished combat correction
within both existing deadlines. The controller keeps that already-observed motion
pending, using `CombatContinuity`'s existing pending-move mechanism. Once the path is
fully reconciled, `followCombat()` can build the checked continuation under the
same remaining reserve. A blocked or expired attempt is consumed normally; target
motion alone does not renew the response or hard deadline. No observed target
step is created from idle observation.

In the exact #38255 recording, much of the 538-ms hold is while the target's true
footprint is already adjacent to player authority. That hold is still appropriate.
The change addresses the avoidable extra wait if the target leaves again before
the correction finishes, and reduces the visible startup wobble in either case.

## Verification and in-game follow-up

`CloseCombatStartupTest` adds five tests. Three failed before these changes:
close-start excursion size, restoration of native one-tile pace on confirmation,
and retention/retry of target motion during recovery. The other two guard the
already-adjacent hold and blocked/expired continuation. The two bookmarked close
starts are exercised at approximately 120/50/30-FPS cadences with the captured
5.3 speed, 15% catch-up and 13% ahead slowdown. Synthetic collision/native state is
explicit; these are API-double replays rather than automated game input.

`.\gradlew.bat build --offline --console=plain --no-daemon` passes **705 tests,
zero failures/ignored tests**, retaining all 700 previous checks.
`git diff --check` passes.

Launch `.\gradlew.bat run` using
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).
Recheck close one-tile melee Attack starts, especially a target that walks into
reach and then away again, plus normal longer/moving approaches. Please bookmark
any remaining nudge, unnecessary stop or delayed restart. Only the user performs
gameplay; runtime confirmation of this refinement is pending.
