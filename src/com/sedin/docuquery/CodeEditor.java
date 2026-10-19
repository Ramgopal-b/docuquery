package com.sedin.docuquery;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Text editor with line numbers, undo/redo and DQL syntax highlighting. */
final class CodeEditor extends JPanel {

    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
            "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "LIKE", "IS", "NULL", "NULLSTRING", "NULLDATE",
            "NULLINT", "ORDER", "BY", "GROUP", "HAVING", "ASC", "DESC", "DISTINCT", "ALL", "ANY", "UPDATE", "SET",
            "CREATE", "DELETE", "OBJECT", "OBJECTS", "INSERT", "INTO", "VALUES", "REGISTER", "UNREGISTER",
            "EXECUTE", "ENABLE", "RETURN_TOP", "FETCH_ALL_RESULTS", "OPTIMIZE_TOP", "FOLDER", "CABINET",
            "DESCEND", "TYPE", "ALTER", "DROP", "GRANT", "REVOKE", "APPEND", "TRUNCATE", "LINK", "UNLINK",
            "MOVE", "TO", "WITH", "AS", "UNION", "EXISTS", "BETWEEN", "DATE", "TODAY", "NOW", "YESTERDAY",
            "TOMORROW", "SEARCH", "DOCUMENT", "CONTAINS", "TOPIC", "ALL_VERSIONS", "DELETED", "SUPERTYPE",
            "TABLE", "ON", "JOIN", "ADD", "MODIFY", "REPEATING", "PUBLIC", "PRIVATE", "LIMIT", "SQL_DEF",
            "TRUE", "FALSE", "FOR", "READ", "BROWSE", "WRITE", "DELETE_OBJECT", "USER", "GROUP", "ACL",
            "MAPPING", "TABLE", "HITS", "RETURN_RANGE", "NOFTDQL", "FTDQL", "ROW_BASED", "UNCOMMITTED_READ",
            "CHECK_FOR_DELETED", "FORCE_ORDER", "GROUP_LIST_LIMIT", "HIDE_SHARED_PARENT", "TRY_FTDQL_FIRST",
            "SETFILE", "PAGE_NO", "PAGE", "CONTENT_FORMAT", "WITHIN", "ENABLE"));

    private static final Set<String> FUNCTIONS = new HashSet<>(Arrays.asList(
            "UPPER", "LOWER", "SUBSTR", "COUNT", "MIN", "MAX", "AVG", "SUM", "ID", "MFILE_URL", "DATEADD",
            "DATEDIFF", "DATEFLOOR", "DATETOSTRING", "DATETOSTRING_LOCAL", "ANY", "ISCURRENT", "ISREPLICA",
            "ISPUBLIC", "LEFT", "RIGHT", "TRIM", "CAST"));

    private static final Pattern TOKENS = Pattern.compile(
            "(--[^\\n]*|/\\*[\\s\\S]*?(?:\\*/|$))|('(?:[^']|'')*'?)|(\\b\\d+(?:\\.\\d+)?\\b)|([A-Za-z_][A-Za-z0-9_]*)");

    private final JTextPane pane;
    private final Timer timer;
    private final boolean highlight;
    private final UndoManager undo = new UndoManager();
    private final SimpleAttributeSet plain = new SimpleAttributeSet();
    private final SimpleAttributeSet keyword = new SimpleAttributeSet();
    private final SimpleAttributeSet function = new SimpleAttributeSet();
    private final SimpleAttributeSet string = new SimpleAttributeSet();
    private final SimpleAttributeSet number = new SimpleAttributeSet();
    private final SimpleAttributeSet comment = new SimpleAttributeSet();

    CodeEditor(boolean dqlHighlight) {
        super(new BorderLayout());
        this.highlight = dqlHighlight;
        pane = new JTextPane() {
            @Override
            protected void paintComponent(Graphics g) {
                // white background + soft highlight of the caret line (like dqMan)
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
                try {
                    Rectangle2D r = modelToView2D(getCaretPosition());
                    if (r != null && getSelectionStart() == getSelectionEnd()) {
                        g.setColor(new Color(0xFFFBE6));
                        g.fillRect(0, (int) r.getY(), getWidth(), (int) r.getHeight());
                    }
                } catch (BadLocationException ignored) {
                    // ignore
                }
                super.paintComponent(g);
            }

            @Override
            public boolean getScrollableTracksViewportWidth() {
                Container p = getParent();
                return p == null || getUI().getPreferredSize(this).width <= p.getSize().width;
            }
        };
        pane.setFont(Theme.mono);
        pane.setMargin(new Insets(3, 5, 3, 5));
        pane.setOpaque(false);
        pane.setBackground(Color.WHITE);

        StyleConstants.setForeground(plain, new Color(0x1F2328));
        StyleConstants.setForeground(keyword, new Color(0x00008B));
        StyleConstants.setBold(keyword, false);
        StyleConstants.setForeground(function, new Color(0x00627A));
        StyleConstants.setForeground(string, new Color(0x2E8B57));
        StyleConstants.setForeground(number, new Color(0x1750EB));
        StyleConstants.setForeground(comment, new Color(0x8C8C8C));
        StyleConstants.setItalic(comment, true);

        JScrollPane sp = new JScrollPane(pane);
        sp.getViewport().setBackground(Color.WHITE);
        sp.setBorder(Theme.box());
        LineGutter gutter = new LineGutter(pane);
        sp.setRowHeaderView(gutter);
        pane.addCaretListener(e -> {
            pane.repaint();
            gutter.repaint();
        });
        add(sp);

        timer = new Timer(150, e -> highlight());
        timer.setRepeats(false);
        pane.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                timer.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                timer.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // attribute changes only
            }
        });
        pane.getDocument().addUndoableEditListener(e -> {
            if (e.getEdit() instanceof AbstractDocument.DefaultDocumentEvent
                    && ((AbstractDocument.DefaultDocumentEvent) e.getEdit()).getType() == DocumentEvent.EventType.CHANGE) {
                return; // ignore colouring
            }
            undo.addEdit(e.getEdit());
        });
        Ui.bind(pane, Ui.ctrl(KeyEvent.VK_Z), "undo", () -> {
            try {
                if (undo.canUndo()) undo.undo();
            } catch (CannotUndoException ignored) {
                // ignore
            }
        });
        Ui.bind(pane, Ui.ctrl(KeyEvent.VK_Y), "redo", () -> {
            try {
                if (undo.canRedo()) undo.redo();
            } catch (CannotRedoException ignored) {
                // ignore
            }
        });
    }

    JTextPane pane() {
        return pane;
    }

    String getText() {
        return pane.getText();
    }

    void setText(String t) {
        pane.setText(t);
        pane.setCaretPosition(Math.min(t.length(), pane.getDocument().getLength()));
        highlight();
    }

    /** "line : column" of the caret, shown in the status bar. */
    String caretText() {
        int pos = pane.getCaretPosition();
        Element root = pane.getDocument().getDefaultRootElement();
        int line = root.getElementIndex(pos);
        int col = pos - root.getElement(line).getStartOffset();
        return (line + 1) + " : " + (col + 1);
    }

    String getSelectedOrAll() {
        String s = pane.getSelectedText();
        return (s != null && !s.trim().isEmpty()) ? s : pane.getText();
    }

    @Override
    public void requestFocus() {
        pane.requestFocusInWindow();
    }

    private void highlight() {
        StyledDocument doc = pane.getStyledDocument();
        String text;
        try {
            text = doc.getText(0, doc.getLength());
        } catch (BadLocationException e) {
            return;
        }
        doc.setCharacterAttributes(0, text.length(), plain, true);
        if (!highlight) return;
        Matcher m = TOKENS.matcher(text);
        while (m.find()) {
            AttributeSet st = null;
            if (m.group(1) != null) st = comment;
            else if (m.group(2) != null) st = string;
            else if (m.group(3) != null) st = number;
            else if (m.group(4) != null) {
                String w = m.group(4).toUpperCase(Locale.ROOT);
                if (KEYWORDS.contains(w)) st = keyword;
                else if (FUNCTIONS.contains(w)) st = function;
            }
            if (st != null) doc.setCharacterAttributes(m.start(), m.end() - m.start(), st, true);
        }
    }

    /** Row header that paints line numbers. */
    private static final class LineGutter extends JComponent {
        private final JTextComponent tc;

        LineGutter(JTextComponent tc) {
            this.tc = tc;
            setFont(Theme.mono.deriveFont(12f));
            setForeground(new Color(0x9A9A9A));
            tc.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    changed();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    changed();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    repaint();
                }
            });
        }

        private void changed() {
            SwingUtilities.invokeLater(() -> {
                revalidate();
                repaint();
            });
        }

        @Override
        public Dimension getPreferredSize() {
            int lines = tc.getDocument().getDefaultRootElement().getElementCount();
            int digits = Math.max(3, String.valueOf(lines).length());
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.charWidth('0') * digits + 18, tc.getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Rectangle clip = g.getClipBounds();
            g.setColor(Color.WHITE);
            g.fillRect(clip.x, clip.y, clip.width, clip.height);
            g.setColor(new Color(0xE0E0E0));
            g.drawLine(getWidth() - 1, clip.y, getWidth() - 1, clip.y + clip.height);
            ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(getForeground());
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            Element root = tc.getDocument().getDefaultRootElement();
            int start = tc.viewToModel2D(new Point(0, clip.y));
            int end = tc.viewToModel2D(new Point(0, clip.y + clip.height));
            int l0 = root.getElementIndex(Math.max(0, start));
            int l1 = root.getElementIndex(Math.max(0, end));
            int caretLine = root.getElementIndex(tc.getCaretPosition());
            for (int i = l0; i <= l1; i++) {
                try {
                    Rectangle2D r = tc.modelToView2D(root.getElement(i).getStartOffset());
                    if (r == null) continue;
                    if (i == caretLine) {
                        g.setColor(new Color(0xF5C518));
                        g.fillRect(getWidth() - 4, (int) r.getY(), 3, (int) r.getHeight());
                        g.setColor(getForeground());
                    }
                    String s = String.valueOf(i + 1);
                    int x = getWidth() - fm.stringWidth(s) - 9;
                    int y = (int) (r.getY() + (r.getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                    g.drawString(s, x, y);
                } catch (BadLocationException ignored) {
                    // ignore
                }
            }
        }
    }
}
