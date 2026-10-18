package com.responsivemovement;

import java.util.function.LongSupplier;
import net.runelite.api.Client;
import net.runelite.api.Actor;
import net.runelite.api.Hitsplat;
import net.runelite.api.NPC;
import net.runelite.api.CollisionData;
import net.runelite.api.GameState;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.util.Text;

/** One controller and one MovementPath for ordinary and anticipatory movement. */
final class MovementController
{
    // Named region constants: RuneLite currently has no corresponding gamevals.
    private static final int POH_WEST = 8046;
    private static final int POH_EAST = 8047;
    private final Client client;
    private final ResponsiveMovementConfig config;
    private final LongSupplier clock;
    private final PlayerPresentation presentation;
    private final MovementGait gait = new MovementGait();
    private final MovementFacing facing = new MovementFacing();
    private final MovementInput input = new MovementInput();
    private final MovementTrace trace;
    private Player owner;
    private MovementPath path;
    private WorldPoint lastAuthority;
    private int worldView = Integer.MIN_VALUE;
    private int plane = -1;
    private int baseX;
    private int baseY;
    private int generation;
    private boolean scenePending = true;
    private boolean sceneContinuation;
    private boolean instanced;
    private boolean firstPresentationPending;
    private boolean pohArrival;
    private boolean yellowFacing;
    private boolean walkInput;
    private boolean preserveFacing;
    private boolean facingSettled;
    private int nativeTarget;
    private int nativeFacing;
    private long facingChangedNanos;
    private long lastFrameNanos;
    private LocalPoint visible;
    private int clickCycle = -1;
    private String startDecision = "none";
    private boolean interactionApproach;
    private LocalPoint observedApproachDestination;
    private ObjectApproach objectApproach;
    private InteractionTarget interactionTarget;
    private NativeApproach nativeApproach;
    private boolean nativeIntentPreview;
    private boolean interactionReplacementPending;
    private InteractionFacing interactionFacing;
    private NpcApproach npcApproach;
    private CombatContinuity combatContinuity;
    private FollowPresentation followPresentation;
    private boolean combatWalk;
    private Actor releasedCombatTarget;
    private boolean combatDisengaged;
    private CombatApproach combatEffectProfile;
    private CombatEffectCarry combatEffects;
    private long combatWalkClickNanos;
    private boolean combatInput;
    private long combatInputNanos;
    private boolean npcPreview;
    private LocalPoint approachGoal;
    private String clickAction = "none";
    private int clickTarget = -1;

    MovementController(Client client, ResponsiveMovementConfig config)
    {
        this(client, config, System::nanoTime);
    }

    MovementController(Client client, ResponsiveMovementConfig config, LongSupplier clock)
    {
        this(client, config, clock, new MovementTrace(clock));
    }

    MovementController(Client client, ResponsiveMovementConfig config, LongSupplier clock, MovementTrace trace)
    {
        this.client = client; this.config = config;
        this.clock = clock;
        this.trace = trace;
        presentation = new PlayerPresentation(client);
    }

    void sceneChanged(boolean loading)
    {
        clearPresentationOwnership();
        ++generation;
        sceneContinuation = loading && (!scenePending || sceneContinuation);
        if (!loading) { path = null; }
        scenePending = true;
        firstPresentationPending = true;
        input.reset();
        clickCycle = -1;
        startDecision = "scene-changed";
        clickAction = "none"; clickTarget = -1;
        interactionApproach = false;
        observedApproachDestination = null;
        objectApproach = null; approachGoal = null;
        interactionTarget = null;
        nativeApproach = null;
        npcApproach = null; npcPreview = false;
        presentation.invalidateScene();
        gait.reset();
        lastFrameNanos = 0;
    }

    void walkClick()
    {
        walkClick(client.getLocalDestinationLocation(), "WALK", config.clickSmoothingMs());
    }

    void gameTick()
    {
        if (owner == null || owner.getWorldView() == null) { return; }
        long now = clock.getAsLong();
        if (nativeApproach != null) { nativeApproach.gameTick(now); }
        if (!input.pending()) { return; }
        // Copy the native publication before ending the wait. An older flag must
        // not displace an already latched click, and a tick is not route evidence.
        LocalPoint previousClickDestination = !interactionApproach && path != null && (path.moving() || "preview".equals(path.phase())) && client.getGameCycle() > clickCycle
            ? path.clickedDestination() : null;
        input.destination(client.getLocalDestinationLocation(), now, generation, owner.getWorldView().getPlane(), previousClickDestination);
        input.gameTick(now, generation, owner.getWorldView().getPlane());
        // Movement still advances once, in BeforeRender, under its usual checks.
    }

    void walkClick(LocalPoint previousDestination)
    {
        walkClick(previousDestination, "MINIMAP", config.clickSmoothingMs());
        // Minimap interface processing has already run before ClientTick.
        // Latch its first native destination now, including an unchanged repeat
        // that replaces a different displayed walk. Do not wait for a render
        // where an older server update could have republished the preceding flag.
        if (input.pending())
        {
            input.destination(client.getLocalDestinationLocation(), clock.getAsLong(), generation,
                owner.getWorldView().getPlane(), path != null && (path.moving() || "preview".equals(path.phase())) ? path.clickedDestination() : null);
        }
    }

    private void walkClick(LocalPoint previousDestination, String action, int smoothingMillis)
    {
        if (interactionFacing != null) { facing.hold(); }
        interactionFacing = null;
        followPresentation = null;
        long now = clock.getAsLong();
        boolean recentCombatInput = combatInput && now - combatInputNanos <= 1_800_000_000L;
        Actor engagedTarget = owner == null ? null : owner.getInteracting();
        boolean leavingCombat = combatContinuity != null || combatWalk || recentCombatInput || engagedTarget instanceof NPC &&
            engagedTarget.getWorldView() == owner.getWorldView() && NpcApproach.attackable((NPC) engagedTarget);
        if (leavingCombat && owner != null)
        {
            CombatApproach profile = combatContinuity != null ? combatContinuity.target.combat : combatEffectProfile;
            if (profile == null || !profile.valid(client)) { profile = CombatApproach.capture(client); }
            combatWalk = true;
            releasedCombatTarget = owner.getInteracting();
            combatDisengaged = releasedCombatTarget == null;
            combatEffectProfile = profile;
            combatWalkClickNanos = now;
            if (!nativeLocationAction(owner.getAnimation()) &&
                (combatEffects == null || !combatEffects.containsOnly(owner, client.getGameCycle())))
            {
                combatEffects = CombatEffectCarry.capture(owner, client.getGameCycle(), now);
            }
            facing.hold();
        }
        clearCombat();
        clearCombatInput();
        trace.click(client, config, generation, action, null, visible, previousDestination);
        clickAction = action; clickTarget = -1;
        interactionApproach = false;
        observedApproachDestination = null;
        objectApproach = null; approachGoal = null;
        interactionTarget = null;
        nativeApproach = null;
        nativeIntentPreview = interactionReplacementPending = false;
        npcApproach = null; npcPreview = false;
        armClick(previousDestination, smoothingMillis);
        // A successfully armed explicit Walk owns its input across a delayed
        // non-location action from the preceding interaction. It supplies no
        // route or renewed budget: destination observation and MovementPath's
        // deadlines still decide whether any preview is available.
        walkInput = input.pending();
        pohArrival = false;
        yellowFacing = true;
        if (!input.pending() && path != null)
        {
            path.replacement(true);
            if (!previewPoseAllowed() || !previewEffectsAllowed()) { path.cancel(); }
        }
    }

    private void armClick(LocalPoint previousDestination, int smoothingMillis)
    {
        // Observe only the destination published after this actual client
        // action. Target coordinates are not necessarily an approach tile.
        clickCycle = client.getGameCycle();
        startDecision = "ineligible-click";
        if (owner != null && config.responsiveStarts() && previewPoseAllowed() &&
            previewEffectsAllowed() && !client.isKeyPressed(KeyCode.KC_CONTROL))
        {
            input.click(previousDestination, clock.getAsLong(), generation, owner.getWorldView().getPlane(), smoothingMillis);
            startDecision = "awaiting-destination";
        }
        else { input.clear(); }
    }

