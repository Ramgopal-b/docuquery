package com.sedin.docuquery;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Writes result tables to CSV and to a minimal .xlsx workbook (no external libraries). */
final class Export {
    private Export() {}

    static void csv(File f, String[] header, List<String[]> rows) throws IOException {
        try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))) {
            w.write('﻿'); // BOM so Excel detects UTF-8
            line(w, header);
            for (String[] r : rows) line(w, r);
        }
    }

    private static void line(Writer w, String[] vals) throws IOException {
        for (int i = 0; i < vals.length; i++) {
            if (i > 0) w.write(',');
            String v = vals[i] == null ? "" : vals[i];
            if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
                v = "\"" + v.replace("\"", "\"\"") + "\"";
            }
            w.write(v);
        }
        w.write("\r\n");
    }

    static void html(File f, String title, String[] header, List<String[]> rows) throws IOException {
        try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))) {
            w.write("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>DQL results</title><style>"
                    + "body{font-family:Segoe UI,Arial,sans-serif;font-size:13px}table{border-collapse:collapse}"
                    + "th,td{border:1px solid #ccc;padding:3px 6px;text-align:left}th{background:#f0f0f0}"
                    + "pre{background:#f7f7f7;padding:6px}</style></head><body>");
            if (title != null && !title.isEmpty()) w.write("<pre>" + xml(title) + "</pre>");
            w.write("<table><tr>");
            for (String h : header) w.write("<th>" + xml(h) + "</th>");
            w.write("</tr>");
            for (String[] r : rows) {
                w.write("<tr>");
                for (String v : r) w.write("<td>" + xml(v == null ? "" : v) + "</td>");
                w.write("</tr>");
            }
            w.write("</table><p>" + rows.size() + " rows</p></body></html>");
        }
    }

    static void xlsx(File f, String sheetName, String[] header, List<String[]> rows) throws IOException {
        try (ZipOutputStream z = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(f)))) {
            put(z, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                    + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                    + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                    + "</Types>");
            put(z, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                    + "</Relationships>");
            put(z, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                    + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                    + "<sheets><sheet name=\"" + xml(sheetName) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            put(z, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                    + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                    + "</Relationships>");
            put(z, "xl/styles.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                    + "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                    + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
                    + "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>"
                    + "<borders count=\"1\"><border/></borders>"
                    + "<cellStyleXfs count=\"1\"><xf/></cellStyleXfs>"
                    + "<cellXfs count=\"2\"><xf/><xf fontId=\"1\" applyFont=\"1\"/></cellXfs>"
                    + "</styleSheet>");

            z.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            Writer w = new OutputStreamWriter(z, StandardCharsets.UTF_8);
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                    + "<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" state=\"frozen\" activePane=\"bottomLeft\"/></sheetView></sheetViews>"
                    + "<sheetData>");
            row(w, 1, header, true);
            int r = 2;
            for (String[] vals : rows) row(w, r++, vals, false);
            w.write("</sheetData></worksheet>");
            w.flush();
            z.closeEntry();
        }
    }

    private static void row(Writer w, int r, String[] vals, boolean bold) throws IOException {
        w.write("<row r=\"" + r + "\">");
        for (int c = 0; c < vals.length; c++) {
            w.write("<c r=\"" + colName(c) + r + "\" t=\"inlineStr\"" + (bold ? " s=\"1\"" : "") + "><is><t xml:space=\"preserve\">");
            w.write(xml(vals[c] == null ? "" : vals[c]));
            w.write("</t></is></c>");
        }
        w.write("</row>");
    }

    private static String colName(int c) {
        StringBuilder s = new StringBuilder();
        int n = c + 1;
        while (n > 0) {
            int m = (n - 1) % 26;
            s.insert(0, (char) ('A' + m));
            n = (n - 1) / 26;
        }
        return s.toString();
    }

    private static void put(ZipOutputStream z, String name, String content) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        z.write(content.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }

    static String xml(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '<': b.append("&lt;"); break;
                case '>': b.append("&gt;"); break;
                case '&': b.append("&amp;"); break;
                case '"': b.append("&quot;"); break;
                default:
                    if (ch < 0x20 && ch != '\t' && ch != '\n' && ch != '\r') continue;
                    b.append(ch);
            }
        }
        return b.toString();
    }
}
