package fr.nexoratv.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text as M3Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import fr.nexoratv.tv.AppViewModel
import fr.nexoratv.tv.ui.theme.NexoraBackdrop
import fr.nexoratv.tv.ui.theme.NexoraGradient

@Composable
fun AddSourceScreen(vm: AppViewModel) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(NexoraBackdrop), Alignment.Center) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp).padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "NexoraTV",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    style = TextStyle(brush = NexoraGradient),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Connectez votre abonnement Xtream Codes",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(28.dp))

                val fieldMod = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                val fieldColors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                )

                OutlinedTextField(name, { name = it }, fieldMod, singleLine = true,
                    label = { M3Text("Nom (facultatif)") }, colors = fieldColors)
                OutlinedTextField(host, { host = it }, fieldMod, singleLine = true,
                    label = { M3Text("Adresse du serveur") },
                    placeholder = { M3Text("http://exemple.com:8080") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = fieldColors)
                OutlinedTextField(user, { user = it }, fieldMod, singleLine = true,
                    label = { M3Text("Identifiant") }, colors = fieldColors)
                OutlinedTextField(pass, { pass = it }, fieldMod, singleLine = true,
                    label = { M3Text("Mot de passe") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors = fieldColors)

                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp)
                }

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (busy) return@Button
                        error = null; busy = true
                        vm.addXtream(name, host, user, pass) { ok, err ->
                            busy = false
                            if (!ok) error = err
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (busy) "Vérification…" else "Vérifier et ajouter") }
            }
        }
    }
}
