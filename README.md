# FeatherMorph Core

FeatherMorph Core 是一款適用於 Paper、Purpur 與 Folia（Minecraft Java 版 26.3）的伺服器端變身偽裝插件。玩家可以偽裝成各種生物或其他玩家，部分偽裝型態具備獨特的技能與能力。偽裝畫面由伺服器端透過 PacketEvents 渲染，玩家不需要安裝任何模組。

> **社群延續維護聲明**
> 本專案為 **FeatherMorph** 的社群延續維護版本。原專案由 [**MATRIX-feather**](https://github.com/NiFeather/FeatherMorph) 開發，原倉庫目前已封存（Archived）。
> **原作者不再提供任何維護或技術支援。若在使用過程中遇到任何問題或 Bug，請至 [本專案 Issues](https://github.com/clre20/FeatherMorph-Core/issues) 回報，請勿前往原專案或打擾原作者！**

## 功能特性

* **生物與玩家偽裝**：
  * 可偽裝成大部分原版生物，或偽裝成其他玩家（含自訂皮膚）。
  * 偽裝由伺服器端渲染，其他玩家不需安裝客戶端模組即可看到。
* **技能與能力**：
  * 多數偽裝擁有主動技能（例如末影人傳送、苦力怕爆炸）與被動能力（例如飛行、水下呼吸）。
  * 技能與能力可在設定檔中逐一調整或關閉。
* **硫磺立方體（照原版運作）**：
  * 以手上的方塊（副手優先，其次主手）作為立方體「吞下」的方塊。
  * 型態、物理屬性、浮力、擊退、音效、接觸傷害與爆炸規則，全部即時讀取伺服器的原版型態定義與物品標籤，與原版及資料包一致。
  * 裝著方塊時與原版相同：不能爬上台階、可在水下呼吸、不會凍結，並對 `#minecraft:sulfur_cube_with_block_immune_to` 內的傷害免疫（可在設定檔關閉）。
* **互動鏡像、聊天覆蓋與魔法瓶**等原有玩法皆保留。
* **玩家偽裝標記**：
  * 可選擇在偽裝成其他玩家時，名牌加上「偽裝」標記（上方 / 下方 / 名字後面），避免被拿來冒充其他玩家或管理員。
* **自動檢查更新**：
  * 伺服器啟動時以非同步方式向 GitHub Releases 檢查一次，結果快取 10 分鐘，不做定時輪詢。
  * 有新版本時，具備 `feathermorph.version` 權限的管理員登入 3 秒後會收到提醒。

## 伺服器需求

* **核心**：Paper、Purpur 或 Folia（Minecraft 26.3），Java 25。
* **前置插件**：[PacketEvents 2.14.0+](https://modrinth.com/plugin/packetevents)

## 指令與權限

本插件主指令為 `/feathermorph`（別名 `/fm`）。

| 指令 | 說明 | 執行端 | 權限節點 | 預設 |
| :--- | :--- | :--- | :--- | :--- |
| `/morph <生物ID>` | 偽裝成指定生物 | 僅限玩家 | `feathermorph.morph` | 所有人 |
| `/morphplayer <玩家名稱>` | 偽裝成指定玩家 | 僅限玩家 | `feathermorph.morph` | 所有人 |
| `/unmorph` | 取消偽裝 | 僅限玩家 | `feathermorph.unmorph` | 所有人 |
| `/request <send\|accept\|deny> <玩家>` | 與其他玩家交換偽裝 | 僅限玩家 | `feathermorph.request.*` | 所有人 |
| `/fm help` | 顯示指令說明 | 遊戲內 / 控制台 | 無 | 所有人 |
| `/fm reload` | 重新載入設定檔（自動補齊缺少的設定項目） | 遊戲內 / 控制台 | `feathermorph.reload` | OP |
| `/fm version` | 檢查 GitHub 上是否有新版本 | 遊戲內 / 控制台 | `feathermorph.version` | OP |
| `/fm option <選項> [值]` | 在遊戲內修改設定 | 遊戲內 / 控制台 | `feathermorph.toggle` | OP |
| `/fm manage <grant\|revoke\|morph\|unmorph> ...` | 管理玩家的偽裝 | 遊戲內 / 控制台 | `feathermorph.manage.*` | OP |
| `/fm query <玩家>` / `/fm queryall` | 查詢偽裝狀態 | 遊戲內 / 控制台 | `feathermorph.query` / `feathermorph.queryall` | OP |
| `/fm lookup <玩家>` | 查詢玩家已解鎖的偽裝 | 遊戲內 / 控制台 | `feathermorph.lookup` | OP |
| `/fm stat` | 顯示插件統計資訊 | 遊戲內 / 控制台 | `feathermorph.stat` | OP |
| （權限） | 偽裝成玩家時名牌不顯示「偽裝」標記 | - | `feathermorph.nameplate.bypass` | 無人（含 OP） |

> 指定生物的偽裝權限格式為 `feathermorph.morph.as.minecraft.<生物>`，例如 `feathermorph.morph.as.minecraft.sheep`。

## 使用範例

### 1. 偽裝成硫磺立方體並裝入方塊
1. 執行：
   ```bash
   /morph minecraft:sulfur_cube
   ```
2. 將方塊拿在副手或主手，例如原木（彈跳型）、藍冰（快速滑行型）、TNT（爆炸型）。
3. 換手上的方塊即可即時切換型態；空手則恢復為沒有吞下方塊的立方體。

### 2. 手動檢查更新
```bash
/fm version
```

## 設定檔

以下項目位於 `config.yml`。插件啟動與重載時會自動補上缺少的項目，並保留你原有的設定值。

| 欄位 | 說明 | 預設 |
| :--- | :--- | :--- |
| `update-checker.check-on-startup` | 啟動時檢查更新 | `true` |
| `update-checker.notify-on-join` | 有新版本時提醒登入的管理員 | `true` |
| `sulfur_cube.vanilla_damage_immunity` | 裝著方塊時照原版免疫部分傷害 | `true` |
| `sulfur_cube.tnt_breaks_blocks` | 爆炸型立方體是否破壞方塊（另需 `mobGriefing` 遊戲規則） | `false` |
| `player_disguise_nameplate.enabled` | 偽裝成其他玩家時，名牌加上「偽裝」標記 | `false` |
| `player_disguise_nameplate.position` | 標記位置：`above`（上方）、`below`（下方）、`suffix`（名字後面） | `suffix` |
| `player_disguise_nameplate.text` | 標記文字，支援 MiniMessage | `<gray>(偽裝)</gray>` |

## 致謝

* [**MATRIX-feather / NiFeather**](https://github.com/NiFeather/FeatherMorph)：FeatherMorph 原作者。
* [**LibsDisguises**](https://github.com/libraryaddict/LibsDisguises)：伺服器端渲染的參考。
* [**PacketEvents**](https://github.com/retrooper/packetevents)：封包處理與伺服器端實體渲染。
* [**PaperMC**](https://papermc.io/)：伺服器 API 與開發框架。

---

# FeatherMorph Core

FeatherMorph Core is a server-side morph and disguise plugin for Paper, Purpur and Folia (Minecraft Java Edition 26.3). Players can disguise as most mobs or as other players, and many disguises come with their own skills and abilities. Disguises are rendered server-side through PacketEvents, so players don't need any client mod.

> **Community Continuation Notice**
> This project is a community-maintained continuation of **FeatherMorph**. The original project was created by [**MATRIX-feather**](https://github.com/NiFeather/FeatherMorph) and is now archived.
> **The original author no longer maintains this project. If you encounter any issues or bugs, please report them to [Our Issues](https://github.com/clre20/FeatherMorph-Core/issues). Please do NOT contact or disturb the original author!**

## Features

* **Mob and player disguises**:
  * Disguise as most vanilla mobs, or as other players (custom skins supported).
  * Rendered server-side; other players see it without a client mod.
* **Skills and abilities**:
  * Many disguises have an active skill (e.g. enderman teleport, creeper explosion) and passive abilities (e.g. flight, water breathing).
  * Every skill and ability can be tuned or turned off in the configuration.
* **Sulfur cube (vanilla behavior)**:
  * The block in your hand (off hand first, then main hand) is the block the cube has swallowed.
  * Archetypes, physics attributes, buoyancy, knockback, sounds, contact damage and explosions are all read live from the server's vanilla archetype registry and item tags, so they match vanilla and any data packs.
  * While holding a block it behaves like vanilla: no step-up, breathes underwater, cannot freeze, and is immune to damage in `#minecraft:sulfur_cube_with_block_immune_to` (can be turned off).
* **Player disguise marker**:
  * Optionally add a "disguised" marker (above / below / after the name) to the nameplate of player disguises, so they can't be used to impersonate other players or staff.
* **Interaction Mirror, Chat Override and Magic Bottle** are kept as before.
* **Update checker**:
  * Checks GitHub Releases asynchronously once on startup; results are cached for 10 minutes and there is no periodic polling.
  * Admins with `feathermorph.version` are notified 3 seconds after joining when a new version is available.

## Requirements

* **Server**: Paper, Purpur or Folia (Minecraft 26.3), Java 25.
* **Dependency**: [PacketEvents 2.14.0+](https://modrinth.com/plugin/packetevents)

## Commands & Permissions

The main command is `/feathermorph` (alias `/fm`).

| Command | Description | Executor | Permission Node | Default |
| :--- | :--- | :--- | :--- | :--- |
| `/morph <mob id>` | Disguise as a mob | Player only | `feathermorph.morph` | Everyone |
| `/morphplayer <player>` | Disguise as a player | Player only | `feathermorph.morph` | Everyone |
| `/unmorph` | Remove your disguise | Player only | `feathermorph.unmorph` | Everyone |
| `/request <send\|accept\|deny> <player>` | Exchange disguises with another player | Player only | `feathermorph.request.*` | Everyone |
| `/fm help` | Show command help | In-game / Console | None | Everyone |
| `/fm reload` | Reload the configuration (fills in missing options) | In-game / Console | `feathermorph.reload` | OP |
| `/fm version` | Check GitHub for a newer version | In-game / Console | `feathermorph.version` | OP |
| `/fm option <option> [value]` | Change settings in-game | In-game / Console | `feathermorph.toggle` | OP |
| `/fm manage <grant\|revoke\|morph\|unmorph> ...` | Manage players' disguises | In-game / Console | `feathermorph.manage.*` | OP |
| `/fm query <player>` / `/fm queryall` | Check disguise states | In-game / Console | `feathermorph.query` / `feathermorph.queryall` | OP |
| `/fm lookup <player>` | List a player's unlocked disguises | In-game / Console | `feathermorph.lookup` | OP |
| `/fm stat` | Show plugin statistics | In-game / Console | `feathermorph.stat` | OP |
| (permission) | Player disguises don't show the nameplate marker | - | `feathermorph.nameplate.bypass` | Nobody (incl. OP) |

> Per-mob disguise permissions use `feathermorph.morph.as.minecraft.<mob>`, e.g. `feathermorph.morph.as.minecraft.sheep`.

## Usage Examples

### 1. Become a sulfur cube with a block inside
1. Run:
   ```bash
   /morph minecraft:sulfur_cube
   ```
2. Hold a block in your off hand or main hand, e.g. a log (bouncy), blue ice (fast sliding) or TNT (explosive).
3. Switch the held block to change archetype instantly; with empty hands you are a cube without a block.

### 2. Check for updates manually
```bash
/fm version
```

## Configuration

These options live in `config.yml`. Missing options are filled in on startup and reload, and your existing values are kept.

| Key | Description | Default |
| :--- | :--- | :--- |
| `update-checker.check-on-startup` | Check for updates on startup | `true` |
| `update-checker.notify-on-join` | Notify admins on join when an update is available | `true` |
| `sulfur_cube.vanilla_damage_immunity` | Vanilla damage immunity while holding a block | `true` |
| `sulfur_cube.tnt_breaks_blocks` | Whether an explosive cube breaks blocks (also requires the `mobGriefing` game rule) | `false` |
| `player_disguise_nameplate.enabled` | Add a "disguised" marker to the nameplate of player disguises | `false` |
| `player_disguise_nameplate.position` | Marker position: `above`, `below` or `suffix` | `suffix` |
| `player_disguise_nameplate.text` | Marker text, MiniMessage supported | `<gray>(偽裝)</gray>` |

## Credits

* [**MATRIX-feather / NiFeather**](https://github.com/NiFeather/FeatherMorph): Original creator of FeatherMorph.
* [**LibsDisguises**](https://github.com/libraryaddict/LibsDisguises): Reference for server-side rendering.
* [**PacketEvents**](https://github.com/retrooper/packetevents): Packet handling and server-side entity rendering.
* [**PaperMC**](https://papermc.io/): Server API and development framework.
