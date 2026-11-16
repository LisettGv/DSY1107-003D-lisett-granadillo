# DevLog: Kafka Game Day — Resiliencia y recuperación de mensajes

**Asignatura:** DSY1107 – Desarrollo Cloud Native I  
**Estudiante:** Lisett Granadillo  
**Modalidad:** Trabajo individual  

---

## 1. Registro de Ejecución y Evidencias (Checkpoints E0–E4)

### E0 – Preparación del entorno
* **Servicios desplegados:** Broker Apache Kafka (`dsy1107-kafka-s12` en estado `healthy`), Kafbat UI (`http://localhost:8083`), `compras-api` (`http://localhost:8081`), `procesador-api` (Consumer Group: `procesador-s13`), `resumen-api` (`http://localhost:8082`) y frontend `web` (`http://localhost:5173`).
* **Topics identificados en Kafbat UI:**
  * `compras` (Particiones: `3`, Replicación: `1`)
  * `compras-validas` (Particiones: `3`, Replicación: `1`)
  * `compras-rechazadas` (Particiones: `3`, Replicación: `1`)
  * `compras-dlt` (Particiones: `3`, Replicación: `1`)
* **Evidencias:** `evidencias/E0-1-entorno-docker.png` (Figura 1) y `evidencias/E0-2-topics-kafbat-ui.png` (Figura 2).

### E1 – Procesamiento normal (Línea base)
* **Prueba ejecutada:** Plan JMeter `load/carga-compras.jmx` (`5` hilos × `5` vueltas, `rampSec=10`, `delayMs=250`, prefijo `LAB-OK-`), resultados almacenados en `results/e1-normal.jtl`.
* **Métricas reales observadas:**
  * Solicitudes enviadas: `25` | Respuestas HTTP 201: `25` (`0.00%` error) | Duración: `9 s` (`2.7 req/s`, `Avg: 204 ms`, `Min: 8 ms`, `Max: 2163 ms`).
  * Distribución y offsets verificados en Kafbat UI: todos los eventos `LAB-OK-*` ingresaron a `compras` y fueron procesados hacia `compras-validas` (ej. mensaje `LAB-OK-5-25`, `idOrigen: 30dbf49a-5f57-4f4a-92c6-2aa07072e4b7`, Partición `1`, Offset `7`, `monto: 795`, `categoria: NORMAL`), dejando el *Consumer Lag* final en `0`.
* **Evidencias:** `evidencias/E1-1-jmeter-normal.png` (Figura 3) y `evidencias/E1-2-kafbat-compras-validas.png` (Figura 4).

### E2 – Fallos transitorios y reintentos
* **Escenario ejecutado:** Carga controlada mediante JMeter con prefijo `SIM-RETRY-` (`3` hilos × `2` vueltas = `6` solicitudes, `0.00%` error HTTP, `Avg: 30 ms`, almacenada en `results/e2-retry.jtl`) para activar fallos temporales simulados en `FaultSimulator` de `procesador-api`.
* **Observaciones en logs y offsets:**
  * Para el mensaje ubicado en `origen=compras:1:11` (Partición `1`, Offset `11`), el consumidor registró dos fallos transitorios consecutivos (`SIMULACION FALLO TRANSITORIO`, `intento=1/3` a las `22:14:34.302Z` e `intento=2/3` a las `22:14:35.304Z`) separados por el intervalo configurado de `1000 ms` (`FixedBackOff(1000, 2)`).
  * En el tercer intento (`intento=3/3` a las `22:14:36.212Z`), el log registró `SIMULACION RECUPERADA origen=compras:1:11 intento=3/3`, publicando exitosamente los 6 eventos en `compras-validas` (`SIM-RETRY-1-1`, `SIM-RETRY-1-2`, `SIM-RETRY-2-3`, `SIM-RETRY-2-4`, `SIM-RETRY-3-5`, `SIM-RETRY-3-6`) y avanzando el offset sin pérdida de mensajes.
* **Evidencias:** `evidencias/E2-1-jmeter-retry.png` (Figura 5), `evidencias/E2-2-logs-reintentos.png` (Figura 6) y `logs/procesador-gameday.log`.

