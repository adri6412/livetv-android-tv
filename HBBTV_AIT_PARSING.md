# HbbTV AIT Parsing System - DISATTIVATO

⚠️ **NOTA IMPORTANTE**: Il supporto HbbTV non è completamente implementato ed è attualmente disattivato nella versione corrente dell'app.

This document describes the planned robust HbbTV AIT (Application Information Table) parsing system for the LiveTV Android TV app. **This functionality is currently disabled and not fully implemented.**

## Overview

The system replaces the previous regex-based URL extraction with proper MPEG-TS PSI/SI parsing according to ETSI standards (TS 102 809 / TS 102 796). It provides:

- **Accurate AIT parsing** from transport stream data
- **PSI section reassembly** for fragmented sections
- **CRC32 validation** for data integrity
- **Automatic PID discovery** via PAT/PMT parsing
- **Fallback scanning** for non-standard AIT PIDs

## Architecture

### Core Components

1. **`AitParser`** - Parses AIT sections and extracts HbbTV application URLs
2. **`PsiReassembler`** - Reassembles PSI sections from TS packets
3. **`HbbTvManager`** - Coordinates the parsing system and provides callbacks
4. **`EnhancedTsExtractor`** - Integrates with ExoPlayer's TS pipeline

### Data Flow

```
TS Stream → PSI Reassembly → AIT Parsing → URL Extraction → Callback
```

## Implementation Details

### AIT Parser (`AitParser.kt`)

The AIT parser implements the ETSI standard for parsing Application Information Tables:

- **Table ID**: 0x74 (AIT)
- **Protocol Support**: HTTP (protocol_id 0x0003)
- **URL Extraction**: Combines `url_base` + `url_extension`
- **Application Selection**: Prefers autostart applications

#### Key Methods

```kotlin
fun onSection(tableId: Int, section: ByteArray)
```

Processes a complete PSI section with table_id 0x74.

### PSI Reassembler (`PsiReassembler.kt`)

Handles the complex task of reassembling PSI sections from fragmented TS packets:

- **TS Header Parsing**: Extracts PID, payload flags, and adaptation field info
- **Section Buffering**: Collects section fragments across multiple packets
- **Pointer Field Handling**: Manages stuffing bytes and section boundaries
- **CRC32 Validation**: Ensures section integrity before processing

#### Key Methods

```kotlin
fun processPacket(pid: Int, packet: ByteArray)
fun registerConsumer(tableId: Int, consumer: SectionConsumer)
fun clearBuffers()
```

### HbbTV Manager (`HbbTvManager.kt`)

Coordinates the entire HbbTV parsing system:

- **Initialization**: Sets up PAT/PMT consumers and AIT parsing
- **State Management**: Tracks AIT detection and timeout handling
- **Callback Interface**: Provides clean interface for PlayerActivity
- **PID Discovery**: Automatically finds AIT PIDs via PMT parsing

#### Key Methods

```kotlin
fun initialize(callback: HbbTvCallback)
fun startAitDetection()
fun clearState()
fun getStats(): HbbTvStats
```

## Integration with PlayerActivity

### Initialization

```kotlin
// Initialize HbbTV Manager
hbbTvManager = HbbTvManager()
hbbTvManager.initialize(object : HbbTvCallback {
    override fun onHbbTvUrlFound(info: HbbTvAppUrl) {
        // Handle found HbbTV URL
    }
    
    override fun onAitPresentButNoUrl(reason: String) {
        // Handle AIT present but no HTTP URL
    }
    
    override fun onNoAitDetected() {
        // Handle no AIT detected
    }
})
```

### Channel Switching

```kotlin
private fun playChannel(channel: Channel) {
    // Clear HbbTV state for new channel
    hbbTvManager.clearState()
    
    // ... rest of channel switching logic
}
```

### Track Analysis

```kotlin
private fun extractDvbDataFromTracks(tracks: Tracks) {
    // Start HbbTV AIT detection
    hbbTvManager.startAitDetection()
    
    // ... rest of track analysis
}
```

## Data Structures

### HbbTvAppUrl

```kotlin
data class HbbTvAppUrl(
    val url: String,           // Complete HbbTV application URL
    val autostart: Boolean,    // Whether this is an autostart app
    val appId: Int?,           // Application identifier
    val orgId: Int?            // Organization identifier
)
```

### HbbTvStats

```kotlin
data class HbbTvStats(
    val isInitialized: Boolean,    // Manager initialization status
    val aitDetected: Boolean,      // Whether AIT was detected
    val totalPids: Int,           // Number of active PIDs
    val totalConsumers: Int,      // Number of registered consumers
    val activePids: List<Int>     // List of active PID values
)
```

## Error Handling

The system gracefully handles various error conditions:

- **Invalid TS packets**: Logged and skipped
- **Corrupted sections**: CRC32 validation prevents processing
- **Malformed AIT data**: Parsing errors are logged
- **Missing HTTP protocols**: Clear reason reporting

## Logging

Comprehensive logging is provided at multiple levels:

- **Debug**: Detailed parsing information
- **Info**: HbbTV URL discovery and AIT status
- **Warning**: Parsing issues and fallbacks
- **Error**: System errors and exceptions

## Testing

### Unit Tests

- **`AitParserTest`**: Tests AIT parsing with various scenarios
- **`PsiReassemblerTest`**: Tests TS packet processing and section reassembly

### Test Scenarios

1. **Valid AIT with HTTP application** - Expects URL extraction
2. **AIT with only DSM-CC** - Expects "no HTTP" reason
3. **AIT with no applications** - Expects "no applications" reason
4. **Corrupted CRC** - Expects graceful handling
5. **Multiple applications** - Expects best app selection

## Performance Considerations

- **Memory efficient**: Section buffers are limited to 4KB
- **Timeout handling**: 5-second timeout for AIT detection
- **State cleanup**: Automatic cleanup on channel switching
- **Resource management**: Proper release in onDestroy

## Future Enhancements

Potential improvements for future versions:

1. **Multiple AIT support**: Handle multiple AIT tables
2. **Advanced filtering**: More sophisticated application selection
3. **Caching**: Cache parsed AIT data for repeated channels
4. **Metrics**: Detailed performance and success rate metrics
5. **Configuration**: Configurable timeout and buffer sizes

## Troubleshooting

### Common Issues

1. **No AIT detected**: Check if channel actually carries HbbTV data
2. **CRC errors**: May indicate stream corruption or parsing issues
3. **Timeout issues**: Increase timeout for slow streams
4. **Memory issues**: Check for buffer overflow in logs

### Debug Information

Enable debug logging to see detailed parsing information:

```kotlin
Log.d("AitParser", "Processing AIT section: ${section.size} bytes")
Log.d("PsiReassembler", "Complete section, table_id=0x${tableId.toString(16)}")
Log.d("HbbTvManager", "AIT detected on PID 0x${pid.toString(16)}")
```

## Standards Compliance

This implementation follows:

- **ETSI TS 102 809**: HbbTV specification
- **ETSI TS 102 796**: Application Information Table
- **ISO/IEC 13818-1**: MPEG-2 Transport Stream
- **DVB standards**: Program Specific Information

## Conclusion

The new HbbTV AIT parsing system provides a robust, standards-compliant solution for extracting HbbTV application URLs from DVB transport streams. It replaces the unreliable regex-based approach with proper byte-level parsing and provides comprehensive error handling and logging.
