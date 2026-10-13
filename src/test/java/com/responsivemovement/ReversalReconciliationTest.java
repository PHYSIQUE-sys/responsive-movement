package com.responsivemovement;

import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

import static org.junit.Assert.*;

public class ReversalReconciliationTest
{
    private static LocalPoint point(int x) { return new LocalPoint(x, 9024, 0); }
    private static void advance(MovementPath path, int from, int to)
    {
        for (int ms = from + 10; ms <= to; ms += 10) { path.advance(ms * 1_000_000L); }
    }

    @Test
    public void delayedOppositeUpdateKeepsTheNewestClickUntilItsOriginalDeadline()
    {
        // 11:38 capture, cycles 128718/128719: reverse toward 7232 while
        // authority is 7104, then receive the previous westbound endpoint 6848.
        MovementPath west = MovementPath.anticipate(point(6848), point(7104), point(6336), true,
            0, 1, MovementPath.freshDeadline(0), (a, b) -> true);
        advance(west, 0, 560);
        assertEquals(point(6624), west.position());
        MovementPath east = west.retargetWalk(point(7232), true, 560_000_000L);
        assertNotNull(east);
        assertTrue(east.accept(point(6848), true));
        assertEquals("preview", east.phase());
        for (int ms = 570; ms < 1460; ms += 10)
        {
            LocalPoint before = east.position();
            east.advance(ms * 1_000_000L);
            assertEquals("do not follow an older click while awaiting the new click", before.getX() + 4, east.position().getX());
            assertTrue(east.clear());
        }
        // If the new click never confirms, the opposite update is authoritative.
        int beforeExpiry = east.position().getX();
        advance(east, 1450, 1460);
        assertTrue(east.position().getX() < beforeExpiry);
        advance(east, 1460, 2400);
        assertEquals(point(6848), east.position());
        assertTrue(east.finished());
    }

    @Test
    public void latestCaptureDoesNotTurnBackAndStopForOneStaleServerTick()
    {
        // Capture 1789956595877, cycles 12115..12149: westbound authority
        // arrives ~80ms after the east click, then east confirmation ~600ms later.
        MovementPath west = MovementPath.anticipate(point(6720), point(6720), point(6208), true,
            0, 1, MovementPath.freshDeadline(0), (a, b) -> true);
        advance(west, 0, 620);
        assertEquals(point(6472), west.position());
        MovementPath east = west.retargetWalk(point(7232), true, 620_000_000L);
        assertNotNull(east);
        for (int ms = 630; ms <= 1300; ms += 10)
        {
            if (ms == 700) { assertTrue(east.accept(point(6464), true)); }
            if (ms == 1300) { assertTrue(east.accept(point(6720), true)); }
            LocalPoint before = east.position();
            east.advance(ms * 1_000_000L);
            assertEquals("unwanted reversal/pause at " + ms, before.getX() + 4, east.position().getX());
            assertTrue(east.clear());
            assertTrue(MovementPath.distance(east.position(), east.confirmed()) <= 4 * 128);
        }
        assertTrue(east.accept(point(6976), true));
        advance(east, 1300, 1800);
        assertTrue(east.accept(point(7232), true));
        advance(east, 1800, 2600);
        assertEquals(point(7232), east.position());
        assertTrue(east.finished());
    }

    @Test
    public void latestCaptureClickAndServerOrderingSurvivesSeveralReversals()
    {
        // Relative timing of click cycles 12013,12039,12064,12089,12114
        // and server cycles 12029,12059,12089,12119,12149,12179,12209.
        MovementPath path = MovementPath.anticipate(point(6464), point(6464), point(7104), true,
            0, 1, MovementPath.freshDeadline(0), (a, b) -> true);
        int[] clicks = {520, 1020, 1520, 2020};
        int[] destinations = {6080, 7232, 6208, 7232};
        int[] ticks = {320, 920, 1520, 2120, 2720, 3320, 3920};
        int[] endpoints = {6720, 6464, 6720, 6464, 6720, 6976, 7232};
        int click = 0, tick = 0;
        for (int ms = 20; ms <= 4400; ms += 20)
        {
            LocalPoint before = path.position();
            if (tick < ticks.length && ms == ticks[tick]) { assertTrue(path.accept(point(endpoints[tick++]), true)); }
            if (click < clicks.length && ms == clicks[click])
            {
                MovementPath next = path.retargetWalk(point(destinations[click++]), true, ms * 1_000_000L);
                assertNotNull(next);
                path = next;
            }
            assertEquals(before, path.position());
            path.advance(ms * 1_000_000L);
            assertTrue(MovementPath.distance(before, path.position()) <= 8);
            if (ms > 2020 && before.getX() < 7232)
            {
                assertTrue("final click reversed or stalled at " + ms, path.position().getX() > before.getX());
            }
            assertTrue(path.clear());
        }
        assertEquals(point(7232), path.position());
        assertTrue(path.finished());
    }

