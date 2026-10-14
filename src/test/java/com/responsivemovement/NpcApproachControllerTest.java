package com.responsivemovement;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Animation;
import net.runelite.api.IterableHashTable;
import net.runelite.api.Renderable;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.GameState;
import net.runelite.api.GameObject;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.RuneLiteObjectController;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.StructComposition;
import net.runelite.api.WorldView;
import net.runelite.api.WallObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.VarPlayerID;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real controller with API doubles and a deterministic clock; no game input or renderer. */
public class NpcApproachControllerTest
{
    @Test
    public void recordedBankerDelayCanStartBeforeAuthorityWithoutAnyDestination()
    {
        // Recorded start/first authority and 583 ms delay; NPC geometry is synthetic
        // because the old trace did not record the target footprint or collision map.
        Fixture f = new Fixture(6080, 8128, 6464, 8384);
        f.click("Bank");
        f.frame(0);
        assertEquals(f.start, f.controller.position());
        f.frame(20);
        assertNotEquals(f.start, f.controller.position());
        assertFalse(f.controller.nativeVisible());
        assertEquals(f.start, f.authority);
        for (int ms = 40; ms <= 580; ms += 20) { f.frame(ms); }
        LocalPoint before = f.controller.position();
        f.authority = point(6336, 8256);
        f.frame(583);
        assertTrue(f.controller.position().getX() >= before.getX());
        f.frame(600);
        assertNull(f.destination);
        assertTrue(f.controller.position().getX() > f.start.getX());
        f.controller.close();
        assertTrue(f.objects.isEmpty());
    }

    @Test
    public void recordedExchangeDelayUsesNpcThenHandsOffToLateNativeDestination()
    {
        Fixture f = new Fixture(5952, 8128, 6848, 8128);
        f.click("Exchange");
        for (int ms = 0; ms <= 480; ms += 20) { f.frame(ms); }
        assertTrue(f.controller.position().getX() > f.start.getX());
        LocalPoint before = f.controller.position();
        f.destination = point(6720, 8128);
        f.authority = point(6208, 8128);
        f.frame(481);
        assertTrue(f.controller.position().getX() >= before.getX());
        assertTrue(MovementPath.distance(before, f.controller.position()) <= 1);
        for (int ms = 500; ms <= 1000; ms += 20) { f.frame(ms); }
        assertTrue(f.controller.position().getX() > 6208);
        f.controller.close();
    }

    @Test
    public void anAlreadyPublishedNativeDestinationWinsOverNpcGeometry()
    {
        Fixture f = new Fixture(5952, 8128, 6848, 8128);
        f.click("Exchange");
        f.destination = point(5952, 8640);
        f.frame(0); f.frame(20);
        assertEquals(f.start.getX(), f.controller.position().getX());
        assertTrue(f.controller.position().getY() > f.start.getY());
        f.controller.close();
    }

    @Test
    public void staleWalkDestinationIsNotUsedForTheNpcClick()
    {
        Fixture f = new Fixture(5952, 8128, 6848, 8128);
        f.destination = point(5952, 8640);
        f.click("Exchange");
        f.frame(0); f.frame(20);
        assertTrue(f.controller.position().getX() > f.start.getX());
        assertEquals(f.start.getY(), f.controller.position().getY());
        f.controller.close();
    }

    @Test
    public void movingDespawnedAndReplacedTargetsCancelRatherThanChasing()
    {
        for (int change = 0; change < 3; ++change)
        {
            Fixture f = new Fixture(5952, 8128, 6848, 8128);
            f.click("Exchange");
            f.frame(0); f.frame(100);
            assertNotEquals(f.start, f.controller.position());
            if (change == 0) { f.area = new WorldArea(3255, 3264, 1, 1, 0); }
            if (change == 1) { f.present = false; }
            if (change == 2) { f.npcId++; }
            for (int ms = 120; ms <= 600; ms += 20) { f.frame(ms); }
            assertEquals(f.start, f.controller.position());
            f.controller.close();
        }
    }

    @Test
    public void ordinaryNpcOptionsAndProfiledAttackAreCapturedButUseStillNeedsNativeTiming()
    {
        Fixture f = new Fixture(5952, 8128, 6848, 8128);
        for (MenuAction action : new MenuAction[] {MenuAction.NPC_FIRST_OPTION, MenuAction.NPC_SECOND_OPTION,
            MenuAction.NPC_THIRD_OPTION, MenuAction.NPC_FOURTH_OPTION, MenuAction.NPC_FIFTH_OPTION})
        {
            assertNotNull(NpcApproach.capture(f.view, f.event(action, "Talk-to"), null));
            assertNull(NpcApproach.capture(f.view, f.event(action, "<col=ff0000>Attack</col>"), null));
            assertNotNull(NpcApproach.capture(f.client, f.view, f.event(action, "<col=ff0000>Attack</col>"), null));
        }
        assertNull(NpcApproach.capture(f.view, f.event(MenuAction.WIDGET_TARGET_ON_NPC, "Cast"), null));
        assertNull(NpcApproach.capture(f.view, f.event(MenuAction.ITEM_USE_ON_NPC, "Use"), null));
        f.click("Attack"); f.frame(0); f.frame(20);
        assertTrue(f.controller.position().getX() > f.start.getX());
        f.controller.close();
    }

    @Test
    public void immediateWallAndAdjacentNpcDoNotInventMovement()
    {
        for (boolean wall : new boolean[] {false, true})
        {
            Fixture f = new Fixture(5952, 8128, wall ? 6848 : 6080, 8128);
            if (wall) { f.flags[47][63] = CollisionDataFlag.BLOCK_MOVEMENT_FULL; }
            f.click("Bank"); f.frame(0); f.frame(20); f.frame(120);
            assertEquals(f.start, f.controller.position());
            f.controller.close();
        }
    }

    @Test
    public void replacementClickAndSceneChangeRetireNpcEvidence()
    {
        Fixture f = new Fixture(5952, 8128, 6848, 8128);
        f.click("Exchange"); f.frame(0); f.frame(100);
        f.controller.walkClick();
        f.destination = point(5952, 8640);
        f.frame(160); f.frame(180);
        assertTrue(f.controller.position().getY() > f.start.getY() || f.controller.position().getX() <= 5992);
        f.controller.sceneChanged(false);
        f.destination = null;
        f.frame(200); f.frame(220);
        assertEquals(f.start, f.controller.position());
        f.controller.close();
    }

    @Test
    public void disabledStartsControlAndActionRetainNativeTiming()
    {
        for (int guard = 0; guard < 3; ++guard)
        {
            Fixture f = new Fixture(5952, 8128, 6848, 8128);
            if (guard == 0) { f.starts = false; }
            if (guard == 1) { f.control = true; }
            if (guard == 2) { f.animation = 123; }
            f.click("Exchange"); f.frame(0); f.frame(20);
            assertEquals(f.start, f.controller.position());
            f.controller.close();
        }
    }

