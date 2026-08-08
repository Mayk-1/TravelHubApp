package myk.w.travelhub.ui.common

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * Anotacion "multipreview": al ponerla sobre un composable, Android Studio
 * genera DOS vistas previas (clara y oscura) en vez de una.
 *
 * Evita tener que duplicar cada funcion de preview solo para cambiar el tema.
 */
@Preview(name = "1 Claro", showBackground = true)
@Preview(
    name = "2 Oscuro",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
annotation class PreviewClaroOscuro

/**
 * Igual, pero ademas en un telefono pequeno. Util para detectar textos que
 * se desbordan en pantallas de gama baja, que son las que mas se usan.
 */
@Preview(name = "1 Claro", showBackground = true)
@Preview(
    name = "2 Oscuro",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Preview(name = "3 Pantalla pequena", showBackground = true, widthDp = 320, heightDp = 600)
annotation class PreviewPantallas
