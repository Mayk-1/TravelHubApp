# Preguntas probables — sustentación TravelHub

Preguntas que un docente puede hacer sobre este proyecto, con la respuesta
anclada al código real. Las de "modifica esto delante de mí" llevan la ruta
exacta de archivos.

---

## Parte 1 — "Modifica esto ahora"

Son las que más se piden y las que más nervios dan. **Todas siguen el mismo
recorrido**: si entiendes uno, entiendes todos.

```
Pantalla → ViewModel → Repository → ApiService → routes → controller → SQL
```

### 1.1. "Añade un campo al registro" (edad, teléfono, DNI…)

El recorrido completo, en orden:

| # | Archivo | Qué haces |
|---|---|---|
| 1 | `LoginViewModel.kt` | Campo en `LoginFormState` + función `onXChange` |
| 2 | `LoginScreen.kt` | Parámetro en `LoginContenido` + `OutlinedTextField` |
| 3 | `LoginScreen.kt` | **Pasar el callback** en la llamada a `LoginContenido` |
| 4 | `RegistroRequest.kt` | Campo con su `@SerializedName` |
| 5 | `AuthRepository.kt` | Parámetro en `registro()` |
| 6 | `auth.routes.js` | Validación con `body('campo')` |
| 7 | `auth.controller.js` | Añadirlo al `INSERT` |
| 8 | `schema.sql` | Columna nueva + migración |

**El paso 3 es el que se olvida.** El parámetro tiene valor por defecto `= {}`,
así que si no lo pasas compila igual y el campo no responde al teclado. Ya
pasó una vez.

**Trampa del paso 1:** al copiar `onRolChange` es fácil dejar `it.copy(rol = v)`
en vez de `it.copy(edad = v)`. Como ambos son `String`, el compilador no dice
nada y el bug aparece en otro sitio.

> Si te lo piden y hay poco tiempo, di: *"Lo hago en la interfaz para que se
> vea, y explico los otros cuatro pasos hasta la base de datos"*. Demuestra
> que entiendes el recorrido completo.

### 1.2. "Añade un filtro al catálogo"

Más corto, porque el backend ya los soporta casi todos.

1. `CatalogoUiState.kt` → campo en `FiltrosState`
2. `CatalogoViewModel.kt` → función `onXChange` que llama a `cargar()`
3. `CatalogoScreen.kt` → control en `BarraFiltros` + pasar el callback
4. `ServicioRepository.kt` → pasar el parámetro a `api.listar(...)`

El `ServicioApiService` **ya declara** `ciudad`, `precio_min`, `precio_max`,
`calificacion` y `fecha`. Solo la ciudad y el precio máximo no tienen control
en pantalla todavía. Es la modificación más fácil que te pueden pedir.

### 1.3. "Cambia una validación"

Contraseña de 8 a 10 caracteres — hay que tocar **dos sitios**:

- `LoginViewModel.kt` → `password.length >= 8` (habilita el botón)
- `auth.routes.js` → `isLength({ min: 8 })` (validación real)

**Pregunta de seguimiento casi segura:** *"¿por qué en dos sitios?"*
Porque la del cliente es comodidad —evita una petición inútil— y la del
servidor es la que protege. Cualquiera puede saltarse la app y llamar a la
API con curl.

### 1.4. "Añade una categoría de servicio"

```sql
INSERT INTO categorias_servicio (slug, nombre, icono)
VALUES ('alquiler', 'Alquiler de equipos', 'hiking');
```

Y ya está: aparece en los chips del catálogo sin recompilar nada. **Ese es
justo el argumento** de por qué las categorías son una tabla y no un `ENUM`.
Con `ENUM` haría falta un `ALTER TABLE`.

### 1.5. "Muestra un dato más en una tarjeta"

Si el dato ya viaja en el JSON, es una línea en el composable. Si no:

1. ¿Lo devuelve el controller? Si no, añadirlo al `SELECT`
2. ¿Está en el modelo Kotlin? Si no, campo con `@SerializedName`
3. Pintarlo

**Ojo:** si añades el campo en Kotlin pero el backend no lo manda, Gson lo
deja en `null` **sin error**. Nos pasó con `direccion`, y por eso existe la
migración 002.

---

## Parte 2 — Arquitectura

**¿Por qué MVVM y no todo en la pantalla?**
Porque la pantalla se destruye al girar el móvil y el ViewModel no. Si el
estado viviera en la pantalla, al rotar perderías lo que el usuario escribió.
Además permite las 63 vistas previas: el composable "sin estado" se dibuja sin
ViewModel ni backend.

**¿Qué hace el Repository? ¿No sobra una capa?**
Traduce respuestas HTTP a `Result` de éxito o error con un mensaje ya legible.
El ViewModel no sabe que existe Retrofit ni que hay códigos 401 o 500. Si
mañana cambiamos Retrofit por Ktor, solo se toca esa capa.

