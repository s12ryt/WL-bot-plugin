package tw.cute.wlbot.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.cute.wlbot.domain.PlayerIdentity;
import tw.cute.wlbot.queue.PendingApplication;
import tw.cute.wlbot.review.ReviewAction;

class TelegramHttpGatewayTest {
    private static final UUID PLAYER_UUID = UUID.fromString("4757ccde-28ac-4e7d-8781-0843345b6406");

    private final Map<String, String> requestBodies = new ConcurrentHashMap<>();
    private final AtomicReference<String> updatesResponse = new AtomicReference<>();
    private HttpServer server;
    private TelegramHttpGateway gateway;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/botsecret/sendMessage", exchange -> respond(exchange,
                "{\"ok\":true,\"result\":{\"message_id\":321}}"));
        updatesResponse.set("{\"ok\":true,\"result\":["
                        + "{\"update_id\":5,\"callback_query\":{\"id\":\"cb-1\",\"from\":{\"id\":42},"
                        + "\"message\":{\"message_id\":321,\"chat\":{\"id\":100}},"
                        + "\"data\":\"approve:" + PLAYER_UUID + "\"}},"
                        + "{\"update_id\":6,\"message\":{}}]}");
        server.createContext("/botsecret/getUpdates", exchange -> respond(exchange, updatesResponse.get()));
        server.createContext("/botsecret/answerCallbackQuery", exchange -> respond(exchange, "{\"ok\":true,\"result\":true}"));
        server.createContext("/botsecret/deleteMessage", exchange -> respond(exchange, "{\"ok\":true,\"result\":true}"));
        server.start();
        URI baseUri = URI.create("http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort());
        gateway = new TelegramHttpGateway(
                HttpClient.newHttpClient(), baseUri, "secret", 100L, new CallbackDataCodec());
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsApplicationWithInlineApproveAndRejectButtons() {
        PendingApplication application = new PendingApplication(
                9L,
                new PlayerIdentity(PLAYER_UUID, "ContractPlayer"),
                Instant.parse("2026-10-31T10:00:00Z"),
                OptionalLong.empty());

        TelegramMessage message = gateway.sendApplication(application);

        assertEquals(new TelegramMessage(100L, 321L), message);
        JsonObject body = bodyFor("/botsecret/sendMessage");
        assertEquals(100L, body.get("chat_id").getAsLong());
        assertTrue(body.get("text").getAsString().contains("ContractPlayer"));
        assertTrue(body.get("text").getAsString().contains(PLAYER_UUID.toString()));
        var buttons = body.getAsJsonObject("reply_markup").getAsJsonArray("inline_keyboard")
                .get(0).getAsJsonArray();
        assertEquals("approve:" + PLAYER_UUID,
                buttons.get(0).getAsJsonObject().get("callback_data").getAsString());
        assertEquals("reject:" + PLAYER_UUID,
                buttons.get(1).getAsJsonObject().get("callback_data").getAsString());
    }

    @Test
    void parsesCallbackUpdatesAndKeepsNonCallbackUpdatesForOffsetAdvancement() {
        var updates = gateway.getUpdates(4L, 10);

        assertEquals(2, updates.size());
        assertEquals(5L, updates.get(0).updateId());
        TelegramCallback callback = updates.get(0).callback().orElseThrow();
        assertEquals("cb-1", callback.callbackId());
        assertEquals(42L, callback.actorUserId());
        assertEquals(100L, callback.chatId());
        assertEquals(321L, callback.messageId());
        assertEquals("approve:" + PLAYER_UUID, callback.data());
        assertEquals(6L, updates.get(1).updateId());
        assertTrue(updates.get(1).callback().isEmpty());

        JsonObject request = bodyFor("/botsecret/getUpdates");
        assertEquals(4L, request.get("offset").getAsLong());
        assertEquals(10, request.get("timeout").getAsInt());
        assertEquals("callback_query", request.getAsJsonArray("allowed_updates").get(0).getAsString());
    }

    @Test
    void treatsMalformedCallbackAsAnEmptyUpdateSoPollingCanAdvance() {
        updatesResponse.set("{\"ok\":true,\"result\":[{\"update_id\":9,"
                + "\"callback_query\":{\"id\":\"cb-bad\",\"from\":\"invalid\","
                + "\"message\":{},\"data\":\"approve:" + PLAYER_UUID + "\"}}]}");

        var updates = gateway.getUpdates(9L, 10);

        assertEquals(1, updates.size());
        assertEquals(9L, updates.getFirst().updateId());
        assertTrue(updates.getFirst().callback().isEmpty());
    }

    @Test
    void usesTelegramContractsForCallbackAnswerAndMessageDeletion() {
        gateway.answerCallback("cb-1", "done", true);
        gateway.deleteMessage(100L, 321L);

        JsonObject answer = bodyFor("/botsecret/answerCallbackQuery");
        assertEquals("cb-1", answer.get("callback_query_id").getAsString());
        assertEquals("done", answer.get("text").getAsString());
        assertTrue(answer.get("show_alert").getAsBoolean());

        JsonObject deletion = bodyFor("/botsecret/deleteMessage");
        assertEquals(100L, deletion.get("chat_id").getAsLong());
        assertEquals(321L, deletion.get("message_id").getAsLong());
        assertFalse(deletion.has("token"));
    }

    private JsonObject bodyFor(String path) {
        return JsonParser.parseString(requestBodies.get(path)).getAsJsonObject();
    }

    private void respond(HttpExchange exchange, String response) throws IOException {
        requestBodies.put(exchange.getRequestURI().getPath(),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
