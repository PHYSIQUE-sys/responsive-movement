# Interaction startup and Movement delay: 23:59 bookmarks

**User-confirmed working well:** the user reports this startup/delay correction
seems to have fixed everything they were having issues with, following their
confirmation that the preceding native-Use correction fixed stopping/starting
**between confirmed run steps**. This confirmation covers their tested experience;
the automated matrices and other interaction/renderer variants remain regression
coverage, not a claim of separate in-game verification of every combination.

Session **1791982742542**, 2026-10-14/15, local timezone **+11:00**:

| Bookmark / click | Recorded finding |
| --- | --- |
| #386 / 260, 23:59:07.719 Use on banker | Idle display waits roughly **522 ms** for the first native destination/authority. Native Use stops at `(7360,6464)`, three tiles from the solid counter target, although its flag names `(7104,6464)`. |
| #1397 / 1282, 23:59:28.183 Use on Perdu | First movement evidence appears about **440 ms** after input. |
| Click 1569, 23:59:33.864 Use on Emblem Trader | First movement evidence appears about **160 ms** after input. |
| Click 2069, 23:59:43.782 moving Use on Perdu | The display drains its small old confirmed remainder, then waits/nearly stops until a native flag appears about **438 ms** after the click. |
| #2473 / 2346, 23:59:49.258 moving Use on Emblem Trader | Checked recovery reaches `(6592,7616)` about **143 ms** after input and holds roughly **225 ms** until new authority/destination. |
| #3607 / 3428, 00:00:10.780 moving Use on Perdu | The small confirmed remainder is almost exhausted before native evidence at about **439 ms**. |
| Clicks 520 / 719 | Object Use publishes a destination immediately; moving Bank already has a captured counter forecast. Both are retained as parity checks. |

All four bookmarks and all eight interaction windows were reviewed, including
the preceding Walks for the moving cases. Settings are speed **5.8**, Movement
delay **0**, catch-up **15%**, ahead slowdown **10%**, arrival **0.2 / 50% +
1 / 49.8%**, original-when-aligned off, and arrival-facing on.

## Shared response policy

- `NativeApproach` now captures immutable click-time footprint bounds for eligible
  **Use**, **Trade**, selected **Cast**, and player **Attack** intent. It still has
  no displayed position, route queue or movement clock. One directly indexed actor
  supplies identity/footprint evidence; nothing enumerates the scene per frame.
- Use/Trade previews use a checked cardinal-side approach. An already occupied or
  adjacent target supplies no invented movement. A solid, single-tile, one-sided
  counter uses a conservative three-tile preview reserve, based on collision
  geometry, to avoid treating the banker's adjacent native flag as its actual stop.
  No NPC ID, name or coordinate table is added.
- Selected spells retain the conservative ten-tile outer reserve; player Attack
  uses existing native weapon/style cache evidence. Already being inside that
  conservative reserve does not prove another step is needed. Unknown/ranged
  targets are not treated as adjacent Use targets.
- `MovementPath.anticipateNativeIntent` owns the prediction. A current checked
  fractional edge/corridor can join it directly; real unfinished confirmed travel
  or a checked remainder to authority is preserved ahead of the forecast. A new
  target does not require finishing an obsolete speculative Walk or waiting idle
  for a late native flag.
- Short seeds reuse the existing exponential reserve, including behind confirmed
  debt, so they do not finish early and publish idle before the first native step.
  Confirmed debt itself is not subjected to startup easing.
- A newly published native destination replaces the provisional boundary through
  the shared checked refinement/join. It retains the original prediction deadlines.
  Changed target footprints retire provisional speculation; they do not start a
  target-follow route watcher. Native actions/effects and actual authority remain
  decisive; primary action IDs/frames are untouched.

## Movement delay

The persisted `clickSmoothingMs` key and default **50 ms** are unchanged. The wait
now applies to world interactions as well as Walk:

- **0:** eligible click-time/native destination evidence enters the path in the
  next preparation without a deliberate wait.
- **1–300 ms:** hold the new response until the configured wait elapses, or the
  first new GameTick releases it earlier, matching the setting's existing description.
- The existing run can continue during a positive wait; replacement/redirect is
  deferred until release. Native destination observation remains independent and
  latches its first publication once. Geometry is not a fake native destination.
- No observation callback advances movement. Same-timestamp repeated preparation
  and live setting changes retain one movement/facing budget. Native authoritative
  travel is still accepted normally.

## Verification and evidence limits

`NativeIntentResponsivenessTest` adds **7 tests**:

- **24 recorded replays:** all eight interaction timelines at roughly
  120/50/30-FPS cadences, checking prompt eligible starts, moving handoffs,
  positional/facing budgets and eventual native stopping points.
- **40 delay cases:** item/widget Use on NPC/player and player Trade with delays
  0/50/150/300 ms, both ordinary expiry and earlier GameTick release.
- Delay/parity checks for native object/ground-item destinations, ordinary NPC
  approach and NPC combat, including the first latched publication surviving an
  older flag while settling.
- **64 short-seed cases:** NPC/player, idle/confirmed-prefix starts, minimum/base/
  captured/maximum speed and four cadences; no premature short-start idle and
  finite true-tile convergence.
- Collision, Control, disabled starts, adjacent/occupied targets, target movement/
  disappearance, native range/action clocks, player Attack/distant Cast, and a
  moving positive-delay handoff that keeps running until the new response releases.

The full build passes **784 tests, zero failures/errors/skips** using
`.\gradlew.bat build --offline --console=plain --no-daemon`. `git diff --check`
passes. All 777 prior test methods remain; assertions that deliberately required
Use to wait for a native flag now exercise conservative Cast or the bounded Use
policy. Historical constant-pace geometry fixtures explicitly use delay 0; the
separate delay tests exercise the shared delay policy.

`native-intent-2359.txt` preserves click/native publication times and collision
columns **44–64, Y 48–66**. All eight full click-time maps agree; uncaptured cells
are blocked. Native fractions are held between retained publications rather than
inventing intermediate native frames. Actor/model selectors are API doubles;
these checks do not execute the game's renderer or establish every interaction
reach, moving-target pattern or server route.

## User check

Launch `./gradlew run` or Windows `.\gradlew.bat run`, with GPU or 117 HD.
Use [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
for development-client login. Only the user performs gameplay.

At the recorded settings, check Use on banker/Perdu/Emblem Trader and players from
idle and during scene/minimap runs. With delay **0**, look for immediate visual
response and no pause while switching away from a run. With delay **50/150/300**,
look for the configured wait (or earlier tick release), with the current run
continuing during that wait. Recheck short approaches, corners/counters, repeated
targeting, object/ground-item actions, ranged spell/Attack boundaries, normal
Bank/Exchange, Follow, primary actions, and scene/plugin lifecycle.

**User reports the startup/delay correction seems to have fixed everything they
were having issues with.** Their in-game confirmation supersedes the earlier
pending-validation wording. They have not separately claimed to test every
renderer, target, collision layout, or delay value listed above.
