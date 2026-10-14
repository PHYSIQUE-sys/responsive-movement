package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.AnimationID;
import org.junit.Test;
import static org.junit.Assert.*;

/** Secondary gait follows display; primary action/effect clocks stay entirely native. */
public class CombatVisualContinuityTest
{
    static LocalPoint p(int x, int y) { return new LocalPoint(x, y, 0); }
    static Fixture bow()
    {
        Fixture f = new Fixture(6464, 5824, 6976, 5824);
        f.ranged("Magic shortbow (i)", 12788, 1); f.smoothing = 0;
        selectors(f); f.interacting = f.npc; f.click("Attack"); f.frame(0); f.frame(200);
        return f;
    }

    static void selectors(Fixture f)
    {
        f.poses.put("IdlePoseAnimation", 808); f.poses.put("WalkAnimation", 819); f.poses.put("RunAnimation", 824);
        for (String key : new String[] {"IdleRotateLeft", "IdleRotateRight", "WalkRotateLeft", "WalkRotateRight", "WalkRotate180"})
        {
            f.poses.put(key, 819);
        }
        f.poses.put("PoseAnimation", 808); f.poses.put("PoseAnimationFrame", 2); f.poses.put("AnimationFrame", 4);
    }

    @Test
    public void anOwnedBowShotEffectDoesNotRejectTheNativeWalkPublication()
    {
        Fixture f = bow(); f.animation = AnimationID.HUMAN_BOW; f.spot = true;
        f.controller.walkClick(); f.destination = p(6976, 5824);
        f.frame(220); f.frame(240);
        assertTrue("Walk must respond during the owned shot/effect", f.controller.position().getX() > f.start.getX());
        assertEquals(AnimationID.HUMAN_BOW, f.animation); assertEquals(4, (int) f.poses.get("AnimationFrame"));
        f.controller.close();
    }

    @Test
    public void aPendingCombatWalkSurvivesItsShotGraphicAppearingBeforePreparation()
    {
        Fixture f = bow(); f.controller.walkClick();
        f.animation = AnimationID.HUMAN_BOW; f.spot = true; f.destination = p(6976, 5824);
        f.frame(220); f.frame(240);
        assertTrue(f.controller.position().getX() > f.start.getX());
        f.controller.close();
    }

    @Test
    public void stationaryShootPoseCannotBorrowRunningFromTheHiddenActor()
    {
        Fixture f = bow(); f.controller.close(); f.animation = AnimationID.HUMAN_BOW; f.spot = true;
        f.poses.put("PoseAnimation", 824); f.poses.put("PoseAnimationFrame", 5);
        f.nativePoint = p(6540, 5824);
        PlayerPresentation presentation = new PlayerPresentation(f.client); presentation.owner(f.player);
        assertTrue(presentation.prepare(f.start, 0, false, false, false, true, false));
        assertEquals("stationary displayed player selects native idle gait", 808, f.player.getPoseAnimation());
        assertEquals("primary shot omits idle secondary instead of running on the spot", -1, f.builtPose);
        assertEquals(AnimationID.HUMAN_BOW, f.builtAction); assertEquals(4, f.builtActionFrame);
        presentation.close(); f.controller.close();
    }

    @Test
    public void movingDuringAShotUsesNativeRunGaitAndKeepsTheNativePrimaryFrame()
    {
        Fixture f = bow(); f.controller.close(); f.animation = AnimationID.HUMAN_BOW;
        f.poses.put("PoseAnimation", 819); f.poses.put("PoseAnimationFrame", 3);
        PlayerPresentation presentation = new PlayerPresentation(f.client); presentation.owner(f.player);
        assertTrue(presentation.prepare(p(6508, 5824), 1536, true, true, false, true, true));
        assertEquals(824, f.player.getPoseAnimation());
        assertEquals("native tick idle selector preserves the gait clock while actor is stationary", 824, f.player.getIdlePoseAnimation());
        f.objects.forEach(object -> object.getModel());
        assertEquals("native builder must see the real idle selector when blending the primary shot", 808, f.builtIdle);
        assertEquals(824, f.builtPose);
        assertEquals(AnimationID.HUMAN_BOW, f.builtAction); assertEquals(4, f.builtActionFrame);
        assertEquals("temporary builder selector is restored after draw", 824, f.player.getIdlePoseAnimation());
        for (int frame = 2; frame <= 5; ++frame)
        {
            f.poses.put("PoseAnimationFrame", frame);
            presentation.prepare(p(6508 + frame * 8, 5824), 1536, true, true, false, true, true);
            assertEquals("native gait frame advances without a second controller/reset", frame, f.player.getPoseAnimationFrame());
            assertEquals(4, (int) f.poses.get("AnimationFrame"));
        }
        presentation.close(); f.controller.close();
    }

