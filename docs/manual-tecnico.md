# Manual técnico — TravelHub

Cómo está construido el sistema y por qué. Pensado para entenderlo de punta
a punta y poder defender cada decisión.

---

## 1. Panorama general

TravelHub son **tres piezas** que hablan entre sí:

```
┌────────────────────┐      HTTP / JSON      ┌──────────────────┐      SQL      ┌─────────┐
│  App Android       │ ────────────────────► │  API REST        │ ────────────► │  MySQL  │
│  Kotlin + Compose  │ ◄──────────────────── │  Node + Express  │ ◄──────────── │         │
│                    │                        │                  │               └─────────┘
│                    │ ◄───WebSocket────────► │  Socket.io       │
└────────────────────┘      (chat)            └──────────────────┘
```

Ninguna pieza sabe cómo está hecha la otra. La app no sabe que hay MySQL
detrás; el backend no sabe que el cliente es Android. Lo único que comparten
es el **contrato**: qué rutas existen, qué reciben y qué devuelven.

Eso es lo que permite que dos personas del equipo trabajen en paralelo: una en
Kotlin y otra en Node, poniéndose de acuerdo solo en el JSON.

### Por qué esta separación importa

Si la app se conectara directamente a MySQL —que técnicamente se puede— habría
que meter la contraseña de la base de datos dentro del APK. Cualquiera que
descargue la app puede descompilarla y leerla. Con un backend en medio, el
celular solo conoce una URL pública y su propio token; las credenciales de la
base nunca salen del servidor.

---

## 2. El modelo de datos

Antes de escribir una línea de código hay que decidir **qué cosas existen** y
cómo se relacionan. Todo lo demás cuelga de ahí: si el modelo está mal, cada
consulta posterior sufre.

### 2.1. El problema de los cinco tipos de servicio

El enunciado pide guías, hospedajes, restaurantes, transportistas y
traductores. Todos comparten cosas (precio, ubicación, quién lo ofrece,
calificación) pero cada uno tiene lo suyo: un hotel tiene habitaciones y hora
de check-in; un guía tiene años de experiencia e idiomas; una van tiene placa
y número de asientos.

Hay tres formas de resolverlo:

| Opción | Cómo | Problema |
|---|---|---|
| Una tabla por tipo | `hoteles`, `guias`, `vans`… | El catálogo unificado se vuelve un `UNION` de cinco consultas. Filtrar por precio es un dolor. |
| Una tabla con columnas de todo | `servicios` con `habitaciones`, `placa`, `idiomas`… | Un guía tendría `placa = NULL`, un hotel `anios_experiencia = NULL`. Tabla llena de huecos. |
| Una tabla con un `JSON` | `servicios.atributos = {...}` | Rápido de escribir, pero no está normalizado y no se puede indexar ni validar. |

TravelHub usa una cuarta: **Class Table Inheritance**. Una tabla base con lo
común, y una tabla satélite por tipo con lo específico:

```
servicios  (id, prestador_id, categoria_id, titulo, precio, lat, lng, ...)
   │
   ├── servicios_guia        (servicio_id PK/FK, anios_experiencia, duracion_horas, ...)
   ├── servicios_hospedaje   (servicio_id PK/FK, habitaciones, wifi, hora_check_in, ...)
   ├── servicios_transporte  (servicio_id PK/FK, placa, asientos, ...)
   ├── servicios_alimentacion(servicio_id PK/FK, tipo_cocina, ...)
   └── servicios_traduccion  (servicio_id PK/FK, modalidad, ...)
```

El truco está en que en cada satélite **la clave primaria es también la clave
foránea**. Eso obliga al motor a garantizar la relación 1:1: no puede haber
dos filas de `servicios_guia` para el mismo servicio, ni una fila huérfana.

Ventajas concretas:

- El catálogo se consulta sobre **una sola tabla**, con filtros unificados.
- Agregar un tipo nuevo es crear una tabla; no se toca nada existente.
- No hay columnas nulas ni datos sin validar.

El MVP solo implementa guías y hospedajes, pero las otras tres tablas están
creadas. Eso es lo que el enunciado llama *"modelo preparado para escalar"*.

### 2.2. Datos de catálogo vs datos transaccionales

Esta distinción explica varias decisiones del modelo.

`servicios.precio` es un dato **de catálogo**: cambia cuando el prestador
quiere. `reservas.precio_unitario` es un dato **transaccional**: es lo que se
pactó ese día y no puede cambiar nunca.

Por eso la reserva **copia** el precio en vez de referenciarlo:

