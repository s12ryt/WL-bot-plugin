package tw.cute.wlbot.queue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import tw.cute.wlbot.domain.PlayerIdentity;

public final class SqliteQueueRepository implements QueueRepository {
    private static final String PENDING = "PENDING";
    private static final String COOLDOWN = "COOLDOWN";

    private final Connection connection;

    private SqliteQueueRepository(Connection connection) {
        this.connection = connection;
    }

    public static SqliteQueueRepository open(Path databasePath) throws SQLException {
        Objects.requireNonNull(databasePath, "databasePath");
        try {
            Path parent = databasePath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (java.io.IOException exception) {
            throw new SQLException("Unable to create database directory", exception);
        }

        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
        var repository = new SqliteQueueRepository(connection);
        try {
            repository.configure();
            repository.createSchema();
            return repository;
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
    }

    private void configure() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA busy_timeout=5000");
        }
    }

    private void createSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS whitelist_requests (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_uuid TEXT NOT NULL UNIQUE,
                        player_name TEXT NOT NULL,
                        state TEXT NOT NULL CHECK (state IN ('PENDING', 'COOLDOWN')),
                        requested_at INTEGER NOT NULL,
                        cooldown_until INTEGER,
                        telegram_message_id INTEGER
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_whitelist_requests_pending
                    ON whitelist_requests(state, id)
                    """);
        }
    }

    @Override
    public synchronized QueueDecision request(PlayerIdentity player, Instant now) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(now, "now");
        return inTransaction(() -> {
            ExistingRequest existing = findExisting(player.uuid());
            if (existing != null && PENDING.equals(existing.state())) {
                updatePlayerName(player);
                PendingApplication application = new PendingApplication(
                        existing.sequence(),
                        player,
                        Instant.ofEpochMilli(existing.requestedAt()),
                        optionalLong(existing.telegramMessageId()));
                return pendingDecision(application, false);
            }
            if (existing != null && existing.cooldownUntil() != null) {
                Instant cooldownUntil = Instant.ofEpochMilli(existing.cooldownUntil());
                if (now.isBefore(cooldownUntil)) {
                    return new CooldownDecision(Duration.between(now, cooldownUntil));
                }
                delete(player.uuid());
            }

            PendingApplication application = insertPending(player, now);
            return pendingDecision(application, true);
        });
    }

    @Override
    public synchronized Optional<PendingApplication> findPending(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, player_uuid, player_name, requested_at, telegram_message_id
                FROM whitelist_requests
                WHERE player_uuid = ? AND state = 'PENDING'
                """)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(readPending(rows)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw storageFailure("Unable to find pending request", exception);
        }
    }

    @Override
    public synchronized List<PendingApplication> findUnnotified(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be positive");
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, player_uuid, player_name, requested_at, telegram_message_id
                FROM whitelist_requests
                WHERE state = 'PENDING' AND telegram_message_id IS NULL
                ORDER BY id
                LIMIT ?
                """)) {
            statement.setInt(1, limit);
            try (ResultSet rows = statement.executeQuery()) {
                List<PendingApplication> applications = new ArrayList<>();
                while (rows.next()) {
                    applications.add(readPending(rows));
                }
                return List.copyOf(applications);
            }
        } catch (SQLException exception) {
            throw storageFailure("Unable to list unnotified requests", exception);
        }
    }

    @Override
    public synchronized boolean attachTelegramMessage(UUID playerUuid, long messageId) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        if (messageId < 1) {
            throw new IllegalArgumentException("Message ID must be positive");
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE whitelist_requests
                SET telegram_message_id = ?
                WHERE player_uuid = ? AND state = 'PENDING' AND telegram_message_id IS NULL
                """)) {
            statement.setLong(1, messageId);
            statement.setString(2, playerUuid.toString());
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw storageFailure("Unable to attach Telegram message", exception);
        }
    }

    @Override
    public synchronized boolean approve(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM whitelist_requests
                WHERE player_uuid = ? AND state = 'PENDING'
                """)) {
            statement.setString(1, playerUuid.toString());
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw storageFailure("Unable to approve request", exception);
        }
    }

    @Override
    public synchronized boolean reject(UUID playerUuid, Instant cooldownUntil) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(cooldownUntil, "cooldownUntil");
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE whitelist_requests
                SET state = 'COOLDOWN', cooldown_until = ?, telegram_message_id = NULL
                WHERE player_uuid = ? AND state = 'PENDING'
                """)) {
            statement.setLong(1, cooldownUntil.toEpochMilli());
            statement.setString(2, playerUuid.toString());
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw storageFailure("Unable to reject request", exception);
        }
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException exception) {
            throw storageFailure("Unable to close queue database", exception);
        }
    }

    private ExistingRequest findExisting(UUID playerUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, state, requested_at, cooldown_until, telegram_message_id
                FROM whitelist_requests
                WHERE player_uuid = ?
                """)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return new ExistingRequest(
                        rows.getLong("id"),
                        rows.getString("state"),
                        rows.getLong("requested_at"),
                        nullableLong(rows, "cooldown_until"),
                        nullableLong(rows, "telegram_message_id"));
            }
        }
    }

    private void updatePlayerName(PlayerIdentity player) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE whitelist_requests SET player_name = ? WHERE player_uuid = ?
                """)) {
            statement.setString(1, player.name());
            statement.setString(2, player.uuid().toString());
            statement.executeUpdate();
        }
    }

    private PendingApplication insertPending(PlayerIdentity player, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO whitelist_requests (
                    player_uuid, player_name, state, requested_at, cooldown_until, telegram_message_id
                ) VALUES (?, ?, 'PENDING', ?, NULL, NULL)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, player.uuid().toString());
            statement.setString(2, player.name());
            statement.setLong(3, now.toEpochMilli());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("SQLite did not return a request sequence");
                }
                return new PendingApplication(
                        keys.getLong(1), player, now, OptionalLong.empty());
            }
        }
    }

    private PendingDecision pendingDecision(PendingApplication application, boolean created) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT
                    SUM(CASE WHEN id <= ? THEN 1 ELSE 0 END) AS position,
                    COUNT(*) AS total
                FROM whitelist_requests
                WHERE state = 'PENDING'
                """)) {
            statement.setLong(1, application.sequence());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("Unable to calculate queue position");
                }
                return new PendingDecision(
                        application,
                        rows.getInt("position"),
                        rows.getInt("total"),
                        created);
            }
        }
    }

    private void delete(UUID playerUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM whitelist_requests WHERE player_uuid = ?
                """)) {
            statement.setString(1, playerUuid.toString());
            statement.executeUpdate();
        }
    }

    private PendingApplication readPending(ResultSet rows) throws SQLException {
        return new PendingApplication(
                rows.getLong("id"),
                new PlayerIdentity(
                        UUID.fromString(rows.getString("player_uuid")),
                        rows.getString("player_name")),
                Instant.ofEpochMilli(rows.getLong("requested_at")),
                optionalLong(nullableLong(rows, "telegram_message_id")));
    }

    private static Long nullableLong(ResultSet rows, String column) throws SQLException {
        long value = rows.getLong(column);
        return rows.wasNull() ? null : value;
    }

    private static OptionalLong optionalLong(Long value) {
        return value == null ? OptionalLong.empty() : OptionalLong.of(value);
    }

    private <T> T inTransaction(SqlOperation<T> operation) {
        try {
            connection.setAutoCommit(false);
            try {
                T result = operation.run();
                connection.commit();
                return result;
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (QueueStorageException exception) {
            throw exception;
        } catch (Exception exception) {
            throw storageFailure("Queue transaction failed", exception);
        }
    }

    private static QueueStorageException storageFailure(String message, Throwable cause) {
        return new QueueStorageException(message, cause);
    }

    @FunctionalInterface
    private interface SqlOperation<T> {
        T run() throws Exception;
    }

    private record ExistingRequest(
            long sequence,
            String state,
            long requestedAt,
            Long cooldownUntil,
            Long telegramMessageId) {}
}
