# Fase 2 — Materiales para Google Play (Upe timbre)

Todo lo que necesitás para cargar la ficha y el cumplimiento en Play Console.

## Archivos
| Archivo | Para qué |
|---|---|
| [`privacidad.html`](./privacidad.html) | **Política de privacidad** (obligatoria). Se publica en Pages: `https://amargorm.github.io/toctoc/playstore/privacidad.html` |
| [`ficha.md`](./ficha.md) | Textos de la ficha: nombre, descripciones, categoría, contacto |
| [`data-safety.md`](./data-safety.md) | Respuestas del formulario **Seguridad de los datos** |
| `assets/icon-512.png` | **Ícono** de la app (512×512) ✅ |
| `assets/feature-graphic.png` | **Gráfico destacado** (1024×500) ✅ listo para subir |
| `assets/feature-graphic.svg` | Fuente editable del gráfico destacado |

## Checklist en Play Console
- [ ] **Política de privacidad**: pegar la URL de arriba (se activa al mergear a `main` con Pages).
- [ ] **Ficha principal**: copiar de `ficha.md` (nombre, descripción corta y larga, categoría).
- [ ] **Ícono**: subir `assets/icon-512.png`.
- [ ] **Gráfico destacado**: subir `assets/feature-graphic.png` (ya está en 1024×500).
- [ ] **Capturas** (mín. 2): sacarlas del teléfono (ver `ficha.md`).
- [ ] **Seguridad de los datos**: completar según `data-safety.md`.
- [ ] **Clasificación de contenido**: cuestionario (app utilitaria, sin contenido sensible).
- [ ] **Correo de contacto**: `amargo95@gmail.com` (cambialo si querés otro).

## Notas
- El **correo de contacto** de la política y la ficha es `amargo95@gmail.com`.
  Si querés usar otro, cambialo en `privacidad.html` y en `ficha.md`.
- La política queda pública **al mergear a `main`** (GitHub Pages ya está activo).
- Estos materiales **no** afectan la app ni el build; son solo para la tienda.

## Lo que falta (Fase 1 — código, más adelante)
- Build "Play": quitar el autoactualizador y (gracias a FCM) el servicio en primer
  plano permanente.
- AAB + clave de subida + Play App Signing + actualizar `assetlinks.json` con la
  huella de Play.
