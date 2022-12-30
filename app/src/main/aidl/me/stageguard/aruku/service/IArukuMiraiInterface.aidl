// IArukuMiraiInterface.aidl
package me.stageguard.aruku.service;

import me.stageguard.aruku.service.parcel.AccountInfo;
import me.stageguard.aruku.service.IBotListObserver;
import me.stageguard.aruku.service.ILoginSolver;
import me.stageguard.aruku.service.IMessageConsumer;

// Declare any non-default types here with import statements

interface IArukuMiraiInterface {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
    boolean addBot(in AccountInfo info, boolean alsoLogin);
    boolean removeBot(long accountNo);
    long[] getBots();
    void loginAll();
    boolean login(long accountNo);
    void addBotListObserver(String identity, in IBotListObserver observer);
    void removeBotListObserver(String identity);
    void addLoginSolver(long bot, in ILoginSolver solver);
    void removeLoginSolver(long bot);
    void addMessageEventConsumer(long bot, String identity, in IMessageConsumer consumer);
    void removeMessageEventConsumer(String identity);
    String getAvatar(long account, int type, long subject);
    String getNickname(long account, int type, long subject);
}