package com.livetv.androidtv.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import android.content.Context
import com.livetv.androidtv.data.entity.Channel
import com.livetv.androidtv.data.entity.Program
import com.livetv.androidtv.data.entity.Playlist
import com.livetv.androidtv.data.entity.EPGProgram
import com.livetv.androidtv.data.dao.ChannelDao
import com.livetv.androidtv.data.dao.ProgramDao
import com.livetv.androidtv.data.dao.PlaylistDao
import com.livetv.androidtv.data.dao.EPGDao

@Database(
    entities = [Channel::class, Program::class, Playlist::class, EPGProgram::class],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class LiveTVDatabase : RoomDatabase() {
    
    abstract fun channelDao(): ChannelDao
    abstract fun programDao(): ProgramDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun epgDao(): EPGDao
    
    companion object {
        @Volatile
        private var INSTANCE: LiveTVDatabase? = null
        
        fun getDatabase(context: Context): LiveTVDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LiveTVDatabase::class.java,
                    "livetv_database"
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
                INSTANCE = instance
                instance
            }
        }
        
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Migrazione sicura da versione 2 a 3
                // Aggiungi nuove tabelle o colonne senza perdere dati esistenti
                try {
                    // Se la tabella epg_programs non esiste, creala
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS epg_programs (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            channelId TEXT NOT NULL,
                            title TEXT NOT NULL,
                            description TEXT,
                            startTime INTEGER NOT NULL,
                            endTime INTEGER NOT NULL,
                            category TEXT,
                            isLive INTEGER NOT NULL DEFAULT 0,
                            hbbtvUrl TEXT
                        )
                    """)
                    
                    // Crea indici per migliorare le performance
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_epg_programs_channelId ON epg_programs (channelId)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_epg_programs_startTime ON epg_programs (startTime)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_epg_programs_endTime ON epg_programs (endTime)")
                    
                } catch (e: Exception) {
                    // Log dell'errore ma non fallire la migrazione
                    android.util.Log.w("LiveTVDatabase", "Errore durante la migrazione 2->3", e)
                }
            }
        }
        
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Migrazione sicura da versione 3 a 4
                // Aggiungi il campo epgDisplayName alla tabella channels
                try {
                    // Aggiungi la colonna epgDisplayName se non esiste
                    database.execSQL("ALTER TABLE channels ADD COLUMN epgDisplayName TEXT")
                    
                    // Aggiorna i canali esistenti per impostare epgDisplayName basato sul nome
                    database.execSQL("""
                        UPDATE channels 
                        SET epgDisplayName = CASE 
                            WHEN name REGEXP '^[0-9]+\\s*-\\s*' 
                            THEN REPLACE(name, REGEXP('^[0-9]+\\s*-\\s*'), '')
                            ELSE name 
                        END
                    """)
                    
                    android.util.Log.d("LiveTVDatabase", "Migrazione 3->4 completata: aggiunto campo epgDisplayName")
                    
                } catch (e: Exception) {
                    // Log dell'errore ma non fallire la migrazione
                    android.util.Log.w("LiveTVDatabase", "Errore durante la migrazione 3->4", e)
                }
            }
        }
        
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Migrazione sicura da versione 4 a 5
                // Rimuovi il vincolo di foreign key dalla tabella epg_programs
                try {
                    // SQLite non supporta la rimozione diretta di foreign key constraints
                    // Dobbiamo ricreare la tabella senza i vincoli
                    
                    // Crea una tabella temporanea con la nuova struttura
                    database.execSQL("""
                        CREATE TABLE epg_programs_new (
                            id TEXT PRIMARY KEY NOT NULL,
                            channelId TEXT NOT NULL,
                            title TEXT NOT NULL,
                            description TEXT,
                            category TEXT,
                            startTime INTEGER NOT NULL,
                            endTime INTEGER NOT NULL,
                            episodeNumber INTEGER,
                            seasonNumber INTEGER,
                            year INTEGER,
                            rating TEXT,
                            director TEXT,
                            actors TEXT,
                            genres TEXT,
                            posterUrl TEXT,
                            isLive INTEGER NOT NULL DEFAULT 0,
                            isRepeat INTEGER NOT NULL DEFAULT 0,
                            hasSubtitles INTEGER NOT NULL DEFAULT 0,
                            audioDescription INTEGER NOT NULL DEFAULT 0,
                            hbbtvUrl TEXT,
                            lastUpdated INTEGER NOT NULL DEFAULT 0
                        )
                    """)
                    
                    // Copia i dati esistenti (gestisce i campi che potrebbero non esistere)
                    try {
                        database.execSQL("""
                            INSERT INTO epg_programs_new 
                            SELECT id, channelId, title, description, category, startTime, endTime,
                                   COALESCE(episodeNumber, 0), COALESCE(seasonNumber, 0), COALESCE(year, 0), 
                                   rating, director, actors, genres,
                                   posterUrl, COALESCE(isLive, 0), COALESCE(isRepeat, 0), 
                                   COALESCE(hasSubtitles, 0), COALESCE(audioDescription, 0), 
                                   hbbtvUrl, COALESCE(lastUpdated, 0)
                            FROM epg_programs
                        """)
                    } catch (e: Exception) {
                        // Se la query fallisce, prova con i campi base
                        android.util.Log.w("LiveTVDatabase", "Fallback a campi base per migrazione EPG", e)
                        database.execSQL("""
                            INSERT INTO epg_programs_new 
                            SELECT id, channelId, title, description, category, startTime, endTime,
                                   0, 0, 0, NULL, NULL, NULL, NULL,
                                   NULL, 0, 0, 0, 0, NULL, 0
                            FROM epg_programs
                        """)
                    }
                    
                    // Rimuovi la tabella vecchia
                    database.execSQL("DROP TABLE epg_programs")
                    
                    // Rinomina la nuova tabella
                    database.execSQL("ALTER TABLE epg_programs_new RENAME TO epg_programs")
                    
                    // Ricrea gli indici
                    database.execSQL("CREATE INDEX index_epg_programs_channelId ON epg_programs (channelId)")
                    database.execSQL("CREATE INDEX index_epg_programs_startTime ON epg_programs (startTime)")
                    database.execSQL("CREATE INDEX index_epg_programs_endTime ON epg_programs (endTime)")
                    
                    android.util.Log.d("LiveTVDatabase", "Migrazione 4->5 completata: rimossa foreign key constraint da epg_programs")
                    
                } catch (e: Exception) {
                    // Log dell'errore ma non fallire la migrazione
                    android.util.Log.w("LiveTVDatabase", "Errore durante la migrazione 4->5", e)
                }
            }
        }
    }
}