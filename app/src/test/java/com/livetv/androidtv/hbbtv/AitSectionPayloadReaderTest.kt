package com.livetv.androidtv.hbbtv

import androidx.media3.common.util.ParsableByteArray
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*

/**
 * Unit tests for AitSectionPayloadReader
 * Tests various AIT parsing scenarios including valid HTTP apps, DSM-CC only, and CRC errors
 */
class AitSectionPayloadReaderTest {

    @Test
    fun testValidHttpApplication() {
        // Create mock listener
        val mockListener = mock(AitListener::class.java)
        val reader = AitSectionPayloadReader(mockListener)

        // Create test AIT section with HTTP application
        val testSection = createTestAitSection(
            hasHttpApp = true,
            isAutostart = true,
            urlBase = "https://example.com",
            urlExtension = "/hbbtv",
            initialPath = "index.html"
        )

        // Process section
        reader.consume(ParsableByteArray(testSection))

        // Verify listener was called with correct URL
        verify(mockListener).onHbbTvUrlFound(
            argThat { url ->
                url.url == "https://example.com/hbbtv/index.html" &&
                url.autostart == true &&
                url.appId == 0x12345678 &&
                url.orgId == 0x87654321
            }
        )
    }

    @Test
    fun testDsmCcOnlyApplication() {
        // Create mock listener
        val mockListener = mock(AitListener::class.java)
        val reader = AitSectionPayloadReader(mockListener)

        // Create test AIT section with only DSM-CC application
        val testSection = createTestAitSection(
            hasHttpApp = false,
            hasDsmCcApp = true
        )

        // Process section
        reader.consume(ParsableByteArray(testSection))

        // Verify listener was called with "no HTTP" reason
        verify(mockListener).onAitPresentButNoUrl("No HTTP transport (only DSM-CC)")
    }

    @Test
    fun testNoApplications() {
        // Create mock listener
        val mockListener = mock(AitListener::class.java)
        val reader = AitSectionPayloadReader(mockListener)

        // Create test AIT section with no applications
        val testSection = createTestAitSection(
            hasHttpApp = false,
            hasDsmCcApp = false
        )

        // Process section
        reader.consume(ParsableByteArray(testSection))

        // Verify listener was called with "no applications" reason
        verify(mockListener).onAitPresentButNoUrl("No applications found in AIT")
    }

    @Test
    fun testCrcError() {
        // Create mock listener
        val mockListener = mock(AitListener::class.java)
        val reader = AitSectionPayloadReader(mockListener)

        // Create test AIT section with invalid CRC
        val testSection = createTestAitSection(
            hasHttpApp = true,
            invalidCrc = true
        )

        // Process section
        reader.consume(ParsableByteArray(testSection))

        // Verify listener was NOT called (CRC error should prevent processing)
        verify(mockListener, never()).onHbbTvUrlFound(any())
        verify(mockListener, never()).onAitPresentButNoUrl(any())
    }

    @Test
    fun testMultipleApplications() {
        // Create mock listener
        val mockListener = mock(AitListener::class.java)
        val reader = AitSectionPayloadReader(mockListener)

        // Create test AIT section with multiple applications (one autostart, one not)
        val testSection = createTestAitSectionWithMultipleApps()

        // Process section
        reader.consume(ParsableByteArray(testSection))

        // Verify autostart application was selected
        verify(mockListener).onHbbTvUrlFound(
            argThat { url ->
                url.url == "https://autostart.example.com/app" &&
                url.autostart == true
            }
        )
    }

