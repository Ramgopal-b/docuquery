package com.sedin.docuquery;

import com.documentum.fc.client.IDfPersistentObject;
import com.documentum.fc.client.IDfSysObject;
import com.documentum.fc.common.DfId;

import javax.swing.*;
import java.io.File;
import java.util.Locale;

/** Object operations shared by the grid, navigator and object viewer. */
final class Ops {
    private Ops() {}

    static void dump(SessionPanel ctx, String id) {
        ctx.status("Dumping " + id + "...");
        ctx.conn().run(s -> s.getObject(new DfId(id)).dump(),
                text -> {
                    ctx.status("Dump of " + id);
                    Ui.showText(ctx, "Dump " + id, text);
                },
                t -> Ui.error(ctx, "Dump failed", t));
    }

    /** Looks up object name + file extension, asks for a target file, then calls getFile. */
    static void exportContent(SessionPanel ctx, String id) {
        ctx.conn().run(s -> {
            IDfPersistentObject o = s.getObject(new DfId(id));
            if (!(o instanceof IDfSysObject)) throw new IllegalArgumentException(id + " is not a sysobject – it has no content.");
            IDfSysObject so = (IDfSysObject) o;
            String fmt = so.getContentType();
            String ext = "";
            if (fmt != null && !fmt.isEmpty()) {
                QueryResult r = QueryResult.execute(s,
                        "select dos_extension from dm_format where name='" + QueryResult.esc(fmt) + "'", 1, null);
                if (!r.rows.isEmpty()) ext = r.rows.get(0)[0].trim();
            }
            String name = so.getObjectName();
            if (name == null || name.trim().isEmpty()) name = id;
            name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
            if (!ext.isEmpty() && !name.toLowerCase(Locale.ROOT).endsWith("." + ext.toLowerCase(Locale.ROOT))) {
                name = name + "." + ext;
            }
            return name;
        }, name -> {
            JFileChooser fc = Ui.chooser("content");
            fc.setSelectedFile(new File(fc.getCurrentDirectory(), name));
            if (fc.showSaveDialog(ctx) != JFileChooser.APPROVE_OPTION) return;
            File f = fc.getSelectedFile();
            Ui.remember("content", f);
            ctx.status("Exporting content of " + id + "...");
            ctx.conn().run(s -> ((IDfSysObject) s.getObject(new DfId(id))).getFile(f.getAbsolutePath()),
                    p -> ctx.status("Content exported to " + p),
                    t -> Ui.error(ctx, "Export failed", t));
        }, t -> Ui.error(ctx, "Export failed", t));
    }

    /** Imports one or more local files into a folder (by folder id). */
    static void importInto(SessionPanel ctx, String folderId, String folderName, Runnable done) {
        JFileChooser fc = Ui.chooser("import");
        fc.setMultiSelectionEnabled(true);
        fc.setDialogTitle("Import into " + folderName);
        if (fc.showOpenDialog(ctx) != JFileChooser.APPROVE_OPTION) return;
        File[] files = fc.getSelectedFiles();
        if (files.length == 0) return;
        Ui.remember("import", files[0]);
        String type = (String) JOptionPane.showInputDialog(ctx, "Object type for the new document(s):",
                "Import", JOptionPane.QUESTION_MESSAGE, null, null, Prefs.get("import.type", "dm_document"));
        if (type == null || type.trim().isEmpty()) return;
        String t = type.trim();
        Prefs.put("import.type", t);
        ctx.status("Importing " + files.length + " file(s)...");
        ctx.conn().run(s -> {
            StringBuilder log = new StringBuilder();
            for (File f : files) {
                String fmt = formatFor(s, f.getName());
                IDfSysObject doc = (IDfSysObject) s.newObject(t);
                doc.setObjectName(f.getName());
                if (fmt != null) doc.setContentType(fmt);
                doc.setFile(f.getAbsolutePath());
                doc.link(folderId);
                doc.save();
                log.append(f.getName()).append(" -> ").append(doc.getObjectId().getId())
                        .append(" (").append(fmt == null ? "no format" : fmt).append(")\n");
            }
            return log.toString();
        }, log -> {
            ctx.status("Imported " + files.length + " file(s) into " + folderName);
            if (done != null) done.run();
            Ui.showText(ctx, "Import result", log);
        }, ex -> Ui.error(ctx, "Import failed", ex));
    }

