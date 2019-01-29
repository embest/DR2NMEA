/*
 * Copyright (C) 2018 Embest
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.embest.gps.dr2nmea

import android.Manifest
import android.os.Bundle
import android.support.design.widget.BottomNavigationView
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import android.support.v4.app.Fragment
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.content.SharedPreferences
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.icu.text.SimpleDateFormat
import android.location.*
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import android.preference.PreferenceActivity
import android.preference.PreferenceManager
import android.provider.Settings
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.view.WindowManager
import android.widget.Toast.*
import net.embest.gps.dr2nmea.fragments.*
import net.embest.gps.dr2nmea.utils.*
import java.util.*


class MainActivity : AppCompatActivity() {

    private val mSnrFragment = SnrFragment.newInstance()
    private val mSensorFragment = SensorFragment.newInstance()
    private val mMapFragment = MapFragment.newInstance()

    private val mContext by lazy { this }
    private var mCurrentFragment: Int = 0

    private val mFile = FileHelper()
    private val mNmeaGenerator = NmeaGenerator()

    private var mService: LocationManager? = null
    private var mProvider: LocationProvider? = null

    private var mGnssStarted: Boolean = false
    private var mNeedExit: Boolean =false

    private var mRecordFileName: String = "gnss_record"

    private var mPreferences: SharedPreferences? = null

    private var mGnssInfo: GnssInfo? = null

    private var mGnssStatusCallBack: GnssStatus.Callback? = null
    private var mGnssMeasurementsCallBack: GnssMeasurementsEvent.Callback? = null
    private var mGnssNavigationCallBack: GnssNavigationMessage.Callback? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.e(TAG, "onCreate()")
        setContentView(R.layout.activity_main)
        //navigation.menu.removeItem(R.id.navigation_map)


        // Check the screen orientation, and keep it
        val value = resources.configuration.orientation
        requestedOrientation = if (value == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        else{
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        mGnssInfo = GnssInfo()

        val preferences = getSharedPreferences("_has_set_default_values", Context.MODE_PRIVATE)
        if (!preferences.getBoolean("_has_set_default_values", false)) {
            PreferenceManager.setDefaultValues(this, R.xml.pref_general, true)
        }

        mPreferences = PreferenceManager.getDefaultSharedPreferences(baseContext)

        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener)
        checkAndRequestPermissions()

        fab.setOnClickListener{ view ->

            mGnssStarted = if (!mGnssStarted) {
                onMakeRecordName()
                mGnssInfo!!.reset()
                gpsStart(1000,0f)
                fab.setImageResource(android.R.drawable.ic_media_pause)
                Snackbar.make(view, mContext.resources.getString(R.string.msg_snack_bar_start_test), Snackbar.LENGTH_SHORT)
                    .setAction(mContext.resources.getString(R.string.msg_snack_bar_action), null).show()
                true
            } else {
                gpsStop()
                Snackbar.make(view, mContext.resources.getString(R.string.msg_snack_bar_stop_test), Snackbar.LENGTH_SHORT)
                    .setAction(mContext.resources.getString(R.string.msg_snack_bar_action), null).show()
                fab.setImageResource(android.R.drawable.ic_media_play)
                false
            }
        }
    }

    override fun onResume() {
        Log.e(TAG, "onResume()")
        super.onResume()
        //Keep screen
        if (mPreferences!!.getBoolean("preference_keep_screen",true)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        //Reset the test status
        if (!mGnssStarted) {
            fab.setImageResource(android.R.drawable.ic_media_play)
        } else {
            fab.setImageResource(android.R.drawable.ic_media_pause)
        }
    }


    override fun onPause(){
        super.onPause()
        Log.e(TAG, "onPause:$mCurrentFragment")
    }


    override fun onDestroy() {
        super.onDestroy()
        Log.e(TAG, "onDestroy()")
        unregisterCallbacks()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(mContext, SettingsActivity::class.java)
                intent.putExtra( PreferenceActivity.EXTRA_SHOW_FRAGMENT, SettingsActivity.GeneralPreferenceFragment::class.java.name )
                intent.putExtra( PreferenceActivity.EXTRA_NO_HEADERS, true )
                startActivity(intent)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if (mNeedExit) {
            finish()
        } else {
            makeText(this, mContext.resources.getString(R.string.msg_exit_app), LENGTH_SHORT).show()
            mNeedExit = true
            Handler().postDelayed({ mNeedExit = false }, (3 * 1000).toLong())
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.container, fragment)
        fragmentTransaction.commitNow()
        if(!mGnssInfo!!.satellites.isEmpty()){
            onUpdateView()
        }
    }

    private val mOnNavigationItemSelectedListener = BottomNavigationView.OnNavigationItemSelectedListener { item ->
        when (item.itemId) {
            R.id.navigation_snr -> {
                mCurrentFragment = SNR_FRAGMENT
                replaceFragment(mSnrFragment)
                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_sen -> {
                mCurrentFragment = SEN_FRAGMENT
                replaceFragment(mSensorFragment)
                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_map ->{
                mCurrentFragment = MAP_FRAGMENT
                replaceFragment(mMapFragment)
                return@OnNavigationItemSelectedListener true
            }
        }
        false
    }


    private fun  onMakeRecordName() {
        val lTime = System.currentTimeMillis()

        val time = SimpleDateFormat("yyMMdd_HHmmss", Locale.US)

        val device = "${Build.MODEL}_${mPreferences!!.getString("preference_device_name", "01")}"

        mRecordFileName = device + "_" + time.format(lTime)

        mRecordFileName = mRecordFileName.replace("-","_")
        mRecordFileName = mRecordFileName.replace(" ", "_")
        mRecordFileName = mRecordFileName.replace(".", "")

        Log.i(TAG, "Name:$mRecordFileName")
    }


    private fun onUpdatePermissions() {
        mService = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (Build.VERSION.SDK_INT >= 28) {
            Log.e(TAG, "gnssHardwareModelName: " + mService!!.gnssHardwareModelName + mService!!.gnssYearOfHardware )
        }

        mProvider = mService!!.getProvider(LocationManager.GPS_PROVIDER)

        if (mProvider == null) {
            Log.e(TAG, "Unable to get GPS_PROVIDER")
            makeText(this, getString(R.string.msg_gps_not_supported), LENGTH_SHORT).show()
            finish()
        }

        replaceFragment(mSnrFragment)
    }

    private fun checkAndRequestPermissions(): Boolean {
        val permissionWrite = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val permissionLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)

        val listPermissionsNeeded = ArrayList<String>()

        if (permissionWrite != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (permissionLocation != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toTypedArray(), REQUEST_ID_MULTIPLE_PERMISSIONS)
            return false
        }
        onUpdatePermissions()
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        Log.d(TAG, "Permission callback called")
        when (requestCode) {
            REQUEST_ID_MULTIPLE_PERMISSIONS -> {
                val perms = HashMap<String, Int>()
                perms[Manifest.permission.WRITE_EXTERNAL_STORAGE] = PackageManager.PERMISSION_GRANTED
                perms[Manifest.permission.ACCESS_FINE_LOCATION] = PackageManager.PERMISSION_GRANTED
                if (grantResults.isNotEmpty()) {
                    for (i in permissions.indices)
                        perms[permissions[i]] = grantResults[i]
                    if ( perms[Manifest.permission.WRITE_EXTERNAL_STORAGE] == PackageManager.PERMISSION_GRANTED
                        && perms[Manifest.permission.ACCESS_FINE_LOCATION] == PackageManager.PERMISSION_GRANTED) {
                        Log.e(TAG, "storage & location services permission granted")
                        onUpdatePermissions()
                    } else {
                        Log.d(TAG, "Some permissions are not granted ask again ")
                        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                            val dialog = AlertDialog.Builder(this)
                            dialog.setMessage(R.string.msg_request_permissions)
                                .setPositiveButton(R.string.msg_dialog_yes) { _, _ -> checkAndRequestPermissions() }
                                .setNegativeButton(R.string.msg_dialog_cancel) { _, _ -> finish() }
                            dialog.show()
                        } else {
                            val dialog = AlertDialog.Builder(this)
                            dialog.setMessage(R.string.msg_request_permissions_settings)
                                .setPositiveButton(R.string.msg_dialog_yes) { _, _ ->
                                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:net.embest.gps.gnsstest")))
                                }
                                .setNegativeButton(R.string.msg_dialog_cancel) { _, _ -> finish() }
                            dialog.show()
                        }
                    }
                }
            }
        }
    }

    private fun onUpdateView() {
        when (mCurrentFragment) {
            SNR_FRAGMENT  -> mSnrFragment.onUpdateView(mGnssInfo!!)
            SEN_FRAGMENT  -> mSensorFragment.onUpdateView(mGnssInfo!!)
            MAP_FRAGMENT  -> mMapFragment.onUpdateView(mGnssInfo!!)
        }
    }

    @Synchronized private fun gpsStart(minTime: Long, minDis:Float) {
        if (!mGnssStarted) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mService!!.requestLocationUpdates(mProvider!!.name, minTime, minDis, mLocationListener)
                mGnssStarted = true
                registerGnssStatusCallback()
                registerGnssMeasurementsCallback()
                registerGnssNavigationMessageCallback()
                registerNmeaMessageListener()
            }
        }
    }

    @Synchronized private fun gpsStop() {
        if (mGnssStarted) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mService!!.removeUpdates(mLocationListener)
                mGnssStarted = false
            }
        }
        unregisterCallbacks()
    }


    private val mLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            mGnssInfo!!.accuracy = location.accuracy
            mGnssInfo!!.speed = location.speed
            mGnssInfo!!.altitude = location.altitude
            mGnssInfo!!.latitude = location.latitude
            mGnssInfo!!.longitude = location.longitude
            mGnssInfo!!.bearing = location.bearing

            if (mGnssInfo!!.time > 0){
                mGnssInfo!!.fixtime = (location.time - mGnssInfo!!.time).toFloat() / 1000
            }
            mGnssInfo!!.time = location.time
            Log.d(TAG, "onLocationChanged ${mGnssInfo!!.latitude}  ${mGnssInfo!!.longitude}  ${mGnssInfo!!.altitude} ${mGnssInfo!!.fixtime}")
            Log.e(TAG, "onLocationChanged: ${mNmeaGenerator.onGenerateNmea(mGnssInfo!!)}")

            if (mPreferences!!.getBoolean("preference_nmea_generator", true)) {
                mFile.writeGeneratorNmea(mRecordFileName, mNmeaGenerator.onGenerateNmea(mGnssInfo!!))
            }
        }

        override fun onProviderDisabled(provider: String) {
            Log.d(TAG, "LocationListener onProviderDisabled")
        }

        override fun onProviderEnabled(provider: String) {
            Log.i(TAG, "LocationListener onProviderEnabled")
        }

        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
            Log.e(TAG, "LocationListener onStatusChanged")
        }
    }

    private fun registerGnssStatusCallback(){
        if (mGnssStatusCallBack == null) {
            mGnssStatusCallBack = object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    mGnssInfo!!.cleanSatellites()
                    var use = 0
                    var view = 0
                    val list: ArrayList<String> = ArrayList()
                    for (i in 0 until status.satelliteCount) {
                        if (status.getCn0DbHz(i) > 0) {
                            val satellite = GnssSatellite()
                            satellite.azimuths = status.getAzimuthDegrees(i)
                            satellite.cn0 = status.getCn0DbHz(i)
                            satellite.constellation = status.getConstellationType(i)
                            satellite.svid = status.getSvid(i)
                            satellite.inUse = status.usedInFix(i)
                            satellite.elevations = status.getElevationDegrees(i)
                            satellite.frequency = status.getCarrierFrequencyHz(i)

                            if (satellite.frequency == 0.0f)
                            {
                                val sat = String.format("%d%4d)", satellite.constellation, satellite.svid)
                                if (list.indexOf(sat) < 0){
                                    list.add(sat)
                                }else{
                                    satellite.frequency = GnssSatellite.GPS_L5_FREQUENCY
                                }
                            }

//                            Log.e(TAG, "SAT:${satellite.svid} Type:${satellite.constellation} is:${satellite.frequency}")

                            mGnssInfo!!.addSatellite(satellite)
                            view++
                            if (status.usedInFix(i)) {
                                use++
                            }
                        }
                    }

                    mGnssInfo!!.inuse = use
                    mGnssInfo!!.inview = view
                    onUpdateView()
                }

                override fun onFirstFix(ttffMillis: Int) {
                    mGnssInfo!!.ttff = ttffMillis/1000f
                    Log.e(TAG, "TTFF: ${mNmeaGenerator.onGenerateFIX(mGnssInfo!!)}")
                }
            }
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mService!!.registerGnssStatusCallback(mGnssStatusCallBack)
            }
        }
    }

    private fun registerGnssMeasurementsCallback(){
        if (mGnssMeasurementsCallBack == null) {
            mGnssMeasurementsCallBack = object : GnssMeasurementsEvent.Callback() {
                override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
                    for (meas in event.measurements) {
                        val measurementStream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            "Raw,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\r\n".format(
                                SystemClock.elapsedRealtime(),
                                event.clock.timeNanos,
                                event.clock.leapSecond,
                                event.clock.timeUncertaintyNanos,
                                event.clock.fullBiasNanos,
                                event.clock.biasNanos,
                                event.clock.biasUncertaintyNanos,
                                event.clock.driftNanosPerSecond,
                                event.clock.driftUncertaintyNanosPerSecond,
                                event.clock.hardwareClockDiscontinuityCount,
                                meas.svid,
                                meas.timeOffsetNanos,
                                meas.state,
                                meas.receivedSvTimeNanos,
                                meas.receivedSvTimeUncertaintyNanos,
                                meas.cn0DbHz,
                                meas.pseudorangeRateMetersPerSecond,
                                meas.pseudorangeRateUncertaintyMetersPerSecond,
                                meas.accumulatedDeltaRangeState,
                                meas.accumulatedDeltaRangeMeters,
                                meas.accumulatedDeltaRangeUncertaintyMeters,
                                meas.carrierFrequencyHz,
                                meas.carrierCycles,
                                meas.carrierPhase,
                                meas.carrierPhaseUncertainty,
                                meas.multipathIndicator,
                                meas.snrInDb,
                                meas.constellationType,
                                meas.automaticGainControlLevelDb,
                                meas.carrierFrequencyHz).replace("NaN","")

                        } else {
                            "Raw,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\r\n".format(
                                SystemClock.elapsedRealtime(),
                                event.clock.timeNanos,
                                event.clock.leapSecond,
                                event.clock.timeUncertaintyNanos,
                                event.clock.fullBiasNanos,
                                event.clock.biasNanos,
                                event.clock.biasUncertaintyNanos,
                                event.clock.driftNanosPerSecond,
                                event.clock.driftUncertaintyNanosPerSecond,
                                event.clock.hardwareClockDiscontinuityCount,
                                meas.svid,
                                meas.timeOffsetNanos,
                                meas.state,
                                meas.receivedSvTimeNanos,
                                meas.receivedSvTimeUncertaintyNanos,
                                meas.cn0DbHz,
                                meas.pseudorangeRateMetersPerSecond,
                                meas.pseudorangeRateUncertaintyMetersPerSecond,
                                meas.accumulatedDeltaRangeState,
                                meas.accumulatedDeltaRangeMeters,
                                meas.accumulatedDeltaRangeUncertaintyMeters,
                                meas.carrierFrequencyHz,
                                meas.carrierCycles,
                                meas.carrierPhase,
                                meas.carrierPhaseUncertainty,
                                meas.multipathIndicator,
                                meas.snrInDb,
                                meas.constellationType,
                                "",
                                meas.carrierFrequencyHz).replace("NaN","")
                        }
//                        Log.e(TAG,"CF:" + meas.carrierFrequencyHz)
//                        Log.e(TAG, measurementStream)
                        if (mPreferences!!.getBoolean("preference_raw_record", true)) {
                            mFile.writeMeasurementFile(mRecordFileName,measurementStream)
                        }
                    }
                }

                override fun onStatusChanged(status: Int) {}
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mService!!.registerGnssMeasurementsCallback(mGnssMeasurementsCallBack)
        }
    }

    private fun registerGnssNavigationMessageCallback(){
        if (mGnssNavigationCallBack == null) {
            mGnssNavigationCallBack = object : GnssNavigationMessage.Callback() {
                override fun onGnssNavigationMessageReceived(event: GnssNavigationMessage) {
                    Log.i(TAG, "NAV : " + event.toString())
                }
                override fun onStatusChanged(status: Int) {}
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mService!!.registerGnssNavigationMessageCallback(mGnssNavigationCallBack)
        }
    }

    private fun registerNmeaMessageListener(){
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            mService!!.addNmeaListener(OnNmeaMessageListener { message, _ ->
                if (mPreferences!!.getBoolean("preference_nmea_record", true)) {
                    mFile.writeNmeaFile(mRecordFileName, message)
                }
            })
        }
    }

    private fun unregisterCallbacks() {
        if (mGnssStatusCallBack != null) {
            mService!!.unregisterGnssStatusCallback(mGnssStatusCallBack)
            mGnssStatusCallBack = null
        }

        if (mGnssMeasurementsCallBack != null) {

            mService!!.unregisterGnssMeasurementsCallback(mGnssMeasurementsCallBack)
            mGnssMeasurementsCallBack = null
        }

        if (mGnssNavigationCallBack != null) {

            mService!!.unregisterGnssNavigationMessageCallback(mGnssNavigationCallBack)
            mGnssNavigationCallBack = null
        }
    }


    companion object {
        private const val TAG = "DR2NMEA"

        private const val REQUEST_ID_MULTIPLE_PERMISSIONS = 1
        private const val SNR_FRAGMENT  = 0
        private const val SEN_FRAGMENT  = 1
        private const val MAP_FRAGMENT  = 2

    }

}
