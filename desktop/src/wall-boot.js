/* Shift Calendar PC wallpaper: runs after the control panel's own code, which draws the calendar.
   Reads wallpaper-data.js (written by Update-Wallpaper.ps1): your look (a settings code) and your
   Google calendars (.ics text), then draws the calendar full-screen for a desktop picture.
   Page address options: ?w=1920&h=1080 (screen size in CSS pixels), &live=1 (Lively: redraw by itself). */
"use strict";
(async function wallpaper() {
  const D = window.WALL_DATA || {}, Q = new URLSearchParams(location.search);
  const cfg = Object.assign({ pos: "right", size: 48, scale: 1.25, top: 24, bottom: 64, side: 24, bg: "", buttons: false }, D.config || {});
  const SW = +Q.get("w") || window.innerWidth, SH = +Q.get("h") || window.innerHeight;
  document.documentElement.classList.add("wallmode");
  const st = document.createElement("style");
  st.textContent = `html.wallmode, html.wallmode body { overflow: hidden !important; width: ${SW}px; height: ${SH}px; margin: 0; }
    html.wallmode body > *:not(#wallStage) { display: none !important; }
    #wallStage { position: fixed; inset: 0; overflow: hidden; }
    #wallStage .wall { position: absolute; padding: 0 !important; background: none !important; border-radius: 0 !important; overflow: visible !important; max-width: none !important; }
    #wallStage .hdl { display: none !important; }
    #wallMsg { position: absolute; left: 24px; bottom: 80px; font: 14px "Segoe UI", sans-serif; color: #fff; background: rgba(0,0,0,.55); padding: 8px 12px; border-radius: 6px; max-width: 60%; white-space: pre-wrap; }`;
  document.head.append(st);

  const msgs = [];
  // 1) your look
  let settings = {};
  if (D.code) { try { settings = await decodeCode(String(D.code)); } catch (e) { msgs.push("Couldn't read the settings code in my-look.txt (" + e.message + "). Showing the standard look."); } }
  // 2) your calendars
  const now = Date.now(), from = now - 70 * 864e5, to = now + 140 * 864e5;
  const events = [];
  const dec = b64 => { const bin = atob(b64), u = new Uint8Array(bin.length); for (let i = 0; i < bin.length; i++) u[i] = bin.charCodeAt(i); return new TextDecoder("utf-8").decode(u); };
  (D.calendars || []).forEach((c, i) => {
    if (c.error) { msgs.push("Couldn't download calendar " + (i + 1) + (c.name ? " (" + c.name + ")" : "") + ": " + c.error); }
    if (!c.ics) return;
    try { events.push(...ICS.parse(dec(c.ics), "ics" + i, c.color || "#039BE5", from, to)); }
    catch (e) { msgs.push("Couldn't read calendar " + (i + 1) + ": " + e.message); }
  });
  if (!(D.calendars || []).length) msgs.push("No calendars set up yet: showing sample events. Run Setup.cmd and paste your Google Calendar secret iCal address.");

  // 3) draw it with the panel's own renderer, exactly like the phone
  $("#editMode").checked = false;
  applyLoaded(Object.assign({}, settings, { __fromPhone: true }));
  S.cals = null;                                   // the phone's calendar ticks don't apply to these calendars
  if (!cfg.buttons) for (const k of ["h_prev", "h_next", "h_add", "h_today", "h_set", "h_arrow"]) S[k] = 0;   // nothing to press on a picture
  if ((D.calendars || []).length) PHONE = { settings, calendars: [], events, version: "pc" };
  // the panel draws up to 800 x 1200; scale up past that
  const top = cfg.top, bottom = cfg.bottom, side = cfg.side;
  const availH = SH - top - bottom;
  let boxW = cfg.pos === "full" ? SW - 2 * side : Math.round(SW * Math.max(20, Math.min(100, cfg.size)) / 100);
  boxW = Math.min(boxW, SW - 2 * side);
  let Z = Math.max(0.5, Math.min(2, +cfg.scale || 1));
  Z = Math.max(Z, boxW / 800, availH / 1200); Z = Math.min(Z, 2);
  const W = Math.round(Math.max(200, Math.min(800, boxW / Z))), H = Math.round(Math.max(200, Math.min(1200, availH / Z)));
  $("#pw").value = W; $("#ph").value = H; $("#pz").value = Z; $("#pToday").value = "";

  const stage = document.createElement("div"); stage.id = "wallStage";
  const wall = $("#wall"); stage.append(wall); document.body.append(stage);
  render();
  const P = palette();
  stage.style.background = cfg.bg ? cfg.bg : P.bg;
  const realW = W * Z, realH = H * Z;
  const left = cfg.pos === "left" ? side : cfg.pos === "center" || cfg.pos === "full" ? Math.round((SW - realW) / 2) : SW - side - realW;
  wall.style.left = left + "px"; wall.style.top = Math.round(top + (availH - realH) / 2) + "px";
  if (msgs.length) {
    const m = document.createElement("div"); m.id = "wallMsg"; m.textContent = msgs.join("\n"); stage.append(m);
    if (cfg.pos === "left") { m.style.left = "auto"; m.style.right = "24px"; m.style.maxWidth = Math.max(240, SW - realW - side - 72) + "px"; }
    else if (cfg.pos === "right") m.style.maxWidth = Math.max(240, SW - realW - side - 72) + "px";
  }
  window.__wallReady = true;
  if (Q.get("live") === "1") setTimeout(() => location.reload(), 10 * 60000);   // Lively: pick up new data every 10 minutes
})().catch(e => { document.body.insertAdjacentHTML("beforeend", "<pre style='position:fixed;left:20px;bottom:80px;color:#fff;background:#900;padding:10px'>Wallpaper problem: " + (e && e.stack || e) + "</pre>"); });
