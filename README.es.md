# Orange TV para Android TV / Fire TV (no oficial)

🌐 [Français](README.md) · [English](README.en.md) · [Nederlands](README.nl.md) · [Deutsch](README.lu.md) · [Română](README.ro.md) · [Polski](README.pl.md) · [Slovenčina](README.sk.md) · **Español**

> Proyecto personal **no oficial**, sin relación con Orange.

App Android que muestra la web de Orange TV de tu país a pantalla completa y la hace usable con el mando.
Probada en Fire TV Stick 4K Max (Fire OS 8). Debería funcionar en Android TV / Google TV (sin probar).

![Página de inicio de orangetv.orange.es en la app](docs/screenshots/es.jpg)

## Lo que añade la app

- Navegación con las flechas + OK entre los elementos de la web, con un marco de selección
- Los controles del reproductor aparecen solos; Atrás cierra primero el panel abierto
- Inicio de sesión con código QR: usuario y contraseña se escriben en el móvil (misma wifi que la TV)
- Textos de la app en 8 idiomas (idioma del sistema); el idioma de la web depende de cada web

| Tecla | Acción |
|---|---|
| Flechas / OK | Mover la selección / hacer clic |
| Atrás | Cerrar el panel abierto, si no, página anterior |
| ⏪ / ⏩ | −10 s / +10 s |
| ⏯ | Reproducir / pausa |
| Canal + / − | Canal siguiente / anterior |
| Menú | Cursor libre (activar / desactivar) |
| Menú (pulsación larga) | Elegir la web de Orange (país); «Automático» sigue la región de la TV |

## Instalación

**Directamente en la TV, sin PC:**

1. Instala la app **Downloader** (Appstore de Amazon).
2. Permite que Downloader instale apps: Configuración > Mi Fire TV > Opciones para desarrolladores > Instalar apps desconocidas > Downloader (si falta el menú: Mi Fire TV > Acerca de, pulsa 7 veces el nombre del dispositivo).
3. En Downloader, escribe este enlace corto y luego Instalar:

   ```
   is.gd/orangetvapk
   ```

   **Antes de instalar, comprueba adónde lleva:** abre `is.gd/orangetvapk-` (con un guion al final), en Downloader o en un navegador. La página «Shortened link» debe mostrar exactamente:

   ```
   https://github.com/tsnaketech/orange-tv/releases/latest/download/orange-tv.apk
   ```

   Si muestra otra cosa, no instales. El enlace completo de arriba también funciona directamente en Downloader.

El enlace da siempre la última versión. Para actualizar, repite el paso 3: la sesión se conserva.

**Desde un PC:** Descarga `orange-tv-<version>.apk` desde las [Releases](https://github.com/tsnaketech/orange-tv/releases/latest), activa la depuración ADB en la TV y luego:

```powershell
adb connect <IP de la TV>:5555
adb install -r orange-tv-<version>.apk
```

Desde el código (desarrolladores): requiere Android Studio. La build debug se instala junto a la release, como «Orange TV (debug)». Requisitos: Android Studio, `adb`, depuración ADB activada en la TV.

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
adb connect <IP de la TV>:5555
.\gradlew.bat installDebug
```

## Particularidades de España

- La app abre la web de Orange del país configurado en la TV (idioma y región del sistema); si no, la web
  francesa https://tv.orange.fr/.
- En España, Orange TV ya tiene una app oficial en la Appstore de Amazon para Fire TV: es la opción recomendada.
- Con la región España, la app abre https://orangetv.orange.es/ (cuenta Orange España, solo dentro del EEE).
  Tiene su propio inicio de sesión con código QR. Su navegación con mando solo cubre el menú superior en
  navegador, así que la app navega por las tarjetas y desliza los carruseles al llegar al final de una fila;
  Canal ± se convierte en Re Pág / Av Pág. Navegación comprobada en la TV, sin cuenta (reproducción sin probar).
