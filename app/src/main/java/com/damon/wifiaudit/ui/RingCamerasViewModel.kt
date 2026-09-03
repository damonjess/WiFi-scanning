package com.damon.wifiaudit.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.damon.wifiaudit.data.AppDatabase
import com.damon.wifiaudit.data.RingCamera
import kotlinx.coroutines.launch

class RingCamerasViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val ringCameraDao = db.ringCameraDao()

    val ringCameras = ringCameraDao.getAllRingCameras()

    fun addRingCamera(ringCamera: RingCamera) {
        viewModelScope.launch {
            ringCameraDao.insert(ringCamera)
        }
    }

    fun deleteCamera(ringCamera: RingCamera) {
        viewModelScope.launch {
            ringCameraDao.delete(ringCamera)
        }
    }

    fun updateCamera(ringCamera: RingCamera) {
        viewModelScope.launch {
            ringCameraDao.update(ringCamera)
        }
    }
}
