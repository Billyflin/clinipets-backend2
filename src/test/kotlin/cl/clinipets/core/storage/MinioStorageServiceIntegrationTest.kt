package cl.clinipets.core.storage

import cl.clinipets.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.TestPropertySource
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.stream.Collectors

@TestPropertySource(properties = ["storage.type=minio"])
class MinioStorageServiceIntegrationTest : AbstractIntegrationTest() {

    @Autowired(required = false)
    private var storageService: StorageService? = null

    @Test
    fun `should upload and retrieve file from minio`() {
        assertNotNull(storageService, "StorageService should be initialized when storage.type=minio")
        assertTrue(storageService is MinioStorageService)

        val content = "Hello Minio World"
        val file = MockMultipartFile("testfile", "test.txt", "text/plain", content.toByteArray())

        val objectName = storageService!!.uploadFile(file, "test-folder")
        assertNotNull(objectName)
        assertTrue(objectName.startsWith("test-folder/"))

        val inputStream = storageService!!.getFile(objectName)
        val result = BufferedReader(InputStreamReader(inputStream)).lines().collect(Collectors.joining("\n"))

        assertEquals(content, result)

        storageService!!.deleteFile(objectName)
    }
}
