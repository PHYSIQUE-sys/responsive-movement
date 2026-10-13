package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import java.util.function.BiPredicate;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** Shared native refinement replacing the historical on-route preview trim. */
public class NativeApproachShorteningTest
{
    private static final BiPredicate<LocalPoint, LocalPoint> OPEN = (a, b) -> true;
    private static LocalPoint tile(int x, int y) { return new LocalPoint(x * 128 + 64, y * 128 + 64, 0); }

    @Test
    public void shortenedNativeFlagsKeepControllerProgressAndStopAtTheNewGoalWhileWalkingOrRunning()
    {
        for (boolean run : new boolean[] {false, true})
        {
            LocalPoint start = tile(30, 30), goal = tile(34, 30);
            Fixture f = new Fixture(start.getX(), start.getY(), 7000, 7000);
            f.run = run;
            f.controller.worldInteraction(f.sceneEvent(MenuAction.GROUND_ITEM_THIRD_OPTION, "Take", 233, 36, 32));
            f.destination = tile(36, 32);
            LocalPoint before = start;
            for (int ms = 0; ms <= 3000; ms += 20)
            {
                if (ms == 600) { f.authority = tile(run ? 32 : 31, 30); }
                if (ms == 620) { f.destination = goal; }
                if (ms == 1200) { f.authority = tile(run ? 34 : 32, 30); }
                if (!run && ms == 1800) { f.authority = tile(33, 30); }
                if (!run && ms == 2400) { f.authority = goal; }
                f.frame(ms);
                LocalPoint actual = f.controller.position();
                assertEquals("on-route shortening cannot introduce a sideways detour", start.getY(), actual.getY());
                assertTrue("no reversal toward the old click", actual.getX() >= before.getX());
                assertTrue("never visit the obsolete tail", actual.getX() <= goal.getX());
                assertTrue("one frame budget", MovementPath.distance(before, actual) <= (run ? 8 : 4));
                before = actual;
            }
            assertEquals(goal, f.controller.position());
            f.controller.close();
            assertTrue(f.objects.isEmpty());
        }
    }

    @Test
    public void shorteningKeepsMultipleConfirmedLegsBeforeRebuildingOddOrEvenRunPairs()
    {
        for (boolean run : new boolean[] {false, true})
        {
            for (int confirmedSteps : new int[] {2, 3})
            {
                for (int cut : new int[] {4, 5})
                {
                    LocalPoint start = tile(30, 30), goal = tile(30 + cut, cut == 5 ? 31 : 30);
                    MovementPath path = MovementPath.anticipate(start, start, tile(36, 32), run,
                        0, 1, MovementPath.freshDeadline(0), OPEN);
                    assertNotNull(path);
                    for (int step = 1; step <= confirmedSteps; ++step) { assertTrue(path.accept(tile(30 + step, 30), run)); }
                    advance(path, 0, 100);
                    LocalPoint before = path.position();
                    long hard = path.predictionDeadlineNanos();
                    path = path.retargetApproachDestination(goal, run, 100_000_000L);
                    assertNotNull(path);
                    assertEquals(before, path.position());
                    assertEquals(hard, path.predictionDeadlineNanos());
                    assertEquals(goal, path.clickedDestination());
                    int prefixMillis = confirmedSteps * (run ? 320 : 640);
                    for (int ms = 110; ms <= 3500; ms += 10)
                    {
                        if (!run && confirmedSteps == 2 && ms == 300) { assertTrue(path.accept(tile(33, 30), false)); }
                        if (ms == 600) { assertTrue(path.accept(tile(34, 30), run)); }
                        if (ms == 1200) { assertTrue(path.accept(goal, run)); }
                        before = path.position();
                        path.advance(ms * 1_000_000L);
                        if (ms <= prefixMillis)
                        {
                            assertEquals("finish all confirmed cardinal debt before the new bend", start.getY(), path.position().getY());
                            assertEquals(start.getX() + Math.round(ms * (run ? 0.4 : 0.2)), path.position().getX());
                        }
                        assertTrue("frame budget at " + ms + " run=" + run + " prefix=" + confirmedSteps + " cut=" + cut,
                            MovementPath.distance(before, path.position()) <= (run ? 4 : 2));
                        assertTrue(path.position().getX() >= before.getX());
                        assertTrue(path.position().getX() <= goal.getX());
                        assertTrue(path.clear());
                    }
                    assertEquals(goal, path.position());
                    assertTrue(path.finished());
                }
            }
        }
    }

