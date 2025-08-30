package com.livetv.androidtv.ts

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.ts.TsPayloadReader
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.SectionReader
import androidx.media3.extractor.ts.TsExtractor
import androidx.media3.extractor.ts.TsPayloadReader.TrackIdGenerator
import com.livetv.androidtv.hbbtv.AitParser
import com.livetv.androidtv.hbbtv.AitListener
import com.livetv.androidtv.hbbtv.HbbTvAppUrl

/**
 * Enhanced TS Extractor that integrates with ExoPlayer and extracts PSI sections for HbbTV AIT parsing
 */
class EnhancedTsExtractor : Extractor {

    companion object {
        private const val TAG = "EnhancedTsExtractor"
        private const val TS_PACKET_SIZE = 188
        private const val TS_SYNC_BYTE = 0x47

        // Standard PSI PIDs
        private const val PID_PAT = 0x0000
        private const val PID_CAT = 0x0001
        private const val PID_TSDT = 0x0002
        private const val PID_NULL = 0x1FFF

        // AIT table ID
        private const val TABLE_ID_AIT = 0x74
    }

    private val psiReassembler = PsiReassembler()
    private val aitParser = AitParser(object : AitListener {
        override fun onHbbTvUrlFound(info: HbbTvAppUrl) {
            Log.i(TAG, "🎯 HbbTV URL found: ${info.url} (autostart: ${info.autostart})")
            // TODO: Notify PlayerActivity about the found URL
        }

        override fun onAitPresentButNoUrl(reason: String) {
            Log.i(TAG, "ℹ️ AIT present but no HbbTV URL: $reason")
            // TODO: Notify PlayerActivity about AIT presence without URL
        }
    })

    // Track outputs for different stream types
    private var videoTrackOutput: TrackOutput? = null
    private var audioTrackOutput: TrackOutput? = null
    private var psiTrackOutput: TrackOutput? = null

    // PSI section readers
    private val psiReaders = mutableMapOf<Int, SectionReader>()

    // Track ID generator
    private val trackIdGenerator = TrackIdGenerator(0, 1)

    // State tracking
    private var hasOutputFormat = false
    private var bytesRead = 0L

    override fun init(output: ExtractorOutput) {
        Log.d(TAG, "Initializing Enhanced TS Extractor")

        // Create track outputs
        videoTrackOutput = output.track(0, C.TRACK_TYPE_VIDEO)
        audioTrackOutput = output.track(1, C.TRACK_TYPE_AUDIO)
        psiTrackOutput = output.track(2, 3) // TRACK_TYPE_DATA = 3

        // Register AIT consumer with PSI reassembler
        psiReassembler.registerConsumer(TABLE_ID_AIT, object : SectionConsumer {
            override fun onSection(tableId: Int, section: ByteArray) {
                Log.d(TAG, "Received AIT section: ${section.size} bytes")
                aitParser.onSection(tableId, section)
            }
        })

        // Register PAT consumer to discover PMT PIDs
        psiReassembler.registerConsumer(0x00, object : SectionConsumer {
            override fun onSection(tableId: Int, section: ByteArray) {
                Log.d(TAG, "Received PAT section: ${section.size} bytes")
                parsePAT(section)
            }
        })

        // Register PMT consumer to discover AIT PIDs
        psiReassembler.registerConsumer(0x02, object : SectionConsumer {
            override fun onSection(tableId: Int, section: ByteArray) {
                Log.d(TAG, "Received PMT section: ${section.size} bytes")
                parsePMT(section)
            }
        })

        Log.d(TAG, "Enhanced TS Extractor initialized")
    }

    override fun sniff(input: ExtractorInput): Boolean {
        // Try to read a few TS packets to see if this looks like a valid TS stream
        try {
            val packet = ByteArray(TS_PACKET_SIZE)
            val bytesRead = input.read(packet, 0, TS_PACKET_SIZE)
            
            if (bytesRead < TS_PACKET_SIZE) {
                return false
            }
            
            // Check if first byte is sync byte
            if (packet[0] != TS_SYNC_BYTE.toByte()) {
                return false
            }
            
            // Check a few more packets to be sure
            for (i in 1..3) {
                val nextPacket = ByteArray(TS_PACKET_SIZE)
                val nextBytesRead = input.read(nextPacket, 0, TS_PACKET_SIZE)
                
                if (nextBytesRead < TS_PACKET_SIZE || nextPacket[0] != TS_SYNC_BYTE.toByte()) {
                    return false
                }
            }
            
            return true
        } catch (e: Exception) {
            return false
        }
    }

