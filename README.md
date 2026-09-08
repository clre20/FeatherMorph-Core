<div align="center">

# FeatherMorph Core

A server-side morph and disguise plugin for Paper, Purpur, and Folia.  
一款適用於 Paper、Purpur 與 Folia 的伺服器端變身偽裝插件。

</div>

---

> [!IMPORTANT]
> **社群延續維護聲明**  
> 本專案為 **FeatherMorph Core** 的社群延續維護版本。原專案由 **[MATRIX-feather](https://github.com/NiFeather/FeatherMorph)** 開發，原倉庫目前已封存（Archived）。  
> **原作者不再提供任何維護或技術支援。若在使用過程中遇到任何問題或 Bug，請至 [本專案 Issues](https://github.com/clre20/FeatherMorph-Core/issues) 回報，請勿前往原專案或打擾原作者！**
>
> **Community Continuation Notice**  
> This project is a community-maintained continuation of **FeatherMorph**. The original project was created by **[MATRIX-feather](https://github.com/NiFeather/FeatherMorph)** and is now archived.  
> **The original author no longer maintains this project. If you encounter any issues or bugs, please report them to [Our Issues](https://github.com/clre20/FeatherMorph-Core/issues). Please do NOT contact or disturb the original author!**

### 特色 / Features
- 允許玩家在遊戲中偽裝成各種生物（Mob）或其他玩家。  
  *Allows you and your friends to disguise as various mobs and players in-game.*
- 部分偽裝型態具備其獨特的技能與特殊能力。  
  *Several disguise forms also have their corresponding skills and abilities.*
- 若同時安裝客戶端模組（Client mod），可開啟專屬的 GUI 偽裝選擇界面與快捷鍵技能操作。  
  *If also installed the Client integration mod, you can have a GUI disguise selection screen and skill/actions hotkey.*
- 支援根據玩家客戶端所選語言發送對應的多語言訊息。  
  *Supports sending messages to players depending on their client language selection.*

### 伺服器依賴 / Dependencies
- **核心需求 / Core Requirements**：Paper、Purpur 或 Folia 伺服器。
- **前置插件 / Server Plugins**：
  - **2.x 版本**：[PacketEvents 2.12.0+](https://modrinth.com/plugin/packetevents)
  - **1.x 版本**：[ProtocolLib](https://ci.dmulloy2.net/job/ProtocolLib)
  - **0.x 版本**：[ProtocolLib](https://ci.dmulloy2.net/job/ProtocolLib) / [LibsDisguises](https://www.spigotmc.org/resources/libs-disguises-free.81/)

### 支援說明 / Support
- 本插件在開發中使用了 NMS 與特定版本 API，因此難以同時相容所有 Minecraft 版本。本分支主要針對最新的 Minecraft 版本進行更新與維護。  
  *We use NMS and newer APIs introduced in various versions, making it hard to support all Minecraft versions at once. Therefore, this continuation focuses on the latest Minecraft releases.*

### 編譯方式 / Building
```bash
git clone https://github.com/clre20/FeatherMorph-Core.git
cd FeatherMorph-Core
./gradlew build --no-daemon
```
編譯完成後，請使用位於 `build/libs` 且結尾為 `-final.jar` 的檔案。  
*The file located at `build/libs` that ends with `-final.jar` is the file that you should use.*

---

### 致謝與鳴謝 / Credits
- **[MATRIX-feather / NiFeather](https://github.com/NiFeather/FeatherMorph)**: Original creator of FeatherMorph.
- **[LibsDisguises](https://github.com/libraryaddict/LibsDisguises)**: For making this project possible, and reference on server renderers.
- **[VeinMiner](https://github.com/2008Choco/VeinMiner)**: For client-server communication implementation reference.
- **[PacketEvents](https://github.com/retrooper/packetevents)**: For packet handling and server-side entity rendering.
- **[PaperMC](https://papermc.io/)**: For server APIs and development framework.
