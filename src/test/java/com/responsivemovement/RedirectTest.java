package com.responsivemovement;

import java.util.function.BiPredicate;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Bounded redirect of an unconfirmed preview for native interaction refinements,
 * plus coverage of the fresh Walk entry point on the same captured geometry.
 *
 * Geometry mirrors the recorded 19:54:27 route: a running westbound preview
 * (7360,7488) -> (6720,7360), authority confirmed at (7104,7488), the visible
 * sub-tile at (7064,7488), and a new click behind/south of the occupied
 * horizontal edge.
 */
public class RedirectTest
{
    private static final BiPredicate<LocalPoint, LocalPoint> OPEN = (a, b) -> true;
    private static LocalPoint point(int x, int y) { return new LocalPoint(x, y, 0); }
    private static void advance(MovementPath path, int from, int to)
    {
        for (int ms = from + 10; ms <= to; ms += 10) { path.advance(ms * 1_000_000L); }
    }
    private static long distance(LocalPoint a, LocalPoint b)
    {
        long x = a.getX() - b.getX(), y = a.getY() - b.getY();
        return x * x + y * y;
    }

    private static MovementPath capturedWestboundPreview(BiPredicate<LocalPoint, LocalPoint> collision)
    {
        MovementPath path = MovementPath.anticipate(point(7360, 7488), point(7360, 7488),
            point(6720, 7360), true, 0, 1, MovementPath.freshDeadline(0), collision);
        assertNotNull(path);
        advance(path, 0, 640);
        assertTrue(path.accept(point(7104, 7488), true));
        advance(path, 640, 740);
        assertEquals(point(7064, 7488), path.position());
        return path;
    }

    /** Behind and two tiles south: the forward checked route is a knight chord. */
    private static LocalPoint behind() { return point(7104, 7232); }
    /** One tile south of the backward endpoint: a normal single-edge route. */
    private static LocalPoint nearBehind() { return point(7104, 7360); }

    @Test
    public void capturedNonCollinearWalkRetargetReplacesTheStalePreview()
    {
        MovementPath path = capturedWestboundPreview(OPEN);
        assertFalse(path.canAnticipateContinuation());
        LocalPoint before = path.position();
        MovementPath next = path.retargetWalk(behind(), true, 740_000_000L);
        assertNotNull(next);
        assertEquals(before, next.position());
        assertEquals(path.confirmed(), next.confirmed());
        assertEquals(path.predictionDeadlineNanos(), next.predictionDeadlineNanos());
        advance(next, 740, 940);
        assertTrue("fresh Walk must turn toward the new click", distance(next.position(), behind()) < distance(before, behind()));
        assertTrue(next.clear());
        advance(path, 740, 940);
        assertTrue("building a retarget leaves the original itinerary intact",
            distance(path.position(), behind()) > distance(before, behind()));
    }

    @Test
    public void nonCollinearRedirectTurnsFromTheExactSubTileWithoutASnap()
    {
        MovementPath path = capturedWestboundPreview(OPEN);
        LocalPoint before = path.position();
        MovementPath redirect = path.anticipateRedirect(behind(), true, 740_000_000L);
        assertNotNull(redirect);
        assertEquals("no snap to a tile centre", before, redirect.position());
        assertEquals(point(7104, 7488), redirect.confirmed());
        assertTrue(redirect.clear());
        // The nearer checked endpoint is behind the player, so travel continues
        // east along the occupied edge instead of the stale westbound preview.
        advance(redirect, 740, 840);
        assertTrue(redirect.position().getX() > before.getX());
        assertTrue(distance(redirect.position(), behind()) < distance(before, behind()));
        assertTrue(redirect.clear());
    }

    @Test
    public void redirectReachesTheNewDestinationOnConfirmedAuthority()
    {
        MovementPath path = capturedWestboundPreview(OPEN);
        MovementPath redirect = path.anticipateRedirect(behind(), true, 740_000_000L);
        assertNotNull(redirect);
        assertTrue(redirect.accept(behind(), true));
        advance(redirect, 740, 2600);
        assertEquals(behind(), redirect.position());
        assertTrue(redirect.finished());
        assertTrue(redirect.clear());
    }

