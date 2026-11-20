-- Initial course seed mappings for the optional, teacher-configured distractor diagnosis feature.
-- These mappings are explainable course data, not a claim of independent teacher validation.
INSERT INTO quiz_option_diagnosis (question_id, option_key, knowledge_point_id, rationale)
SELECT q.id, 'A', kp.id, '把加工这种数据变换过程误当成数据对象名称，混淆了加工与数据存储的命名语义。'
FROM quiz_question q JOIN course c ON c.id=q.course_id AND c.code='SE-2026-FALL'
JOIN knowledge_point kp ON kp.course_id=c.id AND kp.graph_entity='数据存储' AND kp.enabled=TRUE
WHERE q.id=1 AND NOT EXISTS (SELECT 1 FROM quiz_option_diagnosis d WHERE d.question_id=q.id AND d.option_key='A');

INSERT INTO quiz_option_diagnosis (question_id, option_key, knowledge_point_id, rationale)
SELECT q.id, 'A', kp.id, '把用例图与描述对象消息时序的交互过程混为一谈，忽略了用例图的用户目标视角。'
FROM quiz_question q JOIN course c ON c.id=q.course_id AND c.code='SE-2026-FALL'
JOIN knowledge_point kp ON kp.course_id=c.id AND kp.graph_entity='数据流图DFD' AND kp.enabled=TRUE
WHERE q.id=2 AND NOT EXISTS (SELECT 1 FROM quiz_option_diagnosis d WHERE d.question_id=q.id AND d.option_key='A');

INSERT INTO quiz_option_diagnosis (question_id, option_key, knowledge_point_id, rationale)
SELECT q.id, 'B', kp.id, '把需求分析阶段的交付物误换成实现环境中的操作系统对象，混淆了需求文档与实现技术。'
FROM quiz_question q JOIN course c ON c.id=q.course_id AND c.code='SE-2026-FALL'
JOIN knowledge_point kp ON kp.course_id=c.id AND kp.graph_entity='软件生命周期' AND kp.enabled=TRUE
WHERE q.id=3 AND NOT EXISTS (SELECT 1 FROM quiz_option_diagnosis d WHERE d.question_id=q.id AND d.option_key='B');

INSERT INTO quiz_option_diagnosis (question_id, option_key, knowledge_point_id, rationale)
SELECT q.id, 'A', kp.id, '把无法直接验收的主观形容词当成质量要求，混淆了可验证需求与笼统体验描述。'
FROM quiz_question q JOIN course c ON c.id=q.course_id AND c.code='SE-2026-FALL'
JOIN knowledge_point kp ON kp.course_id=c.id AND kp.graph_entity='软件需求规格说明书' AND kp.enabled=TRUE
WHERE q.id=4 AND NOT EXISTS (SELECT 1 FROM quiz_option_diagnosis d WHERE d.question_id=q.id AND d.option_key='A');

INSERT INTO quiz_option_diagnosis (question_id, option_key, knowledge_point_id, rationale)
SELECT q.id, 'C', kp.id, '把未经确认的假设直接写成需求事实，混淆了需求获取中的待核实信息与已确认约束。'
FROM quiz_question q JOIN course c ON c.id=q.course_id AND c.code='SE-2026-FALL'
JOIN knowledge_point kp ON kp.course_id=c.id AND kp.graph_entity='软件需求规格说明书' AND kp.enabled=TRUE
WHERE q.id=5 AND NOT EXISTS (SELECT 1 FROM quiz_option_diagnosis d WHERE d.question_id=q.id AND d.option_key='C');

INSERT INTO quiz_option_diagnosis (question_id, option_key, knowledge_point_id, rationale)
SELECT q.id, 'FALSE', kp.id, '把理解业务目标与选择具体界面方案对立起来，混淆了需求目标和解决方案之间的关系。'
FROM quiz_question q JOIN course c ON c.id=q.course_id AND c.code='SE-2026-FALL'
JOIN knowledge_point kp ON kp.course_id=c.id AND kp.graph_entity='软件需求规格说明书' AND kp.enabled=TRUE
WHERE q.id=6 AND NOT EXISTS (SELECT 1 FROM quiz_option_diagnosis d WHERE d.question_id=q.id AND d.option_key='FALSE');

INSERT INTO quiz_option_diagnosis (question_id, option_key, knowledge_point_id, rationale)
SELECT q.id, 'A', kp.id, '把分解数量当成固定规则，混淆了分层数据流图的边界平衡要求与加工个数。'
FROM quiz_question q JOIN course c ON c.id=q.course_id AND c.code='SE-2026-FALL'
JOIN knowledge_point kp ON kp.course_id=c.id AND kp.graph_entity='数据流图DFD' AND kp.enabled=TRUE
WHERE q.id=7 AND NOT EXISTS (SELECT 1 FROM quiz_option_diagnosis d WHERE d.question_id=q.id AND d.option_key='A');

