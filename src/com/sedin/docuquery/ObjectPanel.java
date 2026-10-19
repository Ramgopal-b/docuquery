package com.sedin.docuquery;

import com.documentum.fc.client.IDfPersistentObject;
import com.documentum.fc.client.IDfSysObject;
import com.documentum.fc.client.IDfTypedObject;
import com.documentum.fc.common.DfId;
import com.documentum.fc.common.DfTime;
import com.documentum.fc.common.IDfAttr;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Shows all attributes of one object; lets you edit and save them and work with content. */
final class ObjectPanel extends JPanel {

    static final class Attr {
        String name;
        int type;
        boolean rep;
        List<String> values = new ArrayList<>();
        boolean dirty;

        String display() {
            if (!rep) return values.isEmpty() ? "" : values.get(0);
            return "[" + values.size() + "] " + String.join("; ", values);
        }

        boolean readOnly() {
            return name.startsWith("r_") || name.startsWith("i_");
        }
    }

    static final class Loaded {
        List<Attr> attrs = new ArrayList<>();
        String type = "";
        String name = "";
        boolean sys;
        String lockOwner = "";
    }

    final String id;
    private final SessionPanel ctx;
    private final AttrModel model = new AttrModel();
    private final JTable table = new JTable(model);
    private final TableRowSorter<AttrModel> sorter = new TableRowSorter<>(model);
    private final JLabel header = new JLabel(" ");
    private final JTextField filter = new JTextField(16);
    private final JButton saveBtn;
    private final List<JButton> contentButtons = new ArrayList<>();
    private Loaded data;

    ObjectPanel(SessionPanel ctx, String id) {
        super(new BorderLayout());
        this.ctx = ctx;
        this.id = id;

        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.setBorder(new EmptyBorder(2, 4, 2, 4));
        tb.add(Ui.smallButton("Refresh", "Reload attributes", e -> load()));
        tb.add(Box.createHorizontalStrut(4));
        saveBtn = Ui.smallButton("Save", "Save changed attributes", e -> save());
        saveBtn.setEnabled(false);
        tb.add(saveBtn);
        tb.add(Box.createHorizontalStrut(4));
        tb.add(Ui.smallButton("Dump", "Show the API dump", e -> Ops.dump(ctx, id)));
        tb.addSeparator();
        contentButtons.add(Ui.smallButton("Export content", "Save the content to a local file", e -> Ops.exportContent(ctx, id)));
        contentButtons.add(Ui.smallButton("Check out", "Lock the object", e -> Ops.checkout(ctx, id, this::load)));
        contentButtons.add(Ui.smallButton("Check in...", "Check in a new version from a local file", e -> Ops.checkin(ctx, id)));
        contentButtons.add(Ui.smallButton("Cancel checkout", "Release the lock", e -> Ops.cancelCheckout(ctx, id, this::load)));
        for (JButton b : contentButtons) {
            tb.add(b);
            tb.add(Box.createHorizontalStrut(4));
        }
        tb.addSeparator();
        tb.add(Ui.smallButton("DQL", "Open a DQL query for this object", e ->
                ctx.openDql("select * from " + (data == null || data.type.isEmpty() ? "dm_sysobject" : data.type)
                        + " where r_object_id = '" + id + "'", true)));
        tb.add(Box.createHorizontalStrut(4));
        JButton destroy = Ui.smallButton("Destroy", "Permanently delete this object", e ->
                Ops.destroy(ctx, id, data == null ? id : data.name, () -> ctx.closeTab(this)));
        destroy.setForeground(new Color(0xB00020));
        tb.add(destroy);
        tb.add(Box.createHorizontalGlue());
        tb.add(new JLabel("Filter: "));
        filter.setMaximumSize(new Dimension(180, 26));
        tb.add(filter);

        header.setBorder(new EmptyBorder(6, 8, 6, 8));
        header.setFont(header.getFont().deriveFont(Font.BOLD, header.getFont().getSize() + 1f));
        JPanel north = new JPanel(new BorderLayout());
        north.add(tb, BorderLayout.NORTH);
        north.add(header, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        table.setRowSorter(sorter);
        table.setRowHeight(Math.max(20, table.getRowHeight() + 2));
        table.setGridColor(new Color(0xE3E3E3));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.getColumnModel().getColumn(0).setPreferredWidth(220);
        table.getColumnModel().getColumn(1).setPreferredWidth(80);
        table.getColumnModel().getColumn(2).setPreferredWidth(40);
        table.getColumnModel().getColumn(3).setPreferredWidth(600);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean f, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, sel, f, r, c);
                Attr a = model.rows.get(t.convertRowIndexToModel(r));
                comp.setFont(comp.getFont().deriveFont(a.dirty ? Font.BOLD : Font.PLAIN));
                if (!sel) comp.setForeground(a.dirty ? new Color(0x0033B3) : a.readOnly() ? new Color(0x777777) : Color.BLACK);
                return comp;
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int r = table.rowAtPoint(e.getPoint());
                if (r < 0) return;
                Attr a = model.rows.get(table.convertRowIndexToModel(r));
                if (e.getClickCount() == 2 && a.rep) editRepeating(a);
                else if (e.getClickCount() == 2 && a.readOnly() && a.type == Dfc.DM_ID
                        && QueryResult.isId(a.display())) ctx.openObject(a.display());
            }