    override fun read(input: ExtractorInput, positionHolder: PositionHolder): Int {
        try {
            // Read TS packet
            val packet = ByteArray(TS_PACKET_SIZE)
            val bytesRead = input.read(packet, 0, TS_PACKET_SIZE)

            if (bytesRead == -1) {
                return Extractor.RESULT_END_OF_INPUT
            }

            if (bytesRead < TS_PACKET_SIZE) {
                Log.w(TAG, "Incomplete TS packet read: $bytesRead bytes")
                return Extractor.RESULT_END_OF_INPUT
            }

            // Validate sync byte
            if (packet[0] != TS_SYNC_BYTE.toByte()) {
                Log.w(TAG, "Invalid TS sync byte: 0x${packet[0].toInt() and 0xFF}")
                return Extractor.RESULT_CONTINUE
            }

            // Parse TS header
            val header = parseTsHeader(packet)
            if (header == null) {
                return Extractor.RESULT_CONTINUE
            }

            // Process PSI packets
            if (isPsiPid(header.pid)) {
                processPsiPacket(header, packet)
            }

            // Process media packets
            processMediaPacket(header, packet)

            this.bytesRead += bytesRead.toLong()
            return Extractor.RESULT_CONTINUE

        } catch (e: Exception) {
            Log.e(TAG, "Error reading TS stream", e)
            return Extractor.RESULT_CONTINUE
        }
    }

    override fun seek(position: Long, seekTimeUs: Long) {
        Log.d(TAG, "Seeking to position: $position, time: $seekTimeUs")

        // Clear PSI buffers on seek
        psiReassembler.clearBuffers()

        // Reset state
        hasOutputFormat = false
        bytesRead = position
    }

    override fun release() {
        Log.d(TAG, "Releasing Enhanced TS Extractor")
        psiReassembler.clearBuffers()
    }

    /**
     * Parse TS packet header
     */
    private fun parseTsHeader(packet: ByteArray): TsHeader? {
        if (packet.size < 4) return null

        val syncByte = u8(packet[0])
        if (syncByte != TS_SYNC_BYTE) return null

        val payloadUnitStartIndicator = (u8(packet[1]) and 0x40) != 0
        val transportPriority = (u8(packet[1]) and 0x20) != 0
        val pid = ((u8(packet[1]) and 0x1F) shl 8) or u8(packet[2])
        val scramblingControl = (u8(packet[3]) shr 6) and 0x03
        val adaptationFieldControl = (u8(packet[3]) shr 4) and 0x03
        val continuityCounter = u8(packet[3]) and 0x0F

        return TsHeader(
            payloadUnitStartIndicator = payloadUnitStartIndicator,
            transportPriority = transportPriority,
            pid = pid,
            scramblingControl = scramblingControl,
            adaptationFieldControl = adaptationFieldControl,
            continuityCounter = continuityCounter
        )
    }

    /**
     * Check if PID carries PSI/SI data
     */
    private fun isPsiPid(pid: Int): Boolean {
        return when (pid) {
            PID_PAT, PID_CAT, PID_TSDT -> true
            in 0x0010..0x1FFE -> true // PMT and other PSI PIDs
            else -> false
        }
    }

    /**
     * Process PSI packet for section reassembly
     */
    private fun processPsiPacket(header: TsHeader, packet: ByteArray) {
        // Extract payload (skip 4-byte header)
        val payload = packet.sliceArray(4 until packet.size)

        // Feed to PSI reassembler
        psiReassembler.processPacket(header.pid, packet)

        Log.d(TAG, "Processed PSI packet: PID=0x${header.pid.toString(16)}, " +
                "start=${header.payloadUnitStartIndicator}, size=${payload.size}")
    }

    /**
     * Process media packet (video/audio)
     */
    private fun processMediaPacket(header: TsHeader, packet: ByteArray) {
        // Extract payload
        val payload = packet.sliceArray(4 until packet.size)

        // Route to appropriate track output based on PID
        when {
            header.pid in 0x1000..0x1FFF -> {
                // Video PID range
                videoTrackOutput?.sampleData(
                    ParsableByteArray(payload),
                    payload.size
                )
            }
            header.pid in 0x1100..0x1FFF -> {
                // Audio PID range
                audioTrackOutput?.sampleData(
                    ParsableByteArray(payload),
                    payload.size
                )
            }
        }
    }