INSERT INTO quiz_option_diagnosis (question_id, option_key, knowledge_point_id, rationale)
SELECT q.id, 'C', kp.id, '把外部实体直接写入数据存储，混淆了系统边界角色与数据持久化之间必须经过加工的关系。'
FROM quiz_question q JOIN course c ON c.id=q.course_id AND c.code='SE-2026-FALL'
JOIN knowledge_point kp ON kp.course_id=c.id AND kp.graph_entity='数据存储' AND kp.enabled=TRUE
WHERE q.id=8 AND NOT EXISTS (SELECT 1 FROM quiz_option_diagnosis d WHERE d.question_id=q.id AND d.option_key='C');

INSERT INTO quiz_option_diagnosis (question_id, option_key, knowledge_point_id, rationale)
SELECT q.id, 'TRUE', kp.id, '把数据流图中的数据流方向误解为程序控制流，混淆了数据变换建模与执行顺序建模。'
FROM quiz_question q JOIN course c ON c.id=q.course_id AND c.code='SE-2026-FALL'
JOIN knowledge_point kp ON kp.course_id=c.id AND kp.graph_entity='软件生命周期' AND kp.enabled=TRUE
WHERE q.id=9 AND NOT EXISTS (SELECT 1 FROM quiz_option_diagnosis d WHERE d.question_id=q.id AND d.option_key='TRUE');

INSERT INTO quiz_option_diagnosis (question_id, option_key, knowledge_point_id, rationale)
SELECT q.id, 'A', kp.id, '把系统内部数据库表当作系统外部参与者，混淆了系统边界与内部数据实现。'
FROM quiz_question q JOIN course c ON c.id=q.course_id AND c.code='SE-2026-FALL'
JOIN knowledge_point kp ON kp.course_id=c.id AND kp.graph_entity='需求分析' AND kp.enabled=TRUE
WHERE q.id=10 AND NOT EXISTS (SELECT 1 FROM quiz_option_diagnosis d WHERE d.question_id=q.id AND d.option_key='A');

INSERT INTO quiz_option_diagnosis (question_id, option_key, knowledge_point_id, rationale)
SELECT q.id, 'D', kp.id, '把用例图的目标关系当成可以替代接口时序和数据库设计的完整设计文档，混淆了模型的表达范围。'
FROM quiz_question q JOIN course c ON c.id=q.course_id AND c.code='SE-2026-FALL'
JOIN knowledge_point kp ON kp.course_id=c.id AND kp.graph_entity='软件需求规格说明书' AND kp.enabled=TRUE
WHERE q.id=11 AND NOT EXISTS (SELECT 1 FROM quiz_option_diagnosis d WHERE d.question_id=q.id AND d.option_key='D');

INSERT INTO quiz_option_diagnosis (question_id, option_key, knowledge_point_id, rationale)
SELECT q.id, 'FALSE', kp.id, '把异常业务路径排除在用例说明之外，混淆了正常流程与替代流程对需求完整性的作用。'
FROM quiz_question q JOIN course c ON c.id=q.course_id AND c.code='SE-2026-FALL'
JOIN knowledge_point kp ON kp.course_id=c.id AND kp.graph_entity='软件需求规格说明书' AND kp.enabled=TRUE
WHERE q.id=12 AND NOT EXISTS (SELECT 1 FROM quiz_option_diagnosis d WHERE d.question_id=q.id AND d.option_key='FALSE');

INSERT INTO quiz_option_diagnosis (question_id, option_key, knowledge_point_id, rationale)
SELECT q.id, 'A', kp.id, '把可追踪关系误解为需求永不变化的保证，混淆了需求变更管理与追踪记录的作用。'
FROM quiz_question q JOIN course c ON c.id=q.course_id AND c.code='SE-2026-FALL'
JOIN knowledge_point kp ON kp.course_id=c.id AND kp.graph_entity='软件生命周期' AND kp.enabled=TRUE
WHERE q.id=13 AND NOT EXISTS (SELECT 1 FROM quiz_option_diagnosis d WHERE d.question_id=q.id AND d.option_key='A');

