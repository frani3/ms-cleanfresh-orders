# ms-cleanfresh-orders

Microservicio de órdenes de Clean&Fresh Manager. Spring Boot 4.1.1 /
Java 21. Guarda las órdenes en PostgreSQL (base `orders_db`) con Spring
Data JPA y se consume únicamente a través del BFF (`ms-cleanfresh-bff`) —
no valida JWT por su cuenta, confía en que solo el BFF le habla.

Proyecto individual de **DSY1107 Cloud Native 1** (DuocUC). Desde EP2 los
datos viven en PostgreSQL (en EP1 eran listas en memoria).

## Endpoints

Documentación OpenAPI (Swagger, Spec 046): `http://localhost:8081/swagger-ui.html` y el contrato en `/v3/api-docs`.

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/orders` | Todas las órdenes |
| GET | `/api/orders/{id}` | Una orden por id |
| GET | `/api/orders/estado/{estado}` | Órdenes filtradas por estado |
| POST | `/api/orders` | Crea una orden nueva (`{ cliente, servicio, total, sucursal }`) |
| PUT | `/api/orders/{numeroOrden}/estado` | Cambia el estado (`{ "estado": "DESPACHADO" }`). Acepta `CREADO`, `ACEPTADO`, `EN_PREPARACION`, `DESPACHADO`, `ENTREGADO`, `CANCELADO` (en cualquier mayúscula); otro valor responde `400` y una orden inexistente `404` |

Al crear, `numeroOrden` (`ORD-` + id con 4 dígitos), `id`, `fecha`, `fechaHora` (el instante exacto de creación,
ISO-8601 en UTC, Spec 036) y `estado` (`CREADO`) se generan en el servidor y la orden queda guardada en
la base: sobrevive a reinicios. Las órdenes anteriores a la Spec 036 no tienen
`fechaHora` (viene `null`; solo tienen `fecha`). Si la tabla está vacía, al arrancar se
cargan las 6 órdenes de ejemplo de EP1.

## Requisitos

- Java 21 (`JAVA_HOME` apuntando a un JDK 21)
- Una base PostgreSQL con `orders_db` y un usuario con acceso solo a ella

## Configuración (variables de entorno)

La conexión llega solo por variables de entorno, sin valores por defecto: si
faltan, el servicio no arranca.

| Variable | Ejemplo |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/orders_db` |
| `DB_USER` | `orders_user` |
| `DB_PASSWORD` | (la del usuario) |

Las tablas se crean/actualizan solas (`ddl-auto: update`).

## Comandos por RabbitMQ (opcional, Spec 033)

Si RabbitMQ está activado, al crear una orden y al despacharla el servicio publica **comandos**
en el clúster de RabbitMQ (`EP2/despliegue/mq/` del repo del frontend), dentro de un sobre común
`{ type, eventId, timestamp, traceId, correlationId, payload }`:

| Evento | Comandos (clave de ruteo) |
|---|---|
| Orden creada (`ORDEN_CREADA`) | `kitchen.ticket` y `email.send` |
| Orden pasa a `DESPACHADO` (`ORDEN_LISTA`) | `email.send` e `invoice.gen` |
| Orden pasa a `ACEPTADO`, `EN_PREPARACION`, `ENTREGADO` o `CANCELADO` (`ORDEN_ESTADO`, Spec 035) | `email.send` (aviso al Cliente; el estado va en `payload.estado`) |

Se publica solo cuando el estado **realmente cambia** (repetir el estado actual no avisa de nuevo) y volver a
`CREADO` no genera aviso. No se restringen los saltos de estado.

Las órdenes de **Servicio exprés** van por el exchange `cmd.topic` con la variante de la clave
(`email.send.high`, `kitchen.ticket.thermal`, `invoice.gen.pdf`); las demás por `cmd.direct`.
Los mensajes son persistentes, se publican con confirmación del broker (*publisher confirms*) y con
*mandatory*: si ninguna cola los recibe, se trata como error.

Se publica **después** de confirmarse la transacción; si la publicación falla, la orden o el cambio de
estado quedan guardados igual y el error se registra en el log. Por defecto está apagado y el servicio
funciona sin RabbitMQ.

| Variable | Descripción |
|---|---|
| `RABBIT_ENABLED` | `true` para publicar (por defecto `false`) |
| `RABBITMQ_ADDRESSES` | Nodos del clúster, p. ej. `localhost:5672,localhost:5673` |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | Credenciales de RabbitMQ |

Para probarlo en local, levantar el clúster con `EP2/despliegue/mq/docker-compose.yml` y
verificar el flujo completo con `EP2/despliegue/mq/verificar-flujo.sh`.

