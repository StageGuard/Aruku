package me.stageguard.aruku.common.service.bridge

import me.stageguard.aruku.common.service.parcel.AccountInfo
import me.stageguard.aruku.common.service.parcel.ContactInfo
import me.stageguard.aruku.common.service.parcel.ContactSyncOp
import remoter.annotations.ParamOut
import remoter.annotations.Remoter

@Remoter
interface ContactSyncBridge {
    fun onSyncContact(@ParamOut op: ContactSyncOp, account: Long, @ParamOut contacts: List<ContactInfo>)

    fun onUpdateAccountInfo(@ParamOut info: AccountInfo)
}

