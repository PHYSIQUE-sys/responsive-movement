package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import org.junit.Test;
import static org.junit.Assert.*;

/** 2026-10-12 23:10..23:29 bookmarked input/combat failures; native state is an explicit test double. */
public class BookmarkedMovementRegressionTest
{
    private static LocalPoint p(int x, int y) { return new LocalPoint(x, y, 0); }
    private static void advance(MovementPath path, int from, int to)
    {
        for (int ms = from + 10; ms <= to; ms += 10) { path.advance(ms * 1_000_000L); }
    }

    @Test
    public void aForwardButOffRoutePreviousStepCannotDiscardTheNewestWalkAndWaitAtItsOldEndpoint()
    {
        // session=1791807021971 clickSeq=1512 / 11887: new publication then
        // the preceding diagonal endpoint, followed ~600 ms later by the new goal.
        // Square collision is an explicit open-map double.
        int[][] cases = {{7104,6720,7232,6592,7104,6592}, {6976,6592,6848,6720,6720,6592}};
        for (int[] c : cases)
        {
            LocalPoint start = p(c[0], c[1]), old = p(c[2], c[3]), goal = p(c[4], c[5]);
            MovementPath path = MovementPath.anticipate(start, start, old, true, 0, 1.1,
                MovementPath.freshDeadline(0), (a, b) -> true);
            assertNotNull(path);
            advance(path, 0, 560);
            path = path.retargetWalk(goal, true, 560_000_000L);
            assertNotNull(path);
            advance(path, 560, 620);
            LocalPoint before = path.position();
            MovementPath aligned = path.alignWalkAuthority(old, true, 620_000_000L);
            assertNotNull("checked off-route progress must preserve the latest goal", aligned);
            path = aligned;
            assertEquals(before, path.position());
            assertTrue(path.accept(old, true));
            for (int ms = 630; ms <= 1400; ms += 10)
            {
                if (ms == 1220) { assertTrue(path.accept(goal, true)); }
                before = path.position();
                path.advance(ms * 1_000_000L);
                assertTrue(MovementPath.distance(before, path.position()) <= 5);
                if (!path.moving()) { assertEquals("no idle at the preceding endpoint", goal, path.position()); }
                assertTrue(path.clear());
            }
            assertEquals(goal, path.position());
            assertTrue(path.finished());
        }
    }

    @Test
    public void movingMeleeTargetCannotFinishAnObsoleteUnconfirmedLegBeforeReturningToItsNewSide()
    {
        // session=1791807324851 clickSeq=6270: north approach, then target and
        // authority change northwest on the attack frame. The old display went
        // all the way to (6976,6592) before returning to (6848,6464).
        Fixture f = combat(p(6976,6336), p(6976,6720));
        try
        {
            f.click("Attack"); f.frame(0);
            for (int ms = 20; ms <= 360; ms += 20) { f.frame(ms); }
            LocalPoint before = f.controller.position();
            f.area = area(p(6848,6592));
            f.authority = p(6848,6464); f.interacting = f.npc; f.animation = 390;
            for (int ms = 380; ms <= 1400; ms += 20)
            {
                f.frame(ms);
                LocalPoint at = f.controller.position();
                assertTrue("do not finish the superseded northbound forecast", at.getY() <= before.getY());
                assertTrue(MovementPath.distance(before, at) <= 11);
                before = at;
            }
            assertEquals(f.authority, f.controller.position());
            assertEquals("native action clock remains owned by the client", 390, f.animation);
        }
        finally { f.controller.close(); }
    }

    @Test
    public void freshAttackFromACompletedRecoveryStartsWithoutWaitingForAnotherServerStep()
    {
        // clickSeq=42549 remained motionless for ~401 ms solely because its
        // preceding completed recovery still carried recovering=true.
        Fixture f = combat(p(6336,6208), p(6976,6336));
        try
        {
            f.controller.walkClick(); f.destination = p(6848,6208);
            for (int ms = 0; ms <= 2500; ms += 20) { f.frame(ms); }
            assertEquals(f.start, f.controller.position());
            f.destination = null;
            f.click("Attack"); f.frame(2520);
            assertTrue("a new idle attack owns a fresh bounded reserve", f.controller.position().getX() > f.start.getX());
            for (int ms = 2540; ms <= 6000; ms += 20) { f.frame(ms); }
            assertEquals("a rejected new attack still expires and reconciles", f.start, f.controller.position());
        }
        finally { f.controller.close(); }
    }

    @Test
    public void conservativeAttackKeepsItsConfirmedRunReserveUntilTheNextNativeEndpoint()
    {
        // clickSeq=37889: conservative weapon 29796, an unchanged native flag,
        // and ~160 ms idle at the first confirmed pair before authority advances.
        List<MovementTrace.Entry> trace = new ArrayList<>();
        Fixture f = new Fixture(6848,6336,7232,5824,trace);
        try
        {
            settings(f);
            f.weaponId = 29796; f.weaponName = "Noxious halberd"; f.category = 9; f.attackStyle = 1;
            f.click("Attack");
            for (int ms = 0; ms <= 1500; ms += 20)
            {
                if (ms == 120)
                {
                    f.authority = p(6976,6080); f.destination = p(7232,5824);
                    f.area = area(p(7360,5824)); f.interacting = f.npc;
                }
                if (ms == 740)
                {
                    f.authority = p(7232,5824); f.destination = null;
                    f.area = area(p(7488,5824)); f.animation = 440;
                }
                f.frame(ms);
                assertTrue("held run cannot invent a tile beyond authority", ms >= 740 ||
                    f.controller.position().getY() >= 6080);
            }
            assertEquals(f.authority, f.controller.position());
        }
        finally { f.controller.close(); }
        long held = trace.stream().filter(e -> e instanceof MovementTrace.Sample)
            .map(e -> (MovementTrace.Sample) e).filter(s -> s.micros >= 580_000 && s.micros <= 700_000)
            .peek(s -> {
                assertEquals("conservative", s.combatProfile);
                assertEquals("confirmed", s.phase);
                assertTrue("native ongoing route keeps the gait rather than publishing idle", s.moving && s.running);
            }).count();
        assertTrue("capture the gap before the next native step", held >= 5);
    }

