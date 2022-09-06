@file:Suppress("ObjectPropertyName")

package me.stageguard.aruku.preference

import android.content.Context
import androidx.datastore.dataStore
import me.stageguard.aruku.preference.proto.AccountsOuterClass
import me.stageguard.aruku.preference.serializer.AccountsProtoSerializer

private val _accountStore = dataStore("accounts.proto", AccountsProtoSerializer)
private val _accountProtoDefault = lazy { AccountsOuterClass.Accounts.getDefaultInstance() }
val Context.accountStore
    get() = dataStoreRepositoryDelegate(_accountStore, _accountProtoDefault)