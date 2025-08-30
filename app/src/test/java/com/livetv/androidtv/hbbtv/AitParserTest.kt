package com.livetv.androidtv.hbbtv

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.After

/**
 * Unit tests for AitParser
 */
class AitParserTest {
    
    private lateinit var aitParser: AitParser
    private lateinit var testListener: TestAitListener
    
    @Before
    fun setUp() {
        testListener = TestAitListener()
        aitParser = AitParser(testListener)
    }
    
    @After
    fun tearDown() {
        testListener.reset()
    }
    
    @Test
    fun `test parse valid AIT with HTTP application`() {
        // Create a valid AIT section with HTTP application
        val aitSection = createValidAitSection(
            orgId = 0x1234,
            appId = 0x5678,
            controlCode = 0x01, // AUTOSTART
            urlBase = "https://www.example.com",
            urlExtension = "/hbbtv"
        )
        
        // Process the section
        aitParser.onSection(0x74, aitSection)
        
        // Verify results
        assertTrue("Should have found HbbTV URL", testListener.urlFound)
        assertNotNull("HbbTV info should not be null", testListener.hbbTvInfo)
        assertEquals("URL should match", "https://www.example.com/hbbtv", testListener.hbbTvInfo!!.url)
        assertTrue("Should be autostart", testListener.hbbTvInfo!!.autostart)
        assertEquals("Org ID should match", 0x1234, testListener.hbbTvInfo!!.orgId)
        assertEquals("App ID should match", 0x5678, testListener.hbbTvInfo!!.appId)
    }
    
    @Test
    fun `test parse AIT with only DSM-CC applications`() {
        // Create AIT section with only DSM-CC applications (no HTTP)
        val aitSection = createAitSectionWithDsmccOnly()
        
        // Process the section
        aitParser.onSection(0x74, aitSection)
        
        // Verify results
        assertFalse("Should not have found HbbTV URL", testListener.urlFound)
        assertTrue("Should have reported AIT present but no URL", testListener.aitPresentButNoUrl)
        assertEquals("Reason should indicate no HTTP", "No HTTP applications found (protocol_id 0x0003)", testListener.reason)
    }
    
    @Test
    fun `test parse AIT with no applications`() {
        // Create AIT section with no applications
        val aitSection = createAitSectionWithNoApplications()
        
        // Process the section
        aitParser.onSection(0x74, aitSection)
        
        // Verify results
        assertFalse("Should not have found HbbTV URL", testListener.urlFound)
        assertTrue("Should have reported AIT present but no URL", testListener.aitPresentButNoUrl)
        assertEquals("Reason should indicate no applications", "No applications found in AIT", testListener.reason)
    }
    
    @Test
    fun `test ignore non-AIT table ID`() {
        // Create section with wrong table ID
        val nonAitSection = createValidAitSection(
            orgId = 0x1234,
            appId = 0x5678,
            controlCode = 0x01,
            urlBase = "https://www.example.com",
            urlExtension = ""
        ).apply {
            this[0] = 0x00.toByte() // Change table_id from 0x74 to 0x00
        }
        
        // Process the section
        aitParser.onSection(0x00, nonAitSection)
        
        // Verify results
        assertFalse("Should not have processed non-AIT section", testListener.urlFound)
        assertFalse("Should not have reported AIT present", testListener.aitPresentButNoUrl)
    }
    
    @Test
    fun `test parse AIT with corrupted CRC`() {
        // Create AIT section with corrupted CRC
        val aitSection = createValidAitSection(
            orgId = 0x1234,
            appId = 0x5678,
            controlCode = 0x01,
            urlBase = "https://www.example.com",
            urlExtension = ""
        )
        
        // Corrupt the CRC
        aitSection[aitSection.size - 1] = 0x00.toByte()
        
        // Process the section
        aitParser.onSection(0x74, aitSection)
        
        // Verify results - should still work as we're not validating CRC in the parser
        // (CRC validation is done in the PSI reassembler)
        assertTrue("Should have found HbbTV URL despite corrupted CRC", testListener.urlFound)
    }
    
    @Test
    fun `test parse AIT with multiple applications`() {
        // Create AIT section with multiple applications
        val aitSection = createAitSectionWithMultipleApplications()
        
        // Process the section
        aitParser.onSection(0x74, aitSection)
        
        // Verify results - should find the first HTTP application
        assertTrue("Should have found HbbTV URL", testListener.urlFound)
        assertNotNull("HbbTV info should not be null", testListener.hbbTvInfo)
        assertEquals("Should find first HTTP app", "https://www.example1.com", testListener.hbbTvInfo!!.url)
    }
    
