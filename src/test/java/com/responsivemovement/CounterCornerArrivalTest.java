package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static com.responsivemovement.GeApproachReplayTest.collision;
import static com.responsivemovement.GeApproachReplayTest.tile;
import static org.junit.Assert.*;

/** Recorded Bank stops at the accessible-side (2,2) corner without publishing a flag. */
public class CounterCornerArrivalTest
{
    @Test
    public void recordedSingleClickStopsAtItsActualCornerWithoutAnExtraPairOrTimeoutReturn() throws Exception
    {
        List<long[]> nativePoints = data().stream().filter(line -> line.startsWith("N "))
            .map(line -> Arrays.stream(line.substring(2).split(" ")).mapToLong(Long::parseLong).toArray())
            .collect(Collectors.toList());
        for (String option : new String[] {"Bank", "Exchange", "Talk-to"})
        {
            for (long cadence : new long[] {8_333, 20_000, 33_333})
            {
                Fixture f = fixture();
                f.click(option);
                TreeSet<Long> times = new TreeSet<>();
                times.add(895L); times.add(734305L);
                for (long[] point : nativePoints) { if (point[0] > 0) { times.add(point[0]); } }
                for (long t = 895; t <= 2_500_000; t += cadence) { times.add(t); }
                int nextNative = 0;
                LocalPoint before = f.start;
                long previousTime = 895;
                int previousAngle = f.controller.orientation();
                for (long t : times)
                {
                    while (nextNative < nativePoints.size() && nativePoints.get(nextNative)[0] <= t)
                    {
                        long[] point = nativePoints.get(nextNative++);
                        f.nativePoint = new LocalPoint((int) point[1], (int) point[2], 0);
                    }
                    if (t >= 734305) { f.authority = tile(49, 63); }
                    frame(f, t);
                    LocalPoint at = f.controller.position();
                    assertEquals("no unsupported diagonal pair: " + option + " at " + t, 6336, at.getX());
                    assertEquals("normal run pace then hold: " + option + " at " + t,
                        Math.min(8128, Math.round(7872 + (t - 895) * 0.000432)), at.getY());
                    assertTrue(at.getY() >= before.getY());
                    assertTrue(MovementPath.distance(before, at) <= Math.ceil((t - previousTime) * 0.000432) + 1);
                    assertTrue(Math.abs(MotionMath.difference(previousAngle, f.controller.orientation())) <=
                        30 * (t - previousTime) / 16667.0 + 1);
                    assertNull(f.destination);
                    before = at; previousTime = t; previousAngle = f.controller.orientation();
                }
                assertEquals(f.authority, f.controller.position());
                f.controller.close();
                assertTrue(f.objects.isEmpty());
            }
        }
    }

    @Test
    public void matchingLateNativeFlagRetainsTheOriginalCounterRunWithoutAnIntermediateStop() throws Exception
    {
        for (long cadence : new long[] {8_333, 20_000, 33_333})
        {
            Fixture f = fixture();
            f.click("Bank");
            TreeSet<Long> times = new TreeSet<>();
            for (long t = 0; t <= 2_000_000; t += cadence) { times.add(t); }
            times.add(200_000L); times.add(734305L); times.add(1_300_000L);
            for (long t : times)
            {
                if (t >= 200_000) { f.destination = tile(50, 65); }
                if (t >= 734305) { f.authority = tile(49, 63); }
                if (t >= 1_300_000) { f.authority = tile(50, 65); f.destination = null; }
                frame(f, t);
                assertCounterPace(f, t);
            }
            assertEquals(f.authority, f.controller.position());
            f.controller.close();
        }
    }

    @Test
    public void immediatelyPublishedNativeCounterGoalAlsoRetainsTheWholeRun() throws Exception
    {
        Fixture f = fixture();
        f.click("Bank"); f.destination = tile(50, 65);
        for (int ms = 0; ms <= 1600; ms += 20)
        {
            if (ms == 300) { f.authority = tile(49, 63); }
            if (ms == 900) { f.authority = tile(50, 65); f.destination = null; }
            f.frame(ms); assertCounterPace(f, ms * 1000L);
        }
        f.controller.close();
    }

    @Test
    public void realOnwardAuthorityWithoutAFlagCanStillCompleteTheCounterRoute() throws Exception
    {
        Fixture f = fixture();
        f.click("Bank");
        for (int ms = 0; ms <= 1800; ms += 20)
        {
            if (ms == 300) { f.authority = tile(49, 63); }
            if (ms == 900) { f.authority = tile(50, 65); }
            f.frame(ms);
            assertTrue("authority remains supported by checked custom presentation", !f.controller.nativeVisible());
        }
        assertEquals(tile(50, 65), f.controller.position());
        f.controller.close();
    }

    @Test
    public void counterCornerPredictionStillTimesOutWithoutAnyServerProgress() throws Exception
    {
        Fixture f = fixture();
        f.click("Bank");
        for (int ms = 0; ms <= 3000; ms += 20)
        {
            f.frame(ms);
            assertEquals(f.start.getX(), f.controller.position().getX());
            assertTrue(f.controller.position().getY() <= tile(49, 63).getY());
        }
        assertEquals(f.start, f.controller.position());
        f.controller.close();
    }

