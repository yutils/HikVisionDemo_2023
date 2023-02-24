package com.hcnetsdk.control;

import android.util.Log;

import com.hcnetsdk.jna.HCNetSDKByJNA;
import com.hcnetsdk.jna.HCNetSDKJNAInstance;
import com.hikvision.netsdk.HCNetSDK;
import com.hikvision.netsdk.NET_DVR_XML_CONFIG_INPUT;
import com.hikvision.netsdk.NET_DVR_XML_CONFIG_OUTPUT;

public class DevTransportGuider {

    public boolean STDXMLConfig_jni(int lUserID, NET_DVR_XML_CONFIG_INPUT lpInputParam, NET_DVR_XML_CONFIG_OUTPUT lpOutputParam) {
        if (lUserID < 0) {
            Log.e("SimpleDemo", "STDXMLConfig_jni failed with error param");
            return false;
        }
        return HCNetSDK.getInstance().NET_DVR_STDXMLConfig(lUserID, lpInputParam, lpOutputParam);
    }

    public boolean STDXMLConfig_jna(int lUserID, HCNetSDKByJNA.NET_DVR_XML_CONFIG_INPUT lpInputParam, HCNetSDKByJNA.NET_DVR_XML_CONFIG_OUTPUT lpOutputParam) {
        if (lUserID < 0) {
            Log.e("SimpleDemo", "STDXMLConfig_jna failed with error param");
            return false;
        }
        return HCNetSDKJNAInstance.getInstance().NET_DVR_STDXMLConfig(lUserID, lpInputParam, lpOutputParam);
    }
}
