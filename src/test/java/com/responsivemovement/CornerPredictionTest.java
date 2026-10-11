package com.responsivemovement;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

import static org.junit.Assert.*;

public class CornerPredictionTest
{
    @Test
    public void blockedInteractionTileCanBeApproachedWithoutWalkingIntoTheObject()
    {
        LocalPoint start = point(6464, 5952), target = point(6464, 6464);
        BiPredicate<LocalPoint, LocalPoint> corridor = (a, b) ->
            a.getX() == 6464 && b.getX() == 6464 &&
            a.getY() >= 5952 && a.getY() <= 6336 && b.getY() >= 5952 && b.getY() <= 6336;
        assertNull(MovementPath.anticipate(start, start, target, true, 0, 1,
            MovementPath.freshDeadline(0), corridor));
        MovementPath path = MovementPath.anticipate(start, start, target, true, 0, 1,
            MovementPath.freshDeadline(0), corridor, true);
        assertNotNull(path);
        advance(path, 0, 400);
        assertTrue(path.accept(point(6464, 6208), true));
        advance(path, 400, 960);
        assertEquals(point(6464, 6336), path.position());
        assertTrue(path.accept(point(6464, 6336), true));
        assertTrue(path.finished());
        assertNull(MovementPath.anticipate(point(6464, 6336), point(6464, 6336), target, true,
            1000_000_000L, 1, MovementPath.freshDeadline(1000_000_000L), corridor, true));
    }

    @Test
    public void blockedInteractionDoesNotInventADiagonalOrUnreachableApproach()
    {
        LocalPoint start = point(6208, 6208), target = point(6464, 6464);
        assertNull(MovementPath.anticipate(start, start, target, true, 0, 1,
            MovementPath.freshDeadline(0), (a, b) -> false, true));
        assertNull(MovementRoute.find(start, target, 64, (a, b) ->
            a.getY() <= 6336 && b.getY() <= 6336 && a.getX() <= 6336 && b.getX() <= 6336, true));
    }

    // A synthetic, reversible corridor through the recorded 19:40 route's
    // endpoints. The capture contains positions, not the actual collision map.
    private static final List<LocalPoint> CORRIDOR = List.of(
        point(9920, 9024), point(9792, 9024), point(9792, 8896), point(9792, 8768),
        point(9792, 8640), point(9920, 8640), point(9920, 8512), point(10048, 8512),
        point(10176, 8512), point(10176, 8640));
    private static final LocalPoint START = CORRIDOR.get(0);
    private static final LocalPoint END = CORRIDOR.get(CORRIDOR.size() - 1);
    private static final BiPredicate<LocalPoint, LocalPoint> WALLS = (a, b) ->
        CORRIDOR.contains(a) && CORRIDOR.contains(b) && Math.abs(CORRIDOR.indexOf(a) - CORRIDOR.indexOf(b)) == 1;

    private static LocalPoint point(int x, int y) { return new LocalPoint(x, y, 0); }
    private static void advance(MovementPath path, int from, int to)
    {
        for (int ms = from + 10; ms <= to; ms += 10) { path.advance(ms * 1_000_000L); }
    }

    @Test
    public void cornerStartMovesBeforeConfirmationAndContinuesThroughRecordedBends()
    {
        MovementPath path = MovementPath.anticipate(START, START, END, true, 0, 1,
            MovementPath.freshDeadline(0), WALLS);
        assertNotNull(path);
        advance(path, 0, 100);
        assertEquals(point(START.getX() - 40, START.getY()), path.position());
        assertEquals(START, path.confirmed());
        int next = 2;
        for (int ms = 110; ms <= 3000; ms += 10)
        {
            LocalPoint before = path.position();
            path.advance(ms * 1_000_000L);
            assertTrue(MovementPath.distance(before, path.position()) <= 4);
            if (ms % 600 == 0)
            {
                assertTrue(path.accept(CORRIDOR.get(Math.min(next, CORRIDOR.size() - 1)), true));
                next += 2;
            }
            assertTrue(path.clear());
        }
        assertEquals(END, path.position());
        assertTrue(path.finished());
    }

    @Test
    public void rejectedCornerClickReturnsAroundTheObstacle()
    {
        MovementPath path = MovementPath.anticipate(START, START, END, true, 0, 1,
            MovementPath.freshDeadline(0), WALLS);
        assertNotNull(path);
        for (int ms = 10; ms <= 2400; ms += 10)
        {
            LocalPoint before = path.position();
            path.advance(ms * 1_000_000L);
            assertTrue(MovementPath.distance(before, path.position()) <= 4);
            assertTrue(path.clear());
        }
        assertEquals(START, path.position());
        assertTrue(path.finished());
    }

    @Test
    public void midRunClickQueuesAtTheConfirmedCornerWithoutStoppingOrSnapping()
    {
        MovementPath path = MovementPath.idle(START, 0, 1, WALLS);
        assertTrue(path.accept(CORRIDOR.get(2), true));
        advance(path, 0, 200);
        LocalPoint before = path.position();
        path.replacement(true);
        assertTrue(path.anticipateContinuation(END, true, 200_000_000L));
        assertEquals(before, path.position());
        advance(path, 200, 640);
        assertEquals(CORRIDOR.get(2), path.position());
        assertTrue(path.moving());
        assertTrue(path.running());
        advance(path, 640, 740);
        assertEquals(point(9792, 8856), path.position());
        assertEquals(CORRIDOR.get(2), path.confirmed());
    }

