package com.kotlinx.hk

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.yujing.utils.YUtils

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        YUtils.init(this)
        initValue()
    }

    //设置系统初始值
    open fun initValue() {
        //设置初始音量值0-15
        //AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        //am.setStreamVolume(AudioManager.STREAM_MUSIC, 7, AudioManager.FLAG_PLAY_SOUND);
    }
}