```sql
-- MAL: el subtotal cambiaría solo si el prestador sube su tarifa
SELECT r.num_personas * s.precio FROM reservas r JOIN servicios s ...

-- BIEN: la reserva guarda lo que se acordó
precio_unitario  DECIMAL(10,2) NOT NULL,
cantidad         DECIMAL(6,2)  NOT NULL,
subtotal         DECIMAL(10,2) NOT NULL,
```

La misma lógica aplica a `unidad_precio`. No es lo mismo 90 soles *por noche*
que *por persona*: la unidad determina cómo se multiplica.

### 2.3. Desnormalización deliberada

`servicios.calificacion_promedio` y `total_resenas` son datos **duplicados**:
se podrían calcular con un `AVG()` sobre la tabla de reseñas. Están ahí a
propósito, porque el catálogo se lista muchísimo más de lo que se escriben
reseñas, y hacer un `AVG()` por cada fila de cada listado es caro.

El riesgo de duplicar un dato es que se desincronice. Aquí lo evitan dos
**triggers** en la base de datos:

```sql
CREATE TRIGGER trg_resena_insert AFTER INSERT ON resenas ...
CREATE TRIGGER trg_resena_delete AFTER DELETE ON resenas ...
```

Están en la base y no en el código de Node a propósito: si mañana alguien
inserta una reseña desde phpMyAdmin, el promedio se actualiza igual.

> **Asimetría a tener presente:** los triggers cubren `INSERT` y `DELETE`,
> no `UPDATE`. Al **editar** una reseña, el promedio se recalcula a mano en
> `resenas.controller.js`. Es una decisión consciente, no un olvido.

### 2.4. Estados con ENUM, no con booleanos

`prestadores.estado_verificacion` es `ENUM('pendiente','aprobado','rechazado')`.
Al principio era un booleano `verificado`, y hubo que migrarlo: con un
booleano, "nunca revisado" y "revisado y rechazado" son ambos `false`, y el
administrador no puede saber qué solicitudes le quedan por atender.

La lección general: **si un estado tiene más de dos situaciones posibles en el
futuro, un booleano se te va a quedar corto.**

### 2.5. Política de borrado

Cada clave foránea declara qué pasa si se borra el registro padre. No es un
detalle menor: define si el sistema pierde información.

| Relación | Regla | Razón |
|---|---|---|
| `usuarios` → `prestadores` | `CASCADE` | El perfil no tiene sentido sin la cuenta |
| `servicios` → `reservas` | `RESTRICT` | **Prohíbe** borrar un servicio con reservas. Hay que desactivarlo (`activo = FALSE`) |
| `reservas` → `itinerario_items` | `SET NULL` | El punto sigue en el mapa aunque se cancele la reserva |

`RESTRICT` en reservas es la más importante: protege el historial del turista.
Por eso el panel de admin **desactiva** en vez de borrar.

---

## 3. Anatomía del backend

```
backend/src/
  server.js              arranca todo y monta las rutas
  config/db.js           pool de conexiones a MySQL
  middleware/
    auth.js              verificarToken, exigirRol
    errores.js           asyncHandler, validar, manejadorErrores
  routes/                QUÉ rutas existen y qué validan
  controllers/           QUÉ hace cada ruta (la lógica y el SQL)
  services/              lógica compartida entre REST y sockets
  sockets/chat.socket.js chat en tiempo real
  db/
    schema.sql           estructura de la base
    seed.sql             datos de prueba
    migraciones/         cambios posteriores al esquema
```

### La separación rutas / controllers

Es la que más confunde al principio. La regla es:

- **Las rutas** dicen *qué* URL existe, *quién* puede entrar y *qué forma*
  deben tener los datos. No tienen lógica.
- **Los controllers** hacen el trabajo: consultan, calculan, responden.

```js
// routes/reservas.routes.js — el "qué" y el "quién"
router.post('/',
  [ body('servicio_id').isInt({ min: 1 }),
    body('fecha_inicio').isDate() ],
  validar,
  asyncHandler(controlador.crear)
);
```

```js
// controllers/reservas.controller.js — el "cómo"
async function crear(req, res) { ... }
```

La ventaja: mirando solo el archivo de rutas ves de un vistazo toda la
superficie de la API y sus permisos. No tienes que leer 400 líneas de lógica
para saber si un endpoint está protegido.

### El pool de conexiones

Abrir una conexión a MySQL es caro (handshake TCP, autenticación). Si cada
petición abriera la suya, con 50 usuarios simultáneos el servidor MySQL
rechazaría conexiones.

