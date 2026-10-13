package com.responsivemovement;

import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real sidebar controls and action dispatcher with only dialogs/client-task scheduling doubled. */
public class MovementProfilesSidebarTest
{
    @Test
    public void selectingAProfileDoesNothingUntilLoadAppliesItAndShowsTheRefreshNotice() throws Exception
    {
        Fixture f = new Fixture();
        try
        {
            f.saved.profiles.save("First"); f.saved.speed = 6.2; f.saved.profiles.save("Second");
            SwingUtilities.invokeAndWait(() -> {
                f.panel.refresh(f.saved.profiles.names(), f.saved.profiles.selected(), null);
                Map<String, String> before = new HashMap<>(f.saved.values);
                f.list().setSelectedValue("First", false);
                assertEquals(before, f.saved.values);
                assertTrue(f.tasks.isEmpty());
                f.button("Load").doClick();
                assertEquals(before, f.saved.values);
                assertFalse(f.button("Load").isEnabled());
                assertFalse(f.button("Save").isEnabled());
            });
            f.runTasks();
            assertEquals("First", f.saved.profiles.selected());
            assertEquals("5.4", f.saved.values.get("movementSpeed"));
            SwingUtilities.invokeAndWait(() -> {
                assertTrue(f.text().contains("Loaded “First”"));
                assertTrue(f.text().contains("reopen the plugin settings"));
                assertTrue(f.text().contains("No game restart is needed"));
                assertTrue(f.button("Load").isEnabled());
            });
        }
        finally { f.close(); }
    }

    @Test
    public void savePrefillsLoadedNameEvenWhenBrowsingAnotherProfileAndCancelMakesNoWrites() throws Exception
    {
        Fixture f = new Fixture();
        try
        {
            f.saved.profiles.save("First"); f.saved.profiles.save("Loaded");
            Map<String, String> before = new HashMap<>(f.saved.values);
            SwingUtilities.invokeAndWait(() -> {
                f.panel.refresh(f.saved.profiles.names(), f.saved.profiles.selected(), null);
                f.list().setSelectedValue("First", false);
                f.button("Save").doClick();
                assertEquals("Loaded", f.dialogs.prefilled);
                assertTrue(f.tasks.isEmpty());
            });
            assertEquals(before, f.saved.values);
            f.saved.speed = 5.1; f.dialogs.saveName = "Loaded";
            SwingUtilities.invokeAndWait(() -> f.button("Save").doClick());
            f.runTasks();
            assertEquals(2, f.saved.profiles.names().size());
            f.saved.profiles.load("Loaded");
            assertEquals("5.1", f.saved.values.get("movementSpeed"));
        }
        finally { f.close(); }
    }

    @Test
    public void deleteAsksAboutSelectedProfileAndCancelPreservesItThenConfirmRemovesOnlyTheSave() throws Exception
    {
        Fixture f = new Fixture();
        try
        {
            f.saved.profiles.save("Delete me"); f.saved.profiles.load("Delete me");
            Map<String, String> before = new HashMap<>(f.saved.values);
            SwingUtilities.invokeAndWait(() -> {
                f.panel.refresh(f.saved.profiles.names(), f.saved.profiles.selected(), null);
                f.button("Delete").doClick();
                assertEquals("Delete me", f.dialogs.deleteName);
                assertEquals(1, f.dialogs.confirmations);
                assertTrue(f.tasks.isEmpty());
            });
            assertEquals(before, f.saved.values);
            f.dialogs.confirmDelete = true;
            SwingUtilities.invokeAndWait(() -> f.button("Delete").doClick());
            assertEquals(2, f.dialogs.confirmations);
            f.runTasks();
            assertTrue(f.saved.profiles.names().isEmpty());
            assertEquals("5.4", f.saved.values.get("movementSpeed"));
            SwingUtilities.invokeAndWait(() -> {
                assertTrue(f.text().contains("Deleted “Delete me”"));
                assertFalse(f.button("Load").isEnabled());
                assertFalse(f.button("Delete").isEnabled());
                assertTrue(f.button("Save").isEnabled());
            });
        }
        finally { f.close(); }
    }

