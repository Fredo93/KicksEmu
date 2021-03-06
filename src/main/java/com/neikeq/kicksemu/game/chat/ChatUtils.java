package com.neikeq.kicksemu.game.chat;

import com.neikeq.kicksemu.game.sessions.Session;
import com.neikeq.kicksemu.io.Output;
import com.neikeq.kicksemu.network.packets.out.MessageBuilder;
import com.neikeq.kicksemu.network.server.ServerManager;

public class ChatUtils {

    public static void broadcastNotice(String notice) {
        ServerManager.getPlayers().values().forEach(s -> sendServerNotice(s, notice));
        Output.println("Chat notice: " + notice);
    }

    public static void sendServerMessage(Session session, String message) {
        session.sendAndFlush(MessageBuilder.chatMessage(MessageType.SERVER_MESSAGE, message));
    }

    private static void sendServerNotice(Session session, String notice) {
        session.sendAndFlush(MessageBuilder.chatMessage(MessageType.SERVER_NOTICE, notice));
    }
}
