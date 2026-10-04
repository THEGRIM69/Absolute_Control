# Fase 2: transición entre pantallas

## Objetivo

Que el cambio de control se sienta como si los monitores de ambas PCs estuvieran uno junto al otro:

```text
[ PC PRINCIPAL ] [ PC SECUNDARIA ]
```

Cada PC conserva su propio monitor. No se transmite video.

## Centro dinámico y GeometriaPantalla

El centro fijo `683,384` fue eliminado del código de producción. Esos números siguen presentes únicamente como valores esperados de pruebas para 1366×768.

[GeometriaPantalla.java](../src/Absolute_Control/core/GeometriaPantalla.java) centraliza dimensiones, centro, bordes, altura relativa, puntos de entrada y límites del movimiento:

```text
ancho, alto = Toolkit.getDefaultToolkit().getScreenSize()
centro      = (ancho / 2, alto / 2), con división entera
izquierda   = 0
derecha     = ancho - 1
```

La geometría usa las coordenadas de AWT/Robot y una sola pantalla por PC. Se obtiene al activar el cliente o iniciar el servidor; no se recalcula automáticamente si cambia la resolución durante una sesión.

## Izquierda y derecha

`secundariaALaDerecha` significa la posición de la secundaria respecto a la principal. La interfaz usa esa misma definición en ambos modos, y el mensaje de entrada define la disposición de la sesión remota.

| Disposición | Salida de principal | Entrada en secundaria | Regreso desde secundaria |
|---|---|---|---|
| Secundaria a la derecha | Borde derecho | Cerca del borde izquierdo | Borde izquierdo |
| Secundaria a la izquierda | Borde izquierdo | Cerca del borde derecho | Borde derecho |

## Altura relativa

La salida se normaliza como `y / (altoOrigen - 1)`, limitada a `[0,1]`. En destino se calcula `round(alturaRelativa * (altoDestino - 1))`; una pantalla de altura 1 usa altura relativa 0.

Cruzar aproximadamente al 70% de la principal coloca el cursor aproximadamente al 70% de la secundaria, incluso con alturas diferentes. El regreso utiliza el mismo criterio hacia la principal.

## Margen de entrada y regreso

`MARGEN_ENTRADA` vale **8 píxeles** y `TOLERANCIA_BORDE` vale **2 píxeles**. La entrada normal queda fuera de la zona que dispara el regreso: a la izquierda usa `x = 8`; a la derecha, `x = ancho - 1 - 8`. En pantallas muy pequeñas el margen se reduce a `min(8, (ancho - 1) / 2)` para no salir de sus límites.

El monitor remoto espera a que el mouse esté inicializado antes de comprobar el borde. Así no devuelve el control por la posición residual anterior al mensaje de entrada. La separación respecto del borde está validada para las resoluciones probadas, no para dimensiones artificiales extremadamente pequeñas.

Al recibir un `REGRESAR` válido de la sesión actual, la principal recoloca el cursor cerca de su borde de salida, con margen y altura conservada. Ignora el evento de esa recolocación y arma el borde para un nuevo cruce inmediato. Una caída sin `REGRESAR` mantiene el enfriamiento y rearme de Fase 1.1.

## Movimiento y protocolo ENTRAR

El cliente ancla el cursor local en su centro dinámico, mide el desplazamiento y envía **`D,dx,dy`**. El servidor aplica el delta y lo limita a su pantalla. No se envía un escritorio completo ni se reemplazó este flujo por posiciones absolutas.

El mismo escritor TCP envía primero:

```text
ENTRAR,DERECHA,0.7
ENTRAR,IZQUIERDA,0.7
```

El segundo campo indica dónde está la secundaria; el tercero es un número finito de `[0,1]`. Se envía una de esas variantes por sesión antes de PING y los deltas. El servidor coloca el cursor y habilita la comprobación del borde.

El regreso actual puede ser `REGRESAR,0.8`. El cliente también acepta `REGRESAR` sin altura, usando la altura guardada al entrar. `PING` solo comprueba la sesión y obtiene `PONG`: no inicializa ni mueve el cursor. Si un emisor no envía `ENTRAR`, el servidor conserva compatibilidad inicializando con su configuración local y altura `0.5` al recibir el primer comando de entrada real, y usa regreso sin altura. Esto no constituye negociación de versiones.

## Pruebas

[MouseTransitionCheck.java](../tests/MouseTransitionCheck.java) se ejecuta mediante [run-mouse-transition.ps1](../tests/run-mouse-transition.ps1):

```powershell
.\tests\run-mouse-transition.ps1
```

✅ Validación final registrada el **2026-10-03**, con código **0**:

- Geometría para 1366×768, 1920×1080 y 2560×1440.
- Ambos lados, centro, altura relativa, margen, deltas y rearme.
- Protocolo con sockets reales en localhost.
- **54 ciclos cliente/servidor**: 18 combinaciones de resolución/lado, tres ciclos por combinación; entrada al 70% y regreso al 80%.
- Liberación de teclas y botones por sesión.
- **0 hilos `kvm-` vivos al finalizar.**

El cálculo geométrico se prueba sin entorno gráfico. La integración usa sockets reales y entrada/cursor simulados; las clases derivadas de Robot requieren entorno gráfico. La suite completa de estabilidad también terminó con código 0 sobre los archivos actuales.

🟡 La validación definitiva en dos PCs físicas continúa pendiente, incluidos escalado de pantalla, cruces rápidos, cortes de red y Escape durante uso real.
