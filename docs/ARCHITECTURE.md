# Responsive Movement architecture

Author/owner: **PHYSIQUE-sys (formerly GimmitheBong)**. This is an independent Java 11 RuneLite project
with config group `responsive-movement` and package `com.responsivemovement`.
See the root BSD license for retained source attribution.

## One movement pipeline

`MovementPath` owns all custom positional movement. An idle path accepts normal
authoritative one/two-tile updates; a responsive start seeds a bounded prediction
into that same path. Confirmations, corrections, ordinary walking/running and
replacement clicks then use the same queue and movement clock.

The retained rules include:

- Straight-first logical paths with collision checks on every edge.
- Native-style straight knight chords when both step orderings are clear.
- A finite startup reserve, replenished only by new matching authority.
- Visible-idle starts, including fully settled idle and a stopped visual preview.
- A blocked direct click uses `MovementRoute` to find a reversible collision-checked
  detour, bounded to 64 steps and 2,048 visited tiles. Searches run for a new
  prediction/correction, not on every rendered frame.
- A mid-run click can append a preview behind a confirmed queue. The confirmed
  prefix and its sub-tile position survive replacement, timeout and disagreement;
  re-clicking without server progress cannot renew the hard prediction deadline.
- A straight cardinal or exact tile-diagonal reversal can instead replace that
  queue from the exact visible sub-tile position once its click is released by
  input observation/smoothing. The reverse edge must be collision-clear,
  and nearby off-axis authority must have a bounded, reversible collision-checked
  connection to the reversal origin. The existing prediction deadline and distance
  bounds still apply. Corners and knight chords keep their checked paths.
- A reversal's newly built legs remain predictions until matching server progress;
  a delayed endpoint in the old direction must not preserve an invented forward
  prefix. Server progress toward a reversal's origin can replenish a clipped
  preview within its original horizon and distance cap before reaching that origin.
- An opposite-direction update during an unconfirmed reversal can still belong
  to the preceding click. Retain the newest forecast within its existing response
  deadline, clipping it to the gap from updated authority. Such updates renew
  neither deadline; rejection/timeout still reconciles to the real endpoint.
- The earlier `anticipateRedirect` handler can redirect eligible unconfirmed
  previews via a checked occupied edge. Fresh yellow Walk clicks now first
  attempt the more general `retargetWalk` described below, including checked
  knight-chord and one-tile diagonal-side connectors. Failed replans fall back
  to normal continuation/recovery; prediction bounds still apply.
- The user has confirmed this arbitrary-direction retargeting for scene clicks.
  Minimap clicks now share the same routing and smoothing; the minimap extension
  awaits its own in-game confirmation.
- Fresh `retargetWalk` itineraries whose new goal already equals the old true tile
  remain pending when their visible position is elsewhere. That equality must not
  lend confirmation to the new legs before a delayed preceding-click endpoint arrives:
  otherwise accept() appends a redundant return trip behind the fresh route. The
  existing bounded stale-reversal handling retains the latest checked fractional route,
  updates actual authority and grants no extra deadline. Pure one-tile/knight endpoint
  connectors with a singleton logical goal share that rule. Matching new authority
  confirms normally; timeout/collision/gap recovery and ordinary confirmed-debt handling
  are retained. This preserves responsive retargeting rather than restoring obsolete
  visual-queue deferral. The 00:41/00:46 replays and five guards pass **498 tests**;
  the user subsequently confirms this correction works well for their tested situations.
- A moving Walk forecast can have a construction origin off the actual
  authoritative route. `alignWalkAuthority` now uses a new checked one/two-tile
  forward server step to rebuild the logical route from that true-tile endpoint.
  It joins the exact visible fraction through its occupied reversible edge or
  checked corridor to the new forecast, preferring forward travel and shorter
  geometric joins. This avoids continuing along a parallel row/column until
  timeout. The existing movement clock, collision checks and prediction limits
  apply; only genuine forward authority grants a fresh reserve. Unchanged tiles,
  opposite-direction stale ticks, expired forecasts and unavailable connectors
  do not rearm it. Pending input, native interactions and unfinished confirmed-prefix
  queues retain their existing handling. The user confirms this correction worked
  really well for scene clicks. Minimap clicks now use the same alignment rule;
  their extension passes regressions and awaits in-game confirmation.
- An interaction may publish a more precise native approach destination after
  its first server step. If the new tile lies on the checked forecast, trim the
  unconfirmed tail there; otherwise the existing bounded, collision-checked
  redirect can connect to it from the occupied edge. A revised native endpoint
  may also finish the occupied reversible knight chord without cutting through
  it or dropping an unfinished confirmed prefix. This check precedes the same
  frame's authoritative update. A failed connector
  reconciles to authority instead of following the superseded tree/object route.
- Ordinary object options and item/widget target-on-object actions share a
  click-time `ObjectApproach` footprint lookup. For a matched solid multi-tile
  rectangular game object, a published destination inside its footprint uses
  a reachable cardinal perimeter staging point instead of the object's anchor tile.
  The 2026-10-08 01:32 follow-up chooses that point with the shared bounded,
  reversible MovementRoute search, rather than raw coordinate distance: the
  geometrically nearer side can require a detour past an equally early boundary.
  Cardinal-first search tie ordering is retained. This is destination construction
  for an observed click/refinement, not a per-frame scene scan or a second route clock.
  Already being there suppresses an unnecessary preview; any onward movement
  still needs native evidence. The staging point does not establish interaction
  reach or permitted sides. Distant routes still use MovementPath collision
  checks, and a revised native destination outside the footprint remains exact.
  The lookup reads only the clicked tile, captures immutable bounds, and is
  cleared with click/scene/lifecycle state. Missing geometry, walkable objects,
  other walls and single-tile objects retain the existing handling.
- Ordinary **Open/Close** options on a matched native `WallObject` (shape 0–3,
  same clicked ID/view/plane) now capture that clicked tile as a singleton
  `ObjectApproach`. This reuses its perimeter staging and MovementPath's checked
  routes rather than predicting travel to a door/gate hinge flag. Actual movement
  through the doorway still comes from authority. An occupied boundary tile does
  not reverse an existing crossing toward its construction anchor; confirmed
  prefixes finish normally. An unchanged preceding Walk flag naming that hinge
  can retire an obsolete preview when authority already stands on the perimeter.
  Late hinge refinements retain the same boundary; destinations outside it remain
  exact. Missing/mismatched walls, other options, targeted item/spell input and
  unsupported geometry retain their existing policies. The lookup is click-time
  only. A newly closed occupied edge still requires checked recovery or native
  fallback; the plugin does not ignore current collision to hide that handoff.
  Nine new regressions pass with the full **507-test** build; in-game confirmation
  is pending. See `agent-work/door-gate-1245/CHECKPOINT.md` for evidence limits.
