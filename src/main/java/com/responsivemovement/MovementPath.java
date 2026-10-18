package com.responsivemovement;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.function.BiPredicate;
import net.runelite.api.Constants;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;

/**
 * The single spatial pipeline for predicted and confirmed player movement.
 * Derived from the responsive-start work in True Tile Movement (BSD-2-Clause).
 * Only presentation coordinates are changed; actor route state is read-only.
 */
final class MovementPath
{
    private static final int TILE = Perspective.LOCAL_TILE_SIZE;
    private static final int MAX_ROUTE = 64;
    private static final int MAX_QUEUE = 64;
    private static final long RESPONSE_NANOS = 900_000_000L;
    private static final long CHAIN_NANOS = 1_800_000_000L;
    private static final double EPS = 0.000001;

    private LocalPoint origin;
    private LocalPoint confirmed;
    private LocalPoint npcMin;
    private LocalPoint npcMax;
    private int npcReserveTiles = 2;
    private final List<LocalPoint> clickedPath = new ArrayList<>();
    private List<LocalPoint> preview = new ArrayList<>();
    private final Deque<Leg> legs = new ArrayDeque<>();
    private final BiPredicate<LocalPoint, LocalPoint> collision;
    private Leg lastLeg;
    private double x;
    private double y;
    private double multiplier;
    private boolean running;
    private boolean speculative;
    private boolean recovering;
    private boolean replacement;
    private boolean agreement;
    private boolean awaitingOrigin;
    private boolean reversalPreview;
    private boolean invalid;
    private boolean easingNpcStart;
    private boolean npcRetarget;
    private boolean combatTracking;
    private boolean combatCredit;
    private boolean combatTrailing;
    private LocalPoint npcArrivalGoal;
    private LocalPoint counterNativeGoal;
    private LocalPoint counterBoundaryGoal;
    private int confirmedPreviewIndex;
    private int confirmedClickIndex;
    private long lastNanos;
    private long deadline;
    private long chainDeadline;
    private double turnFraction;
    private double turnX;
    private double turnY;
    private double tangentX;
    private double tangentY;
    private boolean walkContinuity;
    private boolean walkForecast;
    private boolean arrivalWalking;
    private boolean arrivalPaced;
    private boolean publicationWalking;
    private int confirmedTripTiles;
    private boolean confirmedArrivalTrip;
    private boolean nativeCounterPairs;
    private int nativeCounterReleaseIndex;
    private boolean tripEnded = true;
    private int consecutiveSingleSteps;

    private static final class Leg
    {
        final LocalPoint start;
        final LocalPoint end;
        final LocalPoint corridorEnd;
        final boolean firstOfPair;
        boolean running;
        double headingX = Double.NaN;
        double headingY = Double.NaN;
        boolean joinPrepared;
        JoinCurve curve;

        Leg(LocalPoint start, LocalPoint end, boolean running, boolean pair, LocalPoint corridor)
        {
            this.start = start;
            this.end = end;
            this.running = running;
            firstOfPair = pair;
            corridorEnd = corridor;
            if (pair && corridor != null)
            {
                headingX = end.getX() - start.getX();
                headingY = end.getY() - start.getY();
            }
        }

        Leg endingAt(LocalPoint end, boolean running)
        {
            return new Leg(start, end, running, false, corridorEnd);
        }
    }

    private MovementPath(LocalPoint visible, LocalPoint authoritative, long now,
        double speed, BiPredicate<LocalPoint, LocalPoint> collision)
    {
        origin = visible;
        confirmed = authoritative;
        x = visible.getX();
        y = visible.getY();
        lastNanos = now;
        multiplier = normalizeSpeed(speed);
        this.collision = collision;
        chainDeadline = freshDeadline(now);
        deadline = now + RESPONSE_NANOS;
    }

    static MovementPath idle(LocalPoint at, long now, double speed,
        BiPredicate<LocalPoint, LocalPoint> collision)
    {
        return new MovementPath(at, at, now, speed, collision);
    }

    static MovementPath anticipate(LocalPoint visible, LocalPoint authoritative, LocalPoint destination,
        boolean run, long now, double speed, long hardDeadline,
        BiPredicate<LocalPoint, LocalPoint> collision)
    {
        return anticipate(visible, authoritative, destination, run, now, speed, hardDeadline, collision, false);
    }

    static MovementPath anticipate(LocalPoint visible, LocalPoint authoritative, LocalPoint destination,
        boolean run, long now, double speed, long hardDeadline,
        BiPredicate<LocalPoint, LocalPoint> collision, boolean approach)
    {
        return anticipate(visible, authoritative, destination, run, now, speed, hardDeadline, collision, approach, null, null);
    }

    static MovementPath anticipate(LocalPoint visible, LocalPoint authoritative, LocalPoint destination,
        boolean run, long now, double speed, long hardDeadline,
        BiPredicate<LocalPoint, LocalPoint> collision, boolean approach, LocalPoint npcMin, LocalPoint npcMax)
    {
        return anticipate(visible, authoritative, destination, run, now, speed, hardDeadline,
            collision, approach, npcMin, npcMax, 2);
    }

    static MovementPath anticipate(LocalPoint visible, LocalPoint authoritative, LocalPoint destination,
        boolean run, long now, double speed, long hardDeadline,
        BiPredicate<LocalPoint, LocalPoint> collision, boolean approach, LocalPoint npcMin, LocalPoint npcMax, int npcReserveTiles)
    {
        return anticipate(visible, authoritative, destination, run, now, speed, hardDeadline,
            collision, approach, npcMin, npcMax, npcReserveTiles, false);
    }

    static MovementPath anticipateNpcRunPair(LocalPoint visible, LocalPoint authoritative, LocalPoint destination,
        boolean run, long now, double speed, long hardDeadline,
        BiPredicate<LocalPoint, LocalPoint> collision, LocalPoint npcMin, LocalPoint npcMax)
    {
        if (!run || !sameView(visible, authoritative) || !visible.equals(authoritative) || destination == null ||
            !destination.equals(NpcApproach.shortRunGoal(visible, npcMin, npcMax, collision))) { return null; }
        return anticipate(visible, authoritative, destination, true, now, speed, hardDeadline,
            collision, false, npcMin, npcMax, 2, true);
    }

    static boolean clearNpcRunPair(LocalPoint from, LocalPoint goal, BiPredicate<LocalPoint, LocalPoint> collision)
    {
        List<LocalPoint> pair = direct(from, goal, (a, b) -> collision.test(a, b) && collision.test(b, a), 2, false);
        return pair != null && pair.size() == 2;
    }

    static MovementPath anticipateCounterRun(LocalPoint visible, LocalPoint authoritative, NpcApproach.CounterRun plan,
        long now, double speed, long hardDeadline, BiPredicate<LocalPoint, LocalPoint> collision,
        LocalPoint npcMin, LocalPoint npcMax)
    {
        if (plan == null || !sameView(visible, authoritative) || !visible.equals(authoritative) ||
            !tileCenter(visible) || now >= hardDeadline || plan.steps.isEmpty() || plan.steps.size() > MAX_ROUTE) { return null; }
        LocalPoint previous = visible;
        for (LocalPoint step : plan.steps)
        {
            if (!sameView(previous, step) || distance(previous, step) != TILE ||
                !collision.test(previous, step) || !collision.test(step, previous)) { return null; }
            previous = step;
        }
        return seed(visible, authoritative, plan.steps, true, now, speed, hardDeadline, collision,
            npcMin, npcMax, 2, false, plan.nativeGoal, false, plan.boundaryGoal);
    }

    private static MovementPath anticipate(LocalPoint visible, LocalPoint authoritative, LocalPoint destination,
        boolean run, long now, double speed, long hardDeadline,
        BiPredicate<LocalPoint, LocalPoint> collision, boolean approach, LocalPoint npcMin, LocalPoint npcMax,
        int npcReserveTiles, boolean npcRunPair)
    {
        if (!sameView(visible, authoritative) || !tileCenter(visible) || now >= hardDeadline)
        {
            return null;
        }
        // Keep the established straight-first route whenever it is clear.
        // Only a blocked click needs the bounded obstacle search. Predicted
        // edges must also be reversible in case the server rejects the click.
        List<LocalPoint> route = direct(visible, destination,
            (a, b) -> collision.test(a, b) && collision.test(b, a), MAX_ROUTE, false);
        if (route == null) { route = MovementRoute.find(visible, destination, MAX_ROUTE, collision, approach); }
        return seed(visible, authoritative, route, run, now, speed, hardDeadline, collision,
            npcMin, npcMax, npcReserveTiles, npcRunPair, null);
    }

    private static MovementPath seed(LocalPoint visible, LocalPoint authoritative, List<LocalPoint> route,
        boolean run, long now, double speed, long hardDeadline, BiPredicate<LocalPoint, LocalPoint> collision,
        LocalPoint npcMin, LocalPoint npcMax, int npcReserveTiles, boolean npcRunPair, LocalPoint counterNativeGoal)
    {
        return seed(visible, authoritative, route, run, now, speed, hardDeadline, collision,
            npcMin, npcMax, npcReserveTiles, npcRunPair, counterNativeGoal, false);
    }

    private static MovementPath seed(LocalPoint visible, LocalPoint authoritative, List<LocalPoint> route,
        boolean run, long now, double speed, long hardDeadline, BiPredicate<LocalPoint, LocalPoint> collision,
        LocalPoint npcMin, LocalPoint npcMax, int npcReserveTiles, boolean npcRunPair, LocalPoint counterNativeGoal, boolean rangedArrival)
    {
        return seed(visible, authoritative, route, run, now, speed, hardDeadline, collision,
            npcMin, npcMax, npcReserveTiles, npcRunPair, counterNativeGoal, rangedArrival, null);
    }

    private static MovementPath seed(LocalPoint visible, LocalPoint authoritative, List<LocalPoint> route,
        boolean run, long now, double speed, long hardDeadline, BiPredicate<LocalPoint, LocalPoint> collision,
        LocalPoint npcMin, LocalPoint npcMax, int npcReserveTiles, boolean npcRunPair, LocalPoint counterNativeGoal,
        boolean rangedArrival, LocalPoint counterBoundaryGoal)
    {
        if (route == null || route.isEmpty())
        {
            return null;
        }
        if (npcRunPair && route.size() != 2) { return null; }
        MovementPath path = new MovementPath(visible, authoritative, now, speed, collision);
        path.npcMin = npcMin; path.npcMax = npcMax;
        path.npcReserveTiles = Math.max(1, Math.min(10, npcReserveTiles));
        path.running = run && route.size() > 1;
        path.counterNativeGoal = counterNativeGoal;
        path.counterBoundaryGoal = counterBoundaryGoal;
        if (npcRunPair || counterNativeGoal != null)
        {
            if (path.forecastSteps() < 2) { return null; }
            path.npcArrivalGoal = route.get(route.size() - 1);
        }
        if (rangedArrival) { path.npcArrivalGoal = route.get(route.size() - 1); }
        path.chainDeadline = hardDeadline;
        path.deadline = Math.min(now + RESPONSE_NANOS, hardDeadline);
        path.clickedPath.add(visible);
        path.clickedPath.addAll(route);
        path.confirmedClickIndex = path.clickedPath.indexOf(authoritative);
        path.awaitingOrigin = path.confirmedClickIndex < 0;
        path.confirmedPreviewIndex = Math.max(0, path.confirmedClickIndex);
        path.agreement = true;
        int limit = path.limitForecast(path.completePair(Math.min(route.size(), path.confirmedPreviewIndex + path.forecastSteps())));
        for (int index = 1; index <= limit; ++index)
        {
            if (distance(path.clickedPath.get(index), authoritative) > (path.forecastSteps() + 1) * TILE)
            {
                limit = index - 1;
                break;
            }
        }
        if (limit == 0)
        {
            return null;
        }
        // A close idle start has only one checked tile before the NPC staging
        // boundary. Do not sprint to it, publish idle, then restart on the first
        // server step. Ease only that unconfirmed seed; authority restores normal
        // pacing immediately, and no extra tile or prediction time is granted.
        path.easingNpcStart = npcReserveTiles == 2 && !npcRunPair && counterNativeGoal == null && visible.equals(authoritative) && limit == 1 &&
            path.npcDistance(visible) == (path.npcReserveTiles + 1) * TILE && path.insideNpcStaging(route.get(0));
        path.preview.add(visible);
        path.preview.addAll(route.subList(0, limit));
        path.speculative = path.awaitingOrigin || path.confirmedPreviewIndex < limit;
        path.append(visible, route.subList(0, limit));
        return path;
    }

    boolean canAnticipateContinuation()
    {
        return !invalid && !recovering && !awaitingOrigin && !speculative && !legs.isEmpty() &&
            legs.peekLast().end.equals(confirmed);
    }

    /** Release the final checked run pair in the captured five-tile cardinal approach. */
    boolean releaseNpcArrival(LocalPoint nativeGoal, LocalPoint previousAuthority, boolean run, long now)
    {
        if (!run || !running || !speculative || invalid || recovering || replacement || !agreement ||
            awaitingOrigin || reversalPreview || npcReserveTiles != 2 || !sameView(npcMin, npcMax) ||
            !npcMin.equals(npcMax) || !origin.equals(previousAuthority) || clickedPath.size() != 5 ||
            confirmedClickIndex != 2 || confirmedPreviewIndex != 2 || !confirmed.equals(clickedPath.get(2)) ||
            !clickedDestination().equals(nativeGoal) || npcDistance(origin) != 5 * TILE ||
            npcDistance(nativeGoal) != TILE || preview.size() != 4 || legs.isEmpty() || forecastSteps() < 2 ||
            now >= deadline || now >= chainDeadline || legs.size() + 1 > MAX_QUEUE) { return false; }
        int dx = nativeGoal.getX() - origin.getX(), dy = nativeGoal.getY() - origin.getY();
        if ((dx == 0) == (dy == 0) || !sameLine(origin, nativeGoal, npcMin)) { return false; }
        for (int index = 1; index < clickedPath.size(); ++index)
        {
            LocalPoint previous = clickedPath.get(index - 1), next = clickedPath.get(index);
            if (next.getX() - previous.getX() != Integer.signum(dx) * TILE ||
                next.getY() - previous.getY() != Integer.signum(dy) * TILE) { return false; }
        }
        LocalPoint middle = clickedPath.get(3);
        if (!collision.test(confirmed, middle) || !collision.test(middle, confirmed) ||
            !collision.test(middle, nativeGoal) || !collision.test(nativeGoal, middle)) { return false; }
        // In this exact case, matching authority has started a four-step run to
        // the published adjacent goal. Keep normal pacing through its second pair
        // instead of stopping at the generic two-tile staging ring. The added last
        // tile stays speculative under the existing response/distance limits.
        append(middle, Collections.singletonList(nativeGoal));
        preview.add(nativeGoal);
        npcArrivalGoal = nativeGoal;
        return true;
    }

    LocalPoint npcArrivalGoal() { return counterBoundaryGoal == null ? npcArrivalGoal : counterBoundaryGoal; }
    LocalPoint counterNativeGoal() { return counterNativeGoal; }

    /** A matching native flag releases only the retained, checked counter route. */
    boolean releaseCounterBoundary(LocalPoint nativeGoal, long now)
    {
        if (counterBoundaryGoal == null || !sameView(counterNativeGoal, nativeGoal) ||
            !counterNativeGoal.equals(nativeGoal) || invalid || recovering || replacement || !agreement ||
            now >= deadline || now >= chainDeadline) { return false; }
        int limit = completePair(Math.min(clickedPath.size() - 1, confirmedClickIndex + forecastSteps()));
        int tail = clickedPath.indexOf(preview.get(preview.size() - 1));
        for (int i = tail + 1; i <= limit; ++i)
        {
            if (!collision.test(clickedPath.get(i - 1), clickedPath.get(i)) ||
                !collision.test(clickedPath.get(i), clickedPath.get(i - 1))) { return false; }
        }
        counterBoundaryGoal = null;
        long response = deadline;
        extend();
        // Destination evidence changes the boundary, not the prediction budget.
        deadline = response;
        return true;
    }

    boolean counterRunAccepts(LocalPoint endpoint)
    {
        return counterRunAccepts(endpoint, lastNanos);
    }

    boolean counterRunAccepts(LocalPoint endpoint, long now)
    {
        if (endpoint.equals(confirmed)) { return true; }
        int previous = clickedPath.indexOf(confirmed), next = clickedPath.indexOf(endpoint);
        return previous >= 0 && next == Math.min(previous + 2, clickedPath.size() - 1) ||
            pendingCounterRetarget(now) && canRetainNpcRetarget(endpoint, now) &&
                checkedRoute(confirmed, endpoint, collision, 2) != null;
    }

    boolean pendingCounterRetarget(long now)
    {
        return counterNativeGoal != null && npcRetarget && confirmedClickIndex <= 0 &&
            !invalid && !recovering && now < deadline && now < chainDeadline;
    }

    void retireCounterRun()
    {
        cancel();
        counterNativeGoal = npcArrivalGoal = counterBoundaryGoal = null;
    }

    boolean npcRunPairDisagreed(LocalPoint previousAuthority)
    {
        return npcArrivalGoal != null && clickedPath.size() == 3 && origin.equals(previousAuthority) &&
            !confirmed.equals(origin) && !confirmed.equals(npcArrivalGoal);
    }

    boolean awaitingNpcRunPair()
    {
        return counterNativeGoal != null && speculative ||
            npcArrivalGoal != null && clickedPath.size() == 3 && confirmed.equals(origin);
    }

