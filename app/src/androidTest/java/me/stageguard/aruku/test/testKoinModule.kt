package me.stageguard.aruku.test

import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.mock.MockBotFactory
import org.koin.dsl.module

val testBotFactoryModule = module {
    single<BotFactory> { MockBotFactory }
}