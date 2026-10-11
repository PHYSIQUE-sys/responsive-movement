package com.responsivemovement;

import java.util.Arrays;
import net.runelite.api.Hitsplat;

/** Four stable display slots; ambiguous overflow hands the whole UI back to native drawing. */
final class OverheadHitsplats
{
    private final Hit[] slots = new Hit[4];
    private int nextSlot;
    private int nativeUntil;

    void hit(Hitsplat hit, int cycle)
    {
        tick(cycle);
        if (hit == null || hit.getDisappearsOnGameCycle() <= cycle) { return; }
        Hit observed = new Hit(hit);
        if (nativeRequired(cycle))
        {
            nativeUntil = Math.max(nativeUntil, observed.expires);
            return;
        }
        boolean occupied = false;
        for (Hit slot : slots) { occupied |= slot != null; }
        if (!occupied) { nextSlot = 0; }
        if (observed.type >= 0)
        {
            for (int i = 0; i < slots.length; ++i)
            {
                int slot = nextSlot;
                nextSlot = (nextSlot + 1) % slots.length;
                if (slots[slot] == null) { slots[slot] = observed; return; }
            }
        }
        // HitsplatApplied also reports rejected native hits. Its supported API
        // does not expose acceptance/replacement priority or the native slot.
        // Never pick a winner by amount/expiry: keep native UI through the whole
        // possibly visible burst, then resume from empty, unambiguous slots.
        nativeUntil = observed.expires;
        for (Hit slot : slots)
        {
            if (slot != null) { nativeUntil = Math.max(nativeUntil, slot.expires); }
        }
        Arrays.fill(slots, null);
        nextSlot = 0;
    }

    void tick(int cycle)
    {
        for (int i = 0; i < slots.length; ++i)
        {
            if (slots[i] != null && slots[i].expires <= cycle) { slots[i] = null; }
        }
        if (nativeUntil <= cycle) { nativeUntil = 0; }
    }

    boolean nativeRequired(int cycle) { return cycle < nativeUntil; }
    Hit slot(int index, int cycle)
    {
        Hit hit = slots[index];
        return nativeRequired(cycle) || hit == null || hit.expires <= cycle ? null : hit;
    }

    void clear() { Arrays.fill(slots, null); nextSlot = nativeUntil = 0; }

    static final class Hit
    {
        final int type, amount, expires;
        Hit(Hitsplat hit)
        {
            type = hit.getHitsplatType(); amount = hit.getAmount(); expires = hit.getDisappearsOnGameCycle();
        }
    }
}
