package com.sedin.docuquery;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

/** Colours and fonts for the flat, dqMan-like look. */
final class Theme {
    static final Color TOOLBAR_BG = new Color(0xF3F3F3);
    static final Color PANEL_BG = new Color(0xF0F0F0);
    static final Color BORDER = new Color(0xD5D5D5);
    static final Color TAB_SELECTED = new Color(0x5F6368);
    static final Color TAB_HOVER = new Color(0xE2E2E2);
    static final Color TOGGLE_BG = new Color(0xD9D9D9);
    static final Color ACCENT = new Color(0x0078D7);
    static final Color CLOSE_RED = new Color(0xE81123);
    static final Color TEXT = new Color(0x1E1E1E);
    static final Color ZEBRA = new Color(0xF5F5F5);

    static Font ui = new Font(Font.DIALOG, Font.PLAIN, 12);
    static Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 13);

    private Theme() {}

    static void install() {
        Set<String> fonts = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        if (fonts.contains("Segoe UI")) ui = new Font("Segoe UI", Font.PLAIN, 12);
        if (fonts.contains("Consolas")) mono = new Font("Consolas", Font.PLAIN, 13);
        else if (fonts.contains("DejaVu Sans Mono")) mono = new Font("DejaVu Sans Mono", Font.PLAIN, 12);

        FontUIResource f = new FontUIResource(ui);
        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object k = keys.nextElement();
            if (UIManager.get(k) instanceof FontUIResource) UIManager.put(k, f);
        }
        UIManager.put("Tree.paintLines", Boolean.FALSE);
        UIManager.put("Tree.lineTypeDashed", Boolean.FALSE);
        UIManager.put("Tree.collapsedIcon", Icons.chevron(false));
        UIManager.put("Tree.expandedIcon", Icons.chevron(true));
        UIManager.put("Tree.rowHeight", 19);
        UIManager.put("SplitPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("SplitPaneDivider.border", BorderFactory.createEmptyBorder());
        UIManager.put("ToolTip.font", f);
    }

    /** Thin grey border used around editors, grids and trees. */
    static javax.swing.border.Border box() {
        return BorderFactory.createLineBorder(BORDER);
    }
}
