package com.livetv.androidtv.utils

import android.content.Context
import com.livetv.androidtv.data.entity.Channel
import com.livetv.androidtv.data.entity.EPGProgram
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class XMLTVParser {
    
    companion object {
        private const val TAG_TV = "tv"
        private const val TAG_CHANNEL = "channel"
        private const val TAG_DISPLAY_NAME = "display-name"
        private const val TAG_PROGRAMME = "programme"
        private const val TAG_TITLE = "title"
        private const val TAG_SUBTITLE = "subtitle"
        private const val TAG_DESC = "desc"
        private const val TAG_CATEGORY = "category"
        private const val TAG_ICON = "icon"
        private const val TAG_EPISODE_NUM = "episode-num"
        private const val TAG_DATE = "date"
        private const val TAG_RATING = "rating"
        private const val TAG_DIRECTOR = "director"
        private const val TAG_ACTOR = "actor"
        private const val TAG_GENRE = "genre"
        private const val TAG_LIVE = "live"
        private const val TAG_REPEAT = "repeat"
        private const val TAG_SUBTITLES = "subtitles"
        private const val TAG_AUDIO_DESC = "audio-desc"
        private const val TAG_HBBTV_URL = "hbbtv-url"
        
        private val dateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.getDefault())
        
        /**
         * Funzione di utilità per il matching fuzzy dei nomi dei canali
         * Gestisce variazioni comuni nei nomi
         */
        private fun findChannelByFuzzyMatch(channelName: String, existingChannels: List<Channel>): Channel? {
            val normalizedSearchName = channelName.replace(Regex("[0-9\\s-]+"), "").trim().lowercase()
            
            // Prima prova con match esatto normalizzato
            val exactMatch = existingChannels.find { channel ->
                val normalizedChannelName = channel.name.replace(Regex("[0-9\\s-]+"), "").trim().lowercase()
                normalizedChannelName == normalizedSearchName
            }
            if (exactMatch != null) return exactMatch
            
            // Poi prova con match parziale
            val partialMatch = existingChannels.find { channel ->
                val normalizedChannelName = channel.name.replace(Regex("[0-9\\s-]+"), "").trim().lowercase()
                normalizedChannelName.contains(normalizedSearchName) || normalizedSearchName.contains(normalizedChannelName)
            }
            if (partialMatch != null) return partialMatch
            
            // Infine prova con match che ignora spazi e caratteri speciali
            val fuzzyMatch = existingChannels.find { channel ->
                val cleanChannelName = channel.name.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
                val cleanSearchName = normalizedSearchName.replace(Regex("[^a-zA-Z0-9]"), "")
                cleanChannelName.contains(cleanSearchName) || cleanSearchName.contains(cleanChannelName)
            }
            
            return fuzzyMatch
        }
        
        /**
         * Parsa il file XMLTV e restituisce canali e programmi
         */
        fun parseXMLTV(context: Context, fileName: String): XMLTVData {
            val inputStream = context.assets.open(fileName)
            return parseXMLTV(inputStream)
        }
        
        /**
         * Parsa il file XMLTV e restituisce solo i programmi, mappandoli ai canali esistenti
         */
        fun parseXMLTVForExistingChannels(context: Context, fileName: String, existingChannels: List<Channel>): List<EPGProgram> {
            val inputStream = context.assets.open(fileName)
            return parseXMLTVForExistingChannels(inputStream, existingChannels)
        }
        
        /**
         * Parsa un InputStream XMLTV
         */
        fun parseXMLTV(inputStream: InputStream): XMLTVData {
            val channels = mutableListOf<Channel>()
            val programs = mutableListOf<EPGProgram>()
            
            try {
                val factory = XmlPullParserFactory.newInstance()
                factory.isNamespaceAware = true
                val parser = factory.newPullParser()
                parser.setInput(inputStream, null)
                
                var eventType = parser.eventType
                var currentChannel: Channel? = null
                var currentProgram: EPGProgram? = null
                var currentTag = ""
                var channelCount = 0
                var programCount = 0
                
                android.util.Log.d("XMLTVParser", "Inizio parsing XMLTV")
                
                // Prima passata: parsiamo solo i canali
                android.util.Log.d("XMLTVParser", "=== PRIMA PASSA: PARSING CANALI ===")
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            currentTag = parser.name
                            
                            when (currentTag) {
                                TAG_CHANNEL -> {
                                    val channelId = parser.getAttributeValue(null, "id")
                                    android.util.Log.d("XMLTVParser", "Nuovo canale: $channelId")
                                    currentChannel = Channel(
                                        id = channelId,
                                        number = 0,
                                        name = "",
                                        group = null,
                                        logoUrl = null,
                                        streamUrl = "",
                                        epgId = channelId,
                                        epgDisplayName = null, // Per i canali EPG, non abbiamo ancora il nome normalizzato
                                        isFavorite = false,
                                        isHD = false,
                                        isActive = true
                                    )
                                }
                            }
                        }
                        XmlPullParser.TEXT -> {
                            val text = parser.text.trim()
                            
                            when (currentTag) {
                                TAG_DISPLAY_NAME -> {
                                    currentChannel?.let { channel ->
                                        if (channel.name.isEmpty()) {
                                            currentChannel = channel.copy(name = text)
                                            android.util.Log.d("XMLTVParser", "Nome canale impostato: $text")
                                        } else if (text.matches(Regex("\\d+"))) {
                                            currentChannel = channel.copy(number = text.toIntOrNull() ?: 0)
                                            android.util.Log.d("XMLTVParser", "Numero canale impostato: $text")
                                        } else {
                                            android.util.Log.d("XMLTVParser", "Testo ignorato per TAG_DISPLAY_NAME: $text")
                                        }
                                    }
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            when (parser.name) {
                                TAG_CHANNEL -> {
                                    currentChannel?.let { channel ->
                                        if (channel.name.isNotEmpty()) {
                                            channels.add(channel)
                                            channelCount++
                                            android.util.Log.d("XMLTVParser", "Canale aggiunto: ${channel.name} (ID: ${channel.id})")
                                        } else {
                                            android.util.Log.w("XMLTVParser", "Canale scartato: nome vuoto")
                                        }
                                    }
                                    currentChannel = null
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
                
                android.util.Log.d("XMLTVParser", "Prima passata completata: $channelCount canali parsati")
                
                // Seconda passata: parsiamo i programmi
                android.util.Log.d("XMLTVParser", "=== SECONDA PASSA: PARSING PROGRAMMI ===")
                inputStream.reset()
                parser.setInput(inputStream, null)
                eventType = parser.eventType
                currentTag = ""
                
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            currentTag = parser.name
                            
                            when (currentTag) {
                                TAG_PROGRAMME -> {
                                    val channelId = parser.getAttributeValue(null, "channel")
                                    val startAttr = parser.getAttributeValue(null, "start")
                                    val stopAttr = parser.getAttributeValue(null, "stop")
                                    
                                    // Verifica che il canale esista prima di creare il programma
                                    if (channels.any { it.id == channelId }) {
                                        android.util.Log.d("XMLTVParser", "Nuovo programma per canale: $channelId, start: $startAttr, stop: $stopAttr")
                                        
                                        var startTime = 0L
                                        var endTime = 0L
                                        
                                        // Parsing delle date dagli attributi
                                        try {
                                            if (startAttr != null) {
                                                val startDate = dateFormat.parse(startAttr)
                                                startTime = startDate?.time ?: 0L
                                                android.util.Log.d("XMLTVParser", "Start time parsato: $startAttr -> $startTime")
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("XMLTVParser", "Errore parsing start time: $startAttr", e)
                                        }
                                        
                                        try {
                                            if (stopAttr != null) {
                                                val stopDate = dateFormat.parse(stopAttr)
                                                endTime = stopDate?.time ?: 0L
                                                android.util.Log.d("XMLTVParser", "Stop time parsato: $stopAttr -> $endTime")
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("XMLTVParser", "Errore parsing stop time: $stopAttr", e)
                                        }
                                        
                                        currentProgram = EPGProgram(
                                            id = "",
                                            channelId = channelId,
                                            title = "",
                                            description = null,
                                            category = null,
                                            startTime = startTime,
                                            endTime = endTime
                                        )
                                        android.util.Log.d("XMLTVParser", "Programma creato: channelId=$channelId, start=$startTime, end=$endTime")
                                    } else {
                                        android.util.Log.w("XMLTVParser", "Canale non trovato per programma: $channelId, programma saltato")
                                        currentProgram = null
                                    }
                                }
                            }
                        }
                        XmlPullParser.TEXT -> {
                            val text = parser.text.trim()
                            
                            when (currentTag) {
                                TAG_TITLE -> {
                                    // Non sovrascrivere il titolo se è già impostato e valido
                                    if (currentProgram?.title.isNullOrEmpty() || text.isNotEmpty()) {
                                        currentProgram = currentProgram?.copy(title = text)
                                        android.util.Log.d("XMLTVParser", "Titolo programma impostato: '$text'")
                                    } else {
                                        android.util.Log.d("XMLTVParser", "Titolo ignorato (già impostato): '$text'")
                                    }
                                }
                                TAG_SUBTITLE -> {
                                    // Aggiungiamo il sottotitolo al titolo principale solo se abbiamo già un titolo
                                    currentProgram?.let { program ->
                                        if (program.title.isNotEmpty() && text.isNotEmpty()) {
                                            val fullTitle = "${program.title} - $text"
                                            currentProgram = program.copy(title = fullTitle)
                                            android.util.Log.d("XMLTVParser", "Sottotitolo aggiunto al titolo: '$fullTitle'")
                                        } else if (text.isNotEmpty()) {
                                            // Se non abbiamo un titolo principale, usa il sottotitolo
                                            currentProgram = program.copy(title = text)
                                            android.util.Log.d("XMLTVParser", "Sottotitolo usato come titolo principale: '$text'")
                                        } else {
                                            
                                        }
                                    }
                                }
                                TAG_DESC -> {
                                    currentProgram = currentProgram?.copy(description = text)
                                    android.util.Log.d("XMLTVParser", "Descrizione programma impostata: ${text.take(50)}")
                                }
                                TAG_CATEGORY -> {
                                    currentProgram = currentProgram?.copy(category = text)
                                    android.util.Log.d("XMLTVParser", "Categoria programma impostata: $text")
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            when (parser.name) {
                                TAG_PROGRAMME -> {
                                    currentProgram?.let { program ->
                                        android.util.Log.d("XMLTVParser", "Validazione programma: titolo='${program.title}', start=${program.startTime}, end=${program.endTime}")
                                        if (program.title.isNotEmpty() && program.startTime > 0 && program.endTime > 0) {
                                            val finalProgram = program.copy(id = "${program.channelId}_${program.startTime}")
                                            programs.add(finalProgram)
                                            programCount++
                                            android.util.Log.d("XMLTVParser", "Programma aggiunto: ${program.title} per canale ${program.channelId}")
                                        } else {
                                            android.util.Log.w("XMLTVParser", "Programma scartato: titolo='${program.title}', start=${program.startTime}, end=${program.endTime}")
                                        }
                                    }
                                    currentProgram = null
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
                
                android.util.Log.d("XMLTVParser", "Parsing completato: $channelCount canali, $programCount programmi")
                
            } catch (e: Exception) {
                android.util.Log.e("XMLTVParser", "Errore durante il parsing", e)
                e.printStackTrace()
            } finally {
                inputStream.close()
            }
            
            return XMLTVData(channels, programs)
        }
        
        /**
         * Parsa un InputStream XMLTV e restituisce solo i programmi, mappandoli ai canali esistenti
         */
        fun parseXMLTVForExistingChannels(inputStream: InputStream, existingChannels: List<Channel>): List<EPGProgram> {
            val programs = mutableListOf<EPGProgram>()
            
            try {
                val factory = XmlPullParserFactory.newInstance()
                factory.isNamespaceAware = true
                val parser = factory.newPullParser()
                parser.setInput(inputStream, null)
                
                var eventType = parser.eventType
                var currentProgram: EPGProgram? = null
                var currentTag = ""
                var programCount = 0
                var skippedCount = 0
                
                android.util.Log.d("XMLTVParser", "Inizio parsing XMLTV per canali esistenti (${existingChannels.size} canali)")
                
                // Creiamo una mappa per lookup veloce usando display-name invece di epgId
                // Questo dovrebbe migliorare il matching tra playlist e EPG
                val displayNameToChannelIdMap = existingChannels.associateBy { channel ->
                    // Usa il nome del canale come chiave per il matching
                    channel.name.trim()
                }
                
                // Creiamo anche una mappa con nomi normalizzati (senza numeri e trattini)
                val normalizedNameToChannelIdMap = existingChannels.associateBy { channel ->
                    // Rimuovi numeri e trattini per matching più flessibile
                    channel.name.replace(Regex("[0-9\\s-]+"), "").trim()
                }
                
                // Creiamo una mappa usando epgDisplayName per matching più preciso
                val epgDisplayNameToChannelIdMap = existingChannels.associateBy { channel ->
                    channel.getEpgMatchingName().trim()
                }
                
                android.util.Log.d("XMLTVParser", "Mappa display-name creata: ${displayNameToChannelIdMap.keys.take(5)}...")
                android.util.Log.d("XMLTVParser", "Mappa nomi normalizzati creata: ${normalizedNameToChannelIdMap.keys.take(5)}...")
                android.util.Log.d("XMLTVParser", "Mappa epgDisplayName creata: ${epgDisplayNameToChannelIdMap.keys.take(5)}...")
                
                // Log per debug: mostra quanti canali hanno epgId impostato
                val channelsWithEpgId = existingChannels.count { it.epgId != null }
                val channelsWithEpgDisplayName = existingChannels.count { it.epgDisplayName != null }
                android.util.Log.d("XMLTVParser", "=== STATISTICHE CANALI ===")
                android.util.Log.d("XMLTVParser", "Canali totali: ${existingChannels.size}")
                android.util.Log.d("XMLTVParser", "Canali con epgId: $channelsWithEpgId")
                android.util.Log.d("XMLTVParser", "Canali con epgDisplayName: $channelsWithEpgDisplayName")
                
                // Mostra alcuni esempi di canali per debug
                existingChannels.take(5).forEach { channel ->
                    android.util.Log.d("XMLTVParser", "Canale: ${channel.name}")
                    android.util.Log.d("XMLTVParser", "  ID: ${channel.id}")
                    android.util.Log.d("XMLTVParser", "  EPG ID: ${channel.epgId}")
                    android.util.Log.d("XMLTVParser", "  EPG Display Name: ${channel.epgDisplayName}")
                }
                
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            currentTag = parser.name
                            
                            when (currentTag) {
                                TAG_PROGRAMME -> {
                                    val epgChannelId = parser.getAttributeValue(null, "channel")
                                    val startAttr = parser.getAttributeValue(null, "start")
                                    val stopAttr = parser.getAttributeValue(null, "stop")
                                    
                                    android.util.Log.d("XMLTVParser", "=== NUOVO PROGRAMMA ===")
                                    android.util.Log.d("XMLTVParser", "Channel ID: $epgChannelId")
                                    android.util.Log.d("XMLTVParser", "Start: $startAttr")
                                    android.util.Log.d("XMLTVParser", "Stop: $stopAttr")
                                    
                                    // Verifica se l'ID EPG corrisponde a un canale esistente
                                    // Strategia di matching migliorata:
                                    // 1. Prima prova con epgDisplayName (più preciso)
                                    // 2. Poi prova con display-name esatto
                                    // 3. Poi prova con nome normalizzato (senza numeri/trattini)
                                    // 4. Poi prova con epgId
                                    // 5. Infine prova con id
                                    var existingChannel = epgDisplayNameToChannelIdMap[epgChannelId]
                                    
                                    if (existingChannel == null) {
                                        // Prova con display-name esatto
                                        existingChannel = displayNameToChannelIdMap[epgChannelId]
                                        if (existingChannel != null) {
                                            android.util.Log.d("XMLTVParser", "Match trovato con display-name: '$epgChannelId' -> '${existingChannel.name}'")
                                        }
                                    } else {
                                        android.util.Log.d("XMLTVParser", "Match trovato con epgDisplayName: '$epgChannelId' -> '${existingChannel.name}'")
                                    }
                                    
                                    if (existingChannel == null) {
                                        // Prova con nome normalizzato
                                        val normalizedEpgId = epgChannelId.replace(Regex("[0-9\\s-]+"), "").trim()
                                        existingChannel = normalizedNameToChannelIdMap[normalizedEpgId]
                                        if (existingChannel != null) {
                                            android.util.Log.d("XMLTVParser", "Match trovato con nome normalizzato: '$epgChannelId' -> '${existingChannel.name}'")
                                        }
                                    }
                                    
                                    if (existingChannel == null) {
                                        // Fallback: prova con epgId
                                        existingChannel = existingChannels.find { it.epgId == epgChannelId }
                                        if (existingChannel != null) {
                                            android.util.Log.d("XMLTVParser", "Match trovato con epgId: '$epgChannelId' -> '${existingChannel.name}'")
                                        }
                                    }
                                    
                                    if (existingChannel == null) {
                                        // Fallback: prova con id
                                        existingChannel = existingChannels.find { it.id == epgChannelId }
                                        if (existingChannel != null) {
                                            android.util.Log.d("XMLTVParser", "Match trovato con id: '$epgChannelId' -> '${existingChannel.name}'")
                                        }
                                    }
                                    
                                    if (existingChannel == null) {
                                        // Ultimo tentativo: usa il matching fuzzy
                                        existingChannel = findChannelByFuzzyMatch(epgChannelId, existingChannels)
                                        if (existingChannel != null) {
                                            android.util.Log.d("XMLTVParser", "Match trovato con fuzzy matching: '$epgChannelId' -> '${existingChannel.name}'")
                                        }
                                    }
                                    
                                    if (existingChannel != null) {
                                        android.util.Log.d("XMLTVParser", "Programma trovato per canale: ${existingChannel.name} (EPG ID: $epgChannelId)")
                                        
                                        var startTime = 0L
                                        var endTime = 0L
                                        
                                        // Parsing delle date dagli attributi
                                        try {
                                            if (startAttr != null) {
                                                val startDate = dateFormat.parse(startAttr)
                                                startTime = startDate?.time ?: 0L
                                                android.util.Log.d("XMLTVParser", "Start time parsato: $startAttr -> $startTime")
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("XMLTVParser", "Errore parsing start time: $startAttr", e)
                                        }
                                        
                                        try {
                                            if (stopAttr != null) {
                                                val stopDate = dateFormat.parse(stopAttr)
                                                endTime = stopDate?.time ?: 0L
                                                android.util.Log.d("XMLTVParser", "Stop time parsato: $stopAttr -> $endTime")
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("XMLTVParser", "Errore parsing stop time: $stopAttr", e)
                                        }
                                        
                                        currentProgram = EPGProgram(
                                            id = "",
                                            channelId = epgChannelId, // Usa l'EPG ID per mantenere la relazione con i dati EPG
                                            title = "",
                                            description = null,
                                            category = null,
                                            startTime = startTime,
                                            endTime = endTime
                                        )
                                        android.util.Log.d("XMLTVParser", "Programma creato per canale: ${existingChannel.name}")
                                    } else {
                                        android.util.Log.d("XMLTVParser", "Canale EPG non trovato: $epgChannelId, programma saltato")
                                        currentProgram = null
                                        skippedCount++
                                    }
                                }
                                TAG_TITLE -> {
                                    android.util.Log.d("XMLTVParser", "Tag TAG_TITLE trovato")
                                }
                                TAG_SUBTITLE -> {
                                    android.util.Log.d("XMLTVParser", "Tag TAG_SUBTITLE trovato")
                                }
                                TAG_DESC -> {
                                    android.util.Log.d("XMLTVParser", "Tag TAG_DESC trovato")
                                }
                                TAG_CATEGORY -> {
                                    android.util.Log.d("XMLTVParser", "Tag TAG_CATEGORY trovato")
                                }
                            }
                        }
                        XmlPullParser.TEXT -> {
                            val text = parser.text.trim()
                            
                            when (currentTag) {
                                TAG_TITLE -> {
                                    // Non sovrascrivere il titolo se è già impostato e valido
                                    if (currentProgram?.title.isNullOrEmpty() || text.isNotEmpty()) {
                                        currentProgram = currentProgram?.copy(title = text)
                                        android.util.Log.d("XMLTVParser", "Titolo programma impostato: '$text'")
                                    } else {
                                        android.util.Log.d("XMLTVParser", "Titolo ignorato (già impostato): '$text'")
                                    }
                                }
                                TAG_SUBTITLE -> {
                                    // Aggiungiamo il sottotitolo al titolo principale solo se abbiamo già un titolo
                                    currentProgram?.let { program ->
                                        if (program.title.isNotEmpty() && text.isNotEmpty()) {
                                            val fullTitle = "${program.title} - $text"
                                            currentProgram = program.copy(title = fullTitle)
                                            android.util.Log.d("XMLTVParser", "Sottotitolo aggiunto al titolo: '$fullTitle'")
                                        } else if (text.isNotEmpty()) {
                                            // Se non abbiamo un titolo principale, usa il sottotitolo
                                            currentProgram = program.copy(title = text)
                                            android.util.Log.d("XMLTVParser", "Sottotitolo usato come titolo principale: '$text'")
                                        } else {
                                            // Nessuna condizione soddisfatta, ignora
                                            android.util.Log.d("XMLTVParser", "Sottotitolo ignorato: testo vuoto e titolo principale vuoto")
                                        }
                                    }
                                }
                                TAG_DESC -> {
                                    currentProgram = currentProgram?.copy(description = text)
                                    android.util.Log.d("XMLTVParser", "Descrizione programma impostata: ${text.take(50)}")
                                }
                                TAG_CATEGORY -> {
                                    currentProgram = currentProgram?.copy(category = text)
                                    android.util.Log.d("XMLTVParser", "Categoria programma impostata: $text")
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            when (parser.name) {
                                TAG_PROGRAMME -> {
                                    currentProgram?.let { program ->
                                        android.util.Log.d("XMLTVParser", "Validazione programma: titolo='${program.title}', start=${program.startTime}, end=${program.endTime}")
                                        if (program.title.isNotEmpty() && program.startTime > 0 && program.endTime > 0) {
                                            val finalProgram = program.copy(id = "${program.channelId}_${program.startTime}")
                                            programs.add(finalProgram)
                                            programCount++
                                            android.util.Log.d("XMLTVParser", "Programma aggiunto: ${program.title} per canale ${program.channelId}")
                                        } else {
                                            android.util.Log.w("XMLTVParser", "Programma scartato: titolo='${program.title}', start=${program.startTime}, end=${program.endTime}")
                                        }
                                    }
                                    currentProgram = null
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
                
                android.util.Log.d("XMLTVParser", "Parsing completato: $programCount programmi aggiunti, $skippedCount programmi saltati")
                
            } catch (e: Exception) {
                android.util.Log.e("XMLTVParser", "Errore durante il parsing", e)
                e.printStackTrace()
            } finally {
                inputStream.close()
            }
            
            return programs
        }
        
        /**
         * Aggiorna i programmi correnti basandosi sul timestamp corrente
         */
        fun updateCurrentPrograms(programs: List<EPGProgram>): List<EPGProgram> {
            val currentTime = System.currentTimeMillis()
            return programs.map { program ->
                if (currentTime >= program.startTime && currentTime < program.endTime) {
                    // Il programma è attualmente in onda
                    program
                } else {
                    // Il programma non è attualmente in onda
                    program
                }
            }
        }
        
        /**
         * Trova il programma corrente per un canale specifico
         */
        fun findCurrentProgram(channelId: String, programs: List<EPGProgram>): EPGProgram? {
            val currentTime = System.currentTimeMillis()
            return programs.find { program ->
                program.channelId == channelId && 
                currentTime >= program.startTime && 
                currentTime < program.endTime
            }
        }
        
        /**
         * Trova il prossimo programma per un canale specifico
         */
        fun findNextProgram(channelId: String, programs: List<EPGProgram>): EPGProgram? {
            val currentTime = System.currentTimeMillis()
            return programs
                .filter { it.channelId == channelId && it.startTime > currentTime }
                .minByOrNull { it.startTime }
        }
    }
    
    data class XMLTVData(
        val channels: List<Channel>,
        val programs: List<EPGProgram>
    )
}