El **pool** mantiene 10 conexiones abiertas y las presta:

```js
const pool = mysql.createPool({ ..., connectionLimit: 10 });
```

- `pool.query(...)` → toma una prestada, la usa, la devuelve. Para consultas
  sueltas.
- `pool.getConnection()` → reserva una para ti. **Obligatorio** para
  transacciones, porque todas las sentencias tienen que ir por la *misma*
  conexión.

Y de ahí el `finally` que se repite en todos los controllers:

```js
} finally {
  conexion.release();   // si esto falta, el pool se agota y el server se cuelga
}
```

---

## 4. El ciclo de vida de una petición

Seguimos un login de principio a fin.

```mermaid
sequenceDiagram
    participant A as App Android
    participant E as Express
    participant V as express-validator
    participant C as auth.controller
    participant M as MySQL

    A->>E: POST /api/auth/login {email, password}
    E->>E: cors() y express.json()
    E->>V: valida formato del email y contraseña
    alt formato inválido
        V-->>A: 400 {error: "El correo no es valido"}
    end
    V->>C: asyncHandler(login)
    C->>M: SELECT ... FROM usuarios WHERE email = ?
    M-->>C: fila con password_hash
    C->>C: bcrypt.compare(password, hash)
    alt no coincide
        C-->>A: 401 {error: "Correo o contrasena incorrectos"}
    end
    C->>C: jwt.sign({usuarioId, rol})
    C-->>A: 200 {token, usuario}
    A->>A: TokenStore guarda el token en DataStore
```

**Paso a paso:**

1. **Middlewares globales.** `cors()` decide si el origen puede llamar;
   `express.json()` convierte el cuerpo de la petición en un objeto JS.

2. **Validación.** `express-validator` comprueba el *formato* antes de tocar
   la base. Si el email no tiene `@`, no tiene sentido consultar MySQL. Se
   responde 400 y se corta.

3. **`asyncHandler`.** Envuelve el controller. Sin él, si una consulta falla
   dentro de una función `async`, Express no se entera y la petición se queda
   colgada hasta que expira. Con él, el error llega al manejador central.

4. **El controller.** Consulta, compara la contraseña con `bcrypt`, firma el
   token.

5. **La respuesta.** JSON. Si algo falló, `manejadorErrores` traduce el error
   a un código HTTP con sentido.

### Por qué las contraseñas se comparan con `bcrypt.compare`

En la base nunca hay contraseñas: hay **hashes**. Un hash es un cálculo de una
sola dirección — de la contraseña sacas el hash, del hash no sacas la
contraseña.

```js
// al registrar
const passwordHash = await bcrypt.hash(password, 10);

// al entrar
const valida = await bcrypt.compare(password, usuario.password_hash);
```

El `10` son las *rondas*: bcrypt repite el cálculo 2¹⁰ veces para que probar
contraseñas por fuerza bruta sea lento. Si la base de datos se filtra, las
contraseñas siguen protegidas.

Detalle de diseño: el login responde **el mismo mensaje** si el correo no
existe que si la contraseña está mal. Si dijera "ese correo no está
registrado", un atacante podría averiguar qué direcciones tienen cuenta.

### El JWT

Un JSON Web Token es un texto firmado que dice "quien lleva esto es el usuario
2 y su rol es turista". La firma la hace el servidor con `JWT_SECRET`, así que
nadie puede fabricar uno falso ni modificarlo.

```js
jwt.sign({ usuarioId: usuario.id, rol: usuario.rol }, process.env.JWT_SECRET,
         { expiresIn: '7d' });
```

Lo clave: **el servidor no guarda sesiones**. No hay tabla de tokens activos.
El token lleva la información dentro y el servidor solo verifica la firma. Eso
es lo que permite escalar a varios servidores sin compartir estado.

El **rol viaja dentro del token** a propósito: así `exigirRol('admin')` no
tiene que consultar la base en cada petición.

Contrapartida honesta: como no hay estado en el servidor, **no se puede
invalidar un token antes de que expire**. Si alguien roba uno, sirve hasta que
caduque. Por eso los 7 días y no 30.

### Cómo viaja el token después

Todas las peticiones siguientes llevan:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

En Android eso lo hace `AuthInterceptor` de forma automática: se engancha a
OkHttp y añade la cabecera a cada llamada sin que el resto del código se
entere.

```
verificarToken → lee la cabecera → jwt.verify → req.usuario = {id, rol} → next()
```

