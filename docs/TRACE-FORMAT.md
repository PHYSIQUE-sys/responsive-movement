# Movement trace context (schema 2)

New diagnostic capture for the GE backtracking/zig-zag follow-up. This adds
evidence; movement policies, native input and game actions are unchanged.
Enable **Record movement trace** before reproducing and turn it off afterward.
The control now lives in **Debugging**, with the optional bookmark hotkey.
The separate sidebar has been removed; new manual bookmarks use the hotkey.
Use `movement.log` together with `movement.previous*.log` under
`.runelite/plugin-data/responsive-movement`. RuneLite's managed Filepath provider
migrates the former `.runelite/responsive-movement` folder on first use if the
managed folder does not already exist. This does not change the trace schema.

## Record types

- `[RESPONSIVE-MOVEMENT]`: existing per-client-cycle rendered/native/authority
  samples, now with `clickSeq` and `routeSeq`. Sampling still occurs during render
  preparation, at most once per client cycle. It does not guarantee observations
  of every native frame during loading or low rendering cadence.
- `[RESPONSIVE-MOVEMENT-CLICK]`: one context for each observed Walk or world
  interaction while recording, even if multiple clicks occur in one client cycle.
  Contains `schema=2`, menu option/opcode/identifier, available menu parameters,
  native destination at observation, world/view/base/plane, instancing, player
  world/native/display positions and footprint, run/energy/Ctrl state, movement
  settings, and the clicked NPC's definition ID, index, name and world footprint.
  New click contexts include `faceInteractionsOnArrival`; older captures omit it.
  The 2026-10-09 pacing additions record `catchUp`, `catchUpPercent`, `slowAhead`
  and `slowAheadPercent`. `effectiveSpeed` still denotes the configured base pace,
  not an instantaneous adjustment. Confirmed catch-up tapers over its last tile;
  eligible unconfirmed travel progressively slows over its first tile ahead.
  The independent tile highlight does not alter trace positions or authority.
  No NPC/scene enumeration is used. Walk contexts without a menu event have no
  menu parameters; the native published destination remains their target evidence.
- `[RESPONSIVE-MOVEMENT-COLLISION]`: copied collision columns for that click.
- `[RESPONSIVE-MOVEMENT-COLLISION-END]`: terminates the click's map block.
- `[RESPONSIVE-MOVEMENT-ROUTE]`: the plugin's planned route, emitted when its
  discrete state changes and at the first position sample in each batch. The
  associated position sample has the same sequence ID. This is **not** the game's
  hidden native route queue.
- `[RESPONSIVE-MOVEMENT-BOOKMARK]`: a manual hotkey marker while recording.
  It has its own ordered `seq`, `schema=2`, `source=button|hotkey`, and references
  the last published `clickSeq`/`routeSeq` without claiming a new click or position
  sample. `tUs`/`time` identify input observation; context is copied when the queued
  client-thread callback runs. It records cycle, scene, world/view/base/plane,
  displayed/native/authoritative/destination local coordinates, all current plugin
  settings and a bounded route snapshot if available. `traceBookmarkKey` uses
  `keyCode:modifierMask`. Live actor/config/path objects never reach the writer.
  No collision crop, model read, path advance or budget renewal is performed.
  Submission flushes the pending batch through the same asynchronous managed writer.
  Normal backpressure/error limits still apply; a submission message is not a disk
  completion guarantee. There is at most one outstanding request and one bookmark
  per physical hotkey press, with key repeat and focus loss handled explicitly.
  `source=button` remains readable for records created before the rejected sidebar
  was removed; the current plugin exposes no bookmark button.

`session` and `seq` identify ordered entries; click/map records share the click's
sequence, and a route record shares its position sample's sequence. `clickSeq`
links position samples to the latest observed input, including an ineligible
click; it does not assert that the currently executing route was built by that
click. `routeSeq` points to the last published route snapshot, or `-1` when no path
exists. `tUs` and route deadlines use microseconds since recording began;
`time` contains the local timestamp with UTC offset. Sequence gaps indicate
dropped batches under writer backpressure. Recording does not stop on a drop.

## Collision coordinates and replay

The copied map is the local player's current plane at click observation, bounded
to 128 columns by 128 rows. Ordinary 104-by-104 scenes fit completely. Header
`sceneWidth`/`sceneHeight` give native dimensions; `collisionWidth` and
`collisionHeightLimit` give copy bounds. `collisionComplete=false` means missing,
ragged or truncated data: never treat uncaptured cells as clear.