    @Test
    public void blockedReverseConnectorCannotCreateAnUnrecoverablePreview()
    {
        // Both anchors need a reversible occupied edge so rejection/timeout can
        // get back safely. The base route was built while both edges were open.
        boolean[] blockReverse = {false};
        BiPredicate<LocalPoint, LocalPoint> collision = (a, b) ->
            !(blockReverse[0] && a.equals(point(6976, 7488)) && b.equals(point(7104, 7488)));
        MovementPath path = capturedWestboundPreview(collision);
        blockReverse[0] = true;
        MovementPath redirect = path.anticipateRedirect(nearBehind(), true, 740_000_000L);
        assertNull(redirect);
    }

    @Test
    public void noLegalConnectorOrRouteLeavesThePreviewUnchanged()
    {
        boolean[] blocked = {false};
        BiPredicate<LocalPoint, LocalPoint> collision = (a, b) -> !blocked[0] && OPEN.test(a, b);
        MovementPath path = capturedWestboundPreview(collision);
        LocalPoint before = path.position();
        blocked[0] = true;
        assertNull(path.anticipateRedirect(behind(), true, 740_000_000L));
        assertEquals(before, path.position());
        assertEquals("preview", path.phase());
    }

    @Test
    public void rapidRedirectsKeepTheOriginalHardDeadline()
    {
        MovementPath path = capturedWestboundPreview(OPEN);
        long original = path.predictionDeadlineNanos();
        MovementPath first = path.anticipateRedirect(behind(), true, 740_000_000L);
        assertNotNull(first);
        assertEquals(original, first.predictionDeadlineNanos());
        advance(first, 740, 790);
        MovementPath second = first.anticipateRedirect(point(6976, 7360), true, 790_000_000L);
        assertNotNull(second);
        assertEquals("rapid clicks cannot renew the hard deadline",
            original, second.predictionDeadlineNanos());
        assertTrue(second.clear());
    }

    @Test
    public void staleOldDirectionConfirmationDoesNotSnapOrCollide()
    {
        MovementPath path = capturedWestboundPreview(OPEN);
        MovementPath redirect = path.anticipateRedirect(behind(), true, 740_000_000L);
        assertNotNull(redirect);
        assertTrue(redirect.accept(point(6848, 7488), true));
        for (int ms = 750; ms <= 2600; ms += 10)
        {
            LocalPoint before = redirect.position();
            redirect.advance(ms * 1_000_000L);
            assertTrue("no sub-tile jump", Math.abs(redirect.position().getX() - before.getX()) <= 4 &&
                Math.abs(redirect.position().getY() - before.getY()) <= 4);
            assertTrue(redirect.clear());
        }
        assertEquals(point(6848, 7488), redirect.position());
        assertTrue(redirect.finished());
    }

    @Test
    public void unconfirmedRedirectTimesOutBackToAuthority()
    {
        MovementPath path = capturedWestboundPreview(OPEN);
        MovementPath redirect = path.anticipateRedirect(behind(), true, 740_000_000L);
        assertNotNull(redirect);
        advance(redirect, 740, 2600);
        assertEquals(point(7104, 7488), redirect.position());
        assertTrue(redirect.finished());
        assertTrue(redirect.clear());
    }

    @Test
    public void queuedPredictionDoesNotAuthorizeDroppingConfirmedCornerLegs()
    {
        MovementPath path = MovementPath.idle(point(6464, 7104), 0, 1, OPEN);
        assertTrue(path.accept(point(6592, 7232), false));
        assertTrue(path.accept(point(6720, 7104), false));
        advance(path, 0, 200);
        assertTrue(path.anticipateContinuation(point(6976, 7104), true, 200_000_000L));
        assertNull(path.anticipateRedirect(point(6464, 7360), true, 210_000_000L));
        advance(path, 200, 640);
        assertEquals(point(6592, 7232), path.position());
    }

    @Test
    public void aClosedOccupiedEdgeIsRejectedEvenWithAnOpenOnwardRoute()
    {
        boolean[] blocked = {false};
        LocalPoint a = point(7104, 7488), b = point(6976, 7488);
        MovementPath path = capturedWestboundPreview((from, to) ->
            !(blocked[0] && (from.equals(a) && to.equals(b) || from.equals(b) && to.equals(a))));
        blocked[0] = true;
        assertNull(path.anticipateRedirect(point(6976, 7360), true, 740_000_000L));
    }