INSERT INTO quiz_option_diagnosis (question_id, option_key, knowledge_point_id, rationale)
SELECT q.id, 'D', kp.id, '把需求规格说明书的内容误扩展为最终实现代码，混淆了需求文档和设计实现产物。'
FROM quiz_question q JOIN course c ON c.id=q.course_id AND c.code='SE-2026-FALL'
JOIN knowledge_point kp ON kp.course_id=c.id AND kp.graph_entity='软件生命周期' AND kp.enabled=TRUE
WHERE q.id=14 AND NOT EXISTS (SELECT 1 FROM quiz_option_diagnosis d WHERE d.question_id=q.id AND d.option_key='D');

INSERT INTO quiz_option_diagnosis (question_id, option_key, knowledge_point_id, rationale)
SELECT q.id, 'TRUE', kp.id, '把基线理解为禁止一切变更，混淆了受控变更流程与未经审批的随意修改。'
FROM quiz_question q JOIN course c ON c.id=q.course_id AND c.code='SE-2026-FALL'
JOIN knowledge_point kp ON kp.course_id=c.id AND kp.graph_entity='软件生命周期' AND kp.enabled=TRUE
WHERE q.id=15 AND NOT EXISTS (SELECT 1 FROM quiz_option_diagnosis d WHERE d.question_id=q.id AND d.option_key='TRUE');

INSERT INTO quiz_option_diagnosis (question_id, option_key, knowledge_point_id, rationale)
SELECT q.id, 'A', kp.id, '把数据存储的逻辑含义直接等同于某厂商数据库表，混淆了分析模型与具体实现技术。'
FROM quiz_question q JOIN course c ON c.id=q.course_id AND c.code='SE-2026-FALL'
JOIN knowledge_point kp ON kp.course_id=c.id AND kp.graph_entity='数据存储' AND kp.enabled=TRUE
WHERE q.id=16 AND NOT EXISTS (SELECT 1 FROM quiz_option_diagnosis d WHERE d.question_id=q.id AND d.option_key='A');

INSERT INTO quiz_option_diagnosis (question_id, option_key, knowledge_point_id, rationale)
SELECT q.id, 'D', kp.id, '把数据字典的业务数据定义误解为操作系统线程执行细节，混淆了需求数据描述和运行时实现。'
FROM quiz_question q JOIN course c ON c.id=q.course_id AND c.code='SE-2026-FALL'
JOIN knowledge_point kp ON kp.course_id=c.id AND kp.graph_entity='软件生命周期' AND kp.enabled=TRUE
WHERE q.id=17 AND NOT EXISTS (SELECT 1 FROM quiz_option_diagnosis d WHERE d.question_id=q.id AND d.option_key='D');

INSERT INTO quiz_option_diagnosis (question_id, option_key, knowledge_point_id, rationale)
SELECT q.id, 'FALSE', kp.id, '允许数据存储直接互换数据会跳过数据变换过程，混淆了数据流图的加工约束。'
FROM quiz_question q JOIN course c ON c.id=q.course_id AND c.code='SE-2026-FALL'
JOIN knowledge_point kp ON kp.course_id=c.id AND kp.graph_entity='数据流图DFD' AND kp.enabled=TRUE
WHERE q.id=18 AND NOT EXISTS (SELECT 1 FROM quiz_option_diagnosis d WHERE d.question_id=q.id AND d.option_key='FALSE');

INSERT INTO quiz_option_diagnosis (question_id, option_key, knowledge_point_id, rationale)
SELECT q.id, 'A', kp.id, '把迭代交付误解为只能一次性交付，混淆了迭代开发和瀑布式一次性交付的特点。'
FROM quiz_question q JOIN course c ON c.id=q.course_id AND c.code='SE-2026-FALL'
JOIN knowledge_point kp ON kp.course_id=c.id AND kp.graph_entity='软件生命周期' AND kp.enabled=TRUE
WHERE q.id=19 AND NOT EXISTS (SELECT 1 FROM quiz_option_diagnosis d WHERE d.question_id=q.id AND d.option_key='A');

INSERT INTO quiz_option_diagnosis (question_id, option_key, knowledge_point_id, rationale)
SELECT q.id, 'C', kp.id, '把迭代开发误解为可以取消版本和配置管理，混淆了迭代节奏与质量控制活动。'
FROM quiz_question q JOIN course c ON c.id=q.course_id AND c.code='SE-2026-FALL'
JOIN knowledge_point kp ON kp.course_id=c.id AND kp.graph_entity='软件维护' AND kp.enabled=TRUE
WHERE q.id=20 AND NOT EXISTS (SELECT 1 FROM quiz_option_diagnosis d WHERE d.question_id=q.id AND d.option_key='C');

