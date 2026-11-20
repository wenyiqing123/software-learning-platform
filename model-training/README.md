# 本地 Qwen3.5 模型与软件工程图谱微调数据

本目录已完成模型下载、只读图谱导出和初版训练/验证/测试集构建，没有开始微调，也没有切换 Java 平台的 DeepSeek 配置。全部工作发生在本机，未部署服务器，未修改 Mecha-QA 源码、评分模型、图空间或数据卷。

## 当前可用文件

交付版本是 `data/nebula-20261116/dataset-v1/`。`dataset/` 与 `reproducibility-check/` 是早期模板诊断输出，训练不要引用；`reproducibility-v1/` 是正式版本的独立重建校验副本。

| 文件 | 用途 |
| --- | --- |
| `data/nebula-20261116/raw-triples.jsonl` | 从当前数据库读取的全部物理边，包含 VID、边 rank 和三个字段的原文 |
| `data/nebula-20261116/snapshot-metadata.json` | 导出时间、空间映射、schema、查询、导出前后边数和原始数据 SHA-256 |
| `data/nebula-20261116/dataset-v1/facts.jsonl` | 544 条去重三元组及每条事实的原始物理边来源 |
| `data/nebula-20261116/dataset-v1/train.jsonl` | 微调训练集，标准 `messages` 聊天结构 |
| `data/nebula-20261116/dataset-v1/validation.jsonl` | 验证集，只用于选参数/早停 |
| `data/nebula-20261116/dataset-v1/test.jsonl` | 测试任务表示，只用于最终评测，不用于训练或调提示词 |
| `data/nebula-20261116/dataset-v1/test-cases.jsonl` | 72 条成对对比问题，含统一参考答案、期望宾语、候选证据、真实相关分数、标准证据 ID |
| `data/nebula-20261116/dataset-v1/{train,validation,test}-facts.jsonl` | 每个集合的事实来源，便于审计 |
| `data/nebula-20261116/dataset-v1/manifest.json` | 切分种子、实际数量、排除原因、指标边界及文件哈希 |
| `model-info.json` | 模型标签、实际量化、digest、下载层校验、显存测试快照 |
| `logs/model-inference-smoke.json` | 未微调模型的一次真实短问答，保留原始响应，不代表质量已达标 |

## 模型下载和启动

- 精确下载标签：`qwen3.5:0.8b`。
- 本次官方标签返回 Q8_0 GGUF，总大小 1,322,069,277 字节（约 1.32 GB），而非先前估计的 Q4。完整模型层和 manifest 已逐个做 SHA-256/大小校验。
- 权重存储：`D:/model`；本地 tag manifest：`D:/model/manifests/registry.ollama.ai/library/qwen3.5/0.8b`。
- 旧 Ollama 0.14.2 返回 HTTP 412，不支持此模型。没有覆盖原安装；另行下载官方便携版 0.40.16，位置 `runtime/ollama-0.40.16/`，使用端口 **11435**。原默认 11434 的旧服务不能推理此模型。
- 便携包 SHA-256：`50fcf5ad54277f2fa5d173ebfc93bcb017adbc0c9ef2314daabc96ca9e6aa64e`，已与官方 `sha256sum.txt` 核对。
- 模型 upstream manifest digest：`de63045f29755d4721c09d54c9b45c2dadb644f4fd7d604d4cac90d96049971c`。Ollama 会扩充本地 manifest，因此本地扩充文件哈希另存于 `model-info.json`，不能直接与 upstream manifest digest 混为一谈。

在本目录打开 PowerShell，执行：

```powershell
./start-ollama-local.ps1
```

脚本只设置当前进程环境，默认 2048 上下文、最多一个并行请求、最多加载一个模型，不变更全局环境变量。服务已存在时只显示版本并退出；新服务在当前窗口运行，Ctrl+C 停止。

需要重新下载或断点续传时，先在第一个窗口执行 `./start-ollama-local.ps1 -UseSystemProxy`，然后另一个窗口执行：

```powershell
./pull-model.ps1
```