    /** A fresh yellow click, distinct from native interaction waypoint changes. */
    MovementPath retargetWalk(LocalPoint destination, boolean run, long now)
    {
        if (invalid || !sameView(destination, confirmed) || !tileCenter(destination) || legs.isEmpty()) { return null; }
        Leg active = legs.peekFirst();
        // A fresh click may leave an occupied diagonal through a side corner
        // only when the whole one-tile square is reversible and collision-clear.
        // This avoids retracing to the diagonal's start just to turn sideways.
        if (active.corridorEnd == null && clearTurnSquare(active.start, active.end, collision))
        {
            active = new Leg(active.start, active.end, active.running, false, active.end);
        }
        boolean corridor = active.corridorEnd != null;
        if (corridor ? !inside(active, x, y) || !clearCorridor(active.start, active.corridorEnd, collision)
            : !onSegment(active.start, active.end) || !collision.test(active.start, active.end) ||
                !collision.test(active.end, active.start)) { return null; }

        MovementPath nativeKnight = retargetCheckedKnight(active, confirmed, destination, run, now);
        if (nativeKnight != null) { return nativeKnight; }
        // Authority can still describe the preceding click while its preview is
        // just beside this occupied endpoint. Keep a complete checked knight
        // from that endpoint rather than choosing a one-tile staging corner.
        LocalPoint visibleAnchor = active.end;
        if (!visibleAnchor.equals(confirmed) && tileCenter(visibleAnchor) &&
            Math.max(Math.abs(x - visibleAnchor.getX()), Math.abs(y - visibleAnchor.getY())) <= TILE / 4.0)
        {
            MovementPath visibleKnight = retargetCheckedKnight(active, visibleAnchor, destination, run, now);
            if (visibleKnight != null) { return visibleKnight; }
        }

        MovementPath authorityDeparture = retargetAuthorityDeparture(active, destination, run, now);

        // Only a fresh user click may replace an obsolete confirmed visual
        // itinerary. Authority itself remains unchanged. Ordinary reconciliation
        // and native interaction waypoints keep their confirmed-prefix guards.
        List<LocalPoint> anchors = new ArrayList<>();
        anchors.add(active.end);
        anchors.add(active.start);
        if (corridor)
        {
            corridorAnchors(active, anchors);
        }
        MovementPath best = null;
        MovementPath forward = null;
        MovementPath continuous = null;
        MovementPath directed = null;
        double directedTravel = Double.POSITIVE_INFINITY;
        double directedGeometry = Double.POSITIVE_INFINITY;
        double continuousScore = Double.POSITIVE_INFINITY;
        double bestDistance = Double.POSITIVE_INFINITY;
        double bestGeometry = Double.POSITIVE_INFINITY;
        for (LocalPoint anchor : anchors)
        {
            Leg connector = corridor ? active.endingAt(anchor, active.running)
                : new Leg(anchor.equals(active.end) ? active.start : active.end, anchor, active.running, false, null);
            MovementPath next = walkFromAnchor(connector, destination, run, now);
            if (next == null) { continue; }
            double length = remainingTravel(next);
            if (!corridor && anchor.equals(active.end) &&
                (active.start.getX() == active.end.getX() || active.start.getY() == active.end.getY()) &&
                (long) (active.end.getX() - active.start.getX()) * (destination.getX() - active.end.getX()) +
                    (long) (active.end.getY() - active.start.getY()) * (destination.getY() - active.end.getY()) >= 0 &&
                !next.clickedPath.contains(active.start)) { forward = next; }
            double geometry = remainingGeometry(next);
            if (withinGoalInterval(next, destination) && (length < directedTravel - EPS ||
                Math.abs(length - directedTravel) <= EPS && geometry < directedGeometry - EPS))
            {
                directed = next; directedTravel = length; directedGeometry = geometry;
            }
            Leg first = next.legs.peekFirst();
            double targetX = destination.getX() - x, targetY = destination.getY() - y;
            // A still-forward click need not trade its current tangent for a
            // slightly shorter sideways connector. Prefer a checked forward
            // candidate within the existing one-tile travel allowance. Explicit
            // reversals and routes with no forward candidate keep normal selection.
            if (first != null && tangentX * targetX + tangentY * targetY > 0)
            {
                double fx = first.end.getX() - x, fy = first.end.getY() - y;
                double dot = tangentX * fx + tangentY * fy;
                if (dot > 0)
                {
                    double cosine = dot / (Math.hypot(tangentX, tangentY) * Math.hypot(fx, fy));
                    double score = length + TILE * (1 - cosine);
                    if (score < continuousScore - EPS)
                    {
                        continuous = next; continuousScore = score;
                    }
                }
            }
            if (length < bestDistance - EPS || Math.abs(length - bestDistance) <= EPS && geometry < bestGeometry - EPS)
            {
                best = next; bestDistance = length; bestGeometry = geometry;
            }
        }
        double targetProgress = tangentX * (destination.getX() - x) + tangentY * (destination.getY() - y);
        boolean nearForwardDiagonalEnd = targetProgress > EPS &&
            Math.abs(active.end.getX() - active.start.getX()) == TILE &&
            Math.abs(active.end.getY() - active.start.getY()) == TILE &&
            Math.max(Math.abs(x - active.end.getX()), Math.abs(y - active.end.getY())) <= TILE / 4.0;
        if (directed != null && directedTravel <= bestDistance + TILE &&
            (targetProgress <= EPS && (authorityDeparture == null || active.corridorEnd != null &&
                clearKnight(active.start, active.corridorEnd, collision) && !withinGoalInterval(authorityDeparture, destination)) ||
                nearForwardDiagonalEnd && authorityDeparture != null && !withinGoalInterval(authorityDeparture, destination)))
        {
            // Even a forward click can leave authority behind one axis of the
            // display. Do not prefer its old row/column over an equally bounded
            // checked candidate contained between the displayed fraction and goal.
            return directed;
        }
        if (authorityDeparture != null && remainingTravel(authorityDeparture) <= bestDistance + TILE)
        {
            // A cheaper old middle-tile anchor can manufacture a large sideways
            // excursion just before arrival. Prefer the native authority route
            // through its fully proven first-step square within the same existing
            // one-tile connector allowance; blocked/long detours keep normal selection.
            return authorityDeparture;
        }
        if (best != null && forward != null && !best.legs.isEmpty())
        {
            LocalPoint first = best.legs.peekFirst().end;
            double backwards = (active.end.getX() - active.start.getX()) * (first.getX() - x) +
                (active.end.getY() - active.start.getY()) * (first.getY() - y);
            // A still-forward cardinal click need not backstep just to exploit
            // the max-axis timing of a new diagonal/knight. Prefer its legal
            // forward corner if this costs at most one tile of travel, and the
            // onward route does not itself return through the occupied edge.
            if (backwards < -EPS && remainingTravel(forward) <= bestDistance + TILE) { return forward; }
        }
        if (continuous != null && remainingTravel(continuous) <= bestDistance + TILE) { return continuous; }
        return best;
    }

    /** Join a fresh Walk's cardinal departure through a fully checked local square. */
    private MovementPath retargetAuthorityDeparture(Leg occupied, LocalPoint destination, boolean run, long now)
    {
        if (Math.max(Math.abs(x - confirmed.getX()), Math.abs(y - confirmed.getY())) > TILE) { return null; }
        boolean authorityOnOccupied = occupied.corridorEnd != null
            ? insideCheckedCorridor(occupied, confirmed.getX(), confirmed.getY())
            : confirmed.equals(occupied.start) || confirmed.equals(occupied.end);
        if (!authorityOnOccupied) { return null; }
        MovementPath forecast = anticipate(confirmed, confirmed, destination, run, now, multiplier,
            restartDeadline(now, confirmed), collision);
        if (forecast == null || forecast.legs.isEmpty() ||
            distance(position(), confirmed) > (forecast.forecastSteps() + 1) * TILE) { return null; }
        Leg first = forecast.legs.peekFirst();
        int dx = first.end.getX() - confirmed.getX(), dy = first.end.getY() - confirmed.getY();
        if (first.corridorEnd != null || Math.abs(dx) + Math.abs(dy) != TILE) { return null; }
        MovementPath best = null;
        double bestTravel = Double.POSITIVE_INFINITY;
        for (int side : new int[] {-1, 1})
        {
            LocalPoint corner = new LocalPoint(confirmed.getX() + (dy == 0 ? 0 : side * TILE),
                confirmed.getY() + (dx == 0 ? 0 : side * TILE), confirmed.getWorldView());
            if (!clearTurnSquare(corner, first.end, collision)) { continue; }
            Leg departure = new Leg(corner, first.end, first.running, first.firstOfPair, first.end);
            departure.headingX = departure.headingY = Double.NaN;
            LocalPoint crossing = null;
            if (!insideCheckedCorridor(departure, x, y))
            {
                // A reversal can start in the neighbouring occupied square.
                // Cross their shared boundary only near authority, retaining a
                // separate collision proof for each fractional subsegment.
                if (Math.max(Math.abs(x - confirmed.getX()), Math.abs(y - confirmed.getY())) > TILE / 2.0) { continue; }
                crossing = sharedCorridorCrossing(occupied, departure, first.end);
                if (crossing == null) { continue; }
            }
            MovementPath next = joinedWalkForecast(forecast);
            if (crossing != null)
            {
                Leg connector = occupied.endingAt(crossing, first.running);
                connector.headingX = first.end.getX() - x; connector.headingY = first.end.getY() - y;
                connector.joinPrepared = true;
                next.legs.addLast(connector);
            }
            next.legs.addLast(departure);
            boolean skip = true;
            for (Leg leg : forecast.legs)
            {
                if (skip) { skip = false; } else { next.legs.addLast(leg); }
            }
            next.reversalPreview = true;
            next.inheritWalkContinuity(this);
            next.pendingWalkConfirmation();
            double travel = remainingTravel(next);
            if (next.legs.size() <= MAX_QUEUE && next.clear() && travel < bestTravel - EPS)
            {
                best = next; bestTravel = travel;
            }
        }
        return best;
    }

    /** Preserve a whole checked knight from authority or a nearby occupied endpoint. */
    private MovementPath retargetCheckedKnight(Leg occupied, LocalPoint anchor, LocalPoint destination, boolean run, long now)
    {
        if (!run || !clearKnight(anchor, destination, collision)) { return null; }
        MovementPath next = anticipate(anchor, confirmed, destination, true, now, multiplier,
            restartDeadline(now, confirmed), collision);
        if (next == null || next.legs.size() != 1 || next.legs.peekFirst().corridorEnd == null) { return null; }
        int gap = next.forecastSteps() + 1;
        if (distance(position(), confirmed) > gap * TILE || distance(anchor, confirmed) > gap * TILE) { return null; }
        BiPredicate<LocalPoint, LocalPoint> reversible = (a, b) -> collision.test(a, b) && collision.test(b, a);
        if (checkedRoute(confirmed, anchor, reversible, gap) == null &&
            MovementRoute.find(confirmed, anchor, gap, reversible) == null) { return null; }
        Leg knight = next.legs.peekFirst();
        knight.headingX = knight.headingY = Double.NaN;
        if (!insideCheckedCorridor(knight, x, y))
        {
            // A click just before reaching the old endpoint can be a fraction
            // outside the new corridor. A shared corridor crossing can continue
            // straight without returning to the origin; otherwise finish only
            // the tiny checked alignment. Both regions are independently proven.
            if (Math.max(Math.abs(x - anchor.getX()), Math.abs(y - anchor.getY())) > TILE / 4.0 ||
                !(occupied.corridorEnd != null && insideCheckedCorridor(occupied, x, y) &&
                    insideCheckedCorridor(occupied, anchor.getX(), anchor.getY()) ||
                    onSegment(occupied.start, occupied.end) &&
                        (anchor.equals(occupied.start) || anchor.equals(occupied.end)))) { return null; }
            LocalPoint crossing = sharedCorridorCrossing(occupied, knight, destination);
            Leg connector = occupied.endingAt(crossing == null ? anchor : crossing, running());
            // Its very short positional alignment should not make the body face
            // back toward the preceding click before turning into the new chord.
            connector.headingX = destination.getX() - x;
            connector.headingY = destination.getY() - y;
            connector.joinPrepared = true;
            next.legs.addFirst(connector);
        }
        next.x = x; next.y = y; next.lastNanos = lastNanos;
        next.reversalPreview = true;
        next.tangentX = tangentX; next.tangentY = tangentY;
        next.pendingWalkConfirmation();
        return next.clear() ? next : null;
    }

    /** A straight join is safe when its crossing lies inside both proven convex corridors. */
    private LocalPoint sharedCorridorCrossing(Leg occupied, Leg next, LocalPoint goal)
    {
        if (occupied.corridorEnd == null || !insideCheckedCorridor(occupied, x, y)) { return null; }
        double dx = goal.getX() - x, dy = goal.getY() - y;
        double lo = 0, hi = 1;
        // A segment ending inside a convex region has one entry interval. Find
        // its entry; endpoint containment then proves each complete subsegment.
        // This is bounded geometry at a click, not a scene/collision search.
        for (int i = 0; i < 40; ++i)
        {
            double t = (lo + hi) / 2;
            if (insideCheckedCorridor(next, x + dx * t, y + dy * t)) { hi = t; } else { lo = t; }
        }
        LocalPoint crossing = new LocalPoint((int) Math.round(x + dx * hi), (int) Math.round(y + dy * hi), goal.getWorldView());
        return insideCheckedCorridor(occupied, crossing.getX(), crossing.getY()) &&
            insideCheckedCorridor(next, crossing.getX(), crossing.getY()) ? crossing : null;
    }

    private MovementPath walkFromAnchor(Leg connector, LocalPoint destination, boolean run, long now)
    {
        LocalPoint anchor = connector.end;
        long hardDeadline = restartDeadline(now, confirmed);
        if (now >= hardDeadline) { return null; }
        MovementPath next;
        if (anchor.equals(destination))
        {
            // A click on a connector endpoint has no onward route, but still
            // needs the fractional connector and confirmation/timeout state.
            next = new MovementPath(anchor, confirmed, now, multiplier, collision);
            next.clickedPath.add(anchor); next.preview.add(anchor);
            next.confirmedClickIndex = anchor.equals(confirmed) ? 0 : -1;
            next.awaitingOrigin = next.confirmedClickIndex < 0;
            next.speculative = next.awaitingOrigin;
            next.agreement = true;
            next.chainDeadline = hardDeadline;
            next.deadline = Math.min(now + RESPONSE_NANOS, hardDeadline);
        }
        else
        {
            next = anticipate(anchor, confirmed, destination, run, now, multiplier, hardDeadline, collision);
        }
        if (next == null || next.legs.size() + 1 > MAX_QUEUE) { return null; }
        if (run && running())
        {
            next.running = true;
            for (Leg leg : next.legs) { leg.running = true; }
        }
        int gap = (next.forecastSteps() + 1) * TILE;
        if (distance(position(), confirmed) > gap || distance(anchor, confirmed) > gap) { return null; }
        // Nearby coordinates alone cannot justify a cross-wall connection to
        // authority. Require a bounded reversible route for eventual recovery.
        BiPredicate<LocalPoint, LocalPoint> reversible = (a, b) -> collision.test(a, b) && collision.test(b, a);
        if (checkedRoute(confirmed, anchor, reversible, next.forecastSteps() + 1) == null &&
            MovementRoute.find(confirmed, anchor, next.forecastSteps() + 1, reversible) == null) { return null; }
        MovementPath departure = joinWalkDeparture(connector, next);
        Leg first = next.legs.peekFirst();
        boolean alreadyOnRoute = first != null && (onSegment(first.start, first.end) ||
            insideCheckedCorridor(first, x, y) && clearCorridor(first.start, first.corridorEnd, collision));
        if (alreadyOnRoute)
        {
            // The visible point can already be on the new route or inside its
            // checked first knight corridor. Do not return to that route's
            // construction anchor and then retraverse the same ground.
            first.headingX = first.headingY = Double.NaN;
        }
        else if (Math.abs(anchor.getX() - x) > EPS || Math.abs(anchor.getY() - y) > EPS)
        {
            next.legs.addFirst(connector);
        }
        next.x = x; next.y = y;
        next.lastNanos = lastNanos;
        next.inheritWalkContinuity(this);
        next.reversalPreview = next.clickedPath.size() > 1;
        // This is a fresh click, not confirmation of its newly built itinerary.
        // The old true tile may already equal its goal while an older click's
        // opposite step is still in flight. Keep that route pending so the
        // existing bounded stale-tick handling cannot append a spurious return.
        next.pendingWalkConfirmation();
        if (departure != null && remainingTravel(departure) <= remainingTravel(next) + EPS &&
            remainingGeometry(departure) < remainingGeometry(next) - EPS) { return departure; }
        return next;
    }

    /** Bypass an obsolete corner only inside independently proven local convex regions. */
    private MovementPath joinWalkDeparture(Leg occupied, MovementPath forecast)
    {
        return joinWalkDeparture(occupied, forecast, false);
    }

    private MovementPath joinWalkDeparture(Leg occupied, MovementPath forecast, boolean authorityJoin)
    {
        return joinDeparture(occupied, forecast, authorityJoin, true);
    }

    private MovementPath joinDeparture(Leg occupied, MovementPath forecast, boolean authorityJoin, boolean walk)
    {
        LocalPoint goal = forecast.clickedDestination();
        // Still-forward cardinal clicks retain the established corner policy.
        // Occupied diagonal/knight regions can prove a direct side departure.
        if (goal == null || !authorityJoin && occupied.corridorEnd == null &&
            tangentX * (goal.getX() - x) + tangentY * (goal.getY() - y) > EPS) { return null; }
        Leg first = forecast.legs.peekFirst();
        if (first == null || first.corridorEnd != null) { return null; }
        LocalPoint anchor = first.start;
        int dx = first.end.getX() - anchor.getX(), dy = first.end.getY() - anchor.getY();
        List<LocalPoint> corners = new ArrayList<>();
        if (Math.abs(dx) == TILE && Math.abs(dy) == TILE) { corners.add(anchor); }
        else if (Math.abs(dx) + Math.abs(dy) == TILE)
        {
            for (int side : new int[] {-1, 1})
            {
                corners.add(new LocalPoint(anchor.getX() + (dy == 0 ? 0 : side * TILE),
                    anchor.getY() + (dx == 0 ? 0 : side * TILE), anchor.getWorldView()));
            }
        }
        MovementPath best = null;
        double bestTravel = Double.POSITIVE_INFINITY;
        for (LocalPoint corner : corners)
        {
            if (!clearTurnSquare(corner, first.end, collision)) { continue; }
            // Keep the occupied proof when it already contains this complete
            // straight join. Replacing a knight with a smaller construction
            // square would lose its middle-tile authority/prefix evidence.
            boolean contained = occupied.corridorEnd != null && insideCheckedCorridor(occupied, x, y) &&
                insideCheckedCorridor(occupied, first.end.getX(), first.end.getY());
            Leg departure = contained ? new Leg(occupied.start, first.end, first.running, first.firstOfPair, occupied.corridorEnd) :
                new Leg(corner, first.end, first.running, first.firstOfPair, first.end);
            departure.headingX = departure.headingY = Double.NaN;
            LocalPoint crossing = insideCheckedCorridor(departure, x, y) ? null :
                sharedCorridorCrossing(occupied, departure, first.end);
            if (crossing == null && !insideCheckedCorridor(departure, x, y)) { continue; }
            MovementPath next = joinedWalkForecast(forecast);
            // Unlike forward-authority joins, a construction anchor need not be
            // current authority. Preserve the forecast's pending/stale-tick state.
            next.awaitingOrigin = forecast.awaitingOrigin;
            next.confirmedClickIndex = forecast.confirmedClickIndex;
            next.confirmedPreviewIndex = forecast.confirmedPreviewIndex;
            if (crossing != null)
            {
                Leg connector = occupied.endingAt(crossing, first.running);
                connector.headingX = first.end.getX() - x; connector.headingY = first.end.getY() - y;
                connector.joinPrepared = true;
                next.legs.addLast(connector);
            }
            next.legs.addLast(departure);
            boolean skip = true;
            for (Leg leg : forecast.legs)
            {
                if (skip) { skip = false; } else { next.legs.addLast(leg); }
            }
            if (walk)
            {
                next.reversalPreview = true;
                next.inheritWalkContinuity(this);
                next.pendingWalkConfirmation();
            }
            double travel = remainingTravel(next);
            if (next.legs.size() <= MAX_QUEUE && next.clear() && travel < bestTravel - EPS)
            {
                best = next; bestTravel = travel;
            }
        }
        return best;
    }