    void worldInteraction(MenuOptionClicked event)
    {
        InteractionFacing previousInteractionFacing = interactionFacing;
        interactionFacing = null;
        walkInput = false;
        FollowPresentation previousFollow = followPresentation;
        followPresentation = FollowPresentation.capture(owner, event, clock.getAsLong());
        if (followPresentation != null && followPresentation.same(previousFollow)) { followPresentation = previousFollow; }
        InteractionTarget nextTarget = InteractionTarget.capture(owner == null ? null : owner.getWorldView(), event);
        boolean repeat = interactionApproach && interactionTarget != null && interactionTarget.same(nextTarget) &&
            path != null && (input.pending() || path.moving() || "preview".equals(path.phase())) &&
            !"recovery".equals(path.phase()) && config.responsiveStarts() && owner.getAnimation() == -1 &&
            !presentation.hasSpotAnimation() && !client.isKeyPressed(KeyCode.KC_CONTROL) &&
            (input.pending() || observedApproachDestination != null &&
                observedApproachDestination.equals(client.getLocalDestinationLocation()));
        CombatContinuity previousCombat = combatContinuity;
        clearCombat();
        clearCombatWalk();
        clearCombatInput();
        trace.click(client, config, generation, event.getMenuAction().name(), event, visible,
            client.getLocalDestinationLocation());
        clickAction = event.getMenuAction().name(); clickTarget = event.getId();
        if (repeat)
        {
            interactionFacing = previousInteractionFacing;
            // Same scene action/ID/tile/view selects the existing approach, just
            // like a same-destination Walk. Preserve observation, geometry and
            // deadlines: an unchanged flag is not a new prediction reserve.
            clickCycle = client.getGameCycle();
            startDecision = "repeated-world-interaction";
            return;
        }
        interactionTarget = nextTarget;
        nativeApproach = null;
        nativeIntentPreview = interactionReplacementPending = false;
        pohArrival = false;
        yellowFacing = preserveFacing = facingSettled = false;
        interactionApproach = true;
        if (followPresentation != null)
        {
            // Follow has a moving native target, not a single interaction endpoint.
            // Retire an obsolete preview on the shared checked path, preserving
            // confirmed debt and its fraction until native presentation aligns.
            input.clear(); clickCycle = client.getGameCycle();
            observedApproachDestination = null;
            objectApproach = null; interactionTarget = null;
            npcApproach = null; npcPreview = false; approachGoal = null;
            if (path != null) { path.cancel(); path.replacement(false); }
            startDecision = "awaiting-native-follow";
            return;
        }
        combatInput = owner != null && event.getMenuEntry().getWorldViewId() == owner.getWorldView().getId() &&
            combatInput(event.getMenuAction(), event.getMenuOption());
        combatInputNanos = clock.getAsLong();
        observedApproachDestination = null;
        objectApproach = ObjectApproach.capture(client,owner == null ? null : owner.getWorldView(), event);
        npcApproach = NpcApproach.capture(client, owner == null ? null : owner.getWorldView(), event,
            client.getLocalDestinationLocation());
        npcPreview = false;
        approachGoal = null;
        armClick(client.getLocalDestinationLocation(), config.clickSmoothingMs());
        nativeApproach = input.pending() ? NativeApproach.capture(client, owner, event,
            client.getLocalDestinationLocation(), clock.getAsLong()) : null;
        if (nativeApproach != null) { nativeApproach.delay(config.clickSmoothingMs()); }
        if (input.pending() && nativeApproach == null) { input.clear(); }
        // Captured NPC/combat policies own their own native evidence and reserve.
        // They must not become an unreserved generic forecast after invalidation.
        if (npcApproach != null) { nativeApproach = null; }
        if (config.faceInteractionsOnArrival())
        {
            // Facing is observation-only and does not require preview eligibility
            // (responsive starts may be off, or Ctrl may suppress anticipation).
            NativeApproach facingTarget = nativeApproach;
            if (facingTarget == null && npcApproach == null)
            {
                facingTarget = NativeApproach.capture(client,owner,event,client.getLocalDestinationLocation(),clock.getAsLong());
            }
            interactionFacing = InteractionFacing.capture(owner == null ? null : owner.getWorldView(), event,
                objectApproach, npcApproach, facingTarget, clock.getAsLong());
        }
        if (nativeApproach != null && nativeApproach.hasIntent() && nativeApproach.adjacentIntent() && path != null)
        {
            path.beginNativeIntentTrip();
        }
        if (input.pending() && (config.clickSmoothingMs()>0 || npcApproach != null || nativeApproach != null && nativeApproach.hasIntent())) { input.intent(); }
        interactionReplacementPending = true;
        if (path != null && config.clickSmoothingMs()==0)
        {
            path.replacement(true); interactionReplacementPending = false;
        }
        if (path != null && objectApproach != null &&
            objectApproach.atWallBoundary(path.confirmed(), client.getLocalDestinationLocation()))
        {
            // An unchanged preceding Walk flag can still point at the door's
            // hinge. Already standing beside that wall is not new crossing
            // evidence. Retire only its obsolete preview on the checked path;
            // confirmed movement and fresh native publications remain available.
            path.cancel();
        }
        if (npcApproach != null && npcApproach.combat != null && owner != null && config.responsiveStarts() &&
            !client.isKeyPressed(KeyCode.KC_CONTROL) && !presentation.hasSpotAnimation() &&
            !nativeLocationAction(owner.getAnimation()))
        {
            combatContinuity = new CombatContinuity(npcApproach,
                LocalPoint.fromWorld(owner.getWorldView(), owner.getWorldLocation()), clock.getAsLong());
            combatContinuity.retainLock(previousCombat);
        }
    }