本机系统代理为 `127.0.0.1:7890`；不使用代理时域名的 Fake-IP 解析会触发 Ollama 的非公网跳转保护。这里复用系统代理，不关闭 Ollama 安全保护。代理只用于模型下载，数据集没有上传。

查询模型：

```powershell
Invoke-RestMethod 'http://127.0.0.1:11435/api/tags'
$env:OLLAMA_HOST = '127.0.0.1:11435'
& './runtime/ollama-0.40.16/ollama.exe' run 'qwen3.5:0.8b'
```

短推理已经成功执行。测试时 `num_ctx=2048`、`think=false`、最多输出 96 token，NVIDIA 显存总占用约 2081 MiB、剩余 1882 MiB。首次加载加请求约 69 秒，不能当作稳态吞吐。原始回答没有严格服从给定三元组，已保留在日志中；本次只验证能加载/推理，不保证问答正确性。测试后通过 `keep_alive=0` 卸载权重，显存恢复到约 436 MiB 占用。Nebula 同时运行；没有启动 Java/Vue 做整栈并发压测，也没有做微调显存测试。

## 图谱来源与去重

逻辑空间编号 `1` 沿用 Mecha-QA 的映射 `air_ticket`，实际 Nebula space ID 为 **2196**。真实 schema 是顶点 tag `entity(name)` 和边类型 `relation(name)`。

已导出 **1088 条物理有向边**，按规范化后的主语/关系/宾语去重后为 **544 条唯一事实**；544 条重复边的物理来源仍记录在 `sources` 中，没有删除数据库中的边。无空字段或非法字段被排除。导出使用有序分页并核对前后边数；这不是数据库事务快照，导出期间若有人同时写入，需另选时间重新导出。

## 切分与数据规模

| 统计项 | 训练 train | 验证 validation | 测试 test |
| --- | ---: | ---: | ---: |
| 划分后的事实数 | 434 | 55 | 55 |
| 作为问答标准证据使用的事实数 | 425 | 55 | 55 |
| 独立“主体+关系”问题组 | 312 | 33 | 36 |
| 两种问法合计 | 624 | 66 | 72 |
| 四种任务展开后的 messages 行数 | 2496 | 264 | 288 |

种子为 `20261116`，目标比例约 80/10/10。先以相同主体、相同完整宾语及长答案包含关系形成分组，再进行切分。一个主体的多种问法与不同关系始终留在同一集合。不要把 2496 行当作 2496 条独立知识，也不要把 72 条测试问法当作 72 个独立知识组；测试实际是 36 个主体关系问题组，每组两种措辞。组之间仍可能有相关性，若做置信区间还应按共享主体/答案分组聚类。

训练集“机票预定系统类图—包括”关系有 9 个宾语，超过默认候选上限 6。为避免把完整多值问题标成不完整答案，这 9 条事实保留在事实文件中，并在 `excluded_from_qa` 中标记，没有强行生成残缺问答。数据中其他相同主体/关系的多宾语均合并为同一完整答案，不生成互相矛盾的单宾语标准答案。

候选干扰项只从本集合事实中选择；训练上下文不会拿测试事实当干扰项。事实 ID、主体、完整相同宾语以及候选事实跨集合重叠均为 0；长度至少 8 字符的完整答案，在其他集合消息内的规范化子串重叠也为 0。短语和领域概念仍可能自然重合，不能据此声称完全没有语义泄漏。

## 四种微调任务

`train.jsonl` 将以下四种任务等量混合，训练后仍应使用同一个权重对比三个问答组，不能只给 RANKED 一组更换模型后声称优势来自排序。

1. `DIRECT`：问题 → 标准答案。
2. `TRIPLES`：不含相关性分数的三元组+问题 → 同一标准答案。
3. `EVIDENCE_SELECTION`：候选 ID/三元组/真实相关性分数 → JSON 的 status/taskType/selectedIds。
4. `GROUNDED_ANSWER`：所选证据的系统上下文 + Python 原始格式提示词 → JSON 的 answer/usedIds。