    private boolean withinGoalInterval(MovementPath candidate, LocalPoint goal)
    {
        for (Leg leg : candidate.legs)
        {
            if (!withinGoalInterval(leg.end.getX(), leg.end.getY(), goal)) { return false; }
        }
        return true;
    }

    private boolean withinGoalInterval(double px, double py, LocalPoint goal)
    {
        return px >= Math.min(x, goal.getX()) - EPS && px <= Math.max(x, goal.getX()) + EPS &&
            py >= Math.min(y, goal.getY()) - EPS && py <= Math.max(y, goal.getY()) + EPS;
    }

    /** Fresh idle Walks and moving replacements share the same already-true-goal rule. */
    void pendingWalkConfirmation()
    {
        walkForecast = true;
        if (!legs.isEmpty() && confirmed.equals(clickedDestination()) && !position().equals(confirmed))
        {
            speculative = reversalPreview = true;
        }
    }

    /** Re-anchor a fresh walk to new off-route authority through the occupied checked edge. */
    MovementPath alignWalkAuthority(LocalPoint endpoint, boolean run, long now)
    {
        LocalPoint goal = clickedDestination();
        if (invalid || recovering || !speculative || !agreement || replacement ||
            !reversalPreview || !sameView(endpoint, confirmed) || endpoint.equals(confirmed) ||
            clickedPath.contains(endpoint) || goal == null || now >= deadline || now >= chainDeadline) { return null; }
        // A preceding step can be off this fresh itinerary even when its old
        // true tile was the construction origin. Checked forward progress must
        // join the latest goal instead of rejecting it and idling at that step.
        MovementPath joined = alignForwardAuthority(endpoint, goal, run, now, true);
        if (joined != null) { joined.inheritWalkContinuity(this); }
        return joined;
    }

    /** The same checked forward-authority join for a captured object/item approach. */
    MovementPath alignApproachAuthority(LocalPoint endpoint, boolean run, long now)
    {
        LocalPoint goal = clickedDestination();
        if (invalid || recovering || !speculative || !agreement || replacement ||
            !sameView(endpoint, confirmed) || endpoint.equals(confirmed) || clickedPath.contains(endpoint) ||
            hasUnfinishedConfirmedPrefix() || goal == null || now >= deadline || now >= chainDeadline) { return null; }
        MovementPath joined = alignForwardAuthority(endpoint, goal, run, now, false);
        if (joined != null)
        {
            // The forward join already adopts this new authority before the
            // controller's accept(). Count that real progress exactly once.
            List<LocalPoint> progress = checkedRoute(confirmed, endpoint, collision, 2);
            joined.confirmedTripTiles = Math.min(MAX_ROUTE, confirmedTripTiles + (progress == null ? 0 : progress.size()));
            joined.tripEnded = false;
            joined.confirmedArrivalTrip = confirmedArrivalTrip;
        }
        return joined;
    }

    /** Shared construction after Walk/scene evidence gates; only checked forward authority grants a new horizon. */
    private MovementPath alignForwardAuthority(LocalPoint endpoint, LocalPoint goal, boolean run, long now, boolean walk)
    {
        List<LocalPoint> steps = checkedRoute(confirmed, endpoint, collision, 2);
        long dx = endpoint.getX() - confirmed.getX(), dy = endpoint.getY() - confirmed.getY();
        if (steps == null || dx * (goal.getX() - confirmed.getX()) + dy * (goal.getY() - confirmed.getY()) <= 0 ||
            distance(endpoint, goal) > distance(confirmed, goal)) { return null; }
        MovementPath forecast = anticipate(endpoint, endpoint, goal, run || steps.size() > 1, now, multiplier,
            freshDeadline(now), collision);
        if (forecast == null) { return null; }
        if (steps.size() > 1 || run && running())
        {
            forecast.running = true;
            for (Leg leg : forecast.legs) { leg.running = true; }
        }
        return joinForecast(forecast, dx, dy, walk);
    }

    /** Join an authority-anchored checked itinerary without returning to its construction origin. */
    private MovementPath joinForecast(MovementPath forecast, double dx, double dy)
    {
        return joinForecast(forecast, dx, dy, false);
    }

    private MovementPath joinForecast(MovementPath forecast, double dx, double dy, boolean walk)
    {
        LocalPoint endpoint = forecast.confirmed;
        if (distance(position(), endpoint) > (forecast.forecastSteps() + 1) * TILE) { return null; }
        Leg active = legs.isEmpty() ? lastLeg : legs.peekFirst();
        if (active == null) { return null; }
        if (active.corridorEnd == null && clearTurnSquare(active.start, active.end, collision))
        {
            active = new Leg(active.start, active.end, active.running, false, active.end);
        }
        boolean corridor = active.corridorEnd != null;
        if (corridor ? !insideCheckedCorridor(active, x, y) || !clearCorridor(active.start, active.corridorEnd, collision)
            : !onSegment(active.start, active.end) || !collision.test(active.start, active.end) ||
                !collision.test(active.end, active.start)) { return null; }

        List<MovementPath> candidates = new ArrayList<>();
        // The same fully checked square/shared-boundary join serves native
        // refinements too. Their callers retain confirmed-prefix eligibility,
        // reserves and deadlines; they do not acquire Walk ownership or curves.
        MovementPath departure = joinDeparture(active, forecast, true, walk);
        if (departure != null)
        {
            departure.reversalPreview = forecast.reversalPreview;
            candidates.add(departure);
        }
        // Already on the authority-anchored route: retain its checked chord or
        // edge from the exact fractional point, omitting obsolete construction legs.
        boolean joined = false;
        MovementPath contained = joinedWalkForecast(forecast);
        for (Leg leg : forecast.legs)
        {
            joined |= onSegment(leg.start, leg.end) || insideCheckedCorridor(leg, x, y);
            if (joined) { contained.legs.addLast(leg); }
        }
        if (joined) { candidates.add(contained); }

        List<LocalPoint> anchors = new ArrayList<>();
        anchors.add(active.end); anchors.add(active.start);
        if (corridor) { corridorAnchors(active, anchors); }
        BiPredicate<LocalPoint, LocalPoint> reversible = (a, b) -> collision.test(a, b) && collision.test(b, a);
        int gap = forecast.forecastSteps() + 1;
        for (LocalPoint anchor : anchors)
        {
            if (distance(anchor, endpoint) > gap * TILE) { continue; }
            for (int index = 0; index < forecast.preview.size(); ++index)
            {
                LocalPoint join = forecast.preview.get(index);
                List<LocalPoint> bridge = checkedRoute(anchor, join, reversible, gap);
                if (bridge == null || bridge.stream().anyMatch(p -> distance(p, endpoint) > gap * TILE)) { continue; }
                MovementPath next = joinedWalkForecast(forecast);
                if (Math.abs(anchor.getX() - x) > EPS || Math.abs(anchor.getY() - y) > EPS)
                {
                    next.legs.addLast(corridor ? active.endingAt(anchor, active.running) :
                        new Leg(anchor.equals(active.end) ? active.start : active.end, anchor, active.running, false, null));
                }
                next.append(anchor, bridge);
                next.append(join, forecast.preview.subList(index + 1, forecast.preview.size()));
                if (next.legs.size() <= MAX_QUEUE && next.clear()) { candidates.add(next); }
            }
        }
        MovementPath best = null, forward = null, directed = null;
        double bestTravel = Double.POSITIVE_INFINITY, bestGeometry = Double.POSITIVE_INFINITY;
        double forwardTravel = Double.POSITIVE_INFINITY, forwardGeometry = Double.POSITIVE_INFINITY;
        double directedTravel = Double.POSITIVE_INFINITY, directedGeometry = Double.POSITIVE_INFINITY;
        for (MovementPath candidate : candidates)
        {
            double travel = remainingTravel(candidate), geometry = remainingGeometry(candidate);
            if (walk && withinGoalInterval(candidate, forecast.clickedDestination()) &&
                (travel < directedTravel - EPS || Math.abs(travel - directedTravel) <= EPS && geometry < directedGeometry - EPS))
            {
                directed = candidate; directedTravel = travel; directedGeometry = geometry;
            }
            if (travel < bestTravel - EPS || Math.abs(travel - bestTravel) <= EPS && geometry < bestGeometry - EPS)
            {
                best = candidate; bestTravel = travel; bestGeometry = geometry;
            }
            Leg first = candidate.legs.peekFirst();
            if (first != null && dx * (first.end.getX() - x) + dy * (first.end.getY() - y) >= -EPS &&
                (travel < forwardTravel - EPS || Math.abs(travel - forwardTravel) <= EPS && geometry < forwardGeometry - EPS))
            {
                forward = candidate; forwardTravel = travel; forwardGeometry = geometry;
            }
        }
        // Prefer a legal forward join over a small time saving from retracing.
        // A blocked layout may still require the original checked recovery.
        if (directed != null && directedTravel <= bestTravel + TILE) { return directed; }
        return forward != null && forwardTravel <= bestTravel + TILE ? forward : best;
    }

    private MovementPath joinedWalkForecast(MovementPath forecast)
    {
        MovementPath next = new MovementPath(forecast.origin, forecast.confirmed, lastNanos, multiplier, collision);
        next.x = x; next.y = y;
        next.clickedPath.addAll(forecast.clickedPath);
        next.preview.addAll(forecast.preview);
        next.running = forecast.running;
        next.speculative = forecast.speculative;
        next.agreement = true;
        next.deadline = forecast.deadline; next.chainDeadline = forecast.chainDeadline;
        next.npcMin = forecast.npcMin; next.npcMax = forecast.npcMax;
        next.npcReserveTiles = forecast.npcReserveTiles;
        next.nativeCounterPairs = forecast.nativeCounterPairs;
        next.nativeCounterReleaseIndex = forecast.nativeCounterReleaseIndex;
        next.npcArrivalGoal = forecast.npcArrivalGoal; next.counterNativeGoal = forecast.counterNativeGoal;
        next.counterBoundaryGoal = forecast.counterBoundaryGoal;
        next.combatTracking = forecast.combatTracking;
        next.combatCredit = forecast.combatCredit;
        next.combatTrailing = forecast.combatTrailing;
        return next;
    }

    private void inheritWalkContinuity(MovementPath previous)
    {
        // This is a one-shot join, not a new movement owner or prediction credit.
        walkContinuity = true;
        walkForecast = previous.walkForecast;
        tangentX = previous.tangentX; tangentY = previous.tangentY;
    }

    void armCombat(long now)
    {
        // This is a new owned click. A completed recovery is an idle start,
        // not an in-flight correction that should defer another attack.
        if (!invalid && finished()) { recovering = false; }
        long hard = restartDeadline(now, confirmed);
        combatTracking = combatCredit = true;
        chainDeadline = hard;
        deadline = Math.min(now + RESPONSE_NANOS, hard);
    }

    void endCombat() { combatTracking = combatCredit = combatTrailing = false; }

    /** One observed melee target step spends the existing reserve; idle observation never rearms it. */
    MovementPath followCombat(NpcApproach target, boolean run, long now)
    {
        return followCombat(target, run, now, false);
    }

    MovementPath followCombat(NpcApproach target, boolean run, long now, boolean trail)
    {
        if (!target.movingCombat() || invalid || recovering && !finished() || !combatTracking) { return null; }
        if (!moving() && target.combatBoundary(position()))
        {
            npcMin = target.min; npcMax = target.max;
            return this;
        }
        List<LocalPoint> ranged = target.rangedCombat() ? target.rangedSteps(confirmed, run, collision) : null;
        LocalPoint goal = target.rangedCombat() ? ranged == null || ranged.isEmpty() ? null : ranged.get(ranged.size() - 1) :
            target.goal(confirmed, collision);
        if (goal == null)
        {
            if (!target.combatBoundary(position())) { cancel(); }
            npcMin = target.min; npcMax = target.max;
            return target.combatBoundary(confirmed) ? this : null;
        }
        if (goal.equals(clickedDestination()) && agreement && !replacement)
        {
            npcMin = target.min; npcMax = target.max;
            if (trail) { combatTrailing = true; }
            return this;
        }
        // A confirmed, fully reconciled arrival can retain one movement credit
        // while waiting beside a stationary NPC. Its next real step spends that
        // credit once. Repeated NPC motion without player progress cannot renew it.
        boolean spendCredit = combatCredit && position().equals(confirmed);
        long hard = spendCredit ? freshDeadline(now) : chainDeadline;
        long response = spendCredit ? Math.min(now + RESPONSE_NANOS, hard) : deadline;
        if (now >= hard || now >= response) { return null; }
        MovementPath forecast = target.rangedCombat() ? seed(confirmed, confirmed, ranged, run, now, multiplier,
            Math.min(response, hard), collision, target.min, target.max, target.reserveTiles, false, null, true) :
            anticipate(confirmed, confirmed, goal, run, now, multiplier, Math.min(response, hard), collision,
                false, target.min, target.max, 1);
        if (forecast == null) { return null; }
        // A cold one-tile melee approach is still unconfirmed. Retain its
        // native walk gait and share the close-start exponential reserve rather
        // than sprinting into a tile the target may occupy on its next step.
        boolean closeStart = target.adjacentCombat() && !trail && !moving() &&
            position().equals(confirmed) && forecast.clickedPath.size() == 2;
        forecast.running = run && !trail && (!closeStart || forecast.running);
        for (Leg leg : forecast.legs) { leg.running = forecast.running; }
        forecast.easingNpcStart = closeStart;
        forecast.deadline = response; forecast.chainDeadline = hard;
        forecast.combatTracking = true; forecast.combatCredit = false;
        forecast.combatTrailing = trail;
        MovementPath next;
        if (hasUnfinishedConfirmedPrefix())
        {
            replacement(true);
            next = queuePrediction(forecast) ? this : null;
        }
        else if (position().equals(confirmed) && tileCenter(position()))
        {
            next = forecast;
            next.lastNanos = lastNanos;
        }
        else
        {
            // The revised approach can already be behind the displayed player.
            // Prefer progress toward that actual goal, not the old authority's
            // forward direction which can finish an obsolete speculative leg.
            next = joinForecast(forecast, goal.getX() - x, goal.getY() - y);
        }
        if (next != null)
        {
            next.combatTracking = true; next.combatCredit = false;
            next.combatTrailing = trail;
            next.npcRetarget = true;
        }
        return next;
    }

    /** A real observed target step may wait for this correction, never for a new prediction budget. */
    boolean awaitingCombatRecovery(long now)
    {
        return combatTracking && recovering && !invalid && !finished() && now < deadline && now < chainDeadline;
    }

    /** A real owned hit releases only the collision-checked confirmed remainder, never a snap. */
    boolean completeCombatHit(LocalPoint authority, boolean run)
    {
        if (!combatTracking || !combatTrailing || invalid || !sameView(position(), authority)) { return false; }
        if (!recover(authority, run)) { return false; }
        combatTrailing = false;
        running = run;
        for (Leg leg : legs) { leg.running = run; }
        return true;
    }

    boolean combatTrailing() { return combatTrailing; }

    /** A moving red click replaces only the obsolete preview, using this click's NPC evidence. */
    MovementPath retargetNpcApproach(NpcApproach target, LocalPoint destination, boolean run, long now)
    {
        if (invalid || recovering || legs.isEmpty() || hasUnfinishedConfirmedPrefix() || target.combat != null) { return null; }
        long hardDeadline = restartDeadline(now, confirmed);
        MovementPath forecast;
        if (run && target.counterBank())
        {
            NpcApproach.CounterRun plan = target.counterRun(confirmed, collision);
            if (plan == null || destination != null && !destination.equals(plan.nativeGoal)) { return null; }
            forecast = anticipateCounterRun(confirmed, confirmed, plan, now, multiplier, hardDeadline,
                collision, target.min, target.max);
            if (forecast != null) { forecast.releaseCounterBoundary(destination, now); }
        }
        else
        {
            LocalPoint goal = destination == null ? target.goal(confirmed, collision) : destination;
            if (goal == null && target.ordinaryOption() && target.acceptsDestination(confirmed))
            {
                // Authority is already beside the NPC. Retire an old Walk tail
                // now, rather than finishing it and returning only on timeout.
                cancel();
                return this;
            }
            forecast = anticipate(confirmed, confirmed, goal, run, now, multiplier, hardDeadline,
                collision, false, target.min, target.max, target.reserveTiles);
        }
        if (forecast == null) { return null; }
        if (run && running())
        {
            forecast.running = true;
            for (Leg leg : forecast.legs) { leg.running = true; }
        }
        LocalPoint goal = forecast.clickedDestination();
        MovementPath joined = joinForecast(forecast, goal.getX() - confirmed.getX(), goal.getY() - confirmed.getY());
        if (joined != null) { joined.npcRetarget = target.ordinaryOption() || target.counterBank(); }
        return joined;
    }