    @Test
    public void lateRefinedDestinationRedirectsWithinTheExistingMovementBudget()
    {
        Fixture f = new Fixture(5952, 8128, 6848, 8128);
        f.click("Exchange");
        for (int ms = 0; ms <= 400; ms += 20) { f.frame(ms); }
        LocalPoint before = f.controller.position();
        f.destination = point(6464, 8256);
        f.authority = point(6208, 8128);
        f.frame(420);
        assertTrue(MovementPath.distance(before, f.controller.position()) <= 8);
        assertTrue(f.controller.position().getX() >= before.getX());
        for (int ms = 440; ms <= 1000; ms += 20) { f.frame(ms); }
        assertTrue(f.controller.position().getX() > 6208);
        f.controller.close();
    }

    @Test
    public void npcPreviewQueuesBehindAnUnfinishedConfirmedCorner()
    {
        Fixture f = new Fixture(5952, 8128, 6848, 8384);
        f.authority = point(5952, 8384);
        f.frame(20);
        LocalPoint before = f.controller.position();
        f.click("Exchange");
        for (int ms = 40; ms <= 500; ms += 20)
        {
            f.frame(ms);
            assertEquals("keep the confirmed northbound prefix", 5952, f.controller.position().getX());
            assertTrue(f.controller.position().getY() >= before.getY());
            before = f.controller.position();
        }
        for (int ms = 520; ms <= 760; ms += 20) { f.frame(ms); }
        assertEquals(8384, f.controller.position().getY());
        assertTrue(f.controller.position().getX() > 5952);
        f.controller.close();
    }

    @Test
    public void unconfirmedNpcPreviewExpiresAndCannotRestartItself()
    {
        Fixture f = new Fixture(5952, 8128, 6848, 8128);
        f.click("Exchange");
        for (int ms = 0; ms <= 2500; ms += 20) { f.frame(ms); }
        assertEquals(f.start, f.controller.position());
        f.frame(2600);
        assertEquals(f.start, f.controller.position());
        f.controller.close();
    }

    @Test
    public void npcCaptureRejectsOtherViewsPlanesAndMissingTargets()
    {
        Fixture f = new Fixture(5952, 8128, 6848, 8128);
        MenuOptionClicked click = f.event(MenuAction.NPC_THIRD_OPTION, "Bank");
        WorldView other = proxy(WorldView.class, (method, args) -> {
            if (method.equals("getId")) { return 1; }
            throw new AssertionError(method);
        });
        assertNull(NpcApproach.capture(other, click, null));
        f.area = new WorldArea(3253, 3263, 1, 1, 1);
        assertNull(NpcApproach.capture(f.view, click, null));
        f.area = null;
        assertNull(NpcApproach.capture(f.view, click, null));
        assertNull(NpcApproach.capture(null, click, null));
        f.controller.close();
    }

    @Test
    public void capturedWestBankerStopsTwoTilesAwayEvenWhenNativeFlagNamesOneTileAway()
    {
        // Session 1790745936283, 15:25:48.483: early goal/flag=(6464,8384),
        // NPC=(6592,8384), actual endpoint=(6336,8384). Collision map is synthetic.
        Fixture f = new Fixture(5824, 8384, 6592, 8384);
        f.click("Bank");
        for (int ms = 0; ms <= 260; ms += 20) { f.frame(ms); }
        assertTrue(f.controller.position().getX() > f.start.getX());
        f.destination = point(6464, 8384);
        f.authority = point(6080, 8384);
        for (int ms = 280; ms <= 880; ms += 20) { f.frame(ms); }
        LocalPoint before = f.controller.position();
        f.destination = null; // One prepared frame before the final server step.
        f.frame(884);
        assertTrue("do not cancel toward old authority on withdrawal", f.controller.position().getX() >= before.getX());
        f.authority = point(6336, 8384);
        for (int ms = 900; ms <= 2500; ms += 20)
        {
            f.frame(ms);
            assertTrue("never overshoot the actual endpoint", f.controller.position().getX() <= 6336);
        }
        assertEquals(f.authority, f.controller.position());
        f.controller.close();
    }

    @Test
    public void capturedSouthClerkDoesNotReplenishTheLastUnconfirmedTile()
    {
        // 15:26:15.163: start=(6720,7488), NPC=(6720,8256). Native flag
        // later names (6720,8128), but the second run stops at (6720,8000).
        Fixture f = new Fixture(6720, 7488, 6720, 8256);
        f.click("Exchange");
        for (int ms = 0; ms <= 580; ms += 20) { f.frame(ms); }
        f.authority = point(6720, 7744); f.destination = point(6720, 8128);
        for (int ms = 600; ms <= 1180; ms += 20) { f.frame(ms); }
        f.authority = point(6720, 8000); f.destination = null;
        for (int ms = 1200; ms <= 3000; ms += 20)
        {
            f.frame(ms);
            assertTrue(f.controller.position().getY() <= 8000);
        }
        assertEquals(f.authority, f.controller.position());
        f.controller.close();
    }

    @Test
    public void capturedDiagonalBankerRefinementRetainsTheStagingGuard()
    {
        // 15:26:48.186: original goal=(6464,8256), native refines to
        // (6464,8384), but authority stops at (6336,8256).
        Fixture f = new Fixture(6208, 7744, 6592, 8384);
        f.click("Bank");
        for (int ms = 0; ms <= 380; ms += 20) { f.frame(ms); }
        f.authority = point(6208, 8000); f.destination = point(6464, 8384);
        for (int ms = 400; ms <= 980; ms += 20)
        {
            f.frame(ms);
            assertTrue("no speculation into the inner NPC ring", f.controller.position().getX() <= 6336);
        }
        f.authority = point(6336, 8256); f.destination = null;
        for (int ms = 1000; ms <= 2500; ms += 20)
        {
            f.frame(ms);
            assertTrue(f.controller.position().getX() <= 6336);
            assertTrue(f.controller.position().getY() <= 8256);
        }
        assertEquals(f.authority, f.controller.position());
        f.controller.close();
    }

    @Test
    public void nativeNpcFlagPublishedImmediatelyStillReservesTheFinalApproachForAuthority()
    {
        Fixture f = new Fixture(5824, 8384, 6592, 8384);
        f.click("Bank"); f.destination = point(6464, 8384);
        for (int ms = 0; ms <= 580; ms += 20) { f.frame(ms); }
        f.authority = point(6080, 8384);
        for (int ms = 600; ms <= 1180; ms += 20) { f.frame(ms); }
        f.authority = point(6336, 8384); f.destination = null;
        for (int ms = 1200; ms <= 2500; ms += 20)
        {
            f.frame(ms);
            assertTrue(f.controller.position().getX() <= 6336);
        }
        assertEquals(f.authority, f.controller.position());
        f.controller.close();
    }

