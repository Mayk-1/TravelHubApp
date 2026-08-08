package myk.w.travelhub.data.model.response

import com.google.gson.annotations.SerializedName

/**
 * Un viaje en el listado: GET /api/itinerarios
 *
 * Ademas de las columnas de la tabla, el backend calcula tres valores
 * (`dias`, `total_paradas`, `costo_actual`) para que la tarjeta del listado
 * no tenga que pedir nada mas.
 */
data class ItinerarioResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("titulo") val titulo: String,
    @SerializedName("destino") val destino: String,
    // Llegan como texto "YYYY-MM-DD" gracias a dateStrings en el pool de MySQL.
    @SerializedName("fecha_inicio") val fechaInicio: String,
    @SerializedName("fecha_fin") val fechaFin: String,
    @SerializedName("presupuesto_estimado") val presupuesto: Double? = null,
    @SerializedName("moneda") val moneda: String = "PEN",

    @SerializedName("dias") val dias: Int = 0,
    @SerializedName("total_paradas") val totalParadas: Int = 0,
    @SerializedName("costo_actual") val costoActual: Double = 0.0
) {
    val simbolo: String get() = if (moneda == "PEN") "S/" else moneda

    val costoFormateado: String get() = "$simbolo %.2f".format(costoActual)

    /** true si el gasto ya supera lo que el usuario habia presupuestado. */
    val excedePresupuesto: Boolean
        get() = presupuesto != null && costoActual > presupuesto
}


/** Detalle completo: GET /api/itinerarios/:id */
data class ItinerarioDetalleResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("titulo") val titulo: String,
    @SerializedName("destino") val destino: String,
    @SerializedName("fecha_inicio") val fechaInicio: String,
    @SerializedName("fecha_fin") val fechaFin: String,
    @SerializedName("presupuesto_estimado") val presupuesto: Double? = null,
    @SerializedName("moneda") val moneda: String = "PEN",
    @SerializedName("dias") val dias: List<DiaResponse> = emptyList()
)


/** Un dia del viaje, con sus paradas ya agrupadas por el backend. */
data class DiaResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("dia_numero") val numero: Int,
    @SerializedName("fecha") val fecha: String,
    @SerializedName("notas") val notas: String? = null,
    @SerializedName("items") val items: List<ParadaResponse> = emptyList()
)


/**
 * Una parada del dia. Puede ser una reserva o un punto libre del mapa, de
 * ahi que casi todo sea opcional.
 */
data class ParadaResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("orden") val orden: Int,
    @SerializedName("titulo_libre") val tituloLibre: String? = null,

    @SerializedName("hora_inicio") val horaInicio: String? = null,
    @SerializedName("hora_fin") val horaFin: String? = null,

    // Resultado cacheado de la Directions API: distancia y tiempo desde la
    // parada anterior.
    @SerializedName("distancia_metros") val distanciaMetros: Int? = null,
    @SerializedName("duracion_segundos") val duracionSegundos: Int? = null,

    @SerializedName("reserva_id") val reservaId: Int? = null,
    @SerializedName("reserva_codigo") val reservaCodigo: String? = null,
    @SerializedName("reserva_estado") val reservaEstado: String? = null,
    @SerializedName("reserva_subtotal") val reservaSubtotal: Double? = null,
    @SerializedName("num_personas") val numPersonas: Int? = null,

    @SerializedName("servicio_titulo") val servicioTitulo: String? = null,
    @SerializedName("categoria_slug") val categoriaSlug: String? = null
) {
    /** El titulo del servicio manda; si no hay reserva, el texto libre. */
    val nombre: String
        get() = servicioTitulo ?: tituloLibre ?: "Parada sin nombre"

    val esReserva: Boolean get() = reservaId != null

    /** "06:45 - 16:30", o solo la hora de inicio, o vacio. */
    val horario: String
        get() = when {
            horaInicio != null && horaFin != null -> "${corta(horaInicio)} - ${corta(horaFin)}"
            horaInicio != null -> corta(horaInicio)
            else -> ""
        }

    /** "1.8 km · 20 min" — lo que costo llegar desde la parada anterior. */
    val trayecto: String?
        get() {
            if (distanciaMetros == null) return null
            val km = distanciaMetros / 1000.0
            val distancia = if (km >= 1) "%.1f km".format(km) else "$distanciaMetros m"
            val minutos = duracionSegundos?.let { " · ${it / 60} min" } ?: ""
            return distancia + minutos
        }

    // MySQL devuelve TIME como "06:45:00"; en pantalla sobran los segundos.
    private fun corta(hora: String) = hora.take(5)
}