Desde ahí, **cualquier controller sabe quién está llamando** vía
`req.usuario.id`. Y esa es la única fuente de identidad confiable: nunca se
acepta un `usuario_id` que venga en el cuerpo de la petición, porque el cliente
podría poner cualquiera.

---

## 5. Los patrones que se repiten

Cuatro ideas explican casi todo el código del backend.

### 5.1. Transacciones: todo o nada

Registrar un prestador son dos escrituras: la fila en `usuarios` y la fila en
`prestadores`. Si la segunda falla, la primera **no debe quedar**.

```js
const conexion = await pool.getConnection();
try {
  await conexion.beginTransaction();
  // ... varias escrituras ...
  await conexion.commit();      // confirma todo junto
} catch (err) {
  await conexion.rollback();    // deshace todo
  throw err;
} finally {
  conexion.release();
}
```

Sin transacción quedaría un usuario con rol `prestador` pero sin perfil: la
app fallaría al abrir su panel y nadie sabría por qué.

### 5.2. `SELECT ... FOR UPDATE`: evitar la sobreventa

Este es el más sutil, y el que más probable es que te pregunten.

Queda **una plaza** en un tour. Dos turistas reservan al mismo tiempo:

```
Turista A: lee "1 disponible"  ─┐
Turista B: lee "1 disponible"  ─┤ los dos leen antes de que el otro escriba
Turista A: escribe "0"          │
Turista B: escribe "0"          ┘
Resultado: dos reservas para una plaza.
```

Es una **condición de carrera**. Se resuelve pidiéndole al motor que bloquee
las filas mientras se decide:

```sql
SELECT id, cupos_totales, cupos_ocupados
FROM disponibilidad
WHERE servicio_id = ? AND fecha IN (?)
FOR UPDATE
```

Con `FOR UPDATE`, B **espera** a que A termine su transacción. Cuando le toca,
lee "0 disponible" y recibe un 409. Solo funciona dentro de una transacción y
con InnoDB — por eso todas las tablas son `ENGINE=InnoDB`.

### 5.3. Placeholders `?`: inyección SQL

Nunca se concatena input del usuario dentro de una consulta:

```js
// MAL — si buscar es "'; DROP TABLE usuarios; --" se ejecuta
`WHERE titulo LIKE '%${buscar}%'`

// BIEN — el driver lo manda aparte, como dato, nunca como código
'WHERE titulo LIKE ?', [`%${buscar}%`]
```

Los filtros del catálogo se construyen dinámicamente, **pero siempre con
placeholders**:

```js
if (ciudad) { condiciones.push('ciudad = ?'); parametros.push(ciudad); }
```

El único trozo que no admite placeholder es el `ORDER BY`. Por eso sale de una
**lista blanca**: el usuario manda una clave (`precio`, `calificacion`), no SQL.

```js
const ordenamientos = { precio: 'precio ASC', calificacion: '...' };
const orderBy = ordenamientos[orden] || ordenamientos.calificacion;
```

### 5.4. Autorización por consulta, no por parámetro

Para saber si puedes ver el itinerario 5, no basta con que lo pidas: hay que
comprobar que es tuyo. Y se comprueba **en la misma consulta**:

```js
'SELECT * FROM itinerarios WHERE id = ? AND turista_id = ?', [id, req.usuario.id]
```

Si no devuelve filas, se responde **404, no 403**. Es intencional: un 403
("prohibido") le confirma a un extraño que ese itinerario existe. Un 404 no le
dice nada.

---

## 6. El chat en tiempo real

HTTP tiene una limitación: el cliente pregunta y el servidor responde. El
servidor **no puede iniciar** la conversación. Para un chat eso obligaría a
preguntar "¿hay mensajes nuevos?" cada dos segundos — malgasta batería y datos,
y aun así llega tarde.

Un **WebSocket** es una conexión que queda abierta en ambos sentidos.
Socket.io la gestiona por encima, con reconexión automática.

### Salas

```
usuario:<id>        se entra al conectar. Recibe avisos aunque el chat esté cerrado
                    (para la insignia de no leídos).
conversacion:<id>   se entra al abrir un hilo. Recibe los mensajes y el
                    "está escribiendo...".
```

Cuando alguien escribe, el mensaje se emite a las dos: a la sala del hilo (para
quien lo tenga abierto) y a la sala personal del destinatario (para quien no).

### Tres decisiones

**Se guarda en MySQL antes de reenviar.** El socket solo transporta; la fuente
de verdad es la base. Al revés, un mensaje enviado con el receptor desconectado
se perdería.