    @Test
    public void requestsQueuedBeforeShutdownOrRuneLiteProfileSwitchCannotApply() throws Exception
    {
        for (int change = 0; change < 3; ++change)
        {
            Fixture f = new Fixture();
            try
            {
                f.saved.profiles.save("First");
                Map<String, String> before = new HashMap<>(f.saved.values);
                f.sidebar.apply(new MovementProfilesPanel.Request(MovementProfilesPanel.Action.LOAD, "First"));
                if (change == 0) { f.sidebar.close(); }
                if (change == 1) { f.sidebar.profileChanged(); }
                if (change == 2) { f.profileId.incrementAndGet(); }
                f.runTasks();
                assertEquals(before, f.saved.values);
            }
            finally { f.close(); }
        }
    }

    @Test
    public void scopeChangeWhileSaveOrDeleteDialogIsOpenCancelsItsResponse() throws Exception
    {
        Fixture f = new Fixture();
        try
        {
            f.saved.profiles.save("First");
            Map<String, String> before = new HashMap<>(f.saved.values);
            f.dialogs.saveName = "Wrong scope"; f.dialogs.confirmDelete = true;
            f.dialogs.duringDialog = () -> f.panel.scopeChanged();
            SwingUtilities.invokeAndWait(() -> {
                f.panel.refresh(f.saved.profiles.names(), f.saved.profiles.selected(), null);
                f.button("Save").doClick();
                f.list().setSelectedValue("First", false);
                f.button("Delete").doClick();
                assertTrue(f.tasks.isEmpty());
            });
            assertEquals(before, f.saved.values);
        }
        finally { f.close(); }
    }

    @Test
    public void malformedProfileLoadShowsAnErrorAndKeepsCurrentSettings() throws Exception
    {
        Fixture f = new Fixture();
        try
        {
            f.saved.values.put(MovementProfiles.SAVES_KEY, "{\"schema\":1,\"profiles\":[{\"name\":\"Bad\",\"settings\":{\"movementSpeed\":\"not a number\"}}]}");
            Map<String, String> before = new HashMap<>(f.saved.values);
            SwingUtilities.invokeAndWait(() -> {
                f.panel.refresh(f.saved.profiles.names(), "", null);
                f.list().setSelectedValue("Bad", false);
                f.button("Load").doClick();
            });
            f.runTasks();
            assertEquals(before, f.saved.values);
            SwingUtilities.invokeAndWait(() -> {
                assertTrue(f.text().contains("Invalid saved value"));
                assertFalse(f.text().contains("Settings applied immediately"));
                assertTrue(f.button("Load").isEnabled());
            });
        }
        finally { f.close(); }
    }

    @Test
    public void listHasNoPlaceholderProfileAndActionButtonsFitBelowIt() throws Exception
    {
        Fixture f = new Fixture();
        try
        {
            SwingUtilities.invokeAndWait(() -> {
                f.panel.refresh(List.of(), "", null);
                assertEquals(0, f.list().getModel().getSize());
                assertFalse(f.button("Load").isEnabled());
                assertFalse(f.button("Delete").isEnabled());
                assertTrue(f.button("Save").isEnabled());
                f.panel.refresh(List.of("One", "Two", "Three"), "One", null);
                f.panel.setSize(242, 550); layout(f.panel);
                JScrollPane scroll = find(f.panel, JScrollPane.class, null);
                assertEquals(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER, scroll.getHorizontalScrollBarPolicy());
                assertNull("the empty-state header must reserve no space above saved profiles", scroll.getColumnHeader());
                java.awt.Point firstRow = SwingUtilities.convertPoint(f.list(), 0, f.list().getCellBounds(0, 0).y, scroll);
                assertEquals("first profile starts directly inside the list's top border", scroll.getInsets().top, firstRow.y);
                java.awt.Point listOrigin = SwingUtilities.convertPoint(scroll, 0, 0, f.panel);
                for (String name : List.of("Load", "Save", "Delete"))
                {
                    JButton button = f.button(name);
                    java.awt.Point point = SwingUtilities.convertPoint(button, 0, 0, f.panel);
                    assertTrue(point.y >= listOrigin.y + scroll.getHeight());
                    assertTrue(point.x >= 0 && point.x + button.getWidth() <= f.panel.getWidth());
                    assertTrue(button.getWidth() >= 60);
                }
                f.panel.refresh(List.of(), "", null);
                layout(f.panel);
                assertNotNull("deleting the last profile restores the empty-state message", scroll.getColumnHeader());
                assertTrue(scroll.getColumnHeader().getHeight() > 0);
                f.panel.refresh(List.of("One", "Two", "Three"), "", null);
                layout(f.panel);
                assertNull("repopulating the list removes the empty-state space again", scroll.getColumnHeader());
            });
        }
        finally { f.close(); }
    }

