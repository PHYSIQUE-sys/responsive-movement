package com.responsivemovement;

import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import org.junit.Test;
import static org.junit.Assert.*;

public class OverheadHitsplatsTest
{
    @Test
    public void slotsRemainStableDespiteNewAmountsExpiryAndEmptyGaps()
    {
        OverheadHitsplats hits = new OverheadHitsplats();
        hits.hit(hit(1, 110), 100);
        hits.hit(hit(99, 120), 100);
        hits.hit(hit(2, 130), 100);
        assertEquals(1, hits.slot(0, 100).amount);
        assertEquals(99, hits.slot(1, 100).amount);
        assertEquals(2, hits.slot(2, 100).amount);
        hits.tick(110);
        assertNull(hits.slot(0, 110));
        assertEquals(99, hits.slot(1, 110).amount);
        assertEquals(2, hits.slot(2, 110).amount);
        hits.hit(hit(5, 140), 110);
        assertEquals(5, hits.slot(3, 110).amount);
        hits.hit(hit(6, 150), 110);
        assertEquals(6, hits.slot(0, 110).amount);
        assertFalse(hits.nativeRequired(110));
    }

    @Test
    public void overflowHandsAllSlotsToNativeUntilEveryPotentialHitExpires()
    {
        OverheadHitsplats hits = new OverheadHitsplats();
        for (int amount = 1; amount <= 4; ++amount) { hits.hit(hit(amount, 150), 100); }
        hits.hit(hit(100, 120), 100); // Could be a rejected hit or a native replacement.
        assertTrue(hits.nativeRequired(100));
        for (int slot = 0; slot < 4; ++slot) { assertNull(hits.slot(slot, 100)); }
        hits.hit(hit(101, 170), 110);
        hits.tick(150);
        assertTrue(hits.nativeRequired(150));
        hits.tick(170);
        assertFalse(hits.nativeRequired(170));
        hits.hit(hit(3, 200), 170);
        assertEquals(3, hits.slot(0, 170).amount);
        assertNull(hits.slot(1, 170));
    }

    @Test
    public void expiredEventsDoNotConsumeSlotsAndCleanupRetiresNativeOwnership()
    {
        OverheadHitsplats hits = new OverheadHitsplats();
        hits.hit(hit(1, 100), 100);
        hits.hit(null, 100);
        assertNull(hits.slot(0, 100));
        for (int i = 0; i < 5; ++i) { hits.hit(hit(i, 150), 100); }
        assertTrue(hits.nativeRequired(100));
        hits.clear();
        assertFalse(hits.nativeRequired(100));
        hits.hit(hit(7, 130), 100);
        assertEquals(7, hits.slot(0, 100).amount);
        hits.tick(130);
        hits.hit(hit(8, 160), 130);
        assertEquals(8, hits.slot(0, 130).amount);
    }

    static Hitsplat hit(int amount, int expires)
    {
        return new Hitsplat()
        {
            @Override public int getHitsplatType() { return HitsplatID.DAMAGE_ME; }
            @Override public int getAmount() { return amount; }
            @Override public int getDisappearsOnGameCycle() { return expires; }
        };
    }
}