选择与生成提示词读取现有 Java `ranked-grounded-v3` 文本，不将参考答案或期望实体放入模型输入。排序沿用原项目升序格式。分数由现有本地 `air_ticket-finetuned-model-riddle_new/model` 在 CPU 上计算问题与三元组的 cosine similarity，绝不把 gold 标为人为 1 分、干扰项标为人为 0 分。原文件夹没有 SentenceTransformer 模块配置，和当前 Bridge 一样会显示自动 MEAN pooling 提示；这是现有评分流程，不是本次重新训练的评分模型。

所有参考答案是图谱原文按关系模板自然连接，不是人工教师认证答案。问法为模板生成，需人工抽检尤其是图形标签、编号步骤、流程动作和多值关系。现有 Java v3 对类型/方式关系的句式限制较硬，模板标签优先保证事实与自然表述；实际训练前应统一自然语言规范，并做课程专家审校。初版未加入专门的证据不足/冲突负例，不应拿它单独证明拒答和冲突处理能力。

`test-cases.jsonl` 预置 gold 在候选集中，适合测模型使用证据的能力；不能直接当作真实检索召回率。正式平台实验须另外检查 Bridge 能否检索到 gold，不能把 gold 偷塞给某一个对比组。

## 复验与重新构建

脚本使用本机已存在的 Python 3.10 环境，仅运行与数据有关的代码，不安装/升级全局依赖：

```powershell
Set-Location 'D:/develop/PythonProject/pythonallwork/毕设/software-learning-platform'
$py = 'D:/develop/anaconda/app/envs/LLM/python.exe'
& $py -X utf8 -m unittest discover -s model-training/tests -v
& $py -X utf8 model-training/verify_dataset.py model-training/data/nebula-20261116/dataset-v1
```

已使用同一快照和种子独立重建到 `reproducibility-v1/`，各 JSONL SHA-256 均一致。保留 `manifest.json` 与快照用于复现；审核后修改标签应新建版本，避免覆盖原始证据。

重新导出（命令中的输出目录必须不存在）：

```powershell
& $py -X utf8 model-training/export_nebula.py --out model-training/data/nebula-new
& $py -X utf8 model-training/build_dataset.py `
  --input model-training/data/nebula-new/raw-triples.jsonl `
  --out model-training/data/nebula-new/dataset-v1 `
  --scoring-model 'D:/develop/PythonProject/pythonallwork/Mecha-QA/output/air_ticket-finetuned-model-riddle_new/model'
```

Nebula 的主机/账户从 `NEBULA_HOST/NEBULA_PORT/NEBULA_ACCOUNT/NEBULA_PASSWORD` 环境变量读取，本机默认值沿用 Bridge；凭据不写入数据集、日志或清单。导出脚本只允许运行单条 SHOW/DESCRIBE/USE/MATCH 读语句，拒绝修改语句。

## 开始微调之前

Ollama 下载的是 GGUF 推理权重，Ollama 的 pull/create 不会执行监督微调。实际 LoRA 训练需要另准备 `Qwen/Qwen3.5-0.8B` 对应的可训练 Safetensors 权重、支持该架构的训练框架和独立环境；当前 Bridge Python 环境中的旧 transformers/torch 不应直接升级来承担训练。不要把 GGUF 文件直接交给 Transformers PEFT。

本次没有下载额外 Safetensors 或训练框架，也没有启动训练。后续需确定训练方式、确认 GPU 显存预算，再配置模板/训练 tokenization：模型 chat template、非思考输出格式、只对 assistant 标签计算 loss，以及按实际 tokenizer 限制序列长度。当前样本记录的是字符长度，不能把“字符数”当 token 数直接截断。测试集绝不能用于梯度更新、早停或挑提示词。

当前 4GB GPU 同时运行整套平台时不保证 LoRA 训练稳定。建议初次训练使用更大显存的独立机器，本机负责推理；若本机尝试，先小批量显存验证。无论哪种训练方式，都保留未微调基线和同权重的三组结果，不预先承诺排序组一定最高。

来源：Ollama 官方 qwen3.5:0.8b 模型库、Ollama 0.40.16 GitHub release/sha256sum、Ollama Import 文档、Qwen 官方 `Qwen/Qwen3.5-0.8B` 模型说明。机器可核验信息见 `model-info.json` 与下载日志。