            @Override
            public void mousePressed(MouseEvent e) {
                popup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                popup(e);
            }
        });
        add(new JScrollPane(table));

        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });
        load();
    }

    private void applyFilter() {
        String t = filter.getText().trim();
        sorter.setRowFilter(t.isEmpty() ? null : RowFilter.regexFilter("(?i)" + Pattern.quote(t), 0, 3));
    }

    private void popup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        int r = table.rowAtPoint(e.getPoint());
        if (r < 0) return;
        table.setRowSelectionInterval(r, r);
        Attr a = model.rows.get(table.convertRowIndexToModel(r));
        JPopupMenu m = new JPopupMenu();
        m.add(Ui.item("Copy value", ev -> Ui.copy(a.rep ? String.join("\n", a.values) : a.display())));
        m.add(Ui.item("Copy name", ev -> Ui.copy(a.name)));
        if (a.rep) m.add(Ui.item("Edit values...", ev -> editRepeating(a)));
        for (String v : a.values) {
            if (QueryResult.isId(v)) {
                m.add(Ui.item("Open " + v, ev -> ctx.openObject(v)));
                if (m.getComponentCount() > 12) break;
            }
        }
        m.show(table, e.getX(), e.getY());
    }

    void load() {
        header.setText("Loading " + id + "...");
        ctx.conn().run(s -> {
            IDfPersistentObject o = s.getObject(new DfId(id));
            Loaded l = new Loaded();
            int n = o.getAttrCount();
            for (int i = 0; i < n; i++) {
                IDfAttr ia = o.getAttr(i);
                Attr a = new Attr();
                a.name = ia.getName();
                a.type = ia.getDataType();
                a.rep = ia.isRepeating();
                if (a.rep) {
                    int c = o.getValueCount(a.name);
                    for (int j = 0; j < c; j++) a.values.add(o.getRepeatingString(a.name, j));
                } else {
                    a.values.add(o.getString(a.name));
                }
                l.attrs.add(a);
            }
            l.type = o.getType().getName();
            l.sys = o instanceof IDfSysObject;
            if (l.sys) {
                IDfSysObject so = (IDfSysObject) o;
                l.name = so.getObjectName();
                l.lockOwner = so.getLockOwner();
            } else {
                for (String key : new String[]{"object_name", "user_name", "group_name", "name"}) {
                    for (Attr a : l.attrs) if (a.name.equals(key) && !a.values.isEmpty()) l.name = a.values.get(0);
                    if (!l.name.isEmpty()) break;
                }
            }
            return l;
        }, l -> {
            data = l;
            model.set(l.attrs);
            saveBtn.setEnabled(false);
            for (JButton b : contentButtons) b.setEnabled(l.sys);
            String lock = l.lockOwner == null || l.lockOwner.isEmpty() ? "" : "   [checked out by " + l.lockOwner + "]";
            header.setText(l.name + "   (" + l.type + ", " + id + ")" + lock);
            ctx.setTabTitle(this, l.name == null || l.name.isEmpty() ? id : l.name);
            ctx.status(l.attrs.size() + " attributes loaded for " + id);
        }, t -> {
            header.setText("Could not load " + id);
            Ui.error(this, "Open object failed", t);
        });
    }

    private void editRepeating(Attr a) {
        JTextArea ta = new JTextArea(String.join("\n", a.values), 14, 50);
        ta.setFont(Theme.mono);
        ta.setEditable(!a.readOnly());
        int r = JOptionPane.showConfirmDialog(this, new Object[]{
                a.name + " (" + QueryResult.typeName(a.type) + ", repeating) – one value per line:", new JScrollPane(ta)},
                "Edit " + a.name, a.readOnly() ? JOptionPane.DEFAULT_OPTION : JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION || a.readOnly()) return;
        List<String> vals = new ArrayList<>();
        for (String v : ta.getText().split("\\r?\\n")) if (!v.isEmpty()) vals.add(v);
        if (!vals.equals(a.values)) {
            a.values = vals;
            a.dirty = true;
            model.fireTableDataChanged();
            saveBtn.setEnabled(true);
        }
    }

    private void save() {
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
        List<Attr> dirty = new ArrayList<>();
        for (Attr a : model.rows) if (a.dirty) dirty.add(a);
        if (dirty.isEmpty()) return;
        ctx.status("Saving " + id + "...");
        ctx.conn().run(s -> {
            IDfPersistentObject o = s.getObject(new DfId(id));
            for (Attr a : dirty) {
                if (a.rep) {
                    o.removeAll(a.name);
                    for (String v : a.values) append(o, a.name, a.type, v);
                } else {
                    set(o, a.name, a.type, a.values.isEmpty() ? "" : a.values.get(0));
                }
            }
            o.save();
            return dirty.size();
        }, n -> {
            ctx.status("Saved " + n + " attribute(s) on " + id);
            load();
        }, t -> Ui.error(this, "Save failed", t));
    }

    static boolean bool(String v) {
        String s = v.trim().toLowerCase(Locale.ROOT);
        return s.equals("true") || s.equals("t") || s.equals("1") || s.equals("yes") || s.equals("y");
    }

    static void set(IDfTypedObject o, String n, int t, String v) throws Exception {
        String s = v.trim();
        if (t == Dfc.DM_BOOLEAN) o.setBoolean(n, bool(s));
        else if (t == Dfc.DM_INTEGER) o.setInt(n, s.isEmpty() ? 0 : Integer.parseInt(s));
        else if (t == Dfc.DM_DOUBLE) o.setDouble(n, s.isEmpty() ? 0 : Double.parseDouble(s));
        else if (t == Dfc.DM_TIME) o.setTime(n, s.isEmpty() || s.equalsIgnoreCase("nulldate") ? DfTime.DF_NULLDATE : new DfTime(s));
        else if (t == Dfc.DM_ID) o.setId(n, new DfId(s.isEmpty() ? "0000000000000000" : s));
        else o.setString(n, v);
    }

    static void append(IDfTypedObject o, String n, int t, String v) throws Exception {
        String s = v.trim();
        if (t == Dfc.DM_BOOLEAN) o.appendBoolean(n, bool(s));
        else if (t == Dfc.DM_INTEGER) o.appendInt(n, Integer.parseInt(s));
        else if (t == Dfc.DM_DOUBLE) o.appendDouble(n, Double.parseDouble(s));
        else if (t == Dfc.DM_TIME) o.appendTime(n, new DfTime(s));
        else if (t == Dfc.DM_ID) o.appendId(n, new DfId(s));
        else o.appendString(n, v);
    }

    private final class AttrModel extends AbstractTableModel {
        final List<Attr> rows = new ArrayList<>();
        private final String[] cols = {"Attribute", "Type", "Rep.", "Value"};

        void set(List<Attr> l) {
            rows.clear();
            rows.addAll(l);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return cols.length;
        }

        @Override
        public String getColumnName(int c) {
            return cols[c];
        }

        @Override
        public Object getValueAt(int r, int c) {
            Attr a = rows.get(r);
            switch (c) {
                case 0: return a.name;
                case 1: return QueryResult.typeName(a.type);
                case 2: return a.rep ? "R" : "";
                default: return a.display();
            }
        }

        @Override
        public boolean isCellEditable(int r, int c) {
            Attr a = rows.get(r);
            return c == 3 && !a.rep && !a.readOnly();
        }

        @Override
        public void setValueAt(Object v, int r, int c) {
            Attr a = rows.get(r);
            String nv = v == null ? "" : v.toString();
            if (!a.values.isEmpty() && a.values.get(0).equals(nv)) return;
            a.values.clear();
            a.values.add(nv);
            a.dirty = true;
            saveBtn.setEnabled(true);
            fireTableRowsUpdated(r, r);
        }
    }
}
