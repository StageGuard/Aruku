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
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit

val applicationModule = module {
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

    single {
        Retrofit.Builder()
            .baseUrl("http://localhost/")
            .build()
    }

    // repo
    single<MainRepository>(createdAtStart = false) {
        MainRepositoryImpl(
            context = get(),
            database = get(),
            retrofit = get(),
        )
    }

    // view model
    viewModel { params ->
        MainViewModel(get(), get(), params.get(), params.get())
    }
    viewModel { LoginViewModel(get()) }
    viewModel { HomeViewModel(get()) }
    viewModel { MessageViewModel(get()) }
    viewModel { params -> ContactViewModel(get(), params.get()) }
    viewModel { params -> ChatViewModel(get(), params.get(), get()) }
}