    /** Click-time intent joins the same checked queue and clock, retaining real confirmed debt. */
    MovementPath anticipateNativeIntent(LocalPoint goal, boolean run, long now, LocalPoint min, LocalPoint max, int reserve,
        boolean counterPairs)
    {
        if (invalid || !sameView(goal,confirmed)) { return null; }
        MovementPath forecast = anticipate(confirmed,confirmed,goal,run,now,multiplier,
            restartDeadline(now,confirmed),collision,false,min,max,reserve);
        if (forecast == null) { return null; }
        forecast.nativeCounterPairs = counterPairs && run;
        if (forecast.nativeCounterPairs) { forecast.extend(); }
        // A short checked seed must not finish and publish idle before the first
        // native endpoint. Reuse the existing single-clock exponential reserve.
        forecast.easingNpcStart = !forecast.nativeCounterPairs && forecast.legs.size()==1 && forecast.preview.size()<=3;
        if (hasUnfinishedConfirmedPrefix() || !speculative && !legs.isEmpty() && legs.peekLast().end.equals(confirmed))
        {
            replacement(true);
            if (!queuePrediction(forecast)) { return null; }
            recovering = false;
            easingNpcStart = forecast.easingNpcStart;
            confirmedTripTiles = 0; confirmedArrivalTrip = false;
            return this;
        }
        if (finished() && tileCenter(position()))
        {
            forecast.lastNanos = lastNanos;
            return forecast;
        }
        MovementPath joined = joinForecast(forecast,goal.getX()-x,goal.getY()-y);
        if (joined != null) { joined.easingNpcStart = forecast.easingNpcStart; }
        return joined;
    }

    /** Native destination evidence supersedes only the provisional stopping reserve. */
    void nativeIntentDestination(boolean retainBoundary)
    {
        if (!retainBoundary) { npcMin = npcMax = null; }
        easingNpcStart = false;
        confirmedArrivalTrip = false;
        nativeCounterPairs = false;
    }

    boolean matchesNativeCounterDestination(LocalPoint destination, long now)
    {
        return nativeCounterPairs && !invalid && !recovering && agreement && !replacement &&
            now < deadline && now < chainDeadline && destination != null && destination.equals(clickedDestination());
    }

    void retireNativeCounterPairs()
    {
        if (nativeCounterPairs) { nativeCounterPairs=false; cancel(); }
    }

    LocalPoint nativeCounterArrivalGoal()
    {
        if (!nativeCounterPairs || invalid || recovering || replacement || !agreement) { return null; }
        int index = nativeCounterBoundaryIndex();
        return index < 0 ? null : clickedPath.get(index);
    }

    boolean releaseNativeCounterContinuation(LocalPoint destination, LocalPoint previousAuthority, long now)
    {
        if (!nativeCounterPairs || invalid || recovering || replacement || !agreement || !running ||
            confirmed.equals(previousAuthority) || destination == null || !destination.equals(clickedDestination()) ||
            now >= deadline || now >= chainDeadline || confirmedClickIndex < nativeCounterBoundaryIndex() || forecastSteps()<2) { return false; }
        int onward=Math.min(clickedPath.size()-1,confirmedClickIndex+2);
        if (onward<=confirmedClickIndex) { return false; }
        for(int i=confirmedClickIndex+1;i<=onward;++i)
        {
            if (!collision.test(clickedPath.get(i-1),clickedPath.get(i)) || !collision.test(clickedPath.get(i),clickedPath.get(i-1))) { return false; }
        }
        nativeCounterReleaseIndex=onward;
        long response=deadline; extend(); deadline=response;
        return true;
    }

    private int nativeCounterBoundaryIndex()
    {
        for (int i=0;i<clickedPath.size();++i)
        {
            if (insideNpcStaging(clickedPath.get(i)))
            {
                // The native client executes a whole run pair before checking
                // counter reach. Do not split a checked pair at its first tile.
                int boundary=running && i%2==1 && i+1<clickedPath.size() ? i+1 : i;
                return Math.max(boundary,nativeCounterReleaseIndex);
            }
        }
        return -1;
    }

    /** Native intent's actor/flag distance never proves a qualifying actual trip. */
    void useConfirmedArrivalTrip()
    {
        confirmedArrivalTrip = true;
    }

    /** New actor input cannot inherit the previous movement's arrival threshold. */
    void beginNativeIntentTrip()
    {
        confirmedTripTiles = 0;
        tripEnded = false;
    }

    /** A changed native flag can repair a failed start within its existing horizon. */
    MovementPath refineConfirmedApproach(LocalPoint destination, boolean run, long now, boolean approach)
    {
        if (invalid || recovering || speculative || now >= chainDeadline ||
            !sameView(destination, confirmed) || destination.equals(confirmed)) { return null; }
        MovementPath forecast = anticipate(confirmed, confirmed, destination, run, now, multiplier,
            chainDeadline, collision, approach);
        if (forecast == null) { return null; }
        forecast.confirmedTripTiles = confirmedTripTiles;
        forecast.tripEnded = tripEnded;
        forecast.confirmedArrivalTrip = confirmedArrivalTrip;
        if (canAnticipateContinuation()) { return queuePrediction(forecast) ? this : null; }
        if (finished())
        {
            forecast.lastNanos = lastNanos;
            return forecast;
        }
        return null;
    }

    /** Native destination evidence replaces an obsolete scene-interaction preview. */
    MovementPath retargetInteraction(LocalPoint destination, boolean run, long now, boolean approach)
    {
        if (invalid || recovering || legs.isEmpty() || !sameView(destination, confirmed)) { return null; }
        if (destination.equals(confirmed)) { cancel(); return this; }
        if (hasUnfinishedConfirmedPrefix()) { return null; }
        MovementPath forecast = anticipate(confirmed, confirmed, destination, run, now, multiplier,
            restartDeadline(now, confirmed), collision, approach);
        if (forecast != null && run && running())
        {
            forecast.running = true;
            for (Leg leg : forecast.legs) { leg.running = true; }
        }
        return forecast == null ? null : joinForecast(forecast,
            destination.getX() - confirmed.getX(), destination.getY() - confirmed.getY());
    }

    /** New forward NPC authority may identify a different side/row than the provisional prefix. */
    MovementPath alignNpcAuthority(LocalPoint endpoint, NpcApproach target, LocalPoint destination, boolean run, long now)
    {
        boolean counter = target.counterBank() && pendingCounterRetarget(now);
        if ((!target.ordinaryOption() && !target.movingCombat() && !counter) || invalid || recovering || !speculative || !agreement || replacement ||
            !sameView(endpoint, confirmed) || endpoint.equals(confirmed) || clickedPath.contains(endpoint) ||
            hasUnfinishedConfirmedPrefix() || now >= deadline || now >= chainDeadline) { return null; }
        List<LocalPoint> steps = checkedRoute(confirmed, endpoint, collision, 2);
        long dx = endpoint.getX() - confirmed.getX(), dy = endpoint.getY() - confirmed.getY();
        long tx = (target.min.getX() + target.max.getX()) / 2L - confirmed.getX();
        long ty = (target.min.getY() + target.max.getY()) / 2L - confirmed.getY();
        if (steps == null || dx * tx + dy * ty <= 0) { return null; }
        List<LocalPoint> ranged = target.rangedCombat() ? target.rangedSteps(endpoint, run || steps.size() > 1, collision) : null;
        LocalPoint goal = target.rangedCombat() ? ranged == null || ranged.isEmpty() ? null : ranged.get(ranged.size() - 1) :
            target.acceptsDestination(destination) ? destination : target.goal(endpoint, collision);
        MovementPath forecast;
        if (counter)
        {
            if (!run) { return null; }
            NpcApproach.CounterRun plan = target.counterRun(endpoint, collision);
            forecast = anticipateCounterRun(endpoint, endpoint, plan, now, multiplier, freshDeadline(now),
                collision, target.min, target.max);
            if (forecast != null) { forecast.releaseCounterBoundary(destination, now); }
        }
        else if (goal == null && (target.movingCombat() ? target.combatBoundary(endpoint) : target.acceptsDestination(endpoint)))
        {
            forecast = idle(endpoint, now, multiplier, collision);
            forecast.clickedPath.add(endpoint); forecast.preview.add(endpoint);
        }
        else
        {
            forecast = target.rangedCombat() ? ranged == null ? null : seed(endpoint, endpoint, ranged, run || steps.size() > 1,
                now, multiplier, freshDeadline(now), collision, target.min, target.max, target.reserveTiles, false, null, true) :
                anticipate(endpoint, endpoint, goal, run || steps.size() > 1, now, multiplier,
                freshDeadline(now), collision, false, target.min, target.max, target.reserveTiles);
        }
        if (forecast == null) { return null; }
        if (steps.size() > 1 || run && running())
        {
            forecast.running = true;
            for (Leg leg : forecast.legs) { leg.running = true; }
        }
        MovementPath joined = joinForecast(forecast, dx, dy);
        if (joined != null && counter) { joined.npcRetarget = true; }
        if (joined != null && target.movingCombat())
        {
            joined.combatTracking = joined.combatCredit = true;
            joined.combatTrailing = target.adjacentCombat() && combatTrailing;
        }
        return joined;
    }

    /**
     * A revised native interaction destination may redirect an unconfirmed preview.
     * The player keeps travelling along the currently occupied checked edge to one of its
     * endpoints, then follows a freshly checked route from that anchor. No new
     * diagonal or corner is invented, confirmed legs and the existing hard
     * deadline are preserved, and the bounded queue/gap limits still apply.
     */
    MovementPath anticipateRedirect(LocalPoint destination, boolean run, long now)
    {
        if (invalid || recovering || !speculative || legs.isEmpty())
        {
            return null;
        }
        Leg active = legs.peekFirst();
        if (active == null || active.corridorEnd != null || !onSegment(active.start, active.end) ||
            !sameView(destination, active.start))
        {
            return null;
        }
        if (destination.equals(active.end) || destination.equals(active.start) || destination.equals(position()))
        {
            return null;
        }
        if (!reversalPreview && !awaitingOrigin && !position().equals(confirmed))
        {
            // A queued forecast can share its queue with unfinished confirmed
            // movement. Only the unconfirmed part is eligible for redirection.
            if (hasUnfinishedConfirmedPrefix()) { return null; }
        }
        MovementPath forward = redirectFrom(active, active.end, destination, run, now);
        MovementPath backward = null;
        if (collision.test(active.end, active.start) && collision.test(active.start, active.end))
        {
            backward = redirectFrom(new Leg(active.end, active.start, active.running, false, null),
                active.start, destination, run, now);
        }
        if (forward == null) { return backward; }
        if (backward == null) { return forward; }
        return remainingTravel(forward) <= remainingTravel(backward) ? forward : backward;
    }

    /** A revised native interaction tile may finish the already occupied checked knight chord. */
    boolean holdCombatForecast(LocalPoint destination, long now)
    {
        // A flag inside combat reach is not permission to enter that region.
        // Keep the existing checked outer forecast; new server steps can still
        // correct its direction or finish closer. No new clock/budget is granted.
        return !invalid && !recovering && speculative && agreement && !replacement &&
            now < deadline && now < chainDeadline && insideNpcStaging(destination) &&
            !preview.isEmpty() && insideNpcStaging(preview.get(preview.size() - 1));
    }

    /** Scene/NPC refinements share the authority-anchored join, not a backward startup connector. */
    MovementPath retargetApproachDestination(LocalPoint destination, boolean run, long now)
    {
        return retargetApproachDestination(destination, run, now, false);
    }

    MovementPath retargetApproachDestination(LocalPoint destination, boolean run, long now, boolean approach)
    {
        if (speculative && (now >= deadline || now >= chainDeadline)) { return null; }
        if (!invalid && !recovering && speculative && !replacement && agreement &&
            !legs.isEmpty())
        {
            // A native refinement is the same interaction, not a fresh click.
            // Rebuild from authority and join the occupied checked edge/corridor;
            // never choose a backward construction-anchor detour for a shorter
            // capped forecast. Keep both original deadlines and the NPC policy.
            MovementPath forecast = anticipate(confirmed, confirmed, destination, run, now, multiplier,
                Math.min(deadline, chainDeadline), collision, approach, npcMin, npcMax, npcReserveTiles);
            if (forecast != null)
            {
                forecast.deadline = Math.min(deadline, chainDeadline);
                forecast.chainDeadline = chainDeadline;
                if (hasUnfinishedConfirmedPrefix())
                {
                    // The native endpoint can refine while the display still owes
                    // checked confirmed travel. Preserve that prefix and fraction,
                    // queue only the replacement forecast, and reuse both deadlines.
                    // A blocked/unavailable forecast still takes ordinary recovery.
                    if (legs.size() + forecast.legs.size() > MAX_QUEUE) { return null; }
                    replacement(true);
                    return queuePrediction(forecast) ? this : null;
                }
                MovementPath joined = joinForecast(forecast, destination.getX() - x, destination.getY() - y);
                if (joined != null)
                {
                    joined.confirmedTripTiles = confirmedTripTiles; joined.tripEnded = tripEnded;
                    joined.confirmedArrivalTrip = confirmedArrivalTrip;
                    return joined;
                }
            }
        }
        MovementPath fallback = retargetNativeApproach(destination, run, now);
        if (fallback != null)
        {
            // The checked occupied-edge fallback is also a refinement, not
            // replacement input. It cannot renew either prediction horizon.
            fallback.deadline = Math.min(fallback.deadline, deadline);
            fallback.chainDeadline = Math.min(fallback.chainDeadline, chainDeadline);
            fallback.confirmedTripTiles = confirmedTripTiles;
            fallback.tripEnded = tripEnded;
            fallback.confirmedArrivalTrip = confirmedArrivalTrip;
        }
        return fallback;
    }

    /** A revised native interaction tile may finish the already occupied checked knight chord. */
    MovementPath retargetNativeApproach(LocalPoint destination, boolean run, long now)
    {
        // An early NPC prefix can finish before the native approach is published.
        // Continue from that exact endpoint within the same prediction horizon.
        if (!invalid && !recovering && speculative && legs.isEmpty() && tileCenter(position()))
        {
            if (now < deadline && now < chainDeadline && insideNpcStaging(position()) && insideNpcStaging(destination))
            {
                // A closer native flag still does not prove interaction reach.
                // Stay at the checked staging endpoint until authority advances;
                // neither resurrect movement nor cancel back toward an old tile.
                return this;
            }
            return anticipate(position(), confirmed, destination, run, now, multiplier,
                Math.min(deadline, chainDeadline), collision, false, npcMin, npcMax, npcReserveTiles);
        }
        MovementPath redirected = anticipateRedirect(destination, run, now);
        if (redirected != null) { return redirected; }
        if (invalid || recovering || !speculative || legs.isEmpty()) { return null; }
        Leg active = legs.peekFirst();
        if (active.corridorEnd == null || !onSegment(active.start, active.end) ||
            !sameView(destination, active.end) || destination.equals(active.end)) { return null; }
        if (!reversalPreview && !awaitingOrigin && !position().equals(confirmed) &&
            hasUnfinishedConfirmedPrefix()) { return null; }
        // Unlike a fresh yellow click, this is the native route's own revised
        // approach tile. Finish the occupied corridor; never reverse/cut it.
        return redirectFrom(active, active.end, destination, run, now);
    }

    private boolean hasUnfinishedConfirmedPrefix()
    {
        for (Leg leg : legs)
        {
            if (leg.end.equals(confirmed) || inside(leg, confirmed.getX(), confirmed.getY()) &&
                !leg.start.equals(confirmed) && (leg != legs.peekFirst() ||
                    (leg.end.getX() - leg.start.getX()) * (confirmed.getX() - x) +
                    (leg.end.getY() - leg.start.getY()) * (confirmed.getY() - y) > EPS)) { return true; }
        }
        // Authority inside an occupied knight can already be behind the display.
        // Its containing rectangle does not confirm the old speculative suffix.
        return false;
    }

    private MovementPath redirectFrom(Leg connector, LocalPoint anchor, LocalPoint destination,
        boolean run, long now)
    {
        if (connector.corridorEnd != null ? !clearCorridor(connector.start, connector.corridorEnd, collision)
            : !collision.test(connector.start, connector.end) || !collision.test(connector.end, connector.start)) { return null; }
        MovementPath next = anticipate(anchor, confirmed, destination, run, now, multiplier,
            restartDeadline(now, confirmed), collision, false, npcMin, npcMax, npcReserveTiles);
        if (next == null || next.legs.isEmpty() || next.legs.size() + 1 > MAX_QUEUE) { return null; }
        next.easingNpcStart = false; // A refinement connector is not a new idle start.
        int gap = (next.forecastSteps() + 1) * TILE;
        if (distance(position(), confirmed) > gap || distance(anchor, confirmed) > gap) { return null; }
        // The connector is the occupied checked edge/chord (or a checked
        // reverse edge for native interaction redirects).
        // Starting from the exact sub-tile position keeps the travel continuous.
        if (Math.abs(anchor.getX() - x) > EPS || Math.abs(anchor.getY() - y) > EPS) { next.legs.addFirst(connector); }
        next.x = x;
        next.y = y;
        next.lastNanos = lastNanos;
        // Reuse the reversal stale-tick handling: a delayed old-direction update
        // must not immediately undo the redirect or build a zig-zag connector.
        next.reversalPreview = true;
        return next.onSegment(connector.start, connector.end) ? next : null;
    }

    private double remainingTravel(MovementPath path)
    {
        if (path.legs.isEmpty()) { return 0; }
        double length = 0, px = x, py = y;
        for (Leg leg : path.legs)
        {
            length += Math.max(Math.abs(leg.end.getX() - px), Math.abs(leg.end.getY() - py));
            px = leg.end.getX(); py = leg.end.getY();
        }
        // Both candidates must be compared through the clicked destination,
        // not just their potentially different bounded forecast lengths.
        int tail = path.clickedPath.indexOf(path.legs.peekLast().end);
        return length + Math.max(0, path.clickedPath.size() - tail - 1) * TILE;
    }

