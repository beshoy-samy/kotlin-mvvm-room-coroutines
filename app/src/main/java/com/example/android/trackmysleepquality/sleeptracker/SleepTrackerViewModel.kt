/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.trackmysleepquality.sleeptracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.example.android.trackmysleepquality.database.SleepDatabaseDao
import com.example.android.trackmysleepquality.database.SleepNight
import com.example.android.trackmysleepquality.formatNights
import kotlinx.coroutines.*

/**
 * ViewModel for SleepTrackerFragment.
 */
class SleepTrackerViewModel(
        val database: SleepDatabaseDao,
        application: Application) : AndroidViewModel(application) {

    private var viewModelJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)
    private var tonight = MutableLiveData<SleepNight>()
    private val allNights = database.getAllNights()

    private val _navigationSleepNightQuality = MutableLiveData<SleepNight>()
    val navigationSleepNightQuality: LiveData<SleepNight>
        get() = _navigationSleepNightQuality

    val nightsData = Transformations.map(allNights) { nights ->
        formatNights(nights, application.resources)
    }

    val startBtnVisible = Transformations.map(tonight) { it == null }
    val stopBtnVisible = Transformations.map(tonight) { it != null }
    val clearBtnVisible = Transformations.map(allNights) { it?.isNotEmpty() }

    init {
        initializeTonight()
    }

    private fun initializeTonight() {
        uiScope.launch {
            tonight.value = getTonightFromDatabase()
        }
    }

    private suspend fun getTonightFromDatabase(): SleepNight? {
        return withContext(Dispatchers.IO) {
            var night = database.getTonight()
            if (night?.startTimeMilli != night?.endTimeMilli) night = null
            return@withContext night
        }
    }

    fun onStartTrack() {
        uiScope.launch {
            val newNight = SleepNight()
            newNight.nightId = insert(newNight)
            tonight.value = newNight
        }
    }

    private suspend fun insert(newNight: SleepNight): Long {
        return withContext(Dispatchers.IO) {
            return@withContext database.insert(newNight)
        }
    }

    fun onStopTrack() {
        uiScope.launch {
            val oldNight = tonight.value ?: return@launch
            oldNight.endTimeMilli = System.currentTimeMillis()
            update(oldNight)
            _navigationSleepNightQuality.value = oldNight
        }
    }

    fun onSleepQualityNavigationDone() {
        _navigationSleepNightQuality.value = null
    }

    private suspend fun update(oldNight: SleepNight) {
        withContext(Dispatchers.IO) {
            database.update(oldNight)
        }
    }

    fun onClear() {
        uiScope.launch {
            clearAll()
            tonight.value = null
        }
    }

    private suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            database.clear()
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

}