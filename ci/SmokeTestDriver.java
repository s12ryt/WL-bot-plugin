// WL-bot-plugin 煙霧測試驅動（JEP 330 單檔程式，不進入正式 build）。
//
// 用途：以真實 Paper/Folia 26.3 伺服器 + 本機 mock Telegram API，對 wl-bot-plugin
// 做端到端驗證：pending 申請 → 通知 admin → callback 審核 → 玩家進白名單 → 佇列列刪除。
//
// 執行（於專案根目錄，先 ./gradlew build 產出 fat JAR）：
//   java --add-modules jdk.httpserver -cp build/libs/wl-bot-plugin-1.0.0.jar ci/SmokeTestDriver.java paper
//   java --add-modules jdk.httpserver -cp build/libs/wl-bot-plugin-1.0.0.jar ci/SmokeTestDriver.java folia
//
// 依賴：fat JAR 內的 org.sqlite（JDBC 注入用）與 gson（解析 fill API），
//       jdk.httpserver 模組提供 mock HTTP 伺服器。
//
// 離開碼：0 = 全部斷言通過；1 = 有斷言失敗或流程錯誤；2 = 用法錯誤。
// 失敗時保留 build/smoke/<flavor>/run/logs 與 mock-requests.jsonl 供檢查。

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SmokeTestDriver {

    static final String MC_VERSION = "26.3";
    static final String BOT_TOKEN = "123456:SMOKE_TEST_TOKEN";
    static final long ADMIN_USER_ID = 111111L;
    static final long ADMIN_CHAT_ID = 222222L;
    static final int MOCK_PORT = 18080;
    static final int SERVER_PORT = 25599;
    static final int MOCK_MESSAGE_ID = 777;
    static final long CALLBACK_UPDATE_ID = 1000L;
    static final UUID PLAYER_UUID = UUID.fromString("7a1f3f5e-8c2b-4d9a-b6e4-3f2a1c0d9b8e");
    static final String PLAYER_NAME = "SmokeTester";
    static final Duration DONE_TIMEOUT_PAPER = Duration.ofSeconds(300);
    static final Duration DONE_TIMEOUT_FOLIA = Duration.ofSeconds(420);
    static final String USER_AGENT = "WLBotPlugin-CI/1.0.0 (https://github.com/s12ryt/WL-bot-plugin)";

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !(args[0].equals("paper") || args[0].equals("folia"))) {
            System.err.println("用法: java SmokeTestDriver.java <paper|folia>");
            System.exit(2);
        }
        String flavor = args[0];
        Path root = Path.of("build", "smoke", flavor);
        Path cacheDir = root.resolve("cache");
        Path runDir = root.resolve("run");
        System.out.printf("[smoke] flavor=%s root=%s%n", flavor, root.toAbsolutePath());

        MockTelegram mock = null;
        try {
            step(1, "下載 " + flavor + ' ' + MC_VERSION + " 伺服器（fill v3 API）");
            Path serverJar = downloadServer(flavor, cacheDir);

            step(2, "準備 run 目錄與 plugins");
            prepareRunDir(runDir, serverJar);

            step(3, "第一次啟動：預設 config，預期外掛因缺 bot-token 拒絕啟用");
            firstBoot(runDir, flavor);

            step(4, "改寫 config.yml 指向 mock，並注入 PENDING 申請");
            configurePlugin(runDir);
            injectPending(runDir);

            step(5, "啟動 mock Telegram API（127.0.0.1:" + MOCK_PORT + "）");
            mock = MockTelegram.start(root.resolve("mock-requests.jsonl"));

            step(6, "第二次啟動：通知 → callback 審核 approve → 加入白名單");
            secondBoot(runDir, flavor);

            step(7, "最終斷言");
            finalAsserts(runDir, mock);

            System.out.println("[smoke] SUCCESS: 所有煙霧測試斷言通過 (" + flavor + ")");
            System.exit(0);
        } catch (Throwable t) {
            System.out.println("[smoke] FAIL: " + t);
            for (StackTraceElement e : t.getStackTrace()) System.out.println("    at " + e);
            System.exit(1);
        } finally {
            if (mock != null) mock.close();
        }
    }

    // ---------- 步驟 1：下載伺服器 ----------

    static Path downloadServer(String flavor, Path cacheDir) throws Exception {
        Files.createDirectories(cacheDir);
        Path jar = cacheDir.resolve(flavor + '-' + MC_VERSION + ".jar");
        if (Files.exists(jar) && Files.size(jar) > 1_000_000L) {
            System.out.println("[smoke] 使用快取伺服器 jar: " + jar);
            return jar;
        }
        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        URI meta = URI.create("https://fill.papermc.io/v3/projects/" + flavor
                + "/versions/" + MC_VERSION + "/builds/latest");
        HttpRequest metaReq = HttpRequest.newBuilder(meta)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", USER_AGENT)
                .GET().build();
        HttpResponse<String> metaResp = http.send(metaReq, HttpResponse.BodyHandlers.ofString());
        if (metaResp.statusCode() != 200) {
            throw new IllegalStateException("fill API 回應 " + metaResp.statusCode() + ": " + metaResp.body());
        }
        JsonObject root = JsonParser.parseString(metaResp.body()).getAsJsonObject();
        JsonObject downloads = root.getAsJsonObject("downloads");
        if (downloads == null || downloads.get("server:default") == null) {
            throw new IllegalStateException("fill API 回應缺少 downloads.\"server:default\": " + metaResp.body());
        }
        JsonObject server = downloads.getAsJsonObject("server:default");
        String url = server.get("url").getAsString();
        String expectedSha256 = null;
        if (server.get("checksums") != null && server.getAsJsonObject("checksums").get("sha256") != null) {
            expectedSha256 = server.getAsJsonObject("checksums").get("sha256").getAsString();
        }
        System.out.println("[smoke] 下載 " + url + (expectedSha256 != null ? " (sha256 驗證)" : " (無 checksum，略過驗證)"));

        Path tmp = Files.createTempFile(cacheDir, "server-", ".jar.part");
        HttpRequest dlReq = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(10))
                .header("User-Agent", USER_AGENT)
                .GET().build();
        HttpResponse<Path> dlResp = http.send(dlReq, HttpResponse.BodyHandlers.ofFile(tmp));
        if (dlResp.statusCode() != 200) {
            throw new IllegalStateException("伺服器 jar 下載失敗 HTTP " + dlResp.statusCode());
        }
        if (expectedSha256 != null) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String actual = HexFormat.of().formatHex(md.digest(Files.readAllBytes(tmp)));
            if (!actual.equalsIgnoreCase(expectedSha256)) {
                throw new IllegalStateException("sha256 不符: expected=" + expectedSha256 + " actual=" + actual);
            }
        }
        Files.move(tmp, jar, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("[smoke] 伺服器 jar 就緒: " + jar + " (" + Files.size(jar) + " bytes)");
        return jar;
    }

    // ---------- 步驟 2：run 目錄 ----------

    static void prepareRunDir(Path runDir, Path serverJar) throws Exception {
        deleteRecursively(runDir);
        Files.createDirectories(runDir.resolve("plugins"));
        Files.writeString(runDir.resolve("eula.txt"), "eula=true\n");
        Files.writeString(runDir.resolve("server.properties"), """
                server-port=%d
                white-list=true
                online-mode=true
                level-type=minecraft:flat
                view-distance=3
                simulation-distance=3
                generate-structures=false
                spawn-protection=0
                motd=WLBotPlugin smoke test
                """.formatted(SERVER_PORT));
        Files.copy(serverJar, runDir.resolve("server.jar"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(pluginJar(), runDir.resolve("plugins").resolve("WLBotPlugin.jar"),
                StandardCopyOption.REPLACE_EXISTING);
        System.out.println("[smoke] run 目錄就緒: " + runDir.toAbsolutePath());
    }

    static Path pluginJar() throws IOException {
        Path p = Path.of("build", "libs", "wl-bot-plugin-1.0.0.jar");
        if (!Files.exists(p)) {
            throw new FileNotFoundException("找不到 " + p.toAbsolutePath() + "，請先執行 ./gradlew build");
        }
        return p;
    }

    static Path pluginData(Path runDir, String name) {
        return runDir.resolve("plugins").resolve("WLBotPlugin").resolve(name);
    }

    // ---------- 步驟 3：第一次啟動（預設 config） ----------

    static void firstBoot(Path runDir, String flavor) throws Exception {
        ServerProcess sp = startServer(runDir, flavor, "boot1");
        try {
            awaitDone(sp, doneTimeout(flavor));
            sendStop(sp);
            awaitExit(sp, Duration.ofSeconds(120));
        } finally {
            sp.close();
        }
        assertTrue(Files.exists(pluginData(runDir, "config.yml")),
                "第一次啟動後應產生 plugins/WLBotPlugin/config.yml");
        String console = Files.readString(sp.consoleLog);
        assertTrue(console.contains("請先在 config.yml 設定 telegram.bot-token"),
                "第一次啟動 log 應含「請先在 config.yml 設定 telegram.bot-token」");
        System.out.println("[smoke] boot1 斷言通過：config.yml 已產生、缺 token 錯誤訊息存在");
    }

    // ---------- 步驟 4：config + pending 注入 ----------

    static void configurePlugin(Path runDir) throws Exception {
        Path cfg = pluginData(runDir, "config.yml");
        String s = Files.readString(cfg, StandardCharsets.UTF_8);
        s = s.replace("bot-token: 'CHANGE_ME'", "bot-token: '" + BOT_TOKEN + "'");
        s = s.replace("api-url: 'https://api.telegram.org'",
                "api-url: 'http://127.0.0.1:" + MOCK_PORT + "'");
        s = s.replace("admin-user-id: 0", "admin-user-id: " + ADMIN_USER_ID);
        s = s.replace("admin-chat-id: 0", "admin-chat-id: " + ADMIN_CHAT_ID);
        Files.writeString(cfg, s, StandardCharsets.UTF_8);
        assertTrue(s.contains(BOT_TOKEN) && s.contains("127.0.0.1:" + MOCK_PORT)
                        && s.contains("admin-user-id: " + ADMIN_USER_ID)
                        && s.contains("admin-chat-id: " + ADMIN_CHAT_ID),
                "config.yml 替換後應含測試值（原樣:\n" + Files.readString(cfg) + "）");
        System.out.println("[smoke] config.yml 已指向 mock Telegram");
    }

    static void injectPending(Path runDir) throws Exception {
        Path db = pluginData(runDir, "whitelist-queue.db");
        Files.createDirectories(db.getParent());
        Class.forName("org.sqlite.JDBC");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath())) {
            try (Statement st = c.createStatement()) {
                // 與 SqliteQueueRepository 相同的表結構；IF NOT EXISTS 冪等，
                // 第二次啟動時外掛自己的 CREATE TABLE IF NOT EXISTS 不會影響既有資料。
                st.execute("""
                        CREATE TABLE IF NOT EXISTS whitelist_requests(
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            player_uuid TEXT NOT NULL UNIQUE,
                            player_name TEXT NOT NULL,
                            state TEXT NOT NULL CHECK(state IN ('PENDING','COOLDOWN')),
                            requested_at INTEGER NOT NULL,
                            cooldown_until INTEGER,
                            telegram_message_id INTEGER)""");
                st.execute("CREATE INDEX IF NOT EXISTS idx_whitelist_requests_pending"
                        + " ON whitelist_requests(state,id)");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO whitelist_requests(player_uuid, player_name, state, requested_at)"
                            + " VALUES(?, ?, 'PENDING', ?)")) {
                ps.setString(1, PLAYER_UUID.toString());
                ps.setString(2, PLAYER_NAME);
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            }
        }
        System.out.println("[smoke] 已注入 PENDING 申請: " + PLAYER_UUID + " (" + PLAYER_NAME + ")");
    }

    // ---------- 步驟 6：第二次啟動 ----------

    static void secondBoot(Path runDir, String flavor) throws Exception {
        ServerProcess sp = startServer(runDir, flavor, "boot2");
        try {
            awaitDone(sp, doneTimeout(flavor));
            awaitWhitelisted(runDir, Duration.ofSeconds(90));
            sendStop(sp);
            awaitExit(sp, Duration.ofSeconds(120));
        } finally {
            sp.close();
        }
        String console = Files.readString(sp.consoleLog);
        assertTrue(console.contains("WLBotPlugin 已啟用"), "boot2 log 應含「WLBotPlugin 已啟用」");
        assertFalse(console.contains("Error occurred while enabling WLBotPlugin"),
                "boot2 log 不應有外掛啟用錯誤");
        System.out.println("[smoke] boot2 斷言通過：外掛完整啟用、玩家已進白名單、伺服器乾淨關閉");
    }

    static void awaitWhitelisted(Path runDir, Duration timeout) throws Exception {
        Path whitelist = runDir.resolve("whitelist.json");
        String dashed = PLAYER_UUID.toString();
        String plain = dashed.replace("-", "");
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.exists(whitelist)) {
                String s = Files.readString(whitelist);
                if (s.contains(dashed) || s.contains(plain)) {
                    System.out.println("[smoke] whitelist.json 已包含 " + dashed);
                    return;
                }
            }
            Thread.sleep(2000);
        }
        throw new AssertionError("等待 " + dashed + " 進入 whitelist.json 逾時（" + timeout.toSeconds() + "s）");
    }

    // ---------- 步驟 7：最終斷言 ----------

    static void finalAsserts(Path runDir, MockTelegram mock) throws Exception {
        for (String method : List.of("sendMessage", "getUpdates", "answerCallbackQuery", "deleteMessage")) {
            assertTrue(mock.methodsSeen.contains(method), "mock 未收到 " + method + " 請求");
        }
        System.out.println("[smoke] mock 已收到全部四種 API 呼叫");

        String wl = Files.readString(runDir.resolve("whitelist.json"));
        assertTrue(wl.contains(PLAYER_UUID.toString()) || wl.contains(PLAYER_UUID.toString().replace("-", "")),
                "whitelist.json 應含玩家 UUID");

        Path db = pluginData(runDir, "whitelist-queue.db");
        assertTrue(Files.exists(db), "whitelist-queue.db 應存在");
        Class.forName("org.sqlite.JDBC");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM whitelist_requests WHERE player_uuid = ?")) {
            ps.setString(1, PLAYER_UUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                int n = rs.getInt(1);
                assertTrue(n == 0, "approve 後佇列應刪除該玩家列，剩餘 " + n + " 列");
            }
        }
        System.out.println("[smoke] 佇列中該玩家列已刪除");
    }

    // ---------- 伺服器進程管理 ----------

    static final class ServerProcess implements AutoCloseable {
        final Process process;
        final Path consoleLog;
        final AtomicBoolean doneSeen;
        final List<String> recentLines;

        ServerProcess(Process process, Path consoleLog, AtomicBoolean doneSeen, List<String> recentLines) {
            this.process = process;
            this.consoleLog = consoleLog;
            this.doneSeen = doneSeen;
            this.recentLines = recentLines;
        }

        @Override
        public void close() {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    static ServerProcess startServer(Path runDir, String flavor, String tag) throws Exception {
        Path consoleLog = runDir.resolve("logs").resolve("smoke-" + tag + "-console.log");
        Files.createDirectories(consoleLog.getParent());
        AtomicBoolean doneSeen = new AtomicBoolean();
        List<String> recentLines = new ArrayList<>();
        BufferedWriter sink = Files.newBufferedWriter(consoleLog, StandardCharsets.UTF_8);
        Process p = new ProcessBuilder(javaBin().toString(), "-Dfile.encoding=UTF-8",
                        "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                        "-Xms512M", "-Xmx2048M",
                        "-jar", "server.jar", "nogui")
                .directory(runDir.toFile())
                .redirectErrorStream(true)
                .start();
        Thread.ofVirtual().name("smoke-console-" + tag).start(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    synchronized (sink) {
                        sink.write(line);
                        sink.newLine();
                        sink.flush();
                    }
                    synchronized (recentLines) {
                        recentLines.add(line);
                        if (recentLines.size() > 200) {
                            recentLines.remove(0);
                        }
                    }
                    if (line.contains("Done (")) {
                        doneSeen.set(true);
                    }
                }
            } catch (IOException ignored) {
                // 進程結束時串流關閉屬正常
            } finally {
                try {
                    sink.close();
                } catch (IOException ignored) {
                }
            }
        });
        System.out.println("[smoke] 伺服器啟動中 pid=" + p.pid() + " log=" + consoleLog);
        return new ServerProcess(p, consoleLog, doneSeen, recentLines);
    }

    static void awaitDone(ServerProcess sp, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (sp.doneSeen.get()) {
                System.out.println("[smoke] 伺服器已就緒（看見 Done 標記）");
                return;
            }
            if (!sp.process.isAlive()) {
                throw new AssertionError("伺服器在 Done 前退出，exit="
                        + sp.process.exitValue() + "，最後 log:\n" + tail(sp));
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("等待伺服器 Done 逾時（" + timeout.toSeconds() + "s），最後 log:\n" + tail(sp));
    }

    static void sendStop(ServerProcess sp) throws Exception {
        sp.process.getOutputStream().write("stop\n".getBytes(StandardCharsets.UTF_8));
        sp.process.getOutputStream().flush();
        System.out.println("[smoke] 已送出 stop 指令");
    }

    static void awaitExit(ServerProcess sp, Duration timeout) throws Exception {
        if (!sp.process.waitFor(timeout.toSeconds(), java.util.concurrent.TimeUnit.SECONDS)) {
            sp.process.destroyForcibly();
            sp.process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            throw new AssertionError("伺服器 stop 後未在 " + timeout.toSeconds() + "s 內退出");
        }
        int code = sp.process.exitValue();
        assertTrue(code == 0, "伺服器應正常退出（exit=0），實際 exit=" + code);
        System.out.println("[smoke] 伺服器正常退出 (exit=0)");
    }

    static String tail(ServerProcess sp) {
        synchronized (sp.recentLines) {
            return String.join("\n", sp.recentLines);
        }
    }

    static Duration doneTimeout(String flavor) {
        return flavor.equals("folia") ? DONE_TIMEOUT_FOLIA : DONE_TIMEOUT_PAPER;
    }

    static Path javaBin() {
        String home = System.getProperty("java.home");
        String exe = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        return Path.of(home, "bin", exe);
    }

    // ---------- mock Telegram ----------

    static final class MockTelegram implements AutoCloseable {
        final HttpServer server;
        final Set<String> methodsSeen = ConcurrentHashMap.newKeySet();
        final AtomicBoolean sendMessageSeen = new AtomicBoolean();
        final AtomicBoolean callbackSent = new AtomicBoolean();
        private final BufferedWriter jsonl;

        private MockTelegram(HttpServer server, BufferedWriter jsonl) {
            this.server = server;
            this.jsonl = jsonl;
        }

        static MockTelegram start(Path jsonlPath) throws Exception {
            Files.createDirectories(jsonlPath.getParent());
            BufferedWriter jsonl = Files.newBufferedWriter(jsonlPath, StandardCharsets.UTF_8);
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", MOCK_PORT), 0);
            MockTelegram mock = new MockTelegram(server, jsonl);
            server.createContext("/", mock::handle);
            server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
            server.start();
            System.out.println("[smoke] mock Telegram 已啟動，記錄至 " + jsonlPath.toAbsolutePath());
            return mock;
        }

        void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String method = methodOf(path);
            methodsSeen.add(method);
            recordRequest(method, body);
            String result;
            switch (method) {
                case "sendMessage" -> {
                    sendMessageSeen.set(true);
                    result = "{\"ok\":true,\"result\":{\"message_id\":" + MOCK_MESSAGE_ID
                            + ",\"date\":0,\"chat\":{\"id\":" + ADMIN_CHAT_ID + "}}}";
                }
                case "getUpdates" -> {
                    if (sendMessageSeen.get() && !callbackSent.get()) {
                        callbackSent.set(true);
                        sleepQuietly(300);
                        result = "{\"ok\":true,\"result\":[{\"update_id\":" + CALLBACK_UPDATE_ID
                                + ",\"callback_query\":{\"id\":\"cbq-smoke\",\"from\":{\"id\":"
                                + ADMIN_USER_ID + ",\"is_bot\":false,\"first_name\":\"SmokeAdmin\"},"
                                + "\"message\":{\"message_id\":" + MOCK_MESSAGE_ID
                                + ",\"date\":0,\"chat\":{\"id\":" + ADMIN_CHAT_ID + "}},"
                                + "\"chat_instance\":\"0\",\"data\":\"approve:" + PLAYER_UUID + "\"}}]}";
                    } else {
                        // 模擬 long-poll 節流，避免外掛空轉打爆 mock
                        sleepQuietly(2000);
                        result = "{\"ok\":true,\"result\":[]}";
                    }
                }
                case "answerCallbackQuery" -> result = "{\"ok\":true,\"result\":true}";
                case "deleteMessage" -> result = "{\"ok\":true,\"result\":true}";
                default -> {
                    System.out.println("[smoke] mock 收到未知方法 " + method + "，回 ok");
                    result = "{\"ok\":true,\"result\":true}";
                }
            }
            byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            try (var out = ex.getResponseBody()) {
                out.write(bytes);
            }
        }

        static String methodOf(String path) {
            int idx = path.lastIndexOf('/');
            return idx >= 0 ? path.substring(idx + 1) : path;
        }

        void recordRequest(String method, String body) {
            String line = "{\"ts\":" + System.currentTimeMillis()
                    + ",\"method\":\"" + method + "\",\"body\":" + jsonString(body) + "}";
            synchronized (jsonl) {
                try {
                    jsonl.write(line);
                    jsonl.newLine();
                    jsonl.flush();
                } catch (IOException e) {
                    System.out.println("[smoke] mock 記錄失敗: " + e);
                }
            }
        }

        static String jsonString(String s) {
            StringBuilder sb = new StringBuilder("\"");
            for (int i = 0; i < s.length(); i++) {
                char ch = s.charAt(i);
                switch (ch) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> {
                        if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch));
                        else sb.append(ch);
                    }
                }
            }
            return sb.append('"').toString();
        }

        static void sleepQuietly(long ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void close() {
            server.stop(0);
            synchronized (jsonl) {
                try {
                    jsonl.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    // ---------- 共用工具 ----------

    static void step(int n, String title) {
        System.out.println("[smoke] === 步驟 " + n + ": " + title + " ===");
    }

    static void assertTrue(boolean cond, String message) {
        if (!cond) {
            throw new AssertionError(message);
        }
    }

    static void assertFalse(boolean cond, String message) {
        assertTrue(!cond, message);
    }

    static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }
}
