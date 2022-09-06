// IBotListObserver.aidl
package me.stageguard.aruku.service;

// Declare any non-default types here with import statements



interface IBotListObserver {
    void onChange(out long[] newList);
}