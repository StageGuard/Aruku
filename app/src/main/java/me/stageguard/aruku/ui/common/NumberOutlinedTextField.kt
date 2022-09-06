package me.stageguard.aruku.ui.common

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Created by LoliBall on 2022/9/6 18:27.
 * https://github.com/WhichWho
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@SuppressLint("ModifierParameter")
fun NumberOutlinedTextField(
    value: Number,
    label: String = "",
    modifier: Modifier = Modifier
        .padding(vertical = 4.dp)
        .fillMaxWidth(),
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value.toString(),
        label = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(15.dp),
        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
        onValueChange = onValueChange,
        enabled = enabled
    )
}