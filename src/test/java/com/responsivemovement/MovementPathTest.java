package com.responsivemovement;

import java.util.function.BiPredicate;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

import static org.junit.Assert.*;

public class MovementPathTest
{
    @Test
    public void straightReversalStartsFromTheFractionalVisiblePositionWithoutDrainingOldQueue()
    {
        MovementPath path = MovementPath.idle(tile(10, 10), 0, 1, OPEN);
        assertTrue(path.accept(tile(12, 10), true));
        assertTrue(path.accept(tile(14, 10), true));
        advance(path, 0, 200);
        LocalPoint before = path.position();
        MovementPath reverse = path.retargetWalk(tile(9, 10), true, 200_000_000L);
        assertNotNull(reverse);
        assertEquals(before, reverse.position());
        advance(reverse, 200, 220);
        assertEquals(before.getX() - 8, reverse.position().getX());
        assertEquals(before.getY(), reverse.position().getY());
        assertTrue(reverse.clear());
        assertTrue(reverse.accept(tile(12, 10), true));
        assertTrue(reverse.accept(tile(10, 10), true));
        advance(reverse, 220, 520);
        assertTrue(reverse.accept(tile(9, 10), true));
        advance(reverse, 520, 900);
        assertEquals(tile(9, 10), reverse.position());
        assertTrue(reverse.finished());
    }

    @Test
    public void walkRetargetKeepsThePredictionDeadlineForTurnsForwardClicksAndKnightReturns()
    {
        MovementPath path = MovementPath.idle(tile(10, 10), 0, 1, OPEN);
        assertTrue(path.accept(tile(12, 10), true));
        advance(path, 0, 200);
        for (LocalPoint target : new LocalPoint[] {tile(9, 11), tile(15, 10)})
        {
            MovementPath next = path.retargetWalk(target, true, 200_000_000L);
            assertNotNull(next);
            assertEquals(path.position(), next.position());
            assertEquals(path.confirmed(), next.confirmed());
            assertEquals(target, next.clickedDestination());
            assertEquals(path.predictionDeadlineNanos(), next.predictionDeadlineNanos());
            advance(next, 200, 220);
            assertTrue(distanceSquared(next.position(), target) < distanceSquared(path.position(), target));
            assertTrue(next.clear());
        }
        assertNull(path.retargetWalk(tile(9, 10), true, path.predictionDeadlineNanos()));
        MovementPath chord = MovementPath.idle(tile(10, 10), 0, 1, OPEN);
        assertTrue(chord.accept(tile(12, 11), true));
        advance(chord, 0, 200);
        MovementPath reverse = chord.retargetWalk(tile(9, 10), true, 200_000_000L);
        assertNotNull(reverse);
        assertEquals(chord.position(), reverse.position());
        assertEquals(chord.predictionDeadlineNanos(), reverse.predictionDeadlineNanos());
        advance(reverse, 200, 220);
        assertTrue(distanceSquared(reverse.position(), tile(9, 10)) < distanceSquared(chord.position(), tile(9, 10)));
        assertTrue(reverse.clear());
    }

    @Test
    public void reversalRequiresTheReverseCollisionEdgeToBeOpen()
    {
        MovementPath path = MovementPath.idle(tile(10, 10), 0, 1,
            (a, b) -> b.getX() > a.getX());
        assertTrue(path.accept(tile(12, 10), true));
        advance(path, 0, 200);
        LocalPoint before = path.position();
        assertNull(path.retargetWalk(tile(9, 10), true, 200_000_000L));
        assertEquals(before, path.position());
    }

