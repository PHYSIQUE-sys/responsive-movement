package com.responsivemovement;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import net.runelite.api.Client;
import net.runelite.api.Actor;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.util.Text;

/** Click-scoped NPC evidence for a provisional approach when no native destination is published. */
final class NpcApproach
{
    final LocalPoint min;
    final LocalPoint max;
    final CombatApproach combat;
    final int reserveTiles;
    private final boolean extendedApproach;
    private final LocalPoint counterSide;
    private final NPC npc;
    private final WorldView view;
    private final WorldArea area;
    private final int index;
    private final int id;
    private final LocalPoint previousDestination;

    private NpcApproach(NPC npc, WorldView view, WorldArea area, LocalPoint min, LocalPoint max,
        LocalPoint previousDestination, CombatApproach combat, boolean bankOrExchange, boolean bankServiceTalk)
    {
        this.npc = npc; this.view = view; this.area = area;
        this.min = min; this.max = max;
        index = npc.getIndex(); id = npc.getId();
        this.previousDestination = previousDestination;
        this.combat = combat;
        counterSide = (bankOrExchange || bankServiceTalk) && combat == null ? counterSide(view, min, max) : null;
        // Talk-to shares the bank counter stop only with bank-service cache
        // actions AND the proven one-sided geometry. Other ordinary options
        // retain adjacency, including bank-service Talk-to on unsupported layouts.
        extendedApproach = bankOrExchange || bankServiceTalk && counterSide != null;
        reserveTiles = combat == null ? extendedApproach ? 2 : 1 : combat.reserveTiles;
    }

    static NpcApproach capture(WorldView view, MenuOptionClicked event, LocalPoint previousDestination)
    {
        return capture(null, view, event, previousDestination);
    }

    static NpcApproach capture(Client client, WorldView view, MenuOptionClicked event, LocalPoint previousDestination)
    {
        MenuAction action = event.getMenuAction();
        if (action != MenuAction.NPC_FIRST_OPTION && action != MenuAction.NPC_SECOND_OPTION &&
            action != MenuAction.NPC_THIRD_OPTION && action != MenuAction.NPC_FOURTH_OPTION &&
            action != MenuAction.NPC_FIFTH_OPTION) { return null; }
        // Item/spell targeting retains native evidence. Attack gets a separate
        // weapon/style stopping reserve instead of assuming NPC adjacency.
        String option = event.getMenuOption();
        if (option == null) { return null; }
        option = Text.removeTags(option).trim();
        boolean attack = "Attack".equalsIgnoreCase(option);
        CombatApproach combat = attack ? CombatApproach.capture(client) : null;
        if (attack && combat == null) { return null; }
        if (view == null || event.getMenuEntry().getWorldViewId() != view.getId()) { return null; }
        NPC npc = event.getMenuEntry().getNpc();
        if (npc == null || npc.getWorldView() != view || npc.getIndex() != event.getId() || npc.isDead()) { return null; }
        WorldArea area = npc.getWorldArea();
        if (area == null || area.getPlane() != view.getPlane() || area.getWidth() < 1 || area.getHeight() < 1) { return null; }
        LocalPoint min = LocalPoint.fromWorld(view, area.toWorldPoint());
        if (min == null) { return null; }
        LocalPoint max = new LocalPoint(min.getX() + (area.getWidth() - 1) * 128,
            min.getY() + (area.getHeight() - 1) * 128, view.getId());
        if (max.getSceneX() >= view.getSizeX() || max.getSceneY() >= view.getSizeY()) { return null; }
        return new NpcApproach(npc, view, area, min, max, previousDestination, combat,
            "Bank".equalsIgnoreCase(option) || "Exchange".equalsIgnoreCase(option),
            "Talk-to".equalsIgnoreCase(option) && bankService(npc));
    }

    private static boolean bankService(NPC npc)
    {
        // Capture only this clicked target's supported cache actions, once.
        // Neither an NPC-name/ID table nor a scene-wide service lookup is needed.
        String[] actions = nativeActions(npc);
        if (actions == null) { return false; }
        for (String action : actions)
        {
            if (action != null && ("Bank".equalsIgnoreCase(action.trim()) || "Exchange".equalsIgnoreCase(action.trim()))) { return true; }
        }
        return false;
    }

