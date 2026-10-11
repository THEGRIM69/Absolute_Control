# UI-4.3 — Icono oficial para Windows

## Recursos

- PNG original: `assets/branding/absolute-control-crown-original.png`
- Icono multirresolución: `assets/branding/absolute-control.ico`
- Resoluciones incluidas: 16×16, 24×24, 32×32, 48×48, 64×64, 128×128 y 256×256.

El PNG original permanece intacto, incluido su fondo azul oscuro. Su SHA-256 es
`7674435E32134B3709010E1245C90D7CCF2909658E1D3B0EBB84CFD8324FD5CD`.

## Generación

El ICO fue generado con Pillow 12.2.0, disponible localmente. Cada resolución
se creó directamente desde el PNG oficial mediante `Image.Resampling.NEAREST`.
Los cuadros ya escalados se proporcionaron explícitamente al escritor de ICO
para impedir que aplicara su remuestreo suavizado predeterminado.

No se aplicaron filtros, antialiasing, transparencia, trazado ni cambios de
color. El ICO conserva las imágenes como cargas PNG RGB dentro del contenedor.

## Validación

La validación comprobó:

- Cabecera ICO (`reserved=0`, `type=1`) y siete entradas.
- Presencia exacta de todas las resoluciones solicitadas.
- Rangos y tamaños válidos para cada carga interna.
- Firma PNG válida en cada entrada.
- Decodificación correcta de todas las imágenes con Pillow.
- Igualdad píxel a píxel con el resultado esperado de escalar el original por
  vecino más cercano.
- Conservación del hash del PNG original antes y después de generar el ICO.

## Uso futuro con jpackage

En una futura fase de empaquetado para Windows, el archivo podrá indicarse a
`jpackage` mediante una opción equivalente a:

```powershell
--icon "assets\branding\absolute-control.ico"
```

Esta fase no modifica todavía el script de empaquetado ni genera instaladores.

## Limitaciones conocidas

La versión 16×16 conserva la silueta general y sigue siendo identificable como
la corona oficial, pero pierde inevitablemente algunos destellos, contornos y
detalles internos por la fuerte reducción. No se creó una variante simplificada
ni se alteró el diseño para compensarlo. Las cargas PNG dentro de ICO son
compatibles con versiones modernas de Windows y con el uso previsto en
`jpackage`.
