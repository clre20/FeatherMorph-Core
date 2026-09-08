# FeatherMorph Core 2.9.1 (Community Continuation)

> [!IMPORTANT]
> **非官方維護版聲明**  
> 本專案為社群接手維護版本。原專案由 **[MATRIX-feather](https://github.com/NiFeather/FeatherMorph)** 開發，原倉庫目前已封存 (Archived)。本次 2.9.1 版本適配至 Minecraft 26.2 穩定版核心。  
> **原作者不再提供任何維護與技術支援。如有任何問題，請前往 [本專案 Issues 頁面](https://github.com/clre20/FeatherMorph-Core/issues) 回報，請勿前往原專案或打擾原作者！**
> 
> **Unofficial Continuation Notice**  
> This project is a community-maintained continuation. The original project was developed by **[MATRIX-feather](https://github.com/NiFeather/FeatherMorph)** and is now archived. Version 2.9.1 updates support for Minecraft 26.2 stable server software.  
> **The original author no longer provides support. Please report any issues to [Our Issues Page](https://github.com/clre20/FeatherMorph-Core/issues). Please do not disturb the original author!**

---

### 📦 專案資訊與授權 / Project Info & License
- **維護者 / Maintainer**: clre20
- **原專案作者 / Original Author**: [MATRIX-feather](https://github.com/NiFeather/FeatherMorph)
- **授權條款 / License**: GNU General Public License v3.0 ([GPL-3.0](LICENSE))
- **開源倉庫 / Repository**: https://github.com/clre20/FeatherMorph-Core

---

### ✨ 2.9.1 更新內容 / Changelog

- 🚀 **升級支援 Minecraft 26.2**：適配至 Minecraft 26.2 及 Paper `26.2.build.121-stable` 核心。  
  *Support Minecraft 26.2*: Fully adapted to Minecraft 26.2 and Paper `26.2.build.121-stable` server software.
- 🧱 **新生物偽裝支援**：支援 26.2 新增生物（如硫磺立方怪 Sulfur Cube）之變身外觀與碰撞體積。  
  *New Mobs Disguise*: Supports disguise appearance and hitbox for 26.2 mobs including Sulfur Cube.
- 🔧 **NMS 與 API 相容性修正 / Compatibility Fixes**：
  - 適配新版 Adventure 點擊事件（`ClickEvent.openUrl`） / Adapted to Adventure `ClickEvent.openUrl`.
  - 適配 Paper 26.2 實體類型映射（`CraftEntityType.bukkitToMinecraft`） / Updated Bukkit-to-NMS entity type mapping.
  - 修復 Mob 尋路目標 `goalSelector` 訪問相容性 / Fixed `goalSelector` reflective access on Mob.
  - 喚魔者生成隨機數安全性優化 / Thread-safe random generation for Vex spawning.

---

### 📥 安裝說明 / Installation
1. 將 `feathermorph-2.9.1-26.2-final.jar` 放入伺服器的 `plugins/` 目錄。  
   *Place `feathermorph-2.9.1-26.2-final.jar` into your server's `plugins/` folder.*
2. 確保伺服器已安裝 **PacketEvents 2.12.0+**。  
   *Ensure **PacketEvents 2.12.0+** is installed on your server.*
3. 重啟伺服器即可。  
   *Restart your server.*
