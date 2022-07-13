package com.patloew.colocationsample

import android.app.Activity
import android.content.Intent
import android.location.Address
import android.location.Location
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.RadioButton
import android.widget.RadioGroup
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
import com.patloew.colocation.CoGeocoder
import com.patloew.colocation.CoLocation
import com.patloew.colocation.LocationServicesSource
import java.text.DateFormat
import java.util.Date

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
    private var sourceGroup: RadioGroup? = null

    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel?> create(modelClass: Class<T>): T =
                MainViewModel(applicationContext) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lastUpdate = findViewById(R.id.tv_last_update)
        locationText = findViewById(R.id.tv_current_location)
        addressText = findViewById(R.id.tv_current_address)
        sourceGroup = findViewById(R.id.source_group)
        lifecycle.addObserver(viewModel)
        viewModel.locationUpdates.observe(this, this::onLocationUpdate)
        viewModel.addressUpdates.observe(this, this::onAddressUpdate)
        viewModel.resolveSettingsEvent.observe(this) { it.resolve(this, REQUEST_SHOW_SETTINGS) }

        initSources()
    }

    private fun initSources() {
        sourceGroup?.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.source_auto -> {
                    viewModel.switchSource(LocationServicesSource.NONE)
                }
                R.id.source_gms -> {
                    viewModel.switchSource(LocationServicesSource.GMS)
                }
                R.id.source_hms -> {
                    viewModel.switchSource(LocationServicesSource.HMS)
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SHOW_SETTINGS && resultCode == Activity.RESULT_OK) viewModel.startLocationUpdates()
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
        locationText!!.text = location?.run { "$latitude, $longitude" } ?: "N/A"
    }

    private fun onAddressUpdate(address: Address?) {
        addressText!!.text = address?.fullText ?: "N/A"
    }

    private val Address.fullText: String
        get() = (0..maxAddressLineIndex).joinToString(separator = "\n") { getAddressLine(it) }
}
