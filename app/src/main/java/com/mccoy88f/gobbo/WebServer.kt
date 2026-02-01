package com.mccoy88f.gobbo

import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Server HTTP snello per il controllo remoto web del teleprompter.
 * Serve una pagina Material UI e API REST per play/pause, scroll, WPM, file importati, ecc.
 * Se pinHash è impostato, richiede PIN per accedere alla pagina e alle API.
 */
class WebServer(
    port: Int,
    private val controller: WebRemoteController,
    private val deviceName: String,
    private val pinHash: String?
) : NanoHTTPD(port) {

    private val validTokens = mutableSetOf<String>()
    private val pinRequired: Boolean get() = !pinHash.isNullOrBlank()

    private fun getToken(session: IHTTPSession): String? =
        paramValue(session.getParms(), "token")

    private fun requireAuth(token: String?): Boolean =
        !pinRequired || (token != null && token in validTokens)

    private fun hashPin(pin: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            md.digest(pin.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: ""
        val method = session.method
        val token = getToken(session)

        return when {
            uri == "/login" && method == Method.POST -> serveLoginPost(session)
            uri == "/" || uri.startsWith("/index.html") -> {
                if (requireAuth(token)) serveHtml(token ?: "")
                else serveLoginPage(error = false)
            }
            uri.startsWith("/api/") -> {
                if (!requireAuth(token)) newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_PLAINTEXT, "Unauthorized")
                else when {
                    uri.startsWith("/api/state") -> serveState()
            uri.startsWith("/api/recent-files") -> serveRecentFiles()
            uri.startsWith("/api/load-recent") -> {
                val params = session.getParms()
                val index = paramValue(params, "index")?.toIntOrNull()
                if (index != null) controller.loadRecentFile(index)
                okJson("""{"ok":true}""")
            }
            uri.startsWith("/api/play-pause") && (method == Method.POST || method == Method.GET) -> {
                controller.onPlayPause()
                okJson("""{"ok":true}""")
            }
            uri.startsWith("/api/scroll") -> {
                val params = session.getParms()
                val dir = paramValue(params, "dir", "direction")?.lowercase() ?: ""
                when (dir) {
                    "up" -> controller.onScrollUp()
                    "down" -> controller.onScrollDown()
                }
                okJson("""{"ok":true}""")
            }
            uri.startsWith("/api/wpm") -> {
                val params = session.getParms()
                val value = paramValue(params, "value")?.toIntOrNull()?.coerceIn(60, 250)
                if (value != null) controller.onSetWpm(value)
                okJson("""{"ok":true}""")
            }
            uri.startsWith("/api/text-size") -> {
                val params = session.getParms()
                val value = paramValue(params, "value")?.toFloatOrNull()?.coerceIn(12f, 48f)
                if (value != null) controller.onSetTextSize(value)
                okJson("""{"ok":true}""")
            }
            uri.startsWith("/api/scroll-mode") -> {
                val params = session.getParms()
                val value = paramValue(params, "value")?.toIntOrNull()?.coerceIn(0, 2)
                if (value != null) controller.onChangeScrollMode(value)
                okJson("""{"ok":true}""")
            }
                    else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
                }
            }
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    }

    private fun serveLoginPost(session: IHTTPSession): Response {
        try { session.parseBody(mutableMapOf()) } catch (_: Exception) { }
        val params = session.getParms()
        val pin = paramValue(params, "pin")?.trim() ?: ""
        val hash = hashPin(pin)
        if (pinHash != null && hash == pinHash) {
            val newToken = UUID.randomUUID().toString()
            validTokens.add(newToken)
            val redirect = newFixedLengthResponse(Response.Status.REDIRECT, MIME_HTML, "")
            redirect.addHeader("Location", "/?token=$newToken")
            return redirect
        }
        return serveLoginPage(error = true)
    }

    private fun serveLoginPage(error: Boolean): Response {
        val errMsg = if (error) """<p style="color:#d32f2f;font-size:14px;">PIN non corretto.</p>""" else ""
        val html = LOGIN_PAGE.replace("{{ERROR}}", errMsg)
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)), html.length.toLong())
    }

    private fun paramValue(params: Map<String, String>, vararg keys: String): String? {
        for (key in keys) {
            val v = params[key]
            if (v != null) return v
        }
        return null
    }

    private fun serveRecentFiles(): Response {
        val files = controller.getRecentFiles()
        val json = files.joinToString(",") { """{"index":${it.first},"name":${escapeJson(it.second)}}""" }
        return okJson("[$json]")
    }

    private fun escapeJson(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    private fun serveState(): Response {
        val state = controller.getState()
        val json = """{"playing":${state.playing},"wpm":${state.wpm},"textSize":${state.textSize},"hasText":${state.hasText},"scrollMode":${state.scrollMode}}"""
        return okJson(json)
    }

    private fun okJson(json: String): Response {
        return newFixedLengthResponse(Response.Status.OK, "application/json", json)
    }

    private fun serveHtml(token: String): Response {
        val deviceNameEscaped = deviceName.replace("<", "&lt;").replace(">", "&gt;")
        val deviceNameVisible = if (deviceName.isEmpty()) "display:none" else ""
        val tokenEscaped = token.replace("\\", "\\\\").replace("'", "\\'")
        val html = HTML_PAGE
            .replace("{{DEVICE_NAME}}", deviceNameEscaped)
            .replace("{{DEVICE_NAME_VISIBLE}}", deviceNameVisible)
            .replace("{{TOKEN}}", tokenEscaped)
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)), html.length.toLong())
    }

    companion object {
        private const val LOGIN_PAGE = """
<!DOCTYPE html>
<html lang="it">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Accesso - Gobbo Teleprompter</title>
  <link rel="stylesheet" href="https://unpkg.com/material-components-web@14.0.0/dist/material-components-web.min.css">
  <link href="https://fonts.googleapis.com/icon?family=Material+Icons" rel="stylesheet">
  <link href="https://fonts.googleapis.com/css2?family=Roboto:wght@400;500;700&display=swap" rel="stylesheet">
  <style>
    * { box-sizing: border-box; }
    body { font-family: 'Roboto', sans-serif; margin: 0; padding: 16px; background: #f5f5f5; min-height: 100vh; display: flex; align-items: center; justify-content: center; }
    .mdc-card { background: #fff; max-width: 420px; width: 100%; margin: 0 auto; padding: 24px; border-radius: 12px; }
    .title { font-size: 20px; font-weight: 500; margin: 0 0 4px; color: #1f1f1f; }
    .subtitle { font-size: 14px; color: #5f6368; margin: 0 0 20px; }
    .login-input { width: 100%; padding: 12px; border: 1px solid #dadce0; border-radius: 8px; font-size: 16px; margin-bottom: 16px; background: #fff; }
    .mdc-button { width: 100%; }
  </style>
</head>
<body>
  <div class="mdc-card">
    <h1 class="title">Accesso controllo web</h1>
    <p class="subtitle">Inserisci il PIN configurato nell'app.</p>
    {{ERROR}}
    <form method="post" action="/login">
      <input type="password" class="login-input" name="pin" placeholder="PIN" inputmode="numeric" pattern="[0-9]*" maxlength="8" autofocus required>
      <button type="submit" class="mdc-button mdc-button--raised">
        <span class="mdc-button__label">Accedi</span>
      </button>
    </form>
  </div>
</body>
</html>
"""
        private const val HTML_PAGE = """
<!DOCTYPE html>
<html lang="it">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Gobbo Teleprompter</title>
  <link rel="stylesheet" href="https://unpkg.com/material-components-web@14.0.0/dist/material-components-web.min.css">
  <link href="https://fonts.googleapis.com/icon?family=Material+Icons" rel="stylesheet">
  <link href="https://fonts.googleapis.com/css2?family=Roboto:wght@400;500;700&display=swap" rel="stylesheet">
  <style>
    * { box-sizing: border-box; }
    body { font-family: 'Roboto', sans-serif; margin: 0; padding: 16px; background: #f5f5f5; min-height: 100vh; }
    .mdc-card { max-width: 420px; margin: 0 auto 16px; padding: 24px; border-radius: 12px; }
    .title { font-size: 20px; font-weight: 500; margin: 0 0 4px; color: #1f1f1f; }
    .device-name { font-size: 14px; color: #5f6368; margin: 0 0 16px; }
    .row { display: flex; gap: 12px; align-items: center; margin-bottom: 16px; flex-wrap: wrap; }
    .row .mdc-button { flex: 1; min-width: 100px; }
    .status { font-size: 14px; color: #5f6368; margin-top: 8px; }
    .mdc-slider { width: 100%; margin: 8px 0 16px; }
    .label { font-size: 14px; color: #5f6368; margin-bottom: 4px; }
    .wpm-row { display: flex; gap: 12px; align-items: center; margin-bottom: 8px; }
    .wpm-row input[type="number"] { width: 72px; padding: 8px 12px; border: 1px solid #dadce0; border-radius: 8px; font-size: 16px; }
    select { width: 100%; padding: 12px; border: 1px solid #dadce0; border-radius: 8px; font-size: 14px; margin-top: 4px; background: #fff; }
  </style>
</head>
<body>
  <div class="mdc-card" style="background: #fff;">
    <h1 class="title">Gobbo Teleprompter</h1>
    <p class="device-name" id="deviceName" style="{{DEVICE_NAME_VISIBLE}}">{{DEVICE_NAME}}</p>
    <p class="status" id="status">Caricamento…</p>

    <div class="row">
      <button type="button" class="mdc-button mdc-button--raised" id="btnPlayPause">
        <span class="mdc-button__label"><span class="material-icons" style="vertical-align: middle; margin-right: 4px;">play_arrow</span><span id="playLabel">Avvia</span></span>
      </button>
    </div>

    <div class="row">
      <button type="button" class="mdc-button mdc-button--outlined" id="btnScrollUp">
        <span class="mdc-button__label"><span class="material-icons" style="vertical-align: middle;">arrow_upward</span> Su</span>
      </button>
      <button type="button" class="mdc-button mdc-button--outlined" id="btnScrollDown">
        <span class="mdc-button__label"><span class="material-icons" style="vertical-align: middle;">arrow_downward</span> Giù</span>
      </button>
    </div>

    <div class="label">Parole/min (WPM) — come nell'app (60–250)</div>
    <div class="wpm-row">
      <input type="range" class="mdc-slider" id="wpmSlider" min="60" max="250" value="120" step="1" aria-label="WPM" style="flex:1;">
      <input type="number" id="wpmInput" min="60" max="250" value="120" step="1" aria-label="WPM">
      <button type="button" class="mdc-button mdc-button--outlined" id="btnSetWpm" style="flex-shrink:0;">
        <span class="mdc-button__label">Imposta WPM</span>
      </button>
    </div>

    <div class="label">Dimensione testo</div>
    <input type="range" class="mdc-slider" id="textSizeSlider" min="12" max="48" value="24" step="2" aria-label="Dimensione testo">

    <div class="label">File importati</div>
    <select id="recentFiles">
      <option value="">— Seleziona un file —</option>
    </select>
    <button type="button" class="mdc-button mdc-button--outlined" id="btnLoadRecent" style="margin-top:8px;width:100%;">
      <span class="mdc-button__label">Apri file selezionato</span>
    </button>
  </div>

  <script src="https://unpkg.com/material-components-web@14.0.0/dist/material-components-web.min.js"></script>
  <script>
    (function() {
      const AUTH_TOKEN = '{{TOKEN}}';
      function authUrl(path) {
        var sep = path.indexOf('?') >= 0 ? '&' : '?';
        return path + (AUTH_TOKEN ? sep + 'token=' + encodeURIComponent(AUTH_TOKEN) : '');
      }
      const statusEl = document.getElementById('status');
      const playLabel = document.getElementById('playLabel');
      const btnPlayPause = document.getElementById('btnPlayPause');
      const btnScrollUp = document.getElementById('btnScrollUp');
      const btnScrollDown = document.getElementById('btnScrollDown');
      const wpmSlider = document.getElementById('wpmSlider');
      const wpmInput = document.getElementById('wpmInput');
      const btnSetWpm = document.getElementById('btnSetWpm');
      const textSizeSlider = document.getElementById('textSizeSlider');
      const recentFiles = document.getElementById('recentFiles');
      const btnLoadRecent = document.getElementById('btnLoadRecent');

      function api(method, path) {
        return fetch(authUrl(path), { method: method || 'GET' }).then(r => r.json()).catch(() => ({}));
      }

      function setWpm(v) {
        const val = Math.max(60, Math.min(250, parseInt(v, 10) || 120));
        wpmSlider.value = val;
        wpmInput.value = val;
        return api('POST', '/api/wpm?value=' + val);
      }

      function poll() {
        fetch(authUrl('/api/state')).then(r => r.json()).then(s => {
          statusEl.textContent = s.hasText ? (s.playing ? 'In riproduzione' : 'In pausa') + ' · ' + s.wpm + ' parole/min' : 'Nessun testo caricato';
          playLabel.textContent = s.playing ? 'Pausa' : 'Avvia';
          var w = s.wpm || 120;
          var editingWpm = document.activeElement === wpmInput || document.activeElement === wpmSlider;
          if (!editingWpm) {
            wpmSlider.value = w;
            wpmInput.value = w;
          }
          textSizeSlider.value = Math.max(12, Math.min(48, s.textSize || 24));
        }).catch(() => statusEl.textContent = 'Disconnesso');
      }

      function loadRecentFiles() {
        fetch(authUrl('/api/recent-files')).then(r => r.json()).then(arr => {
          recentFiles.innerHTML = '<option value="">— Seleziona un file —</option>';
          (arr || []).forEach(f => {
            const opt = document.createElement('option');
            opt.value = f.index;
            opt.textContent = f.name || ('File ' + (f.index + 1));
            recentFiles.appendChild(opt);
          });
        });
      }

      btnPlayPause.addEventListener('click', () => api('POST', '/api/play-pause').then(poll));
      btnScrollUp.addEventListener('click', () => api('POST', '/api/scroll?dir=up'));
      btnScrollDown.addEventListener('click', () => api('POST', '/api/scroll?dir=down'));

      wpmSlider.addEventListener('input', () => { wpmInput.value = wpmSlider.value; });
      wpmSlider.addEventListener('change', () => setWpm(wpmSlider.value).then(poll));
      wpmInput.addEventListener('change', () => setWpm(wpmInput.value).then(poll));
      if (btnSetWpm) btnSetWpm.addEventListener('click', () => setWpm(wpmInput.value).then(poll));

      textSizeSlider.addEventListener('change', () => api('POST', '/api/text-size?value=' + textSizeSlider.value).then(poll));

      btnLoadRecent.addEventListener('click', () => {
        const idx = recentFiles.value;
        if (idx !== '') api('POST', '/api/load-recent?index=' + idx).then(() => { poll(); loadRecentFiles(); });
      });

      poll();
      loadRecentFiles();
      setInterval(poll, 2000);
    })();
  </script>
</body>
</html>
"""
    }
}
