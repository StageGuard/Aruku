package me.stageguard.aruku.database.contact

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.stageguard.aruku.database.BaseDao
import me.stageguard.aruku.common.service.parcel.ContactType

// TODO: add custom upsert method for group and friend
@Dao
abstract class ContactDao : BaseDao<ContactEntity> {
    @Query("select * from `contact` where account_id=:account")
    abstract fun getContacts(account: Long): List<ContactEntity>

    @Query("select * from `contact` where account_id=:account and type=:type")
    abstract fun getContacts(account: Long, type: me.stageguard.aruku.common.service.parcel.ContactType): List<ContactEntity>

    @Query("select * from `contact` where account_id=:account")
    abstract fun getContactsPaging(account: Long): PagingSource<Int, ContactEntity>

    @Query("select * from `contact` where account_id=:account and type=:type")
    abstract fun getContactsPaging(account: Long, type: me.stageguard.aruku.common.service.parcel.ContactType): PagingSource<Int, ContactEntity>

    @Query("select * from `contact` where account_id=:account")
    abstract fun getContactsFlow(account: Long): Flow<List<ContactEntity>>

    @Query("select * from `contact` where account_id=:account and type=:type")
    abstract fun getContactsFlow(account: Long, type: me.stageguard.aruku.common.service.parcel.ContactType): Flow<List<ContactEntity>>

    @Query("select * from `contact` where account_id=:account and subject=:subject and type=:type")
    abstract fun getContact(account: Long, subject: Long, type: me.stageguard.aruku.common.service.parcel.ContactType): List<ContactEntity>
}