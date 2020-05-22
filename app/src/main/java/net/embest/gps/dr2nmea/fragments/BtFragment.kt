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

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.clj.fastble.BleManager
import com.clj.fastble.callback.BleGattCallback
import com.clj.fastble.callback.BleScanCallback
import com.clj.fastble.data.BleDevice
import com.clj.fastble.exception.BleException
import com.clj.fastble.scan.BleScanRuleConfig
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.android.synthetic.main.fragment_bt.*
import net.embest.gps.dr2nmea.R
import java.util.*


class BtFragment : Fragment() {

    private var mListener: OnFragmentInteractionListener? = null
    private var mIsInScan = false
    private var mDeviceAdapter: DeviceAdapter? = null
    private var mContext: Context? = null
    private var mDevicesListView: ListView? = null

    private var xAxis: XAxis? = null

    private val mSpeed:ArrayList<Entry> = ArrayList()
    private var mPreferences: SharedPreferences? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        val rootView = inflater.inflate(R.layout.fragment_bt, container, false)
        mContext = rootView.context

        mDevicesListView = rootView.findViewById(R.id.listViewDevices)
        mDeviceAdapter = DeviceAdapter(mContext!!.applicationContext, R.layout.include_device)
        mDevicesListView!!.adapter = mDeviceAdapter