- Current authoritative coordinates remain separate from rendered coordinates.
- Ordinary object/ground-item options capture immutable `InteractionTarget`
  identity (action/ID/tile/view/plane), separate from destination evidence. A
  matching repeated click retains the original approach and observation/deadlines,
  like a same-target Walk. Item/spell targeting and NPC input retain their existing
  evidence policies. Walk, scene/lifecycle changes and native actions retire this
  identity. Missing/newly changed native destinations still require observation.
- New native object/item destinations use `retargetInteraction` to join an
  authority-anchored checked forecast from the visible fraction. Native refinements
  share `retargetApproachDestination` with ordinary NPCs, keeping original deadlines;
  `alignApproachAuthority` shares the checked forward-authority/fractional join.
  These reuse `joinForecast`, not an object-specific route engine. An occupied
  knight whose logical middle tile is confirmed may trim its obsolete tail to that
  still-forward confirmed point only inside the rechecked proven corridor. It keeps
  fractional position and all real confirmed debt. Blocked joins and unsupported
  authority retain recovery; an unfinished confirmed prefix is not discarded.
- The 2026-10-08 19:49 object capture shows an anchor flag returning while that
  confirmed prefix is unfinished. `retargetApproachDestination` now queues its
  checked authority-anchored replacement behind the preserved prefix instead of
  cancelling the whole speculative tail and waiting at authority. It retains the
  exact fraction, single clock, original response/chain deadlines, NPC reserve and
  queue bounds. Native object-style refinements use their existing adjacent-search
  policy for blocked anchors; ordinary ground items still require the exact native
  destination. Failed/expired forecasts and changed collision retain checked recovery.
  The captured repeated-click ending passes at three cadences, alongside prefix,
  knight, exact-goal, timeout, NPC and rebase guards. The user subsequently reports
  this correction and the tick-aware smoothing follow-up worked well for the tested situations.
- Ordinary ground-item identity also classifies its native destination as **exact**
  for route search. `MovementController.adjacentApproach` excludes those options
  from the generic object-style adjacent search at idle starts, moving handoffs and
  confirmed-prefix continuations. If a direct route is blocked, the same bounded,
  reversible MovementRoute search must reach the observed destination itself. A
  legal adjacent tile cannot shorten the logical goal and later provoke an authority
  rejoin toward that obsolete staging point. Missing/unchanged native publications,
  inaccessible goals, repeat lifetimes, collision proofs and prediction bounds keep
  their existing handling; clicked tile coordinates remain identity, not replacement
  destination evidence. The 20:33/20:35 capture replays and five guards pass the full
  **464-test** build; the user subsequently confirms the exact-item correction was
  good for their tested pickups.
- Ordinary non-Attack NPC options have a click-scoped `NpcApproach` fallback
  when native input publishes no new destination. It captures only the menu's
  targeted NPC, world view and footprint. A bounded straight-first prefix stops
  before the first blocked/non-reversible edge; it never
  searches for a speculative detour to the occupied NPC tile. `MovementPath`
  owns the resulting forecast and all time/distance limits, confirmation and
  recovery, including confirmed-prefix preservation when queued. Attack has a
  separate `CombatApproach` weapon/style profile; the new adjacent-melee handoff
  below supersedes the earlier deferred guard-start experiment for that profile.
  Item/widget-on-NPC actions still require native destination evidence because
  their reach may be ranged. Existing start eligibility gates remain in force.
  A direct indexed identity/footprint check retires a provisional forecast if
  the NPC moves, disappears or changes identity. A newly published native
  endpoint supersedes the fallback even after the input observation window;
  a completed provisional prefix can continue from its exact endpoint within
  its remaining deadline. Ordinary interactions do not acquire a target-follow
  watcher or a renewed prediction budget. Replacement input and lifecycle changes
  clear the capture.
- Adjacent melee Attack uses `CombatContinuity` for click, one-NPC identity/footprint
  and native engagement evidence, with no coordinates/route queue or movement clock
  outside `MovementPath`. Only cache-identified melee styles with adjacent reach
  qualify for melee pursuit; known ranged profiles now use the same target evidence
  with their checked firing-boundary policy below. Halberd/casting/unknown profiles
  keep their existing positional reserve and can retain stationary target facing.
  `NpcApproach.refreshCombat` reads the clicked NPC directly, accepts only bounded
  one/two-tile footprint changes on the same view/plane/identity/size, and publishes
  a new immutable footprint. A pending click expires without native engagement;
  established engagement, real native combat poses and target observation can persist
  across the first hit until input, target, equipment/style or scene invalidation.
  Native animation selectors/model building remain in `PlayerPresentation`.
- `MovementPath.armCombat` / `followCombat` spend a finite movement credit on the
  checked adjacent approach or an observed target step. Actual forward player
  authority can replenish it; a reconciled stationary arrival can retain one unused
  credit until the NPC actually moves. NPC motion alone cannot renew an outstanding
  response/chain deadline or increase the forecast gap. The same checked authority
  join handles off-route melee progress, including during a real native hit pose.
  Stationary target observation creates no new positional itinerary. Failed or
  expired previews use normal checked recovery; unfinished confirmed prefixes
  remain protected. Melee facing uses the displayed travel/target geometry under
  the same turn cap, and eligible confirmed-leg pose gaps use the existing 100-ms
  bridge. The user reports the initial 396-test combat work is working well.
- Arrival latches a melee gaze lock in `CombatContinuity`, retained through repeated
  Attack on the same NPC. `NpcApproach.facingPoint` reads that target's native drawn
  local position directly; it does not enumerate the scene or retain a model.
  The same capped facing budget now prioritizes that point over travel headings
  while locked. `PlayerPresentation` keeps the custom provider's facing ownership
  through aligned native idle; scene/input/profile/target/engagement invalidation
  still restores native presentation normally.
- Post-arrival pursuit remains entirely in `MovementPath`: 65% of configured walk
  rate and an exponential last-leg tail preserve continuity across observed steps.
  An owned hit event on the selected NPC is evidence to release checked confirmed
  debt to current authority at normal pace, with no positional snap or native
  animation changes. Only `Hitsplat.isMine()` on that target qualifies. The event
  listener is the existing plugin hitsplat subscription; pending evidence is bounded
  and cleared with combat state. Prediction deadlines/credit/gap checks are retained,
  and a missed-hit fallback releases confirmed slow pacing at the response bound.
  New diagnostics copy lock/facing-point/hit/pacing scalars on the client thread.
  The five latest captured timelines plus ownership/cadence guards pass the full
  409-test build; the user subsequently reports the gaze/slower-pursuit refinement
  is working well for the tested situations.
- The 2026-10-04 18:10 combat follow-up generalizes **Attack-facing ownership** across
  captured profiles. A stationary selected Attack target is faced before native
  engagement/poses arrive, through shots and between actions. Its directly indexed
  footprint may refresh by bounded one/two-tile steps; facing uses its native drawn
  local point. Established engagement and pending-click expiry retain their existing
  evidence rules. The user explicitly retains the previous moving melee gaze lock.
