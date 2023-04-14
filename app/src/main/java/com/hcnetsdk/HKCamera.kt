package com.hcnetsdk

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.os.Environment
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.hcnetsdk.control.DevManageGuider
import com.hcnetsdk.control.SDKGuider
import com.hcnetsdk.jna.HCNetSDKJNAInstance
import com.hikvision.netsdk.*
import java.nio.charset.Charset

/**
 * 海康威视摄像头SDK控制
 * 支持放大缩小，旋转
 * 播放，停止，拍照，录视频
 * 基于最新（202212）海康官网SDK编写。
 */
/*
准备：
1.复制libs\hikvision文件夹到新项目
2.build.gradle中设置
    2.1 设置so位置
    sourceSets.main {
        jniLibs.srcDirs = ['libs/hikvision']
    }
    2.2 引入
    implementation fileTree(dir: 'libs/hikvision', include: ['*.aar','*.jar'])
3.添加surfaceView
 <SurfaceView
        android:id="@+id/surfaceView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
用法：
var hkCamera = HKCamera(binding.surfaceView).apply {
    devName = "余静的摄像头"
    ip = "192.168.1.70"
    port = "8000"
    username = "admin"
    password = "pw&123456"
}
//初始化，包含登录
hkCamera.init()

//开始预览（播放）
hkCamera.start()
//停止
hkCamera.stop()
//拍照，存盘
hkCamera.snap(path)
//开始录制视频
hkCamera.recordStart(path)
//停止录制视频
hkCamera.recordStop()
//拍照，获取bitmap
hkCamera.takePicture()
//叠加文字到视频
hkCamera.showString("你好，我是余静！")
 */
class HKCamera(var surfaceView: SurfaceView) {
    val TAG = "HKCamera"
    var m_iPreviewHandle = -1 // playback
    var channel = 1 //选择的通道
    var streamType = 0 //选择的流类型  0主码流 1子码流 2三码流
    var m_iUserID = -1 // return by NET_DVR_Login_v30
    var m_byChanNum = 0 // analog channel nums
    var m_byStartChan = 0 //start analog channel
    var m_IPChanNum = 0 //digital channel nums
    var m_byStartDChan = 0 //start digital channel
    var m_iSendDataHandle = -1 //串口数据handle

    var isLogin = false
    var devName = "CameraName"
    var ip = ""
    var port = "8000"
    var username = ""
    var password = ""

    //设备信息
    lateinit var deviceInfo: DevManageGuider.DeviceItem

    fun init(): Boolean {
        //创建一个配置
        deviceInfo = SDKGuider.g_sdkGuider.m_comDMGuider.DeviceItem()
        deviceInfo.m_szDevName = devName
        deviceInfo.m_struNetInfo = SDKGuider.g_sdkGuider.m_comDMGuider.DevNetInfo(ip, port, username, password)
        //添加到设备列表
        SDKGuider.g_sdkGuider.m_comDMGuider.devList = ArrayList<DevManageGuider.DeviceItem>().apply { add(deviceInfo) }

        //登录
        isLogin = if (SDKGuider.g_sdkGuider.m_comDMGuider.login_v40_jna_with_index(0)) {
            Log.i(TAG, "摄像头登录成功")
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
            //Surface
            surfaceView.holder.addCallback(callback)
            surfaceView.setZOrderOnTop(true)//将surfaceView放置在屏幕顶层
            surfaceView.setZOrderMediaOverlay(true)//设置Z顺序覆盖，设置顶层时候，如果需要在视频顶层显示其他view，就必须设置这个
            true
        } else {
            Log.i(TAG, "摄像头登陆失败")
            false
        }
        return isLogin
    }

