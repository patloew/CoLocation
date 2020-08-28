package com.patloew.colocationsample

import android.app.Activity
import android.content.Intent
import android.location.Address
import android.location.Location
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.LibsBuilder
import com.patloew.colocation.CoLocation
import java.text.DateFormat
import java.util.*

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
class MainActivity : AppCompatActivity() {

    companion object {
        private val DATE_FORMAT = DateFormat.getDateTimeInstance()
        private const val REQUEST_SHOW_SETTINGS = 123
    }

    private var lastUpdate: TextView? = null
    private var locationText: TextView? = null
    private var addressText: TextView? = null

    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel?> create(modelClass: Class<T>): T =
                    MainViewModel(CoLocation.from(this@MainActivity)) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lastUpdate = findViewById(R.id.tv_last_update)
        locationText = findViewById(R.id.tv_current_location)
        addressText = findViewById(R.id.tv_current_address)

        lifecycle.addObserver(viewModel)
        viewModel.locationUpdates.observe(this, this::onLocationUpdate)
        viewModel.resolveLocationSettingsEvent.observe(this) { it.resolve(this, REQUEST_SHOW_SETTINGS) }
    }

    override fun onResume() {
        super.onResume()
        checkPlayServicesAvailable()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SHOW_SETTINGS && resultCode == Activity.RESULT_OK) viewModel.startLocationUpdates()
    }

    private fun checkPlayServicesAvailable() {
        val apiAvailability = GoogleApiAvailability.getInstance()
        val status = apiAvailability.isGooglePlayServicesAvailable(this)

        if (status != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(status)) {
                apiAvailability.getErrorDialog(this, status, 1).show()
            } else {
                Snackbar.make(lastUpdate!!, "Google Play Services unavailable. This app will not work", Snackbar.LENGTH_INDEFINITE).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_licenses) {
            LibsBuilder()
                    .withFields(*Libs.toStringArray(R.string::class.java.fields))
                    .withActivityStyle(Libs.ActivityStyle.LIGHT_DARK_TOOLBAR)
                    .withActivityTitle("Open Source Licenses")
                    .withLicenseShown(true)
                    .start(this)

            return true
        }

        return false
    }

    private fun onLocationUpdate(location: Location?) {
        lastUpdate!!.text = DATE_FORMAT.format(Date())
        locationText!!.text = location?.let { "${it.latitude}, ${it.longitude}" } ?: "N/A"
    }

    private fun onAddressUpdate(address: Address) {
        addressText!!.text = getAddressText(address)
    }

    private fun getAddressText(address: Address): String {
        var addressText = ""
        val maxAddressLineIndex = address.maxAddressLineIndex

        for (i in 0..maxAddressLineIndex) {
            addressText += address.getAddressLine(i)
            if (i != maxAddressLineIndex) {
                addressText += "\n"
            }
        }

        return addressText
    }
}
