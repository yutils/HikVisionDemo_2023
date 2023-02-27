package com.kotlinx.hk

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.hcnetsdk.HKCamera
import com.kotlinx.hk.databinding.ActivityMainBinding
import com.yujing.utils.YConvert
import com.yujing.utils.YImageDialog
import com.yujing.utils.YPermissions
import com.yujing.utils.YToast
import java.util.regex.Pattern

/**
 * 海康威视202212月demo核心播放功能，剥离
 */
class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding
    lateinit var hkCamera: HKCamera
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        YPermissions.requestAll(this)//获取权限
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        binding.btnUp.setOnTouchListener(touchListener)
        binding.btnDown.setOnTouchListener(touchListener)
        binding.btnLeft.setOnTouchListener(touchListener)
        binding.btnRight.setOnTouchListener(touchListener)
        binding.btnZoomIn.setOnTouchListener(touchListener)
        binding.btnZoomOut.setOnTouchListener(touchListener)

        hkCamera = HKCamera(binding.SurfacePreviewPlay).apply {
            devName = "余静的摄像头"
            ip = "192.168.1.31"
            port = "8000"
            username = "admin"
            password = "pw@123456"
        }
        hkCamera.init()

        //配置通道和类型
        var iAnalogStartChan = hkCamera.m_byStartChan
        var iDigitalStartChan = hkCamera.m_byStartDChan
        val listChannel: MutableList<String> = ArrayList()
        for (indexChanNum in 0 until hkCamera.m_byChanNum) {
            listChannel.add("ACamera_$iAnalogStartChan")
            iAnalogStartChan++
        }
        for (indexChanNum in 0 until hkCamera.m_IPChanNum) {
            listChannel.add("DCamera_$iDigitalStartChan")
            iDigitalStartChan++
        }
        val channelAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listChannel)
        channelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.bychanSpinner.adapter = channelAdapter
        binding.bychanSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            //通过此方法为下拉列表设置点击事件
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View, i: Int, l: Long) {
                val text = binding.bychanSpinner.getItemAtPosition(i).toString()
                hkCamera.m_iSelectChannel = Integer.valueOf(getChannel(text)).toInt()
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {}
        }
        //stream Type
        val listStream: MutableList<String> = ArrayList()
        listStream.add("主码流")
        listStream.add("子码流")
        listStream.add("三码流")
        val streamAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listStream)
        streamAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.streamSpinnerSurface.adapter = streamAdapter
        binding.streamSpinnerSurface.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            //通过此方法为下拉列表设置点击事件
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View, i: Int, l: Long) {
                hkCamera.m_iSelectStreamType = i
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {}
        }
    }

    fun onClick(v: View) {
        when (v.id) {
            R.id.button_preview_start -> {
                val success = hkCamera.start()
                YToast.show("开始播放${if (success) "成功" else "失败"}", Toast.LENGTH_SHORT)
            }
            R.id.button_preview_stop -> {
                val success = hkCamera.stop()
                YToast.show("停止播放${if (success) "成功" else "失败"}", Toast.LENGTH_SHORT)
            }
            R.id.button_preview_snap -> {
                val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).path + "/picture_${System.currentTimeMillis()}.bmp"
                val success = hkCamera.snap(path)
                YToast.show("拍照${if (success) "成功：${path}" else "失败"}", Toast.LENGTH_SHORT)
            }
            R.id.button_preview_record -> {
                val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).path + "/video_${System.currentTimeMillis()}.mp4"
                val success = hkCamera.recordStart(path)
                YToast.show("开始录像${if (success) "成功：${path}" else "失败"}", Toast.LENGTH_SHORT)
            }
            R.id.button_preview_record_stop -> {
                val success = hkCamera.recordStop()
                YToast.show("停止录像录像${if (success) "成功" else "失败"}", Toast.LENGTH_SHORT)
            }
            R.id.button_picture -> {
                val timeStart = System.currentTimeMillis()
                val bitmap: Bitmap = hkCamera.takePicture() ?: return YToast.showSpeak("拍照失败")
                YImageDialog.show(bitmap)
                YToast.show(
                    """
                    分辨率:${bitmap.width}*${bitmap.height}
                    耗时：${System.currentTimeMillis() - timeStart}
                    """.trimIndent()
                )
            }
            R.id.button_show_text -> {
                val success = hkCamera.showString("你好，我是余静！")
                YToast.show("叠加文字${if (success) "成功" else "失败"}", Toast.LENGTH_SHORT)
            }
            R.id.button_clear_text -> {
                val success = hkCamera.showString("")
                YToast.show("叠加文字${if (success) "成功" else "失败"}", Toast.LENGTH_SHORT)
            }
            R.id.button_open_serialPort -> {
                val success = hkCamera.openSerialTrans(if (binding.serialPortType.isChecked) 2 else 1) { iSerialHandle, bytes, iBufSize ->
                    val hex = YConvert.bytesToHexString(bytes)
                    YToast.show("收到内容：${hex}")
                }
                YToast.show("打开串口${if (success) "成功" else "失败"}", Toast.LENGTH_SHORT)
            }
            R.id.button_send_serialPort -> {
                val success = hkCamera.sendSerialPort("123".toByteArray(), 1, if (binding.serialPortType.isChecked) 2 else 1)
                YToast.show("发送数据${if (success) "成功" else "失败"}", Toast.LENGTH_SHORT)
            }
            R.id.button_close_serialPort -> {
                val success = hkCamera.closeSerialTrans()
                YToast.show("关闭串口${if (success) "成功" else "失败"}", Toast.LENGTH_SHORT)
            }
        }
    }

    //按下事件监听
    private val touchListener = View.OnTouchListener { v, event ->
        Log.d("log", "touch:${v.id} ${event!!.action}")
        Thread {
            when (v!!.id) {
                R.id.btn_Up -> {
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        hkCamera.startMove(8, hkCamera.m_iUserID)
                    }
                    if (event.action == MotionEvent.ACTION_UP) {
                        hkCamera.stopMove(8, hkCamera.m_iUserID)
                    }
                }
                R.id.btn_Left -> {
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        hkCamera.startMove(4, hkCamera.m_iUserID)
                    }
                    if (event.action == MotionEvent.ACTION_UP) {
                        hkCamera.stopMove(4, hkCamera.m_iUserID)
                    }
                }
                R.id.btn_Right -> {
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        hkCamera.startMove(6, hkCamera.m_iUserID)
                    }
                    if (event.action == MotionEvent.ACTION_UP) {
                        hkCamera.stopMove(6, hkCamera.m_iUserID)
                    }
                }
                R.id.btn_Down -> {
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        hkCamera.startMove(2, hkCamera.m_iUserID)
                    }
                    if (event.action == MotionEvent.ACTION_UP) {
                        hkCamera.stopMove(2, hkCamera.m_iUserID)
                    }
                }
                R.id.btn_ZoomIn -> {
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        hkCamera.startZoom(1, hkCamera.m_iUserID)
                    }
                    if (event.action == MotionEvent.ACTION_UP) {
                        hkCamera.stopZoom(1, hkCamera.m_iUserID)
                    }
                }
                R.id.btn_ZoomOut -> {
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        hkCamera.startZoom(-1, hkCamera.m_iUserID)
                    }
                    if (event.action == MotionEvent.ACTION_UP) {
                        hkCamera.stopZoom(-1, hkCamera.m_iUserID)
                    }
                }
                else -> {}
            }
        }.start()
        v.performClick()
        false
    }

    fun getChannel(inPutStr: String?): String {
        val p = Pattern.compile("[^0-9]")
        val m = p.matcher(inPutStr)
        return m.replaceAll("").trim { it <= ' ' }
    }

    override fun onDestroy() {
        hkCamera.destroy()
        super.onDestroy()
    }
}