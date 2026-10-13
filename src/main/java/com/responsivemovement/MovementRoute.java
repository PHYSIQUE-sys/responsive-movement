package com.responsivemovement;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;

/** Bounded collision search for a click or a prediction correction, never a scene scan. */
final class MovementRoute
{
    static final int MAX_VISITED = 2048;
    private static final int[][] DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1},
        {-1, -1}, {1, -1}, {-1, 1}, {1, 1}};

    private MovementRoute() { }

    static List<LocalPoint> find(LocalPoint from, LocalPoint to, int maxSteps,
        BiPredicate<LocalPoint, LocalPoint> collision)
    {
        return find(from, to, maxSteps, collision, false);
    }

    static List<LocalPoint> find(LocalPoint from, LocalPoint to, int maxSteps,
        BiPredicate<LocalPoint, LocalPoint> collision, boolean approach)
    {
        int tile = Perspective.LOCAL_TILE_SIZE;
        if (!MovementPath.sameView(from, to) || !MovementPath.tileCenter(from) || !MovementPath.tileCenter(to) ||
            maxSteps < 0 || MovementPath.distance(from, to) > maxSteps * tile) { return null; }
        int radius = MovementPath.distance(from, to) / tile;
        // A distant goal can lie beyond the open-area radius of the vertex
        // budget. Direct routes were tried first by MovementPath; guide only
        // this long obstacle search instead of spending its budget sideways.
        LocalPoint guide = (long) (2 * radius + 1) * (2 * radius + 1) > MAX_VISITED ? to : null;
        return search(from, maxSteps, collision, point -> reached(point, to, approach), guide, approach);
    }

    /** First reachable cardinal perimeter tile, using the same bounded reversible search. */
    static List<LocalPoint> boundary(LocalPoint from, LocalPoint min, LocalPoint max, int maxSteps,
        BiPredicate<LocalPoint, LocalPoint> collision)
    {
        if (!MovementPath.sameView(from, min) || !MovementPath.sameView(min, max) ||
            !MovementPath.tileCenter(from) || !MovementPath.tileCenter(min) || !MovementPath.tileCenter(max) ||
            min.getX() > max.getX() || min.getY() > max.getY() || maxSteps < 0) { return null; }
        return search(from, maxSteps, collision, point ->
        {
            int dx = Math.max(0, Math.max(min.getX() - point.getX(), point.getX() - max.getX()));
            int dy = Math.max(0, Math.max(min.getY() - point.getY(), point.getY() - max.getY()));
            return dx + dy == Perspective.LOCAL_TILE_SIZE;
        });
    }

    private static List<LocalPoint> search(LocalPoint from, int maxSteps,
        BiPredicate<LocalPoint, LocalPoint> collision, Predicate<LocalPoint> reached)
    {
        return search(from, maxSteps, collision, reached, null, false);
    }

    private static final class Step
    {
        final LocalPoint point;
        final int depth, remaining, order;

        Step(LocalPoint point, int depth, LocalPoint guide, boolean approach, int order)
        {
            this.point = point; this.depth = depth; this.order = order;
            remaining = guide == null ? 0 : Math.max(0,
                MovementPath.distance(point, guide) / Perspective.LOCAL_TILE_SIZE - (approach ? 1 : 0));
        }
    }

    private static List<LocalPoint> search(LocalPoint from, int maxSteps,
        BiPredicate<LocalPoint, LocalPoint> collision, Predicate<LocalPoint> reached,
        LocalPoint guide, boolean approach)
    {
        int tile = Perspective.LOCAL_TILE_SIZE;
        if (reached.test(from)) { return Collections.emptyList(); }
        Map<LocalPoint, LocalPoint> parent = new HashMap<>();
        Map<LocalPoint, Integer> depths = new HashMap<>();
        Queue<Step> queue = guide == null ? new ArrayDeque<>() : new PriorityQueue<>(
            Comparator.comparingInt((Step step) -> step.depth + step.remaining)
                .thenComparingInt(step -> step.remaining).thenComparingInt(step -> step.order));
        parent.put(from, from);
        depths.put(from, 0);
        int order = 0, expanded = 0;
        queue.add(new Step(from, 0, guide, approach, order++));
        while (!queue.isEmpty())
        {
            Step current = queue.remove();
            if (current.depth != depths.get(current.point) || current.depth >= maxSteps) { continue; }
            if (++expanded > MAX_VISITED) { return null; }
            for (int[] direction : DIRECTIONS)
            {
                LocalPoint next = new LocalPoint(current.point.getX() + direction[0] * tile,
                    current.point.getY() + direction[1] * tile, from.getWorldView());
                int depth = current.depth + 1;
                Integer previousDepth = depths.get(next);
                if (previousDepth != null && previousDepth <= depth ||
                    !collision.test(current.point, next) || !collision.test(next, current.point)) { continue; }
                if (previousDepth == null && parent.size() >= MAX_VISITED) { return null; }
                parent.put(next, current.point);
                depths.put(next, depth);
                if (reached.test(next))
                {
                    List<LocalPoint> route = new ArrayList<>();
                    for (LocalPoint point = next; !point.equals(from); point = parent.get(point)) { route.add(point); }
                    Collections.reverse(route);
                    return route;
                }
                queue.add(new Step(next, depth, guide, approach, order++));
            }
        }
        return null;
    }

    private static boolean reached(LocalPoint point, LocalPoint target, boolean approach)
    {
        return point.equals(target) || approach &&
            Math.abs(point.getX() - target.getX()) + Math.abs(point.getY() - target.getY()) == Perspective.LOCAL_TILE_SIZE;
    }
}