## Eventos de negocio por Kafka (opcional, Spec 044)

Además de los comandos de RabbitMQ, `orders` publica en **Kafka** lo que ya ocurrió, para que `reportes` y `auditoria` lo lean a su ritmo. Va
apagado por defecto (`KAFKA_ENABLED=false`), igual que RabbitMQ: sin clúster el servicio funciona igual.

| Tópico | Qué se publica | Clave |
|---|---|---|
| `orders.events` | Un evento por cada creación o cambio de estado: `OrderCreated`, `OrderAccepted`, `OrderPreparing`, `OrderDispatched`, `OrderDelivered`, `OrderCancelled` | Número de orden (todos los eventos de una orden van a la misma partición y se leen en orden) |
| `audit.timeline` | Un registro de auditoría por cada evento: quién lo hizo, qué, sobre qué, cuándo y desde qué IP | `orden:<número>:<eventId>` |

- Los mensajes son JSON tipado (`OrderEventMessage` y `AuditEntryMessage`), con las cabeceras `eventId`, `traceId` y `type` visibles en Kafka-UI.
- **Quién y desde dónde:** el BFF manda las cabeceras `X-Actor` (el `username` del token) y `X-Origen` (la IP del cliente). Sin ellas, el actor es el
  cliente de la orden (al crearla) o `desconocido`.
- **No bloquea ni hace fallar la orden:** el envío corre en un hilo aparte (cola acotada de 10.000). Si Kafka no responde, la orden se guarda y
  responde igual (se comprobó con los 3 brokers apagados: 201 en ~20 ms) y el fallo queda en el log con el número de orden, el tipo y el tópico.
  Los eventos que no alcanzaron a publicarse no se reintentan después de que vence el plazo del productor (30 s).
- El productor usa `acks=all` (con `min.insync.replicas=2` en los tópicos), idempotencia, compresión `lz4` y reintentos; se configura en
  `spring.kafka.producer.*` de `application.yaml`.

| Variable | Para qué | Por defecto |
|---|---|---|
| `KAFKA_ENABLED` | Enciende la publicación en Kafka | `false` |
| `KAFKA_BOOTSTRAP_SERVERS` | Los 3 brokers, separados por coma | `localhost:9092,localhost:9093,localhost:9094` |

## Levantar en local

Base de datos de prueba con Docker (credenciales de ejemplo, cámbialas):

```powershell
docker run -d --name cleanfresh-pg -e POSTGRES_PASSWORD=<admin> -p 5432:5432 postgres:16
docker exec -it cleanfresh-pg psql -U postgres -c "CREATE USER orders_user WITH PASSWORD '<clave>'" -c "CREATE DATABASE orders_db OWNER orders_user"
```

Luego, con las tres variables definidas:

```powershell
.\mvnw.cmd spring-boot:run
```

O compilar y correr el jar:

```powershell
.\mvnw.cmd clean package -DskipTests
java -jar target\ms-cleanfresh-orders-0.0.1-SNAPSHOT.jar
```

Los tests (`.\mvnw.cmd test`) usan H2 en memoria y no necesitan PostgreSQL.

Corre en `http://localhost:8081`.

## Probar

```bash
curl http://localhost:8081/api/orders

curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"cliente":"Test","servicio":"Lavado y secado","total":18000,"sucursal":"Providencia"}'
```

## Arquitectura y decisiones técnicas

Este servicio es parte de un sistema de varios repositorios. La documentación está en el repo del frontend:
[`CLAUDE.md`](https://github.com/frani3/cleanfresh-frontend/blob/main/CLAUDE.md) (contexto, AWS, roles y decisiones), [`EP2/ARQUITECTURA.md`](https://github.com/frani3/cleanfresh-frontend/blob/main/EP2/ARQUITECTURA.md) y el
[índice de la entrega 2](https://github.com/frani3/cleanfresh-frontend/blob/main/EP2/README.md). El [`CLAUDE.md`](CLAUDE.md) de este repo resume lo propio del servicio.

## Docker

`Dockerfile` multi-etapa (Maven + JDK 21 para compilar, JRE 21 sin root para correr). Se configura solo por variables de entorno.

```bash
docker build -t cleanfresh/orders .
docker run -p 8081:8081 -e DB_URL=... -e DB_USER=... -e DB_PASSWORD=... cleanfresh/orders
```

Los 5 microservicios se levantan juntos con el `docker-compose.yml` de `EP2/despliegue/` en el repo `cleanfresh-frontend`.
