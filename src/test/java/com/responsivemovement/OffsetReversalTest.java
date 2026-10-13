package com.responsivemovement;

import java.util.function.BiPredicate;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

import static org.junit.Assert.*;

public class OffsetReversalTest
{
    private static LocalPoint point(int x, int y, boolean transpose)
    {
        return new LocalPoint(transpose ? y : x, transpose ? x : y, 0);
    }

    private static void advance(MovementPath path, int from, int to)
    {
        for (int ms = from + 10; ms <= to; ms += 10) { path.advance(ms * 1_000_000L); }
    }

    @Test
    public void recordedLateClickReversesWhileAuthorityIsOnTheAdjacentRow()
    {
        for (boolean transpose : new boolean[] {false, true})
        {
            LocalPoint authority = point(6592, 5696, transpose);
            MovementPath west = MovementPath.anticipate(point(6976, 5568, transpose), authority,
                point(6336, 5568, transpose), true, 0, 1, MovementPath.freshDeadline(0), (a, b) -> true);
            assertNotNull(west);
            advance(west, 0, 400);
            assertEquals(point(6816, 5568, transpose), west.position());
            MovementPath east = west.retargetWalk(point(7232, 5568, transpose), true, 400_000_000L);
            assertNotNull("capture cycle 61814 must not wait for the old forecast", east);
            assertEquals(west.position(), east.position());
            advance(east, 400, 420);
            assertEquals(point(6824, 5568, transpose), east.position());
            assertEquals(authority, east.confirmed());
            assertTrue(east.accept(point(6848, 5696, transpose), true));
            advance(east, 420, 600);
            assertTrue(east.accept(point(6976, 5568, transpose), true));
            advance(east, 600, 1000);
            assertTrue(east.accept(point(7232, 5568, transpose), true));
            advance(east, 1000, 1600);
            assertEquals(point(7232, 5568, transpose), east.position());
            assertTrue(east.finished());
            assertTrue(east.clear());
        }
    }

    @Test
    public void offsetAuthorityCannotStartAReversalAcrossADisconnectedWall()
    {
        // The visible horizontal edge is open, but the adjacent authoritative
        // row cannot connect to it. Do not relax this to a distance-only test.
        BiPredicate<LocalPoint, LocalPoint> rows = (a, b) -> a.getY() == b.getY();
        MovementPath west = MovementPath.anticipate(point(6976, 5568, false), point(6592, 5696, false),
            point(6336, 5568, false), true, 0, 1, MovementPath.freshDeadline(0), rows);
        assertNotNull(west);
        advance(west, 0, 400);
        LocalPoint before = west.position();
        assertNull(west.retargetWalk(point(7232, 5568, false), true, 400_000_000L));
        assertEquals(before, west.position());
    }

    @Test
    public void unconfirmedOffsetReversalStillTimesOutAndReturnsToAuthority()
    {
        LocalPoint authority = point(6592, 5696, false);
        MovementPath west = MovementPath.anticipate(point(6976, 5568, false), authority,
            point(6336, 5568, false), true, 0, 1, MovementPath.freshDeadline(0), (a, b) -> true);
        advance(west, 0, 400);
        MovementPath east = west.retargetWalk(point(7232, 5568, false), true, 400_000_000L);
        assertNotNull(east);
        for (int ms = 410; ms <= 4000; ms += 10)
        {
            LocalPoint before = east.position();
            east.advance(ms * 1_000_000L);
            assertTrue(MovementPath.distance(before, east.position()) <= 4);
            assertTrue(east.clear());
        }
        assertEquals(authority, east.position());
        assertTrue(east.finished());
    }
}
