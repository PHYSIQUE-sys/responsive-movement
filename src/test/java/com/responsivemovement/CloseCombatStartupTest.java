package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import org.junit.Test;
import static org.junit.Assert.*;

/** Close unconfirmed melee starts from bookmarks #26055/#38255; collision/native motion are explicit doubles. */
public class CloseCombatStartupTest
{
    private static LocalPoint p(int x, int y) { return new LocalPoint(x, y, 0); }

    @Test
    public void bookmarkedNpcMovingIntoReachWithdrawsASmallWalkingReserveInsteadOfAFullRunLunge()
    {
        // Start, clicked NPC, the NPC's new adjacent tile, observed step time (us).
        int[][] cases = {{6976,6080,7104,6208,7104,6080,38_764},
            {6848,5952,6848,5696,6848,5824,295_688}};
        for (int[] c : cases)
        {
            for (long cadence : new long[] {8_333, 20_000, 33_333})
            {
                Fixture f = fixture(p(c[0],c[1]), p(c[2],c[3]), null);
                try
                {
                    f.click("Attack");
                    TreeSet<Long> times = timeline(800_000, cadence, c[6]);
                    LocalPoint previous = f.start;
                    long last = 0;
                    int maximum = 0;
                    for (long us : times)
                    {
                        if (us >= c[6])
                        {
                            move(f, p(c[4],c[5]));
                            if (c[6] > 100_000) { f.interacting = f.npc; f.animation = 4177; }
                        }
                        at(f, us); f.controller.update();
                        LocalPoint actual = f.controller.position();
                        maximum = Math.max(maximum, MovementPath.distance(f.start, actual));
                        assertTrue("single frame movement cap", MovementPath.distance(previous, actual) <=
                            Math.ceil((us - last) * 0.000424) + 1);
                        previous = actual; last = us;
                    }
                    assertTrue("a one-tile unconfirmed start uses at most walk-rate travel before correction: " + maximum,
                        maximum <= Math.ceil(c[6] * 0.000212) + 1);
                    assertEquals("no invented step into the NPC's new footprint", f.start, f.controller.position());
                    assertEquals(c[6] > 100_000 ? 4177 : -1, f.animation);
                }
                finally { f.controller.close(); }
            }
        }
    }

    @Test
    public void genuinePlayerAuthorityReleasesCloseStartEasingAtTheNativeOneTilePace()
    {
        Fixture f = fixture(p(6976,6080), p(7232,6080), null);
        try
        {
            f.catchUp = false;
            f.click("Attack"); f.frame(0);
            for (int ms = 20; ms <= 200; ms += 20) { f.frame(ms); }
            int predicted = f.controller.position().getX() - f.start.getX();
            assertTrue("retain an unconfirmed positional reserve", predicted > 0 && predicted < 43);
            f.authority = p(7104,6080); f.interacting = f.npc;
            LocalPoint previous = f.controller.position();
            for (int ms = 220; ms <= 500; ms += 20)
            {
                f.frame(ms);
                int travel = f.controller.position().getX() - previous.getX();
                assertTrue("confirmed one-tile movement immediately restores normal walk rate", travel >= 4 && travel <= 5);
                previous = f.controller.position();
            }
            for (int ms = 520; ms <= 1000; ms += 20) { f.frame(ms); }
            assertEquals(f.authority, f.controller.position());
        }
        finally { f.controller.close(); }
    }

