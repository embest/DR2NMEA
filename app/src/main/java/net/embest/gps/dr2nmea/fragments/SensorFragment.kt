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

package net.embest.gps.dr2nmea.fragments

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorManager
import net.embest.gps.dr2nmea.R

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.support.v4.app.Fragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis

import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.components.YAxis
import kotlinx.android.synthetic.main.fragment_sensor.*
import kotlin.collections.ArrayList





class SensorFragment : Fragment() {

    private var xAxis: XAxis? = null

    private val mAccX:ArrayList<Float> = ArrayList()
    private val mAccY:ArrayList<Float> = ArrayList()
    private val mAccZ:ArrayList<Float> = ArrayList()

    private val mGyroX:ArrayList<Float> = ArrayList()
    private val mGyroY:ArrayList<Float> = ArrayList()
    private val mGyroZ:ArrayList<Float> = ArrayList()

    private val mPressure:ArrayList<Entry> = ArrayList()
    private val mMagnetic:ArrayList<Entry> = ArrayList()


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_sensor, container, false)
    }

    override fun onResume() {
        super.onResume()

        val mSensorManager = context!!.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        val mSensorAcc = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val mSensorGyro = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val mSensorBaro = mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        val mSensorMagnetic = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        mAccX.clear()
        mAccY.clear()
        mAccZ.clear()
        mGyroX.clear()
        mGyroY.clear()
        mGyroZ.clear()
        mPressure.clear()
        mMagnetic.clear()

        lineSensorAcc.visibility = View.GONE
        lineSensorGyro.visibility = View.GONE
        lineSensorBaro.visibility = View.GONE
        lineSensorMag.visibility = View.GONE

        if (mSensorAcc != null){
            initSetting(lineSensorAcc,"Accelerometer :${mSensorAcc.name}")
            lineSensorAcc.visibility = View.VISIBLE
        }
        if (mSensorGyro != null){
            initSetting(lineSensorGyro,"Gyroscope: ${mSensorGyro.name}")
            lineSensorGyro.visibility = View.VISIBLE
        }
        if (mSensorBaro != null){
            initSetting(lineSensorBaro,"Pressure: ${mSensorBaro.name}")
            lineSensorBaro.visibility = View.VISIBLE
        }
        if (mSensorMagnetic != null){
            initSetting(lineSensorMag, "Magnetic: ${mSensorMagnetic.name}")
            lineSensorMag.visibility = View.VISIBLE
        }
    }

    fun onUpdateView(time :Long, x: Float,y: Float,z: Float, type: Int) {
        when (type){
            Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> {
                val names:ArrayList<String> = ArrayList()
                names.add("ACC:X")
                names.add("ACC:Y")
                names.add("ACC:Z")

                val colors:ArrayList<Int> = ArrayList()
                colors.add(Color.BLUE)
                colors.add(Color.RED)
                colors.add(Color.GREEN)

                val yLineData:ArrayList<ArrayList<Float>> = ArrayList()

                mAccX.add(x)
                mAccY.add(y)
                mAccZ.add(z)

                yLineData.add(mAccX)
                yLineData.add(mAccY)
                yLineData.add(mAccZ)

                setMoreLineData(lineSensorAcc, yLineData, names, colors)
            }
            Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> {
                val names:ArrayList<String> = ArrayList()
                names.add("GYRO:X")
                names.add("GYRO:Y")
                names.add("GYRO:Z")

                val colors:ArrayList<Int> = ArrayList()
                colors.add(Color.BLUE)
                colors.add(Color.RED)
                colors.add(Color.GREEN)

                val yLineData:ArrayList<ArrayList<Float>> = ArrayList()

                mGyroX.add(x)
                mGyroY.add(y)
                mGyroZ.add(z)

                yLineData.add(mGyroX)
                yLineData.add(mGyroY)
                yLineData.add(mGyroZ)

                setMoreLineData(lineSensorGyro, yLineData, names, colors)
            }
            Sensor.TYPE_PRESSURE -> {
                mPressure.add(Entry(mPressure.size.toFloat(), x))
                setSingleLineData(lineSensorBaro ,mPressure)
            }
            Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> {
                mMagnetic.add(Entry(mMagnetic.size.toFloat(), x))
                setSingleLineData(lineSensorMag ,mMagnetic)
            }
        }
    }


    fun onUpdateAccuracy(type: Int, accuracy: Int) {
        when (type){
            Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> {
                val description = lineSensorAcc.description
                description.isEnabled = true
                description.text = "Accuracy:$accuracy"
                description.setPosition( lineSensorAcc.width/2.0F, lineSensorAcc.height/2.0F)
                description.textAlign = Paint.Align.CENTER
            }
            Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> {
                val description = lineSensorGyro.description
                description.isEnabled = true
                description.text = "Accuracy:$accuracy"
                description.setPosition( lineSensorGyro.width/2.0F, lineSensorGyro.height/2.0F)
                description.textAlign = Paint.Align.CENTER
            }
            Sensor.TYPE_PRESSURE -> {
                val description = lineSensorBaro.description
                description.isEnabled = true
                description.text = "Accuracy:$accuracy"
                description.setPosition( lineSensorBaro.width/2.0F, lineSensorBaro.height/2.0F)
                description.textAlign = Paint.Align.CENTER
            }
            Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> {
                val description = lineSensorMag.description
                description.isEnabled = true
                description.text = "Accuracy:$accuracy"
                description.setPosition( lineSensorMag.width/2.0F, lineSensorMag.height/2.0F)
                description.textAlign = Paint.Align.CENTER
            }
        }
    }

    private fun initSetting(chart: LineChart, description: String) {
        chart.description.text = ""
        chart.description.textColor = Color.RED
        chart.description.textSize = 16f
        chart.setNoDataText(description)

        xAxis = chart.xAxis
        xAxis!!.position = XAxis.XAxisPosition.BOTTOM
        xAxis!!.textSize = 12f
        xAxis!!.textColor = Color.RED
        xAxis!!.isEnabled = true
        xAxis!!.setDrawLabels(true)
        xAxis!!.setDrawGridLines(true)
        xAxis!!.enableGridDashedLine(2f, 2f, 2f)
        xAxis!!.labelRotationAngle = 0f
        xAxis!!.granularity = 1f

        val yAxisLef = chart.axisLeft
        yAxisLef.textSize = 14f
        val yAxisRight = chart.axisRight
        yAxisRight.isEnabled = false
    }


    private fun setSingleLineData(chart:LineChart, lineYVals: List<Entry>) {
        val lineData = getSingleLine(lineYVals)
        chart.data = lineData
        lineData.notifyDataChanged()

        chart.notifyDataSetChanged()
        chart.setVisibleXRangeMaximum(120.0f)
        chart.moveViewToX(lineData.entryCount.toFloat())
    }

    private fun setMoreLineData(chart: LineChart, lineChartYs: List<List<Float>>,
                                lineNames: List<String>, lineColors: List<Int>) {
        val lineData = getMoreLine(lineChartYs, lineNames, lineColors)
        chart.data = lineData
        lineData.notifyDataChanged()

        chart.notifyDataSetChanged()
        chart.setVisibleXRangeMaximum(120.0f)
        chart.moveViewToX(lineData.entryCount.toFloat())
    }

    private fun getSingleLine(lineYVals: List<Entry> ):LineData{
        val lineDataSet = LineDataSet(lineYVals, "")
        lineDataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        lineDataSet.highLightColor = Color.RED
        lineDataSet.isHighlightEnabled = false
        lineDataSet.color = Color.BLACK
        lineDataSet.setCircleColor(Color.BLUE)
        lineDataSet.setDrawCircles(false)
        lineDataSet.setDrawValues(false)
        lineDataSet.valueTextColor = Color.RED
        lineDataSet.valueTextSize = 14f
        return LineData(lineDataSet)
    }

    private fun getMoreLine( lineChartYs:List<List<Float>>,  lineNames:List<String>,  lineColors:List<Int>):LineData{
        val lineData =  LineData()
        for (i in 0 until lineChartYs.size){
            val yValues:ArrayList<Entry> =  ArrayList()
            for (j in 0 until  lineChartYs[i].size) {
                yValues.add( Entry(j.toFloat(), lineChartYs[i][j]))
            }
            val lineDataSet = LineDataSet(yValues, lineNames[i])
            lineDataSet.color = lineColors[i]
            lineDataSet.setCircleColor(lineColors[i])
            lineDataSet.valueTextColor = lineColors[i]
            lineDataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
            lineDataSet.highLightColor = Color.RED
            lineDataSet.isHighlightEnabled = false
            lineDataSet.setDrawCircles(false)
            lineDataSet.setDrawValues(false)
            lineDataSet.valueTextSize = 14f
            lineDataSet.axisDependency = YAxis.AxisDependency.LEFT
            lineData.addDataSet(lineDataSet)
        }
        return lineData
    }


    companion object {
        fun newInstance() = SensorFragment()
    }
}