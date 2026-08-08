require('dotenv').config();

const http = require('http');
const express = require('express');
const cors = require('cors');

const pool = require('./config/db');
const { configurarChat } = require('./sockets/chat.socket');
const { noEncontrado, manejadorErrores } = require('./middleware/errores');

const authRoutes = require('./routes/auth.routes');
const serviciosRoutes = require('./routes/servicios.routes');
const reservasRoutes = require('./routes/reservas.routes');
const itinerariosRoutes = require('./routes/itinerarios.routes');
const resenasRoutes = require('./routes/resenas.routes');
const adminRoutes = require('./routes/admin.routes');
const chatRoutes = require('./routes/chat.routes');

if (!process.env.JWT_SECRET) {
  console.error('Falta JWT_SECRET en el archivo .env. Copia .env.example y completalo.');
  process.exit(1);
}

const app = express();

const origenes = (process.env.CORS_ORIGINS || '')
  .split(',')
  .map((o) => o.trim())
  .filter(Boolean);

app.use(cors({ origin: origenes.length ? origenes : true }));
app.use(express.json({ limit: '1mb' }));

app.get('/api/salud', async (req, res) => {
  try {
    await pool.query('SELECT 1');
    res.json({ estado: 'ok', bd: 'conectada', hora: new Date().toISOString() });
  } catch (err) {
    res.status(503).json({ estado: 'degradado', bd: 'sin conexion' });
  }
});

app.use('/api/auth', authRoutes);
app.use('/api/servicios', serviciosRoutes);
app.use('/api/reservas', reservasRoutes);
app.use('/api/itinerarios', itinerariosRoutes);
app.use('/api/resenas', resenasRoutes);
app.use('/api/admin', adminRoutes);
app.use('/api/chat', chatRoutes);

try {
  const path = require('path');
  const fs = require('fs');
  const rutaSpec = path.join(__dirname, 'docs', 'openapi.yaml');
  if (fs.existsSync(rutaSpec)) {
    const swaggerUi = require('swagger-ui-express');
    const YAML = require('yamljs');
    const spec = YAML.load(rutaSpec);
    app.use('/api-docs', swaggerUi.serve, swaggerUi.setup(spec));
    app.get('/api-docs.json', (req, res) => res.json(spec));
  }
} catch (err) {
  console.warn('No se pudo cargar la documentacion Swagger:', err.message);
}

app.use(noEncontrado);
app.use(manejadorErrores);

const PUERTO = Number(process.env.PORT) || 3000;

const servidor = http.createServer(app);
const io = configurarChat(servidor, app);

async function iniciar() {
  try {
    await pool.probarConexion();
    console.log(`Base de datos "${process.env.DB_NAME}" conectada`);
  } catch (err) {
    console.error('No se pudo conectar a MySQL:', err.message);
    console.error('Revisa las credenciales del archivo .env');
    process.exit(1);
  }

  servidor.listen(PUERTO, () => {
    console.log(`TravelHub API escuchando en http://localhost:${PUERTO}`);
    console.log(`Salud:  http://localhost:${PUERTO}/api/salud`);
    console.log(`Socket: ws://localhost:${PUERTO}  (Socket.io)`);
  });
}

async function apagar(senal) {
  console.log(`\n${senal} recibida, cerrando servidor...`);
  io.close();
  servidor.close(async () => {
    try {
      await pool.end();
    } catch (err) {
      console.error('Error al cerrar el pool de MySQL:', err.message);
    }
    console.log('Cerrado correctamente');
    process.exit(0);
  });
  // Si se queda colgado se cierra iguamente en 10 segundos
  setTimeout(() => process.exit(1), 10000).unref();
}

process.on('SIGINT', () => apagar('SIGINT'));
process.on('SIGTERM', () => apagar('SIGTERM'));

iniciar();

module.exports = { app, servidor, io };