**¿Por qué la app no se conecta directamente a MySQL?**
Habría que meter la contraseña de la base dentro del APK, y cualquiera puede
descompilarlo. Con el backend en medio, el celular solo conoce una URL y su
propio token.

**¿Qué pasa si giro el teléfono?**
No se pierde nada: el estado vive en ViewModels. El diálogo de eliminar viaje
es un ejemplo deliberado — el viaje pendiente de borrar está en el ViewModel,
no en un `remember`, justo para que sobreviva al giro.

**¿Cómo sobrevive la sesión al cerrar la app?**
`TokenStore` guarda el JWT en DataStore, que persiste en disco. `MainActivity`
lo lee al arrancar y decide si va a Login o a Home.

---

## Parte 3 — Base de datos

**Explica el modelo de servicios.**
Class Table Inheritance: una tabla `servicios` con lo común y cinco tablas
satélite con lo específico. En cada satélite la clave primaria **es** la
foránea, lo que garantiza la relación 1:1 a nivel de motor. Permite un
catálogo unificado con filtros y añadir tipos sin tocar lo existente.

**¿Por qué guardas el precio en la reserva si ya está en el servicio?**
Porque son datos de naturaleza distinta. El precio del servicio es de
catálogo y cambia; el de la reserva es transaccional y es lo que se pactó.
Si el prestador sube su tarifa mañana, las reservas hechas conservan el
importe acordado.

**¿Qué pasa si borro un servicio que tiene reservas?**
No se puede: la FK es `ON DELETE RESTRICT`. Hay que desactivarlo con
`activo = FALSE`. Protege el historial del turista, y por eso el panel de
admin desactiva en vez de borrar.

**¿Para qué usas triggers? Hay cuatro.**
Dos mantienen `calificacion_promedio` sincronizado al insertar o borrar
reseñas (`trg_resena_insert`, `trg_resena_delete`). Están en la base y no en
Node para que el dato se mantenga aunque alguien inserte por otra vía.

Los otros dos (`trg_item_contenido_insert`, `trg_item_contenido_update`)
validan que una parada del itinerario tenga reserva o título. Eso debería ser
un `CHECK`, pero **MySQL lo rechaza** con el error 3823 cuando la columna
tiene una clave foránea con `ON DELETE SET NULL`: esa acción modificaría la
columna sin volver a evaluar el `CHECK`, así que el motor prohíbe la
combinación. Es una restricción específica de MySQL — PostgreSQL sí lo acepta.

**¿Por qué una vista y no un JOIN en cada consulta?**
`v_catalogo` resuelve cuatro `JOIN` una sola vez. Si la estructura cambia, se
toca la vista y no seis controllers.

**¿Está normalizado?**
Sí, hasta 3FN, con dos desnormalizaciones deliberadas y documentadas:
`calificacion_promedio` y `total_resenas`, cacheadas por rendimiento y
mantenidas por triggers.

**¿Por qué esos índices?**
`idx_servicios_busqueda (activo, categoria_id, ciudad)` cubre la consulta más
frecuente del catálogo. `uq_disponibilidad (servicio_id, fecha)` además de
evitar duplicados permite `ON DUPLICATE KEY UPDATE` para el upsert del
calendario.

---

## Parte 4 — Seguridad

Aquí es donde más puntos se ganan.

**¿Cómo guardas las contraseñas?**
Con `bcrypt` a 10 rondas. Nunca en texto plano. Un hash es de una sola
dirección: de la contraseña sacas el hash, del hash no sacas la contraseña.
Las rondas hacen que probar por fuerza bruta sea lento.

**¿Qué pasa si el correo no existe al iniciar sesión?**
Devuelve el mismo mensaje que si la contraseña estuviera mal. Si dijera "ese
correo no está registrado", un atacante podría averiguar qué direcciones
tienen cuenta.

**¿Qué es el JWT y dónde se guarda?**
Un texto firmado por el servidor que dice quién eres y qué rol tienes. El
servidor **no guarda sesiones**: solo verifica la firma. En el celular vive
en DataStore.

**¿Se puede falsificar?**
No sin el `JWT_SECRET`. Pero sí se puede **leer**: el contenido va codificado,
no cifrado. Por eso dentro solo hay el id y el rol, nunca datos sensibles.

**¿Puedes cerrar la sesión de alguien a distancia?**
No, y es la contrapartida de no guardar estado. Si roban un token, sirve
hasta que caduque. Por eso están a 7 días. Se resolvería con una lista negra
en Redis o tokens de refresco.

**¿Cómo evitas la inyección SQL?**
Todo va con placeholders `?`; los valores nunca se concatenan. El `ORDER BY`,
que no admite placeholder, sale de una lista blanca: el usuario manda una
clave como `precio`, no SQL.

**¿Qué pasa si dos personas reservan la última plaza a la vez?**
Es una condición de carrera, y se resuelve con `SELECT ... FOR UPDATE` dentro
de una transacción. El segundo espera al primero y se encuentra sin cupo. Sin
eso, ambos leerían "1 disponible" y venderías la plaza dos veces.