    @Test
    public void nearAnchorDoesNotHideAnExcessiveGapToAuthority()
    {
        MovementPath path = MovementPath.idle(point(6464, 7104), 0, 1, OPEN);
        assertTrue(path.accept(point(6720, 7104), true));
        assertTrue(path.accept(point(6976, 7104), true));
        assertTrue(path.accept(point(7232, 7104), true));
        advance(path, 0, 100);
        assertTrue(path.anticipateContinuation(point(7488, 7104), true, 100_000_000L));
        assertNull(path.anticipateRedirect(point(6720, 7232), true, 100_000_000L));
    }

    @Test
    public void updatedNativeTreeApproachCanRedirectTheOccupiedCheckedEdge()
    {
        // 20:54:31–33: a tree click initially points at (6464,7872), then
        // the native destination becomes (6848,7872) while the forecast goes west.
        LocalPoint start = point(7616, 7744), tree = point(6464, 7872);
        LocalPoint approach = point(6848, 7872);
        MovementPath path = MovementPath.anticipate(start, start, tree, true, 0, 1.2,
            MovementPath.freshDeadline(0), OPEN, true);
        assertNotNull(path);
        advance(path, 0, 600);
        assertTrue(path.accept(point(7360, 7744), true));
        advance(path, 600, 640);
        LocalPoint before = path.position();
        MovementPath redirected = path.anticipateRedirect(approach, true, 640_000_000L);
        assertNotNull(redirected);
        assertEquals(before, redirected.position());
        assertEquals(path.predictionDeadlineNanos(), redirected.predictionDeadlineNanos());
        assertTrue(redirected.clear());
        for (int ms = 650; ms <= 2200; ms += 10)
        {
            if (ms == 1200) { assertTrue(redirected.accept(point(7104, 7744), true)); }
            if (ms == 1800) { assertTrue(redirected.accept(approach, true)); }
            redirected.advance(ms * 1_000_000L);
            assertTrue("do not run past the native approach and double back", redirected.position().getX() >= approach.getX());
            assertTrue(redirected.clear());
        }
        assertEquals(approach, redirected.position());
        assertTrue(redirected.finished());
    }

    @Test
    public void treeWaypointMustBeObservedBeforeTheSameFrameServerStep()
    {
        // 21:15:44.855–.965: the native approach changes to (6720,6720)
        // on the frame confirming (6720,6336). Reconcile the route first so
        // the newly confirmed prefix need not be reversed or discarded.
        LocalPoint start = point(6720, 6080), tree = point(6464, 6848);
        LocalPoint approach = point(6720, 6720);
        MovementPath path = MovementPath.anticipate(start, start, tree, true, 0, 1.2,
            MovementPath.freshDeadline(0), OPEN, true);
        assertNotNull(path);
        advance(path, 0, 100);
        LocalPoint before = path.position();
        MovementPath adjusted = path.anticipateRedirect(approach, true, 100_000_000L);
        assertNotNull(adjusted);
        assertEquals(before, adjusted.position());
        assertEquals(path.predictionDeadlineNanos(), adjusted.predictionDeadlineNanos());
        assertTrue(adjusted.accept(point(6720, 6336), true));
        assertTrue(adjusted.clear());
        assertEquals("preview", adjusted.phase());
        for (int ms = 110; ms <= 1500; ms += 10)
        {
            if (ms == 700) { assertTrue(adjusted.accept(point(6720, 6592), true)); }
            if (ms == 1300) { assertTrue(adjusted.accept(approach, true)); }
            LocalPoint previous = adjusted.position();
            adjusted.advance(ms * 1_000_000L);
            assertTrue("no reverse after the native waypoint arrives", adjusted.position().getY() >= previous.getY());
            assertTrue(adjusted.clear());
        }
        assertEquals(approach, adjusted.position());
        assertTrue(adjusted.finished());
    }

