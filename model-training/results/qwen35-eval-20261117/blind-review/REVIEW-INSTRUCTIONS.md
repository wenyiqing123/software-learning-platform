# 输出盲审说明

`finetuned-output-review.csv` 是参考答案与 gold 标签隐藏的模型输出审核表，行顺序已随机化。表中包含题目、图谱候选三元组、模型选中的证据和模型答案；不包含参考答案、gold evidence、期望实体、模型名称或排序分数。

请审核者对每行检查：

- `selectionReview`：所选三元组是否与问题相关且足以回答。
- `graphSupportReview`：答案中的事实是否由所列图谱三元组支持。
- `completenessReview`：答案是否完整回答问题。
- 上述三列填写 `YES`、`NO` 或 `UNCERTAIN`；有问题时在 `reviewNotes` 说明。
- 填写审核人和审核日期。

这张表只用于审核模型输出，不是测试集标准答案审批表，不能据此把测试集标记为已审校。要冻结测试集 gold，请由未查看模型预测的独立审核者审查 `qwen35-transfer-v1/review/test-review.csv`，逐条确认参考答案与 `goldEvidenceJson`，填写审核人、日期和 `APPROVED`；确有修订时再填写修订列。之后运行 `tools/apply_test_review.py` 生成单独的 `evaluation-reviewed/`。该步骤不会修改训练集。

当前评估为未审校标签上的探索性结果，不可直接作为论文正式成绩。