    @Test
    public void repeatedReversalsRemainImmediateInBothAxesWhenAuthorityContinues()
    {
        for (boolean vertical : new boolean[] {false, true})
        {
            LocalPoint low = tile(10, 10), high = vertical ? tile(10, 14) : tile(14, 10);
            MovementPath path = MovementPath.idle(low, 0, 1, OPEN);
            assertTrue(path.accept(vertical ? tile(10, 12) : tile(12, 10), true));
            advance(path, 0, 400);
            int time = 400;
            for (int i = 0; i < 8; ++i)
            {
                LocalPoint target = i % 2 == 0 ? low : high;
                LocalPoint before = path.position();
                MovementPath next = path.retargetWalk(target, true, time * 1_000_000L);
                assertNotNull(next);
                assertEquals(before, next.position());
                path = next;
                advance(path, time, time + 20);
                assertTrue(distanceSquared(path.position(), target) < distanceSquared(before, target));
                // Model a new confirmed endpoint along the selected direction.
                assertTrue(path.accept(i % 2 == 0 ? low : vertical ? tile(10, 12) : tile(12, 10), true));
                advance(path, time + 20, time + 200);
                time += 200;
                assertTrue(path.clear());
            }
        }
    }

    private static final BiPredicate<LocalPoint, LocalPoint> OPEN = (a, b) -> true;
    private static LocalPoint tile(int x, int y) { return new LocalPoint(x * 128 + 64, y * 128 + 64, 0); }
    private static void advance(MovementPath path, int from, int to)
    {
        for (int ms = from + 10; ms <= to; ms += 10) { path.advance(ms * 1_000_000L); }
    }

    @Test
    public void ordinaryConfirmedMovementUsesTheSameNativeRatePipeline()
    {
        MovementPath path = MovementPath.idle(tile(10, 10), 0, 1, OPEN);
        assertFalse(path.moving());
        assertTrue(path.accept(tile(12, 10), true));
        advance(path, 0, 320);
        assertEquals(tile(11, 10), path.position());
        assertTrue(path.running());
        advance(path, 320, 640);
        assertEquals(tile(12, 10), path.position());
        assertTrue(path.finished());
        assertTrue(path.accept(tile(12, 11), false));
        advance(path, 640, 1280);
        assertEquals(tile(12, 11), path.position());
        assertTrue(path.finished());
    }

    @Test
    public void aConfirmedTwoTileStepRecognizesTemporaryRunOverrides()
    {
        MovementPath path = MovementPath.idle(tile(10, 10), 0, 1, OPEN);
        assertTrue(path.accept(tile(12, 10), false));
        assertTrue(path.running());
        advance(path, 0, 640);
        assertEquals(tile(12, 10), path.position());
    }

    @Test
    public void coldAndCatchupStartsBothMoveBeforeAnotherServerPoint()
    {
        MovementPath cold = MovementPath.anticipate(tile(10, 10), tile(10, 10), tile(14, 10),
            true, 0, 1, MovementPath.freshDeadline(0), OPEN);
        MovementPath catchup = MovementPath.anticipate(tile(11, 10), tile(10, 10), tile(14, 10),
            true, 0, 1, MovementPath.freshDeadline(0), OPEN);
        assertNotNull(cold); assertNotNull(catchup);
        advance(cold, 0, 100); advance(catchup, 0, 100);
        assertEquals(tile(10, 10).getX() + 40, cold.position().getX());
        assertEquals(tile(11, 10).getX() + 40, catchup.position().getX());
        assertEquals(tile(10, 10), catchup.confirmed());
    }

    @Test
    public void allKnightDirectionsAreStraightForBothConfirmedAndAnticipatedMovement()
    {
        int[][] directions = {{2, 1}, {2, -1}, {-2, 1}, {-2, -1}, {1, 2}, {-1, 2}, {1, -2}, {-1, -2}};
        for (int[] direction : directions)
        {
            LocalPoint origin = tile(10, 10), end = tile(10 + direction[0], 10 + direction[1]);
            MovementPath confirmed = MovementPath.idle(origin, 0, 1, OPEN);
            assertTrue(confirmed.accept(end, true));
            MovementPath preview = MovementPath.anticipate(origin, origin, end, true, 0, 1, MovementPath.freshDeadline(0), OPEN);
            for (int ms = 20; ms <= 640; ms += 20)
            {
                confirmed.advance(ms * 1_000_000L); preview.advance(ms * 1_000_000L);
                assertEquals(origin.getX() + direction[0] * ms / 5, confirmed.position().getX());
                assertEquals(origin.getY() + direction[1] * ms / 5, confirmed.position().getY());
                assertEquals(confirmed.position(), preview.position());
                assertEquals(MotionMath.heading(direction[0], direction[1]), MotionMath.heading(preview.turnX(), preview.turnY()));
            }
            assertTrue(confirmed.finished());
            assertFalse(preview.moving());
            assertTrue(preview.accept(end, true));
            assertTrue(preview.finished());
        }
    }

