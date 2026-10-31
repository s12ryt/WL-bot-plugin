# 操作紀錄

## 2026-10-31

- 讀取工作區：初始為空，沒有既有程式、建置設定或 agent 文件。
- 使用者確認 Paper/Folia 26.3、SQLite、單一 Telegram 管理員、拒絕後冷卻 15 秒。
- 查閱 Paper 官方文件：26.3 使用 Java 25、`paper-api:26.3.build.+`、`api-version: '26.3'`，Folia 需 `folia-supported: true`。
- 查閱 Telegram Bot API：採用 `getUpdates` long polling、inline keyboard callback、`answerCallbackQuery` 與 `deleteMessage`。
- 環境檢查：Java 25.0.2 可用；未安裝全域 Gradle，專案需提供 Wrapper。
- 產生 Gradle 9.1 Wrapper，加入 Paper API 26.3、sqlite-jdbc 3.50.3.0、Gson 2.13.2 與 JUnit 5.13.4。
- 建立 SQLite 佇列：待審去重、動態順位、15 秒冷卻、Telegram message ID 與跨重啟恢復。
- 建立 Telegram Bot API gateway、callback 授權審核、未通知重試、完成後刪訊息與 long polling offset。
- 建立獨立通知與 polling 背景執行緒；外掛停用時先中斷工作，再關閉 SQLite。
- 白名單通過操作經 Paper/Folia Global Region Scheduler 執行。
- 登入薄層改用 Paper 26.3 `ProfileWhitelistVerifyEvent`，避免已棄用的 `PlayerLoginEvent`，並只攔截真正會被原生白名單拒絕的玩家。
- 第五輪回歸修復：畸形 Telegram callback 轉為無 callback update，使 offset 可前進；附加 message ID 失敗時刪除已送出的孤立訊息，避免下一輪重複通知。
- `gradlew.bat clean build --no-daemon` 通過：13 個測試類別、33 項測試、0 失敗；Java 25 `-Xlint:all` 無警告。
- 產物 `build/libs/wl-bot-plugin-1.0.0.jar` 約 14.7 MB，已確認包含外掛資源、SQLite 與 Gson，未包含測試類別。
- LSP 診斷未執行：本機沒有 `jdtls`；以乾淨編譯、`-Xlint:all`、完整測試與建置替代。
- 使用者要求 GitHub CI 及 JAR Releases workflow，確認授權建立公開 `s12ryt/WL-bot-plugin` 儲存庫。
- 新增 CI 與 Release workflow；兩者使用 Java 25 與 Gradle Wrapper，發布前核對標籤和兩處版本。
- actionlint 1.7.12 靜態檢查通過；本機完整 clean build 再次通過。YAML 靜態設定依 TDD 例外以靜態分析及遠端執行替代 RED 測試，未新增外掛程式行為。
- 已建立並推送 https://github.com/s12ryt/WL-bot-plugin（公開）；CI run 41161452308 成功，Release run 41161552240 成功發布 v1.0.0 JAR（14,703,982 bytes）及 SHA256SUMS.txt。
- 首次遠端執行發現 Action Node 20 棄用警告，依各官方 release 更新 setup-java v6、upload-artifact v7、gradle/actions/setup-gradle v6。
