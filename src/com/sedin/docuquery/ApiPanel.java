package com.sedin.docuquery;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/** Interactive API console (like iapi): dump,c,&lt;id&gt; / get,c,&lt;id&gt;,attr / set,c,&lt;id&gt;,attr + value ... */
final class ApiPanel extends JPanel implements SessionPanel.Executable {

    private final SessionPanel ctx;
    private final JTextField input = new JTextField();
    private final JTextField value = new JTextField();
    private final JComboBox<Api.Kind> mode = new JComboBox<>(Api.Kind.values());
    private final JTextArea output = new JTextArea();
    private final List<String> hist = new ArrayList<>();
    private int histPos = -1;

    ApiPanel(SessionPanel ctx) {
        super(new BorderLayout());
        this.ctx = ctx;
        input.setFont(Theme.mono);
        value.setFont(Theme.mono);
        output.setFont(Theme.mono);
        output.setEditable(false);
        output.setText("API console – examples:\n"
                + "  dump,c,0900000180000123\n"
                + "  get,c,0900000180000123,object_name\n"
                + "  set,c,0900000180000123,title        (enter the value in the Value field)\n"
                + "  save,c,0900000180000123\n"
                + "  getservermap,c,<docbase>\n"
                + "  ?,c,select r_object_id, object_name from dm_cabinet   (run DQL)\n"
                + "Mode 'AUTO' picks get/exec/set from the command name; override it if needed.\n"
                + "Use the Up/Down keys in the command field for history.\n\n");

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(6, 6, 6, 6));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(2, 2, 2, 2);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.gridx = 0;
        g.gridy = 0;
        form.add(new JLabel("Command:"), g);
        g.gridx = 1;
        g.weightx = 1;
        form.add(input, g);
        g.gridx = 2;
        g.weightx = 0;
        form.add(mode, g);
        g.gridx = 3;
        JButton run = Ui.smallButton("Execute", "Execute (Enter)", e -> execute());
        form.add(run, g);
        g.gridx = 0;
        g.gridy = 1;
        form.add(new JLabel("Value:"), g);
        g.gridx = 1;
        g.weightx = 1;
        form.add(value, g);
        g.gridx = 2;
        g.gridwidth = 2;
        g.weightx = 0;
        form.add(Ui.smallButton("Clear output", null, e -> output.setText("")), g);
        value.setToolTipText("Value for set / append / insert commands");

        add(form, BorderLayout.NORTH);
        add(new JScrollPane(output));

        input.addActionListener(e -> execute());
        value.addActionListener(e -> execute());
        input.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (hist.isEmpty()) return;
                if (e.getKeyCode() == KeyEvent.VK_UP) {
                    histPos = Math.min(hist.size() - 1, histPos + 1);
                    input.setText(hist.get(histPos));
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    histPos = Math.max(-1, histPos - 1);
                    input.setText(histPos < 0 ? "" : hist.get(histPos));
                }
            }
        });
    }

    void focusInput() {
        input.requestFocusInWindow();
    }

    @Override
    public void execute() {
        String line = input.getText().trim();
        if (line.isEmpty()) return;
        hist.remove(line);
        hist.add(0, line);
        histPos = -1;
        String val = value.getText();
        Api.Kind k = (Api.Kind) mode.getSelectedItem();
        String dql = Api.dqlOf(line);
        boolean isSet = k == Api.Kind.SET || (k == Api.Kind.AUTO && Api.needsValue(line));
        append("API> " + line + (isSet ? "\n     value: " + val : "") + "\n");
        ctx.status("Executing API command...");
        ctx.conn().run(s -> {
            if (dql != null) return QueryResult.execute(s, dql, 1000, null).toText(1000);
            return Api.execute(s, line, val, k);
        }, r -> {
            append(r + (r.endsWith("\n") ? "" : "\n") + "\n");
            ctx.status("API command executed");
            input.selectAll();
        }, t -> {
            append("[ERROR] " + DocConnection.message(t) + "\n\n");
            ctx.status("API command failed");
        });
    }

    @Override
    public void stop() {
        // single API calls cannot be interrupted
    }

    private void append(String s) {
        output.append(s);
        output.setCaretPosition(output.getDocument().getLength());
    }
}
