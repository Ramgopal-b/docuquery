package com.sedin.docuquery;

import java.awt.Component;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** Help > Diagnostics: configuration check, what every session is doing, and the log tail. */
final class Diagnostics {
    private Diagnostics() {}

    static void show(Component parent, List<Component> sessions) {
        Ui.showText(parent, "DocuQuery diagnostics", build(sessions));
    }

    static String build(List<Component> sessions) {
        StringBuilder b = new StringBuilder();
        b.append("DocuQuery ").append(App.VERSION).append('\n');
        b.append("Java       : ").append(System.getProperty("java.version")).append(" (")
                .append(System.getProperty("java.vendor")).append(")  ").append(System.getProperty("java.home")).append('\n');
        b.append("Program dir: ").append(DfcSetup.appHome().getAbsolutePath()).append('\n');
        File jar = new File(new File(DfcSetup.appHome(), "lib"), "dfc.jar");
        b.append("dfc.jar    : ").append(jar.isFile() ? jar.getAbsolutePath() + "  (" + jar.length() / 1024 + " KB)" : "MISSING").append('\n');
        b.append("DFC version: ").append(dfcVersion()).append('\n');

        File props = DfcSetup.propertiesFile();
        b.append("\n--- dfc.properties: ").append(props.getAbsolutePath()).append('\n');
        String text = "";
        try {
            text = new String(Files.readAllBytes(props.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            b.append("  NOT READABLE: ").append(e.getMessage()).append('\n');
        }
        String host = DfcSetup.prop(text, "dfc.docbroker.host[0]");
        String port = DfcSetup.prop(text, "dfc.docbroker.port[0]");
        String reg = DfcSetup.prop(text, "dfc.globalregistry.repository");
        String regUser = DfcSetup.prop(text, "dfc.globalregistry.username");
        String regPw = DfcSetup.prop(text, "dfc.globalregistry.password");
        b.append("  docbroker host     : ").append(host.isEmpty() ? "MISSING" : host).append('\n');
        b.append("  docbroker port     : ").append(port.isEmpty() ? "(default 1489)" : port).append('\n');
        b.append("  global registry    : ").append(reg.isEmpty() ? "(not set)" : reg).append('\n');
        b.append("  registry user      : ").append(regUser.isEmpty() ? "(not set)" : regUser).append('\n');
        b.append("  registry password  : ").append(regPw.isEmpty() ? "(EMPTY)" : "(set)").append('\n');
        b.append("  dfc.data.dir       : ").append(DfcSetup.prop(text, "dfc.data.dir")).append('\n');

        b.append("\n--- Checks\n");
        boolean any = false;
        if (host.isEmpty()) {
            b.append("  ! No docbroker host - set it with Edit > dfc.properties.\n");
            any = true;
        }
        if (!reg.isEmpty() && regPw.isEmpty()) {
            b.append("  ! dfc.globalregistry.repository is set but dfc.globalregistry.password is EMPTY.\n"
                    + "    DFC then tries to log in to the global registry and waits for time-outs, which makes\n"
                    + "    queries on custom types (e.g. cms_user_profile) very slow or seem to hang.\n"
                    + "    Fix: copy the encrypted password line from a working dfc.properties (dqMan PC or\n"
                    + "    Content Server), or remove / comment out the three dfc.globalregistry.* lines.\n"
                    + "    Then restart DocuQuery.\n");
            any = true;
        }
        if (!any) b.append("  (no problems found in dfc.properties)\n");

        b.append("\n--- Sessions\n");
        if (sessions.isEmpty()) b.append("  (none)\n");
        for (Component c : sessions) {
            if (c instanceof SessionPanel) {
                DocConnection con = ((SessionPanel) c).conn();
                b.append("  ").append(con.label()).append(": ").append(con.busyText()).append('\n');
            }
        }

        b.append("\n--- Log (last 60 lines of ").append(Log.file().getAbsolutePath()).append(")\n");
        b.append(Log.tail(60)).append('\n');
        return b.toString();
    }

    private static String dfcVersion() {
        try {
            Class<?> c = Class.forName("com.documentum.com.DfClientX");
            Object cx = c.getDeclaredConstructor().newInstance();
            Object v = c.getMethod("getDFCVersion").invoke(cx);
            return String.valueOf(v);
        } catch (Throwable t) {
            try {
                Package p = Class.forName("com.documentum.fc.client.IDfSession").getPackage();
                String v = p == null ? null : p.getImplementationVersion();
                return v == null ? "(unknown)" : v;
            } catch (Throwable t2) {
                return "(DFC not loaded)";
            }
        }
    }
}
