package com.sedin.docuquery;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/** Edit > dfc.properties, and the DFC setup wizard used when DFC is missing. */
final class DfcSetup {
    private DfcSetup() {}

    /** Folder that contains docuquery.jar (or the working directory when run from classes). */
    static File appHome() {
        try {
            File f = new File(App.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return f.isFile() ? f.getParentFile() : new File(System.getProperty("user.dir"));
        } catch (Exception e) {
            return new File(System.getProperty("user.dir"));
        }
    }

    /** The only dfc.properties DocuQuery uses: config\dfc.properties in the program folder. */
    static File propertiesFile() {
        return new File(new File(appHome(), "config"), "dfc.properties");
    }

    /**
     * Called at start-up, before DFC is loaded: points DFC to the program folder's
     * config\dfc.properties and keeps DFC's data/cache folder inside the program folder (data\).
     */
    static void useProgramFolderOnly() {
        File f = propertiesFile();
        System.setProperty("dfc.properties.file", f.getAbsolutePath());
        if (!f.isFile()) return;
        try {
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            String data = new File(appHome(), "data").getAbsolutePath().replace('\\', '/');
            if (!data.equals(prop(text, "dfc.data.dir"))) {
                Files.createDirectories(Paths.get(data));
                Files.write(f.toPath(), setProp(text, "dfc.data.dir", data).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            // read-only folder: DFC falls back to its defaults
        }
    }

    /**
     * After login: an incomplete global registry (repository set, password empty) makes DFC wait for
     * time-outs on custom types. Offer to switch those lines off.
     */
    static void checkRegistry(Component parent) {
        File f = propertiesFile();
        try {
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            String reg = prop(text, "dfc.globalregistry.repository");
            if (reg.isEmpty() || !prop(text, "dfc.globalregistry.password").isEmpty()) return;
            String key = f.getAbsolutePath() + "|" + reg;
            if (key.equals(Prefs.get("registry.ignored", ""))) return;
            Object[] opts = {"Disable global registry (recommended)", "Keep as is"};
            int r = JOptionPane.showOptionDialog(parent,
                    "dfc.properties sets dfc.globalregistry.repository=" + reg + "\n"
                            + "but dfc.globalregistry.password is EMPTY.\n\n"
                            + "DFC then keeps trying to log in to the global registry and waits for time-outs,\n"
                            + "so queries and updates on custom types (e.g. cms_user_profile) hang or are very slow.\n\n"
                            + "Disable the dfc.globalregistry lines now? (You can add the encrypted password later\n"
                            + "with Edit > dfc.properties.) DocuQuery must be restarted afterwards.",
                    "Global registry incomplete", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, opts, opts[0]);
            if (r != 0) {
                Prefs.put("registry.ignored", key);
                return;
            }
            Files.copy(f.toPath(), new File(f.getPath() + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
            StringBuilder out = new StringBuilder();
            for (String line : text.split("\\r?\\n", -1)) {
                out.append(line.trim().startsWith("dfc.globalregistry.") ? "# disabled by DocuQuery: " + line : line).append('\n');
            }
            Files.write(f.toPath(), out.toString().getBytes(StandardCharsets.UTF_8));
            Log.info("global registry lines disabled in " + f);
            JOptionPane.showMessageDialog(parent, "Done (backup: dfc.properties.bak).\nPlease close and start DocuQuery again.",
                    "Global registry", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            Log.error("registry check failed", e);
        }
    }

    static boolean dfcAvailable() {
        try {
            Class.forName("com.documentum.com.DfClientX");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------ Edit > dfc.properties

    static void editProperties(Component parent) {
        File f = propertiesFile();
        String text;
        try {
            text = f.exists() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : template();
        } catch (IOException e) {
            Ui.error(parent, "dfc.properties", e);
            return;
        }
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog d = new JDialog(owner, "dfc.properties – " + f.getAbsolutePath(), Dialog.ModalityType.MODELESS);
        CodeEditor ed = new CodeEditor(false);
        ed.setText(text);
        // ---- simple form for the docbroker (Connection Broker) host and port
        JTextField host = new JTextField(prop(text, "dfc.docbroker.host[0]"), 24);
        JTextField port = new JTextField(prop(text, "dfc.docbroker.port[0]").isEmpty() ? "1489" : prop(text, "dfc.docbroker.port[0]"), 6);
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        form.add(new JLabel("Docbroker host name:"));
        form.add(host);
        form.add(new JLabel("Port:"));
        form.add(port);
        form.add(Ui.smallButton("Test connection", "Check that this PC can reach the docbroker host and port", e ->
                testConnection(form, host.getText().trim(), port.getText().trim())));
        form.add(Ui.smallButton("Apply to file text", "Write host and port into the text below", e ->
                ed.setText(setProp(setProp(ed.getText(), "dfc.docbroker.host[0]", host.getText().trim()),
                        "dfc.docbroker.port[0]", port.getText().trim()))));
        JLabel hint = new JLabel(f.exists() ? "Changes take effect after restarting DocuQuery."
                : "File does not exist yet – Save creates it. Restart DocuQuery afterwards.");
        hint.setBorder(new EmptyBorder(4, 6, 4, 6));
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(Ui.smallButton("Save", "Save dfc.properties", e -> {
            try {
                if (f.getParentFile() != null) Files.createDirectories(f.getParentFile().toPath());
                if (f.exists()) Files.copy(f.toPath(), new File(f.getPath() + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
                String out = ed.getText();
                if (!host.getText().trim().isEmpty()) {
                    out = setProp(setProp(out, "dfc.docbroker.host[0]", host.getText().trim()),
                            "dfc.docbroker.port[0]", port.getText().trim());
                    ed.setText(out);
                }
                Files.write(f.toPath(), out.getBytes(StandardCharsets.UTF_8));
                hint.setText("Saved (backup: dfc.properties.bak). Restart DocuQuery to apply.");
            } catch (IOException ex) {
                Ui.error(d, "Save dfc.properties", ex);
            }
        }));
        south.add(Ui.smallButton("Close", null, e -> d.dispose()));
        JPanel north = new JPanel(new BorderLayout());
        north.add(form, BorderLayout.NORTH);
        north.add(hint, BorderLayout.SOUTH);
        d.add(north, BorderLayout.NORTH);
        d.add(ed);
        d.add(south, BorderLayout.SOUTH);
        d.setSize(820, 560);
        d.setLocationRelativeTo(parent);
        d.setVisible(true);
    }

    /** Opens a plain TCP connection to the docbroker – shows whether the network path is open. */
    static void testConnection(Component parent, String host, String port) {
        if (host.isEmpty()) {
            Ui.error(parent, "Test connection", "Enter the docbroker host name or IP first.");
            return;
        }
        int p;
        try {
            p = port.isEmpty() ? 1489 : Integer.parseInt(port);
        } catch (NumberFormatException e) {
            Ui.error(parent, "Test connection", "Port must be a number (normally 1489).");
            return;
        }
        parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                long t0 = System.currentTimeMillis();
                try (java.net.Socket so = new java.net.Socket()) {
                    so.connect(new java.net.InetSocketAddress(host, p), 5000);
                    return "OK – " + host + ":" + p + " is reachable (" + (System.currentTimeMillis() - t0) + " ms).\n"
                            + "If login still fails, the docbroker service itself may be down, or the repository\n"
                            + "ports (behind the docbroker) are blocked – ask the Documentum administrator.";
                } catch (java.net.UnknownHostException e) {
                    return "FAILED – host name '" + host + "' is unknown on this PC (DNS).\nUse the IP address or check the name.";
                } catch (java.net.SocketTimeoutException e) {
                    return "FAILED – no answer from " + host + ":" + p + " within 5 seconds.\n"
                            + "Usually: VPN not connected, firewall blocks port " + p + ", or wrong IP / server down.";
                } catch (Exception e) {
                    return "FAILED – " + host + ":" + p + ": " + e.getMessage() + "\n"
                            + "Connection refused = server reachable but no docbroker listening on that port.";
                }
            }

            @Override
            protected void done() {
                parent.setCursor(Cursor.getDefaultCursor());
                try {
                    String r = get();
                    JOptionPane.showMessageDialog(parent, r, "Test connection",
                            r.startsWith("OK") ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }.execute();
    }

    /** Value of a key in properties text ("" if missing). */
    static String prop(String text, String key) {
        for (String line : text.split("\\r?\\n")) {
            String t = line.trim();
            if (t.startsWith("#") || !t.contains("=")) continue;
            if (t.substring(0, t.indexOf('=')).trim().equals(key)) return t.substring(t.indexOf('=') + 1).trim();
        }
        return "";
    }

    /** Replaces (or appends) key=value in properties text, keeping everything else unchanged. */
    static String setProp(String text, String key, String value) {
        String[] lines = text.split("\\r?\\n", -1);
        boolean done = false;
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].trim();
            if (t.startsWith("#") || !t.contains("=")) continue;
            if (t.substring(0, t.indexOf('=')).trim().equals(key)) {
                lines[i] = key + "=" + value;
                done = true;
            }
        }
        String out = String.join("\n", lines);
        if (!done) out = (out.endsWith("\n") || out.isEmpty() ? out : out + "\n") + key + "=" + value + "\n";
        return out;
    }

    static String template() {
        return "# DFC configuration for DocuQuery\n"
                + "dfc.docbroker.host[0]=your-docbroker-host\n"
                + "dfc.docbroker.port[0]=1489\n"
                + "dfc.globalregistry.repository=EDMS\n"
                + "dfc.globalregistry.username=dm_bof_registry\n"
                + "dfc.globalregistry.password=\n"
                + "dfc.data.dir=C:/Documentum\n";
    }

    // ------------------------------------------------------------ setup wizard

    private static final String[] DFC_DIRS = {
            System.getenv("DOCUMENTUM_SHARED"),
            "C:\\Program Files\\Documentum\\Shared",
            "C:\\Program Files (x86)\\Documentum\\Shared",
            "C:\\Documentum\\Shared"};
    private static final String[] CONFIG_DIRS = {
            System.getenv("DOCUMENTUM") == null ? null : System.getenv("DOCUMENTUM") + "\\config",
            "C:\\Documentum\\config",
            "C:\\ProgramData\\Documentum\\config"};

    private static String firstExisting(String[] dirs, String file) {
        for (String d : dirs) if (d != null && new File(d, file).exists()) return d;
        return "";
    }

    /** Copies DFC jars and config into the app's lib/ and config/ folders. */
    static void wizard(Component parent) {
        JTextField dfcDir = new JTextField(firstExisting(DFC_DIRS, "dfc.jar"), 40);
        JTextField cfgDir = new JTextField(firstExisting(CONFIG_DIRS, "dfc.properties"), 40);
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.anchor = GridBagConstraints.WEST;
        g.gridx = 0;
        g.gridy = 0;
        g.gridwidth = 3;
        p.add(new JLabel("<html>DocuQuery needs the OpenText DFC libraries and your <b>dfc.properties</b>.<br>"
                + "Point to the DFC folder that contains <b>dfc.jar</b> (from your DFC installation)<br>"
                + "and to the folder that contains <b>dfc.properties</b>. They are copied into:<br>"
                + appHome().getAbsolutePath() + "\\lib  and  \\config</html>"), g);
        g.gridwidth = 1;
        row(p, g, 1, "DFC folder (dfc.jar):", dfcDir);
        row(p, g, 2, "Config folder (dfc.properties):", cfgDir);
        while (true) {
            int r = JOptionPane.showConfirmDialog(parent, p, "DFC setup", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (r != JOptionPane.OK_OPTION) return;
            File dfc = new File(dfcDir.getText().trim());
            File cfg = new File(cfgDir.getText().trim());
            if (!new File(dfc, "dfc.jar").exists()) {
                Ui.error(parent, "DFC setup", "dfc.jar was not found in " + dfc);
                continue;
            }
            if (!new File(cfg, "dfc.properties").exists()) {
                Ui.error(parent, "DFC setup", "dfc.properties was not found in " + cfg);
                continue;
            }
            try {
                List<String> copied = copyDfc(dfc, cfg, appHome());
                JOptionPane.showMessageDialog(parent, "Copied " + copied.size() + " files.\n\n"
                        + "Please close DocuQuery and start it again with run.bat.", "DFC setup", JOptionPane.INFORMATION_MESSAGE);
                return;
            } catch (IOException e) {
                Ui.error(parent, "DFC setup", e);
                return;
            }
        }
    }

    private static void row(JPanel p, GridBagConstraints g, int y, String label, JTextField f) {
        g.gridy = y;
        g.gridx = 0;
        p.add(new JLabel(label), g);
        g.gridx = 1;
        p.add(f, g);
        g.gridx = 2;
        p.add(Ui.smallButton("...", "Browse", e -> {
            JFileChooser fc = new JFileChooser(f.getText());
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(p) == JFileChooser.APPROVE_OPTION) f.setText(fc.getSelectedFile().getAbsolutePath());
        }), g);
    }

    static List<String> copyDfc(File dfcDir, File cfgDir, File home) throws IOException {
        List<String> out = new ArrayList<>();
        Path lib = home.toPath().resolve("lib");
        Path libLib = lib.resolve("lib");
        Path conf = home.toPath().resolve("config");
        Files.createDirectories(libLib);
        Files.createDirectories(conf);
        out.addAll(copyJars(dfcDir.toPath(), lib));
        File sub = new File(dfcDir, "lib");
        if (sub.isDirectory()) out.addAll(copyJars(sub.toPath(), libLib));
        String[] cfgFiles = {"dfc.properties", "dfc.keystore", "dbor.properties", "log4j.properties", "log4j2.properties",
                "log4j2.xml", "dfcfull.properties"};
        for (String n : cfgFiles) {
            File f = new File(cfgDir, n);
            if (f.isFile()) {
                Files.copy(f.toPath(), conf.resolve(n), StandardCopyOption.REPLACE_EXISTING);
                out.add(n);
            }
        }
        return out;
    }

    private static List<String> copyJars(Path from, Path to) throws IOException {
        List<String> out = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(from, "*.jar")) {
            for (Path p : ds) {
                Files.copy(p, to.resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                out.add(p.getFileName().toString());
            }
        }
        return out;
    }
}