    static boolean attackable(NPC npc)
    {
        if (npc == null || npc.isDead()) { return false; }
        String[] actions = nativeActions(npc);
        if (actions == null) { return false; }
        for (String action : actions)
        {
            if (action != null && "Attack".equalsIgnoreCase(action.trim())) { return true; }
        }
        return false;
    }

    private static String[] nativeActions(NPC npc)
    {
        NPCComposition composition = npc.getTransformedComposition();
        if (composition == null) { composition = npc.getComposition(); }
        return composition == null ? null : composition.getActions();
    }

    boolean valid(WorldView current)
    {
        // Direct indexed lookup of this target only; never enumerate NPCs or the scene.
        if (current != view || view.getPlane() != area.getPlane() || view.npcs().byIndex(index) != npc ||
            npc.getWorldView() != view || npc.getId() != id || npc.isDead()) { return false; }
        WorldArea now = npc.getWorldArea();
        return now != null && now.getX() == area.getX() && now.getY() == area.getY() &&
            now.getPlane() == area.getPlane() && now.getWidth() == area.getWidth() && now.getHeight() == area.getHeight();
    }

    boolean sameTarget(Actor actor) { return actor == npc; }

    boolean sameCapturedTarget(NpcApproach other)
    {
        return other != null && npc == other.npc && view == other.view && id == other.id && index == other.index;
    }

    LocalPoint facingPoint()
    {
        LocalPoint point = npc.getLocalLocation();
        return MovementPath.sameView(min, point) ? point :
            new LocalPoint((min.getX() + max.getX()) / 2, (min.getY() + max.getY()) / 2, min.getWorldView());
    }

    /** Refresh only this clicked Attack target; unsupported profiles gain facing, not route credit. */
    NpcApproach refreshCombat(WorldView current)
    {
        if (combat == null || current != view || view.getPlane() != area.getPlane() ||
            view.npcs().byIndex(index) != npc || npc.getWorldView() != view || npc.getId() != id || npc.isDead()) { return null; }
        WorldArea now = npc.getWorldArea();
        if (now == null || now.getPlane() != area.getPlane() || now.getWidth() != area.getWidth() || now.getHeight() != area.getHeight() ||
            Math.max(Math.abs(now.getX() - area.getX()), Math.abs(now.getY() - area.getY())) > 2) { return null; }
        if (now.getX() == area.getX() && now.getY() == area.getY()) { return this; }
        LocalPoint nextMin = LocalPoint.fromWorld(view, now.toWorldPoint());
        if (nextMin == null) { return null; }
        LocalPoint nextMax = new LocalPoint(nextMin.getX() + (now.getWidth() - 1) * 128,
            nextMin.getY() + (now.getHeight() - 1) * 128, view.getId());
        if (nextMax.getSceneX() >= view.getSizeX() || nextMax.getSceneY() >= view.getSizeY()) { return null; }
        return new NpcApproach(npc, view, now, nextMin, nextMax, previousDestination, combat, false, false);
    }

    boolean adjacentCombat() { return combat != null && combat.adjacentMelee; }
    boolean rangedCombat() { return combat != null && combat.knownRanged; }
    boolean movingCombat() { return adjacentCombat() || rangedCombat(); }

    boolean combatBoundary(LocalPoint point)
    {
        return adjacentCombat() ? adjacent(point) : rangedCombat() && inRangedReach(point);
    }

    private boolean inRangedReach(LocalPoint point)
    {
        if (!MovementPath.sameView(min, point) || !MovementPath.tileCenter(point)) { return false; }
        int dx = Math.max(0, Math.max(min.getX() - point.getX(), point.getX() - max.getX()));
        int dy = Math.max(0, Math.max(min.getY() - point.getY(), point.getY() - max.getY()));
        if (Math.max(dx, dy) > reserveTiles * 128 || view.getScene() == null || view.getScene().getTiles() == null) { return false; }
        WorldPoint from = new WorldPoint(view.getBaseX() + point.getSceneX(), view.getBaseY() + point.getSceneY(), area.getPlane());
        return from.toWorldArea().hasLineOfSightTo(view, area);
    }

