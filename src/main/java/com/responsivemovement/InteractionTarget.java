package com.responsivemovement;

import net.runelite.api.MenuAction;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.MenuOptionClicked;

/** Immutable identity for an ordinary scene interaction; never an inferred destination. */
final class InteractionTarget
{
    private final WorldView view;
    private final int plane;
    private final MenuAction action;
    private final int id;
    private final int x;
    private final int y;

    private InteractionTarget(WorldView view, MenuOptionClicked event)
    {
        this.view = view; plane = view.getPlane(); action = event.getMenuAction();
        id = event.getId(); x = event.getParam0(); y = event.getParam1();
    }

    static InteractionTarget capture(WorldView view, MenuOptionClicked event)
    {
        // Item/spell targeting has additional selected-item/widget identity and
        // reach semantics. NPCs retain their existing click/engagement evidence.
        switch (event.getMenuAction())
        {
            case GAME_OBJECT_FIRST_OPTION: case GAME_OBJECT_SECOND_OPTION: case GAME_OBJECT_THIRD_OPTION:
            case GAME_OBJECT_FOURTH_OPTION: case GAME_OBJECT_FIFTH_OPTION:
            case GROUND_ITEM_FIRST_OPTION: case GROUND_ITEM_SECOND_OPTION: case GROUND_ITEM_THIRD_OPTION:
            case GROUND_ITEM_FOURTH_OPTION: case GROUND_ITEM_FIFTH_OPTION:
                break;
            default: return null;
        }
        if (view == null || event.getMenuEntry().getWorldViewId() != view.getId() ||
            event.getParam0() < 0 || event.getParam0() >= view.getSizeX() ||
            event.getParam1() < 0 || event.getParam1() >= view.getSizeY()) { return null; }
        return new InteractionTarget(view, event);
    }

    boolean same(InteractionTarget other)
    {
        return other != null && view == other.view && plane == other.plane && action == other.action &&
            id == other.id && x == other.x && y == other.y;
    }

    boolean groundItem()
    {
        return action == MenuAction.GROUND_ITEM_FIRST_OPTION || action == MenuAction.GROUND_ITEM_SECOND_OPTION ||
            action == MenuAction.GROUND_ITEM_THIRD_OPTION || action == MenuAction.GROUND_ITEM_FOURTH_OPTION ||
            action == MenuAction.GROUND_ITEM_FIFTH_OPTION;
    }

    boolean atAnchor(LocalPoint point)
    {
        return point != null && point.getWorldView() == view.getId() &&
            point.getSceneX() == x && point.getSceneY() == y;
    }
}