    /** Break equal travel-time ties by visible distance, not anchor enumeration order. */
    private double remainingGeometry(MovementPath path)
    {
        double length = 0, px = x, py = y;
        for (Leg leg : path.legs)
        {
            length += Math.hypot(leg.end.getX() - px, leg.end.getY() - py);
            px = leg.end.getX(); py = leg.end.getY();
        }
        if (!path.legs.isEmpty())
        {
            int tail = path.clickedPath.indexOf(path.legs.peekLast().end);
            for (int i = tail + 1; i < path.clickedPath.size(); ++i)
            {
                LocalPoint point = path.clickedPath.get(i);
                length += Math.hypot(point.getX() - px, point.getY() - py);
                px = point.getX(); py = point.getY();
            }
        }
        return length;
    }

    /** Hard chain deadline shared by rapid clicks; exposed for focused tests. */
    long predictionDeadlineNanos() { return chainDeadline; }

    /** Queue a fresh click behind confirmed movement without an idle frame or a position reset. */
    boolean anticipateContinuation(LocalPoint destination, boolean run, long now)
    {
        return anticipateContinuation(destination, run, now, false);
    }

    boolean anticipateContinuation(LocalPoint destination, boolean run, long now, boolean approach)
    {
        return anticipateContinuation(destination, run, now, approach, null, null);
    }

    boolean anticipateContinuation(LocalPoint destination, boolean run, long now, boolean approach,
        LocalPoint npcMin, LocalPoint npcMax)
    {
        return anticipateContinuation(destination, run, now, approach, npcMin, npcMax, 2);
    }

    boolean anticipateContinuation(LocalPoint destination, boolean run, long now, boolean approach,
        LocalPoint npcMin, LocalPoint npcMax, int npcReserveTiles)
    {
        if (!canAnticipateContinuation()) { return false; }
        MovementPath next = anticipate(confirmed, confirmed, destination, run, now, multiplier,
            restartDeadline(now, confirmed), collision, approach, npcMin, npcMax, npcReserveTiles);
        return queuePrediction(next);
    }

    boolean anticipateCounterContinuation(NpcApproach.CounterRun plan, long now, LocalPoint npcMin, LocalPoint npcMax)
    {
        if (!canAnticipateContinuation()) { return false; }
        boolean queued = queuePrediction(anticipateCounterRun(confirmed, confirmed, plan, now, multiplier,
            restartDeadline(now, confirmed), collision, npcMin, npcMax));
        if (queued) { npcRetarget = true; }
        return queued;
    }

    private boolean queuePrediction(MovementPath next)
    {
        if (next == null || legs.size() + next.legs.size() > MAX_QUEUE) { return false; }
        clickedPath.clear(); clickedPath.addAll(next.clickedPath);
        preview = next.preview;
        confirmedClickIndex = confirmedPreviewIndex = 0;
        legs.addAll(next.legs);
        running = next.running;
        speculative = agreement = true;
        reversalPreview = false;
        replacement = false;
        deadline = next.deadline;
        chainDeadline = next.chainDeadline;
        this.npcMin = next.npcMin; this.npcMax = next.npcMax;
        this.npcReserveTiles = next.npcReserveTiles;
        nativeCounterPairs = next.nativeCounterPairs;
        nativeCounterReleaseIndex = next.nativeCounterReleaseIndex;
        npcArrivalGoal = next.npcArrivalGoal; counterNativeGoal = next.counterNativeGoal;
        counterBoundaryGoal = next.counterBoundaryGoal;
        combatTracking = next.combatTracking; combatCredit = next.combatCredit;
        combatTrailing = next.combatTrailing;
        return true;
    }

    /** Adopt a native/fractional presentation without snapping to a tile centre. */
    static MovementPath adopt(LocalPoint visible, LocalPoint authoritative, boolean run, long now,
        double speed, BiPredicate<LocalPoint, LocalPoint> collision)
    {
        if (!sameView(visible, authoritative))
        {
            return null;
        }
        if (visible.equals(authoritative))
        {
            return idle(visible, now, speed, collision);
        }
        int floorX = Math.floorDiv(visible.getX() - TILE / 2, TILE) * TILE + TILE / 2;
        int floorY = Math.floorDiv(visible.getY() - TILE / 2, TILE) * TILE + TILE / 2;
        for (int ix = 0; ix < 2; ++ix)
        {
            for (int iy = 0; iy < 2; ++iy)
            {
                LocalPoint anchor = new LocalPoint(floorX + ix * TILE, floorY + iy * TILE, visible.getWorldView());
                List<LocalPoint> route = checkedRoute(anchor, authoritative, collision, MAX_QUEUE);
                if (route == null || route.isEmpty())
                {
                    continue;
                }
                MovementPath path = new MovementPath(anchor, authoritative, now, speed, collision);
                path.running = run && distance(anchor, authoritative) > TILE;
                path.append(anchor, route);
                path.x = visible.getX();
                path.y = visible.getY();
                Leg first = path.legs.peekFirst();
                if (path.onSegment(first.start, first.end) || inside(first, path.x, path.y))
                {
                    first.headingX = first.headingY = Double.NaN;
                    return path;
                }
            }
        }
        return null;
    }

    static double normalizeSpeed(double value)
    {
        // The user-facing 0.1 increment is an internal 0.02 increment.
        // Clamp invalid/extreme input without quantizing that finer pace again.
        return Double.isFinite(value) ? Math.max(0.1, Math.min(2, value)) : 1;
    }

    /** Convert the user-facing 5.0 baseline to the internal 1.0 pace. */
    static double configuredSpeed(double value)
    {
        return normalizeSpeed(value / 5.0);
    }

    void speed(double value) { multiplier = normalizeSpeed(value); }
    private double rate(boolean run) { return 4.0 * (run ? 2 : 1) / Constants.CLIENT_TICK_LENGTH * multiplier; }
    private int forecastSteps() { return Math.max(1, (int) Math.ceil(rate(running) * 900 / TILE)); }
    static long freshDeadline(long now) { return now + CHAIN_NANOS; }

    long restartDeadline(long now, LocalPoint authoritative)
    {
        return position().equals(authoritative) || !confirmed.equals(authoritative) ? freshDeadline(now) : chainDeadline;
    }

    boolean accept(LocalPoint endpoint, boolean run)
    {
        if (confirmed.equals(endpoint))
        {
            return true;
        }
        easingNpcStart = false;
        if (endpoint.equals(npcArrivalGoal)) { npcArrivalGoal = counterNativeGoal = counterBoundaryGoal = null; }
        List<LocalPoint> steps = checkedRoute(confirmed, endpoint, collision, 2);
        if (agreement && !replacement && !recovering && !awaitingOrigin)
        {
            // The server publishes endpoints, not the intermediate tile of a
            // run. Retain our checked one/two-step bend when its endpoint is
            // confirmed, rather than replacing it with a different ordering.
            for (int index = confirmedClickIndex + 1; index < clickedPath.size() && index <= confirmedClickIndex + 2; ++index)
            {
                if (!endpoint.equals(clickedPath.get(index))) { continue; }
                List<LocalPoint> planned = clickedPath.subList(confirmedClickIndex + 1, index + 1);
                LocalPoint previous = confirmed;
                boolean clear = true;
                for (LocalPoint point : planned)
                {
                    clear &= collision.test(previous, point) && collision.test(point, previous);
                    previous = point;
                }
                if (clear) { steps = new ArrayList<>(planned); }
                break;
            }
        }
        if (steps == null)
        {
            return false;
        }
        if (nativeCounterPairs && steps.size()!=2)
        {
            // Unsupported single-step authority ends the provisional pair
            // assumption. Preserve/reconcile real movement under normal checks.
            nativeCounterPairs = false;
            cancel();
        }
        if (tripEnded) { confirmedTripTiles = 0; }
        confirmedTripTiles = Math.min(MAX_ROUTE, confirmedTripTiles + steps.size());
        tripEnded = false;
        if (retainNpcRetarget(endpoint) || retainPendingReversal(endpoint)) { return !invalid; }
        npcRetarget = false;
        if (combatTracking && sameView(npcMin, npcMax))
        {
            long dx = endpoint.getX() - confirmed.getX(), dy = endpoint.getY() - confirmed.getY();
            long tx = (npcMin.getX() + npcMax.getX()) / 2L - confirmed.getX();
            long ty = (npcMin.getY() + npcMax.getY()) / 2L - confirmed.getY();
            if (dx * tx + dy * ty > 0)
            {
                combatCredit = true;
                deadline = lastNanos + RESPONSE_NANOS;
            }
        }
        chainDeadline = lastNanos + CHAIN_NANOS;
        if (awaitingOrigin && !recovering)
        {
            int index = clickedPath.indexOf(endpoint);
            int previousGap = distance(confirmed, origin);
            confirmed = endpoint;
            if (index >= 0)
            {
                int planned = preview.size() - 1;
                if (index > planned)
                {
                    List<LocalPoint> known = clickedPath.subList(planned + 1, index + 1);
                    append(preview.get(planned), known);
                    preview.addAll(known);
                }
                awaitingOrigin = false;
                reversalPreview = false;
                confirmedClickIndex = confirmedPreviewIndex = index;
                speculative = index < preview.size() - 1;
                deadline = lastNanos + RESPONSE_NANOS;
                extend();
            }
            else if (distance(position(), endpoint) > (forecastSteps() + 1) * TILE ||
                (!legs.isEmpty() && distance(legs.peekLast().end, endpoint) > (forecastSteps() + 1) * TILE))
            {
                cancel();
            }
            else if (reversalPreview && agreement && !replacement && distance(endpoint, origin) < previousGap &&
                (endpoint.getX() == origin.getX() && clickedPath.get(1).getX() == origin.getX() ||
                    endpoint.getY() == origin.getY() && clickedPath.get(1).getY() == origin.getY()))
            {
                replenishReversalApproach();
            }
            return !invalid;
        }
        // A published two-tile step is running even when Ctrl temporarily
        // overrides a disabled run orb. A short continuation retains the
        // established gait only while the requested run mode remains active.
        consecutiveSingleSteps = steps.size() == 1 ? consecutiveSingleSteps + 1 : 0;
        running = steps.size() > 1 || (run && running &&
            (consecutiveSingleSteps < 2 || speculative && agreement && !replacement));
        if (agreement && !replacement && !recovering)
        {
            for (LocalPoint step : steps)
            {
                if (confirmedClickIndex + 1 >= clickedPath.size() || !step.equals(clickedPath.get(confirmedClickIndex + 1)))
                {
                    agreement = false;
                    break;
                }
                ++confirmedClickIndex;
            }
        }
        // Preserve a queue that still ends at the confirmed tile. Reconnecting
        // it can choose the old diagonal's start as a shorter anchor, reversing
        // through a bend the player has just traversed. Off-route/recovery
        // states still need the original reconciliation below.
        boolean confirmedTail = legs.isEmpty() ? position().equals(confirmed) : legs.peekLast().end.equals(confirmed);
        if (recovering || (replacement && !speculative && !confirmedTail))
        {
            if (!reconnect(endpoint, steps)) { return false; }
            speculative = recovering = false;
        }
        else if (speculative)
        {
            int matched = 0;
            int remaining = preview.size() - confirmedPreviewIndex - 1;
            while (matched < steps.size() && matched < remaining &&
                steps.get(matched).equals(preview.get(confirmedPreviewIndex + matched + 1)))
            {
                ++matched;
            }
            if (matched == steps.size() || matched == remaining)
            {
                if (matched > 0) { reversalPreview = false; }
                confirmedPreviewIndex += matched;
                Leg confirmedEnd = null;
                for (Leg leg : legs) { if (leg.end.equals(confirmed)) { confirmedEnd = leg; break; } }
                boolean predicted = confirmedEnd == null;
                for (Leg leg : legs)
                {
                    if (predicted) { leg.running = running; }
                    if (leg == confirmedEnd) { predicted = true; }
                }
                if (matched < steps.size())
                {
                    append(preview.get(preview.size() - 1), steps.subList(matched, steps.size()));
                }
                speculative = confirmedPreviewIndex < preview.size() - 1;
                if (speculative) { deadline = lastNanos + RESPONSE_NANOS; }
            }
            else
            {
                // A new forecast may be queued behind an unfinished confirmed
                // corner. Reject only that forecast, not the confirmed prefix.
                replacement(true);
                if (!speculative) { append(confirmed, steps); }
                else if (!reconnect(endpoint, steps)) { return false; }
                speculative = false;
            }
        }
        else
        {
            append(confirmed, steps);
        }
        confirmed = endpoint;
        extend();
        if (!speculative) { reversalPreview = false; deadline = lastNanos + RESPONSE_NANOS; }
        return legs.size() <= MAX_QUEUE;
    }

    private boolean retainPendingReversal(LocalPoint endpoint)
    {
        if (!reversalPreview || !speculative || !agreement || replacement || recovering ||
            lastNanos >= deadline || lastNanos >= chainDeadline || clickedPath.isEmpty()) { return false; }
        int index = clickedPath.indexOf(endpoint);
        if (index >= 0 && (awaitingOrigin && index == 0 || confirmedClickIndex >= 0 &&
            index > confirmedClickIndex && index <= confirmedClickIndex + 2))
        {
            // Checked forward logical progress (including an actual construction
            // origin) can legitimately move away from the geometric goal around
            // an obstacle. It is route evidence, not a stale reverse endpoint.
            return false;
        }
        boolean opposite;
        if (clickedPath.size() == 1)
        {
            // A fresh endpoint click can consist solely of its checked fractional
            // connector (including a knight). The singleton is the old true goal,
            // not proof that subsequent away-moving authority belongs to this click.
            opposite = !endpoint.equals(clickedPath.get(0));
        }
        else
        {
            LocalPoint goal = clickedDestination();
            int dx = goal.getX() - origin.getX(), dy = goal.getY() - origin.getY();
            // A knight's first logical step is cardinal, but its real published
            // endpoint is off that line. Compare the whole latest itinerary: a
            // delayed preceding endpoint must not confirm its construction origin
            // or append a return trip behind the newly clicked reversal.
            long progress = (long) dx * (endpoint.getX() - confirmed.getX()) +
                (long) dy * (endpoint.getY() - confirmed.getY());
            // An explicitly owned fresh Walk can also receive a perpendicular
            // endpoint from its preceding click. It confirms no latest-route
            // progress and must not add a return trip or renew either deadline.
            // A delayed step can be positive on the construction route's minor
            // axis while going away from the displayed latest-goal direction.
            // Keep only an owned bounded forecast when BOTH the authority gap
            // increases and that displayed direction is opposed. Logical route
            // matching above still wins for genuine obstacle detours.
            double visibleProgress = (goal.getX() - x) * (endpoint.getX() - confirmed.getX()) +
                (goal.getY() - y) * (endpoint.getY() - confirmed.getY());
            opposite = progress < 0 || walkForecast && (progress == 0 || visibleProgress < 0 &&
                distance(endpoint, goal) > distance(confirmed, goal));
        }
        if (!opposite) { return false; }

        // A server tick can still describe the previous click. Keep the latest
        // reversal speculative until its existing response deadline, rather
        // than immediately turning back toward that older endpoint. This does
        // not renew either deadline or treat the old tick as forward agreement.
        return retainForecastAuthority(endpoint);
    }

    private boolean retainNpcRetarget(LocalPoint endpoint)
    {
        if (!canRetainNpcRetarget(endpoint, lastNanos)) { return false; }
        return retainForecastAuthority(endpoint);
    }

    private boolean canRetainNpcRetarget(LocalPoint endpoint, long now)
    {
        if (!npcRetarget || !speculative || !agreement || replacement || recovering ||
            now >= deadline || now >= chainDeadline || clickedPath.contains(endpoint)) { return false; }
        LocalPoint goal = clickedDestination();
        long dx = endpoint.getX() - confirmed.getX(), dy = endpoint.getY() - confirmed.getY();
        if (dx * (goal.getX() - confirmed.getX()) + dy * (goal.getY() - confirmed.getY()) > 0) { return false; }
        // A delayed preceding Walk step is still real authority, but it must
        // not undo this newer click before its existing response deadline. Keep
        // the checked forecast clipped to the updated authority gap, with no
        // deadline renewal. New forward evidence can re-anchor it above.
        return true;
    }

    /** Shared clipping only; Walk/NPC evidence gates and deadlines stay with their callers. */
    private boolean retainForecastAuthority(LocalPoint endpoint)
    {
        confirmed = endpoint;
        int gap = (forecastSteps() + 1) * TILE;
        if (distance(position(), endpoint) > gap) { cancel(); return true; }
        while (preview.size() > 1 && distance(preview.get(preview.size() - 1), endpoint) > gap) { preview.remove(preview.size() - 1); }
        while (!legs.isEmpty() && distance(legs.peekLast().end, endpoint) > gap) { legs.removeLast(); }
        confirmedClickIndex = clickedPath.indexOf(endpoint);
        awaitingOrigin = confirmedClickIndex < 0;
        confirmedPreviewIndex = Math.max(0, confirmedClickIndex);
        return true;
    }

    private void replenishReversalApproach()
    {
        // Fresh server progress toward the reverse route can release preview
        // steps clipped by the original distance cap, even before it reaches
        // the route's first tile. Keep the same initial horizon and gap bound.
        int planned = preview.size() - 1;
        int limit = limitForecast(completePair(Math.min(clickedPath.size() - 1, forecastSteps())));
        LocalPoint previous = preview.get(planned);
        int accepted = planned;
        for (int index = planned + 1; index <= limit; ++index)
        {
            LocalPoint next = clickedPath.get(index);
            if (distance(next, confirmed) > (forecastSteps() + 1) * TILE ||
                !collision.test(previous, next) || !collision.test(next, previous)) { break; }
            accepted = index;
            previous = next;
        }
        if (accepted > planned)
        {
            List<LocalPoint> extension = clickedPath.subList(planned + 1, accepted + 1);
            append(preview.get(planned), extension);
            preview.addAll(extension);
        }
        deadline = Math.min(lastNanos + RESPONSE_NANOS, chainDeadline);
    }

