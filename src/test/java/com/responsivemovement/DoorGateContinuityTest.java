package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import java.util.Arrays;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** Gate 1559 capture geometry/timings; closing collision is an explicit API double. */
public class DoorGateContinuityTest
{
    @Test
    public void closeFromTheNorthDoesNotPreviewTheGatesFarSide()
    {
        // 12:44:39.519 +11:00: authority/display=9536, native=9483,
        // Close publishes 9408 then closes at the old native-fallback frame.
        for (int cadence : new int[] {8, 20, 33})
        {
            Fixture f = gate(9536);
            f.nativePoint = p(9483);
            f.controller.worldInteraction(f.sceneEvent(MenuAction.GAME_OBJECT_FIRST_OPTION, "Close", 1559, 30, 73));
            f.destination = p(9408);
            for (int ms = 0; ms <= 1000; ms += cadence)
            {
                if (ms >= 221) { f.destination = null; close(f); }
                f.nativePoint = p(Math.min(9536, 9483 + ms / 5));
                f.frame(ms);
                assertEquals("do not manufacture a trip through the closing gate", p(9536), f.controller.position());
            }
            f.controller.close();
            assertTrue(f.objects.isEmpty());
        }
    }

    @Test
    public void recordedTwoTileCloseKeepsItsNearSideConfirmedStopAtNormalPace()
    {
        // 12:43:55.859: display=9664; goal=9408; real authority stops at
        // 9536 after 661 ms. The old preview reaches 9408 then jumps to 9603.
        for (int cadence : new int[] {8, 20, 33})
        {
            Fixture f = gate(9664);
            f.controller.worldInteraction(f.sceneEvent(MenuAction.GAME_OBJECT_FIRST_OPTION, "Close", 1559, 30, 73));
            f.destination = p(9408);
            LocalPoint previous = f.start;
            for (int ms = 0; ms <= 2000; ms += cadence)
            {
                if (ms >= 661) { f.authority = p(9536); f.destination = null; }
                if (ms >= 1262) { close(f); }
                f.nativePoint = ms < 661 ? p(9664) : p(Math.max(9536, 9664 - (ms - 661) / 5));
                f.frame(ms);
                LocalPoint actual = f.controller.position();
                assertTrue("never cross the real stopping boundary", actual.getY() >= 9536);
                assertTrue("no native-position jump", MovementPath.distance(previous, actual) <= Math.ceil(cadence * 0.44) + 1);
                assertTrue("no return itinerary", actual.getY() <= previous.getY());
                previous = actual;
            }
            assertEquals(p(9536), f.controller.position());
            f.controller.close();
        }
    }

    @Test
    public void closeQueuesOnlyToTheNearSideBehindAnUnfinishedConfirmedWalk()
    {
        // 12:43:39.578: the preceding confirmed run has ~3 units left at 9664;
        // actual Close authority stops at 9536 after 143 ms, not flag 9408.
        Fixture f = gate(9408);
        f.authority = p(9664);
        for (int ms = 20; ms <= 580; ms += 20) { f.frame(ms); }
        LocalPoint before = f.controller.position();
        assertTrue(before.getY() > 9640);
        f.controller.worldInteraction(f.sceneEvent(MenuAction.GAME_OBJECT_FIRST_OPTION, "Close", 1559, 30, 73));
        f.destination = p(9408);
        f.frame(582);
        assertEquals("retain the confirmed endpoint before the approach", p(9664), f.controller.position());
        for (int ms = 600; ms <= 1800; ms += 20)
        {
            if (ms >= 740) { f.authority = p(9536); f.destination = null; }
            if (ms >= 1340) { close(f); }
            f.frame(ms);
            assertTrue("no unsupported last tile", f.controller.position().getY() >= 9536);
        }
        assertEquals(p(9536), f.controller.position());
        f.controller.close();
    }

    @Test
    public void aCloseAtTheOccupiedBoundaryDoesNotReverseBeforeNewCrossingAuthority()
    {
        // 12:45:15.081: new Close flag equals old authority 9408 while the
        // previous Walk is ~52 units toward 9536. Its real step arrives 60 ms later.
        Fixture f = gate(9408);
        f.controller.walkClick(); f.destination = p(9536);
        for (int ms = 0; ms <= 240; ms += 20) { f.frame(ms); }
        LocalPoint before = f.controller.position();
        f.controller.worldInteraction(f.sceneEvent(MenuAction.GAME_OBJECT_FIRST_OPTION, "Close", 1559, 30, 73));
        f.destination = p(9408);
        for (int ms = 260; ms <= 1200; ms += 20)
        {
            if (ms >= 300) { f.authority = p(9536); f.destination = null; }
            f.frame(ms);
            assertTrue("do not return to the gate's construction anchor", f.controller.position().getY() >= before.getY());
            before = f.controller.position();
        }
        assertEquals(p(9536), f.controller.position());
        f.controller.close();
    }

    @Test
    public void nonBoundaryOptionsAndUnmatchedObjectsKeepTheirExistingGoals()
    {
        Fixture f = gate(9536);
        for (String option : new String[] {"Search", "Use", "Climb-over"})
        {
            assertNull(ObjectApproach.capture(f.view,
                f.sceneEvent(MenuAction.GAME_OBJECT_FIRST_OPTION, option, 1559, 30, 73)));
        }
        assertNull(ObjectApproach.capture(f.view,
            f.sceneEvent(MenuAction.GAME_OBJECT_FIRST_OPTION, "Close", 999, 30, 73)));
        f.controller.close();
    }

