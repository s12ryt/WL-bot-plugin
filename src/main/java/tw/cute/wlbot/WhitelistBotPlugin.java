package tw.cute.wlbot;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import tw.cute.wlbot.command.WlBotCommandExecutor;
import tw.cute.wlbot.command.WlBotCommandService;
import tw.cute.wlbot.config.PluginSettings;
import tw.cute.wlbot.minecraft.LoginRequestService;
import tw.cute.wlbot.minecraft.ScheduledWhitelistGateway;
import tw.cute.wlbot.minecraft.WhitelistLoginListener;
import tw.cute.wlbot.presentation.QueueMessageFormatter;
import tw.cute.wlbot.queue.SqliteQueueRepository;
import tw.cute.wlbot.review.ReviewService;
import tw.cute.wlbot.telegram.CallbackDataCodec;
import tw.cute.wlbot.telegram.ErrorReporter;
import tw.cute.wlbot.telegram.PendingNotifier;
import tw.cute.wlbot.telegram.TelegramBotRuntime;
import tw.cute.wlbot.telegram.TelegramHttpGateway;
import tw.cute.wlbot.telegram.TelegramPollingWorker;
import tw.cute.wlbot.telegram.TelegramReviewProcessor;

public final class WhitelistBotPlugin extends JavaPlugin {
    private static final Duration NOTIFICATION_INTERVAL = Duration.ofSeconds(2);

