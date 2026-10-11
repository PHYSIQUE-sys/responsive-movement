package com.responsivemovement;

import com.google.inject.Provides;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(name = "Responsive Movement", internalName = "responsive-movement",
    legacyDataDirectory = "responsive-movement",
    description = "Smooths the local player's displayed movement with native animations",
    tags = {"movement", "camera", "animation", "smoothing"}, conflicts = {"True Tile Movement"})
public class ResponsiveMovementPlugin extends Plugin
{
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ResponsiveMovementConfig config;
    @Inject private RenderCallbackManager renderCallbacks;
    @Inject private DrawManager drawManager;
    @Inject private MouseManager mouseManager;
    @Inject private OverlayManager overlayManager;
    @Inject private MovementOverheads overheads;
    @Inject private TrueTileOverlay trueTile;
    @Inject private KeyManager keyManager;
    @Inject private ConfigManager configManager;
    @Inject private ClientToolbar clientToolbar;

    private MovementController movement;
    private PresentationCamera camera;
    private volatile MovementProfilesSidebar profilesSidebar;
    private volatile RenderState renderState = RenderState.NATIVE;
    private volatile long lastGpuCallbackNanos;
    private volatile Point primaryPress;
    private LocalPoint previousTickDestination;
    private boolean supported = true;
    private volatile boolean active;
    private long observedGpuCallback;
    private int ticksWithoutGpu;
    private volatile long lifecycleGeneration;
    private final AtomicBoolean bookmarkQueued = new AtomicBoolean();
    private final TraceBookmarkKeyListener bookmarkKey = new TraceBookmarkKeyListener(
        () -> config.traceBookmarkKey(), () -> active && config.recordTrace(), () -> requestBookmark("hotkey"));

    private final MouseAdapter mouse = new MouseAdapter()
    {
        @Override public MouseEvent mousePressed(MouseEvent event)
        {
            if (active && !event.isConsumed() && event.getButton() == MouseEvent.BUTTON1)
            {
                primaryPress = new Point(event.getX(), event.getY());
            }
            return event;
        }
    };

    private final RenderCallback renderer = new RenderCallback()
    {
        @Override public boolean addEntity(Renderable renderable, boolean ui)
        {
            RenderState state = renderState;
            return !ui || !state.hideUi || renderable != state.player;
        }

        @Override public boolean drawObject(Scene scene, TileObject object)
        {
            long hash = object.getHash();
            if (RenderState.isPlayer(hash)) { lastGpuCallbackNanos = System.nanoTime(); }
            return renderState.drawObject(hash, object.getId());
        }
    };

    private final Runnable afterDraw = () ->
    {
        if (!active) { return; }
        if (camera != null) { camera.afterDraw(); }
        if (movement != null) { movement.presented(); }
    };

    private static final Set<MenuAction> WORLD_INTERACTIONS = EnumSet.of(
        MenuAction.ITEM_USE_ON_GAME_OBJECT, MenuAction.WIDGET_TARGET_ON_GAME_OBJECT,
        MenuAction.GAME_OBJECT_FIRST_OPTION, MenuAction.GAME_OBJECT_SECOND_OPTION,
        MenuAction.GAME_OBJECT_THIRD_OPTION, MenuAction.GAME_OBJECT_FOURTH_OPTION, MenuAction.GAME_OBJECT_FIFTH_OPTION,
        MenuAction.ITEM_USE_ON_NPC, MenuAction.WIDGET_TARGET_ON_NPC,
        MenuAction.NPC_FIRST_OPTION, MenuAction.NPC_SECOND_OPTION, MenuAction.NPC_THIRD_OPTION,
        MenuAction.NPC_FOURTH_OPTION, MenuAction.NPC_FIFTH_OPTION,
        MenuAction.ITEM_USE_ON_PLAYER, MenuAction.WIDGET_TARGET_ON_PLAYER,
        MenuAction.PLAYER_FIRST_OPTION, MenuAction.PLAYER_SECOND_OPTION, MenuAction.PLAYER_THIRD_OPTION,
        MenuAction.PLAYER_FOURTH_OPTION, MenuAction.PLAYER_FIFTH_OPTION, MenuAction.PLAYER_SIXTH_OPTION,
        MenuAction.PLAYER_SEVENTH_OPTION, MenuAction.PLAYER_EIGHTH_OPTION,
        MenuAction.ITEM_USE_ON_GROUND_ITEM, MenuAction.WIDGET_TARGET_ON_GROUND_ITEM,
        MenuAction.GROUND_ITEM_FIRST_OPTION, MenuAction.GROUND_ITEM_SECOND_OPTION,
        MenuAction.GROUND_ITEM_THIRD_OPTION, MenuAction.GROUND_ITEM_FOURTH_OPTION, MenuAction.GROUND_ITEM_FIFTH_OPTION,
        MenuAction.WORLD_ENTITY_FIRST_OPTION, MenuAction.WORLD_ENTITY_SECOND_OPTION,
        MenuAction.WORLD_ENTITY_THIRD_OPTION, MenuAction.WORLD_ENTITY_FOURTH_OPTION, MenuAction.WORLD_ENTITY_FIFTH_OPTION);

