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

package net.embest.gps.dr2nmea.utils

import android.os.Build

import android.os.Environment
import android.util.Log
import java.io.*


class FileHelper {

    private val mExternalPath = EXTERNAL_PATH + "DR2NMEA/"

    init {
        val file = File(mExternalPath)
        if (!file.exists()) {
            file.mkdirs()
        }
    }

    fun writeNmeaFile(fileName: String, data: String) {
        if (mExternalPath != "") {
            try {
                val file = File(mExternalPath + fileName + "_nmea.txt")
                if (!file.exists()) {

                    file.createNewFile()
                }
                val stream = OutputStreamWriter(FileOutputStream(file, true))

                stream.write(data)
                stream.close()
            } catch (e: Exception) {
                Log.d(TAG, "file create error")
            }
        }
    }

    fun writeGeneratorNmea(fileName: String, data: String) {
        if (mExternalPath != "") {
            try {
                val file = File("$mExternalPath$fileName.nmea")
                if (!file.exists()) {

                    file.createNewFile()
                }
                val stream = OutputStreamWriter(FileOutputStream(file, true))

                stream.write(data)
                stream.close()
            } catch (e: Exception) {
                Log.d(TAG, "file create error")
            }
        }
    }

    fun writeMeasurementFile(fileName: String, data: String) {
        if (mExternalPath != "") {
            try {
                val file = File(mExternalPath + fileName + "_raw.txt")
                if (!file.exists()) {

                    file.createNewFile()

                    val fileVersion = (
                            "# Version: "
                                    + LOGGER_VERSION
                                    + " Platform: "
                                    + Build.VERSION.RELEASE
                                    + " "
                                    + "Manufacturer: "
                                    + Build.MANUFACTURER
                                    + " "
                                    + "Model: "
                                    + Build.MODEL
                                    + "\r\n")

                    val header: String = ("# \r\n# Header Description:\r\n# \r\n"
                            + fileVersion
                            + "# Raw,ElapsedRealtimeMillis,TimeNanos,LeapSecond,TimeUncertaintyNanos,FullBiasNanos,"
                            + "BiasNanos,BiasUncertaintyNanos,DriftNanosPerSecond,DriftUncertaintyNanosPerSecond,"
                            + "HardwareClockDiscontinuityCount,Svid,TimeOffsetNanos,State,ReceivedSvTimeNanos,"
                            + "ReceivedSvTimeUncertaintyNanos,Cn0DbHz,PseudorangeRateMetersPerSecond,"
                            + "PseudorangeRateUncertaintyMetersPerSecond,"
                            + "AccumulatedDeltaRangeState,AccumulatedDeltaRangeMeters,"
                            + "AccumulatedDeltaRangeUncertaintyMeters,CarrierFrequencyHz,CarrierCycles,"
                            + "CarrierPhase,CarrierPhaseUncertainty,MultipathIndicator,SnrInDb,"
                            + "ConstellationType,AgcDb,CarrierFrequencyHz\r\n"
                            + "# \r\n")
                    val stream = OutputStreamWriter(FileOutputStream(file, true))
                    stream.write(header)
                    stream.close()
                }
                val stream = OutputStreamWriter(FileOutputStream(file, true))

                stream.write(data)
                stream.close()
            } catch (e: Exception) {
                Log.d(TAG, "file create error")
            }
        }
    }

    companion object {
        private const val TAG = "GNSSTestFile"
        private const val LOGGER_VERSION  = "v2.0.0.1"
        private val EXTERNAL_PATH = Environment.getExternalStorageDirectory().path + "/"
    }
}
