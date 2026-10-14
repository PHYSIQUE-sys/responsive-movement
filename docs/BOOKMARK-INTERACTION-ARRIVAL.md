# Interaction arrival pacing and moving-NPC handoff — 2026-10-14

## Available evidence

The user identified the 13:40 bookmark **#172647**, several following bookmarks,
and **#499** as a bad route to an NPC while already moving. The current eight
rotating trace files retain **#499**, session `1791946232019`, at
**13:50:41.924 +11:00**, referencing Attack click **115** at
**13:50:34.276 +11:00**. The 13:40 bookmarks have already rotated out; their
specific object/NPC timelines cannot be verified from the remaining files.

In click 115, the display is `(7399,5293)` with authority `(7488,5312)` inside
the occupied checked knight `(7616,5184) -> (7360,5312)`. Authority is behind
the display along that chord. The target is a Goblin with initial footprint
`(6080,6336)`, and the new approach initially names `(6080,6208)`.

Route 116 retains the preceding Walk's speculative bend through
`(7104,5440) -> (6976,5568)`, then appends a second itinerary beginning at
`(7488,5312)`. At sample 157 the display changes direction from westward travel
to an eastward return through that old construction origin. This is an obsolete
preview being treated as confirmed movement, rather than a new server route.

## Corrections

- `hasUnfinishedConfirmedPrefix()` now distinguishes authority still ahead inside
  an occupied knight from authority already behind the fractional display. The
  latter does not preserve the superseded speculative suffix ahead of a new NPC
  itinerary. Actual queued confirmed endpoints and forward confirmed debt retain
  their existing protection.
- Object arrival pacing uses the already captured, checked perimeter endpoint
  when the native flag names the occupied footprint. It does not recompute an
  object route or scan the scene each frame.
- Native destination withdrawal can retire forecasting without suppressing
  arrival pacing on a final, collision-checked confirmed remainder. Recovery,
  unconfirmed combat movement and combat trailing keep their separate guards.
- Confirmed-publication pacing selects the existing native walk pose when its
  displayed rate is below walking speed. It no longer advertises a running gait
  over a tiny retained movement remainder.
- Known object arrivals are released from the between-publication reserve after
  the native flag clears. Object movement with no published endpoint still uses
  the existing confirmed-only publication handling.

## Verification

`InteractionArrivalRegressionTest` covers solid-object footprint flags, object
and ordinary-NPC final flag withdrawal, slow confirmed publication gait and the
#499 occupied-knight handoff. The route test uses the recorded coordinates with
explicit synthetic collision selecting the captured south-side NPC goal; it is
not a complete native-route/collision replay. The object/NPC tests use supported
API doubles and check the requested native walk/run selectors as well as position.

The full `.\gradlew.bat build --console=plain` passes, including the existing
arrival-settings sweeps, confirmed-prefix guards, native refinements, combat,
door/object handoffs and lifecycle checks. No game input is automated.

Launch `.\gradlew.bat run` using
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).
At speed 5.9, catch-up 15%, ahead slowdown 13% and arrival stages
0.2 tiles / 50%, then 1 tile / 49.8%, recheck object and NPC clicks from idle
and while moving. Watch for a clean walking finish and no return through the
previous Walk route when attacking a moving NPC. The earlier 13:40 captures need
an archived copy or a replacement recording for exact comparison. Only the user
can confirm the in-game presentation.