INSERT INTO quiz_option_diagnosis (question_id, option_key, knowledge_point_id, rationale)
SELECT q.id, 'TRUE', kp.id, '把测试工作限定在编码完成之后，混淆了持续验证与单一阶段测试的概念。'
FROM quiz_question q JOIN course c ON c.id=q.course_id AND c.code='SE-2026-FALL'
JOIN knowledge_point kp ON kp.course_id=c.id AND kp.graph_entity='软件生命周期' AND kp.enabled=TRUE
WHERE q.id=21 AND NOT EXISTS (SELECT 1 FROM quiz_option_diagnosis d WHERE d.question_id=q.id AND d.option_key='TRUE');

INSERT INTO quiz_option_diagnosis (question_id, option_key, knowledge_point_id, rationale)
SELECT q.id, 'A', kp.id, '把适应外部环境变化的维护误判为纠正已发现缺陷的维护，混淆了维护类型的触发原因。'
FROM quiz_question q JOIN course c ON c.id=q.course_id AND c.code='SE-2026-FALL'
JOIN knowledge_point kp ON kp.course_id=c.id AND kp.graph_entity='软件维护' AND kp.enabled=TRUE
WHERE q.id=22 AND NOT EXISTS (SELECT 1 FROM quiz_option_diagnosis d WHERE d.question_id=q.id AND d.option_key='A');

INSERT INTO quiz_option_diagnosis (question_id, option_key, knowledge_point_id, rationale)
SELECT q.id, 'C', kp.id, '跳过既有流程的回归验证会把维护修改与原有行为割裂，混淆了修复验证和回归保护。'
FROM quiz_question q JOIN course c ON c.id=q.course_id AND c.code='SE-2026-FALL'
JOIN knowledge_point kp ON kp.course_id=c.id AND kp.graph_entity='软件维护' AND kp.enabled=TRUE
WHERE q.id=23 AND NOT EXISTS (SELECT 1 FROM quiz_option_diagnosis d WHERE d.question_id=q.id AND d.option_key='C');

INSERT INTO quiz_option_diagnosis (question_id, option_key, knowledge_point_id, rationale)
SELECT q.id, 'TRUE', kp.id, '把软件维护缩减为纠错，忽略了适应性、完善性和预防性维护。'
FROM quiz_question q JOIN course c ON c.id=q.course_id AND c.code='SE-2026-FALL'
JOIN knowledge_point kp ON kp.course_id=c.id AND kp.graph_entity='软件维护' AND kp.enabled=TRUE
WHERE q.id=24 AND NOT EXISTS (SELECT 1 FROM quiz_option_diagnosis d WHERE d.question_id=q.id AND d.option_key='TRUE');

INSERT INTO quiz_option_diagnosis (question_id, option_key, knowledge_point_id, rationale)
SELECT q.id, 'A', kp.id, '把重复通知当成可以重复产生业务效果，混淆了支付事件的幂等处理与简单重试。'
FROM quiz_question q JOIN course c ON c.id=q.course_id AND c.code='SE-2026-FALL'
JOIN knowledge_point kp ON kp.course_id=c.id AND kp.graph_entity='机票预定系统' AND kp.enabled=TRUE
WHERE q.id=25 AND NOT EXISTS (SELECT 1 FROM quiz_option_diagnosis d WHERE d.question_id=q.id AND d.option_key='A');

INSERT INTO quiz_option_diagnosis (question_id, option_key, knowledge_point_id, rationale)
SELECT q.id, 'D', kp.id, '把界面外观检查当成业务验收，混淆了可验证业务结果与表现层检查。'
FROM quiz_question q JOIN course c ON c.id=q.course_id AND c.code='SE-2026-FALL'
JOIN knowledge_point kp ON kp.course_id=c.id AND kp.graph_entity='需求分析' AND kp.enabled=TRUE
WHERE q.id=26 AND NOT EXISTS (SELECT 1 FROM quiz_option_diagnosis d WHERE d.question_id=q.id AND d.option_key='D');

INSERT INTO quiz_option_diagnosis (question_id, option_key, knowledge_point_id, rationale)
SELECT q.id, 'TRUE', kp.id, '把请求超时直接等同于支付失败，混淆了通信结果未知和业务结果已确认两种状态。'
FROM quiz_question q JOIN course c ON c.id=q.course_id AND c.code='SE-2026-FALL'
JOIN knowledge_point kp ON kp.course_id=c.id AND kp.graph_entity='机票预定系统' AND kp.enabled=TRUE
WHERE q.id=27 AND NOT EXISTS (SELECT 1 FROM quiz_option_diagnosis d WHERE d.question_id=q.id AND d.option_key='TRUE');
