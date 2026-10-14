# Moving object handoff: bookmark 130416

Session `1791942160741`, click `130154`, bookmark `130416`.
The click was Open on wall object 1804 at scene tile (43,50).

## Recorded failure

- At the click, display was (6237,6592), authority (6208,6592), and the
  preceding Walk destination (5440,6336).
- The native object flag initially named the hinge (5568,6464).
- Wall staging chose (5696,6464) geometrically, without considering which
  perimeter was reachable. Construction failed (`continuation-unavailable`).
- Replacing the old preview left only about 29 local units of confirmed debt.
  Confirmed pacing spread that debt across the remaining publication interval:
  the display advanced roughly one unit per render until the next server step.
- About 580 ms later, native authority reached (6080,6464) and the native flag
  changed to the reachable south perimeter (5568,6336). The failed construction
  had discarded destination observation, so refinement was ignored.

## Shared corrections

1. Wall staging now uses the same bounded, reversible, collision-checked
   perimeter search as rectangular objects. The wall anchor is excluded.
   Native destinations outside the footprint remain exact.
2. Destination observation survives a failed object/item route construction.
   A changed native flag can queue a checked approach behind confirmed movement
   or start from a settled position within the existing hard deadline. An
   unchanged flag does not trigger retries, and refinement cannot renew expired
   prediction credit.

## Verification

`MovingObjectHandoffTest` loads the recorded collision columns 40–52 from
`door-130416-map.txt`; unrecorded columns are blocked. It checks:

- Idle and four different in-flight click timings, walking and running, at
  10/20/33 ms render cadences (30 combinations).
- The captured moving-click sequence through native destination refinement and
  arrival, both with and without a repeated Open click.
- Initially unavailable routes followed by reachable native refinement, from
  idle and confirmed movement.
- Expired deadlines, blocked connectors and already-speculative routes.

The older gate test expecting every blocked nearest side to prevent movement
now checks that a reachable detour stays outside the hinge and expires normally
if the server never confirms it.

Run `./gradlew test`. Actual RuneScape presentation still requires user testing;
these replays do not automate game input or establish native door-action timing.
