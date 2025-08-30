# LiveTV - Decoder Digitale Terrestre per Android TV

Un'applicazione Android TV che simula un decoder digitale terrestre, in grado di riprodurre stream IPTV da playlist M3U/M3U8 derivanti da TVHeadend in modalità passthrough, mantenendo il segnale DVB-T2 originale via IP.

## Caratteristiche Principali

### 🔴 Riproduzione Video
- **Supporto M3U/M3U8**: Caricamento playlist locali e remote
- **Streaming IPTV**: Riproduzione di canali digitali terrestri via IP
- **Compatibilità TVHeadend**: Ottimizzato per playlist generate da TVHeadend
- **Qualità HD/4K**: Supporto per stream ad alta definizione
- **Codec multipli**: H.264, H.265/HEVC, MPEG-2

### 📺 Interfaccia Decoder
- **UI Android TV**: Interfaccia ottimizzata per televisori
- **Navigazione telecomando**: Controllo completo con telecomando TV
- **Lista canali**: Visualizzazione organizzata per gruppi
- **Cambio canale rapido**: Navigazione con tasti CH+/CH-
- **Informazioni canale**: Display numero, nome, qualità, gruppo

### 📋 Guida TV (EPG)
- **Supporto XMLTV**: Caricamento guida programmi da file XML
- **Programma corrente**: Visualizzazione in tempo reale
- **Prossimo programma**: Anteprima programmazione
- **Ricerca programmi**: Filtro per titolo e categoria
- **Cache locale**: Memorizzazione offline dei dati EPG

### ⚠️ HbbTV (Hybrid Broadcast Broadband TV) - DISATTIVATO
- **Nota importante**: Il supporto HbbTV non è completamente implementato ed è attualmente disattivato
- **Stato**: Funzionalità in sviluppo, non utilizzabile nella versione corrente
- **Tasti colorati**: I tasti rosso/verde/giallo/blu sono presenti ma non funzionali
- **Pianificato**: Implementazione completa in future versioni

### ⚙️ Gestione Configurazione
- **Playlist multiple**: Gestione di più sorgenti
- **Aggiornamento automatico**: Download periodico playlist/EPG
- **Preferiti**: Gestione canali preferiti
- **Cache intelligente**: Ottimizzazione prestazioni
- **Backup/Restore**: Salvataggio configurazioni

## Architettura Tecnica

### 🏗️ Componenti Principali
- **Media3 (ExoPlayer)**: Engine riproduzione video
- **Room Database**: Persistenza dati locali
- **Kotlin Coroutines**: Programmazione asincrona
- **MVVM Pattern**: Architettura Model-View-ViewModel
- **Repository Pattern**: Astrazione accesso dati

### 📱 Struttura Moduli
```
app/
├── data/
│   ├── entity/          # Entità database (Channel, EPGProgram, Playlist)
│   ├── dao/             # Data Access Objects
│   ├── repository/      # Repository pattern
│   └── database/        # Configurazione Room
├── ui/
│   ├── main/           # Schermata principale
│   ├── player/         # Player video
│   ├── settings/       # Configurazioni
│   ├── epg/           # Guida TV
│   └── hbbtv/         # Servizi HbbTV (disattivato)
├── utils/
│   ├── M3UParser       # Parser playlist M3U
│   └── XMLTVParser     # Parser EPG XMLTV
└── service/
    └── EPGService      # Servizio download EPG
```

## Installazione e Configurazione

### 📋 Requisiti
- Android TV 5.0+ (API 21+)
- Connessione internet per stream IPTV
- TVHeadend server configurato (opzionale)

### 🚀 Prima Configurazione
1. **Avvia l'applicazione** su Android TV
2. **Vai in Impostazioni** (tasto MENU)
3. **Configura Playlist**:
   - URL remoto: `http://tvheadend-server:9981/playlist`
   - File locale: Seleziona file M3U dalla memoria
4. **Configura EPG** (opzionale):
   - URL XMLTV: `http://tvheadend-server:9981/xmltv`
5. **Testa configurazione** con pulsante "Test Playlist"
6. **Salva impostazioni**

### 🔧 Configurazione TVHeadend
Per utilizzare con TVHeadend, configura:
```
# Playlist M3U
http://[server]:9981/playlist/channels.m3u

# EPG XMLTV  
http://[server]:9981/xmltv/channels.xml

# Stream passthrough
http://[server]:9981/stream/channel/[uuid]
```

## Utilizzo

### 🎮 Controlli Telecomando
- **↑↓**: Naviga lista canali
- **ENTER**: Riproduci canale selezionato
- **CH+/CH-**: Cambia canale durante riproduzione
- **MENU**: Apri impostazioni
- **GUIDE**: Apri guida TV (EPG)
- **⭐**: Filtra canali preferiti
- **🔴🟢🟡🔵**: Tasti colorati (HbbTV disattivato)
- **INFO**: Mostra informazioni canale
- **BACK**: Torna indietro