    static String formatFor(com.documentum.fc.client.IDfSession s, String fileName) throws Exception {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) return null;
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        QueryResult r = QueryResult.execute(s, "select name from dm_format where lower(dos_extension)='"
                + QueryResult.esc(ext) + "' order by name", 50, null);
        if (r.rows.isEmpty()) return null;
        // prefer the format whose name equals the extension, else the shortest name
        String best = null;
        for (String[] row : r.rows) {
            String n = row[0].trim();
            if (n.equalsIgnoreCase(ext)) return n;
            if (best == null || n.length() < best.length()) best = n;
        }
        return best;
    }

    static void destroy(SessionPanel ctx, String id, String label, Runnable done) {
        int ok = JOptionPane.showConfirmDialog(ctx, "Permanently destroy\n" + label + " (" + id + ")?\n\nThis cannot be undone.",
                "Destroy object", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;
        ctx.conn().run(s -> {
            s.getObject(new DfId(id)).destroy();
            return null;
        }, r -> {
            ctx.status("Destroyed " + id);
            if (done != null) done.run();
        }, t -> Ui.error(ctx, "Destroy failed", t));
    }

    static void checkout(SessionPanel ctx, String id, Runnable done) {
        ctx.conn().run(s -> {
            ((IDfSysObject) s.getObject(new DfId(id))).checkout();
            return null;
        }, r -> {
            ctx.status("Checked out " + id);
            if (done != null) done.run();
        }, t -> Ui.error(ctx, "Check out failed", t));
    }

    static void cancelCheckout(SessionPanel ctx, String id, Runnable done) {
        ctx.conn().run(s -> {
            ((IDfSysObject) s.getObject(new DfId(id))).cancelCheckout();
            return null;
        }, r -> {
            ctx.status("Check out cancelled for " + id);
            if (done != null) done.run();
        }, t -> Ui.error(ctx, "Cancel check out failed", t));
    }

    /** Checks in a new version with content from a local file; opens the new version. */
    static void checkin(SessionPanel ctx, String id) {
        JFileChooser fc = Ui.chooser("import");
        fc.setDialogTitle("Check in – choose the new content file");
        if (fc.showOpenDialog(ctx) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        Ui.remember("import", f);
        Object[] options = {"Minor version", "Major version", "Same version", "Cancel"};
        int v = JOptionPane.showOptionDialog(ctx, "How should the new version be numbered?", "Check in",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (v < 0 || v == 3) return;
        ctx.conn().run(s -> {
            IDfSysObject so = (IDfSysObject) s.getObject(new DfId(id));
            if (!so.isCheckedOut()) so.checkout();
            so.setFile(f.getAbsolutePath());
            if (v == 2) {
                so.save();
                return so.getObjectId();
            }
            String next = v == 1 ? so.getVersionPolicy().getNextMajorLabel()
                    : so.getVersionPolicy().getNextMinorLabel();
            return so.checkin(false, next + ",CURRENT");
        }, nid -> {
            ctx.status("Checked in " + id + " -> " + nid.getId());
            ctx.openObject(nid.getId());
        }, t -> Ui.error(ctx, "Check in failed", t));
    }

    static void runJobNow(SessionPanel ctx, String id) {
        ctx.conn().run(s -> {
            IDfPersistentObject o = s.getObject(new DfId(id));
            o.setBoolean("run_now", true);
            o.save();
            return o.getString("object_name");
        }, name -> ctx.status("Job '" + name + "' flagged to run now (the agent exec picks it up shortly)"),
                t -> Ui.error(ctx, "Run job failed", t));
    }
}
