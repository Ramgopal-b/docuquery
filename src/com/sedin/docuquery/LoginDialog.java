package com.sedin.docuquery;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;

/** Repository login. Repository list comes from the docbroker configured in dfc.properties. */
final class LoginDialog extends JDialog {

    private final JComboBox<String> docbase = new JComboBox<>();
    private final JTextField user = new JTextField(22);
    private final JPasswordField password = new JPasswordField(22);
    private final JTextField domain = new JTextField(22);
    private final JLabel info = new JLabel(" ");
    private final JButton connect = new JButton("Connect");
    private DocConnection result;

    LoginDialog(Frame owner) {
        super(owner, "Connect to repository", true);
        docbase.setEditable(true);
        docbase.setPrototypeDisplayValue("XXXXXXXXXXXXXXXXXXXXXXXX");
        String lastDb = Prefs.get("login.docbase", "");
        if (!lastDb.isEmpty()) {
            docbase.addItem(lastDb);
            docbase.setSelectedItem(lastDb);
        }
        user.setText(Prefs.get("login.user", ""));
        domain.setText(Prefs.get("login.domain", ""));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(14, 16, 6, 16));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;
        addRow(form, g, 0, "Repository:", docbase);
        addRow(form, g, 1, "User name:", user);
        addRow(form, g, 2, "Password:", password);
        addRow(form, g, 3, "Domain (optional):", domain);
        g.gridx = 0;
        g.gridy = 4;
        g.gridwidth = 2;
        info.setForeground(new Color(0x666666));
        form.add(info, g);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        JButton props = new JButton("dfc.properties...");
        props.setToolTipText("View / edit the DFC configuration (docbroker host and port)");
        props.addActionListener(e -> DfcSetup.editProperties(this));
        buttons.add(props);
        if (!DfcSetup.dfcAvailable()) {
            JButton setup = new JButton("DFC setup...");
            setup.setToolTipText("Copy dfc.jar and dfc.properties from your DFC installation into DocuQuery");
            setup.addActionListener(e -> DfcSetup.wizard(this));
            buttons.add(setup);
        }
        buttons.add(connect);
        buttons.add(cancel);
        connect.addActionListener(e -> doConnect());
        cancel.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(connect);
        getRootPane().registerKeyboardAction(e -> dispose(), Ui.key(KeyEvent.VK_ESCAPE),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        JLabel banner = new JLabel(App.NAME + "  –  DQL & API client for Documentum");
        banner.setFont(banner.getFont().deriveFont(Font.BOLD, banner.getFont().getSize() + 2f));
        banner.setBorder(new EmptyBorder(12, 20, 0, 16));
        add(banner, BorderLayout.NORTH);
        add(form);
        add(buttons, BorderLayout.SOUTH);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
        loadDocbases();
        SwingUtilities.invokeLater(() -> (user.getText().isEmpty() ? user : password).requestFocusInWindow());
    }

    private static void addRow(JPanel p, GridBagConstraints g, int row, String label, JComponent c) {
        g.gridx = 0;
        g.gridy = row;
        g.weightx = 0;
        p.add(new JLabel(label), g);
        g.gridx = 1;
        g.weightx = 1;
        p.add(c, g);
    }

    private void loadDocbases() {
        if (!DfcSetup.dfcAvailable()) {
            info.setForeground(new Color(0xB00020));
            info.setText("<html>DFC (dfc.jar) was not found. Click <b>DFC setup...</b> to copy it<br>"
                    + "from your DFC installation, then restart DocuQuery.</html>");
            connect.setEnabled(false);
            pack();
            return;
        }
        info.setText("Reading repositories from docbroker...");
        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                return DocConnection.listDocbases();
            }

            @Override
            protected void done() {
                try {
                    List<String> l = get();
                    Object sel = docbase.getSelectedItem();
                    docbase.removeAllItems();
                    for (String s : l) docbase.addItem(s);
                    if (sel != null && !sel.toString().isEmpty()) docbase.setSelectedItem(sel);
                    info.setText(l.size() + " repositories found");
                } catch (Exception ex) {
                    Throwable c = ex.getCause() == null ? ex : ex.getCause();
                    info.setText("<html>Could not read docbroker list – type the repository name.<br>"
                            + escape(DocConnection.message(c)) + "</html>");
                    pack();
                }
            }
        }.execute();
    }

    private static String escape(String s) {
        String t = s.length() > 200 ? s.substring(0, 200) + "…" : s;
        return t.replace("&", "&amp;").replace("<", "&lt;").replace("\n", "<br>");
    }

    private void doConnect() {
        Object db = docbase.getEditor().getItem();
        String d = db == null ? "" : db.toString().trim();
        String u = user.getText().trim();
        if (d.isEmpty() || u.isEmpty()) {
            info.setText("Repository and user name are required.");
            return;
        }
        connect.setEnabled(false);
        info.setText("Connecting to " + d + "...");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        DocConnection c = new DocConnection(d, u, domain.getText().trim());
        c.connectAsync(new String(password.getPassword()), () -> {
            setCursor(Cursor.getDefaultCursor());
            Prefs.put("login.docbase", d);
            Prefs.put("login.user", u);
            Prefs.put("login.domain", domain.getText().trim());
            result = c;
            dispose();
        }, t -> {
            setCursor(Cursor.getDefaultCursor());
            c.disconnect();
            connect.setEnabled(true);
            info.setText("Login failed.");
            Ui.error(this, "Login failed", t);
        });
    }

    /** Shows the dialog and returns a connected session, or null if cancelled. */
    static DocConnection show(Frame owner) {
        LoginDialog d = new LoginDialog(owner);
        d.setVisible(true);
        return d.result;
    }
}
