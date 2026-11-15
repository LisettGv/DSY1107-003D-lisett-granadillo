# DEVLOG — Operación Avalancha: Diagnóstico de capacidad y rendimiento en Kafka
**Asignatura:** DSY1107 – Desarrollo Cloud Native I  
**Estudiante:** Lisett Granadillo  

---

## 1. Checkpoint A — Preparación y Arquitectura
- **Componentes levantados (`docker compose`):** Broker Apache Kafka 4.1.2 (`dsy1107-kafka-s12`), `kafbat UI` (puerto `8083`), productor HTTP `compras-api` (puerto `8081`), consumidor `procesador-api` (grupo `procesador-s13`), `resumen-api` (puerto `8082`) y frontend web (`5173`).
- **Topología de Topics:** `compras`, `compras-validas` y `compras-rechazadas`, inicializados con **3 particiones** y factor de replicación 1 cada uno.
- **Evidencias:** `evidencias/A1_entorno_levantado.png`, `evidencias/A2_kafka_ui_inicial.png`.

---

## 2. Checkpoint B — Predicción (Hipótesis Inicial)
- **Hipótesis:** El servicio `procesador-api` (grupo `procesador-s13`) será el primer componente en saturarse. Aunque el topic `compras` dispone de **3 particiones**, el consumidor está configurado con `SPRING_KAFKA_LISTENER_CONCURRENCY=1` y una latencia bloqueante simulada de `APP_INCIDENT_DEPENDENCY_LATENCY_MS=450` ms por mensaje. Esto fija un techo teórico de procesamiento de 1000 ms / 450 ms = ~2,22 msg/s, dejando 2 particiones sin paralelismo simultáneo.
- **Comportamiento esperado durante la oleada (`oleada-300s.jmx`):** Durante las fases iniciales (0,5 a 1 req/s) no habrá acumulación; sin embargo, en la fase `P4 · AVALANCHA` (segundos 115–155, hasta 12 req/s), `compras-api` seguirá respondiendo `HTTP 201` rápido por desacoplamiento asíncrono, mientras el **Consumer Lag** crecerá por encima de 350 mensajes.
- **Indicadores para distinguir ingesta vs. procesamiento:**
  1. *Ingesta (publicación):* Respuestas `HTTP 201` en JMeter y avance del `End Offset` en el topic `compras`.
  2. *Procesamiento completado:* Avance del `Current Offset` del grupo `procesador-s13` (`Consumer Lag -> 0`) y aparición de eventos en `compras-validas` / `compras-rechazadas`.

---

## 3. Checkpoint C — Línea Base (Corrida ANTES)
- **Ejecución:** Se ejecutó el plan `oleada-300s.jmx` (`-RunId antes`) junto con el monitoreo de lag cada 10 s (`35` snapshots).
- **Resultados observados:**
  - `compras-api` mantuvo **0 errores HTTP** y latencia media de **6,1–7,4 ms** en el pico de **12,07 req/s** (tramos 120–150 s).
  - En Kafka UI, `procesador-s13` mostró `Members: 1` atendiendo las 3 particiones y acumuló un **Consumer Lag pico de 377 mensajes** (124 en P0, 129 en P1 y 124 en P2).
- **Evidencias:** `evidencias/C1_pico_lag_antes.png`, `evidencias/C3_resumen_jmeter_antes.png`, `results/oleada-antes.jtl`, `results/resumen-antes.csv`, `results/lag-antes.txt`.

---

## 4. Checkpoint D — Diagnóstico Forense
1. **¿Qué ocurrió primero?** El crecimiento acelerado del **Consumer Lag** en `procesador-s13` sobre `compras` (377 mensajes en pico). No ocurrieron errores HTTP ni caída del broker Kafka.
2. **¿Cuándo empezó a crecer la brecha?** Entre los segundos **105 y 120** (inicio de la avalancha, cuando la tasa saltó a 5,20 req/s y luego a 12,07 req/s, superando los ~2,22 msg/s del consumidor).
3. **¿Siguió respondiendo el endpoint HTTP?** Sí (`HTTP 201` en 6,1–7,4 ms), porque `compras-api` publica asíncronamente en Kafka sin esperar el procesamiento downstream.
4. **¿Se perdieron mensajes?** No; los mensajes quedaron retenidos en cola (`backlog`) y se procesaron con retraso conforme bajó el tráfico.
5. **Causa raíz:** Desajuste entre el particionamiento del topic (3 particiones) y la concurrencia unitaria del consumidor (`LISTENER_CONCURRENCY=1`), agravado por los `450 ms` de latencia síncrona por evento (`DEPENDENCY_LATENCY_MS=450`).
6. **¿Bastaría aumentar RAM o agregar `retry`?** No. El problema es de paralelismo y latencia por evento, no de falta de memoria; agregar `retry` reencolaría mensajes y empeoraría el retraso.

---

## 5. Checkpoint E — Optimización y Comparación (Corrida DESPUÉS)
- **Modificación realizada:** En `.env.incident` (y valores por defecto en `compose.incident.yaml`) se ajustaron:
  - `LISTENER_CONCURRENCY=3` (igualando las 3 particiones de `compras`).
  - `DEPENDENCY_LATENCY_MS=100` (reduciendo el tiempo bloqueante por evento de 450 ms a 100 ms, elevando la capacidad teórica a (1000 ms / 100 ms) × 3 = 30,00 msg/s).