    @Provides
    ResponsiveMovementConfig config(ConfigManager manager) { return manager.getConfig(ResponsiveMovementConfig.class); }

    @Override
    protected void startUp()
    {
        // Capture the managed Filepath provider without doing I/O at startup.
        // The trace writer resolves/migrates the directory only when recording.
        movement = new MovementController(client, config, System::nanoTime, new MovementTrace(this::getPluginDirectory));
        camera = new PresentationCamera(client, config);
        renderState = RenderState.NATIVE;
        primaryPress = null;
        previousTickDestination = null;
        lastGpuCallbackNanos = 0;
        observedGpuCallback = 0;
        ticksWithoutGpu = 0;
        supported = active = true;
        renderCallbacks.register(renderer);
        drawManager.registerEveryFrameListener(afterDraw);
        mouseManager.registerMouseListener(mouse);
        overlayManager.add(overheads);
        overlayManager.add(trueTile);
        keyManager.registerKeyListener(bookmarkKey);
        ++lifecycleGeneration;
        profilesSidebar = new MovementProfilesSidebar(configManager, config, task -> clientThread.invoke(task), clientToolbar);
        profilesSidebar.start();
    }

    @Override
    protected void shutDown()
    {
        active = false;
        ++lifecycleGeneration;
        keyManager.unregisterKeyListener(bookmarkKey);
        bookmarkKey.focusLost();
        MovementProfilesSidebar retiringProfiles = profilesSidebar;
        profilesSidebar = null;
        if (retiringProfiles != null) { retiringProfiles.close(); }
        renderState = RenderState.NATIVE;
        primaryPress = null;
        previousTickDestination = null;
        mouseManager.unregisterMouseListener(mouse);
        renderCallbacks.unregister(renderer);
        drawManager.unregisterEveryFrameListener(afterDraw);
        overlayManager.remove(overheads);
        overlayManager.remove(trueTile);
        // Cleanup can be queued while the same plugin instance is re-enabled.
        // Capture the retiring controllers, never close a later startup's state.
        MovementController retiringMovement = movement;
        PresentationCamera retiringCamera = camera;
        movement = null;
        camera = null;
        clientThread.invoke(() ->
        {
            if (retiringMovement != null) { retiringMovement.close(); }
            if (retiringCamera != null) { retiringCamera.close(); }
            if (!active) { overheads.clear(); }
        });
    }

    @Subscribe
    public void onClientTick(ClientTick event)
    {
        if (!active) { return; }
        movement.pollTrace();
        camera.beforeInput();
        boolean wasSupported = supported;
        if (lastGpuCallbackNanos != observedGpuCallback)
        {
            observedGpuCallback = lastGpuCallbackNanos;
            ticksWithoutGpu = 0;
        }
        supported = client.isGpu() && (client.getGameState() != GameState.LOGGED_IN || ticksWithoutGpu <= 50);
        if (client.getGameState() == GameState.LOGGED_IN) { ticksWithoutGpu = Math.min(51, ticksWithoutGpu + 1); }
        if (!supported)
        {
            renderState = RenderState.NATIVE;
            if (wasSupported) { movement.close(); camera.close(); overheads.clear(); }
        }
        Point press = primaryPress;
        primaryPress = null;
        if (press != null && supported && client.getGameState() == GameState.LOGGED_IN && !client.isMenuOpen())
        {
            Widget minimap = minimap();
            // Interface processing can publish the minimap destination before
            // ClientTick. Compare against the previous tick, not the new value.
            if (minimap != null && !minimap.isHidden() && insideMinimap(press, minimap.getBounds()))
            {
                movement.walkClick(previousTickDestination);
            }
        }
    }

    @Subscribe
    public void onPostClientTick(PostClientTick event)
    {
        // Destination observation happens in the next preparation, after native
        // input processing. Keeping native mode here also preserves right-click
        // menus, minimap conversion, zoom scripts, and ordinary world actions.
        if (active)
        {
            camera.beforeInput();
            previousTickDestination = client.getLocalDestinationLocation();
        }
    }