- `CombatApproach` recognizes native cache **Ranging**, the XP-style label used for
  Accurate/Rapid. Known ranged weapon names reuse the existing reach table; unknown
  profiles remain conservative. `NpcApproach.rangedSteps` supplies a straight-first,
  reversible prefix whose walk step or whole run pair ends inside the supported
  weapon/style distance with RuneLite `WorldArea.hasLineOfSightTo` evidence. This
  reads bounded target/sight geometry, not a scene enumeration. Missing/blocked
  evidence waits for native authority; there is no new speculative detour search.
- The prefix enters MovementPath's existing `seed` / `followCombat` / `joinForecast`
  handling, preserving normal pacing, the single clock, confirmed-prefix guards and
  unchanged finite credit/deadlines/gaps. Native forward authority may align it.
  Ranged pursuit never inherits the melee 65% trail or owned-hit debt release.
  A real native ranged pose drops speculation before positional advance, retaining
  checked confirmed debt; target facing survives. Closing sight invalidates its
  forecast without discarding a checked confirmed prefix.
- Walk from captured combat or an observed cache-attackable native NPC releases
  that target immediately. `combatWalk` is input/facing evidence, not a movement
  engine. It permits the same native destination observation, smoothing, retargeting
  and forward-authority alignment through remaining native combat poses. Those
  poses cannot retire the Walk forecast or make facing turn back to the old NPC.
  Ctrl, effects, disabled starts and native-location actions keep their gates.
  A fresh native NPC engagement/pose with auto-retaliation enabled, after the Walk
  observation/destination are gone, can cancel the obsolete speculative tail and
  restore native facing. Checked confirmed movement remains intact during catch-up.
  Scene/profile/input invalidation and shutdown clear these owners symmetrically.
  All ten newest timing replays and seventeen guards pass the full **457-test**
  build; the user subsequently reports this combat follow-up is **working really well**.
- Moving NPC clicks can now join a new authority-anchored forecast from the exact
  visible fraction through the occupied reversible edge or checked corridor.
  `retargetNpcApproach` replaces only an obsolete preview; an unfinished confirmed
  prefix still uses queued continuation. This prevents the old Walk forecast from
  outliving the NPC observation window and then stopping/retracing before the
  approach. Eligible counters retain their exact logical route, run-pair alignment,
  native flag and stopping goal. Ordinary NPC native refinements use
  `retargetApproachDestination` and the same join with their original deadlines.
  These joins share the geometry of `alignWalkAuthority`, including its forward
  preference and shorter geometric tie-break, within the existing queue/gap limits.
  The user reports the 18:20–18:22 correction was good for the tested situations.
- The subsequent ordinary-NPC continuity follow-up chooses a checked cardinal-side
  staging goal rather than stopping diagonally beside the footprint. Equal nearby
  diagonal starts use a geometric horizontal-step tie-break. A one-step checked
  cardinal approach from within the ordinary adjacent ring remains eligible;
  Bank/Exchange staging is not relaxed. When authority is already beside the NPC,
  a moving red click retires the obsolete unconfirmed Walk tail immediately.
  Ordinary NPC flags farther than the captured adjacent footprint are not approach
  evidence: they can still belong to the preceding Walk. A fresh moving NPC retarget
  can retain a distance-clipped preview through an opposite/perpendicular old Walk
  endpoint within the original response/chain deadlines. The real authoritative
  endpoint is still updated. `alignNpcAuthority` uses only a new checked forward
  step toward that ordinary NPC to rebuild its forecast or confirmed final join.
  It reuses `joinForecast`, fractional position and clock, with no unchecked chord
  or removal of an unfinished confirmed prefix. The 32 new 19:01/19:05 capture
  replays pass at three cadences; the user subsequently reports this continuity
  follow-up was also good for the tested situations.
- **Bank/Exchange** forecasts use the captured immutable footprint to reserve the final
  two-tile approach for authority. Ordinary non-Attack options use a **one-tile
  adjacent boundary**, without the banker startup easing or arrival exception.
  Attack retains its separate weapon/style reserve. `MovementPath.limitForecast` caps the initial
  forecast and its replenishment before knight merging, keeping the full logical
  clicked route available for confirmations. It neither splits an occupied chord
  nor rejects an authoritative step into the reserved ring. Native NPC flags can
  also name a tile closer than the actual interaction stop: the same cap survives
  native refinement, continuation and coordinate rebasing. A completed staging
  preview waits within its existing deadlines rather than restarting from a late
  closer flag. This is a conservative prediction boundary, not an assertion of
  every NPC's interaction reach. Clicks already inside it need native movement
  evidence. When a previously observed NPC flag disappears, extension is retired;
  cancellation uses the next authoritative endpoint or settled native state, not
  the preceding tile on a possible early withdrawal frame. Trace decision
  `completed-native-npc-approach` records this reconciliation. The user confirmed
  prompt starts and the stopping refinement for the tested situations.
- A settled idle Bank/Exchange start exactly three tiles from the footprint can have only
  one forecast tile before the reserved ring. That unconfirmed seed now eases
  toward its checked endpoint instead of finishing early and publishing idle
  while waiting for authority. The exponential step is frame-rate-independent,
  scaled by the movement-speed setting and capped by the ordinary leg rate.
  It uses the same fractional position and clock; no geometry, endpoint or
  deadline changes. A changed authoritative endpoint or replacement input ends
  easing immediately; timeout uses existing recovery. Longer starts, queued
  continuations and refinement connectors retain their existing pacing. The
  user-confirmed stopping boundary is retained; this close-range refinement
  was initially reported working well by the user. The later short Bank/Exchange
  exception below supersedes this easing for its captured three-tile cases.
- The five-tile cardinal case has a narrowly gated final-run-pair exception:
  a single-tile NPC exactly five tiles from the origin, a straight checked
  four-step route to its adjacent tile, a matching newly observed native goal,
  and the first two route steps newly confirmed as a run. `releaseNpcArrival`
  rechecks both remaining edges in both directions and releases the final tile
  into the same speculative queue. Normal run pacing continues through the
  generic staging ring to the adjacent goal, without resetting fractional
  position, adding another time budget, or renewing prediction deadlines.
  The last pair must fit the existing forecast distance/time limits. Its goal
  translates with the path on rebase. Cancellation/replacement, a changed
  destination/target or run mode, action and timeout reconcile through the
  existing pipeline. The rule is restricted to Bank/Exchange options; Attack and
  unsupported distances, walking, offsets or detours retain their own policies.
  Activation logs `released-npc-arrival`, explaining the exception to the
  generic `npcReserveTiles=2` snapshot. This replaces the 2026-09-30
  `eased-npc-arrival` approach, which the user rejected after the 11:12 captures.
  The user subsequently confirmed this five-tile replacement worked for the
  tested case.