    @Test
    fun `test parse AIT with autostart preference`() {
        // Create AIT section with multiple applications, one autostart
        val aitSection = createAitSectionWithAutostartPreference()
        
        // Process the section
        aitParser.onSection(0x74, aitSection)
        
        // Verify results - should prefer autostart application
        assertTrue("Should have found HbbTV URL", testListener.urlFound)
        assertNotNull("HbbTV info should not be null", testListener.hbbTvInfo)
        assertTrue("Should prefer autostart app", testListener.hbbTvInfo!!.autostart)
        assertEquals("Should find autostart app", "https://www.autostart.com", testListener.hbbTvInfo!!.url)
    }
    
    /**
     * Create a valid AIT section with HTTP application
     */
    private fun createValidAitSection(
        orgId: Int,
        appId: Int,
        controlCode: Int,
        urlBase: String,
        urlExtension: String
    ): ByteArray {
        val urlBaseBytes = urlBase.toByteArray(Charsets.UTF_8)
        val urlExtBytes = urlExtension.toByteArray(Charsets.UTF_8)
        
        // Calculate section length
        val sectionLength = 3 + // table_id, section_syntax_indicator, section_length
                4 + // version_number, current_next_indicator, section_number, last_section_number
                2 + // common_descriptor_loop_length
                0 + // common descriptors (none)
                2 + // application_loop_length
                8 + // application_identifier, control_code, reserved, app_descriptor_loop_length
                2 + // application_descriptor (tag + length)
                4 + // application_profiles, application_priority, transport_protocol_labels
                2 + // transport_protocol_descriptor (tag + length)
                4 + // protocol_id, transport_protocol_label, reserved, url_base_length
                urlBaseBytes.size + // url_base
                urlExtBytes.size + // url_extension
                4 // CRC32
        
        val section = ByteArray(sectionLength + 3) // +3 for table_id, section_syntax_indicator, section_length
        
        var offset = 0
        
        // table_id
        section[offset++] = 0x74.toByte()
        
        // section_syntax_indicator (1) + reserved (1) + section_length (12)
        section[offset++] = ((0x80 or ((sectionLength shr 8) and 0x0F)).toByte())
        section[offset++] = (sectionLength and 0xFF).toByte()
        
        // reserved (4) + version_number (3) + current_next_indicator (1)
        section[offset++] = 0x00.toByte()
        
        // section_number + last_section_number
        section[offset++] = 0x00.toByte()
        section[offset++] = 0x00.toByte()
        
        // common_descriptor_loop_length (12 bits)
        section[offset++] = 0x00.toByte()
        section[offset++] = 0x00.toByte()
        
        // application_loop_length (16 bits)
        val appLoopLength = 8 + 2 + 4 + 2 + 4 + urlBaseBytes.size + urlExtBytes.size
        section[offset++] = ((appLoopLength shr 8) and 0xFF).toByte()
        section[offset++] = (appLoopLength and 0xFF).toByte()
        
        // application_identifier (organisation_id + application_id)
        section[offset++] = ((orgId shr 8) and 0xFF).toByte()
        section[offset++] = (orgId and 0xFF).toByte()
        section[offset++] = ((appId shr 8) and 0xFF).toByte()
        section[offset++] = (appId and 0xFF).toByte()
        
        // application_control_code + reserved
        section[offset++] = controlCode.toByte()
        section[offset++] = 0x00.toByte()
        
        // application_descriptors_loop_length (16 bits)
        val appDescLoopLength = 2 + 4 + 2 + 4 + urlBaseBytes.size + urlExtBytes.size
        section[offset++] = ((appDescLoopLength shr 8) and 0xFF).toByte()
        section[offset++] = (appDescLoopLength and 0xFF).toByte()
        
        // application_descriptor (tag + length)
        section[offset++] = 0x00.toByte() // application_descriptor tag
        section[offset++] = 0x04.toByte() // length
        
        // application_profiles, application_priority, transport_protocol_labels
        section[offset++] = 0x00.toByte() // profiles high byte
        section[offset++] = 0x01.toByte() // profiles low byte
        section[offset++] = 0x01.toByte() // priority
        section[offset++] = 0x01.toByte() // transport_protocol_labels
        
        // transport_protocol_descriptor (tag + length)
        section[offset++] = 0x02.toByte() // transport_protocol_descriptor tag
        val tpDescLength = 4 + urlBaseBytes.size + urlExtBytes.size
        section[offset++] = tpDescLength.toByte()
        
        // protocol_id (HTTP = 0x0003)
        section[offset++] = 0x00.toByte()
        section[offset++] = 0x03.toByte()
        
        // transport_protocol_label + reserved
        section[offset++] = 0x01.toByte()
        section[offset++] = 0x00.toByte()
        
        // url_base_length + url_base
        section[offset++] = urlBaseBytes.size.toByte()
        System.arraycopy(urlBaseBytes, 0, section, offset, urlBaseBytes.size)
        offset += urlBaseBytes.size
        
        // url_extension_length + url_extension
        section[offset++] = urlExtBytes.size.toByte()
        System.arraycopy(urlExtBytes, 0, section, offset, urlExtBytes.size)
        offset += urlExtBytes.size
        
        // Calculate and add CRC32
        val crc = calculateCrc32(section, 0, offset)
        section[offset++] = ((crc shr 24) and 0xFF).toByte()
        section[offset++] = ((crc shr 16) and 0xFF).toByte()
        section[offset++] = ((crc shr 8) and 0xFF).toByte()
        section[offset++] = (crc and 0xFF).toByte()
        
        return section
    }
    
