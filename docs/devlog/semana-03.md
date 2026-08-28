# DevLog · Semana 03

## Objetivo
Implementar un API Gateway en Amazon Web Services (AWS) como capa de gestión y proxy para servicios HTTP/REST.

## Avance
- Creación de un API Manager utilizando **AWS HTTP API Gateway**.
- Configuración de la ruta `/datos` (método `GET`) e integración mediante **URI de HTTP** dirigida al servicio backend `https://mindicador.cl/api`.
- Despliegue del API en el entorno/stage `Desarrollo`.
- Pruebas de integración exitosas mediante **Postman**, consumiendo la URL de invocación de AWS y verificando la respuesta en formato JSON.

## Bloqueo
- Comprensión inicial sobre la diferencia entre la URL de invocación propia del API Gateway y la URI del backend subyacente que se está enrutando.

## Aprendizaje
- Comprender el rol crítico del API Gateway dentro de una arquitectura Cloud Native como punto único de entrada, desacoplando la firma pública de los clientes del backend real.

## Siguiente
- Aplicar capas de seguridad, reglas de autenticación (Tokens JWT / OAuth2 / API Keys) y políticas de Throttling/Rate Limiting sobre las rutas expuestas en el API Gateway.