    @Test
    public void completedStagingPreviewWaitsForNativeAuthorityRatherThanRestartingOrReturning()
    {
        Fixture f = new Fixture(6080, 8384, 6592, 8384);
        f.click("Bank");
        for (int ms = 0; ms <= 640; ms += 20) { f.frame(ms); }
        assertEquals(point(6336, 8384), f.controller.position());
        f.destination = point(6464, 8384);
        f.frame(660); f.frame(680);
        assertEquals("a late closer flag cannot revive speculation", point(6336, 8384), f.controller.position());
        f.authority = point(6336, 8384); f.destination = null;
        for (int ms = 700; ms <= 2000; ms += 20) { f.frame(ms); }
        assertEquals(f.authority, f.controller.position());
        f.controller.close();
    }

    @Test
    public void bankAndExchangeInsideTheExtendedStagingRingNeedNativeMovementEvidence()
    {
        for (String option : new String[] {"Bank", "Exchange"})
        {
            Fixture f = new Fixture(6336, 8384, 6592, 8384);
            f.click(option);
            for (int ms = 0; ms <= 600; ms += 20) { f.frame(ms); }
            assertEquals(f.start, f.controller.position());
            // A regular adjacent interaction may still need to move one tile.
            f.authority = point(6464, 8384);
            for (int ms = 620; ms <= 1300; ms += 20) { f.frame(ms); }
            assertEquals(f.authority, f.controller.position());
            f.controller.close();
        }
    }

    @Test
    public void capturedCloseBankerStartDoesNotStopAndRestartBeforeItsFirstServerStep()
    {
        // Session 1790747842029, 15:57:31.546: the old one-tile seed stopped
        // at (6336,8384) at 31.845, then restarted at 32.229 (~683 ms).
        Fixture f = new Fixture(6208, 8384, 6592, 8384);
        f.speed = 1.1;
        f.click("Bank"); f.frame(0);
        LocalPoint before = f.controller.position();
        for (int ms = 20; ms <= 680; ms += 20)
        {
            f.frame(ms);
            assertEquals(Math.min(6464, Math.round(6208 + ms * 0.44)), f.controller.position().getX());
            before = f.controller.position();
        }
        f.authority = point(6464, 8384); f.frame(683);
        assertTrue(f.controller.position().getX() >= before.getX());
        before = f.controller.position();
        f.frame(703);
        assertEquals("already at the correct goal when confirmation arrives", before, f.controller.position());
        for (int ms = 723; ms <= 1303; ms += 20) { f.frame(ms); }
        assertEquals(f.authority, f.controller.position());
        f.controller.close();
    }

    @Test
    public void capturedCloseOffsetBankerStartKeepsMovingUntilTheConfirmedCorner()
    {
        // 15:57:40.508: (6208,8512), NPC=(6592,8384). First authority
        // (6464,8384) at 41.233; no native flag was published in the capture.
        Fixture f = new Fixture(6208, 8512, 6592, 8384);
        f.speed = 1.1;
        f.click("Bank"); f.frame(0);
        LocalPoint before = f.controller.position();
        for (int ms = 20; ms <= 720; ms += 20)
        {
            f.frame(ms);
            assertEquals(Math.min(6464, Math.round(6208 + ms * 0.44)), f.controller.position().getX());
            assertEquals(Math.max(8384, Math.round(8512 - ms * 0.22)), f.controller.position().getY());
            before = f.controller.position();
        }
        f.authority = point(6464, 8384); f.frame(725);
        for (int ms = 745; ms <= 1405; ms += 20)
        {
            before = f.controller.position();
            f.frame(ms);
            assertTrue("no backstep at the authority handoff", f.controller.position().getX() >= before.getX());
            assertTrue(f.controller.position().getX() <= 6464);
            assertTrue(f.controller.position().getY() >= 8384);
        }
        assertNull(f.destination);
        assertEquals(f.authority, f.controller.position());
        f.controller.close();
    }

    @Test
    public void unexpectedOneTileAuthorityRetiresTheShortRunPairImmediately()
    {
        Fixture f = new Fixture(6208, 8384, 6592, 8384);
        f.click("Bank");
        for (int ms = 0; ms <= 660; ms += 20) { f.frame(ms); }
        assertEquals(point(6464, 8384), f.controller.position());
        f.authority = point(6336, 8384);
        int previousX = f.controller.position().getX();
        for (int ms = 680; ms <= 1600; ms += 20)
        {
            f.frame(ms);
            assertTrue("recover as soon as the actual step disagrees", f.controller.position().getX() <= previousX);
            previousX = f.controller.position().getX();
        }
        assertEquals(f.authority, f.controller.position());
        f.controller.close();
    }

    @Test
    public void recordedGuardBowClickCanStartBeforeThe560MillisecondNativePublication()
    {
        // 17:03:29.030: start=(5952,5696); first authority=(6208,5696)
        // and flag=(7488,6208) at 29.590. NPC/equipment/collision are synthetic:
        // the old log recorded neither guard footprint nor weapon/style.
        Fixture f = new Fixture(5952, 5696, 7488, 6208);
        f.ranged("Magic shortbow", 861, 1);
        f.controller.worldInteraction(f.event(MenuAction.NPC_SECOND_OPTION, "Attack"));
        f.frame(0); f.frame(20);
        assertTrue(f.controller.position().getX() > 5952);
        assertEquals(point(5952, 5696), f.authority);
        for (int ms = 40; ms <= 540; ms += 20) { f.frame(ms); }
        LocalPoint before = f.controller.position();
        f.authority = point(6208, 5696); f.destination = point(7488, 6208);
        f.frame(560);
        assertTrue(f.controller.position().getX() >= before.getX());
        for (int ms = 580; ms <= 1200; ms += 20)
        {
            f.frame(ms);
            assertTrue("native flag cannot pull a bow forecast into its firing region", f.controller.position().getX() <= 6592);
        }
        f.controller.close();
    }

