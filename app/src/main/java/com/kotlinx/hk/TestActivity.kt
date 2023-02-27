package com.kotlinx.hk

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.hcnetsdk.control.DevManageGuider.DeviceItem
import com.hcnetsdk.control.SDKGuider
import com.hikvision.netsdk.*
import com.kotlinx.hk.databinding.ActivityMainBinding
import com.yujing.utils.*
import java.nio.charset.Charset
import java.util.regex.Pattern

/**
 * 海康威视202212月demo核心播放功能，剥离。
 * 原始用法，在官方demo中命名：FragPreviewBySurfaceView
 */
@kotlin.Deprecated("原始用法，在官方demo中命名：FragPreviewBySurfaceView")
class TestActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding
    private var m_osurfaceView: SurfaceView? = null
    private var m_iPreviewHandle = -1 // playback
    private var m_iSelectChannel = -1
    private var m_iSelectStreamType = -1
    private var m_iUserID = -1 // return by NET_DVR_Login_v30
    private var m_byChanNum = 0 // analog channel nums
    private var m_byStartChan = 0 //start analog channel
    private var m_IPChanNum = 0 //digital channel nums
    private var m_byStartDChan = 0 //start digital channel
    private var m_data_list_channel: MutableList<String> = ArrayList()
    private var m_data_list_stream: MutableList<String> = ArrayList()
    private var m_streamtype_adapter: ArrayAdapter<String>? = null
    private var m_arrchannel_adapter: ArrayAdapter<String>? = null

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

        //创建一个配置
        val deviceInfo = SDKGuider.g_sdkGuider.m_comDMGuider.DeviceItem()
        val devName = "余静的摄像头"
        val ip = "192.168.1.67"
        val port = "8000"
        val username = "admin"
        val password = "pw&123456"
        deviceInfo.m_szDevName = devName
        deviceInfo.m_struNetInfo = SDKGuider.g_sdkGuider.m_comDMGuider.DevNetInfo(ip, port, username, password)
        //添加到设备列表
        SDKGuider.g_sdkGuider.m_comDMGuider.devList = ArrayList<DeviceItem>().apply { add(deviceInfo) }
        //登录
        if (SDKGuider.g_sdkGuider.m_comDMGuider.login_v40_jna_with_index(0)) {
            Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "登陆失败：" + SDKGuider.g_sdkGuider.GetLastError_jni(), Toast.LENGTH_SHORT).show()
            return
        }

        m_iUserID = deviceInfo.m_lUserID
        m_byChanNum = deviceInfo.m_struDeviceInfoV40_jna.struDeviceV30.byChanNum.toInt()
        m_byStartChan = deviceInfo.m_struDeviceInfoV40_jna.struDeviceV30.byStartChan.toInt()
        m_IPChanNum = deviceInfo.m_struDeviceInfoV40_jna.struDeviceV30.byIPChanNum + deviceInfo.m_struDeviceInfoV40_jna.struDeviceV30.byHighDChanNum * 256
        m_byStartDChan = deviceInfo.m_struDeviceInfoV40_jna.struDeviceV30.byStartChan.toInt()
        println("下面是设备信息************************")
        println("userId=$m_iUserID")
        println("通道个数=$m_byChanNum")
        println("通道开始=$m_byStartChan")
        println("ip通道个数=$m_IPChanNum")
        println("启动通道=$m_byStartDChan")
        var iAnalogStartChan = m_byStartChan
        var iDigitalStartChan = m_byStartDChan
        m_data_list_channel = ArrayList()
        for (indexChanNum in 0 until m_byChanNum) {
            m_data_list_channel.add("ACamera_$iAnalogStartChan")
            iAnalogStartChan++
        }
        for (indexChanNum in 0 until m_IPChanNum) {
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
                m_iSelectStreamType = i
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {}
        }
        binding.bychanSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            //通过此方法为下拉列表设置点击事件
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View, i: Int, l: Long) {
                val text = binding.bychanSpinner.getItemAtPosition(i).toString()
                m_iSelectChannel = Integer.valueOf(getChannel(text)).toInt()
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {}
        }
        //Surface
        m_osurfaceView = binding.SurfacePreviewPlay
        m_osurfaceView!!.holder.addCallback(callback)
        //m_osurfaceView!!.setZOrderOnTop(true)//将surfaceView放置在屏幕顶层
    }

    //SurfaceHolder callback
    var callback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            m_osurfaceView!!.holder.setFormat(PixelFormat.TRANSLUCENT)
            if (-1 == m_iPreviewHandle) return
            val surface = holder.surface
            if (surface.isValid) {
                if (-1 == SDKGuider.g_sdkGuider.m_comPreviewGuider.RealPlaySurfaceChanged_jni(m_iPreviewHandle, 0, holder)) Toast.makeText(this@TestActivity, "NET_DVR_PlayBackSurfaceChanged" + SDKGuider.g_sdkGuider.GetLastError_jni(), Toast.LENGTH_SHORT).show()
            }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            //m_osurfaceView!!.setZOrderOnTop(true);//将surfaceView放置在屏幕顶层
            Toast.makeText(this@TestActivity, "surfaceChanged", Toast.LENGTH_SHORT).show();
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            if (-1 == m_iPreviewHandle) return
            if (holder.surface.isValid) {
                if (-1 == SDKGuider.g_sdkGuider.m_comPreviewGuider.RealPlaySurfaceChanged_jni(m_iPreviewHandle, 0, null)) {
                    Toast.makeText(this@TestActivity, "NET_DVR_RealPlaySurfaceChanged" + SDKGuider.g_sdkGuider.GetLastError_jni(), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun getChannel(inPutStr: String?): String {
        val p = Pattern.compile("[^0-9]")
        val m = p.matcher(inPutStr)
        return m.replaceAll("").trim { it <= ' ' }
    }


    fun onClick(v: View) {
        when (v.id) {
            R.id.button_preview_start -> {
                if (m_iPreviewHandle != -1) SDKGuider.g_sdkGuider.m_comPreviewGuider.RealPlay_Stop_jni(m_iPreviewHandle)
                val struPlayInfo = NET_DVR_PREVIEWINFO()
                struPlayInfo.lChannel = m_iSelectChannel
                struPlayInfo.dwStreamType = m_iSelectStreamType
                struPlayInfo.bBlocked = 1
                m_osurfaceView = binding.SurfacePreviewPlay
                struPlayInfo.hHwnd = m_osurfaceView!!.holder
                m_iPreviewHandle = SDKGuider.g_sdkGuider.m_comPreviewGuider.RealPlay_V40_jni(m_iUserID, struPlayInfo, null)
                if (m_iPreviewHandle < 0) return Toast.makeText(this, "开始播放失败" + SDKGuider.g_sdkGuider.GetLastError_jni(), Toast.LENGTH_SHORT).show()
                Toast.makeText(this, "开始播放成功 ", Toast.LENGTH_SHORT).show()
            }
            R.id.button_preview_stop -> {
                if (!SDKGuider.g_sdkGuider.m_comPreviewGuider.RealPlay_Stop_jni(m_iPreviewHandle)) return Toast.makeText(this, "NET_DVR_StopRealPlay m_iPreviewHandle：" + m_iPreviewHandle + "  error:" + SDKGuider.g_sdkGuider.GetLastError_jni(), Toast.LENGTH_SHORT).show()
                m_iPreviewHandle = -1
                Toast.makeText(this, "停止播放成功", Toast.LENGTH_SHORT).show()
            }
            R.id.button_preview_snap -> {
                if (m_iPreviewHandle < 0) return Toast.makeText(this, "请先开始播放", Toast.LENGTH_SHORT).show()
                val path = "/mnt/sdcard/图片${System.currentTimeMillis()}.bmp"
                if (!SDKGuider.g_sdkGuider.m_comPreviewGuider.RealPlay_Snap(m_iPreviewHandle, path)) return Toast.makeText(this, "拍照失败:" + SDKGuider.g_sdkGuider.GetLastError_jni(), Toast.LENGTH_SHORT).show()
                Toast.makeText(this, "拍照成功，路径：${path}", Toast.LENGTH_SHORT).show()
            }
            R.id.button_preview_record -> {
                if (m_iPreviewHandle < 0) return Toast.makeText(this, "请先开始播放", Toast.LENGTH_SHORT).show()
                val path = "/mnt/sdcard/视频${System.currentTimeMillis()}.mp4"
                if (!SDKGuider.g_sdkGuider.m_comPreviewGuider.RealPlay_Record(m_iPreviewHandle, 1, path)) return Toast.makeText(this, "录像失败:" + SDKGuider.g_sdkGuider.GetLastError_jni(), Toast.LENGTH_SHORT).show()
                Toast.makeText(this, "录像开始，路径：${path}", Toast.LENGTH_SHORT).show()
            }
            R.id.button_picture -> {
                val time = System.currentTimeMillis()
                val bitmap: Bitmap? = takePicture()
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
                showString("你好，我是余静！")
            }
            R.id.button_clear_text -> {
                showString("")
            }
        }
    }

    private val touchListener = View.OnTouchListener { v, event ->
        Log.d("log", "touch:${v.id} ${event!!.action}")
        Thread {
            when (v!!.id) {
                R.id.btn_Up -> {
                    if (event!!.action == MotionEvent.ACTION_DOWN) {
                        startMove(8, m_iUserID)
                    }
                    if (event.action == MotionEvent.ACTION_UP) {
                        stopMove(8, m_iUserID)
                    }
                }
                R.id.btn_Left -> {
                    if (event!!.action == MotionEvent.ACTION_DOWN) {
                        startMove(4, m_iUserID)
                    }
                    if (event.action == MotionEvent.ACTION_UP) {
                        stopMove(4, m_iUserID)
                    }
                }
                R.id.btn_Right -> {
                    if (event!!.action == MotionEvent.ACTION_DOWN) {
                        startMove(6, m_iUserID)
                    }
                    if (event.action == MotionEvent.ACTION_UP) {
                        stopMove(6, m_iUserID)
                    }
                }
                R.id.btn_Down -> {
                    if (event!!.action == MotionEvent.ACTION_DOWN) {
                        startMove(2, m_iUserID)
                    }
                    if (event.action == MotionEvent.ACTION_UP) {
                        stopMove(2, m_iUserID)
                    }
                }
                R.id.btn_ZoomIn -> {
                    if (event!!.action == MotionEvent.ACTION_DOWN) {
                        startZoom(1, m_iUserID)
                    }
                    if (event.action == MotionEvent.ACTION_UP) {
                        stopZoom(1, m_iUserID)
                    }
                }
                R.id.btn_ZoomOut -> {
                    if (event!!.action == MotionEvent.ACTION_DOWN) {
                        startZoom(-1, m_iUserID)
                    }
                    if (event.action == MotionEvent.ACTION_UP) {
                        stopZoom(-1, m_iUserID)
                    }
                }
                else -> {}
            }
        }.start()
        false
    }

    //拍照
    fun takePicture(): Bitmap? {
        if (m_iUserID >= 0) {
            val jpegPara = NET_DVR_JPEGPARA().apply {
                wPicSize = 9
                wPicQuality = 0
            }
            //接收照片的数组
            val bytes = ByteArray(1024 * 1024 * 20)
            //返回的数据长度
            val ip = INT_PTR()
            val success = HCNetSDK.getInstance().NET_DVR_CaptureJPEGPicture_NEW(m_iUserID, m_byStartChan, jpegPara, bytes, bytes.size, ip)
            if (success) {
                val newBytes = bytes.copyOf(ip.iValue)
                return BitmapFactory.decodeByteArray(newBytes, 0, newBytes.size)
            }
        }
        return null
    }

    //字符叠加参数
    fun showString(string: String, x: Int = 20, y: Int = 50, size: Int = 40) {
        /*
        函数： public boolean NET_DVR_GetDVRConfig(int lUserID, int dwCommand, int lChannel, NET_DVR_CONFIGDVRConfig)
        参数：   [in] lUserID          NET_DVR_Login_V30的返回值
                [in] dwCommand          设备配置命令，详见 表 3 10
                [in] lChannel           通道号
                [out] DVRConfig         配置信息，不同的配置功能对应不同的 子
        */
        //        //获取叠加字符
        //        NET_DVR_SHOWSTRING_V30 showString = new NET_DVR_SHOWSTRING_V30();
        //        boolean success = HCNetSDK.getInstance().NET_DVR_GetDVRConfig(m_iLogID, HCNetSDK.NET_DVR_GET_SHOWSTRING_V30, device.getChannel(), showString);
        //        if (success) {
        //            YLog.i("获取到字符叠加参数");
        //            for (NET_DVR_SHOWSTRINGINFO item : showString.struStringInfo) {
        //                YLog.i("旧的字符" + (new String(item.sString, Charset.forName("GB18030"))));
        //            }
        //        }
        val showString = NET_DVR_SHOWSTRING_V30()
        showString.struStringInfo[0].wShowString = 1 //1显示0不显示
        showString.struStringInfo[0].wStringSize = size
        showString.struStringInfo[0].wShowStringTopLeftX = x //x位置
        showString.struStringInfo[0].wShowStringTopLeftY = y //y位置
        showString.struStringInfo[0].sString = string.toByteArray(Charset.forName("GB18030")).copyOf(44)
        val success = HCNetSDK.getInstance().NET_DVR_SetDVRConfig(m_iUserID, HCNetSDK.NET_DVR_SET_SHOWSTRING_V30, m_byStartChan, showString)
        if (success) YLog.i("叠加成功")
    }


    fun startMove(orientation: Int, m_iLogID: Int) {
        if (m_iLogID < 0) return
        when (orientation) {
            9 -> HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1, PTZCommand.UP_RIGHT, 0)
            8 -> HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1, PTZCommand.TILT_UP, 0)
            7 -> HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1, PTZCommand.UP_LEFT, 0)
            6 -> HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1, PTZCommand.PAN_RIGHT, 0)
            5 -> HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1, PTZCommand.PAN_AUTO, 0)
            4 -> HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1, PTZCommand.PAN_LEFT, 0)
            3 -> HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1, PTZCommand.DOWN_RIGHT, 0)
            2 -> HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1, PTZCommand.TILT_DOWN, 0)
            1 -> HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1, PTZCommand.DOWN_LEFT, 0)
            else -> {}
        }
    }

    /**
     * 停止移动 NET_DVR_PTZControl_Other参数：(播放标记, 通道， 指令码, 开始标记0或停止标记1)
     *
     * @param orientation 九宫格数字方向
     */
    fun stopMove(orientation: Int, m_iLogID: Int) {
        if (m_iLogID < 0) return
        when (orientation) {
            9 -> HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1, PTZCommand.UP_RIGHT, 1)
            8 -> HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1, PTZCommand.TILT_UP, 1)
            7 -> HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1, PTZCommand.UP_LEFT, 1)
            6 -> HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1, PTZCommand.PAN_RIGHT, 1)
            5 -> HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1, PTZCommand.PAN_AUTO, 1)
            4 -> HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1, PTZCommand.PAN_LEFT, 1)
            3 -> HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1, PTZCommand.DOWN_RIGHT, 1)
            2 -> HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1, PTZCommand.TILT_DOWN, 1)
            1 -> HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1, PTZCommand.DOWN_LEFT, 1)
            else -> {}
        }
    }

    /**
     * 开始缩放 NET_DVR_PTZControl_Other参数：(播放标记, 通道， 指令码, 开始标记0或停止标记1)
     *
     * @param x -1缩小 1放大
     */
    fun startZoom(x: Int, m_iLogID: Int) {
        if (m_iLogID < 0) return
        if (x < 0) {
            HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1, PTZCommand.ZOOM_OUT, 0)
        } else {
            HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1, PTZCommand.ZOOM_IN, 0)
        }
    }

    /**
     * 停止缩放 NET_DVR_PTZControl_Other参数：(播放标记, 通道， 指令码, 开始标记0或停止标记1)
     *
     * @param x -1缩小 1放大
     */
    fun stopZoom(x: Int, m_iLogID: Int) {
        if (m_iLogID < 0) return
        if (x < 0) {
            HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1, PTZCommand.ZOOM_OUT, 1)
        } else {
            HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1, PTZCommand.ZOOM_IN, 1)
        }
    }

    /**
     * 开始变焦 NET_DVR_PTZControl_Other参数：(播放标记, 通道， 指令码, 开始标记0或停止标记1)
     *
     * @param x -1近端 1远端
     */
    fun startFocus(x: Int, m_iLogID: Int) {
        if (m_iLogID < 0) return
        if (x < 0) {
            HCNetSDK.getInstance().NET_DVR_PTZControl_Other(
                m_iLogID, 1,
                PTZCommand.FOCUS_NEAR, 0
            )
        } else {
            HCNetSDK.getInstance().NET_DVR_PTZControl_Other(
                m_iLogID, 1,
                PTZCommand.FOCUS_FAR, 0
            )
        }
    }

    /**
     * 停止变焦 NET_DVR_PTZControl_Other参数：(播放标记, 通道， 指令码, 开始标记0或停止标记1)
     *
     * @param x -1近端 1远端
     */
    fun stopFocus(x: Int, m_iLogID: Int) {
        if (m_iLogID < 0) return
        if (x < 0) {
            HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1, PTZCommand.FOCUS_NEAR, 1)
        } else {
            HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1, PTZCommand.FOCUS_FAR, 1)
        }
    }

    override fun onDestroy() {
        //停止播放
        if (m_iPreviewHandle != -1) {
            SDKGuider.g_sdkGuider.m_comPreviewGuider.RealPlay_Stop_jni(m_iPreviewHandle)
            m_iPreviewHandle = -1
        }
        //退出登录
        if (SDKGuider.g_sdkGuider.m_comDMGuider.logout_jni(0)) {
            Toast.makeText(this, "退出登录成功", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "退出登录失败：" + SDKGuider.g_sdkGuider.GetLastError_jni(), Toast.LENGTH_SHORT).show();
        }
        super.onDestroy()
    }
}