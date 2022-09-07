package me.stageguard.aruku.util

import androidx.lifecycle.MutableLiveData

fun <T> MutableLiveData<T>.notify() {
    this.value = value
}

fun <T> MutableLiveData<T>.notifyPost() {
    this.postValue(this.value)
}