Each line gives scene `x`, `yStart=0`, and `flagsRle=count:hexFlags,...`.
For example, `3:0,1:200000,2:0` expands to six consecutive Y cells, with the fourth
holding hexadecimal `0x200000`. Interpret flags as unsigned 32-bit bit patterns.
`flagsRle=missing` denotes an unavailable column. An empty/short column is also
incomplete; its missing tail is not padded with zeroes. All original collision
bits are retained. Use supported RuneLite collision semantics in replays.

Scene tile `(x,y)` has local centre `(128*x+64,128*y+64)`. In an ordinary
non-instanced scene its world tile is `(baseX+x,baseY+y,plane)`. Route and normal
sample positions use **local units**, not scene tile indices. Context NPC bounds
use **world tiles** and width/height. Include world-view identity when comparing
coordinates. Instance mappings require additional evidence; a base coordinate
alone does not identify an instance template.

Collision is copied once **per click**, not per frame. A later changed door,
scene rebase, collision update or different world view invalidates assumptions
about that old map. Scene generation and view/base fields allow these cases to
be separated. This captures travel geometry, not the server's complete interaction
reach/path-selection policy or any unavailable native intermediate tiles.

## Route snapshots

`logical` is the ordered full clicked route, `preview` is its bounded forecast.
Points use `x,y;x,y;...`; `-` means absent/empty. `origin`, `confirmed`, `npcMin`,
`npcMax`, `npcArrivalGoal` and `counterNativeGoal` use the same local coordinates.
For one-sided Bank/Exchange counter runs, `counterNativeGoal` is the accessible
adjacent native flag while `npcArrivalGoal` is the forecast's stopping endpoint;
they can differ. `started-counter-run`, `queued-counter-run` and
`matched-counter-destination` identify this policy. Reserve and matching
indices are recorded explicitly, along with effective speed and deadlines.
`npcReserveTiles` is one for ordinary adjacent NPC options, two for Bank/Exchange,
and the captured weapon/style reserve for Attack. Moving preview handoffs use
`retargeted-counter-run` / `retargeted-npc-approach`; ordinary NPC native refinement
joins retain `redirected-native-approach`.
`aligned-npc-authority` identifies a checked ordinary-NPC join to new forward
authority. It can also finish a join to an already confirmed adjacent arrival.
Talk-to on a cache-identified Bank/Exchange service with qualifying one-sided
counter geometry uses the existing counter decisions, two-tile reserve and
separate native/stopping goals. Its click record still reports `option="Talk-to"`;
  the original schema-2 captures do not include NPC cache action lists.

The 19:25 corner-arrival follow-up adds `counterBoundaryGoal` to route snapshots.
It is the retained accessible-side `(2,2)` whole-pair forecast cap, or `-` when
absent/released. While held, `npcArrivalGoal` reports that current cap and the
logical route still contains the original rounded-counter tail. A matching native
flag can clear the cap using the same `matched-counter-destination` decision;
it grants no new prediction deadline. This field is absent from older recordings.

Adjacent-melee samples additionally carry `combatPhase=approaching|following|none`
and `combatEngaged`. `following` means native engagement has matched the clicked
target, not that a hit was invented or that the player must keep moving. In that
mode `npcMin` / `npcMax` reflect the refreshed observed footprint of that same NPC,
copied on the client thread. The click context still describes its original
footprint. Old captures have neither live target updates nor native engagement
fields; they cannot reconstruct exact NPC-motion/hit timing by themselves.
Decisions include `started-combat-approach`, `followed-combat-target`,
`held-combat-target`, `aligned-combat-authority`, `combat-follow-unavailable` and
`combat-follow-ended`. Ranged/halberd/unknown profiles retain their earlier handling.

The gaze/pursuit refinement adds `combatLocked`, `combatFaceTarget=x,y`,
`combatHit` and `combatPacing=normal|trailing`. The facing point is the selected
NPC's drawn native local position, copied before the writer runs. `combatHit`
records accepted native owned-hit evidence on that same NPC, not an inferred
animation ID. Its marker survives preparation within an already sampled client
cycle until the next accepted sample, with a bounded lifetime. Native incoming
player hits/other-player NPC damage cannot supply this evidence.
`settled-combat-hit` identifies a checked confirmed-remainder release; it does
not schedule an attack, change animation timing or grant a new prediction budget.

