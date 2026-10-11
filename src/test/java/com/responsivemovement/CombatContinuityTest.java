package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real controller, native action/engagement doubles and explicitly observed target steps. */
public class CombatContinuityTest
{
    @Test
    public void firstEngagementAtOrAfterPendingExpiryCannotReviveAClick()
    {
        for (int millis : new int[] {1800, 1801, 3000})
        {
            Fixture f = fixture(14, 10, 15, 10, null);
            try
            {
                NpcApproach target = NpcApproach.capture(f.client, f.view,
                    f.event(MenuAction.NPC_SECOND_OPTION, "Attack"), null);
                CombatContinuity combat = new CombatContinuity(target, f.authority, f.now);
                f.interacting = f.npc;
                f.at(millis);
                assertFalse(combat.observe(f.client, f.player, f.view, f.authority, false, f.now));
                assertFalse(combat.engaged());
            }
            finally { f.controller.close(); }
        }
    }

    @Test
    public void engagementBeforeExpirySurvivesLaterFramesButStillRetiresOnTargetLoss()
    {
        Fixture f = fixture(14, 10, 15, 10, null);
        try
        {
            NpcApproach target = NpcApproach.capture(f.client, f.view,
                f.event(MenuAction.NPC_SECOND_OPTION, "Attack"), null);
            CombatContinuity combat = new CombatContinuity(target, f.authority, f.now);
            f.interacting = f.npc;
            f.at(1799);
            assertTrue(combat.observe(f.client, f.player, f.view, f.authority, false, f.now));
            assertTrue(combat.engaged());
            f.at(4000);
            assertTrue(combat.observe(f.client, f.player, f.view, f.authority, false, f.now));
            f.interacting = null;
            assertFalse(combat.observe(f.client, f.player, f.view, f.authority, false, f.now));
        }
        finally { f.controller.close(); }
    }

    @Test
    public void skippedControllerPreparationRetiresLateEngagementAndTargetFacing()
    {
        List<MovementTrace.Entry> trace = new ArrayList<>();
        Fixture f = fixture(14, 10, 15, 10, trace);
        try
        {
            f.click("Attack");
            f.interacting = f.npc;
            f.frame(1800);
            assertEquals(f.authority, f.controller.position());
        }
        finally { f.controller.close(); }
        assertTrue(trace.stream().filter(e -> e instanceof MovementTrace.Sample)
            .map(e -> (MovementTrace.Sample) e).anyMatch(s ->
                s.startDecision.equals("combat-follow-ended") && s.combatPhase.equals("none") && !s.combatEngaged));
        assertTrue(f.objects.isEmpty());
    }

    private static LocalPoint p(int x, int y) { return new LocalPoint(x * 128 + 64, y * 128 + 64, 0); }
    private static Fixture fixture(int x, int y, int nx, int ny, List<MovementTrace.Entry> trace)
    {
        Fixture f = new Fixture(p(x, y).getX(), p(x, y).getY(), p(nx, ny).getX(), p(nx, ny).getY(), trace);
        f.weaponId = 26484; f.weaponName = "Captured melee whip"; f.speed = 1.3;
        return f;
    }
    private static void move(Fixture f, int x, int y) { f.area = new WorldArea(3200 + x, 3200 + y, 1, 1, 0); }

    @Test
    public void stationaryTargetHasOneRunApproachThenAHeldPositionThroughTheNativeHit()
    {
        List<MovementTrace.Entry> trace = new ArrayList<>();
        Fixture f = fixture(10, 10, 15, 10, trace);
        f.click("Attack"); f.frame(0);
        for (int ms = 20; ms <= 1600; ms += 20)
        {
            if (ms == 300) { f.authority = p(12, 10); f.interacting = f.npc; }
            if (ms == 1100) { f.authority = p(14, 10); f.animation = 1658; }
            f.frame(ms);
            assertEquals("normal run pace and no two-tile early pause", Math.min(p(14, 10).getX(), Math.round(p(10, 10).getX() + ms * 0.52)),
                f.controller.position().getX());
            assertEquals(p(10, 10).getY(), f.controller.position().getY());
        }
        LocalPoint held = f.controller.position();
        for (int ms = 1620; ms <= 4000; ms += 20)
        {
            if (ms == 2000) { f.animation = -1; }
            if (ms == 2800) { f.animation = 1658; }
            f.frame(ms);
            assertEquals("stationary NPC must not create another tile of following", held, f.controller.position());
        }
        assertEquals("native action is never selected/restarted by the plugin", 1658, f.animation);
        f.controller.close();
        assertTrue(trace.stream().filter(e -> e instanceof MovementTrace.Sample).map(e -> (MovementTrace.Sample) e)
            .anyMatch(s -> s.combatEngaged && s.combatPhase.equals("following") && s.npcReserveTiles == 1));
        assertTrue(f.objects.isEmpty());
    }

