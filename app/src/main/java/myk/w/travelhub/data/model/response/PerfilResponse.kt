package myk.w.travelhub.data.model.response

import com.google.gson.annotations.SerializedName

/**
 * GET /api/auth/me
 *
 * El objeto `prestador` solo llega si rol = "prestador"; en turistas y
 * administradores viene ausente y Gson lo deja en null.
 */
data class PerfilResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("nombre") val nombre: String,
    @SerializedName("email") val email: String,
    @SerializedName("rol") val rol: String = "turista",
    @SerializedName("telefono") val telefono: String? = null,
    @SerializedName("foto_url") val fotoUrl: String? = null,
    @SerializedName("creado_en") val creadoEn: String? = null,
    @SerializedName("prestador") val prestador: PerfilPrestador? = null
) {
    val esPrestador: Boolean get() = rol == "prestador"
    val esAdmin: Boolean get() = rol == "admin"

    val rolLegible: String
        get() = when (rol) {
            "prestador" -> "Prestador de servicios"
            "admin" -> "Administrador"
            else -> "Turista"
        }

    /** Iniciales para el avatar cuando no hay foto: "Camila Rojas" -> "CR". */
    val iniciales: String
        get() = nombre.trim().split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifEmpty { "?" }

    /** "2026-06-15 10:00:00" -> "2026-06-15". Solo la fecha basta en pantalla. */
    val miembroDesde: String? get() = creadoEn?.take(10)
}


data class PerfilPrestador(
    @SerializedName("id") val id: Int,
    @SerializedName("razon_social") val razonSocial: String? = null,
    @SerializedName("descripcion") val descripcion: String? = null,
    @SerializedName("ciudad_base") val ciudadBase: String = "Puno",
    @SerializedName("estado_verificacion") val estado: String = "pendiente",
    @SerializedName("motivo_rechazo") val motivoRechazo: String? = null
) {
    val aprobado: Boolean get() = estado == "aprobado"
    val rechazado: Boolean get() = estado == "rechazado"

    val estadoLegible: String
        get() = when (estado) {
            "aprobado" -> "Verificado"
            "rechazado" -> "Rechazado"
            else -> "Pendiente de aprobación"
        }

    /**
     * Qué puede hacer el prestador segun su estado. El backend lo aplica de
     * verdad al crear un servicio; aqui solo se explica.
     */
    val explicacion: String
        get() = when (estado) {
            "aprobado" -> "Puedes publicar servicios y recibir reservas."
            "rechazado" -> motivoRechazo ?: "Un administrador rechazó tu solicitud."
            else -> "Un administrador debe aprobar tu cuenta antes de que puedas publicar servicios."
        }
}


/**
 * GET /api/servicios/mios — un servicio del prestador, con cuántas reservas
 * activas tiene.
 */
data class MiServicioResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("titulo") val titulo: String,
    @SerializedName("precio") val precio: Double,
    @SerializedName("moneda") val moneda: String = "PEN",
    @SerializedName("unidad_precio") val unidadPrecio: String = "por_servicio",
    @SerializedName("ciudad") val ciudad: String = "Puno",
    @SerializedName("activo") val activo: Boolean = true,
    @SerializedName("calificacion_promedio") val calificacion: Double = 0.0,
    @SerializedName("total_resenas") val totalResenas: Int = 0,
    @SerializedName("categoria_nombre") val categoriaNombre: String? = null,
    @SerializedName("reservas_activas") val reservasActivas: Int = 0
) {
    val simbolo: String get() = if (moneda == "PEN") "S/" else moneda
    val precioFormateado: String get() = "$simbolo %.2f".format(precio)
}


/**
 * GET /api/reservas/recibidas — una reserva vista desde el lado del
 * prestador: quién reservó y cómo contactarle.
 */
data class ReservaRecibidaResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("codigo") val codigo: String,
    @SerializedName("fecha_inicio") val fechaInicio: String,
    @SerializedName("fecha_fin") val fechaFin: String? = null,
    @SerializedName("num_personas") val numPersonas: Int = 1,
    @SerializedName("subtotal") val subtotal: Double = 0.0,
    @SerializedName("moneda") val moneda: String = "PEN",
    @SerializedName("estado") val estado: String = "pendiente",
    @SerializedName("servicio_titulo") val servicioTitulo: String? = null,
    @SerializedName("turista_nombre") val turistaNombre: String? = null,
    @SerializedName("turista_telefono") val turistaTelefono: String? = null
) {
    val simbolo: String get() = if (moneda == "PEN") "S/" else moneda
    val subtotalFormateado: String get() = "$simbolo %.2f".format(subtotal)

    val estadoLegible: String
        get() = when (estado) {
            "pendiente" -> "Pendiente"
            "confirmada" -> "Confirmada"
            "completada" -> "Completada"
            "cancelada" -> "Cancelada"
            else -> estado
        }
}