**¿Puede un usuario ver el itinerario de otro?**
No. La propiedad se comprueba en la misma consulta:
`WHERE id = ? AND turista_id = ?`. Y si no es suyo se responde **404, no 403**,
porque un 403 le confirmaría a un extraño que ese recurso existe.

**¿Qué impide que alguien reserve por un precio inventado?**
Que el precio lo calcula el servidor. El subtotal que ve el usuario antes de
confirmar es una estimación local, y así lo dice la pantalla.

---

## Parte 5 — Kotlin y Compose

**¿Qué es un composable?**
Una función que describe un trozo de interfaz. No devuelve vistas: se ejecuta
otra vez cuando cambia el estado que lee. A eso se le llama recomposición.

**¿Por qué el `TextField` necesita `value` y `onValueChange`?**
Porque es totalmente controlado: no guarda lo que escribes, solo muestra lo
que le pasas y avisa de las teclas. Si nadie actualiza el estado, el campo se
queda vacío por mucho que teclees.

**¿Qué es una corrutina y por qué `suspend`?**
Una función que puede pausarse sin bloquear el hilo. `suspend` marca las que
esperan algo —una petición de red— para que Kotlin sepa que ahí puede ceder
el turno. Sin esto, la app se congelaría durante cada llamada.

**¿Qué es `viewModelScope`?**
El ámbito de corrutinas del ViewModel. Cuando el ViewModel muere, cancela
automáticamente lo que quedara en marcha. Evita que una respuesta llegue a
una pantalla que ya no existe.

**¿Qué es un `StateFlow`?**
Un flujo que siempre tiene un valor actual y notifica cuando cambia. Es lo que
la pantalla observa con `collectAsStateWithLifecycle()`.

**¿Por qué `sealed interface` para los estados?**
Porque obliga al `when` a cubrir todos los casos. Si mañana añado un estado
"Vacío" y olvido pintarlo, **no compila**. Con strings o booleanos el olvido
sería silencioso.

---

## Parte 6 — Puntos débiles (dilos tú antes)

Reconocer los límites da mejor impresión que pretender que no existen.

| Hueco | Qué decir |
|---|---|
| Registrarse como prestador falla | El backend exige `documento_numero` y el formulario no lo envía. Está identificado y es una pantalla de tres campos |
| El token no se valida al arrancar | Si caducó, entras a Home y falla en la primera petición real. Se cierra llamando a `/auth/me` al iniciar |
| Las imágenes salen en gris | Las URLs del seed apuntan a un almacenamiento en la nube que aún no existe. El modelo ya lo contempla |
| No hay mapa | Requiere clave de Google Maps. El modelo guarda latitud, longitud y la caché de distancias |
| Sin notificaciones push | La tabla `dispositivos` y el endpoint que registra el token de FCM ya existen; falta enviar el aviso cuando el destinatario no tiene socket conectado |
| Chat sin pantalla | Backend completo, 6 eventos de socket y prueba automática de autorización. Falta la UI |
| Sin desplegar | Corre en local; `.env` y `BuildConfig` ya separan desarrollo de producción |

---

## Parte 7 — Preguntas trampa

**"¿Esto lo hiciste tú?"**
Responde con honestidad y demuestra dominio explicando **por qué** algo está
así. Nadie que no entienda el código puede justificar `FOR UPDATE` o la
diferencia entre 403 y 404.

**"¿Qué harías distinto?"**
Buena respuesta: añadir una capa de repositorios en el backend para que los
controllers no escriban SQL. No se hizo porque para 21 tablas serían ~500
líneas que solo reenvían llamadas, y nadie va a cambiar de motor a mitad de
semestre. Es un compromiso consciente, no un descuido.

**"¿Qué es lo más difícil que resolviste?"**
La condición de carrera de los cupos. Es concreta, tiene una solución técnica
clara y demuestra que pensaste en concurrencia.

**"Demuéstrame que la calculadora funciona de verdad."**
Reserva un servicio desde el catálogo, ve a Viajes, añádela a un día y enseña
cómo cambia el total y la barra de presupuesto. Ensaya ese recorrido: es la
demo más completa que tienes.

---

## Cómo prepararte

1. **Ensaya el recorrido completo** — login, catálogo, reservar, añadir al
   viaje, ver el costo cambiar. Cronométralo.
2. **Ten el backend corriendo antes de empezar.** Si se cae, la app parece
   rota. Ten a mano `npm run dev` y `curl /api/salud`.
3. **Abre el Logcat filtrado por `okhttp`** en una ventana. Si algo falla,
   diagnosticarlo en vivo impresiona más que una demo perfecta.
4. **Ten `schema.sql` abierto** para señalar mientras explicas el modelo.
5. Si no sabes algo, dilo y razona en voz alta. "No lo recuerdo, pero
   funcionaría así porque…" vale mucho más que inventar.
