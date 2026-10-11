package com.responsivemovement;

import java.awt.event.KeyEvent;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.runelite.client.config.Keybind;
import net.runelite.client.input.KeyListener;

/** Diagnostic observation only: no consumed/injected input, with one bookmark per physical press. */
final class TraceBookmarkKeyListener implements KeyListener
{
    private final Supplier<Keybind> keybind;
    private final BooleanSupplier enabled;
    private final Runnable bookmark;
    private volatile Keybind pressed;

    TraceBookmarkKeyListener(Supplier<Keybind> keybind, BooleanSupplier enabled, Runnable bookmark)
    {
        this.keybind = keybind; this.enabled = enabled; this.bookmark = bookmark;
    }

    @Override public void keyPressed(KeyEvent event)
    {
        if (event.isConsumed() || pressed != null || !enabled.getAsBoolean()) { return; }
        Keybind binding = keybind.get();
        if (binding != null && binding.matches(event)) { pressed = binding; bookmark.run(); }
    }

    @Override public void keyReleased(KeyEvent event)
    {
        if (pressed != null && pressed.matches(event)) { pressed = null; }
    }

    @Override public void keyTyped(KeyEvent event) { }
    @Override public void focusLost() { pressed = null; }
}
