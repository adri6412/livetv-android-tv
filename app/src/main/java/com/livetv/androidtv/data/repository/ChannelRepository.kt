package com.livetv.androidtv.data.repository

import androidx.lifecycle.LiveData
import com.livetv.androidtv.data.dao.ChannelDao
import com.livetv.androidtv.data.dao.GroupCount
import com.livetv.androidtv.data.entity.Channel
import kotlinx.coroutines.flow.Flow

class ChannelRepository(
    private val channelDao: ChannelDao
) {
    
    fun getAllChannels(): Flow<List<Channel>> = channelDao.getAllChannels()
    
    fun getAllChannelsLiveData(): LiveData<List<Channel>> = channelDao.getAllChannelsLiveData()
    
    fun getFavoriteChannels(): Flow<List<Channel>> = channelDao.getFavoriteChannels()
    
    fun getChannelsByGroup(group: String): Flow<List<Channel>> = channelDao.getChannelsByGroup(group)
    
    fun getAllGroups(): Flow<List<String>> = channelDao.getAllGroups()
    
    fun searchChannels(query: String): Flow<List<Channel>> = channelDao.searchChannels(query)
    
    fun getHbbTVChannels(): Flow<List<Channel>> = channelDao.getHbbTVChannels()
    
    suspend fun getChannelById(channelId: String): Channel? = channelDao.getChannelById(channelId)
    
    suspend fun getChannelByNumber(number: Int): Channel? = channelDao.getChannelByNumber(number)
    
    suspend fun getChannelCount(): Int = channelDao.getChannelCount()
    
    suspend fun getFavoriteChannelCount(): Int = channelDao.getFavoriteChannelCount()
    
    suspend fun insertChannel(channel: Channel) = channelDao.insertChannel(channel)
    
    suspend fun insertChannels(channels: List<Channel>) = channelDao.insertChannels(channels)
    
    suspend fun updateChannel(channel: Channel) = channelDao.updateChannel(channel)
    
    suspend fun updateFavoriteStatus(channelId: String, isFavorite: Boolean) = 
        channelDao.updateFavoriteStatus(channelId, isFavorite)
    
    suspend fun deleteChannel(channel: Channel) = channelDao.deleteChannel(channel)
    
    suspend fun deleteChannelById(channelId: String) = channelDao.deleteChannelById(channelId)
    
    suspend fun deleteAllChannels() = channelDao.deleteAllChannels()
    
    suspend fun deactivateAllChannels() = channelDao.deactivateAllChannels()
    
    suspend fun deleteInactiveChannels() = channelDao.deleteInactiveChannels()
    
    // Navigazione canali
    suspend fun getNextChannel(currentNumber: Int): Channel? = channelDao.getNextChannel(currentNumber)
    
    suspend fun getPreviousChannel(currentNumber: Int): Channel? = channelDao.getPreviousChannel(currentNumber)
    
    suspend fun getFirstChannel(): Channel? = channelDao.getFirstChannel()
    
    suspend fun getLastChannel(): Channel? = channelDao.getLastChannel()
    
    suspend fun getChannelCountByGroup(): List<GroupCount> = channelDao.getChannelCountByGroup()
    
    // Metodo sincrono per ottenere tutti i canali (per il popup)
    suspend fun getAllChannelsSync(): List<Channel> = channelDao.getAllChannelsSync()
}