### E3 – Fallos permanentes y Dead Letter Topic (DLT)
* **Escenario ejecutado:** Carga mediante JMeter con prefijo `SIM-DLT-` (`2` hilos × `2` vueltas = `4` solicitudes, `0.00%` error HTTP, `Avg: 25 ms`, almacenada en `results/e3-dlt.jtl`) para simular errores no recuperables.
* **Comportamiento observado:**
  * Tras ejecutar los 3 intentos configurados (`SIMULACION FALLO PERMANENT` en `FaultSimulator`) sin éxito, el manejador de errores (`DeadLetterPublishingRecoverer`) derivó los 4 mensajes al topic `compras-dlt` (`1` en Partición `1` y `3` en Partición `2`) y confirmó (*commit*) los offsets originales en `compras` para no bloquear el consumo.
  * En Kafbat UI se verificaron los headers de trazabilidad adjuntos a cada mensaje en `compras-dlt`:
    * `SIM-DLT-1-1` (`compras-dlt` Partición `2`, Offset `0`): `x-original-topic: compras`, `x-original-partition: 2`, `x-original-offset: 6`, `x-error-type: ListenerExecutionFailedException`.
    * `SIM-DLT-1-2` (`compras-dlt` Partición `2`, Offset `1`): `x-original-topic: compras`, `x-original-partition: 2`, `x-original-offset: 7`, `x-error-type: ListenerExecutionFailedException`.
    * `SIM-DLT-2-3` (`compras-dlt` Partición `2`, Offset `2`): `x-original-topic: compras`, `x-original-partition: 2`, `x-original-offset: 8`, `x-error-type: ListenerExecutionFailedException`.
    * `SIM-DLT-2-4` (`compras-dlt` Partición `1`, Offset `0`): `x-original-topic: compras`, `x-original-partition: 1`, `x-original-offset: 12`, `x-error-type: ListenerExecutionFailedException`.
* **Evidencias:** `evidencias/E3-1-jmeter-dlt.png` (Figura 7), `evidencias/E3-2-kafbat-dlt-headers.png` (Figura 8) y `logs/procesador-gameday.log`.

### E4 – Mensajes con información inválida
* **Escenario ejecutado:** Ejecución del script `.\scripts\inyectar-invalidos.ps1`, que envió dos eventos con contrato inválido (una compra con monto negativo `negativo-01` y un payload con JSON malformado) seguidos inmediatamente de una compra válida (`OK-DESPUES` / `posterior-01`).
* **Comportamiento observado:**
  * Los dos eventos inválidos fueron detectados en la validación de entrada y enrutados directamente a `compras-rechazadas` sin ejecutar reintentos innecesarios:
    * Partición `0`, Offset `0` (`Key: negativo-01`): `{"idOrigen": "negativo-01", "motivo": "MONTO_DEBE_SER_POSITIVO"}`.
    * Partición `2`, Offset `0` (`Key: null`): `{"idOrigen": null, "motivo": "JSON_INVALIDO"}`.
  * La compra válida enviada inmediatamente después (`Key: OK-DESPUES`, `idOrigen: posterior-01`, `cliente: Ana`, `monto: 250`, `categoria: NORMAL`) fue procesada con éxito hacia `compras-validas` (Partición `2`, Offset `6`, total `32` mensajes válidos acumulados), confirmando continuidad operativa sin bloqueo de particiones y con *Consumer Lag* en `0`.
* **Evidencias:** `evidencias/E4-1-kafbat-rechazadas.png` (Figura 9) y `evidencias/E4-2-continuidad-validas.png` (Figura 10).

---

## 2. Análisis Técnico

### 2.1 ¿Cuál es la diferencia entre un rechazo, un reintento y el envío a una DLT?
* **Rechazo (Validación / Descarte controlado):** Ocurre cuando un mensaje presenta errores estructurales o de reglas de negocio (como JSON malformado o monto negativo). Como volver a procesarlo jamás producirá un resultado distinto, no se reintenta: se deriva inmediatamente a un topic de rechazos de negocio (`compras-rechazadas`).
* **Reintento (Retry):** Es un mecanismo de resiliencia aplicado ante **fallos transitorios** (como *timeouts* de red, microcortes o indisponibilidad momentánea de una dependencia externa). El consumidor vuelve a intentar procesar el mismo mensaje tras un tiempo de espera (*backoff*) esperando que la dependencia se recupere.
* **Envío a Dead Letter Topic (DLT):** Es el mecanismo de aislamiento aplicado ante **fallos técnicos permanentes** o cuando un mensaje agota todos sus reintentos configurados sin éxito. En lugar de detener la partición o descartar el evento silenciosamente, el mensaje se publica en un topic dedicado (`compras-dlt`) conservando su payload original y los headers del error para auditoría o reprocesamiento posterior.

