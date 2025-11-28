package cl.clinipets.backend.nucleo.auditoria

import cl.clinipets.backend.identidad.dominio.Usuario
import cl.clinipets.backend.identidad.infraestructura.UsuarioRepository
import cl.clinipets.backend.mascotas.dominio.Especie
import cl.clinipets.backend.mascotas.dominio.Mascota
import cl.clinipets.backend.mascotas.infraestructura.MascotaRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.domain.AuditorAware
import org.springframework.test.context.ActiveProfiles
import java.util.*

@DataJpaTest
@ActiveProfiles("test")
@Import(AuditoriaConfig::class)
class AuditableEntityJpaTest @Autowired constructor(
    private val usuarioRepository: UsuarioRepository,
    private val mascotaRepository: MascotaRepository
) {
    companion object {
        val FIXED_AUDITOR: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }

    @TestConfiguration
    class AuditorStubConfig {
        @Bean(name = ["auditorAware"]) // coincide con auditorAwareRef
        fun auditorAware(): AuditorAware<UUID> = AuditorAware { Optional.of(FIXED_AUDITOR) }
    }

    @Test
    fun `auditoria se establece al crear y actualizar Usuario`() {
        val u = Usuario(email = "tester@example.com")
        val saved = usuarioRepository.saveAndFlush(u)

        assertNotNull(saved.creadoEn, "creadoEn debe ser asignado")
        assertNotNull(saved.modificadoEn, "modificadoEn debe ser asignado")
        assertEquals(FIXED_AUDITOR, saved.creadoPor, "creadoPor debe ser el auditor fijo")
        assertEquals(FIXED_AUDITOR, saved.modificadoPor, "modificadoPor debe ser el auditor fijo")

        val originalCreadoEn = saved.creadoEn
        val originalModificadoEn = saved.modificadoEn

        saved.nombre = "Nuevo Nombre"
        val updated = usuarioRepository.saveAndFlush(saved)

        assertEquals(originalCreadoEn, updated.creadoEn, "creadoEn no debe cambiar en update")
        assertTrue(updated.modificadoEn >= originalModificadoEn, "modificadoEn debe avanzar o mantenerse")
        assertEquals(FIXED_AUDITOR, updated.modificadoPor, "modificadoPor debe mantenerse como auditor fijo")
    }

    @Test
    fun `auditoria se establece al crear Mascota`() {
        val tutor = usuarioRepository.saveAndFlush(Usuario(email = "tutor@example.com"))
        val m = Mascota(
            nombre = "Firulais",
            especie = Especie.PERRO,
            tutor = tutor
        )
        val saved = mascotaRepository.saveAndFlush(m)

        assertNotNull(saved.creadoEn, "creadoEn debe ser asignado")
        assertNotNull(saved.modificadoEn, "modificadoEn debe ser asignado")
        assertEquals(FIXED_AUDITOR, saved.creadoPor, "creadoPor debe ser el auditor fijo")
        assertEquals(FIXED_AUDITOR, saved.modificadoPor, "modificadoPor debe ser el auditor fijo")
    }
}
