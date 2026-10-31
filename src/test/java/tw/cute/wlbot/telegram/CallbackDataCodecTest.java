package tw.cute.wlbot.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import tw.cute.wlbot.review.ReviewAction;

class CallbackDataCodecTest {
    private static final UUID PLAYER_UUID = UUID.fromString("8f3d5e3a-b6e4-4d92-9f43-f1c58226c2ab");

    private final CallbackDataCodec codec = new CallbackDataCodec();

    @Test
    void roundTripsApproveAndRejectWithinTelegramLimit() {
        for (ReviewAction action : ReviewAction.values()) {
            String encoded = codec.encode(action, PLAYER_UUID);
            CallbackCommand decoded = codec.decode(encoded).orElseThrow();

            assertEquals(action, decoded.action());
            assertEquals(PLAYER_UUID, decoded.playerUuid());
            assertTrue(encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 64);
        }
    }

    @Test
    void rejectsMalformedOrUnsupportedCallbackData() {
        assertTrue(codec.decode(null).isEmpty());
        assertTrue(codec.decode("").isEmpty());
        assertTrue(codec.decode("approve:not-a-uuid").isEmpty());
        assertTrue(codec.decode("unknown:" + PLAYER_UUID).isEmpty());
        assertTrue(codec.decode("APPROVE:" + PLAYER_UUID).isEmpty());
        assertTrue(codec.decode("approve:" + PLAYER_UUID + ":extra").isEmpty());
    }
}
