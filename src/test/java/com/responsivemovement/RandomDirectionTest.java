package com.responsivemovement;

import java.util.function.BiPredicate;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class RandomDirectionTest
{
    private static final BiPredicate<LocalPoint, LocalPoint> OPEN = (a, b) -> true;
    private static LocalPoint point(int x, int y) { return new LocalPoint(x, y, 0); }
    private static LocalPoint tile(int x, int y) { return point(x * 128 + 64, y * 128 + 64); }
    private static void advance(MovementPath path, int from, int to)
    {
        for (int ms = from + 10; ms <= to; ms += 10) { path.advance(ms * 1_000_000L); }
    }
    private static double distance(LocalPoint a, LocalPoint b)
    {
        return Math.hypot(a.getX() - b.getX(), a.getY() - b.getY());
    }

    @Test
    public void capturedConfirmedKnightMoveRespondsToNewRandomDirection()
    {
        // Session 1790563797102 cycles 118423/118424: authority reaches the
        // old knight endpoint while visible is midway and a new southwest click arrives.
        LocalPoint start = point(6464, 7616), oldEnd = point(6592, 7872), target = point(6336, 7488);
        MovementPath path = MovementPath.anticipate(start, start, oldEnd, true, 0, 1,
            MovementPath.freshDeadline(0), OPEN);
        advance(path, 0, 360);
        assertTrue(path.accept(oldEnd, true));
        LocalPoint before = path.position();
        assertNull(path.anticipateRedirect(target, true, 360_000_000L));
        MovementPath revised = path.retargetWalk(target, true, 360_000_000L);
        assertNotNull("a fresh click must not wait behind the confirmed chord", revised);
        assertEquals(before, revised.position());
        assertEquals(oldEnd, revised.confirmed());
        advance(revised, 360, 380);
        assertTrue("new direction must begin in the first update", distance(revised.position(), target) < distance(before, target));
        assertTrue(revised.clear());
    }

    @Test
    public void allDirectionsCanReplaceConfirmedOrSpeculativeCardinalDiagonalAndKnightMoves()
    {
        int[][] activeDirections = {{2, 0}, {0, 2}, {-2, 0}, {0, -2}, {2, 2}, {-2, 2},
            {2, -2}, {-2, -2}, {2, 1}, {-2, 1}, {1, 2}, {-1, -2}};
        int[][] clickDirections = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}, {3, 3}, {-3, 3}, {3, -3}, {-3, -3}, {2, -1}, {-1, 2}};
        for (boolean confirmed : new boolean[] {false, true})
        {
            for (int[] active : activeDirections)
            {
                LocalPoint start = tile(30, 30), end = tile(30 + active[0], 30 + active[1]);
                for (int[] click : clickDirections)
                {
                    MovementPath path = MovementPath.anticipate(start, start, end, true, 0, 1,
                        MovementPath.freshDeadline(0), OPEN);
                    advance(path, 0, 200);
                    if (confirmed) { assertTrue(path.accept(end, true)); }
                    LocalPoint target = tile(30 + click[0], 30 + click[1]), before = path.position();
                    MovementPath revised = path.retargetWalk(target, true, 200_000_000L);
                    assertNotNull("active=" + end + " target=" + target + " confirmed=" + confirmed, revised);
                    assertEquals(before, revised.position());
                    assertEquals(path.confirmed(), revised.confirmed());
                    assertEquals(target, revised.clickedDestination());
                    assertEquals(path.predictionDeadlineNanos(), revised.predictionDeadlineNanos());
                    advance(revised, 200, 220);
                    assertNotEquals("movement cannot pause waiting for authority", before, revised.position());
                    assertTrue(MovementPath.distance(before, revised.position()) <= 8);
                    assertTrue(revised.clear());
                }
            }
        }
    }

    @Test
    public void freshClickCanReplaceAConfirmedCornerButOrdinaryReconciliationKeepsIt()
    {
        MovementPath path = MovementPath.idle(tile(30, 30), 0, 1, OPEN);
        assertTrue(path.accept(tile(31, 31), false));
        assertTrue(path.accept(tile(32, 30), false));
        advance(path, 0, 200);
        LocalPoint before = path.position(), target = tile(29, 32);
        MovementPath revised = path.retargetWalk(target, true, 200_000_000L);
        assertNotNull(revised);
        assertEquals(before, revised.position());
        assertEquals(tile(32, 30), revised.confirmed());
        // Merely constructing the candidate must not rewrite the old path.
        advance(path, 200, 640);
        assertEquals(tile(31, 31), path.position());
        advance(path, 640, 1280);
        assertEquals(tile(32, 30), path.position());
    }

    @Test
    public void aBlockedKnightCorridorCannotBeUsedToCutAcrossAWall()
    {
        boolean[] blocked = {false};
        MovementPath path = MovementPath.anticipate(tile(30, 30), tile(30, 30), tile(32, 31), true,
            0, 1, MovementPath.freshDeadline(0), (a, b) -> !blocked[0]);
        advance(path, 0, 200);
        LocalPoint before = path.position();
        blocked[0] = true;
        assertNull(path.retargetWalk(tile(29, 32), true, 200_000_000L));
        assertEquals(before, path.position());
    }

    @Test
    public void randomClicksWithDelayedSixHundredMillisecondAuthorityKeepResponding()
    {
        LocalPoint center = tile(30, 30);
        LocalPoint[] targets = {tile(31, 32), tile(29, 29), tile(32, 30), tile(28, 31),
            tile(32, 29), tile(29, 32), tile(30, 28), tile(32, 32), tile(28, 30), tile(31, 28)};
        LocalPoint server = center, destination = targets[0], previousServerGoal = destination;
        MovementPath path = MovementPath.anticipate(center, server, destination, true, 0, 1,
            MovementPath.freshDeadline(0), OPEN);
        int changes = 0;
        for (int ms = 20; ms <= 12800; ms += 20)
        {
            boolean changedWhileMoving = false;
            if (ms % 600 == 0)
            {
                java.util.List<LocalPoint> route = MovementPath.checkedRoute(server, previousServerGoal, OPEN, 64);
                assertNotNull(route);
                if (!route.isEmpty()) { server = route.get(Math.min(2, route.size()) - 1); }
                assertTrue("authority at " + ms, path.accept(server, true));
                previousServerGoal = destination;
            }
            if (ms % 400 == 0)
            {
                destination = targets[(++changes) % targets.length];
                LocalPoint before = path.position();
                changedWhileMoving = path.moving();
                MovementPath next = path.moving() ? path.retargetWalk(destination, true, ms * 1_000_000L)
                    : MovementPath.anticipate(before, server, destination, true, ms * 1_000_000L, 1,
                        path.restartDeadline(ms * 1_000_000L, server), OPEN);
                assertNotNull("click at " + ms + " visible=" + before + " authority=" + server +
                    " phase=" + path.phase() + " target=" + destination, next);
                assertEquals(before, next.position());
                assertEquals(destination, next.clickedDestination());
                path = next;
            }
            LocalPoint before = path.position();
            path.advance(ms * 1_000_000L);
            if (changedWhileMoving) { assertNotEquals("response on the same prepared frame", before, path.position()); }
            assertTrue(MovementPath.distance(before, path.position()) <= 8);
            assertTrue(path.clear());
        }
        assertEquals(32, changes);
    }

    @Test
    public void aFreshClickOnACorridorAnchorHasNoUnwantedOnwardLeg()
    {
        LocalPoint start = tile(30, 30), oldEnd = tile(32, 31), target = tile(31, 30);
        MovementPath path = MovementPath.idle(start, 0, 1, OPEN);
        assertTrue(path.accept(oldEnd, true));
        advance(path, 0, 200);
        LocalPoint before = path.position();
        MovementPath next = path.retargetWalk(target, true, 200_000_000L);
        assertNotNull(next);
        assertEquals(before, next.position());
        advance(next, 200, 400);
        assertEquals(target, next.position());
        assertTrue(next.accept(target, true));
        assertTrue(next.finished());
    }

    @Test
    public void randomRetargetsWithoutServerProgressCannotRenewTheHardDeadline()
    {
        LocalPoint start = tile(30, 30);
        MovementPath path = MovementPath.idle(start, 0, 1, OPEN);
        assertTrue(path.accept(tile(32, 31), true));
        long deadline = path.predictionDeadlineNanos();
        LocalPoint[] targets = {tile(29, 31), tile(32, 32), tile(30, 29), tile(31, 32)};
        int from = 0;
        for (int i = 0; i < 4; ++i)
        {
            int ms = 200 + i * 300;
            advance(path, from, ms);
            MovementPath next = path.retargetWalk(targets[i], true, ms * 1_000_000L);
            assertNotNull(next);
            assertEquals(deadline, next.predictionDeadlineNanos());
            path = next; from = ms;
        }
        assertNull(path.retargetWalk(tile(30, 30), true, deadline));
        advance(path, from, 5000);
        assertEquals(tile(32, 31), path.position());
        assertTrue(path.finished());
    }

    @Test
    public void cancelledArbitraryTurnAndSceneRebaseRecoverToAuthorityWithoutASnap()
    {
        MovementPath path = MovementPath.idle(tile(30, 30), 0, 1, OPEN);
        assertTrue(path.accept(tile(32, 31), true));
        advance(path, 0, 200);
        path = path.retargetWalk(tile(29, 32), true, 200_000_000L);
        assertNotNull(path);
        advance(path, 200, 300);
        LocalPoint before = path.position();
        path.rebase(-128, -128, 300_000_000L);
        assertEquals(point(before.getX() - 128, before.getY() - 128), path.position());
        for (int ms = 310; ms <= 4000; ms += 10)
        {
            before = path.position(); path.advance(ms * 1_000_000L);
            assertTrue(MovementPath.distance(before, path.position()) <= 4);
            assertTrue(path.clear());
        }
        assertEquals(tile(31, 30), path.position());
        assertTrue(path.finished());
    }
}
