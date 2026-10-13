package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import java.util.function.BiPredicate;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** Evidence, geometry, lifetime and frame-budget guards for shared scene-approach handling. */
public class InteractionContinuityGuardTest
{
    private static final BiPredicate<LocalPoint, LocalPoint> OPEN = (a, b) -> true;
    private static LocalPoint p(int x, int y) { return new LocalPoint(x, y, 0); }

    @Test
    public void repeatedPickupCannotRenewTheOriginalPredictionWithoutAuthority()
    {
        Fixture single = new Fixture(5952, 3904, 7000, 7000), repeated = new Fixture(5952, 3904, 7000, 7000);
        MidnightContinuityTest.take(single, 233, 39, 31); MidnightContinuityTest.take(repeated, 233, 39, 31);
        single.destination = repeated.destination = p(5056, 4032);
        for (int ms = 0; ms <= 2800; ms += 20)
        {
            if (ms > 0 && ms <= 800 && ms % 100 == 0) { repeated.at(ms); MidnightContinuityTest.take(repeated, 233, 39, 31); }
            single.frame(ms); repeated.frame(ms);
            assertEquals("same repeat cannot renew recovery/deadlines", single.controller.position(), repeated.controller.position());
        }
        assertEquals(repeated.start, repeated.controller.position());
        single.controller.close(); repeated.controller.close();
    }

    @Test
    public void changingItemTileOrActionRetiresTheOldForecastEvenIfTheFlagHasNotChanged()
    {
        for (int change = 0; change < 3; ++change)
        {
            Fixture f = new Fixture(5952, 3904, 7000, 7000);
            MidnightContinuityTest.take(f, 233, 39, 31);
            f.destination = p(5056, 4032);
            for (int ms = 0; ms <= 200; ms += 20) { f.frame(ms); }
            f.controller.worldInteraction(f.sceneEvent(change == 2 ? MenuAction.GROUND_ITEM_SECOND_OPTION : MenuAction.GROUND_ITEM_THIRD_OPTION,
                "Take", change == 0 ? 234 : 233, change == 1 ? 40 : 39, 31));
            f.authority = p(5696, 3904);
            for (int ms = 220; ms <= 1600; ms += 20) { f.frame(ms); }
            assertEquals("unchanged old flag cannot rearm a different interaction", f.authority, f.controller.position());
            f.controller.close();
        }
    }

    @Test
    public void pendingRepeatsAndSceneChangesDoNotExtendNativeDestinationObservation()
    {
        Fixture f = new Fixture(5952, 3904, 7000, 7000);
        MidnightContinuityTest.take(f, 233, 39, 31); f.frame(0);
        f.at(80); MidnightContinuityTest.take(f, 233, 39, 31); f.frame(80);
        f.destination = p(5056, 4032); // Too late for the original 100-ms observation.
        for (int ms = 120; ms <= 500; ms += 20) { f.frame(ms); }
        assertEquals(f.start, f.controller.position());
        f.controller.sceneChanged(false); f.destination = null; f.frame(520);
        MidnightContinuityTest.take(f, 233, 39, 31); f.destination = p(5056, 4032);
        f.frame(540); f.frame(560);
        assertTrue("a fresh scene click can start normally", f.controller.position().getX() < f.start.getX());
        f.controller.close();
    }

    @Test
    public void uncheckedAndUnfinishedConfirmedConnectorsRetainTheOriginalGuards()
    {
        boolean[] blocked = {false};
        MovementPath preview = MovementPath.anticipate(p(5952, 3904), p(5952, 3904), p(5056, 4032), true,
            0, 1.2, MovementPath.freshDeadline(0), (a, b) -> !blocked[0]);
        preview.advance(100_000_000L);
        LocalPoint before = preview.position();
        blocked[0] = true;
        assertNull(preview.retargetInteraction(p(4544, 3904), true, 100_000_000L, false));
        assertEquals(before, preview.position());
        MovementPath confirmed = MovementPath.idle(p(5952, 3904), 0, 1.2, OPEN);
        assertTrue(confirmed.accept(p(5952, 4160), true)); confirmed.advance(100_000_000L);
        assertNull(confirmed.retargetInteraction(p(4544, 3904), true, 100_000_000L, false));
        assertTrue(confirmed.anticipateContinuation(p(4544, 3904), true, 100_000_000L));
        confirmed.advance(200_000_000L);
        assertEquals("preserve the confirmed northbound prefix", 5952, confirmed.position().getX());
    }