    @Subscribe
    public void onBeforeRender(BeforeRender event)
    {
        renderState = RenderState.NATIVE;
        if (!active || !supported || client.getGameState() != GameState.LOGGED_IN) { return; }
        if (!client.isGpu()) { return; }
        Player player = client.getLocalPlayer();
        if (player == null) { return; }
        boolean custom = movement.update();
        if (custom && player.getWorldView() != null)
        {
            renderState = new RenderState(player, player.getId(), player.getWorldView().getId(), true,
                config.overheads() && overheads.ready());
        }
        camera.prepare(player, movement.position(), movement.nativeCamera());
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (!active) { return; }
        // A native menu action accounts for this primary press. Its menu may
        // already be closed by ClientTick: do not reinterpret a selection over
        // the minimap as another Walk (or overwrite an interaction capture).
        primaryPress = null;
        if (!supported || event.isConsumed() || client.getGameState() != GameState.LOGGED_IN) { return; }
        if (event.getMenuAction() == MenuAction.WALK) { movement.walkClick(); }
        else if (worldInteraction(event.getMenuAction())) { movement.worldInteraction(event); }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (!active) { return; }
        GameState state = event.getGameState();
        primaryPress = null;
        previousTickDestination = null;
        renderState = RenderState.NATIVE;
        if (state == GameState.LOADING || state == GameState.HOPPING || state == GameState.CONNECTION_LOST)
        {
            movement.sceneChanged(state == GameState.LOADING); camera.sceneChanged();
        }
        if (state == GameState.LOGGED_IN) { ticksWithoutGpu = 0; }
        if (state == GameState.LOGIN_SCREEN || state == GameState.LOGIN_SCREEN_AUTHENTICATOR)
        {
            movement.close(); camera.close(); overheads.clear();
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (active && client.getGameState() == GameState.LOGGED_IN)
        {
            if (supported) { movement.gameTick(); }
            overheads.tick();
        }
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event)
    {
        if (active && event.getActor() == client.getLocalPlayer()) { overheads.hit(event.getHitsplat()); }
        else if (active && movement != null) { movement.combatHit(event.getActor(), event.getHitsplat()); }
    }

    private void requestBookmark(String source)
    {
        if (!active || !bookmarkQueued.compareAndSet(false, true)) { return; }
        long observedNanos = System.nanoTime();
        long generation = lifecycleGeneration;
        clientThread.invoke(() -> {
            bookmarkQueued.set(false);
            if (!active || generation != lifecycleGeneration || movement == null || !config.recordTrace()) { return; }
            long sequence = movement.bookmark(source, observedNanos);
            if (sequence >= 0)
            {
                String message = "Responsive Movement: trace bookmark #" + sequence + " submitted.";
                if (client.getGameState() == GameState.LOGGED_IN) { client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null); }
            }
        });
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        MovementProfilesSidebar sidebar = profilesSidebar;
        if (active && sidebar != null && ResponsiveMovementConfig.GROUP.equals(event.getGroup()) &&
            (MovementProfiles.SAVES_KEY.equals(event.getKey()) || MovementProfiles.SELECTED_KEY.equals(event.getKey())))
        {
            sidebar.changed();
        }
    }

    @Subscribe
    public void onProfileChanged(ProfileChanged event)
    {
        MovementProfilesSidebar sidebar = profilesSidebar;
        if (active && sidebar != null) { sidebar.profileChanged(); }
    }

    private Widget minimap()
    {
        if (!client.isResized()) { return client.getWidget(InterfaceID.Toplevel.MINIMAP); }
        return client.getVarbitValue(VarbitID.RESIZABLE_STONE_ARRANGEMENT) == 1
            ? client.getWidget(InterfaceID.ToplevelPreEoc.MINIMAP) : client.getWidget(InterfaceID.ToplevelOsrsStretch.MINIMAP);
    }

    static boolean insideMinimap(Point point, Rectangle bounds)
    {
        if (point == null || bounds == null || bounds.width <= 0 || bounds.height <= 0) { return false; }
        double x = (point.getX() - bounds.getCenterX()) / (bounds.width / 2.0);
        double y = (point.getY() - bounds.getCenterY()) / (bounds.height / 2.0);
        return x * x + y * y <= 1;
    }

    static boolean worldInteraction(MenuAction action) { return action != null && WORLD_INTERACTIONS.contains(action); }
    boolean supported() { return supported; }
    boolean customUi() { return renderState.hideUi; }
    LocalPoint position() { return movement == null ? null : movement.position(); }
}