- **Resultados tras reiniciar el stack bajo exactamente el mismo perfil de carga (`12,07 req/s` pico):**
  - `procesador-s13` escaló a **`Members: 3`** (1 consumidor dedicado por partición).
  - El **Consumer Lag** cayó de **377 mensajes** a un máximo puntual de **1 mensaje** durante la avalancha y **0 mensajes** al cierre (918 ingresados en `compras` y 918 procesados en `compras-validas`).
- **Evidencias:** `evidencias/E1_cambio_env_incident.png`, `evidencias/E2_pico_lag_despues.png`, `evidencias/E3_topics_despues.png`, `evidencias/E4_cierre_kafka_ui_despues.png`, `evidencias/E5_resumen_jmeter_despues.png`, `results/oleada-despues.jtl`, `results/resumen-despues.csv`, `results/lag-despues.txt`.

---

## 6. Tabla Comparativa Oficial (ANTES vs. DESPUÉS)

| Métrica / Parámetro | Corrida ANTES (Línea Base) | Corrida DESPUÉS (Optimizada) | Variación / Impacto |
| :--- | :---: | :---: | :--- |
| **Hilos consumidores (`LISTENER_CONCURRENCY`)** | 1 hilo (para 3 particiones) | **3 hilos** (1 por partición) | +200 % paralelismo activo |
| **Latencia de dependencia (`DEPENDENCY_LATENCY_MS`)** | 450 ms | **100 ms** | −77,8 % tiempo por evento |
| **Capacidad teórica de procesamiento** | ~2,22 msg/s | **30,00 msg/s** | **13,5×** capacidad de consumo |
| **Carga pico configurada / observada (120–150 s)** | 12,07 req/s (181 req/tramo) | **12,07 req/s (181 req/tramo)** | Perfil de carga idéntico (válido) |
| **Errores HTTP / Aserciones (JMeter)** | 0 % (0 errores) | **0 % (0 errores)** | Sin degradación en ingesta |
| **Latencia media HTTP en pico (135–150 s)** | 6,1 ms | **7,3 ms** | Desacoplamiento HTTP estable |
| **Consumer Lag máximo en pico (`procesador-s13`)** | **377 mensajes** | **1 mensaje (0 al cierre)** | **−99,7 %** reducción de lag |
| **Mensajes ingresados vs. procesados al cierre** | 927 publicados / 905 procesados | **918 publicados / 918 procesados** | **100 % drenado en tiempo real** |

---

## 7. Análisis Técnico Final (Preguntas de Cierre)
1. **¿Qué componente presentó el principal cuello de botella?**  
   El servicio consumidor `procesador-api` (grupo `procesador-s13`), limitado por su única hebra consumidora (`concurrency=1`) y su latencia de procesamiento de 450 ms por evento.
2. **¿Qué relación observaron entre el volumen de mensajes publicados y el consumer lag?**  
   Mientras la tasa de publicación se mantuvo por debajo de la capacidad de consumo (~2,22 msg/s en ANTES; 30 msg/s en DESPUÉS), el lag permaneció en 0. En cuanto la tasa de publicación superó la capacidad de consumo (12,07 req/s en ANTES), el lag creció linealmente con la diferencia entre la tasa de entrada y la tasa de consumo.
3. **¿Por qué una respuesta HTTP exitosa no garantiza que el procesamiento Kafka haya terminado?**  
   Porque `compras-api` implementa mensajería asíncrona: responde `HTTP 201` en cuanto el broker Kafka confirma el `append` al log del topic `compras`, desacoplando la recepción de la petición del procesamiento posterior realizado por `procesador-api`.
4. **¿Qué modificación realizaron para mejorar el comportamiento del sistema?**  
   Se incrementó `LISTENER_CONCURRENCY` de `1` a `3` para aprovechar el paralelismo completo de las 3 particiones del topic `compras`, y se redujo `DEPENDENCY_LATENCY_MS` de `450` a `100` ms para optimizar el tiempo de atención de cada evento.
5. **¿Qué diferencias observaron entre las ejecuciones ANTES y DESPUÉS?**  
   Manteniendo idéntica carga (`12,07 req/s` pico y 0 % de error HTTP), el consumer lag máximo bajó de **377 mensajes a 1 mensaje** (−99,7 %), el grupo consumidor pasó de 1 a 3 miembros activos y el 100 % de los 918 eventos publicados quedó drenado en tiempo real sin acumulación residual.
6. **¿Qué costos, limitaciones o riesgos introduce la mejora implementada?**  
   - *Mayor consumo de CPU y memoria* en `procesador-api` al sostener 3 hilos concurrentes.
   - *Mayor presión sobre la dependencia externa* (base de datos o servicio downstream), que ahora recibe hasta 3× más consultas simultáneas.
   - *Techo rígido por particiones:* Subir `LISTENER_CONCURRENCY` por encima de 3 no generaría ganancia alguna mientras el topic `compras` tenga solo 3 particiones, ya que Kafka asigna como máximo 1 consumidor activo por partición dentro de un mismo *consumer group*.
7. **Si el volumen de solicitudes aumentara nuevamente, ¿qué componente podría convertirse en el siguiente cuello de botella?**  
   - **A corto plazo (superando ~30 req/s):** Nuevamente `procesador-api` y la dependencia externa subyacente, lo que obligaría a aumentar el número de particiones del topic `compras` (por ejemplo, a 6 o 12) para poder escalar horizontalmente más instancias/hilos consumidores.
   - **A mayor escala:** El disco/I/O del broker Kafka (`dsy1107-kafka-s12` con `replication-factor=1`) o los hilos HTTP/conexiones de red de `compras-api`.
