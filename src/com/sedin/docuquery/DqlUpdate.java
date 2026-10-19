package com.sedin.docuquery;

import com.documentum.fc.client.IDfPersistentObject;
import com.documentum.fc.client.IDfSession;
import com.documentum.fc.common.DfId;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Saves one attribute value with a server-side DQL UPDATE (like dqMan) and reads the stored
 * value back. DQL avoids DFC's object/BOF layer, so it is fast and the result is unambiguous
 * (objects_updated = 1, or a clear server error).
 */
final class DqlUpdate {
    private static final Pattern FROM = Pattern.compile("(?is)\\bfrom\\s+([A-Za-z0-9_$.]+)");

    private DqlUpdate() {}

    /** Object type named in the FROM clause of the query that produced the grid, or null. */
    static String typeOf(String dql) {
        if (dql == null) return null;
        Matcher m = FROM.matcher(dql);
        if (!m.find()) return null;
        String t = m.group(1);
        return t.contains(".") ? null : t; // registered tables (dm_dbo.x) are not objects
    }

    static final class Saved {
        String sql;
        String stored;
    }

    /** Updates attr on object id; returns the value the server stored. */
    static Saved save(IDfSession s, String type, String id, String attr, int dataType, boolean repeating, String value)
            throws Exception {
        Saved out = new Saved();
        if (dataType == Dfc.DM_TIME) {
            // dates: DFC converts the displayed format reliably; DQL DATE() would depend on server locale
            IDfPersistentObject o = s.getObject(new DfId(id));
            if (repeating) {
                o.removeAll(attr);
                for (String v : split(value)) ObjectPanel.append(o, attr, dataType, v);
            } else {
                ObjectPanel.set(o, attr, dataType, value);
            }
            o.save();
            out.sql = "(DFC) set " + attr + " on " + id;
        } else {
            StringBuilder q = new StringBuilder("update ").append(type).append(" objects ");
            if (repeating) {
                q.append("truncate ").append(attr);
                for (String v : split(value)) q.append(", append ").append(attr).append(" = ").append(literal(dataType, v));
            } else {
                q.append("set ").append(attr).append(" = ").append(literal(dataType, value));
            }
            q.append(" where r_object_id = '").append(id).append("'");
            out.sql = q.toString();
            QueryResult r = QueryResult.execute(s, out.sql, 0, null);
            int c = r.col("objects_updated");
            if (c >= 0 && !r.rows.isEmpty() && "0".equals(r.rows.get(0)[c].trim())) {
                throw new IllegalStateException("The server updated 0 objects.\n"
                        + "Possible reasons: no WRITE permission, the object is checked out by someone else,\n"
                        + "it is an old (immutable) version, or the attribute belongs to another type.\n\nDQL: " + out.sql);
            }
        }
        out.stored = readBack(s, type, id, attr, repeating, value);
        Log.info("grid update OK: " + out.sql + "  -> stored '" + out.stored + "'");
        return out;
    }

    private static String readBack(IDfSession s, String type, String id, String attr, boolean repeating, String fallback) {
        try {
            QueryResult r = QueryResult.execute(s, "select r_object_id, " + attr + " from " + type
                    + " where r_object_id = '" + id + "'", 1, null);
            if (r.rows.isEmpty()) return fallback;
            int c = r.col(attr);
            return c < 0 ? fallback : r.rows.get(0)[c];
        } catch (Exception e) {
            return fallback;
        }
    }

    static List<String> split(String value) {
        List<String> l = new ArrayList<>();
        for (String v : value.split(";")) if (!v.trim().isEmpty()) l.add(v.trim());
        return l;
    }

    static String literal(int t, String v) {
        String s = v == null ? "" : v.trim();
        if (t == Dfc.DM_BOOLEAN) return ObjectPanel.bool(s) ? "TRUE" : "FALSE";
        if (t == Dfc.DM_INTEGER || t == Dfc.DM_DOUBLE) {
            if (s.isEmpty()) return "0";
            Double.parseDouble(s.replace(",", ".")); // throws NumberFormatException for bad input
            return s.replace(",", ".");
        }
        String raw = t == Dfc.DM_STRING ? (v == null ? "" : v) : s;
        return "'" + raw.replace("'", "''") + "'";
    }

    /** "describe [type] name" -> attribute list of the type, like dqMan. Returns null if not a describe. */
    static String describeTarget(String dql) {
        Matcher m = Pattern.compile("(?is)^\\s*describe\\s+(?:type\\s+)?([A-Za-z0-9_]+)\\s*;?\\s*$").matcher(dql);
        return m.matches() ? m.group(1).toLowerCase(Locale.ROOT) : null;
    }

    static QueryResult describe(IDfSession s, String type) throws Exception {
        QueryResult r = QueryResult.of(new String[]{"attr_name", "data_type", "length", "repeating", "defined_in"});
        com.documentum.fc.client.IDfType t = s.getType(type);
        if (t == null) throw new IllegalArgumentException("Type not found: " + type);
        int start = t.getInt("start_pos");
        int n = t.getTypeAttrCount();
        String sup = t.getString("super_name");
        for (int i = 0; i < n; i++) {
            com.documentum.fc.common.IDfAttr a = t.getTypeAttr(i);
            r.rows.add(new String[]{a.getName(), QueryResult.typeName(a.getDataType()),
                    a.getDataType() == Dfc.DM_STRING ? String.valueOf(a.getLength()) : "",
                    a.isRepeating() ? "R" : "", i < start ? "inherited (" + sup + ")" : type});
        }
        r.dql = "describe " + type;
        return r;
    }
}
