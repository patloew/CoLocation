package com.patloew.colocationsample

import android.content.Context
import android.location.Address
import android.location.Location
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.patloew.colocation.CoGeocoder
import com.patloew.colocation.CoLocation
import com.patloew.colocation.request.LocationRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/* Copyright 2020 Patrick Löwenstein
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. */
class MainViewModel(
    private val coLocation: CoLocation,
    private val coGeocoder: CoGeocoder
) : ViewModel(), LifecycleObserver {

    private val locationRequest: LocationRequest = LocationRequest.create()
        .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        //.setSmallestDisplacement(1f)
        //.setNumUpdates(3)
        .setInterval(5000)
        .setFastestInterval(2500)


    private val mutableLocationUpdates: MutableLiveData<Location> = MutableLiveData()
    val locationUpdates: LiveData<Location> = mutableLocationUpdates
    val addressUpdates: LiveData<Address?> = locationUpdates.switchMap { location ->
        liveData { emit(coGeocoder.getAddressFromLocation(location)) }
    }

    private val mutableResolveSettingsEvent: MutableLiveData<CoLocation.SettingsResult.Resolvable> =
        MutableLiveData()
    val resolveSettingsEvent: LiveData<CoLocation.SettingsResult.Resolvable> =
        mutableResolveSettingsEvent

    private var locationUpdatesJob: Job? = null

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart() {
        startLocationUpdatesAfterCheck()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        locationUpdatesJob?.cancel()
        locationUpdatesJob = null
    }

    private fun startLocationUpdatesAfterCheck() {
        viewModelScope.launch {
            val settingsResult = coLocation.checkLocationSettings(locationRequest)
            when (settingsResult) {
                CoLocation.SettingsResult.Satisfied -> {
                    coLocation.getLastLocation()?.run(mutableLocationUpdates::postValue)
                    startLocationUpdates()
                }
                is CoLocation.SettingsResult.Resolvable -> mutableResolveSettingsEvent.postValue(
                    settingsResult
                )
                else -> { /* Ignore for now, we can't resolve this anyway */
                }
            }
        }
    }

    fun startLocationUpdates() {
        locationUpdatesJob?.cancel()
        locationUpdatesJob = viewModelScope.launch {
            try {
                coLocation.getLocationUpdates(locationRequest).collect { location ->
                    Log.d("MainViewModel", "Location update received: $location")
                    mutableLocationUpdates.postValue(location)
                }
            } catch (e: CancellationException) {
                Log.e("MainViewModel", "Location updates cancelled", e)
            }
        }
    }

}