    @Test
    public void confirmedTreeCornersCanFollowTheRefinedNativeDestination()
    {
        // Three first-step revisions from the 21:15 capture. The native
        // destination and server endpoint appear together in one client frame.
        LocalPoint tree = point(6464, 6848), approach = point(6720, 6720);
        LocalPoint[] starts = {point(7360, 6336), point(7232, 6336), point(7104, 6208)};
        LocalPoint[] endpoints = {point(7104, 6592), point(6976, 6464), point(6976, 6464)};
        int[] times = {620, 120, 440};
        for (int i = 0; i < starts.length; ++i)
        {
            MovementPath path = MovementPath.anticipate(starts[i], starts[i], tree, true, 0, 1.2,
                MovementPath.freshDeadline(0), OPEN, true);
            assertNotNull(path);
            advance(path, 0, times[i]);
            LocalPoint before = path.position();
            MovementPath revised = path.retargetNativeApproach(approach, true, times[i] * 1_000_000L);
            assertNotNull("start " + starts[i] + " at " + times[i] + " phase " + path.phase() +
                " position " + before + " confirmed " + path.confirmed() + " queue " + path.queuedLegs(), revised);
            assertEquals(before, revised.position());
            assertTrue(revised.accept(endpoints[i], true));
            assertEquals(before, revised.position());
            assertTrue(revised.clear());
            assertEquals("refined route should survive the confirmed first step from " + starts[i],
                "preview", revised.phase());
            if (i == 0)
            {
                // Accepting the endpoint first discards the speculative path;
                // the client must process its simultaneous waypoint update first.
                assertTrue(path.accept(endpoints[i], true));
                assertEquals("confirmed", path.phase());
            }
        }
    }

    @Test
    public void revisedTreeWaypointCannotFinishAClosedKnightCorridor()
    {
        boolean[] blocked = {false};
        MovementPath path = MovementPath.anticipate(point(7360, 6336), point(7360, 6336),
            point(6464, 6848), true, 0, 1.2, MovementPath.freshDeadline(0),
            (a, b) -> !blocked[0], true);
        assertNotNull(path);
        advance(path, 0, 620);
        LocalPoint before = path.position();
        blocked[0] = true;
        assertNull(path.retargetNativeApproach(point(6720, 6720), true, 620_000_000L));
        assertEquals(before, path.position());
    }

    @Test
    public void revisedNativeWaypointCannotReplaceAnUnfinishedConfirmedKnightChord()
    {
        LocalPoint start = point(7360, 6336), confirmed = point(7104, 6464);
        MovementPath path = MovementPath.idle(start, 0, 1.2, OPEN);
        assertTrue(path.accept(confirmed, true));
        advance(path, 0, 100);
        LocalPoint before = path.position();
        assertTrue(path.anticipateContinuation(point(6720, 6720), true, 100_000_000L, true));
        assertNull(path.retargetNativeApproach(point(6848, 6336), true, 100_000_000L));
        assertEquals(before, path.position());
        advance(path, 100, 400);
        assertEquals(point(7168, 6432), path.position());
        assertTrue(path.clear());
    }

    @Test
    public void updatedNativeTreeEndpointShortensThroughSharedRefinementWithoutReversing()
    {
        // 20:55:46–48: native route stops diagonally beside the tree at
        // (6592,7744), while the original forecast would go west to (6464,7744).
        LocalPoint start = point(6464, 7104), tree = point(6464, 7872);
        LocalPoint approach = point(6592, 7744);
        LocalPoint[] corridor = {start, point(6592, 7232), point(6592, 7360),
            point(6592, 7488), point(6592, 7616), approach, point(6464, 7744)};
        BiPredicate<LocalPoint, LocalPoint> collision = (a, b) ->
        {
            for (int i = 1; i < corridor.length; ++i)
            {
                if (a.equals(corridor[i - 1]) && b.equals(corridor[i]) ||
                    b.equals(corridor[i - 1]) && a.equals(corridor[i])) { return true; }
            }
            return false;
        };
        MovementPath path = MovementPath.anticipate(start, start, tree, true, 0, 1,
            MovementPath.freshDeadline(0), collision, true);
        assertNotNull(path);
        advance(path, 0, 600);
        assertTrue(path.accept(point(6592, 7360), true));
        LocalPoint before = path.position();
        long deadline = path.predictionDeadlineNanos();
        path = path.retargetApproachDestination(approach, true, 600_000_000L, true);
        assertNotNull(path);
        assertEquals(before, path.position());
        assertEquals(deadline, path.predictionDeadlineNanos());
        assertEquals(approach, path.clickedDestination());
        assertTrue(path.clear());
        for (int ms = 610; ms <= 1900; ms += 10)
        {
            if (ms == 1200) { assertTrue(path.accept(point(6592, 7616), true)); }
            if (ms == 1800) { assertTrue(path.accept(approach, true)); }
            path.advance(ms * 1_000_000L);
            assertTrue("the displayed player must not cross to the wrong side", path.position().getX() >= approach.getX());
            assertTrue(path.clear());
        }
        assertEquals(approach, path.position());
        assertTrue(path.finished());
    }
}
