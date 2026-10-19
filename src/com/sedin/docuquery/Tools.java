package com.sedin.docuquery;

import com.documentum.fc.client.IDfPersistentObject;
import com.documentum.fc.client.IDfSysObject;
import com.documentum.fc.common.DfId;
import com.documentum.fc.common.IDfAttr;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

/** Actions of the Functions and Extras menus. */
@SuppressWarnings("deprecation")
final class Tools {
    private Tools() {}

    // ------------------------------------------------------------ helpers

    /** Runs DQL and shows the result in its own window. */
    static void resultWindow(SessionPanel s, String title, String dql) {
        ResultPanel p = new ResultPanel(s, false);
        p.showMessage("Executing...");
        s.addTab(title, p);
        s.status("Executing " + title + "...");
        s.conn().run(se -> QueryResult.execute(se, dql, 0, null), r -> {
            p.show(r);
            s.status(title + ": " + r.summary());
        }, t -> p.showError(DocConnection.message(t)));
    }

    static String clipboardText() {
        try {
            Object o = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            return o == null ? "" : o.toString();
        } catch (Exception e) {
            return "";
        }
    }

    static String clipboardId(Component parent) {
        String t = clipboardText().trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b[0-9a-fA-F]{16}\\b").matcher(t);
        if (m.find()) return m.group();
        Ui.error(parent, "Clipboard", "The clipboard does not contain a 16-character object id.");
        return null;
    }

    // ------------------------------------------------------------ Functions

    /** Functions > Dump Config Objects > ... (docbaseconfig, serverconfig, sessionconfig, connectionconfig). */
    static void dumpConfig(SessionPanel s, String which) {
        s.conn().run(se -> se.apiGet("dump", which), text -> Ui.showText(s, "Dump " + which, text),
                t -> Ui.error(s, "Dump " + which, t));
    }

    /** Functions > Flush Caches > ... */
    static void flushCache(SessionPanel s, String cache) {
        s.conn().run(se -> se.apiExec("flush", cache), ok -> s.status("Flush " + cache + ": " + (ok ? "OK" : "FAILED")),
                t -> Ui.error(s, "Flush " + cache, t));
    }

    /** Functions > Last SQL statement */
    static void lastSql(SessionPanel s) {
        s.conn().run(se -> QueryResult.execute(se, "EXECUTE get_last_sql", 0, null), r -> {
            StringBuilder b = new StringBuilder();
            for (String[] row : r.rows) for (String v : row) b.append(v).append('\n');
            Ui.showText(s, "Last SQL statement", b.length() == 0 ? "(no SQL statement recorded)" : b.toString());
        }, t -> Ui.error(s, "Last SQL statement", t));
    }

    /** Functions > Server Session Statistics */
    static void sessionStatistics(SessionPanel s) {
        resultWindow(s, "Server Sessions", "EXECUTE show_sessions");
    }

    /** Functions > Tracing > ... */
    static void tracing(SessionPanel s, String option, boolean on) {
        s.conn().run(se -> QueryResult.execute(se, "EXECUTE set_options WITH option = '" + option + "', value = " + on, 0, null),
                r -> s.status(option + " tracing switched " + (on ? "ON" : "OFF")),
                t -> Ui.error(s, "Tracing", t));
    }

    // ------------------------------------------------------------ Session

    /** Session > Locale: sets session_locale of the session config (data dictionary labels etc.). */
    static void setLocale(SessionPanel s, String locale) {
        s.conn().run(se -> se.apiSet("set", "sessionconfig,session_locale", locale),
                ok -> s.status("Session locale set to " + locale + (ok ? "" : " (server returned FAILED)")),
                t -> Ui.error(s, "Locale", t));
    }

    // ------------------------------------------------------------ Extras