    @Test
    public void aSpotEffectWithoutAPrimaryActionStillUsesTheDisplayedIdleOrGait()
    {
        Fixture f = bow(); f.controller.close(); f.spot = true; f.animation = -1;
        PlayerPresentation presentation = new PlayerPresentation(f.client); presentation.owner(f.player);
        f.poses.put("PoseAnimation", 824);
        presentation.prepare(f.start, 0, false, false, false, true, false);
        assertEquals(808, f.player.getPoseAnimation());
        presentation.prepare(p(6500, 5824), 0, true, true, false, true, true);
        assertEquals(824, f.player.getPoseAnimation());
        assertTrue("native effect is retained", f.spot);
        presentation.close(); f.controller.close();
    }

    @Test
    public void unrelatedEffectsOutsideCombatAndNativeLocationActionsKeepTheirExistingStartGate()
    {
        for (int gate = 0; gate < 3; ++gate)
        {
            Fixture f = bow(); f.animation = gate == 0 ? AnimationID.HUMAN_UNARMEDBLOCK : AnimationID.HUMAN_BOW;
            if (gate == 1) { f.weaponName = "Unknown bow"; f.weaponId++; }
            if (gate == 2) { f.animation = AnimationID.HUMAN_CASTTELEPORT; }
            if (gate != 2)
            {
                f.controller.worldInteraction(f.sceneEvent(MenuAction.GAME_OBJECT_FIRST_OPTION, "Chop down", 10822, 54, 45));
            }
            f.spot = true; f.controller.walkClick(); f.destination = p(6976, 5824);
            f.frame(220); f.frame(240); assertEquals(f.start, f.controller.position());
            f.controller.close();
        }
    }

    @Test
    public void newlyReplacedEffectsCannotInheritTheCapturedShotException()
    {
        Fixture f = bow(); f.animation = AnimationID.HUMAN_BOW; f.spot = true;
        f.controller.walkClick(); f.spotId = 2; f.destination = p(6976, 5824);
        f.frame(220); f.frame(240); assertEquals(f.start, f.controller.position());
        f.controller.close();
    }

    @Test
    public void ordinaryInteractionRetiresTheCombatEffectException()
    {
        Fixture f = bow(); f.animation = AnimationID.HUMAN_BOW; f.spot = true;
        f.controller.walkClick();
        f.controller.worldInteraction(f.sceneEvent(MenuAction.GROUND_ITEM_THIRD_OPTION, "Take", 526, 54, 45));
        f.destination = p(6976, 5824); f.frame(220); f.frame(240);
        assertEquals(f.start, f.controller.position()); f.controller.close();
    }

    @Test
    public void repeatedWalksCannotRenewTheSameShotEvidenceOrItsUnconfirmedMovement()
    {
        Fixture f = bow(); f.animation = AnimationID.HUMAN_BOW; f.spot = true;
        f.controller.walkClick(); f.destination = p(7104, 5824);
        for (int ms = 220; ms <= 3000; ms += 20)
        {
            if (ms % 200 == 0 && ms <= 1200) { f.controller.walkClick(); }
            f.frame(ms);
            assertEquals(4, (int) f.poses.get("AnimationFrame"));
        }
        assertEquals("no authority: finite prediction must still reconcile", f.start, f.controller.position());
        f.controller.close();
    }

    @Test
    public void aGraphicFirstAppearingAfterTheObservationWindowCannotRearmTheWalk()
    {
        Fixture f = bow(); f.animation = AnimationID.HUMAN_BOW; f.controller.walkClick(); f.frame(320);
        f.spot = true; f.destination = p(6976, 5824);
        f.frame(340); f.frame(360); assertEquals(f.start, f.controller.position());
        f.controller.close();
    }