### 📺 Riproduzione
1. **Seleziona canale** dalla lista principale
2. **Premi ENTER** per avviare riproduzione
3. **Usa CH+/CH-** per cambiare canale
4. **Premi ENTER** durante riproduzione per controlli
5. **Tasti colorati** non funzionali (HbbTV disattivato)

### 📋 Gestione Preferiti
1. **Seleziona canale** nella lista
2. **Tieni premuto ENTER** per menu contestuale
3. **Aggiungi/Rimuovi** dai preferiti
4. **Tasto ⭐** per visualizzare solo preferiti

## Formati Supportati

### 📼 Playlist M3U
```m3u
#EXTM3U
#EXTINF:-1 tvg-id="rai1" tvg-name="Rai 1" tvg-logo="logo.png" group-title="Rai",Rai 1 HD
http://server:9981/stream/channel/uuid1

#EXTINF:-1 tvg-id="canale5" tvg-name="Canale 5",Canale 5 HD
http://server:9981/stream/channel/uuid2
```

### 📋 EPG XMLTV
```xml
<?xml version="1.0" encoding="UTF-8"?>
<tv>
  <channel id="rai1">
    <display-name>Rai 1</display-name>
  </channel>
  <programme start="20231201200000 +0100" stop="20231201220000 +0100" channel="rai1">
    <title>Programma TV</title>
    <desc>Descrizione programma</desc>
    <category>Intrattenimento</category>
  </programme>
</tv>
```

## Sviluppo

### 🛠️ Build Requirements
- Android Studio Arctic Fox+
- Kotlin 1.8+
- Gradle 8.0+
- Android SDK 34

### 📦 Dipendenze Principali
```gradle
// Media playback
implementation 'androidx.media3:media3-exoplayer:1.2.0'
implementation 'androidx.media3:media3-ui:1.2.0'

// Android TV
implementation 'androidx.leanback:leanback:1.0.0'

// Database
implementation 'androidx.room:room-runtime:2.6.0'
implementation 'androidx.room:room-ktx:2.6.0'

// Networking
implementation 'com.squareup.okhttp3:okhttp:4.12.0'

// Image loading
implementation 'com.github.bumptech.glide:glide:4.16.0'

// XML parsing
implementation 'org.jsoup:jsoup:1.16.2'
```

### 🔨 Build Commands
```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Install on device
./gradlew installDebug
```

## Troubleshooting

### ❌ Problemi Comuni

**Nessun canale visualizzato**
- Verifica URL playlist M3U
- Controlla connessione internet
- Testa playlist con "Test Playlist"

**Stream non si avvia**
- Verifica formato stream supportato
- Controlla firewall/proxy
- Prova con stream di test

**EPG non carica**
- Verifica URL XMLTV
- Controlla formato file EPG
- Riavvia servizio EPG

**HbbTV non funziona**
- **Nota**: HbbTV è disattivato nella versione corrente
- La funzionalità è in sviluppo per future versioni
- I tasti colorati non sono funzionali

### 📊 Log e Debug
I log dell'applicazione sono disponibili tramite:
```bash
adb logcat | grep "LiveTV"
```

## Roadmap

### 🚧 Funzionalità Future
- [ ] **HbbTV completo**: Implementazione completa servizi interattivi
- [ ] **Registrazione programmi**: PVR integrato
- [ ] **Timeshift**: Pausa/riavvolgimento live TV
- [ ] **Multi-audio**: Selezione tracce audio
- [ ] **Sottotitoli**: Supporto DVB subtitles
- [ ] **Parental control**: Controllo genitori
- [ ] **Skin personalizzabili**: Temi interfaccia
- [ ] **Cast support**: Chromecast integration
- [ ] **Voice control**: Controllo vocale Android TV

### 🔄 Miglioramenti Pianificati
- [ ] **Performance**: Ottimizzazione memoria
- [ ] **Stabilità**: Gestione errori avanzata
- [ ] **UX**: Animazioni e transizioni
- [ ] **Accessibilità**: Supporto TalkBack
- [ ] **Localizzazione**: Supporto multi-lingua

## Licenza

Questo progetto è rilasciato sotto licenza MIT. Vedi file `LICENSE` per dettagli.

## Contributi

I contributi sono benvenuti! Per contribuire:
1. Fork del repository
2. Crea feature branch
3. Commit delle modifiche
4. Push al branch
5. Apri Pull Request

## Supporto

Per supporto e segnalazione bug:
- 🐛 **Issues**: GitHub Issues
- 📧 **Email**: support@livetv-app.com
- 💬 **Forum**: Community Forum

---

**LiveTV** - Porta il digitale terrestre su Android TV! 📺✨