    /** Known weapon/style plus checked whole pairs; no speculative search through a sight-blocking wall. */
    List<LocalPoint> rangedSteps(LocalPoint from, boolean run, BiPredicate<LocalPoint, LocalPoint> collision)
    {
        if (!rangedCombat() || !MovementPath.sameView(from, min) || !MovementPath.tileCenter(from)) { return null; }
        if (inRangedReach(from)) { return List.of(); }
        int dx = Math.max(min.getX(), Math.min(max.getX(), from.getX())) - from.getX();
        int dy = Math.max(min.getY(), Math.min(max.getY(), from.getY())) - from.getY();
        int xs = Math.abs(dx) / 128, ys = Math.abs(dy) / 128, length = Math.max(xs, ys);
        if (length > 64) { return null; }
        List<LocalPoint> steps = new ArrayList<>();
        LocalPoint last = from;
        for (int i = 1; i < length; ++i)
        {
            int sx = Math.max(0, i - (length - xs)), sy = Math.max(0, i - (length - ys));
            LocalPoint next = new LocalPoint(from.getX() + Integer.signum(dx) * sx * 128,
                from.getY() + Integer.signum(dy) * sy * 128, from.getWorldView());
            if (!collision.test(last, next) || !collision.test(next, last)) { return null; }
            steps.add(next); last = next;
            if ((!run || i % 2 == 0) && inRangedReach(next)) { return steps; }
        }
        return null;
    }

    boolean adjacent(LocalPoint point)
    {
        if (!MovementPath.sameView(min, point)) { return false; }
        int dx = Math.max(0, Math.max(min.getX() - point.getX(), point.getX() - max.getX()));
        int dy = Math.max(0, Math.max(min.getY() - point.getY(), point.getY() - max.getY()));
        return dx + dy == 128;
    }

    boolean newlyPublished(LocalPoint destination)
    {
        return acceptsDestination(destination) && !destination.equals(previousDestination);
    }

    boolean ordinaryOption() { return combat == null && !extendedApproach; }

    boolean acceptsDestination(LocalPoint destination)
    {
        if (!MovementPath.sameView(min, destination)) { return false; }
        if (!ordinaryOption() && !adjacentCombat()) { return true; }
        int dx = Math.max(0, Math.max(min.getX() - destination.getX(), destination.getX() - max.getX()));
        int dy = Math.max(0, Math.max(min.getY() - destination.getY(), destination.getY() - max.getY()));
        // A late flag far from this ordinary target can still be the preceding
        // Walk. It is not evidence to redirect an NPC preview away from the NPC.
        return Math.max(dx, dy) <= 128;
    }

    LocalPoint goal(LocalPoint from, BiPredicate<LocalPoint, LocalPoint> collision)
    {
        return ordinaryOption() || adjacentCombat() ? adjacentGoal(from, min, max, collision) : goal(from, min, max, collision);
    }

    /** Checked cardinal-side staging for adjacent ordinary interactions, not the diagonal ring. */
    static LocalPoint adjacentGoal(LocalPoint from, LocalPoint min, LocalPoint max,
        BiPredicate<LocalPoint, LocalPoint> collision)
    {
        if (!MovementPath.sameView(from, min) || !MovementPath.sameView(min, max) || !MovementPath.tileCenter(from)) { return null; }
        int x = Math.max(min.getX(), Math.min(max.getX(), from.getX()));
        int y = Math.max(min.getY(), Math.min(max.getY(), from.getY()));
        LocalPoint west = new LocalPoint(min.getX() - 128, y, min.getWorldView());
        LocalPoint east = new LocalPoint(max.getX() + 128, y, min.getWorldView());
        LocalPoint south = new LocalPoint(x, min.getY() - 128, min.getWorldView());
        LocalPoint north = new LocalPoint(x, max.getY() + 128, min.getWorldView());
        LocalPoint[] sides = from.getX() != x && from.getY() != y
            ? new LocalPoint[] {south, north, west, east} : new LocalPoint[] {west, east, south, north};
        // Equal one-step diagonal approaches prefer the side reached by the
        // horizontal step, matching these captures and the route search's
        // cardinal ordering. This is a geometric tie-break, not an NPC table.
        LocalPoint best = null;
        int distance = Integer.MAX_VALUE, geometry = Integer.MAX_VALUE;
        for (LocalPoint side : sides)
        {
            if (from.equals(side)) { return null; }
            List<LocalPoint> route = MovementPath.checkedRoute(from, side,
                (a, b) -> collision.test(a, b) && collision.test(b, a) &&
                    !(b.getX() >= min.getX() && b.getX() <= max.getX() && b.getY() >= min.getY() && b.getY() <= max.getY()), 64);
            if (route == null) { continue; }
            int length = route.size(), travel = Math.abs(side.getX() - from.getX()) + Math.abs(side.getY() - from.getY());
            if (length < distance || length == distance && travel < geometry)
            {
                best = side; distance = length; geometry = travel;
            }
        }
        // A blocked approach retains only the original clear prefix, never a
        // speculative detour around an occupied target or guessed interaction range.
        return best == null ? goal(from, min, max, collision) : best;
    }

