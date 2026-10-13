package com.responsivemovement;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/** EDT-owned profile manager only: selecting a row never changes game or plugin settings. */
final class MovementProfilesPanel extends PluginPanel
{
    static final String LOAD_NOTICE = "Settings applied immediately. You may need to reopen the plugin settings " +
        "to refresh the displayed values. No game restart is needed.";
    enum Action { LOAD, SAVE, DELETE }
    static final class Request
    {
        final Action action;
        final String name;
        Request(Action action, String name) { this.action = action; this.name = name; }
    }

    interface Dialogs
    {
        String save(Component owner, String loadedName);
        boolean delete(Component owner, String name);
        void close();
    }

    private final Consumer<Request> submit;
    private final Dialogs dialogs;
    private final DefaultListModel<String> names = new DefaultListModel<>();
    private final JList<String> profiles = new JList<>(names);
    private final JScrollPane list;
    private final JLabel count = new JLabel("0");
    private final JTextArea empty = text("No saved profiles yet.\n\nClick Save to create your first profile.");
    private final JTextArea current = text("No profile loaded.");
    private final JTextArea status = text("Select a profile, then click Load.");
    private final JButton load = button("Load"), save = button("Save"), delete = button("Delete");
    private String loadedName = "";
    private Set<String> deletableNames = new HashSet<>();
    private boolean busy, closed;
    private int dialogGeneration;

    MovementProfilesPanel(Consumer<Request> submit)
    {
        this(submit, new ProfileDialogs());
    }

