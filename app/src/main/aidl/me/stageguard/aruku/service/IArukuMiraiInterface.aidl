// IArukuMiraiInterface.aidl
package me.stageguard.aruku.service;

import me.stageguard.aruku.service.parcel.AccountInfo;
import me.stageguard.aruku.service.IBotListObserver;
import me.stageguard.aruku.service.ILoginSolver;
import me.stageguard.aruku.service.IMessageConsumer;
import me.stageguard.aruku.service.parcel.ArukuContact;
import me.stageguard.aruku.service.parcel.GroupMemberInfo;
import me.stageguard.aruku.service.parcel.GroupAtMessage;
import me.stageguard.aruku.service.parcel.ArukuAudio;

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
    // accessing mirai from application
    String getAvatarUrl(long account, in ArukuContact contact);
    String getNickname(long account, in ArukuContact contact);
    GroupMemberInfo getGroupMemberInfo(long account, long groupId, long memberId);
}