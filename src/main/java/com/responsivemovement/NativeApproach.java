package com.responsivemovement;

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
    private final long clicked;
    final LocalPoint min, max;
    final int reserve;
    private long delayNanos;
    private boolean tickReleased;
    private final Client client;
    private final CombatApproach combat;

    private NativeApproach(Client client, WorldView view, Actor target, LocalPoint previousDestination, long now,
        int intentReserve, CombatApproach combat)
    {
        this.view = view; plane = view.getPlane(); this.target = target;
        index = target instanceof NPC ? ((NPC) target).getIndex() : target instanceof Player ? ((Player) target).getId() : -1;
        definition = target instanceof NPC ? ((NPC) target).getId() : -1;
        this.previousDestination = previousDestination; clicked = now;
        this.client = client; this.combat = combat;
        WorldArea area = intentReserve > 0 && target != null ? target.getWorldArea() : null;
        LocalPoint point = area == null || area.getPlane() != plane ? null : LocalPoint.fromWorld(view,area.toWorldPoint());
        min = point;
        max = point == null ? null : new LocalPoint(point.getX()+(area.getWidth()-1)*128,
            point.getY()+(area.getHeight()-1)*128,view.getId());
        // A solid one-sided counter can stop a Use run pair before adjacency.
        // Reserve that final region rather than applying Bank-specific reach.
        reserve = intentReserve == 1 && min != null && NpcApproach.counterSide(view,min,max) != null ? 3 : intentReserve;
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
        return new NativeApproach(client, view, target, previousDestination, now, intentReserve, combat);
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
        return authorityProgress && age >= 0 && age < 1_800_000_000L && (tickReleased || age>=delayNanos) && MovementPath.tileCenter(destination) &&
            destination.getWorldView() == view.getId() && !destination.equals(previousDestination);
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
        return reserve == 1 ? NpcApproach.adjacentGoal(from,min,max,collision) : NpcApproach.goal(from,min,max,collision);
    }
    boolean newlyPublished(LocalPoint destination)
    {
        return MovementPath.tileCenter(destination) && destination.getWorldView()==view.getId() &&
            !destination.equals(previousDestination);
    }
}
