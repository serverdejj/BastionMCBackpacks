# BastionMCBackpacks v1.0.0

Plugin para **BASTION MC**, Purpur/Paper **1.21.11**, Java 21.

## Características

- 25 mochilas individuales por usuario, contenido persistente en `plugins/BastionMCBackpacks/data/<uuid>/<numero>.yml`.
- Mochilas 1–3: 27 espacios; 4–10: 36; 11–25: 54.
- `/pv`, `/mochila`, `/mochilas`: menú de las 25 mochilas.
- `/pv 1`, `/pv 2`, `/mochila 3`, etc.: abrir directamente.
- GUI con barriles y efecto glow en las mochilas desbloqueadas.
- Mochilas bloqueadas con mensaje configurable.
- Permisos independientes `bastionbackpacks.use.1` a `bastionbackpacks.use.25`.
- Los permisos 1–3 son **default: true**: todos los jugadores los reciben automáticamente, sin configurar LuckPerms.
- Permiso `bastionbackpacks.admin` predeterminado para operadores, permite todas las mochilas.
- Todos los nombres, lore, materiales, slots, tamaños, mensajes, colores y permisos pueden configurarse en YAML.
- Colores `&` y HEX `&#RRGGBB`; los degradados pueden definirse carácter por carácter.
- Guardado al cerrar mochila, salir y apagar el servidor, más autosalvado.

## Permisos de LuckPerms

Para habilitar la mochila 4 para un rango:

`/lp group vikingo permission set bastionbackpacks.use.4 true`

Repite para cada mochila hasta la 25. Si un rango hereda de `default`, ya tiene 1–3.

Para retirar una mochila, desactiva su permiso. Los objetos siguen almacenados y se recuperan cuando vuelva a tener permiso.

## Compilar por GitHub Actions

Sube el **contenido** del ZIP a la raíz del repositorio, incluyendo `.github/workflows/build.yml` (GitHub oculta carpetas con punto en algunas vistas). En la pestaña **Actions**, ejecuta **Compilar BastionMCBackpacks** y descarga el ZIP `BastionMCBackpacks-JAR` de Artifacts. Dentro está el `.jar`.

No necesita Vault ni PlaceholderAPI; funciona con permisos de LuckPerms a través de Bukkit.

**Importante:** no borres la carpeta `data/` en futuras actualizaciones. Este proyecto no ha sido compilado ni probado directamente en un servidor Purpur 1.21.11.
