package com.responsivemovement;

import java.util.List;
import java.util.function.BiPredicate;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.GameObject;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.WallObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.util.Text;

/** Immutable clicked-object footprint, read once at input rather than scanning the scene per frame. */
final class ObjectApproach
{
    final LocalPoint min;
    final LocalPoint max;

    private ObjectApproach(LocalPoint min, LocalPoint max)
    {
        this.min = min;
        this.max = max;
    }

    static ObjectApproach bounds(LocalPoint min, LocalPoint max)
    {
        if (!MovementPath.sameView(min, max) || !MovementPath.tileCenter(min) || !MovementPath.tileCenter(max) ||
            min.getX() > max.getX() || min.getY() > max.getY() || min.equals(max)) { return null; }
        return new ObjectApproach(min, max);
    }

    static ObjectApproach capture(WorldView view, MenuOptionClicked event)
    {
        MenuAction action = event.getMenuAction();
        // Item/widget use targets the same scene object as its menu options.
        // In particular, seeds and compost arrive as WIDGET_TARGET_ON_GAME_OBJECT.
        // Keep the footprint for every object-targeting action; otherwise a use
        // click beside a large object incorrectly routes toward its anchor tile.
        if (action != MenuAction.GAME_OBJECT_FIRST_OPTION && action != MenuAction.GAME_OBJECT_SECOND_OPTION &&
            action != MenuAction.GAME_OBJECT_THIRD_OPTION && action != MenuAction.GAME_OBJECT_FOURTH_OPTION &&
            action != MenuAction.GAME_OBJECT_FIFTH_OPTION && action != MenuAction.ITEM_USE_ON_GAME_OBJECT &&
            action != MenuAction.WIDGET_TARGET_ON_GAME_OBJECT) { return null; }
        if (view == null || view.getScene() == null || event.getMenuEntry().getWorldViewId() != view.getId()) { return null; }
        int x = event.getParam0(), y = event.getParam1(), plane = view.getPlane();
        Tile[][][] tiles = view.getScene().getTiles();
        if (tiles == null || plane < 0 || plane >= tiles.length || tiles[plane] == null ||
            x < 0 || x >= tiles[plane].length || tiles[plane][x] == null ||
            y < 0 || y >= tiles[plane][x].length) { return null; }
        Tile tile = tiles[plane][x][y];
        if (tile == null) { return null; }
        GameObject[] objects = tile.getGameObjects();
        for (GameObject object : objects == null ? new GameObject[0] : objects)
        {
            if (object == null || object.getId() != event.getId() || object.getPlane() != plane ||
                object.getWorldView() != view) { continue; }
            // Native rectangular game-object shapes, not walls/diagonal wall objects.
            int shape = object.getConfig() & 31;
            if (shape != 10 && shape != 11) { continue; }
            Point min = object.getSceneMinLocation(), max = object.getSceneMaxLocation();
            if (min == null || max == null || min.getX() < 0 || min.getY() < 0 ||
                max.getX() >= view.getSizeX() || max.getY() >= view.getSizeY() ||
                x < min.getX() || x > max.getX() || y < min.getY() || y > max.getY()) { continue; }
            // Walkable platforms and floor objects must still use their native
            // destination. A footprint is useful here only for a solid object.
            CollisionData[] maps = view.getCollisionMaps();
            if (maps == null || plane >= maps.length || maps[plane] == null) { return null; }
            int[][] flags = maps[plane].getFlags();
            if (flags == null || min.getX() >= flags.length || flags[min.getX()] == null ||
                min.getY() >= flags[min.getX()].length ||
                (flags[min.getX()][min.getY()] & CollisionDataFlag.BLOCK_MOVEMENT_OBJECT) == 0) { return null; }
            return bounds(new LocalPoint(min.getX() * 128 + 64, min.getY() * 128 + 64, view.getId()),
                new LocalPoint(max.getX() * 128 + 64, max.getY() * 128 + 64, view.getId()));
        }
        if (action == MenuAction.ITEM_USE_ON_GAME_OBJECT || action == MenuAction.WIDGET_TARGET_ON_GAME_OBJECT) { return null; }
        String option = event.getMenuOption();
        option = option == null ? "" : Text.removeTags(option).trim();
        if ("Open".equalsIgnoreCase(option) || "Close".equalsIgnoreCase(option))
        {
            WallObject wall = tile.getWallObject();
            if (wall != null && wall.getId() == event.getId() && wall.getPlane() == plane && wall.getWorldView() == view &&
                (wall.getConfig() & 31) <= 3)
            {
                // A door/gate flag can name its hinge tile on the far side while
                // the actual interaction stops beside it. Use the same perimeter
                // staging as solid objects; only native authority crosses the wall.
                LocalPoint at = new LocalPoint(x * 128 + 64, y * 128 + 64, view.getId());
                return new ObjectApproach(at, at);
            }
        }
        return null;
    }

    boolean contains(LocalPoint point)
    {
        return MovementPath.sameView(min, point) && point.getX() >= min.getX() && point.getX() <= max.getX() &&
            point.getY() >= min.getY() && point.getY() <= max.getY();
    }

    boolean atWallBoundary(LocalPoint from, LocalPoint published)
    {
        return wallBoundary() && contains(published) && from != null && from.equals(goal(from, published));
    }

    boolean wallBoundary() { return min.equals(max); }

    LocalPoint goal(LocalPoint from, LocalPoint published, BiPredicate<LocalPoint, LocalPoint> collision)
    {
        if (!contains(published)) { return published; }
        if (!MovementPath.sameView(from, min) || !MovementPath.tileCenter(from) || contains(from)) { return null; }
        // Walls need the same reachable-perimeter search as solid footprints:
        // a geometrically near side can be sealed behind the wall itself.
        // Coordinate distance can prefer a side whose checked route bends past
        // an equally near, earlier perimeter. Use the shared cardinal-first
        // search to choose a reachable boundary, without guessing interaction reach.
        List<LocalPoint> route = MovementRoute.boundary(from, min, max, 64,
            (a, b) -> !contains(a) && !contains(b) && collision.test(a, b));
        return route == null ? null : route.isEmpty() ? from : route.get(route.size() - 1);
    }

    LocalPoint goal(LocalPoint from, LocalPoint published)
    {
        if (!contains(published)) { return published; }
        if (!MovementPath.sameView(from, min) || !MovementPath.tileCenter(from) || contains(from)) { return null; }
        int x = Math.max(min.getX(), Math.min(max.getX(), from.getX()));
        int y = Math.max(min.getY(), Math.min(max.getY(), from.getY()));
        LocalPoint[] edges = {
            new LocalPoint(x, min.getY() - 128, min.getWorldView()),
            new LocalPoint(x, max.getY() + 128, min.getWorldView()),
            new LocalPoint(min.getX() - 128, y, min.getWorldView()),
            new LocalPoint(max.getX() + 128, y, min.getWorldView())};
        LocalPoint best = null;
        int distance = Integer.MAX_VALUE, tie = Integer.MAX_VALUE;
        for (LocalPoint edge : edges)
        {
            int d = MovementPath.distance(from, edge);
            int t = Math.abs(from.getX() - edge.getX()) + Math.abs(from.getY() - edge.getY());
            if (d < distance || d == distance && t < tie) { best = edge; distance = d; tie = t; }
        }
        // This is a bounded staging point, not proof of interaction reach or
        // permitted sides. MovementPath still checks every edge; only native
        // updates can confirm the route or request travel along another side.
        return best;
    }
}