    @Test
    public void shotCarryChecksProfileAndSceneLifetimeAndStillKeepsWalkSmoothing()
    {
        for (int gate = 0; gate < 3; ++gate)
        {
            Fixture f = bow(); f.smoothing = 60; f.animation = AnimationID.HUMAN_BOW; f.spot = true;
            f.controller.walkClick(); f.destination = p(6976, 5824);
            f.frame(220); assertEquals(f.start, f.controller.position());
            if (gate == 0) { f.weaponId++; }
            if (gate == 1) { f.controller.sceneChanged(false); f.destination = null; }
            f.frame(240); assertEquals(f.start, f.controller.position());
            f.frame(260); f.frame(280);
            if (gate == 2) { assertTrue(f.controller.position().getX() > f.start.getX()); }
            else { assertEquals(f.start, f.controller.position()); }
            f.controller.close();
        }
    }

    @Test
    public void aCapturedEffectCanProgressAndDisappearWithoutReclaimingTheTargetOrRestartingTheShot()
    {
        Fixture f = bow(); f.animation = AnimationID.HUMAN_BOW; f.spot = true;
        f.controller.walkClick(); f.destination = p(6976, 5824);
        f.frame(220); f.spotFrame = 3; f.frame(240);
        LocalPoint before = f.controller.position();
        f.animation = -1; f.frame(260);
        assertTrue(f.controller.position().getX() > before.getX());
        f.spot = false; f.frame(280);
        assertEquals(824, f.player.getPoseAnimation());
        assertEquals(4, (int) f.poses.get("AnimationFrame"));
        f.controller.close();
    }

    @Test
    public void clickDiagnosticsCopyEffectIdentityAndPrimaryFrameBeforeAsynchronousFormatting()
    {
        Fixture f = bow(); f.animation = AnimationID.HUMAN_BOW; f.spot = true; f.spotFrame = 3;
        MovementTraceContext context = new MovementTraceContext(1, 2, 3, f.client, new ResponsiveMovementConfig() {}, 0,
            "WALK", null, f.start, null);
        f.spotId = 99; f.spotFrame = 7; f.poses.put("AnimationFrame", 6);
        String line = java.util.concurrent.CompletableFuture.supplyAsync(context::line).join();
        assertTrue(line.contains("actionFrame=4 spotEffects=[1,1,0,3] spotEffectsComplete=true"));
        f.controller.close();
    }

    @Test
    public void allWeaponProfilesCanReleaseWalkDuringTheirNativeAttackAndEffect()
    {
        for (int profile = 0; profile < 7; ++profile)
        {
            Fixture f = new Fixture(6464, 5824, 6976, 5824);
            selectors(f); f.smoothing = 0;
            if (profile == 0) { f.weaponName = "Melee weapon"; f.weaponId = 100; }
            if (profile == 1) { f.weaponName = "Halberd"; f.weaponId = 101; }
            if (profile == 2) { f.ranged("Magic shortbow (i)", 12788, 1); }
            if (profile == 3) { f.ranged("Unknown new ranged weapon", 102, 1); }
            if (profile == 4)
            {
                f.weaponName = "Magic weapon"; f.weaponId = 103; f.category = 18;
                f.combatStyles = new String[] {"Casting", "Defensive Casting"};
            }
            if (profile == 5) { f.weaponName = "Unknown combat weapon"; f.weaponId = 104; f.combatStyles = new String[] {"Other"}; }
            // Profile 6 retains the native unarmed equipment/style defaults.
            f.click("Attack"); f.interacting = f.npc; f.frame(0);
            // Deliberately distinct native animation IDs: no weapon/attack-ID
            // whitelist decides eligibility. Production never selects these.
            int nativeAction = 2000 + profile;
            f.animation = nativeAction; f.spot = true;
            f.controller.walkClick(); f.destination = p(6976, 5824); f.frame(20); f.frame(40);
            assertTrue("escape from combat profile " + profile, f.controller.position().getX() > f.start.getX());
            assertEquals(nativeAction, f.animation); assertEquals(4, (int) f.poses.get("AnimationFrame"));
            assertEquals(824, f.player.getPoseAnimation());
            f.controller.close();
        }
    }

