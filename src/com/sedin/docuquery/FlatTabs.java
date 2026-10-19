package com.sedin.docuquery;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Flat tab strip like dqMan: the selected tab is a dark grey block with white text.
 * SESSION style shows an icon and a red close square; WINDOW style hides the strip
 * when only one window is open.
 */
final class FlatTabs extends JPanel {

    enum Style { SESSION, NAV, WINDOW }

    private final class Tab {
        Component comp;
        JPanel chip;
        JLabel label;
        String key;
    }

    private final Style style;
    private final JPanel strip = new JPanel();
    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final List<Tab> tabs = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private Tab selected;
    private int seq;

    FlatTabs(Style style) {
        super(new BorderLayout());
        this.style = style;
        strip.setLayout(new BoxLayout(strip, BoxLayout.X_AXIS));
        strip.setOpaque(true);
        strip.setBackground(style == Style.SESSION ? Theme.TOOLBAR_BG : Theme.PANEL_BG);
        strip.setBorder(style == Style.SESSION ? new EmptyBorder(2, 2, 0, 2)
                : style == Style.NAV ? new EmptyBorder(2, 2, 2, 2) : new EmptyBorder(2, 2, 0, 2));
        add(strip, BorderLayout.NORTH);
        add(body);
        updateStrip();
    }

    void onChange(Runnable r) {
        listeners.add(r);
    }

    void add(String title, Icon icon, Component c, boolean closable, Runnable onClose) {
        Tab t = new Tab();
        t.comp = c;
        t.key = "t" + (seq++);
        t.chip = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0)) {
            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };
        t.chip.setOpaque(true);
        boolean session = style == Style.SESSION;
        t.chip.setBorder(session ? new EmptyBorder(4, 6, 4, 4)
                : style == Style.NAV ? new EmptyBorder(4, 6, 4, 6) : new EmptyBorder(4, 8, 4, closable ? 4 : 8));
        ((FlowLayout) t.chip.getLayout()).setHgap(style == Style.NAV ? 0 : 5);
        if (icon != null) t.chip.add(new JLabel(icon));
        t.label = new JLabel(title);
        t.chip.add(t.label);
        if (closable) {
            JLabel x = new JLabel(session ? Icons.closeRed() : Icons.closeGrey());
            x.setToolTipText("Close");
            x.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            x.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (onClose != null) onClose.run();
                    removeTab(c);
                }
            });
            t.chip.add(x);
        }
        MouseAdapter sel = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                select(c);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                if (selected != t) t.chip.setBackground(Theme.TAB_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                paintChip(t);
            }
        };
        t.chip.addMouseListener(sel);
        t.label.addMouseListener(sel);
        tabs.add(t);
        strip.add(t.chip);
        body.add(c, t.key);
        select(c);
        updateStrip();
    }

    void select(Component c) {
        for (Tab t : tabs) {
            if (t.comp == c) {
                selected = t;
                if (body.getLayout() == cards) cards.show(body, t.key);
            }
        }
        for (Tab t : tabs) paintChip(t);
        for (Runnable r : listeners) r.run();
    }

    private void paintChip(Tab t) {
        boolean sel = t == selected;
        if (style == Style.SESSION) {
            t.chip.setBackground(sel ? Theme.TAB_SELECTED : new Color(0xC9C9C9));
            t.label.setForeground(sel ? Color.WHITE : Theme.TEXT);
        } else {
            t.chip.setBackground(sel ? Theme.TAB_SELECTED : strip.getBackground());
            t.label.setForeground(sel ? Color.WHITE : new Color(0x333333));
        }
    }

    /** Windows > Arrange: "cascade" = one at a time (tabs), "vertical" = side by side, "horizontal" = stacked. */
    void arrange(String mode) {
        if ("cascade".equals(mode)) {
            body.setLayout(cards);
            if (selected != null) cards.show(body, selected.key);
            for (Tab t : tabs) t.comp.setVisible(t == selected);
        } else {
            int n = Math.max(1, tabs.size());
            body.setLayout("vertical".equals(mode) ? new GridLayout(1, n, 4, 0) : new GridLayout(n, 1, 0, 4));
            for (Tab t : tabs) t.comp.setVisible(true);
        }
        body.revalidate();
        body.repaint();
    }

    Component selected() {
        return selected == null ? null : selected.comp;
    }

    int selectedIndex() {
        return selected == null ? -1 : tabs.indexOf(selected);
    }

    void removeTab(Component c) {
        Tab gone = null;
        for (Tab t : tabs) if (t.comp == c) gone = t;
        if (gone == null) return;
        int idx = tabs.indexOf(gone);
        tabs.remove(gone);
        strip.remove(gone.chip);
        body.remove(gone.comp);
        if (selected == gone) {
            selected = null;
            if (!tabs.isEmpty()) select(tabs.get(Math.max(0, idx - 1)).comp);
            else for (Runnable r : listeners) r.run();
        }
        updateStrip();
    }

    void setTitle(Component c, String title) {
        for (Tab t : tabs) if (t.comp == c) t.label.setText(title);
    }

    String title(Component c) {
        for (Tab t : tabs) if (t.comp == c) return t.label.getText();
        return "";
    }

    List<Component> components() {
        List<Component> l = new ArrayList<>();
        for (Tab t : tabs) l.add(t.comp);
        return l;
    }

    int count() {
        return tabs.size();
    }

    private void updateStrip() {
        strip.setVisible(style != Style.WINDOW || tabs.size() > 1);
        strip.revalidate();
        strip.repaint();
        revalidate();
        repaint();
    }
}
