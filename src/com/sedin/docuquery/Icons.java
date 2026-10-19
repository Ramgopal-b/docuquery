package com.sedin.docuquery;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/** Toolbar and tree icons, drawn with Java2D (no image files needed). */
final class Icons {
    private static final Color RED = new Color(0xC8102E);
    private static final Color DARK_RED = new Color(0x8E1B2A);
    private static final Color GREY = new Color(0xA0A0A0);
    private static final Color INK = new Color(0x2B2B2B);

    private Icons() {}

    private interface Painter {
        void paint(Graphics2D g, int s);
    }

    private static ImageIcon make(int s, Painter p) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        p.paint(g, s);
        g.dispose();
        return new ImageIcon(img);
    }

    // ------------------------------------------------------------ primitives

    private static void page(Graphics2D g, float x, float y, float w, float h, Color fill, Color line) {
        float c = w * 0.3f;
        GeneralPath p = new GeneralPath();
        p.moveTo(x, y);
        p.lineTo(x + w - c, y);
        p.lineTo(x + w, y + c);
        p.lineTo(x + w, y + h);
        p.lineTo(x, y + h);
        p.closePath();
        g.setColor(fill);
        g.fill(p);
        g.setColor(line);
        g.setStroke(new BasicStroke(1.1f));
        g.draw(p);
        GeneralPath fold = new GeneralPath();
        fold.moveTo(x + w - c, y);
        fold.lineTo(x + w - c, y + c);
        fold.lineTo(x + w, y + c);
        g.draw(fold);
    }

    private static void band(Graphics2D g, float x, float y, float w, String text, Color bg) {
        g.setColor(bg);
        g.fill(new Rectangle2DF(x, y, w, 8).shape());
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 7));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, x + (w - fm.stringWidth(text)) / 2f, y + 7f);
    }

    /** tiny helper to avoid importing Rectangle2D.Float everywhere */
    private static final class Rectangle2DF {
        final java.awt.geom.Rectangle2D.Float r;

        Rectangle2DF(float x, float y, float w, float h) {
            r = new java.awt.geom.Rectangle2D.Float(x, y, w, h);
        }

        Shape shape() {
            return r;
        }
    }

    private static void badge(Graphics2D g, float cx, float cy, float r, Color bg, boolean plus) {
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Float(cx - r - 1, cy - r - 1, 2 * r + 2, 2 * r + 2));
        g.setColor(bg);
        g.fill(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
        if (plus) {
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1.6f));
            g.drawLine(Math.round(cx - r / 2), Math.round(cy), Math.round(cx + r / 2), Math.round(cy));
            g.drawLine(Math.round(cx), Math.round(cy - r / 2), Math.round(cx), Math.round(cy + r / 2));
        }
    }

    private static void gear(Graphics2D g, float cx, float cy, float r, Color c) {
        g.setColor(c);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            Graphics2D t = (Graphics2D) g.create();
            t.rotate(a, cx, cy);
            t.fill(new java.awt.geom.Rectangle2D.Float(cx - 1.8f, cy - r - 2.2f, 3.6f, 4f));
            t.dispose();
        }
        g.fill(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Float(cx - r * 0.4f, cy - r * 0.4f, r * 0.8f, r * 0.8f));
    }

    // ------------------------------------------------------------ toolbar icons (24 px)

    static Icon session() {
        return make(24, (g, s) -> {
            page(g, 4, 2, 13, 17, RED, DARK_RED);
            badge(g, 17, 17, 5, INK, true);
        });
    }

    static Icon jobs() {
        return make(24, (g, s) -> {
            gear(g, 10, 10, 6, RED);
            badge(g, 17, 17, 5, INK, true);
        });
    }

    static Icon copy() {
        return make(24, (g, s) -> {
            page(g, 3, 2, 12, 15, new Color(0xF4C7CE), RED);
            page(g, 9, 7, 12, 15, RED, DARK_RED);
        });
    }

    static Icon disconnect() {
        return make(24, (g, s) -> {
            g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(RED);
            g.draw(new RoundRectangle2D.Float(2, 11, 9, 6, 6, 6));
            g.setColor(INK);
            g.draw(new RoundRectangle2D.Float(13, 6, 9, 6, 6, 6));
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(12, 15, 15, 18);
            g.drawLine(9, 4, 10, 8);
            g.drawLine(15, 19, 17, 21);
        });
    }

    static Icon doc(String label, boolean stacked, Color bandColor) {
        return make(24, (g, s) -> {
            if (stacked) page(g, 7, 1, 14, 17, Color.WHITE, new Color(0x6B6B6B));
            page(g, 3, stacked ? 5 : 2, 15, 19, Color.WHITE, new Color(0x6B6B6B));
            band(g, 1, stacked ? 10 : 9, 19, label, bandColor);
        });
    }

    static Icon dql() {
        return doc("DQL", false, RED);
    }

    static Icon api() {
        return doc("API", false, DARK_RED);
    }

    static Icon dqlScript() {
        return doc("DQL", true, RED);
    }

    static Icon apiScript() {
        return doc("API", true, DARK_RED);
    }

    static Icon navigator() {
        return make(24, (g, s) -> {
            Color o = new Color(0xE8A23A);
            Color ol = new Color(0xB9771B);
            g.setColor(new Color(0x7A7A7A));
            g.setStroke(new BasicStroke(1.2f));
            g.drawLine(7, 8, 7, 19);
            g.drawLine(7, 13, 13, 13);
            g.drawLine(7, 19, 13, 19);
            int[][] boxes = {{3, 2, 9, 6}, {13, 10, 8, 6}, {13, 16, 8, 6}};
            for (int[] b : boxes) {
                g.setColor(o);
                g.fillRect(b[0], b[1], b[2], b[3]);
                g.setColor(ol);
                g.drawRect(b[0], b[1], b[2], b[3]);
            }
        });
    }

    static Icon object() {
        return make(24, (g, s) -> {
            g.setColor(new Color(0x7A7A7A));
            g.setStroke(new BasicStroke(1.3f));
            g.drawLine(6, 6, 17, 7);
            g.drawLine(6, 6, 9, 18);
            g.drawLine(17, 7, 9, 18);
            g.setColor(new Color(0x1E88E5));
            g.fill(new Ellipse2D.Float(2, 2, 8, 8));
            g.setColor(new Color(0x43A047));
            g.fill(new Ellipse2D.Float(13, 3, 8, 8));
            g.setColor(new Color(0xE53935));
            g.fill(new Ellipse2D.Float(5, 14, 8, 8));
        });
    }

    static Icon history() {
        return make(24, (g, s) -> {
            g.setColor(new Color(0x6D4C41));
            g.fillRect(5, 2, 14, 2);
            g.fillRect(5, 20, 14, 2);
            GeneralPath p = new GeneralPath();
            p.moveTo(6, 4);
            p.lineTo(18, 4);
            p.lineTo(13, 12);
            p.lineTo(18, 20);
            p.lineTo(6, 20);
            p.lineTo(11, 12);
            p.closePath();
            g.setColor(new Color(0xFFF3E0));
            g.fill(p);
            g.setColor(new Color(0x8D6E63));
            g.draw(p);
            g.setColor(new Color(0xF57C00));
            GeneralPath sand = new GeneralPath();
            sand.moveTo(8, 19);
            sand.lineTo(16, 19);
            sand.lineTo(12, 14);
            sand.closePath();
            g.fill(sand);
            g.fillRect(8, 5, 8, 2);
        });
    }

    static Icon star() {
        return make(24, (g, s) -> {
            int[] xs = new int[10];
            int[] ys = new int[10];
            for (int i = 0; i < 10; i++) {
                double r = i % 2 == 0 ? 10 : 4.3;
                double a = Math.PI / 2 + i * Math.PI / 5;
                xs[i] = (int) Math.round(12 + r * Math.cos(a));
                ys[i] = (int) Math.round(13 - r * Math.sin(a));
            }
            g.setColor(new Color(0xF6C343));
            g.fillPolygon(xs, ys, 10);
            g.setColor(new Color(0xC9961A));
            g.drawPolygon(xs, ys, 10);
        });
    }

    static Icon arrow(boolean back, boolean enabled) {
        return make(24, (g, s) -> {
            g.setColor(enabled ? new Color(0xD7263D) : new Color(0xC4C4C4));
            g.fill(new Ellipse2D.Float(2, 2, 20, 20));
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (back) {
                g.drawLine(7, 12, 17, 12);
                g.drawLine(7, 12, 11, 8);
                g.drawLine(7, 12, 11, 16);
            } else {
                g.drawLine(7, 12, 17, 12);
                g.drawLine(17, 12, 13, 8);
                g.drawLine(17, 12, 13, 16);
            }
        });
    }

    static Icon run(boolean enabled) {
        return make(24, (g, s) -> {
            g.setColor(enabled ? new Color(0x2E9E44) : GREY);
            g.fillPolygon(new int[]{6, 6, 20}, new int[]{4, 20, 12}, 3);
        });
    }

    static Icon step(boolean enabled) {
        return make(24, (g, s) -> {
            g.setColor(enabled ? new Color(0x2E9E44) : new Color(0xB8B8B8));
            g.fillPolygon(new int[]{4, 4, 14}, new int[]{5, 19, 12}, 3);
            g.fillPolygon(new int[]{11, 11, 21}, new int[]{5, 19, 12}, 3);
        });
    }

    static Icon pause(boolean enabled) {
        return make(24, (g, s) -> {
            g.setColor(enabled ? new Color(0x555555) : new Color(0xB8B8B8));
            g.fillRect(6, 4, 4, 16);
            g.fillRect(14, 4, 4, 16);
        });
    }

    static Icon stop(boolean enabled) {
        return make(24, (g, s) -> {
            g.setColor(enabled ? new Color(0xC62828) : new Color(0xB8B8B8));
            g.fillRect(5, 5, 14, 14);
        });
    }

    // ------------------------------------------------------------ small icons

    static Icon dropArrow() {
        return make(10, (g, s) -> {
            g.setColor(new Color(0x444444));
            g.fillPolygon(new int[]{1, 9, 5}, new int[]{3, 3, 8}, 3);
        });
    }

    static Icon chevron(boolean down) {
        return make(12, (g, s) -> {
            g.setColor(new Color(0x555555));
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (down) {
                g.drawLine(2, 4, 6, 8);
                g.drawLine(6, 8, 10, 4);
            } else {
                g.drawLine(4, 2, 8, 6);
                g.drawLine(8, 6, 4, 10);
            }
        });
    }

    static Icon link() {
        return make(16, (g, s) -> {
            g.setColor(new Color(0xE0E0E0));
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Graphics2D t = (Graphics2D) g.create();
            t.rotate(-Math.PI / 4, 8, 8);
            t.draw(new RoundRectangle2D.Float(1, 5.5f, 8, 5, 5, 5));
            t.draw(new RoundRectangle2D.Float(7, 5.5f, 8, 5, 5, 5));
            t.dispose();
        });
    }

    /** Red square with a white x, as on dqMan's session tab and navigator header. */
    static Icon closeRed() {
        return make(14, (g, s) -> {
            g.setColor(Theme.CLOSE_RED);
            g.fillRect(0, 0, 14, 14);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(4, 4, 10, 10);
            g.drawLine(10, 4, 4, 10);
        });
    }

    static Icon closeGrey() {
        return make(12, (g, s) -> {
            g.setColor(new Color(0x777777));
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(3, 3, 9, 9);
            g.drawLine(9, 3, 3, 9);
        });
    }

    static Icon repository() {
        return make(16, (g, s) -> {
            g.setColor(RED);
            g.fill(new Ellipse2D.Float(5, 0, 6, 6));
            g.fill(new RoundRectangle2D.Float(3, 6, 10, 6, 4, 4));
            g.fill(new Ellipse2D.Float(0, 9, 5, 5));
            g.fill(new Ellipse2D.Float(11, 9, 5, 5));
            g.setColor(new Color(0x9E9E9E));
            g.fillRect(2, 14, 12, 2);
        });
    }

    static Icon cabinet() {
        return make(16, (g, s) -> {
            g.setColor(Color.WHITE);
            g.fillRect(3, 1, 10, 14);
            g.setColor(new Color(0x505050));
            g.drawRect(3, 1, 10, 14);
            g.drawLine(3, 5, 13, 5);
            g.drawLine(3, 10, 13, 10);
            g.fillRect(7, 3, 2, 1);
            g.fillRect(7, 7, 2, 1);
            g.fillRect(7, 12, 2, 1);
        });
    }

    static Icon folder() {
        return make(16, (g, s) -> {
            g.setColor(new Color(0xE0A82E));
            g.fillRect(1, 3, 6, 3);
            g.setColor(new Color(0xF3C552));
            g.fillRect(1, 5, 14, 9);
            g.setColor(new Color(0xC08A1E));
            g.drawRect(1, 5, 14, 9);
        });
    }

    static Icon document() {
        return make(16, (g, s) -> page(g, 3, 1, 10, 14, Color.WHITE, new Color(0x707070)));
    }

    static Icon lockedDocument() {
        return make(16, (g, s) -> {
            page(g, 2, 1, 10, 14, Color.WHITE, new Color(0x707070));
            g.setColor(new Color(0xC62828));
            g.fillRect(9, 9, 6, 6);
            g.setColor(Color.WHITE);
            g.fillRect(11, 11, 2, 2);
        });
    }

    static Icon type() {
        return make(16, (g, s) -> {
            g.setColor(new Color(0x5C6BC0));
            g.fill(new RoundRectangle2D.Float(2, 2, 12, 12, 3, 3));
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            g.drawString("T", 5, 12);
        });
    }

    static Icon group() {
        return make(16, (g, s) -> {
            g.setColor(new Color(0x8D99AE));
            g.fill(new Ellipse2D.Float(1, 2, 6, 6));
            g.fill(new RoundRectangle2D.Float(0, 8, 8, 6, 4, 4));
            g.setColor(new Color(0x2B6CB0));
            g.fill(new Ellipse2D.Float(8, 1, 6, 6));
            g.fill(new RoundRectangle2D.Float(7, 7, 9, 7, 4, 4));
        });
    }

    static Icon role() {
        return make(16, (g, s) -> {
            g.setColor(new Color(0x8E44AD));
            g.fill(new Ellipse2D.Float(4, 1, 7, 7));
            g.fill(new RoundRectangle2D.Float(2, 8, 11, 7, 4, 4));
            g.setColor(new Color(0xF6C343));
            g.fillRect(11, 1, 4, 4);
        });
    }

    static Icon user() {
        return make(16, (g, s) -> {
            g.setColor(new Color(0x2B6CB0));
            g.fill(new Ellipse2D.Float(4, 1, 7, 7));
            g.fill(new RoundRectangle2D.Float(2, 8, 11, 7, 5, 5));
        });
    }

    static Icon table() {
        return make(16, (g, s) -> {
            g.setColor(Color.WHITE);
            g.fillRect(1, 2, 14, 12);
            g.setColor(new Color(0x3C8D40));
            g.fillRect(1, 2, 14, 3);
            g.setColor(new Color(0x606060));
            g.drawRect(1, 2, 14, 12);
            g.drawLine(1, 8, 15, 8);
            g.drawLine(1, 11, 15, 11);
            g.drawLine(6, 5, 6, 14);
        });
    }

    static Icon starSmall() {
        return make(16, (g, s) -> {
            int[] xs = new int[10];
            int[] ys = new int[10];
            for (int i = 0; i < 10; i++) {
                double r = i % 2 == 0 ? 7 : 3;
                double a = Math.PI / 2 + i * Math.PI / 5;
                xs[i] = (int) Math.round(8 + r * Math.cos(a));
                ys[i] = (int) Math.round(8.5 - r * Math.sin(a));
            }
            g.setColor(new Color(0xF6C343));
            g.fillPolygon(xs, ys, 10);
            g.setColor(new Color(0xC9961A));
            g.drawPolygon(xs, ys, 10);
        });
    }

    static Icon query() {
        return make(16, (g, s) -> {
            page(g, 3, 1, 10, 14, Color.WHITE, new Color(0x707070));
            g.setColor(RED);
            g.fillRect(3, 6, 10, 4);
        });
    }

    static Icon pencil() {
        return make(16, (g, s) -> {
            Graphics2D t = (Graphics2D) g.create();
            t.rotate(Math.PI / 4, 8, 8);
            t.setColor(new Color(0x8A8A8A));
            t.fillRect(6, 0, 4, 12);
            t.setColor(new Color(0x5A5A5A));
            t.fillPolygon(new int[]{6, 10, 8}, new int[]{12, 12, 16}, 3);
            t.dispose();
        });
    }

    static Icon search() {
        return make(16, (g, s) -> {
            g.setColor(new Color(0x2B6CB0));
            g.setStroke(new BasicStroke(2f));
            g.draw(new Ellipse2D.Float(2, 2, 8, 8));
            g.drawLine(9, 9, 14, 14);
        });
    }

    /** Application icon: white DQ on a red rounded square. */
    static Image appImage(int size) {
        return ((ImageIcon) make(size, (g, s) -> {
            g.setColor(new Color(0xC8102E));
            g.fill(new RoundRectangle2D.Float(0, 0, s, s, s * 0.35f, s * 0.35f));
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.round(s * 0.48f)));
            FontMetrics fm = g.getFontMetrics();
            g.drawString("DQ", (s - fm.stringWidth("DQ")) / 2f, (s - fm.getHeight()) / 2f + fm.getAscent());
        })).getImage();
    }
}
