@file:Suppress("ObjectPropertyName")

package me.stageguard.aruku.preference

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import me.stageguard.aruku.preference.proto.AccountsOuterClass
import me.stageguard.aruku.preference.serializer.AccountsProtoSerializer
import me.stageguard.aruku.util.safeFlow

val Context.accountStore by dataStore("accounts.proto", AccountsProtoSerializer)
val DataStore<AccountsOuterClass.Accounts>.safeFlow
    get() = safeFlow(AccountsOuterClass.Accounts.getDefaultInstance())