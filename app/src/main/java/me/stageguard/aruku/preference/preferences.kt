@file:Suppress("ObjectPropertyName")

package me.stageguard.aruku.preference

import android.content.Context
import androidx.datastore.dataStore
import me.stageguard.aruku.preference.serializer.AccountsProtoSerializer

val Context.accountStore by dataStore("accounts.proto", AccountsProtoSerializer)