    private SqliteQueueRepository repository;
    private TelegramBotRuntime botRuntime;
    private PluginSettings activeSettings;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getServer().hasWhitelist()) {
            disable("伺服器尚未啟用原生白名單，WLBotPlugin 已停用。", null);
            return;
        }
        registerCommands();

        try {
            initialize(loadSettings(getConfig()));
            getLogger().info("WLBotPlugin 已啟用，等待白名單申請。");
        } catch (RuntimeException | SQLException exception) {
            disable("WLBotPlugin 初始化失敗：" + exception.getMessage(), exception);
        }
    }

    @Override
    public void onDisable() {
        teardown();
    }

    private void teardown() {
        TelegramBotRuntime currentRuntime = botRuntime;
        botRuntime = null;
        if (currentRuntime != null) {
            currentRuntime.close();
        }

        HandlerList.unregisterAll(this);

        SqliteQueueRepository currentRepository = repository;
        repository = null;
        if (currentRepository != null) {
            currentRepository.close();
        }
    }

    private void initialize(PluginSettings settings) throws SQLException {
        SqliteQueueRepository stale = repository;
        repository = null;
        if (stale != null) {
            stale.close();
        }

        Path databasePath = getDataFolder().toPath().resolve(settings.databaseFile()).normalize();
        SqliteQueueRepository openedRepository = SqliteQueueRepository.open(databasePath);
        repository = openedRepository;

        ErrorReporter errors = (message, error) -> getLogger().log(Level.WARNING, message, error);
        CallbackDataCodec codec = new CallbackDataCodec();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        TelegramHttpGateway telegram = new TelegramHttpGateway(
                httpClient,
                settings.apiBaseUrl(),
                settings.botToken(),
                settings.adminChatId(),
                codec);
        ScheduledWhitelistGateway whitelist = new ScheduledWhitelistGateway(
                task -> getServer().getGlobalRegionScheduler().execute(this, task),
                player -> getServer().getOfflinePlayer(player.uuid()).setWhitelisted(true));
        ReviewService reviewService = new ReviewService(
                openedRepository,
                whitelist,
                settings.adminUserId(),
                settings.adminChatId(),
                settings.rejectionCooldown(),
                Clock.systemUTC());
        TelegramReviewProcessor reviewProcessor = new TelegramReviewProcessor(
                reviewService, telegram, codec, errors, Duration.ofSeconds(10));
        TelegramPollingWorker pollingWorker = new TelegramPollingWorker(
                telegram, reviewProcessor, errors);
        PendingNotifier notifier = new PendingNotifier(openedRepository, telegram, errors);
        TelegramBotRuntime runtime = new TelegramBotRuntime(
                notifier::dispatch,
                pollingWorker,
                settings.pollingTimeoutSeconds(),
                NOTIFICATION_INTERVAL,
                errors);
        botRuntime = runtime;

        LoginRequestService loginRequests = new LoginRequestService(
                openedRepository, new QueueMessageFormatter());
        getServer().getPluginManager().registerEvents(
                new WhitelistLoginListener(loginRequests, Clock.systemUTC(), getLogger()), this);
        runtime.start();
        activeSettings = settings;
    }

    private void registerCommands() {
        PluginCommand command = getCommand("wl-bot");
        if (command == null) {
            getLogger().warning("plugin.yml 缺少 wl-bot 指令宣告，遊戲內指令未啟用。");
            return;
        }
        WlBotCommandService service = new WlBotCommandService(
                this::reloadConfiguration,
                this::whitelistedPlayerNames,
                this::removeFromWhitelist);
        WlBotCommandExecutor executor = new WlBotCommandExecutor(service);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private String reloadConfiguration() {
        reloadConfig();
        PluginSettings fresh;
        try {
            fresh = loadSettings(getConfig());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("config 設定無效：" + exception.getMessage(), exception);
        }

        PluginSettings previous = activeSettings;
        if (previous == null) {
            throw new IllegalStateException("外掛尚未完成初始化，無法重載設定");
        }

        teardown();
        try {
            initialize(fresh);
        } catch (RuntimeException | SQLException failure) {
            getLogger().log(Level.WARNING, "套用新設定失敗，嘗試回復舊設定。", failure);
            try {
                initialize(previous);
            } catch (RuntimeException | SQLException rollbackFailure) {
                disable("WLBotPlugin 回復舊設定也失敗，外掛已停用。", rollbackFailure);
                throw new IllegalStateException(
                        "回復舊設定失敗，外掛已停用，請查看伺服器記錄。", rollbackFailure);
            }
            throw new IllegalStateException(
                    "新設定初始化失敗（已回復舊設定繼續運行）：" + failure.getMessage(), failure);
        }
        return "設定已套用";
    }

    private List<String> whitelistedPlayerNames() {
        return getServer().getWhitelistedPlayers().stream()
                .map(OfflinePlayer::getName)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void removeFromWhitelist(String playerName) {
        OfflinePlayer target = getServer().getWhitelistedPlayers().stream()
                .filter(player -> playerName.equalsIgnoreCase(player.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(playerName + " 已不在白名單中"));
        getServer().getGlobalRegionScheduler().execute(this, () -> target.setWhitelisted(false));
    }

    private PluginSettings loadSettings(FileConfiguration config) {
        String token = config.getString("telegram.bot-token", "");
        if (token.equals("CHANGE_ME")) {
            throw new IllegalArgumentException("請先在 config.yml 設定 telegram.bot-token");
        }
        URI apiBaseUrl = parseApiBaseUrl(config.getString("telegram.api-url"));
        String databaseFile = config.getString("database.file", "whitelist-queue.db");
        try {
            return new PluginSettings(
                    token,
                    apiBaseUrl,
                    config.getLong("telegram.admin-user-id"),
                    config.getLong("telegram.admin-chat-id"),
                    Duration.ofSeconds(config.getLong("review.rejection-cooldown-seconds", 15L)),
                    Path.of(databaseFile),
                    config.getInt("polling.timeout-seconds", 20));
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException("database.file 不是有效路徑", exception);
        }
    }

    private static URI parseApiBaseUrl(String rawUrl) {
        String candidate = (rawUrl == null || rawUrl.isBlank())
                ? PluginSettings.DEFAULT_API_BASE.toString()
                : rawUrl.trim();
        try {
            return URI.create(candidate);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("telegram.api-url 不是有效的 URI", exception);
        }
    }

    private void disable(String message, Throwable error) {
        if (error == null) {
            getLogger().severe(message);
        } else {
            getLogger().log(Level.SEVERE, message, error);
        }
        getServer().getPluginManager().disablePlugin(this);
    }
}
