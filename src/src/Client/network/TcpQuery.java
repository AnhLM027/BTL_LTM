package Client.network;

import Server.network.ProtocolMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Collects one begin/item/end protocol response without exposing socket reads to Swing views.
 */
public final class TcpQuery implements TcpGameClient.MessageListener {
    private final TcpGameClient client;
    private final String itemType;
    private final String endType;
    private final List<ProtocolMessage> items = new ArrayList<>();
    private final CompletableFuture<List<ProtocolMessage>> result = new CompletableFuture<>();

    private TcpQuery(TcpGameClient client, String itemType, String endType) {
        this.client = client;
        this.itemType = itemType;
        this.endType = endType;
    }

    public static CompletableFuture<List<ProtocolMessage>> collect(TcpGameClient client, ProtocolMessage request, String itemType, String endType) throws IOException {
        TcpQuery query = new TcpQuery(client, itemType, endType);
        client.addListener(query);
        query.result.whenComplete((ignored, error) -> client.removeListener(query));
        client.send(request);
        return query.result;
    }

    public static CompletableFuture<ProtocolMessage> single(TcpGameClient client, ProtocolMessage request, String responseType) throws IOException {
        CompletableFuture<ProtocolMessage> result = new CompletableFuture<>();
        TcpGameClient.MessageListener listener = new TcpGameClient.MessageListener() {
            @Override
            public void onMessage(ProtocolMessage message) {
                if (message.type().equals(responseType)) result.complete(message);
                else if (message.type().equals("ERROR"))
                    result.completeExceptionally(new IOException(message.fields().getOrDefault("message", "Server error")));
            }

            @Override
            public void onConnectionError(Exception exception) {
                result.completeExceptionally(exception);
            }
        };
        client.addListener(listener);
        result.whenComplete((ignored, error) -> client.removeListener(listener));
        client.send(request);
        return result;
    }

    @Override
    public void onMessage(ProtocolMessage message) {
        if (message.type().equals(itemType)) items.add(message);
        else if (message.type().equals(endType)) result.complete(List.copyOf(items));
        else if (message.type().equals("ERROR"))
            result.completeExceptionally(new IOException(message.fields().getOrDefault("message", "Server error")));
    }

    @Override
    public void onConnectionError(Exception exception) {
        result.completeExceptionally(exception);
    }
}