    MovementProfilesPanel(Consumer<Request> submit, Dialogs dialogs)
    {
        super(false);
        this.submit = submit; this.dialogs = dialogs;
        setLayout(new BorderLayout(0, 12));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(BorderFactory.createEmptyBorder(12, 10, 12, 10));

        JPanel heading = panel(new BorderLayout(0, 5));
        JLabel title = new JLabel("Movement Profiles");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setForeground(ColorScheme.BRAND_ORANGE);
        heading.add(title, BorderLayout.NORTH);
        heading.add(text("Saved settings for Responsive Movement"), BorderLayout.CENTER);
        add(heading, BorderLayout.NORTH);

        JPanel section = panel(new BorderLayout(0, 8));
        JPanel sectionHeader = panel(new BorderLayout());
        JLabel caption = new JLabel("PROFILES");
        caption.setFont(caption.getFont().deriveFont(Font.BOLD, 11f));
        caption.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        count.setForeground(ColorScheme.BRAND_ORANGE);
        sectionHeader.add(caption, BorderLayout.WEST); sectionHeader.add(count, BorderLayout.EAST);
        section.add(sectionHeader, BorderLayout.NORTH);

        profiles.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        profiles.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        profiles.setForeground(Color.WHITE);
        profiles.setSelectionBackground(new Color(74, 57, 38));
        profiles.setSelectionForeground(ColorScheme.BRAND_ORANGE);
        profiles.setFixedCellHeight(36);
        profiles.setFont(profiles.getFont().deriveFont(13f));
        DefaultListCellRenderer renderer = new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean selected, boolean focus)
            {
                Component row = super.getListCellRendererComponent(list, value, index, selected, focus);
                setBorder(BorderFactory.createEmptyBorder(5, 9, 5, 9));
                return row;
            }
        };
        renderer.putClientProperty("html.disable", true);
        profiles.setCellRenderer(renderer);
        profiles.addListSelectionListener(event -> { if (!event.getValueIsAdjusting()) { updateButtons(); } });
        list = new JScrollPane(profiles);
        list.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        list.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
        list.setPreferredSize(new Dimension(200, 260));
        // A non-selectable empty-state message, rather than a fake profile row.
        list.setColumnHeaderView(empty);
        list.getColumnHeader().setBackground(ColorScheme.DARKER_GRAY_COLOR);
        empty.setBorder(BorderFactory.createEmptyBorder(14, 10, 14, 10));
        section.add(list, BorderLayout.CENTER);
        add(section, BorderLayout.CENTER);

        JPanel footer = panel(new BorderLayout(0, 9));
        JPanel feedback = panel(new BorderLayout(0, 7));
        current.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        feedback.add(current, BorderLayout.NORTH); feedback.add(status, BorderLayout.CENTER);
        footer.add(feedback, BorderLayout.CENTER);
        JPanel buttons = panel(new GridLayout(1, 3, 5, 0));
        delete.setForeground(new Color(232, 137, 137));
        buttons.add(load); buttons.add(save); buttons.add(delete);
        footer.add(buttons, BorderLayout.SOUTH);
        footer.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, ColorScheme.MEDIUM_GRAY_COLOR),
            BorderFactory.createEmptyBorder(10, 0, 0, 0)));
        add(footer, BorderLayout.SOUTH);

        load.setToolTipText("Apply the selected profile now");
        save.setToolTipText("Save your current plugin settings");
        delete.setToolTipText("Delete the selected saved profile after confirmation");
        load.addActionListener(event -> load());
        save.addActionListener(event -> save());
        delete.addActionListener(event -> delete());
        updateButtons();
    }

    void refresh(List<String> saved, String loaded, String preferredSelection)
    {
        refresh(saved, loaded, preferredSelection, saved);
    }

    void refresh(List<String> saved, String loaded, String preferredSelection, List<String> deletable)
    {
        if (closed) { return; }
        deletableNames = new HashSet<>(deletable);
        String previous = profiles.getSelectedValue();
        names.clear(); saved.forEach(names::addElement);
        loadedName = loaded;
        String selected = preferredSelection != null && saved.contains(preferredSelection) ? preferredSelection :
            previous != null && saved.contains(previous) ? previous : loaded;
        if (saved.contains(selected)) { profiles.setSelectedValue(selected, true); }
        // Hiding the text alone leaves its column-header viewport reserving
        // height. Remove that viewport when profiles exist so row zero is flush.
        if (saved.isEmpty())
        {
            list.setColumnHeaderView(empty);
            list.getColumnHeader().setBackground(ColorScheme.DARKER_GRAY_COLOR);
        }
        else { list.setColumnHeader(null); }
        count.setText(Integer.toString(saved.size()));
        current.setText(loaded.isEmpty() ? "No profile loaded." : "Last loaded/saved: " + loaded);
        updateButtons(); revalidate(); repaint();
    }

    private void load()
    {
        String selected = profiles.getSelectedValue();
        if (!closed && !busy && selected != null) { request(Action.LOAD, selected); }
    }

    private void save()
    {
        if (closed || busy) { return; }
        int generation = dialogGeneration;
        String name = dialogs.save(this, loadedName);
        if (name != null && !closed && generation == dialogGeneration) { request(Action.SAVE, name); }
    }

    private void delete()
    {
        String selected = profiles.getSelectedValue();
        if (closed || busy || selected == null || !deletableNames.contains(selected)) { return; }
        int generation = dialogGeneration;
        if (dialogs.delete(this, selected) && !closed && generation == dialogGeneration) { request(Action.DELETE, selected); }
    }

    private void request(Action action, String name)
    {
        busy = true; updateButtons();
        status.setText("Working…");
        submit.accept(new Request(action, name));
    }

    void complete(String message, boolean error)
    {
        if (closed) { return; }
        busy = false;
        status.setForeground(error ? new Color(232, 137, 137) : ColorScheme.LIGHT_GRAY_COLOR);
        status.setText(message); updateButtons(); revalidate();
    }

    void scopeChanged()
    {
        ++dialogGeneration; dialogs.close(); busy = false;
        profiles.clearSelection(); loadedName = "";
        complete("Select a profile, then click Load.", false);
    }

    void close()
    {
        closed = true; ++dialogGeneration; dialogs.close();
        updateButtons();
    }

    private void updateButtons()
    {
        boolean enabled = !closed && !busy;
        profiles.setEnabled(enabled);
        load.setEnabled(enabled && profiles.getSelectedValue() != null);
        save.setEnabled(enabled);
        delete.setEnabled(enabled && deletableNames.contains(profiles.getSelectedValue()));
    }

    private static JButton button(String title)
    {
        JButton button = new JButton(title);
        button.setFocusPainted(false);
        button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        button.setForeground(Color.WHITE);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 12f));
        button.setPreferredSize(new Dimension(64, 32));
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR), BorderFactory.createEmptyBorder(6, 3, 6, 3)));
        return button;
    }

    private static JPanel panel(java.awt.LayoutManager layout)
    {
        JPanel panel = new JPanel(layout); panel.setBackground(ColorScheme.DARK_GRAY_COLOR); return panel;
    }

    private static JTextArea text(String value)
    {
        JTextArea text = new JTextArea(value);
        text.setEditable(false); text.setFocusable(false); text.setOpaque(false);
        text.setLineWrap(true); text.setWrapStyleWord(true);
        text.setFont(text.getFont().deriveFont(12f)); text.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        return text;
    }

    @Override public Dimension getPreferredSize() { return new Dimension(PANEL_WIDTH + SCROLLBAR_WIDTH, 510); }

    private static final class ProfileDialogs implements Dialogs
    {
        private JDialog current;

        @Override public String save(Component owner, String loadedName)
        {
            JTextField name = new JTextField(loadedName, 20);
            JPanel form = new JPanel(); form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
            form.add(new JLabel("Profile name")); form.add(name);
            JOptionPane pane = new JOptionPane(form, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION,
                null, new String[] {"Save", "Cancel"}, "Save");
            JDialog dialog = pane.createDialog(owner, "Save movement profile");
            dialog.addWindowListener(new WindowAdapter() {
                @Override public void windowOpened(WindowEvent event) { name.requestFocusInWindow(); name.selectAll(); }
            });
            show(dialog);
            return "Save".equals(pane.getValue()) ? name.getText() : null;
        }

        @Override public boolean delete(Component owner, String name)
        {
            JTextArea message = text("Are you sure you want to delete “" + name + "”?\n\nYour current plugin settings will not change.");
            message.setColumns(26);
            JOptionPane pane = new JOptionPane(message, JOptionPane.WARNING_MESSAGE, JOptionPane.OK_CANCEL_OPTION,
                null, new String[] {"Delete", "Cancel"}, "Cancel");
            show(pane.createDialog(owner, "Delete movement profile?"));
            return "Delete".equals(pane.getValue());
        }

        private void show(JDialog dialog)
        {
            current = dialog; dialog.setVisible(true); dialog.dispose();
            if (current == dialog) { current = null; }
        }

        @Override public void close() { if (current != null) { current.dispose(); current = null; } }
    }
}