    LocalPoint shortRunGoal(LocalPoint from, BiPredicate<LocalPoint, LocalPoint> collision)
    {
        return extendedApproach && combat == null ? shortRunGoal(from, min, max, collision) : null;
    }

    boolean counterBank() { return counterSide != null; }
    boolean extendedRangeOption() { return extendedApproach && combat == null; }

    /** A fresh bank-counter click at the two-tile square needs actual movement evidence. */
    boolean atCounterStartBoundary(LocalPoint point)
    {
        // The 13:10 capture's (2,2) start performs no native movement. Keep this
        // conservative start gate separate from the rounded whole-run-pair stop:
        // a continuing run may still have to finish its pair through that corner.
        // counterSide already requires Bank/Exchange or proven bank-service Talk-to.
        return counterSide != null && MovementPath.sameView(min, point) && MovementPath.tileCenter(point) &&
            MovementPath.distance(point, min) <= 256;
    }

    /** One-sided, solid counter geometry, captured once; no NPC IDs or GE coordinates. */
    static LocalPoint counterSide(WorldView view, LocalPoint min, LocalPoint max)
    {
        if (!min.equals(max)) { return null; }
        CollisionData[] maps = view.getCollisionMaps();
        int plane = view.getPlane();
        if (maps == null || plane < 0 || plane >= maps.length || maps[plane] == null) { return null; }
        int[][] flags = maps[plane].getFlags();
        int x = min.getSceneX(), y = min.getSceneY();
        if (flags == null || x < 1 || x + 1 >= flags.length || y < 1) { return null; }
        for (int sx = x - 1; sx <= x + 1; ++sx)
        {
            if (flags[sx] == null || y + 1 >= flags[sx].length) { return null; }
        }
        // The captured bank island is movement-blocked but not a projectile-blocking wall.
        if ((flags[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0 ||
            (flags[x][y] & CollisionDataFlag.BLOCK_LINE_OF_SIGHT_FULL) != 0) { return null; }
        LocalPoint side = null;
        for (int[] offset : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}})
        {
            if ((flags[x + offset[0]][y + offset[1]] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) != 0) { continue; }
            if (side != null) { return null; }
            side = new LocalPoint(min.getX() + offset[0] * 128, min.getY() + offset[1] * 128, min.getWorldView());
        }
        return side;
    }

    CounterRun counterRun(LocalPoint from, BiPredicate<LocalPoint, LocalPoint> collision)
    {
        if (counterSide == null || !MovementPath.sameView(from, min) || !MovementPath.tileCenter(from) ||
            atCounterStartBoundary(from)) { return null; }
        List<LocalPoint> route = MovementRoute.find(from, counterSide, 64, collision);
        if (route == null || route.isEmpty()) { return null; }
        LocalPoint boundary = null;
        // Native movement executes the run pair before checking interaction reach.
        // Retain that pair's alignment, rather than clipping at the first tile in
        // a square staging ring. The rounded two-tile envelope is supported by
        // the captured Bank cases, not asserted for other options or arbitrary NPCs.
        for (int end = Math.min(2, route.size()); end <= route.size(); end = Math.min(end + 2, route.size()))
        {
            LocalPoint endpoint = route.get(end - 1);
            // The accessible-side diagonal corner can already interact without
            // a native flag (19:25 capture). Reserve onward prediction there,
            // but retain this same ordered route for a matching native flag or
            // actual later steps. Corners behind the blocked counter still need
            // the original rounded stop to approach its accessible side.
            long side = (long) (counterSide.getX() - min.getX()) * (endpoint.getX() - min.getX()) +
                (long) (counterSide.getY() - min.getY()) * (endpoint.getY() - min.getY());
            if (boundary == null && side > 0 && Math.abs(endpoint.getX() - min.getX()) == 256 &&
                Math.abs(endpoint.getY() - min.getY()) == 256) { boundary = endpoint; }
            if (inCounterReach(endpoint)) { return new CounterRun(counterSide, route.subList(0, end), boundary); }
            if (end == route.size()) { break; }
        }
        return null;
    }

