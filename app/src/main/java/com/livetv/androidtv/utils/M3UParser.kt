package com.livetv.androidtv.utils

import com.livetv.androidtv.data.entity.Channel
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.regex.Pattern

class M3UParser {
    
    companion object {
        private const val EXTINF_PATTERN = "#EXTINF:(-?\\d+(?:\\.\\d+)?),?(.*)"
        private const val ATTRIBUTE_PATTERN = "(\\w+(?:-\\w+)*)=\"([^\"]*)\""
        
        fun parseM3U(inputStream: InputStream): List<Channel> {
            val channels = mutableListOf<Channel>()
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            
            var line: String?
            var currentExtinf: String? = null
            var pendingUserAgent: String? = null
            var channelNumber = 1

            try {
                while (reader.readLine().also { line = it } != null) {
                    line?.let { currentLine ->
                        when {
                            currentLine.startsWith("#EXTM3U") -> {
                                // Header M3U, ignora
                            }
                            currentLine.startsWith("#EXTINF:") -> {
                                currentExtinf = currentLine
                                pendingUserAgent = null
                            }
                            currentLine.startsWith("#EXTVLCOPT:http-user-agent=") -> {
                                pendingUserAgent = currentLine
                                    .substringAfter("#EXTVLCOPT:http-user-agent=")
                                    .trim()
                            }
                            currentLine.startsWith("#") -> {
                                // Altri commenti, ignora
                            }
                            currentLine.trim().isNotEmpty() && currentExtinf != null -> {
                                // Questa è una URL di stream
                                val channel = parseChannel(currentExtinf!!, currentLine.trim(), channelNumber, pendingUserAgent)
                                if (channel != null) {
                                    channels.add(channel)
                                    channelNumber++
                                }
                                currentExtinf = null
                                pendingUserAgent = null
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                reader.close()
            }
            
            return channels
        }
        
        private fun parseChannel(extinf: String, url: String, defaultNumber: Int, httpUserAgent: String? = null): Channel? {
            try {
                val pattern = Pattern.compile(EXTINF_PATTERN)
                val matcher = pattern.matcher(extinf)

                if (!matcher.find()) return null

                val info = matcher.group(2) ?: ""

                // Estrai attributi e nome del canale
                val baseAttributes = extractAttributes(info)
                val attributes = if (httpUserAgent != null) {
                    baseAttributes + mapOf("http-user-agent" to httpUserAgent)
                } else {
                    baseAttributes
                }
                val channelName = extractChannelName(info)

                if (channelName.isEmpty()) return null

                // Genera ID univoco
                val channelId = generateChannelId(channelName, url)

                // Estrai numero canale
                val channelNumber = attributes["tvg-chno"]?.toIntOrNull()
                    ?: attributes["channel-id"]?.toIntOrNull()
                    ?: defaultNumber

                return Channel.createFromM3U(
                    id = channelId,
                    name = channelName,
                    url = url,
                    attributes = attributes
                ).copy(number = channelNumber)
                
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }
        
        private fun extractAttributes(info: String): Map<String, String> {
            val attributes = mutableMapOf<String, String>()
            val pattern = Pattern.compile(ATTRIBUTE_PATTERN)
            val matcher = pattern.matcher(info)
            
            while (matcher.find()) {
                val key = matcher.group(1)
                val value = matcher.group(2)
                if (key != null && value != null) {
                    attributes[key] = value
                }
            }
            
            return attributes
        }
        
        private fun extractChannelName(info: String): String {
            // Rimuovi tutti gli attributi per ottenere il nome del canale
            var name = info
            val pattern = Pattern.compile(ATTRIBUTE_PATTERN)
            val matcher = pattern.matcher(info)
            
            while (matcher.find()) {
                name = name.replace(matcher.group(0) ?: "", "")
            }
            
            // Pulisci il nome
            name = name.trim().replace(Regex("^,+|,+$"), "")
            
            return name.trim()
        }
        
        private fun generateChannelId(name: String, url: String): String {
            return "${name.hashCode()}_${url.hashCode()}".replace("-", "")
        }
        
        fun parseM3UFromString(content: String): List<Channel> {
            return parseM3U(content.byteInputStream())
        }
        
        fun validateM3U(inputStream: InputStream): Boolean {
            return try {
                val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
                val firstLine = reader.readLine()
                reader.close()
                firstLine?.startsWith("#EXTM3U") == true
            } catch (e: Exception) {
                false
            }
        }
        
        fun validateM3UFromString(content: String): Boolean {
            return content.trim().startsWith("#EXTM3U")
        }
    }
}