    /**
     * Create a test AIT section with specified parameters
     */
    private fun createTestAitSection(
        hasHttpApp: Boolean = true,
        hasDsmCcApp: Boolean = false,
        isAutostart: Boolean = false,
        urlBase: String? = null,
        urlExtension: String? = null,
        initialPath: String? = null,
        invalidCrc: Boolean = false
    ): ByteArray {
        val section = mutableListOf<Byte>()
        
        // Table ID (0x74 for AIT)
        section.add(0x74)
        
        // Section syntax indicator (1) + reserved (3) + section length (will be calculated)
        section.add(0x80.toByte())
        section.add(0x00) // Placeholder for section length
        section.add(0x00) // Placeholder for section length
        
        // Reserved (2) + version (3) + current/next (1) + section number (8) + last section number (8)
        section.add(0x00)
        section.add(0x00)
        section.add(0x00)
        section.add(0x00)
        section.add(0x00)
        
        // Common descriptor loop length (0 for this test)
        section.add(0x00)
        section.add(0x00)
        
        // Application loop length (will be calculated)
        section.add(0x00) // Placeholder
        section.add(0x00) // Placeholder
        
        if (hasHttpApp) {
            // Add HTTP application
            section.addAll(createHttpApplication(
                orgId = 0x87654321,
                appId = 0x12345678,
                isAutostart = isAutostart,
                urlBase = urlBase ?: "https://example.com",
                urlExtension = urlExtension ?: "",
                initialPath = initialPath ?: ""
            ))
        }
        
        if (hasDsmCcApp) {
            // Add DSM-CC application
            section.addAll(createDsmCcApplication(
                orgId = 0x11111111,
                appId = 0x22222222
            ))
        }
        
        // Calculate and set section length
        val sectionLength = section.size - 3 // Exclude table_id, syntax_indicator, and length fields
        section[2] = ((sectionLength shr 8) and 0x0F).toByte()
        section[3] = (sectionLength and 0xFF).toByte()
        
        // Calculate and set application loop length
        val appLoopLength = section.size - 11 // From after app loop length field
        section[9] = ((appLoopLength shr 8) and 0x0F).toByte()
        section[10] = (appLoopLength and 0xFF).toByte()
        
        // Add CRC32 (valid or invalid)
        val crc = if (invalidCrc) 0xDEADBEEF else calculateCrc32(section.toByteArray())
        section.add((crc shr 24).toByte())
        section.add((crc shr 16).toByte())
        section.add((crc shr 8).toByte())
        section.add(crc.toByte())
        
        return section.toByteArray()
    }

    /**
     * Create an HTTP application with specified parameters
     */
    private fun createHttpApplication(
        orgId: Int,
        appId: Int,
        isAutostart: Boolean,
        urlBase: String,
        urlExtension: String,
        initialPath: String
    ): List<Byte> {
        val app = mutableListOf<Byte>()
        
        // Application identifier (org_id + app_id)
        app.add((orgId shr 24).toByte())
        app.add((orgId shr 16).toByte())
        app.add((orgId shr 8).toByte())
        app.add(orgId.toByte())
        app.add((appId shr 24).toByte())
        app.add((appId shr 16).toByte())
        app.add((appId shr 8).toByte())
        app.add(appId.toByte())
        
        // Application control code
        val controlCode = if (isAutostart) 0x01 else 0x02
        app.add(controlCode.toByte())
        
        // Application descriptors loop length (will be calculated)
        app.add(0x00) // Placeholder
        app.add(0x00) // Placeholder
        
        val descriptorsStart = app.size
        
        // Application descriptor (0x00)
        app.add(0x00) // Descriptor tag
        app.add(0x01) // Descriptor length
        app.add(controlCode.toByte()) // Application control
        
        // Transport protocol descriptor (0x02) for HTTP
        app.add(0x02) // Descriptor tag
        val transportDescLength = 3 + 2 + urlBase.length + 1 + urlExtension.length + 1 + initialPath.length
        app.add(transportDescLength.toByte()) // Descriptor length
        
        app.add(0x00) // Reserved
        app.add(0x03) // Protocol ID (HTTP)
        app.add(0x00) // Transport protocol label
        
        app.add(0x00) // Reserved
        app.add(urlBase.length.toByte()) // URL base length
        app.addAll(urlBase.map { it.toByte() })
        
        app.add(1) // URL extension length
        app.addAll(urlExtension.map { it.toByte() })
        
        app.add(1) // Initial path length
        app.addAll(initialPath.map { it.toByte() })
        
        // Calculate and set application descriptors loop length
        val descriptorsLength = app.size - descriptorsStart
        app[descriptorsStart - 2] = ((descriptorsLength shr 8) and 0x0F).toByte()
        app[descriptorsStart - 1] = (descriptorsLength and 0xFF).toByte()
        
        return app
    }

