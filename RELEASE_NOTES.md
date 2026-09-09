# FeatherMorph Core v261.0.1 (Community Continuation)

> [!IMPORTANT]
> **非官方維護版聲明**  
> 本專案為社群接手維護版本。原專案由 **[MATRIX-feather](https://github.com/NiFeather/FeatherMorph)** 開發，原倉庫目前已封存 (Archived)。本次 v261.0.1 版本針對 Minecraft 26.1.2 進行錯誤修復與相容性強化。  
> **原作者不再提供任何維護與技術支援。如有任何問題，請前往 [本專案 Issues 頁面](https://github.com/clre20/FeatherMorph-Core/issues) 回報，請勿前往原專案或打擾原作者！**
> 
> **Unofficial Continuation Notice**  
> This project is a community-maintained continuation. The original project was developed by **[MATRIX-feather](https://github.com/NiFeather/FeatherMorph)** and is now archived. Version v261.0.1 includes bug fixes and compatibility enhancements for Minecraft 26.1.2.  
> **The original author no longer provides support. Please report any issues to [Our Issues Page](https://github.com/clre20/FeatherMorph-Core/issues). Please do not disturb the original author!**

---

### 📦 專案資訊與授權 / Project Info & License
- **維護者 / Maintainer**: clre20
- **原專案作者 / Original Author**: [MATRIX-feather](https://github.com/NiFeather/FeatherMorph)
- **授權條款 / License**: GNU General Public License v3.0 ([GPL-3.0](LICENSE))
- **開源倉庫 / Repository**: https://github.com/clre20/FeatherMorph-Core

---

### ✨ v261.0.1 更新內容 / Changelog

- 🐛 **修復 Datapack 載入報錯**：將 `loot_tables` 下的說明文件 `README.md` 移出 `data/` 目錄，修復 Paper 啟動時回報 `Invalid path in pack: feathermorph:loot_table/README.md` 的錯誤。  
  *Fix Datapack Error*: Moved `README.md` out of the datapack `loot_table` data directory to prevent invalid path error on Paper.
- 🛡️ **PacketEvents 前置檢測與容錯優化**：在 `MorphManager` 中加入 PacketEvents 是否啟用的前置檢查，若伺服器未安裝 PacketEvents 則直接提示警告並降級為純 Mod 模式，避免觸發反射例外與長堆疊報錯。  
  *PacketEvents Pre-check & Robustness*: Added check for PacketEvents plugin availability before initializing ServerBackend, gracefully warning and falling back without throwing reflection errors.
- 🏷️ **版本字串相容性優化**：優化 `VersionHandling` 解析邏輯，支援帶有 `v` 前綴之版本號。  
  *Version String Parsing*: Enhanced `VersionHandling` to support version tags with `v` prefix.

---

### ✨ 2.9.0 既有更新內容 / Previous Changes (2.9.0)

- 🚀 **支援 Minecraft 26.1**：升級適配至 Minecraft 26.1.2 及對應之 Paper / Purpur / Folia 核心。  
  *Support Minecraft 26.1*: Fully adapted to Minecraft 26.1.2 and Paper / Purpur / Folia server software.
- ☕ **Java 25 執行環境**：支援 Java 25 運行環境。  
  *Java 25 Support*: Compatible with Java 25 runtime environment.
- 🔧 **相依庫版本更新 / Dependency Updates**：
  - 更新 PacketEvents 至 `2.12.0` / Bumped PacketEvents to `2.12.0`
  - 更新 GSit 至 `1.6.0` / Bumped GSit to `1.6.0`
  - 更新 PlaceholderAPI 至 `2.11.5` / Bumped PlaceholderAPI to `2.11.5`
  - 更新 Towny 至 `0.100.4.0` / Bumped Towny to `0.100.4.0`
- 🛠️ **架構優化與清理 / Architecture Cleanups**：
  - 分離 AI Modification 與 Interaction Mirror 為獨立插件模組  
    *Separated AI Modification & Interaction Mirror into standalone plugins.*
  - 修復離線偽裝狀態恢復機制 (Offline Disguise State Recover)  
    *Fixed Offline Disguise State Recovery (store and use disguise property).*
  - 核心與協議層適配優化  
    *Core and protocol compatibility improvements.*

---

### 📥 安裝說明 / Installation
1. 將 `feathermorph-v261.0.1-26.1.2-final.jar` 放入伺服器的 `plugins/` 目錄。  
   *Place `feathermorph-v261.0.1-26.1.2-final.jar` into your server's `plugins/` folder.*
2. 確保伺服器已安裝 **PacketEvents 2.12.0+**。  
   *Ensure **PacketEvents 2.12.0+** is installed on your server.*
3. 重啟伺服器即可。  
   *Restart your server.*