    boolean update()
    {
        Player player = client.getLocalPlayer();
        if (client.getGameState() != GameState.LOGGED_IN || player == null || player.getWorldView() == null)
        {
            clearPresentationOwnership();
            gait.reset();
            presentation.nativeFallback();
            return false;
        }
        long now = clock.getAsLong();
        trace.enabled(config.recordTrace());
        double delta = lastFrameNanos == 0 ? 0 : Math.min(100, Math.max(0, now - lastFrameNanos) / 1_000_000.0);
        lastFrameNanos = now;
        WorldView view = player.getWorldView();
        LocalPoint authoritative = LocalPoint.fromWorld(view, player.getWorldLocation());
        LocalPoint nativePoint = player.getLocalLocation();
        if (authoritative == null || nativePoint == null) { gait.reset(); presentation.nativeFallback(); return false; }
        boolean ownerChanged = owner != player;
        boolean discontinuity = ownerChanged || worldView != view.getId() || plane != view.getPlane() ||
            lastAuthority != null && lastAuthority.distanceTo(player.getWorldLocation()) > config.snapDistance();
        if (ownerChanged)
        {
            owner = player;
            presentation.owner(player);
            facing.reset(player.getCurrentOrientation());
        }
        if (scenePending || discontinuity || baseX != view.getBaseX() || baseY != view.getBaseY())
        {
            clearPresentationOwnership();
            presentation.invalidateScene();
            gait.reset();
            boolean resumed = false;
            if (!discontinuity && path != null)
            {
                int dx = (baseX - view.getBaseX()) * 128, dy = (baseY - view.getBaseY()) * 128;
                if (!instanced && !view.isInstance() && (!scenePending || sceneContinuation))
                {
                    resumed = path.resumeScene(dx, dy, authoritative, client.getLocalDestinationLocation(), runEnabled(), now);
                    if (!resumed) { path = null; }
                    startDecision = resumed ? "resumed-scene" : "scene-recovery-unavailable";
                }
                else
                {
                    path.rebase(dx, dy, now);
                    if (!path.recover(authoritative, runEnabled())) { path = null; }
                }
            }
            else { path = null; facing.reset(player.getCurrentOrientation()); }
            input.reset();
            observedApproachDestination = null;
            objectApproach = null; approachGoal = null;
            interactionTarget = null;
            npcApproach = null; npcPreview = false;
            if (discontinuity) { yellowFacing = false; }
            if (!resumed) { preserveFacing = facingSettled = false; }
            pohArrival = isHouse(view);
            firstPresentationPending = true;
            scenePending = false;
            sceneContinuation = false;
        }
        worldView = view.getId(); plane = view.getPlane();
        instanced = view.isInstance();
        baseX = view.getBaseX(); baseY = view.getBaseY();
        lastAuthority = player.getWorldLocation();

        if (pohArrival || nativeLocationAction(player.getAnimation()))
        {
            clearPresentationOwnership();
            interactionTarget = null;
            npcApproach = null; npcPreview = false;
            gait.reset();
            path = null;
            visible = nativePoint;
            facing.reset(player.getCurrentOrientation());
            presentation.nativeFallback();
            traceNative(player, authoritative, nativePoint);
            return false;
        }
        if (followPresentation != null)
        {
            if (!followPresentation.observe(player, view, now))
            {
                followPresentation = null;
                startDecision = "native-follow-ended";
            }
            else
            {
                LocalPoint displayed = path == null ? visible : path.position();
                boolean aligned = (path == null || path.finished()) && nativePoint.equals(displayed) &&
                    Math.abs(MotionMath.difference((int) facing.angle(), player.getCurrentOrientation())) <= 10 &&
                    player.getAnimation() == -1 && !presentation.hasSpotAnimation();
                if (followPresentation.nativeOwned || followPresentation.engaged() && aligned)
                {
                    // Latch only at an aligned boundary, then leave native Follow's
                    // gait, turning and changing endpoints together until invalidated.
                    // There is no custom positional tween underneath this handoff.
                    followPresentation.nativeOwned = true;
                    path = null; gait.reset(); input.clear();
                    visible = nativePoint; facing.reset(player.getCurrentOrientation());
                    presentation.nativeFallback();
                    startDecision = "native-follow";
                    traceNative(player, authoritative, nativePoint);
                    return false;
                }
            }
        }
        if (path == null)
        {
            path = MovementPath.adopt(nativePoint, authoritative, runEnabled(), now, MovementPath.configuredSpeed(config.movementSpeed()), this::canStep);
            if (path == null)
            {
                gait.reset();
                visible = nativePoint;
                presentation.nativeFallback();
                traceNative(player, authoritative, nativePoint);
                return false;
            }
        }
        path.speed(MovementPath.configuredSpeed(config.movementSpeed()));
        if (walkInput && !input.pending() && path.finished() && nativePoint.equals(authoritative)) { walkInput = false; }
        boolean effect = presentation.hasSpotAnimation();
        boolean actionOrEffect = player.getAnimation() != -1 || effect;
        captureCombatEffects(now);
        boolean followingCombat = observeCombat(player, view, authoritative, effect, now);
        observeCombatWalk(player);
        if (nativeApproach != null && (!nativeApproach.valid(view) ||
            nativeApproach.counterIntent() && !nativeApproach.sameFootprint()))
        {
            path.cancel(); input.clear(); observedApproachDestination = null;
            nativeApproach = null;
            nativeIntentPreview = false;
            startDecision = "native-approach-invalidated";
        }
        boolean walkEffectsAllowed = !effect || combatEffectsAllowed(now);
        if (nativeApproach != null && nativeApproach.counterIntent() && !runEnabled()) { path.retireNativeCounterPairs(); }
        if (followingCombat && combatContinuity.allowed && (combatContinuity.initial || combatContinuity.moved))
        {
            offerCombat(now);
        }
        if (followingCombat && npcApproach.rangedCombat() && "preview".equals(path.phase()) &&
            !npcApproach.combatBoundary(path.clickedDestination()))
        {
            path.cancel(); startDecision = "ranged-boundary-changed";
        }
        // Pending replacement input owns its observation window. The preceding
        // counter forecast must not cancel it merely because its NPC owner was
        // released by the new click. The normal replacement path retires that
        // forecast once the new destination/delay is ready.
        if (path.counterNativeGoal() != null && !input.pending() && (!runEnabled() || npcApproach == null ||
            !npcApproach.valid(view)))
        {
            path.retireCounterRun(); input.clear(); observedApproachDestination = null;
            npcApproach = null; npcPreview = false;
            startDecision = "counter-run-recovery";
        }
        if (npcApproach != null && npcApproach.combat != null &&
            (!npcApproach.combat.valid(client) || !npcApproach.valid(view)))
        {
            path.cancel(); input.clear(); observedApproachDestination = null;
            clearCombat(); followingCombat = false;
            npcApproach = null; npcPreview = false;
            startDecision = "combat-profile-changed";
        }
        // The native client can refine an interaction destination in the same
        // frame it publishes a server step. Redirect the old forecast before
        // accept() can retire it as a route disagreement.
        if ((!followingCombat || !npcApproach.movingCombat()) && config.responsiveStarts() && !actionOrEffect)
        {
            observeRefinedApproach(now);
        }
        LocalPoint previousAuthority = path.confirmed();
        boolean routeClear = path.clear();
        if (routeClear && interactionApproach && npcApproach != null && (npcApproach.ordinaryOption() || npcApproach.counterBank() || followingCombat) &&
            npcApproach.valid(view) && !input.pending() && (!actionOrEffect || followingCombat) && config.responsiveStarts())
        {
            MovementPath aligned = path.alignNpcAuthority(authoritative, npcApproach,
                client.getLocalDestinationLocation(), runEnabled(), now);
            if (aligned != null) { path = aligned; startDecision = followingCombat ? "aligned-combat-authority" : "aligned-npc-authority"; }
        }
        if (path.counterNativeGoal() != null && !input.pending() && !path.counterRunAccepts(authoritative, now))
        {
            path.retireCounterRun(); input.clear(); observedApproachDestination = null;
            npcApproach = null; npcPreview = false;
            startDecision = "counter-run-recovery";
        }
        if (routeClear && yellowFacing && !interactionApproach &&
            !input.pending() && walkEffectsAllowed && previewPoseAllowed() && config.responsiveStarts())
        {
            MovementPath aligned = path.alignWalkAuthority(authoritative, runEnabled(), now);
            if (aligned != null)
            {
                path = aligned;
                startDecision = "aligned-walk-authority";
            }
        }
        if (routeClear && interactionApproach && npcApproach == null &&
            (interactionTarget != null || objectApproach != null || nativeApproach != null) && !input.pending() &&
            observedApproachDestination != null && !actionOrEffect && config.responsiveStarts())
        {
            MovementPath aligned = path.alignApproachAuthority(authoritative, runEnabled(), now);
            if (aligned != null) { path = aligned; startDecision = "aligned-approach-authority"; }
        }
        if (!routeClear || !path.accept(authoritative, runEnabled()))
        {
            interactionFacing = null;
            // Genuine discontinuities and unwalkable forced movement use the
            // native presentation until a supported local segment is available.
            path = null;
            walkInput = false;
            nativeApproach = null;
            clearCombat();
            clearCombatWalk();
            interactionTarget = null;
            npcApproach = null; npcPreview = false;
            gait.reset();
            visible = nativePoint;
            presentation.nativeFallback();
            traceNative(player, authoritative, nativePoint);
            return false;
        }
        LocalPoint publishedNpcGoal = client.getLocalDestinationLocation();
        if (nativeApproach != null && nativeApproach.counterIntent() && nativeApproach.sameFootprint() &&
            !input.pending() && runEnabled() && !actionOrEffect &&
            path.releaseNativeCounterContinuation(publishedNpcGoal,previousAuthority,now))
        {
            startDecision = "released-native-counter-pair";
        }
        if (npcApproach != null && npcApproach.combat == null && path.npcArrivalGoal() != null && path.counterNativeGoal() == null &&
            (!runEnabled() || !npcApproach.valid(view) ||
                publishedNpcGoal != null && !publishedNpcGoal.equals(path.npcArrivalGoal()) ||
                path.npcRunPairDisagreed(previousAuthority)))
        {
            path.cancel(); input.clear(); observedApproachDestination = null;
            npcApproach = null; npcPreview = false;
            startDecision = "npc-arrival-recovery";
        }
        if (!actionOrEffect && config.responsiveStarts() && npcApproach != null &&
            npcApproach.extendedRangeOption() && observedApproachDestination != null && npcApproach.valid(view) &&
            path.releaseNpcArrival(publishedNpcGoal, previousAuthority, runEnabled(), now))
        {
            startDecision = "released-npc-arrival";
        }
        // A destination can first appear with the server step after the short
        // click-observation window. That checked authority is evidence for the
        // current native action, not permission to revive input or guess range.
        if (!followingCombat && npcApproach == null && nativeApproach != null && !input.pending() &&
            observedApproachDestination == null && !actionOrEffect && config.responsiveStarts() &&
            nativeApproach.acceptsLate(client.getLocalDestinationLocation(), !previousAuthority.equals(authoritative), now))
        {
            LocalPoint published = client.getLocalDestinationLocation();
            observedApproachDestination = published;
            LocalPoint goal = approachGoal(authoritative, published);
            MovementPath refined = path.refineConfirmedApproach(goal, runEnabled(), now, adjacentApproach(published));
            if (refined != null) { path = refined; startDecision = "observed-native-approach-authority"; }
            else if (unprovenObjectDestination(published)) { startDecision = "unproven-object-destination"; }
        }
        boolean nativeIntentArrival = nativeApproach != null && nativeApproach.atIntentBoundary(authoritative) &&
            client.getLocalDestinationLocation() == null;
        boolean measuredIntentTrip = nativeApproach != null && nativeApproach.hasIntent() && nativeApproach.adjacentIntent();
        if (!followingCombat && (npcApproach != null || objectApproach != null || nativeApproach != null) &&
            observedApproachDestination != null && client.getLocalDestinationLocation() == null)
        {
            // Native input can withdraw the flag one render before the final
            // authoritative endpoint arrives. Retire extension immediately, but
            // reconcile only against that new endpoint (or native settled state),
            // never reverse toward the preceding server tile on the early frame.
            path.replacement(false);
            if (!previousAuthority.equals(authoritative) || nativePoint.equals(authoritative) && !path.awaitingNpcRunPair())
            {
                String completed = objectApproach != null ? "completed-native-object-approach" :
                    npcApproach != null ? "completed-native-npc-approach" : "completed-native-approach";
                boolean interruptedNativeInput = nativeApproach != null && nativeApproach.pending(now) &&
                    !authoritative.equals(approachGoal);
                path.cancel(); input.clear();
                approachGoal = authoritative;
                observedApproachDestination = null;
                npcApproach = null; npcPreview = false;
                // A preceding click can withdraw its flag alongside its final
                // server step. Keep only the new click's original bounded native
                // evidence, so a later flag plus fresh authority can use the
                // existing acceptsLate/refineConfirmedApproach handoff.
                if (!interruptedNativeInput) { nativeApproach = null; }
                startDecision = completed;
            }
        }
        if (!config.responsiveStarts())
        {
            walkInput = false;
            clearCombat();
            clearCombatWalk();
            path.cancel(); input.clear(); observedApproachDestination = null;
            nativeApproach = null;
            npcApproach = null; npcPreview = false;
        }
        boolean ownedWalk = (combatWalk || walkInput) && walkEffectsAllowed && previewPoseAllowed();
        if (actionOrEffect && !followingCombat && !ownedWalk)
        {
            interactionTarget = null;
            // An interaction has begun: discard any remaining approach preview
            // rather than carrying the visible player past the action tile.
            if (interactionApproach)
            {
                if (npcApproach != null && npcApproach.combat != null) { startDecision = "combat-action-recovery"; }
                path.cancel();
            }
            path.replacement(false); input.clear(); observedApproachDestination = null;
            nativeApproach = null;
            npcApproach = null; npcPreview = false;
        }
        if (followingCombat && (effect || player.getAnimation() != -1 &&
            (!combatContinuity.engaged() || !npcApproach.adjacentCombat()))) { path.cancel(); }
        if (followingCombat && combatContinuity.allowed && combatContinuity.progressed) { offerCombat(now); }
        boolean combatHit = followingCombat && combatContinuity.takeHit(authoritative, now);
        if (combatHit && path.completeCombatHit(authoritative, runEnabled())) { startDecision = "settled-combat-hit"; }
        if (player.getAnimation() != -1 && !ownedWalk) { yellowFacing = preserveFacing = facingSettled = false; }
        tryStart(now, authoritative);
        // Item/Trade flags and actor footprints are provisional reach evidence.
        // Preserve measured eligibility even if an action/withdrawal just retired
        // that capture, or the true stop lies outside its conservative boundary.
        if (measuredIntentTrip && !input.pending()) { path.useConfirmedArrivalTrip(); }
        LocalPoint destination = client.getLocalDestinationLocation();
        // Object flags can name the occupied footprint, while the checked
        // itinerary ends at its accessible perimeter. Pace toward that endpoint,
        // not the unwalkable object tile, once this click owns the publication.
        LocalPoint arrivalDestination = (objectApproach != null && objectApproach.contains(destination) ||
            unprovenObjectDestination(destination)) &&
            !input.pending() && destination.equals(observedApproachDestination) ? approachGoal : destination;
        LocalPoint counterArrival = path.nativeCounterArrivalGoal();
        if (counterArrival != null) { arrivalDestination = counterArrival; }
        // Conservative attacks still use native approach steps. While their
        // observed flag remains onward, share the confirmed run's small reserve
        // instead of publishing idle between pairs; actions/arrival release it.
        // Every native-only world input shares confirmed publication pacing:
        // item/widget-on-actor, player options, ground items and world entities
        // can all walk/run without an immediate usable destination. Specialized
        // NPC/combat forecasts retain their own staging/trailing policies.
        boolean nativeMovement = !interactionApproach || npcApproach == null || npcApproach.combat == null &&
            path.counterNativeGoal() == null && path.npcArrivalGoal() == null ||
            followingCombat && !npcApproach.movingCombat();
        // A wall's perimeter is a prediction boundary, not evidence that the
        // native route has ended. Pace only real confirmed debt while its flag
        // remains onward; this grants no speculative crossing of a closed door.
        LocalPoint continuityDestination = objectApproach != null && objectApproach.wallBoundary() ? destination : arrivalDestination;
        boolean continuingRun = nativeMovement && !nativeIntentArrival &&
            (!actionOrEffect || ownedWalk) && !input.pending() &&
            (MovementPath.sameView(continuityDestination, authoritative) && !continuityDestination.equals(authoritative) ||
                interactionApproach && destination == null && (approachGoal == null ||
                    nativeApproach != null && nativeApproach.pending(now))) &&
            (!nativePoint.equals(authoritative) || interactionApproach && "preview".equals(path.phase()) &&
                destination != null && destination.equals(observedApproachDestination));
        path.advance(now, continuingRun, config.catchUp() ? config.catchUpPercent() : 0,
            config.slowAhead() ? config.slowAheadPercent() : 0,
            arrivalDestination,
            config.slowArrival() ? new ArrivalPacing(config.arrivalFirstTiles(), config.arrivalFinalTiles(),
                config.arrivalFirstSpeed(), config.arrivalFinalSpeed()) : ArrivalPacing.OFF,
            interactionApproach && (npcApproach == null || npcApproach.combat == null));
        boolean travelled = path.turnFraction() > 0;
        if (followingCombat) { combatContinuity.arrived(path.position()); }
        LocalPoint combatFaceTarget = followingCombat && (!path.moving() || npcApproach.adjacentCombat() && combatContinuity.locked)
            ? npcApproach.facingPoint() : null;
        if (!config.faceInteractionsOnArrival()) { interactionFacing = null; }
        LocalPoint arrivalFaceTarget = interactionFacing == null ? null : interactionFacing.target(view, path,
            input.pending(), now, player.getOrientation(), (int) facing.angle());
        if (interactionFacing != null && interactionFacing.retired) { interactionFacing = null; }
        LocalPoint faceTarget = combatFaceTarget != null ? combatFaceTarget : arrivalFaceTarget;
        if (faceTarget != null)
        {
            LocalPoint at = path.position();
            double dx = faceTarget.getX() - at.getX(), dy = faceTarget.getY() - at.getY();
            if (dx != 0 || dy != 0) { facing.movement(dx, dy); }
        }
        else if (travelled && (player.getAnimation() == -1 || followingCombat || ownedWalk))
        {
            facing.movement(path.turnX(), path.turnY());
        }
        else if (followingCombat && !path.moving())
        {
            LocalPoint at = path.position();
            double dx = (npcApproach.min.getX() + npcApproach.max.getX()) / 2.0 - at.getX();
            double dy = (npcApproach.min.getY() + npcApproach.max.getY()) / 2.0 - at.getY();
            if (dx != 0 || dy != 0) { facing.movement(dx, dy); }
        }
        boolean moving = path.moving();
        boolean posedMoving = gait.present(moving, path.presentationRunning(), path.finished() && "confirmed".equals(path.phase()),
            MovementPath.sameView(destination, authoritative) && !destination.equals(authoritative),
            !nativePoint.equals(authoritative), nativeMovement || followingCombat, !actionOrEffect, now);
        if (posedMoving) { preserveFacing = false; facingSettled = false; }
        else if (yellowFacing && !preserveFacing)
        {
            preserveFacing = true;
            nativeTarget = player.getOrientation();
            nativeFacing = player.getCurrentOrientation();
            facingChangedNanos = now;
        }
        if (!combatWalk) { settleFacing(now); }
        boolean nativeArrived = interactionFacingReady(nativePoint, authoritative, path.finished());
        // Select one target and spend one turn budget, including arrival frames.
        // A held arrival keeps easing to its travel heading after the path stops.
        boolean followNative = arrivalFaceTarget == null && !followingCombat && !ownedWalk && (player.getAnimation() != -1 ||
            !travelled && !posedMoving && !preserveFacing && (!interactionApproach || nativeArrived));
        facing.advance(player.getOrientation(), followNative, config.turnSpeed() * delta / 16.667);
        // A click made just before a confirmed corner can start in this same
        // prepared frame, without an intermediate idle publication.
        tryStart(now, authoritative);
        visible = path.position();
        boolean held = preserveFacing && (!facingSettled || !visible.equals(nativePoint)) ||
            interactionApproach && !nativeArrived || faceTarget != null || combatWalk;
        boolean ready = presentation.prepare(visible, (int) facing.angle(), posedMoving,
            moving ? path.presentationRunning() : gait.running(),
            config.originalWhenAligned(), held || !path.finished(), nativePoint.equals(authoritative));
        boolean sampleAccepted = trace.willSample(client.getGameCycle());
        trace.record(client.getGameCycle(), generation, presentation.nativeVisible() ? nativePoint : visible,
            authoritative, nativePoint, path, (int) facing.angle(), player.getPoseAnimation(),
            player.getPoseAnimationFrame(), player.getAnimation(), presentation.nativeVisible(),
            destination, clickCycle, startDecision, interactionApproach, clickAction, clickTarget,
            path.canAnticipateContinuation(), input.pending(), inputAgeMicros(now), spotAnimation(), objectApproach, approachGoal, npcApproach,
            combatContinuity == null ? "none" : combatContinuity.phase(), followingCombat && combatContinuity.engaged(),
            followingCombat && combatContinuity.locked, combatFaceTarget, followingCombat && combatContinuity.sampleHit(now), combatWalk,
            effect && combatWalk && walkEffectsAllowed, sampleAccepted ? player.getAnimationFrame() : -1);
        if (followingCombat && sampleAccepted) { combatContinuity.sampledHit(); }
        return ready && !presentation.nativeVisible();
    }

