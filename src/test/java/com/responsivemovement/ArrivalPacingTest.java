package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import java.util.function.BiPredicate;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArrivalPacingTest
{
    private static final BiPredicate<LocalPoint, LocalPoint> OPEN = (a, b) -> true;
    private static LocalPoint tile(int x, int y) { return new LocalPoint(x * 128 + 64, y * 128 + 64, 0); }

    @Test
    public void shortWalkRunDiagonalAndKnightMovesMatchArrivalSlowdownOff()
    {
        for (boolean run : new boolean[] {false, true})
        {
            for (LocalPoint goal : new LocalPoint[] {tile(12, 10), tile(12, 12), tile(12, 11)})
            {
                MovementPath base = confirmed(goal, run), eased = confirmed(goal, run);
                int halfway = run ? 320 : 640;
                advance(base, 0, halfway, 20, goal, 0, 0, 0);
                advance(eased, 0, halfway, 20, goal, 50, 0, 0);
                assertEquals(base.position(), eased.position());
                advance(base, halfway, halfway + 100, 20, goal, 0, 0, 0);
                advance(eased, halfway, halfway + 100, 20, goal, 50, 0, 0);
                assertEquals(base.position(), eased.position());
                assertEquals(base.presentationRunning(), eased.presentationRunning());
                assertEquals(goal, eased.confirmed());
                advance(eased, halfway + 100, 2000, 20, null, 50, 0, 0);
                assertEquals(goal, eased.position());
                assertTrue(eased.finished());
                assertTrue(eased.clear());
            }
        }
    }

    @Test
    public void bothStageBoundariesAreFrameIndependentWithCatchUpAndAheadPacing()
    {
        for (boolean preview : new boolean[] {false, true})
        {
            for (int percent : new int[] {10, 25, 50})
            {
                LocalPoint goal = tile(13, 11), reference = null;
                for (int cadence : new int[] {8, 20, 33, 100})
                {
                    MovementPath path = preview ? MovementPath.anticipate(tile(10, 10), tile(10, 10), goal,
                        true, 0, 1, MovementPath.freshDeadline(0), OPEN) : confirmed(goal, true);
                    long deadline = path.predictionDeadlineNanos();
                    advance(path, 0, 600, cadence, goal, percent, 50, 25);
                    if (reference == null) { reference = path.position(); }
                    assertEquals(reference, path.position());
                    assertEquals(deadline, path.predictionDeadlineNanos());
                    assertTrue(path.clear());
                    if (preview)
                    {
                        assertTrue(path.accept(tile(12, 10), true));
                        advance(path, 600, 900, cadence, goal, percent, 50, 25);
                        assertTrue(path.accept(goal, true));
                    }
                    else { advance(path, 600, 900, cadence, goal, percent, 50, 25); }
                    advance(path, 900, 3000, cadence, null, percent, 50, 25);
                    assertEquals(goal, path.position());
                    assertTrue(path.finished());
                }
            }
        }
    }

    @Test
    public void onwardDestinationDoesNotSlowAnIntermediateConfirmedEndpoint()
    {
        MovementPath base = confirmed(tile(12, 10), true), eased = confirmed(tile(12, 10), true);
        advance(base, 0, 500, 20, tile(14, 10), 0, 10, 10);
        advance(eased, 0, 500, 20, tile(14, 10), 50, 10, 10);
        assertEquals(base.position(), eased.position());
        assertTrue(base.traceSnapshot().same(eased.traceSnapshot()));
        MovementPath preview = MovementPath.anticipate(tile(10, 10), tile(10, 10), tile(20, 10),
            true, 0, 1, MovementPath.freshDeadline(0), OPEN);
        MovementPath other = MovementPath.anticipate(tile(10, 10), tile(10, 10), tile(20, 10),
            true, 0, 1, MovementPath.freshDeadline(0), OPEN);
        advance(preview, 0, 800, 20, null, 0, 0, 0);
        advance(other, 0, 800, 20, null, 50, 0, 0);
        assertEquals(preview.position(), other.position());
    }

    @Test
    public void rejectionRecoveryKeepsItsPaceAndPredictionDeadline()
    {
        LocalPoint start = tile(10, 10), goal = tile(12, 10);
        MovementPath base = MovementPath.anticipate(start, start, goal, true, 0, 1, MovementPath.freshDeadline(0), OPEN);
        MovementPath eased = MovementPath.anticipate(start, start, goal, true, 0, 1, MovementPath.freshDeadline(0), OPEN);
        advance(base, 0, 200, 20, goal, 0, 0, 0);
        advance(eased, 0, 200, 20, goal, 0, 0, 0);
        assertEquals(base.position(), eased.position());
        base.cancel(); eased.cancel();
        advance(base, 200, 600, 20, start, 0, 0, 0);
        advance(eased, 200, 600, 20, start, 50, 0, 0);
        assertEquals(base.position(), eased.position());
        assertEquals(start, eased.position());
        assertEquals(base.predictionDeadlineNanos(), eased.predictionDeadlineNanos());
    }

    @Test
    public void liveControllerToggleAndFullSpeedStagesWorkForSceneAndMinimapClicks()
    {
        for (boolean minimap : new boolean[] {false, true})
        {
            Fixture f = new Fixture(tile(10, 10).getX(), tile(10, 10).getY(), 7000, 7000);
            try
            {
                f.smoothing = 0; f.slowArrival = true; f.arrivalFirstTiles = 2;
                if (minimap) { f.destination = tile(13, 10); f.controller.walkClick(null); }
                else { f.controller.walkClick(); f.destination = tile(13, 10); }
                f.frame(0); f.frame(100);
                int first = f.controller.position().getX() - f.start.getX();
                assertTrue(first > 0 && first < 40);
                f.slowArrival = false;
                int before = f.controller.position().getX(); f.frame(200);
                assertEquals(40, f.controller.position().getX() - before);
                f.slowArrival = true; f.arrivalFirstSpeed = 100; f.arrivalFinalSpeed = 100;
                before = f.controller.position().getX(); f.frame(300);
                assertEquals(40, f.controller.position().getX() - before);
                assertEquals(1, f.speed, 0);
            }
            finally { f.controller.close(); }
        }
    }

    @Test
    public void fiveTileRunEasesEarlyThenKeepsOneWalkPoseThroughTheFinalStep()
    {
        LocalPoint goal = tile(15, 10);
        MovementPath base = fiveTiles(true), eased = fiveTiles(true);
        advance(base, 0, 900, 20, goal, 0, 0, 0);
        advance(eased, 0, 900, 20, goal, 25, 0, 0);
        assertEquals("normal pace until the two-tile approach", base.position(), eased.position());
        assertTrue(eased.presentationRunning());
        advance(base, 900, 1100, 20, goal, 0, 0, 0);
        advance(eased, 900, 1100, 20, goal, 25, 0, 0);
        assertTrue("slows with more than a tile left", eased.position().getX() < tile(14, 10).getX());
        assertTrue(eased.position().getX() < base.position().getX());
        assertTrue(eased.presentationRunning());
        advance(eased, 1100, 1500, 20, goal, 25, 0, 0);
        assertTrue(eased.position().getX() > tile(14, 10).getX());
        assertFalse("walk pose after reaching walking pace", eased.presentationRunning());
        int previous = eased.position().getX();
        int previousStep = 4;
        for (int ms = 1520; ms <= 2100; ms += 20)
        {
            eased.advance(ms * 1_000_000L, false, 0, 0, goal, 25);
            int step = eased.position().getX() - previous;
            assertTrue("no speed jumps in the final walk", step <= previousStep + 1);
            assertTrue(step > 0);
            assertFalse(eased.presentationRunning());
            previous = eased.position().getX(); previousStep = step;
        }
        advance(eased, 2100, 2300, 20, null, 25, 0, 0);
        assertEquals(goal, eased.position());
        assertTrue(eased.finished());
    }

    @Test
    public void fiveTileWalkAndRunCrossBothRampsWithoutCadenceDependentArrival()
    {
        for (boolean run : new boolean[] {false, true})
        {
            LocalPoint goal = tile(15, 10), reference = null;
            int sample = run ? 1700 : 2900;
            for (int cadence : new int[] {8, 20, 33, 100})
            {
                MovementPath path = fiveTiles(run);
                advance(path, 0, sample, cadence, goal, 25, 15, 13);
                if (reference == null) { reference = path.position(); }
                assertEquals(reference, path.position());
                assertTrue(path.clear());
                advance(path, sample, 4500, cadence, null, 25, 15, 13);
                assertEquals(goal, path.position());
                assertTrue(path.finished());
            }
        }
    }

    @Test
    public void fartherRedirectReleasesTheSlowWalkWithoutChangingPosition()
    {
        MovementPath path = fiveTiles(true);
        advance(path, 0, 1500, 20, tile(15, 10), 25, 0, 0);
        assertFalse(path.presentationRunning());
        LocalPoint before = path.position();
        MovementPath next = path.retargetWalk(tile(20, 10), true, 1_500_000_000L);
        assertNotNull(next);
        assertEquals(before, next.position());
        advance(next, 1500, 1520, 20, tile(20, 10), 25, 0, 0);
        assertEquals(8, next.position().getX() - before.getX());
        assertTrue(next.presentationRunning());
        assertTrue(next.clear());
    }

    private static MovementPath fiveTiles(boolean run)
    {
        MovementPath path = MovementPath.idle(tile(10, 10), 0, 1, OPEN);
        for (int x = 10 + (run ? 2 : 1); x <= 15; x += run ? 2 : 1)
        {
            assertTrue(path.accept(tile(x, 10), run));
        }
        if (run) { assertTrue(path.accept(tile(15, 10), true)); }
        return path;
    }

    @Test
    public void threeTileMinimumCountsDiagonalStepsRatherThanEuclideanOrManhattanDistance()
    {
        ArrivalPacing pacing = new ArrivalPacing(1, 1, 50, 30);
        for (boolean preview : new boolean[] {false, true})
        {
            for (int dx : new int[] {-1, 0, 1})
            {
                for (int dy : new int[] {-1, 0, 1})
                {
                    if (dx == 0 && dy == 0) { continue; }
                    for (int tiles : new int[] {1, 2, 3})
                    {
                        LocalPoint goal = tile(10 + dx * tiles, 10 + dy * tiles);
                        MovementPath base = preview ? MovementPath.anticipate(tile(10, 10), tile(10, 10), goal,
                            true, 0, 1, MovementPath.freshDeadline(0), OPEN) : confirmed(goal, true);
                        MovementPath eased = preview ? MovementPath.anticipate(tile(10, 10), tile(10, 10), goal,
                            true, 0, 1, MovementPath.freshDeadline(0), OPEN) : confirmed(goal, true);
                        customAdvance(base, 0, 500, 20, goal, ArrivalPacing.OFF);
                        customAdvance(eased, 0, 500, 20, goal, pacing);
                        if (tiles < 3)
                        {
                            assertEquals(base.position(), eased.position());
                            assertEquals(base.presentationRunning(), eased.presentationRunning());
                            assertTrue(base.traceSnapshot().same(eased.traceSnapshot()));
                        }
                        else
                        {
                            assertTrue("three diagonal/cardinal steps qualify", distance(eased.position(), goal) > distance(base.position(), goal));
                        }
                    }
                }
            }
        }
    }

    @Test
    public void speedsAreIndependentAndEachLengthChangesWhenItsRampStarts()
    {
        LocalPoint goal = tile(15, 10);
        MovementPath first = fiveTiles(true), second = fiveTiles(true);
        customAdvance(first, 0, 1300, 20, goal, new ArrivalPacing(1, 1, 60, 20));
        customAdvance(second, 0, 1300, 20, goal, new ArrivalPacing(1, 1, 60, 45));
        assertEquals("final speed does not alter the first ramp", first.position(), second.position());
        customAdvance(first, 1300, 1700, 20, goal, new ArrivalPacing(1, 1, 60, 20));
        customAdvance(second, 1300, 1700, 20, goal, new ArrivalPacing(1, 1, 60, 45));
        assertTrue(first.position().getX() < second.position().getX());

        MovementPath normal = fiveTiles(true), longerFirst = fiveTiles(true), longerFinal = fiveTiles(true);
        customAdvance(normal, 0, 800, 20, goal, new ArrivalPacing(1, 1, 60, 30));
        customAdvance(longerFirst, 0, 800, 20, goal, new ArrivalPacing(2, 1, 60, 30));
        customAdvance(longerFinal, 0, 800, 20, goal, new ArrivalPacing(1, 2, 60, 30));
        assertTrue(longerFirst.position().getX() < normal.position().getX());
        assertTrue(longerFinal.position().getX() < normal.position().getX());

        ArrivalPacing slowFirst = new ArrivalPacing(1, 1, 40, 30), fastFirst = new ArrivalPacing(1, 1, 70, 30);
        assertEquals("final target is independent of first target", slowFirst.speed(0), fastFirst.speed(0), 1e-12);
        assertEquals(0.3, slowFirst.speed(0), 1e-12);
    }

    @Test
    public void fractionalSkippedAndOversizedStagesStayFrameIndependentAndFinishExactly()
    {
        LocalPoint goal = tile(15, 10);
        for (ArrivalPacing pacing : new ArrivalPacing[] {
            new ArrivalPacing(0.75, 1.5, 65, 25), new ArrivalPacing(0, 2, 50, 30),
            new ArrivalPacing(2, 0, 50, 30), new ArrivalPacing(5, 5, 60, 30),
            new ArrivalPacing(1, 1, 30, 60), new ArrivalPacing(0, 0, 50, 30)})
        {
            LocalPoint reference = null;
            for (int cadence : new int[] {8, 20, 33, 100})
            {
                MovementPath path = fiveTiles(true);
                customAdvance(path, 0, 1500, cadence, goal, pacing);
                if (reference == null) { reference = path.position(); }
                assertEquals(reference, path.position());
                assertTrue(path.clear());
                customAdvance(path, 1500, 8000, cadence, null, pacing);
                assertEquals(goal, path.position());
                assertTrue(path.finished());
            }
        }
        ArrivalPacing fitted = new ArrivalPacing(5, 5, 60, 30).fit(3 * 128);
        assertEquals(1.5 * 128, fitted.firstLength, 0);
        assertEquals(1.5 * 128, fitted.finalLength, 0);
        assertEquals("an oversized approach still starts at full pace", 1, fitted.speed(3 * 128), 1e-12);
        assertTrue(Double.isFinite(new ArrivalPacing(1, 1, 1, 1).millis(256, 0.4, 256)));
    }

    @Test
    public void aNewShortTripDoesNotInheritThePreviousLongTripsEligibility()
    {
        MovementPath path = fiveTiles(true);
        customAdvance(path, 0, 3000, 20, null, new ArrivalPacing(1, 1, 50, 37.5));
        assertTrue(path.finished());
        assertTrue(path.accept(tile(17, 10), true));
        customAdvance(path, 3000, 3100, 20, tile(17, 10), new ArrivalPacing(1, 1, 50, 37.5));
        assertEquals(tile(15, 10).getX() + 40, path.position().getX());
    }

    @Test
    public void confirmedOnlyWalkingCountsATripAcrossIntermediateQueueGaps()
    {
        LocalPoint goal = tile(13, 10);
        MovementPath path = MovementPath.idle(tile(10, 10), 0, 1, OPEN);
        assertTrue(path.accept(tile(11, 10), false));
        customAdvance(path, 0, 640, 20, goal, new ArrivalPacing(1, 1, 50, 30));
        assertTrue(path.finished());
        assertTrue(path.accept(tile(12, 10), false));
        customAdvance(path, 640, 1280, 20, goal, new ArrivalPacing(1, 1, 50, 30));
        assertTrue(path.finished());
        assertTrue(path.accept(goal, false));
        customAdvance(path, 1280, 1380, 20, null, new ArrivalPacing(1, 1, 50, 30));
        assertTrue(path.position().getX() - tile(12, 10).getX() < 20);
        customAdvance(path, 1380, 4000, 20, null, new ArrivalPacing(1, 1, 50, 30));
        assertEquals(goal, path.position());
    }

    private static double distance(LocalPoint a, LocalPoint b)
    {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()));
    }

    private static void customAdvance(MovementPath path, int from, int to, int cadence, LocalPoint goal, ArrivalPacing pacing)
    {
        for (int ms = from + cadence; ms < to; ms += cadence) { path.advance(ms * 1_000_000L, false, 0, 0, goal, pacing); }
        path.advance(to * 1_000_000L, false, 0, 0, goal, pacing);
    }

    private static MovementPath confirmed(LocalPoint goal, boolean run)
    {
        MovementPath path = MovementPath.idle(tile(10, 10), 0, 1, OPEN);
        java.util.List<LocalPoint> route = MovementPath.checkedRoute(tile(10, 10), goal, OPEN, 64);
        assertNotNull(route);
        for (int i = 0; i < route.size(); i += run ? 2 : 1)
        {
            assertTrue(path.accept(route.get(Math.min(route.size() - 1, i + (run ? 1 : 0))), run));
        }
        return path;
    }

    private static void advance(MovementPath path, int from, int to, int cadence, LocalPoint goal,
        int arrival, int catchUp, int ahead)
    {
        for (int ms = from + cadence; ms < to; ms += cadence)
        {
            path.advance(ms * 1_000_000L, false, catchUp, ahead, goal, arrival);
        }
        path.advance(to * 1_000_000L, false, catchUp, ahead, goal, arrival);
    }
}