    @Test
    public void blockedKnightCorridorKeepsItsCheckedBend()
    {
        LocalPoint origin = tile(10, 10), end = tile(12, 11);
        BiPredicate<LocalPoint, LocalPoint> blocked = (a, b) -> !(a.equals(origin) && b.equals(tile(11, 11)));
        assertFalse(MovementPath.clearKnight(origin, end, blocked));
        MovementPath path = MovementPath.idle(origin, 0, 1, blocked);
        assertTrue(path.accept(end, true));
        advance(path, 0, 320);
        assertEquals(tile(11, 10), path.position());
        assertTrue(path.clear());
    }

    @Test
    public void adoptedFractionalNativePositionNeverSnapsToATileCentre()
    {
        LocalPoint visible = new LocalPoint(tile(10, 10).getX() + 64, tile(10, 10).getY() + 32, 0);
        MovementPath path = MovementPath.adopt(visible, tile(12, 11), true, 0, 1, OPEN);
        assertNotNull(path);
        assertEquals(visible, path.position());
        advance(path, 0, 100);
        assertEquals(visible.getX() + 40, path.position().getX());
        assertEquals(visible.getY() + 20, path.position().getY());
    }

    @Test
    public void singleTileStartsAndLiveSpeedChangesHavePredictableTiming()
    {
        MovementPath one = MovementPath.anticipate(tile(10, 10), tile(10, 10), tile(11, 10), true,
            0, 1, MovementPath.freshDeadline(0), OPEN);
        advance(one, 0, 320);
        assertEquals(tile(10, 10).getX() + 64, one.position().getX());
        assertFalse(one.running());
        advance(one, 320, 640);
        assertEquals(tile(11, 10), one.position());
        for (double speed : new double[] {0.8, 0.9, 1, 1.1, 1.2, 1.3})
        {
            MovementPath path = MovementPath.idle(tile(10, 10), 0, speed, OPEN);
            assertTrue(path.accept(tile(12, 10), true));
            advance(path, 0, 100);
            assertEquals(tile(10, 10).getX() + Math.round(40 * speed), path.position().getX());
            LocalPoint before = path.position();
            path.speed(0.5);
            assertEquals(before, path.position());
            advance(path, 100, 200);
            assertEquals(before.getX() + 20, path.position().getX());
        }
    }

    @Test
    public void configuredSpeedUsesFiveAsTheFormerNormalPace()
    {
        assertEquals(1.0, MovementPath.configuredSpeed(5.0), 0.000001);
        assertEquals(1.02, MovementPath.configuredSpeed(5.1), 0.000001);
        assertEquals(0.98, MovementPath.configuredSpeed(4.9), 0.000001);
        assertEquals(0.1, MovementPath.configuredSpeed(0.5), 0.000001);
        assertEquals(2.0, MovementPath.configuredSpeed(10.0), 0.000001);
    }

