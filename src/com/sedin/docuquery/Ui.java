package com.sedin.docuquery;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;

/** Small Swing helpers shared by the panels. */
final class Ui {


    private Ui() {}

    /** Generated toolbar icon: a coloured rounded tile with a short label. */
    static Icon icon(String text, Color color, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(color);
        g.fillRoundRect(1, 1, size - 2, size - 2, 7, 7);
        g.setColor(Color.WHITE);
        int c = size / 2;
        if (text.equals("run")) {
            g.fillPolygon(new int[]{c - 5, c - 5, c + 7}, new int[]{c - 7, c + 7, c}, 3);
            g.dispose();
            return new ImageIcon(img);
        }
        if (text.equals("stop")) {
            g.fillRect(c - 6, c - 6, 12, 12);
            g.dispose();
            return new ImageIcon(img);
        }
        if (text.equals("star")) {
            int[] xs = new int[10];
            int[] ys = new int[10];
            for (int i = 0; i < 10; i++) {
                double r = i % 2 == 0 ? size * 0.38 : size * 0.16;
                double a = Math.PI / 2 + i * Math.PI / 5;
                xs[i] = (int) Math.round(c + r * Math.cos(a));
                ys[i] = (int) Math.round(c + 1 - r * Math.sin(a));
            }
            g.fillPolygon(xs, ys, 10);
            g.dispose();
            return new ImageIcon(img);
        }
        int fs = text.length() <= 1 ? size - 10 : text.length() == 2 ? size - 13 : size - 15;
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(8, fs)));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (size - fm.stringWidth(text)) / 2, (size - fm.getHeight()) / 2 + fm.getAscent());
        g.dispose();
        return new ImageIcon(img);
    }

    static JButton toolButton(String label, Icon icon, String tip, ActionListener a) {
        JButton b = new JButton(label, icon);
        b.setToolTipText(tip);
        b.setVerticalTextPosition(SwingConstants.BOTTOM);
        b.setHorizontalTextPosition(SwingConstants.CENTER);
        b.setFocusable(false);
        b.setMargin(new Insets(2, 6, 2, 6));
        b.addActionListener(a);
        return b;
    }

    static JButton smallButton(String label, String tip, ActionListener a) {
        JButton b = new JButton(label);
        b.setToolTipText(tip);
        b.setFocusable(false);
        b.setMargin(new Insets(2, 8, 2, 8));
        b.addActionListener(a);
        return b;
    }

    static JMenuItem item(String label, ActionListener a) {
        JMenuItem m = new JMenuItem(label);
        m.addActionListener(a);
        return m;
    }

    static JMenuItem item(String label, KeyStroke ks, ActionListener a) {
        JMenuItem m = item(label, a);
        m.setAccelerator(ks);
        return m;
    }

    static KeyStroke ctrl(int key) {
        return KeyStroke.getKeyStroke(key, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
    }

    static KeyStroke key(int key) {
        return KeyStroke.getKeyStroke(key, 0);
    }

    static void bind(JComponent c, KeyStroke ks, String name, Runnable r) {
        c.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ks, name);
        c.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                r.run();
            }
        });
    }

    /** Tab header with a title and a small close (x) button. */
    static void addClosableTab(JTabbedPane tabs, String title, Component c, Runnable onClose) {
        tabs.addTab(title, c);
        int idx = tabs.indexOfComponent(c);
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.setOpaque(false);
        JLabel l = new JLabel(title);
        JButton x = new JButton("×");
        x.setMargin(new Insets(0, 3, 0, 3));
        x.setBorderPainted(false);
        x.setContentAreaFilled(false);
        x.setFocusable(false);
        x.setToolTipText("Close");
        x.setForeground(new Color(0xC0392B));
        x.addActionListener(e -> {
            int i = tabs.indexOfComponent(c);
            if (i >= 0) {
                if (onClose != null) onClose.run();
                tabs.remove(i);
            }
        });
        p.add(l);
        p.add(x);
        p.putClientProperty("title", l);
        tabs.setTabComponentAt(idx, p);
        tabs.setSelectedIndex(idx);
    }

    static void setTabTitle(JTabbedPane tabs, Component c, String title) {
        int i = tabs.indexOfComponent(c);
        if (i < 0) return;
        tabs.setTitleAt(i, title);
        Component tc = tabs.getTabComponentAt(i);
        if (tc instanceof JComponent) {
            Object l = ((JComponent) tc).getClientProperty("title");
            if (l instanceof JLabel) ((JLabel) l).setText(title);
        }
    }

    static void error(Component parent, String title, Throwable t) {
        error(parent, title, DocConnection.message(t));
    }

    static void error(Component parent, String title, String msg) {
        JTextArea ta = new JTextArea(msg, Math.min(15, Math.max(3, msg.split("\n").length + 1)), 70);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setEditable(false);
        ta.setFont(Theme.mono);
        JOptionPane.showMessageDialog(parent, new JScrollPane(ta), title, JOptionPane.ERROR_MESSAGE);
    }

    /** Non-modal window showing plain text (e.g. dump output). */
    static void showText(Component parent, String title, String text) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog d = new JDialog(owner, title, Dialog.ModalityType.MODELESS);
        JTextArea ta = new JTextArea(text);
        ta.setFont(Theme.mono);
        ta.setEditable(false);
        ta.setCaretPosition(0);
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(smallButton("Copy", "Copy to clipboard", e -> copy(text)));
        south.add(smallButton("Close", null, e -> d.dispose()));
        d.add(new JScrollPane(ta));
        d.add(south, BorderLayout.SOUTH);
        d.setSize(760, 620);
        d.setLocationRelativeTo(parent);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(), key(KeyEvent.VK_ESCAPE),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setVisible(true);
    }

    static void copy(String s) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(s), null);
    }

    static JFileChooser chooser(String key) {
        JFileChooser fc = new JFileChooser(Prefs.get("dir." + key, System.getProperty("user.home")));
        return fc;
    }

    static void remember(String key, File f) {
        File dir = f.isDirectory() ? f : f.getParentFile();
        if (dir != null) Prefs.put("dir." + key, dir.getAbsolutePath());
    }

    static JPanel padded(Component c) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(4, 4, 4, 4));
        p.add(c);
        return p;
    }
}
