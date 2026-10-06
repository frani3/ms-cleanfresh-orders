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

## Eventos en SQS (opcional)

Al crear una orden, si SQS está activado, el servicio publica un mensaje
`ORDEN_CREADA` en la cola con `numeroOrden`, `cliente`, `servicio`, `sucursal`,
`total` y `fecha`. Se publica **después** de confirmarse la transacción; si la
publicación falla, la orden queda creada igual y el error se registra en el
log. Por defecto está apagado y el servicio funciona sin AWS.

| Variable | Descripción |
|---|---|
| `SQS_ENABLED` | `true` para publicar (por defecto `false`) |
| `SQS_QUEUE_URL` | URL de la cola, p. ej. `.../cleanfresh-ordenes` |
| `AWS_REGION` | Región (por defecto `us-east-1`) |
| `SQS_ENDPOINT` | Solo para emuladores locales; vacío en AWS |

Las credenciales salen de la cadena por defecto del AWS SDK (variables
`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`, perfil de la EC2, etc.).

Para probarlo en local sin AWS, con ElasticMQ (emulador de SQS):

```powershell
docker run -d --name cleanfresh-sqs -p 9324:9324 softwaremill/elasticmq-native
curl "http://localhost:9324/?Action=CreateQueue&QueueName=cleanfresh-ordenes"
$env:SQS_ENABLED="true"; $env:SQS_ENDPOINT="http://localhost:9324"
$env:SQS_QUEUE_URL="http://localhost:9324/000000000000/cleanfresh-ordenes"
$env:AWS_ACCESS_KEY_ID="local"; $env:AWS_SECRET_ACCESS_KEY="local"
```

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
