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
- 新版 Actions 驗證完成：CI 41161645938、手動 Release 41161645954 均成功；確認既有 Release 附件可更新，無 Node 20 棄用警告。
- 下載 Release JAR 重新計算 SHA-256：`4857316224d1139023a03bf2491374df8c2dedaa57d96e537a4da73dae90ba65`，與 SHA256SUMS.txt 一致。

## 2026-10-31（煙霧測試與 README）

- 實測 PaperMC 下載 API：`api.papermc.io/v2` 已 sunset；新端點 `https://fill.papermc.io/v3/projects/{paper|folia}/versions/26.3/builds/latest` 必須帶自訂 User-Agent，下載網域 fill-data.papermc.io，`.downloads."server:default"` 含 url 與 sha256。
- TDD 加入 `telegram.api-url`（PluginSettings 第 2 參數 URI apiBaseUrl + DEFAULT_API_BASE；WhitelistBotPlugin.parseApiBaseUrl；config.yml 預設值）：RED 3 編譯錯誤 → GREEN 38 測試全過。
- 新增 `ci/SmokeTestDriver.java`（約 600 行 JEP 330 單檔）：常數見檔頭；ServerProcess 以 virtual thread 讀主控台寫 `run/logs/smoke-<tag>-console.log` 並掃 `Done (`；MockTelegram 監聽 127.0.0.1:18080（sendMessage→message_id 777；getUpdates 在 sendMessage 後回 approve callback update_id 1000，之後回空並 sleep 2s 節流；全部請求記 mock-requests.jsonl）。
- 關鍵修復：Windows 上 Paper 主控台與 log4j 輸出中文皆亂碼——伺服器 JVM 需加 `-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8`（JEP 400：System.out 由 stdout.encoding 決定，僅 file.encoding 無效）。
- 修正 driver race：reader lambda 原經 holder[0] 取容器（start 後才賦值會 NPE），改為先建局部 AtomicBoolean/List 再傳入 ServerProcess。
- 本地驗證：paper 與 folia 皆 `SUCCESS ... (exit 0)`；mock 流量 sendMessage×1、getUpdates×3、answerCallbackQuery×1、deleteMessage×1；whitelist.json 含測試 UUID、佇列列已刪、伺服器 exit 0。
- ci.yml 加 `smoke-test` job（needs test、matrix paper/folia、download-artifact WLBotPlugin→build/libs、always 上傳 smoke-logs-<flavor>）；actionlint 1.7.12 無發現；clean build 19s。
- 提交 `5fb1192` 推送；遠端 CI run `41163491643` 全綠：test 31s、smoke paper 49s、smoke folia 43s，artifacts 含 smoke-logs-paper/folia。
- README 詳細化（29 行→約 150 行）：特色、流程圖、BotFather/userinfobot 憑證取得、config 全表（含 api-url 與驗證行為）、審核操作、SQLite schema、開發（單元測試＋煙霧測試本地執行指令）、專案結構、CI/Release 更新、FAQ；FAQ 重啟行為依 findUnnotified（message_id IS NULL 才重送）修正措辭。