    @Test
    public void confirmedKnightMidpointDoesNotPreserveTheOldUnconfirmedForwardEnd()
    {
        // 00:04:17.581: visible=(4987,3933), authority=(5056,3904),
        // occupied checked knight=(4928,3904)->(5184,4032). The ordinary
        // object click must preserve debt to the confirmed middle tile, not
        // an extra trip to the old Walk's still-unconfirmed forward endpoint.
        // Exact coordinates/clock reconstructed with an explicit open-map double.
        MovementPath path = MovementPath.anticipate(p(4672, 3904), p(4800, 3904), p(5184, 4032), true,
            0, 1.2, MovementPath.freshDeadline(0), OPEN);
        for (int ms = 20; ms <= 660; ms += 20)
        {
            if (ms == 400) { assertTrue(path.accept(p(5056, 3904), true)); }
            path.advance(ms * 1_000_000L);
        }
        LocalPoint fraction = path.position();
        path.replacement(true);
        assertEquals(fraction, path.position());
        assertTrue("mid-chord confirmed debt must become an appendable prefix", path.canAnticipateContinuation());
        assertTrue(path.anticipateContinuation(p(4672, 3776), true, 660_000_000L));
        for (int ms = 680; ms <= 800; ms += 20)
        {
            path.advance(ms * 1_000_000L);
            assertTrue("do not visit the unconfirmed old knight endpoint", path.position().getX() <= 5056);
            assertTrue("trim only through the proven corridor", path.clear());
        }
    }

    @Test
    public void nativeRefinementAndForwardAuthorityUseCheckedFractionalJoinsAndFiniteDeadlines()
    {
        MovementPath original = MovementPath.anticipate(p(5312, 4032), p(5312, 3904), p(4672, 3776), true,
            0, 1.2, MovementPath.freshDeadline(0), OPEN);
        original.advance(100_000_000L);
        long hard = original.predictionDeadlineNanos();
        MovementPath refined = original.retargetApproachDestination(p(4544, 3904), true, 100_000_000L);
        assertNotNull(refined);
        assertEquals(original.position(), refined.position());
        assertEquals("a native refinement is not new time credit", hard, refined.predictionDeadlineNanos());
        assertNull(refined.alignApproachAuthority(refined.confirmed(), true, 100_000_000L));
        assertNull(refined.alignApproachAuthority(p(5568, 3904), true, 100_000_000L));
        for (int ms = 120; ms <= 2600; ms += 20) { refined.advance(ms * 1_000_000L); }
        assertEquals("failed authority still recovers", p(5312, 3904), refined.position());

        MovementPath offset = MovementPath.anticipate(p(5312, 4032), p(5312, 3904), p(4544, 3904), true,
            0, 1.2, MovementPath.freshDeadline(0), OPEN);
        offset.advance(100_000_000L);
        MovementPath aligned = offset.alignApproachAuthority(p(5056, 3904), true, 100_000_000L);
        assertNotNull(aligned);
        assertEquals(offset.position(), aligned.position());
        assertEquals(p(5056, 3904), aligned.confirmed());
        aligned.advance(100_000_000L);
        assertEquals("same preparation cannot spend another frame budget", offset.position(), aligned.position());
        assertTrue(aligned.clear());
    }