    @Test
    public void manualSpellInputRetainsNativeApproachPolicyButCanReleaseItsCastEffect()
    {
        Fixture f = new Fixture(6464, 5824, 6976, 5824);
        selectors(f); f.smoothing = 0; f.weaponName = "Any staff"; f.weaponId = 105;
        f.controller.worldInteraction(f.event(MenuAction.WIDGET_TARGET_ON_NPC, "Cast"));
        f.frame(0); f.frame(80);
        assertEquals("selected spell does not invent a footprint approach", f.start, f.controller.position());
        f.animation = 3001; f.spot = true; f.interacting = null;
        f.controller.walkClick(); f.destination = p(6976, 5824); f.frame(100); f.frame(120);
        assertTrue("recent native spell input is combat escape evidence even after engagement clears", f.controller.position().getX() > f.start.getX());
        assertEquals(3001, f.animation); assertEquals(4, (int) f.poses.get("AnimationFrame"));
        f.controller.close();
    }

    @Test
    public void lingeringEffectsAfterACombatActionEndsStillAllowExplicitWalkRelease()
    {
        Fixture f = bow(); f.animation = -1; f.spot = true;
        f.controller.walkClick(); f.destination = p(6976, 5824); f.frame(220); f.frame(240);
        assertTrue(f.controller.position().getX() > f.start.getX());
        assertEquals(-1, f.animation); f.controller.close();
    }

    @Test
    public void expiredManualSpellEvidenceAndNoncombatWidgetUseCannotRelaxEffects()
    {
        for (boolean expired : new boolean[] {false, true})
        {
            Fixture f = new Fixture(6464, 5824, 6976, 5824);
            selectors(f); f.smoothing = 0;
            f.controller.worldInteraction(f.event(MenuAction.WIDGET_TARGET_ON_NPC, expired ? "Cast" : "Use"));
            f.frame(expired ? 2000 : 80);
            LocalPoint beforeEffect = f.controller.position();
            f.animation = 3001; f.spot = true; f.interacting = null;
            f.controller.walkClick(); f.destination = p(6976, 5824); f.frame(expired ? 2020 : 100); f.frame(expired ? 2040 : 120);
            assertTrue("effects cannot grant further preview travel",f.controller.position().getX() <= beforeEffect.getX());
            for (int ms = expired ? 2060 : 140; ms <= (expired ? 3000 : 1200); ms += 20) { f.frame(ms); }
            assertEquals(f.start, f.controller.position()); f.controller.close();
        }
    }

    @Test
    public void effectExemptionsDoNotDependOnRunModeOrSceneVersusMinimapInput()
    {
        for (boolean run : new boolean[] {false, true})
        {
            for (boolean minimap : new boolean[] {false, true})
            {
                Fixture f = bow(); f.run = run; f.animation = 3002; f.spot = true;
                if (minimap) { f.destination = p(6976, 5824); f.controller.walkClick(null); }
                else { f.controller.walkClick(); f.destination = p(6976, 5824); }
                f.frame(220); f.frame(240);
                assertTrue(f.controller.position().getX() > f.start.getX());
                assertEquals(run ? 824 : 819, f.player.getPoseAnimation());
                assertEquals(3002, f.animation); f.controller.close();
            }
        }
    }

    @Test
    public void attackAndCastEvidenceIsClassifiedByNativeInputRatherThanEquipmentOrAnimationTables()
    {
        assertTrue(MovementController.combatInput(MenuAction.NPC_FIFTH_OPTION, "<col=ff0000>Attack</col>"));
        assertTrue(MovementController.combatInput(MenuAction.PLAYER_FIRST_OPTION, "Attack"));
        assertTrue(MovementController.combatInput(MenuAction.WIDGET_TARGET_ON_NPC, "Cast"));
        assertTrue(MovementController.combatInput(MenuAction.WIDGET_TARGET_ON_PLAYER, "Cast"));
        assertFalse(MovementController.combatInput(MenuAction.NPC_FIRST_OPTION, "Bank"));
        assertFalse(MovementController.combatInput(MenuAction.WIDGET_TARGET_ON_NPC, "Use"));
        assertFalse(MovementController.combatInput(MenuAction.ITEM_USE_ON_NPC, "Use"));
        assertFalse(MovementController.combatInput(MenuAction.CC_OP, "Cast"));
    }
}