    @Test
    public void npcStepDuringRecoveryIsRetriedAfterTheFractionSettlesWithoutRenewingEitherDeadline()
    {
        List<MovementTrace.Entry> trace = new ArrayList<>();
        Fixture f = fixture(p(6976,6080), p(7232,6080), trace);
        try
        {
            f.click("Attack"); f.frame(0);
            for (int ms = 20; ms <= 80; ms += 20) { f.frame(ms); }
            move(f, p(7104,6080)); f.frame(100); // NPC now occupies the predicted goal.
            move(f, p(7232,6080)); f.frame(120); // Leaves again before correction finishes.
            for (int ms = 140; ms <= 300; ms += 20) { f.frame(ms); }
            assertTrue("do not lose a real target step and wait for another server update", f.controller.position().getX() > f.start.getX());
            for (int ms = 320; ms <= 1800; ms += 20) { f.frame(ms); }
            assertEquals("unconfirmed continuation still expires at the original response bound", f.start, f.controller.position());
        }
        finally { f.controller.close(); }
        long resumed = trace.stream().filter(e -> e instanceof MovementTrace.Sample)
            .map(e -> (MovementTrace.Sample) e).filter(s -> s.micros >= 120_000 && s.micros <= 300_000 && s.phase.equals("preview"))
            .filter(s -> s.route != null)
            .peek(s -> {
                String fields = s.route.fields(s.started);
                assertTrue(fields, fields.contains("deadlineUs=900000 chainDeadlineUs=1800000"));
            }).count();
        assertTrue("publish the bounded resumed route", resumed > 0);
    }

    @Test
    public void alreadyAdjacentNpcDoesNotCreateAnotherStartupOrRecoverIntoAnOccupiedTile()
    {
        Fixture f = fixture(p(6976,6080), p(7232,6080), null);
        try
        {
            f.click("Attack"); f.frame(0); f.frame(20);
            move(f, p(7104,6080)); f.interacting = f.npc;
            for (int ms = 40; ms <= 2000; ms += 20) { f.frame(ms); }
            assertEquals(f.start, f.controller.position());
            for (int ms = 2020; ms <= 4000; ms += 20) { f.frame(ms); assertEquals(f.start, f.controller.position()); }
        }
        finally { f.controller.close(); }
    }

    @Test
    public void anExpiredOrNewlyBlockedDeferredNpcStepCannotRestartTheOldAttack()
    {
        for (boolean blocked : new boolean[] {false,true})
        {
            Fixture f = fixture(p(6976,6080), p(7232,6080), null);
            try
            {
                f.click("Attack"); f.frame(0);
                for (int ms = 20; ms <= 100; ms += 20) { f.frame(ms); }
                move(f, p(7104,6080)); f.frame(120);
                if (blocked)
                {
                    move(f, p(6976,6336));
                    // Leave the east-west return edge open; all cardinal sides
                    // of the NPC's new footprint are now blocked.
                    for (int[] cell : new int[][] {{54,48},{53,49},{55,49},{54,50}})
                    {
                        f.flags[cell[0]][cell[1]] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
                    }
                    f.frame(140);
                }
                for (int ms = blocked ? 160 : 140; ms <= 1000; ms += 20) { f.frame(ms); }
                if (!blocked) { move(f, p(7232,6080)); }
                for (int ms = 1020; ms <= 3000; ms += 20) { f.frame(ms); }
                assertEquals("blocked or expired target motion cannot rearm the click", f.start, f.controller.position());
            }
            finally { f.controller.close(); }
        }
    }

    private static Fixture fixture(LocalPoint start, LocalPoint npc, List<MovementTrace.Entry> trace)
    {
        Fixture f = new Fixture(start.getX(), start.getY(), npc.getX(), npc.getY(), trace);
        f.speed = 1.06; f.smoothing = 0; f.originalWhenAligned = false;
        f.catchUp = true; f.catchUpPercent = 15; f.slowAhead = true; f.slowAheadPercent = 13;
        f.weaponId = 27690; f.weaponName = "Captured adjacent melee weapon"; f.category = 9; f.attackStyle = 1;
        return f;
    }

    private static void move(Fixture f, LocalPoint npc)
    {
        f.area = new WorldArea(3200 + npc.getSceneX(),3200 + npc.getSceneY(),1,1,0);
    }

    private static void at(Fixture f, long micros)
    {
        f.now = 1_000_000_000L + micros * 1000;
        f.cycle = 100 + (int) (micros / 20_000);
    }

    private static TreeSet<Long> timeline(long finish, long cadence, long event)
    {
        TreeSet<Long> times = new TreeSet<>();
        for (long us = 0; us <= finish; us += cadence) { times.add(us); }
        times.add(event); times.add(finish);
        return times;
    }
}
