package com.responsivemovement;

import java.util.function.BiPredicate;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** Delayed preceding-click publications in the user's 20:27--20:33 bookmarks. */
public class LatestBookmarkAuthorityTest
{
    private static final BiPredicate<LocalPoint,LocalPoint> OPEN = (a,b) -> true;
    private static LocalPoint p(int x, int y) { return new LocalPoint(x,y,0); }

    @Test
    public void forwardAuthorityJoinDoesNotVisitTheOldCornerBeforeTheLatestGoal()
    {
        // #7407 / #7051: previous northwest click, then west; its delayed
        // northern endpoint must not turn a southbound displayed join north again.
        MovementPath path = MovementPath.anticipate(p(6720,6592),p(6720,6592),p(6592,6720),false,
            0,1.18,MovementPath.freshDeadline(0),OPEN);
        advance(path,0,460);
        path = path.retargetWalk(p(6592,6592),false,460_000_000L); assertNotNull(path);
        advance(path,460,500);
        LocalPoint before = path.position();
        MovementPath joined = path.alignWalkAuthority(p(6592,6720),false,500_000_000L);
        assertNotNull(joined); assertEquals(before,joined.position());
        joined.advance(520_000_000L);
        assertTrue("latest southbound goal, not preceding northern authority " + joined.traceSnapshot().fields(0),
            joined.position().getY() < before.getY());
        assertTrue(joined.clear());
    }

    @Test
    public void perpendicularOldAuthorityDoesNotRejectTheFreshWalkOrRenewItsBudget()
    {
        // #10085 / #10011 and #10262 / #10195: a preceding southward one-tile
        // endpoint arrives after a new west/east Walk, then the new endpoint follows.
        MovementPath path = MovementPath.anticipate(p(5056,6720),p(5056,6720),p(5056,6592),false,
            0,1.18,MovementPath.freshDeadline(0),OPEN);
        advance(path,0,400);
        path = path.retargetWalk(p(4928,6720),false,400_000_000L); assertNotNull(path);
        advance(path,400,450);
        LocalPoint before = path.position(); long hard = path.predictionDeadlineNanos();
        assertTrue(path.accept(p(5056,6592),false));
        assertEquals("observation preserves exact fraction",before,path.position());
        assertEquals("old perpendicular progress creates no prediction credit",hard,path.predictionDeadlineNanos());
        assertEquals("the latest checked Walk remains pending","preview",path.phase());
        path.advance(470_000_000L);
        assertTrue("no detour back south",path.position().getY() >= before.getY());
        assertTrue(path.clear());
        assertTrue(path.accept(p(4928,6720),false));
        advance(path,470,1400);
        assertEquals(p(4928,6720),path.position()); assertTrue(path.finished());
    }

    @Test
    public void diagonalToKnightTurnDoesNotBowTowardThePrecedingClick()
    {
        // #19352 / #19247 has an east/south first control on an east/north
        // departure. This representative whole-knight fraction isolates that
        // partial-axis tangent policy (not the capture's complete native clock).
        MovementPath path = MovementPath.anticipate(p(7488,6336),p(7488,6336),p(7744,6208),true,
            0,1.18,MovementPath.freshDeadline(0),OPEN);
        advance(path,0,200);
        LocalPoint before = path.position();
        path = path.retargetWalk(p(7744,6464),true,200_000_000L); assertNotNull(path);
        path.advance(220_000_000L);
        assertTrue("no old southbound tangent excursion " + path.traceSnapshot().fields(0),
            path.position().getY() >= before.getY());
        assertTrue("preserve eastbound continuity",path.position().getX() > before.getX());
        assertTrue("still use a smooth local join",path.traceSnapshot().fields(0).matches(".* joins=\\[[0-9].*"));
        assertTrue(path.clear());
    }

    @Test
    public void nativeInteractionDoesNotInheritThePrecedingWalksPerpendicularRetention()
    {
        MovementPath path = MovementPath.anticipate(p(5056,6720),p(5056,6720),p(5056,6592),false,
            0,1.18,MovementPath.freshDeadline(0),OPEN);
        path.pendingWalkConfirmation(); advance(path,0,400);
        path = path.retargetInteraction(p(4928,6720),false,400_000_000L,false); assertNotNull(path);
        advance(path,400,450);
        assertTrue(path.accept(p(5056,6592),false));
        assertNotEquals("native refinements retain their own authority evidence","preview",path.phase());
        assertTrue(path.clear());
    }

    @Test
    public void expiredPerpendicularForecastRecoversRatherThanRearming()
    {
        MovementPath path = MovementPath.anticipate(p(5056,6720),p(5056,6720),p(5056,6592),false,
            0,1.18,MovementPath.freshDeadline(0),OPEN);
        advance(path,0,400);
        path = path.retargetWalk(p(4928,6720),false,400_000_000L); assertNotNull(path);
        advance(path,400,1300);
        assertNotEquals("preview ends at its existing response bound","preview",path.phase());
        assertTrue(path.accept(p(5056,6592),false)); advance(path,1300,2300);
        assertEquals(p(5056,6592),path.position()); assertTrue(path.finished());
    }

    @Test
    public void resumedWalkRetainsItsFractionAndOriginalPerpendicularForecastDeadline()
    {
        MovementPath path = MovementPath.anticipate(p(5056,6720),p(5056,6720),p(5056,6592),false,
            0,1.18,MovementPath.freshDeadline(0),OPEN);
        advance(path,0,400);
        path = path.retargetWalk(p(4928,6720),false,400_000_000L); assertNotNull(path);
        advance(path,400,450);
        LocalPoint before = path.position(); long hard = path.predictionDeadlineNanos();
        assertTrue(path.resumeScene(-128,-256,p(4928,6464),p(4800,6464),false,450_000_000L));
        assertEquals(p(before.getX()-128,before.getY()-256),path.position());
        assertTrue(path.accept(p(4928,6336),false));
        assertEquals("rebasing/old authority creates no extra reserve",hard,path.predictionDeadlineNanos());
        assertEquals("Walk ownership survives a supported rebase","preview",path.phase());
        assertTrue(path.clear());
    }

    @Test
    public void everyNewAuthoritySquareEdgeStillRequiresBothDirections()
    {
        LocalPoint a = p(6720,6720), b = p(6592,6592), c = p(6720,6592), d = p(6592,6720);
        for (LocalPoint[] edge : new LocalPoint[][] {{a,b},{c,d},{a,c},{a,d},{b,c},{b,d}})
        {
            for (boolean reverse : new boolean[] {false,true})
            {
                LocalPoint from = edge[reverse ? 1 : 0], to = edge[reverse ? 0 : 1];
                boolean[] closed = {false};
                BiPredicate<LocalPoint,LocalPoint> collision = (x,y) -> !closed[0] || !x.equals(from) || !y.equals(to);
                MovementPath path = MovementPath.anticipate(c,c,d,false,0,1.18,MovementPath.freshDeadline(0),collision);
                advance(path,0,460); path = path.retargetWalk(b,false,460_000_000L); assertNotNull(path);
                advance(path,460,500); LocalPoint before = path.position(); closed[0] = true;
                MovementPath joined = path.alignWalkAuthority(d,false,500_000_000L);
                if (joined != null) { assertTrue("a fallback can only use surviving proofs",joined.clear()); }
                assertEquals("proof queries never advance position",before,path.position());
            }
        }
    }

    private static void advance(MovementPath path, int from, int to)
    {
        for (int ms = from+20; ms < to; ms += 20) { path.advance(ms*1_000_000L); }
        path.advance(to*1_000_000L);
    }
}
