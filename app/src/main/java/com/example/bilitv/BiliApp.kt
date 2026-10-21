package com.example.bilitv

import android.app.Application
import com.example.bilitv.data.Session

class BiliApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Session.init(this)
    }
}