    @Test
    public void bundledRowsLoadOnlyOnRequestAndSavedOverridesCanBeDeleted() throws Exception
    {
        Fixture f = new Fixture(true);
        try
        {
            SwingUtilities.invokeAndWait(() -> {
                f.panel.refresh(f.saved.profiles.names(), "", null, f.saved.profiles.savedNames());
                f.list().setSelectedValue("True Tile", false);
                assertTrue(f.saved.values.isEmpty());
                assertTrue(f.button("Load").isEnabled());
                assertFalse(f.button("Delete").isEnabled());
                f.button("Load").doClick();
                assertTrue(f.saved.values.isEmpty());
            });
            f.runTasks();
            assertEquals("True Tile", f.saved.profiles.selected());
            assertEquals("49.800000000000004", f.saved.values.get("arrivalFinalSpeed"));
            f.dialogs.saveName = "True Tile";
            SwingUtilities.invokeAndWait(() -> f.button("Save").doClick());
            f.runTasks();
            SwingUtilities.invokeAndWait(() -> assertTrue(f.button("Delete").isEnabled()));
            f.dialogs.confirmDelete = true;
            SwingUtilities.invokeAndWait(() -> f.button("Delete").doClick());
            f.runTasks();
            assertEquals(List.of("Default", "True Tile"), f.saved.profiles.names());
            assertTrue(f.saved.profiles.savedNames().isEmpty());
            SwingUtilities.invokeAndWait(() -> {
                assertEquals("True Tile", f.list().getSelectedValue());
                assertTrue(f.button("Load").isEnabled());
                assertFalse(f.button("Delete").isEnabled());
            });
        }
        finally { f.close(); }
    }

    private static void layout(Container parent)
    {
        parent.doLayout();
        for (Component child : parent.getComponents()) { if (child instanceof Container) { layout((Container) child); } }
    }

    private static <T extends Component> T find(Container parent, Class<T> type, String title)
    {
        for (Component child : parent.getComponents())
        {
            if (type.isInstance(child) && (title == null || title.equals(((JButton) child).getText()))) { return type.cast(child); }
            if (child instanceof Container)
            {
                T found = find((Container) child, type, title);
                if (found != null) { return found; }
            }
        }
        return null;
    }

    private static final class Fixture
    {
        final MovementProfilesTest.Fixture saved;
        final List<Runnable> tasks = new ArrayList<>();
        final AtomicLong profileId = new AtomicLong(1);
        final FakeDialogs dialogs = new FakeDialogs();
        final MovementProfilesSidebar sidebar;
        MovementProfilesPanel panel;

        Fixture() throws Exception { this(false); }

        Fixture(boolean bundled) throws Exception
        {
            saved = new MovementProfilesTest.Fixture(bundled);
            sidebar = new MovementProfilesSidebar(saved.profiles, tasks::add, null, profileId::get);
            SwingUtilities.invokeAndWait(() -> panel = new MovementProfilesPanel(sidebar::apply, dialogs));
            // Test-only wiring; production creates the same view through start() on EDT.
            Field view = MovementProfilesSidebar.class.getDeclaredField("panel");
            view.setAccessible(true); view.set(sidebar, panel);
        }

        JButton button(String title) { return find(panel, JButton.class, title); }
        JList<?> list() { return find(panel, JList.class, null); }
        String text()
        {
            return text(panel);
        }
        private String text(Container parent)
        {
            StringBuilder result = new StringBuilder();
            for (Component child : parent.getComponents())
            {
                if (child instanceof JTextArea) { result.append(((JTextArea) child).getText()).append('\n'); }
                else if (child instanceof Container) { result.append(text((Container) child)); }
            }
            return result.toString();
        }
        void runTasks() throws Exception
        {
            List<Runnable> pending = new ArrayList<>(tasks); tasks.clear(); pending.forEach(Runnable::run);
            SwingUtilities.invokeAndWait(() -> { });
        }
        void close() throws Exception { sidebar.close(); SwingUtilities.invokeAndWait(() -> { }); }
    }

    private static final class FakeDialogs implements MovementProfilesPanel.Dialogs
    {
        String prefilled, saveName, deleteName;
        int confirmations;
        boolean confirmDelete;
        Runnable duringDialog = () -> { };
        @Override public String save(Component owner, String loadedName)
        {
            prefilled = loadedName; duringDialog.run(); return saveName;
        }
        @Override public boolean delete(Component owner, String name)
        {
            deleteName = name; ++confirmations; duringDialog.run(); return confirmDelete;
        }
        @Override public void close() { }
    }
}
