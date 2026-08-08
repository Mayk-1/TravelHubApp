package myk.w.travelhub.data.model.response

import com.google.gson.annotations.SerializedName

/**
 * GET /api/servicios/:id
 *
 * Es la fila de v_catalogo mas cuatro anadidos que hace el controller:
 * `detalle` (de la tabla satelite), `fotos`, `idiomas` y `resenas`.
 *
 * `detalle` es el punto delicado: sus campos dependen del tipo de servicio.
 * Un guia trae anios_experiencia; un hospedaje trae habitaciones. Como Gson
 * necesita una clase concreta, se declaran TODOS los campos posibles como
 * opcionales y solo llegan los que correspondan. Es la forma mas simple de
 * manejar el Class Table Inheritance del backend desde el cliente.
 */
data class ServicioDetalleResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("titulo") val titulo: String,
    @SerializedName("descripcion") val descripcion: String? = null,

    @SerializedName("precio") val precio: Double,
    @SerializedName("moneda") val moneda: String = "PEN",
    @SerializedName("unidad_precio") val unidadPrecio: String = "por_servicio",

    @SerializedName("direccion") val direccion: String? = null,
    @SerializedName("ciudad") val ciudad: String,
    @SerializedName("latitud") val latitud: Double? = null,
    @SerializedName("longitud") val longitud: Double? = null,
    @SerializedName("capacidad_maxima") val capacidadMaxima: Int = 1,

    @SerializedName("calificacion_promedio") val calificacion: Double = 0.0,
    @SerializedName("total_resenas") val totalResenas: Int = 0,

    @SerializedName("categoria_slug") val categoriaSlug: String,
    @SerializedName("categoria_nombre") val categoriaNombre: String,

    @SerializedName("prestador_id") val prestadorId: Int,
    @SerializedName("prestador_nombre") val prestadorNombre: String,
    @SerializedName("prestador_verificado") val prestadorVerificado: Boolean = false,

    @SerializedName("foto_principal") val fotoPrincipal: String? = null,

    @SerializedName("detalle") val detalle: DetalleEspecifico? = null,
    @SerializedName("fotos") val fotos: List<FotoResponse> = emptyList(),
    @SerializedName("idiomas") val idiomas: List<IdiomaResponse> = emptyList(),
    @SerializedName("resenas") val resenas: List<ResenaResponse> = emptyList()
) {
    val simbolo: String get() = if (moneda == "PEN") "S/" else moneda

    val precioFormateado: String get() = "$simbolo %.2f".format(precio)

    val unidadLegible: String
        get() = when (unidadPrecio) {
            "por_persona" -> "por persona"
            "por_noche" -> "por noche"
            "por_dia" -> "por dia"
            "por_hora" -> "por hora"
            "por_km" -> "por km"
            else -> ""
        }

    /** true si el precio se multiplica por las noches entre dos fechas. */
    val requiereRangoDeFechas: Boolean get() = unidadPrecio == "por_noche"

    /**
     * Pares "etiqueta - valor" con los atributos propios del tipo, listos
     * para pintar. Devuelve solo los que llegaron.
     */
    val atributos: List<Pair<String, String>>
        get() {
            val d = detalle ?: return emptyList()
            return buildList {
                // Guia
                d.aniosExperiencia?.let { add("Experiencia" to "$it anios") }
                d.duracionHoras?.let { add("Duracion" to "$it horas") }
                d.tamanoMaxGrupo?.let { add("Grupo maximo" to "$it personas") }
                d.puntoEncuentro?.let { add("Punto de encuentro" to it) }
                if (d.incluyeTransporte == true) add("Transporte" to "Incluido")

                // Hospedaje
                d.tipoAlojamiento?.let { add("Tipo" to it.replaceFirstChar(Char::uppercase)) }
                d.habitaciones?.let { add("Habitaciones" to "$it") }
                d.camas?.let { add("Camas" to "$it") }
                d.banos?.let { add("Banos" to "$it") }
                if (d.wifi == true) add("Wifi" to "Si")
                if (d.desayunoIncluido == true) add("Desayuno" to "Incluido")
                if (d.estacionamiento == true) add("Estacionamiento" to "Si")
                d.horaCheckIn?.let { add("Check-in" to it.take(5)) }
                d.horaCheckOut?.let { add("Check-out" to it.take(5)) }
            }
        }
}


/**
 * Union de los campos de todas las tablas satelite. Todos opcionales:
 * llegan solo los del tipo que corresponda.
 */
data class DetalleEspecifico(
    // servicios_guia
    @SerializedName("anios_experiencia") val aniosExperiencia: Int? = null,
    @SerializedName("duracion_horas") val duracionHoras: Double? = null,
    @SerializedName("tamano_max_grupo") val tamanoMaxGrupo: Int? = null,
    @SerializedName("incluye_transporte") val incluyeTransporte: Boolean? = null,
    @SerializedName("punto_encuentro") val puntoEncuentro: String? = null,

    // servicios_hospedaje
    @SerializedName("tipo_alojamiento") val tipoAlojamiento: String? = null,
    @SerializedName("habitaciones") val habitaciones: Int? = null,
    @SerializedName("camas") val camas: Int? = null,
    @SerializedName("banos") val banos: Int? = null,
    @SerializedName("wifi") val wifi: Boolean? = null,
    @SerializedName("desayuno_incluido") val desayunoIncluido: Boolean? = null,
    @SerializedName("estacionamiento") val estacionamiento: Boolean? = null,
    @SerializedName("hora_check_in") val horaCheckIn: String? = null,
    @SerializedName("hora_check_out") val horaCheckOut: String? = null
)


data class FotoResponse(
    @SerializedName("url") val url: String,
    @SerializedName("orden") val orden: Int = 0
)

data class IdiomaResponse(
    @SerializedName("codigo") val codigo: String,
    @SerializedName("nombre") val nombre: String,
    @SerializedName("nivel") val nivel: String
)

data class ResenaResponse(
    @SerializedName("calificacion") val calificacion: Int,
    @SerializedName("comentario") val comentario: String? = null,
    @SerializedName("creado_en") val creadoEn: String? = null,
    @SerializedName("turista_nombre") val turistaNombre: String
)


/** GET /api/servicios/:id/disponibilidad?desde=&hasta= */
data class DisponibilidadResponse(
    @SerializedName("fecha") val fecha: String,
    @SerializedName("cupos_totales") val cuposTotales: Int,
    @SerializedName("cupos_ocupados") val cuposOcupados: Int,
    @SerializedName("cupos_libres") val cuposLibres: Int,
    @SerializedName("precio_especial") val precioEspecial: Double? = null,
    @SerializedName("bloqueado") val bloqueado: Boolean = false
) {
    val disponible: Boolean get() = !bloqueado && cuposLibres > 0
}