- The subsequent 12:48/12:49 Bank captures add a separate short-start exception:
  an idle visible/authoritative tile, active run, a single-tile clicked NPC
  three tiles away on the major axis and zero/one/two tiles on the minor axis.
  It is gated to Bank/Exchange options. `NpcApproach.shortRunGoal` uses the
  adjacent major-axis side for zero/one-tile offsets when its two straight-first
  edges are reversible and clear. Two-tile offsets retain their row/column to
  use the recorded extended reach; a blocked adjacent side can use the same
  checked straight pair. Neither candidate requires a detour search.
  `anticipateNpcRunPair` seeds the entire two-step prediction in the existing
  queue and clock, bypassing staging/idle-start easing for this case. Knight
  chords still require both orderings; otherwise their checked corner remains.
  Startup distance/time bounds are retained, no deadline is renewed, and native
  refinements remain authoritative. A one-tile or unsupported first update
  retires the provisional whole-pair assumption, along with target/mode/action
  changes. Native flag withdrawal before the first endpoint must not interpret
  the idle native origin as a completed approach; bounded timeout still applies.
  Activation logs `started-npc-run-pair`. The extension passes regressions and
    awaits in-game confirmation.
- The 23:14–23:22 capture adds a geometry-based counter policy ahead of those
  generic NPC policies. A Bank/Exchange click on a single-tile, movement-blocked
  NPC footprint with exactly one unblocked cardinal side (and no full projectile
  block on the target tile) captures that side once. `NpcApproach.counterRun`
  uses the existing reversible, 64-step/2,048-visited-tile search to that side.
  It retains the checked route's order and stops at the first whole-run-pair
  endpoint in the captured rounded two-tile envelope: max axis distance <=2,
  sum of axis distances <=3. All 57 observed Bank endpoint sequences agree with
  that rule. This is a bounded forecast for supported counter geometry/options,
  not a claim about every NPC's reach or the game's hidden route algorithm.
  Already being in the envelope does not manufacture movement.
  `anticipateCounterRun` seeds that exact prefix into MovementPath, or
  `anticipateCounterContinuation` queues it behind an unfinished confirmed prefix.
  Reconstructing a new direct route to the shortened endpoint is deliberately
  avoided, since that changes the intermediate tiles around the counter.
  `counterNativeGoal` retains the adjacent native flag separately from
  `npcArrivalGoal`, the provisional server stopping point. A matching late native
  flag confirms the route evidence without redirecting or resetting its clock.
  Normal run pacing replaces startup easing/staging stops in this case. Prediction
  time/distance limits, reversible edges and both-order knight proofs still apply.
  Unexpected one-step/off-route authority, changed run/target/action or native
  goal retire the assumption; early flag withdrawal waits for authority within
  existing bounds. Unsupported geometry/options retain their earlier handling.
  There is no GE coordinate or NPC-ID table. The user confirmed this correction
  works in-game for their tested situations;
  see [the counter checkpoint](agent-work/ge-counter-2314/CHECKPOINT.md).
- The 19:45–19:46 banker capture extends that same one-sided counter policy to
  **Talk-to** only when the clicked target's native cache actions include Bank or
  Exchange and the collision geometry qualifies. Cache role evidence is read once
  from the transformed NPC composition (raw composition fallback); no name/ID
  table or per-frame service lookup is used. Ordinary Talk-to, unsupported service
  geometry and other options retain their own policies. This avoids extending an
  ordinary adjacent forecast past the bank counter's actual whole-run-pair stop
  when a closer native flag appears. All twelve recorded cases match the existing
  counter route/stopping rule and pass constant-pace replays; in-game confirmation
  of this extension was subsequently reported working well by the user.
- The 2026-10-04 13:10 capture distinguishes **start eligibility** from the
  preceding whole-run-pair stopping rule. A stationary `(2,2)` diagonal start at
  the same bank counter performs no native movement, but the old start policy
  invents a pair to its adjacent side and returns on timeout. For a fresh click,
  `NpcApproach.atCounterStartBoundary` now gates the two-tile square only when
  `counterSide` identifies the supported Bank/Exchange or cache-qualified
  bank-service Talk-to geometry. `MovementController.tryStart` retires an old
  speculative tail through the existing `cancel` path, preserves checked
  unfinished confirmed movement, and waits for actual authority. A closer native
  destination alone cannot prove movement is required. New authority still enters
  the normal shared pipeline. `counterRun` uses this start gate, while its existing
  rounded envelope for continuing whole-pair stops is retained. Thus an in-flight
  longer run can pass a `(2,2)` corner under its original forecast without being
  clipped there. Ordinary Talk-to/Trade/Attack and unsupported counter layouts
  retain their own distances. Trace decision `at-counter-boundary` records the
  gate. The full 430-test build passes; in-game confirmation is pending. See
  [the corner checkpoint](agent-work/bank-counter-1310/CHECKPOINT.md).
- The **2026-10-05 19:25** single Bank capture adds an arrival distinction: one
  checked run pair stops at the accessible-side `(2,2)` corner with no native flag.
  `NpcApproach.counterRun` retains the same full ordered rounded-counter route,
  with a `boundaryGoal` at that whole-pair corner when its signed offset is on the
  sole accessible cardinal side. Corners behind the solid counter retain their
  original route. `MovementPath.counterBoundaryGoal` caps only the forecast;
  confirming the corner does not release the next pair. A matching new native flag
  invokes `releaseCounterBoundary`, rechecking the retained onward edges and using
  the same `extend`/queue/clock while preserving the original response/chain deadlines.
  Actual onward authority remains accepted normally. Idle starts, queued confirmed
  prefixes and fractional counter handoffs all share this reserve; rebasing translates
  it, replacement/recovery retires it, and trace snapshots publish it. The reported
  arrival goal reflects the current cap while the logical route retains its original
  tail. The fresh-click two-tile-square gate and earlier rounded whole-pair runs
  remain supported. Ten new regressions plus all earlier counter captures pass the
  full **517-test** build. The user reports the correction is working well for
  the tested situation; other counter layouts remain unconfirmed. See
  [the arrival checkpoint](agent-work/counter-corner-1925/CHECKPOINT.md).
- Normal base travel of 4/8 local units per 20 ms walking/running, scaled by the
  movement-speed setting: **5.0** is the former **1.0** pace, with 0.1 increments
  applying a 2% change. The setting ranges from 0.5 to 10.0. Changing speed does
  not reset position. Internal normalization clamps rather than rounding the
  multiplier: 5.1 now retains its intended 1.02 pace throughout construction,
  live changes, forecasts and diagnostics.
- One time budget per rendered update, including queued/corrective legs.
- Optional **Catch up to true tile** (`catchUp`, default on) applies a bounded
  displayed-speed boost only to queued travel ending at confirmed authority.
  **Catch-up speed boost (%)** (`catchUpPercent`, default 10, clamped 0–50)
  is relative to the configured base walk/run rate. The extra fraction is
  `percent / 100 * min(1, remainingConfirmedTravel / 128)`, so it tapers over
  the last tile and returns to base pace at authority. MovementPath integrates
  that rate exactly against its existing frame-time budget, including curves,
  corners and remaining time after crossing into an unconfirmed tail. Debt is
  measured along the bounded queue, not by drawing a shortcut to the true tile.
  A confirmed endpoint must remain in that queue; an unconfirmed knight endpoint,
  an awaiting-origin/fresh reversal, speculative travel ahead of authority and
  rejection recovery receive no extra pace. Slow melee pursuit retains its
  existing rate; continuous confirmed running retains its base-rate final
  100-ms exponential reserve. Toggling/changing strength does not reset fractional
  position or the clock. Forecast construction uses the unboosted rate, and no
  route, collision proof, deadline or prediction credit changes. The 2026-10-09
  checkpoint passed 617 tests; the user subsequently confirms catch-up is working well
  for their tested situations.
