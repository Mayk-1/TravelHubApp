const mysql = require('mysql2/promise');

const pool = mysql.createPool({
  host: process.env.DB_HOST || 'localhost',
  port: Number(process.env.DB_PORT) || 3306,
  user: process.env.DB_USER || 'root',
  password: process.env.DB_PASSWORD || '',
  database: process.env.DB_NAME || 'travelhub',

  waitForConnections: true,
  connectionLimit: 10,
  queueLimit: 0,

  decimalNumbers: true,

  dateStrings: ['DATE', 'DATETIME']
});

async function probarConexion() {
  const conexion = await pool.getConnection();
  try {
    await conexion.ping();
  } finally {
    conexion.release();
  }
}

module.exports = pool;
module.exports.probarConexion = probarConexion;
