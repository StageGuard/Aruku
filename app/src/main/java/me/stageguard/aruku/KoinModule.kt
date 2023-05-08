package me.stageguard.aruku

import androidx.room.Room
import com.heyanle.okkv2.MMKVStore
import com.heyanle.okkv2.core.Okkv
import loli.ball.okkv2.composeInterceptor
import me.stageguard.aruku.database.ArukuDatabase
import me.stageguard.aruku.database.DBTypeConverters
import me.stageguard.aruku.domain.MainRepository
import me.stageguard.aruku.ui.page.MainViewModel
import me.stageguard.aruku.ui.page.chat.ChatViewModel
import me.stageguard.aruku.ui.page.home.HomeViewModel
import me.stageguard.aruku.ui.page.home.contact.ContactViewModel
import me.stageguard.aruku.ui.page.home.message.MessageViewModel
import me.stageguard.aruku.ui.page.login.LoginViewModel
import net.mamoe.mirai.BotFactory
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module
import java.util.concurrent.ConcurrentHashMap

val applicationModule = module {
    single<BotFactory> { BotFactory }

    // database and preference
    single {
        Room.databaseBuilder(get(), ArukuDatabase::class.java, "aruku-db")
            .fallbackToDestructiveMigration()
            .addTypeConverter(DBTypeConverters())
            .build()
    }
    single {
        Okkv.Builder(MMKVStore(get()))
            .cache(true)
            .composeInterceptor()
            .build()
            .init()
            .default()
    }

    // cache
    single(qualifier = qualifier("avatar_cache")) {
        ConcurrentHashMap<Long, String>()
    }
    single(qualifier = qualifier("nickname_cache")) {
        ConcurrentHashMap<Long, String>()
    }

    // repo
    single<MainRepository>(createdAtStart = false) { params ->
        MainRepositoryImpl(
            connectorRef = params.get(),
            database = get(),
            avatarCache = get(qualifier = qualifier("avatar_cache")),
            nicknameCache = get(qualifier = qualifier("nickname_cache")),
        )
    }

    // view model
    single(createdAtStart = false) { params ->
        MainViewModel(get(), get(), params.get(), params.get())
    }
    viewModel { LoginViewModel(get()) }
    viewModel { HomeViewModel(get()) }
    viewModel { MessageViewModel(get()) }
    viewModel { params -> ContactViewModel(get(), params.get()) }
    viewModel { params -> ChatViewModel(get(), params.get(), get()) }
}