    private void extend()
    {
        if (!agreement || replacement || recovering) { return; }
        int limit = limitForecast(completePair(Math.min(clickedPath.size() - 1, confirmedClickIndex + forecastSteps())));
        LocalPoint tail = speculative ? preview.get(preview.size() - 1) : confirmed;
        int index = clickedPath.indexOf(tail);
        if (index < 0 || limit <= index) { return; }
        LocalPoint previous = tail;
        for (int i = index + 1; i <= limit; ++i)
        {
            LocalPoint next = clickedPath.get(i);
            if (!collision.test(previous, next) || !collision.test(next, previous))
            {
                agreement = false;
                return;
            }
            previous = next;
        }
        if (!speculative)
        {
            preview = new ArrayList<>();
            preview.add(confirmed);
            confirmedPreviewIndex = 0;
        }
        List<LocalPoint> extension = clickedPath.subList(index + 1, limit + 1);
        preview.addAll(extension);
        append(tail, extension);
        speculative = true;
        deadline = lastNanos + RESPONSE_NANOS;
    }

    private int completePair(int limit)
    {
        return running && limit % 2 == 1 && limit < clickedPath.size() - 1 &&
            clearKnight(clickedPath.get(limit - 1), clickedPath.get(limit + 1), collision) ? limit + 1 : limit;
    }

    /** NPC footprint is direction evidence, not proof of the final interaction tile. */
    private int limitForecast(int limit)
    {
        if (nativeCounterPairs)
        {
            int boundary = nativeCounterBoundaryIndex();
            if (boundary >= 0) { return Math.min(limit,Math.max(boundary,confirmedClickIndex)); }
        }
        if (counterBoundaryGoal != null)
        {
            // A confirmed corner is still not evidence for the next pair. Later
            // authority beyond it must remain fully accepted on the same route.
            return Math.min(limit, Math.max(clickedPath.indexOf(counterBoundaryGoal), confirmedClickIndex));
        }
        if (npcArrivalGoal != null && npcArrivalGoal.equals(clickedDestination())) { return limit; }
        if (!sameView(npcMin, npcMax)) { return limit; }
        LocalPoint goal = clickedDestination();
        if (npcReserveTiles == 1 && goal != null && npcDistance(origin) <= TILE && distance(origin, goal) <= TILE)
        {
            int dx = Math.max(0, Math.max(npcMin.getX() - goal.getX(), goal.getX() - npcMax.getX()));
            int dy = Math.max(0, Math.max(npcMin.getY() - goal.getY(), goal.getY() - npcMax.getY()));
            // A diagonal-adjacent tile (or the target's own tile) can still need
            // one checked step to a cardinal side. Do not cap that click at its
            // origin and wait for authority to start the same ordinary step.
            if (dx + dy == TILE) { return limit; }
        }
        for (int index = 0; index <= limit; ++index)
        {
            LocalPoint point = clickedPath.get(index);
            if (insideNpcStaging(point))
            {
                // Reserve the final NPC/combat approach for authority. Keep the full logical
                // route for matching, but never replenish speculation through this
                // staging ring, even when the native flag names a closer tile.
                // Clipping precedes append/knight merging: no occupied chord is cut.
                return Math.min(limit, Math.max(index, confirmedClickIndex));
            }
        }
        return limit;
    }

    private boolean insideNpcStaging(LocalPoint point)
    {
        return npcDistance(point) <= npcReserveTiles * TILE;
    }

    private int npcDistance(LocalPoint point)
    {
        if (!sameView(npcMin, npcMax) || !sameView(npcMin, point)) { return Integer.MAX_VALUE; }
        int dx = Math.max(0, Math.max(npcMin.getX() - point.getX(), point.getX() - npcMax.getX()));
        int dy = Math.max(0, Math.max(npcMin.getY() - point.getY(), point.getY() - npcMax.getY()));
        return Math.max(dx, dy);
    }

    private void append(LocalPoint previous, List<LocalPoint> steps)
    {
        int index = clickedPath.indexOf(previous);
        int offset = 0;
        for (LocalPoint step : steps)
        {
            boolean follows = index >= 0 && index + 1 < clickedPath.size() && step.equals(clickedPath.get(index + 1));
            legs.add(new Leg(previous, step, running, (follows ? index : offset) % 2 == 0, null));
            index = follows ? index + 1 : -1;
            previous = step;
            ++offset;
        }
        mergeKnights(legs);
    }

    private void mergeKnights(Deque<Leg> queue)
    {
        List<Leg> source = new ArrayList<>(queue);
        Deque<Leg> merged = new ArrayDeque<>();
        for (int i = 0; i < source.size(); ++i)
        {
            Leg a = source.get(i);
            Leg b = i + 1 < source.size() ? source.get(i + 1) : null;
            if (b != null && a.firstOfPair && !b.firstOfPair && a.corridorEnd == null && b.corridorEnd == null &&
                a.running && b.running && a.end.equals(b.start) && (i > 0 || onSegment(a.start, b.end)) &&
                clearKnight(a.start, b.end, collision))
            {
                merged.add(new Leg(a.start, b.end, true, true, b.end));
                ++i;
            }
            else { merged.add(a); }
        }
        queue.clear();
        queue.addAll(merged);
    }

    void replacement(boolean walk)
    {
        npcRetarget = false;
        easingNpcStart = false;
        if (walk) { npcArrivalGoal = counterNativeGoal = counterBoundaryGoal = null; nativeCounterPairs = false; }
        replacement = true;
        agreement = false;
        if (!walk) { return; }
        if (position().equals(confirmed))
        {
            legs.clear();
            speculative = false;
            return;
        }
        // A reversal builds a new forecast from the visible position. Merely
        // passing through the old authoritative tile does not make its prefix
        // confirmed. On rejection/timeout reconcile directly to authority.
        if (speculative && reversalPreview) { return; }
        Leg active = legs.peekFirst();
        if (speculative && active != null && active.corridorEnd != null && clickedPath.contains(confirmed) &&
            insideCheckedCorridor(active, x, y) && insideCheckedCorridor(active, confirmed.getX(), confirmed.getY()) &&
            (active.end.getX() - active.start.getX()) * (confirmed.getX() - x) +
                (active.end.getY() - active.start.getY()) * (confirmed.getY() - y) > EPS &&
            clearCorridor(active.start, active.corridorEnd, collision))
        {
            // A server run may confirm the logical middle tile of an occupied
            // visual knight. Preserve only that still-forward confirmed debt,
            // using reconnect()'s proven corridor geometry; its old forward end
            // is still a preview and must not survive a replacement red click.
            legs.clear(); legs.add(active.endingAt(confirmed, active.running));
            speculative = false;
            return;
        }
        Deque<Leg> kept = new ArrayDeque<>();
        for (Leg leg : legs)
        {
            boolean future = leg != legs.peekFirst() && inside(leg, confirmed.getX(), confirmed.getY());
            kept.add(future ? new Leg(leg.start, confirmed, leg.running, false, null) : leg);
            if (future || leg.end.equals(confirmed))
            {
                legs.clear();
                legs.addAll(kept);
                speculative = false;
                return;
            }
        }
    }

    void cancel()
    {
        if (!speculative) { return; }
        // An unstarted continuation can be dropped while keeping every
        // confirmed leg, including its corner and current fractional position.
        replacement(true);
        if (!speculative) { awaitingOrigin = false; return; }
        invalid = !reconnect(confirmed, Collections.emptyList());
        awaitingOrigin = speculative = false;
        reversalPreview = false;
        recovering = true;
    }

    private boolean reconnect(LocalPoint endpoint, List<LocalPoint> nativeSteps)
    {
        Leg active = legs.isEmpty() ? lastLeg : legs.peekFirst();
        if (active != null && inside(active, x, y) && inside(active, endpoint.getX(), endpoint.getY()) &&
            clearCorridor(active.start, active.corridorEnd, collision))
        {
            legs.clear();
            legs.add(active.endingAt(endpoint, running));
            return true;
        }
        Deque<Leg> nativeLegs = new ArrayDeque<>();
        LocalPoint previous = confirmed;
        int index = 0;
        for (LocalPoint step : nativeSteps)
        {
            nativeLegs.add(new Leg(previous, step, running, index++ % 2 == 0, null));
            previous = step;
        }
        mergeKnights(nativeLegs);
        Deque<Leg> joined = new ArrayDeque<>();
        boolean found = false;
        for (Leg leg : nativeLegs)
        {
            found |= onSegment(leg.start, leg.end);
            if (found) { joined.add(leg); }
        }
        if (found)
        {
            legs.clear(); legs.addAll(joined); return true;
        }
        List<LocalPoint> anchors = new ArrayList<>();
        LocalPoint point = position();
        if (Math.abs(x - point.getX()) < EPS && Math.abs(y - point.getY()) < EPS && tileCenter(point))
        {
            anchors.add(point);
        }
        if (active != null && (onSegment(active.start, active.end) || inside(active, x, y)))
        {
            anchors.add(active.end);
            if (active.corridorEnd != null)
            {
                corridorAnchors(active, anchors);
            }
            anchors.add(active.start);
        }
        Deque<Leg> best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (LocalPoint anchor : anchors)
        {
            List<LocalPoint> route = checkedRoute(anchor, endpoint, collision, MAX_QUEUE);
            // A corner preview may need to return around the same obstacle.
            // Direct-only recovery would drop to the native player mid-route.
            if (route == null) { route = MovementRoute.find(anchor, endpoint, MAX_QUEUE, collision); }
            if (route == null) { continue; }
            double first = Math.max(Math.abs(anchor.getX() - x), Math.abs(anchor.getY() - y));
            double length = first + route.size() * TILE;
            if (length >= bestDistance) { continue; }
            Deque<Leg> candidate = new ArrayDeque<>();
            if (first > EPS)
            {
                if (active.corridorEnd != null)
                {
                    if (!clearCorridor(active.start, active.corridorEnd, collision)) { continue; }
                    candidate.add(active.endingAt(anchor, running));
                }
                else
                {
                    LocalPoint other = anchor.equals(active.end) ? active.start : active.end;
                    if (!collision.test(other, anchor)) { continue; }
                    candidate.add(new Leg(other, anchor, running, false, null));
                }
            }
            previous = anchor;
            index = 0;
            for (LocalPoint step : route)
            {
                candidate.add(new Leg(previous, step, running, index++ % 2 == 0, null));
                previous = step;
            }
            best = candidate;
            bestDistance = length;
        }
        if (best == null) { return false; }
        legs.clear(); legs.addAll(best); mergeKnights(legs);
        return true;
    }

    void advance(long now)
    {
        advance(now, false);
    }

    void advance(long now, boolean continuingRun)
    {
        advance(now, continuingRun, 0);
    }

    void advance(long now, boolean continuingRun, int catchUpPercent)
    {
        advance(now, continuingRun, catchUpPercent, 0);
    }

    void advance(long now, boolean continuingRun, int catchUpPercent, int slowAheadPercent)
    {
        advance(now, continuingRun, catchUpPercent, slowAheadPercent, null, 0);
    }

    void advance(long now, boolean continuingRun, int catchUpPercent, int slowAheadPercent,
        LocalPoint arrivalGoal, int slowArrivalPercent)
    {
        advance(now, continuingRun, catchUpPercent, slowAheadPercent, arrivalGoal,
            ArrivalPacing.legacy(running, slowArrivalPercent));
    }

    void advance(long now, boolean continuingRun, int catchUpPercent, int slowAheadPercent,
        LocalPoint arrivalGoal, ArrivalPacing arrival)
    {
        advance(now, continuingRun, catchUpPercent, slowAheadPercent, arrivalGoal, arrival, false);
    }

    void advance(long now, boolean continuingRun, int catchUpPercent, int slowAheadPercent,
        LocalPoint arrivalGoal, ArrivalPacing arrival, boolean approachContinuity)
    {
        turnFraction = 0;
        arrivalWalking = false;
        arrivalPaced = false;
        publicationWalking = false;
        double boost = Math.max(0, Math.min(50, catchUpPercent)) / 100.0;
        double slowdown = Math.max(0, Math.min(50, slowAheadPercent)) / 100.0;
        LocalPoint counterGoal = nativeCounterArrivalGoal();
        int counterIndex = counterGoal == null ? -1 : clickedPath.indexOf(counterGoal);
        int tripTiles = counterIndex < 0 ? !confirmedArrivalTrip && !clickedPath.isEmpty() && agreement ?
            clickedPath.size()-1 : confirmedTripTiles : confirmedTripTiles+Math.max(0,counterIndex-confirmedClickIndex);
        double tripLength = tripTiles * (double) TILE;
        arrival = tripLength >= 3 * TILE ? arrival.fit(tripLength) : ArrivalPacing.OFF;
        double millis = Math.min(100, Math.max(0, now - lastNanos) / 1_000_000.0);
        long frameStart = lastNanos;
        lastNanos = now;
        if (speculative && now >= deadline) { cancel(); }
        // Confirmed debt cannot linger indefinitely if a renderer/target drops
        // the hitsplat event. Release its pacing at the existing response bound.
        if (combatTrailing && !speculative && now >= deadline) { combatTrailing = false; }
        // Confirmed-only travel has no onward geometry to spend early. Spread
        // its remaining debt over the publication interval instead of racing
        // to the endpoint and applying an almost-stationary exponential tail.
        // The deadline is renewed only by authority, never by an unchanged flag.
        double confirmedRate = Double.POSITIVE_INFINITY;
        if (continuingRun && !speculative && !recovering && !combatTracking && !legs.isEmpty() &&
            legs.peekLast().end.equals(confirmed) && now < deadline)
        {
            double remainingMillis = (deadline - RESPONSE_NANOS + 620_000_000L - frameStart) / 1_000_000.0;
            double linear = Math.min(millis, Math.max(0, remainingMillis - 20));
            double retained = remainingMillis > 20 ? (remainingMillis - linear) / remainingMillis : 1;
            retained *= Math.exp(-(millis - linear) / 20);
            if (millis > 0) { confirmedRate = confirmedTravel() * (1 - retained) / millis; }
            publicationWalking = confirmedRate <= rate(false) + EPS;
            arrival = ArrivalPacing.OFF;
        }
        while (!legs.isEmpty())
        {
            Leg leg = legs.peekFirst();
            double dx = leg.end.getX() - x;
            double dy = leg.end.getY() - y;
            double distance = Math.max(Math.abs(dx), Math.abs(dy));
            if (distance < EPS)
            {
                x = leg.end.getX(); y = leg.end.getY(); legs.removeFirst(); continue;
            }
            double travelRate = combatTrailing && !recovering ? rate(false) * 0.65 : rate(leg.running);
            if (Double.isFinite(confirmedRate)) { travelRate = Math.min(travelRate * (1 + boost), confirmedRate); }
            if (walkContinuity && !recovering && !combatTracking && npcMin == null && !leg.joinPrepared)
            {
                leg.joinPrepared = true;
                leg.curve = checkedJoin(leg);
                walkContinuity = false;
            }
            double debt = boost > 0 && !Double.isFinite(confirmedRate) ? catchUpDebt() : 0;
            double lead = slowdown > 0 && debt <= 0 ? forecastLead() : Double.NaN;
            double arrivalRemaining = arrival.enabled() ? arrivalRemaining(arrivalGoal) : Double.NaN;
            if (leg.curve != null)
            {
                JoinCurve curve = leg.curve;
                double remaining = curve.length - curve.travel;
                double spend = Math.min(remaining, pacedTravel(millis, travelRate, debt, boost, lead, slowdown,
                    arrivalRemaining, arrival, rate(running)));
                if (spend > EPS)
                {
                    curve.travel += spend;
                    double t = curve.parameter(curve.travel);
                    x = curve.x(t); y = curve.y(t);
                    tangentX = curve.dx(t); tangentY = curve.dy(t);
                    // A local curve returning to its straight leg can briefly
                    // steer past that leg's tangent before returning. Keep body
                    // facing monotonic between the intended endpoint headings;
                    // retain the real derivative only for positional continuity.
                    turnX = curve.facingX(t); turnY = curve.facingY(t);
                    turnFraction = Math.min(1, spend / Math.max(EPS, remaining));
                    lastLeg = leg;
                }
                if (curve.travel + EPS < curve.length) { break; }
                x = curve.ex; y = curve.ey;
                millis = Math.max(0, millis - pacedMillis(spend, travelRate, debt, boost, lead, slowdown,
                    arrivalRemaining, arrival, rate(running)));
                leg.curve = null;
                leg.headingX = leg.end.getX() - x; leg.headingY = leg.end.getY() - y;
                if (Math.abs(x - leg.end.getX()) < EPS && Math.abs(y - leg.end.getY()) < EPS) { legs.removeFirst(); }
                continue;
            }
            double duration = pacedMillis(distance, travelRate, debt, boost, lead, slowdown,
                arrivalRemaining, arrival, rate(running));
            if (millis > 0)
            {
                lastLeg = leg;
                turnFraction = Math.min(1, (millis + EPS) / duration);
                if (!Double.isFinite(leg.headingX)) { leg.headingX = dx; leg.headingY = dy; }
                turnX = leg.headingX;
                turnY = leg.headingY;
                tangentX = dx; tangentY = dy;
            }
            if (easingNpcStart && speculative && agreement && !replacement && !recovering && confirmedTravel()<=EPS)
            {
                // Exponential approach stays within the same checked leg and
                // rate cap, independent of frame cadence. It leaves a small
                // positional reserve until confirmation or the existing timeout.
                // A one-leg seed may be a merged two-tile knight, not one
                // tile. A fixed one-tile denominator doubles its initial rate.
                // Keep the reserve constant over this leg for exact integration.
                double reserve = Math.max(TILE, distance(leg.start, leg.end));
                if (leg.corridorEnd != null) { reserve = Math.max(reserve, distance(leg.start, leg.corridorEnd)); }
                double fraction = exponentialFraction(rate(leg.running), millis, reserve);
                publicationWalking = distance*(1-fraction)*rate(leg.running)/reserve <= rate(false)+EPS;
                turnFraction = fraction;
                x += dx * fraction;
                y += dy * fraction;
                break;
            }
            if (combatTrailing && !recovering && legs.size() == 1)
            {
                // Leave a positional reserve on the checked last leg while
                // awaiting the real hit or another target step. The exponential
                // tail has the same clock/rate cap at every frame cadence.
                double fraction = exponentialFraction(travelRate, millis, TILE);
                turnFraction = fraction;
                x += dx * fraction; y += dy * fraction;
                break;
            }
            if (approachContinuity && continuingRun && speculative && agreement && !replacement && !recovering && !combatTracking &&
                legs.size() == 1 && (npcReserveTiles <= 2 && npcMin != null && npcArrivalGoal == null && counterNativeGoal == null &&
                    !leg.end.equals(clickedDestination()) || sameView(arrivalGoal, clickedDestination()) &&
                    !arrivalGoal.equals(clickedDestination())))
            {
                // A native-owned approach can reach its conservative preview
                // boundary before the next authority update. Keep a small part
                // of THIS checked leg instead of publishing idle there. Integrate
                // linear travel then a bounded 300-ms exponential tail exactly; never
                // extend the route, staging ring, response or chain deadline.
                double reserve = Math.min(TILE, travelRate * 300);
                double linear = Math.min(millis, Math.max(0, (distance - reserve) / travelRate));
                double remaining = (distance - travelRate * linear) *
                    Math.exp(-travelRate * (millis - linear) / reserve);
                double fraction = 1 - remaining / distance;
                publicationWalking = travelRate*Math.min(1,remaining/reserve) <= rate(false)+EPS;
                x += dx * fraction; y += dy * fraction; turnFraction = fraction;
                break;
            }
            if (millis + EPS < duration)
            {
                double fraction = pacedTravel(millis, travelRate, debt, boost, lead, slowdown,
                    arrivalRemaining, arrival, rate(running)) / distance;
                x += dx * fraction;
                y += dy * fraction;
                break;
            }
            x = leg.end.getX(); y = leg.end.getY();
            millis = Math.max(0, millis - duration);
            legs.removeFirst();
        }
        if (legs.isEmpty()) { tangentX = tangentY = 0; }
        if (arrival.enabled())
        {
            double remaining = arrivalRemaining(arrivalGoal);
            arrivalPaced = Double.isFinite(remaining) && remaining <= arrival.length() + EPS;
            // The actual configured speed, rather than the stage boundary,
            // determines the native walk pose. Route gait remains untouched.
            arrivalWalking = arrivalPaced && rate(running) * arrival.speed(remaining) <= rate(false) + EPS;
        }
        if (finished() && arrivalGoal == null) { tripEnded = true; }
        else if (arrivalGoal != null || moving()) { tripEnded = false; }
    }

