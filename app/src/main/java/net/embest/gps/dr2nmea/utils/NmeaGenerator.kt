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

    private fun GenerateGGA(info: GnssInfo): String{
        //$GPGGA,054426.00,3110.772682,N,12135.844892,E,1,27,0.3,6.8,M,10.0,M,,*66
        var gpgga = "\$GPGGA,,,,,,0,,,,,,,,"

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

            gpgga = "\$GPGGA,$utc_time,$latitude,$north_south,$longitude,$east_west,1,12,$dop,$altitude,M,10.0,M,,"
        }
        gpgga = gpgga + "*" + calculateChecksum(gpgga)

        return gpgga
    }


    private fun GenerateRMC(info: GnssInfo): String{
        //$GPRMC,054425.00,A,3110.772186,N,12135.844962,E,001.8,341.7,160119,,,A*55
        var gprmc = "\$GPRMC,,V,,,,,,,,,,N"

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

            gprmc = "\$GPRMC,$utc_time,A,$latitude,$north_south,$longitude,$east_west,$speed,$bearing,$utc_date,,,A"
        }
        gprmc = gprmc + "*" + calculateChecksum(gprmc)

        return gprmc
    }

     fun GenerateFIX(info: GnssInfo): String{
        //$PGLOR,1,FIX,1.0,1.0*20
        var fix = ""

        if (info.ttff > 0) {
            val time = if (info.fixtime > 0){
                String.format("%.1f", info.fixtime)
            } else{
                String.format("%.1f", info.ttff)
            }

            fix = "\$PGLOR,1,FIX,$time,$time"
            fix = fix + "*" + calculateChecksum(fix)
        }
        return fix
    }


    fun GenerateNmea(info: GnssInfo): String {
        val nmea = GenerateGGA(info) + "\r\n" + GenerateFIX(info) + "\r\n" + GenerateRMC(info) + "\r\n"
        return nmea
    }
}