package tw.cute.wlbot.telegram;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import tw.cute.wlbot.queue.PendingApplication;
import tw.cute.wlbot.review.ReviewAction;

public final class TelegramHttpGateway implements TelegramGateway {
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final String apiRoot;
    private final long targetChatId;
    private final CallbackDataCodec codec;
    private final Gson gson = new Gson();

    public TelegramHttpGateway(
            HttpClient httpClient,
            URI baseApiUri,
            String botToken,
            long targetChatId,
            CallbackDataCodec codec) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        Objects.requireNonNull(baseApiUri, "baseApiUri");
        if (!baseApiUri.isAbsolute()) {
            throw new IllegalArgumentException("Telegram base URI must be absolute");
        }
        if (botToken == null || botToken.isBlank()) {
            throw new IllegalArgumentException("Telegram bot token must not be blank");
        }
        if (targetChatId == 0) {
            throw new IllegalArgumentException("Target chat ID must not be zero");
        }
        String base = baseApiUri.toString();
        this.apiRoot = (base.endsWith("/") ? base.substring(0, base.length() - 1) : base)
                + "/bot" + botToken + "/";
        this.targetChatId = targetChatId;
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    @Override
    public TelegramMessage sendApplication(PendingApplication application) {
        Objects.requireNonNull(application, "application");
        JsonObject body = new JsonObject();
        body.addProperty("chat_id", targetChatId);
        body.addProperty("text", "新的白名單申請\n玩家：" + application.player().name()
                + "\nUUID：" + application.player().uuid()
                + "\n申請序號：" + application.sequence());
        body.add("reply_markup", inlineKeyboard(application));

        JsonObject result = post("sendMessage", body, DEFAULT_REQUEST_TIMEOUT).getAsJsonObject();
        return new TelegramMessage(targetChatId, requiredLong(result, "message_id"));
    }

    @Override
    public List<TelegramUpdate> getUpdates(long offset, int timeoutSeconds) {
        if (offset < 0 || timeoutSeconds < 1 || timeoutSeconds > 50) {
            throw new IllegalArgumentException("Invalid Telegram polling arguments");
        }
        JsonObject body = new JsonObject();
        body.addProperty("offset", offset);
        body.addProperty("timeout", timeoutSeconds);
        JsonArray allowedUpdates = new JsonArray();
        allowedUpdates.add("callback_query");
        body.add("allowed_updates", allowedUpdates);

        JsonArray result = post(
                "getUpdates", body, Duration.ofSeconds((long) timeoutSeconds + 5L)).getAsJsonArray();
        List<TelegramUpdate> updates = new ArrayList<>(result.size());
        for (JsonElement element : result) {
            JsonObject update = element.getAsJsonObject();
            long updateId = requiredLong(update, "update_id");
            updates.add(new TelegramUpdate(updateId, parseCallback(update)));
        }
        return List.copyOf(updates);
    }

    @Override
    public void answerCallback(String callbackId, String text, boolean showAlert) {
        if (callbackId == null || callbackId.isBlank()) {
            throw new IllegalArgumentException("Callback ID must not be blank");
        }
        Objects.requireNonNull(text, "text");
        JsonObject body = new JsonObject();
        body.addProperty("callback_query_id", callbackId);
        body.addProperty("text", text);
        body.addProperty("show_alert", showAlert);
        post("answerCallbackQuery", body, DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    public void deleteMessage(long chatId, long messageId) {
        if (chatId == 0 || messageId <= 0) {
            throw new IllegalArgumentException("Telegram message identifiers are invalid");
        }
        JsonObject body = new JsonObject();
        body.addProperty("chat_id", chatId);
        body.addProperty("message_id", messageId);
        post("deleteMessage", body, DEFAULT_REQUEST_TIMEOUT);
    }

    private JsonObject inlineKeyboard(PendingApplication application) {
        JsonObject approve = button(
                "通過", codec.encode(ReviewAction.APPROVE, application.player().uuid()));
        JsonObject reject = button(
                "拒絕", codec.encode(ReviewAction.REJECT, application.player().uuid()));
        JsonArray row = new JsonArray();
        row.add(approve);
        row.add(reject);
        JsonArray keyboard = new JsonArray();
        keyboard.add(row);
        JsonObject markup = new JsonObject();
        markup.add("inline_keyboard", keyboard);
        return markup;
    }

    private static JsonObject button(String text, String callbackData) {
        JsonObject button = new JsonObject();
        button.addProperty("text", text);
        button.addProperty("callback_data", callbackData);
        return button;
    }

    private Optional<TelegramCallback> parseCallback(JsonObject update) {
        JsonElement callbackElement = update.get("callback_query");
        if (callbackElement == null || !callbackElement.isJsonObject()) {
            return Optional.empty();
        }
        try {
            JsonObject callback = callbackElement.getAsJsonObject();
            JsonObject from = callback.getAsJsonObject("from");
            JsonObject message = callback.getAsJsonObject("message");
            JsonObject chat = message.getAsJsonObject("chat");
            return Optional.of(new TelegramCallback(
                    requiredString(callback, "id"),
                    requiredLong(from, "id"),
                    requiredLong(chat, "id"),
                    requiredLong(message, "message_id"),
                    requiredString(callback, "data")));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private JsonElement post(String method, JsonObject body, Duration timeout) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiRoot + method))
                .timeout(timeout)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TelegramApiException("Telegram request was interrupted", exception);
        } catch (IOException exception) {
            throw new TelegramApiException("Telegram request failed", exception);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new TelegramApiException("Telegram returned HTTP " + response.statusCode());
        }
        try {
            JsonObject envelope = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!envelope.has("ok") || !envelope.get("ok").getAsBoolean()) {
                String description = envelope.has("description")
                        ? envelope.get("description").getAsString()
                        : "unknown API error";
                throw new TelegramApiException("Telegram API error: " + description);
            }
            if (!envelope.has("result")) {
                throw new TelegramApiException("Telegram response did not contain a result");
            }
            return envelope.get("result");
        } catch (JsonParseException | IllegalStateException exception) {
            throw new TelegramApiException("Telegram returned invalid JSON", exception);
        }
    }

    private static long requiredLong(JsonObject object, String property) {
        if (object == null || !object.has(property)) {
            throw new IllegalArgumentException("Missing Telegram property: " + property);
        }
        return object.get(property).getAsLong();
    }

    private static String requiredString(JsonObject object, String property) {
        if (object == null || !object.has(property)) {
            throw new IllegalArgumentException("Missing Telegram property: " + property);
        }
        return object.get(property).getAsString();
    }
}