    /**
     * Create a DSM-CC application
     */
    private fun createDsmCcApplication(orgId: Int, appId: Int): List<Byte> {
        val app = mutableListOf<Byte>()
        
        // Application identifier
        app.add((orgId shr 24).toByte())
        app.add((orgId shr 16).toByte())
        app.add((orgId shr 8).toByte())
        app.add(orgId.toByte())
        app.add((appId shr 24).toByte())
        app.add((appId shr 16).toByte())
        app.add((appId shr 8).toByte())
        app.add(appId.toByte())
        
        // Application control code
        app.add(0x02.toByte()) // Present
        
        // Application descriptors loop length
        app.add(0x00) // Placeholder
        app.add(0x00) // Placeholder
        
        val descriptorsStart = app.size
        
        // Transport protocol descriptor (0x02) for DSM-CC
        app.add(0x02) // Descriptor tag
        app.add(0x03) // Descriptor length
        app.add(0x00) // Reserved
        app.add(0x01) // Protocol ID (DSM-CC)
        app.add(0x00) // Transport protocol label
        
        // Calculate and set application descriptors loop length
        val descriptorsLength = app.size - descriptorsStart
        app[descriptorsStart - 2] = ((descriptorsLength shr 8) and 0x0F).toByte()
        app[descriptorsStart - 1] = (descriptorsLength and 0xFF).toByte()
        
        return app
    }

    /**
     * Create test AIT section with multiple applications
     */
    private fun createTestAitSectionWithMultipleApps(): ByteArray {
        val section = mutableListOf<Byte>()
        
        // Table ID
        section.add(0x74)
        
        // Section syntax indicator + length
        section.add(0x80.toByte())
        section.add(0x00) // Placeholder
        section.add(0x00) // Placeholder
        
        // Reserved + version + current/next + section numbers
        section.addAll(List(5) { 0x00 })
        
        // Common descriptor loop length
        section.addAll(List(2) { 0x00 })
        
        // Application loop length
        section.add(0x00) // Placeholder
        section.add(0x00) // Placeholder
        
        // Add autostart HTTP application
        section.addAll(createHttpApplication(
            orgId = 0xAAAAAAAA,
            appId = 0xBBBBBBBB,
            isAutostart = true,
            urlBase = "https://autostart.example.com",
            urlExtension = "",
            initialPath = "app"
        ))
        
        // Add non-autostart HTTP application
        section.addAll(createHttpApplication(
            orgId = 0xCCCCCCCC,
            appId = 0xDDDDDDDD,
            isAutostart = false,
            urlBase = "https://normal.example.com",
            urlExtension = "",
            initialPath = "app"
        ))
        
        // Calculate lengths
        val sectionLength = section.size - 3
        section[2] = ((sectionLength shr 8) and 0x0F).toByte()
        section[3] = (sectionLength and 0xFF).toByte()
        
        val appLoopLength = section.size - 11
        section[9] = ((appLoopLength shr 8) and 0x0F).toByte()
        section[10] = (appLoopLength and 0xFF).toByte()
        
        // Add CRC32
        val crc = calculateCrc32(section.toByteArray())
        section.add((crc shr 24).toByte())
        section.add((crc shr 16).toByte())
        section.add((crc shr 8).toByte())
        section.add(crc.toByte())
        
        return section.toByteArray()
    }

    /**
     * Calculate CRC32 for section validation
     */
    private fun calculateCrc32(data: ByteArray): Int {
        val crc = java.util.zip.CRC32()
        crc.update(data)
        return crc.value.toInt()
    }
}
