package com.sedin.docuquery;

import com.documentum.com.DfClientX;
import com.documentum.com.IDfClientX;
import com.documentum.fc.client.IDfClient;
import com.documentum.fc.client.IDfDocbaseMap;
import com.documentum.fc.client.IDfSession;
import com.documentum.fc.client.IDfSessionManager;
import com.documentum.fc.common.IDfLoginInfo;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * One DFC session to one repository. All DFC calls are serialised on a single
 * background thread so the UI never blocks and the session is never used concurrently.
 */
final class DocConnection {

    /** Work executed on the DFC thread with the live session. */
    interface Task<T> {
        T run(IDfSession s) throws Exception;
    }

    final String docbase;
    final String user;
    final String domain;
    String alias;              // Session > Session Alias
    private String password;   // kept in memory only, for Session > Copy
    private IDfSessionManager sm;
    private volatile IDfSession session;
    private final ExecutorService exec;
    private volatile Thread worker;
    private volatile String currentTask;
    private volatile long taskStart;
    private final java.util.concurrent.atomic.AtomicInteger queued = new java.util.concurrent.atomic.AtomicInteger();

    DocConnection(String docbase, String user, String domain) {
        this.docbase = docbase;
        this.user = user;
        this.domain = domain;
        this.exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "dfc-" + docbase);
            t.setDaemon(true);
            worker = t;
            return t;
        });
    }

    /** Reads the repository list from the docbroker(s) configured in dfc.properties. */
    static List<String> listDocbases() throws Exception {
        IDfClientX cx = new DfClientX();
        IDfDocbaseMap map = cx.getLocalClient().getDocbaseMap();
        List<String> l = new ArrayList<>();
        int n = map.getDocbaseCount();
        for (int i = 0; i < n; i++) l.add(map.getDocbaseName(i));
        return l;
    }

    void connectAsync(String password, Runnable ok, Consumer<Throwable> err) {
        this.password = password;
        exec.submit(() -> {
            long t0 = System.currentTimeMillis();
            currentTask = "connect " + user + "@" + docbase;
            taskStart = t0;
            try {
                Log.info("connect " + user + "@" + docbase);
                IDfClientX cx = new DfClientX();
                IDfClient client = cx.getLocalClient();
                sm = client.newSessionManager();
                IDfLoginInfo li = cx.getLoginInfo();
                li.setUser(user);
                li.setPassword(password);
                if (domain != null && !domain.trim().isEmpty()) li.setDomain(domain.trim());
                sm.setIdentity(docbase, li);
                session = sm.getSession(docbase);
                Log.info("connected " + user + "@" + docbase + " in " + (System.currentTimeMillis() - t0) + " ms");
                SwingUtilities.invokeLater(ok);
            } catch (Throwable t) {
                Log.error("connect failed " + user + "@" + docbase, t);
                SwingUtilities.invokeLater(() -> err.accept(t));
            } finally {
                currentTask = null;
            }
        });
    }

    <T> Future<?> run(Task<T> task, Consumer<T> ok, Consumer<Throwable> err) {
        return run(null, task, ok, err);
    }

    /** Runs work on the DFC thread; label is shown in Help > Diagnostics and written to the log. */
    <T> Future<?> run(String label, Task<T> task, Consumer<T> ok, Consumer<Throwable> err) {
        String what = label == null ? callerName() : label;
        queued.incrementAndGet();
        return exec.submit(() -> {
            queued.decrementAndGet();
            long t0 = System.currentTimeMillis();
            currentTask = what;
            taskStart = t0;
            try {
                IDfSession s = session;
                if (s == null) throw new IllegalStateException("Not connected to " + docbase);
                T r = task.run(s);
                long ms = System.currentTimeMillis() - t0;
                if (ms > 2000) Log.info("slow (" + ms + " ms): " + what);
                if (ok != null) SwingUtilities.invokeLater(() -> ok.accept(r));
            } catch (Throwable t) {
                Log.error("failed after " + (System.currentTimeMillis() - t0) + " ms: " + what, t);
                if (err != null) SwingUtilities.invokeLater(() -> err.accept(t));
            } finally {
                currentTask = null;
            }
        });
    }

    private static String callerName() {
        StackTraceElement[] st = new Throwable().getStackTrace();
        for (StackTraceElement e : st) {
            if (!e.getClassName().endsWith("DocConnection")) return e.getClassName().replaceAll(".*\\.", "") + "." + e.getMethodName();
        }
        return "task";
    }

    /** What the DFC thread is doing right now (for Help > Diagnostics). */
    String busyText() {
        String t = currentTask;
        StringBuilder b = new StringBuilder();
        if (t == null) b.append("idle");
        else b.append("RUNNING for ").append((System.currentTimeMillis() - taskStart) / 1000).append(" s: ").append(t);
        b.append("   (waiting in queue: ").append(Math.max(0, queued.get())).append(")\n");
        Thread w = worker;
        if (w != null && t != null) {
            b.append("  DFC thread stack (what it is waiting for):\n");
            StackTraceElement[] st = w.getStackTrace();
            for (int i = 0; i < Math.min(40, st.length); i++) b.append("    at ").append(st[i]).append('\n');
        }
        return b.toString();
    }

    long busySeconds() {
        return currentTask == null ? 0 : (System.currentTimeMillis() - taskStart) / 1000;
    }

    void disconnect() {
        if (exec.isShutdown()) return;
        exec.submit(() -> {
            try {
                if (sm != null && session != null) sm.release(session);
            } catch (Throwable ignored) {
                // ignore
            }
            session = null;
        });
        exec.shutdown();
    }

    boolean isConnected() {
        return session != null;
    }

    String label() {
        return alias != null && !alias.trim().isEmpty() ? alias.trim() : user + "@" + docbase;
    }

    /** A new, not yet connected session with the same credentials (Session > Copy). */
    DocConnection copy() {
        DocConnection c = new DocConnection(docbase, user, domain);
        c.password = password;
        return c;
    }

    String password() {
        return password;
    }

    static String message(Throwable t) {
        if (t instanceof NoClassDefFoundError || t instanceof ClassNotFoundException) {
            return "DFC libraries not found on the classpath (" + t.getMessage() + ").\n"
                    + "Check that DFC is installed or that its jars are in the 'lib' folder.";
        }
        String m = t.getMessage();
        if (m == null || m.trim().isEmpty()) m = t.toString();
        m = m.trim();
        if (m.contains("DFC_DOCBROKER_REQUEST_FAILED") || m.contains("DM_DOCBROKER")) {
            m += "\n\nThis PC cannot reach the docbroker (Connection Broker). Check:\n"
                    + "  1. VPN / office network is connected\n"
                    + "  2. Host and port in dfc.properties are right (button 'dfc.properties...' > Test connection)\n"
                    + "  3. Firewall allows the docbroker port (normally 1489) and the repository ports\n"
                    + "  4. The docbroker service is running on the server (ask the Documentum admin)\n"
                    + "After changing dfc.properties, restart DocuQuery.";
        }
        return m;
    }
}
