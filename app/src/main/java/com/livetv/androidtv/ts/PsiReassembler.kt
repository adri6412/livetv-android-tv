package com.livetv.androidtv.ts

import android.util.Log
import java.util.zip.CRC32

/**
 * PSI/SI section reassembler for MPEG-TS streams
 * Handles payload_unit_start_indicator, pointer_field, and section continuation
 */
class PsiReassembler {
    
    companion object {
        private const val TAG = "PsiReassembler"
        private const val TS_PACKET_SIZE = 188
        private const val TS_HEADER_SIZE = 4
        private const val TS_PAYLOAD_SIZE = TS_PACKET_SIZE - TS_HEADER_SIZE
        private const val MAX_SECTION_SIZE = 4096 // Maximum PSI section size
    }
    
    // PID -> Section buffer mapping
    private val sectionBuffers = mutableMapOf<Int, SectionBuffer>()
    
    // Table ID -> Section consumers
    private val sectionConsumers = mutableMapOf<Int, MutableList<SectionConsumer>>()
    
    /**
     * Process a TS packet for a specific PID
     * @param pid The PID this packet belongs to
     * @param packet The TS packet data (188 bytes)
     */
    fun processPacket(pid: Int, packet: ByteArray) {
        if (packet.size != TS_PACKET_SIZE) {
            Log.w(TAG, "Invalid TS packet size: ${packet.size}, expected $TS_PACKET_SIZE")
            return
        }
        
        try {
            // Parse TS header
            val header = parseTsHeader(packet)
            if (header == null) {
                Log.w(TAG, "Failed to parse TS header for PID 0x${pid.toString(16)}")
                return
            }
            
            // Extract payload
            val payload = packet.sliceArray(TS_HEADER_SIZE until packet.size)
            
            // Process payload based on header flags
            if (header.payloadUnitStartIndicator) {
                // New section starts here
                processNewSection(pid, header, payload)
            } else {
                // Continue existing section
                continueSection(pid, header, payload)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing TS packet for PID 0x${pid.toString(16)}", e)
        }
    }
    
    /**
     * Parse TS packet header
     */
    private fun parseTsHeader(packet: ByteArray): TsHeader? {
        if (packet.size < TS_HEADER_SIZE) return null
        
        val syncByte = u8(packet[0])
        if (syncByte != 0x47) {
            Log.w(TAG, "Invalid sync byte: 0x${syncByte.toString(16)}")
            return null
        }
        
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
     * Process a new section start
     */
    private fun processNewSection(pid: Int, header: TsHeader, payload: ByteArray) {
        // Get or create section buffer for this PID
        val buffer = sectionBuffers.getOrPut(pid) { SectionBuffer() }
        
        // Reset buffer for new section
        buffer.reset()
        
        // Handle pointer field if present
        var payloadOffset = 0
        if (header.adaptationFieldControl == 0x01 || header.adaptationFieldControl == 0x03) {
            // Adaptation field present, check for pointer field
            if (payload.isNotEmpty()) {
                val pointerField = u8(payload[0])
                if (pointerField > 0) {
                    // Skip stuffing bytes
                    payloadOffset = pointerField
                    Log.d(TAG, "PID 0x${pid.toString(16)}: Pointer field = $pointerField, skipping $pointerField bytes")
                }
            }
        }
        
        // Start collecting section data
        if (payloadOffset < payload.size) {
            val sectionData = payload.sliceArray(payloadOffset until payload.size)
            buffer.appendData(sectionData)
            
            // Check if we have enough data to parse section header
            if (buffer.data.size >= 3) {
                val tableId = u8(buffer.data[0])
                Log.d(TAG, "PID 0x${pid.toString(16)}: New section started, table_id=0x${tableId.toString(16)}")
                
                // Check if this is a complete section
                checkCompleteSection(pid, buffer)
            }
        }
    }
    
    /**
     * Continue collecting section data
     */
    private fun continueSection(pid: Int, header: TsHeader, payload: ByteArray) {
        val buffer = sectionBuffers[pid] ?: return
        
        // Append payload to current section
        buffer.appendData(payload)
        
        // Check if we have a complete section
        checkCompleteSection(pid, buffer)
    }
    
    /**
     * Check if we have a complete section and emit it
     */
    private fun checkCompleteSection(pid: Int, buffer: SectionBuffer) {
        if (buffer.data.size < 3) return
        
        val tableId = u8(buffer.data[0])
        
        // Parse section length from header
        val sectionLength = ((u8(buffer.data[1]) and 0x0F) shl 8) or u8(buffer.data[2])
        val totalSectionSize = sectionLength + 3 // +3 for table_id, section_syntax_indicator, and section_length
        
        if (buffer.data.size >= totalSectionSize) {
            // We have a complete section
            val sectionData = buffer.data.take(totalSectionSize).toByteArray()
            
            // Validate CRC32 if section is long enough
            if (sectionLength >= 4 && validateCrc32(sectionData)) {
                Log.d(TAG, "PID 0x${pid.toString(16)}: Complete section, table_id=0x${tableId.toString(16)}, " +
                        "size=$totalSectionSize bytes")
                
                // Emit section to consumers
                emitSection(tableId, sectionData)
                
                // Remove processed data from buffer
                buffer.removeProcessed(totalSectionSize)
                
                // Check if there's another section in the same packet
                if (buffer.data.size >= 3) {
                    checkCompleteSection(pid, buffer)
                }
            } else {
                Log.w(TAG, "PID 0x${pid.toString(16)}: Invalid CRC32 for section, table_id=0x${tableId.toString(16)}")
                // Reset buffer on CRC error
                buffer.reset()
            }
        }
    }
    
    /**
     * Validate CRC32 of a PSI section
     */
    private fun validateCrc32(section: ByteArray): Boolean {
        if (section.size < 4) return false
        
        // Extract section data (excluding CRC32)
        val sectionData = section.sliceArray(0 until section.size - 4)
        
        // Calculate expected CRC32
        val crc32 = CRC32()
        crc32.update(sectionData)
        val expectedCrc = crc32.value
        
        // Extract actual CRC32 from section
        val actualCrc = u32(section[section.size - 4], section[section.size - 3], 
                           section[section.size - 2], section[section.size - 1])
        
        return expectedCrc == actualCrc.toLong()
    }
    
    /**
     * Emit a complete section to registered consumers
     */
    private fun emitSection(tableId: Int, sectionData: ByteArray) {
        val consumers = sectionConsumers[tableId]
        if (consumers != null) {
            for (consumer in consumers) {
                try {
                    consumer.onSection(tableId, sectionData)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in section consumer for table_id 0x${tableId.toString(16)}", e)
                }
            }
        }
    }
    
    /**
     * Register a consumer for sections with a specific table ID
     */
    fun registerConsumer(tableId: Int, consumer: SectionConsumer) {
        sectionConsumers.getOrPut(tableId) { mutableListOf() }.add(consumer)
        Log.d(TAG, "Registered consumer for table_id 0x${tableId.toString(16)}")
    }
    
    /**
     * Unregister a consumer
     */
    fun unregisterConsumer(tableId: Int, consumer: SectionConsumer) {
        sectionConsumers[tableId]?.remove(consumer)
        if (sectionConsumers[tableId]?.isEmpty() == true) {
            sectionConsumers.remove(tableId)
        }
        Log.d(TAG, "Unregistered consumer for table_id 0x${tableId.toString(16)}")
    }
    
    /**
     * Clear all section buffers (useful when switching channels)
     */
    fun clearBuffers() {
        sectionBuffers.clear()
        Log.d(TAG, "Cleared all section buffers")
    }
    
    /**
     * Get statistics about current reassembly state
     */
    fun getStats(): ReassemblyStats {
        val totalBuffers = sectionBuffers.size
        val totalConsumers = sectionConsumers.values.sumOf { it.size }
        val activePids = sectionBuffers.keys.toList()
        
        return ReassemblyStats(
            totalBuffers = totalBuffers,
            totalConsumers = totalConsumers,
            activePids = activePids
        )
    }
    
    // Utility functions for unsigned byte reading
    private fun u8(b: Byte) = b.toInt() and 0xFF
    private fun u16(b1: Byte, b2: Byte) = ((u8(b1) shl 8) or u8(b2))
    private fun u32(b1: Byte, b2: Byte, b3: Byte, b4: Byte) = 
        (u8(b1).toLong() shl 24) or (u8(b2).toLong() shl 16) or (u8(b3).toLong() shl 8) or u8(b4).toLong()
    
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
    
    /**
     * Buffer for reassembling PSI sections
     */
    private class SectionBuffer {
        val data = mutableListOf<Byte>()
        
        fun appendData(newData: ByteArray) {
            data.addAll(newData.toList())
            
            // Prevent buffer from growing too large
            if (data.size > MAX_SECTION_SIZE) {
                Log.w(TAG, "Section buffer overflow, resetting")
                reset()
            }
        }
        
        fun removeProcessed(bytes: Int) {
            if (bytes <= data.size) {
                repeat(bytes) { data.removeAt(0) }
            }
        }
        
        fun reset() {
            data.clear()
        }
    }
    
    /**
     * Statistics about reassembly state
     */
    data class ReassemblyStats(
        val totalBuffers: Int,
        val totalConsumers: Int,
        val activePids: List<Int>
    )
}

/**
 * Interface for consuming reassembled PSI sections
 */
interface SectionConsumer {
    fun onSection(tableId: Int, section: ByteArray)
}
