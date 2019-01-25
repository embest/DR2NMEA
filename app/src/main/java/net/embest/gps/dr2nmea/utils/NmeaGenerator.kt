package net.embest.gps.dr2nmea.utils

import java.text.SimpleDateFormat
import java.util.*

class NmeaGenerator {
    var time: Long = 0

    init {

    }

    private fun calculateChecksum(data: String): String {
        val array = data.toByteArray()
        var cksum = array[1]
        for (i in 2 until data.length) {
            val one = cksum.toInt()
            val two = array[i].toInt()
            val xor = one xor two
            cksum = (0xff and xor).toByte()
        }
        return String.format("%02X ", cksum).trim { it <= ' ' }
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

        if (info.ttff > 0) {
            var north_south = "S"
            var east_west = "W"

            if (info.latitude > 0) {
                north_south = "N"
            }

            if (info.longitude > 0) {
                east_west = "E"
            }
            val utc_time_fmt = SimpleDateFormat("HHmmss.SS", Locale.US)
            utc_time_fmt.timeZone = TimeZone.getTimeZone("UTC")

            val utc_time = utc_time_fmt.format(info.time)

            val latitude = formatNmeaLatitude(info.latitude)
            val longitude = formatNmeaLongitude(info.longitude)

            val dop = String.format("%.1f", info.accuracy / 10)
            val altitude = String.format("%.1f", info.altitude - 10)

            var used = 0
            for (sat in info.satellites){
                if (sat.inUse)
                    used++
            }

            nmea = "\$GPGGA,$utc_time,$latitude,$north_south,$longitude,$east_west,1,$used,$dop,$altitude,M,10.0,M,,"
        }
        nmea = nmea + "*" + calculateChecksum(nmea) + "\r\n"

        return nmea
    }


    private fun onGenerateRMC(info: GnssInfo): String{
        //$GPRMC,054425.00,A,3110.772186,N,12135.844962,E,001.8,341.7,160119,,,A*55
        var nmea = "\$GPRMC,,V,,,,,,,,,,N"

        if (info.ttff > 0) {
            var north_south = "S"
            var east_west = "W"

            if (info.latitude > 0) {
                north_south = "N"
            }

            if (info.longitude > 0) {
                east_west = "E"
            }
            val utc_time_fmt = SimpleDateFormat("HHmmss.SS", Locale.US)
            val utc_date_fmt = SimpleDateFormat("ddMMyy", Locale.US)
            utc_time_fmt.timeZone = TimeZone.getTimeZone("UTC")
            utc_date_fmt.timeZone = TimeZone.getTimeZone("UTC")

            val utc_time = utc_time_fmt.format(info.time)
            val utc_date = utc_date_fmt.format(info.time)

            val latitude = formatNmeaLatitude(info.latitude)
            val longitude = formatNmeaLongitude(info.longitude)

            val speed = String.format("%.1f", info.speed)
            val bearing = String.format("%.1f", info.bearing)

            nmea = "\$GPRMC,$utc_time,A,$latitude,$north_south,$longitude,$east_west,$speed,$bearing,$utc_date,,,A"
        }
        nmea = nmea + "*" + calculateChecksum(nmea) + "\r\n"

        return nmea
    }

