# Qwen3.5-0.8B 外部微调转移包

本包用于在外部 NVIDIA GPU 机器上微调。训练基座是 Transformers/Safetensors 格式；本机 Ollama 的 `qwen3.5:0.8b` Q8_0 GGUF 是独立推理文件，不能作为 PEFT 训练底座。本项目不改 Mecha-QA、Java/DeepSeek 配置、Nebula 数据或现有 Python 环境。

## 数据风险与人工审校

只保留 Java `ranked-grounded-v3` 两阶段线上流程的 `EVIDENCE_SELECTION`、`GROUNDED_ANSWER`。答案阶段 system context 中的已选图谱证据是 Java 第二次调用本来就会提供的条件输入，不是隐藏标准答案；loss 只计算 assistant 起始 token 之后。沿用原来的实体分组 train/validation/test 切分。test 文件仅在 `evaluation/`，`train.py` 不接收 test 路径。

参考答案和问法由图谱模板生成，**不是教师审定的金标准**，所以默认拒绝直接训练。`review/train-validation-review.csv` 有 690 条训练和验证记录，初始状态 `PENDING`。逐条审校并填写审核人 `reviewerName`、日期 `reviewedAt`（`YYYY-MM-DD`），将 `reviewStatus` 设为 `APPROVED`；如需修订，填写 `correctedReferenceAnswer` 和 JSON 数组 `correctedGoldEvidenceJson`。独立留出的 72 个 test case 在另一个 `review/test-review.csv`，只能用于封存最终评估 gold，不参与训练/早停/提示词调整。审校 test 标签时不要参考任何模型预测，并在测试前一次性冻结。

审校后生成新数据版本：

```powershell
python tools/apply_review.py
```

脚本要求 train/validation 全部通过审核，核验证据编号和答案覆盖的三元组主体、关系、宾语，并同步更新两阶段标签。原始数据不覆盖，结果写到 `dataset-reviewed/`。若暂时无法审校，可在训练命令加 `--allow-unreviewed-labels` 显式承认，报告会保留该事实，不能把标签说成教师标注。

可另行审核 test 标准答案并创建只供最终评估使用的数据：

```powershell
python tools/apply_test_review.py
```

它必须收到 72 条已批准的 test review，并仅生成 `evaluation-reviewed/`。训练脚本固定只读 `dataset-reviewed/train.jsonl` 与 `validation.jsonl`，没有 `evaluation-reviewed` 参数。最终评估可加 `--evaluation-dir evaluation-reviewed`，该命令会校验独立 test 审校清单。

## 基座模型

- 仓库 `Qwen/Qwen3.5-0.8B`；固定 revision `2fc06364715b967f1860aea9cf38778875588b17`。
- Safetensors 文件 1,746,942,600 字节；权重与 tokenizer SHA-256 在 `provenance/base-model-verification.json`。
- 配置、许可证、tokenizer、chat template 一并下载至 `base-model/`。

本机用临时隔离 Python 3.11 环境下载，不改全局环境；需预留约 1.8 GB：

```powershell
uv run --python 3.11 --with huggingface-hub==2.4.0 tools/fetch_model.py
python tools/verify_package.py
```

已用锁定 revision 的真实 tokenizer 对 train/validation/test 共 1,524 条对话逐条做过模板和长度 smoke test，报告在 `provenance/tokenization-smoke.json`。训练集最长 978 tokens、验证集最长 912 tokens，test 最长 1,206 tokens。重建数据后可用 `uv run --no-project --python 3.11 --with transformers==5.23.0 --with huggingface-hub==2.4.0 --with safetensors==0.8.0 --with jinja2==3.1.6 python tools/tokenizer_smoke.py` 重新验证。

下载支持续传。若网络需要代理，在当前 PowerShell 进程临时设置 `HTTPS_PROXY` 后重试。

## 外部 Python 环境

Linux 使用 Python 3.11 `venv`；Windows Anaconda 主机使用包内 Conda 安装辅助脚本。脚本只创建工作区里的 Python 环境，不改全局 PATH、系统变量、注册表、Anaconda 根环境或 NVIDIA 驱动。PyTorch 2.10.0 CUDA 12.8 runtime、Transformers 5.23.0、PEFT 0.21.3、Accelerate 1.15.0 从官方 PyTorch/PyPI 下载；首次安装需要网络，工作区内的 pip constraints 固定 `torch==2.10.0+cu128`，避免依赖解析换成 CPU/其他 CUDA wheel。安装后会用 GPU 矩阵乘实际验证当前驱动与 CUDA runtime 的兼容性。若烟测失败，脚本会停止并保留驱动不动。若之前安装中断，可重新运行辅助脚本；它会复用工作区内已有 Python prefix 并续装，不删除或覆盖该环境。

```bash
python3.11 tools/install_python_environment.py
```

Windows 通用方式可用 `py -3.11 tools/install_python_environment.py`。如使用已安装的 Anaconda 并要求环境及缓存都放在 `D:\SXJ`，解压后运行：

```powershell
C:\ProgramData\Anaconda3\python.exe D:\SXJ\qwen35-finetune\package\tools\setup_conda_environment.py `
  --conda-exe C:\ProgramData\Anaconda3\Scripts\conda.exe `
  --workspace D:\SXJ\qwen35-finetune `
  --torch-wheel D:\SXJ\qwen35-finetune\cache\wheels\torch-2.10.0+cu128-cp311-cp311-win_amd64.whl `
  --wheelhouse D:\SXJ\qwen35-finetune\cache\wheelhouse
```

