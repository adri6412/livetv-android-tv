# HbbTV AIT Parsing Implementation

This document describes the implementation of real AIT (Application Information Table) parsing for the LiveTV Android TV app, replacing the previous regex-based approach with proper MPEG-TS PSI/SI parsing.

## Overview

The new implementation integrates directly with ExoPlayer's TS parsing pipeline to:
- **Force TS passthrough** using TsExtractor with custom payload reader factory
- **Parse AIT sections in real-time** as they arrive from the transport stream
- **Extract HbbTV application URLs** from transport protocol descriptors
- **Handle autostart applications** according to ETSI standards
- **Provide fallback scanning** for non-standard AIT PIDs

## Architecture

### Core Components

1. **`MyTsPayloadReaderFactory`** - Extends DefaultTsPayloadReaderFactory to intercept DVB section streams
2. **`AitSectionPayloadReader`** - Implements SectionPayloadReader to parse AIT sections (table_id 0x74)
3. **`HbbTvManager`** - Manages timeout handling and callback coordination
4. **Integration in PlayerActivity** - Custom TS extractor with AIT parsing

### Data Flow

```
TS Stream → ExoPlayer TsExtractor → MyTsPayloadReaderFactory → AitSectionPayloadReader → HbbTV URL Extraction → UI Callback
```

## Implementation Details

### 1. MyTsPayloadReaderFactory

**Location**: `app/src/main/java/com/livetv/androidtv/ts/MyTsPayloadReaderFactory.kt`

**Key Features**:
- Extends `DefaultTsPayloadReaderFactory` to work with ExoPlayer's TS pipeline
- Intercepts `TS_STREAM_TYPE_DVB_SECTION` (0x05) streams
- Provides fallback section scanning for PIDs without explicit stream type
- Creates `AitSectionPayloadReader` instances for AIT parsing

**Usage**:
```kotlin
val customTsExtractor = TsExtractor(
    TsExtractor.MODE_SINGLE_PMT,
    MyTsPayloadReaderFactory(aitListener)
)
```

### 2. AitSectionPayloadReader

**Location**: `app/src/main/java/com/livetv/androidtv/hbbtv/AitSectionPayloadReader.kt`

**Key Features**:
- Implements `SectionPayloadReader` interface for ExoPlayer integration
- Parses AIT sections according to ETSI TS 102 809 / TS 102 796
- Extracts transport protocol descriptors (tag 0x02) for HTTP URLs
- Parses simple application location descriptors (tag 0x15) for initial paths
- Handles application descriptors (tag 0x00) for autostart flags

**Descriptor Parsing**:
- **Transport Protocol (0x02)**: Extracts `url_base` + `url_extension` for HTTP (protocol_id 0x0003)
- **Simple Application Location (0x15)**: Extracts `initial_path` for complete URL construction
- **Application (0x00)**: Checks autostart and present flags

### 3. HbbTvManager

**Location**: `app/src/main/java/com/livetv/androidtv/hbbtv/HbbTvManager.kt`

**Key Features**:
- Manages 5-second timeout for AIT detection
- Provides callback interface for PlayerActivity
- Coordinates state management during channel switching
- Simplified to focus on timeout handling (PSI parsing moved to ExoPlayer)

### 4. PlayerActivity Integration

**Key Changes**:
- `createTsMediaSource()` now creates custom TS extractor with AIT parser
- Direct callback handling for AIT parsing results
- Automatic HbbTV application launching for autostart apps
- Real-time UI updates based on AIT parsing

## Testing

### Test Scenarios

#### 1. Rai 1 (or any HTTP HbbTV broadcaster)
**Expected Result**: AIT detected within ~2 seconds, full URL logged
```
🎯 HbbTV URL found: https://www.raiplay.it/hbbtv/rai1 (autostart: true)
```