The 18:10 combat input follow-up adds `combatWalk`, `combatCategory`, `combatStyle`,
`combatCacheStyle` and `combatProfile=melee|ranged|conservative|none`. These are copied
scalars from click-scoped native cache evidence; no worker reads a client/actor/cache.
`Ranging` is the native XP-style name for Accurate/Rapid, so the normalized diagnostic
value is `ranging`. Click contexts copy the style/category indices and `autoRetaliate`.
Older captures do not contain those fields. `combatFaceTarget` may now be present for
stationary Attack before `combatLocked` latches at a supported movement boundary;
stationary facing is shared across profiles, while melee trailing/hit release remains
melee-only. `combatWalk=true` identifies explicit release of the selected target in
favour of the checked Walk; a stale native NPC reference alone cannot reclaim it.
`ranged-boundary-changed` records cancellation of an unsupported ranged forecast,
including changed line of sight. Native animation timings and the server route are
not changed by any of these fields or decisions.

The 21:17 all-attack continuity follow-up adds sample `combatEffectCarry` and native
`actionFrame`. The flag means the currently active effects match bounded captured
combat-release evidence; it does not select, schedule or restart an attack/cast.
Click contexts add `actionFrame`, `spotEffects` and `spotEffectsComplete`. The flat
array contains four values per captured effect: **ID, hash, start cycle, frame**,
for at most eight entries, including pending effects. Completeness is false when
the table exceeds that bound. Values are copied on the client thread; formatting
remains on the ordered asynchronous writer. Old recordings lack these identity/frame
fields and therefore need explicitly labelled effect/primary-clock doubles in replays.

`legs` is a flat integer array, eight values per queued leg:

`startX,startY,endX,endY,corridorEndX,corridorEndY,running,firstOfPair`

Missing corridor coordinates are `-1,-1`; boolean values are 0/1. The current
sample's displayed position may already be partway through the first leg.

The 23:33 marked-handoff follow-up can use a sub-tile `endX,endY` at the shared crossing
of two proven corridors. Such a queue endpoint need not be a logical tile centre. Its
incoming and onward legs retain their separate corridor proofs; the ordered logical
route still records tile centres. No trace schema change or extra movement clock is added.

The 21:43 circle follow-up adds `joins`, eight floating-point local coordinates per
queued leg: **startX,startY,control1X,control1Y,control2X,control2Y,endX,endY**. `NaN`
entries mean that leg has no active local cubic blend. The blend endpoint may precede
the leg's tile endpoint; it rejoins that same checked leg. These are immutable geometry
copies, not a second route/clock. Position samples give current progress. Controls shift
on scene rebasing and the snapshot changes when a blend starts/finishes; continuous
arc progress is not emitted as a new route every frame. Older captures omit this field.

`stateFlags` bit values:

| Value | Meaning |
| --- | --- |
| 1 | speculative |
| 2 | recovering |
| 4 | replacement |
| 8 | agreement |
| 16 | awaiting construction origin |
| 32 | reversal/retarget preview |
| 64 | invalid |
| 128 | easing NPC idle start |
| 256 | path running mode |
| 512 | fresh NPC/melee retarget can retain its bounded forecast through a stale preceding-Walk endpoint |
| 1024 | adjacent-melee path owns the bounded combat movement credit |
| 2048 | one movement credit available from the click or checked forward player authority |
| 4096 | slower post-arrival combat pursuit / exponential checked last-leg pacing |
| 8192 | a Walk join has a pending one-shot tangent-continuity preparation |

## Lifetime and bounds

All actor/config/geometry reads occur on the client thread while recording.
Collision arrays are deep-copied; path snapshots contain bounded immutable point
lists and copied queue data. Formatting, collision RLE and file I/O execute on the
existing ordered asynchronous writer. No model reads or scene scans are added.
Disabled tracing takes no diagnostic client/geometry snapshots. Batches are capped
at 128 entries/eight click contexts and the writer queue at four batches. Shutdown
and disabling recording submit pending data without waiting for disk I/O.
The existing eight-file, approximately 8 MiB-per-file rotation remains in effect.

For a useful first capture, reproduce the troublesome Bank/Exchange approaches
from settled idle, one click at a time, then a few moving retargets. Note times
and visual symptoms. The new geometry and route snapshots can support realistic
regression replays; they do not by themselves validate the final in-game fix.
