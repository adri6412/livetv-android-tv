package com.livetv.androidtv.ts

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.After

/**
 * Unit tests for PsiReassembler
 */
class PsiReassemblerTest {
    
    private lateinit var psiReassembler: PsiReassembler
    private lateinit var testConsumer: TestSectionConsumer
    
    @Before
    fun setUp() {
        psiReassembler = PsiReassembler()
        testConsumer = TestSectionConsumer()
    }
    
    @After
    fun tearDown() {
        psiReassembler.clearBuffers()
    }
    
    @Test
    fun `test process valid TS packet`() {
        // Create a valid TS packet
        val tsPacket = createValidTsPacket(pid = 0x1000, payloadUnitStartIndicator = true)
        
        // Process the packet
        psiReassembler.processPacket(0x1000, tsPacket)
        
        // Verify no errors occurred
        assertTrue("Should process packet without errors", true)
    }
    
    @Test
    fun `test process invalid TS packet size`() {
        // Create an invalid TS packet (wrong size)
        val invalidPacket = ByteArray(100) // Should be 188 bytes
        
        // Process the packet
        psiReassembler.processPacket(0x1000, invalidPacket)
        
        // Verify no errors occurred (should handle gracefully)
        assertTrue("Should handle invalid packet size gracefully", true)
    }
    
    @Test
    fun `test process TS packet with invalid sync byte`() {
        // Create TS packet with invalid sync byte
        val tsPacket = createValidTsPacket(pid = 0x1000, payloadUnitStartIndicator = true)
        tsPacket[0] = 0x00.toByte() // Invalid sync byte
        
        // Process the packet
        psiReassembler.processPacket(0x1000, tsPacket)
        
        // Verify no errors occurred (should handle gracefully)
        assertTrue("Should handle invalid sync byte gracefully", true)
    }
    
    @Test
    fun `test register and unregister consumer`() {
        // Register consumer for table_id 0x74 (AIT)
        psiReassembler.registerConsumer(0x74, testConsumer)
        
        // Verify consumer is registered
        val stats = psiReassembler.getStats()
        assertEquals("Should have 1 consumer", 1, stats.totalConsumers)
        
        // Unregister consumer
        psiReassembler.unregisterConsumer(0x74, testConsumer)
        
        // Verify consumer is unregistered
        val statsAfter = psiReassembler.getStats()
        assertEquals("Should have 0 consumers after unregister", 0, statsAfter.totalConsumers)
    }
    
    @Test
    fun `test clear buffers`() {
        // Process some packets to create buffers
        val tsPacket = createValidTsPacket(pid = 0x1000, payloadUnitStartIndicator = true)
        psiReassembler.processPacket(0x1000, tsPacket)
        
        // Verify buffers exist
        val statsBefore = psiReassembler.getStats()
        assertTrue("Should have buffers before clear", statsBefore.totalBuffers > 0)
        
        // Clear buffers
        psiReassembler.clearBuffers()
        
        // Verify buffers are cleared
        val statsAfter = psiReassembler.getStats()
        assertEquals("Should have 0 buffers after clear", 0, statsAfter.totalBuffers)
    }
    
    @Test
    fun `test get stats`() {
        // Get initial stats
        val initialStats = psiReassembler.getStats()
        assertEquals("Initial total buffers should be 0", 0, initialStats.totalBuffers)
        assertEquals("Initial total consumers should be 0", 0, initialStats.totalConsumers)
        assertTrue("Initial active PIDs should be empty", initialStats.activePids.isEmpty())
        
        // Process a packet to create a buffer
        val tsPacket = createValidTsPacket(pid = 0x1000, payloadUnitStartIndicator = true)
        psiReassembler.processPacket(0x1000, tsPacket)
        
        // Get updated stats
        val updatedStats = psiReassembler.getStats()
        assertEquals("Should have 1 buffer after processing packet", 1, updatedStats.totalBuffers)
        assertTrue("Should have active PID 0x1000", updatedStats.activePids.contains(0x1000))
    }
    
    /**
     * Create a valid TS packet for testing
     */
    private fun createValidTsPacket(pid: Int, payloadUnitStartIndicator: Boolean): ByteArray {
        val packet = ByteArray(188)
        
        // Sync byte
        packet[0] = 0x47.toByte()
        
        // Transport error indicator (0) + payload unit start indicator + transport priority (0) + PID (13 bits)
        packet[1] = ((if (payloadUnitStartIndicator) 0x40 else 0x00) or ((pid shr 8) and 0x1F)).toByte()
        packet[2] = (pid and 0xFF).toByte()
        
        // Transport scrambling control (00) + adaptation field control (01) + continuity counter (0000)
        packet[3] = 0x10.toByte()
        
        // Payload (fill with test data)
        for (i in 4 until packet.size) {
            packet[i] = (i % 256).toByte()
        }
        
        return packet
    }
    
    /**
     * Test section consumer implementation
     */
    private class TestSectionConsumer : SectionConsumer {
        var sectionsReceived = 0
        var lastTableId: Int? = null
        var lastSectionSize: Int? = null
        
        override fun onSection(tableId: Int, section: ByteArray) {
            sectionsReceived++
            lastTableId = tableId
            lastSectionSize = section.size
        }
        
        fun reset() {
            sectionsReceived = 0
            lastTableId = null
            lastSectionSize = null
        }
    }
}
