package cl.clinipets.ui.features.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.clinipets.data.model.Mascota
import cl.clinipets.data.model.ServicioMedicoDto
import cl.clinipets.data.repository.BookingRepository
import cl.clinipets.data.repository.PetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookingUiState(
    val isLoading: Boolean = false,
    val pets: List<Mascota> = emptyList(),
    val selectedPet: Mascota? = null,
    val availableServices: List<ServicioMedicoDto> = emptyList(),
    val cart: List<ServicioMedicoDto> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class BookingViewModel @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val petRepository: PetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookingUiState())
    val uiState: StateFlow<BookingUiState> = _uiState.asStateFlow()

    // Raw data source
    private var _allServices: List<ServicioMedicoDto> = emptyList()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val pets = petRepository.getMyPets()
                _allServices = bookingRepository.getServices() // Fetch raw services

                _uiState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        pets = pets,
                        selectedPet = pets.firstOrNull() // Auto-select first pet
                    )
                }
                // Trigger calculation after loading data and setting initial pet
                recalculateAvailableServices()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun onPetSelected(pet: Mascota) {
        _uiState.update { it.copy(selectedPet = pet) }
        recalculateAvailableServices()
    }

    /**
     * Core Logic: Filters services based on Inventory and Species compatibility.
     */
    private fun recalculateAvailableServices() {
        val currentPet = _uiState.value.selectedPet
        
        val filteredList = _allServices.filter { service ->
            // 1. Species Check: If allowed species set is not empty, pet's species must be in it.
            val isSpeciesCompatible = if (service.especiesPermitidas.isNotEmpty()) {
                currentPet != null && service.especiesPermitidas.contains(currentPet.especie)
            } else {
                true // Service available for all (e.g. generic product)
            }

            // 2. Stock Check: Must be null (infinite) or > 0
            val hasStock = service.stock == null || service.stock!! > 0

            // Include only if both pass
            isSpeciesCompatible && hasStock
        }

        _uiState.update { it.copy(availableServices = filteredList) }
    }

    fun addToCart(service: ServicioMedicoDto) {
        // Double check stock before adding (Defensive Programming)
        if (service.stock != null && service.stock!! <= 0) {
            _uiState.update { it.copy(error = "Lo sentimos, este servicio ya no tiene stock.") }
            return
        }

        _uiState.update {
            it.copy(cart = it.cart + service)
        }
    }

    fun removeFromCart(service: ServicioMedicoDto) {
        _uiState.update {
            it.copy(cart = it.cart - service)
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
