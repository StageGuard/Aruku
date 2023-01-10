package me.stageguard.aruku

import androidx.room.Room
import com.heyanle.okkv2.MMKVStore
import com.heyanle.okkv2.core.Okkv
import me.stageguard.aruku.database.ArukuDatabase
import me.stageguard.aruku.service.ArukuServiceConnector
import me.stageguard.aruku.service.parcel.ArukuContact
import me.stageguard.aruku.ui.activity.unitProp
import me.stageguard.aruku.ui.page.chat.ChatViewModel
import me.stageguard.aruku.ui.page.home.HomeViewModel
import me.stageguard.aruku.ui.page.home.account.AccountAvatarViewModel
import me.stageguard.aruku.ui.page.home.contact.ContactViewModel
import me.stageguard.aruku.ui.page.home.message.MessageViewModel
import me.stageguard.aruku.ui.page.login.LoginViewModel
import net.mamoe.mirai.BotFactory
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val applicationModule = module {
    single<BotFactory> { BotFactory }

    // database and preference
    single {
        Room.databaseBuilder(get(), ArukuDatabase::class.java, "aruku-db")
            .fallbackToDestructiveMigration()
            .build()
    }
    single { Okkv.Builder().store(MMKVStore(get())).cache(true).build().init().default() }

    // service
    single { ArukuServiceConnector(get()) }
    factory {
        val connector: ArukuServiceConnector = get()
        if (connector.connected.value == true)
            connector.getValue(Unit, ::unitProp)
        else null
    }

    // view model
    viewModel { LoginViewModel(get()) }
    viewModel { HomeViewModel(get(), get<ArukuServiceConnector>().bots, get()) }
    viewModel { MessageViewModel(get(), get()) }
    viewModel { ContactViewModel(get(), get()) }
    viewModel { AccountAvatarViewModel() }
    viewModel { params ->
        val contact: ArukuContact = params.get()
        ChatViewModel(get(), get(), params.get())
    }
}