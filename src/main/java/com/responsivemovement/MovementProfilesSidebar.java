package com.responsivemovement;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

/** Owns the compact profile UI lifetime; setting writes execute as one guarded client-thread task. */
final class MovementProfilesSidebar implements AutoCloseable
{
    private final MovementProfiles profiles;
    private final Consumer<Runnable> clientExecutor;
    private final ClientToolbar toolbar;
    private final LongSupplier profileId;
    private final AtomicLong scope = new AtomicLong();
    private final AtomicBoolean refreshQueued = new AtomicBoolean();
    private volatile boolean active = true;
    private MovementProfilesPanel panel;
    private NavigationButton navigation;

    MovementProfilesSidebar(ConfigManager manager, ResponsiveMovementConfig config,
        Consumer<Runnable> clientExecutor, ClientToolbar toolbar)
    {
        this(new MovementProfiles(new MovementProfiles.Store() {
            @Override public String get(String key) { return manager.getConfiguration(ResponsiveMovementConfig.GROUP, key); }
            @Override public void set(String key, String value) { manager.setConfiguration(ResponsiveMovementConfig.GROUP, key, value); }
        }, new MovementSettings(config)), clientExecutor, toolbar, () -> manager.getProfile().getId());
    }

    MovementProfilesSidebar(MovementProfiles profiles, Consumer<Runnable> clientExecutor,
        ClientToolbar toolbar, LongSupplier profileId)
    {
        this.profiles = profiles; this.clientExecutor = clientExecutor; this.toolbar = toolbar; this.profileId = profileId;
    }

    void start()
    {
        SwingUtilities.invokeLater(() -> {
            if (!active) { return; }
            panel = new MovementProfilesPanel(this::apply);
            refreshPanel(null);
            if (!active) { panel.close(); panel = null; return; }
            navigation = NavigationButton.builder().tooltip("Responsive Movement profiles")
                .icon(icon()).priority(6).panel(panel).build();
            toolbar.addNavigation(navigation);
        });
    }

    void apply(MovementProfilesPanel.Request request)
    {
        long generation = scope.get(), expectedProfile = profileId.getAsLong();
        clientExecutor.accept(() -> {
            if (!current(generation, expectedProfile)) { return; }
            String message, selection = request.name;
            boolean error = false;
            try
            {
                switch (request.action)
                {
                    case LOAD:
                        profiles.load(request.name);
                        message = "Loaded “" + request.name + "”.\n" + MovementProfilesPanel.LOAD_NOTICE;
                        break;
                    case SAVE:
                        profiles.save(request.name);
                        selection = profiles.selected();
                        message = "Saved “" + selection + "”.";
                        break;
                    case DELETE:
                        profiles.delete(request.name);
                        selection = null;
                        message = "Deleted “" + request.name + "”.";
                        break;
                    default: throw new IllegalArgumentException("Unsupported profile action.");
                }
            }
            catch (IllegalArgumentException failure) { message = failure.getMessage(); error = true; }
            String result = message, selected = selection;
            boolean failed = error;
            SwingUtilities.invokeLater(() -> {
                if (!current(generation, expectedProfile) || panel == null) { return; }
                refreshPanel(selected);
                panel.complete(result, failed);
            });
        });
    }

    private boolean current(long generation, long expectedProfile)
    {
        return active && generation == scope.get() && expectedProfile == profileId.getAsLong();
    }

    void changed()
    {
        if (!active || !refreshQueued.compareAndSet(false, true)) { return; }
        SwingUtilities.invokeLater(() -> {
            refreshQueued.set(false);
            if (active) { refreshPanel(null); }
        });
    }

    void profileChanged()
    {
        scope.incrementAndGet();
        SwingUtilities.invokeLater(() -> {
            if (!active || panel == null) { return; }
            panel.scopeChanged(); refreshPanel(null);
        });
    }

    private void refreshPanel(String selected)
    {
        if (panel == null) { return; }
        try { panel.refresh(profiles.names(), profiles.selected(), selected, profiles.savedNames()); }
        catch (IllegalArgumentException error) { panel.complete(error.getMessage(), true); }
    }

    @Override public void close()
    {
        active = false; scope.incrementAndGet();
        SwingUtilities.invokeLater(() -> {
            if (panel != null) { panel.close(); panel = null; }
            if (navigation != null) { toolbar.removeNavigation(navigation); navigation = null; }
        });
    }

    private static BufferedImage icon()
    {
        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = icon.createGraphics();
        try
        {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(155, 155, 155)); graphics.drawRoundRect(1, 1, 10, 11, 2, 2);
            graphics.setColor(new Color(50, 50, 50)); graphics.fillRoundRect(4, 4, 10, 11, 2, 2);
            graphics.setColor(new Color(230, 150, 55)); graphics.drawRoundRect(4, 4, 10, 11, 2, 2);
            graphics.drawLine(7, 7, 11, 7);
            graphics.setColor(new Color(210, 210, 210)); graphics.drawLine(7, 10, 11, 10); graphics.drawLine(7, 12, 10, 12);
        }
        finally { graphics.dispose(); }
        return icon;
    }
}