    @Test
    public void fineConfiguredSpeedSurvivesConstructionLiveChangesAndForecasts()
    {
        for (boolean run : new boolean[] {false, true})
        {
            for (double configured : new double[] {4.9, 5.0, 5.1, 5.2, 5.3, 5.4, 5.5})
            {
                double speed = MovementPath.configuredSpeed(configured);
                MovementPath confirmed = MovementPath.idle(tile(10, 10), 0, speed, OPEN);
                assertTrue(confirmed.accept(tile(run ? 12 : 11, 10), run));
                MovementPath preview = MovementPath.anticipate(tile(10, 10), tile(10, 10), tile(14, 10),
                    run, 0, speed, MovementPath.freshDeadline(0), OPEN);
                assertNotNull(preview);
                for (MovementPath path : new MovementPath[] {confirmed, preview})
                {
                    path.advance(100_000_000L);
                    assertEquals(tile(10, 10).getX() + Math.round((run ? 40 : 20) * speed), path.position().getX());
                    LocalPoint before = path.position();
                    path.speed(MovementPath.configuredSpeed(5.1));
                    assertEquals(before, path.position());
                    path.advance(200_000_000L);
                    assertEquals(tile(10, 10).getX() + Math.round((run ? 40 : 20) * (speed + 1.02)), path.position().getX());
                }
            }
        }
        assertEquals(0.1, MovementPath.normalizeSpeed(-1), 0);
        assertEquals(2, MovementPath.normalizeSpeed(100), 0);
        assertEquals(1, MovementPath.normalizeSpeed(Double.NaN), 0);
    }

    @Test
    public void repeatedFramesDoNotSpendTheMovementClockTwice()
    {
        MovementPath path = MovementPath.idle(tile(10, 10), 0, 1, OPEN);
        path.accept(tile(12, 10), true);
        for (int ms = 5; ms <= 500; ms += 5)
        {
            path.advance(ms * 1_000_000L);
            LocalPoint before = path.position();
            path.advance(ms * 1_000_000L);
            assertEquals(before, path.position());
        }
        assertEquals(tile(10, 10).getX() + 200, path.position().getX());
    }

    @Test
    public void queuedConfirmedStepsShareOneFrameBudget()
    {
        MovementPath path = MovementPath.idle(tile(10, 10), 0, 1, OPEN);
        path.accept(tile(12, 10), true); path.accept(tile(14, 10), true); path.accept(tile(16, 10), true);
        advance(path, 0, 640);
        assertEquals(tile(12, 10), path.position());
        advance(path, 640, 1920);
        assertEquals(tile(16, 10), path.position());
        assertTrue(path.finished());
    }

    @Test
    public void sceneRebasePreservesTheRenderedFractionAndRemainingRoute()
    {
        MovementPath path = MovementPath.idle(tile(40, 40), 0, 1, OPEN);
        path.accept(tile(42, 41), true);
        advance(path, 0, 200);
        LocalPoint before = path.position();
        path.rebase(-16 * 128, -8 * 128, 900_000_000L);
        assertEquals(before.getX() - 16 * 128, path.position().getX());
        assertEquals(before.getY() - 8 * 128, path.position().getY());
        path.presented(1000_000_000L);
        advance(path, 1000, 1100);
        assertEquals(before.getX() - 16 * 128 + 40, path.position().getX());
        assertEquals(before.getY() - 8 * 128 + 20, path.position().getY());
    }

    @Test
    public void sceneRecoveryCanConsumeSeveralMissedStepsWithoutAVisualSnap()
    {
        MovementPath path = MovementPath.idle(tile(40, 40), 0, 1, OPEN);
        assertTrue(path.accept(tile(42, 40), true));
        advance(path, 0, 200);
        path.rebase(-16 * 128, 0, 900_000_000L);
        LocalPoint before = path.position();
        assertTrue(path.recover(tile(30, 40), true));
        assertEquals(before, path.position());
        path.presented(1000_000_000L);
        advance(path, 1000, 1100);
        assertEquals(before.getX() + 40, path.position().getX());
        assertEquals(tile(30, 40), path.confirmed());
    }

    @Test
    public void timeoutRecoveryEndsAtRealAuthority()
    {
        MovementPath path = MovementPath.anticipate(tile(11, 10), tile(10, 10), tile(12, 10), true,
            0, 1, MovementPath.freshDeadline(0), OPEN);
        advance(path, 0, 640);
        assertEquals(tile(12, 10), path.position());
        advance(path, 640, 2200);
        assertEquals(tile(10, 10), path.position());
        assertTrue(path.finished());
    }

