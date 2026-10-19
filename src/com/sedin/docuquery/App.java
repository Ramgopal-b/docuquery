package com.sedin.docuquery;

import javax.swing.*;

/** Entry point for DocuQuery – a DQL / API client for OpenText Documentum built on DFC. */
public final class App {
    public static final String NAME = "DocuQuery";
    public static final String VERSION = "1.1.2";

    private App() {}

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        DfcSetup.useProgramFolderOnly();
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // fall back to default look and feel
            }
            Theme.install();
            MainFrame f = new MainFrame();
            f.setVisible(true);
            f.newSession();
        });
    }
}
