"use strict";

// ---------- helpers ----------
const $ = id => document.getElementById(id);
const fmt = n => (n ?? 0).toLocaleString("ru-RU");
const fmt1 = n => (n ?? 0).toLocaleString("ru-RU", { maximumFractionDigits: 1 });
const SRC_LABEL = { MODE_S: "Mode S", MODE_AC: "Mode A/C", PRIMARY: "первичный" };

// ---------- map ----------
const map = L.map("map", { zoomControl: true, attributionControl: false }).setView([39.5, 33.5], 6);
L.tileLayer("https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png", {
  maxZoom: 12, subdomains: "abcd"
}).addTo(map);
const markers = new Map();           // icao -> L.marker
let fittedOnce = false;

function planeIcon(src, active) {
  const cls = !active ? "inactive" : src === "MODE_AC" ? "ac" : src === "PRIMARY" ? "psr" : "";
  return L.divIcon({ className: "", html: `<div class="plane ${cls}"></div>`, iconSize: [10, 10] });
}

function updateMap(flights) {
  const seen = new Set();
  const pts = [];
  for (const f of flights) {
    if (f.lat == null || f.lon == null) continue;
    const key = f.key || f.icao;
    seen.add(key);
    const active = f.state === "ACTIVE";
    const label = f.callsign || f.icao || (f.mode3A ? "A" + f.mode3A : "трек " + f.trackNumber);
    const pop = `<b>${label}</b> · ${SRC_LABEL[f.sourceType] || ""}<br>`
      + `ICAO ${f.icao || "—"} · 3/A ${f.mode3A || "—"} · FL${f.flightLevel ?? "—"}<br>${f.lat}, ${f.lon}`;
    let m = markers.get(key);
    if (m) { m.setLatLng([f.lat, f.lon]); m.setIcon(planeIcon(f.sourceType, active)); m.getPopup()?.setContent(pop); }
    else {
      m = L.marker([f.lat, f.lon], { icon: planeIcon(f.sourceType, active) }).bindPopup(pop);
      m.addTo(map); markers.set(key, m);
    }
    pts.push([f.lat, f.lon]);
  }
  for (const [key, m] of markers) if (!seen.has(key)) { map.removeLayer(m); markers.delete(key); }
  if (!fittedOnce && pts.length > 2) { map.fitBounds(pts, { padding: [30, 30] }); fittedOnce = true; }
}

// ---------- stats rendering ----------
function renderStats(s) {
  $("s_files").textContent = fmt(s.files);
  $("s_recv").textContent  = fmt(s.received);
  $("s_ok").textContent    = fmt(s.decoded);
  $("s_err").textContent   = fmt(s.errors);
  $("s_plots").textContent = fmt(s.plots);
  $("s_active").textContent= fmt(s.activeTracks);
  $("s_mb").innerHTML      = fmt1(s.mbProcessed) + '<span class="u">MB</span>';
  $("s_rate").innerHTML    = fmt(Math.round(s.blocksPerSec)) + '<span class="u">пак/с</span>';

  $("sweep").classList.toggle("live", s.running);
  $("mapHint").textContent = `${s.radarCount} приёмников в базе`;
  $("errBox").textContent  = s.lastError ? ("⚠ " + s.lastError) : "";
  if (!document.activeElement || document.activeElement.id !== "pathInput") {
    if (s.folder && !$("pathInput").value) $("pathInput").value = s.folder;
  }

  // CAT bars
  const cats = Object.entries(s.perCategory || {});
  const max = Math.max(1, ...cats.map(([, v]) => v));
  $("catBars").innerHTML = cats.length ? cats.map(([name, v]) => `
    <div class="bar">
      <div class="row"><span class="name">${name}</span><span class="num">${fmt(v)}</span></div>
      <div class="track"><div class="fill" style="width:${(v / max * 100).toFixed(1)}%"></div></div>
    </div>`).join("") : '<div class="empty">нет данных</div>';

  // SAC/SIC table
  const rows = s.perSource || [];
  $("srcRows").innerHTML = rows.length ? rows.map(r => `
    <tr><td>${r.sac}</td><td>${r.sic}</td><td>${fmt(r.received)}</td><td>${fmt(r.processed)}</td></tr>
  `).join("") : '<tr><td colspan="4" class="empty">нет данных</td></tr>';
}

