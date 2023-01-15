package me.stageguard.aruku

import android.content.Context
import androidx.room.Room
import com.heyanle.okkv2.MMKVStore
import com.heyanle.okkv2.core.Okkv
import me.stageguard.aruku.cache.AudioCache
import me.stageguard.aruku.database.ArukuDatabase
import me.stageguard.aruku.domain.MainRepository
import me.stageguard.aruku.domain.RetrofitDownloadService
import me.stageguard.aruku.service.ArukuServiceConnector
import me.stageguard.aruku.ui.page.chat.ChatViewModel
import me.stageguard.aruku.ui.page.home.HomeViewModel
import me.stageguard.aruku.ui.page.home.account.AccountAvatarViewModel
import me.stageguard.aruku.ui.page.home.contact.ContactViewModel
import me.stageguard.aruku.ui.page.home.message.MessageViewModel
import me.stageguard.aruku.ui.page.login.LoginViewModel
import net.mamoe.mirai.BotFactory
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit

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

    // repo
    factory<MainRepository> {
        val connector: ArukuServiceConnector = get()
        val binder by connector
        val connected = connector.connected.value == true
        MainRepositoryImpl(if (connected) binder else null, get())
    }

    // cache
    single { Retrofit.Builder().baseUrl("http://localhost/").build() }
    single {
        AudioCache(
            get<Context>().externalCacheDir!!.resolve("audio_cache"),
            get<Retrofit>().create(RetrofitDownloadService::class.java)
        )
    }

    // view model
    viewModel { LoginViewModel(get()) }
    viewModel { HomeViewModel(get(), get<ArukuServiceConnector>().bots) }
    viewModel { MessageViewModel(get()) }
    viewModel { ContactViewModel(get()) }
    viewModel { AccountAvatarViewModel() }
    viewModel { params -> ChatViewModel(get(), get(), params.get()) }
}