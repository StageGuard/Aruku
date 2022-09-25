package me.stageguard.aruku

import androidx.room.Room
import com.heyanle.okkv2.MMKVStore
import com.heyanle.okkv2.core.Okkv
import me.stageguard.aruku.database.ArukuDatabase
import net.mamoe.mirai.BotFactory
import org.koin.dsl.module

val botFactoryModule = module {
    single<BotFactory> { BotFactory }
    single { Room.databaseBuilder(get(), ArukuDatabase::class.java, "aruku-db").build() }
    single { Okkv.Builder().store(MMKVStore(get())).cache(true).build().init() }
}