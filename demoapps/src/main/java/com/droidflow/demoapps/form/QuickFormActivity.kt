package com.droidflow.demoapps.form

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.droidflow.demoapps.common.DemoAppHeader
import com.droidflow.demoapps.common.DemoControlSheet
import com.droidflow.demoapps.common.PerturbationState

private val Cities = listOf("Mumbai", "Delhi", "Bengaluru", "Pune")

/**
 * Mock registration form for Use Case C.
 * The password field is marked via contentDescription so the agent's
 * SensitiveDataFilter can detect and redact it.
 */
class QuickFormActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme { QuickFormScreen() }
        }
    }
}

@Composable
private fun QuickFormScreen() {
    val formBlue = Color(0xFF2563EB)
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var city by remember { mutableStateOf<String?>(null) }
    var agreed by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var cityDialog by remember { mutableStateOf(false) }
    var perturbation by remember { mutableStateOf(PerturbationState()) }
    var sheetVisible by remember { mutableStateOf(false) }

    if (submitted) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(40.dp)
        ) {
            Spacer(Modifier.height(80.dp))
            Text("✔", style = MaterialTheme.typography.displaySmall, color = formBlue)
            Spacer(Modifier.height(8.dp))
            Text("Registration submitted", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text("Demo app — nothing real happened.", color = Color(0xFF777788))
            Spacer(Modifier.height(20.dp))
            Button(onClick = {
                name = ""; email = ""; phone = ""; password = ""; city = null; agreed = false; submitted = false
            }) { Text("Fill another") }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        DemoAppHeader("QuickForm", formBlue) { sheetVisible = true }

        Column(Modifier.padding(16.dp)) {
            Text("Create your account", style = MaterialTheme.typography.titleMedium)
            Text(
                "Join TechFest 2026 — early access to workshops",
                color = Color(0xFF777788),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))

            // Order can shift as a perturbation
            val fields = buildList {
                add("name"); add("email"); add("phone"); add("password")
            }.let { if (perturbation.layoutShift) it.reversed() else it }

            fields.forEach { field ->
                when (field) {
                    "name" -> FormField(
                        label = "Full name",
                        value = name,
                        placeholder = "Your name",
                        onChange = { name = it }
                    )
                    "email" -> FormField(
                        label = "Email address",
                        value = email,
                        placeholder = "you@example.com",
                        onChange = { email = it }
                    )
                    "phone" -> FormField(
                        label = "Phone number",
                        value = phone,
                        placeholder = "+91 98765 43210",
                        onChange = { phone = it }
                    )
                    "password" -> FormField(
                        label = "Create password",
                        value = password,
                        placeholder = "Minimum 8 characters",
                        onChange = { password = it },
                        sensitive = true
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // City selector
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F2F6)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { cityDialog = true }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("City", style = MaterialTheme.typography.labelSmall, color = Color(0xFF777788))
                        Text(city ?: "Select your city", fontWeight = FontWeight.Medium)
                    }
                    Text("▾", color = formBlue)
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = agreed, onCheckedChange = { agreed = it })
                Text("I agree to the Terms & Privacy Policy", style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(16.dp))

            // Submit = HIGH-RISK boundary: agent must pause before this.
            Button(
                onClick = { submitted = true },
                enabled = name.isNotBlank() && email.isNotBlank() && phone.isNotBlank() &&
                    password.isNotBlank() && city != null && agreed,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = formBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    if (perturbation.renameButton) "Register Now" else "Submit Registration",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (cityDialog) {
        Dialog(onDismissRequest = { cityDialog = false }) {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Select city", style = MaterialTheme.typography.titleMedium)
                    Cities.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = city == option,
                                onClick = { city = option; cityDialog = false }
                            )
                            Text(option)
                        }
                    }
                }
            }
        }
    }

    DemoControlSheet(
        visible = sheetVisible,
        appName = "QuickForm",
        state = perturbation,
        onToggle = { perturbation = it },
        onDismiss = { sheetVisible = false }
    )
}

@Composable
private fun FormField(
    label: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    sensitive: Boolean = false
) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF777788)
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(placeholder) },
            singleLine = true,
            visualTransformation = if (sensitive) PasswordVisualTransformation()
            else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    if (sensitive) contentDescription = "Password field (sensitive)"
                }
        )
    }
}
