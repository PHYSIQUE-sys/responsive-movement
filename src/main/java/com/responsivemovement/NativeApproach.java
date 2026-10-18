package com.responsivemovement;

import java.util.List;
import java.util.function.BiPredicate;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.coords.WorldArea;
import net.runelite.client.util.Text;

/** Click-scoped target/publication evidence. MovementPath owns every bounded visual forecast. */
final class NativeApproach
{
    private final WorldView view;
    private final int plane;
    private final Actor target;
    private final int index;
    private final int definition;
    private final LocalPoint previousDestination;
    private final LocalPoint objectAnchor;
    private LocalPoint objectGoalOrigin;
    private LocalPoint checkedObjectGoal;
    private final long clicked;
    final LocalPoint min, max;
    final int reserve;
    private long delayNanos;
    private boolean tickReleased;
    private final Client client;
    private final CombatApproach combat;
    private final boolean adjacentIntent;
    private final LocalPoint counterSide;

    private NativeApproach(Client client, WorldView view, Actor target, LocalPoint previousDestination, long now,
        int intentReserve, CombatApproach combat, LocalPoint objectAnchor)
    {
        this.view = view; plane = view.getPlane(); this.target = target;
        index = target instanceof NPC ? ((NPC) target).getIndex() : target instanceof Player ? ((Player) target).getId() : -1;
        definition = target instanceof NPC ? ((NPC) target).getId() : -1;
        this.previousDestination = previousDestination; clicked = now;
        this.objectAnchor = objectAnchor;
        this.client = client; this.combat = combat;
        WorldArea area = intentReserve > 0 && target != null ? target.getWorldArea() : null;
        LocalPoint point = area == null || area.getPlane() != plane ? null : LocalPoint.fromWorld(view,area.toWorldPoint());
        min = point;
        max = point == null ? null : new LocalPoint(point.getX()+(area.getWidth()-1)*128,
            point.getY()+(area.getHeight()-1)*128,view.getId());
        // A solid one-sided counter can stop a Use run pair before adjacency.
        // Reserve that final region rather than applying Bank-specific reach.
        adjacentIntent = intentReserve == 1;
        counterSide = adjacentIntent && min != null ? NpcApproach.counterSide(view,min,max) : null;
        reserve = counterSide != null ? 3 : intentReserve;
    }

    static NativeApproach capture(Client client, Player owner, MenuOptionClicked event, LocalPoint previousDestination, long now)
    {
        WorldView view = owner == null ? null : owner.getWorldView();
        if (view == null || event.getMenuEntry().getWorldViewId() != view.getId() ||
            !ResponsiveMovementPlugin.worldInteraction(event.getMenuAction())) { return null; }
        boolean npc = false, player = false;
        switch (event.getMenuAction())
        {
            case NPC_FIRST_OPTION: case NPC_SECOND_OPTION: case NPC_THIRD_OPTION:
            case NPC_FOURTH_OPTION: case NPC_FIFTH_OPTION: case ITEM_USE_ON_NPC: case WIDGET_TARGET_ON_NPC:
                npc = true; break;
            case PLAYER_FIRST_OPTION: case PLAYER_SECOND_OPTION: case PLAYER_THIRD_OPTION: case PLAYER_FOURTH_OPTION:
            case PLAYER_FIFTH_OPTION: case PLAYER_SIXTH_OPTION: case PLAYER_SEVENTH_OPTION: case PLAYER_EIGHTH_OPTION:
            case ITEM_USE_ON_PLAYER: case WIDGET_TARGET_ON_PLAYER:
                player = true; break;
            default: break;
        }
        Actor target = npc ? event.getMenuEntry().getNpc() : player ? event.getMenuEntry().getPlayer() : null;
        if ((npc || player) && (target == null || target == owner || target.getWorldView() != view || target.isDead() ||
            target.getWorldLocation() == null || target.getWorldLocation().getPlane() != view.getPlane() ||
            event.getId() != (npc ? ((NPC) target).getIndex() : ((Player) target).getId()))) { return null; }
        String option = event.getMenuOption();
        option = option == null ? "" : Text.removeTags(option).trim();
        boolean adjacentIntent = target != null && ("Use".equalsIgnoreCase(option) ||
            "Trade".equalsIgnoreCase(option) || "Trade with".equalsIgnoreCase(option) || "Trade-with".equalsIgnoreCase(option));
        CombatApproach combat = player && "Attack".equalsIgnoreCase(option) ? CombatApproach.capture(client) : null;
        // Selected spells and unknown/ranged player attacks keep the same outer
        // reserve policy as NPC combat; never presume they require adjacency.
        int intentReserve = adjacentIntent ? 1 : target != null && "Cast".equalsIgnoreCase(option) ? 10 :
            combat == null ? 0 : combat.reserveTiles;
        LocalPoint objectAnchor = null;
        switch (event.getMenuAction())
        {
            case ITEM_USE_ON_GAME_OBJECT: case WIDGET_TARGET_ON_GAME_OBJECT:
                int x = event.getParam0(), y = event.getParam1();
                if (x >= 0 && y >= 0 && x < view.getSizeX() && y < view.getSizeY())
                {
                    objectAnchor = new LocalPoint(x * 128 + 64, y * 128 + 64, view.getId());
                }
                break;
            default: break;
        }
        return new NativeApproach(client, view, target, previousDestination, now, intentReserve, combat, objectAnchor);
    }

