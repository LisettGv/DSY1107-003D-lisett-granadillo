# Laboratorio 01: Creación y Despliegue de un API Manager con AWS API Gateway

## Objetivo
Configurar e implementar un API Gateway como puerta de entrada segura para exponer endpoints de servicios HTTP/REST.

## Descripción de la Actividad
En este laboratorio práctico se utilizó la consola de **AWS Academy** para configurar un servicio de **Amazon API Gateway** (HTTP API) como proxy/gateway intermediario sobre un servicio externo (`mindicador.cl`).

---

## Paso a Paso Implementado

1. **Acceso e Inicialización**:
   - Ingreso a la consola de AWS a través del portal de AWS Academy.
   - Navegación hacia el servicio **API Gateway**.
2. **Creación del API Manager**:
   - Selección del tipo de API: **HTTP API**.
   - Nombre asignado al API: `Mi_Api_GateWay`.
3. **Configuración de Rutas y Métodos**:
   - Creación de la ruta raíz `/datos` con método `GET`.
4. **Asociación de Integración**:
   - Tipo de integración: **URI de HTTP**.
   - Endpoint de destino (backend a proteger/exponer): `https://mindicador.cl/api` con método `GET`.
5. **Creación de Stage y Despliegue**:
   - Creación del Stage de despliegue denominado `Desarrollo`.
   - Ejecución del despliegue (`Deploy`) para la obtención de la URL de Invocación base (ejemplo: `https://<id-api>.execute-api.us-east-1.amazonaws.com`).
6. **Pruebas y Verificación**:
   - Validación mediante **Postman** haciendo una petición `GET` a la ruta final: `https://<id-api>.execute-api.us-east-1.amazonaws.com/datos`.
   - Verificación del recibo correcto del payload JSON proveniente de `mindicador.cl`.