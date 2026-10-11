package com.responsivemovement;

import net.runelite.api.Client;
import net.runelite.api.Actor;
import net.runelite.api.Hitsplat;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

/** Click/engagement evidence only; MovementPath retains every coordinate, route and prediction budget. */
final class CombatContinuity
{
    private static final long PENDING_NANOS = 1_800_000_000L;
    NpcApproach target;
    final long clicked;
    boolean armed;
    private LocalPoint lastAuthority;
    private boolean engaged;
    private boolean attempted;
    private boolean pendingMove;
    boolean moved;
    boolean progressed;
    boolean initial;
    boolean allowed;
    boolean locked;
    private boolean hitPending;
    private long hitNanos;
    private boolean hitSamplePending;
    private long hitAcceptedNanos;

    CombatContinuity(NpcApproach target, LocalPoint authority, long now)
    {
        this.target = target; lastAuthority = authority; clicked = now;
    }

    void retainLock(CombatContinuity previous)
    {
        if (previous != null && target.sameCapturedTarget(previous.target))
        {
            locked = previous.locked;
            hitPending = previous.hitPending; hitNanos = previous.hitNanos;
            hitSamplePending = previous.hitSamplePending; hitAcceptedNanos = previous.hitAcceptedNanos;
        }
    }

    void arrived(LocalPoint visible) { if (target.combatBoundary(visible)) { locked = true; } }

    void hit(Actor actor, Hitsplat hitsplat, long now)
    {
        if (target.sameTarget(actor) && hitsplat != null && hitsplat.isMine())
        {
            hitPending = true; hitNanos = now;
        }
    }

    boolean takeHit(LocalPoint authority, long now)
    {
        if (!hitPending) { return false; }
        if (now - hitNanos > 900_000_000L) { hitPending = false; return false; }
        if (!engaged || !target.adjacentCombat() || !target.adjacent(authority)) { return false; }
        hitPending = false; locked = true;
        hitSamplePending = true; hitAcceptedNanos = now;
        return true;
    }

    boolean sampleHit(long now) { return hitSamplePending && now - hitAcceptedNanos <= 900_000_000L; }
    void sampledHit() { hitSamplePending = false; }

    boolean observe(Client client, Player player, WorldView view, LocalPoint authority, boolean effect, long now)
    {
        // A skipped preparation cannot let late native engagement revive a
        // pending click. Established engagement has its own invalidation rules.
        if (!engaged && now - clicked >= PENDING_NANOS) { return false; }
        if (!target.combat.valid(client)) { return false; }
        NpcApproach current = target.refreshCombat(view);
        if (current == null) { return false; }
        boolean interacting = current.sameTarget(player.getInteracting());
        if (engaged && !interacting) { return false; }
        if (interacting) { engaged = true; }
        boolean changed = !current.min.equals(target.min) || !current.max.equals(target.max);
        pendingMove |= changed;
        progressed = !authority.equals(lastAuthority);
        target = current; lastAuthority = authority;
        allowed = !effect && (player.getAnimation() == -1 || target.adjacentCombat() && interacting);
        moved = changed || allowed && pendingMove;
        initial = !attempted && allowed;
        return true;
    }

    void attempted() { attempted = true; pendingMove = false; }
    boolean engaged() { return engaged; }
    String phase() { return engaged ? "following" : "approaching"; }
}