    @Test
    public void onRouteApproachArrivalUsesAcceptanceWithoutRebuildingOrResettingItsFraction()
    {
        LocalPoint goal = p(5056, 3904);
        MovementPath path = MovementPath.anticipate(p(5312, 4032), p(5312, 3904), goal, true,
            0, 1, MovementPath.freshDeadline(0), OPEN);
        assertNotNull(path);
        path.advance(100_000_000L);
        LocalPoint before = path.position();
        long hard = path.predictionDeadlineNanos();
        assertNull(path.alignApproachAuthority(goal, true, 100_000_000L));
        assertEquals(before, path.position());
        assertEquals(hard, path.predictionDeadlineNanos());
        assertTrue(path.accept(goal, true));
        assertEquals(before, path.position());
        for (int ms = 120; ms <= 1400; ms += 20) { path.advance(ms * 1_000_000L); }
        assertEquals(goal, path.position());
        assertTrue(path.finished());
        assertTrue(path.clear());
    }

    @Test
    public void confirmedRunTailIsCadenceIndependentAndNeverLeavesItsCheckedKnight()
    {
        LocalPoint start = p(6592, 3136), endpoint = p(6848, 3008);
        MovementPath fine = MovementPath.idle(start, 0, 1.2, OPEN), coarse = MovementPath.idle(start, 0, 1.2, OPEN);
        assertTrue(fine.accept(endpoint, true)); assertTrue(coarse.accept(endpoint, true));
        long hard = fine.predictionDeadlineNanos();
        for (int ms = 10; ms <= 800; ms += 10)
        {
            fine.advance(ms * 1_000_000L, true);
            assertTrue(fine.position().getX() <= endpoint.getX());
            assertTrue(fine.position().getY() >= endpoint.getY());
            assertEquals("tail is confirmed travel, not another forecast", "confirmed", fine.phase());
            assertTrue(fine.clear());
            if (ms % 100 == 0)
            {
                coarse.advance(ms * 1_000_000L, true);
                assertEquals(fine.position(), coarse.position());
            }
        }
        assertEquals(hard, fine.predictionDeadlineNanos());
        assertTrue("small reserve survives a late tick", fine.moving());
        for (int ms = 810; ms <= 1200; ms += 10) { fine.advance(ms * 1_000_000L, true); }
        assertEquals("stale ongoing flag cannot hold confirmed debt indefinitely", endpoint, fine.position());
        assertFalse(fine.moving());
    }

    @Test
    public void confirmedTailEndsForNativeArrivalActionAndReplacementInput()
    {
        for (int release = 0; release < 3; ++release)
        {
            Fixture f = new Fixture(4544, 3904, 7000, 7000);
            f.speed = 1.2; f.destination = p(6720, 3136); f.authority = p(4800, 3904);
            for (int ms = 20; ms <= 600; ms += 20) { f.frame(ms); }
            assertTrue(f.controller.position().getX() < 4800);
            if (release == 0) { f.destination = null; f.nativePoint = f.authority; }
            if (release == 1) { f.animation = 422; }
            if (release == 2) { f.controller.walkClick(); }
            f.frame(620); f.frame(640); f.frame(660);
            assertEquals("release the short confirmed remainder at normal pace", f.authority, f.controller.position());
            f.controller.close();
        }
    }

    @Test
    public void disabledControlAndBlockedSceneClicksCannotUseTheNewJoin()
    {
        for (int gate = 0; gate < 3; ++gate)
        {
            Fixture f = new Fixture(5952, 3904, 7000, 7000);
            if (gate == 0) { f.starts = false; }
            if (gate == 1) { f.control = true; }
            if (gate == 2) { for (int[] column : f.flags) { java.util.Arrays.fill(column, CollisionDataFlag.BLOCK_MOVEMENT_FULL); } }
            MidnightContinuityTest.take(f, 233, 39, 31); f.destination = p(5056, 4032);
            for (int ms = 0; ms <= 500; ms += 20) { f.frame(ms); }
            assertEquals(f.start, f.controller.position());
            f.controller.close();
        }
    }
}
