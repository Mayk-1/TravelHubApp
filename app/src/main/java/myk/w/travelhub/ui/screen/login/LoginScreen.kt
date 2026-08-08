package myk.w.travelhub.ui.screen.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import myk.w.travelhub.ui.common.LoadingDialog
import myk.w.travelhub.ui.common.PreviewPantallas
import myk.w.travelhub.ui.theme.TravelHubTheme

@Composable
fun LoginScreen(
    onLoginExitoso: () -> Unit = {},
    viewModel: LoginViewModel = viewModel()
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()

    // Efecto secundario: cuando el login sale bien, navegar UNA sola vez.
    LaunchedEffect(estado) {
        if (estado is LoginUiState.Exito) {
            viewModel.consumirEstado()
            onLoginExitoso()
        }
    }

    LoginContenido(
        estado = estado,
        form = form,
        onNombreChange = viewModel::onNombreChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onRolChange = viewModel::onRolChange,
        onAlternarModo = viewModel::alternarModo,
        onEnviar = viewModel::enviar
    )
}


@Composable
fun LoginContenido(
    estado: LoginUiState,
    form: LoginFormState,
    onNombreChange: (String) -> Unit = {},
    onEmailChange: (String) -> Unit = {},
    onPasswordChange: (String) -> Unit = {},
    onRolChange: (String) -> Unit = {},
    onAlternarModo: () -> Unit = {},
    onEnviar: () -> Unit = {}
) {
    var passwordVisible by remember { mutableStateOf(false) }

    LoadingDialog(
        isLoading = estado is LoginUiState.Cargando,
        mensaje = if (form.modoRegistro) "Creando tu cuenta..." else "Ingresando..."
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "TravelHub",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Arma tu viaje sin intermediarios",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        Text(
            text = if (form.modoRegistro) "Crea tu cuenta" else "Ingresa a tu cuenta",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(16.dp))

        if (form.modoRegistro) {
            OutlinedTextField(
                value = form.nombre,
                onValueChange = onNombreChange,
                label = { Text("Nombre completo") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = form.email,
            onValueChange = onEmailChange,
            label = { Text("Correo electrónico") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = form.password,
            onValueChange = onPasswordChange,
            label = { Text("Contraseña") },
            supportingText = { Text("Mínimo 8 carácteres") },
            singleLine = true,
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) {
                            Icons.Filled.VisibilityOff
                        } else {
                            Icons.Filled.Visibility
                        },
                        contentDescription = if (passwordVisible) {
                            "Ocultar contraseña"
                        } else {
                            "Mostrar contraseña"
                        }
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (form.modoRegistro) {
            Spacer(Modifier.height(16.dp))
            Text("Me registro como:", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = form.rol == "turista",
                    onClick = { onRolChange("turista") },
                    label = { Text("Turista") }
                )
                FilterChip(
                    selected = form.rol == "prestador",
                    onClick = { onRolChange("prestador") },
                    label = { Text("Prestador") }
                )
            }
        }

        (estado as? LoginUiState.Error)?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                text = it.mensaje,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onEnviar,
            enabled = form.esValido && estado !is LoginUiState.Cargando,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(if (form.modoRegistro) "Crear cuenta" else "Ingresar")
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (form.modoRegistro) "Ya tienes cuenta?" else "No tienes cuenta?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onAlternarModo) {
                Text(if (form.modoRegistro) "Inicia sesion" else "Registrate")
            }
        }
    }
}

@Composable
private fun Envoltorio(contenido: @Composable () -> Unit) {
    TravelHubTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) { contenido() }
    }
}

@PreviewPantallas
@Composable
private fun LoginVacioPreview() = Envoltorio {
    LoginContenido(
        estado = LoginUiState.Inicial,
        form = LoginFormState()
    )
}

@PreviewPantallas
@Composable
private fun LoginRellenoPreview() = Envoltorio {
    LoginContenido(
        estado = LoginUiState.Inicial,
        form = LoginFormState(email = "camila@example.com", password = "travelhub2026")
    )
}

@PreviewPantallas
@Composable
private fun RegistroTuristaPreview() = Envoltorio {
    LoginContenido(
        estado = LoginUiState.Inicial,
        form = LoginFormState(
            nombre = "Camila Rojas",
            email = "camila@example.com",
            password = "travelhub2026",
            modoRegistro = true
        )
    )
}

@PreviewPantallas
@Composable
private fun RegistroPrestadorPreview() = Envoltorio {
    LoginContenido(
        estado = LoginUiState.Inicial,
        form = LoginFormState(
            nombre = "Julio Mamani",
            email = "julio.guia@example.com",
            password = "travelhub2026",
            rol = "prestador",
            modoRegistro = true
        )
    )
}

@PreviewPantallas
@Composable
private fun LoginErrorPreview() = Envoltorio {
    LoginContenido(
        estado = LoginUiState.Error("Correo o contraseña incorrectos"),
        form = LoginFormState(email = "camila@example.com", password = "clavemala")
    )
}

@PreviewPantallas
@Composable
private fun LoginSinConexionPreview() = Envoltorio {
    LoginContenido(
        estado = LoginUiState.Error("No se pudo conectar con el servidor. Revisa tu conexion."),
        form = LoginFormState(email = "camila@example.com", password = "travelhub2026")
    )
}

@PreviewPantallas
@Composable
private fun LoginCargandoPreview() = Envoltorio {
    LoginContenido(
        estado = LoginUiState.Cargando,
        form = LoginFormState(email = "camila@example.com", password = "travelhub2026")
    )
}