    /** Only a queued endpoint proven by authority is catch-up debt, not a forecast gap. */
    private double catchUpDebt()
    {
        if (invalid || recovering || awaitingOrigin || reversalPreview || combatTrailing ||
            Math.max(Math.abs(x - confirmed.getX()), Math.abs(y - confirmed.getY())) < EPS) { return 0; }
        return confirmedTravel();
    }

    private double confirmedTravel()
    {
        double debt = 0, px = x, py = y;
        for (Leg leg : legs)
        {
            if (leg.curve != null)
            {
                debt += leg.curve.length - leg.curve.travel;
                px = leg.curve.ex; py = leg.curve.ey;
            }
            debt += Math.max(Math.abs(leg.end.getX() - px), Math.abs(leg.end.getY() - py));
            if (leg.end.equals(confirmed)) { return debt; }
            px = leg.end.getX(); py = leg.end.getY();
        }
        return 0;
    }

    /** Exact integral of baseRate * (1 + boost * min(1, confirmedDebt / TILE)). */
    private static double catchUpMillis(double distance, double baseRate, double debt, double boost)
    {
        if (boost <= 0 || debt <= 0) { return distance / baseRate; }
        double accelerated = Math.min(distance, debt);
        double full = Math.min(accelerated, Math.max(0, debt - TILE));
        double from = Math.min(TILE, debt - full), to = Math.min(TILE, Math.max(0, debt - accelerated));
        return full / (baseRate * (1 + boost)) +
            TILE / (baseRate * boost) * Math.log1p(boost * (from - to) / (TILE + boost * to)) +
            (distance - accelerated) / baseRate;
    }

    private static double catchUpTravel(double millis, double baseRate, double debt, double boost)
    {
        if (boost <= 0 || debt <= 0) { return baseRate * millis; }
        double full = Math.max(0, debt - TILE);
        double normal = Math.min(millis, full / (baseRate * (1 + boost)));
        double spend = normal * baseRate * (1 + boost);
        double remaining = debt - spend;
        double tail = millis - normal;
        double arrival = catchUpMillis(remaining, baseRate, remaining, boost);
        if (tail >= arrival) { return debt + baseRate * (tail - arrival); }
        return spend + (remaining + TILE / boost) * -Math.expm1(-baseRate * boost * tail / TILE);
    }

    /** Logical forecast progress minus remaining checked travel; never infer lead from a stale reversal gap. */
    private double forecastLead()
    {
        if (!speculative || invalid || recovering || awaitingOrigin || reversalPreview || !agreement || replacement ||
            easingNpcStart || combatTracking || preview.isEmpty()) { return Double.NaN; }
        double forecast = 0;
        for (int i = confirmedPreviewIndex + 1; i < preview.size(); ++i)
        {
            forecast += distance(preview.get(i - 1), preview.get(i));
        }
        double remaining = 0, px = x, py = y;
        for (Leg leg : legs)
        {
            if (leg.curve != null)
            {
                remaining += leg.curve.length - leg.curve.travel;
                px = leg.curve.ex; py = leg.curve.ey;
            }
            remaining += Math.max(Math.abs(leg.end.getX() - px), Math.abs(leg.end.getY() - py));
            px = leg.end.getX(); py = leg.end.getY();
        }
        return forecast - remaining;
    }

    /** A queue boundary is not an arrival unless it is the actual destination. */
    private double arrivalRemaining(LocalPoint goal)
    {
        // The native flag clears on arrival. Keep a pending click's actual goal,
        // or settle the remaining confirmed route onto the final true tile.
        if (goal == null) { goal = speculative ? clickedDestination() : confirmed; }
        boolean finalAuthority = !speculative && confirmed.equals(goal);
        if (goal == null || invalid || recovering || replacement && !finalAuthority || awaitingOrigin ||
            combatTracking && (!finalAuthority || combatTrailing) ||
            easingNpcStart || legs.isEmpty() || !legs.peekLast().end.equals(goal)) { return Double.NaN; }
        double remaining = 0, px = x, py = y;
        for (Leg leg : legs)
        {
            if (leg.curve != null)
            {
                remaining += leg.curve.length - leg.curve.travel;
                px = leg.curve.ex; py = leg.curve.ey;
            }
            remaining += Math.max(Math.abs(leg.end.getX() - px), Math.abs(leg.end.getY() - py));
            px = leg.end.getX(); py = leg.end.getY();
        }
        return remaining;
    }

    private static double pacedMillis(double distance, double rate, double debt, double boost, double lead, double slowdown,
        double arrivalRemaining, ArrivalPacing arrival, double arrivalRate)
    {
        if (!Double.isFinite(arrivalRemaining)) { return pacedMillis(distance, rate, debt, boost, lead, slowdown); }
        double normal = Math.min(distance, Math.max(0, arrivalRemaining - arrival.length()));
        return pacedMillis(normal, rate, debt, boost, lead, slowdown) +
            arrival.millis(distance - normal, arrivalRate, arrivalRemaining - normal);
    }

    private static double pacedTravel(double millis, double rate, double debt, double boost, double lead, double slowdown,
        double arrivalRemaining, ArrivalPacing arrival, double arrivalRate)
    {
        if (!Double.isFinite(arrivalRemaining)) { return pacedTravel(millis, rate, debt, boost, lead, slowdown); }
        double normal = Math.max(0, arrivalRemaining - arrival.length());
        double normalTime = pacedMillis(normal, rate, debt, boost, lead, slowdown);
        if (millis < normalTime) { return pacedTravel(millis, rate, debt, boost, lead, slowdown); }
        return normal + arrival.travel(millis - normalTime, arrivalRate, arrivalRemaining - normal);
    }

    private static double pacedMillis(double distance, double rate, double debt, double boost, double lead, double slowdown)
    {
        return Double.isFinite(lead) ? aheadMillis(distance, rate, lead, slowdown) : catchUpMillis(distance, rate, debt, boost);
    }

    private static double pacedTravel(double millis, double rate, double debt, double boost, double lead, double slowdown)
    {
        return Double.isFinite(lead) ? aheadTravel(millis, rate, lead, slowdown) : catchUpTravel(millis, rate, debt, boost);
    }

    /** Exact integral of baseRate * (1 - slowdown * min(1, max(0, lead) / TILE)). */
    private static double aheadMillis(double distance, double baseRate, double lead, double slowdown)
    {
        if (slowdown <= 0) { return distance / baseRate; }
        double normal = Math.min(distance, Math.max(0, -lead));
        double from = Math.min(TILE, Math.max(0, lead + normal));
        double taper = Math.min(distance - normal, TILE - from);
        return normal / baseRate + TILE / (baseRate * slowdown) *
            -Math.log1p(-slowdown * taper / (TILE - slowdown * from)) +
            (distance - normal - taper) / (baseRate * (1 - slowdown));
    }

    private static double aheadTravel(double millis, double baseRate, double lead, double slowdown)
    {
        if (slowdown <= 0) { return baseRate * millis; }
        double normal = Math.min(millis, Math.max(0, -lead) / baseRate);
        double spend = normal * baseRate;
        double from = Math.min(TILE, Math.max(0, lead + spend));
        double taperTime = aheadMillis(TILE - from, baseRate, from, slowdown);
        double tail = millis - normal;
        if (tail >= taperTime) { return spend + TILE - from + baseRate * (1 - slowdown) * (tail - taperTime); }
        return spend + (TILE / slowdown - from) * -Math.expm1(-baseRate * slowdown * tail / TILE);
    }

    /** Blend a Walk join only inside the convex region proven by both checked orderings. */
    private JoinCurve checkedJoin(Leg leg)
    {
        if (leg.corridorEnd == null || !insideCheckedCorridor(leg, x, y) ||
            !insideCheckedCorridor(leg, leg.end.getX(), leg.end.getY()) ||
            !clearCorridor(leg.start, leg.corridorEnd, collision)) { return null; }
        double dx = leg.end.getX() - x, dy = leg.end.getY() - y;
        double incoming = Math.max(Math.abs(tangentX), Math.abs(tangentY));
        double distance = Math.max(Math.abs(dx), Math.abs(dy));
        if (incoming < EPS || distance < 8 || tangentX * dx + tangentY * dy <= 0 ||
            Math.abs(tangentX * dy - tangentY * dx) < EPS) { return null; }
        LocalPoint goal = clickedDestination();
        if (goal != null && tangentX * (goal.getX() - x) + tangentY * (goal.getY() - y) <= 0) { return null; }
        // A local blend rejoins this same leg within roughly 120 ms of ordinary
        // travel. Do not bow the whole itinerary or change distant anchor choices.
        double blend = Math.min(distance, rate(leg.running) * 120);
        double endX = x + dx / distance * blend, endY = y + dy / distance * blend;
        double reach = blend * 0.4;
        // Shrink a boundary-adjacent tangent instead of leaving the proven region.
        for (int attempt = 0; attempt < 5 && reach >= 4; ++attempt, reach *= 0.5)
        {
            double cx = x + tangentX / incoming * reach, cy = y + tangentY / incoming * reach;
            double tx = endX - dx / distance * reach, ty = endY - dy / distance * reach;
            // Keep the useful component of an incoming tangent while dropping
            // its old-click excursion on the other axis. Projection retains a
            // local smooth turn; both projected controls must still pass the
            // original convex corridor and forecast-gap proofs below.
            if (goal != null && withinGoalInterval(leg.end.getX(), leg.end.getY(), goal))
            {
                cx = Math.max(Math.min(x, goal.getX()), Math.min(Math.max(x, goal.getX()), cx));
                cy = Math.max(Math.min(y, goal.getY()), Math.min(Math.max(y, goal.getY()), cy));
                tx = Math.max(Math.min(x, goal.getX()), Math.min(Math.max(x, goal.getX()), tx));
                ty = Math.max(Math.min(y, goal.getY()), Math.min(Math.max(y, goal.getY()), ty));
                if (Math.max(Math.abs(cx - x), Math.abs(cy - y)) < EPS ||
                    Math.max(Math.abs(endX - tx), Math.abs(endY - ty)) < EPS) { continue; }
            }
            if (insideCheckedCorridor(leg, cx, cy) && insideCheckedCorridor(leg, tx, ty) &&
                withinForecastGap(cx, cy) && withinForecastGap(tx, ty) && withinForecastGap(endX, endY))
            {
                return new JoinCurve(x, y, cx, cy, tx, ty, endX, endY);
            }
        }
        return null;
    }

    private boolean withinForecastGap(double px, double py)
    {
        return Math.max(Math.abs(px - confirmed.getX()), Math.abs(py - confirmed.getY())) <= (forecastSteps() + 1) * TILE;
    }

    /** Cubic geometry only; MovementPath's one clock spends its exact max-axis arc length. */
    private static final class JoinCurve
    {
        final double sx, sy, cx, cy, tx, ty, ex, ey, length;
        final double[] breaks;
        double travel;

        JoinCurve(double sx, double sy, double cx, double cy, double tx, double ty, double ex, double ey)
        {
            this.sx = sx; this.sy = sy; this.cx = cx; this.cy = cy;
            this.tx = tx; this.ty = ty; this.ex = ex; this.ey = ey;
            double ax = cx - sx, ay = cy - sy, bx = 2 * (tx - 2 * cx + sx), by = 2 * (ty - 2 * cy + sy);
            double qx = ex - 3 * tx + 3 * cx - sx, qy = ey - 3 * ty + 3 * cy - sy;
            List<Double> points = new ArrayList<>(); points.add(0.0); points.add(1.0);
            roots(points, ax, bx, qx); roots(points, ay, by, qy);
            roots(points, ax - ay, bx - by, qx - qy); roots(points, ax + ay, bx + by, qx + qy);
            breaks = points.stream().mapToDouble(Double::doubleValue).filter(t -> t >= 0 && t <= 1).sorted().distinct().toArray();
            length = arc(1);
        }

        private static void roots(List<Double> points, double a, double b, double c)
        {
            if (Math.abs(c) < EPS)
            {
                if (Math.abs(b) >= EPS) { points.add(-a / b); }
                return;
            }
            double discriminant = b * b - 4 * c * a;
            if (discriminant < 0) { return; }
            double root = Math.sqrt(discriminant);
            points.add((-b - root) / (2 * c)); points.add((-b + root) / (2 * c));
        }
        double x(double t) { double u = 1 - t; return u * u * u * sx + 3 * u * u * t * cx + 3 * u * t * t * tx + t * t * t * ex; }
        double y(double t) { double u = 1 - t; return u * u * u * sy + 3 * u * u * t * cy + 3 * u * t * t * ty + t * t * t * ey; }
        double dx(double t) { double u = 1 - t; return 3 * (u * u * (cx - sx) + 2 * u * t * (tx - cx) + t * t * (ex - tx)); }
        double dy(double t) { double u = 1 - t; return 3 * (u * u * (cy - sy) + 2 * u * t * (ty - cy) + t * t * (ey - ty)); }

        double facingX(double t) { return facing(cx - sx, ex - tx, t); }
        double facingY(double t) { return facing(cy - sy, ey - ty, t); }
        private double facing(double from, double to, double t)
        {
            double blend = t * t * (3 - 2 * t);
            double incoming = Math.max(Math.abs(cx - sx), Math.abs(cy - sy));
            double outgoing = Math.max(Math.abs(ex - tx), Math.abs(ey - ty));
            return from / incoming * (1 - blend) + to / outgoing * blend;
        }

        double arc(double t)
        {
            double sum = 0;
            for (int i = 1; i < breaks.length; ++i)
            {
                double a = breaks[i - 1], b = Math.min(t, breaks[i]);
                if (b <= a) { continue; }
                double mid = (a + b) / 2;
                // Breakpoints fix the dominant axis and its sign. Simpson's rule
                // integrates that quadratic derivative exactly, not per-frame sampling.
                boolean horizontal = Math.abs(dx(mid)) >= Math.abs(dy(mid));
                sum += (b - a) / 6 * (horizontal ? Math.abs(dx(a)) + 4 * Math.abs(dx(mid)) + Math.abs(dx(b)) :
                    Math.abs(dy(a)) + 4 * Math.abs(dy(mid)) + Math.abs(dy(b)));
            }
            return sum;
        }

        double parameter(double distance)
        {
            if (distance >= length) { return 1; }
            double lo = 0, hi = 1;
            for (int i = 0; i < 28; ++i)
            {
                double mid = (lo + hi) / 2;
                if (arc(mid) < distance) { lo = mid; } else { hi = mid; }
            }
            return lo;
        }

        JoinCurve shifted(int dx, int dy)
        {
            JoinCurve copy = new JoinCurve(sx + dx, sy + dy, cx + dx, cy + dy, tx + dx, ty + dy, ex + dx, ey + dy);
            copy.travel = travel;
            return copy;
        }
    }

    private static double exponentialFraction(double rate, double millis, double reserve)
    {
        return -Math.expm1(-rate * millis / reserve);
    }

    boolean clear()
    {
        if (invalid) { return false; }
        if (speculative)
        {
            for (int i = confirmedPreviewIndex + 1; i < preview.size(); ++i)
            {
                if (!collision.test(preview.get(i - 1), preview.get(i)) ||
                    !collision.test(preview.get(i), preview.get(i - 1))) { return false; }
            }
        }
        for (Leg leg : legs)
        {
            if (leg.corridorEnd != null ? !clearCorridor(leg.start, leg.corridorEnd, collision)
                : !collision.test(leg.start, leg.end)) { return false; }
        }
        return true;
    }

    /** Affine scene rebase keeps all route, chord and sub-tile presentation state. */
    void rebase(int dx, int dy, long now)
    {
        translate(dx, dy, now);
        // Unrelated scene changes cannot retain an old-scene prediction.
        replacement = true;
        agreement = false;
        if (speculative) { cancel(); }
    }

