package com.sedin.docuquery;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.util.List;

/**
 * One repository connection, laid out like dqMan: Repository Navigator on the left,
 * the DQL window (and any other windows) on the right, status bar at the bottom.
 */
final class SessionPanel extends JPanel {

    /** Implemented by windows that react to the global Run / Stop buttons. */
    interface Executable {
        void execute();

        void stop();
    }

    private DocConnection conn;
    private final NavigatorPanel nav;
    private final FlatTabs windows = new FlatTabs(FlatTabs.Style.WINDOW);
    private final JSplitPane split;
    private final JLabel status = new JLabel("Connected");
    private final JLabel caret = new JLabel(" ");
    private final JLabel sessionNo = new JLabel();
    private Runnable stateListener;
    private boolean running;
    private int navWidth = 285;
    private int dqlCount;

    SessionPanel(DocConnection conn, int index) {
        super(new BorderLayout());
        this.conn = conn;
        this.nav = new NavigatorPanel(this);
        setBackground(Theme.PANEL_BG);

        windows.setBorder(new EmptyBorder(0, 0, 0, 2));
        windows.onChange(this::fireState);
        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, nav, windows);
        split.setDividerLocation(navWidth);
        split.setDividerSize(6);
        split.setBorder(null);
        split.setContinuousLayout(true);
        add(split);