这会创建或续用 `D:\SXJ\qwen35-finetune\envs\qwen35-finetune`，并把 Conda 包、pip、模型、Torch 和临时缓存放在同一工作区；局部 Conda 配置与 PyTorch 约束写入工作区。`--torch-wheel` 和 `--wheelhouse` 指向预先放入工作区的 wheel；CUDA wheel SHA-256 `3523fda6e2cfab2b04ae09b1424681358e508bb3faa11ceb67004113d5e7acad` 会在安装前验证，依赖使用 `--no-index` 离线安装。这样避开该主机访问 CDN/PyPI wheel 文件时的 403/低速下载。安装后会验证 PyTorch 版本、CUDA runtime、GPU 矩阵烟测和整包文件；结果写到 `setup-report.json`。

## 训练

建议先完成人工审校。Linux：

```bash
.venv/bin/python tools/verify_package.py
.venv/bin/python tools/train.py --dataset dataset-reviewed --output output/qwen35-ranked-lora-v1
```

Windows 将 `.venv/bin/python` 替换成上述 Conda 环境的 `D:\SXJ\qwen35-finetune\envs\qwen35-finetune\python.exe`。此训练器使用 LoRA 更新适配器参数，**全部 1,248 条训练记录都会参与**；基础模型仍被冻结，并非 0.8B 全参数更新。速度优先配置为原生支持时用 BF16，否则 FP16（Tesla T4 使用 FP16）、LoRA `r=8`/`alpha=16`、fused AdamW、最大 1,024 tokens、关闭梯度检查点。批量大小自动用最长样本做前后向探测，按 8、4、2、1 的顺序选择能通过的最大 micro-batch，并保留至少 1 GiB 探测余量；梯度累积自动调整，目标有效 batch 为 8。探测只计算梯度，不执行优化器更新。此方案以 16 GB NVIDIA GPU 为目标，启动前要求至少 14 GiB 总显存和 6 GiB 空闲显存。若自动探测所有批量仍 OOM，可加 `--gradient-checkpointing`（会降低速度），或缩短序列长度后重跑 token 检查：

```bash
.venv/bin/python tools/train.py --dataset dataset-reviewed --batch-size 2 --gradient-accumulation 4 --output output/qwen35-ranked-lora-v1
```

按 validation assistant loss 选 `best-adapter/`，连续 2 个 epoch 无改善早停。任何训练/验证记录超过 1,024 tokens 都会中止并报告 case ID，不会静默删样本。实际速度仍依显卡型号和外部机器负载而变；本机 RTX 3050 4 GB 不执行全量训练。

未审校时的显式选项：

```bash
.venv/bin/python tools/train.py --allow-unreviewed-labels --output output/qwen35-ranked-unreviewed
```

默认省略 `--batch-size` 会自动探测；设置 `--batch-size 4` 可固定批量，但如果不适合该显卡，建议删除该参数让探测器自动回退。`--gradient-accumulation` 可覆盖自动的有效批量计算。还可用 `--epochs`、`--max-seq-len`、`--learning-rate` 调参。训练集和验证集做 assistant-only loss；test 不参与梯度、选 epoch 或调提示词。

## 评估

先对未微调 base 跑基线，再对 adapter 跑完全相同的 72-case test：

```bash
.venv/bin/python tools/evaluate.py --output evaluation-baseline.jsonl
.venv/bin/python tools/evaluate.py --adapter output/qwen35-ranked-lora-v1/best-adapter --output evaluation-finetuned.jsonl
```

汇总证据选择 exact match、gold 证据下的答案生成质量、端到端 Java 证据校验后的质量及有效率。ROUGE-L、有效阶数 BLEU-4、期望实体召回复现 Java `ExperimentMetrics` 的 Unicode 字符级算法。完整评估逐 case 运行选择与生成并保留 JSONL 原始结果及 summary；`--max-cases 1` 只用于链路 smoke test，不能作为实验成绩。评测前不宣称指标提升。

## ZIP 转移

下载模型后运行：

```powershell
python tools/verify_package.py
python tools/pack_transfer.py
```

生成 `model-training/qwen35-transfer-v1-offline.zip` 及 SHA-256 文件。包含固定 revision 权重、训练/测试分离数据、审校 CSV、Python 脚本与依赖说明；不含 Ollama、缓存、虚拟环境或训练输出。外部解压后先运行 `python tools/verify_package.py`。

## 数据规模及局限

| 集合 | 事实 | cases | 两阶段对话 |
| --- | ---: | ---: | ---: |
| train | 434 | 624 | 1,248 |
| validation | 55 | 66 | 132 |
| test（仅评估） | 55 | 72 | 144 |

Nebula 原有 1,088 条物理边去重为 544 条事实；9 条训练事实因单一关系的宾语超过答案/候选预算而不生成可能不完整的问答。test 候选集含 gold，测的是给定候选条件下的排序选择和生成，不是检索召回率。自动标签、课程覆盖偏差、样本量不足应作为论文限制讨论。Adapter 需与相同基座 revision 配套。
