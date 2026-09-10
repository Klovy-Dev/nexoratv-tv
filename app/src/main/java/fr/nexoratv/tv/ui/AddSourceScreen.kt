package fr.nexoratv.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import fr.nexoratv.tv.AppViewModel

@Composable
fun AddSourceScreen(vm: AppViewModel) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Ajouter votre abonnement", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Compte Xtream Codes",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f),
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
        )

        val fieldMod = Modifier.fillMaxWidth().widthIn(max = 520.dp).padding(vertical = 6.dp)

        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { M3Text("Nom (facultatif)") }, singleLine = true, modifier = fieldMod,
        )
        OutlinedTextField(
            value = host, onValueChange = { host = it },
            label = { M3Text("Adresse du serveur") },
            placeholder = { M3Text("http://exemple.com:8080") },
            singleLine = true, modifier = fieldMod,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedTextField(
            value = user, onValueChange = { user = it },
            label = { M3Text("Identifiant") }, singleLine = true, modifier = fieldMod,
        )
        OutlinedTextField(
            value = pass, onValueChange = { pass = it },
            label = { M3Text("Mot de passe") }, singleLine = true, modifier = fieldMod,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )

        error?.let {
            Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }

        Button(
            onClick = {
                if (busy) return@Button
                error = null
                busy = true
                vm.addXtream(name, host, user, pass) { ok, err ->
                    busy = false
                    if (!ok) error = err
                }
            },
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text(if (busy) "Vérification…" else "Vérifier et ajouter")
        }
    }
}
