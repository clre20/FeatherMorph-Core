# FeatherMorph Core 2.9.0 (Community Continuation)

> [!IMPORTANT]
> **非官方維護版聲明**  
> 本專案為社群接手維護版本。原專案由 **[MATRIX-feather](https://github.com/NiFeather/FeatherMorph)** 開發，原倉庫目前已封存 (Archived)。本次 2.9.0 版本基於原作者最後未發布之源碼進行相容性適配、編譯與驗證。  
> **原作者不再提供任何維護與技術支援。如有任何問題，請前往 [本專案 Issues 頁面](https://github.com/clre20/FeatherMorph-Core/issues) 回報，請勿前往原專案或打擾原作者！**
> 
> **Unofficial Continuation Notice**  
> This project is a community-maintained continuation. The original project was developed by **[MATRIX-feather](https://github.com/NiFeather/FeatherMorph)** and is now archived. Version 2.9.0 is based on the original author's unreleased 26.1 branch, with verification and build fixes.  
> **The original author no longer provides support. Please report any issues to [Our Issues Page](https://github.com/clre20/FeatherMorph-Core/issues). Please do not disturb the original author!**

---

### 📦 專案資訊與授權 / Project Info & License
- **維護者 / Maintainer**: clre20
- **原專案作者 / Original Author**: [MATRIX-feather](https://github.com/NiFeather/FeatherMorph)
- **授權條款 / License**: GNU General Public License v3.0 ([GPL-3.0](LICENSE))
- **開源倉庫 / Repository**: https://github.com/clre20/FeatherMorph-Core

---

### ✨ 2.9.0 更新內容 / Changelog

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
1. 將 `feathermorph-2.9.0-26.1.2-final.jar` 放入伺服器的 `plugins/` 目錄。  
   *Place `feathermorph-2.9.0-26.1.2-final.jar` into your server's `plugins/` folder.*
2. 確保伺服器已安裝 **PacketEvents 2.12.0+**。  
   *Ensure **PacketEvents 2.12.0+** is installed on your server.*
3. 重啟伺服器即可。  
   *Restart your server.*