     fun onGenerateFIX(info: GnssInfo): String{
        //$PGLOR,1,FIX,1.0,1.0*20
        var nmea = ""

        if (info.ttff > 0) {
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


    private fun onGenerteGSV(info: GnssInfo): String{
        var nmea = ""

        val talkers = arrayOf("\$UNGSV", "\$GPGSV", "\$SBGSV", "\$GLGSV", "\$QZGSV", "\$BDGSV", "\$GAGSV", "\$NCGSV")
        val df_end = arrayOf("", ",8", "", "", ",8", "", ",1", ",8")

        val satellite: ArrayList<GnssSatellite> = ArrayList()
        satellite.addAll(info.satellites.sortedBy { it.svid }.sortedBy { it.constellation }.sortedBy { it.frequency })

        for (i in 1 until 7) {
            val sat_l1 = satellite.filter { it.constellation == i } .filter { Math.abs(it.frequency  - GnssSatellite.GPS_L5_FREQUENCY) > 200.0 }
            val sat_l5 = satellite.filter { it.constellation == i } .filter { Math.abs(it.frequency  - GnssSatellite.GPS_L5_FREQUENCY) < 200.0 }

            val number = satellite.filter { it.constellation == i } .size
            var totle = Math.ceil(sat_l1.size/4.0).toInt()+Math.ceil(sat_l5.size/4.0).toInt()
            var index = 1
            var gsv = ""
            for ((count, j) in sat_l1.withIndex()) {
                gsv += ",${j.svid},${j.elevations.toInt()},${j.azimuths.toInt()},${j.cn0.toInt()}"
                if ((count+1)%4 ==0 || count+1 == sat_l1.size ){
                    gsv = "${talkers[i]},$totle,$index,$number$gsv"
                    nmea += gsv + "*" + calculateChecksum(gsv) +"\r\n"
                    index ++
                    gsv = ""
                }
            }

            gsv = ""
            for ((count, j) in sat_l5.withIndex()) {
                gsv += ",${j.svid},${j.elevations.toInt()},${j.azimuths.toInt()},${j.cn0.toInt()}"
                if ((count+1)%4 ==0 || count+1 == sat_l5.size ){
                    gsv = "${talkers[i]},$totle,$index,$number$gsv${df_end[i]}"
                    nmea += gsv + "*" + calculateChecksum(gsv) +"\r\n"
                    index ++
                    gsv = ""
                }
            }
        }

//        for (i in 1 until 7) {
//            val sat = satellite.filter { it.constellation == i } .filter { Math.abs(it.frequency  - GnssSatellite.GPS_L5_FREQUENCY) < 200.0 }
//            val number = sat.size
//            val totle = Math.ceil(number/4.0).toInt()
//            var index = 1
//            var gsv = ""
//            for ((count, j) in sat.withIndex()) {
//                gsv += ",${j.svid},${j.elevations.toInt()},${j.azimuths.toInt()},${j.cn0.toInt()}"
//                if ((count+1)%4 ==0 || count+1 == number ){
//                    gsv = "${talkers[i]},$totle,$index,$number$gsv${df_end[i]}"
//                    nmea += gsv + "*" + calculateChecksum(gsv) +"\r\n"
//                    index ++
//                    gsv = ""
//                }
//            }
//        }

        return nmea
    }

//    private fun GenerteGSA(info: GnssInfo): String{
//        var nmea = ""
//
//        val talkers = arrayOf("\$UNGSA", "\$GPGSA", "\$SBGSA", "\$GNGSA", "\$QZGSA", "\$BDGSA", "\$GAGSA", "\$NCGSA")
//
//        val satellite: ArrayList<GnssSatellite> = ArrayList()
//        satellite.addAll(info.satellites.sortedBy { it.svid }.sortedBy { it.constellation }.sortedBy { it.frequency })
//
//        for (i in 1 until 7) {
//            val sat = satellite.filter { it.constellation == i }
//            val number = sat.size
//            var index = 0
//            var gsa = ""
//            for ((count, j) in sat.withIndex()) {
//                if (j.inUse) {
//                    gsa += ",${j.svid}"
//                    index++
//                    if ((count + 1) % 12 == 0 || count + 1 == number) {
//                        if (index < 12) {
//                            for (k in 1 until 12 - index) {
//                                gsa += ","
//                            }
//                        }
//                        gsa = "${talkers[i]},A,3$gsa,,,"
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
        return onGenerateGGA(info) + onGenerteGSV(info) + onGenerateFIX(info) + onGenerateRMC(info)
    }
}