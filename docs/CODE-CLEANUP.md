# Unused code and duplicate handling review — 2026-10-12

Reviewed the 34 Java files under `src/main/java/com/responsivemovement` against
the user-confirmed bookmark/settings-sweep commit `6f3d168`. The working tree was
clean before this review. Findings were checked against production callers, test
callers, RuneLite/Swing callbacks and the model/cleanup lifetime contracts.
A development-only Java syntax usage audit helped identify candidates; matching
names alone were not treated as proof that a member could be deleted.

## Removed unused members

| Member | Evidence |
| --- | --- |
| `PlayerPresentation.ready()` | No production or test caller. Preparation already returns availability, and native visibility is queried separately. |
| `PlayerPresentation.location()` | No production or test caller. The controller publishes the displayed MovementPath position; effect transforms use the body object directly. |
| `MovementSettings.SECTIONS` | No source reference. It was display metadata for the removed full settings sidebar; native sections remain defined by `ResponsiveMovementConfig`. |
| `MovementProfiles.selectCurrentSettings()` | No production caller or active UI action. Its only caller was part of an older backend test. The surviving test still checks case-insensitive overwrite, loading, second-save selection and preservation of both saves. |

## Consolidated genuinely repeated operations

| Area | Cleanup |
| --- | --- |
| `MovementController` | Five identical six-statement owner-reset blocks share `clearPresentationOwnership()`. Reset order and each caller's path/input/scene cleanup remain explicit. |
| `MovementInput` | Destination observation and game-tick release share `validObservation()`, retaining the same scene/plane checks, strict expiry comparison and 100/900-ms windows. |
| `MovementPath` | Walk stale-reversal and NPC stale-retarget handlers share the identical authority update/forecast clipping in `retainForecastAuthority()`. Their different evidence gates still determine whether this operation is allowed; it grants no new deadline. |
| `NpcApproach` | Bank-service and attackable checks share the transformed-composition/raw-composition fallback through `nativeActions()`. Each still applies its own action policy and existing dead/identity gates. |
| `PlayerPresentation.prepare()` | Native alignment eligibility is evaluated once and used for idle selector handoff and body visibility. The selector changes between those decisions do not change position, facing, action or effect eligibility. |
| `PlayerPresentation.invalidateScene()` | Removed the second effect-unregistration/map-clearing block. Its preceding `nativeFallback()` already calls `mirrorSpots(false)`, unregistering all effect providers and emptying both tracking collections. |
| Speed conversion/diagnostics | User-facing speed conversion delegates its existing finite/clamp policy to `normalizeSpeed()`. Click diagnostics no longer normalize that already normalized result a second time. |
| `MovementTrace.Sample` | Removed `indicatorX`/`indicatorY`, which always duplicated `ax`/`ay`. The existing `trueTileIndicator` text field is still emitted from the authoritative coordinates with the same format. |

The initial production cleanup removes 29 net lines. It primarily consolidates operations
rather than deleting movement policies. Persisted keys, profile schema, trace schema,
native primary actions, draw-time model providers and prediction bounds retain their
existing contracts.

## Follow-up: removed the historical reversal algorithm

`MovementPath.anticipateReversal()` had no production caller and has now been
removed. Its useful regressions in `MovementPathTest`, `DiagonalReversalTest`,
`OffsetReversalTest` and `ReversalReconciliationTest` use the production
`retargetWalk()` entry point. Coverage retains exact fractional starts, immediate
cardinal/diagonal returns, repeated reversals, reversible collision edges,
disconnected authority rows, stale server updates, forecast gap clipping,
deadline preservation, preview replenishment and timeout recovery.

Assertions that only documented the old algorithm's refusal of turns, forward
clicks or knight chords now check collision-clear, continuous retargets under
the existing deadline. `RedirectTest` exercises the fresh Walk fix on its captured
non-collinear geometry, and `RandomDirectionTest` no longer compares against the
deleted baseline. No test method was removed.

The captured short-diagonal return now explicitly checks the active entry point's
already-true-goal policy: visible arrival stays pending until the response deadline
when no new server evidence arrives, then finishes at actual authority. The active
movement algorithms were not changed by this follow-up. Comments now identify
`anticipateRedirect()` as the native-interaction refinement handler.

The user reports that this cleanup is working well in-game in their tested situations.

## Follow-up: special-case and alignment cleanup

Implemented the three reviewed candidates after the user confirmed the historical
reversal cleanup. This follow-up removes **43 net production lines**: 38 from
`MovementPath` and five from `MovementController`.

### 1. Removed the unreachable approach-arrival construction

In `MovementPath.alignApproachAuthority()`, `goal` is `clickedDestination()`:
the last element of `clickedPath`. The entry guard rejects
`clickedPath.contains(endpoint)`, so the later `endpoint.equals(goal)` branches
could not execute. Removed both the special `idle(endpoint, ...)` forecast and its
`clickedPath`/`preview` singleton initialization.

Arrival at an on-route destination is already handled by `accept()`, called after
alignment in `MovementController.update()`. A permanent
`InteractionContinuityGuardTest` regression now checks that an offset approach
rejects alignment at its goal, then accepts that authority without changing the
visible fraction and finishes at the goal. The on-route guard remains intact.

