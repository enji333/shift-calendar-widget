package com.shiftcal.widget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;

/**
 * "Receive settings from computer". While this screen is open the app listens on the home Wi-Fi
 * for the desktop control panel. Every request must carry the 4-digit pairing code shown here.
 * Closing the screen stops listening. Also takes a pasted settings code, or a shiftcal://import link.
 */
public class ReceiveActivity extends Activity {
    static final int PORT = 8765;
    private ServerSocket server;
    private volatile boolean running;
    private String pin;
    private int port, bad;
    private volatile boolean pinged;
    private TextView status;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private int dp(float v) { return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics())); }
    private TextView text(LinearLayout box, String s, float sp, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(6), 0, dp(6)); box.addView(t); return t;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        final Prefs prefs = new Prefs(this);
        pin = prefs.pin();   // stays the same, so the control panel stays paired (v3.6)
        ScrollView sv = new ScrollView(this); sv.setFitsSystemWindows(true);
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(40)); sv.addView(box); setContentView(sv);

        TextView h = text(box, "Receive from computer", 24, true); h.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        text(box, "Your phone and computer must be on the same Wi-Fi. In the control panel on your computer, type the address and pairing code below, then press Send. The phone only listens while this screen is open.", 14, false).setAlpha(0.75f);
        text(box, "Address", 13, true).setAlpha(0.7f);
        final TextView addr = text(box, "starting…", 26, true); addr.setTypeface(Typeface.MONOSPACE, Typeface.BOLD); addr.setTextIsSelectable(true);
        text(box, "Pairing code", 13, true).setAlpha(0.7f);
        final TextView pv = text(box, pin, 34, true); pv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD); pv.setLetterSpacing(0.3f);
        text(box, "This code stays the same, so the control panel remembers it. Only make a new one if someone else knows it.", 13, false).setAlpha(0.7f);
        Button np = new Button(this); np.setText("Make a new pairing code"); np.setAllCaps(false); box.addView(np);
        np.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            prefs.newPin(); pin = prefs.pin(); pv.setText(pin); status.setText("New code made. Type it into the control panel."); } });
        status = text(box, "Waiting for the computer…", 15, false);

        text(box, "No Wi-Fi? Use a settings code instead", 13, true).setPadding(0, dp(28), 0, dp(4));
        text(box, "In the control panel press \"Copy settings code\", get it to your phone (for example email or message it to yourself), copy it, then paste it here.", 13, false).setAlpha(0.75f);
        final EditText code = new EditText(this); code.setHint("Paste the settings code here"); code.setMinLines(2);
        code.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        box.addView(code);
        Button apply = new Button(this); apply.setText("Apply this code"); apply.setAllCaps(false); box.addView(apply);
        apply.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            String c = code.getText().toString();
            if (c.trim().isEmpty()) { ClipboardManager cm = getSystemService(ClipboardManager.class); if (cm != null && cm.hasPrimaryClip()) c = String.valueOf(cm.getPrimaryClip().getItemAt(0).coerceToText(ReceiveActivity.this)); }
            applyCode(c, false);
        } });
        Button copy = new Button(this); copy.setText("Copy this phone's settings as a code"); copy.setAllCaps(false); box.addView(copy);
        copy.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            try {
                String c = Sync.encode(Sync.export(ReceiveActivity.this));
                getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Shift Calendar settings", c));
                status.setText("Copied. Paste it into the control panel's \"Load a settings code\" box.");
            } catch (Exception e) { status.setText("Could not copy: " + e.getMessage()); }
        } });
        Button done = new Button(this); done.setText("Done"); done.setAllCaps(false); box.addView(done);
        done.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });

        start(addr);
        handleLink(getIntent());
    }
    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); handleLink(i); }
    @Override protected void onDestroy() { stop(); super.onDestroy(); }

    private void handleLink(Intent i) {
        if (i == null || i.getData() == null || !"shiftcal".equals(i.getData().getScheme())) return;
        Uri u = i.getData(); String d = u.getQueryParameter("d");
        if (d != null) applyCode(d, true);
    }
    private void applyCode(final String c, boolean ask) {
        final JSONObject s;
        try { s = Sync.decode(c); } catch (Exception e) { status.setText("That code didn't work: " + e.getMessage()); return; }
        if (!ask) { doApply(s); return; }
        new AlertDialog.Builder(this).setTitle("Apply settings?")
            .setMessage("Apply widget settings from the control panel? Your current settings will be replaced where they differ.")
            .setPositiveButton("Apply", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { doApply(s); } })
            .setNegativeButton("Cancel", null).show();
    }
    private void doApply(JSONObject s) {
        try { int n = Sync.apply(this, s); status.setText("✓ Applied " + n + " settings. The widget has been redrawn."); }
        catch (Exception e) { status.setText("Could not apply: " + e.getMessage()); }
    }

    // ---------------- tiny HTTP server ----------------
    private void start(final TextView addr) {
        running = true;
        new Thread(new Runnable() { public void run() {
            for (int p = PORT; p < PORT + 10 && server == null; p++) {
                try { ServerSocket s = new ServerSocket(); s.setReuseAddress(true); s.bind(new InetSocketAddress(p)); server = s; port = p; }
                catch (Exception ignored) { }
            }
            final String shown;
            if (server == null) shown = "could not start";
            else {
                List<String> ips = ips(); StringBuilder sb = new StringBuilder();
                for (String ip : ips) { if (sb.length() > 0) sb.append('\n'); sb.append(ip).append(':').append(port); }
                shown = sb.length() == 0 ? "not on Wi-Fi?" : sb.toString();
            }
            ui.post(new Runnable() { public void run() { addr.setText(shown); } });
            while (running && server != null) {
                try { final Socket sock = server.accept(); new Thread(new Runnable() { public void run() { serve(sock); } }).start(); }
                catch (Exception e) { if (!running) break; }
            }
        } }).start();
    }
    private void stop() { running = false; try { if (server != null) server.close(); } catch (Exception ignored) { } }

    static List<String> ips() {
        List<String> out = new ArrayList<String>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                String n = ni.getName() == null ? "" : ni.getName();
                if (n.startsWith("rmnet") || n.startsWith("ccmni") || n.startsWith("dummy") || n.startsWith("tun")) continue;   // mobile data / VPN
                for (InetAddress a : Collections.list(ni.getInetAddresses()))
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) { String ip = a.getHostAddress(); if (n.startsWith("wlan")) out.add(0, ip); else out.add(ip); }
            }
        } catch (Exception ignored) { }
        return out;
    }

    private void serve(Socket sock) {
        try {
            sock.setSoTimeout(15000);
            InputStream in = sock.getInputStream();
            String line = readLine(in); if (line == null) return;
            String[] parts = line.split(" ");
            String method = parts[0], path = parts.length > 1 ? parts[1] : "/";
            int len = 0; String l;
            while ((l = readLine(in)) != null && !l.isEmpty()) {
                int c = l.indexOf(':');
                if (c > 0 && l.substring(0, c).trim().equalsIgnoreCase("content-length")) len = Integer.parseInt(l.substring(c + 1).trim());
            }
            if (len > 2000000) { reply(sock, 413, "{\"error\":\"too big\"}"); return; }
            byte[] body = new byte[len]; int got = 0;
            while (got < len) { int n = in.read(body, got, len - got); if (n < 0) break; got += n; }

            if ("OPTIONS".equals(method)) { reply(sock, 204, null); return; }
            Uri u = Uri.parse("http://x" + path);
            if (!pin.equals(u.getQueryParameter("pin"))) {
                if (++bad >= 20) { ui.post(new Runnable() { public void run() { status.setText("Too many wrong pairing codes, so receiving was stopped. Close and reopen this screen."); } }); stop(); }
                reply(sock, 403, "{\"error\":\"Wrong pairing code\"}"); return;
            }
            String route = u.getPath() == null ? "/" : u.getPath();
            if (route.equals("/ping")) {
                JSONObject o = new JSONObject(); o.put("ok", true); o.put("app", "Shift Calendar");
                try { o.put("version", getPackageManager().getPackageInfo(getPackageName(), 0).versionName); } catch (Exception ignored) { }
                if (!pinged) note("Computer connected.");   // the panel checks every few seconds for its connection light
                pinged = true;
                reply(sock, 200, o.toString());
            } else if (route.equals("/state")) {
                note("Computer loaded your settings and events for the preview.");
                reply(sock, 200, Sync.state(this).toString());
            } else if (route.equals("/apply") && "POST".equals(method)) {
                JSONObject o = new JSONObject(new String(body, "UTF-8"));
                int n = Sync.apply(this, o.has("settings") ? o.getJSONObject("settings") : o);
                note("✓ Received " + n + " settings at " + java.time.LocalTime.now().withNano(0) + ". The widget has been redrawn.");
                reply(sock, 200, "{\"ok\":true,\"applied\":" + n + "}");
            } else reply(sock, 404, "{\"error\":\"not found\"}");
        } catch (Throwable t) {
            Prefs.logCrash(this, t);
            try { reply(sock, 500, JSONObject.quote(String.valueOf(t))); } catch (Exception ignored) { }
        } finally { try { sock.close(); } catch (Exception ignored) { } }
    }
    private void note(final String s) { ui.post(new Runnable() { public void run() { status.setText(s); } }); }
    private static String readLine(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder(); int c;
        while ((c = in.read()) >= 0) { if (c == '\n') break; if (c != '\r') sb.append((char) c); if (sb.length() > 8000) break; }
        return c < 0 && sb.length() == 0 ? null : sb.toString();
    }
    private static void reply(Socket s, int code, String json) throws Exception {
        byte[] b = json == null ? new byte[0] : json.getBytes("UTF-8");
        String head = "HTTP/1.1 " + code + (code == 200 ? " OK" : code == 204 ? " No Content" : " Error") + "\r\n"
            + "Content-Type: application/json; charset=utf-8\r\n"
            + "Access-Control-Allow-Origin: *\r\n"
            + "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n"
            + "Access-Control-Allow-Headers: Content-Type\r\n"
            + "Access-Control-Allow-Private-Network: true\r\n"
            + "Cache-Control: no-store\r\n"
            + "Content-Length: " + b.length + "\r\nConnection: close\r\n\r\n";
        OutputStream o = s.getOutputStream(); o.write(head.getBytes("UTF-8")); o.write(b); o.flush();
    }
}
