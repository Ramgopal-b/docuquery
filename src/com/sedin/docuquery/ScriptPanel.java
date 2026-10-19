package com.sedin.docuquery;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs a batch of DQL statements (separated by ';' at line end or a line containing 'go')
 * or a batch of API commands (one per line; set/append/insert take the value from the next line).
 */
final class ScriptPanel extends JPanel implements SessionPanel.Executable {

    private final SessionPanel ctx;
    private final boolean api;
    private final CodeEditor editor;
    private final JTextArea log = new JTextArea();
    private final JCheckBox stopOnError = new JCheckBox("Stop on error", true);
    private final JSpinner printRows = new JSpinner(new SpinnerNumberModel(50, 0, 100000, 10));
    private final JButton runBtn;
    private final JButton stopBtn;
    private AtomicBoolean cancel;

    ScriptPanel(SessionPanel ctx, boolean api) {
        super(new BorderLayout());
        this.ctx = ctx;
        this.api = api;
        this.editor = new CodeEditor(!api);
        log.setFont(Theme.mono);
        log.setEditable(false);

        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.setBorder(new EmptyBorder(2, 4, 2, 4));
        tb.add(new JLabel(api ? "API Script  " : "DQL Script  "));
        runBtn = Ui.smallButton("Run script", "Run the whole script (F5)", e -> execute());
        runBtn.setForeground(new Color(0x1E7B34));
        stopBtn = Ui.smallButton("Stop", "Stop after the current statement", e -> stop());
        stopBtn.setEnabled(false);
        tb.add(runBtn);
        tb.add(Box.createHorizontalStrut(4));
        tb.add(stopBtn);
        tb.addSeparator();
        tb.add(stopOnError);
        if (!api) {
            tb.addSeparator();
            tb.add(new JLabel("Rows to print per select: "));
            printRows.setMaximumSize(new Dimension(80, 26));
            tb.add(printRows);
        }
        tb.addSeparator();
        tb.add(Ui.smallButton("Open...", "Load a script file", e -> load()));
        tb.add(Box.createHorizontalStrut(4));
        tb.add(Ui.smallButton("Save...", "Save the script to a file", e -> save()));
        tb.add(Box.createHorizontalStrut(4));
        tb.add(Ui.smallButton("Clear log", null, e -> log.setText("")));

        JPanel top = new JPanel(new BorderLayout());
        top.add(tb, BorderLayout.NORTH);
        top.add(editor);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, new JScrollPane(log));
        split.setResizeWeight(0.5);
        split.setDividerLocation(280);
        split.setBorder(null);
        add(split);

