# Coroutines Location API Library for Android

[![Build Status](https://travis-ci.org/patloew/CoLocation.svg?branch=main)](https://travis-ci.org/patloew/CoLocation) [![codecov](https://codecov.io/gh/patloew/CoLocation/branch/main/graph/badge.svg)](https://codecov.io/gh/patloew/CoLocation) [![Download](https://api.bintray.com/packages/patloew/maven/CoLocation/images/download.svg) ](https://bintray.com/patloew/maven/CoLocation/_latestVersion) [![API](https://img.shields.io/badge/API-14%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=14)

This library wraps the Location APIs in Kotlin coroutines and `Flow`.

# Usage

Create a `CoLocation` or `CoGeocoding` instance, preferably by using a dependency injection framework. `CoLocation` is
very similar to the classes provided by the Location APIs. Instead of `LocationServices.getFusedLocationProviderClient(context).lastLocation`
you can use `coLocation.getLastLocation()`. Make sure to have the Location permission from Marshmallow on, if they are
needed by your API requests.

Example:

```kotlin
val coLocation = CoLocation.from(context);
val coGeocoder = CoGeocoder.from(context);

val locationRequest = LocationRequest.create()
        .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        .setInterval(5000)

val locationUpdates: MutableLiveData<Location> = MutableLiveData()
val addressUpdates: LiveData<Address?> = locationUpdates.switchMap { location ->
        liveData { emit(coGeocoder.getAddressFromLocation(location)) }
    }

val job = viewModelScope.launch {
    try {
        coLocation.getLocationUpdates(locationRequest).collect(mutableLocationUpdates::postValue)
    } catch (e: CancellationException) {
        // Location updates cancelled
    }
}

// The updates get canceled automatically when viewModelScope is cancelled.
// If you want to cancel it before that, save the job and cancel it.
job.cancel()
```

The following APIs are wrapped by this library:

* `FusedLocationProviderClient` via `CoLocation`
* `SettingsClient` via `CoLocation`
* `Geocoder` via `CoGeocoder`

Checking the location settings is simplified with this library, by providing a `SettingsResult` via
`coLocation.checkLocationSettings(locationRequest)`:

```kotlin
val settingsResult = coLocation.checkLocationSettings(locationRequest)

when (settingsResult) {
    SettingsResult.Satisfied -> startLocationUpdates()
    is SettingsResult.Resolvable -> settingsResult.resolve(activity, REQUEST_SHOW_SETTINGS)
    else -> { /* Ignore for now, we can't resolve this anyway */ }
}

override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == REQUEST_SHOW_SETTINGS && resultCode == Activity.RESULT_OK) {
        // Resolution success, location settings are now satisfied
        startLocationUpdates()
    }
}
```

# Sample

A basic sample app is available in the `sample` project.

# Testing

When unit testing your app's classes, `CoLocation` and `CoGeocoder` behavior can be mocked easily. See
`MainViewModelTest` in the `sample` project for an example test.

# Donations

If you like the library and want to support the creator for development/maintenance, you can make a donation in Bitcoin
to `bc1q5uejfyl2kskhhveg7lx4fcwgv8hz88r92yzjsu`. Thank you!

# License

	Copyright 2020 Patrick Löwenstein

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

	    http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
