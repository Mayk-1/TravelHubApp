package myk.w.travelhub.ui.common

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "1 Claro", showBackground = true)
@Preview(
    name = "2 Oscuro",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
annotation class PreviewClaroOscuro

@Preview(name = "1 Claro", showBackground = true)
@Preview(
    name = "2 Oscuro",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Preview(name = "3 Pantalla pequena", showBackground = true, widthDp = 320, heightDp = 600)
annotation class PreviewPantallas
