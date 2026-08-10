/**
 * Relay de Upe timbre — Cloudflare Worker.
 *
 * Recibe la llamada de la página del NFC y envía un push por FCM al "topic"
 * del timbre. Guarda el secreto (cuenta de servicio de Firebase) fuera del
 * alcance del navegador.
 *
 *   GET/POST  /ring?t=<topic>&n=<nombre>
 *
 * Requiere una variable secreta llamada SERVICE_ACCOUNT con el JSON completo
 * de la cuenta de servicio de Firebase (ver relay/README.md).
 */

export default {
  async fetch(request, env) {
    const cors = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
    };
    if (request.method === "OPTIONS") {
      return new Response(null, { headers: cors });
    }

    const url = new URL(request.url);
    if (url.pathname !== "/ring") {
      return new Response("Upe timbre relay OK", { status: 200, headers: cors });
    }

    const topic = (url.searchParams.get("t") || "").trim();
    const name = url.searchParams.get("n") || "la casa";

    // El topic debe ser válido para FCM (evita inyección de rutas)
    if (!/^[A-Za-z0-9-_.~%]{1,120}$/.test(topic)) {
      return json({ ok: false, error: "topic inválido" }, 400, cors);
    }

    try {
      const sa = JSON.parse(env.SERVICE_ACCOUNT);
      const accessToken = await getAccessToken(sa);
      const res = await fetch(
        `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`,
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${accessToken}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            message: {
              topic: topic,
              // Mensaje DATA (no "notification") para que la app lo maneje
              // siempre y muestre el timbre a pantalla completa.
              data: {
                name: name,
                message: `Alguien está tocando el timbre de ${name}`,
              },
              android: { priority: "high" },
            },
          }),
        }
      );
      const body = await res.text();
      return json({ ok: res.ok, status: res.status, body }, res.ok ? 200 : 502, cors);
    } catch (e) {
      return json({ ok: false, error: String(e && e.message || e) }, 500, cors);
    }
  },
};

function json(obj, status, cors) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { ...cors, "Content-Type": "application/json" },
  });
}

// ---- OAuth2 con la cuenta de servicio (JWT RS256 -> access_token) ----
async function getAccessToken(sa) {
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", typ: "JWT" };
  const claims = {
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  };
  const enc = (o) => b64url(new TextEncoder().encode(JSON.stringify(o)));
  const unsigned = `${enc(header)}.${enc(claims)}`;
  const key = await importPrivateKey(sa.private_key);
  const sig = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(unsigned)
  );
  const jwt = `${unsigned}.${b64url(new Uint8Array(sig))}`;

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body:
      "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=" + jwt,
  });
  const j = await res.json();
  if (!j.access_token) throw new Error("sin access_token: " + JSON.stringify(j));
  return j.access_token;
}

function b64url(bytes) {
  let bin = "";
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function importPrivateKey(pem) {
  const clean = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s+/g, "");
  const bin = atob(clean);
  const buf = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) buf[i] = bin.charCodeAt(i);
  return crypto.subtle.importKey(
    "pkcs8",
    buf.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );
}
