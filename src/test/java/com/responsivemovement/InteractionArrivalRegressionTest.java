package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class InteractionArrivalRegressionTest
{
    private static LocalPoint p(int x, int y) { return new LocalPoint(x, y, 0); }
    private static LocalPoint tile(int x, int y) { return p(x * 128 + 64, y * 128 + 64); }

    @Test
    public void objectFootprintFlagsUseTheCheckedApproachTileForSlowdownAndWalkingPose()
    {
        Fixture f = arrivalFixture();
        try
        {
            f.object(100, tile(15, 10), tile(15, 11));
            f.flags[15][10] = net.runelite.api.CollisionDataFlag.BLOCK_MOVEMENT_OBJECT;
            f.flags[15][11] = net.runelite.api.CollisionDataFlag.BLOCK_MOVEMENT_OBJECT;
            f.controller.worldInteraction(f.sceneEvent(MenuAction.GAME_OBJECT_FIRST_OPTION, "Use", 100, 15, 10));
            f.destination = tile(15, 10);
            f.frame(0);
            boolean sawSlowWalk = false;
            for (int ms = 20; ms <= 1180; ms += 20)
            {
                if (ms == 600) { f.authority = tile(12, 10); }
                LocalPoint before = f.controller.position();
                f.frame(ms);
                int remaining = tile(14, 10).getX() - f.controller.position().getX();
                if (remaining > 8 && remaining < 110)
                {
                    int step = f.controller.position().getX() - before.getX();
                    assertTrue("the object approach must use arrival pace, not full run pace at " + ms + " remaining=" + remaining + " step=" + step, step <= 6);
                    assertEquals("walk pose at walking-speed arrival", Integer.valueOf(819), f.poses.get("PoseAnimation"));
                    sawSlowWalk = true;
                }
            }
            assertTrue("exercise the slow final approach before confirmation", sawSlowWalk);
        }
        finally { f.controller.close(); }
    }

    @Test
    public void finalNpcAndObjectAuthorityKeepsArrivalPacingWhenTheNativeFlagClears()
    {
        for (boolean npc : new boolean[] {false, true})
        {
            Fixture f = arrivalFixture();
            try
            {
                if (npc) { f.click("Talk-to"); }
                else
                {
                    f.object(100, tile(15, 10), tile(15, 11));
                    f.flags[15][10] = net.runelite.api.CollisionDataFlag.BLOCK_MOVEMENT_OBJECT;
                    f.flags[15][11] = net.runelite.api.CollisionDataFlag.BLOCK_MOVEMENT_OBJECT;
                    f.controller.worldInteraction(f.sceneEvent(MenuAction.GAME_OBJECT_FIRST_OPTION, "Use", 100, 15, 10));
                }
                f.destination = tile(14, 10); f.frame(0);
                boolean slowAfterConfirmation = false;
                for (int ms = 20; ms <= 2400; ms += 20)
                {
                    if (ms == 600) { f.authority = tile(12, 10); }
                    if (ms == 1200) { f.authority = tile(14, 10); f.destination = null; }
                    LocalPoint before = f.controller.position();
                    f.frame(ms);
                    int remaining = tile(14, 10).getX() - f.controller.position().getX();
                    if (ms >= 1200 && remaining > 4 && remaining < 100)
                    {
                        int step = f.controller.position().getX() - before.getX();
                        assertTrue("confirmed arrival must not become a tiny publication reserve", step >= 3 && step <= 6);
                        assertEquals(Integer.valueOf(819), f.poses.get("PoseAnimation"));
                        slowAfterConfirmation = true;
                    }
                    assertTrue(f.controller.position().getX() <= tile(14, 10).getX());
                }
                assertTrue("exercise visible movement after the final native endpoint", slowAfterConfirmation);
                assertEquals(tile(14, 10), f.controller.position());
            }
            finally { f.controller.close(); }
        }
    }

    @Test
    public void confirmedPublicationReserveUsesWalkingPoseWhenItIsBelowWalkingSpeed()
    {
        MovementPath path = MovementPath.idle(tile(10, 10), 0, 1.18, (a, b) -> true);
        assertTrue(path.accept(tile(12, 10), true));
        for (int ms = 20; ms <= 500; ms += 20) { path.advance(ms * 1_000_000L); }
        path.advance(520_000_000L, true, 15, 13, tile(18, 10), ArrivalPacing.OFF);
        assertTrue(path.moving());
        assertFalse("do not run in place over a small retained confirmed remainder", path.presentationRunning());
        assertTrue(path.accept(tile(14, 10), true));
        path.advance(540_000_000L, true, 15, 13, tile(18, 10), ArrivalPacing.OFF);
        assertTrue("new full-pair authority can restore running pace", path.presentationRunning());
    }

    @Test
    public void bookmarkedMovingAttackDoesNotAppendTheOldWalkPreviewBehindItsOccupiedKnight()
    {
        // #499 / click 115: occupied knight (7616,5184)->(7360,5312),
        // authority (7488,5312) lies behind the display inside that corridor.
        // Synthetic collision restricts the other three NPC sides to reproduce
        // the captured south-side goal; it is not a full collision-map replay.
        java.util.function.BiPredicate<LocalPoint, LocalPoint> collision = (a, b) ->
            clearNpcSide(a) && clearNpcSide(b);
        MovementPath path = MovementPath.anticipate(p(7616,5184), p(7616,5184), p(7360,5312), true,
            0, 1.18, MovementPath.freshDeadline(0), collision);
        for (int ms = 10; ms <= 450; ms += 10) { path.advance(ms * 1_000_000L); }
        path = path.retargetWalk(p(6592,5952), true, 450_000_000L);
        assertNotNull(path);
        path = path.alignWalkAuthority(p(7488,5312), true, 450_000_000L);
        assertNotNull(path);
        assertTrue(path.accept(p(7488,5312), true));
        assertTrue(path.position().getX() < path.confirmed().getX());
        assertTrue(path.traceSnapshot().fields(0), path.traceSnapshot().fields(0).contains("7616,5184,7360,5312"));

        Fixture f = new Fixture(7488,5312,6080,6336);
        try
        {
            f.weaponId = 27690; f.weaponName = "Captured adjacent melee weapon"; f.category = 9; f.attackStyle = 1;
            NpcApproach target = NpcApproach.capture(f.client, f.view, f.event(MenuAction.NPC_SECOND_OPTION, "Attack"), null);
            assertNotNull(target);
            path.replacement(true); path.armCombat(450_000_000L);
            long hard = path.predictionDeadlineNanos();
            LocalPoint before = path.position();
            path = path.followCombat(target, true, 450_000_000L);
            assertNotNull(path);
            assertEquals(before, path.position());
            assertEquals(hard, path.predictionDeadlineNanos());
            String route = path.traceSnapshot().fields(0);
            assertFalse("the superseded Walk bend must not precede the NPC itinerary: " + route,
                route.contains("7104,5440,6976,5568"));
            for (int ms = 460; ms <= 900; ms += 10)
            {
                before = path.position(); path.advance(ms * 1_000_000L);
                assertTrue("the new NPC route must not loop east through the old origin", path.position().getX() <= before.getX());
                assertTrue(MovementPath.distance(before, path.position()) <= 6);
                assertTrue(path.clear());
            }
        }
        finally { f.controller.close(); }
    }

    private static Fixture arrivalFixture()
    {
        Fixture f = new Fixture(tile(10, 10).getX(), tile(10, 10).getY(), tile(15, 10).getX(), tile(15, 10).getY());
        f.poses.put("IdlePoseAnimation", 808); f.poses.put("WalkAnimation", 819); f.poses.put("RunAnimation", 824);
        f.poses.put("PoseAnimation", 808); f.poses.put("PoseAnimationFrame", 0);
        f.speed = 1.18; f.smoothing = 0; f.originalWhenAligned = false;
        f.catchUp = true; f.catchUpPercent = 15; f.slowAhead = true; f.slowAheadPercent = 13;
        f.slowArrival = true; f.arrivalFinalSpeed = 49.8;
        return f;
    }

    private static boolean clearNpcSide(LocalPoint at)
    {
        return !at.equals(p(6208,6336)) && !at.equals(p(5952,6336)) && !at.equals(p(6080,6464));
    }
}
