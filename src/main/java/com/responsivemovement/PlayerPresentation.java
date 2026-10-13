package com.responsivemovement;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.IterableHashTable;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.coords.LocalPoint;

/** Native geometry and pose clocks, drawn at the MovementPath presentation. */
final class PlayerPresentation
{
    private final Client client;
    private Player owner;
    private NativeModelObject body;
    private final PoseSet originals = new PoseSet();
    private final Map<Long, NativeModelObject> spots = new HashMap<>();
    private final Set<Long> activeSpots = new HashSet<>();
    private boolean nativeVisible = true;
    private boolean wasMoving;
    private int publishedPose = -1;

    PlayerPresentation(Client client) { this.client = client; }

    void owner(Player player)
    {
        if (owner != player)
        {
            close();
            owner = player;
            originals.read(player);
        }
    }

    boolean prepare(LocalPoint location, int orientation, boolean moving, boolean running,
        boolean allowNative, boolean holdFacing, boolean nativeStationary)
    {
        if (owner == null || location == null) { return false; }
        originals.read(owner);
        if (body == null)
        {
            body = new NativeModelObject(client);
            body.setRenderMode(Renderable.RENDERMODE_SORTED);
        }
        body.setLocation(location, owner.getWorldView().getPlane());
        body.setOrientation(orientation);
        int z = Perspective.getFootprintTileHeight(client, location, owner.getWorldView().getPlane(), owner.getFootprintSize());
        body.setZ(z - owner.getAnimationHeightOffset());

        boolean action = owner.getAnimation() != -1;
        boolean effect = hasSpotAnimation();
        int requested = moving ? running ? originals.run : originals.walk : originals.idle;
        boolean useNative = canUseNative(allowNative, moving, action, effect, holdFacing,
            location, owner.getLocalLocation(), orientation, owner.getCurrentOrientation());
        boolean nativeIdle = useNative &&
            owner.getPoseAnimation() == originals.idle && owner.getPoseAnimationFrame() >= 0;
        if (nativeIdle)
        {
            originals.restore(owner);
            publishedPose = -1;
        }
        else if (requested != -1)
        {
            // Keep the native idle selector distinct during ordinary locomotion:
            // other plugins use pose == idle to recognize a stationary actor.
            // During an anticipatory start or catch-up after native arrival,
            // the native actor is stationary while the displayed body moves.
            // Match its idle selector to the requested gait there so native
            // animation ticks cannot restart the pose clock every 20 ms.
            originals.override(owner, requested, moving && nativeStationary);
            if (owner.getPoseAnimation() != requested || (moving && !wasMoving) || (!moving && wasMoving))
            {
                Animation animation = client.loadAnimation(requested);
                if (animation != null && animation.getNumFrames() > 0)
                {
                    int frame = poseFrame(owner.getPoseAnimationFrame(), animation.getNumFrames(),
                        publishedPose != requested && moving == wasMoving);
                    owner.setPoseAnimationFrame(frame);
                    owner.setPoseAnimation(requested);
                }
            }
            publishedPose = requested;
        }
        wasMoving = moving;

        // 1.13.0 removed posed-model merging. Probe availability here, then
        // build again only when the native temporary-object pass requests it.
        // Never retain a shared scratch model or animate an already posed mesh.
        boolean validPose = action || owner.getPoseAnimation() == -1 || owner.getPoseAnimationFrame() >= 0;
        // A stationary native actor needs idle == displayed gait to keep its
        // native pose clock advancing. During an action the native builder must
        // instead see the real idle selector, so it can blend that gait with the
        // untouched primary action. Scope only this selector to each model build.
        int builderIdle = action && moving && nativeStationary ? originals.idle : -1;
        boolean ready = body.prepare(validPose ? owner : null, !useNative, builderIdle);
        nativeVisible = !ready || useNative;
        if (ready && !body.isActive()) { body.setActive(true); }
        if (nativeVisible)
        {
            // Keep the object registered; only its drawable decision changes.
            // Restoring selectors leaves the native idle phase intact.
            originals.restore(owner);
        }
        mirrorSpots(!nativeVisible);
        return ready;
    }

    static int poseFrame(int current, int frameCount, boolean compatibleTransition)
    {
        return compatibleTransition && current >= 0 && current < frameCount ? current : 0;
    }

    static boolean canUseNative(boolean enabled, boolean moving, boolean action, boolean effect,
        boolean facingHeld, LocalPoint visible, LocalPoint nativePoint, int visibleAngle, int nativeAngle)
    {
        return enabled && !moving && !action && !effect && !facingHeld && visible != null &&
            visible.equals(nativePoint) && Math.abs(MotionMath.difference(visibleAngle, nativeAngle)) <= 10;
    }

