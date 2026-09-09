# FeatherMorph Core v262.0.1 (Community Continuation)

> [!IMPORTANT]
> **非官方維護版聲明**  
> 本專案為社群接手維護版本。原專案由 **[MATRIX-feather](https://github.com/NiFeather/FeatherMorph)** 開發，原倉庫目前已封存 (Archived)。本次 v262.0.1 版本適配至 Minecraft 26.2 穩定版核心，並完整新增 26.2 新生物技能、特性與問題修復。  
> **原作者不再提供任何維護與技術支援。如有任何問題，請前往 [本專案 Issues 頁面](https://github.com/clre20/FeatherMorph-Core/issues) 回報，請勿前往原專案或打擾原作者！**
> 
> **Unofficial Continuation Notice**  
> This project is a community-maintained continuation. The original project was developed by **[MATRIX-feather](https://github.com/NiFeather/FeatherMorph)** and is now archived. Version v262.0.1 provides full support for Minecraft 26.2, including new mob abilities, skills, and bug fixes.  
> **The original author no longer provides support. Please report any issues to [Our Issues Page](https://github.com/clre20/FeatherMorph-Core/issues). Please do not disturb the original author!**

---

### 📦 專案資訊與授權 / Project Info & License
- **維護者 / Maintainer**: clre20
- **原專案作者 / Original Author**: [MATRIX-feather](https://github.com/NiFeather/FeatherMorph)
- **授權條款 / License**: GNU General Public License v3.0 ([GPL-3.0](LICENSE))
- **開源倉庫 / Repository**: https://github.com/clre20/FeatherMorph-Core

---

### ✨ v262.0.1 更新內容 / Changelog

- 🚀 **升級支援 Minecraft 26.2**：完整適配至 Minecraft 26.2 及 Paper `26.2.build.121-stable` 伺服器核心。  
  *Support Minecraft 26.2*: Fully adapted to Minecraft 26.2 and Paper `26.2.build.121-stable` server software.
- 💥 **新增 26.2 生物技能、外觀與特性支援 / 26.2 Mob Additions**:
  - **硫磺立方怪 (Sulfur Cube)**:
    - 註冊專屬 Watcher（`SulfurCubeWatcher`），支援 `/morph property size` 尺寸調整。
    - **手持物品動態改變內容物**：根據玩家主手手持方塊動態改變立方體吞入內容物（僅允許吞入方塊，手持非方塊物品如牛肉、工具等則顯示為空立方體）。
    - **8 大物理原型特性與浮力機制**（`sulfur_cube_physics` 被動技能）：
      - 足球（普通 / REGULAR - 石頭、礦石類）：中等速度與彈跳、低空氣阻力、**有浮力**。
      - 橡膠球（彈性 / BOUNCY - 木頭類）：高移動速度、超高彈跳力、中等摩擦力、低空氣阻力、**有浮力**。
      - 藥球（沉重平移 / SLOW_FLAT - 金屬方塊）：移動緩慢、極低彈性、抗推抗擊退、**無浮力（液體沉底）**。
      - 高爾夫球（高速平移 / FAST_FLAT - 植物與有機方塊）：快速平移、低彈跳、極低空氣阻力、**無浮力**。
      - 沙灘球（輕盈 / LIGHT - 羊毛方塊）：移動慢、高彈跳、大幅降低重力與高空氣阻力（空中強烈滯空飄浮感）、**有浮力**。
      - 冰球（極速滑行 / FAST_SLIDING - 冰方塊）：極速滑行、無彈跳、地面近乎無摩擦力長距離滑行、**無浮力**。
      - 冰壺（緩慢滑行 / SLOW_SLIDING - 蘑菇方塊）：較慢滑行、無彈跳、地面低摩擦滑行、**無浮力**。
      - 空（基礎 / EMPTY - 空手或非方塊物品）：基礎物理狀態、**有浮力**。
    - 支援主動自爆技能（`explode`，帶引信計時與 TNT 點燃音效，引燃火焰傷害）。
    - 具備水下呼吸（`canBreatheUnderWater`）與無摔落傷害（`noFallDamage`）被動特性。
    - 納入魔法瓶（Magic Bottle）可收集名單。
  - **乾屍骷髏 (Parched)**:
    - 支援骷髏類虛擬裝備展示（`fake_equip`）。
    - 具備陽光燃燒（`burnsUnderSun`）、恐懼狼群（`panicsFrom`）及 16 點最大生命值機制。
  - **駱駝屍殼 (Camel Husk)**:
    - 支援衝刺技能（`dash`）與馬類 Watcher 裝備同步。
    - 支援鞍具裝備（`saddleable`）。
  - **鸚鵡螺 (Nautilus)**:
    - 納入魔法瓶（Magic Bottle）可收集名單。
- 🐛 **修復 Datapack 載入報錯**：將 `loot_tables` 下的說明文件 `README.md` 移出 `data/` 目錄，修復 Paper 啟動時回報 `Invalid path in pack` 的問題。  
  *Fix Datapack Error*: Moved `README.md` out of the datapack `loot_table` data directory to prevent invalid path error on Paper.
- 🛡️ **PacketEvents 前置檢測與優化**：在 `MorphManager` 中加入 PacketEvents 是否啟用的前置檢查，若未安裝則安全退回至預設後端，避免拋出反射崩潰例外。  
  *PacketEvents Pre-check & Robustness*: Added check for PacketEvents plugin availability before initializing ServerBackend, gracefully warning and falling back without throwing reflection errors.
- 🔄 **更新檢測器遷移至 GitHub Releases**：更新檢查器改為請求 `clre20/FeatherMorph-Core` GitHub Releases / Tags，不再依賴已封存的 Modrinth 專案。  
  *Update Checker Migration*: Migrated update checker to GitHub Releases/Tags API for `clre20/FeatherMorph-Core`.
- 🛠️ **編譯與工具鏈優化**：配置 Gradle Java 25 Toolchain，確保本地與 IDE 使用 Java 25 穩定建置，並移除未使用的 GitHub Actions 工作流。  
  *Toolchain & Build*: Configured Java 25 Toolchain for Gradle build consistency and removed unused GitHub Actions workflows.

---

### 📥 安裝說明 / Installation
1. 將 `feathermorph-v262.0.1-final.jar` 放入伺服器的 `plugins/` 目錄。  
   *Place `feathermorph-v262.0.1-final.jar` into your server's `plugins/` folder.*
2. 確保伺服器已安裝 **PacketEvents 2.12.0+**。  
   *Ensure **PacketEvents 2.12.0+** is installed on your server.*
3. 重啟伺服器即可。  
   *Restart your server.*
