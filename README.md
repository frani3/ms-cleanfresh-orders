# ms-cleanfresh-orders

Microservicio de órdenes de Clean&Fresh Manager. Spring Boot 4.1.1 /
Java 21. Sirve datos mock en memoria (sin base de datos todavía) y se
consume únicamente a través del BFF (`ms-cleanfresh-bff`) — no valida
JWT por su cuenta, confía en que solo el BFF le habla.

Proyecto individual para **EP1** de **DSY1107 Cloud Native 1** (DuocUC).

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/orders` | Todas las órdenes |
| GET | `/api/orders/{id}` | Una orden por id |
| GET | `/api/orders/estado/{estado}` | Órdenes filtradas por estado |
| POST | `/api/orders` | Crea una orden nueva (`{ cliente, servicio, total, sucursal }`) |

Al crear, `numeroOrden`, `id`, `fecha` y `estado` (`CREADO`) se generan
en el servidor; queda guardada en la lista en memoria junto a las 6
órdenes mock iniciales (se pierde al reiniciar — no hay DB cloud
todavía, ver `CLAUDE.md` del frontend → Pendientes).

## Requisitos

- Java 21 (`JAVA_HOME` apuntando a un JDK 21)

## Levantar en local

```powershell
.\mvnw.cmd spring-boot:run
```

O compilar y correr el jar:

```powershell
.\mvnw.cmd clean package -DskipTests
java -jar target\ms-cleanfresh-orders-0.0.1-SNAPSHOT.jar
```

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