### 2. Consolidated Walk/scene authority-alignment construction

`alignWalkAuthority()` and `alignApproachAuthority()` now delegate their common
checked forward-step/distance validation, forecast construction, run-state promotion
and `joinForecast()` call to `alignForwardAuthority()`.

Eligibility remains with the callers: Walk requires `awaitingOrigin` and
`reversalPreview` and inherits Walk tangent continuity; scene approaches reject
unfinished confirmed prefixes. Only checked forward authority grants a new horizon.
NPC alignment retains its target/ranged construction and combat evidence policies.

### 3. Migrated native-preview shortening and removed the old trim

Removed `MovementPath.trimPreviewTo()` and its controller fast path. Eligible
scene/NPC shortening now follows `retargetApproachDestination()`, preserving
confirmed movement through `queuePrediction()` and fractional joins through
`joinForecast()`. Existing native-edge fallback and combat boundary policies remain
with their handlers.

The captured tree-stop regression in `RedirectTest` now calls shared refinement.
`NativeApproachShorteningTest` adds six tests covering controller-native flags while
walking/running, multiple confirmed legs with odd/even pair grouping, confirmed
walking corners, NPC staging reserves before final authority, repeated on-route
shortening and original response expiry, and newly blocked/expired endpoints.

The shared handler reconstructs the speculative suffix from authority; it can
regroup a checked run pair instead of retaining obsolete forecast geometry. The
confirmed prefix, fractional position, single-frame budget, original refinement
deadlines and NPC reserve remain covered by regressions. The original tree case
also keeps its explicit obstacle corridor. No regression test method was removed.

On-route refinements now emit `redirected-native-approach` in diagnostics. Existing
captures containing `trimmed-native-approach` remain readable; trace field names and
formats were not changed.

### Older cases with distinct behavior still required

- `replenishReversalApproach()` and `extend()` both append checked previews, but
  use different horizons and collision-failure handling. A temporary comparison of
  the `ReversalReconciliationTest` clipped-return scenario showed that forward
  `alignWalkAuthority()` replanning stops its initial forecast sooner than the old
  replenishment path. Keep the replenishment behavior; a common append helper would
  need explicit limits and failure policy.
- `anticipateRedirect()`/`retargetNativeApproach()` still handle occupied-edge/chord
  fallback and completed provisional NPC prefixes. `NpcApproachTest` checks the
  completed-prefix case; `RedirectTest` checks the chord/prefix guards. The shared
  refinement handler itself calls this fallback.
- `releaseNpcArrival()`, short NPC run pairs and counter-boundary releases change
  which speculative travel is permitted from specific native evidence. Generic
  `extend()` deliberately caps that travel at the staging ring. Their dedicated
  `BankArrivalContinuityTest` and `CounterCornerArrivalTest` guards remain relevant.
- Close-NPC easing, combat trailing and confirmed-run tails already share
  `exponentialFraction()`, but retain different evidence and release conditions.
  Ahead slowdown does not replace those positional reserves.

The initial identification review passed 55 focused tests and three temporary
probes. Implementation adds seven permanent tests and passes the full **693-test**
build. Runtime confirmation of this follow-up is pending.

## Important surviving candidates and lookalikes

- **`anticipateRedirect()` is active.** `retargetNativeApproach()` calls it, and that
  path is used directly by `MovementController.observeRefinedApproach()` and as the
  fallback for `retargetApproachDestination()`. Replacing it wholesale with fresh
  Walk routing would mix native-refinement and new-click policies.
- **`predictionDeadlineNanos()` and `NativeModelObject.ready()` are test observability
  seams.** They expose bounded scalar/provider state without reflection or changing
  movement/model ownership. Lack of a production caller is not stale movement behavior.
- **The authority-alignment and NPC/ranged approach routines have different gates.**
  Their similar-looking geometry is already partly shared through `joinForecast()`.
  Confirmed-prefix, reach, target evidence and deadline differences are meaningful.
- **Profile/trace constructor overloads and thin forwarding overloads support API-double
  tests.** They do not add alternative movement algorithms. RuneLite/Swing entry points
  and configuration accessors are also called by their frameworks, not necessarily
  through a direct source invocation.

This review found a small set of clearly unused members and repeated operations,
not evidence that the settings sweep superseded the historical movement fixes.

## Verification

`.\gradlew.bat build --offline --console=plain --no-daemon` passes **693 tests,
zero failures/ignored tests**, including the full bookmark settings sweep and the
existing native-model, input, combat, interaction, scene/lifecycle and trace checks.
No regression test method was removed. The obsolete profile action assertion was
updated to exercise the active save-selection behavior. `git diff --check` passes.

The historical reversal cleanup is user-confirmed working well in their tested
situations; the subsequent alignment/preview-shortening cleanup awaits its own
runtime check. Use `.\gradlew.bat run` with the
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
guide. Recheck scene/minimap replacements and reversals at differing pacing strengths,
object/NPC approaches and combat/Follow escape, region/teleport/disable-re-enable
handoffs, body/effect cleanup, profile saves/loads/deletion and a short trace/bookmark.
Only the user performs gameplay. No commit, push or Hub marker update was made by
this cleanup review.
