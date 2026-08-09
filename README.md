# TravelHub — Backend (Node.js + Express + MySQL)

API REST de la plataforma de gestión de servicios turísticos.

## Estructura

```
src/
  server.js                punto de entrada, monta rutas y middlewares
  config/db.js             pool de conexiones MySQL
  middleware/auth.js       verificarToken y exigirRol
  middleware/errores.js    asyncHandler, validación y manejo central de errores
  routes/                  definición de endpoints y validación de entrada
  controllers/             lógica y consultas SQL
  db/schema.sql            esquema de la base de datos
  db/seed.sql              datos de prueba
```

## Puesta en marcha

### 1. Base de datos

```bash
mysql -u root -p < src/db/schema.sql
mysql -u root -p < src/db/seed.sql    # opcional, datos de prueba
```

### 2. Variables de entorno

```bash
cp .env.example .env
```

Completa `.env`. El `JWT_SECRET` es obligatorio — el servidor se niega a
arrancar sin él. Genera uno con:

```bash
node -e "console.log(require('crypto').randomBytes(48).toString('hex'))"
```

### 3. Servidor

```bash
npm install
npm run dev      # con nodemon
```

Queda en `http://localhost:3000`. Comprueba que responde:

```bash
curl http://localhost:3000/api/salud
```

## Endpoints

Todos requieren `Authorization: Bearer <token>` salvo los marcados como
públicos.

### Auth

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/auth/registro` | Crea cuenta (turista o prestador). Público |
| POST | `/api/auth/login` | Devuelve token. Público |
| GET | `/api/auth/me` | Datos del usuario autenticado |
| POST | `/api/auth/dispositivos` | Registra el token de FCM del celular |

### Servicios

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/servicios` | Catálogo con filtros. Público |
| GET | `/api/servicios/categorias` | Lista de categorías. Público |
| GET | `/api/servicios/:id` | Detalle + fotos, idiomas y reseñas. Público |
| GET | `/api/servicios/:id/disponibilidad?desde=&hasta=` | Calendario. Público |
| GET | `/api/servicios/mios` | Servicios del prestador |
| POST | `/api/servicios` | Crea servicio. Solo prestador **aprobado** |
| PUT | `/api/servicios/:id/disponibilidad` | Guarda calendario (upsert masivo) |

Filtros de `GET /api/servicios`: `categoria`, `ciudad`, `precio_min`,
`precio_max`, `calificacion`, `fecha`, `buscar`, `orden`, `pagina`, `limite`.

### Reservas

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/reservas` | Crea reserva (transacción con bloqueo de cupos) |
| GET | `/api/reservas?estado=` | Historial del turista |
| GET | `/api/reservas/recibidas` | Reservas recibidas. Solo prestador |
| PATCH | `/api/reservas/:id/estado` | Confirmar / cancelar / completar |
| GET | `/api/reservas/costos/:itinerarioId` | Calculadora de costos del viaje |

### Itinerarios

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/itinerarios` | Mis viajes, con nº de paradas y costo actual |
| POST | `/api/itinerarios` | Crea el viaje y **genera los días automáticamente** |
| GET | `/api/itinerarios/:id` | Detalle con días y paradas ya agrupadas |
| PUT | `/api/itinerarios/:id` | Actualiza; si cambian las fechas reconstruye los días |
| DELETE | `/api/itinerarios/:id` | Elimina el viaje (las reservas sobreviven) |
| POST | `/api/itinerarios/:id/dias/:diaNumero/items` | Agrega una parada |
| PUT | `/api/itinerarios/:id/dias/:diaNumero/orden` | Reordena las paradas (arrastrar y soltar) |
| PATCH | `/api/itinerarios/items/:itemId` | Edita la parada / guarda caché de distancias |
| DELETE | `/api/itinerarios/items/:itemId` | Elimina la parada y renumera el resto |

### Reseñas

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/resenas/servicio/:servicioId` | Reseñas + distribución de estrellas. Público |
| POST | `/api/resenas` | Califica una reserva **completada** |
| GET | `/api/resenas/mias` | Reseñas que escribí |
| GET | `/api/resenas/pendientes` | Reservas completadas sin calificar |
| PUT | `/api/resenas/:id` | Edita mi reseña |
| DELETE | `/api/resenas/:id` | Elimina mi reseña |

### Administración

Todo `/api/admin/*` exige rol `admin`.

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/admin/metricas` | Cifras del tablero + tendencia de 30 días |
| GET | `/api/admin/prestadores?estado=pendiente` | Bandeja de solicitudes |
| PATCH | `/api/admin/prestadores/:id/verificacion` | Aprobar / rechazar (con motivo) |
| GET | `/api/admin/usuarios?rol=&activo=&buscar=` | Listado de usuarios |
| PATCH | `/api/admin/usuarios/:id/activo` | Activar / desactivar cuenta |
| GET | `/api/admin/servicios?activo=` | Catálogo completo, incluidos los ocultos |
| PATCH | `/api/admin/servicios/:id/activo` | Retirar / reponer un servicio |

### Chat (REST)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/chat/conversaciones` | Mis hilos, con último mensaje y no leídos |
| POST | `/api/chat/conversaciones` | Abre hilo con un prestador (`prestador_id` o `servicio_id`) |
| GET | `/api/chat/conversaciones/:id/mensajes?antes=` | Historial paginado por cursor |
| POST | `/api/chat/conversaciones/:id/mensajes` | Envía mensaje (respaldo si el socket no conecta) |
| PATCH | `/api/chat/conversaciones/:id/leidos` | Marca como leídos |
| GET | `/api/chat/no-leidos` | Total para la insignia del icono |

### Chat (Socket.io)

Conexión en el mismo puerto que la API. El token va en el handshake:

```kotlin
// Android
val opciones = IO.Options().apply { auth = mapOf("token" to tokenJwt) }
val socket = IO.socket("http://10.0.2.2:3000", opciones)
```

**Salas:** `usuario:<id>` (se entra al conectar, para la insignia de no leídos
aunque el chat esté cerrado) y `conversacion:<id>` (se entra al abrir un hilo).

| Dirección | Evento | Datos |
|---|---|---|
| → envía | `unirse` | `{ conversacion_id }` — valida acceso y marca leídos |
| → envía | `salir` | `{ conversacion_id }` |
| → envía | `mensaje` | `{ conversacion_id, contenido }` — callback devuelve el mensaje guardado |
| → envía | `escribiendo` | `{ conversacion_id, activo }` |
| → envía | `marcar_leidos` | `{ conversacion_id }` |
| ← recibe | `mensaje_nuevo` | el mensaje completo con su id de MySQL |
| ← recibe | `escribiendo` | `{ conversacion_id, usuario_id, activo }` |
| ← recibe | `mensajes_leidos` | `{ conversacion_id, por_usuario_id }` |
| ← recibe | `error_chat` | `{ evento, mensaje }` |

## Migraciones

`src/db/migraciones/` contiene los cambios de esquema posteriores a la
creación inicial. Solo hacen falta si ya creaste la base con una versión
anterior; `schema.sql` siempre está al día.

| Archivo | Cambio |
|---|---|
| `001_estado_verificacion.sql` | `prestadores.verificado` (BOOLEAN) → `estado_verificacion` (ENUM de 3 estados) + `motivo_rechazo` |
| `002_direccion_en_catalogo.sql` | Añade `direccion` a la vista `v_catalogo`. No destructiva: solo redefine la vista |

## Pendiente

- Chat con Socket.io sobre las tablas `conversaciones` y `mensajes`
- `src/docs/openapi.yaml` para Swagger
- Despliegue en la nube