#### 2. Services with only DSM-CC
**Expected Result**: AIT present but no HTTP transport
```
ℹ️ AIT present but no HbbTV URL: AIT present but no HTTP transport (only DSM-CC)
```

#### 3. Services without AIT
**Expected Result**: Clean timeout after 5 seconds
```
ℹ️ No AIT detected within timeout period
```

### Log Analysis

**Look for these log patterns**:
```
MyTsPayloadReaderFactory: 🎯 DVB Section stream detected on PID 0x1234 - creating AIT parser
AitSectionPayloadReader: Processing AIT section: 256 bytes
AitSectionPayloadReader: HbbTV URL found: https://example.com/app (autostart: true)
PlayerActivity: 🎯 HbbTV URL found from AIT parsing: https://example.com/app
```

**PID Discovery**:
```
MyTsPayloadReaderFactory: Building payload reader: streamType=0x05, PID=0x1234
MyTsPayloadReaderFactory: 🎯 DVB Section stream detected on PID 0x1234 - creating AIT parser
```

### Debug Information

**AIT Section Details**:
```
AIT section: syntax_indicator=true, length=256
AIT application loop length: 128
App: orgId=0x12345678, appId=0x1234, control=0x01, autostart=true, descriptors_length=64
Transport protocol: protocol_id=0x0003, profile=0x0001
URL base: https://www.example.com
Initial path: /hbbtv/app
```

## Troubleshooting

### Common Issues

1. **"No AIT detected" timeout**
   - Check if broadcaster actually transmits AIT
   - Verify stream contains PSI sections
   - Check logs for PID discovery

2. **AIT detected but no URL**
   - Verify transport protocol is HTTP (0x0003)
   - Check if URL base is present in descriptor
   - Look for parsing errors in logs

3. **Parsing errors**
   - Check AIT section integrity
   - Verify descriptor lengths
   - Look for malformed PSI data

### Debug Commands

**Enable verbose logging**:
```kotlin
Log.d(TAG, "AIT parsing debug info")
```

**Check PID discovery**:
```kotlin
Log.i(TAG, "🎯 DVB Section stream detected on PID 0x${pid.toString(16)}")
```

**Verify URL construction**:
```kotlin
Log.d(TAG, "URL base: $urlBase")
Log.d(TAG, "Initial path: $initialPath")
Log.d(TAG, "Complete URL: $url")
```

## Performance

### Expected Results

- **AIT Detection**: 1-3 seconds for active broadcasters
- **URL Extraction**: Immediate after AIT section parsing
- **Memory Usage**: Minimal (no buffering of TS packets)
- **CPU Impact**: Low (only processes AIT sections, not entire stream)

### Optimization Notes

- AIT sections are typically small (< 1KB)
- Parsing happens in real-time as sections arrive
- No need to buffer or store TS packets
- Automatic cleanup on channel switching

## Future Enhancements

### Potential Improvements

1. **Multiple AIT Support**: Handle multiple applications per service
2. **Advanced Descriptors**: Support for additional descriptor types
3. **Caching**: Cache AIT data for faster channel switching
4. **Metrics**: Track AIT detection success rates
5. **Fallback**: Implement DSM-CC transport support

### Standards Compliance

- **ETSI TS 102 809**: HbbTV Application Signalling
- **ETSI TS 102 796**: HbbTV Application Discovery
- **ISO/IEC 13818-1**: MPEG-2 Systems (PSI/SI parsing)

## Conclusion

This implementation provides robust, real-time AIT parsing that integrates seamlessly with ExoPlayer's TS pipeline. It replaces the previous regex-based approach with proper MPEG-TS parsing according to ETSI standards, ensuring accurate HbbTV application discovery and URL extraction.

The system automatically handles:
- PID discovery via PMT parsing
- AIT section reassembly and validation
- Descriptor parsing for URL construction
- Autostart application detection
- Clean timeout handling for services without AIT

This results in a much more reliable and standards-compliant HbbTV experience for users.
