package com.sedin.docuquery;

import com.documentum.fc.client.IDfSession;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Runs classic Documentum server API commands (dump,c,&lt;id&gt; / set,c,&lt;id&gt;,attr ...). */
@SuppressWarnings("deprecation")
final class Api {

    enum Kind { AUTO, GET, EXEC, SET }

    private static final Set<String> SET = new HashSet<>(Arrays.asList("set", "append", "insert"));

    private static final Set<String> GET = new HashSet<>(Arrays.asList(
            "dump", "get", "getfile", "getpath", "getcontent", "id", "retrieve", "create", "saveasnew",
            "checkin", "checkinapp", "branch", "apply", "query", "readquery", "cachequery", "getlastcoll",
            "getservermap", "getdocbasemap", "getlogin", "getmessage", "listmessage", "describe", "count",
            "values", "getevents", "connect", "locate", "getconnection", "getdocbaseconfig",
            "getserverconfig", "getsessionconfig", "listsessions", "type", "repeating", "attrname",
            "nextid", "getsession", "getcontentattr", "ticket", "getlogininfo"));

    private Api() {}

    static final class Parsed {
        String cmd;
        String args;
    }

    static Parsed parse(String line) {
        String l = line.trim();
        int i = l.indexOf(',');
        Parsed p = new Parsed();
        p.cmd = (i < 0 ? l : l.substring(0, i)).trim().toLowerCase(Locale.ROOT);
        String rest = i < 0 ? "" : l.substring(i + 1);
        int j = rest.indexOf(',');
        String first = (j < 0 ? rest : rest.substring(0, j)).trim();
        if (first.equalsIgnoreCase("c") || first.equalsIgnoreCase("current") || first.matches("[sS]\\d+")) {
            rest = j < 0 ? "" : rest.substring(j + 1);
        }
        p.args = rest;
        return p;
    }

    static Kind classify(String cmd) {
        if (SET.contains(cmd)) return Kind.SET;
        if (GET.contains(cmd)) return Kind.GET;
        return Kind.EXEC;
    }

    static boolean needsValue(String line) {
        return classify(parse(line).cmd) == Kind.SET;
    }

    /** True for "?,c,select ..." – the iapi shortcut to run DQL. */
    static String dqlOf(String line) {
        Parsed p = parse(line);
        return p.cmd.equals("?") ? p.args : null;
    }

    static String execute(IDfSession s, String line, String value, Kind force) throws Exception {
        Parsed p = parse(line);
        Kind k = (force == null || force == Kind.AUTO) ? classify(p.cmd) : force;
        String args = p.args.isEmpty() ? null : p.args;
        if (k == Kind.GET) {
            String r = s.apiGet(p.cmd, args);
            return r == null ? "" : r;
        }
        if (k == Kind.SET) {
            return s.apiSet(p.cmd, args, value == null ? "" : value) ? "OK" : "FAILED";
        }
        return s.apiExec(p.cmd, args) ? "OK" : "FAILED";
    }
}
