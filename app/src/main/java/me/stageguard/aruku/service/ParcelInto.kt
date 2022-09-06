package me.stageguard.aruku.service

import android.os.Parcelable

interface ParcelInto<T> {
    fun into(): T
}