    @Test
    public void latestShortBankCapturesFinishTheWholePairWithoutStagingSlowdown()
    {
        // Session 1790822916958. Recorded origin, NPC, final authority and
        // first-step age; scene collision flags are synthetic, not in the trace.
        int[][] captures = {
            {7232, 6208, 7616, 6336, 7488, 6336, 619, 0}, // 12:48:41.343, checked knight
            {7232, 6336, 7616, 6336, 7488, 6336, 400, 0}, // 12:48:46.361, straight pair
            {7232, 6336, 7616, 6464, 7488, 6464, 339, 0}, // 12:48:52.421, other banker
            {7360, 5952, 7616, 6336, 7360, 6208, 641, 0}, // 12:49:06.540, two-column offset
            {7488, 5952, 7616, 6336, 7488, 6208, 321, 1}}; // 12:49:17.642, counter side
        for (int[] c : captures)
        {
            for (String option : new String[] {"Bank", "Exchange"})
            {
                Fixture f = new Fixture(c[0], c[1], c[2], c[3]);
                f.speed = 1.1;
                if (c[7] == 1) { f.flags[c[2] / 128][(c[3] - 128) / 128] = CollisionDataFlag.BLOCK_MOVEMENT_FULL; }
                f.click(option); f.frame(0);
                LocalPoint goal = point(c[4], c[5]);
                assertEquals(f.start, f.controller.position());
                for (int ms = 20; ms < c[6]; ms += 20)
                {
                    f.frame(ms);
                    assertStraightRunPosition(f.start, goal, ms, f.controller.position());
                }
                f.authority = goal; f.frame(c[6]);
                assertStraightRunPosition(f.start, goal, c[6], f.controller.position());
                for (int ms = c[6] + 20; ms <= 1000; ms += 20)
                {
                    f.frame(ms);
                    assertStraightRunPosition(f.start, goal, ms, f.controller.position());
                }
                assertEquals(goal, f.controller.position());
                assertNull(f.destination);
                f.controller.close();
            }
        }
    }

    private static void assertStraightRunPosition(LocalPoint start, LocalPoint goal, int ms, LocalPoint actual)
    {
        double fraction = Math.min(1, ms * 0.44 / 256);
        assertEquals((int) Math.round(start.getX() + (goal.getX() - start.getX()) * fraction), actual.getX());
        assertEquals((int) Math.round(start.getY() + (goal.getY() - start.getY()) * fraction), actual.getY());
    }

    @Test
    public void immediatelyPublishedShortNativeGoalAlsoUsesNormalPairPacing()
    {
        Fixture f = new Fixture(7232, 6208, 7616, 6336);
        f.speed = 1.1;
        f.click("Bank"); f.destination = point(7488, 6336);
        f.frame(0); f.frame(100);
        assertStraightRunPosition(f.start, f.destination, 100, f.controller.position());
        f.authority = f.destination; f.frame(400);
        for (int ms = 420; ms <= 800; ms += 20) { f.frame(ms); }
        assertEquals(f.authority, f.controller.position());
        f.controller.close();
    }

    @Test
    public void shortNativeFlagWithdrawalBeforeFirstAuthorityDoesNotReverseTowardTheIdleOrigin()
    {
        Fixture f = new Fixture(7232, 6336, 7616, 6336);
        f.speed = 1.1;
        f.click("Bank"); f.destination = point(7488, 6336);
        for (int ms = 0; ms <= 300; ms += 20) { f.frame(ms); }
        f.destination = null;
        f.frame(320);
        assertStraightRunPosition(f.start, point(7488, 6336), 320, f.controller.position());
        f.authority = point(7488, 6336);
        for (int ms = 340; ms <= 800; ms += 20) { f.frame(ms); }
        assertEquals(f.authority, f.controller.position());
        f.controller.close();
    }

    @Test
    public void shortExceptionRequiresBankExchangeRunAndClearAvailableTargetEvidence()
    {
        for (int guard = 0; guard < 5; ++guard)
        {
            Fixture f = new Fixture(7232, 6336, 7616, 6336);
            if (guard == 1) { f.run = false; }
            if (guard == 2) { f.flags[57][49] = CollisionDataFlag.BLOCK_MOVEMENT_FULL; }
            if (guard == 3) { f.control = true; }
            if (guard == 4) { f.present = false; }
            f.click(guard == 0 ? "Talk-to" : "Bank"); f.frame(0); f.frame(100);
            if (guard == 0)
            {
                assertEquals("ordinary adjacent approach has no banker easing", 7272, f.controller.position().getX());
            }
            else if (guard == 1)
            {
                assertTrue("generic staging stays bounded", f.controller.position().getX() < 7360);
            }
            else { assertEquals(f.start, f.controller.position()); }
            f.controller.close();
        }
    }

    @Test
    public void shortPairRetiresOnRunModeTargetOrActionChangesAndNeverRearmsItself()
    {
        for (int change = 0; change < 3; ++change)
        {
            Fixture f = new Fixture(7232, 6336, 7616, 6336);
            f.click("Bank"); f.frame(0); f.frame(100);
            assertTrue(f.controller.position().getX() > f.start.getX());
            if (change == 0) { f.run = false; }
            if (change == 1) { f.present = false; }
            if (change == 2) { f.animation = 123; }
            for (int ms = 120; ms <= 1200; ms += 20) { f.frame(ms); }
            assertEquals(f.authority, f.controller.position());
            f.animation = -1; f.frame(1220);
            assertEquals(f.authority, f.controller.position());
            f.controller.close();
        }
    }

    @Test
    public void capturedFiveTileBankerRunHasNoIdleBeforeItsFinalConfirmedStep()
    {
        // Session 1790761419817: 19:43:41.097 start, first step/flag at
        // 41.416, flag withdrawn 42.035, final endpoint at 42.096.
        Fixture f = new Fixture(5952, 8384, 6592, 8384);
        f.speed = 1.1;
        f.click("Bank");
        for (int ms = 0; ms <= 300; ms += 20) { f.frame(ms); }
        f.authority = point(6208, 8384); f.destination = point(6464, 8384);
        f.frame(319);
        LocalPoint before = f.controller.position();
        for (int ms = 339; ms <= 919; ms += 20)
        {
            f.frame(ms);
            assertTrue("normal pace through the old staged tile", f.controller.position().getX() - before.getX() >= 8);
            assertTrue(f.controller.position().getX() < 6464);
            before = f.controller.position();
        }
        f.destination = null;
        f.frame(938); f.frame(958); f.frame(978); f.frame(998);
        assertEquals("do not wait one tile before the goal", Math.round(5952 + 998 * 0.44), f.controller.position().getX());
        f.authority = point(6464, 8384);
        for (int ms = 999; ms <= 1599; ms += 20)
        {
            before = f.controller.position();
            f.frame(ms);
            assertTrue(f.controller.position().getX() >= before.getX());
            assertTrue(f.controller.position().getX() <= 6464);
        }
        assertEquals(f.authority, f.controller.position());
        f.controller.close();
    }

    @Test
    public void capturedFiveTileExchangeRunKeepsMovingInsteadOfStoppingAt8000()
    {
        // 19:43:52.317: (6720,7616), NPC=(6720,8256); first authority
        // (6720,7872) at 52.834, final (6720,8128) at 53.437.
        Fixture f = new Fixture(6720, 7616, 6720, 8256);
        f.speed = 1.1;
        f.click("Exchange");
        for (int ms = 0; ms <= 500; ms += 20) { f.frame(ms); }
        f.authority = point(6720, 7872); f.destination = point(6720, 8128);
        f.frame(517);
        LocalPoint before = f.controller.position();
        for (int ms = 537; ms <= 1117; ms += 20)
        {
            f.frame(ms);
            assertTrue("full run rate through the old pause", f.controller.position().getY() - before.getY() >= 8);
            assertTrue(f.controller.position().getY() < 8128);
            before = f.controller.position();
        }
        f.authority = point(6720, 8128); f.destination = null;
        for (int ms = 1120; ms <= 1720; ms += 20)
        {
            before = f.controller.position();
            f.frame(ms);
            assertTrue(f.controller.position().getY() >= before.getY());
            assertTrue(f.controller.position().getY() <= 8128);
        }
        assertEquals(f.authority, f.controller.position());
        f.controller.close();
    }