function renderFlights(flights) {
  if (!flights.length) {
    $("fltRows").innerHTML = '<tr><td colspan="15" class="empty">ожидание данных…</td></tr>';
  } else {
    $("fltRows").innerHTML = flights.map(f => `
      <tr>
        <td class="src-tag src-${f.sourceType}">${SRC_LABEL[f.sourceType] || "—"}</td>
        <td class="pill">${f.icao || "—"}</td>
        <td>${f.trackNumber ?? "—"}</td>
        <td>${f.mode3A || "—"}</td>
        <td>${f.callsign || "—"}</td>
        <td>${f.country || "—"}</td>
        <td>${f.aircraftType || "—"}</td>
        <td>${f.aircraftModel || "—"}</td>
        <td>${f.lat ?? "—"}</td>
        <td>${f.lon ?? "—"}</td>
        <td>${f.flightLevel != null ? "FL" + f.flightLevel : "—"}</td>
        <td>${f.startTime || "—"}</td>
        <td>${f.endTime || "—"}</td>
        <td>${fmt(f.plotCount)}</td>
        <td class="state-${f.state}">${f.state === "ACTIVE" ? "активен" : "неактивен"}</td>
      </tr>`).join("");
  }
  updateMap(flights);
}

// ---------- websocket ----------
function connect() {
  const sock = new SockJS("/ws");
  const stomp = Stomp.over(sock);
  stomp.debug = null;
  stomp.connect({}, () => {
    $("connDot").classList.add("up");
    $("connText").textContent = "поток активен";
    stomp.subscribe("/topic/stats", m => renderStats(JSON.parse(m.body)));
    stomp.subscribe("/topic/flights", m => renderFlights(JSON.parse(m.body)));
  }, () => {
    $("connDot").classList.remove("up");
    $("connText").textContent = "переподключение…";
    setTimeout(connect, 2000);
  });
}

// ---------- controls ----------
async function api(path, opts) {
  const r = await fetch(path, opts);
  return r.json().catch(() => ({}));
}
$("startBtn").onclick = async () => {
  const path = $("pathInput").value.trim();
  if (!path) { $("errBox").textContent = "укажите путь к папке"; return; }
  const res = await api("/api/start", {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ path })
  });
  $("errBox").textContent = res.error ? ("⚠ " + res.error) : "";
};
$("stopBtn").onclick = () => api("/api/stop", { method: "POST" });

// ---------- folder browser ----------
$("browseBtn").onclick = () => openBrowser($("pathInput").value.trim() || null);

async function openBrowser(path) {
  const data = await api("/api/fs" + (path ? "?path=" + encodeURIComponent(path) : ""));
  if (data.error) { $("errBox").textContent = "⚠ " + data.error; return; }
  showBrowserModal(data);
}

function showBrowserModal(data) {
  let modal = $("browserModal");
  if (!modal) {
    modal = document.createElement("div");
    modal.id = "browserModal";
    modal.style.cssText = "position:fixed;inset:0;background:rgba(4,10,12,.7);display:flex;align-items:center;justify-content:center;z-index:1000";
    modal.innerHTML = `<div style="background:var(--panel);border:1px solid var(--line);border-radius:12px;width:min(560px,92vw);max-height:80vh;display:flex;flex-direction:column;overflow:hidden">
      <div style="padding:14px 18px;border-bottom:1px solid var(--line);display:flex;justify-content:space-between;align-items:center">
        <strong>Выбор папки</strong><button id="brClose">✕</button></div>
      <div id="brCurrent" class="mono" style="padding:10px 18px;color:var(--cyan);font-size:12px;border-bottom:1px solid var(--line);word-break:break-all"></div>
      <div id="brList" style="overflow:auto;padding:6px 0"></div>
      <div style="padding:12px 18px;border-top:1px solid var(--line);display:flex;gap:10px;justify-content:flex-end">
        <button id="brUse" class="primary">Использовать эту папку</button></div>
    </div>`;
    document.body.appendChild(modal);
    $("brClose").onclick = () => modal.remove();
    modal.onclick = e => { if (e.target === modal) modal.remove(); };
  }
  const render = d => {
    $("brCurrent").textContent = d.current;
    const items = [];
    if (d.parent) items.push({ name: "⬆ ..", path: d.parent });
    for (const e of d.entries) items.push({ name: "🗀 " + e.name, path: e.path });
    $("brList").innerHTML = items.map((it, i) =>
      `<div data-p="${encodeURIComponent(it.path)}" class="mono" style="padding:8px 18px;cursor:pointer;font-size:13px"
        onmouseover="this.style.background='rgba(79,208,224,.07)'" onmouseout="this.style.background=''">${it.name}</div>`
    ).join("") || '<div class="empty">нет вложенных папок</div>';
    $("brList").querySelectorAll("[data-p]").forEach(el =>
      el.onclick = async () => render(await api("/api/fs?path=" + el.dataset.p)));
    $("brUse").onclick = () => { $("pathInput").value = d.current; modal.remove(); };
  };
  render(data);
}

// ---------- boot ----------
connect();
// initial paint from REST in case first WS frame is delayed
api("/api/status").then(s => { if (s && s.received !== undefined) renderStats(s); });
api("/api/flights").then(f => { if (Array.isArray(f)) renderFlights(f); });
