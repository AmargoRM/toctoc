# Seguridad de los datos (Data Safety) — Upe timbre

Respuestas sugeridas para el formulario de Google Play Console.
**Revisalas** antes de enviar; son fieles al funcionamiento actual de la app.

## Resumen
- **Sin cuentas ni datos personales** (no se pide nombre, correo, teléfono, ubicación).
- El único dato que "sale" del teléfono es lo mínimo para entregar el aviso:
  un **identificador aleatorio** del timbre y el **token de mensajería** que usa
  Firebase Cloud Messaging (Google).

---

## Preguntas del formulario

**¿La app recopila o comparte alguno de los tipos de datos requeridos?**
→ **Sí** (por el uso de mensajería push).

**¿Todos los datos se cifran en tránsito?**
→ **Sí** (HTTPS).

**¿Ofrecés una forma de solicitar la eliminación de datos?**
→ **Sí, en parte:** no hay cuenta ni almacenamiento en servidor de datos personales.
Los datos viven en el teléfono; se eliminan al **desinstalar** la app, y el
identificador se puede **regenerar** desde la app cuando quieras.

---

## Tipos de datos a declarar

### Identificadores del dispositivo u otros (Device or other IDs)
- **¿Se recopila?** Sí.
- **¿Se comparte?** Sí — con **Google (Firebase Cloud Messaging)** para poder
  entregar la notificación.
- **Finalidad:** **Funcionalidad de la app** (mensajería / entrega del aviso).
- **¿Es opcional para el usuario?** No (es necesario para que funcione el timbre).
- **¿Se usa para publicidad o marketing?** No.
- **¿Procesamiento efímero?** No aplica / mínimo.

> Nota: el "identificador del timbre" (topic) es un código **aleatorio generado
> en el dispositivo**, no vinculado a la identidad del usuario. Se envía junto al
> aviso solo para enrutarlo al teléfono correcto.

---

## Tipos de datos que NO se recopilan
Marcar **No** en todos estos:
- Información personal (nombre, correo, teléfono, dirección, documento).
- Ubicación (precisa o aproximada).
- Información financiera o de pagos.
- Salud y actividad física.
- Mensajes (SMS, correo).
- Fotos, videos, audio, archivos.
- Contactos, calendario.
- Actividad de navegación web.
- Información de rendimiento con datos personales.

---

## Justificaciones útiles (para otras secciones de la Consola)
- **Permiso de notificaciones a pantalla completa (USE_FULL_SCREEN_INTENT):**
  "La app funciona como un timbre: al recibir un aviso en tiempo real muestra una
  alerta a pantalla completa, similar a una llamada entrante, para que el usuario
  no se pierda a quien está en la puerta."
- **Mensajería (FCM):** "Se usa exclusivamente para entregar el aviso del timbre
  al dueño; no se envían comunicaciones comerciales."