    private void tryStart(long now, LocalPoint authoritative)
    {
        if (combatContinuity != null && npcApproach.movingCombat()) { return; }
        boolean wasPending = input.pending();
        // A repeated yellow click can select the native destination again
        // when the visible route still represents a different earlier click.
        // Wait for native input processing; red clicks still require a change.
        LocalPoint previousClickDestination = !interactionApproach && (path.moving() || "preview".equals(path.phase())) && client.getGameCycle() > clickCycle
            ? path.clickedDestination() : null;
        LocalPoint destination = input.destination(client.getLocalDestinationLocation(), now, generation, plane, previousClickDestination);
        if (interactionApproach && input.pending() && !input.released(now,generation,plane))
        {
            startDecision = "settling-click";
            return;
        }
        if (interactionReplacementPending && (destination != null || input.pending() && input.released(now,generation,plane)))
        {
            path.replacement(true);
            interactionReplacementPending = false;
        }
        if (destination == null && input.pending() && nativeApproach != null && nativeApproach.hasIntent())
        {
            LocalPoint goal = nativeApproach.intentGoal(path.confirmed(),this::canStep);
            MovementPath next = goal == null ? null : path.anticipateNativeIntent(goal,runEnabled(),now,
                nativeApproach.min,nativeApproach.max,nativeApproach.reserve,nativeApproach.counterIntent());
            input.clear();
            if (next != null)
            {
                path = next; nativeIntentPreview = true;
                startDecision = "started-native-intent";
                return;
            }
            // Unsupported/adjacent/blocked targets retain checked native timing.
            path.cancel();
            startDecision = "native-intent-unavailable";
            return;
        }
        if (npcApproach != null && npcApproach.ordinaryOption() && destination != null &&
            !npcApproach.acceptsDestination(destination)) { destination = null; }
        if (unprovenObjectDestination(destination))
        {
            // Missing/unsupported scene geometry must not turn item/widget Use's
            // raw object flag into a Walk onto it. Distant approaches retain a
            // checked boundary forecast; authority alone can enter the anchor.
            LocalPoint goal = approachGoal(path.confirmed(), destination);
            if (goal == null || goal.equals(path.confirmed()))
            {
                observedApproachDestination = destination;
                path.cancel(); input.clear();
                startDecision = "unproven-object-destination";
                return;
            }
        }
        if (input.pending() && npcApproach != null && npcApproach.atCounterStartBoundary(authoritative) &&
            config.responsiveStarts() && owner.getAnimation() == -1 && !presentation.hasSpotAnimation() &&
            npcApproach.valid(owner.getWorldView()))
        {
            // This bank-service click may already be in range, including the
            // diagonal (2,2) corner. A closer flag alone does not require travel.
            // Drop only the old speculative tail; checked confirmed movement
            // still completes normally and any new server step is accepted.
            path.cancel(); input.clear(); npcPreview = false;
            observedApproachDestination = null; approachGoal = authoritative;
            startDecision = "at-counter-boundary";
            return;
        }
        if (destination != null && interactionApproach && npcApproach == null &&
            (interactionTarget != null || objectApproach != null || nativeApproach != null) && path.moving() &&
            !path.canAnticipateContinuation() && config.responsiveStarts() && owner.getAnimation() == -1 &&
            !presentation.hasSpotAnimation())
        {
            LocalPoint goal = approachGoal(path.confirmed(), destination);
            MovementPath redirected = path.retargetInteraction(goal, runEnabled(), now, adjacentApproach(destination));
            if (redirected != null)
            {
                path = redirected; input.clear(); observedApproachDestination = destination;
                startDecision = "retargeted-world-approach";
                return;
            }
        }
        if (input.pending() && npcApproach != null && path.moving() && !path.canAnticipateContinuation() &&
            config.responsiveStarts() && owner.getAnimation() == -1 && !presentation.hasSpotAnimation() &&
            npcApproach.valid(owner.getWorldView()))
        {
            MovementPath redirected = path.retargetNpcApproach(npcApproach, destination, runEnabled(), now);
            if (redirected != null)
            {
                path = redirected; input.clear(); npcPreview = destination == null;
                observedApproachDestination = destination; approachGoal = redirected.counterNativeGoal() == null ?
                    redirected.clickedDestination() : redirected.npcArrivalGoal();
                startDecision = path.counterNativeGoal() != null ? "retargeted-counter-run" : "retargeted-npc-approach";
                return;
            }
        }
        if (input.pending() && npcApproach != null && npcApproach.counterBank() && runEnabled() &&
            (!path.moving() && path.position().equals(authoritative) || path.canAnticipateContinuation()) && config.responsiveStarts() &&
            owner.getAnimation() == -1 && !presentation.hasSpotAnimation() && npcApproach.valid(owner.getWorldView()))
        {
            boolean continuation = path.moving();
            NpcApproach.CounterRun plan = npcApproach.counterRun(continuation ? path.confirmed() : path.position(), this::canStep);
            if (destination == null || plan != null && destination.equals(plan.nativeGoal))
            {
                // One checked route owns both the early start and later native
                // handoff. Do not reconstruct a different straight-first path
                // to its shortened stopping endpoint or restart it at a late flag.
                MovementPath next = continuation ?
                    path.anticipateCounterContinuation(plan, now, npcApproach.min, npcApproach.max) ? path : null :
                    MovementPath.anticipateCounterRun(path.position(), authoritative, plan, now,
                        MovementPath.configuredSpeed(config.movementSpeed()), path.restartDeadline(now, authoritative),
                        this::canStep, npcApproach.min, npcApproach.max);
                input.clear();
                if (next != null)
                {
                    next.releaseCounterBoundary(destination, now);
                    path = next; npcPreview = destination == null;
                    observedApproachDestination = destination; approachGoal = next.npcArrivalGoal();
                    startDecision = continuation ? "queued-counter-run" : "started-counter-run";
                }
                else { startDecision = "counter-run-unavailable"; }
                return;
            }
        }
        boolean provisionalNpc = false;
        boolean shortNpcPair = false;
        if (destination == null && input.pending() && npcApproach != null &&
            (!path.moving() || path.canAnticipateContinuation()))
        {
            if (npcApproach.valid(owner.getWorldView()))
            {
                LocalPoint from = path.moving() ? path.confirmed() : path.position();
                if (!path.moving() && from.equals(authoritative) && runEnabled())
                {
                    destination = npcApproach.shortRunGoal(from, this::canStep);
                    shortNpcPair = destination != null;
                }
                if (destination == null) { destination = npcApproach.goal(from, this::canStep); }
                provisionalNpc = destination != null;
            }
            else { npcApproach = null; }
        }
        if (destination == null)
        {
            if (wasPending && nativeApproach != null && npcApproach == null && objectApproach == null && interactionTarget == null)
            {
                // Native-only targeting provides no provisional reach. Do not
                // keep following the preceding Walk while waiting for its flag.
                // cancel() preserves all checked confirmed debt and its fraction.
                path.cancel();
            }
            if (input.settling()) { startDecision = "settling-click"; }
            else if (wasPending && !input.pending())
            {
                startDecision = "awaiting-destination".equals(startDecision) ? "destination-not-observed" : "click-expired";
                if (!npcPreview && combatContinuity == null) { npcApproach = null; }
            }
            return;
        }
        if (!config.responsiveStarts() || !previewPoseAllowed() || !previewEffectsAllowed()) { return; }
        // Destination observation belongs to the click, even when its first
        // route cannot be built. A later native refinement must still be seen.
        if (interactionApproach && npcApproach == null && (interactionTarget != null || objectApproach != null || nativeApproach != null))
        {
            observedApproachDestination = destination;
        }
        if (!provisionalNpc)
        {
            npcPreview = false;
            if (npcApproach != null && !npcApproach.valid(owner.getWorldView())) { npcApproach = null; }
        }
        if (input.takeReplacement())
        {
            // Every newly observed yellow destination gets the same geometric
            // replan, regardless of direction or the old visual queue's phase.
            // Red clicks retain their native/object-specific approach handling.
            MovementPath redirected = interactionApproach ? null : path.retargetWalk(destination, runEnabled(), now);
            if (redirected != null)
            {
                path = redirected;
                input.clear();
                preserveFacing = facingSettled = false;
                startDecision = "retargeted-walk";
                return;
            }
            path.replacement(true);
        }
        if (path.moving())
        {
            if (!path.canAnticipateContinuation()) { startDecision = "waiting-for-confirmed-tail"; return; }
            input.clear();
            LocalPoint goal = approachGoal(path.confirmed(), destination);
            boolean footprint = objectApproach != null && objectApproach.contains(destination);
            if (footprint && (goal == null || goal.equals(path.confirmed())))
            {
                startDecision = "at-object-boundary";
                return;
            }
            boolean queued = path.anticipateContinuation(goal, runEnabled(), now, adjacentApproach(destination) && !provisionalNpc,
                npcApproach == null ? null : npcApproach.min, npcApproach == null ? null : npcApproach.max,
                npcApproach == null ? 2 : npcApproach.reserveTiles);
            if (queued && interactionApproach)
            {
                npcPreview = provisionalNpc;
                observedApproachDestination = provisionalNpc ? null : destination;
            }
            startDecision = queued ? provisionalNpc ? "queued-npc-approach" : "queued-continuation" : "continuation-unavailable";
            return;
        }
        LocalPoint origin = path.position();
        if (!MovementPath.tileCenter(origin) || MovementPath.distance(origin, authoritative) > config.snapDistance() * 128)
        {
            input.clear(); startDecision = "start-out-of-range"; return;
        }
        input.clear();
        LocalPoint goal = approachGoal(origin, destination);
        boolean footprint = objectApproach != null && objectApproach.contains(destination);
        if (footprint && (goal == null || origin.equals(goal)))
        {
            startDecision = "at-object-boundary";
            return;
        }
        if (origin.equals(destination)) { startDecision = "already-at-destination"; return; }
        if (!shortNpcPair && npcApproach != null && origin.equals(authoritative) && runEnabled())
        {
            shortNpcPair = goal.equals(npcApproach.shortRunGoal(origin, this::canStep));
        }
        MovementPath next = shortNpcPair ? MovementPath.anticipateNpcRunPair(origin, authoritative, goal, true, now,
            MovementPath.configuredSpeed(config.movementSpeed()), path.restartDeadline(now, authoritative), this::canStep,
            npcApproach.min, npcApproach.max) : null;
        if (next == null)
        {
            next = MovementPath.anticipate(origin, authoritative, goal, runEnabled(), now,
                MovementPath.configuredSpeed(config.movementSpeed()), path.restartDeadline(now, authoritative), this::canStep,
                adjacentApproach(destination) && !provisionalNpc, npcApproach == null ? null : npcApproach.min,
                npcApproach == null ? null : npcApproach.max, npcApproach == null ? 2 : npcApproach.reserveTiles);
            shortNpcPair = false;
        }
        if (next != null)
        {
            if (!interactionApproach) { next.pendingWalkConfirmation(); }
            startDecision = shortNpcPair ? "started-npc-run-pair" : provisionalNpc ? "started-npc-approach" : "started";
            path = next;
            if (interactionApproach)
            {
                npcPreview = provisionalNpc;
                observedApproachDestination = provisionalNpc ? null : destination;
            }
            if (config.explainStarts())
            {
                client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
                    "Responsive Movement: " + (next.running() ? "running" : "walking") +
                        (interactionApproach ? " toward interaction destination" : " from visible idle"), null);
            }
        }
        else { startDecision = "route-or-deadline-unavailable"; }
    }

    private LocalPoint approachGoal(LocalPoint from, LocalPoint published)
    {
        LocalPoint goal = unprovenObjectDestination(published) ? nativeApproach.objectGoal(from, this::canStep) :
            objectApproach == null ? published : objectApproach.goal(from, published, this::canStep);
        approachGoal = interactionApproach ? goal : null;
        return goal;
    }

    private boolean unprovenObjectDestination(LocalPoint published)
    {
        return interactionApproach && objectApproach == null && nativeApproach != null && nativeApproach.objectAnchor(published);
    }

    private boolean adjacentApproach(LocalPoint published)
    {
        // Ordinary ground-item actions need the observed native endpoint, not
        // an adjacent object-style staging tile. Keep identity separate from
        // destination evidence; blocked exact routes retain native recovery.
        if (!interactionApproach) { return false; }
        // A refined native endpoint outside a captured object/NPC is already an
        // approach tile. Searching for a neighbour of THAT tile stops twice short.
        if (objectApproach != null || npcApproach != null) { return false; }
        // Targeted objects either supply a precise outside flag or the checked
        // conservative anchor boundary above. Neither needs another adjacent goal.
        if (nativeApproach != null && nativeApproach.objectTarget()) { return false; }
        if (interactionTarget != null)
        {
            return !interactionTarget.groundItem() && interactionTarget.atAnchor(published);
        }
        return true;
    }

    private void observeRefinedApproach(long now)
    {
        if (!interactionApproach) { return; }
        LocalPoint published = client.getLocalDestinationLocation();
        // A moving counter click can receive the preceding Walk's flag before
        // its own response. Retain only the already bounded counter itinerary;
        // matching native publication or real checked progress remains decisive.
        if (path.pendingCounterRetarget(now) && published != null && !published.equals(path.counterNativeGoal())) { return; }
        if (nativeIntentPreview)
        {
            if (nativeApproach != null && nativeApproach.newlyPublished(published))
            {
                nativeIntentPreview = false;
                if (nativeApproach.counterIntent() && nativeApproach.sameFootprint() && path.matchesNativeCounterDestination(published,now))
                {
                    observedApproachDestination = published; approachGoal = path.nativeCounterArrivalGoal();
                    startDecision = "matched-native-intent-destination";
                    return;
                }
                path.nativeIntentDestination(nativeApproach.counterIntent() && nativeApproach.atIntentBoundary(published));
                // Preserve the original prediction deadlines during handoff.
                MovementPath refined = path.retargetApproachDestination(published,runEnabled(),now,adjacentApproach(published));
                observedApproachDestination = published; approachGoal = published;
                if (refined != null) { path = refined; startDecision = "matched-native-intent-destination"; }
                else { path.cancel(); startDecision = "native-approach-recovery"; }
            }
            else if (nativeApproach == null || !nativeApproach.sameFootprint())
            {
                path.cancel(); nativeIntentPreview = false;
                startDecision = "native-intent-target-changed";
            }
            return;
        }
        if (npcApproach != null && npcApproach.ordinaryOption() && published != null &&
            !npcApproach.acceptsDestination(published)) { return; }
        if (npcPreview && npcApproach != null)
        {
            if (npcApproach.newlyPublished(published))
            {
                // Native evidence supersedes the provisional NPC prefix, even
                // after the ordinary 100 ms destination-observation window.
                npcPreview = false;
            }
            else
            {
                if (!npcApproach.valid(owner.getWorldView()))
                {
                    path.cancel(); input.clear();
                    npcApproach = null; npcPreview = false;
                    startDecision = "npc-approach-invalidated";
                }
                else if (!"preview".equals(path.phase()))
                {
                    if (combatContinuity == null) { npcApproach = null; }
                    npcPreview = false;
                }
                return;
            }
        }
        else if (!MovementPath.sameView(published, observedApproachDestination) ||
            published.equals(observedApproachDestination)) { return; }
        observedApproachDestination = published;
        approachGoal = published;
        if (unprovenObjectDestination(published))
        {
            LocalPoint goal = approachGoal(path.confirmed(), published);
            if (goal == null || goal.equals(path.confirmed()))
            {
                path.cancel(); input.clear();
                startDecision = "unproven-object-destination";
                return;
            }
            published = goal;
        }
        if (objectApproach != null && objectApproach.contains(published))
        {
            LocalPoint goal = approachGoal(path.confirmed(), published);
            if (goal == null || goal.equals(path.confirmed()))
            {
                // A late anchor/hinge flag must not undo a crossing already in
                // flight or resurrect travel into a captured object footprint.
                if (goal != null) { path.cancel(); }
                path.replacement(false); input.clear();
                startDecision = "at-object-boundary";
                return;
            }
            published = goal;
        }
        if (path.counterNativeGoal() != null)
        {
            if (published.equals(path.counterNativeGoal()))
            {
                path.releaseCounterBoundary(published, now);
                approachGoal = path.npcArrivalGoal();
                startDecision = "matched-counter-destination";
            }
            else
            {
                path.retireCounterRun(); input.clear(); observedApproachDestination = null;
                npcApproach = null; npcPreview = false;
                startDecision = "counter-destination-changed";
            }
            return;
        }
        if (!"preview".equals(path.phase()))
        {
            if (npcApproach == null && (interactionTarget != null || objectApproach != null || nativeApproach != null))
            {
                LocalPoint goal = approachGoal(path.confirmed(), published);
                MovementPath refined = path.refineConfirmedApproach(goal, runEnabled(), now, adjacentApproach(published));
                if (refined != null) { path = refined; startDecision = "resumed-native-approach"; }
            }
            return;
        }
        if (published.equals(path.clickedDestination())) { return; }
        if (npcApproach != null && npcApproach.combat != null && path.holdCombatForecast(published, now))
        {
            startDecision = "held-combat-boundary";
            return;
        }
        boolean checkedApproach = npcApproach != null && npcApproach.combat == null ||
            npcApproach == null && (interactionTarget != null || objectApproach != null || nativeApproach != null);
        MovementPath redirected = checkedApproach
            ? path.retargetApproachDestination(published, runEnabled(), now,
                npcApproach == null && adjacentApproach(published))
            : path.retargetNativeApproach(published, runEnabled(), now);
        if (redirected != null)
        {
            path = redirected;
            startDecision = "redirected-native-approach";
            return;
        }
        // The native destination supersedes the old forecast, but there is no
        // legal connector. Reconcile rather than carrying the player past it.
        path.cancel();
        observedApproachDestination = null;
        startDecision = "native-approach-recovery";
    }

    void pollTrace()
    {
        if (!config.recordTrace()) { trace.enabled(false); }
        trace.poll();
    }

    long bookmark(String source, long observedNanos)
    {
        if (!config.recordTrace()) { trace.enabled(false); return -1; }
        return trace.bookmark(client, config, generation, source, observedNanos,
            presentation.nativeVisible() && client.getLocalPlayer() != null ? client.getLocalPlayer().getLocalLocation() : visible, path);
    }

    private boolean observeCombat(Player player, WorldView view, LocalPoint authority, boolean effect, long now)
    {
        if (combatContinuity == null) { return false; }
        if (!config.responsiveStarts() || !combatContinuity.observe(client, player, view, authority, effect, now))
        {
            path.cancel(); clearCombat();
            npcApproach = null; npcPreview = false; input.clear();
            startDecision = "combat-follow-ended";
            return false;
        }
        npcApproach = combatContinuity.target;
        if (!npcApproach.movingCombat() && combatContinuity.moved) { path.cancel(); input.clear(); }
        combatContinuity.arrived(path.position());
        if (npcApproach.movingCombat()) { npcPreview = false; }
        if (!combatContinuity.armed && npcApproach.movingCombat())
        {
            path.armCombat(combatContinuity.clicked);
            combatContinuity.armed = true;
        }
        return true;
    }

    private void offerCombat(long now)
    {
        if (input.pending())
        {
            if (!input.released(now,generation,plane)) { return; }
            if (npcApproach.movingCombat()) { input.clear(); }
        }
        if (!npcApproach.movingCombat()) { combatContinuity.attempted(); return; }
        boolean moved = combatContinuity.moved;
        MovementPath next = path.followCombat(npcApproach, runEnabled(), now,
            npcApproach.adjacentCombat() && combatContinuity.locked);
        // Keep an already observed target step pending while its old preview
        // returns to authority. Once settled, the same remaining reserve can
        // handle that step; blocked/expired attempts are still consumed.
        if (next != null || !path.awaitingCombatRecovery(now)) { combatContinuity.attempted(); }
        if (next != null)
        {
            path = next; approachGoal = path.clickedDestination();
            startDecision = path.moving() ? moved ? "followed-combat-target" : "started-combat-approach" : "held-combat-target";
        }
        else { startDecision = "combat-follow-unavailable"; }
    }

    private void clearPresentationOwnership()
    {
        nativeApproach = null;
        nativeIntentPreview = interactionReplacementPending = false;
        interactionFacing = null;
        walkInput = false;
        followPresentation = null;
        clearCombat();
        clearCombatWalk();
        clearCombatInput();
    }

    private void clearCombat()
    {
        combatContinuity = null;
        if (path != null) { path.endCombat(); }
    }

    private boolean previewPoseAllowed()
    {
        return owner.getAnimation() == -1 || (combatWalk || walkInput) && !interactionApproach && !nativeLocationAction(owner.getAnimation());
    }

    private void clearCombatWalk()
    {
        combatWalk = combatDisengaged = false;
        releasedCombatTarget = null;
        combatEffectProfile = null; combatEffects = null; combatWalkClickNanos = 0;
    }

    private void clearCombatInput() { combatInput = false; combatInputNanos = 0; }

    static boolean combatInput(MenuAction action, String option)
    {
        if (option == null || action == null) { return false; }
        String name = Text.removeTags(option).trim();
        switch (action)
        {
            case NPC_FIRST_OPTION: case NPC_SECOND_OPTION: case NPC_THIRD_OPTION:
            case NPC_FOURTH_OPTION: case NPC_FIFTH_OPTION:
            case PLAYER_FIRST_OPTION: case PLAYER_SECOND_OPTION: case PLAYER_THIRD_OPTION: case PLAYER_FOURTH_OPTION:
            case PLAYER_FIFTH_OPTION: case PLAYER_SIXTH_OPTION: case PLAYER_SEVENTH_OPTION: case PLAYER_EIGHTH_OPTION:
                return "Attack".equalsIgnoreCase(name);
            case WIDGET_TARGET_ON_NPC: case WIDGET_TARGET_ON_PLAYER:
                // Selected combat spells retain native destination/range handling;
                // this flag is only bounded click/escape evidence, not a forecast.
                return "Cast".equalsIgnoreCase(name);
            default: return false;
        }
    }

    private void captureCombatEffects(long now)
    {
        // A native attack can publish its graphic between the click and the next
        // input/render preparation. Capture once inside the observation window;
        // an unrelated replacement graphic cannot inherit the existing exception.
        if (combatEffects == null && combatWalk && combatEffectProfile != null && combatEffectProfile.valid(client) &&
            !nativeLocationAction(owner.getAnimation()) && now - combatWalkClickNanos <= 100_000_000L)
        {
            combatEffects = CombatEffectCarry.capture(owner, client.getGameCycle(), now);
        }
    }

    private boolean combatEffectsAllowed(long now)
    {
        return combatWalk && !interactionApproach && combatEffectProfile != null && combatEffectProfile.valid(client) &&
            combatEffects != null && combatEffects.allows(owner, client.getGameCycle(), now) &&
            !nativeLocationAction(owner.getAnimation());
    }

    private boolean previewEffectsAllowed()
    {
        return !presentation.hasSpotAnimation() || combatEffectsAllowed(clock.getAsLong());
    }

    private void observeCombatWalk(Player player)
    {
        if (!combatWalk) { return; }
        Actor interacting = player.getInteracting();
        if (interacting == null) { combatDisengaged = true; }
        // A stale target/remaining shot after Walk is not renewed engagement.
        // A new native engagement/pose with auto-retaliation enabled and the
        // Walk flag withdrawn can retire its forecast even during catch-up.
        // Preserve real confirmed debt; do not continue an obsolete Walk tail.
        if ((combatDisengaged || interacting != releasedCombatTarget) && interacting instanceof NPC &&
            client.getVarpValue(VarPlayerID.OPTION_NODEF) == 0 && !input.pending() &&
            client.getLocalDestinationLocation() == null &&
            player.getAnimation() != -1 && !nativeLocationAction(player.getAnimation()))
        {
            path.cancel();
            clearCombatWalk();
            walkInput = false;
            yellowFacing = preserveFacing = facingSettled = false;
        }
    }

    void combatHit(Actor actor, Hitsplat hitsplat)
    {
        if (combatContinuity != null) { combatContinuity.hit(actor, hitsplat, clock.getAsLong()); }
    }

    private void traceNative(Player player, LocalPoint authoritative, LocalPoint nativePoint)
    {
        trace.record(client.getGameCycle(), generation, nativePoint, authoritative, nativePoint,
            null, player.getCurrentOrientation(), player.getPoseAnimation(), player.getPoseAnimationFrame(),
            player.getAnimation(), true, client.getLocalDestinationLocation(), clickCycle, startDecision, interactionApproach, clickAction, clickTarget,
            false, input.pending(), inputAgeMicros(clock.getAsLong()), spotAnimation(), objectApproach, approachGoal, npcApproach);
    }

    private long inputAgeMicros(long now)
    {
        return input.pending() ? Math.max(0, (now - input.clickedNanos()) / 1000) : -1;
    }

    /** Only scan the spot table when the sample would actually be recorded. */
    private boolean spotAnimation()
    {
        return trace.recording() && presentation.hasSpotAnimation();
    }

    static boolean interactionFacingReady(LocalPoint nativePoint, LocalPoint authoritative, boolean finished)
    {
        return finished && nativePoint != null && nativePoint.equals(authoritative);
    }

    private void settleFacing(long now)
    {
        if (!preserveFacing) { return; }
        int target = owner.getOrientation(), current = owner.getCurrentOrientation();
        if (!facingSettled)
        {
            if (target != nativeTarget || current != nativeFacing)
            {
                nativeTarget = target; nativeFacing = current; facingChangedNanos = now;
            }
            else if (now - facingChangedNanos >= 600_000_000L) { facingSettled = true; }
        }
        else if (target != nativeTarget)
        {
            preserveFacing = yellowFacing = false;
        }
    }

    private boolean runEnabled() { return client.getVarpValue(VarPlayerID.OPTION_RUN) == 1 && client.getEnergy() > 0; }

    private boolean canStep(LocalPoint from, LocalPoint to)
    {
        WorldView view = owner == null ? null : owner.getWorldView();
        if (view == null || !MovementPath.sameView(from, to) || from.getWorldView() != view.getId()) { return false; }
        CollisionData[] maps = view.getCollisionMaps();
        int level = view.getPlane();
        if (maps == null || level < 0 || level >= maps.length || maps[level] == null ||
            from.getSceneX() < 1 || from.getSceneY() < 1 || to.getSceneX() < 1 || to.getSceneY() < 1 ||
            from.getSceneX() >= view.getSizeX() - 1 || to.getSceneX() >= view.getSizeX() - 1 ||
            from.getSceneY() >= view.getSizeY() - 1 || to.getSceneY() >= view.getSizeY() - 1) { return false; }
        WorldPoint world = WorldPoint.fromLocal(client, from);
        return world != null && world.toWorldArea().canTravelInDirection(view,
            Integer.signum(to.getX() - from.getX()), Integer.signum(to.getY() - from.getY()));
    }

    static boolean nativeLocationAction(int animation)
    {
        return animation == AnimationID.HUMAN_CASTTELEPORT || animation == AnimationID.AHOY_ECTO_TELEPORT ||
            animation == AnimationID.HUMAN_TELEPORT_OTHER_IMPACT || animation == AnimationID.TELEPORT_NARDAH_HUMAN ||
            animation == AnimationID.HUMAN_COWBOSS_TELEPORT || animation == AnimationID.POH_SMASH_MAGIC_TABLET ||
            animation == AnimationID.POH_ABSORB_TABLET_TELEPORT || animation == AnimationID.TELEPORT_CABBAGE_HUMAN ||
            animation == AnimationID.NTK_HUMAN_TELE || animation == AnimationID.HUMAN_ROPESWING ||
            animation == AnimationID.HUMAN_ROPESWING_LONG || animation == AnimationID.HUMAN_CRAWLING ||
            animation == AnimationID.HUMAN_LONGCRAWL || animation == AnimationID.HUMAN_CLIMBING ||
            animation == AnimationID.HUMAN_CLIMBING_DOWN;
    }

    private static boolean isHouse(WorldView view)
    {
        if (!view.isInstance() || view.getMapRegions() == null) { return false; }
        boolean west = false, east = false;
        for (int region : view.getMapRegions()) { west |= region == POH_WEST; east |= region == POH_EAST; }
        return west && east;
    }

    void presented()
    {
        if (firstPresentationPending && path != null) { path.presented(clock.getAsLong()); }
        firstPresentationPending = false;
    }

    void close()
    {
        clearPresentationOwnership();
        trace.close();
        gait.reset();
        presentation.close(); input.reset(); path = null; owner = null; lastAuthority = null;
        visible = null; scenePending = true; worldView = Integer.MIN_VALUE; plane = -1;
        sceneContinuation = instanced = false;
        lastFrameNanos = 0; yellowFacing = preserveFacing = facingSettled = false;
        clickCycle = -1; startDecision = "none";
        clickAction = "none"; clickTarget = -1;
        interactionApproach = false;
        observedApproachDestination = null;
        objectApproach = null; approachGoal = null;
        interactionTarget = null;
        npcApproach = null; npcPreview = false;
    }

    LocalPoint position() { return visible; }
    boolean nativeVisible() { return presentation.nativeVisible(); }
    boolean nativeCamera() { return pohArrival || nativeVisible(); }
    int orientation() { return (int) facing.angle(); }
}
