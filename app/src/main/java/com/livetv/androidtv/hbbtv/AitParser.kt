package com.livetv.androidtv.hbbtv

import android.util.Log
import java.util.zip.CRC32

/**
 * Parser for HbbTV Application Information Table (AIT) according to ETSI TS 102 809 / TS 102 796
 * Table ID: 0x74
 */
class AitParser(private val listener: AitListener) {
    
    companion object {
        private const val TAG = "AitParser"
        private const val TABLE_ID_AIT = 0x74
        private const val PROTOCOL_ID_HTTP = 0x0003
        private const val DESCRIPTOR_TAG_APPLICATION = 0x00
        private const val DESCRIPTOR_TAG_TRANSPORT_PROTOCOL = 0x02
    }
    
    /**
     * Process a PSI section with table_id 0x74 (AIT)
     * @param tableId The table ID (must be 0x74 for AIT)
     * @param section The section payload (after pointer_field, including section syntax)
     */
    fun onSection(tableId: Int, section: ByteArray) {
        if (tableId != TABLE_ID_AIT) {
            Log.w(TAG, "Ignoring section with table_id 0x${tableId.toString(16)}, expected 0x74")
            return
        }
        
        try {
            Log.d(TAG, "Processing AIT section: ${section.size} bytes")
            
            // Parse section header
            val sectionLength = parseSectionHeader(section)
            if (sectionLength == null) {
                Log.w(TAG, "Failed to parse AIT section header")
                return
            }
            
            // Validate section length
            if (section.size < sectionLength + 3) { // +3 for table_id, section_syntax_indicator, and section_length
                Log.w(TAG, "AIT section truncated: expected ${sectionLength + 3} bytes, got ${section.size}")
                return
            }
            
            // Extract section data (excluding header and CRC)
            val sectionData = section.sliceArray(3 until sectionLength + 3)
            
            // Parse AIT specific data
            val aitData = parseAitData(sectionData)
            if (aitData == null) {
                Log.w(TAG, "Failed to parse AIT data")
                return
            }
            
            // Find the best HbbTV application
            val bestApp = findBestHbbTvApp(aitData.applications)
            if (bestApp != null) {
                Log.i(TAG, "HbbTV URL found: ${bestApp.url} (autostart: ${bestApp.autostart})")
                listener.onHbbTvUrlFound(bestApp)
            } else {
                val reason = if (aitData.applications.isEmpty()) {
                    "No applications found in AIT"
                } else {
                    "No HTTP applications found (protocol_id 0x0003)"
                }
                Log.i(TAG, "AIT present but no HbbTV URL: $reason")
                listener.onAitPresentButNoUrl(reason)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing AIT section", e)
        }
    }
    
    /**
     * Parse PSI section header
     * @return Section length in bytes, or null if parsing failed
     */
    private fun parseSectionHeader(section: ByteArray): Int? {
        if (section.size < 3) return null
        
        // table_id is already verified (0x74)
        // section_syntax_indicator (1 bit) + reserved (1 bit) + section_length (12 bits)
        val sectionSyntaxIndicator = (u8(section[1]) and 0x80) != 0
        val sectionLength = ((u8(section[1]) and 0x0F) shl 8) or u8(section[2])
        
        Log.d(TAG, "Section header: syntax_indicator=$sectionSyntaxIndicator, length=$sectionLength")
        
        return sectionLength
    }
    
    /**
     * Parse AIT specific data after the common PSI header
     */
    private fun parseAitData(sectionData: ByteArray): AitData? {
        if (sectionData.size < 4) return null
        
        var offset = 0
        
        // Skip reserved bits and version_number
        offset += 1
        
        // current_next_indicator (1 bit) + section_number (8 bits) + last_section_number (8 bits)
        offset += 2
        
        // reserved (4 bits) + common_descriptor_loop_length (12 bits)
        val commonDescriptorLoopLength = ((u8(sectionData[offset]) and 0x0F) shl 8) or u8(sectionData[offset + 1])
        offset += 2
        
        Log.d(TAG, "Common descriptor loop length: $commonDescriptorLoopLength")
        
        // Skip common descriptors
        offset += commonDescriptorLoopLength
        
        if (offset >= sectionData.size) {
            Log.w(TAG, "AIT section too short for application loop")
            return null
        }
        
        // application_loop_length (16 bits)
        val applicationLoopLength = u16(sectionData[offset], sectionData[offset + 1])
        offset += 2
        
        Log.d(TAG, "Application loop length: $applicationLoopLength")
        
        if (offset + applicationLoopLength > sectionData.size) {
            Log.w(TAG, "Application loop extends beyond section data")
            return null
        }
        
        // Parse applications
        val applications = mutableListOf<HbbTvAppUrl>()
        val applicationData = sectionData.sliceArray(offset until offset + applicationLoopLength)
        var appOffset = 0
        
        while (appOffset < applicationData.size) {
            if (appOffset + 4 > applicationData.size) break
            
            // application_identifier (organisation_id + application_id)
            val orgId = u16(applicationData[appOffset], applicationData[appOffset + 1])
            val appId = u16(applicationData[appOffset + 2], applicationData[appOffset + 3])
            appOffset += 4
            
            // application_control_code (8 bits) + reserved (8 bits)
            val controlCode = u8(applicationData[appOffset])
            appOffset += 2
            
            // application_descriptors_loop_length (16 bits)
            val appDescriptorLoopLength = u16(applicationData[appOffset], applicationData[appOffset + 1])
            appOffset += 2
            
            if (appOffset + appDescriptorLoopLength > applicationData.size) break
            
            // Parse application descriptors
            val appDescriptors = applicationData.sliceArray(appOffset until appOffset + appDescriptorLoopLength)
            val appInfo = parseApplicationDescriptors(appDescriptors, orgId, appId, controlCode)
            
            if (appInfo != null) {
                applications.add(appInfo)
            }
            
            appOffset += appDescriptorLoopLength
        }
        
        Log.d(TAG, "Parsed ${applications.size} applications from AIT")
        return AitData(applications)
    }
    
    /**
     * Parse application descriptors to extract HbbTV information
     */
    private fun parseApplicationDescriptors(
        descriptors: ByteArray,
        orgId: Int,
        appId: Int,
        controlCode: Int
    ): HbbTvAppUrl? {
        var offset = 0
        var autostart = false
        var url: String? = null
        
        while (offset < descriptors.size) {
            if (offset + 2 > descriptors.size) break
            
            val descriptorTag = u8(descriptors[offset])
            val descriptorLength = u8(descriptors[offset + 1])
            offset += 2
            
            if (offset + descriptorLength > descriptors.size) break
            
            val descriptorData = descriptors.sliceArray(offset until offset + descriptorLength)
            
            when (descriptorTag) {
                DESCRIPTOR_TAG_APPLICATION -> {
                    // Application descriptor - check autostart flag
                    if (descriptorData.size >= 1) {
                        val applicationProfiles = u16(descriptorData[0], descriptorData[1])
                        val applicationPriority = u8(descriptorData[2])
                        val transportProtocolLabels = descriptorData[3]
                        
                        // Check if this is an autostart application (typically "red button" apps)
                        autostart = (controlCode == 0x01) // AUTOSTART
                        
                        Log.d(TAG, "App descriptor: orgId=0x${orgId.toString(16)}, appId=0x${appId.toString(16)}, " +
                                "profiles=0x${applicationProfiles.toString(16)}, priority=$applicationPriority, " +
                                "autostart=$autostart")
                    }
                }
                DESCRIPTOR_TAG_TRANSPORT_PROTOCOL -> {
                    // Transport protocol descriptor - extract URL for HTTP protocol
                    val protocolInfo = parseTransportProtocolDescriptor(descriptorData)
                    if (protocolInfo != null) {
                        url = protocolInfo
                        Log.d(TAG, "Transport protocol: HTTP URL found: $url")
                    }
                }
            }
            
            offset += descriptorLength
        }
        
        return if (url != null) {
            HbbTvAppUrl(
                url = url,
                autostart = autostart,
                appId = appId,
                orgId = orgId
            )
        } else null
    }
    
    /**
     * Parse transport protocol descriptor to extract HTTP URL
     */
    private fun parseTransportProtocolDescriptor(descriptorData: ByteArray): String? {
        if (descriptorData.size < 4) return null
        
        val protocolId = u16(descriptorData[0], descriptorData[1])
        
        if (protocolId != PROTOCOL_ID_HTTP) {
            Log.d(TAG, "Skipping non-HTTP protocol: 0x${protocolId.toString(16)}")
            return null
        }
        
        // For HTTP protocol, extract URL_base and URL_extension
        var offset = 2
        
        // Skip transport_protocol_label (8 bits)
        offset += 1
        
        // Skip reserved (8 bits)
        offset += 1
        
        // Parse URL_base (length-prefixed string)
        if (offset >= descriptorData.size) return null
        val urlBaseLength = u8(descriptorData[offset])
        offset += 1
        
        if (offset + urlBaseLength > descriptorData.size) return null
        val urlBase = String(descriptorData, offset, urlBaseLength, Charsets.UTF_8)
        offset += urlBaseLength
        
        // Parse URL_extension (optional, length-prefixed string)
        var urlExtension = ""
        if (offset < descriptorData.size) {
            val urlExtLength = u8(descriptorData[offset])
            offset += 1
            
            if (offset + urlExtLength <= descriptorData.size) {
                urlExtension = String(descriptorData, offset, urlExtLength, Charsets.UTF_8)
            }
        }
        
        // Build complete URL
        return if (urlExtension.isNotEmpty()) {
            "$urlBase$urlExtension"
        } else {
            urlBase
        }
    }
    
    /**
     * Find the best HbbTV application (prefer autostart, then first HTTP app)
     */
    private fun findBestHbbTvApp(applications: List<HbbTvAppUrl>): HbbTvAppUrl? {
        if (applications.isEmpty()) return null
        
        // First, look for autostart applications
        val autostartApps = applications.filter { it.autostart }
        if (autostartApps.isNotEmpty()) {
            Log.d(TAG, "Found ${autostartApps.size} autostart applications")
            return autostartApps.first()
        }
        
        // Fallback to first available application
        Log.d(TAG, "No autostart applications, using first available")
        return applications.first()
    }
    
    // Utility functions for unsigned byte reading
    private fun u8(b: Byte) = b.toInt() and 0xFF
    private fun u16(b1: Byte, b2: Byte) = ((u8(b1) shl 8) or u8(b2))
    private fun u24(b1: Byte, b2: Byte, b3: Byte) = (u8(b1) shl 16) or (u8(b2) shl 8) or u8(b3)
    
    /**
     * Internal data structure for parsed AIT
     */
    private data class AitData(val applications: List<HbbTvAppUrl>)
}

/**
 * Data class representing a HbbTV application URL
 */
data class HbbTvAppUrl(
    val url: String,
    val autostart: Boolean,
    val appId: Int?,
    val orgId: Int?
)

/**
 * Interface for AIT parsing callbacks
 */
interface AitListener {
    fun onHbbTvUrlFound(info: HbbTvAppUrl)
    fun onAitPresentButNoUrl(reason: String)
}
