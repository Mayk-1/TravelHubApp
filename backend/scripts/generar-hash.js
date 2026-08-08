
const bcrypt = require('bcrypt');

const RONDAS = 10;   // igual que en auth.controller.js
const clave = process.argv[2];

if (!clave) {
  console.error('Uso: node scripts/generar-hash.js "tu-contrasena"');
  process.exit(1);
}

if (clave.length < 8) {
  console.error('La contrasena debe tener al menos 8 caracteres (lo exige el registro).');
  process.exit(1);
}

(async () => {
  const hash = await bcrypt.hash(clave, RONDAS);

  const acepta = await bcrypt.compare(clave, hash);
  const rechaza = !(await bcrypt.compare(`${clave}-mal`, hash));

  if (!acepta || !rechaza) {
    console.error('El hash generado no supero la verificacion. No lo uses.');
    process.exit(1);
  }

  console.log(`\nContrasena : ${clave}`);
  console.log(`Hash       : ${hash}`);
  console.log('Verificado : si\n');
  console.log('Pegalo en src/db/seed.sql, en la linea:');
  console.log(`  SET @pw = '${hash}';\n`);
})();
