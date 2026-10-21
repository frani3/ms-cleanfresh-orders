# ms-cleanfresh-orders

Microservicio de órdenes de Clean&Fresh Manager. Spring Boot 4.1.1 /
Java 21. Guarda las órdenes en PostgreSQL (base `orders_db`) con Spring
Data JPA y se consume únicamente a través del BFF (`ms-cleanfresh-bff`) —
no valida JWT por su cuenta, confía en que solo el BFF le habla.

Proyecto individual de **DSY1107 Cloud Native 1** (DuocUC). Desde EP2 los
datos viven en PostgreSQL (en EP1 eran listas en memoria).

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/orders` | Todas las órdenes |
| GET | `/api/orders/{id}` | Una orden por id |
| GET | `/api/orders/estado/{estado}` | Órdenes filtradas por estado |
| POST | `/api/orders` | Crea una orden nueva (`{ cliente, servicio, total, sucursal }`) |
| PUT | `/api/orders/{numeroOrden}/estado` | Cambia el estado (`{ "estado": "DESPACHADO" }`). Acepta `CREADO`, `ACEPTADO`, `EN_PREPARACION`, `DESPACHADO`, `ENTREGADO`, `CANCELADO` (en cualquier mayúscula); otro valor responde `400` y una orden inexistente `404` |

Al crear, `numeroOrden` (`ORD-` + id con 4 dígitos), `id`, `fecha` y
`estado` (`CREADO`) se generan en el servidor y la orden queda guardada en
la base: sobrevive a reinicios. Si la tabla está vacía, al arrancar se
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

Ver [`CLAUDE.md`](CLAUDE.md) para el detalle completo del sistema (los
4 repos, cómo se conecta con el BFF, y la pauta de evaluación de EP1).

## Docker

`Dockerfile` multi-etapa (Maven + JDK 21 para compilar, JRE 21 sin root para correr). Se configura solo por variables de entorno.

```bash
docker build -t cleanfresh/orders .
docker run -p 8081:8081 -e DB_URL=... -e DB_USER=... -e DB_PASSWORD=... cleanfresh/orders
```

Los 5 microservicios se levantan juntos con el `docker-compose.yml` de `EP2/despliegue/` en el repo `cleanfresh-frontend`.