    @Test
    public void expiringContinuationPreservesSlowConfirmedMovement()
    {
        MovementPath path = MovementPath.idle(START, 0, 0.25, WALLS);
        // Fine speed is retained: two running tiles take 256/0.10 = 2560 ms.
        assertTrue(path.accept(CORRIDOR.get(2), true));
        advance(path, 0, 200);
        path.replacement(true);
        assertTrue(path.anticipateContinuation(END, true, 200_000_000L));
        advance(path, 200, 1090);
        LocalPoint before = path.position();
        advance(path, 1090, 1100);
        assertTrue(MovementPath.distance(before, path.position()) <= 2);
        assertEquals("confirmed", path.phase());
        assertTrue(path.clear());
        advance(path, 1100, 2560);
        assertEquals(CORRIDOR.get(2), path.position());
        assertTrue(path.finished());
    }

    @Test
    public void serverDisagreementDropsTheContinuationButKeepsTheConfirmedDiagonal()
    {
        MovementPath path = MovementPath.idle(point(1344, 1344), 0, 1, (a, b) -> true);
        assertTrue(path.accept(point(1472, 1472), false));
        advance(path, 0, 640);
        assertTrue(path.accept(point(1600, 1344), false));
        advance(path, 640, 1260);
        path.replacement(true);
        assertTrue(path.anticipateContinuation(point(1344, 1344), true, 1260_000_000L));
        // The next server endpoint goes north, not west as the new click predicted.
        assertTrue(path.accept(point(1600, 1600), true));
        advance(path, 1260, 1280);
        assertEquals(point(1600, 1344), path.position());
        advance(path, 1280, 1600);
        assertEquals(point(1600, 1472), path.position());
        advance(path, 1600, 1920);
        assertEquals(point(1600, 1600), path.position());
        assertTrue(path.finished());
    }

    @Test
    public void confirmingARunContinuationDoesNotSpeedUpTheUnfinishedWalk()
    {
        MovementPath path = MovementPath.idle(point(1344, 1344), 0, 1, (a, b) -> true);
        assertTrue(path.accept(point(1472, 1472), false));
        advance(path, 0, 200);
        path.replacement(true);
        assertTrue(path.anticipateContinuation(point(1728, 1472), true, 200_000_000L));
        advance(path, 200, 300);
        assertTrue(path.accept(point(1728, 1472), true));
        assertFalse(path.running());
        advance(path, 300, 640);
        assertEquals(point(1472, 1472), path.position());
        assertTrue(path.running());
        advance(path, 640, 1280);
        assertEquals(point(1728, 1472), path.position());
        assertTrue(path.finished());
    }

    @Test
    public void repeatedMidRunClicksCannotKeepUnconfirmedForecastsAliveIndefinitely()
    {
        MovementPath path = MovementPath.idle(START, 0, 0.2, WALLS);
        assertTrue(path.accept(CORRIDOR.get(2), true));
        int previous = 0;
        for (int ms : new int[] {200, 1000, 1700})
        {
            advance(path, previous, ms);
            path.replacement(true);
            assertTrue(path.anticipateContinuation(END, true, ms * 1_000_000L));
            previous = ms;
        }
        advance(path, 1700, 1810);
        path.replacement(true);
        assertFalse(path.anticipateContinuation(END, true, 1810_000_000L));
        advance(path, 1810, 3200);
        assertEquals(CORRIDOR.get(2), path.position());
        assertTrue(path.finished());
    }

    @Test
    public void aMovingUnconfirmedPreviewCannotBeOverwrittenByAnotherClick()
    {
        MovementPath path = MovementPath.anticipate(START, START, END, true, 0, 1,
            MovementPath.freshDeadline(0), WALLS);
        advance(path, 0, 100);
        LocalPoint before = path.position();
        path.replacement(true);
        assertFalse(path.canAnticipateContinuation());
        assertFalse(path.anticipateContinuation(CORRIDOR.get(4), true, 100_000_000L));
        assertEquals(before, path.position());
    }

    @Test
    public void searchRejectsUnreachableTargetsOneWayEdgesAndExcessiveRoutes()
    {
        assertNull(MovementRoute.find(START, END, CORRIDOR.size() - 2, WALLS));
        assertEquals(CORRIDOR.subList(1, CORRIDOR.size()), MovementRoute.find(START, END, 64, WALLS));
        assertNull(MovementRoute.find(START, CORRIDOR.get(1), 64,
            (a, b) -> a.equals(START) && b.equals(CORRIDOR.get(1))));
        assertNull(MovementRoute.find(START, new LocalPoint(END.getX(), END.getY(), 1), 64, WALLS));
        assertNull(MovementRoute.find(point(START.getX() + 1, START.getY()), END, 64, WALLS));
    }

    @Test
    public void unreachableSearchHasABoundedCollisionBudget()
    {
        AtomicInteger checks = new AtomicInteger();
        LocalPoint target = point(START.getX() + 256, START.getY());
        assertNull(MovementRoute.find(START, target, 64, (a, b) ->
        {
            checks.incrementAndGet();
            return !b.equals(target);
        }));
        assertTrue(checks.get() > 0);
        assertTrue(checks.get() <= MovementRoute.MAX_VISITED * 16);
    }

    @Test
    public void newCollisionInvalidatesThePredictedCornerBeforeItCanBeUsed()
    {
        boolean[] closed = {false};
        MovementPath path = MovementPath.anticipate(START, START, END, true, 0, 1,
            MovementPath.freshDeadline(0), (a, b) -> !closed[0] && WALLS.test(a, b));
        assertNotNull(path);
        assertTrue(path.clear());
        closed[0] = true;
        assertFalse(path.clear());
    }
}
