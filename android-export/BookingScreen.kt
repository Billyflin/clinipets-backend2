package cl.clinipets.ui.features.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cl.clinipets.data.model.ServicioMedicoDto
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingScreen(
    viewModel: BookingViewModel = hiltViewModel(),
    onBookingSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Error Handling Side Effect
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text("Nueva Reserva") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            // 1. Pet Selector
            Text("Mascota", style = MaterialTheme.typography.labelLarge)
            PetSelector(
                pets = uiState.pets,
                selectedPet = uiState.selectedPet,
                onSelect = { viewModel.onPetSelected(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Service Selector (Reactive Inventory)
            Text("Agregar Servicio / Producto", style = MaterialTheme.typography.labelLarge)
            ServiceDropdown(
                availableServices = uiState.availableServices,
                onServiceSelected = { viewModel.addToCart(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Cart List
            Text("Carrito", style = MaterialTheme.typography.titleMedium)
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(uiState.cart) { item ->
                    CartItemCard(
                        item = item,
                        onRemove = { viewModel.removeFromCart(item) }
                    )
                }
            }

            // 4. Confirm Button
            Button(
                onClick = { /* Implement confirm logic via ViewModel -> Repository */ },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.cart.isNotEmpty()
            ) {
                Text("Confirmar Reserva")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceDropdown(
    availableServices: List<ServicioMedicoDto>,
    onServiceSelected: (ServicioMedicoDto) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedText by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedText,
                onValueChange = {},
                readOnly = true,
                placeholder = { Text("Selecciona un servicio...") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                if (availableServices.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No hay servicios disponibles para esta mascota") },
                        onClick = { }
                    )
                }

                availableServices.forEach { service ->
                    // Logic for Stock Display
                    val stockInfo = if (service.stock != null) {
                        " - Quedan: ${service.stock}"
                    } else {
                        ""
                    }
                    
                    // Logic for Low Stock Warning Color
                    val isLowStock = service.stock != null && service.stock!! < 5
                    val textColor = if (isLowStock) Color(0xFFFF9800) else Color.Unspecified // Orange for alert

                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${service.nombre} (${formatPrice(service.precioBase)})$stockInfo",
                                    color = textColor
                                )
                                if (isLowStock) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Poco stock",
                                        tint = Color(0xFFFF9800),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            onServiceSelected(service)
                            expanded = false
                            // We don't update selectedText to keep it acting as an "Add" button field
                            // or update it to "Added!" briefly. Keeping simple for now.
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CartItemCard(
    item: ServicioMedicoDto,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.nombre, fontWeight = FontWeight.Bold)
                Text(formatPrice(item.precioBase), style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// Utility
fun formatPrice(amount: Int): String {
    return NumberFormat.getCurrencyInstance(Locale("es", "CL")).format(amount)
}

// Placeholder for PetSelector since it wasn't the focus
@Composable
fun PetSelector(
    pets: List<cl.clinipets.data.model.Mascota>,
    selectedPet: cl.clinipets.data.model.Mascota?,
    onSelect: (cl.clinipets.data.model.Mascota) -> Unit
) {
    // Simple Row implementation
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        pets.forEach { pet ->
            FilterChip(
                selected = pet == selectedPet,
                onClick = { onSelect(pet) },
                label = { Text(pet.nombre) },
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}
