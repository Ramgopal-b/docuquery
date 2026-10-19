package com.sedin.docuquery;

import com.documentum.fc.client.IDfSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Editor text -> statements the server understands:
 *  - removes -- and /* *&#47; comments (DQL has no comments), respecting 'quoted strings'
 *  - finds the statement under the caret (statements are separated by ';' or blank lines)
 *  - handles "describe [type] x" itself (types and registered tables), like dqMan
 */
final class Statements {
    private Statements() {}

    /** Removes -- line comments and /* block comments outside of single-quoted strings. */
    static String stripComments(String text) {
        StringBuilder b = new StringBuilder(text.length());
        int n = text.length();
        boolean inStr = false;
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (inStr) {
                b.append(c);
                if (c == '\'') {
                    if (i + 1 < n && text.charAt(i + 1) == '\'') b.append(text.charAt(++i));
                    else inStr = false;
                }
            } else if (c == '\'') {
                inStr = true;
                b.append(c);
            } else if (c == '-' && i + 1 < n && text.charAt(i + 1) == '-') {
                // blank the comment (keeps character positions for "statement under the caret")
                while (i < n && text.charAt(i) != '\n') {
                    b.append(' ');
                    i++;
                }
                if (i < n) b.append('\n');
            } else if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
                int e = text.indexOf("*/", i + 2);
                int stop = e < 0 ? n : e + 2;
                for (int k = i; k < stop; k++) b.append(text.charAt(k) == '\n' ? '\n' : ' ');
                i = stop - 1;
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    /** A statement and its position in the editor text. */
    static final class Stmt {
        final String text;
        final int start;
        final int end;

        Stmt(String text, int start, int end) {
            this.text = text;
            this.start = start;
            this.end = end;
        }
    }

    /** Splits editor text into statements (by ';' at line end or blank lines); comments removed. */
    static List<Stmt> split(String editorText) {
        String clean = stripComments(editorText.replace("\r", ""));
        List<Stmt> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int curStart = -1;
        int pos = 0;
        for (String line : clean.split("\n", -1)) {
            String t = line.trim();
            int lineEnd = pos + line.length();
            if (t.isEmpty() || t.equalsIgnoreCase("go")) {
                flush(out, cur, curStart, pos);
                curStart = -1;
            } else {
                if (curStart >= 0 && startsStatement(t) && !continues(cur)) {
                    flush(out, cur, curStart, pos - 1);
                    curStart = -1;
                }
                if (curStart < 0) curStart = pos;
                cur.append(line).append('\n');
                if (t.endsWith(";")) {
                    flush(out, cur, curStart, lineEnd);
                    curStart = -1;
                }
            }
            pos = lineEnd + 1;
        }
        flush(out, cur, curStart, clean.length());
        return out;
    }

    private static final java.util.regex.Pattern KEYWORD = java.util.regex.Pattern.compile(
            "(?i)^(select|update|delete|insert|create|alter|drop|describe|execute|register|unregister|grant|revoke|"
                    + "change|begin|commit|abort|truncate)\\b.*");

    /** A line that begins a new DQL statement (e.g. "select ...", "describe ..."). */
    private static boolean startsStatement(String trimmedLine) {
        return KEYWORD.matcher(trimmedLine).matches();
    }

    /** The statement so far expects more (ends with "union", "(" , "," , "and", "or", "in", "=" ...). */
    private static boolean continues(StringBuilder cur) {
        String t = cur.toString().trim().toLowerCase(Locale.ROOT);
        return t.endsWith("(") || t.endsWith(",") || t.endsWith("=") || t.matches("(?s).*\\b(union|union all|and|or|in|not|exists|where|from|set|by|select)$");
    }

    private static void flush(List<Stmt> out, StringBuilder cur, int start, int end) {
        String s = QueryResult.clean(cur.toString());
        if (!s.isEmpty()) out.add(new Stmt(s, Math.max(0, start), end));
        cur.setLength(0);
    }

    /** The statement at the caret (or the next one below it, or the last one). */
    static Stmt atCaret(String editorText, int caret) {
        List<Stmt> l = split(editorText);
        if (l.isEmpty()) return null;
        for (Stmt s : l) if (caret >= s.start && caret <= s.end) return s;
        for (Stmt s : l) if (s.start > caret) return s;
        return l.get(l.size() - 1);
    }

    /** Runs one statement: local describe, otherwise DQL on the server. */
    static QueryResult execute(IDfSession s, String stmt, int maxRows, AtomicBoolean cancel) throws Exception {
        String st = QueryResult.clean(stripComments(stmt));
        String target = describeTarget(st);
        if (target != null) return describe(s, target);
        return QueryResult.execute(s, st, maxRows, cancel);
    }

    static String describeTarget(String st) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?is)^\\s*describe\\s+(?:type\\s+|table\\s+)?([A-Za-z0-9_$.]+)\\s*$").matcher(st);
        return m.matches() ? m.group(1) : null;
    }

    /** Attributes of an object type, or columns of a registered table (dm_dbo.name or name). */
    static QueryResult describe(IDfSession s, String target) throws Exception {
        String name = target.contains(".") ? target.substring(target.lastIndexOf('.') + 1) : target;
        if (!target.contains(".")) {
            QueryResult t = QueryResult.execute(s, "select name from dm_type where name = '"
                    + QueryResult.esc(name.toLowerCase(Locale.ROOT)) + "'", 1, null);
            if (!t.rows.isEmpty()) return DqlUpdate.describe(s, name.toLowerCase(Locale.ROOT));
        }
        // registered table
        QueryResult r = QueryResult.execute(s, "select object_name, table_name, table_owner, column_name, column_datatype, "
                + "column_length from dm_registered where lower(object_name) = '" + QueryResult.esc(name.toLowerCase(Locale.ROOT))
                + "' or lower(table_name) = '" + QueryResult.esc(name.toLowerCase(Locale.ROOT)) + "'", 1, null);
        if (r.rows.isEmpty()) {
            throw new IllegalArgumentException("'" + target + "' is neither an object type nor a registered table.\n"
                    + "Check the name in the navigator: Types tab > Object Types / Registered Tables.");
        }
        String[] row = r.rows.get(0);
        String[] cols = row[r.col("column_name")].split(";");
        String[] types = row[r.col("column_datatype")].split(";");
        String[] lens = row[r.col("column_length")].split(";");
        QueryResult out = QueryResult.of(new String[]{"column_name", "data_type", "length", "registered_table"});
        for (int i = 0; i < cols.length; i++) {
            out.rows.add(new String[]{cols[i].trim(), i < types.length ? types[i].trim() : "",
                    i < lens.length ? lens[i].trim() : "", row[r.col("table_owner")].trim() + "." + row[r.col("table_name")].trim()});
        }
        out.dql = "describe " + target;
        return out;
    }
}
