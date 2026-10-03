# Roadmap

Este documento separa el código existente de las propuestas. **Las funciones planeadas todavía no existen y no tienen una fecha comprometida.**

## Implementado

- ✅ KVM por LAN: principal como cliente TCP y secundaria como servidor.
- ✅ Mouse relativo, botones izquierdo/derecho y rueda.
- ✅ Teclado con los mapas actuales y Escape para recuperar control local.
- ✅ Discovery UDP y configuración manual de conexión.
- ✅ Heartbeat y recuperación ante caída, sin reconexión automática.
- ✅ Limpieza tardía, aislamiento de sesiones y liberación de entradas.
- ✅ Reinicio de servidor y Discovery tras terminar la parada.
- ✅ Transición izquierda/derecha y altura relativa.
- ✅ Geometría dinámica de una pantalla por PC.
- ✅ Pruebas automáticas de estabilidad y transición, con salida 0 en la validación del 2026-10-03.

Los detalles están en [Fase 1](Fase-1-Conexiones.md), [Fase 1.1](Fase-1.1-Estabilidad-Concurrencia.md) y [Fase 2](Fase-2-Transicion-Pantallas.md).

## Pendiente de validación

🟡 Probar en dos PCs físicas antes de dar por validada la experiencia real:

- Resoluciones iguales y diferentes, con secundaria a ambos lados.
- Cruces lentos, rápidos y muchas idas/regresos consecutivos.
- Corte de red durante control remoto, recuperación y nuevo cruce válido.
- Escape y ausencia de teclas/botones retenidos.
- Coordenadas con distintos escalados de pantalla.

Las suites locales ya pasaron; estos escenarios físicos siguen pendientes.

## Próxima fase probable: portapapeles compartido

❌ **Fase 3 propuesta: sincronización de texto entre ambas PCs.** No está implementada. El uso temporal del portapapeles local del servidor para escribir caracteres no equivale a esta función.

Después podrían abordarse imágenes de portapapeles y transferencia de archivos. Son propuestas, no capacidades actuales.

## Ideas futuras, no implementadas

- ❌ Multi-monitor.
- ❌ Seguridad, autenticación y emparejamiento.
- ❌ Cifrado del canal.
- ❌ Transferencia de archivos.
- ❌ Captura y transmisión de pantalla.
- ❌ Acceso remoto por Internet.
- ❌ Relay y señalización para conexiones fuera de la LAN.
- ❌ Cliente Android.
- ❌ Evolución hacia un sistema de control remoto completo.

Cada idea requiere una fase propia y validación adicional. Actualmente el proyecto controla teclado y mouse en una LAN; no transmite pantalla ni ofrece las funciones anteriores.