    @Test
    public void ordinaryFiveTileApproachNeedsNoBankerReleaseWhileBankStillRequiresANewNativeGoal()
    {
        for (int guard = 0; guard < 2; ++guard)
        {
            Fixture f = new Fixture(5952, 8384, 6592, 8384);
            f.click(guard == 0 ? "Talk-to" : "Bank");
            for (int ms = 0; ms <= 300; ms += 20) { f.frame(ms); }
            f.authority = point(6208, 8384);
            f.destination = guard == 0 ? point(6464, 8384) : null;
            for (int ms = 320; ms <= 1000; ms += 20) { f.frame(ms); }
            assertEquals(guard == 0 ? point(6352, 8384) : point(6336, 8384), f.controller.position());
            f.controller.close();
        }
    }

    @Test
    public void latestBankerCapturesRunAtFullPaceThroughTheLastTwoTiles()
    {
        // 2026-10-01 11:12:24.063 / 33.304 / 41.618, +10:00.
        // All three: origin=(4928,7360), NPC=(5568,7360), native goal=(5440,7360).
        int[][] timings = {{435, 1094}, {178, 857}, {260, 924}};
        for (int[] timing : timings)
        {
            Fixture f = new Fixture(4928, 7360, 5568, 7360);
            f.speed = 1.1;
            f.click("Bank"); f.frame(0);
            for (int ms = 20; ms < timing[0]; ms += 20) { f.frame(ms); }
            f.authority = point(5184, 7360); f.destination = point(5440, 7360);
            f.frame(timing[0]);
            for (int ms = timing[0] + 20; ms < timing[1]; ms += 20)
            {
                LocalPoint before = f.controller.position();
                f.frame(ms);
                assertTrue("no one-tile-early easing", f.controller.position().getX() - before.getX() >= 8);
                assertEquals(Math.round(4928 + ms * 0.44), f.controller.position().getX());
            }
            f.authority = point(5440, 7360); f.destination = null;
            f.frame(timing[1]);
            for (int ms = timing[1] + 20; ms <= 1400; ms += 20)
            {
                f.frame(ms);
                assertEquals(Math.min(5440, Math.round(4928 + ms * 0.44)), f.controller.position().getX());
            }
            assertEquals(f.authority, f.controller.position());
            f.controller.close();
        }
    }

    @Test
    public void releasedNpcArrivalRetiresWhenRunModeOrTargetEvidenceChanges()
    {
        for (int change = 0; change < 3; ++change)
        {
            Fixture f = new Fixture(4928, 7360, 5568, 7360);
            f.click("Bank"); f.frame(0); f.frame(100);
            f.authority = point(5184, 7360); f.destination = point(5440, 7360);
            f.frame(120); f.frame(220);
            if (change == 0) { f.run = false; }
            if (change == 1) { f.present = false; }
            if (change == 2) { f.destination = point(5312, 7360); }
            for (int ms = 240; ms <= 1400; ms += 20) { f.frame(ms); }
            assertEquals(f.authority, f.controller.position());
            f.controller.close();
        }
    }

    @Test
    public void rangedAttackAlreadyInReachDoesNotManufactureARunBeforeAttacking()
    {
        Fixture f = new Fixture(5952, 5696, 6720, 5696); // Six tiles, inside shortbow reach.
        f.ranged("Magic shortbow", 861, 1);
        f.click("Attack");
        for (int ms = 0; ms <= 580; ms += 20) { f.frame(ms); }
        assertEquals(f.start, f.controller.position());
        f.animation = 426;
        for (int ms = 600; ms <= 1000; ms += 20) { f.frame(ms); }
        assertEquals(f.start, f.controller.position());
        f.controller.close();
    }

    @Test
    public void rapidAndLongrangeUseDifferentBoundariesAndUnknownWeaponsStayConservative()
    {
        for (int style : new int[] {1, 2})
        {
            Fixture f = new Fixture(5952, 5696, 6976, 5696); // Eight tiles.
            f.ranged("Magic shortbow", 861, style);
            f.click("Attack"); f.frame(0); f.frame(100);
            if (style == 1) { assertTrue(f.controller.position().getX() > 5952); }
            else { assertEquals("already in longrange reach", f.start, f.controller.position()); }
            f.controller.close();
        }
        Fixture unknown = new Fixture(5952, 5696, 6976, 5696);
        unknown.ranged("Unlisted bow variant", 999999, 1);
        unknown.click("Attack"); unknown.frame(0); unknown.frame(100);
        assertEquals(unknown.start, unknown.controller.position());
        unknown.controller.close();
    }

    @Test
    public void meleeApproachAndBowActionCancelSpeculationBeforeAnotherForwardPreviewStep()
    {
        for (boolean ranged : new boolean[] {false, true})
        {
            Fixture f = new Fixture(5952, 5696, 7488, 5696);
            if (ranged) { f.ranged("Magic shortbow", 861, 1); }
            f.click("Attack"); f.frame(0); f.frame(100);
            LocalPoint before = f.controller.position();
            assertTrue(before.getX() > f.start.getX());
            f.animation = ranged ? 426 : 422;
            f.frame(120);
            assertTrue("no forward prediction during the attack", f.controller.position().getX() <= before.getX());
            for (int ms = 140; ms <= 1200; ms += 20) { f.frame(ms); }
            assertEquals(f.authority, f.controller.position());
            f.animation = -1;
            f.frame(1220);
            assertEquals("attack click must not rearm itself after its animation", f.authority, f.controller.position());
            f.controller.close();
        }
    }

    @Test
    public void attackWithAFinalServerStepKeepsOnlyConfirmedTravelToTheActualRangeEndpoint()
    {
        Fixture f = new Fixture(5952, 5696, 7488, 5696);
        f.ranged("Toxic blowpipe", 12926, 1);
        f.click("Attack");
        for (int ms = 0; ms <= 540; ms += 20) { f.frame(ms); }
        f.authority = point(6208, 5696); f.destination = point(7488, 5696);
        for (int ms = 560; ms <= 1120; ms += 20) { f.frame(ms); }
        f.authority = point(6464, 5696);
        for (int ms = 1140; ms <= 1700; ms += 20) { f.frame(ms); }
        f.authority = point(6720, 5696); f.animation = 426; f.destination = null;
        for (int ms = 1720; ms <= 3000; ms += 20)
        {
            f.frame(ms);
            assertTrue(f.controller.position().getX() <= f.authority.getX());
        }
        assertEquals("never continue toward the old NPC anchor", f.authority, f.controller.position());
        f.controller.close();
    }

