# Qwen3.5 RANKED 推理集成设计

## 目标

将已训练的 Qwen3.5-0.8B LoRA 适配器接入软件学习平台的日常 RANKED 助教流程，并在真实 Python Bridge、NebulaGraph 和模型服务上验证。未满足所有上线门槛前，功能保持显式关闭，不切换现有 DeepSeek 流量。

## 已知模型与评测边界

- 基座是固定 revision `2fc06364715b967f1860aea9cf38778875588b17` 的 `Qwen/Qwen3.5-0.8B`，适配器在 `D:\SXJ\qwen35-finetune\output\qwen35-ranked-unreviewed\best-adapter`。
- 适配器只训练 RANKED 证据选择/有据生成任务；只准用于 Java 图谱约束后的 RANKED 路由，不声称 DIRECT/TRIPLES 已微调。
- 72 条自动生成测试标签未教师审校；已观测的 100% 结果是未审校探索性结果，不能作为接入正确性的唯一依据或正式论文成绩。
- 本机 GPU 为 RTX 3050 4 GB，远端训练机为 Tesla T4 16 GB。推理优先在 T4 上运行；不得为此修改系统驱动、TrustedHosts、防火墙或全局环境。

## 架构与流量边界

1. Python 服务复用训练 Conda 环境、冻结基座和 LoRA 适配器，不改 `Mecha-QA` 或原 Python Bridge。提供 `/health`、`/v1/models`、`/v1/chat/completions`，实现 Bearer API Key、请求/生成大小限制、串行 GPU 推理和安全日志；OpenAI chat completion 同时支持普通 JSON 响应与 SSE。
2. Java 保留现有 `AiModelClient`/DeepSeek 配置为默认模型。增加独立的可选 Qwen `AiModelClient` 配置，默认关闭。
3. Qwen 开关打开时，交互式 `/api/ai/ask` 与 `/api/ai/ask/stream` 的 `RANKED` 才走现有 `GroundedRankedAnswer` 两阶段选择与答案校验，并由 Qwen 客户端执行两阶段调用。DIRECT/TRIPLES 继续用默认模型。
4. `/api/ai/compare` 及消融实验全部继续使用默认客户端；同一次方法比较内模型与参数不变。不得把 Qwen RANKED 和 DeepSeek DIRECT/TRIPLES 的差异表述成单纯方法收益。
5. RANKED SSE 在两阶段回答完成且 Java 证据覆盖校验通过后才发送答案 delta；无效答案或模型服务故障走现有安全错误事件，不先向浏览器泄露未校验 token。
6. 服务/开关未配置时保持原行为；显式启用但 Qwen 健康检查或调用失败时，RANKED 不静默换模型，返回可诊断的服务不可用错误，避免来源记录与真实模型不一致。关闭开关即可回退到原 DeepSeek RANKED。

## 配置契约

- `RANKED_MODEL_ENABLED=false`：独立模型配置的总开关，默认关闭。
- `RANKED_MODEL_BASE_URL`：OpenAI-compatible base URL，指向 `/v1`，不得包含凭据、查询或 `chat/completions`。
- `RANKED_MODEL_API_KEY`：推理服务 Bearer key，不写日志或仓库。
- `RANKED_MODEL_NAME=qwen35-ranked-lora`、`RANKED_MODEL_TEMPERATURE=0`、`RANKED_MODEL_MAX_TOKENS=512`、`RANKED_MODEL_HTTP_TIMEOUT=90s`。
- Python 端由工作区环境变量读取基座/适配器路径、API key、绑定地址/端口、上下文上限和最大并发，不将密钥保存在命令行参数中。

## 错误、安全与资源约束

- 远端服务只绑定用户明确可达的推理端口；不暴露公网；必须验证 Bearer key。健康响应仅返回状态、模型 ID、设备和适配器是否加载，不返回路径或密钥。
- 请求正文上限 1 MiB，最多 32 条消息、总 token 输入上限 4096、生成 token 上限 1024；并发 GPU 生成上限为 1。超限返回标准安全错误。
- 模型加载失败则健康检查失败，Java 功能不被视为可用；训练模型权重只读，不覆盖、合并或重训适配器。
- 现有 Python Bridge/NebulaGraph 不参与 Qwen 服务生命周期管理，不停止或修改其他 GPU/系统服务。

## 验收标准

1. Python 单元测试覆盖 API key、路径/配置校验、输入消息转换、JSON 与 SSE 契约、并发/长度限制、健康检查，不需要 GPU 即可运行。
2. 远端真实服务以现有 T4、现有 Conda 环境、冻结基座及适配器启动；健康、模型列表、未授权拒绝、授权推理和 SSE smoke 均通过。
3. Java 单元/集成测试证明开关关闭时行为兼容；开启时仅交互 RANKED 走 Qwen，DIRECT/TRIPLES/compare 仍走默认 DeepSeek；RANKED SSE 只发送已通过证据校验的答案。
4. 本地前后端经 Python Bridge/Nebula 进行至少 5 个不同知识点的端到端 RANKED 测试，包括单事实、多事实、无证据/服务不可用；记录原始输出、证据 ID、一致性校验和延迟。任何答案违反所选图谱实体/关系/宾语或无效 JSON 均为验收失败。
5. 只有上述测试全部通过、结果与模型来源记录一致、关闭开关的回退再次验证后，才建议启用；对未审校 72 条测试集不作教师正确性或论文正式指标声明。

## 明确不做

- 不改 Mecha-QA/Python Bridge 原实现。
- 不改变训练集、测试集、图空间、模型权重或 DeepSeek API key。
- 不把未审校测试结果写成教师审校或正式准确率。
- 不修改本机/远端系统级安全配置或驱动。
