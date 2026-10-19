package com.sedin.docuquery;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The DQL window, laid out like dqMan: "DQL Query" editor on top, "Results (n)" grid below.
 * Run / Stop / Back / Next live in the main toolbar.
 */
final class DqlPanel extends JPanel implements SessionPanel.Executable {

    private static final int MAX_BACK = 30;

    private final SessionPanel ctx;
    private final CodeEditor editor = new CodeEditor(true);
    private final ResultPanel results;
    private final List<Object[]> trail = new ArrayList<>(); // {dql, QueryResult}
    private int pos = -1;
    private AtomicBoolean cancel;
    private boolean running;
    private int generation;
    private long startedAt;
    private final javax.swing.Timer ticker = new javax.swing.Timer(1000, e -> tick());

    DqlPanel(SessionPanel ctx, String initial) {
        super(new BorderLayout());
        this.ctx = ctx;
        this.results = new ResultPanel(ctx, false, true);
        setBackground(Theme.PANEL_BG);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel title = new JLabel("DQL Query");
        title.setBorder(new EmptyBorder(4, 2, 3, 2));
        top.add(title, BorderLayout.NORTH);
        top.add(editor);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, results);
        split.setResizeWeight(0.45);
        split.setDividerLocation(275);
        split.setDividerSize(6);
        split.setBorder(null);
        split.setOpaque(false);
        add(split);

