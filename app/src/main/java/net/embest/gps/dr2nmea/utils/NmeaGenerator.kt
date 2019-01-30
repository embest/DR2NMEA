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

import java.text.SimpleDateFormat
import java.util.*

class NmeaGenerator {
    var time: Long = 0

    init {

    }

    private fun calculateChecksum(data: String): String {
        val array = data.toByteArray()
        var sum = array[1]
        for (i in 2 until data.length) {
            val one = sum.toInt()
            val two = array[i].toInt()
            val xor = one xor two
            sum = (0xff and xor).toByte()
        }
        return String.format("%02X ", sum).trim { it <= ' ' }
    }


    private fun formatNmeaLongitude(longitude: Double): String {

        val lon = Math.abs(longitude)
        val degree = String.format("%03d", lon.toInt())
        val minutes = String.format("%02d", ((lon % 1) * 60).toInt())
        val decimal = String.format("%06d", Math.round(((lon % 1) * 60)%1*1000000).toInt())

        return "$degree$minutes.$decimal"
    }

    private fun formatNmeaLatitude(latitude: Double): String {
        val lat = Math.abs(latitude)
        val degree = String.format("%02d", lat.toInt())
        val minutes = String.format("%02d", ((lat % 1) * 60).toInt())
        val decimal = String.format("%06d", Math.round(((lat % 1) * 60)%1*1000000).toInt())

        return "$degree$minutes.$decimal"
    }

    private fun onGenerateGGA(info: GnssInfo): String{
        //$GPGGA,054426.00,3110.772682,N,12135.844892,E,1,27,0.3,6.8,M,10.0,M,,*66
        var nmea = "\$GPGGA,,,,,,0,,,,,,,,"

        if (info.ttff > 0 || info.time > 0) {
            var north = "S"
            var east = "W"

            if (info.latitude > 0) {
                north = "N"
            }

            if (info.longitude > 0) {
                east = "E"
            }
            val utcTimeFmt = SimpleDateFormat("HHmmss.SS", Locale.US)
            utcTimeFmt.timeZone = TimeZone.getTimeZone("UTC")

            val time = utcTimeFmt.format(info.time)

            val latitude = formatNmeaLatitude(info.latitude)
            val longitude = formatNmeaLongitude(info.longitude)

            val dop = String.format("%.1f", info.accuracy / 10)
            val altitude = String.format("%.1f", info.altitude - 10)

            var used = 0
            for (sat in info.satellites){
                if (sat.inUse)
                    used++
            }

            nmea = "\$GPGGA,$time,$latitude,$north,$longitude,$east,1,$used,$dop,$altitude,M,10.0,M,,"
        }
        nmea = nmea + "*" + calculateChecksum(nmea) + "\r\n"

        return nmea
    }


    private fun onGenerateRMC(info: GnssInfo): String{
        //$GPRMC,054425.00,A,3110.772186,N,12135.844962,E,001.8,341.7,160119,,,A*55
        var nmea = "\$GPRMC,,V,,,,,,,,,,N"

        if (info.ttff > 0 || info.time > 0) {
            var north = "S"
            var east = "W"

            if (info.latitude > 0) {
                north = "N"
            }

            if (info.longitude > 0) {
                east = "E"
            }
            val utcTimeFmt = SimpleDateFormat("HHmmss.SS", Locale.US)
            val utcDateFmt = SimpleDateFormat("ddMMyy", Locale.US)
            utcTimeFmt.timeZone = TimeZone.getTimeZone("UTC")
            utcDateFmt.timeZone = TimeZone.getTimeZone("UTC")

            val time = utcTimeFmt.format(info.time)
            val date = utcDateFmt.format(info.time)

            val latitude = formatNmeaLatitude(info.latitude)
            val longitude = formatNmeaLongitude(info.longitude)

            val speed = String.format("%.1f", info.speed)
            val bearing = String.format("%.1f", info.bearing)

            nmea = "\$GPRMC,$time,A,$latitude,$north,$longitude,$east,$speed,$bearing,$date,,,A"
        }
        nmea = nmea + "*" + calculateChecksum(nmea) + "\r\n"

        return nmea
    }