    @Test
    public void cornerReserveUsesTheAccessibleSideAcrossRotatedCountersWithoutNpcIds()
    {
        for (int rotation = 0; rotation < 4; ++rotation)
        {
            LocalPoint npc = tile(52, 52);
            LocalPoint start = rotated(-2, -4, rotation), corner = rotated(-2, -2, rotation);
            Fixture f = new Fixture(start.getX(), start.getY(), npc.getX(), npc.getY());
            f.npcId = 76543;
            f.npcActions = new String[] {"Talk-to", "Exchange"};
            for (int[] offset : new int[][] {{0, 0}, {1, 0}, {0, 1}, {0, -1}})
            {
                LocalPoint at = rotated(offset[0], offset[1], rotation);
                f.flags[at.getSceneX()][at.getSceneY()] = CollisionDataFlag.BLOCK_MOVEMENT_FLOOR;
            }
            for (String option : new String[] {"Bank", "Exchange", "Talk-to"})
            {
                NpcApproach target = NpcApproach.capture(f.client, f.view, f.event(MenuAction.NPC_FIRST_OPTION, option), null);
                NpcApproach.CounterRun plan = target.counterRun(start, collision(f));
                assertNotNull(plan);
                assertEquals(corner, plan.boundaryGoal);
                LocalPoint farSide = rotated(4, -2, rotation);
                NpcApproach.CounterRun around = target.counterRun(farSide, collision(f));
                assertNotNull(around);
                assertNull("a corner behind the solid counter cannot shorten its approach", around.boundaryGoal);
            }
            f.controller.close();
        }
    }

    @Test
    public void aLateFlagCannotRenewPredictionTimeOrReleaseANewlyBlockedOnwardPair() throws Exception
    {
        Fixture f = fixture();
        NpcApproach target = NpcApproach.capture(f.client, f.view, f.event(MenuAction.NPC_THIRD_OPTION, "Bank"), null);
        MovementPath path = MovementPath.anticipateCounterRun(f.start, f.start, target.counterRun(f.start, collision(f)),
            0, 1.1, MovementPath.freshDeadline(0), collision(f), target.min, target.max);
        long hard = path.predictionDeadlineNanos();
        for (int ms = 20; ms <= 800; ms += 20) { path.advance(ms * 1_000_000L); }
        assertEquals(tile(49, 63), path.position());
        f.flags[49][64] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
        assertFalse(path.releaseCounterBoundary(tile(50, 65), 800_000_000L));
        assertTrue(path.clear());
        f.flags[49][64] = 0;
        assertFalse(path.releaseCounterBoundary(tile(50, 64), 800_000_000L));
        assertTrue(path.releaseCounterBoundary(tile(50, 65), 800_000_000L));
        assertEquals(hard, path.predictionDeadlineNanos());
        path.advance(900_000_000L);
        assertEquals("a flag does not grant another response window", "recovery", path.phase());
        for (int ms = 920; ms <= 2400; ms += 20) { path.advance(ms * 1_000_000L); }
        assertEquals(f.start, path.position());
        assertFalse(path.releaseCounterBoundary(tile(50, 65), 2_400_000_000L));
        f.controller.close();
    }

    @Test
    public void queuedCornerReservePreservesAllUnfinishedConfirmedMovement() throws Exception
    {
        Fixture f = fixture();
        NpcApproach target = NpcApproach.capture(f.client, f.view, f.event(MenuAction.NPC_THIRD_OPTION, "Bank"), null);
        MovementPath path = MovementPath.idle(tile(49, 59), 0, 1.1, collision(f));
        assertTrue(path.accept(f.start, true));
        path.advance(100_000_000L);
        LocalPoint before = path.position();
        assertTrue(path.anticipateCounterContinuation(target.counterRun(f.start, collision(f)),
            100_000_000L, target.min, target.max));
        assertEquals(before, path.position());
        for (int ms = 120; ms <= 2000; ms += 20)
        {
            if (ms == 600) { assertTrue(path.accept(tile(49, 63), true)); }
            path.advance(ms * 1_000_000L);
            assertEquals(6336, path.position().getX());
            assertTrue(path.position().getY() >= before.getY());
            assertTrue(path.position().getY() <= 8128);
            before = path.position();
        }
        assertEquals(tile(49, 63), path.position());
        f.controller.close();
    }

