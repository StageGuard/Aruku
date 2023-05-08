package me.stageguard.aruku.service.bridge

import me.stageguard.aruku.service.parcel.AccountInfo
import me.stageguard.aruku.service.parcel.ContactInfo
import me.stageguard.aruku.service.parcel.ContactSyncOp
import remoter.annotations.ParamOut
import remoter.annotations.Remoter

@Remoter
interface ContactSyncBridge {
    fun onSyncContact(@ParamOut op: ContactSyncOp, account: Long, @ParamOut contacts: List<ContactInfo>)

    fun onUpdateAccountInfo(@ParamOut info: AccountInfo)
}

