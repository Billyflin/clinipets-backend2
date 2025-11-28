package cl.clinipets.backend.nucleo.auditoria

import cl.clinipets.backend.identidad.dominio.Rol
import cl.clinipets.backend.identidad.dominio.Usuario
import cl.clinipets.backend.mascotas.dominio.Mascota
import cl.clinipets.backend.nucleo.api.AuditableEntity
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuditableEntityInheritanceTest {

    @Test
    fun `todas las entidades de tablas heredan de AuditableEntity`() {
        val entidadesDeTablas = listOf(
            Usuario::class.java,
            Rol::class.java,
            Mascota::class.java
        )

        entidadesDeTablas.forEach { clazz ->
            assertTrue(
                AuditableEntity::class.java.isAssignableFrom(clazz),
                "${'$'}{clazz.simpleName} debe heredar de AuditableEntity"
            )
        }
    }
}

