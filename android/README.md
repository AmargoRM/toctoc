# TocToc — Timbre NFC (Android)

App de Android que convierte una **etiqueta NFC** en un **timbre inalámbrico**.
Pegás la etiqueta en tu puerta; cuando alguien la toca con su teléfono, tu
teléfono suena a pantalla completa, aunque esté bloqueado.

> Nombre provisional: **TocToc**. `applicationId = app.toctoc.timbre`.

---

## Cómo funciona (arquitectura)

No hay servidor propio ni cuenta de Google/Firebase. El "relevo" del timbre usa
[**ntfy**](https://ntfy.sh) (open-source, gratis, autohospedable).

```
  Visitante                     Relevo (ntfy)                 Dueño (esta app)
 ┌──────────┐   abre URL      ┌──────────────┐   stream      ┌───────────────┐
 │ toca la  │ ──────────────► │ POST /topic  │ ────────────► │ servicio en   │
 │ etiqueta │  (página web)   │  ntfy.sh     │  (JSON SSE)   │ primer plano  │
 │   NFC    │                 └──────────────┘               │  → TIMBRE 🔔  │
 └──────────┘                                                 └───────────────┘
```

1. La etiqueta NFC guarda una URL a una **página web pública** (GitHub Pages)
   con el `topic` único de tu timbre: `…/timbre/?t=<topic>&s=<servidor>&n=<nombre>`.
2. Cualquier visitante (tenga o no la app) toca la etiqueta → se abre la página
   → publica un mensaje en `https://ntfy.sh/<topic>`.
3. Tu teléfono mantiene un **servicio en primer plano** suscripto al stream de
   ese topic. Al llegar el mensaje, lanza la pantalla de timbre con sonido y
   vibración mediante una notificación *full-screen* (patrón de "llamada
   entrante"), por lo que funciona con la pantalla bloqueada.

El `topic` es un identificador aleatorio y difícil de adivinar (`timbre-xxxxxxxx`).
Quien conozca el enlace puede tocar el timbre: es la idea (como un timbre físico).

---

## Compatibilidad

- **minSdk 24** (Android 7.0) → **targetSdk 35** (Android 15). Cubre prácticamente
  todos los teléfonos con NFC en uso.
- NFC declarado como **no obligatorio**: la app igual recibe timbres en teléfonos
  sin NFC (solo no puede *grabar* etiquetas).

---

## Compilar

No hace falta Android Studio para generar el APK: lo hace **GitHub Actions**.
Pero también podés compilar localmente.

### Local

Requisitos: JDK 17 y el SDK de Android (plataforma 35).

```bash
cd android
./gradlew :app:assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

### En GitHub Actions (recomendado)

El workflow [`.github/workflows/android-build.yml`](../.github/workflows/android-build.yml):

1. Compila el APK de release.
2. Lo firma con la **clave compartida** (`android/keystore/toctoc.keystore`).
3. Publica un **Release** con dos assets: `toctoc.apk` y `latest.json`.

Se dispara solo al pushear un tag `v*`, o **a mano** desde la pestaña *Actions →
Build TocToc APK → Run workflow*.

---

## Firma "sin firma de tienda"

La app **no** se publica en Google Play ni usa una clave de Play. Todos los
builds (CI y local) se firman con **la misma** clave de desarrollo versionada en
`android/keystore/toctoc.keystore` (contraseña `toctoc123`).

Esto es a propósito: como la firma es siempre la misma, **las actualizaciones se
instalan encima sin desinstalar** ni perder datos. No es una clave secreta ni
segura para producción en tienda; es lo correcto para una app sideloaded.

Android **no** puede instalar un APK totalmente sin firmar, así que "sin firmas"
se implementa como "una firma propia y estable, no la de Play".

---

## Actualizaciones dentro de la app

1. Descargás **una vez** el primer `toctoc.apk` (desde el Release) y lo instalás.
2. A partir de ahí, en la pantalla **Actualizaciones** tocás *Buscar
   actualización*. La app lee
   `https://github.com/AmargoRM/toctoc/releases/latest/download/latest.json`,
   compara el `versionCode` y, si hay una versión nueva, **descarga e instala**
   el APK con el instalador del sistema.

Para publicar una versión nueva: subí `appVersionCode` y `appVersionName` en
[`app/build.gradle.kts`](app/build.gradle.kts) y corré el workflow. El `latest.json`
se genera automáticamente apuntando al último Release.

---

## Permisos y fiabilidad

Para que **nunca** se pierda un timbre, en el teléfono del dueño conviene:

- Conceder **notificaciones** (se pide al abrir).
- Desactivar la **optimización de batería** para TocToc (botón en la app).
- En Android 14+, activar **Notificaciones a pantalla completa** (botón en la app).
- Instalar apps de **fuentes desconocidas** (lo pide el sistema al actualizar).

---

## Vender/entregar etiquetas NFC

El modelo pensado: entregás/vendés etiquetas NFC ya grabadas. Para grabarlas en
lote, usá el botón **Grabar etiqueta** de la app con el mismo enlace, o cualquier
app de escritura NFC apuntando a la URL del timbre del cliente. Etiquetas
recomendadas: **NTAG213/215/216** (baratas y compatibles).

---

## Estructura

```
android/
├─ app/
│  ├─ src/main/AndroidManifest.xml
│  ├─ src/main/java/app/toctoc/timbre/
│  │  ├─ TocTocApp.kt              # canales de notificación
│  │  ├─ MainActivity.kt           # UI + NFC + permisos
│  │  ├─ BootReceiver.kt           # reinicia el servicio al bootear/actualizar
│  │  ├─ data/                     # DataStore, enlaces, cliente ntfy
│  │  ├─ nfc/NfcHelper.kt          # leer/grabar etiquetas NDEF
│  │  ├─ service/RingListenerService.kt   # escucha en primer plano
│  │  ├─ ring/                     # pantalla de timbre a pantalla completa
│  │  ├─ ui/                       # pantalla principal (Compose)
│  │  └─ update/Updater.kt         # actualizador in-app
│  └─ src/main/res/                # íconos, sonido, temas
├─ keystore/toctoc.keystore        # clave de firma compartida
└─ gradle/ · gradlew · *.gradle.kts

timbre/index.html                  # página web del timbre (GitHub Pages)
```

---

## Puesta en marcha (checklist)

1. **GitHub Pages**: activalo en *Settings → Pages* (rama `main`, carpeta raíz).
   Así la página `…/toctoc/timbre/` queda pública (necesaria para el tap NFC).
2. **Repo público**: los assets del Release deben ser descargables sin login.
3. Corré el workflow **Build TocToc APK** y descargá `toctoc.apk` del Release.
4. Instalalo, poné un nombre al timbre, activá el switch, tocá **Grabar etiqueta**
   y apoyá una etiqueta NFC. Pegala en la puerta. Probá con **Probar timbre**.
