package me.stageguard.aruku

import androidx.room.Room
import com.heyanle.okkv2.MMKVStore
import com.heyanle.okkv2.core.Okkv
import me.stageguard.aruku.database.ArukuDatabase
import me.stageguard.aruku.service.ArukuMiraiService
import me.stageguard.aruku.ui.activity.unitProp
import me.stageguard.aruku.ui.page.home.HomeViewModel
import me.stageguard.aruku.ui.page.login.LoginViewModel
import net.mamoe.mirai.BotFactory
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val applicationModule = module {
    single<BotFactory> { BotFactory }

    // database and preference
    single { Room.databaseBuilder(get(), ArukuDatabase::class.java, "aruku-db").build() }
    single { Okkv.Builder().store(MMKVStore(get())).cache(true).build().init() }

    // service
    single { ArukuMiraiService.Connector(get()) }
    factory {
        val connector: ArukuMiraiService.Connector = get()
        if (connector.connected.value == true)
            connector.getValue(Unit, ::unitProp)
        else null
    }

    // view model
    viewModel { LoginViewModel(get()) }
    viewModel { HomeViewModel(get(), get<ArukuMiraiService.Connector>().botsState, get()) }
}