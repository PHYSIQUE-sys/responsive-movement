package com.responsivemovement;

import net.runelite.api.coords.LocalPoint;

/** Observes normal client input; never creates or consumes a game action. */
final class MovementInput
{
    private boolean pending;
    private LocalPoint beforeDestination;
    private LocalPoint destination;
    private long clicked;
    private int scene;
    private int plane;
    private boolean replacementObserved;
    private long settleNanos;
    private boolean ready;
    private boolean observedDestination;
    private boolean tickReleased;
    private boolean intentObserved;

    void click(LocalPoint previous, long now, int generation, int level)
    {
        click(previous, now, generation, level, 0);
    }

    void click(LocalPoint previous, long now, int generation, int level, int smoothingMillis)
    {
        // A same-target repeat during the settling window keeps the destination
        // already observed for that pending walk, including starts from idle.
        boolean repeated = smoothingMillis > 0 && settleNanos > 0 && pending && !ready &&
            generation == scene && level == plane && now - clicked <= 900_000_000L &&
            destination != null && destination.equals(previous);
        pending = true; beforeDestination = previous;
        if (!repeated) { destination = null; }
        clicked = now; scene = generation; plane = level;
        replacementObserved = false;
        ready = false;
        observedDestination = false;
        tickReleased = false;
        intentObserved = false;
        settleNanos = Math.max(0, Math.min(300, smoothingMillis)) * 1_000_000L;
    }

    LocalPoint destination(LocalPoint current, long now, int generation, int level)
    {
        return destination(current, now, generation, level, null);
    }

    // A repeat walk can override a different preview after the caller has
    // allowed native input processing to run. Red clicks use the overload above.
    LocalPoint destination(LocalPoint current, long now, int generation, int level, LocalPoint previousClickDestination)
    {
        if (!validObservation(now, generation, level)) { return null; }
        if (!observedDestination && current != null && (!current.equals(beforeDestination) ||
            previousClickDestination != null && !current.equals(previousClickDestination)))
        {
            destination = current;
            observedDestination = true;
        }
        // Latch this click's first new publication. A delayed server tick can
        // republish the preceding click's destination during the settling window.
        // Only another click rearms observation; a carried same-target pending
        // walk above can still be replaced by that new click's own publication.
        ready = destination != null && (tickReleased || now - clicked >= settleNanos);
        if (ready) { observedDestination = true; }
        return ready ? destination : null;
    }

    /** Retire the previous forecast once, after this click's destination is identified. */
    boolean takeReplacement()
    {
        if (!pending || !ready || replacementObserved) { return false; }
        replacementObserved = true;
        return true;
    }

    /** The first game tick after a smoothed click ends only its wait, never supplies a destination. */
    void gameTick(long now, int generation, int level)
    {
        if (!validObservation(now, generation, level)) { return; }
        if (settleNanos > 0 && now >= clicked) { tickReleased = true; }
    }

    /** Target geometry is separate from the first latched native destination. */
    void intent() { if (pending) { intentObserved = true; } }
    boolean released(long now, int generation, int level)
    {
        return validObservation(now, generation, level) && (tickReleased || now - clicked >= settleNanos);
    }

    private boolean validObservation(long now, int generation, int level)
    {
        if (!pending) { return false; }
        if (generation != scene || level != plane || now - clicked > (destination == null && !intentObserved ? 100_000_000L : 900_000_000L))
        {
            clear(); return false;
        }
        return true;
    }

    void clear() { pending = ready = observedDestination = tickReleased = intentObserved = false; beforeDestination = destination = null; replacementObserved = false; }
    boolean pending() { return pending; }
    boolean settling() { return pending && destination != null && !ready; }
    /** Observation start time of the current click. */
    long clickedNanos() { return clicked; }
    void reset() { clear(); }
}