    @Test
    public void weaponStyleOrCategorySwitchRetiresAnAttackForecastIncludingAfterNativePublication()
    {
        for (int change = 0; change < 3; ++change)
        {
            Fixture f = new Fixture(5952, 5696, 7488, 5696);
            f.ranged("Magic shortbow", 861, 1);
            f.click("Attack"); f.frame(0); f.frame(100);
            f.destination = point(7488, 5696); f.frame(120);
            assertTrue(f.controller.position().getX() > f.start.getX());
            if (change == 0) { f.weaponId = 9185; }
            if (change == 1) { f.attackStyle = 2; }
            if (change == 2) { f.category++; }
            for (int ms = 140; ms <= 1000; ms += 20) { f.frame(ms); }
            assertEquals(f.start, f.controller.position());
            f.controller.close();
        }
    }

    @Test
    public void discontinuousOrDespawnedGuardAndBlockedAttackStartDoNotContinueTheOldForecast()
    {
        for (int guard = 0; guard < 3; ++guard)
        {
            Fixture f = new Fixture(5952, 5696, 7488, 5696);
            if (guard == 2) { f.flags[47][44] = CollisionDataFlag.BLOCK_MOVEMENT_FULL; }
            f.ranged("Magic shortbow", 861, 1);
            f.click("Attack"); f.frame(0); f.frame(100);
            if (guard == 0) { f.area = new WorldArea(3261, 3245, 1, 1, 0); }
            if (guard == 1) { f.present = false; }
            for (int ms = 120; ms <= 1000; ms += 20) { f.frame(ms); }
            assertEquals(f.start, f.controller.position());
            f.controller.close();
        }
    }

    @Test
    public void spotEffectAlsoRetiresTheCombatPreviewBeforeForwardAdvance()
    {
        Fixture f = new Fixture(5952, 5696, 7488, 5696);
        f.ranged("Magic shortbow", 861, 1);
        f.click("Attack"); f.frame(0); f.frame(100);
        LocalPoint before = f.controller.position();
        f.spot = true; f.frame(120);
        assertTrue(f.controller.position().getX() <= before.getX());
        for (int ms = 140; ms <= 1000; ms += 20) { f.frame(ms); }
        assertEquals(f.authority, f.controller.position());
        f.controller.close();
        assertTrue(f.objects.isEmpty());
    }

    @Test
    public void sceneWalkUsesOffRouteTrueTileProgressToLeaveTheParallelPreviewRow()
    {
        Fixture f = new Fixture(4416, 6720, 7000, 7000);
        f.authority = point(4416, 6592);
        f.frame(0);
        f.controller.walkClick();
        f.destination = point(5312, 6720);
        for (int ms = 20; ms <= 360; ms += 20) { f.frame(ms); }
        assertTrue(f.controller.position().getX() > 4416);
        f.authority = point(4672, 6592);
        for (int ms = 380; ms <= 1600; ms += 20)
        {
            if (ms == 980) { f.authority = point(4928, 6592); }
            if (ms == 1580) { f.authority = point(5184, 6720); }
            LocalPoint before = f.controller.position();
            f.frame(ms);
            assertTrue("forward confirmation must not produce a reverse/stop", f.controller.position().getX() >= before.getX());
            assertTrue(MovementPath.distance(before, f.controller.position()) <= 8);
            if (ms == 1060) { assertEquals("join the row named by true-tile progress", 6592, f.controller.position().getY()); }
        }
        f.controller.close();
        assertTrue(f.objects.isEmpty());
    }