    @Test
    public void aRealNpcStepSpendsOneHeldCreditAndDoesNotMoveWhileTheNpcStaysStill()
    {
        Fixture f = fixture(14, 10, 15, 10, null);
        f.interacting = f.npc; f.click("Attack");
        for (int ms = 0; ms <= 4000; ms += 20) { f.frame(ms); assertEquals(f.start, f.controller.position()); }
        move(f, 16, 10);
        f.frame(4020);
        assertEquals("one slower follow budget from the same visible point", f.start.getX() + 3, f.controller.position().getX());
        for (int ms = 4040; ms <= 4800; ms += 20)
        {
            if (ms == 4260)
            {
                f.authority = p(15, 10); f.animation = 1658;
                f.at(ms); f.controller.combatHit(f.npc, CombatFacingPacingTest.hit(true));
            }
            f.frame(ms);
            assertTrue(f.controller.position().getX() <= p(15, 10).getX());
        }
        for (int ms = 4820; ms <= 6000; ms += 20) { f.frame(ms); assertEquals(p(15, 10), f.controller.position()); }
        f.controller.close();
    }

    @Test
    public void repeatedNpcMotionCannotRenewPredictionWithoutPlayerProgress()
    {
        Fixture f = fixture(14, 10, 15, 10, null);
        f.interacting = f.npc; f.click("Attack"); f.frame(0);
        move(f, 16, 10); f.frame(20);
        for (int ms = 40; ms <= 6000; ms += 20)
        {
            if (ms == 300) { move(f, 17, 10); }
            if (ms == 600) { move(f, 18, 10); }
            if (ms == 1000) { move(f, 19, 10); }
            if (ms == 2000) { move(f, 20, 10); }
            if (ms == 4000) { move(f, 21, 10); }
            f.frame(ms);
            assertTrue("original distance bound", MovementPath.distance(f.start, f.controller.position()) <= 5 * 128);
        }
        assertEquals("rejected follow reconciles and cannot restart itself", f.start, f.controller.position());
        f.controller.close();
    }

    @Test
    public void targetMovementReplansFromAFractionWithoutASecondMovementOrTurnBudget()
    {
        Fixture f = fixture(10, 10, 17, 12, null);
        f.interacting = f.npc; f.click("Attack"); f.frame(0); f.frame(100);
        LocalPoint before = f.controller.position(); int angle = f.controller.orientation();
        move(f, 18, 13); f.frame(120);
        assertTrue(MovementPath.distance(before, f.controller.position()) <= 11);
        assertTrue(Math.abs(MotionMath.difference(angle, f.controller.orientation())) <= 37);
        assertTrue("no cancellation to the old tile on a normal target step", f.controller.position().getX() >= before.getX());
        f.authority = p(12, 10); f.frame(140);
        assertTrue(MovementPath.distance(before, f.controller.position()) <= 22);
        f.controller.close();
    }

    @Test
    public void meleeNativePoseDoesNotRetireItsMatchingEngagedTarget()
    {
        Fixture f = fixture(10, 10, 15, 10, null);
        f.interacting = f.npc; f.animation = 4177;
        f.controller.worldInteraction(f.event(MenuAction.NPC_SECOND_OPTION, "Attack"));
        f.frame(0); f.frame(100);
        assertTrue(f.controller.position().getX() > f.start.getX());
        assertEquals(4177, f.animation);
        f.animation = 1658;
        LocalPoint before = f.controller.position(); f.frame(120);
        assertTrue(f.controller.position().getX() >= before.getX());
        assertEquals(1658, f.animation);
        f.controller.close();
    }

    @Test
    public void weaponTargetModeAndLifecycleChangesRetireTheFollowSymmetrically()
    {
        for (int change = 0; change < 7; ++change)
        {
            Fixture f = fixture(10, 10, 15, 10, null);
            f.interacting = f.npc; f.click("Attack"); f.frame(0); f.frame(100);
            if (change == 0) { f.weaponId++; }
            if (change == 1) { f.attackStyle++; }
            if (change == 2) { f.present = false; }
            if (change == 3) { f.interacting = null; }
            if (change == 4) { f.starts = false; }
            if (change == 5) { f.controller.sceneChanged(false); }
            if (change == 6) { f.controller.walkClick(); f.destination = null; }
            for (int ms = 120; ms <= 3000; ms += 20) { f.frame(ms); }
            assertEquals("change " + change, f.authority, f.controller.position());
            f.controller.close(); assertTrue(f.objects.isEmpty());
        }
    }

