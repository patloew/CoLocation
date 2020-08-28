package com.patloew.colocationsample

import android.location.Location
import android.util.Log
import androidx.lifecycle.*
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationSettingsRequest
import com.patloew.colocation.CoLocation
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
class MainViewModel(private val coLocation: CoLocation) : ViewModel(), LifecycleObserver {

    private val locationRequest: LocationRequest =
            LocationRequest.create()
                    .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                    //.setSmallestDisplacement(1f)
                    //.setNumUpdates(3)
                    .setInterval(5000)
                    .setFastestInterval(2500)


    private val mutableLocationUpdates: MutableLiveData<Location> = MutableLiveData()
    val locationUpdates: LiveData<Location> = mutableLocationUpdates

    private val mutableResolveLocationSettingsEvent: MutableLiveData<CoLocation.SettingsResult.Resolvable> = MutableLiveData()
    val resolveLocationSettingsEvent: LiveData<CoLocation.SettingsResult.Resolvable> = mutableResolveLocationSettingsEvent

    private var locationUpdatesJob: Job? = null

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onResume() {
        startLocationUpdatesAfterCheck()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onPause() {
        locationUpdatesJob?.cancel()
        locationUpdatesJob = null
    }

    private fun startLocationUpdatesAfterCheck() {
        viewModelScope.launch {
            val settingsRequest = LocationSettingsRequest.Builder().addLocationRequest(locationRequest).build()
            val settingsResult = coLocation.areSettingsSatisfied(settingsRequest)

            when (settingsResult) {
                CoLocation.SettingsResult.Satisfied -> {
                    coLocation.lastKnownLocation()?.run(mutableLocationUpdates::postValue)
                    startLocationUpdates()
                }
                is CoLocation.SettingsResult.Resolvable -> mutableResolveLocationSettingsEvent.postValue(settingsResult)
                else -> { /* Ignore for now, we can't resolve this anyway */
                }
            }
        }
    }

    fun startLocationUpdates() {
        locationUpdatesJob?.cancel()
        locationUpdatesJob = viewModelScope.launch {
            try {
                coLocation.locationUpdates(locationRequest).collect { location ->
                    Log.d("MainViewModel", "Location update received: $location")
                    mutableLocationUpdates.postValue(location)
                }
            } catch (e: CancellationException) {
                Log.e("MainViewModel", "Location updates cancelled", e)
            }
        }
    }

}