    //SurfaceHolder callback
    private var callback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            Log.i(TAG, "surfaceCreated")
            surfaceView.holder.setFormat(PixelFormat.TRANSLUCENT)
            if (-1 == m_iPreviewHandle) return
            val surface = holder.surface
            if (surface.isValid) {
                if (-1 == SDKGuider.g_sdkGuider.m_comPreviewGuider.RealPlaySurfaceChanged_jni(m_iPreviewHandle, 0, holder))
                    Log.e(TAG, "surfaceCreated，error：" + SDKGuider.g_sdkGuider.GetLastError_jni())
            }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            Log.i(TAG, "surfaceChanged")
            //surfaceView.setZOrderOnTop(true);//将surfaceView放置在屏幕顶层
            //surfaceView.setZOrderMediaOverlay(true)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            Log.i(TAG, "surfaceDestroyed")
            if (-1 == m_iPreviewHandle) return
            if (holder.surface.isValid) {
                if (-1 == SDKGuider.g_sdkGuider.m_comPreviewGuider.RealPlaySurfaceChanged_jni(m_iPreviewHandle, 0, null)) {
                    Log.e(TAG, "surfaceDestroyed，error：" + SDKGuider.g_sdkGuider.GetLastError_jni())
                }
            }
        }
    }

    /**
     * 打开摄像头预览
     */
    fun start(): Boolean {
        if (m_iPreviewHandle != -1) SDKGuider.g_sdkGuider.m_comPreviewGuider.RealPlay_Stop_jni(m_iPreviewHandle)
        val ndp = NET_DVR_PREVIEWINFO()
        ndp.lChannel = channel
        ndp.dwStreamType = streamType
        ndp.bBlocked = 1
        ndp.hHwnd = surfaceView.holder
        m_iPreviewHandle = SDKGuider.g_sdkGuider.m_comPreviewGuider.RealPlay_V40_jni(m_iUserID, ndp, null)
        if (m_iPreviewHandle < 0) {
            Log.i(TAG, "开始播放失败" + SDKGuider.g_sdkGuider.GetLastError_jni())
            return false
        }
        Log.i(TAG, "开始播放成功 ")
        return true
    }

    /**
     * 关闭预览
     */
    fun stop(): Boolean {
        if (!SDKGuider.g_sdkGuider.m_comPreviewGuider.RealPlay_Stop_jni(m_iPreviewHandle)) return Log.i(TAG, "关闭预览失败：" + m_iPreviewHandle + "  error:" + SDKGuider.g_sdkGuider.GetLastError_jni()).let { false }
        m_iPreviewHandle = -1
        Log.i(TAG, "停止播放成功")
        return true
    }

    /**
     * 拍照
     */
    fun snap(path: String = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).path + "/picture_${System.currentTimeMillis()}.bmp"): Boolean {
        if (m_iPreviewHandle < 0) return Log.e(TAG, "请先开始播放").let { false }
        if (!SDKGuider.g_sdkGuider.m_comPreviewGuider.RealPlay_Snap(m_iPreviewHandle, path)) return Log.i(TAG, "拍照失败:" + SDKGuider.g_sdkGuider.GetLastError_jni()).let { false }
        Log.i(TAG, "拍照成功，路径：${path}")
        return true
    }

    /**
     * 开始录像
     */
    fun recordStart(path: String = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).path + "/video_${System.currentTimeMillis()}.mp4"): Boolean {
        Log.d("path", path)
        if (m_iPreviewHandle < 0) return Log.e(TAG, "请先开始播放").let { false }
        if (!SDKGuider.g_sdkGuider.m_comPreviewGuider.RealPlay_Record(m_iPreviewHandle, 1, path)) return Log.i(TAG, "录像失败:" + SDKGuider.g_sdkGuider.GetLastError_jni()).let { false }
        Log.i(TAG, "录像开始，路径：${path}")
        return true
    }

    /**
     * 停止录像
     */
    fun recordStop(): Boolean {
        if (!HCNetSDKJNAInstance.getInstance().NET_DVR_StopSaveRealData(m_iPreviewHandle)) return Log.i(TAG, "停止录像失败:" + SDKGuider.g_sdkGuider.GetLastError_jni()).let { false }
        return true
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
    fun showString(string: String, x: Int = 20, y: Int = 50, size: Int = 40): Boolean {
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
        if (success) Log.i(TAG, "叠加文字成功")
        return success
    }


    //开始移动
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
            HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1, PTZCommand.FOCUS_NEAR, 0)
        } else {
            HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1, PTZCommand.FOCUS_FAR, 0)
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

    /**
     * 打开串口
     * @param serialPortType 1为232 2为485
     * @param callBack 回调函数
     */
    fun openSerialTrans(serialPortType: Int = 1/*1为232 2为485*/, callBack: SerialDataCallBack): Boolean {
        return if (m_iSendDataHandle == -1) {
            //String strHexData = str2HexStr( byteArrayToStr(byRecvBuf));
            m_iSendDataHandle = SDKGuider.g_sdkGuider.m_comSerialTransGuider.NET_DVR_SerialStart_jni(m_iUserID, serialPortType, callBack)
            if (m_iSendDataHandle == -1) {
                Log.e(TAG, "打开串行通道失败" + HCNetSDK.getInstance().NET_DVR_GetLastError())
                return false
            }
            true
        } else {
            Log.i(TAG, "您已打开串行通道")
            true
        }
    }

    /**
     * 关闭串口
     */
    fun closeSerialTrans(): Boolean {
        return if (m_iSendDataHandle != -1) {
            if (!SDKGuider.g_sdkGuider.m_comSerialTransGuider.NET_DVR_SerialStop_jni(m_iSendDataHandle)) {
                Log.e(TAG, "关闭串行通道失败" + HCNetSDK.getInstance().NET_DVR_GetLastError())
                return false
            }
            m_iSendDataHandle = -1
            true
        } else {
            Log.i(TAG, "请先打开打开串行通道")
            false
        }
    }

    /**
     * 向串口发送数据
     * @param serialChannel 串口通道 获取一共多少个通达 int iChannelNum = (int) m_deviceInfo.m_struDeviceInfoV40_jna.struDeviceV30.byChanNum; 重新1开始数，比如iChannelNum=2，那么就是1,2
     * @param byteArray 发送的数据
     */
    fun sendSerialPort(byteArray: ByteArray, serialChannel: Int = 1): Boolean {
        if (!SDKGuider.g_sdkGuider.m_comSerialTransGuider.NET_DVR_SerialSend_jni(m_iSendDataHandle, serialChannel, byteArray, byteArray.size)) {
            Log.e(TAG, "发送数据失败：" + HCNetSDK.getInstance().NET_DVR_GetLastError())
            return false
        }
        Log.d(TAG, "发送数据成功")
        return true
    }

    /**
     * 向串口发送数据
     * @param serialPortType 串口类型 1为232 2为485
     * @param serialChannel 串口通道 获取一共多少个通达： var iChannelNum = deviceInfo.m_struDeviceInfoV40_jna.struDeviceV30.byChanNum 重新1开始数，比如iChannelNum=2，那么就是1,2
     * @param byteArray 发送的数据
     */
    fun sendSerialPort(byteArray: ByteArray, serialChannel: Int = 1, serialPortType: Int = 1/*1为232 2为485*/): Boolean {
        if (!SDKGuider.g_sdkGuider.m_comSerialTransGuider.NET_DVR_SendToSerialPort_jni(m_iUserID, serialPortType, serialChannel, byteArray, byteArray.size)) {
            Log.e(TAG, "发送数据失败：" + HCNetSDK.getInstance().NET_DVR_GetLastError())
            return false
        }
        Log.d(TAG, "发送数据成功")
        return true
    }

    /**
     * 向串口发送数据
     * @param byteArray 发送的数据
     */
    fun sendSerialPort232(byteArray: ByteArray): Boolean {
        if (!SDKGuider.g_sdkGuider.m_comSerialTransGuider.NET_DVR_SendTo232Port_jni(m_iUserID, byteArray, byteArray.size)) {
            Log.e(TAG, "发送数据失败：" + HCNetSDK.getInstance().NET_DVR_GetLastError())
            return false
        }
        Log.d(TAG, "发送数据成功")
        return true
    }
    fun destroy() {
        //停止播放
        if (m_iPreviewHandle != -1) {
            SDKGuider.g_sdkGuider.m_comPreviewGuider.RealPlay_Stop_jni(m_iPreviewHandle)
            m_iPreviewHandle = -1
        }
        //退出登录
        if (SDKGuider.g_sdkGuider.m_comDMGuider.logout_jni(0)) {
            Log.i(TAG, "退出登录成功")
        } else {
            Log.e(TAG, "退出登录失败：" + SDKGuider.g_sdkGuider.GetLastError_jni())
        }
        m_iPreviewHandle = -1 // playback
        channel = 1 //选择的通道
        streamType = 0 //选择的流类型
        m_iUserID = -1 // return by NET_DVR_Login_v30
        m_byChanNum = 0 // analog channel nums
        m_byStartChan = 0 //start analog channel
        m_IPChanNum = 0 //digital channel nums
        m_byStartDChan = 0 //start digital channel
        m_iSendDataHandle = -1 //串口数据handle
    }
}