        // ---- status bar: message | progress box | line : col | session
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.PANEL_BG);
        bar.setBorder(new MatteBorder(1, 0, 0, 0, Theme.BORDER));
        status.setBorder(new EmptyBorder(3, 6, 3, 6));
        bar.add(status);
        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        east.setOpaque(false);
        JPanel progress = new JPanel();
        progress.setBackground(new Color(0xE6E6E6));
        progress.setPreferredSize(new Dimension(150, 18));
        progress.setBorder(new CompoundBorder(new EmptyBorder(1, 6, 1, 6), BorderFactory.createEmptyBorder()));
        east.add(progress);
        caret.setHorizontalAlignment(SwingConstants.CENTER);
        caret.setPreferredSize(new Dimension(90, 22));
        caret.setBorder(new MatteBorder(0, 1, 0, 0, Theme.BORDER));
        east.add(caret);
        sessionNo.setText("s" + index);
        sessionNo.setHorizontalAlignment(SwingConstants.CENTER);
        sessionNo.setPreferredSize(new Dimension(50, 22));
        sessionNo.setBorder(new MatteBorder(0, 1, 0, 0, Theme.BORDER));
        sessionNo.setToolTipText(conn.label());
        east.add(sessionNo);
        bar.add(east, BorderLayout.EAST);
        add(bar, BorderLayout.SOUTH);

        newDql("");
        nav.start();
    }

    DocConnection conn() {
        return conn;
    }

    void setStateListener(Runnable r) {
        stateListener = r;
    }

    void fireState() {
        if (stateListener != null) stateListener.run();
    }

    void status(String s) {
        status.setText(s);
    }

    void caret(String s) {
        caret.setText(s);
    }

    void running(boolean r) {
        running = r;
        fireState();
    }

    boolean isRunning() {
        return running;
    }

    // ------------------------------------------------------------ navigator

    boolean navigatorVisible() {
        return split.getLeftComponent() != null;
    }

    void setNavigatorVisible(boolean v) {
        if (v == navigatorVisible()) return;
        if (v) {
            split.setLeftComponent(nav);
            split.setDividerSize(6);
            split.setDividerLocation(navWidth);
        } else {
            navWidth = split.getDividerLocation();
            split.setLeftComponent(null);
            split.setDividerSize(0);
        }
        fireState();
    }

    // ------------------------------------------------------------ windows

    Component currentWindow() {
        return windows.selected();
    }

    List<Component> windowList() {
        return windows.components();
    }

    String windowTitle(Component c) {
        return windows.title(c);
    }

    void selectWindow(Component c) {
        windows.select(c);
    }

    void addTab(String title, Component c) {
        windows.add(title, null, c, true, null);
    }

    void setTabTitle(Component c, String title) {
        windows.setTitle(c, title.length() > 40 ? title.substring(0, 40) + "…" : title);
    }

    void closeTab(Component c) {
        windows.removeTab(c);
    }

    void closeCurrentWindow() {
        Component c = windows.selected();
        if (c != null) windows.removeTab(c);
    }

    DqlPanel newDql(String text) {
        DqlPanel p = new DqlPanel(this, text);
        dqlCount++;
        addTab(dqlCount == 1 ? "DQL" : "DQL " + dqlCount, p);
        SwingUtilities.invokeLater(p::focusEditor);
        return p;
    }

    /** The DQL window – selected one, else the first one, else a new one. */
    DqlPanel dqlWindow(boolean select) {
        DqlPanel p = null;
        if (windows.selected() instanceof DqlPanel) p = (DqlPanel) windows.selected();
        if (p == null) {
            for (Component c : windows.components()) {
                if (c instanceof DqlPanel) {
                    p = (DqlPanel) c;
                    break;
                }
            }
        }
        if (p == null) p = newDql("");
        if (select) windows.select(p);
        return p;
    }

    void showDql() {
        dqlWindow(true).focusEditor();
    }

    void newApi() {
        for (Component c : windows.components()) {
            if (c instanceof ApiPanel) {
                windows.select(c);
                ((ApiPanel) c).focusInput();
                return;
            }
        }
        ApiPanel p = new ApiPanel(this);
        addTab("API", p);
        SwingUtilities.invokeLater(p::focusInput);
    }

    void newScript(boolean api) {
        addTab(api ? "API Script" : "DQL Script", new ScriptPanel(this, api));
    }

    /** Loads a query into the DQL window and optionally runs it. */
    void openDql(String dql, boolean run) {
        DqlPanel p = dqlWindow(true);
        p.setText(dql);
        if (run) p.execute();
    }

    void openObject(String id) {
        String clean = id == null ? "" : id.trim();
        if (!QueryResult.isId(clean)) {
            Ui.error(this, "Open object", "'" + id + "' is not a valid 16-character object id.");
            return;
        }
        for (Component c : windows.components()) {
            if (c instanceof ObjectPanel && ((ObjectPanel) c).id.equalsIgnoreCase(clean)) {
                windows.select(c);
                ((ObjectPanel) c).load();
                return;
            }
        }
        addTab(clean, new ObjectPanel(this, clean));
    }

    void openObjectPrompt() {
        String id = JOptionPane.showInputDialog(this, "Object id (r_object_id):", "Object", JOptionPane.QUESTION_MESSAGE);
        if (id != null && !id.trim().isEmpty()) openObject(id.trim());
    }

    /** Jobs window (dm_job list with "Run job now"). */
    void showJobs(String where, String title) {
        for (Component c : windows.components()) {
            if (title.equals(windows.title(c))) windows.removeTab(c);
        }
        ResultPanel p = new ResultPanel(this, false);
        p.addRowAction("Run job now", id -> Ops.runJobNow(this, id));
        p.showMessage("Loading jobs...");
        addTab(title, p);
        conn.run(s -> QueryResult.execute(s, "select r_object_id, object_name, is_inactive, a_last_invocation, "
                        + "a_last_completion, a_current_status, a_next_invocation, run_mode, method_name from dm_job"
                        + (where == null ? "" : " where " + where) + " order by object_name", 0, null),
                r -> {
                    p.show(r);
                    status(r.summary());
                }, t -> p.showError(DocConnection.message(t)));
    }

    // ------------------------------------------------------------ toolbar actions

    void runCurrent() {
        Component c = windows.selected();
        if (c instanceof Executable) ((Executable) c).execute();
        else dqlWindow(true).execute();
    }

    void stopCurrent() {
        for (Component c : windows.components()) {
            if (c instanceof Executable) ((Executable) c).stop();
        }
    }

    boolean canBack() {
        Component c = windows.selected();
        return c instanceof DqlPanel && ((DqlPanel) c).canBack();
    }

    boolean canNext() {
        Component c = windows.selected();
        return c instanceof DqlPanel && ((DqlPanel) c).canNext();
    }

    void back() {
        if (windows.selected() instanceof DqlPanel) ((DqlPanel) windows.selected()).back();
    }

    void next() {
        if (windows.selected() instanceof DqlPanel) ((DqlPanel) windows.selected()).next();
    }

    String currentQueryText() {
        return dqlWindow(false).getQueryText();
    }

    void copySelection() {
        Component c = windows.selected();
        if (c instanceof DqlPanel) ((DqlPanel) c).copySelection();
    }

    /** Session > Reconnect: opens a fresh DFC session (e.g. when the old one is stuck) and drops the old one. */
    void reconnect() {
        DocConnection old = conn;
        DocConnection c = old.copy();
        c.alias = old.alias;
        status("Reconnecting to " + old.docbase + "...");
        c.connectAsync(c.password(), () -> {
            conn = c;
            old.disconnect();
            running(false);
            status("Reconnected – new session to " + c.docbase);
        }, t -> {
            c.disconnect();
            Ui.error(this, "Reconnect failed", t);
        });
    }

    void refreshValuelists() {
        nav.refreshAll();
    }

    void clearCurrent() {
        dqlWindow(true).clear();
    }

    void exportResults(String kind) {
        Component c = windows.selected();
        if (c instanceof DqlPanel) ((DqlPanel) c).exportTo(kind);
        else if (c instanceof ResultPanel) ((ResultPanel) c).exportTo(kind);
        else dqlWindow(true).exportTo(kind);
    }

    private String lastSearch = "";

    void search(boolean again) {
        String t = lastSearch;
        if (!again || t.isEmpty()) {
            t = (String) JOptionPane.showInputDialog(this, "Search for:", "Search", JOptionPane.QUESTION_MESSAGE,
                    null, null, lastSearch);
            if (t == null || t.isEmpty()) return;
            lastSearch = t;
        }
        Component c = windows.selected();
        boolean found = c instanceof DqlPanel ? ((DqlPanel) c).search(t)
                : c instanceof ResultPanel && ((ResultPanel) c).search(t);
        status(found ? "Found '" + t + "'  (F3 = search again)" : "'" + t + "' not found");
    }

    void close() {
        conn.disconnect();
    }
}
