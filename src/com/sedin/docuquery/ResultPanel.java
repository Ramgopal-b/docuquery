package com.sedin.docuquery;


import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Result grid in dqMan style: row numbers, a filter funnel in every column header,
 * sorting, copy/export, and in-place editing – double-click (or F2) a cell, type the
 * new value, press Enter and the attribute is saved to the repository immediately.
 */
final class ResultPanel extends JPanel {

    private static final Color ROWNUM_BG = new Color(0xF0F0F0);
    private static final Color EDITED_BG = new Color(0xE8F5E9);
    private static final int FUNNEL_W = 18;

    private final SessionPanel ctx;
    private final boolean editableGrid;
    private final JTable table = new JTable() {
        @Override
        public String getToolTipText(MouseEvent e) {
            int r = rowAtPoint(e.getPoint());
            int c = columnAtPoint(e.getPoint());
            if (r < 0 || c < 0) return null;
            Object v = getValueAt(r, c);
            String s = v == null ? "" : v.toString();
            return s.length() > 60 ? s : null;
        }
    };
    private final JTextField filter = new JTextField(16);
    private final JLabel info = new JLabel("Results");
    /** Extras > "Edit values in result grid" (global setting). */
    private final List<Object[]> generalActions = new ArrayList<>();
    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final JTextArea message = new JTextArea();
    private final List<Object[]> extraActions = new ArrayList<>();
    private final Map<Integer, String> colFilters = new HashMap<>(); // model column -> regex
    private final Set<String> editedCells = new HashSet<>();          // "row:col" saved in this result
    private DefaultTableModel model;
    private FilterSorter sorter;
    private QueryResult result;
    private int idCol = -1; // index into result.columns of r_object_id

    ResultPanel(SessionPanel ctx, boolean compact) {
        this(ctx, compact, false);
    }

    ResultPanel(SessionPanel ctx, boolean compact, boolean editableGrid) {
        super(new BorderLayout());
        this.ctx = ctx;
        this.editableGrid = editableGrid;

        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setFillsViewportHeight(true);
        table.setFont(Theme.mono);
        table.setRowHeight(21);
        table.setShowGrid(true);
        table.setShowVerticalLines(true);
        table.setShowHorizontalLines(false);
        table.setGridColor(new Color(0xE6E6E6));
        table.setIntercellSpacing(new Dimension(1, 1));
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setCellSelectionEnabled(true);
        table.setSelectionBackground(new Color(0x0078D7));
        table.setSelectionForeground(Color.WHITE);
        table.putClientProperty("JTable.autoStartsEdit", Boolean.FALSE);   // typing alone does not edit
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        table.setDefaultRenderer(String.class, new CellRenderer());
        JTextField editorField = new JTextField();
        editorField.setBorder(BorderFactory.createLineBorder(new Color(0x0078D7)));
        DefaultCellEditor ed = new DefaultCellEditor(editorField);
        ed.setClickCountToStart(2);
        table.setDefaultEditor(String.class, ed);
        table.setTableHeader(new JTableHeader(table.getColumnModel()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // dqMan-style filter funnel at the right edge of every data column
                for (int vc = 0; vc < getColumnModel().getColumnCount(); vc++) {
                    int mc = table.convertColumnIndexToModel(vc);
                    if (mc == 0) continue;
                    Rectangle r = getHeaderRect(vc);
                    Color bg = getBackground();
                    g.setColor(bg);
                    g.fillRect(r.x + r.width - FUNNEL_W - 1, r.y + 2, FUNNEL_W - 1, r.height - 4);
                    new FunnelIcon(colFilters.containsKey(mc)).paintIcon(this, g, r.x + r.width - FUNNEL_W + 1, r.y + (r.height - 12) / 2);
                }
            }
        });
        TableCellRenderer hr = table.getTableHeader().getDefaultRenderer();
        if (hr instanceof JLabel) ((JLabel) hr).setHorizontalAlignment(SwingConstants.LEFT);
        table.getTableHeader().setBackground(Color.WHITE);
        ToolTipManager.sharedInstance().registerComponent(table);

        message.setEditable(false);
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setFont(Theme.mono);
        message.setBorder(new EmptyBorder(8, 8, 8, 8));

        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(Theme.box());
        sp.getViewport().setBackground(Color.WHITE);
        body.add(sp, "table");
        JScrollPane mp = new JScrollPane(message);
        mp.setBorder(Theme.box());
        body.add(mp, "message");
        add(body);