    @Test
    public void aMovingWalkHandoffKeepsTheFractionAndTheSameCornerReserve() throws Exception
    {
        for (boolean nativeFlag : new boolean[] {false, true})
        {
            Fixture f = fixture();
            f.controller.walkClick(); f.destination = tile(49, 59);
            f.frame(0); f.frame(100);
            LocalPoint fraction = f.controller.position();
            f.click("Bank"); f.destination = nativeFlag ? tile(50, 65) : null;
            f.frame(100);
            assertEquals("no snap at the red-click fractional join", fraction, f.controller.position());
            for (int ms = 120; ms <= 2400; ms += 20)
            {
                if (ms == 700) { f.authority = tile(49, 63); }
                if (nativeFlag && ms == 1300) { f.authority = tile(50, 65); f.destination = null; }
                f.frame(ms);
                if (!nativeFlag)
                {
                    assertEquals(6336, f.controller.position().getX());
                    assertTrue(f.controller.position().getY() <= 8128);
                }
            }
            assertEquals(f.authority, f.controller.position());
            f.controller.close();
        }
    }

    @Test
    public void rebaseTranslatesTheCornerReserveAndKeepsItsOriginalDeadline() throws Exception
    {
        Fixture f = fixture();
        NpcApproach target = NpcApproach.capture(f.client, f.view, f.event(MenuAction.NPC_THIRD_OPTION, "Bank"), null);
        // Translate the checked crop in the collision double alongside the scene.
        int[] offset = {0, 0};
        java.util.function.BiPredicate<LocalPoint, LocalPoint> check = (a, b) -> collision(f).test(
            new LocalPoint(a.getX() - offset[0], a.getY() - offset[1], 0),
            new LocalPoint(b.getX() - offset[0], b.getY() - offset[1], 0));
        MovementPath path = MovementPath.anticipateCounterRun(f.start, f.start, target.counterRun(f.start, check),
            0, 1.1, MovementPath.freshDeadline(0), check, target.min, target.max);
        path.advance(100_000_000L);
        LocalPoint before = path.position();
        long hard = path.predictionDeadlineNanos();
        offset[0] = 128; offset[1] = -256;
        assertTrue(path.resumeScene(128, -256, tile(50, 59), tile(51, 63), true, 200_000_000L));
        assertEquals(new LocalPoint(before.getX() + 128, before.getY() - 256, 0), path.position());
        assertEquals(hard, path.predictionDeadlineNanos());
        assertTrue(path.traceSnapshot().fields(0).contains("counterBoundaryGoal=6464,7872"));
        for (int ms = 220; ms <= 1600; ms += 20)
        {
            if (ms == 700) { assertTrue(path.accept(tile(50, 61), true)); }
            path.advance(ms * 1_000_000L);
            assertEquals(6464, path.position().getX());
            assertTrue(path.position().getY() <= 7872);
        }
        assertEquals(tile(50, 61), path.position());
        f.controller.close();
    }

    private static LocalPoint rotated(int x, int y, int rotation)
    {
        for (int i = 0; i < rotation; ++i) { int previous = x; x = -y; y = previous; }
        return tile(52 + x, 52 + y);
    }

    private static void assertCounterPace(Fixture f, long micros)
    {
        double distance = micros * 0.000432; // Exact 5.4 setting, rather than the former rounded 5.5.
        double tail = Math.max(0, Math.min(1, (distance - 256) / 256));
        LocalPoint expected = new LocalPoint((int) Math.round(6336 + 128 * tail),
            (int) Math.round(7872 + Math.min(256, distance) + 256 * tail), 0);
        assertTrue("retain the original route/clock at " + micros + "us: " + f.controller.position(),
            MovementPath.distance(expected, f.controller.position()) <= 1);
    }

    static Fixture fixture() throws Exception
    {
        Fixture f = new Fixture(6336, 7872, 6592, 8384);
        f.npcId = 1633; f.npcActions = new String[] {"Talk-to", null, "Bank"};
        f.speed = 5.4 / 5; f.smoothing = 0; f.nativePoint = f.start;
        for (int[] column : f.flags) { Arrays.fill(column, CollisionDataFlag.BLOCK_MOVEMENT_FULL); }
        for (String line : data())
        {
            if (!line.startsWith("M ")) { continue; }
            String[] fields = line.split(" ");
            int x = Integer.parseInt(fields[1]), y = 0;
            for (String run : fields[2].split(","))
            {
                String[] values = run.split(":");
                int length = Integer.parseInt(values[0]), flag = Integer.parseUnsignedInt(values[1], 16);
                for (int end = y + length; y < end; ++y) { f.flags[x][y] = flag; }
            }
            assertEquals(104, y);
        }
        NpcApproach target = NpcApproach.capture(f.client, f.view, f.event(MenuAction.NPC_THIRD_OPTION, "Bank"), null);
        assertTrue(target.counterBank());
        assertTrue(target.atCounterStartBoundary(tile(49, 63)));
        assertNotNull(target.counterRun(f.start, collision(f)));
        return f;
    }

    private static void frame(Fixture f, long micros)
    {
        f.now = 1_000_000_000L + micros * 1000;
        f.cycle = 100 + (int) (micros / 20_000);
        f.controller.update();
    }

    private static List<String> data() throws Exception
    {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            CounterCornerArrivalTest.class.getResourceAsStream("banker-corner-arrival-1925.txt"), StandardCharsets.UTF_8)))
        {
            return reader.lines().filter(line -> !line.startsWith("#") && !line.isEmpty()).collect(Collectors.toList());
        }
    }
}