- **Movement pacing** now groups the unchanged persisted `catchUp` /
  `catchUpPercent` keys with **Slow down ahead of true tile** (`slowAhead`, default
  on) and **Ahead slowdown (%)** (`slowAheadPercent`, default 10, clamped 0–50).
  For an agreed checked forecast, MovementPath measures lead from logical
  unconfirmed travel minus its remaining queued travel, including local curve arc
  length. Rate becomes `baseRate * (1 - percent / 100 * min(1, max(0, lead) / 128))`.
  This ramps from ordinary pace at authority to the configured reduction one tile
  ahead. Exact time/distance integrals retain frame-splitting independence and the
  same leftover time across a confirmed-to-preview boundary. A confirmed prefix
  still uses catch-up when enabled; lead cannot slow it before authority. Stale
  reversal/awaiting-origin gaps, disagreeing/recovering routes, close NPC startup
  easing and combat tracking retain their existing policies. No authority, forecast
  geometry/reserve/deadline, primary animation or facing budget changes. The new
  639-test build includes both pacing options in the circle/marked-knight replays;
  ahead slowdown awaits in-game confirmation.
- A confirmed run can finish before the next native endpoint even with its gait
  bridge. While an ordinary native run remains in transit with a destination
  beyond authority and no action/pending input, its final checked leg now retains
  an exponential tail over the last 100-ms portion. The shared exponential helper
  also serves the existing NPC/combat tails. This is confirmed debt, not speculative
  onward travel: no tile, second clock or deadline is added. The existing response
  bound limits it; arrival/action/replacement/lost evidence resumes normal pacing.
  Speculative, corrective, walking and interaction/combat paths retain their own pacing.
- A confirmed leg may finish one or two client frames before the next server
  endpoint during continuous running. The native gait pose is held for at most
  100 ms while the native actor is still in transit and a destination remains
  ahead. The MovementPath remains stationary at its confirmed endpoint; action,
  scene changes and actual arrival end the pose bridge immediately.
- Scene rebasing translates the complete path and sub-tile position together.
  Ordinary non-instanced loading can now resume a still-valid preview when the
  translated native destination agrees (or arrival confirms that goal), the new
  collision map validates it, and its original prediction bounds allow it.
  `resumeScene` reconciles new authority without first cancelling the forecast
  toward the old confirmed tile. Rebase alone renews neither deadline and spends
  no loading-time movement budget. Expiry, changed destinations or unsupported
  progress use checked recovery; confirmed recovery retains an unfinished valid
  prefix. Hopping/connection loss retire the path, and instance changes, changed
  owners/views/planes and discontinuities do not use preview resumption.

Fresh yellow destinations currently enter `MovementPath.retargetWalk`, which
can replace an obsolete confirmed visual itinerary as well as a preview. It
connects from the exact fractional position through a checked occupied edge or
fully checked knight corridor, including its middle-tile anchors. Ordinary
reconciliation and native interaction refinements retain their confirmed-prefix
guards. See `agent-work/random-direction-1249/CHECKPOINT.md` for that policy's
user confirmation. Shared Walk-click smoothing now precedes this replan; it does not
change the collision geometry, movement time budget or hard prediction deadline.

The 2026-09-28 evening route corrections refine only these fresh-click
connections. Equal max-axis travel scores use geometric distance as a tie-break,
avoiding an unnecessary return through a knight chord's old origin. A cardinal
click still ahead can prefer its legal forward corner over a backstep to begin
a new knight, at a cost of at most one extra tile; this preference is disabled
when the onward route itself returns through the occupied edge. A new route
already containing the visible point can omit its construction-anchor connector.
For a new knight join, containment uses the proven parallelogram, not its larger
bounding rectangle. An occupied diagonal can use a side corner only after both
diagonals and all four perimeter edges are checked in both directions. That
one-tile corridor is retained for collision validation, cancellation and rebasing.
See `agent-work/route-stability-2145/CHECKPOINT.md` for capture evidence and tests.
The user subsequently confirmed these reported stall/backtracking corrections
worked well in-game; this is confirmation of the tested situations, not of every
collision layout.

The 2026-10-08 21:43 spam-click circle follow-up retains a Walk's last travel tangent
when selecting a fresh checked connector. A still-forward candidate can be preferred
within the existing one-tile travel allowance; explicit reversals keep normal selection.
Fresh Walk/forward-Walk-authority joins can then spend a one-shot local cubic blend
inside the occupied proven knight parallelogram or fully checked diagonal square.
All controls and the endpoint must be inside that convex region and the existing
forecast gap. Its endpoint rejoins the same leg within roughly 120 ms of ordinary
travel; no whole-itinerary curve or new logical tile is added. MovementPath integrates
max-axis arc length under its existing clock/rate budget, including any remaining
time after the blend. Unsafe/unsupported joins retain straight checked connectors.
Scene translation shifts active controls and preserves arc progress; cancellation
uses the existing checked corridor recovery. Traces publish immutable `joins` controls.
The captured replay shows lower aggregate velocity discontinuity at the 20-ms grid;
the 579-test checkpoint passed, and the user subsequently reports it seems to be working
well for the tested situations. See
`agent-work/circle-2143/CHECKPOINT.md` for evidence limits.

The 22:29/22:37 follow-up extends stale-reversal recognition from the first logical
step's line to the direction of the whole latest itinerary. A late knight endpoint
must not confirm a newly retargeted route toward its already-true goal and append an
extra return trip. Checked forward logical/construction-origin evidence still confirms
normally, including a necessary detour whose first step moves away from the geometric
goal. Stale authority updates the true endpoint within the same response/chain/gap
bounds and grants no renewal; timeout and collision still use checked recovery.
Fresh Walk knight destinations can now use `retargetCheckedKnight` to retain the whole
authority-anchored chord if its proven parallelogram contains the displayed fraction.
A fraction within a quarter tile of authority may first finish its proven occupied
connector. Both step orderings, native run eligibility, queue/clock and bounds still
apply. That tiny alignment faces the onward chord rather than the preceding click.
Unsupported geometry retains the existing checked retarget selection. Local cubic
facing now blends monotonically between intended endpoint tangents, rather than
following a derivative that briefly overshoots the terminal heading; spatial continuity
still uses the true derivative, and the remaining leg adopts its correct residual heading.
The eleven new checks and full 590-test build pass; in-game confirmation is pending.
See `agent-work/repeated-knights-2237/CHECKPOINT.md`.

