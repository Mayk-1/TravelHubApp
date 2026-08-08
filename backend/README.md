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

## Ejemplo rápido

```bash
# login con un usuario del seed (contraseña: travelhub2026)
curl -X POST http://localhost:3000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"camila@example.com","password":"travelhub2026"}'

# catálogo de guías en Puno por debajo de 100 soles
curl "http://localhost:3000/api/servicios?categoria=guia&ciudad=Puno&precio_max=100"

# reservar (usa el token del login)
curl -X POST http://localhost:3000/api/reservas \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer TU_TOKEN" \
  -d '{"servicio_id":1,"fecha_inicio":"2026-08-11","num_personas":2}'
```

## Notas de implementación

- **Las contraseñas nunca se guardan en texto plano**: se hashean con `bcrypt`
  antes de insertar, y el login responde el mismo mensaje tanto si el correo
  no existe como si la contraseña está mal, para no revelar qué direcciones
  están registradas.
- **El pool de MySQL se reutiliza** entre peticiones. Nunca abras una conexión
  nueva por endpoint.
- **`SELECT ... FOR UPDATE` al reservar.** Es lo que evita la sobreventa: si
  dos turistas piden la última plaza a la vez, el segundo espera al primero
  y se encuentra sin cupo, en vez de que ambos reserven.
- **El precio se congela en la reserva.** `precio_unitario` es una copia, no
  un `JOIN` a `servicios.precio`. Si el prestador sube la tarifa mañana, lo
  ya reservado conserva lo pactado.
- **Las transiciones de estado son explícitas** (objeto `TRANSICIONES`): no se
  puede pasar de "cancelada" a "confirmada".
- **Todas las consultas usan placeholders `?`**, incluidos los filtros
  dinámicos del catálogo. El ordenamiento no se concatena del input del
  usuario: se elige de una lista blanca.
- **El rol viaja dentro del JWT**, así `exigirRol` no consulta la base de
  datos en cada petición.
- **La propiedad se comprueba siempre por consulta, no por parámetro.** Un
  itinerario o una reseña ajena devuelven 404, no 403: así no se le confirma
  a un extraño que ese recurso existe.
- **El `servicio_id` de una reseña se deriva de la reserva**, nunca del body.
  Si se confiara en el body, alguien podría usar su reserva del servicio A
  para calificar el servicio B.
- **Cambiar las fechas de un itinerario avisa antes de destruir**: si el nuevo
  rango dejaría paradas fuera, responde 409 y exige `"forzar": true`.
- **`exigirRol('admin')` se aplica una vez con `router.use`** en
  `admin.routes.js`, no ruta por ruta. Así ninguna ruta del panel puede
  quedar expuesta por olvido al agregarla después.
- **Desactivar en vez de borrar.** Conserva el historial de reservas y las
  reseñas. El login ya rechaza cuentas inactivas.
- **Salvaguardas del admin**: no puede desactivarse a sí mismo ni dejar el
  sistema sin ningún administrador activo.
- **Rechazar a un prestador desactiva sus servicios** en la misma
  transacción; si no, seguirían apareciendo en el catálogo.
- **El chat guarda en MySQL antes de reenviar.** El socket solo transporta;
  la fuente de verdad es la base de datos. Al revés, un mensaje enviado con
  el receptor desconectado se perdería.
- **El JWT del socket se valida una sola vez, en el handshake.** Después
  nunca se confía en un `usuario_id` que venga en el payload de un evento:
  siempre se usa `socket.usuario`, que salió del token verificado.
- **La autorización del chat vive en `services/chat.service.js`**, compartida
  por REST y por socket. Duplicarla sería dejar un agujero en cuanto se
  arreglara un camino y se olvidara el otro.
- **Cuidado con `conversaciones.prestador_id`**: apunta a `prestadores.id`,
  no a `usuarios.id`. Compararlo directamente contra el id del usuario
  autenticado deja entrar a la persona equivocada cuando los ids coinciden
  por azar. Hay una prueba automática para justo ese caso.

## Pruebas

```bash
npm test
```

Ejercita `accesoAConversacion` con un doble de la base de datos: verifica que
el turista y el prestador entran, que un tercero queda fuera, y que un usuario
cuyo `id` coincide por casualidad con el `prestador_id` de la conversación
**no** entra.

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