    boolean hasSpotAnimation()
    {
        IterableHashTable<ActorSpotAnim> effects = owner == null ? null : owner.getSpotAnims();
        if (effects != null)
        {
            for (ActorSpotAnim effect : effects)
            {
                if (effect != null && effect.getId() != -1 && effect.getFrame() >= 0 && effect.getStartCycle() <= client.getGameCycle())
                {
                    return true;
                }
            }
        }
        return false;
    }

    private void mirrorSpots(boolean custom)
    {
        activeSpots.clear();
        IterableHashTable<ActorSpotAnim> effects = owner == null ? null : owner.getSpotAnims();
        if (custom && effects != null)
        {
            for (ActorSpotAnim effect : effects)
            {
                if (effect == null || effect.getId() == -1 || effect.getFrame() < 0 || effect.getStartCycle() > client.getGameCycle()) { continue; }
                NativeModelObject object = spots.computeIfAbsent(effect.getHash(), ignored -> new NativeModelObject(client));
                if (!object.prepare(effect, true)) { continue; }
                object.setRenderMode(effect.getRenderMode());
                object.setLocation(body.getLocation(), body.getLevel());
                object.setOrientation(body.getOrientation());
                object.setZ(body.getZ());
                if (!object.isActive()) { object.setActive(true); }
                activeSpots.add(effect.getHash());
            }
        }
        spots.entrySet().removeIf(entry ->
        {
            if (activeSpots.contains(entry.getKey())) { return false; }
            // Expired effects must not accumulate registered, invisible objects.
            entry.getValue().setActive(false);
            return true;
        });
    }

    void nativeFallback()
    {
        nativeVisible = true;
        if (body != null) { body.hide(); }
        if (owner != null) { originals.restore(owner); }
        mirrorSpots(false);
    }

    void invalidateScene()
    {
        nativeFallback();
        if (body != null) { body.setActive(false); body = null; }
    }

    void close()
    {
        invalidateScene();
        owner = null;
        originals.clear();
        publishedPose = -1;
        wasMoving = false;
    }

    boolean nativeVisible() { return nativeVisible; }

    private static final class PoseSet
    {
        int idle = -1, walk = -1, run = -1, left = -1, right = -1, walkLeft = -1, walkRight = -1, back = -1;
        int override = -1;

        private int observed(int current, int saved) { return current != -1 && current != override ? current : saved; }
        void read(Player player)
        {
            idle = observed(player.getIdlePoseAnimation(), idle);
            walk = observed(player.getWalkAnimation(), walk);
            run = observed(player.getRunAnimation(), run);
            left = observed(player.getIdleRotateLeft(), left);
            right = observed(player.getIdleRotateRight(), right);
            walkLeft = observed(player.getWalkRotateLeft(), walkLeft);
            walkRight = observed(player.getWalkRotateRight(), walkRight);
            back = observed(player.getWalkRotate180(), back);
        }

        void override(Player p, int pose, boolean nativeIdleDuringMovement)
        {
            int idleSelector = nativeIdleDuringMovement ? pose : idle;
            if (p.getIdlePoseAnimation() != idleSelector) { p.setIdlePoseAnimation(idleSelector); }
            if (p.getWalkAnimation() != pose) { p.setWalkAnimation(pose); }
            if (p.getRunAnimation() != pose) { p.setRunAnimation(pose); }
            if (p.getIdleRotateLeft() != pose) { p.setIdleRotateLeft(pose); }
            if (p.getIdleRotateRight() != pose) { p.setIdleRotateRight(pose); }
            if (p.getWalkRotateLeft() != pose) { p.setWalkRotateLeft(pose); }
            if (p.getWalkRotateRight() != pose) { p.setWalkRotateRight(pose); }
            if (p.getWalkRotate180() != pose) { p.setWalkRotate180(pose); }
            override = pose;
        }

        void restore(Player p)
        {
            if (override == -1) { return; }
            read(p);
            p.setIdlePoseAnimation(idle); p.setWalkAnimation(walk); p.setRunAnimation(run);
            p.setIdleRotateLeft(left); p.setIdleRotateRight(right);
            p.setWalkRotateLeft(walkLeft); p.setWalkRotateRight(walkRight); p.setWalkRotate180(back);
            override = -1;
        }

        void clear() { idle = walk = run = left = right = walkLeft = walkRight = back = override = -1; }
    }
}