The user subsequently confirms substantially improved circles and most reciprocal
knights. The 23:33 marked-pause follow-up handles late authority at a near visible
endpoint: a fresh Walk may preserve a complete checked knight from the occupied
leg's forward endpoint within a quarter tile, with the same bounded reversible
authority connection. It does not choose the old start merely to exploit a new chord.
`sharedCorridorCrossing` can join the occupied and new convex proven corridors along
one visible straight line. Its bounded click-time entry calculation accepts a rounded
crossing only inside both regions; each queue subsegment retains its own corridor for
collision validation, cancellation and translation. There is no new scene scan or clock.
Where no shared straight crossing is proven, checked alignment/corners remain required.
Fresh visible-idle scene/minimap Walk starts also use `pendingWalkConfirmation`, sharing
the existing moving-retarget rule when a new goal equals old authority but display is
elsewhere. Delayed preceding-click endpoints must not create extra confirmed return debt.
Original response/chain/gap limits and real matching confirmation/recovery remain.
All 81 new clicks replay at three cadences, alongside geometry/collision/idle guards;
the full 598-test build passes. In-game confirmation of this follow-up is pending.
See `agent-work/marked-knights-2333/CHECKPOINT.md` for marker and replay evidence limits.

There is no five-by-five animation moveset table, leap controller, Woox-walk
detector, kill celebration, tick-perfect combo counter, or older normal-movement
tween underneath this engine.

## Presentation and native animations

Explicit **Follow** menu options on players/NPCs capture `FollowPresentation`:
one target's identity, view/plane, pending-click time and native engagement, with
no route or movement clock. They do not seed a stationary NPC approach. Obsolete
speculation retires through MovementPath's checked cancellation; unfinished
confirmed movement and fractional position remain until an aligned handoff.
Matching native engagement plus a finished path, exact native/displayed position
and facing within ten orientation units allows native rendering even when
`originalWhenAligned` is disabled. An idle aligned Follow can transfer before the
first native step. Ownership then stays native through ongoing movement/actions,
restoring selectors and hiding custom body/effects, rather than alternating native
and custom passes at each arrival. Walk/other world actions, lost engagement,
direct indexed target invalidation, native-location actions and scene/lifecycle
changes release it. An unengaged click expires after 1.8 s; same-target repeats
retain that lifetime. Attack/Talk-to/Trade are not Follow evidence. Native departure
from Follow is adopted fractionally through the existing MovementPath. The 20:43
player capture supplies endpoint/native-point evidence; NPC Follow is API-double
parity coverage. Twelve regressions and the **529-test** build pass; in-game
confirmation is pending. See `agent-work/follow-2043/CHECKPOINT.md`.

The 21:17 continuity follow-up retains native **primary** action IDs/frames for every
attack/cast/block and supplies **secondary** walk/run/idle selectors from displayed
gait even while an action or spot effect is active. This avoids borrowing run/turn/walk
poses from the hidden actor when its motion disagrees with MovementPath. Native tick
idle selection still matches gait during anticipated/catch-up movement so the native
pose clock progresses. If a primary action is active there, NativeModelObject scopes
the real idle selector to each availability probe and draw-time native build, restoring
the tick selector in `finally`; the native builder can blend the gait with its real
primary action. No primary animation/frame writes, shared posed-mesh retention,
mesh transforms or extra animation controller are introduced.

Combat effect permission is also style-independent: `CombatEffectCarry` retains
bounded identity/ID/hash/start-cycle metadata for at most eight observed effects during
Walk release from combat. Existing effects can finish through the same checked Walk;
replacement/new effects cannot inherit the capture. A 900-ms metadata lifetime and
100-ms post-click publication window leave MovementPath's budgets unchanged. Recent
Attack/Cast input supplies only 1.8-s escape evidence, including manually selected
spells; it does not grant a speculative spell range or target route. Input, profile,
scene and native-location invalidation clear ownership. This covers melee, ranged,
magic and unknown weapons without an animation/weapon whitelist. The 491-test build
passes; the user subsequently confirms the all-attack change is working well for
their tested situations. The later fresh-Walk confirmation change is also user-confirmed
working well for the tested situations.

`MovementFacing` applies one frame-scaled `turnSpeed` budget per preparation.
Short legs and reversals use the same cap as other custom facing changes; there
is no arrival-fraction acceleration. Held arrivals retain the travel heading
and finish turning after positional movement ends. Once native facing takes
over, a later idle click holds the current displayed angle rather than resuming
an old route's heading. Fractional orientation is retained across updates.
The circle follow-up integrates a short exponential tail over the last 32 orientation
units (about 5.6 degrees), avoiding an abrupt capped-turn stop on small headings.
It retains the same maximum turn budget and frame-splitting independence for a held target.

The optional Movement setting **Face interactions on arrival** (`faceInteractionsOnArrival`,
default off) captures `InteractionFacing` evidence for non-combat NPC options and
object options/item-widget targeting. It retains the clicked NPC's existing checked
identity/footprint or immutable object bounds (clicked tile when no footprint is available).
Once the displayed path stops at the nearby interaction boundary, it selects that
target on the same arrival frame under MovementFacing's single capped turn budget.
It can outlive native destination withdrawal and the arrival action, so native
catch-up need not finish first. Distant exhausted forecasts and recovery are not
arrivals. A later checked approach leg restores travel facing. Matching native/displayed
facing releases the short-lived owner; idle pending evidence and arrival ownership
are each bounded to 1.8 seconds. Walk/replacement input, toggle-off, target invalidation,
native-location actions, scenes and cleanup retire it. Combat and explicit Follow
retain their existing owners. No positional queue, animation/model handling or
server-facing actor orientation is changed. Eight new controller regressions and
the full **556-test** build pass; the user subsequently reports this option is
working well for the tested situations. The newer refinement/smoothing build passes
**572 tests** and is also user-confirmed working well for the tested situations.

`MovementController` observes the local player, coordinates input and scene
changes, and advances one `MovementPath`. `PlayerPresentation` caches/restores the
native pose selectors and uses the native model builder for idle, walk, run,
equipment, actions and appearance effects. On RuneLite 1.13.0, `NativeModelObject`
extends the supported `RuneLiteObjectController`: preparation probes availability
and discards the model; `getModel()` calls the native builder again when the
temporary-object pass actually draws it. That API explicitly permits shared
models for immediate drawing. It holds a renderable source, never a temporary
posed mesh across frames, and installs no animation controller. This replaces
the removed `Client.mergeModels(Model...)` API; merging `ModelData` would not
preserve an already-posed body.

If preparation finds no model or an unusable pose frame, it clears the custom
provider and keeps native presentation. The old detached last-mesh fallback is
not retained on 1.13.0. A model becoming unavailable between the probe and draw
can return null for that draw. The user reports that the 1.13.0 compatibility fix
worked when running the plugin; the full set of rendering edge cases is not
separately confirmed.
The custom object remains registered during aligned native-player handoff, while
its source is hidden. Native actor spot animations use separate draw-time model
providers at the visible transform. Shutdown clears sources, unregisters the
objects and restores selectors. The camera marker uses the cache-loaded static
model directly, without any mesh transform or animation controller.

