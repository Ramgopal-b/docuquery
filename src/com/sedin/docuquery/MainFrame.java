package com.sedin.docuquery;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Map;
import java.util.function.Consumer;

/** Main window in the dqMan layout: menu bar, icon toolbar, session tabs. */
final class MainFrame extends JFrame {

    private final FlatTabs sessions = new FlatTabs(FlatTabs.Style.SESSION);
    private int sessionSeq;

    private ToolButton dqlBtn;
    private ToolButton navBtn;
    private ToolButton backBtn;
    private ToolButton nextBtn;
    private ToolButton runBtn;
    private ToolButton stepBtn;
    private ToolButton pauseBtn;
    private ToolButton stopBtn;
    private JCheckBoxMenuItem navMenu;

    MainFrame() {
        super(App.NAME);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exit();
            }
        });
        setIconImages(java.util.Arrays.asList(Icons.appImage(16), Icons.appImage(32), Icons.appImage(64)));
        getContentPane().setBackground(Theme.PANEL_BG);
        setJMenuBar(menu());
        add(toolbar(), BorderLayout.NORTH);
        sessions.setBackground(Theme.PANEL_BG);
        add(sessions);
        sessions.onChange(this::updateState);
        setSize(1366, 820);
        setLocationRelativeTo(null);
        updateState();
    }

    // ================================================================ menu

    private JMenuBar menu() {
        JMenuBar mb = new JMenuBar();
        mb.setBorder(new EmptyBorder(1, 4, 1, 4));

        // ---- Session
        JMenu session = new JMenu("Session");
        session.add(Ui.item("New Session Window", Ui.ctrl(KeyEvent.VK_N), e -> newSession()));
        session.add(Ui.item("Disconnect", e -> disconnect()));
        session.addSeparator();
        session.add(Ui.item("Copy", e -> copySession()));
        session.add(Ui.item("Refresh Valuelists", Ui.ctrl(KeyEvent.VK_R), e -> with(sp -> sp.refreshValuelists())));
        session.add(Ui.item("Session Alias", e -> sessionAlias()));
        session.add(Ui.item("Reconnect", e -> with(sp -> sp.reconnect())));
        session.addSeparator();
        JMenu locale = new JMenu("Locale");
        String[][] locales = {{"English", "en"}, {"German", "de"}, {"French", "fr"}, {"Spanish", "es"}, {"Italian", "it"},
                {"Dutch", "nl"}, {"Portuguese", "pt"}, {"Japanese", "ja"}, {"Chinese", "zh"}, {"Korean", "ko"}};
        for (String[] l : locales) locale.add(Ui.item(l[0] + " (" + l[1] + ")", e -> with(s -> Tools.setLocale(s, l[1]))));
        session.add(locale);
        session.addSeparator();
        session.add(Ui.item("Close", Ui.ctrl(KeyEvent.VK_W), e -> disconnect()));
        session.addSeparator();
        session.add(Ui.item("Exit", e -> exit()));
        mb.add(session);

        // ---- Jobs
        JMenu jobs = new JMenu("Jobs");
        jobs.add(Ui.item("Job list", e -> with(s -> s.showJobs(null, "Jobs"))));
        jobs.add(Ui.item("Active jobs", e -> with(s -> s.showJobs("is_inactive = false", "Active jobs"))));
        jobs.add(Ui.item("Inactive jobs", e -> with(s -> s.showJobs("is_inactive = true", "Inactive jobs"))));
        jobs.add(Ui.item("Failed jobs", e -> with(s -> s.showJobs("upper(a_current_status) like '%FAIL%'", "Failed jobs"))));
        mb.add(jobs);

        // ---- Edit
        JMenu edit = new JMenu("Edit");
        edit.add(Ui.item("Run", Ui.key(KeyEvent.VK_F9), e -> with(sp -> sp.runCurrent())));
        edit.add(Ui.item("Clear", KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
                e -> with(sp -> sp.clearCurrent())));
        edit.addSeparator();
        JMenu export = new JMenu("Export to");
        export.add(Ui.item("CSV file...", e -> with(s -> s.exportResults("csv"))));
        export.add(Ui.item("Excel file (.xlsx)...", e -> with(s -> s.exportResults("xlsx"))));
        export.add(Ui.item("HTML file...", e -> with(s -> s.exportResults("html"))));
        export.add(Ui.item("Clipboard", e -> with(s -> s.exportResults("clipboard"))));
        edit.add(export);
        edit.addSeparator();
        edit.add(Ui.item("Save to Favorites", e -> with(sp -> MainFrame.addFavorite(sp))));
        edit.addSeparator();
        edit.add(Ui.item("Search", Ui.ctrl(KeyEvent.VK_F), e -> with(s -> s.search(false))));
        edit.add(Ui.item("Search again", Ui.key(KeyEvent.VK_F3), e -> with(s -> s.search(true))));
        edit.addSeparator();
        edit.add(Ui.item("dfc.properties", e -> DfcSetup.editProperties(this)));
        mb.add(edit);

        // ---- View
        JMenu view = new JMenu("View");
        navMenu = new JCheckBoxMenuItem("Repository Navigator", true);
        navMenu.addActionListener(e -> with(s -> s.setNavigatorVisible(navMenu.isSelected())));
        view.add(navMenu);
        view.addSeparator();
        view.add(Ui.item("DQL", e -> with(sp -> sp.showDql())));
        view.add(Ui.item("API", e -> with(sp -> sp.newApi())));
        view.add(Ui.item("DQL Script", e -> with(s -> s.newScript(false))));
        view.add(Ui.item("API Script", e -> with(s -> s.newScript(true))));
        view.addSeparator();
        view.add(Ui.item("Object...", Ui.ctrl(KeyEvent.VK_O), e -> with(sp -> sp.openObjectPrompt())));
        view.add(Ui.item("History...", Ui.ctrl(KeyEvent.VK_H), e -> with(sp -> QueryListDialog.history(sp))));
        mb.add(view);

        // ---- Functions
        JMenu functions = new JMenu("Functions");
        JMenu dumpCfg = new JMenu("Dump Config Objects");
        dumpCfg.add(Ui.item("Docbase Config", e -> with(s -> Tools.dumpConfig(s, "docbaseconfig"))));
        dumpCfg.add(Ui.item("Server Config", e -> with(s -> Tools.dumpConfig(s, "serverconfig"))));
        dumpCfg.add(Ui.item("Session Config", e -> with(s -> Tools.dumpConfig(s, "sessionconfig"))));
        dumpCfg.add(Ui.item("Connection Config", e -> with(s -> Tools.dumpConfig(s, "connectionconfig"))));
        functions.add(dumpCfg);
        JMenu flush = new JMenu("Flush Caches");
        String[][] caches = {{"Data Dictionary cache", "ddcache"}, {"Persistent cache", "persistentcache"},
                {"Persistent object cache", "persistentobjcache"}, {"Query cache", "querycache"},
                {"ACL cache", "aclcache"}, {"Group cache", "groupcache"}, {"Registry cache", "registrycache"}};
        for (String[] c : caches) flush.add(Ui.item(c[0], e -> with(s -> Tools.flushCache(s, c[1]))));
        functions.add(flush);
        functions.add(Ui.item("Last SQL statement", e -> with(sp -> Tools.lastSql(sp))));
        functions.add(Ui.item("Server Session Statistics", e -> with(sp -> Tools.sessionStatistics(sp))));
        JMenu tracing = new JMenu("Tracing");
        tracing.add(Ui.item("SQL trace on", e -> with(s -> Tools.tracing(s, "sqltrace", true))));
        tracing.add(Ui.item("SQL trace off", e -> with(s -> Tools.tracing(s, "sqltrace", false))));
        tracing.addSeparator();
        tracing.add(Ui.item("RPC trace on", e -> with(s -> Tools.tracing(s, "rpctrace", true))));
        tracing.add(Ui.item("RPC trace off", e -> with(s -> Tools.tracing(s, "rpctrace", false))));
        functions.add(tracing);
        mb.add(functions);

        // ---- Favorites
        JMenu fav = new JMenu("Favorites");
        fav.addMenuListener(new MenuAdapter(() -> fillFavorites(fav)));
        mb.add(fav);

        // ---- Extras
        JMenu extras = new JMenu("Extras");
        JMenu dumpLoad = new JMenu("Dump & Load (EDMS)");
        dumpLoad.add(Ui.item("Dump records", e -> with(s -> Tools.resultWindow(s, "Dump records",
                "select r_object_id, object_name, file_name, r_creation_date, r_dump_status from dm_dump_record order by r_creation_date desc"))));
        dumpLoad.add(Ui.item("Load records", e -> with(s -> Tools.resultWindow(s, "Load records",
                "select r_object_id, object_name, file_name, r_creation_date, r_load_status from dm_load_record order by r_creation_date desc"))));
        dumpLoad.addSeparator();
        dumpLoad.add(Ui.item("New dump (API script template)", e -> with(sp -> Tools.dumpTemplate(sp))));
        extras.add(dumpLoad);
        JMenuItem copyReg = new JMenuItem("Copy registered Table");
        copyReg.setEnabled(false);
        copyReg.setToolTipText("Not available in this version");
        extras.add(copyReg);
        JMenuItem changeType = new JMenuItem("Change Objecttype");
        changeType.setEnabled(false);
        extras.add(changeType);
        extras.add(Ui.item("Consistency Checker", e -> with(sp -> Tools.consistencyChecker(sp))));
        extras.addSeparator();
        extras.add(Ui.item("Import from File", e -> with(sp -> Tools.importFromFile(sp))));
        extras.add(Ui.item("Import from Clipboard", e -> with(sp -> Tools.importFromClipboard(sp))));
        extras.add(Ui.item("Compare Objects", e -> with(sp -> Tools.compareObjects(sp))));
        extras.add(Ui.item("Compare Data", e -> with(sp -> Tools.compareData(sp))));
        JMenuItem mapping = new JMenuItem("Mapping Lists");
        mapping.setEnabled(false);
        mapping.setToolTipText("Not available in this version");
        extras.add(mapping);
        extras.addSeparator();
        extras.add(Ui.item("Navigate Object", Ui.key(KeyEvent.VK_F12), e -> with(sp -> Tools.navigateObject(sp))));
        extras.add(Ui.item("Navigate Object from Clipboard", KeyStroke.getKeyStroke(KeyEvent.VK_N, java.awt.event.InputEvent.ALT_DOWN_MASK),
                e -> with(s -> {
                    String id = Tools.clipboardId(s);
                    if (id != null) s.openObject(id);
                })));
        extras.add(Ui.item("Dump Object from Clipboard", KeyStroke.getKeyStroke(KeyEvent.VK_D, java.awt.event.InputEvent.ALT_DOWN_MASK),
                e -> with(s -> {
                    String id = Tools.clipboardId(s);
                    if (id != null) Ops.dump(s, id);
                })));
        extras.addSeparator();
        JCheckBoxMenuItem editGrid = new JCheckBoxMenuItem("Edit values in result grid", Settings.editInGrid);
        editGrid.addActionListener(e -> {
            Settings.editInGrid = editGrid.isSelected();
            Prefs.put("grid.edit", String.valueOf(editGrid.isSelected()));
        });
        extras.add(editGrid);
        extras.add(Ui.item("Options", e -> options()));
        mb.add(extras);

        // ---- Windows
        JMenu windows = new JMenu("Windows");
        windows.addMenuListener(new MenuAdapter(() -> fillWindows(windows)));
        mb.add(windows);

        // ---- Help
        JMenu help = new JMenu("Help");
        help.add(Ui.item("Keyboard shortcuts", e -> JOptionPane.showMessageDialog(this,
                "F5 / F9 / Ctrl+Enter   Run query (selection or whole editor)\n"
                        + "Ctrl+Del          Clear\n"
                        + "Ctrl+F / F3       Search / search again\n"
                        + "Ctrl+N            New session window\n"
                        + "Ctrl+R            Refresh value lists\n"
                        + "Ctrl+W            Close session\n"
                        + "Ctrl+O            Open object by id\n"
                        + "Ctrl+H            Query history\n"
                        + "F12               Navigate object\n"
                        + "Alt+N / Alt+D     Navigate / dump object id from clipboard\n"
                        + "Double-click a cell, or F2   Edit the value in the result grid (Enter saves, Esc cancels)\n"
                        + "Double-click an object id    Open the object",
                "Keyboard shortcuts", JOptionPane.INFORMATION_MESSAGE)));
        help.add(Ui.item("Diagnostics", KeyStroke.getKeyStroke(KeyEvent.VK_D, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | java.awt.event.InputEvent.SHIFT_DOWN_MASK),
                e -> Diagnostics.show(this, sessions.components())));
        help.add(Ui.item("Open log folder", e -> {
            try {
                Desktop.getDesktop().open(Log.file().getParentFile());
            } catch (Exception ex) {
                Ui.error(this, "Log folder", Log.file().getParent());
            }
        }));
        help.add(Ui.item("DFC setup...", e -> DfcSetup.wizard(this)));
        help.add(Ui.item("About", e -> JOptionPane.showMessageDialog(this,
                App.NAME + " " + App.VERSION + "\nDQL / API client for OpenText Documentum, built on DFC.\n"
                        + "dfc.properties: " + DfcSetup.propertiesFile() + "\n"
                        + "Settings, history and favourites: " + Prefs.dir(),
                "About", JOptionPane.INFORMATION_MESSAGE)));
        mb.add(help);
        return mb;
    }

    /** Session > Copy (and toolbar Copy): opens a second session with the same login. */
    private void copySession() {
        SessionPanel cur = current();
        if (cur == null) {
            newSession();
            return;
        }
        DocConnection c = cur.conn().copy();
        cur.status("Opening a copy of " + cur.conn().label() + "...");
        c.connectAsync(c.password(), () -> addSession(c), t -> {
            Ui.error(this, "Copy session", t);
            c.disconnect();
        });
    }

    private void sessionAlias() {
        SessionPanel s = current();
        if (s == null) return;
        String a = (String) JOptionPane.showInputDialog(this, "Alias for this session:", "Session Alias",
                JOptionPane.QUESTION_MESSAGE, null, null, s.conn().label());
        if (a == null) return;
        s.conn().alias = a.trim();
        sessions.setTitle(s, s.conn().label());
        updateState();
    }

    private static final class MenuAdapter implements MenuListener {
        private final Runnable onSelect;

        MenuAdapter(Runnable onSelect) {
            this.onSelect = onSelect;
        }

        @Override
        public void menuSelected(MenuEvent e) {
            onSelect.run();
        }

        @Override
        public void menuDeselected(MenuEvent e) {
            // nothing
        }

        @Override
        public void menuCanceled(MenuEvent e) {
            // nothing
        }
    }

    private void fillFavorites(JComponent menu) {
        menu.removeAll();
        for (Map.Entry<String, java.util.List<StandardFavorites.Fav>> c : StandardFavorites.ALL.entrySet()) {
            JMenu cat = new JMenu(c.getKey());
            for (StandardFavorites.Fav f : c.getValue()) {
                JMenuItem it = Ui.item(f.name, e -> with(s -> s.openDql(f.dql, f.runnable())));
                it.setToolTipText(f.dql);
                cat.add(it);
            }
            menu.add(cat);
        }
        addSeparator(menu);
        Map<String, String> m = Prefs.favorites();
        for (Map.Entry<String, String> f : m.entrySet()) {
            JMenuItem it = Ui.item(f.getKey(), e -> with(s -> s.openDql(f.getValue(), false)));
            it.setToolTipText(f.getValue());
            menu.add(it);
        }
        if (!m.isEmpty()) addSeparator(menu);
        menu.add(Ui.item("Save to Favorites...", e -> with(sp -> MainFrame.addFavorite(sp))));
        menu.add(Ui.item("Manage favorites...", e -> with(sp -> QueryListDialog.favorites(sp))));
    }

    private static void addSeparator(JComponent menu) {
        if (menu instanceof JMenu) ((JMenu) menu).addSeparator();
        else ((JPopupMenu) menu).addSeparator();
    }

    private void fillWindows(JMenu menu) {
        menu.removeAll();
        JMenu arrange = new JMenu("Arrange");
        arrange.add(Ui.item("Cascade", e -> sessions.arrange("cascade")));
        arrange.add(Ui.item("Vertical", e -> sessions.arrange("vertical")));
        arrange.add(Ui.item("Horizontal", e -> sessions.arrange("horizontal")));
        menu.add(arrange);
        menu.addSeparator();
        int i = 1;
        for (Component c : sessions.components()) {
            SessionPanel s = (SessionPanel) c;
            JCheckBoxMenuItem it = new JCheckBoxMenuItem(i + " " + s.conn().label(), c == sessions.selected());
            if (i <= 9) it.setMnemonic('0' + i);
            it.addActionListener(e -> sessions.select(c));
            menu.add(it);
            i++;
        }
        SessionPanel s = current();
        if (s != null && s.windowList().size() > 1) {
            menu.addSeparator();
            for (Component c : s.windowList()) {
                JCheckBoxMenuItem it = new JCheckBoxMenuItem("    " + s.windowTitle(c), c == s.currentWindow());
                it.addActionListener(e -> s.selectWindow(c));
                menu.add(it);
            }
            menu.add(Ui.item("    Close window", e -> s.closeCurrentWindow()));
        }
    }

    static void addFavorite(SessionPanel s) {
        String q = s.currentQueryText();
        if (q.isEmpty()) {
            JOptionPane.showMessageDialog(s, "The DQL window is empty.", "Favorites", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String first = q.split("\n")[0];
        String name = JOptionPane.showInputDialog(s, "Favourite name:", first.length() > 50 ? first.substring(0, 50) : first);
        if (name == null || name.trim().isEmpty()) return;
        Prefs.saveFavorite(name.trim(), q);
        s.status("Saved favourite '" + name.trim() + "'");
    }

    private void options() {
        JSpinner max = new JSpinner(new SpinnerNumberModel(Prefs.getInt("maxRows", 1000), 0, 10_000_000, 100));
        JCheckBox edit = new JCheckBox("Edit values in result grid (saves on Enter)", Settings.editInGrid);
        JPanel p = new JPanel(new GridLayout(0, 1, 4, 6));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.add(new JLabel("Maximum rows per query (0 = unlimited):  "));
        row.add(max);
        p.add(row);
        p.add(edit);
        if (JOptionPane.showConfirmDialog(this, p, "Options", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        Prefs.put("maxRows", String.valueOf(max.getValue()));
        Settings.editInGrid = edit.isSelected();
        Prefs.put("grid.edit", String.valueOf(edit.isSelected()));
    }

    private void focusAction(String name) {
        Component f = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (f instanceof JComponent) {
            Action a = ((JComponent) f).getActionMap().get(name);
            if (a != null) a.actionPerformed(new ActionEvent(f, ActionEvent.ACTION_PERFORMED, name));
        }
    }

    private void textAction(Consumer<JTextComponent> a) {
        Component f = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (f instanceof JTextComponent) a.accept((JTextComponent) f);
        else if (a == (Consumer<JTextComponent>) JTextComponent::copy) with(sp -> sp.copySelection());
    }

    // ================================================================ toolbar

    /** Flat toolbar button: icon above text, grey block when hovered or toggled on. */
    static final class ToolButton extends JButton {
        private boolean toggled;
        private boolean hover;

        ToolButton(String text, Icon icon, String tip) {
            super(text, icon);
            setToolTipText(tip);
            setVerticalTextPosition(SwingConstants.BOTTOM);
            setHorizontalTextPosition(SwingConstants.CENTER);
            setIconTextGap(3);
            setFocusable(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setMargin(new Insets(4, 6, 4, 6));
            setBorder(new EmptyBorder(5, 7, 4, 7));
            setFont(Theme.ui);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        void setToggled(boolean t) {
            toggled = t;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            boolean small = getText() == null || getText().isEmpty();
            return new Dimension(small ? d.width : Math.max(d.width, 46), 54);
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (toggled || (hover && isEnabled())) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(toggled ? Theme.TOGGLE_BG : Theme.TAB_HOVER);
                g2.fillRoundRect(0, 1, getWidth(), getHeight() - 2, 6, 6);
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    private static JComponent sep() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(9, 46));
        p.setBorder(new MatteBorder(4, 4, 4, 4, Theme.TOOLBAR_BG) {
            @Override
            public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
                g.setColor(new Color(0xD3D3D3));
                g.drawLine(x + w / 2, y + 6, x + w / 2, y + h - 6);
            }
        });
        return p;
    }

    private static ToolButton tool(String text, Icon icon, String tip, java.awt.event.ActionListener a) {
        ToolButton b = new ToolButton(text, icon, tip);
        b.addActionListener(a);
        return b;
    }

    private ToolButton dropDown(String tip, Consumer<JPopupMenu> fill) {
        ToolButton b = new ToolButton("", Icons.dropArrow(), tip);
        b.setBorder(new EmptyBorder(5, 1, 4, 3));
        b.addActionListener(e -> {
            JPopupMenu m = new JPopupMenu();
            fill.accept(m);
            m.show(b, 0, b.getHeight());
        });
        return b;
    }

    private JComponent toolbar() {
        JPanel tb = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tb.setBackground(Theme.TOOLBAR_BG);
        tb.setBorder(new EmptyBorder(2, 4, 2, 4));

        tb.add(tool("Session", Icons.session(), "New session (Ctrl+N)", e -> newSession()));
        tb.add(dropDown("Sessions", m -> {
            m.add(Ui.item("New session...", e -> newSession()));
            if (sessions.count() > 0) m.addSeparator();
            for (Component c : sessions.components()) {
                SessionPanel s = (SessionPanel) c;
                JCheckBoxMenuItem it = new JCheckBoxMenuItem(s.conn().label(), c == sessions.selected());
                it.addActionListener(e -> sessions.select(c));
                m.add(it);
            }
        }));
        tb.add(tool("Jobs", Icons.jobs(), "Job list", e -> with(s -> s.showJobs(null, "Jobs"))));
        tb.add(dropDown("Jobs", m -> {
            m.add(Ui.item("Job list", e -> with(s -> s.showJobs(null, "Jobs"))));
            m.add(Ui.item("Active jobs", e -> with(s -> s.showJobs("is_inactive = false", "Active jobs"))));
            m.add(Ui.item("Inactive jobs", e -> with(s -> s.showJobs("is_inactive = true", "Inactive jobs"))));
            m.add(Ui.item("Failed jobs", e -> with(s -> s.showJobs("upper(a_current_status) like '%FAIL%'", "Failed jobs"))));
        }));
        tb.add(tool("Copy", Icons.copy(), "Copy session – open a second session with the same login", e -> copySession()));
        tb.add(tool("Disconnect", Icons.disconnect(), "Close the current session", e -> disconnect()));
        tb.add(sep());

        dqlBtn = new ToolButton("DQL", Icons.dql(), "DQL window");
        dqlBtn.addActionListener(e -> with(sp -> sp.showDql()));
        tb.add(dqlBtn);
        tb.add(tool("API", Icons.api(), "API console", e -> with(sp -> sp.newApi())));
        tb.add(tool("DQL Script", Icons.dqlScript(), "DQL script", e -> with(s -> s.newScript(false))));
        tb.add(tool("API Script", Icons.apiScript(), "API script", e -> with(s -> s.newScript(true))));
        tb.add(sep());

        navBtn = new ToolButton("Navigator", Icons.navigator(), "Show / hide the Repository Navigator");
        navBtn.addActionListener(e -> with(s -> s.setNavigatorVisible(!s.navigatorVisible())));
        tb.add(navBtn);
        tb.add(tool("Object", Icons.object(), "Open object by id (Ctrl+O)", e -> with(sp -> sp.openObjectPrompt())));
        tb.add(tool("History", Icons.history(), "Query history (Ctrl+H)", e -> with(sp -> QueryListDialog.history(sp))));
        tb.add(sep());

        ToolButton favBtn = new ToolButton("Favorites", Icons.star(), "Favourite queries");
        favBtn.addActionListener(e -> {
            JPopupMenu m = new JPopupMenu();
            fillFavorites(m);
            m.show(favBtn, 0, favBtn.getHeight());
        });
        tb.add(favBtn);
        tb.add(sep());

        backBtn = new ToolButton("Back", Icons.arrow(true, true), "Previous query and its results");
        backBtn.setDisabledIcon(Icons.arrow(true, false));
        backBtn.addActionListener(e -> with(sp -> sp.back()));
        tb.add(backBtn);
        nextBtn = new ToolButton("Next", Icons.arrow(false, true), "Next query and its results");
        nextBtn.setDisabledIcon(Icons.arrow(false, false));
        nextBtn.addActionListener(e -> with(sp -> sp.next()));
        tb.add(nextBtn);
        tb.add(sep());

        runBtn = new ToolButton("Run", Icons.run(true), "Run (F5)");
        runBtn.setDisabledIcon(Icons.run(false));
        runBtn.addActionListener(e -> with(sp -> sp.runCurrent()));
        tb.add(runBtn);
        stepBtn = new ToolButton("Step", Icons.step(true), "Step (scripts)");
        stepBtn.setDisabledIcon(Icons.step(false));
        tb.add(stepBtn);
        pauseBtn = new ToolButton("Pause", Icons.pause(true), "Pause (scripts)");
        pauseBtn.setDisabledIcon(Icons.pause(false));
        tb.add(pauseBtn);
        stopBtn = new ToolButton("Stop", Icons.stop(true), "Stop");
        stopBtn.setDisabledIcon(Icons.stop(false));
        stopBtn.addActionListener(e -> with(sp -> sp.stopCurrent()));
        tb.add(stopBtn);
        return tb;
    }

    /** Refreshes toggle/enabled state of the toolbar from the current session. */
    void updateState() {
        SessionPanel s = current();
        boolean has = s != null;
        dqlBtn.setToggled(has && s.currentWindow() instanceof DqlPanel);
        navBtn.setToggled(has && s.navigatorVisible());
        navMenu.setSelected(!has || s.navigatorVisible());
        backBtn.setEnabled(has && s.canBack());
        nextBtn.setEnabled(has && s.canNext());
        boolean running = has && s.isRunning();
        runBtn.setEnabled(!running);
        stepBtn.setEnabled(false);
        pauseBtn.setEnabled(false);
        stopBtn.setEnabled(running);
        setTitle(App.NAME + " " + App.VERSION + (has ? " - [" + s.conn().label() + "]" : ""));
    }

    // ================================================================ sessions

    SessionPanel current() {
        Component c = sessions.selected();
        return c instanceof SessionPanel ? (SessionPanel) c : null;
    }

    private void with(Consumer<SessionPanel> action) {
        SessionPanel s = current();
        if (s == null) {
            newSession();
            s = current();
        }
        if (s != null) action.accept(s);
    }

    void newSession() {
        DocConnection c = LoginDialog.show(this);
        if (c == null) return;
        addSession(c);
    }

    private void addSession(DocConnection c) {
        SessionPanel p = new SessionPanel(c, sessionSeq++);
        p.setStateListener(this::updateState);
        sessions.add(c.label(), Icons.link(), p, true, p::close);
        updateState();
        SwingUtilities.invokeLater(() -> DfcSetup.checkRegistry(this));
    }

    private void disconnect() {
        SessionPanel s = current();
        if (s == null) return;
        s.close();
        sessions.removeTab(s);
        updateState();
    }

    private void exit() {
        for (Component c : sessions.components()) {
            if (c instanceof SessionPanel) ((SessionPanel) c).close();
        }
        dispose();
        System.exit(0);
    }
}