    @Test
    public void replacementWalkStopsAtTheConfirmedCornerNotTheOldForecast()
    {
        MovementPath path = MovementPath.anticipate(tile(10, 10), tile(10, 10), tile(11, 13), true,
            0, 1, MovementPath.freshDeadline(0), OPEN);
        advance(path, 0, 300); path.accept(tile(10, 12), true);
        advance(path, 300, 600); path.replacement(true);
        advance(path, 600, 640);
        assertEquals(tile(10, 12), path.position());
        assertTrue(path.finished());
    }

    @Test
    public void captureBasedRoutesRetainContinuousForwardProgress()
    {
        replay(new LocalPoint(7232, 7744, 0), new LocalPoint(6720, 7744, 0),
            new int[] {730, 1320}, new LocalPoint[] {new LocalPoint(6976, 7744, 0), new LocalPoint(6720, 7744, 0)}, 1400);
        replay(new LocalPoint(5696, 6592, 0), new LocalPoint(6208, 6848, 0),
            new int[] {520, 1120}, new LocalPoint[] {new LocalPoint(5952, 6592, 0), new LocalPoint(6208, 6848, 0)}, 1400);
        replay(new LocalPoint(6080, 6592, 0), new LocalPoint(6720, 7104, 0), new int[] {360, 960, 1560},
            new LocalPoint[] {new LocalPoint(6336, 6720, 0), new LocalPoint(6592, 6976, 0), new LocalPoint(6720, 7104, 0)}, 1650);
        replay(tile(10, 10), tile(20, 10), new int[] {720, 1320, 1920, 2520, 3120},
            new LocalPoint[] {tile(12, 10), tile(14, 10), tile(16, 10), tile(18, 10), tile(20, 10)}, 3250);
    }

    private static void replay(LocalPoint origin, LocalPoint end, int[] times, LocalPoint[] endpoints, int finish)
    {
        MovementPath path = MovementPath.anticipate(origin, origin, end, true, 0, 1, MovementPath.freshDeadline(0), OPEN);
        int next = 0;
        for (int ms = 10; ms <= finish; ms += 10)
        {
            LocalPoint before = path.position();
            if (next < times.length && ms == times[next])
            {
                assertTrue(path.accept(endpoints[next++], true));
                assertEquals(before, path.position());
            }
            path.advance(ms * 1_000_000L);
            LocalPoint after = path.position();
            if (!before.equals(end)) { assertTrue("pause/reversal at " + ms, distanceSquared(after, end) < distanceSquared(before, end)); }
            assertTrue(Math.abs(after.getX() - before.getX()) <= 4);
            assertTrue(Math.abs(after.getY() - before.getY()) <= 4);
        }
        assertEquals(end, path.position()); assertTrue(path.finished());
    }

    @Test
    public void confirmedDiagonalReturnKeepsItsEndpointBeforeTheNewStraightLeg()
    {
        for (int dx : new int[] {-1, 1})
        {
            for (int dy : new int[] {-1, 1})
            {
                LocalPoint start = tile(10, 10), bend = tile(10 + dx, 10 + dy), end = tile(10 + 2 * dx, 10);
                MovementPath path = MovementPath.idle(start, 0, 1, OPEN);
                assertTrue(path.accept(bend, false));
                advance(path, 0, 640);
                path.replacement(true);
                assertTrue(path.accept(end, false));
                advance(path, 640, 1260);
                path.replacement(true);
                LocalPoint before = path.position();
                assertTrue(path.accept(start, true));
                assertEquals(before, path.position());
                advance(path, 1260, 1280);
                assertEquals("finish the confirmed diagonal, not its old starting tile", end, path.position());
                advance(path, 1280, 1600);
                assertEquals(tile(10 + dx, 10), path.position());
                advance(path, 1600, 1920);
                assertEquals(start, path.position());
                assertTrue(path.finished());
            }
        }
    }

