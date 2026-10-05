package Server.network;

import java.io.IOException;

public interface ServerEventHandler {
    default void onConnected(ClientSession session) throws IOException {
    }

    void onMessage(ClientSession session, ProtocolMessage message) throws IOException;

    default void onDisconnected(ClientSession session) {
    }
}
