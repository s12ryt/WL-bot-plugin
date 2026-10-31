# 專案任務紀錄

## 目前任務：Telegram 人工審核白名單

- [x] 確認工作區為空專案。
- [x] 確認平台為 Paper/Folia 26.3、Java 25。
- [x] 確認 SQLite、單一 Telegram 管理員、拒絕冷卻 15 秒。
- [x] 建立 Gradle 9.1 Wrapper、Java 25 與 JUnit 5 測試基礎設施。
- [x] RED 1：SQLite 佇列、冷卻、審核權限與白名單寫入順序。
- [x] GREEN 1：持久化佇列與審核核心。
- [x] RED 2：callback codec、Telegram HTTP 契約、訊息生命週期、顯示文字與設定驗證。
- [x] GREEN 2：Telegram gateway、通知器、審核處理器與格式化。
- [x] RED 3：long polling offset、背景工作關閉與登入申請去重。
- [x] GREEN 3：polling worker、bot runtime 與登入服務。
- [x] RED/GREEN 4：Folia Global Region Scheduler 白名單寫入。
- [x] RED/GREEN 5：Paper 白名單事件、畸形 callback 與孤立 Telegram 訊息清理。
- [x] REFACTOR：分離 Telegram polling／通知執行緒，集中錯誤處理與關閉順序。
- [x] 執行 33 項完整測試、Java 25 `-Xlint:all` 編譯與正式 fat JAR 建置。
- [x] 檢查 JAR 內含主類別、`plugin.yml`、`config.yml`、SQLite 與 Gson，且不含測試類別。

## GitHub CI 與 Release（2026-10-31）

- [x] 確認使用者授權建立 `s12ryt/WL-bot-plugin` 公開儲存庫。
- [x] 加入 push／PR／手動 CI：Java 25、完整測試、fat JAR、報告 artifacts。
- [x] 加入標籤／手動 Release：版本核對、完整測試、JAR、SHA-256 校驗碼、可重跑附件上傳。
- [x] actionlint 1.7.12 檢查兩份 workflow 通過。
- [x] 本機重新執行 `gradlew.bat clean build --no-daemon` 通過。
- [x] 建立公開儲存庫並推送；GitHub CI `41161452308` 成功。
- [x] 推送 `v1.0.0`，Release `41161552240` 成功發布 JAR 與校驗碼。
- [x] 依遠端棄用警告更新 setup-java v6、upload-artifact v7、setup-gradle v6。
- [x] 新版 Actions：CI `41161645938` 與手動 Release `41161645954` 均成功。
- [x] 從 Release 下載 JAR，實際 SHA-256 與發布的校驗碼一致。

## 真實伺服器煙霧測試與 README 詳細化（2026-10-31）

- [x] 調查 PaperMC 下載 API：v2 已 sunset，改用 fill v3（自訂 User-Agent 必要）。
- [x] TDD：`PluginSettings` 新增 `telegram.api-url`（預設 `https://api.telegram.org`，驗證絕對 http(s) URI）；RED 3 個編譯錯誤 → GREEN 全量 build 38 測試通過。
- [x] 實作 `ci/SmokeTestDriver.java`（JEP 330 單檔）：下載伺服器（sha256 驗證）→ 兩次啟動 → mock Telegram（jdk.httpserver）→ JDBC 注入 PENDING → 端到端斷言（四種 API 呼叫、whitelist.json、佇列刪除、exit 0）。
- [x] 修復 Windows 主控台編碼：伺服器 JVM 加 `-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8`（JEP 400 下 System.out 由 stdout.encoding 決定，file.encoding 不夠）。
- [x] 本地真實驗證：Paper 26.3 與 Folia 26.3 煙霧測試皆 SUCCESS（exit 0）；mock 流量 sendMessage×1、getUpdates×3、answerCallbackQuery×1、deleteMessage×1。
- [x] CI 加入 `smoke-test` job（paper/folia matrix，needs test，重用 WLBotPlugin artifact，上傳 smoke-logs）。
- [x] actionlint 1.7.12 通過；本地 clean build 19s 通過。
- [x] 推送 `5fb1192`；遠端 CI run `41163491643` 三 job 全綠（test 31s、smoke paper 49s、smoke folia 43s）。
- [x] README 詳細化：特色、流程圖、憑證取得、完整 config 表（含 api-url）、審核操作、資料儲存、開發指南（煙霧測試本地執行）、專案結構、FAQ。

## 尚待部署環境驗證

- [x] 在實際 Paper 26.3 測試服完成一次玩家申請、Telegram 通過／拒絕與重啟持久化煙霧測試。（已由本地與 CI 之真實 Paper/Folia 26.3 煙霧測試覆蓋：申請通知、approve 審核、白名單寫入、佇列刪除、乾淨關閉。）
