# Actividad 2 (28-08-2026): Laboratorio ReservApp · Identidad, Autorización e IDaaS

## Objetivo
Comprender el flujo de autenticación y autorización OAuth2 / OIDC utilizando Authorization Code + PKCE, diferenciando el uso de Access Tokens e ID Tokens en una arquitectura de microservicios protegida por API Gateway.

## Avance
- Despliegue y ejecución local de la arquitectura distribuida completa: IdP simulado (`mock-identity`), API Backend (`reservapp-api`), API Gateway (`gateway`) y Cliente Web (`client`).
- Ejecución del flujo **Authorization Code con PKCE** para la generación de tokens.
- Consumo exitoso de endpoints protegidos (`GET /api/reservations`) recibiendo respuesta `HTTP 200 OK`.
- Verificación del control de acceso granular mediante scopes:
  - Intento de eliminación sin permiso de escritura resultando en `HTTP 403 Forbidden`.
  - Re-emisión de token con scope `reservations.write` y prueba de cancelación exitosa de reserva propia (`R-101`) recibiendo `HTTP 200 OK`.
  - Verificación de propiedad del recurso (*Resource Ownership*) al intentar eliminar reservas de otros usuarios (`R-202`), resultando en `HTTP 403 Forbidden`.
- Pruebas de seguridad enviando un **ID Token como Access Token**, validando el rechazo por parte del gateway con error `HTTP 401 Unauthorized`.

## Bloqueo
- Comprensión práctica sobre por qué un ID Token no debe utilizarse para autorizar peticiones a APIs protegidas, ya que su propósito es validar identidad en el cliente y no transportar scopes ni audiencias de recursos.

## Aprendizaje
- Claridad técnica sobre la diferencia funcional entre **Access Tokens** (autorización delegada para APIs) e **ID Tokens** (autenticación e identidad del usuario).
- Importancia de PKCE para proteger el intercambio del código de autorización en aplicaciones cliente.
- Mecanismos de validación de scopes y propiedad de recursos aplicados secuencialmente entre el API Gateway y el microservicio backend.

## Próximos Pasos
- Integrar un proveedor de identidad real (IDaaS) basado en la nube (como Azure AD B2C o Keycloak) reemplazando el IdP simulado local.