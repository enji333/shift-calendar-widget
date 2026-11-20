/* Shift Calendar PC wallpaper: reads Google Calendar "secret address in iCal format" files.
   Turns VEVENTs (with repeats, skipped dates and moved single repeats) into the control panel's
   event shape { t, l, b, e, a, c, k }: title, location, begin/end ms, all-day, colour, calendar key. */
"use strict";
const ICS = (() => {
  function unfold(text) { return text.replace(/\r\n/g, "\n").replace(/\r/g, "\n").replace(/\n[ \t]/g, ""); }
  function unesc(v) { return v.replace(/\\n/gi, "\n").replace(/\\([,;\\])/g, "$1"); }
  function parseLine(line) {
    // NAME;PARAM=V;PARAM="V":value   (colons inside quoted params are allowed)
    let i = 0, q = false;
    for (; i < line.length; i++) { const ch = line[i]; if (ch === '"') q = !q; else if (ch === ":" && !q) break; }
    const head = line.slice(0, i), value = line.slice(i + 1);
    const parts = head.split(";"), name = parts[0].toUpperCase(), params = {};
    for (const p of parts.slice(1)) { const eq = p.indexOf("="); if (eq > 0) params[p.slice(0, eq).toUpperCase()] = p.slice(eq + 1).replace(/^"|"$/g, ""); }
    return { name, params, value };
  }
  /** Offset (ms) of a time zone at a UTC instant. */
  const fmtCache = {};
  function tzOffset(utcMs, tz) {
    let f = fmtCache[tz];
    if (!f) { try { f = fmtCache[tz] = new Intl.DateTimeFormat("en-US", { timeZone: tz, hourCycle: "h23", year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit" }); } catch (e) { return null; } }
    const o = {}; for (const p of f.formatToParts(new Date(utcMs))) o[p.type] = p.value;
    return Date.UTC(+o.year, +o.month - 1, +o.day, +o.hour % 24, +o.minute, +o.second) - utcMs;
  }
  /** Wall-clock time in a zone → UTC ms. */
  function zoned(y, mo, d, h, mi, s, tz) {
    const guess = Date.UTC(y, mo - 1, d, h, mi, s);
    const o1 = tzOffset(guess, tz); if (o1 == null) return guess;
    const o2 = tzOffset(guess - o1, tz);
    return guess - o2;
  }
  /** A DTSTART/DTEND/EXDATE value → { ms, allDay, wall:[y,m,d,h,mi,s], tz } */
  function when(value, params, defTz) {
    const m = /^(\d{4})(\d{2})(\d{2})(?:T(\d{2})(\d{2})(\d{2})?(Z)?)?/.exec(value.trim());
    if (!m) return null;
    const y = +m[1], mo = +m[2], d = +m[3];
    if (m[4] == null || params.VALUE === "DATE") return { allDay: true, ms: Date.UTC(y, mo - 1, d), wall: [y, mo, d, 0, 0, 0], tz: "UTC" };
    const h = +m[4], mi = +m[5], s = +(m[6] || 0);
    if (m[7]) return { allDay: false, ms: Date.UTC(y, mo - 1, d, h, mi, s), wall: null, tz: "UTC" };
    const tz = params.TZID || defTz;
    return { allDay: false, ms: zoned(y, mo, d, h, mi, s, tz), wall: [y, mo, d, h, mi, s], tz };
  }
  function parseDur(v) {
    const m = /^([+-])?P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$/.exec(v.trim());
    if (!m) return 0;
    const n = x => +(x || 0);
    return (m[1] === "-" ? -1 : 1) * (((n(m[2]) * 7 + n(m[3])) * 24 + n(m[4])) * 3600 + n(m[5]) * 60 + n(m[6])) * 1000;
  }
  const DAYS = { SU: 0, MO: 1, TU: 2, WE: 3, TH: 4, FR: 5, SA: 6 };
  function parseRule(v) {
    const r = {}; for (const p of v.split(";")) { const eq = p.indexOf("="); if (eq > 0) r[p.slice(0, eq).toUpperCase()] = p.slice(eq + 1); }
    return r;
  }
  const dim = (y, m) => new Date(Date.UTC(y, m, 0)).getUTCDate();   // m is 1-based
  /** Every start (as [y,m,d]) of a repeating event, from its first date, up to `until` (a UTC-midnight day number). */
  function expand(start, rule, limitDay) {
    const freq = rule.FREQ, iv = Math.max(1, +(rule.INTERVAL || 1));
    const count = rule.COUNT ? +rule.COUNT : Infinity;
    let untilDay = limitDay;
    if (rule.UNTIL) { const u = /^(\d{4})(\d{2})(\d{2})/.exec(rule.UNTIL); if (u) untilDay = Math.min(untilDay, Date.UTC(+u[1], +u[2] - 1, +u[3]) / 864e5); }
    const byday = rule.BYDAY ? rule.BYDAY.split(",").map(x => { const m = /^([+-]?\d+)?([A-Z]{2})$/.exec(x.trim()); return m ? { n: m[1] ? +m[1] : 0, d: DAYS[m[2]] } : null; }).filter(Boolean) : null;
    const bymd = rule.BYMONTHDAY ? rule.BYMONTHDAY.split(",").map(Number) : null;
    const bymo = rule.BYMONTH ? rule.BYMONTH.split(",").map(Number) : null;
    const [y0, m0, d0] = start; const day0 = Date.UTC(y0, m0 - 1, d0) / 864e5;
    const out = []; let n = 0;
    const push = dn => { if (dn < day0 || dn > untilDay || n >= count) return false; n++; out.push(dn); return true; };
    const ymd = dn => { const t = new Date(dn * 864e5); return [t.getUTCFullYear(), t.getUTCMonth() + 1, t.getUTCDate()]; };
    let guard = 0;
    if (freq === "DAILY") {
      for (let dn = day0; dn <= untilDay && n < count && guard++ < 20000; dn += iv) {
        const [y, m] = ymd(dn); const wd = new Date(dn * 864e5).getUTCDay();
        if (bymo && !bymo.includes(m)) continue;
        if (byday && !byday.some(b => b.d === wd)) continue;
        push(dn);
      }
    } else if (freq === "WEEKLY") {
      const wd0 = new Date(day0 * 864e5).getUTCDay();
      const wkst = DAYS[rule.WKST || "MO"];
      const weekStart0 = day0 - ((wd0 - wkst + 7) % 7);
      const days = byday ? byday.map(b => b.d) : [wd0];
      for (let ws = weekStart0; ws <= untilDay && n < count && guard++ < 5000; ws += 7 * iv) {
        const list = days.map(d => ws + ((d - wkst + 7) % 7)).sort((a, b) => a - b);
        for (const dn of list) push(dn);
      }
    } else if (freq === "MONTHLY" || freq === "YEARLY") {
      let y = y0, m = m0;
      while (guard++ < 3000 && n < count) {
        const first = Date.UTC(y, m - 1, 1) / 864e5; if (first > untilDay) break;
        const len = dim(y, m); let cands = [];
        if ((freq === "MONTHLY" || bymo == null || bymo.includes(m)) && (freq === "MONTHLY" || bymo || m === m0)) {
          if (bymd) for (const md of bymd) { const dd = md > 0 ? md : len + md + 1; if (dd >= 1 && dd <= len) cands.push(first + dd - 1); }
          else if (byday) {
            for (const b of byday) {
              const all = []; for (let dd = 1; dd <= len; dd++) if (new Date((first + dd - 1) * 864e5).getUTCDay() === b.d) all.push(first + dd - 1);
              if (b.n === 0) cands.push(...all); else { const pick = b.n > 0 ? all[b.n - 1] : all[all.length + b.n]; if (pick != null) cands.push(pick); }
            }
            if (rule.BYSETPOS) { const s = cands.sort((a, b) => a - b), pos = +rule.BYSETPOS; cands = [pos > 0 ? s[pos - 1] : s[s.length + pos]].filter(x => x != null); }
          } else if (d0 <= len) cands.push(first + d0 - 1);
        }
        for (const dn of cands.sort((a, b) => a - b)) push(dn);
        if (freq === "MONTHLY") { m += iv; while (m > 12) { m -= 12; y++; } }
        else if (bymo) { m++; if (m > 12) { m = 1; y += iv; } }
        else { y += iv; }
      }
    } else push(day0);
    return out.map(ymd);
  }

  /**
   * text: one .ics file. key: calendar key. color: "#RRGGBB". from/to: ms window to keep.
   * Returns events in the panel's shape.
   */
  function parse(text, key, color, from, to) {
    const lines = unfold(text).split("\n");
    let defTz = "Australia/Perth";
    const evs = []; let cur = null, inAlarm = 0;
    for (const raw of lines) {
      if (!raw) continue;
      const L = parseLine(raw);
      if (L.name === "X-WR-TIMEZONE" && !cur) { defTz = L.value.trim() || defTz; continue; }
      if (L.name === "BEGIN" && L.value === "VEVENT") { cur = { ex: [] }; continue; }
      if (!cur) continue;
      if (L.name === "BEGIN") { inAlarm++; continue; }
      if (L.name === "END" && L.value !== "VEVENT") { inAlarm = Math.max(0, inAlarm - 1); continue; }
      if (L.name === "END" && L.value === "VEVENT") { evs.push(cur); cur = null; inAlarm = 0; continue; }
      if (inAlarm) continue;
      switch (L.name) {
        case "SUMMARY": cur.t = unesc(L.value); break;
        case "LOCATION": cur.l = unesc(L.value); break;
        case "UID": cur.uid = L.value; break;
        case "STATUS": cur.status = L.value.toUpperCase(); break;
        case "DTSTART": cur.ds = L; break;
        case "DTEND": cur.de = L; break;
        case "DURATION": cur.dur = parseDur(L.value); break;
        case "RRULE": cur.rr = parseRule(L.value); break;
        case "EXDATE": for (const v of L.value.split(",")) cur.ex.push({ value: v, params: L.params }); break;
        case "RECURRENCE-ID": cur.rid = L; break;
      }
    }
    // single moved/changed repeats replace the original date
    const moved = {};
    for (const e of evs) if (e.rid && e.uid) { const w = when(e.rid.value, e.rid.params, defTz); if (w) (moved[e.uid] || (moved[e.uid] = new Set())).add(w.ms); }
    const out = [], limitDay = Math.floor(to / 864e5) + 2;
    for (const e of evs) {
      if (!e.ds || e.status === "CANCELLED") continue;
      const s = when(e.ds.value, e.ds.params, defTz); if (!s) continue;
      let len;
      if (e.de) { const en = when(e.de.value, e.de.params, defTz); len = en ? en.ms - s.ms : 0; }
      else len = e.dur != null ? e.dur : (s.allDay ? 864e5 : 0);
      if (s.allDay && len <= 0) len = 864e5;
      const base = { t: e.t || "(no title)", l: e.l || "", a: s.allDay, c: color, k: key };
      const emit = b => { const en = b + len; if (en >= from && b <= to) out.push(Object.assign({ b, e: en }, base)); };
      if (!e.rr || e.rid) { emit(s.ms); continue; }
      const skip = new Set(e.ex.map(x => { const w = when(x.value, x.params, s.tz === "UTC" && !s.allDay ? "UTC" : (x.params.TZID || defTz)); return w ? w.ms : null; }));
      const mv = moved[e.uid] || new Set();
      for (const [y, m, d] of expand(s.wall || (() => { const t = new Date(s.ms); return [t.getUTCFullYear(), t.getUTCMonth() + 1, t.getUTCDate(), t.getUTCHours(), t.getUTCMinutes(), t.getUTCSeconds()]; })(), e.rr, limitDay)) {
        const w = s.wall;
        let b;
        if (s.allDay) b = Date.UTC(y, m - 1, d);
        else if (w) b = zoned(y, m, d, w[3], w[4], w[5], s.tz);
        else { const t = new Date(s.ms); b = Date.UTC(y, m - 1, d, t.getUTCHours(), t.getUTCMinutes(), t.getUTCSeconds()); }
        if (skip.has(b) || mv.has(b)) continue;
        emit(b);
      }
    }
    return out;
  }
  return { parse, expand, zoned };
})();
if (typeof module !== "undefined") module.exports = ICS;
