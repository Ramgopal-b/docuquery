package com.sedin.docuquery;

import com.documentum.fc.client.DfQuery;
import com.documentum.fc.client.IDfCollection;
import com.documentum.fc.client.IDfQuery;
import com.documentum.fc.client.IDfSession;
import com.documentum.fc.common.IDfAttr;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Tabular result of a DQL statement (or any generated table). */
final class QueryResult {
    String dql = "";
    String[] columns = new String[0];
    int[] types = new int[0];
    boolean[] repeating = new boolean[0];
    final List<String[]> rows = new ArrayList<>();
    boolean truncated;
    boolean cancelled;
    long millis;
    long serverMillis;
    /** r_object_id was added automatically (for in-grid editing) and is hidden in the grid. */
    boolean hiddenId;

    static QueryResult of(String[] columns) {
        QueryResult r = new QueryResult();
        r.columns = columns;
        r.types = new int[columns.length];
        r.repeating = new boolean[columns.length];
        return r;
    }

    int col(String name) {
        for (int i = 0; i < columns.length; i++) if (columns[i].equalsIgnoreCase(name)) return i;
        return -1;
    }

    String get(int row, String column) {
        int c = col(column);
        return c < 0 ? "" : rows.get(row)[c];
    }

    static String clean(String dql) {
        String s = dql.trim();
        while (s.endsWith(";")) s = s.substring(0, s.length() - 1).trim();
        if (s.toLowerCase(Locale.ROOT).endsWith("\ngo")) s = s.substring(0, s.length() - 2).trim();
        return s;
    }

    /**
     * For a plain "select a, b from type ..." without r_object_id, returns the same query with
     * r_object_id added so rows can be edited in the grid; null when that is not safe.
     */
    static String withObjectId(String dql) {
        String s = clean(dql);
        String l = s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (!l.startsWith("select ") || l.contains("r_object_id") || l.startsWith("select distinct")
                || l.contains(" group by ") || l.contains(" union ") || l.contains(" having ")
                || l.contains("dm_dbo.") || l.matches("(?s).*\\b(count|sum|avg|min|max)\\s*\\(.*")
                || l.startsWith("select *")) {
            return null;
        }
        return "select r_object_id, " + s.substring(7).trim();
    }

    static boolean isSelect(String dql) {
        String s = dql.trim();
        // skip leading comments
        while (true) {
            if (s.startsWith("--")) {
                int nl = s.indexOf('\n');
                s = nl < 0 ? "" : s.substring(nl + 1).trim();
            } else if (s.startsWith("/*")) {
                int e = s.indexOf("*/");
                s = e < 0 ? "" : s.substring(e + 2).trim();
            } else break;
        }
        return s.toLowerCase(Locale.ROOT).startsWith("select");
    }

    /** Executes a DQL statement. maxRows &lt;= 0 means unlimited. */
    static QueryResult execute(IDfSession s, String dql, int maxRows, AtomicBoolean cancel) throws Exception {
        QueryResult r = new QueryResult();
        r.dql = clean(dql);
        long t0 = System.currentTimeMillis();
        IDfQuery q = new DfQuery();
        q.setDQL(r.dql);
        IDfCollection col = q.execute(s, isSelect(r.dql) ? Dfc.DF_READ_QUERY : Dfc.DF_QUERY);
        r.serverMillis = System.currentTimeMillis() - t0;
        try {
            r.readMeta(col);
            while (col.next()) {
                if (cancel != null && cancel.get()) {
                    r.cancelled = true;
                    break;
                }
                if (maxRows > 0 && r.rows.size() >= maxRows) {
                    r.truncated = true;
                    break;
                }
                if (r.columns.length == 0) r.readMeta(col);
                String[] row = new String[r.columns.length];
                for (int i = 0; i < row.length; i++) {
                    String v = r.repeating[i]
                            ? col.getAllRepeatingStrings(r.columns[i], "; ")
                            : col.getString(r.columns[i]);
                    row[i] = v == null ? "" : v;
                }
                r.rows.add(row);
            }
        } finally {
            try {
                col.close();
            } catch (Exception ignored) {
                // ignore
            }
        }
        r.millis = System.currentTimeMillis() - t0;
        return r;
    }

    private void readMeta(IDfCollection col) throws Exception {
        int n = col.getAttrCount();
        columns = new String[n];
        types = new int[n];
        repeating = new boolean[n];
        for (int i = 0; i < n; i++) {
            IDfAttr a = col.getAttr(i);
            columns[i] = a.getName();
            types[i] = a.getDataType();
            repeating[i] = a.isRepeating();
        }
    }

    String summary() {
        String s = rows.size() + (rows.size() == 1 ? " Row" : " Rows") + " returned in "
                + String.format(Locale.ROOT, "%.3f", millis / 1000.0) + " sec  (Server response time: "
                + String.format(Locale.ROOT, "%.3f", serverMillis / 1000.0) + " sec)";
        if (truncated) s += "  (limited by Max rows – more rows exist)";
        if (cancelled) s += "  (stopped by user)";
        return s;
    }

    /** Plain-text rendering used by the script runner and API console. */
    String toText(int limit) {
        StringBuilder sb = new StringBuilder();
        int[] w = new int[columns.length];
        int shown = limit > 0 ? Math.min(limit, rows.size()) : rows.size();
        for (int c = 0; c < columns.length; c++) {
            w[c] = columns[c].length();
            for (int r = 0; r < shown; r++) w[c] = Math.min(60, Math.max(w[c], rows.get(r)[c].length()));
        }
        for (int c = 0; c < columns.length; c++) sb.append(pad(columns[c], w[c])).append("  ");
        sb.append('\n');
        for (int c = 0; c < columns.length; c++) sb.append(repeat('-', w[c])).append("  ");
        sb.append('\n');
        for (int r = 0; r < shown; r++) {
            for (int c = 0; c < columns.length; c++) sb.append(pad(rows.get(r)[c], w[c])).append("  ");
            sb.append('\n');
        }
        if (shown < rows.size()) sb.append("... ").append(rows.size() - shown).append(" more rows\n");
        sb.append('(').append(summary()).append(")\n");
        return sb.toString();
    }

    private static String pad(String s, int w) {
        if (s.length() > w) return s.substring(0, Math.max(0, w - 1)) + "…";
        StringBuilder b = new StringBuilder(s);
        while (b.length() < w) b.append(' ');
        return b.toString();
    }

    private static String repeat(char ch, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(ch);
        return b.toString();
    }

    static String typeName(int t) {
        if (t == Dfc.DM_BOOLEAN) return "BOOLEAN";
        if (t == Dfc.DM_INTEGER) return "INTEGER";
        if (t == Dfc.DM_STRING) return "STRING";
        if (t == Dfc.DM_ID) return "ID";
        if (t == Dfc.DM_TIME) return "TIME";
        if (t == Dfc.DM_DOUBLE) return "DOUBLE";
        return "UNDEFINED";
    }

    static boolean isId(String v) {
        return v != null && v.length() == 16 && v.matches("[0-9a-fA-F]{16}") && !v.equals("0000000000000000");
    }

    static String esc(String v) {
        return v.replace("'", "''");
    }
}
