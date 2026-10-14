package com.responsivemovement;

import java.util.function.BiPredicate;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** Local departures from the 2026-10-14 19:18--19:22 captures, without game input. */
public class BookmarkedWalkDetourTest
{
    private static final BiPredicate<LocalPoint, LocalPoint> OPEN = (a, b) -> true;
    private static LocalPoint p(int x, int y) { return new LocalPoint(x, y, 0); }

    @Test
    public void cardinalToDiagonalReversalDoesNotFinishThePreviousClicksCorner()
    {
        // #53918: near #53715, display north of authority 7232,9536; new goal
        // 6848,9920. The existing connector first travels south to that authority.
        MovementPath path = MovementPath.idle(p(7232,9792), 0, 1.18, OPEN);
        assertTrue(path.accept(p(7232,9536), true));
        advance(path, 0, 450);
        LocalPoint before = path.position();
        path = path.retargetWalk(p(6848,9920), true, 450_000_000L);
        assertNotNull(path);
        path.advance(470_000_000L);
        assertTrue("new diagonal starts west immediately", path.position().getX() < before.getX());
        assertTrue("do not finish the obsolete southbound corner", path.position().getY() > before.getY());
        assertTrue(path.clear());
    }

    @Test
    public void knightToCardinalReversalCanCrossTheCheckedSharedSquare()
    {
        // #61676: near #61564, reverse a southeast knight toward 6720,9920.
        // The old middle at 6720,9664 need not become a lateral detour.
        MovementPath path = MovementPath.idle(p(6720,9664), 0, 1.18, OPEN);
        assertTrue(path.accept(p(6848,9408), true));
        advance(path, 0, 400);
        LocalPoint before = path.position();
        path = path.retargetWalk(p(6720,9920), true, 400_000_000L);
        assertNotNull(path);
        for (int ms = 420; ms <= 700; ms += 20)
        {
            path.advance(ms * 1_000_000L);
            assertTrue("never continue south after the northbound replan", path.position().getY() >= before.getY());
            assertTrue("no sideways overshoot at " + ms + " " + path.position() + " " + path.traceSnapshot().fields(0),
                path.position().getX() <= before.getX());
            assertTrue(path.clear());
            before = path.position();
        }
    }

    @Test
    public void diagonalSideDepartureDoesNotBorrowTheOldWestboundCorner()
    {
        // #53455/#59669 have diagonal/knight side-anchor connectors. This local
        // square isolates the same west/north -> east/north change of direction.
        MovementPath path = MovementPath.idle(p(7232,9408), 0, 1.18, OPEN);
        assertTrue(path.accept(p(6976,9664), true));
        advance(path, 0, 400);
        LocalPoint before = path.position();
        path = path.retargetWalk(p(7232,9792), true, 400_000_000L);
        assertNotNull(path);
        path.advance(420_000_000L);
        assertTrue("depart east rather than toward the old side anchor " + before + " " + path.position() + path.traceSnapshot().fields(0),
            path.position().getX() >= before.getX());
        assertTrue(path.position().getY() > before.getY());
        assertTrue(path.clear());
    }

    @Test
    public void everyDiagonalAndPerimeterDirectionMustBeClearBeforeBypassingTheCorner()
    {
        LocalPoint a = p(7232,9536), b = p(7104,9664), c = p(7232,9664), d = p(7104,9536);
        for (LocalPoint[] edge : new LocalPoint[][] {{a,b},{c,d},{a,c},{a,d},{b,c},{b,d}})
        {
            for (boolean reverse : new boolean[] {false,true})
            {
                LocalPoint blockedFrom = edge[reverse ? 1 : 0], blockedTo = edge[reverse ? 0 : 1];
                boolean[] closed = {false};
                BiPredicate<LocalPoint,LocalPoint> collision = (from,to) -> !closed[0] ||
                    !from.equals(blockedFrom) || !to.equals(blockedTo);
                MovementPath path = MovementPath.idle(p(7232,9792),0,1.18,collision);
                assertTrue(path.accept(a,true)); advance(path,0,450);
                LocalPoint before = path.position(); closed[0] = true;
                MovementPath next = path.retargetWalk(p(6848,9920),true,450_000_000L);
                if (next == null) { assertEquals(before,path.position()); continue; }
                assertTrue(next.clear()); next.advance(470_000_000L);
                assertFalse("a partially proven square cannot bypass the old corner: " + blockedFrom + " -> " + blockedTo,
                    next.position().getX() < before.getX() && next.position().getY() > before.getY());
            }
        }
    }

