package com.sedin.docuquery;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

/** Stores query history, favourites and simple settings in ~/.docuquery */
final class Prefs {
    private static final Path DIR = Paths.get(System.getProperty("user.home"), ".docuquery");
    private static final String SEP = "--#docuquery#--";
    private static final int MAX_HISTORY = 500;

    private Prefs() {}

    static Path dir() {
        try {
            Files.createDirectories(DIR);
        } catch (IOException ignored) {
            // ignore
        }
        return DIR;
    }

    // ---------------------------------------------------------------- history

    static synchronized List<String> history() {
        Path p = dir().resolve("history.txt");
        List<String> out = new ArrayList<>();
        if (!Files.exists(p)) return out;
        try {
            String all = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
            for (String e : all.split("\n" + Pattern.quote(SEP) + "\n")) {
                if (!e.trim().isEmpty()) out.add(e);
            }
        } catch (IOException ignored) {
            // ignore
        }
        return out;
    }

    static synchronized void addHistory(String query) {
        String q = query.trim();
        if (q.isEmpty()) return;
        List<String> list = history();
        list.remove(q);
        list.add(0, q);
        while (list.size() > MAX_HISTORY) list.remove(list.size() - 1);
        writeHistory(list);
    }

    static synchronized void writeHistory(List<String> list) {
        try {
            Files.write(dir().resolve("history.txt"),
                    String.join("\n" + SEP + "\n", list).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // ignore
        }
    }

    // ------------------------------------------------------------- favourites

    static synchronized TreeMap<String, String> favorites() {
        Properties p = load("favorites.properties");
        TreeMap<String, String> m = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String k : p.stringPropertyNames()) m.put(k, p.getProperty(k));
        return m;
    }

    static synchronized void saveFavorite(String name, String dql) {
        Properties p = load("favorites.properties");
        p.setProperty(name, dql);
        store(p, "favorites.properties");
    }

    static synchronized void removeFavorite(String name) {
        Properties p = load("favorites.properties");
        p.remove(name);
        store(p, "favorites.properties");
    }

    // --------------------------------------------------------------- settings

    static synchronized String get(String key, String def) {
        return load("settings.properties").getProperty(key, def);
    }

    static synchronized int getInt(String key, int def) {
        try {
            return Integer.parseInt(get(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static synchronized void put(String key, String value) {
        Properties p = load("settings.properties");
        if (value == null) p.remove(key); else p.setProperty(key, value);
        store(p, "settings.properties");
    }

    private static Properties load(String file) {
        Properties p = new Properties();
        Path f = dir().resolve(file);
        if (Files.exists(f)) {
            try (InputStream in = Files.newInputStream(f)) {
                p.load(in);
            } catch (IOException ignored) {
                // ignore
            }
        }
        return p;
    }

    private static void store(Properties p, String file) {
        try (OutputStream out = Files.newOutputStream(dir().resolve(file))) {
            p.store(out, App.NAME);
        } catch (IOException ignored) {
            // ignore
        }
    }
}
