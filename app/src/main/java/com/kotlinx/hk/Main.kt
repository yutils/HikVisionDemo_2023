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
import com.kotlinx.hk.databinding.ActivityFragPreviewSurfaceviewBinding
import com.yujing.utils.*
import java.util.regex.Pattern

/**
 * 海康威视202212月demo核心播放功能，剥离
 */
class Main : AppCompatActivity() {
    lateinit var binding: ActivityFragPreviewSurfaceviewBinding
    lateinit var hkCamera: HKCamera

    private var m_data_list_channel: MutableList<String> = ArrayList()
    private var m_data_list_stream: MutableList<String> = ArrayList()
    private var m_streamtype_adapter: ArrayAdapter<String>? = null
    private var m_arrchannel_adapter: ArrayAdapter<String>? = null
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        YPermissions.requestAll(this)//获取权限
        binding = DataBindingUtil.setContentView(this, R.layout.activity_frag_preview_surfaceview)

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
        m_data_list_channel = ArrayList()
        for (indexChanNum in 0 until hkCamera.m_byChanNum) {
            m_data_list_channel.add("ACamera_$iAnalogStartChan")
            iAnalogStartChan++
        }
        for (indexChanNum in 0 until hkCamera.m_IPChanNum) {
            m_data_list_channel.add("DCamera_$iDigitalStartChan")
            iDigitalStartChan++
        }
        m_arrchannel_adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, m_data_list_channel)
        m_arrchannel_adapter!!.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.bychanSpinner.adapter = m_arrchannel_adapter
        //stream spinner
        //stream Type
        m_data_list_stream = ArrayList()
        m_data_list_stream.add("主码流")
        m_data_list_stream.add("子码流")
        m_data_list_stream.add("三码流")
        m_streamtype_adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, m_data_list_stream)
        m_streamtype_adapter!!.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.streamSpinnerSurface.adapter = m_streamtype_adapter
        binding.streamSpinnerSurface.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            //通过此方法为下拉列表设置点击事件
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View, i: Int, l: Long) {
                hkCamera.m_iSelectStreamType = i
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {}
        }
        binding.bychanSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            //通过此方法为下拉列表设置点击事件
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View, i: Int, l: Long) {
                val text = binding.bychanSpinner.getItemAtPosition(i).toString()
                hkCamera.m_iSelectChannel = Integer.valueOf(getChannel(text)).toInt()
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {}
        }
    }

    fun onClick(v: View) {
        when (v.id) {
            R.id.button_preview_start -> {
                if (hkCamera.start()) {
                    Toast.makeText(this, "开始播放成功 ", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "开始播放失败 ", Toast.LENGTH_SHORT).show()
                }
            }
            R.id.button_preview_stop -> {
                if (hkCamera.stop()) {
                    Toast.makeText(this, "停止播放成功 ", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "停止播放失败 ", Toast.LENGTH_SHORT).show()
                }
            }
            R.id.button_preview_snap -> {
                val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).path + "/picture_${System.currentTimeMillis()}.bmp"
                if (hkCamera.snap(path)) {
                    Toast.makeText(this, "拍照成功：${path}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "拍照失败", Toast.LENGTH_SHORT).show()
                }
            }
            R.id.button_preview_record -> {
                val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).path + "/video_${System.currentTimeMillis()}.mp4"
                if (hkCamera.recordStart(path)) {
                    Toast.makeText(this, "开始录像：${path}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "录像失败", Toast.LENGTH_SHORT).show()
                }
            }
            R.id.button_preview_record_stop -> {
                if (hkCamera.recordStop()) {
                    Toast.makeText(this, "停止录像录像成功", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "停止录像录像失败", Toast.LENGTH_SHORT).show()
                }
            }
            R.id.button_picture -> {
                val time = System.currentTimeMillis()
                val bitmap: Bitmap? = hkCamera.takePicture()
                //bitmap = Utils.addTextToBitmap(bitmap, "名称：张三");
                if (bitmap == null) {
                    TTS.speak("拍照失败")
                    YToast.show("拍照失败")
                    return
                }
                YImageDialog.show(bitmap)
                YToast.show(
                    """
                    分辨率:${bitmap.width}*${bitmap.height}
                    耗时：${System.currentTimeMillis() - time}
                    """.trimIndent()
                )
            }
            R.id.button_show_text -> {
                hkCamera.showString("你好，我是余静！")
            }
            R.id.button_clear_text -> {
                hkCamera.showString("")
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