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

## 尚待部署環境驗證

## GitHub CI 與 Release（2026-10-31）

- [x] 確認使用者授權建立 `s12ryt/WL-bot-plugin` 公開儲存庫。
- [x] 加入 push／PR／手動 CI：Java 25、完整測試、fat JAR、報告 artifacts。
- [x] 加入標籤／手動 Release：版本核對、完整測試、JAR、SHA-256 校驗碼、可重跑附件上傳。
- [x] actionlint 1.7.12 檢查兩份 workflow 通過。
- [x] 本機重新執行 `gradlew.bat clean build --no-daemon` 通過。
- [ ] 推送並確認 GitHub Actions CI 與 Release 實際執行結果。

## 尚待部署環境驗證（續）

- [ ] 在實際 Paper 26.3 測試服完成一次玩家申請、Telegram 通過／拒絕與重啟持久化煙霧測試。