    @Test
    public void stationaryUnconfirmedClickExpiresAndDeadOrDiscontinuousTargetsAreNotFollowed()
    {
        for (boolean jump : new boolean[] {false, true})
        {
            Fixture f = fixture(10, 10, 15, 10, null);
            f.click("Attack"); f.frame(0); f.frame(100);
            if (jump) { move(f, 25, 10); }
            for (int ms = 120; ms <= 4000; ms += 20) { f.frame(ms); }
            assertEquals(f.start, f.controller.position());
            f.controller.close();
        }
    }

    @Test
    public void rangedHalberdAndUnknownStylesKeepTheirConservativeApproachPolicy()
    {
        assertTrue(CombatApproach.adjacentMelee("Abyssal whip", "Controlled", false, 2));
        assertTrue(CombatApproach.adjacentMelee("", "Accurate", false, 0));
        assertFalse(CombatApproach.adjacentMelee("Noxious halberd", "Aggressive", false, 1));
        assertFalse(CombatApproach.adjacentMelee("Magic shortbow", "Rapid", true, 1));
        assertFalse(CombatApproach.adjacentMelee("Staff", "Casting", false, 4));
        assertFalse(CombatApproach.adjacentMelee("Unknown", "Other", false, 0));
    }

    @Test
    public void blockedFollowingCannotCrossAnUnavailableCorridor()
    {
        Fixture f = fixture(14, 10, 15, 10, null);
        f.interacting = f.npc; f.click("Attack"); f.frame(0); f.frame(100);
        java.util.Arrays.fill(f.flags[15], net.runelite.api.CollisionDataFlag.BLOCK_MOVEMENT_FULL);
        move(f, 16, 10);
        for (int ms = 120; ms <= 2000; ms += 20) { f.frame(ms); assertEquals(f.start, f.controller.position()); }
        f.controller.close();
    }

    @Test
    public void deadOrDifferentIdentityTargetsStopFollowing()
    {
        for (boolean dead : new boolean[] {false, true})
        {
            Fixture f = fixture(10, 10, 15, 10, null);
            f.interacting = f.npc; f.click("Attack"); f.frame(0); f.frame(100);
            if (dead) { f.npcDead = true; }
            else { ++f.npcId; }
            for (int ms = 120; ms <= 3000; ms += 20) { f.frame(ms); }
            assertEquals(f.authority, f.controller.position());
            f.controller.close();
        }
    }

    @Test
    public void walkingModeAndControlStartGateRemainNative()
    {
        Fixture f = fixture(14, 10, 15, 10, null);
        f.interacting = f.npc; f.run = false; f.click("Attack"); f.frame(0); f.frame(100);
        move(f, 16, 10); f.frame(120);
        assertEquals("post-arrival following stays below walking pace", f.start.getX() + 3, f.controller.position().getX());
        f.controller.close();
        Fixture control = fixture(10, 10, 15, 10, null);
        control.interacting = control.npc; control.control = true;
        control.click("Attack"); control.frame(0); control.frame(100);
        assertEquals(control.start, control.controller.position());
        control.controller.close();
    }

    @Test
    public void combatTraceCopiesUpdatedFootprintsAndNativeEngagementWithoutWorkerActorReads()
    {
        List<MovementTrace.Entry> trace = new ArrayList<>();
        Fixture f = fixture(14, 10, 15, 10, trace);
        f.interacting = f.npc; f.click("Attack"); f.frame(0); f.frame(100);
        move(f, 16, 10); f.frame(120); f.controller.pollTrace();
        f.controller.close();
        MovementTrace.Sample observed = trace.stream().filter(e -> e instanceof MovementTrace.Sample)
            .map(e -> (MovementTrace.Sample) e).filter(s -> s.npcMinX == p(16, 10).getX()).findFirst().get();
        move(f, 17, 10);
        assertEquals(p(16, 10).getX(), observed.npcMinX);
        assertTrue(observed.combatEngaged);
        assertTrue(observed.line().contains("combatPhase=following combatEngaged=true"));
    }
}
