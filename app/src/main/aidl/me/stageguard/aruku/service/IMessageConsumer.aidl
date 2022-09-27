// IMessageConsumer.aidl
package me.stageguard.aruku.service;

import me.stageguard.aruku.service.parcel.ArukuMessageEvent;

// Declare any non-default types here with import statements

interface IMessageConsumer {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
    void consume(inout ArukuMessageEvent event);
}