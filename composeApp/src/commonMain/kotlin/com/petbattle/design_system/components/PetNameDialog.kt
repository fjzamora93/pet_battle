package com.petbattle.design_system.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.petbattle.shared_features.biomes.domain.Biome
import com.petbattle.shared_features.biomes.presentation.label
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import com.petbattle.shared_features.pet_status.domain.GameRules
import com.petbattle.shared_features.pet_status.domain.PetNameError

/**
 * Pide el nombre de un polluelo nuevo en [biome]. Una mascota no se puede guardar sin él, así
 * que el diálogo solo se cierra con un nombre válido (o al cancelar). [submit] intenta
 * criarlo y devuelve el motivo del fallo, o `null` si tiene éxito.
 */
@Composable
fun PetNameDialog(biome: Biome, price: Int, submit: (String) -> PetNameError?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<PetNameError?>(null) }
    fun trySubmit() {
        val e = submit(name)
        if (e == null) onDismiss() else error = e
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo polluelo en ${biome.label.lowercase()}") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BirdFrame(PetSpecies.chick, 96.dp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        if (it.length <= GameRules.MAX_NAME_LENGTH) name = it
                        error = null
                    },
                    label = { Text("Nombre") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { Text(error?.message ?: "${name.length}/${GameRules.MAX_NAME_LENGTH}") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { trySubmit() }),
                )
                Text("Lo que le des de comer decidirá en qué pájaro se convierte.", style = MaterialTheme.typography.bodySmall)
                Text(
                    if (price == 0) "Este huevo es gratis." else "El huevo cuesta $price de oro.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        confirmButton = { Button(onClick = ::trySubmit) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

val PetNameError.message get() = when (this) {
    PetNameError.empty -> "Ponle un nombre para poder guardarla"
    PetNameError.tooLong -> "Máximo ${GameRules.MAX_NAME_LENGTH} caracteres"
    PetNameError.taken -> "Ya tienes una mascota con ese nombre"
}