Publication preparation unregisters and removes expired effect providers rather
than retaining invisible objects. Native fallback retains only the hidden body
provider; effects are removed. The optional native-position marker uses its fixed
original orb and unregisters when disabled or aligned. Renderer-loss cleanup is
owned by ClientTick so BeforeRender cannot suppress the transition cleanup;
shutdown captures the retiring controllers before queued client-thread cleanup.

Native model-provider callbacks are explicitly client-thread-only: an off-thread
`getModel()` request returns null before reading actor/effect state. The plugin's
separate `RenderCallback` filtering still consumes only immutable `RenderState`
snapshots, including when invoked from the map-loader thread. See
[RUNELITE-1.13.md](RUNELITE-1.13.md) for the API references and migration checks.

Real teleports, supported native-location actions and POH arrival use the native
presentation. They are not synthesized movement animations. Scene invalidation
publishes the native fallback before replacing scene-owned objects.

## Camera and input

`PresentationCamera` follows the visible model in world-relative coordinates.
It retains frame-rate-independent following, snap/follow distances, terrain
footprint sampling, vertical easing, native small/big zoom clamping and follow
height, and the optional native-position marker.
Its retained world-relative focus already survives an area rebase; an API-double
test now covers both-axis translation and restoration to native input mode. The
area-crossing path correction removes the artificial reverse/stop it previously
followed. It cannot supply frames while the native client is stalled loading.

Horizontal follow integrates exponential remaining-distance decay calibrated to
the former 60-FPS response. Its retained world-relative horizontal coordinates use
double precision; only the published native focal point is converted to float.
Equal elapsed time toward a held target now gives the same focus across frame splits,
including zero-time repeated preparation. Snap distance and native input-mode,
zoom and follow-height policies remain in place.

Free camera mode is confined to drawing. The post-draw listener returns to native
camera mode, as do input-processing boundaries. Native minimap conversion and
right-click menus remain authoritative. The plugin observes `MenuOptionClicked`:
Walk arms a start; world interactions retire that prediction/facing hold; widget
and inventory actions are kept separate. Minimap primary presses are observed
without consuming or injecting events. No menu entries or server actions are
created by the plugin.
Minimap presses compare the published destination with the preceding tick's
destination, because interface processing can update it before `ClientTick`.
The minimap controller entry immediately latches that first observed native
publication before an older flag can return during the wait for rendering.
After native input processing, a repeated yellow click can also select an
unchanged native destination if the moving visual route represents a different
previous click; minimap input has already passed that processing boundary at
observation. Same-target repeats retain their preview. Consumed primary events
are ignored; native menu actions and lifecycle changes discard queued primary
presses so a closed menu selection cannot become a second minimap Walk. Native red-click
destination observation still requires a new publication; the ordinary NPC
fallback described above is separate, explicit provisional target evidence.

Scene Walk clicks (including right-click Walk here) and minimap presses share the
`clickSmoothingMs` settling window, default 50 ms and clamped to 0–300 ms. Its UI
label is now **Walk-click smoothing (ms)**; the persisted key/group are retained. Input
observation starts immediately; previews are released after the window measured
from native click observation, not after an additional wait following destination publication.
The first new native destination for each click is latched during that window:
a delayed server update must not replace it with the preceding click's target.
A genuinely new click rearms observation, and a repeated pending walk can retain
its already observed destination. Current path movement and
server confirmation continue normally. The 100 ms destination-observation and
900 ms pending-input limits still apply, and no sleep or timer blocks the client.
Zero restores immediate preview release. Red-click observation retains its
existing input timing. Both yellow click sources use the same eligibility,
replacement, collision, pacing, facing, prediction and authority-alignment rules.
Actual visible response also depends on destination availability and render
cadence, so the configured value is not a guaranteed end-to-end latency.

For nonzero smoothing, the first `GameTick` after that click ends its wait early:
release uses the earlier of the configured timer and that tick. `MovementController.gameTick`
observes the native publication before `MovementInput.gameTick` marks the release.
An already latched destination cannot be overwritten by an older server flag.
The tick grants no destination, positional advance or prediction reserve; the next
normal render preparation performs the same checks and spends the single frame budget.
A later click clears the previous tick-release flag, and scene/plane/lifecycle
invalidation or expiry cannot revive it. The 100-ms native observation and 900-ms
pending-input limits remain, including when smoothing exceeds 100 ms: a destination
must first have been observed within its normal evidence window.

An eligible explicit Walk also retains `walkInput` ownership across a delayed
non-location action from the preceding interaction. That action cannot clear its
observation/smoothing or retire agreement on an already checked forecast. Facing
continues under the same capped budget; native primary animation/frame handling is
unchanged. This flag has no positions or prediction budget: native destination
observation and MovementPath's existing bounds still control motion. Replacement
world input, native-location actions, scenes, renderer/lifecycle cleanup, disabled
starts and settled native/displayed arrival clear it. Unrelated effects retain
their gate; combat's existing bounded effect carry remains separate. A fresh Walk
during an already active ordinary action is still ineligible unless existing
Walk/combat ownership supplies evidence. The 01:32 replay and guards pass; in-game
confirmation is pending. See `agent-work/early-0132/CHECKPOINT.md`.

## Overheads, callbacks, and diagnostics

The user now requests a compact **profiles-only** sidebar, rather than the removed
full duplicated-settings sidebar. `MovementProfilesPanel` owns a single-selection
Profiles list and bottom Load/Save/Delete buttons with RuneLite dark styling. Row
selection is browsing only. Load explicitly applies settings and shows the reminder
to reopen the standard settings page if its already-rendered controls are stale;
it does not require a game/plugin restart. Save prefills the last loaded/saved name.
Delete requires confirmation and only changes saved metadata, not live settings.
The empty-state scroll header is attached only when no profiles exist; a populated
list removes the header viewport entirely so its first row starts at the top border.

`MovementProfilesSidebar` owns navigation, dialogs and UI refresh on EDT and submits
one guarded client-thread task for each action. Lifecycle, configuration-profile
generation and native profile-ID checks reject stale queued actions. Profile changes
cancel open dialogs and reset pending UI ownership. Shutdown captures the retiring
helper, closes dialogs and removes its exact navigation button asynchronously; no
shutdown waits are introduced. Supported ConfigManager writes update the same
persisted keys read by the live plugin. Native settings-panel controls are not
modified through reflection or UI-tree inspection.

`MovementSettings` lists 31 typed movement/appearance settings for profiles and the
three independent debugging controls for bookmark evidence. `MovementProfiles`
reads/validates the retained schema-1 JSON under `savedMovementProfiles` and
`selectedMovementProfile` in the existing group, preserving earlier saves. Load
validates all values before any write and retains current values for missing keys;
unknown/debugging keys are not applied. Same-name save updates the entry. Delete
preserves other saves and clears the last-loaded name only if that save was deleted.
The UI's action/confirmation/notice checks and backend/live-clock checks pass; native
visual presentation of the panel still requires the user's check.
**Debugging** remains the final native settings section, retaining `explainStarts`
and `recordTrace` and adding `traceBookmarkKey`.