**El token se valida una sola vez, en el handshake.** Después nunca se confía
en un `usuario_id` que venga en un evento: se usa `socket.usuario`, que salió
del JWT verificado.

**La autorización vive en `services/chat.service.js`**, compartida por REST y
por socket. Duplicarla sería dejar un agujero en cuanto se arreglara un camino
y se olvidara el otro.

> **Trampa del modelo:** `conversaciones.prestador_id` apunta a
> `prestadores.id`, **no** a `usuarios.id`. Compararlo directamente contra el
> id del usuario autenticado deja entrar a la persona equivocada cuando los
> ids coinciden por azar. Hay una prueba automática (`npm test`) para ese caso.

---

## 7. Cómo se conecta la app Android

La app está en capas, igual que el backend:

```
Pantalla (Compose)     dibuja y emite eventos
      ↓
ViewModel              guarda el estado, decide qué hacer
      ↓
Repository             traduce la respuesta HTTP a un Result de éxito o error
      ↓
ApiService (Retrofit)  interfaz: cada función es una llamada HTTP
      ↓
API REST
```

**Retrofit** convierte una interfaz de Kotlin en llamadas HTTP:

```kotlin
@POST("auth/login")
suspend fun login(@Body request: LoginRequest): Response<AuthResponse>
```

Con eso, `api.login(...)` hace el `POST`, serializa el objeto a JSON y
convierte la respuesta de vuelta a un objeto Kotlin. Gson hace la traducción,
y por eso los nombres tienen que calzar:

```kotlin
@SerializedName("usuario") val usuario: UsuarioResponse
```

Si el backend manda `usuario` y Kotlin espera `user`, Gson **no falla**: deja
el campo en `null` aunque el tipo sea no-nulo, y revienta más adelante en un
sitio que no tiene nada que ver. Es el error más difícil de encontrar de todos.

**El estado sube, los eventos bajan.** La pantalla no decide nada: recibe un
estado y emite eventos. El ViewModel decide. Por eso `LoginContenido` recibe
`estado` y `onEnviar` en vez de hablar con el ViewModel — y por eso se puede
previsualizar en cualquier estado sin backend.

---

## 8. Seguridad, en resumen

| Amenaza | Defensa | Dónde |
|---|---|---|
| Contraseñas filtradas | `bcrypt` con 10 rondas | `auth.controller.js` |
| Enumeración de cuentas | Mismo mensaje para correo inexistente y clave mala | `auth.controller.js` |
| Inyección SQL | Placeholders `?` y lista blanca en `ORDER BY` | todos los controllers |
| Suplantación | JWT firmado; identidad solo desde `req.usuario` | `middleware/auth.js` |
| Acceso a datos ajenos | Propiedad comprobada en la consulta; 404 en vez de 403 | controllers |
| Escalada de privilegios | `exigirRol` con `router.use` en todo el panel | `admin.routes.js` |
| Sobreventa | `SELECT ... FOR UPDATE` en transacción | `reservas.controller.js` |
| Secretos en el repo | `.env` en `.gitignore`; `.env.example` sin valores | raíz |
| Datos en claro por la red | HTTPS en producción; `network_security_config` limita HTTP a localhost | app Android |

---

## 9. Puesta en marcha

```bash
# 1. Base de datos
cd backend
mysql -u root -p < src/db/schema.sql
mysql -u root -p < src/db/seed.sql

# 2. Variables de entorno
copy .env.example .env
node -e "console.log(require('crypto').randomBytes(48).toString('hex'))"   # para JWT_SECRET

# 3. Servidor
npm install
npm run dev

# 4. Comprobar
curl http://localhost:3000/api/salud
```

Usuarios de prueba (contraseña `travelhub2026`):

| Correo | Rol |
|---|---|
| `admin@travelhub.pe` | admin |
| `camila@example.com` | turista |
| `julio.guia@example.com` | prestador aprobado |
| `marco.guia@example.com` | prestador **pendiente** de aprobación |

Desde el emulador de Android, el `localhost` de tu PC es `10.0.2.2`. Desde un
celular físico, la IP de tu máquina en la red local.

---

## 10. Qué falta

- Notificación push por FCM cuando el destinatario del chat está desconectado
  (el punto exacto está marcado con un `TODO` en `chat.socket.js`)
- `src/docs/openapi.yaml` para que Swagger funcione
- Pantallas de la app más allá de login y home
- Despliegue en la nube

---

## Documentos relacionados

- [`modelo-datos.md`](modelo-datos.md) — diagrama ER y detalle de cada tabla
- [`../backend/README.md`](../README.md) — referencia de los 44 endpoints