    @Test
    public void replacementPreservesMultipleConfirmedLegsInOrder()
    {
        MovementPath path = MovementPath.idle(tile(10, 10), 0, 1, OPEN);
        assertTrue(path.accept(tile(11, 11), false));
        assertTrue(path.accept(tile(12, 10), false));
        advance(path, 0, 200);
        path.replacement(true);
        assertTrue(path.accept(tile(10, 10), true));
        advance(path, 200, 640);
        assertEquals(tile(11, 11), path.position());
        advance(path, 640, 1280);
        assertEquals(tile(12, 10), path.position());
        advance(path, 1280, 1920);
        assertEquals(tile(10, 10), path.position());
        assertTrue(path.finished());
    }

    @Test
    public void replacementStillCorrectsAnUnconfirmedPreview()
    {
        MovementPath path = MovementPath.anticipate(tile(10, 10), tile(10, 10), tile(14, 10), true,
            0, 1, MovementPath.freshDeadline(0), OPEN);
        advance(path, 0, 400);
        LocalPoint before = path.position();
        path.replacement(true);
        assertTrue(path.accept(tile(10, 12), true));
        assertEquals(before, path.position());
        for (int ms = 410; ms <= 1800; ms += 10)
        {
            path.advance(ms * 1_000_000L);
            assertTrue(path.position().getX() <= before.getX());
            assertTrue(path.clear());
        }
        assertEquals(tile(10, 12), path.position());
        assertTrue(path.finished());
    }

    @Test
    public void adjacentBlockedDiagonalAcceptsOnlyTheLegalTwoStepBend()
    {
        // Includes the recorded (8128,8256) -> (8000,8128) corner and mirrors.
        LocalPoint start = new LocalPoint(8128, 8256, 0);
        for (int dx : new int[] {-128, 128})
        {
            for (int dy : new int[] {-128, 128})
            {
                LocalPoint end = new LocalPoint(start.getX() + dx, start.getY() + dy, 0);
                for (boolean xFirst : new boolean[] {false, true})
                {
                    LocalPoint bend = new LocalPoint(xFirst ? end.getX() : start.getX(),
                        xFirst ? start.getY() : end.getY(), 0);
                    BiPredicate<LocalPoint, LocalPoint> corridor = (a, b) ->
                        a.equals(start) && b.equals(bend) || a.equals(bend) && b.equals(end);
                    MovementPath path = MovementPath.idle(start, 0, 1, corridor);
                    assertTrue(path.accept(end, true));
                    assertTrue(path.clear());
                    advance(path, 0, 320);
                    assertEquals(bend, path.position());
                    advance(path, 320, 640);
                    assertEquals(end, path.position());
                    assertTrue(path.finished());
                    assertNull(MovementPath.checkedRoute(start, end, corridor, 1));
                    assertNull(MovementPath.anticipate(start, start, end, true, 0, 1,
                        MovementPath.freshDeadline(0), corridor));
                }
            }
        }
    }

    @Test
    public void cornerCorrectionDoesNotInventLongerDetoursOrIgnoreBlockedEdges()
    {
        LocalPoint start = tile(10, 10), end = tile(11, 11);
        assertNull(MovementPath.checkedRoute(start, end, (a, b) -> a.equals(start) && !b.equals(end), 2));
        assertNull(MovementPath.checkedRoute(start, end, (a, b) -> b.equals(end) && !a.equals(start), 2));
        assertNull(MovementPath.checkedRoute(start, tile(12, 10),
            (a, b) -> a.getY() != b.getY(), 2));
    }

    private static long distanceSquared(LocalPoint a, LocalPoint b)
    {
        long x = a.getX() - b.getX(), y = a.getY() - b.getY();
        return x * x + y * y;
    }

    @Test
    public void interactionBeginningDropsPreviewBeyondItsConfirmedApproachTile()
    {
        MovementPath path = MovementPath.anticipate(tile(10, 10), tile(10, 10), tile(16, 10), true,
            0, 1, MovementPath.freshDeadline(0), OPEN);
        advance(path, 0, 200);
        assertTrue(path.accept(tile(12, 10), true));
        LocalPoint before = path.position();
        path.cancel();
        path.replacement(false);
        assertEquals(before, path.position());
        advance(path, 200, 2000);
        assertEquals(tile(12, 10), path.position());
        assertTrue(path.finished());
    }
}