`TraceBookmarkKeyListener` observes a configured key without consuming/injecting input,
suppresses repeat, and clears its latch on release/focus loss. Hotkey requests
copy their observation time and queue one client-thread callback. `TraceBookmark`
captures only scalar/settings/local-point and route-snapshot evidence, never posed
models or collision crops. It has an independent entry sequence without changing
click ownership or once-per-cycle position sampling. Disabled recording produces
no bookmark snapshot or filesystem resolution. Bookmark flushes use the existing
bounded ordered asynchronous managed-Filepath writer; no callback waits for disk.
Lifecycle generations reject requests queued by a retiring startup, and shutdown
unregisters the bookmark key listener. The removed sidebar's bookmark button is no
longer available; old button-sourced records remain compatible with the trace format.

`TrueTileOverlay` is an independent, stateless overlay registered/removed with the
plugin. Its **True tile** config section offers an opt-in highlight, `@Alpha` fill
and border colours, 0–10-pixel border thickness and 0–20-pixel edge feathering.
It samples only the local player each overlay frame. Default **Server true tile**
uses `getWorldLocation()` converted within the player's actual WorldView/plane.
Optional **Native movement tiles** snaps `getLocalLocation()` to its containing
tile, allowing intermediate native run tiles between ticks. That mode is explicitly
native interpolation, not a faster server-authority stream. Neither mode reads
MovementPath/custom display position, retains a route/clock, reconstructs missing
steps or scans the scene. Invalid/missing/out-of-scene geometry is suppressed;
stateless selection naturally adopts teleports/rebases/views without old highlights.
RuneLite Perspective projects the actual tile; bounded screen-space AWT feather
bands soften fill/border and use an isolated Graphics2D copy. Renderer/model/facing
ownership is unaffected, and the overlay also works during native presentation.
API-double geometry and AWT raster checks pass; visual renderer confirmation is pending.

`MovementOverheads` retains the original prayer, skull and hitsplat PNGs, overhead
text, HP bars, and Interface Styles HD-health-bar support. The overlay consumes
prepared positions; it never advances movement a second time.
Head projection carries the player's actual world-view ID, suppressing missing
projections and mismatched/out-of-scene positions. `OverheadHitsplats` retains at
most four value-only hits in stable rotating slots rather than re-sorting them by
amount/expiry. `HitsplatApplied` also includes native rejected hits and exposes no
supported native slot/priority evidence. Overflow therefore suppresses the whole
custom UI and publishes native UI visibility through `RenderState`, preserving
native selection at the native player position while custom body movement continues.
That owner lasts until all potentially visible burst hits expire; new burst events
extend only UI lifetime. Clearing overheads clears both slots and native UI ownership.

`RenderState` is an immutable callback snapshot. Filtering requires the exact
local-player ID, player hash type and world-view ID. The renderer callback cannot
mistake a same-ID scenery object for the player or access the movement controller
from the map-loader thread.

The optional trace writes bounded, ordered immutable snapshots asynchronously to
`.runelite/plugin-data/responsive-movement/movement.log`. It records both native fallbacks and
custom movement, including `trueTileIndicator` (the local-player world location
converted to local coordinates, matching True Tile Player Indicators), with no
per-frame scene scan or client-thread file I/O. While recording, each observed
Walk/world-interaction click copies the current plane's collision flags (at most
128 by 128 cells), scene/world coordinates, configuration and the clicked NPC's
identity/footprint/option. The copy occurs at click observation; subsequent world
changes cannot mutate it. Missing/truncated maps are explicit. Formatting and
run-length encoding happen on the existing ordered asynchronous writer.
Each recorded cycle also copies the bounded path's logical route, forecast, queue,
state flags and deadlines. Route details are emitted on change and at the first
sample of each batch; normal position samples reference their `routeSeq` and the
latest `clickSeq`. Multiple clicks within one cycle have distinct sequence IDs.
These diagnostic snapshots never advance or change the path. See
[TRACE-FORMAT.md](TRACE-FORMAT.md) for coordinates, fields and evidence limits.
Samples also include the native destination, last observed walk-click cycle and
prediction decision (started, queued, deferred, expired or unavailable).
`startDecision=settling-click` identifies an observed Walk destination waiting
for its short smoothing window. Fresh moving walk replans use `retargeted-walk`.
`aligned-walk-authority` identifies an eligible off-route Walk forecast joined
to a newly confirmed forward true-tile endpoint. `clickAction=WALK` and
`clickAction=MINIMAP` distinguish the input sources without selecting different
movement policies.
Older captures used `started-reversal` for the narrow reversal handler and
`redirected` for a bounded preview redirect.
Native interaction endpoint changes use `trimmed-native-approach` when the new
tile is on the forecast, `redirected-native-approach` for a checked connector,
or `native-approach-recovery` when neither is possible.
Each sample also carries whether a continuation was currently available, whether
a click observation is still pending and its age, and whether a spot animation is
active, so a deferred start or a red-click approach can be told apart from input
that was dropped after its observation window. The spot flag is only read while
recording is enabled.
NPC fallback starts use `started-npc-approach` or `queued-npc-approach`;
`npc-approach-invalidated` identifies target invalidation. `npcMin`/`npcMax`
record the captured footprint while retained, and `approachGoal` records the
provisional endpoint. These are scalar snapshots, not worker-thread actor reads.
One-sided counter runs use `started-counter-run` / `queued-counter-run`, with
`matched-counter-destination` for a matching native flag. `counter-run-recovery`
and `counter-destination-changed` distinguish retirement conditions. Route
snapshots retain both `counterNativeGoal` and the provisional `npcArrivalGoal`.
Recording continues while enabled, with at most one position sample per client cycle and partial
batches flushed at least once per second during client ticks. The writer keeps
eight approximately 8 MiB files (current plus seven archives). Oldest archives
are replaced on rotation. A temporary full writer queue drops that batch, visible
as a sequence gap, rather than ending the recording. Each line includes local
wall-clock time with UTC offset and the last click's action/type and target ID
for position samples. Click records also carry timestamps; collision columns
reference their owning click. Batches hold at most 128 entries/eight click contexts,
and the writer queue holds at most four batches. No worker reads live client state.

Filesystem operations use RuneLite `Filepath` obtained from the plugin's
`getPluginDirectory()` provider. Startup captures the provider without resolving it;
the ordered asynchronous writer resolves the directory, performs any native
`legacyDataDirectory` migration, rotates archives, and writes the batch. The
descriptor retains `legacyDataDirectory="responsive-movement"` for the former
folder. Production code constructs no raw filesystem path and uses no
`Filepath.Unchecked` API. API-double controllers without an injected writer have
no filesystem capability. See [PUBLISHING.md](PUBLISHING.md).

## Validation boundary

Tests cover the extracted math/state, captured route regressions, camera zoom and
height behavior, input classification, render filtering and bundled overhead
artwork. The standalone lifecycle and final visual integration require the
user's in-game validation. See `VALIDATION.md`.