        editor.pane().addCaretListener(e -> ctx.caret(editor.caretText()));
        Ui.bind(editor.pane(), Ui.key(KeyEvent.VK_F5), "run", this::execute);
        Ui.bind(editor.pane(), Ui.ctrl(KeyEvent.VK_ENTER), "run2", this::execute);
        editor.setText(initial == null ? "" : initial);
    }

    void setText(String t) {
        editor.setText(t);
        editor.requestFocus();
    }

    String getQueryText() {
        String sel = editor.pane().getSelectedText();
        if (sel != null && !sel.trim().isEmpty()) return sel.trim();
        Statements.Stmt st = Statements.atCaret(editor.getText(), editor.pane().getCaretPosition());
        return st == null ? "" : st.text;
    }

    void focusEditor() {
        editor.requestFocus();
        ctx.caret(editor.caretText());
    }

    /** Edit > Clear */
    void clear() {
        editor.setText("");
        results.showMessage("");
        editor.requestFocus();
    }

    void exportTo(String kind) {
        results.exportTo(kind);
    }

    /** Edit > Search: looks in the editor first (from the caret), then in the result grid. */
    boolean search(String text) {
        String all = editor.getText().toLowerCase(java.util.Locale.ROOT);
        String t = text.toLowerCase(java.util.Locale.ROOT);
        int from = editor.pane().getSelectionEnd();
        int i = all.indexOf(t, from);
        if (i < 0 && from > 0 && !editor.pane().hasFocus()) i = all.indexOf(t);
        if (i >= 0 && editor.pane().hasFocus()) {
            editor.pane().select(i, i + t.length());
            return true;
        }
        if (results.search(text)) return true;
        if (i >= 0) {
            editor.pane().requestFocusInWindow();
            editor.pane().select(i, i + t.length());
            return true;
        }
        return false;
    }

    void copySelection() {
        results.copySelection();
    }

    @Override
    public void execute() {
        if (running) return;
        // selection -> run exactly that; otherwise the statement under the caret
        // (statements are separated by ';' or blank lines; -- comments are removed)
        String sel = editor.pane().getSelectedText();
        String dql;
        if (sel != null && !sel.trim().isEmpty()) {
            dql = QueryResult.clean(Statements.stripComments(sel));
        } else {
            Statements.Stmt st = Statements.atCaret(editor.getText(), editor.pane().getCaretPosition());
            dql = st == null ? "" : st.text;
            if (st != null) {
                // show which statement runs (like dqMan)
                int len = editor.pane().getDocument().getLength();
                editor.pane().select(Math.min(st.start, len), Math.min(st.end, len));
            }
        }
        if (dql.isEmpty()) {
            ctx.status("Nothing to run (only comments?)");
            return;
        }
        Prefs.addHistory(dql);
        AtomicBoolean c = new AtomicBoolean();
        cancel = c;
        setRunning(true);
        results.showMessage("Executing...");
        ctx.status("Executing query...");
        int max = Prefs.getInt("maxRows", 1000);
        int myGen = ++generation;
        String label = "DQL: " + dql.replaceAll("\\s+", " ");
        if (label.length() > 300) label = label.substring(0, 300) + "...";
        ctx.conn().run(label, s -> {
                    if (Statements.describeTarget(dql) != null) return Statements.execute(s, dql, max, c);
                    // add r_object_id (hidden) so the grid can be edited, fall back if the server rejects it
                    String withId = QueryResult.withObjectId(dql);
                    if (withId != null) {
                        try {
                            QueryResult r = QueryResult.execute(s, withId, max, c);
                            r.hiddenId = true;
                            return r;
                        } catch (Exception ignored) {
                            // e.g. registered tables or views without r_object_id
                        }
                    }
                    return Statements.execute(s, dql, max, c);
                },
                r -> {
                    if (myGen != generation) return; // stopped / superseded
                    setRunning(false);
                    results.show(r);
                    ctx.status(r.summary());
                    remember(editor.getText(), r);
                },
                t -> {
                    if (myGen != generation) return;
                    setRunning(false);
                    results.showError(DocConnection.message(t));
                    ctx.status("Query failed");
                });
    }

    @Override
    public void stop() {
        if (cancel != null) cancel.set(true);
        if (!running) return;
        // DFC cannot interrupt a statement the server is still working on: free the window now
        // and ignore the late result. Session > Reconnect gives a fresh session if it stays busy.
        generation++;
        setRunning(false);
        results.showMessage("Stopped.\n\nIf the server is still working on the statement, the next query waits until it is done.\n"
                + "Use Session > Reconnect to continue immediately with a fresh session,\n"
                + "and Help > Diagnostics to see what DFC was waiting for.");
        ctx.status("Stopped");
    }

    private void setRunning(boolean r) {
        running = r;
        if (r) {
            startedAt = System.currentTimeMillis();
            ticker.start();
        } else {
            ticker.stop();
        }
        ctx.running(r);
    }

    private void tick() {
        long sec = (System.currentTimeMillis() - startedAt) / 1000;
        String msg = "Executing... " + sec + " s";
        if (sec >= 10) msg += "   – taking long: Stop, Session > Reconnect, or Help > Diagnostics to see why";
        ctx.status(msg);
        if (sec >= 2) results.showMessage("Executing... " + sec + " s"
                + (sec >= 10 ? "\n\nStill waiting for the server / DFC. Help > Diagnostics shows what it is waiting for." : ""));
    }

    // ------------------------------------------------------------ Back / Next

    private void remember(String text, QueryResult r) {
        while (trail.size() > pos + 1) trail.remove(trail.size() - 1);
        trail.add(new Object[]{text, r});
        while (trail.size() > MAX_BACK) trail.remove(0);
        pos = trail.size() - 1;
        ctx.fireState();
    }

    boolean canBack() {
        return pos > 0;
    }

    boolean canNext() {
        return pos >= 0 && pos < trail.size() - 1;
    }

    void back() {
        if (canBack()) showTrail(--pos);
    }

    void next() {
        if (canNext()) showTrail(++pos);
    }

    private void showTrail(int i) {
        Object[] e = trail.get(i);
        editor.setText((String) e[0]);
        QueryResult r = (QueryResult) e[1];
        results.show(r);
        ctx.status(r.summary() + "   (from history – press Run to refresh)");
        ctx.fireState();
    }
}