        return rootView
    }

    override fun onResume() {
        super.onResume()

        mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext)

        for(bleDevice in BleManager.getInstance().allConnectedDevice) {
            mDeviceAdapter!!.addDevice(bleDevice)
            mDeviceAdapter!!.notifyDataSetChanged()
        }

        if (mIsInScan){
            buttonScan.text = getString(R.string.text_bt_scan_stop)
        }else{
            buttonScan.text = getString(R.string.text_bt_scan_start)
        }

        buttonScan.setOnClickListener {
            if (mIsInScan){
                BleManager.getInstance().cancelScan()
            }
            else{
                setScanRule()
                startScan()
            }
        }
        mDeviceAdapter!!.notifyDataSetChanged()

        mDeviceAdapter!!.setOnDeviceClickListener(object : DeviceAdapter.OnDeviceClickListener {

            override fun onConnect(bleDevice: BleDevice, isConnected: Boolean) {
                if (isConnected){
                    if (BleManager.getInstance().isConnected(bleDevice)) {
                        BleManager.getInstance().disconnect(bleDevice)
                    }
                }
                else{
                    if (!BleManager.getInstance().isConnected(bleDevice)) {
                        BleManager.getInstance().cancelScan()
                        connect(bleDevice)
                    }
                }
            }
        })

        mSpeed.clear()

        initSetting(lineSensorSpeed,"Speed")
    }

    fun onUpdateView(time :Long, x: Float,y: Float,z: Float, type: Int) {
        mSpeed.add(Entry(mSpeed.size.toFloat(), x))
        setSingleLineData(lineSensorSpeed ,mSpeed)
    }

    private fun setSingleLineData(chart:LineChart, lineYVals: List<Entry>) {
        val lineData = getSingleLine(lineYVals)
        chart.data = lineData
        lineData.notifyDataChanged()

        chart.notifyDataSetChanged()
        chart.setVisibleXRangeMaximum(120.0f)
        chart.moveViewToX(lineData.entryCount.toFloat())
    }

    private fun getSingleLine(lineYVals: List<Entry> ): LineData {
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


    private fun connect(bleDevice: BleDevice) {
        BleManager.getInstance().connect(bleDevice, object : BleGattCallback() {
            override fun onStartConnect() {
            }

            override fun onConnectFail(
                bleDevice: BleDevice,
                exception: BleException
            ) {
//                img_loading!!.clearAnimation()
//                img_loading!!.visibility = View.INVISIBLE
                buttonScan!!.text = getString(R.string.text_bt_scan_start)
                Toast.makeText(
                    mContext,
                    getString(R.string.connect_fail),
                    Toast.LENGTH_LONG
                ).show()
            }

            override fun onConnectSuccess( bleDevice: BleDevice,  gatt: BluetoothGatt, status: Int ) {
                mDeviceAdapter!!.addDevice(bleDevice)
                mDeviceAdapter!!.notifyDataSetChanged()

                val uuid = UUID.fromString(mPreferences!!.getString("preference_bt_speed_uuid", "00001816-0000-1000-8000-00805f9b34fb"))
                val characteristics = gatt.getService(uuid).characteristics
                for (characteristic in characteristics){
                    val charaProp = characteristic.properties
                    if ((charaProp and BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0){
                        mListener?.onBtDeviceNotfiy(bleDevice, characteristic)
                    }
                }
            }

            override fun onDisConnected(
                isActiveDisConnected: Boolean,
                bleDevice: BleDevice,
                gatt: BluetoothGatt,
                status: Int
            ) {
                mDeviceAdapter!!.removeDevice(bleDevice)
                mDeviceAdapter!!.notifyDataSetChanged()
                if (isActiveDisConnected) {
                    Toast.makeText(
                        mContext,
                        getString(R.string.active_disconnected),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        mContext,
                        getString(R.string.disconnected),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })
    }

    private fun setScanRule() {
        val uuids: MutableList<String> = ArrayList()
        uuids.add(mPreferences!!.getString("preference_bt_speed_uuid", "00001816-0000-1000-8000-00805f9b34fb")!!)

        var serviceUuids: Array<UUID?>? = null
        if (uuids.isNotEmpty()) {
            serviceUuids = arrayOfNulls(uuids.size)
            for (i in uuids.indices) {
                val name = uuids[i]
                val components = name.split("-").toTypedArray()
                if (components.size != 5) {
                    serviceUuids[i] = null
                } else {
                    serviceUuids[i] = UUID.fromString(uuids[i])
                }
            }
        }

        val scanRuleConfig = BleScanRuleConfig.Builder()
            .setServiceUuids(serviceUuids)
            .setAutoConnect(false)
            .setScanTimeOut(10000)
            .build()
        BleManager.getInstance().initScanRule(scanRuleConfig)
    }
//
    private fun startScan() {
        BleManager.getInstance().scan(object : BleScanCallback() {
            override fun onScanStarted(success: Boolean) {
                Log.e("BTTTT", "onScanStarted")
                mDeviceAdapter!!.clearScanDevice()
                mDeviceAdapter!!.notifyDataSetChanged()
//                img_loading!!.startAnimation(operatingAnim)
//                img_loading!!.visibility = View.VISIBLE
                mIsInScan = true
                buttonScan.text = getString(R.string.text_bt_scan_stop)
            }

            override fun onScanning(bleDevice: BleDevice) {
                mDeviceAdapter!!.addDevice(bleDevice)
                mDeviceAdapter!!.notifyDataSetChanged()
                Log.e("BTTTT", "onScanning: ${bleDevice.name}, ${bleDevice.mac} ")
            }

            override fun onScanFinished(scanResultList: List<BleDevice>) {
//                img_loading!!.clearAnimation()
//                img_loading!!.visibility = View.INVISIBLE
                Log.e("BTTTT", "onScanFinished")
                mIsInScan = false
                buttonScan.text = getString(R.string.text_bt_scan_start)

            }
        })
    }

    private class DeviceAdapter internal constructor(context: Context, resource: Int) : ArrayAdapter<BleDevice>(context, resource) {
        private val mAdapterDeviceList: MutableList<BleDevice>

        init {
            mAdapterDeviceList = ArrayList()
        }

        fun addDevice(bleDevice: BleDevice) {
            removeDevice(bleDevice)
            mAdapterDeviceList.add(bleDevice)
        }

        fun removeDevice(bleDevice: BleDevice) {
            for (i in mAdapterDeviceList.indices) {
                val device = mAdapterDeviceList[i]
                if (bleDevice.key == device.key) {
                    mAdapterDeviceList.removeAt(i)
                }
            }
        }

        fun clearConnectedDevice() {
            for (i in mAdapterDeviceList.indices) {
                val device = mAdapterDeviceList[i]
                if (BleManager.getInstance().isConnected(device)) {
                    mAdapterDeviceList.removeAt(i)
                }
            }
        }

        fun clearScanDevice() {
            for (i in mAdapterDeviceList.indices) {
                val device = mAdapterDeviceList[i]
                if (!BleManager.getInstance().isConnected(device)) {
                    mAdapterDeviceList.removeAt(i)
                }
            }
        }

        override fun clear() {
            clearConnectedDevice()
            clearScanDevice()
        }

        override fun getCount(): Int {
            return mAdapterDeviceList.size
        }

        override fun getItem(position: Int): BleDevice {
            return (if (position > mAdapterDeviceList.size) null else mAdapterDeviceList[position])!!
        }

        override fun getItemId(position: Int): Long {
            return 0
        }

        private inner class ViewHolder {
            internal var mImgFlag: ImageView? = null
            internal var mTextName: TextView? = null
            internal var mTextMac: TextView? = null
            internal var mTextRssi: TextView? = null
            internal var mBtnConnect: Button? = null
        }

        override fun getView(position: Int, view: View?, parent: ViewGroup): View? {
            var convertView = view
            val holder: ViewHolder
            if (convertView == null) {
                val vi = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                convertView = vi.inflate(R.layout.include_device, parent, false)

                holder = ViewHolder()
                holder.mImgFlag = convertView!!.findViewById(R.id.img_blue)
                holder.mTextName = convertView.findViewById(R.id.txt_name)
                holder.mTextMac = convertView.findViewById(R.id.txt_mac)
                holder.mTextRssi = convertView.findViewById(R.id.txt_rssi)
                holder.mBtnConnect = convertView.findViewById(R.id.btn_connect)


                convertView.tag = holder
            } else {
                holder = convertView.tag as ViewHolder
            }

            val bleDevice = mAdapterDeviceList[position]

            val isConnected = BleManager.getInstance().isConnected(bleDevice)
            val name = bleDevice.name
            val mac = bleDevice.mac
            val rssi = bleDevice.rssi
            holder.mTextName!!.text = name
            holder.mTextMac!!.text = mac
            holder.mTextRssi!!.text = rssi.toString()
            if (isConnected) {
                holder.mImgFlag!!.setImageResource(R.mipmap.ic_blue_connected)
                holder.mTextName!!.setTextColor(-0xe2164a)
                holder.mTextMac!!.setTextColor(-0xe2164a)
                holder.mBtnConnect!!.setText(R.string.disconnect)
            } else {
                holder.mImgFlag!!.setImageResource(R.mipmap.ic_blue_remote)
                holder.mTextName!!.setTextColor(-0x1000000)
                holder.mTextMac!!.setTextColor(-0x1000000)
                holder.mBtnConnect!!.setText(R.string.connect)
            }

            holder.mBtnConnect!!.setOnClickListener(View.OnClickListener {
                if (mButtonListener != null) {
                    mButtonListener!!.onConnect(bleDevice, isConnected)
                }
            })
            return convertView
        }

        interface OnDeviceClickListener {
            fun onConnect(bleDevice: BleDevice, isConnected: Boolean)
        }

        private var mButtonListener: OnDeviceClickListener? = null

        fun setOnDeviceClickListener(listener: OnDeviceClickListener?) {
            mButtonListener = listener
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFragmentInteractionListener) {
            mListener = context
        } else {
            throw RuntimeException("$context must implement OnFragmentInteractionListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
    }

    interface OnFragmentInteractionListener {
        fun onBtDeviceNotfiy(bleDevice: BleDevice, characteristic: BluetoothGattCharacteristic)
    }

    companion object {
        fun newInstance() = BtFragment()
    }
}