package myk.w.travelhub.data.api

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

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