     fun onGenerateFIX(info: GnssInfo): String{
        //$PGLOR,1,FIX,1.0,1.0*20
        var nmea = ""

        if (info.ttff > 0 || info.time > 0) {
            val time = if (info.fixtime > 0){
                String.format("%.1f", info.fixtime)
            } else{
                String.format("%.1f", info.ttff)
            }

            nmea = "\$PGLOR,1,FIX,$time,$time"
            nmea = nmea + "*" + calculateChecksum(nmea) + "\r\n"
        }
        return nmea
    }


    private fun onGenerateGSV(info: GnssInfo): String{
        var nmea = ""

        val talkers = arrayOf("\$UNGSV", "\$GPGSV", "\$SBGSV", "\$GLGSV", "\$QZGSV", "\$BDGSV", "\$GAGSV", "\$NCGSV")
        val dfEnd = arrayOf("", ",8", "", "", ",8", "", ",1", ",8")
        val svid2Nmea = arrayOf(0, 0, 32, 64, 1, 200, 100, 0)

        val satellites = info.satellites.sortedBy { it.svid }.sortedBy { it.constellation }.sortedBy { it.frequency }

        for (constellation in 1 until 7) {
            val satL1 = satellites.filter { it.constellation == constellation }.filter { Math.abs(it.frequency  - GnssSatellite.GPS_L5_FREQUENCY) > 200.0 }
            val satL5 = satellites.filter { it.constellation == constellation }.filter { Math.abs(it.frequency  - GnssSatellite.GPS_L5_FREQUENCY) < 200.0 }

            val number = satellites.filter { it.constellation == constellation } .size
            val total = Math.ceil(satL1.size/4.0).toInt()+Math.ceil(satL5.size/4.0).toInt()
            var index = 1
            var gsv = ""
            for ((count, sat) in satL1.withIndex()) {
                gsv += ",${sat.svid},${sat.elevations.toInt()},${sat.azimuths.toInt()},${sat.cn0.toInt()}"
                if ((count+1)%4 ==0 || count+1 == satL1.size ){
                    gsv = "${talkers[constellation]},$total,$index,$number$gsv"
                    nmea += gsv + "*" + calculateChecksum(gsv) +"\r\n"
                    index ++
                    gsv = ""
                }
            }

            gsv = ""
            for ((count, sat) in satL5.withIndex()) {
                gsv += ",${sat.svid},${sat.elevations.toInt()},${sat.azimuths.toInt()},${sat.cn0.toInt()}"
                if ((count+1)%4 ==0 || count+1 == satL5.size ){
                    gsv = "${talkers[constellation]},$total,$index,$number$gsv${dfEnd[constellation]}"
                    nmea += gsv + "*" + calculateChecksum(gsv) +"\r\n"
                    index ++
                    gsv = ""
                }
            }
        }

        return nmea
    }

//    private fun onGenerateGSA(info: GnssInfo): String{
//        var nmea = ""
//
//        val talkers = arrayOf("\$UNGSA", "\$GPGSA", "\$SBGSA", "\$GNGSA", "\$QZGSA", "\$BDGSA", "\$GAGSA", "\$NCGSA")
//        val svid2Nmea = arrayOf(0, 0, 32, 64, 1, 200, 100, 0)
//
//        val satellites = info.satellites.sortedBy { it.svid }.sortedBy { it.constellation }.sortedBy { it.frequency }
//
//        for (constellation in 1 until 7) {
//            val satL1 = satellites.filter { it.constellation == constellation }.filter { Math.abs(it.frequency  - GnssSatellite.GPS_L5_FREQUENCY) > 200.0 }
//            val number = satL1.size
//            var index = 0
//            var gsa = ""
//            for ((count, j) in satL1.withIndex()) {
//                if (j.inUse) {
//                    gsa += ",${j.svid}"
//                    index++
//                    if ((count + 1) % 12 == 0 || count + 1 == number) {
//                        if (index < 12) {
//                            for (k in 1 until 12 - index) {
//                                gsa += ","
//                            }
//                        }
//                        gsa = "${talkers[constellation]},A,3$gsa,,,"
//                        nmea += gsa + "*" + calculateChecksum(gsa) + "\r\n"
//                        gsa = ""
//                        index = 0
//                    }
//                }
//            }
//        }
//
//        return  nmea
//    }

    fun onGenerateNmea(info: GnssInfo): String {
        return onGenerateGGA(info) + onGenerateGSV(info) + onGenerateFIX(info) + onGenerateRMC(info)
    }
}