    @Test
    public void aCloseRetiresAnUnchangedWalkFlagBeforeTheGateCloses()
    {
        // Final 12:45:20.281 click: Walk flag=9408 never changes for Close,
        // authority remains 9536. Recover its obsolete fractional preview now,
        // rather than finishing the whole tile and snapping 128 units later.
        Fixture f = gate(9536);
        f.controller.walkClick(); f.destination = p(9408);
        for (int ms = 0; ms <= 340; ms += 20) { f.frame(ms); }
        LocalPoint before = f.controller.position();
        assertTrue(before.getY() < 9480);
        f.controller.worldInteraction(f.sceneEvent(MenuAction.GAME_OBJECT_FIRST_OPTION, "Close", 1559, 30, 73));
        for (int ms = 360; ms <= 900; ms += 20)
        {
            f.frame(ms);
            assertTrue("do not continue the obsolete Walk through the wall", f.controller.position().getY() >= before.getY());
            assertTrue("checked recovery keeps one movement budget", MovementPath.distance(before, f.controller.position()) <= 5);
            before = f.controller.position();
        }
        assertEquals(p(9536), f.controller.position());
        f.controller.close();
    }

    @Test
    public void lateHingeRefinementKeepsTheSamePerimeterStoppingRule()
    {
        Fixture f = gate(9920);
        f.controller.worldInteraction(f.sceneEvent(MenuAction.GAME_OBJECT_FIRST_OPTION, "Close", 1559, 30, 73));
        f.destination = p(9664);
        f.frame(0); f.frame(100);
        f.destination = p(9408);
        for (int ms = 120; ms <= 1600; ms += 20)
        {
            if (ms == 400) { f.authority = p(9664); }
            if (ms == 1000) { f.authority = p(9536); f.destination = null; }
            f.frame(ms);
            assertTrue("refinement cannot restore the unsupported far-side goal", f.controller.position().getY() >= 9536);
        }
        assertEquals(p(9536), f.controller.position());
        f.controller.close();
    }

    @Test
    public void finalCapturedCloseLeavesOnlyASmallNativeHandoffIfTheOccupiedEdgeClosesEarly()
    {
        // 12:45:20.281..20.539: unchanged Walk flag, no server crossing.
        // Collision is not recorded each frame: closing at the observed fallback
        // time is an explicit double using the later closed-gate map evidence.
        for (int cadence : new int[] {8, 20, 33})
        {
            Fixture f = gate(9536);
            f.controller.walkClick(); f.destination = p(9408);
            for (int ms = 0; ms < 340; ms += cadence) { f.frame(ms); }
            f.frame(340);
            f.controller.worldInteraction(f.sceneEvent(MenuAction.GAME_OBJECT_FIRST_OPTION, "Close", 1559, 30, 73));
            LocalPoint previous = f.controller.position();
            for (int ms = 340 + cadence; ms <= 1400; ms += cadence)
            {
                if (ms >= 598) { f.destination = null; close(f); }
                f.frame(ms);
                LocalPoint actual = f.controller.position();
                assertTrue("do not finish the obsolete crossing", actual.getY() >= previous.getY());
                int allowance = ms >= 598 ? 24 : (int) Math.ceil(cadence * 0.22) + 1;
                assertTrue("avoid the recorded full-tile snap", MovementPath.distance(previous, actual) <= allowance);
                previous = actual;
            }
            assertEquals(p(9536), f.controller.position());
            f.controller.close();
        }
    }

    @Test
    public void nativeDestinationsOutsideTheWallRemainExactAndBlockedNearSidesUseCheckedPerimeters()
    {
        Fixture f = gate(9536);
        ObjectApproach wall = ObjectApproach.capture(f.view,
            f.sceneEvent(MenuAction.GAME_OBJECT_FIRST_OPTION, "<col=ffff00>Open</col>", 1559, 30, 73));
        assertNotNull(wall);
        assertEquals(p(9664), wall.goal(f.start, p(9664)));
        f.controller.close();

        f = gate(9664);
        f.flags[30][74] |= CollisionDataFlag.BLOCK_MOVEMENT_FULL;
        f.controller.worldInteraction(f.sceneEvent(MenuAction.GAME_OBJECT_FIRST_OPTION, "Close", 1559, 30, 73));
        f.destination = p(9408);
        f.frame(0); f.frame(20);
        assertNotEquals("a blocked nearest tile does not exclude every reachable side", f.start, f.controller.position());
        for (int ms = 40; ms <= 3000; ms += 20)
        {
            f.frame(ms);
            assertNotEquals("never speculate into the wall anchor", p(9408), f.controller.position());
        }
        assertEquals("unconfirmed detour still expires", f.start, f.controller.position());
        f.controller.close();
    }

    private static Fixture gate(int y)
    {
        Fixture f = new Fixture(3904, y, 7000, 7000);
        f.speed = 1.1; f.smoothing = 0;
        // Exact clicked crop X=28..32/Y=71..77 from 12:43:39.578,
        // identical in the other Close captures. Unknown cells stay blocked.
        for (int[] column : f.flags) { Arrays.fill(column, CollisionDataFlag.BLOCK_MOVEMENT_FULL); }
        for (int x = 28; x <= 32; ++x) { Arrays.fill(f.flags[x], 71, 78, 0); }
        f.flags[28][73] = 0x2; f.flags[28][74] = 0x20;
        f.flags[30][72] = f.flags[30][73] = 0x1008;
        f.flags[31][72] = 0x10080; f.flags[31][73] = 0x10082;
        f.flags[31][74] = 0x20; f.flags[32][74] = 0x100;
        f.wall(1559, 30, 73);
        return f;
    }

    private static void close(Fixture f)
    {
        f.flags[30][73] |= CollisionDataFlag.BLOCK_MOVEMENT_NORTH;
        f.flags[30][74] |= CollisionDataFlag.BLOCK_MOVEMENT_SOUTH;
    }

    private static LocalPoint p(int y) { return new LocalPoint(3904, y, 0); }
}