    boolean valid(WorldView current)
    {
        if (current != view || view.getPlane() != plane || combat != null && !combat.valid(client)) { return false; }
        if (target == null) { return true; }
        return target.getWorldView() == view && !target.isDead() && target.getWorldLocation() != null &&
            target.getWorldLocation().getPlane() == plane && (target instanceof NPC
                ? view.npcs().byIndex(index) == target && ((NPC) target).getId() == definition
                : view.players().byIndex(index) == target);
    }

    /** A late flag requires a new checked server step, not an extended input-observation window. */
    boolean acceptsLate(LocalPoint destination, boolean authorityProgress, long now)
    {
        long age = now - clicked;
        return authorityProgress && pending(now) && (tickReleased || age>=delayNanos) && MovementPath.tileCenter(destination) &&
            destination.getWorldView() == view.getId() && !destination.equals(previousDestination);
    }

    boolean pending(long now) { return now >= clicked && now - clicked < 1_800_000_000L; }

    boolean counterIntent() { return counterSide != null; }
    boolean adjacentIntent() { return adjacentIntent; }

    /** A targeted object's raw flag can name its anchor, not a required stopping tile. */
    boolean objectTarget() { return objectAnchor != null; }
    boolean objectAnchor(LocalPoint destination) { return objectAnchor != null && objectAnchor.equals(destination); }

    /** Reserve the unknown object's tile, not an assumed server interaction reach. */
    LocalPoint objectGoal(LocalPoint from, BiPredicate<LocalPoint, LocalPoint> collision)
    {
        if (!MovementPath.sameView(from, objectAnchor) || !MovementPath.tileCenter(from) || from.equals(objectAnchor)) { return null; }
        if (!from.equals(objectGoalOrigin))
        {
            List<LocalPoint> route = MovementRoute.boundary(from, objectAnchor, objectAnchor, 64,
                (a, b) -> !objectAnchor.equals(a) && !objectAnchor.equals(b) && collision.test(a, b));
            checkedObjectGoal = route == null ? null : route.isEmpty() ? from : route.get(route.size() - 1);
            objectGoalOrigin = from;
        }
        return checkedObjectGoal;
    }

    LocalPoint facingPoint()
    {
        LocalPoint point = target == null ? null : target.getLocalLocation();
        return MovementPath.sameView(point,min) ? point :
            new LocalPoint((min.getX()+max.getX())/2,(min.getY()+max.getY())/2,view.getId());
    }

    /** Limits forecasts and confirmed-arrival pacing; never establishes server reach. */
    boolean atIntentBoundary(LocalPoint point)
    {
        if (!adjacentIntent || !hasIntent() || !sameFootprint() || !MovementPath.sameView(point,min)) { return false; }
        int dx=Math.max(0,Math.max(min.getX()-point.getX(),point.getX()-max.getX()));
        int dy=Math.max(0,Math.max(min.getY()-point.getY(),point.getY()-max.getY()));
        return reserve > 1 ? Math.max(dx,dy)<=reserve*128 : dx+dy<=128;
    }

    void delay(int millis) { delayNanos = Math.max(0,Math.min(300,millis))*1_000_000L; }
    void gameTick(long now) { if (now>=clicked) { tickReleased = true; } }
    boolean hasIntent()
    {
        return min != null && max != null && min.getX()<=max.getX() && min.getY()<=max.getY() &&
            min.getSceneX()>=0 && min.getSceneY()>=0 && max.getSceneX()<view.getSizeX() && max.getSceneY()<view.getSizeY();
    }
    boolean sameFootprint()
    {
        WorldArea area = target == null ? null : target.getWorldArea();
        LocalPoint point = area == null || area.getPlane() != plane ? null : LocalPoint.fromWorld(view,area.toWorldPoint());
        return hasIntent() && min.equals(point) && max.getX()==min.getX()+(area.getWidth()-1)*128 &&
            max.getY()==min.getY()+(area.getHeight()-1)*128;
    }
    LocalPoint intentGoal(LocalPoint from, BiPredicate<LocalPoint,LocalPoint> collision)
    {
        if (!hasIntent() || !sameFootprint() || !MovementPath.sameView(from,min)) { return null; }
        int dx=Math.max(0,Math.max(min.getX()-from.getX(),from.getX()-max.getX()));
        int dy=Math.max(0,Math.max(min.getY()-from.getY(),from.getY()-max.getY()));
        if (dx+dy<=128 || reserve>1 && Math.max(dx,dy)<=reserve*128) { return null; }
        // MovementPath searches to the captured accessible side, rather than
        // an arbitrary fragment toward an occupied counter. Reserve is unchanged.
        if (counterSide != null) { return counterSide; }
        return reserve == 1 ? NpcApproach.adjacentGoal(from,min,max,collision) : NpcApproach.goal(from,min,max,collision);
    }
    boolean newlyPublished(LocalPoint destination)
    {
        return MovementPath.tileCenter(destination) && destination.getWorldView()==view.getId() &&
            !destination.equals(previousDestination);
    }
}
