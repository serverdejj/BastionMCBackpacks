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

## v1.0.1 — Menu actualizado

- Menu de 54 slots, con ultima fila decorativa de cristal negro (slots 45–53).
- Mochilas 21–25 movidas una columna a la izquierda en la quinta fila (slots 38–42).
- Al actualizar desde v1.0.0, si la config conserva las posiciones y el cristal predeterminados, se migran automaticamente. Configuraciones personalizadas se respetan.
- Mantiene sin cambios archivos de data, permisos y capacidades de las 25 mochilas.

## Diseno v1.0.3
- GUI de 54 espacios con bordes de cristal negro.
- 25 barriles en slots: 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40 (indices desde 0).
- La ultima hilera se desplazo un espacio a la izquierda.
- Migra automaticamente configuraciones de menu predeterminadas anteriores, sin cambiar posiciones personalizadas ni datos guardados de las mochilas.


## v1.0.4: Administrar mochilas y reload
- `/mochilas ver <jugador>`: menú de las 25 mochilas de un jugador conocido (conectado o desconectado).
- `/mochilas ver <jugador> <1-25>`: abre directamente la mochila ajena para agregar o retirar objetos.
- `/mochilas reload`: recarga `config.yml` y `messages.yml` sin borrar los inventarios ya abiertos.
- Permisos: `bastionbackpacks.admin.view` y `bastionbackpacks.reload` (OP por defecto).
- Mantiene el menú v1.0.3 con cristal negro, los permisos de mochilas y el contenido de `plugins/BastionMCBackpacks/data/`.
- Por seguridad no crea perfiles de jugadores desconocidos: el jugador debe haberse conectado al servidor alguna vez.
- Puede haber inventarios abiertos mientras se usan los comandos; la edición es compartida en memoria y se guarda al cerrar.
