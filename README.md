# WLBotPlugin

Paper / Folia 26.3 白名單審核外掛。未在原生白名單內的玩家連線時，外掛會建立待審申請，並將含有「✅ 通過／❌ 拒絕」按鈕的訊息傳送至指定 Telegram 私人聊天，由管理員手動審核。通過後玩家自動寫入伺服器原生白名單（`whitelist.json`）。

## 特色

- **人工審核**：申請送到 Telegram 私人聊天，管理員點按鈕即可通過或拒絕，不必進伺服器下指令。
- **原生白名單整合**：審核結果直接寫入 Paper/Folia 原生白名單，與 `whitelist` 指令、`whitelist.json` 完全互通。
- **Folia 支援**：所有對伺服器狀態的修改都經由正確的排程器（`GlobalRegionScheduler`）執行，可直接跑在 Folia 多執行緒區域架上。
- **持久化佇列**：申請佇列存於 SQLite（WAL 模式），伺服器重啟後未審核的申請不會遺失。
- **拒絕冷卻**：被拒絕的玩家預設 15 秒後才能再次申請，防止刷屏。
- **單檔 fat JAR**：SQLite JDBC 與 JSON 解析已打包進 JAR，伺服器端無需另外安裝依賴。

## 運作流程

```
玩家連線（不在白名單）
        │
        ▼
ProfileWhitelistVerifyEvent（外掛攔截）
        │  建立申請（SQLite，狀態 PENDING）
        ▼
PendingNotifier（每 2 秒）──► Telegram：申請訊息＋[✅ 通過] [❌ 拒絕] 按鈕
        │
        ▼
管理員點按鈕 ──► Telegram Polling 收到 callback_query
        │
        ├─ ✅ 通過 ─► 寫入原生白名單 ─► 刪除佇列列 ─► 回覆「已通過」並刪除申請訊息
        └─ ❌ 拒絕 ─► 佇列列改為 COOLDOWN（預設 15 秒）──► 回覆「已拒絕」並刪除申請訊息
```

只有設定的管理員 Telegram 帳號能在該私人聊天中審核；其他人按按鈕只會收到「你沒有審核權限。」。

## 環境需求

- Paper 或 Folia 26.3
- Java 25以上
- 伺服器原生白名單已啟用（`white-list=true`；外掛啟動時會檢查，未啟用則停用自身）
- `online-mode=true`（申請以玩家 UUID 識別）

## 安裝

