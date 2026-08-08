const servicio = require('../src/services/chat.service');

// Doble de la base de datos: devuelve la fila que le indiquemos.
function ejecutorCon(fila) {
  return { query: async () => [fila ? [fila] : []] };
}

let fallos = 0;
function afirmar(nombre, condicion) {
  console.log(`  ${condicion ? 'PASA' : 'FALLA'}  ${nombre}`);
  if (!condicion) fallos++;
}

(async () => {
  // Conversacion 7: turista es el usuario 2; el prestador es prestadores.id=3,
  // cuyo usuario_id es 40.
  const fila = { id: 7, turista_id: 2, prestador_id: 3, prestador_usuario_id: 40 };

  const comoTurista = await servicio.accesoAConversacion(ejecutorCon(fila), 7, 2);
  afirmar('el turista entra', comoTurista !== null);
  afirmar('el turista escribe al prestador (usuario 40)',
    comoTurista?.destinatarioUsuarioId === 40);
  afirmar('esTurista es true', comoTurista?.esTurista === true);

  const comoPrestador = await servicio.accesoAConversacion(ejecutorCon(fila), 7, 40);
  afirmar('el prestador entra por su usuario_id', comoPrestador !== null);
  afirmar('el prestador escribe al turista (usuario 2)',
    comoPrestador?.destinatarioUsuarioId === 2);

  const extrano = await servicio.accesoAConversacion(ejecutorCon(fila), 7, 99);
  afirmar('un tercero queda fuera', extrano === null);

  // EL CASO IMPORTANTE: el usuario 3 no participa. Pero prestador_id vale 3,
  // asi que comparar usuario.id contra prestador_id lo dejaria entrar.
  const colision = await servicio.accesoAConversacion(ejecutorCon(fila), 7, 3);
  afirmar('usuario cuyo id coincide con prestador_id NO entra', colision === null);

  const inexistente = await servicio.accesoAConversacion(ejecutorCon(null), 999, 2);
  afirmar('conversacion inexistente devuelve null', inexistente === null);

  console.log(fallos === 0 ? '\nTodas las comprobaciones pasan.' : `\n${fallos} FALLO(S)`);
  process.exit(fallos ? 1 : 0);
})();
