package com.responsivemovement;

import java.util.List;
import java.util.function.BiPredicate;
import net.runelite.api.CollisionData;
import net.runelite.api.Client;
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
    private final boolean wall;
    private final boolean bank;

    private ObjectApproach(LocalPoint min, LocalPoint max)
    {
        this(min, max, false, false);
    }

    private ObjectApproach(LocalPoint min, LocalPoint max, boolean wall, boolean bank)
    {
        this.min = min;
        this.max = max;
        this.wall = wall;
        this.bank = bank;
    }

    static ObjectApproach bounds(LocalPoint min, LocalPoint max)
    {
        if (!MovementPath.sameView(min, max) || !MovementPath.tileCenter(min) || !MovementPath.tileCenter(max) ||
            min.getX() > max.getX() || min.getY() > max.getY() || min.equals(max)) { return null; }
        return new ObjectApproach(min, max);
    }

    static ObjectApproach capture(WorldView view, MenuOptionClicked event)
    {
        return capture(null,view,event);
    }

    static ObjectApproach capture(Client client, WorldView view, MenuOptionClicked event)
    {
        MenuAction action = event.getMenuAction();
        String option = event.getMenuOption();
        option = option == null ? "" : Text.removeTags(option).trim();
        boolean bank = "Bank".equalsIgnoreCase(option) &&
            action != MenuAction.ITEM_USE_ON_GAME_OBJECT && action != MenuAction.WIDGET_TARGET_ON_GAME_OBJECT;
        boolean targeted = action == MenuAction.ITEM_USE_ON_GAME_OBJECT || action == MenuAction.WIDGET_TARGET_ON_GAME_OBJECT;
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
                min.getY() >= flags[min.getX()].length) { return null; }
            boolean solid = (flags[min.getX()][min.getY()] & CollisionDataFlag.BLOCK_MOVEMENT_OBJECT) != 0;
            boolean service = bank;
            if (!solid && !service && targeted && client != null)
            {
                net.runelite.api.ObjectComposition definition = client.getObjectDefinition(event.getId());
                String[] actions = definition == null ? null : definition.getActions();
                if (actions != null)
                {
                    for (String candidate:actions) { if (candidate != null && "Bank".equalsIgnoreCase(candidate.trim())) { service=true; break; } }
                }
            }
            if (!solid && !service) { return null; }
            // Bank booths can be walkable single-tile geometry: their native
            // anchor flag is not the interaction stop. Capture only the matched
            // Bank object, without changing walkable-platform or Use policies.
            if (service || targeted && min.equals(max))
            {
                return new ObjectApproach(new LocalPoint(min.getX() * 128 + 64, min.getY() * 128 + 64, view.getId()),
                    new LocalPoint(max.getX() * 128 + 64, max.getY() * 128 + 64, view.getId()), false, service);
            }
            return bounds(new LocalPoint(min.getX() * 128 + 64, min.getY() * 128 + 64, view.getId()),
                new LocalPoint(max.getX() * 128 + 64, max.getY() * 128 + 64, view.getId()));
        }
        if (action == MenuAction.ITEM_USE_ON_GAME_OBJECT || action == MenuAction.WIDGET_TARGET_ON_GAME_OBJECT) { return null; }
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
                return new ObjectApproach(at, at, true, false);
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

    boolean wallBoundary() { return wall; }

    LocalPoint goal(LocalPoint from, LocalPoint published, BiPredicate<LocalPoint, LocalPoint> collision)
    {
        if (!contains(published)) { return published; }
        if (!MovementPath.sameView(from, min) || !MovementPath.tileCenter(from) || contains(from)) { return null; }
        if (bank)
        {
            // Stay on an already occupied perimeter row/column when selecting
            // another booth. A diagonal shortcut to its side can run underneath
            // the neighbouring booth in a walkable counter row. This is checked
            // staging, not an inferred interaction side or permission to enter it.
            LocalPoint side = null;
            if (from.getY() == min.getY() - 128 || from.getY() == max.getY() + 128)
            {
                side = new LocalPoint(Math.max(min.getX(), Math.min(max.getX(), from.getX())), from.getY(), min.getWorldView());
            }
            else if (from.getX() == min.getX() - 128 || from.getX() == max.getX() + 128)
            {
                side = new LocalPoint(from.getX(), Math.max(min.getY(), Math.min(max.getY(), from.getY())), min.getWorldView());
            }
            if (side != null && MovementPath.checkedRoute(from, side,
                (a, b) -> collision.test(a, b) && collision.test(b, a) && !contains(a) && !contains(b), 64) != null)
            {
                return side;
            }
        }
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