    @Test
    public void longObstacleSearchRemainsReversibleAndWithinItsOriginalCollisionBudget()
    {
        LocalPoint start = p(6976,6208), goal = p(6848,10176), blocked = p(6976,6464);
        AtomicInteger calls = new AtomicInteger();
        List<LocalPoint> route = MovementRoute.find(start, goal, 64, (a, b) ->
        {
            calls.incrementAndGet();
            return !a.equals(blocked) && !b.equals(blocked);
        });
        assertNotNull(route);
        assertEquals(31, route.size());
        assertEquals(goal, route.get(route.size() - 1));
        assertFalse(route.contains(blocked));
        assertTrue(calls.get() <= MovementRoute.MAX_VISITED * 16);
        assertNull(MovementRoute.find(start, goal, 30, (a, b) -> true));
        assertNull(MovementRoute.find(start, goal, 64, (a, b) -> b.getY() > a.getY()));
        calls.set(0);
        assertNull(MovementRoute.find(start, goal, 64, (a, b) ->
        {
            calls.incrementAndGet();
            return !a.equals(goal) && !b.equals(goal);
        }));
        assertTrue(calls.get() <= MovementRoute.MAX_VISITED * 16);
    }

    @Test
    public void longObjectSearchCanUseACardinalBoundaryButAnExactItemGoalMustBeReachable()
    {
        LocalPoint start = p(6976,6208), goal = p(6848,10176);
        List<LocalPoint> route = MovementRoute.find(start, goal, 64,
            (a, b) -> !a.equals(goal) && !b.equals(goal), true);
        assertNotNull(route);
        LocalPoint end = route.get(route.size() - 1);
        assertEquals(128, Math.abs(end.getX() - goal.getX()) + Math.abs(end.getY() - goal.getY()));
        assertTrue(route.size() <= 31);
        assertNull(MovementRoute.find(start, goal, 64, (a, b) -> !a.equals(goal) && !b.equals(goal)));
    }

    @Test
    public void longMinimapDetourKeepsABoundedForecastInsteadOfBrakingAtEveryConfirmedPair() throws Exception
    {
        // clickSeq=10275: 31-tile minimap route; native authority runs along
        // x=6848 every ~600 ms. Recorded nearby collision, synthetic open background.
        Fixture f = new Fixture(6976,6208,7000,7000);
        try
        {
            settings(f);
            loadCrop(f);
            LocalPoint goal = p(6848,10176);
            f.destination = goal; f.controller.walkClick((LocalPoint) null); f.frame(0); f.frame(20);
            assertNotEquals("a long checked detour must not exhaust a radial search before starting", f.start, f.controller.position());
            LocalPoint before = f.controller.position();
            for (int ms = 40; ms <= 11000; ms += 20)
            {
                if (ms >= 80)
                {
                    int steps = Math.min(29, ((ms - 80) / 600) * 2);
                    f.authority = p(6848,6464 + steps * 128);
                    if (f.authority.equals(goal)) { f.destination = null; }
                }
                f.frame(ms);
                LocalPoint at = f.controller.position();
                assertTrue("one movement budget", MovementPath.distance(before, at) <= 11);
                if (ms >= 1000 && ms <= 8500)
                {
                    assertTrue("no recurrent confirmed-endpoint brake at " + ms, at.getY() - before.getY() >= 5);
                }
                before = at;
            }
            assertEquals(goal, f.controller.position());
        }
        finally { f.controller.close(); }
    }

    private static Fixture combat(LocalPoint start, LocalPoint npc)
    {
        Fixture f = new Fixture(start.getX(), start.getY(), npc.getX(), npc.getY());
        settings(f);
        f.weaponId = 23332; f.weaponName = "Captured melee weapon"; f.category = 9; f.attackStyle = 1;
        return f;
    }

    private static void settings(Fixture f)
    {
        f.speed = 1.1; f.smoothing = 0; f.catchUp = true; f.catchUpPercent = 15;
        f.slowAhead = true; f.slowAheadPercent = 13; f.originalWhenAligned = false;
        f.faceInteractionsOnArrival = true;
    }

    private static WorldArea area(LocalPoint p)
    {
        return new WorldArea(3200 + p.getSceneX(), 3200 + p.getSceneY(), 1, 1, 0);
    }

    private static void loadCrop(Fixture f) throws Exception
    {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            BookmarkedMovementRegressionTest.class.getResourceAsStream("bookmark-minimap-20261012-crop.txt"), StandardCharsets.UTF_8)))
        {
            for (String line; (line = reader.readLine()) != null;)
            {
                if (line.startsWith("#")) { continue; }
                String[] fields = line.split(" ");
                int y = Integer.parseInt(fields[0]);
                for (int x = 0; x < 9; ++x) { f.flags[50 + x][y] = Integer.parseUnsignedInt(fields[1 + x], 16); }
            }
        }
    }
}