        editor.setText(api
                ? "# One API command per line. set/append/insert read their value from the next line.\n"
                + "# retrieve,c,dm_document where object_name = 'test'\n"
                + "# set,c,l,title\n# My new title\n# save,c,l\n"
                : "-- Statements end with ';' at the end of a line, or with a line containing only 'go'.\n"
                + "-- select count(*) from dm_document;\n"
                + "-- update dm_document objects set title = 'x' where object_name = 'test';\n");
        Ui.bind(editor.pane(), Ui.key(KeyEvent.VK_F5), "run", this::execute);
    }

    void setScript(String text) {
        editor.setText(text);
    }

    static List<String> splitDql(String text) {
        List<String> l = new ArrayList<>();
        for (Statements.Stmt st : Statements.split(text)) l.add(st.text);
        return l;
    }

    static List<String> splitDqlOld(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String line : text.split("\\r?\\n", -1)) {
            String t = line.trim();
            if (t.equalsIgnoreCase("go")) {
                flush(out, cur);
                continue;
            }
            if (cur.length() == 0 && (t.isEmpty() || t.startsWith("--"))) continue;
            cur.append(line).append('\n');
            if (t.endsWith(";")) flush(out, cur);
        }
        flush(out, cur);
        return out;
    }

    private static void flush(List<String> out, StringBuilder cur) {
        String s = QueryResult.clean(cur.toString());
        if (!s.isEmpty()) out.add(s);
        cur.setLength(0);
    }

    @Override
    public void execute() {
        if (cancel != null) return; // already running
        String text = editor.getText();
        AtomicBoolean c = new AtomicBoolean();
        cancel = c;
        runBtn.setEnabled(false);
        stopBtn.setEnabled(true);
        boolean stopErr = stopOnError.isSelected();
        int limit = (Integer) printRows.getValue();
        append("==== Script started " + new java.util.Date() + " ====\n");
        ctx.status("Running script...");
        ctx.conn().run(s -> {
            int ok = 0;
            int failed = 0;
            if (api) {
                String[] lines = text.split("\\r?\\n");
                for (int i = 0; i < lines.length && !c.get(); i++) {
                    String line = lines[i].trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String val = null;
                    if (Api.needsValue(line)) val = i + 1 < lines.length ? lines[++i] : "";
                    append("API> " + line + (val != null ? "\n     value: " + val : "") + "\n");
                    try {
                        String dql = Api.dqlOf(line);
                        String r = dql != null ? QueryResult.execute(s, dql, 1000, null).toText(1000)
                                : Api.execute(s, line, val, Api.Kind.AUTO);
                        append(r + (r.endsWith("\n") ? "" : "\n"));
                        ok++;
                    } catch (Exception ex) {
                        failed++;
                        append("[ERROR] " + DocConnection.message(ex) + "\n");
                        if (stopErr) break;
                    }
                }
            } else {
                List<String> stmts = splitDql(text);
                int n = 0;
                for (String st : stmts) {
                    if (c.get()) break;
                    n++;
                    append("\n[" + n + "/" + stmts.size() + "] " + st + "\n");
                    try {
                        QueryResult r = Statements.execute(s, st, QueryResult.isSelect(st) ? (limit <= 0 ? 0 : limit) : 0, c);
                        if (QueryResult.isSelect(st)) append(r.toText(limit));
                        else if (r.rows.isEmpty()) append("OK (" + r.summary() + ")\n");
                        else {
                            StringBuilder b = new StringBuilder();
                            for (int i = 0; i < r.columns.length; i++) {
                                b.append(r.columns[i]).append(" = ").append(r.rows.get(0)[i]).append('\n');
                            }
                            append(b.toString());
                        }
                        ok++;
                    } catch (Exception ex) {
                        failed++;
                        append("[ERROR] " + DocConnection.message(ex) + "\n");
                        if (stopErr) break;
                    }
                }
            }
            return "==== Finished: " + ok + " succeeded, " + failed + " failed" + (c.get() ? ", stopped by user" : "") + " ====\n\n";
        }, summary -> {
            append(summary);
            done();
            ctx.status(summary.replace("=", "").trim());
        }, t -> {
            append("[ERROR] " + DocConnection.message(t) + "\n");
            done();
        });
    }

    private void done() {
        cancel = null;
        runBtn.setEnabled(true);
        stopBtn.setEnabled(false);
    }

    @Override
    public void stop() {
        if (cancel != null) cancel.set(true);
    }

    private void append(String s) {
        SwingUtilities.invokeLater(() -> {
            log.append(s);
            log.setCaretPosition(log.getDocument().getLength());
        });
    }

    private void load() {
        JFileChooser fc = Ui.chooser("script");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            File f = fc.getSelectedFile();
            Ui.remember("script", f);
            editor.setText(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        } catch (Exception ex) {
            Ui.error(this, "Open failed", ex);
        }
    }

    private void save() {
        JFileChooser fc = Ui.chooser("script");
        fc.setSelectedFile(new File(fc.getCurrentDirectory(), api ? "script.api" : "script.dql"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            File f = fc.getSelectedFile();
            Ui.remember("script", f);
            Files.write(f.toPath(), editor.getText().getBytes(StandardCharsets.UTF_8));
            ctx.status("Saved " + f.getAbsolutePath());
        } catch (Exception ex) {
            Ui.error(this, "Save failed", ex);
        }
    }
}
