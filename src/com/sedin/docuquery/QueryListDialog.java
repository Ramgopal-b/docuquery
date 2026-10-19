package com.sedin.docuquery;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** History and Favourites browser. */
final class QueryListDialog extends JDialog {

    private final boolean favorites;
    private final SessionPanel ctx;
    private final DefaultListModel<String[]> model = new DefaultListModel<>();
    private final JList<String[]> list = new JList<>(model);
    private final JTextArea preview = new JTextArea();
    private final JTextField filter = new JTextField();
    private List<String[]> all = new ArrayList<>();

    static void history(SessionPanel ctx) {
        new QueryListDialog(ctx, false).setVisible(true);
    }

    static void favorites(SessionPanel ctx) {
        new QueryListDialog(ctx, true).setVisible(true);
    }

    private QueryListDialog(SessionPanel ctx, boolean favorites) {
        super(SwingUtilities.getWindowAncestor(ctx), favorites ? "Favorites" : "Query history", ModalityType.MODELESS);
        this.ctx = ctx;
        this.favorites = favorites;

        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                String[] e = (String[]) v;
                String label = favorites ? e[0] : e[1].replaceAll("\\s+", " ");
                if (label.length() > 140) label = label.substring(0, 140) + "…";
                return super.getListCellRendererComponent(l, label, i, s, f);
            }
        });
        list.setFont(Theme.mono);
        preview.setFont(Theme.mono);
        preview.setEditable(false);
        preview.setLineWrap(true);
        list.addListSelectionListener(e -> {
            String[] v = list.getSelectedValue();
            preview.setText(v == null ? "" : v[1]);
            preview.setCaretPosition(0);
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) use(false);
            }
        });

        JPanel top = new JPanel(new BorderLayout(4, 0));
        top.setBorder(new EmptyBorder(6, 6, 6, 6));
        top.add(new JLabel("Search: "), BorderLayout.WEST);
        top.add(filter);
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refill();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refill();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refill();
            }
        });

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(list), new JScrollPane(preview));
        split.setResizeWeight(0.6);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(Ui.smallButton("Load", "Load into the DQL editor", e -> use(false)));
        buttons.add(Ui.smallButton("Load & Run", "Load and execute", e -> use(true)));
        buttons.add(Ui.smallButton("Delete", "Remove the selected entry", e -> delete()));
        if (!favorites) buttons.add(Ui.smallButton("Clear all", "Clear the whole history", e -> clearAll()));
        buttons.add(Ui.smallButton("Close", null, e -> dispose()));

        add(top, BorderLayout.NORTH);
        add(split);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().registerKeyboardAction(e -> dispose(), Ui.key(KeyEvent.VK_ESCAPE),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        reload();
        setSize(760, 560);
        setLocationRelativeTo(ctx);
        split.setDividerLocation(300);
    }

    private void reload() {
        all = new ArrayList<>();
        if (favorites) {
            for (Map.Entry<String, String> e : Prefs.favorites().entrySet()) all.add(new String[]{e.getKey(), e.getValue()});
        } else {
            for (String q : Prefs.history()) all.add(new String[]{"", q});
        }
        refill();
    }

    private void refill() {
        String f = filter.getText().trim().toLowerCase(Locale.ROOT);
        model.clear();
        for (String[] e : all) {
            if (f.isEmpty() || e[0].toLowerCase(Locale.ROOT).contains(f) || e[1].toLowerCase(Locale.ROOT).contains(f)) {
                model.addElement(e);
            }
        }
        if (!model.isEmpty()) list.setSelectedIndex(0);
    }

    private void use(boolean run) {
        String[] v = list.getSelectedValue();
        if (v == null) return;
        ctx.openDql(v[1], run);
        dispose();
    }

    private void delete() {
        String[] v = list.getSelectedValue();
        if (v == null) return;
        if (favorites) {
            Prefs.removeFavorite(v[0]);
        } else {
            List<String> h = Prefs.history();
            h.remove(v[1]);
            Prefs.writeHistory(h);
        }
        reload();
    }

    private void clearAll() {
        if (JOptionPane.showConfirmDialog(this, "Clear the whole query history?", "History",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        Prefs.writeHistory(new ArrayList<>());
        reload();
    }
}