    @Test
    public void shortenedTailPreservesConfirmedWalkingCornersInsteadOfTakingAShorterConnector()
    {
        LocalPoint start = tile(30, 30), corner = tile(31, 31), authority = tile(32, 30), goal = tile(34, 30);
        MovementPath path = MovementPath.idle(start, 0, 1, OPEN);
        assertTrue(path.accept(corner, false));
        assertTrue(path.accept(authority, false));
        assertTrue(path.anticipateContinuation(tile(36, 32), true, 0));
        advance(path, 0, 100);
        LocalPoint before = path.position();
        path = path.retargetApproachDestination(goal, true, 100_000_000L);
        assertNotNull(path);
        assertEquals(before, path.position());
        assertTrue(path.accept(goal, true));
        advance(path, 100, 640);
        assertEquals(corner, path.position());
        advance(path, 640, 1280);
        assertEquals(authority, path.position());
        advance(path, 1280, 1920);
        assertEquals(goal, path.position());
        assertTrue(path.finished());
        assertTrue(path.clear());
    }

    @Test
    public void shorteningAtTheNpcStagingRingRetainsTheReserveAndDoesNotReleaseTheCloserOldGoal()
    {
        LocalPoint start = tile(30, 30), npc = tile(38, 30), goal = tile(36, 30);
        MovementPath path = MovementPath.anticipate(start, start, tile(37, 30), true, 0, 1,
            MovementPath.freshDeadline(0), OPEN, false, npc, npc, 2);
        assertNotNull(path);
        advance(path, 0, 600);
        assertTrue(path.accept(tile(32, 30), true));
        advance(path, 600, 1000);
        assertTrue(path.accept(tile(34, 30), true));
        LocalPoint before = path.position();
        long hard = path.predictionDeadlineNanos();
        path = path.retargetApproachDestination(goal, true, 1000_000_000L);
        assertNotNull(path);
        assertEquals(before, path.position());
        assertEquals(hard, path.predictionDeadlineNanos());
        assertTrue(path.traceSnapshot().fields(0).contains("npcReserveTiles=2 npcMin=4928,3904 npcMax=4928,3904"));
        for (int ms = 1010; ms <= 2500; ms += 10)
        {
            if (ms == 1500) { assertTrue(path.accept(tile(35, 30), true)); }
            if (ms == 2200) { assertTrue(path.accept(goal, true)); }
            before = path.position();
            path.advance(ms * 1_000_000L);
            assertTrue(MovementPath.distance(before, path.position()) <= 4);
            assertTrue(path.position().getX() <= goal.getX());
            assertTrue(path.clear());
            if (ms >= 1920 && ms < 2200)
            {
                assertEquals("stop at the retained staging ring before final authority", goal, path.position());
                assertEquals("preview", path.phase());
            }
        }
        assertEquals(goal, path.position());
        assertTrue(path.finished());
    }

    @Test
    public void repeatedOnRouteShorteningCannotRenewTheResponseAndStillRecoversToRealAuthority()
    {
        LocalPoint start = tile(30, 30);
        MovementPath path = MovementPath.anticipate(start, start, tile(36, 30), true,
            0, 1, MovementPath.freshDeadline(0), OPEN);
        assertNotNull(path);
        long hard = path.predictionDeadlineNanos();
        int from = 0;
        for (int[] change : new int[][] {{100, 34}, {200, 33}, {500, 32}})
        {
            advance(path, from, change[0]);
            LocalPoint before = path.position();
            path = path.retargetApproachDestination(tile(change[1], 30), true, change[0] * 1_000_000L);
            assertNotNull(path);
            assertEquals(before, path.position());
            assertEquals(hard, path.predictionDeadlineNanos());
            from = change[0];
        }
        advance(path, 500, 890);
        assertEquals("preview", path.phase());
        path.advance(900_000_000L);
        assertEquals("original response deadline", "recovery", path.phase());
        advance(path, 900, 2200);
        assertEquals(start, path.position());
        assertTrue(path.finished());
        assertTrue(path.clear());
    }

    @Test
    public void aNewlyBlockedOrExpiredOnRouteEndpointCannotUseTheOldClippingShortcut()
    {
        LocalPoint start = tile(30, 30), goal = tile(32, 30);
        boolean[] blocked = {false};
        MovementPath path = MovementPath.anticipate(start, start, tile(36, 30), true, 0, 1,
            MovementPath.freshDeadline(0), (a, b) -> !blocked[0] || !a.equals(goal) && !b.equals(goal));
        advance(path, 0, 100);
        LocalPoint before = path.position();
        blocked[0] = true;
        assertNull(path.retargetApproachDestination(goal, true, 100_000_000L));
        assertEquals(before, path.position());
        blocked[0] = false;
        assertNull(path.retargetApproachDestination(goal, true, 900_000_000L));
        assertEquals(before, path.position());
        path.advance(900_000_000L);
        assertEquals("recovery", path.phase());
    }

    private static void advance(MovementPath path, int from, int to)
    {
        for (int ms = from + 10; ms <= to; ms += 10) { path.advance(ms * 1_000_000L); }
    }
}