        if (!compact) {
            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            top.setBorder(new EmptyBorder(4, 2, 3, 2));
            top.add(info, BorderLayout.WEST);
            add(top, BorderLayout.NORTH);
        }
        setOpaque(false);

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

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                popup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                popup(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(e)) return;
                int r = table.rowAtPoint(e.getPoint());
                int c = table.columnAtPoint(e.getPoint());
                if (r < 0 || c < 0 || table.isCellEditable(r, c) || table.isEditing()) return;
                String v = cell(r, c);
                if (QueryResult.isId(v)) {
                    ctx.openObject(v);
                } else if (editableGrid && Settings.editInGrid && c > 0) {
                    ctx.status(whyNotEditable(table.convertColumnIndexToModel(c) - 1));
                }
            }
        });

        // header: sort on click, filter popup when the funnel is clicked
        JTableHeader header = table.getTableHeader();
        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (sorter != null) sorter.suppressToggle = onFunnel(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (onFunnel(e)) {
                    int vc = header.columnAtPoint(e.getPoint());
                    showColumnFilter(table.convertColumnIndexToModel(vc), header.getHeaderRect(vc));
                }
            }
        });
        Ui.bind(table, Ui.ctrl(KeyEvent.VK_C), "copyCells", () -> copyRows(false));
        showMessage("");
    }

    /** Extra context-menu action that receives the object id of the clicked row. */
    void addRowAction(String label, Consumer<String> action) {
        extraActions.add(new Object[]{label, action});
    }

    /** Context-menu action that does not need a row (e.g. Refresh). */
    void addGeneralAction(String label, Runnable action) {
        generalActions.add(new Object[]{label, action});
    }

    void copySelection() {
        if (table.getSelectedRowCount() > 0) copyRows(false);
    }

    void setFilterText(String text) {
        filter.setText(text);
    }

    QueryResult result() {
        return result;
    }

    void showMessage(String msg) {
        message.setForeground(new Color(0x444444));
        message.setText(msg);
        cards.show(body, "message");
    }

    void showError(String msg) {
        message.setForeground(new Color(0xB00020));
        message.setText(msg);
        cards.show(body, "message");
        info.setText("Results (error)");
    }

    void show(QueryResult r) {
        this.result = r;
        this.idCol = r.col("r_object_id");
        colFilters.clear();
        editedCells.clear();
        String[] cols = new String[r.columns.length + 1];
        cols[0] = "";
        System.arraycopy(r.columns, 0, cols, 1, r.columns.length);
        Object[][] data = new Object[r.rows.size()][];
        for (int i = 0; i < data.length; i++) {
            String[] src = r.rows.get(i);
            Object[] row = new Object[src.length + 1];
            row[0] = i + 1;
            System.arraycopy(src, 0, row, 1, src.length);
            data[i] = row;
        }
        model = new DefaultTableModel(data, cols) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column > 0 && canEdit(column - 1);
            }

            @Override
            public Class<?> getColumnClass(int c) {
                return c == 0 ? Integer.class : String.class;
            }

            @Override
            public void setValueAt(Object v, int row, int column) {
                String nv = v == null ? "" : v.toString();
                Object old = getValueAt(row, column);
                String ov = old == null ? "" : old.toString();
                if (nv.equals(ov)) return;
                super.setValueAt(nv, row, column);
                saveCell(row, column, ov, nv);
            }
        };
        table.setModel(model);
        sorter = new FilterSorter(model);
        table.setRowSorter(sorter);

        DefaultTableCellRenderer num = new DefaultTableCellRenderer();
        num.setHorizontalAlignment(SwingConstants.LEFT);
        num.setFont(Theme.ui);
        num.setBackground(ROWNUM_BG);
        num.setForeground(new Color(0x555555));
        table.getColumnModel().getColumn(0).setCellRenderer(num);
        autoSize();
        if (r.hiddenId && idCol >= 0) {
            table.removeColumn(table.getColumnModel().getColumn(table.convertColumnIndexToView(idCol + 1)));
        }
        applyFilter();
        if (r.columns.length == 0) showMessage("Statement executed. No result columns returned.");
        else cards.show(body, "table");
    }

    // ------------------------------------------------------------- editing

    private boolean canEdit(int c) {
        return whyNotEditable(c) == null;
    }

    /** null if the result column can be edited in the grid, otherwise the reason. */
    private String whyNotEditable(int c) {
        if (!editableGrid || result == null) return "This grid is read-only.";
        if (!Settings.editInGrid) return "Editing in the grid is switched off (Extras > Edit values in result grid).";
        if (idCol < 0) return "To edit values in the grid, include r_object_id in the select list (e.g. select * ...).";
        if (c < 0 || c >= result.columns.length) return "Not editable.";
        String n = result.columns[c].toLowerCase(Locale.ROOT);
        if (n.startsWith("r_") || n.startsWith("i_")) return n + " is a read-only system attribute.";
        if (result.types[c] == Dfc.DM_ID) return n + " is an ID attribute – edit it in the object view (double-click the id).";
        return null;
    }

    private void saveCell(int mrow, int mcol, String oldVal, String newVal) {
        int c = mcol - 1;
        String attr = result.columns[c];
        String id = result.rows.get(mrow)[idCol];
        int dtype = result.types[c];
        boolean rep = result.repeating[c];
        String objType = DqlUpdate.typeOf(result.dql);
        DefaultTableModel m = model;
        QueryResult res = result;
        if (objType == null) {
            rowVector(mrow).set(mcol, oldVal);
            m.fireTableRowsUpdated(mrow, mrow);
            Ui.error(this, "Edit", "Cannot tell the object type from the query (FROM clause) – value not saved.");
            return;
        }
        if (dtype == Dfc.DM_INTEGER || dtype == Dfc.DM_DOUBLE) {
            try {
                for (String v : rep ? DqlUpdate.split(newVal) : java.util.Collections.singletonList(newVal)) DqlUpdate.literal(dtype, v);
            } catch (NumberFormatException ex) {
                rowVector(mrow).set(mcol, oldVal);
                m.fireTableRowsUpdated(mrow, mrow);
                Ui.error(this, "Edit", attr + " is a number attribute – '" + newVal + "' is not a number. Value not saved.");
                return;
            }
        }
        res.rows.get(mrow)[c] = newVal;
        ctx.status("Saving " + attr + " on " + id + " ...");
        ctx.conn().run("grid update " + objType + "." + attr + " on " + id,
                s -> DqlUpdate.save(s, objType, id, attr, dtype, rep, newVal), saved -> {
            if (m != model) return; // a new query replaced the grid meanwhile
            String stored = saved.stored == null ? newVal : saved.stored;
            for (int r = 0; r < res.rows.size(); r++) {
                if (!id.equals(res.rows.get(r)[idCol])) continue;
                res.rows.get(r)[c] = stored;
                rowVector(r).set(mcol, stored);   // show what the server really stored
                m.fireTableRowsUpdated(r, r);
                editedCells.add(r + ":" + mcol);
            }
            table.repaint();
            ctx.status("Saved and verified: " + attr + " = '" + stored + "'  (" + id + ")");
        }, t -> {
            if (m == model) {
                res.rows.get(mrow)[c] = oldVal;
                rowVector(mrow).set(mcol, oldVal); // restore without triggering another save
                m.fireTableRowsUpdated(mrow, mrow);
            }
            ctx.status("Save failed for " + attr + " on " + id);
            Ui.error(this, "Could not save " + attr, t);
        });
    }

    @SuppressWarnings("unchecked")
    private Vector<Object> rowVector(int mrow) {
        return (Vector<Object>) model.getDataVector().get(mrow);
    }

    // ------------------------------------------------------------- layout / filter

    private void autoSize() {
        FontMetrics fm = table.getFontMetrics(table.getFont());
        FontMetrics hfm = table.getTableHeader().getFontMetrics(table.getTableHeader().getFont());
        TableColumnModel cm = table.getColumnModel();
        int rows = Math.min(200, table.getModel().getRowCount());
        for (int c = 0; c < cm.getColumnCount(); c++) {
            int w = c == 0 ? fm.stringWidth(String.valueOf(rows)) + 18
                    : hfm.stringWidth(String.valueOf(table.getModel().getColumnName(c))) + FUNNEL_W + 22;
            for (int r = 0; r < rows && c > 0; r++) {
                Object v = table.getModel().getValueAt(r, c);
                if (v != null) w = Math.max(w, fm.stringWidth(v.toString()) + 14);
            }
            cm.getColumn(c).setPreferredWidth(Math.min(c == 0 ? 60 : 420, Math.max(c == 0 ? 28 : 60, w)));
        }
    }

    private void applyFilter() {
        if (sorter == null) return;
        List<RowFilter<TableModel, Integer>> fs = new ArrayList<>();
        String t = filter.getText().trim();
        if (!t.isEmpty()) fs.add(RowFilter.regexFilter("(?i)" + Pattern.quote(t)));
        for (Map.Entry<Integer, String> e : colFilters.entrySet()) fs.add(RowFilter.regexFilter(e.getValue(), e.getKey()));
        sorter.setRowFilter(fs.isEmpty() ? null : fs.size() == 1 ? fs.get(0) : RowFilter.andFilter(fs));
        table.getTableHeader().repaint();
        if (result != null) {
            int shown = table.getRowCount();
            info.setText("Results (" + (shown == result.rows.size() ? String.valueOf(shown)
                    : shown + " of " + result.rows.size()) + (result.truncated ? "+" : "") + ")");
        }
    }

    private boolean onFunnel(MouseEvent e) {
        JTableHeader h = table.getTableHeader();
        int vc = h.columnAtPoint(e.getPoint());
        if (vc < 0 || table.convertColumnIndexToModel(vc) == 0) return false;
        Rectangle r = h.getHeaderRect(vc);
        return e.getX() >= r.x + r.width - FUNNEL_W - 2 && h.getCursor().getType() == Cursor.DEFAULT_CURSOR;
    }

    /** dqMan-like column filter: free text "contains" or pick one of the distinct values. */
    private void showColumnFilter(int mcol, Rectangle hdr) {
        if (result == null || mcol <= 0) return;
        String colName = model.getColumnName(mcol);
        TreeMap<String, Integer> distinct = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String[] row : result.rows) distinct.merge(row[mcol - 1], 1, Integer::sum);

        JPopupMenu pop = new JPopupMenu();
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBorder(new EmptyBorder(6, 6, 6, 6));
        JTextField contains = new JTextField(18);
        String cur = colFilters.get(mcol);
        if (cur != null && cur.startsWith("(?i)\\Q")) contains.setText(cur.substring(6, cur.length() - 2));
        JPanel north = new JPanel(new BorderLayout(4, 0));
        north.add(new JLabel("Contains:"), BorderLayout.WEST);
        north.add(contains);
        p.add(north, BorderLayout.NORTH);

        DefaultListModel<String> lm = new DefaultListModel<>();
        lm.addElement("(All)");
        int n = 0;
        for (Map.Entry<String, Integer> e : distinct.entrySet()) {
            if (n++ > 1000) break;
            lm.addElement((e.getKey().isEmpty() ? "(Empty)" : e.getKey()) + "   [" + e.getValue() + "]");
        }
        JList<String> list = new JList<>(lm);
        List<String> keys = new ArrayList<>(distinct.keySet());
        list.setVisibleRowCount(Math.min(12, lm.size()));
        JScrollPane ls = new JScrollPane(list);
        ls.setPreferredSize(new Dimension(Math.max(220, hdr.width), Math.min(260, 22 + lm.size() * 19)));
        p.add(ls);
        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int i = list.getSelectedIndex();
            if (i < 0) return;
            if (i == 0) colFilters.remove(mcol);
            else colFilters.put(mcol, "^" + Pattern.quote(keys.get(i - 1)) + "$");
            applyFilter();
            pop.setVisible(false);
        });
        contains.addActionListener(e -> {
            String t = contains.getText();
            if (t.isEmpty()) colFilters.remove(mcol);
            else colFilters.put(mcol, "(?i)" + Pattern.quote(t));
            applyFilter();
            pop.setVisible(false);
        });
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        south.add(Ui.smallButton("Clear filter", null, e -> {
            colFilters.remove(mcol);
            applyFilter();
            pop.setVisible(false);
        }));
        south.add(Ui.smallButton("Sort A-Z", null, e -> {
            sorter.setSortKeys(Collections.singletonList(new RowSorter.SortKey(mcol, SortOrder.ASCENDING)));
            pop.setVisible(false);
        }));
        south.add(Ui.smallButton("Sort Z-A", null, e -> {
            sorter.setSortKeys(Collections.singletonList(new RowSorter.SortKey(mcol, SortOrder.DESCENDING)));
            pop.setVisible(false);
        }));
        p.add(south, BorderLayout.SOUTH);
        pop.add(p);
        pop.show(table.getTableHeader(), hdr.x, hdr.y + hdr.height);
        SwingUtilities.invokeLater(contains::requestFocusInWindow);
        ctx.status("Filter column " + colName + ": type text + Enter, or pick a value");
    }

    // ------------------------------------------------------------------ actions

    private String cell(int viewRow, int viewCol) {
        if (viewRow < 0 || viewCol < 0) return null;
        Object v = table.getValueAt(viewRow, viewCol);
        return v == null ? null : v.toString();
    }

    /** Object id for a cell: the cell itself if it is an id, else r_object_id of the row, else any id in the row. */
    private String idAt(int viewRow, int viewCol) {
        if (viewRow < 0 || result == null) return null;
        String v = cell(viewRow, viewCol);
        if (QueryResult.isId(v)) return v;
        int mr = table.convertRowIndexToModel(viewRow);
        String[] row = result.rows.get(mr);
        if (idCol >= 0 && QueryResult.isId(row[idCol])) return row[idCol];
        for (String s : row) if (QueryResult.isId(s)) return s;
        return null;
    }

    private void popup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        int r = table.rowAtPoint(e.getPoint());
        int c = table.columnAtPoint(e.getPoint());
        if (r >= 0 && !table.isCellSelected(r, c)) table.changeSelection(r, c, false, false);
        String id = idAt(r, c);
        JPopupMenu m = new JPopupMenu();
        if (r >= 0 && c > 0 && table.isCellEditable(r, c)) {
            m.add(Ui.item("Edit value (F2)", ev -> {
                table.editCellAt(r, c);
                Component ed = table.getEditorComponent();
                if (ed != null) ed.requestFocusInWindow();
            }));
            m.add(Ui.item("Set to empty", ev -> table.setValueAt("", r, c)));
            m.addSeparator();
        }
        m.add(Ui.item("Copy cell", ev -> {
            String v = cell(r, c);
            if (v != null) Ui.copy(v);
        }));
        m.add(Ui.item("Copy selected rows", ev -> copyRows(false)));
        m.add(Ui.item("Copy selected rows with header", ev -> copyRows(true)));
        if (id != null) {
            m.addSeparator();
            m.add(Ui.item("Open object  " + id, ev -> ctx.openObject(id)));
            m.add(Ui.item("Dump", ev -> Ops.dump(ctx, id)));
            m.add(Ui.item("Export content...", ev -> Ops.exportContent(ctx, id)));
            m.add(Ui.item("Copy object id", ev -> Ui.copy(id)));
            for (Object[] a : extraActions) {
                @SuppressWarnings("unchecked")
                Consumer<String> act = (Consumer<String>) a[1];
                m.add(Ui.item((String) a[0], ev -> act.accept(id)));
            }
        }
        m.addSeparator();
        if (!colFilters.isEmpty() || !filter.getText().isEmpty()) {
            m.add(Ui.item("Clear all filters", ev -> {
                colFilters.clear();
                filter.setText("");
                applyFilter();
            }));
        }
        for (Object[] a : generalActions) {
            Runnable act = (Runnable) a[1];
            m.add(Ui.item((String) a[0], ev -> act.run()));
        }
        m.add(Ui.item("Export to CSV...", ev -> export(false)));
        m.add(Ui.item("Export to Excel...", ev -> export(true)));
        m.show(table, e.getX(), e.getY());
    }

    private void copyRows(boolean header) {
        int[] rows = table.getSelectedRows();
        int[] cols = table.getSelectedColumns();
        StringBuilder sb = new StringBuilder();
        boolean single = rows.length == 1 && cols.length == 1 && !header;
        if (single) {
            Object v = table.getValueAt(rows[0], cols[0]);
            Ui.copy(v == null ? "" : v.toString());
            return;
        }
        if (header) {
            for (int c = 1; c < table.getColumnCount(); c++) {
                if (c > 1) sb.append('\t');
                sb.append(table.getColumnName(c));
            }
            sb.append('\n');
        }
        for (int r : rows) {
            for (int c = 1; c < table.getColumnCount(); c++) {
                if (c > 1) sb.append('\t');
                Object v = table.getValueAt(r, c);
                sb.append(v == null ? "" : v);
            }
            sb.append('\n');
        }
        Ui.copy(sb.toString());
    }

    private void export(boolean excel) {
        exportTo(excel ? "xlsx" : "csv");
    }

    /** Edit > Export to > csv | xlsx | html | clipboard (visible rows, current order). */
    void exportTo(String kind) {
        if (result == null || result.columns.length == 0) {
            ctx.status("Nothing to export");
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (int v = 0; v < table.getRowCount(); v++) rows.add(result.rows.get(table.convertRowIndexToModel(v)));
        if ("clipboard".equals(kind)) {
            StringBuilder sb = new StringBuilder(String.join("\t", result.columns)).append('\n');
            for (String[] r : rows) sb.append(String.join("\t", r)).append('\n');
            Ui.copy(sb.toString());
            ctx.status(rows.size() + " rows copied to the clipboard");
            return;
        }
        JFileChooser fc = Ui.chooser("export");
        fc.setSelectedFile(new File(fc.getCurrentDirectory(), "dql_results." + kind));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        Ui.remember("export", f);
        try {
            if ("xlsx".equals(kind)) Export.xlsx(f, "Results", result.columns, rows);
            else if ("html".equals(kind)) Export.html(f, result.dql, result.columns, rows);
            else Export.csv(f, result.columns, rows);
            ctx.status("Exported " + rows.size() + " rows to " + f.getAbsolutePath());
        } catch (Exception ex) {
            Ui.error(this, "Export failed", ex);
        }
    }

    /** Edit > Search: finds text in the grid after the current cell; returns false if not found. */
    boolean search(String text) {
        if (result == null || text == null || text.isEmpty() || table.getRowCount() == 0) return false;
        String t = text.toLowerCase(Locale.ROOT);
        int rows = table.getRowCount();
        int cols = table.getColumnCount();
        int startR = Math.max(0, table.getSelectedRow());
        int startC = table.getSelectedColumn() < 0 ? 0 : table.getSelectedColumn() + 1;
        for (int i = 0; i <= rows; i++) {
            int r = (startR + i) % rows;
            for (int c = (i == 0 ? startC : 1); c < cols; c++) {
                Object v = table.getValueAt(r, c);
                if (v != null && v.toString().toLowerCase(Locale.ROOT).contains(t)) {
                    table.changeSelection(r, c, false, false);
                    table.requestFocusInWindow();
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ rendering

    /** Sorter that ignores the header click used to open the filter popup. */
    private static final class FilterSorter extends TableRowSorter<TableModel> {
        boolean suppressToggle;

        FilterSorter(TableModel m) {
            super(m);
        }

        @Override
        public void toggleSortOrder(int column) {
            if (suppressToggle) {
                suppressToggle = false;
                return;
            }
            super.toggleSortOrder(column);
        }
    }

    private final class CellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            super.getTableCellRendererComponent(t, v, sel, focus, r, c);
            setBorder(new EmptyBorder(0, 4, 0, 4));
            if (!sel) {
                int mr = t.convertRowIndexToModel(r);
                int mc = t.convertColumnIndexToModel(c);
                setBackground(editedCells.contains(mr + ":" + mc) ? EDITED_BG : (r % 2 == 1 ? Theme.ZEBRA : Color.WHITE));
                setForeground(Color.BLACK);
            }
            return this;
        }
    }

    private static final class FunnelIcon implements Icon {
        private final boolean active;

        FunnelIcon(boolean active) {
            this.active = active;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int[] xs = {x + 1, x + 12, x + 8, x + 8, x + 5, x + 5};
            int[] ys = {y + 1, y + 1, y + 6, y + 11, y + 9, y + 6};
            if (active) {
                g2.setColor(new Color(0x0078D7));
                g2.fillPolygon(xs, ys, 6);
            }
            g2.setColor(active ? new Color(0x005A9E) : new Color(0x555555));
            g2.drawPolygon(xs, ys, 6);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 14;
        }

        @Override
        public int getIconHeight() {
            return 12;
        }
    }

}