    /** Resume an ordinary area rebase, not a teleport, instance change or reconnect. */
    boolean resumeScene(int dx, int dy, LocalPoint authoritative, LocalPoint destination, boolean run, long now)
    {
        translate(dx, dy, now);
        if (!sameView(position(), authoritative)) { return false; }
        // A published destination must still identify the same click. Arrival
        // can clear the native flag in the same update that confirms its goal.
        LocalPoint goal = clickedDestination();
        boolean sameClick = goal != null && (goal.equals(destination) || destination == null && goal.equals(authoritative));
        if (clear() && speculative && agreement && !replacement && sameClick && now < deadline &&
            now < chainDeadline && distance(position(), authoritative) <= (forecastSteps() + 1) * TILE &&
            accept(authoritative, run))
        {
            // Neither translation nor an unchanged authoritative tile renews
            // prediction time/distance. Only accept()'s real progress can do so.
            return clear();
        }
        return recover(authoritative, run) && clear();
    }

    private void translate(int dx, int dy, long now)
    {
        origin = shifted(origin, dx, dy);
        if (npcArrivalGoal != null) { npcArrivalGoal = shifted(npcArrivalGoal, dx, dy); }
        if (counterNativeGoal != null) { counterNativeGoal = shifted(counterNativeGoal, dx, dy); }
        if (counterBoundaryGoal != null) { counterBoundaryGoal = shifted(counterBoundaryGoal, dx, dy); }
        confirmed = shifted(confirmed, dx, dy);
        if (npcMin != null) { npcMin = shifted(npcMin, dx, dy); }
        if (npcMax != null) { npcMax = shifted(npcMax, dx, dy); }
        for (int i = 0; i < clickedPath.size(); ++i) { clickedPath.set(i, shifted(clickedPath.get(i), dx, dy)); }
        for (int i = 0; i < preview.size(); ++i) { preview.set(i, shifted(preview.get(i), dx, dy)); }
        Deque<Leg> shifted = new ArrayDeque<>();
        for (Leg leg : legs) { shifted.add(shifted(leg, dx, dy)); }
        legs.clear(); legs.addAll(shifted);
        if (lastLeg != null) { lastLeg = shifted(lastLeg, dx, dy); }
        x += dx; y += dy;
        lastNanos = now;
    }

    void presented(long now) { lastNanos = now; }

    boolean recover(LocalPoint authoritative, boolean run)
    {
        if (!sameView(position(), authoritative)) { return false; }
        List<LocalPoint> steps = checkedRoute(confirmed, authoritative, collision, MAX_QUEUE);
        if (steps == null) { return false; }
        running = run && (running || steps.size() > 1);
        boolean confirmedTail = legs.isEmpty() ? position().equals(confirmed) : legs.peekLast().end.equals(confirmed);
        if (!speculative && !recovering && confirmedTail && clear())
        {
            // A rebase does not invalidate an unfinished confirmed bend. Keep
            // its ordering rather than choosing a shorter backward connector.
            append(confirmed, steps);
            if (legs.size() > MAX_QUEUE) { return false; }
        }
        else if (!reconnect(authoritative, steps)) { return false; }
        confirmed = authoritative;
        speculative = awaitingOrigin = recovering = false;
        agreement = false;
        replacement = true;
        return true;
    }

    LocalPoint position() { return new LocalPoint((int) Math.round(x), (int) Math.round(y), origin.getWorldView()); }
    LocalPoint confirmed() { return confirmed; }
    LocalPoint clickedDestination() { return clickedPath.isEmpty() ? null : clickedPath.get(clickedPath.size() - 1); }
    boolean moving() { return !legs.isEmpty(); }
    boolean running() { return moving() && !(combatTrailing && !recovering) && legs.peekFirst().running; }
    boolean presentationRunning()
    {
        return !publicationWalking && (arrivalPaced ? moving() && running && !arrivalWalking : running());
    }
    boolean finished() { return !speculative && legs.isEmpty() && position().equals(confirmed); }
    double turnFraction() { return turnFraction; }
    double turnX() { return turnX; }
    double turnY() { return turnY; }
    int queuedLegs() { return legs.size(); }
    String phase() { return speculative ? "preview" : recovering ? "recovery" : "confirmed"; }

    /** Bounded value copy for diagnostics only; never advance/replan while taking a snapshot. */
    TraceSnapshot traceSnapshot() { return new TraceSnapshot(this); }

    static final class TraceSnapshot
    {
        private final List<LocalPoint> route, preview;
        private final int[] queue;
        private final double[] joins;
        private final LocalPoint origin, confirmed, npcMin, npcMax, npcArrivalGoal, counterNativeGoal, counterBoundaryGoal, nativeCounterGoal;
        private final int flags, confirmedClickIndex, confirmedPreviewIndex, reserve, confirmedTripTiles;
        private final long deadline, chainDeadline;
        private final double speed;

        TraceSnapshot(MovementPath path)
        {
            route = List.copyOf(path.clickedPath); preview = List.copyOf(path.preview);
            queue = new int[path.legs.size() * 8];
            joins = new double[path.legs.size() * 8];
            Arrays.fill(joins, Double.NaN);
            int index = 0;
            int joinIndex = 0;
            for (Leg leg : path.legs)
            {
                queue[index++] = leg.start.getX(); queue[index++] = leg.start.getY();
                queue[index++] = leg.end.getX(); queue[index++] = leg.end.getY();
                queue[index++] = leg.corridorEnd == null ? -1 : leg.corridorEnd.getX();
                queue[index++] = leg.corridorEnd == null ? -1 : leg.corridorEnd.getY();
                queue[index++] = leg.running ? 1 : 0; queue[index++] = leg.firstOfPair ? 1 : 0;
                if (leg.curve != null)
                {
                    joins[joinIndex] = leg.curve.sx; joins[joinIndex + 1] = leg.curve.sy;
                    joins[joinIndex + 2] = leg.curve.cx; joins[joinIndex + 3] = leg.curve.cy;
                    joins[joinIndex + 4] = leg.curve.tx; joins[joinIndex + 5] = leg.curve.ty;
                    joins[joinIndex + 6] = leg.curve.ex; joins[joinIndex + 7] = leg.curve.ey;
                }
                joinIndex += 8;
            }
            origin = path.origin; confirmed = path.confirmed;
            npcMin = path.npcMin; npcMax = path.npcMax; npcArrivalGoal = path.npcArrivalGoal();
            counterNativeGoal = path.counterNativeGoal;
            counterBoundaryGoal = path.counterBoundaryGoal;
            nativeCounterGoal = path.nativeCounterArrivalGoal();
            flags = (path.speculative ? 1 : 0) | (path.recovering ? 2 : 0) | (path.replacement ? 4 : 0) |
                (path.agreement ? 8 : 0) | (path.awaitingOrigin ? 16 : 0) | (path.reversalPreview ? 32 : 0) |
                (path.invalid ? 64 : 0) | (path.easingNpcStart ? 128 : 0) | (path.running ? 256 : 0) | (path.npcRetarget ? 512 : 0) |
                (path.combatTracking ? 1024 : 0) | (path.combatCredit ? 2048 : 0) | (path.combatTrailing ? 4096 : 0) |
                (path.walkContinuity ? 8192 : 0) | (path.confirmedArrivalTrip ? 16384 : 0) | (path.nativeCounterPairs ? 32768 : 0);
            confirmedClickIndex = path.confirmedClickIndex; confirmedPreviewIndex = path.confirmedPreviewIndex;
            confirmedTripTiles = path.confirmedTripTiles;
            reserve = path.npcReserveTiles; deadline = path.deadline; chainDeadline = path.chainDeadline; speed = path.multiplier;
        }

        boolean same(TraceSnapshot other)
        {
            return other != null && route.equals(other.route) && preview.equals(other.preview) && Arrays.equals(queue, other.queue) &&
                Arrays.equals(joins, other.joins) &&
                origin.equals(other.origin) && confirmed.equals(other.confirmed) && java.util.Objects.equals(npcMin, other.npcMin) &&
                java.util.Objects.equals(npcMax, other.npcMax) && java.util.Objects.equals(npcArrivalGoal, other.npcArrivalGoal) &&
                java.util.Objects.equals(counterNativeGoal, other.counterNativeGoal) &&
                java.util.Objects.equals(counterBoundaryGoal, other.counterBoundaryGoal) &&
                java.util.Objects.equals(nativeCounterGoal, other.nativeCounterGoal) &&
                flags == other.flags && confirmedClickIndex == other.confirmedClickIndex && confirmedPreviewIndex == other.confirmedPreviewIndex &&
                reserve == other.reserve && confirmedTripTiles == other.confirmedTripTiles &&
                deadline == other.deadline && chainDeadline == other.chainDeadline && speed == other.speed;
        }

        String fields(long started)
        {
            return " view=" + origin.getWorldView() + " origin=" + point(origin) + " confirmed=" + point(confirmed) +
                " stateFlags=" + flags + " confirmedClickIndex=" + confirmedClickIndex + " confirmedPreviewIndex=" + confirmedPreviewIndex +
                " confirmedTripTiles=" + confirmedTripTiles +
                " npcReserveTiles=" + reserve + " npcMin=" + point(npcMin) + " npcMax=" + point(npcMax) +
                " npcArrivalGoal=" + point(npcArrivalGoal) + " counterNativeGoal=" + point(counterNativeGoal) +
                " counterBoundaryGoal=" + point(counterBoundaryGoal) +
                " nativeCounterGoal=" + point(nativeCounterGoal) +
                " deadlineUs=" + (deadline - started) / 1000 +
                " chainDeadlineUs=" + (chainDeadline - started) / 1000 + " effectiveSpeed=" + speed +
                " logical=" + points(route) + " preview=" + points(preview) + " legs=" + Arrays.toString(queue).replace(" ", "") +
                " joins=" + Arrays.toString(joins).replace(" ", "");
        }

        private static String point(LocalPoint p) { return p == null ? "-" : p.getX() + "," + p.getY(); }
        private static String points(List<LocalPoint> points)
        {
            StringBuilder value = new StringBuilder();
            for (LocalPoint p : points) { if (value.length() > 0) { value.append(';'); } value.append(point(p)); }
            return value.length() == 0 ? "-" : value.toString();
        }
    }

    private static LocalPoint shifted(LocalPoint p, int dx, int dy)
    {
        return new LocalPoint(p.getX() + dx, p.getY() + dy, p.getWorldView());
    }

    private static Leg shifted(Leg leg, int dx, int dy)
    {
        Leg result = new Leg(shifted(leg.start, dx, dy), shifted(leg.end, dx, dy), leg.running,
            leg.firstOfPair, leg.corridorEnd == null ? null : shifted(leg.corridorEnd, dx, dy));
        result.headingX = leg.headingX;
        result.headingY = leg.headingY;
        result.joinPrepared = leg.joinPrepared;
        result.curve = leg.curve == null ? null : leg.curve.shifted(dx, dy);
        return result;
    }

    static boolean clearKnight(LocalPoint from, LocalPoint to, BiPredicate<LocalPoint, LocalPoint> check)
    {
        if (!sameView(from, to)) { return false; }
        int dx = Math.abs(to.getX() - from.getX());
        int dy = Math.abs(to.getY() - from.getY());
        if (!((dx == TILE * 2 && dy == TILE) || (dx == TILE && dy == TILE * 2))) { return false; }
        LocalPoint straight = middle(from, to, false);
        LocalPoint diagonal = middle(from, to, true);
        return check.test(from, straight) && check.test(straight, to) && check.test(from, diagonal) && check.test(diagonal, to) &&
            check.test(to, straight) && check.test(straight, from) && check.test(to, diagonal) && check.test(diagonal, from);
    }

    private static boolean clearCorridor(LocalPoint from, LocalPoint to, BiPredicate<LocalPoint, LocalPoint> check)
    {
        return clearKnight(from, to, check) || clearTurnSquare(from, to, check);
    }

    private static boolean clearTurnSquare(LocalPoint from, LocalPoint to, BiPredicate<LocalPoint, LocalPoint> check)
    {
        if (!sameView(from, to) || Math.abs(to.getX() - from.getX()) != TILE ||
            Math.abs(to.getY() - from.getY()) != TILE) { return false; }
        LocalPoint a = new LocalPoint(from.getX(), to.getY(), from.getWorldView());
        LocalPoint b = new LocalPoint(to.getX(), from.getY(), from.getWorldView());
        // Check both diagonals and all four perimeter edges in both directions.
        // A clear occupied diagonal alone does not prove its side connectors.
        return check.test(from, to) && check.test(to, from) && check.test(a, b) && check.test(b, a) &&
            check.test(from, a) && check.test(a, from) && check.test(from, b) && check.test(b, from) &&
            check.test(to, a) && check.test(a, to) && check.test(to, b) && check.test(b, to);
    }

    private static void corridorAnchors(Leg leg, List<LocalPoint> anchors)
    {
        anchors.add(leg.corridorEnd);
        if (Math.abs(leg.corridorEnd.getX() - leg.start.getX()) == TILE &&
            Math.abs(leg.corridorEnd.getY() - leg.start.getY()) == TILE)
        {
            anchors.add(new LocalPoint(leg.start.getX(), leg.corridorEnd.getY(), leg.start.getWorldView()));
            anchors.add(new LocalPoint(leg.corridorEnd.getX(), leg.start.getY(), leg.start.getWorldView()));
        }
        else
        {
            anchors.add(middle(leg.start, leg.corridorEnd, false));
            anchors.add(middle(leg.start, leg.corridorEnd, true));
        }
    }

    private static LocalPoint middle(LocalPoint from, LocalPoint to, boolean diagonal)
    {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        return new LocalPoint(from.getX() + (diagonal || Math.abs(dx) > Math.abs(dy) ? Integer.signum(dx) * TILE : 0),
            from.getY() + (diagonal || Math.abs(dy) > Math.abs(dx) ? Integer.signum(dy) * TILE : 0), from.getWorldView());
    }

    private static boolean inside(Leg leg, double x, double y)
    {
        return leg.corridorEnd != null && x >= Math.min(leg.start.getX(), leg.corridorEnd.getX()) - EPS &&
            x <= Math.max(leg.start.getX(), leg.corridorEnd.getX()) + EPS &&
            y >= Math.min(leg.start.getY(), leg.corridorEnd.getY()) - EPS && y <= Math.max(leg.start.getY(), leg.corridorEnd.getY()) + EPS;
    }

    private static boolean insideCheckedCorridor(Leg leg, double x, double y)
    {
        if (!inside(leg, x, y)) { return false; }
        int dx = leg.corridorEnd.getX() - leg.start.getX(), dy = leg.corridorEnd.getY() - leg.start.getY();
        if (Math.abs(dx) == TILE && Math.abs(dy) == TILE) { return true; }
        double px = (x - leg.start.getX()) * Integer.signum(dx);
        double py = (y - leg.start.getY()) * Integer.signum(dy);
        double lead = Math.abs(dx) > Math.abs(dy) ? px - py : py - px;
        // A knight's two checked orderings prove a parallelogram, not the two
        // unused corners of its bounding rectangle. New joins must be inside it.
        return lead >= -EPS && lead <= TILE + EPS;
    }

    private boolean onSegment(LocalPoint from, LocalPoint to)
    {
        double dx = to.getX() - from.getX(), dy = to.getY() - from.getY();
        return Math.abs(dx * (y - from.getY()) - dy * (x - from.getX())) < 0.001 &&
            x >= Math.min(from.getX(), to.getX()) - EPS && x <= Math.max(from.getX(), to.getX()) + EPS &&
            y >= Math.min(from.getY(), to.getY()) - EPS && y <= Math.max(from.getY(), to.getY()) + EPS;
    }

    static List<LocalPoint> checkedRoute(LocalPoint from, LocalPoint to, BiPredicate<LocalPoint, LocalPoint> check, int max)
    {
        List<LocalPoint> steps = direct(from, to, check, max, false);
        if (steps == null) { steps = direct(from, to, check, max, true); }
        if (steps != null || max < 2 || !sameView(from, to) || !tileCenter(from) || !tileCenter(to) ||
            Math.abs(to.getX() - from.getX()) != TILE || Math.abs(to.getY() - from.getY()) != TILE)
        {
            return steps;
        }
        // A diagonally adjacent confirmed endpoint may have been reached by
        // two cardinal steps around a corner. Check only those two bends;
        // longer obstacle searches belong to click prediction/correction.
        LocalPoint bend = new LocalPoint(to.getX(), from.getY(), from.getWorldView());
        if (check.test(from, bend) && check.test(bend, to)) { return List.of(bend, to); }
        bend = new LocalPoint(from.getX(), to.getY(), from.getWorldView());
        return check.test(from, bend) && check.test(bend, to) ? List.of(bend, to) : null;
    }

    private static List<LocalPoint> direct(LocalPoint from, LocalPoint to, BiPredicate<LocalPoint, LocalPoint> check,
        int maximum, boolean diagonalFirst)
    {
        if (!sameView(from, to)) { return null; }
        int dx = to.getX() - from.getX(), dy = to.getY() - from.getY();
        int xs = Math.abs(dx) / TILE, ys = Math.abs(dy) / TILE, length = Math.max(xs, ys);
        if (dx % TILE != 0 || dy % TILE != 0 || length > maximum) { return null; }
        List<LocalPoint> result = new ArrayList<>(length);
        LocalPoint previous = from;
        for (int i = 1; i <= length; ++i)
        {
            int sx = diagonalFirst ? Math.min(i, xs) : Math.max(0, i - (length - xs));
            int sy = diagonalFirst ? Math.min(i, ys) : Math.max(0, i - (length - ys));
            LocalPoint next = new LocalPoint(from.getX() + Integer.signum(dx) * sx * TILE,
                from.getY() + Integer.signum(dy) * sy * TILE, from.getWorldView());
            if (!check.test(previous, next)) { return null; }
            result.add(next); previous = next;
        }
        return result;
    }

    static boolean sameView(LocalPoint a, LocalPoint b) { return a != null && b != null && a.getWorldView() == b.getWorldView(); }
    private static boolean sameLine(LocalPoint a, LocalPoint b, LocalPoint point)
    {
        return (long) (b.getX() - a.getX()) * (point.getY() - a.getY()) ==
            (long) (b.getY() - a.getY()) * (point.getX() - a.getX());
    }
    static boolean tileCenter(LocalPoint p) { return p != null && Math.floorMod(p.getX(), TILE) == TILE / 2 && Math.floorMod(p.getY(), TILE) == TILE / 2; }
    static int distance(LocalPoint a, LocalPoint b) { return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY())); }
}