    private static MovementPath limitedEastwardReversal()
    {
        MovementPath west = MovementPath.idle(point(6336), 0, 1, (a, b) -> true);
        assertTrue(west.accept(point(6080), true));
        assertTrue(west.accept(point(5824), true));
        advance(west, 0, 220);
        assertEquals(point(6248), west.position());
        MovementPath east = west.retargetWalk(point(6976), true, 220_000_000L);
        assertNotNull(east);
        advance(east, 220, 600);
        assertEquals(point(6336), east.position());
        assertFalse(east.moving());
        return east;
    }

    @Test
    public void repeatedOldDirectionUpdatesDoNotRenewTheResponseDeadline()
    {
        MovementPath west = MovementPath.anticipate(point(6848), point(7104), point(6336), true,
            0, 1, MovementPath.freshDeadline(0), (a, b) -> true);
        advance(west, 0, 560);
        MovementPath east = west.retargetWalk(point(7232), true, 560_000_000L);
        assertNotNull(east);
        assertTrue(east.accept(point(6848), true));
        advance(east, 560, 1000);
        assertTrue(east.accept(point(6720), true));
        advance(east, 1000, 1450);
        assertEquals("preview", east.phase());
        advance(east, 1450, 1460);
        assertEquals("recovery", east.phase());
        advance(east, 1460, 3000);
        assertEquals(point(6720), east.position());
        assertTrue(east.finished());
    }

    @Test
    public void staleTickClipsTheForecastToTheExistingGapLimit()
    {
        MovementPath west = MovementPath.anticipate(point(6720), point(6720), point(6208), true,
            0, 1, MovementPath.freshDeadline(0), (a, b) -> true);
        advance(west, 0, 620);
        MovementPath east = west.retargetWalk(point(7232), true, 620_000_000L);
        assertNotNull(east);
        assertTrue(east.accept(point(6464), true));
        advance(east, 620, 700);
        assertTrue(east.accept(point(6208), true));
        for (int ms = 710; ms <= 3000; ms += 10)
        {
            LocalPoint before = east.position();
            east.advance(ms * 1_000_000L);
            assertTrue(MovementPath.distance(before, east.position()) <= 4);
            assertTrue(MovementPath.distance(east.position(), east.confirmed()) <= 4 * 128);
            assertTrue(east.clear());
        }
        assertEquals(point(6208), east.position());
        assertTrue(east.finished());
    }

    @Test
    public void boundedStaleTickHandlingAlsoWorksForNorthSouthReversals()
    {
        LocalPoint north = new LocalPoint(9024, 6720, 0), south = new LocalPoint(9024, 6208, 0);
        MovementPath path = MovementPath.anticipate(north, north, south, true, 0, 1,
            MovementPath.freshDeadline(0), (a, b) -> true);
        advance(path, 0, 620);
        path = path.retargetWalk(new LocalPoint(9024, 7232, 0), true, 620_000_000L);
        assertNotNull(path);
        assertTrue(path.accept(new LocalPoint(9024, 6464, 0), true));
        int before = path.position().getY();
        advance(path, 620, 720);
        assertEquals(before + 40, path.position().getY());
        assertTrue(path.clear());
    }

    @Test
    public void authorityApproachingTheReversalOriginReplenishesTheClippedPreview()
    {
        // Capture cycles 128262..128269 stopped at 6336. The new endpoint
        // 6080 approaches origin 6208 but does not land on the clicked path yet.
        MovementPath east = limitedEastwardReversal();
        assertTrue(east.accept(point(6080), true));
        assertEquals(point(6336), east.position());
        assertTrue("fresh approach progress should release the stalled preview", east.moving());
        for (int ms = 610; ms <= 1190; ms += 10)
        {
            LocalPoint before = east.position();
            east.advance(ms * 1_000_000L);
            assertEquals(before.getX() + 4, east.position().getX());
            assertTrue(MovementPath.distance(east.position(), east.confirmed()) <= 4 * 128);
        }
        assertTrue(east.accept(point(6336), true));
        advance(east, 1190, 1220);
        assertTrue(east.moving());
        assertTrue(east.clear());
    }

    @Test
    public void replenishedReversalStillRecoversWhenConfirmationsStop()
    {
        MovementPath east = limitedEastwardReversal();
        assertTrue(east.accept(point(6080), true));
        advance(east, 600, 3200);
        assertEquals(point(6080), east.position());
        assertTrue(east.finished());
        assertTrue(east.clear());
    }
}