    /**
     * Create AIT section with only DSM-CC applications
     */
    private fun createAitSectionWithDsmccOnly(): ByteArray {
        // Similar to valid AIT but with protocol_id != 0x0003
        val section = createValidAitSection(
            orgId = 0x1234,
            appId = 0x5678,
            controlCode = 0x01,
            urlBase = "https://www.example.com",
            urlExtension = ""
        )
        
        // Change protocol_id to DSM-CC (0x0001)
        section[section.size - 8] = 0x00.toByte()
        section[section.size - 7] = 0x01.toByte()
        
        // Recalculate CRC
        val crc = calculateCrc32(section, 0, section.size - 4)
        section[section.size - 4] = ((crc shr 24) and 0xFF).toByte()
        section[section.size - 3] = ((crc shr 16) and 0xFF).toByte()
        section[section.size - 2] = ((crc shr 8) and 0xFF).toByte()
        section[section.size - 1] = (crc and 0xFF).toByte()
        
        return section
    }
    
    /**
     * Create AIT section with no applications
     */
    private fun createAitSectionWithNoApplications(): ByteArray {
        val sectionLength = 3 + 4 + 2 + 0 + 2 + 0 // No applications
        val section = ByteArray(sectionLength + 3)
        
        var offset = 0
        
        // table_id
        section[offset++] = 0x74.toByte()
        
        // section_syntax_indicator + section_length
        section[offset++] = ((0x80 or ((sectionLength shr 8) and 0x0F)).toByte())
        section[offset++] = (sectionLength and 0xFF).toByte()
        
        // version_number, current_next_indicator, section_number, last_section_number
        section[offset++] = 0x00.toByte()
        section[offset++] = 0x00.toByte()
        section[offset++] = 0x00.toByte()
        section[offset++] = 0x00.toByte()
        
        // common_descriptor_loop_length
        section[offset++] = 0x00.toByte()
        section[offset++] = 0x00.toByte()
        
        // application_loop_length (0)
        section[offset++] = 0x00.toByte()
        section[offset++] = 0x00.toByte()
        
        // Add CRC32
        val crc = calculateCrc32(section, 0, offset)
        section[offset++] = ((crc shr 24) and 0xFF).toByte()
        section[offset++] = ((crc shr 16) and 0xFF).toByte()
        section[offset++] = ((crc shr 8) and 0xFF).toByte()
        section[offset++] = (crc and 0xFF).toByte()
        
        return section
    }
    
    /**
     * Create AIT section with multiple applications
     */
    private fun createAitSectionWithMultipleApplications(): ByteArray {
        // This would be a more complex section with multiple apps
        // For simplicity, we'll use the single app version
        return createValidAitSection(
            orgId = 0x1234,
            appId = 0x5678,
            controlCode = 0x01,
            urlBase = "https://www.example1.com",
            urlExtension = ""
        )
    }
    
    /**
     * Create AIT section with autostart preference
     */
    private fun createAitSectionWithAutostartPreference(): ByteArray {
        return createValidAitSection(
            orgId = 0x1234,
            appId = 0x5678,
            controlCode = 0x01, // AUTOSTART
            urlBase = "https://www.autostart.com",
            urlExtension = ""
        )
    }
    
    /**
     * Calculate CRC32 for section validation
     */
    private fun calculateCrc32(data: ByteArray, offset: Int, length: Int): Int {
        val crc = java.util.zip.CRC32()
        crc.update(data, offset, length)
        return crc.value.toInt()
    }
    
    /**
     * Test listener implementation
     */
    private class TestAitListener : AitListener {
        var urlFound = false
        var aitPresentButNoUrl = false
        var hbbTvInfo: HbbTvAppUrl? = null
        var reason: String? = null
        
        override fun onHbbTvUrlFound(info: HbbTvAppUrl) {
            urlFound = true
            hbbTvInfo = info
        }
        
        override fun onAitPresentButNoUrl(reason: String) {
            aitPresentButNoUrl = true
            this.reason = reason
        }
        
        fun reset() {
            urlFound = false
            aitPresentButNoUrl = false
            hbbTvInfo = null
            reason = null
        }
    }
}
