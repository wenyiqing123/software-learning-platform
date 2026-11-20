# Qwen3.5 RANKED 推理服务

这是软件学习平台的可选私有推理端点，只服务 Java 的交互式 `RANKED` 问答。它不修改 `Mecha-QA` 或原 Python Bridge，也不用于 DIRECT、TRIPLES 或 `/api/ai/compare`。

同一个已加载的 LoRA 包装模型还可通过两个 model ID 执行实验：`qwen35-base` 在请求期间禁用 LoRA，`qwen35-ranked-lora` 启用 LoRA。生成锁保证二者不会并发切换 adapter。两者共享同一个固定基座和 GPU 驻留模型，不会复制第二份模型权重。

## 运行环境

使用已经完成训练的 Conda 环境和只读模型目录：

```powershell
$env:QWEN35_API_KEY = '<随机长密钥>'
$env:QWEN35_BASE_MODEL_DIR = 'D:\SXJ\qwen35-finetune\package\base-model'
$env:QWEN35_ADAPTER_DIR = 'D:\SXJ\qwen35-finetune\output\qwen35-ranked-unreviewed\best-adapter'
$env:QWEN35_HOST = '0.0.0.0'
$env:QWEN35_PORT = '8998'
$env:QWEN35_MODEL_ID = 'qwen35-ranked-lora'
$env:QWEN35_BASE_MODEL_ID = 'qwen35-base'
$env:QWEN35_MAX_NEW_TOKENS = '512'
$env:QWEN35_MIN_FREE_VRAM_MIB = '3072'

& 'D:\SXJ\qwen35-finetune\envs\qwen35-finetune\python.exe' `
  'D:\SXJ\qwen35-finetune\package\serving\openai_server.py'
```

不要把密钥写入命令历史、代码仓库或日志。生产/服务器启动脚本应从受保护的进程环境或工作区 ACL 保护的配置文件读取。

## 接口

- `GET /health`：不需要密钥，只返回状态、模型 ID、设备和 GPU 名称，不返回本地路径。
- `GET /v1/models`：需要 `Authorization: Bearer ...`。
- `GET /v1/models` 同时返回 `qwen35-base` 与 `qwen35-ranked-lora` 两个可请求模型 ID。
- `POST /v1/chat/completions`：需要 Bearer key，支持 JSON 和 SSE，固定单并发、纯文本消息和有限 token。

服务启动前会读取 package/provenance/base-model-verification.json，并流式校验清单中的所有基座文件 SHA-256 和大小；同时校验 Qwen3.5 架构、适配器声明的基座仓库与固定 revision、适配器权重文件及 GPU 空闲显存。基座目录必须保持在该 package 布局内。任何校验失败都应让服务无法健康启动；模型路径或凭据不会写入健康响应。

## Java 配置

Java 默认不启用：

```text
RANKED_MODEL_ENABLED=false
```

验收通过后才在 Java 环境启用：

```text
RANKED_MODEL_ENABLED=true
RANKED_MODEL_BASE_URL=http://<T4主机>:8998/v1
RANKED_MODEL_API_KEY=<与服务端相同的密钥>
RANKED_MODEL_NAME=qwen35-ranked-lora
```

启用后，只有交互式 RANKED 使用 Qwen；DIRECT、TRIPLES 和比较实验继续使用默认模型。关闭开关即可回退到原逻辑。

## 验证

先运行服务合约测试：

```powershell
python -X utf8 -m unittest serving.test_openai_server -v
```

真实服务至少验证：健康、未授权拒绝、模型列表、JSON completion、SSE completion、Java `/api/ai/dependencies`、单事实/多事实 RANKED、无证据和关闭开关回退。