    private static LocalPoint point(int x, int y) { return new LocalPoint(x, y, 0); }
    private interface Call { Object invoke(String method, Object[] args); }
    private static <T> T proxy(Class<T> type, Call call)
    {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (object, method, args) -> {
            if (method.getName().equals("hashCode")) { return System.identityHashCode(object); }
            if (method.getName().equals("equals")) { return object == args[0]; }
            return call.invoke(method.getName(), args);
        }));
    }

    static final class Fixture
    {
        final LocalPoint start;
        LocalPoint authority, destination, nativePoint, npcNativePoint;
        Actor interacting;
        WorldArea area;
        long now = 1_000_000_000L;
        int cycle = 100, npcId = 1634, animation = -1;
        int weaponId = -1, category, attackStyle;
        String weaponName = "";
        String[] npcActions = {"Talk-to", "Trade"};
        boolean missingNpcComposition, untransformedNpc;
        int npcCompositionReads;
        String[] combatStyles = {"Accurate", "Aggressive", "Controlled", "Defensive"};
        boolean present = true, starts = true, control, spot, run = true, npcDead, autoRetaliate;
        boolean originalWhenAligned = true;
        boolean faceInteractionsOnArrival;
        // Historical captures assert the original base-rate budget. New pacing
        // tests explicitly enable catch-up rather than rewriting those fixtures.
        boolean catchUp;
        int catchUpPercent = 10;
        boolean slowAhead;
        int slowAheadPercent = 10;
        boolean slowArrival;
        int slowArrivalPercent = -1;
        double arrivalFirstTiles = 0.2;
        double arrivalFinalTiles = 1;
        double arrivalFirstSpeed = 50;
        double arrivalFinalSpeed = 49.7;
        int nativeOrientation;
        int spotId = 1, spotStartCycle, spotFrame;
        private ActorSpotAnim retainedSpot;
        private int retainedSpotId = -1, retainedSpotStart = -1;
        int builtIdle = -1, builtPose = -1, builtAction = -1, builtActionFrame = -1, modelBuilds;
        double speed = 1;
        int smoothing = 0;
        int turnSpeed = 30;
        final int[][] flags = new int[104][104];
        final int[][][] heights = new int[4][105][105];
        final byte[][][] settings = new byte[4][104][104];
        Scene scene;
        final Set<RuneLiteObjectController> objects = Collections.newSetFromMap(new IdentityHashMap<>());
        final Map<String, Integer> poses = new HashMap<>();
        final Model model = proxy(Model.class, (method, args) -> { throw new AssertionError(method); });
        final CollisionData collision = proxy(CollisionData.class, (method, args) -> {
            if (method.equals("getFlags")) { return flags; }
            throw new AssertionError(method);
        });
        final WorldView view;
        final NPC npc;
        final Player followedPlayer;
        final Player player;
        final Client client;
        final MovementController controller;

        Fixture(int x, int y, int npcX, int npcY)
        {
            this(x, y, npcX, npcY, null);
        }

        Fixture(int x, int y, int npcX, int npcY, List<MovementTrace.Entry> traceEntries)
        {
            start = authority = point(x, y);
            area = new WorldArea(3200 + npcX / 128, 3200 + npcY / 128, 1, 1, 0);
            view = proxy(WorldView.class, this::viewCall);
            npc = proxy(NPC.class, this::npcCall);
            followedPlayer = proxy(Player.class, (method, args) -> {
                switch (method)
                {
                    case "getId": return 623;
                    case "getWorldView": return view;
                    case "getWorldLocation": return area.toWorldPoint();
                    case "getWorldArea": return area;
                    case "isDead": return npcDead;
                    default: throw new AssertionError("followed player: " + method);
                }
            });
            player = proxy(Player.class, this::playerCall);
            client = proxy(Client.class, this::clientCall);
            ResponsiveMovementConfig config = new ResponsiveMovementConfig() {
                @Override public boolean responsiveStarts() { return starts; }
                @Override public double movementSpeed() { return speed * 5; }
                @Override public int clickSmoothingMs() { return smoothing; }
                @Override public int turnSpeed() { return turnSpeed; }
                @Override public boolean recordTrace() { return traceEntries != null; }
                @Override public boolean originalWhenAligned() { return originalWhenAligned; }
                @Override public boolean faceInteractionsOnArrival() { return faceInteractionsOnArrival; }
                @Override public boolean catchUp() { return catchUp; }
                @Override public int catchUpPercent() { return catchUpPercent; }
                @Override public boolean slowAhead() { return slowAhead; }
                @Override public int slowAheadPercent() { return slowAheadPercent; }
                @Override public boolean slowArrival() { return slowArrival; }
                @Override public int slowArrivalPercent() { return slowArrivalPercent; }
                @Override public double arrivalFirstTiles() { return arrivalFirstTiles; }
                @Override public double arrivalFinalTiles() { return arrivalFinalTiles; }
                @Override public double arrivalFirstSpeed() { return arrivalFirstSpeed; }
                @Override public double arrivalFinalSpeed() { return arrivalFinalSpeed; }
            };
            controller = traceEntries == null ? new MovementController(client, config, () -> now) :
                new MovementController(client, config, () -> now, new MovementTrace(() -> now, () -> 1790938361318L,
                    batch -> { traceEntries.addAll(batch); return true; }));
            controller.update();
            controller.presented();
        }

        void at(int millis) { now = 1_000_000_000L + millis * 1_000_000L; cycle = 100 + millis / 20; }
        void frame(int millis) { at(millis); controller.update(); }
        void click(String option) { controller.worldInteraction(event(MenuAction.NPC_THIRD_OPTION, option)); }
        void ranged(String name, int id, int style)
        {
            weaponName = name; weaponId = id; attackStyle = style; category = 3;
            // ATTACK_STYLE_NAME is the native XP style, not the button label.
            combatStyles = new String[] {"Ranging", "Ranging", "Longrange"};
        }
        MenuOptionClicked event(MenuAction action, String option)
        {
            return new MenuOptionClicked(proxy(MenuEntry.class, (method, args) -> {
                switch (method)
                {
                    case "getType": return action;
                    case "getOption": return option;
                    case "getIdentifier": return action.name().startsWith("PLAYER_") || action.name().endsWith("_ON_PLAYER") ? 623 : 25398;
                    case "getParam0": case "getParam1": return 0;
                    case "getWorldViewId": return 0;
                    case "getNpc": return npc;
                    case "getPlayer": return followedPlayer;
                    default: throw new AssertionError("menu: " + method);
                }
            }));
        }

        MenuOptionClicked sceneEvent(MenuAction action, String option, int id, int x, int y)
        {
            return new MenuOptionClicked(proxy(MenuEntry.class, (method, args) -> {
                switch (method)
                {
                    case "getType": return action;
                    case "getOption": return option;
                    case "getIdentifier": return id;
                    case "getParam0": return x;
                    case "getParam1": return y;
                    case "getWorldViewId": return 0;
                    case "getNpc": return null;
                    default: throw new AssertionError("scene menu: " + method);
                }
            }));
        }

        void object(int id, LocalPoint min, LocalPoint max)
        {
            GameObject object = proxy(GameObject.class, (method, args) -> {
                switch (method)
                {
                    case "getId": return id;
                    case "getPlane": return 0;
                    case "getWorldView": return view;
                    case "getConfig": return 10;
                    case "getSceneMinLocation": return new Point(min.getSceneX(), min.getSceneY());
                    case "getSceneMaxLocation": return new Point(max.getSceneX(), max.getSceneY());
                    default: throw new AssertionError("object: " + method);
                }
            });
            Tile tile = proxy(Tile.class, (method, args) -> {
                if (method.equals("getGameObjects")) { return new GameObject[] {object}; }
                throw new AssertionError("tile: " + method);
            });
            Tile[][][] tiles = new Tile[4][104][104];
            for (int x = min.getSceneX(); x <= max.getSceneX(); ++x)
            {
                for (int y = min.getSceneY(); y <= max.getSceneY(); ++y) { tiles[0][x][y] = tile; }
            }
            scene = proxy(Scene.class, (method, args) -> {
                if (method.equals("getTiles")) { return tiles; }
                throw new AssertionError("scene: " + method);
            });
        }

        void wall(int id, int x, int y)
        {
            WallObject wall = proxy(WallObject.class, (method, args) -> {
                switch (method)
                {
                    case "getId": return id;
                    case "getPlane": case "getConfig": return 0;
                    case "getWorldView": return view;
                    default: throw new AssertionError("wall: " + method);
                }
            });
            Tile tile = proxy(Tile.class, (method, args) -> {
                if (method.equals("getWallObject")) { return wall; }
                if (method.equals("getGameObjects")) { return null; }
                throw new AssertionError("wall tile: " + method);
            });
            Tile[][][] tiles = new Tile[4][104][104];
            tiles[0][x][y] = tile;
            scene = proxy(Scene.class, (method, args) -> {
                if (method.equals("getTiles")) { return tiles; }
                throw new AssertionError("wall scene: " + method);
            });
        }

        Object viewCall(String method, Object[] args)
        {
            switch (method)
            {
                case "getId": case "getPlane": return 0;
                case "getBaseX": case "getBaseY": return 3200;
                case "getSizeX": case "getSizeY": return 104;
                case "isInstance": return false;
                case "getCollisionMaps": return new CollisionData[] {collision};
                case "getScene":
                    if (scene == null)
                    {
                        Tile[][][] tiles = new Tile[4][104][104];
                        for (int x = 0; x < 104; ++x)
                        {
                            for (int y = 0; y < 104; ++y)
                            {
                                final Point location = new Point(x, y);
                                tiles[0][x][y] = proxy(Tile.class, (name, values) -> {
                                    if (name.equals("getSceneLocation")) { return location; }
                                    if (name.equals("getPlane")) { return 0; }
                                    if (name.equals("getGameObjects")) { return null; }
                                    if (name.equals("getWallObject")) { return null; }
                                    throw new AssertionError("sight tile: " + name);
                                });
                            }
                        }
                        scene = proxy(Scene.class, (name, values) -> {
                            if (name.equals("getTiles")) { return tiles; }
                            throw new AssertionError("sight scene: " + name);
                        });
                    }
                    return scene;
                case "getTileHeights": return heights;
                case "getTileSettings": return settings;
                case "getTileHeight": return 0;
                case "npcs": return proxy(IndexedObjectSet.class, (name, values) -> {
                    if (name.equals("byIndex")) { assertEquals(25398, values[0]); return present ? npc : null; }
                    throw new AssertionError("NPC enumeration: " + name);
                });
                case "players": return proxy(IndexedObjectSet.class, (name, values) -> {
                    if (name.equals("byIndex")) { assertEquals(623, values[0]); return present ? followedPlayer : null; }
                    throw new AssertionError("player enumeration: " + name);
                });
                default: throw new AssertionError("view: " + method);
            }
        }

        Object npcCall(String method, Object[] args)
        {
            switch (method)
            {
                case "getWorldView": return view;
                case "getWorldArea": return area;
                case "getWorldLocation": return area == null ? null : area.toWorldPoint();
                case "getLocalLocation": return npcNativePoint == null ? LocalPoint.fromWorld(view, area.toWorldPoint()) : npcNativePoint;
                case "getIndex": return 25398;
                case "getId": return npcId;
                case "getName": return "Banker";
                case "getComposition": case "getTransformedComposition":
                    ++npcCompositionReads;
                    if (missingNpcComposition || untransformedNpc && method.equals("getTransformedComposition")) { return null; }
                    return proxy(NPCComposition.class, (name, values) -> {
                        if (name.equals("getActions")) { return npcActions; }
                        throw new AssertionError("NPC composition: " + name);
                    });
                case "isDead": return npcDead;
                default: throw new AssertionError("NPC: " + method);
            }
        }

        Object playerCall(String method, Object[] args)
        {
            switch (method)
            {
                case "getWorldView": return view;
                case "getWorldLocation": return new WorldPoint(3200 + authority.getSceneX(), 3200 + authority.getSceneY(), 0);
                case "getLocalLocation": return nativePoint == null ? start : nativePoint;
                case "getAnimation": return animation;
                case "getInteracting": return interacting;
                case "getSpotAnims":
                    if (!spot) { return null; }
                    if (retainedSpot == null || retainedSpotId != spotId || retainedSpotStart != spotStartCycle)
                    {
                        retainedSpotId = spotId; retainedSpotStart = spotStartCycle;
                        final int id = spotId, startCycle = spotStartCycle;
                        retainedSpot = proxy(ActorSpotAnim.class, (name, values) -> {
                            switch (name)
                            {
                                case "getId": return id;
                                case "getFrame": return spotFrame;
                                case "getStartCycle": return startCycle;
                                case "getHash": return 1L;
                                case "getModel": return model;
                                case "getRenderMode": return Renderable.RENDERMODE_SORTED;
                                default: throw new AssertionError(name);
                            }
                        });
                    }
                    return proxy(IterableHashTable.class, (name, values) -> {
                        if (name.equals("iterator")) { return Collections.singletonList(retainedSpot).iterator(); }
                        throw new AssertionError(name);
                    });
                case "getFootprintSize": return 1;
                case "getOrientation": case "getCurrentOrientation": return nativeOrientation;
                case "getAnimationHeightOffset": return 0;
                case "getModel":
                    ++modelBuilds;
                    builtIdle = poses.getOrDefault("IdlePoseAnimation", -1);
                    int pose = poses.getOrDefault("PoseAnimation", -1);
                    // Explicit native-builder double: a primary action omits its
                    // idle secondary sequence; other gait sequences may be blended.
                    builtPose = animation != -1 && pose == builtIdle ? -1 : pose;
                    builtAction = animation; builtActionFrame = poses.getOrDefault("AnimationFrame", -1);
                    return model;
                default:
                    if (method.startsWith("get")) { return poses.getOrDefault(method.substring(3), -1); }
                    if (method.startsWith("set"))
                    {
                        if (method.equals("setAnimation") || method.equals("setAnimationFrame") || method.equals("setActionFrame"))
                        {
                            throw new AssertionError("native action clock must never be changed: " + method);
                        }
                        poses.put(method.substring(3), (int) args[0]); return null;
                    }
                    throw new AssertionError("player: " + method);
            }
        }

        Object clientCall(String method, Object[] args)
        {
            switch (method)
            {
                case "getLocalPlayer": return player;
                case "getGameState": return GameState.LOGGED_IN;
                case "getLocalDestinationLocation": return destination;
                case "getGameCycle": return cycle;
                case "getWorld": return 301;
                case "getVarpValue":
                    if ((int) args[0] == VarPlayerID.COM_MODE) { return attackStyle; }
                    if ((int) args[0] == VarPlayerID.OPTION_NODEF) { return autoRetaliate ? 0 : 1; }
                    return run ? 1 : 0;
                case "getVarbitValue": return category;
                case "getItemContainer": return proxy(ItemContainer.class, (name, values) -> {
                    if (name.equals("getItem")) { return weaponId < 0 ? null : new Item(weaponId, 1); }
                    throw new AssertionError(name);
                });
                case "getItemDefinition": return proxy(ItemComposition.class, (name, values) -> {
                    if (name.equals("getName")) { return weaponName; }
                    throw new AssertionError(name);
                });
                case "getEnum": return proxy(EnumComposition.class, (name, values) -> {
                    if ((int) args[0] == EnumID.WEAPON_STYLES && name.equals("getIntValue")) { return 1000; }
                    if (name.equals("getIntVals"))
                    {
                        int[] ids = new int[combatStyles.length];
                        for (int i = 0; i < ids.length; ++i) { ids[i] = i; }
                        return ids;
                    }
                    throw new AssertionError(name);
                });
                case "getStructComposition": return proxy(StructComposition.class, (name, values) -> {
                    if (name.equals("getStringValue")) { return combatStyles[(int) args[0]]; }
                    throw new AssertionError(name);
                });
                case "getEnergy": return 10000;
                case "isKeyPressed": return control;
                case "isClientThread": return true;
                case "loadAnimation": return proxy(Animation.class, (name, values) -> {
                    if (name.equals("getNumFrames")) { return 8; }
                    throw new AssertionError("animation: " + name);
                });
                case "getWorldView": case "getTopLevelWorldView": return view;
                case "isRuneLiteObjectRegistered": return objects.contains(args[0]);
                case "registerRuneLiteObject": objects.add((RuneLiteObjectController) args[0]); return null;
                case "removeRuneLiteObject": objects.remove(args[0]); return null;
                default: throw new AssertionError("client: " + method);
            }
        }
    }
}