### 2.2 ¿Por qué una respuesta HTTP 201 no garantiza que el mensaje haya sido procesado?
Porque la arquitectura implementa un **desacoplamiento asíncrono basado en eventos**. El productor (`compras-api`) responde `HTTP 201 Created` en cuanto valida la recepción HTTP y el broker Kafka confirma que el mensaje quedó persistido en el log del topic `compras`. En ese instante el evento aún no ha sido consumido ni procesado por `procesador-api`, por lo que posteriormente puede experimentar latencia, fallos transitorios, rechazos por contrato inválido o derivación a la DLT.

### 2.3 ¿Qué consecuencias tendría mantener reintentos indefinidos?
Mantener reintentos infinitos frente a un fallo permanente genera el antipatrón conocido como **Poison Pill (mensaje venenoso)**:
1. **Bloqueo de partición (*Head-of-Line Blocking*):** Como Kafka garantiza el orden dentro de cada partición y avanza el offset secuencialmente, el hilo consumidor queda atrapado en el mensaje fallido, deteniendo el procesamiento de todos los eventos válidos posteriores en esa partición.
2. **Crecimiento descontrolado del *Consumer Lag*:** Se acumulan mensajes sin procesar mientras `compras-api` sigue ingresando tráfico nuevo.
3. **Agotamiento de recursos y tormenta de peticiones:** El consumidor satura su CPU/hilos y sobrecarga continuamente a los servicios dependientes aguas abajo, pudiendo desencadenar fallos en cascada.

### 2.4 ¿Qué información debería conservar un mensaje enviado a una DLT?
Para garantizar observabilidad, diagnóstico forense y capacidad de reprocesamiento seguro, un mensaje en la DLT debe conservar:
* **Payload y Key originales intactos:** El cuerpo exacto del evento y su clave de particionamiento tal como fueron producidos.
* **Coordenadas de origen en Kafka:** Nombre del topic original (`x-original-topic`), número de partición (`x-original-partition`), offset original (`x-original-offset`) y timestamp original de creación.
* **Contexto técnico del fallo:** Tipo o clase de la excepción (`x-error-type` / `exception-fqcn`), mensaje descriptivo del error, traza de pila (*stacktrace*) y momento en que ocurrió la falla.
* **Metadatos de trazabilidad:** Identificador de correlación (`idOrigen` / `correlation-id`), grupo consumidor (`procesador-s13`) y número de intentos ejecutados antes de enviarlo a la DLT.

### 2.5 ¿Qué estrategia implementarían para recuperar mensajes fallidos?
Implementaría una política de recuperación diferenciada por tipo de error en cuatro niveles:
1. **Clasificación explícita de excepciones:** Separar errores no recuperables (deserialización o reglas de negocio → envío directo a `compras-rechazadas` sin reintentos) de errores transitorios (fallos de red/I/O o servicios externos).
2. **Reintentos acotados con *Backoff* Exponencial y *Jitter*:** Configurar un límite máximo de reintentos (ej. 3 intentos con esperas crecientes y aleatoriedad) o topics de reintento diferido (*non-blocking retry topics*) para no frenar el flujo principal.
3. **Observabilidad y alertas sobre la DLT:** Monitorizar el ingreso de mensajes a `compras-dlt` mediante alertas automatizadas para detectar incidentes operativos en tiempo real.
4. **Reprocesamiento controlado e idempotente (*Replay*):** Una vez corregida la causa raíz (restaurado el servicio externo o aplicado un *hotfix*), ejecutar un proceso de *replay* que lea los eventos desde `compras-dlt`, limpie los headers de error previos y los republique en `compras`, garantizando **idempotencia** mediante el identificador único de compra (`idOrigen`) para evitar cobros o registros duplicados.

---

## 3. Conclusiones
* La resiliencia en sistemas orientados a eventos exige diferenciar entre fallos transitorios, fallos permanentes y contratos inválidos para evitar que un único evento defectuoso detenga el consumo de toda una partición.
* La combinación de **reintentos acotados con *backoff***, **validación temprana hacia `compras-rechazadas`** y **Dead Letter Topics con headers de trazabilidad (`x-original-*`)** asegura que el sistema mantenga su disponibilidad y avance de offsets sin perder ningún evento ni comprometer su auditabilidad.