    @Test
    public void closingAProvenDepartureSquareInvalidatesItWithoutAdvancingOrReplanning()
    {
        boolean[] closed = {false};
        BiPredicate<LocalPoint,LocalPoint> collision = (a,b) -> !closed[0] ||
            !a.equals(p(7232,9664)) || !b.equals(p(7104,9536));
        MovementPath path = MovementPath.idle(p(7232,9792),0,1.18,collision);
        assertTrue(path.accept(p(7232,9536),true)); advance(path,0,450);
        path = path.retargetWalk(p(6848,9920),true,450_000_000L); assertNotNull(path);
        path.advance(470_000_000L); LocalPoint before = path.position();
        closed[0] = true;
        assertFalse(path.clear());
        assertNull(path.retargetWalk(p(6976,9920),true,490_000_000L));
        assertEquals(before,path.position());
    }

    @Test
    public void reversalSquareKeepsItsFractionAndDeadlineThroughRebaseAndTimeoutRecovery()
    {
        MovementPath path = MovementPath.idle(p(7232,9792),0,1.18,OPEN);
        assertTrue(path.accept(p(7232,9536),true)); advance(path,0,450);
        long hard = path.predictionDeadlineNanos();
        path = path.retargetWalk(p(6848,9920),true,450_000_000L); assertNotNull(path);
        path.advance(470_000_000L); LocalPoint before = path.position();
        assertTrue(path.resumeScene(-128,-256,p(7104,9280),p(6720,9664),true,470_000_000L));
        assertEquals(p(before.getX()-128,before.getY()-256),path.position());
        assertEquals(hard,path.predictionDeadlineNanos());
        for (int ms = 490; ms <= 3000; ms += 20)
        {
            path.advance(ms*1_000_000L,false,50,50,p(6720,9664),new ArrivalPacing(10,10,1,1));
            assertEquals("settings and timeout cannot add a chain",hard,path.predictionDeadlineNanos());
            assertTrue(path.clear());
        }
        assertEquals(p(7104,9280),path.position()); assertTrue(path.finished());
    }

    @Test
    public void directReversalRotatesAndReflectsWithoutCoordinateSpecificRules()
    {
        for (int rotation = 0; rotation < 4; ++rotation)
        {
            for (boolean reflected : new boolean[] {false,true})
            {
                LocalPoint from = transform(0,256,rotation,reflected), authority = transform(0,0,rotation,reflected);
                LocalPoint goal = transform(-384,384,rotation,reflected);
                LocalPoint reference = null;
                for (int cadence : new int[] {8,20,33})
                {
                    MovementPath path = MovementPath.idle(from,0,1.18,OPEN);
                    assertTrue(path.accept(authority,true)); advance(path,0,450);
                    LocalPoint before = path.position();
                    path = path.retargetWalk(goal,true,450_000_000L); assertNotNull(path);
                    for (int ms = 450+cadence; ms < 750; ms += cadence)
                    {
                        path.advance(ms*1_000_000L,false,15,13,goal,new ArrivalPacing(0.2,1,50,49.8));
                        assertTrue(between(path.position().getX(),before.getX(),goal.getX()));
                        assertTrue(between(path.position().getY(),before.getY(),goal.getY()));
                        assertTrue(path.clear());
                    }
                    path.advance(750_000_000L,false,15,13,goal,new ArrivalPacing(0.2,1,50,49.8));
                    if (reference == null) { reference = path.position(); }
                    assertTrue("same-time cadence independence",MovementPath.distance(reference,path.position()) <= 1);
                }
            }
        }
    }

    private static LocalPoint transform(int dx, int dy, int rotation, boolean reflected)
    {
        if (reflected) { dx = -dx; }
        for (int i = 0; i < rotation; ++i) { int old = dx; dx = -dy; dy = old; }
        return p(7232+dx,9536+dy);
    }

    private static boolean between(int value, int from, int to)
    {
        return value >= Math.min(from,to)-1 && value <= Math.max(from,to)+1;
    }

    private static void advance(MovementPath path, int from, int to)
    {
        for (int ms = from + 20; ms < to; ms += 20) { path.advance(ms * 1_000_000L); }
        path.advance(to * 1_000_000L);
    }
}