1. 執行 `gradlew.bat clean build`（或直接從 [Releases](https://github.com/s12ryt/WL-bot-plugin/releases) 下載 JAR）。
2. 將 `build/libs/wl-bot-plugin-1.0.0.jar` 放入伺服器的 `plugins` 目錄。
3. 確認 `server.properties` 中 `white-list=true`，啟動一次伺服器以產生 `plugins/WLBotPlugin/config.yml`。
4. 停止伺服器，填入 Bot token、管理員 Telegram 使用者 ID 與私人聊天 ID（見下方設定說明）。
5. 再次啟動伺服器。Log 出現「WLBotPlugin 已啟用，等待白名單申請。」即安裝完成。

## 取得 Telegram 憑證

| 設定值 | 取得方式 |
| --- | --- |
| `bot-token` | 在 Telegram 與 [@BotFather](https://t.me/BotFather) 對話，`/newbot` 建立機器人後取得的 token（格式 `123456789:AA...`）。 |
| `admin-user-id` | 你的 Telegram 使用者數字 ID。可向 [@userinfobot](https://t.me/userinfobot) 傳任意訊息查詢。 |
| `admin-chat-id` | 你與機器人私聊的聊天 ID。先向你的機器人傳一句話，再從 Bot API 的 `getUpdates`（或 [@userinfobot](https://t.me/userinfobot)）查看，私人聊天的 ID 與你的使用者 ID 相同。 |

## config.yml 設定說明

設定檔位於 `plugins/WLBotPlugin/config.yml`：

```yaml
telegram:
  bot-token: '123456789:AA...'          # BotFather 發給你的 token
  api-url: 'https://api.telegram.org'   # Telegram Bot API 位址（一般保持預設）
  admin-user-id: 123456789              # 有審核權限的使用者 ID
  admin-chat-id: 123456789              # 接收申請訊息的私人聊天 ID

review:
  rejection-cooldown-seconds: 15        # 被拒絕後可再次申請的等待秒數

database:
  file: 'whitelist-queue.db'            # SQLite 檔名（相對於外掛資料夾）

polling:
  timeout-seconds: 20                   # Telegram long-polling 逾時（1–50 秒）
```

- `telegram.api-url`：僅在需要自建 Bot API proxy 或測試時修改；必須是絕對的 `http(s)` URL。外掛的煙霧測試就是靠這個選項把 API 指向本機 mock。
- 設定不合法（token 未改、ID 為 0、路徑含 `..` 等）時外掛會在啟動時停用自身並在 log 說明原因。

## 審核操作

- 收到申請訊息後，直接點「✅ 通過」或「❌ 拒絕」。
- 通過：玩家立即寫入白名單，訊息刪除，按鈕回覆「已通過，玩家已加入白名單。」。玩家需要重新連線一次才能進入伺服器。
- 拒絕：進入冷卻（預設 15 秒，可用 `review.rejection-cooldown-seconds` 調整），期間無法再次申請。
- Telegram 刪除訊息受 Bot API 的 48 小時限制；刪除失敗不會回滾已完成的審核結果。

## 遊戲內指令

外掛提供 `/wl-bot` 管理指令，需要 `wlbot.admin` 權限（預設僅 OP）。兩個子指令都支援 Tab 補全：

| 指令 | 說明 |
| --- | --- |
| `/wl-bot reload` | 重新讀取 `config.yml` 並重建機器人執行環境（SQLite 佇列保留）。新設定驗證或初始化失敗時，自動回復舊設定繼續運行，並在回覆與 log 中說明原因。 |
| `/wl-bot del <玩家名>` | 將玩家移出白名單。玩家名不分大小寫，比對對象為目前白名單中的玩家。 |

- 補全行為：輸入 `/wl-bot ` 後補全子命令（`del` / `reload`）；輸入 `/wl-bot del ` 後補全目前白名單中的玩家名（不分大小寫前綴比對、依字母排序）。
- `del` 的白名單寫入與移除都經 `GlobalRegionScheduler` 執行，Folia 安全；移除後該玩家再次連線會回到申請流程。
- 權限 `wlbot.admin` 已在 `plugin.yml` 宣告，可透過權限外掛（如 LuckPerms）授予其他管理者。

## 資料儲存

- 佇列存放於 `plugins/WLBotPlugin/whitelist-queue.db`（SQLite，WAL 模式）。
- 資料表 `whitelist_requests`：`player_uuid`（唯一）、`player_name`、`state`（`PENDING` / `COOLDOWN`）、`requested_at`、`cooldown_until`、`telegram_message_id`。
- 申請通過後該列即刪除；拒絕保留至冷卻結束。玩家白名單狀態本身由伺服器原生的 `whitelist.json` 管理，兩者獨立。

## 開發

### 建置與單元測試

```bash
./gradlew clean build --no-daemon
```

產出 fat JAR：`build/libs/wl-bot-plugin-1.0.0.jar`。測試報告在 `build/reports/tests/test/`。

### 端到端煙霧測試（真實伺服器 + mock Telegram）

`ci/SmokeTestDriver.java` 是單檔測試驅動程式（JEP 330，不需編譯進 JAR），會：

1. 經 [fill v3 API](https://docs.papermc.io/misc/downloads-api/) 下載最新的 Paper 或 Folia 26.3 伺服器（含 sha256 驗證）；
2. 在 `build/smoke/<flavor>/run` 建立乾淨伺服器環境（`white-list=true`、flat 世界、高位埠），安裝外掛 JAR；
3. 第一次啟動：斷言未設定 token 時外掛正確拒絕啟用並產生預設 `config.yml`；
4. 將 `telegram.api-url` 指向本機 mock Bot API，直接以 JDBC 注入一筆 PENDING 申請；
5. 第二次啟動：斷言完整流程——mock 收到 `sendMessage`（申請通知）、`getUpdates`、`answerCallbackQuery`、`deleteMessage`；玩家 UUID 出現在 `whitelist.json`；佇列中該玩家列已刪除；伺服器乾淨退出（exit 0）。

本地執行（Windows / Linux 皆可）：

```bash
./gradlew clean build --no-daemon
java --add-modules jdk.httpserver -cp build/libs/wl-bot-plugin-1.0.0.jar ci/SmokeTestDriver.java paper
java --add-modules jdk.httpserver -cp build/libs/wl-bot-plugin-1.0.0.jar ci/SmokeTestDriver.java folia
```

失敗時伺服器 log 與 mock 請求記錄（`build/smoke/<flavor>/`）會保留供排查；成功則印出 `SUCCESS` 並以 exit 0 結束。

### 專案結構

```
src/main/java/tw/cute/wlbot/
├── WhitelistBotPlugin        # 主類：設定載入、元件裝配、生命週期、/wl-bot 指令接線
├── config/PluginSettings     # 設定值與驗證（record）
├── command/                  # /wl-bot 指令：服務層邏輯＋Bukkit executor/TabCompleter
├── WhitelistLoginListener    # 攔截 ProfileWhitelistVerifyEvent，建立申請
├── PendingNotifier           # 將未通知的申請推送至 Telegram
├── TelegramPollingWorker     # Bot API long-polling，收取審核按鈕
├── TelegramReviewProcessor   # 解析 callback，執行審核授權與回覆
├── ReviewService             # 審核核心：授權判斷、白名單寫入、佇列狀態轉移
├── ScheduledWhitelistGateway # 經 GlobalRegionScheduler 寫白名單（Folia 安全）
├── TelegramHttpGateway       # Bot API HTTP 用戶端
├── TelegramBotRuntime        # 通知與輪詢的背景執行緒
├── CallbackDataCodec         # callback data 編解碼（approve:<uuid> / reject:<uuid>）
└── SqliteQueueRepository     # SQLite 佇列存取
```

## GitHub CI 與 Releases

- `CI`：每次 push、pull request 或手動執行時：
  - **Java 25 tests and build**：使用 Temurin Java 25 執行完整單元測試與 fat JAR 建置。測試報告及成功建置的 JAR 會保存為 Actions artifacts，保留 14 天。
  - **Smoke test**（paper / folia matrix）：下載建置出的 JAR，在 ubuntu runner 上啟動真實的 Paper 與 Folia 26.3 伺服器跑上述端到端煙霧測試，驗證外掛在兩種伺服器上的完整審核流程。伺服器 log 與 mock 記錄保存為 `smoke-logs-*` artifacts。
- `Release`：推送 `vMAJOR.MINOR.PATCH` 標籤時，自動執行完整測試與建置，建立 GitHub Release，附上 JAR 與 `SHA256SUMS.txt`。也可在 Actions 手動執行，輸入已存在的標籤；重跑會更新同一 Release 的附件。
- 發布版本前，必須同步修改 `build.gradle.kts` 與 `src/main/resources/plugin.yml` 的版本；例如 `1.0.0` 對應 `v1.0.0`。版本不符會停止發布。
- Workflows 使用內建 `GITHUB_TOKEN`，不需要額外設定 Telegram 憑證或發布用 secret（煙霧測試使用本機 mock，也不需要真實 token）。

發布範例：先提交並推送版本變更，再執行 `git tag v1.0.0` 與 `git push origin v1.0.0`。

## 常見問題

- **外掛啟動後馬上停用？** 檢查 log：常見原因為 `white-list=false`、token 仍是 `CHANGE_ME`、或管理員 ID 未填。
- **訊息按鈕沒反應？** 確認 `admin-user-id` 是你的使用者 ID（不是使用者名稱），且你是透過 `admin-chat-id` 對應的私人聊天與機器人互動。
- **玩家通過後還是進不來？** 玩家需要斷線重連一次；若仍有問題，在伺服器主控台用 `whitelist list` 確認 UUID 是否已列入。
- **伺服器重啟後申請還在嗎？** 在。PENDING 申請存於 SQLite，重啟後不會遺失：尚未通知的申請會重新送出通知；已通知但尚未審核的申請，按鈕仍在原訊息上，管理員可直接審核。
