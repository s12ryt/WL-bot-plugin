# WLBotPlugin

Paper / Folia 26.3 白名單審核外掛。未在原生白名單內的玩家連線時，外掛會建立待審申請並將含有「通過／拒絕」按鈕的訊息傳送至指定 Telegram 私人聊天。

## 環境

- Paper 或 Folia 26.3
- Java 25
- 伺服器原生白名單必須已啟用
- `online-mode=true`

## 安裝

1. 執行 `gradlew.bat clean build`。
2. 將 `build/libs/wl-bot-plugin-1.0.0.jar` 放入伺服器的 `plugins` 目錄。
3. 啟動一次伺服器以產生 `plugins/WLBotPlugin/config.yml`。
4. 停止伺服器，填入 Bot token、管理員 Telegram 使用者 ID 與私人聊天 ID。
5. 再次啟動伺服器。

外掛使用 SQLite 保存佇列。玩家被拒絕後 15 秒可重新申請；通過後需自行重新連線。Telegram 訊息刪除受 Bot API 的 48 小時限制，刪除失敗不會回滾已完成的審核。

## GitHub CI 與 Releases

- `CI`：每次 push、pull request 或手動執行時，使用 Temurin Java 25 執行完整測試與 fat JAR 建置。測試報告及成功建置的 JAR 會保存為 Actions artifacts，保留 14 天。
- `Release`：推送 `vMAJOR.MINOR.PATCH` 標籤時，自動執行完整測試與建置，建立 GitHub Release，附上 JAR 與 `SHA256SUMS.txt`。也可在 Actions 手動執行，輸入已存在的標籤；重跑會更新同一 Release 的附件。
- 發布版本前，必須同步修改 `build.gradle.kts` 與 `src/main/resources/plugin.yml` 的版本；例如 `1.0.0` 對應 `v1.0.0`。版本不符會停止發布。
- Workflows 使用內建 `GITHUB_TOKEN`，不需要額外設定 Telegram 憑證或發布用 secret。

發布範例：先提交並推送版本變更，再執行 `git tag v1.0.0` 與 `git push origin v1.0.0`。
