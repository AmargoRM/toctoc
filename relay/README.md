# Relay de Upe timbre (Cloudflare Worker)

Este Worker recibe el toque de la etiqueta NFC (desde la página web) y envía el
push por **FCM** al topic del timbre. Es gratis y no necesita tarjeta.

## Qué hace
```
Página NFC  ──▶  GET https://<tu-worker>.workers.dev/ring?t=<topic>&n=<nombre>
                    │
                    ├─ genera un token con la cuenta de servicio de Firebase
                    └─ envía push FCM al topic  ──▶  suena la app (cerrada/dormida)
```

## Requisitos previos
- El proyecto de Firebase **upe-timbre** ya creado (hecho ✅).
- Una **cuenta de servicio** de ese proyecto (la generamos abajo).
- Una cuenta de **Cloudflare** (gratis).

---

## Paso 1 — Generar la cuenta de servicio (el "secreto")
1. Firebase Console → ⚙️ **Configuración del proyecto** → pestaña **Cuentas de servicio**.
2. **Generar nueva clave privada** → descarga un archivo `.json`.
   - ⚠️ Este archivo **es secreto**: NO se sube al repo. Solo se pega en Cloudflare.

## Paso 2 — Crear el Worker (todo en el navegador)
1. Entrá a **https://dash.cloudflare.com** → creá cuenta / iniciá sesión.
2. **Workers & Pages** → **Create** → **Create Worker**.
3. Nombre: `upe-timbre-relay` → **Deploy** (deploya el de ejemplo).
4. **Edit code** → borrá todo y pegá el contenido de [`worker.js`](./worker.js) → **Deploy**.

## Paso 3 — Cargar el secreto
1. En el Worker → **Settings** → **Variables and Secrets** → **Add**.
2. Tipo **Secret**. Nombre **exacto**: `SERVICE_ACCOUNT`.
3. Valor: **pegá el contenido completo** del `.json` de la cuenta de servicio (Paso 1).
4. **Save and deploy**.

## Paso 4 — Probar
- La URL del Worker es algo como `https://upe-timbre-relay.TU-SUBDOMINIO.workers.dev`.
- Abrí en el navegador: `https://…workers.dev/ring?t=timbre-xxxx&n=Casa`
  (con un topic real que tengas suscripto en la app).
- Si la app está instalada y con el timbre activo, **debería sonar**.
- La respuesta muestra `{"ok":true,...}` si FCM aceptó el envío.

## Paso 5 — Pasame la URL del Worker
Con esa URL, actualizo la **página del NFC** para que llame al relay (además de
ntfy, durante la transición). Ahí queda todo conectado.

---

### Notas
- El Worker **no guarda datos**: solo reenvía. El único dato sensible es el
  `SERVICE_ACCOUNT`, que vive como secreto en Cloudflare.
- Si algún día rotás la clave de servicio, actualizá el secreto y listo.

---

## Diagnóstico de errores (qué devuelve `/ring`)
Abrí `https://…workers.dev/ring?t=timbre-xxxx&n=test` en el navegador:

| Respuesta | Causa | Solución |
|---|---|---|
| `{"ok":true,...}` | Todo bien | 🎉 |
| `Cannot read properties of undefined (reading 'replace')` | El `SERVICE_ACCOUNT` **no tiene `private_key`** (JSON incompleto o pegaste algo que no era el archivo de la cuenta de servicio). | Volvé a hacer el **Paso 1** y **Paso 3**: generá una clave privada nueva y pegá el **JSON COMPLETO** en el secreto. |
| `Falta el secreto SERVICE_ACCOUNT` | El secreto no existe o se llama distinto. | Paso 3: creá el secreto con nombre **exacto** `SERVICE_ACCOUNT`. |
| `SERVICE_ACCOUNT no es JSON válido` | Se cortó/pegó mal el JSON. | Repetí el Paso 3 pegando el archivo completo. |
| `Al SERVICE_ACCOUNT le faltan campos: ...` | El JSON existe pero le faltan campos. | Pegá el `.json` **completo** sin editar. |
| `sin access_token: ...` | La cuenta de servicio no tiene permisos FCM, o el proyecto está mal. | Verificá que la cuenta sea del proyecto **upe-timbre** y tenga rol de mensajería. |

> ⚠️ **El error actual es el de la primera fila**: `reading 'replace'` = falta
> `private_key`. Hay que **regenerar la clave** (Firebase → Cuentas de servicio →
> *Generar nueva clave privada*) y **pegar el JSON completo** en el secreto
> `SERVICE_ACCOUNT` de Cloudflare. Un error común es pegar solo un fragmento o
> el JSON de configuración del cliente (`google-services.json`) en vez del de
> la **cuenta de servicio**: son archivos distintos.