    /** Extras > Compare Objects: attribute-by-attribute comparison of two objects. */
    static void compareObjects(SessionPanel s) {
        JTextField a = new JTextField(18);
        JTextField b = new JTextField(18);
        JCheckBox onlyDiff = new JCheckBox("Show differences only", true);
        String clip = clipboardText().trim();
        if (QueryResult.isId(clip)) a.setText(clip);
        JPanel p = new JPanel(new GridLayout(0, 2, 6, 6));
        p.add(new JLabel("Object id 1:"));
        p.add(a);
        p.add(new JLabel("Object id 2:"));
        p.add(b);
        p.add(onlyDiff);
        if (JOptionPane.showConfirmDialog(s, p, "Compare Objects", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        String id1 = a.getText().trim();
        String id2 = b.getText().trim();
        if (!QueryResult.isId(id1) || !QueryResult.isId(id2)) {
            Ui.error(s, "Compare Objects", "Please enter two 16-character object ids.");
            return;
        }
        boolean diffOnly = onlyDiff.isSelected();
        s.conn().run(se -> {
            Map<String, String> m1 = attrs(se.getObject(new DfId(id1)));
            Map<String, String> m2 = attrs(se.getObject(new DfId(id2)));
            TreeSet<String> names = new TreeSet<>(m1.keySet());
            names.addAll(m2.keySet());
            QueryResult r = QueryResult.of(new String[]{"attribute", id1, id2, "equal"});
            for (String n : names) {
                String v1 = m1.getOrDefault(n, "(n/a)");
                String v2 = m2.getOrDefault(n, "(n/a)");
                boolean eq = v1.equals(v2);
                if (diffOnly && eq) continue;
                r.rows.add(new String[]{n, v1, v2, eq ? "yes" : "NO"});
            }
            return r;
        }, r -> {
            ResultPanel rp = new ResultPanel(s, false);
            rp.show(r);
            s.addTab("Compare", rp);
            s.status(r.rows.size() + " attribute(s) " + (diffOnly ? "differ" : "compared"));
        }, t -> Ui.error(s, "Compare Objects", t));
    }

    private static Map<String, String> attrs(IDfPersistentObject o) throws Exception {
        Map<String, String> m = new LinkedHashMap<>();
        int n = o.getAttrCount();
        for (int i = 0; i < n; i++) {
            IDfAttr a = o.getAttr(i);
            String name = a.getName();
            if (a.isRepeating()) {
                List<String> v = new ArrayList<>();
                int c = o.getValueCount(name);
                for (int j = 0; j < c; j++) v.add(o.getRepeatingString(name, j));
                m.put(name, String.join("; ", v));
            } else {
                m.put(name, o.getString(name));
            }
        }
        return m;
    }

    /** Extras > Compare Data: rows that exist in only one of two query results. */
    static void compareData(SessionPanel s) {
        JTextArea q1 = new JTextArea(s.currentQueryText(), 4, 60);
        JTextArea q2 = new JTextArea(4, 60);
        q1.setFont(Theme.mono);
        q2.setFont(Theme.mono);
        Object[] msg = {"Query A:", new JScrollPane(q1), "Query B (same columns):", new JScrollPane(q2)};
        if (JOptionPane.showConfirmDialog(s, msg, "Compare Data", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        String a = q1.getText().trim();
        String b = q2.getText().trim();
        if (a.isEmpty() || b.isEmpty()) return;
        s.status("Comparing data...");
        s.conn().run(se -> {
            QueryResult ra = QueryResult.execute(se, a, 0, null);
            QueryResult rb = QueryResult.execute(se, b, 0, null);
            Map<String, Integer> ca = count(ra);
            Map<String, Integer> cb = count(rb);
            String[] cols = new String[ra.columns.length + 1];
            cols[0] = "only_in";
            System.arraycopy(ra.columns, 0, cols, 1, ra.columns.length);
            QueryResult out = QueryResult.of(cols);
            addMissing(out, ra, cb, "A");
            addMissing(out, rb, ca, "B");
            return out;
        }, r -> {
            ResultPanel rp = new ResultPanel(s, false);
            rp.show(r);
            s.addTab("Compare Data", rp);
            s.status(r.rows.isEmpty() ? "Both queries return the same rows" : r.rows.size() + " differing row(s)");
        }, t -> Ui.error(s, "Compare Data", t));
    }

    private static Map<String, Integer> count(QueryResult r) {
        Map<String, Integer> m = new HashMap<>();
        for (String[] row : r.rows) m.merge(String.join("\u0001", row), 1, Integer::sum);
        return m;
    }

    private static void addMissing(QueryResult out, QueryResult src, Map<String, Integer> other, String label) {
        Map<String, Integer> left = new HashMap<>(other);
        for (String[] row : src.rows) {
            String k = String.join("\u0001", row);
            Integer c = left.get(k);
            if (c != null && c > 0) {
                left.put(k, c - 1);
                continue;
            }
            String[] o = new String[out.columns.length];
            o[0] = label;
            for (int i = 1; i < o.length; i++) o[i] = i - 1 < row.length ? row[i - 1] : "";
            out.rows.add(o);
        }
    }

    /** Extras > Import from File: choose a repository folder by path, then local files. */
    static void importFromFile(SessionPanel s) {
        String path = JOptionPane.showInputDialog(s, "Repository folder path (e.g. /Temp):",
                Prefs.get("import.folder", "/Temp"));
        if (path == null || path.trim().isEmpty()) return;
        String p = path.trim();
        Prefs.put("import.folder", p);
        s.conn().run(se -> folderId(se, p), id -> Ops.importInto(s, id, p, null),
                t -> Ui.error(s, "Import from File", t));
    }

    /** Extras > Import from Clipboard: creates a text document from the clipboard contents. */
    static void importFromClipboard(SessionPanel s) {
        String text = clipboardText();
        if (text.isEmpty()) {
            Ui.error(s, "Import from Clipboard", "The clipboard does not contain text.");
            return;
        }
        JTextField folder = new JTextField(Prefs.get("import.folder", "/Temp"), 24);
        JTextField name = new JTextField("clipboard_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".txt", 24);
        JTextField type = new JTextField(Prefs.get("import.type", "dm_document"), 24);
        JPanel p = new JPanel(new GridLayout(0, 2, 6, 6));
        p.add(new JLabel("Folder path:"));
        p.add(folder);
        p.add(new JLabel("Object name:"));
        p.add(name);
        p.add(new JLabel("Object type:"));
        p.add(type);
        if (JOptionPane.showConfirmDialog(s, p, "Import from Clipboard (" + text.length() + " characters)",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        String fp = folder.getText().trim();
        Prefs.put("import.folder", fp);
        s.conn().run(se -> {
            File tmp = File.createTempFile("docuquery", ".txt");
            Files.write(tmp.toPath(), text.getBytes(StandardCharsets.UTF_8));
            try {
                IDfSysObject doc = (IDfSysObject) se.newObject(type.getText().trim());
                doc.setObjectName(name.getText().trim());
                doc.setContentType("crtext");
                doc.setFile(tmp.getAbsolutePath());
                doc.link(folderId(se, fp));
                doc.save();
                return doc.getObjectId().getId();
            } finally {
                tmp.delete();
            }
        }, id -> {
            s.status("Imported clipboard as " + id);
            s.openObject(id);
        }, t -> Ui.error(s, "Import from Clipboard", t));
    }

    static String folderId(com.documentum.fc.client.IDfSession se, String path) throws Exception {
        QueryResult r = QueryResult.execute(se, "select r_object_id from dm_folder where any r_folder_path = '"
                + QueryResult.esc(path) + "'", 1, null);
        if (r.rows.isEmpty()) throw new IllegalArgumentException("Folder not found: " + path);
        return r.rows.get(0)[0];
    }

    /** Extras > Consistency Checker: flags the dm_ConsistencyChecker job to run now. */
    static void consistencyChecker(SessionPanel s) {
        if (JOptionPane.showConfirmDialog(s, "Run the dm_ConsistencyChecker job now?\nThe report is written to the job's log.",
                "Consistency Checker", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        s.conn().run(se -> {
            QueryResult r = QueryResult.execute(se, "select r_object_id from dm_job where object_name = 'dm_ConsistencyChecker'", 1, null);
            if (r.rows.isEmpty()) throw new IllegalStateException("Job dm_ConsistencyChecker not found.");
            return r.rows.get(0)[0];
        }, id -> Ops.runJobNow(s, id), t -> Ui.error(s, "Consistency Checker", t));
    }

    /** Extras > Dump &amp; Load > new dump record: opens an API script template. */
    static void dumpTemplate(SessionPanel s) {
        s.newScript(true);
        Component c = s.currentWindow();
        if (c instanceof ScriptPanel) {
            ((ScriptPanel) c).setScript("# Create a dump of selected objects (edit file name and types, then Run)\n"
                    + "create,c,dm_dump_record\n"
                    + "set,c,l,file_name\nC:\\temp\\edms_dump.dmp\n"
                    + "append,c,l,type\ndm_document\n"
                    + "append,c,l,predicate\nobject_name like 'test%'\n"
                    + "save,c,l\n");
        }
    }

    /** Extras > Navigate Object (F12): opens the selected/typed id. */
    static void navigateObject(SessionPanel s) {
        String clip = clipboardText().trim();
        String id = (String) JOptionPane.showInputDialog(s, "Object id:", "Navigate Object", JOptionPane.QUESTION_MESSAGE,
                null, null, QueryResult.isId(clip) ? clip : "");
        if (id != null && !id.trim().isEmpty()) s.openObject(id.trim());
    }
}