    /**
     * Parse PAT (Program Association Table) to discover PMT PIDs
     */
    private fun parsePAT(patSection: ByteArray) {
        try {
            if (patSection.size < 8) return

            // Skip table_id, section_syntax_indicator, section_length
            var offset = 3

            // Skip transport_stream_id, version_number, current_next_indicator, section_number, last_section_number
            offset += 5

            // Parse program loop
            val programLoopLength = ((u8(patSection[offset]) and 0x0F) shl 8) or u8(patSection[offset + 1])
            offset += 2

            Log.d(TAG, "PAT: program loop length = $programLoopLength")

            // Parse each program entry
            var programOffset = 0
            while (programOffset < programLoopLength && offset + 4 <= patSection.size) {
                val programNumber = u16(patSection[offset], patSection[offset + 1])
                val pmtPid = ((u8(patSection[offset + 2]) and 0x1F) shl 8) or u8(patSection[offset + 3])

                if (programNumber != 0) { // Skip NIT
                    Log.d(TAG, "PAT: Program $programNumber -> PMT PID 0x${pmtPid.toString(16)}")

                    // Register PMT consumer
                    psiReassembler.registerConsumer(0x02, object : SectionConsumer {
                        override fun onSection(tableId: Int, section: ByteArray) {
                            Log.d(TAG, "Received PMT section for program $programNumber: ${section.size} bytes")
                            parsePMT(section)
                        }
                    })
                }

                offset += 4
                programOffset += 4
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing PAT", e)
        }
    }

    /**
     * Parse PMT (Program Map Table) to discover AIT PIDs
     */
    private fun parsePMT(pmtSection: ByteArray) {
        try {
            if (pmtSection.size < 12) return

            // Skip table_id, section_syntax_indicator, section_length
            var offset = 3

            // Skip program_number, reserved, version_number, current_next_indicator, section_number, last_section_number
            offset += 6

            // Parse PCR_PID
            val pcrPid = ((u8(pmtSection[offset]) and 0x1F) shl 8) or u8(pmtSection[offset + 1])
            offset += 2

            Log.d(TAG, "PMT: PCR PID = 0x${pcrPid.toString(16)}")

            // Parse program_info_descriptors_loop_length
            val programInfoLength = ((u8(pmtSection[offset]) and 0x0F) shl 8) or u8(pmtSection[offset + 1])
            offset += 2

            // Skip program info descriptors
            offset += programInfoLength

            // Parse ES loop
            while (offset + 5 <= pmtSection.size) {
                val streamType = u8(pmtSection[offset])
                val elementaryPid = ((u8(pmtSection[offset + 1]) and 0x1F) shl 8) or u8(pmtSection[offset + 2])
                val esInfoLength = ((u8(pmtSection[offset + 3]) and 0x0F) shl 8) or u8(pmtSection[offset + 4])
                offset += 5

                Log.d(TAG, "PMT: ES PID 0x${elementaryPid.toString(16)}, type=0x${streamType.toString(16)}, " +
                        "info_length=$esInfoLength")

                // Check if this is an AIT stream
                if (streamType == 0x05 || streamType == 0x06) { // Private data or DSM-CC
                    Log.d(TAG, "PMT: Potential AIT stream detected on PID 0x${elementaryPid.toString(16)}")

                    // Register consumer for this PID to catch AIT sections
                    psiReassembler.registerConsumer(TABLE_ID_AIT, object : SectionConsumer {
                        override fun onSection(tableId: Int, section: ByteArray) {
                            Log.d(TAG, "Received AIT section from PMT-discovered PID 0x${elementaryPid.toString(16)}: ${section.size} bytes")
                            aitParser.onSection(tableId, section)
                        }
                    })
                }

                // Skip ES info descriptors
                offset += esInfoLength
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing PMT", e)
        }
    }

    // Utility functions for unsigned byte reading
    private fun u8(b: Byte) = b.toInt() and 0xFF
    private fun u16(b1: Byte, b2: Byte) = ((u8(b1) shl 8) or u8(b2))

    /**
     * TS packet header information
     */
    private data class TsHeader(
        val payloadUnitStartIndicator: Boolean,
        val transportPriority: Boolean,
        val pid: Int,
        val scramblingControl: Int,
        val adaptationFieldControl: Int,
        val continuityCounter: Int
    )
}