    private boolean inCounterReach(LocalPoint point)
    {
        int dx = Math.abs(point.getX() - min.getX()), dy = Math.abs(point.getY() - min.getY());
        return Math.max(dx, dy) <= 256 && dx + dy <= 384;
    }

    static final class CounterRun
    {
        final LocalPoint nativeGoal;
        final List<LocalPoint> steps;
        final LocalPoint boundaryGoal;

        CounterRun(LocalPoint nativeGoal, List<LocalPoint> steps)
        {
            this(nativeGoal, steps, null);
        }

        CounterRun(LocalPoint nativeGoal, List<LocalPoint> steps, LocalPoint boundaryGoal)
        {
            this.nativeGoal = nativeGoal; this.steps = List.copyOf(steps);
            this.boundaryGoal = boundaryGoal;
        }
    }

    /** Captured three-tile starts, with a zero/one/two-tile minor-axis offset. */
    static LocalPoint shortRunGoal(LocalPoint from, LocalPoint min, LocalPoint max,
        BiPredicate<LocalPoint, LocalPoint> collision)
    {
        if (!MovementPath.sameView(from, min) || !MovementPath.sameView(min, max) || !min.equals(max) ||
            !MovementPath.tileCenter(from) || !MovementPath.tileCenter(min)) { return null; }
        int dx = min.getX() - from.getX(), dy = min.getY() - from.getY();
        int major = Math.max(Math.abs(dx), Math.abs(dy)), minor = Math.min(Math.abs(dx), Math.abs(dy));
        if (major != 3 * 128 || minor > 2 * 128) { return null; }
        boolean horizontal = Math.abs(dx) > Math.abs(dy);
        LocalPoint straight = new LocalPoint(from.getX() + (horizontal ? Integer.signum(dx) * 256 : 0),
            from.getY() + (horizontal ? 0 : Integer.signum(dy) * 256), from.getWorldView());
        if (minor <= 128)
        {
            LocalPoint adjacent = new LocalPoint(min.getX() - (horizontal ? Integer.signum(dx) * 128 : 0),
                min.getY() - (horizontal ? 0 : Integer.signum(dy) * 128), min.getWorldView());
            if (MovementPath.clearNpcRunPair(from, adjacent, collision)) { return adjacent; }
        }
        // With the minor axis already two tiles away, retain its column/row:
        // the captures stop there using extended Bank reach. A blocked adjacent
        // side likewise uses the checked straight pair before a counter, rather
        // than guessing travel around/through it or toward the NPC's anchor.
        return MovementPath.clearNpcRunPair(from, straight, collision) ? straight : null;
    }

    static LocalPoint goal(LocalPoint from, LocalPoint min, LocalPoint max,
        BiPredicate<LocalPoint, LocalPoint> collision)
    {
        if (!MovementPath.sameView(from, min) || !MovementPath.sameView(min, max) ||
            !MovementPath.tileCenter(from)) { return null; }
        int targetX = Math.max(min.getX(), Math.min(max.getX(), from.getX()));
        int targetY = Math.max(min.getY(), Math.min(max.getY(), from.getY()));
        int dx = targetX - from.getX(), dy = targetY - from.getY();
        int xs = Math.abs(dx) / 128, ys = Math.abs(dy) / 128, length = Math.max(xs, ys);
        if (length <= 1 || length > 64) { return null; }
        LocalPoint last = from;
        for (int i = 1; i < length; ++i)
        {
            // Only the clear straight-first prefix toward the footprint is evidence
            // for an early start. Stop at the adjacent ring or before a counter/wall;
            // do not invent a detour to the NPC's occupied tile or interaction reach.
            int sx = Math.max(0, i - (length - xs)), sy = Math.max(0, i - (length - ys));
            LocalPoint next = new LocalPoint(from.getX() + Integer.signum(dx) * sx * 128,
                from.getY() + Integer.signum(dy) * sy * 128, from.getWorldView());
            if (!collision.test(last, next) || !collision.test(next, last)) { break; }
            last = next;
        }
        return last.equals(from) ? null : last;
    }
}
