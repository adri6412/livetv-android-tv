package com.livetv.androidtv.hbbtv

import android.util.Log
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ts.TsPayloadReader.TrackIdGenerator
import androidx.media3.common.util.UnstableApi

/**
 * AIT Section Parser that parses Application Information Table sections
 * according to ETSI TS 102 809 and TS 102 796 standards.
 * 
 * This is a standalone parser that can be used to parse AIT sections
 * from MPEG-TS streams. It extracts HbbTV application URLs and metadata.
 */
@UnstableApi
class AitSectionPayloadReader(
    private val listener: AitListener
) {

    companion object {
        private const val TAG = "AitSectionPayloadReader"
        
        // AIT table ID
        private const val TABLE_ID_AIT = 0x74
        
        // Descriptor tags
        private const val DESCRIPTOR_TAG_APPLICATION = 0x00
        private const val DESCRIPTOR_TAG_TRANSPORT_PROTOCOL = 0x02
        private const val DESCRIPTOR_TAG_SIMPLE_APPLICATION_LOCATION = 0x15
        
        // Transport protocol IDs
        private const val PROTOCOL_ID_DSM_CC = 0x0001
        private const val PROTOCOL_ID_HTTP = 0x0003
        
        // Application control codes
        private const val APP_CONTROL_AUTOSTART = 0x01
        private const val APP_CONTROL_PRESENT = 0x02
    }

    // State tracking
    private var aitDetected = false
    private var totalSectionsProcessed = 0
    private var totalCrcErrors = 0
    private var aitPid: Int = -1

    fun init(
        extractorOutput: ExtractorOutput,
        trackIdGenerator: TrackIdGenerator
    ) {
        Log.d(TAG, "Initializing AIT Section Payload Reader")
        
        // For now, we'll skip track creation to avoid compilation issues
        // TODO: Implement proper track creation when Media3 API is stable
        Log.d(TAG, "AIT Section Payload Reader initialized (track creation skipped)")
    }

    fun consume(sectionData: ParsableByteArray) {
        val sectionSize = sectionData.bytesLeft()
        
        if (sectionSize < 8) {
            Log.w(TAG, "Section too short: $sectionSize bytes")
            return
        }

        try {
            // Parse section header
            val tableId = sectionData.readUnsignedByte()
            if (tableId != TABLE_ID_AIT) {
                return // Not an AIT section
            }

            totalSectionsProcessed++
            Log.d(TAG, "Processing AIT section: $sectionSize bytes")

            // Parse section syntax indicator and section length
            val sectionSyntaxIndicator = (sectionData.readUnsignedByte() and 0x80) != 0
            val sectionLength = sectionData.readUnsignedShort() and 0x0FFF

            Log.d(TAG, "AIT section: syntax_indicator=$sectionSyntaxIndicator, length=$sectionLength")

            // Validate section length
            if (sectionSize < sectionLength + 3) {
                Log.w(TAG, "AIT section truncated: expected ${sectionLength + 3} bytes, got $sectionSize")
                return
            }

            // Skip transport_stream_id, version_number, current_next_indicator, section_number, last_section_number
            sectionData.skipBytes(5)

            // Parse application loop
            val applicationLoopLength = sectionData.readUnsignedShort() and 0x0FFF
            Log.d(TAG, "AIT application loop length: $applicationLoopLength")

            val applications = mutableListOf<AitApplication>()

            // Parse applications - we don't need to track offset since parseApplication handles the data position
            while (sectionData.bytesLeft() >= 4) {
                val app = parseApplication(sectionData)
                if (app != null) {
                    applications.add(app)
                    Log.d(TAG, "Parsed application: orgId=0x${app.orgId.toString(16)}, appId=0x${app.appId.toString(16)}, protocol=0x${app.protocolId.toString(16)}")
                }
            }

            // Process parsed applications
            processApplications(applications)

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing AIT section", e)
        }
    }

    /**
     * Parse a single application entry from the AIT
     */
    private fun parseApplication(sectionData: ParsableByteArray): AitApplication? {
        try {
            // Parse application identifiers
            val orgId = sectionData.readUnsignedInt().toInt()
            val appId = sectionData.readUnsignedShort()

            // Parse application control code
            val appControlCode = sectionData.readUnsignedByte()
            val isAutostart = (appControlCode and APP_CONTROL_AUTOSTART) != 0

            // Parse application descriptors loop length
            val appDescriptorsLength = sectionData.readUnsignedShort() and 0x0FFF

            Log.d(TAG, "App: orgId=0x${orgId.toString(16)}, appId=0x${appId.toString(16)}, " +
                    "control=0x${appControlCode.toString(16)}, autostart=$isAutostart, " +
                    "descriptors_length=$appDescriptorsLength")

            // Parse descriptors
            var urlBase: String? = null
            var urlExtension: String? = null
            var initialPath: String? = null
            var protocolId: Int = 0

            val descriptorsEnd = sectionData.position.toInt() + appDescriptorsLength
            while (sectionData.position < descriptorsEnd && sectionData.bytesLeft() >= 2) {
                val descriptorTag = sectionData.readUnsignedByte()
                val descriptorLength = sectionData.readUnsignedByte()

                if (sectionData.bytesLeft() < descriptorLength) {
                    Log.w(TAG, "Descriptor truncated: tag=0x${descriptorTag.toString(16)}, length=$descriptorLength")
                    break
                }

                when (descriptorTag) {
                    DESCRIPTOR_TAG_TRANSPORT_PROTOCOL -> {
                        val parsed = parseTransportProtocolDescriptor(sectionData, descriptorLength)
                        if (parsed != null) {
                            urlBase = parsed.urlBase
                            urlExtension = parsed.urlExtension
                            protocolId = parsed.protocolId
                        }
                    }
                    DESCRIPTOR_TAG_SIMPLE_APPLICATION_LOCATION -> {
                        initialPath = parseSimpleApplicationLocationDescriptor(sectionData, descriptorLength)
                    }
                    DESCRIPTOR_TAG_APPLICATION -> {
                        // Application descriptor - we already parsed the control code
                        sectionData.skipBytes(descriptorLength)
                    }
                    else -> {
                        Log.d(TAG, "Unknown descriptor: tag=0x${descriptorTag.toString(16)}, length=$descriptorLength")
                        sectionData.skipBytes(descriptorLength)
                    }
                }
            }

            // Build URL if we have the necessary components
            val url = buildUrl(urlBase, urlExtension, initialPath)

            return AitApplication(
                orgId = orgId,
                appId = appId,
                isAutostart = isAutostart,
                protocolId = protocolId,
                url = url
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing application entry", e)
            return null
        }
    }

    /**
     * Parse transport protocol descriptor (tag 0x02)
     */
    private fun parseTransportProtocolDescriptor(sectionData: ParsableByteArray, length: Int): TransportProtocolInfo? {
        try {
            if (length < 4) return null

            val protocolId = sectionData.readUnsignedShort()
            val transportProfile = sectionData.readUnsignedShort()

            Log.d(TAG, "Transport protocol: protocol_id=0x${protocolId.toString(16)}, profile=0x${transportProfile.toString(16)}")

            // Only handle HTTP protocol (0x0003)
            if (protocolId != PROTOCOL_ID_HTTP) {
                Log.d(TAG, "Skipping non-HTTP protocol: 0x${protocolId.toString(16)}")
                sectionData.skipBytes(length - 4)
                return null
            }

            // Parse URL base and extension
            var urlBase: String? = null
            var urlExtension: String? = null

            if (sectionData.bytesLeft() >= 1) {
                val urlBaseLength = sectionData.readUnsignedByte()
                if (urlBaseLength > 0 && sectionData.bytesLeft() >= urlBaseLength) {
                    urlBase = String(sectionData.data, sectionData.position, urlBaseLength)
                    sectionData.skipBytes(urlBaseLength)
                    Log.d(TAG, "URL base: $urlBase")
                }
            }

            if (sectionData.bytesLeft() >= 1) {
                val urlExtensionLength = sectionData.readUnsignedByte()
                if (urlExtensionLength > 0 && sectionData.bytesLeft() >= urlExtensionLength) {
                    urlExtension = String(sectionData.data, sectionData.position, urlExtensionLength)
                    sectionData.skipBytes(urlExtensionLength)
                    Log.d(TAG, "URL extension: $urlExtension")
                }
            }

            return TransportProtocolInfo(
                protocolId = protocolId,
                urlBase = urlBase,
                urlExtension = urlExtension
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing transport protocol descriptor", e)
            return null
        }
    }

    /**
     * Parse simple application location descriptor (tag 0x15)
     */
    private fun parseSimpleApplicationLocationDescriptor(sectionData: ParsableByteArray, length: Int): String? {
        try {
            if (length < 1) return null

            val initialPathLength = sectionData.readUnsignedByte()
            if (initialPathLength == 0 || sectionData.bytesLeft() < initialPathLength) {
                return null
            }

            val initialPath = String(sectionData.data, sectionData.position, initialPathLength)
            sectionData.skipBytes(initialPathLength)
            
            Log.d(TAG, "Initial path: $initialPath")
            return initialPath

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing simple application location descriptor", e)
            return null
        }
    }

    /**
     * Build complete URL from components
     */
    private fun buildUrl(urlBase: String?, urlExtension: String?, initialPath: String?): String? {
        if (urlBase.isNullOrBlank()) {
            return null
        }

        var url = urlBase
        
        // Add extension if present
        if (!urlExtension.isNullOrBlank()) {
            url += urlExtension
        }
        
        // Add initial path if present
        if (!initialPath.isNullOrBlank()) {
            if (!url.endsWith("/") && !initialPath.startsWith("/")) {
                url += "/"
            }
            url += initialPath
        }

        return url
    }

    /**
     * Process parsed applications and notify listener
     */
    private fun processApplications(applications: List<AitApplication>) {
        if (applications.isEmpty()) {
            Log.i(TAG, "No applications found in AIT")
            listener.onAitPresentButNoUrl("No applications found in AIT")
            return
        }

        // Find HTTP applications
        val httpApps = applications.filter { it.protocolId == PROTOCOL_ID_HTTP }
        
        if (httpApps.isEmpty()) {
            Log.i(TAG, "AIT present but no HTTP applications (only DSM-CC)")
            listener.onAitPresentButNoUrl("AIT present but no HTTP transport (only DSM-CC)")
            return
        }

        // Find best application (prefer autostart)
        val bestApp = httpApps.find { it.isAutostart } ?: httpApps.first()
        
        Log.i(TAG, "HbbTV URL found: ${bestApp.url} (autostart: ${bestApp.isAutostart})")
        
        val hbbTvUrl = HbbTvAppUrl(
            url = bestApp.url ?: "",
            autostart = bestApp.isAutostart,
            appId = bestApp.appId,
            orgId = bestApp.orgId
        )
        
        listener.onHbbTvUrlFound(hbbTvUrl)
        aitDetected = true
    }

    /**
     * Get statistics for debugging
     */
    fun getStats(): AitReaderStats {
        return AitReaderStats(
            aitDetected = aitDetected,
            totalSectionsProcessed = totalSectionsProcessed,
            totalCrcErrors = totalCrcErrors,
            aitPid = aitPid
        )
    }

    /**
     * Set the PID where AIT was detected
     */
    fun setAitPid(pid: Int) {
        aitPid = pid
        Log.d(TAG, "AIT detected on PID 0x${pid.toString(16)}")
    }

    /**
     * Data class for transport protocol information
     */
    private data class TransportProtocolInfo(
        val protocolId: Int,
        val urlBase: String?,
        val urlExtension: String?
    )

    /**
     * Data class for parsed AIT application
     */
    private data class AitApplication(
        val orgId: Int,
        val appId: Int,
        val isAutostart: Boolean,
        val protocolId: Int,
        val url: String?
    )

    /**
     * Statistics for the AIT reader
     */
    data class AitReaderStats(
        val aitDetected: Boolean,
        val totalSectionsProcessed: Int,
        val totalCrcErrors: Int,
        val aitPid: Int
    )
}
