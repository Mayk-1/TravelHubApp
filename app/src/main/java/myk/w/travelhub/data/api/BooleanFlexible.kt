package myk.w.travelhub.data.api

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

/**
 * Acepta como booleano tanto `true/false` como `1/0` y `"1"/"0"`.
 *
 * POR QUE HACE FALTA
 *
 * MySQL no tiene un tipo BOOLEAN de verdad: `BOOLEAN` es un alias de
 * `TINYINT(1)`. Y las expresiones de comparacion, como la de la vista
 * v_catalogo:
 *
 *     (p.estado_verificacion = 'aprobado') AS prestador_verificado
 *
 * devuelven 1 o 0, no true o false. El JSON que llega a la app es:
 *
 *     "prestador_verificado": 1
 *
 * Gson, por defecto, NO hace esa conversion: lanza
 * "Expected a boolean but was NUMBER". Este deserializador la hace.
 *
 * ALTERNATIVA: arreglarlo en el backend, convirtiendo a booleano en cada
 * controller o con un `typeCast` en el pool de mysql2. Se resuelve aqui
 * porque cubre de una vez todos los campos booleanos, presentes y futuros,
 * y porque un cliente robusto no deberia romperse por esto.
 */
class BooleanFlexible : JsonDeserializer<Boolean> {

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Boolean {
        val primitivo = json.asJsonPrimitive

        return when {
            primitivo.isBoolean -> primitivo.asBoolean
            primitivo.isNumber -> primitivo.asInt != 0
            primitivo.isString -> when (primitivo.asString.lowercase()) {
                "true", "1" -> true
                "false", "0", "" -> false
                else -> throw JsonParseException("No es un booleano: ${primitivo.asString}")
            }
            else -> throw JsonParseException("No es un booleano: $json")
        }
    }
}
