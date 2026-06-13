# 晋升:tmp 技术方案 → 先验经验

开发伴生的技术方案落在临时区 `/tech-proposals/{reqId}/{nodeId}`、带 `tmp` 标签,检索默认排除。当它沉淀为通用经验时**晋升**:

- **动作**:去掉 `tmp` 标签 + 设 `category="experience"` + **改 path** 到经验板块 `/experience/<有意义的名>`。
- **怎么做**:`write_knowledge(path="/experience/…", title, content, category="experience", tags=[…不含 tmp…])`(按新 path upsert);或在前端知识库页点「晋升」。
- 改 path 是固有动作:技术方案统一在临时区,先验经验有独立板块,晋升即「搬板块」。
