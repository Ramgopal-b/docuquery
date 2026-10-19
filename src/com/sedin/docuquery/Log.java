package com.sedin.docuquery;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/** Simple log file: <program folder>\data\docuquery.log (rotated at 2 MB). */
final class Log {
    private static final long MAX = 2L * 1024 * 1024;

    private Log() {}

    static File file() {
        return new File(new File(DfcSetup.appHome(), "data"), "docuquery.log");
    }

    static synchronized void info(String msg) {
        write("INFO ", msg, null);
    }

    static synchronized void error(String msg, Throwable t) {
        write("ERROR", msg, t);
    }

    private static void write(String level, String msg, Throwable t) {
        try {
            File f = file();
            File dir = f.getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs()) return;
            if (f.length() > MAX) {
                File old = new File(dir, "docuquery.log.1");
                old.delete();
                f.renameTo(old);
            }
            try (PrintWriter w = new PrintWriter(new FileWriter(f, true))) {
                w.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + " " + level + " ["
                        + Thread.currentThread().getName() + "] " + msg);
                if (t != null) t.printStackTrace(w);
            }
        } catch (IOException ignored) {
            // logging must never break the application
        }
    }

    static String tail(int lines) {
        try {
            List<String> all = Files.readAllLines(file().toPath(), StandardCharsets.UTF_8);
            return String.join("\n", all.subList(Math.max(0, all.size() - lines), all.size()));
        } catch (Exception e) {
            return "(no log yet)";
        }
    }

    static String stack(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
