package com.example.graduation_project

import android.app.Application
import com.example.graduation_project.data.api.ApiClient
import com.example.graduation_project.data.local.TokenStorage

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiClient.init(TokenStorage(this))
    }
}
