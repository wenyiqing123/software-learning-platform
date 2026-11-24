package edu.wfust.learning.platform.experiment;

import java.util.List;

public class FixedExperimentSuite {
    public static final String SUITE_VERSION = "2026.1";

    public record CaseItem(
            String caseId,
            String question,
            String sourceSection,
            String knowledgePoint
    ) {}

    public record SuiteResponse(
            String suiteVersion,
            List<CaseItem> cases
    ) {}

    private static final List<CaseItem> CASES = List.of(
            new CaseItem(
                    "REQ_ACCEPTANCE",
                    "下列哪条需求最容易通过明确的验收测试验证？",
                    "从真实问题到可验证的需求",
                    "需求分析"
            ),
            new CaseItem(
                    "DFD_BALANCE",
                    "将 DFD 中的一个加工分解为子图时，应优先检查什么？",
                    "用数据流图解释业务数据如何变换",
                    "数据流图DFD"
            ),
            new CaseItem(
                    "UML_ACTOR",
                    "在软件系统的 UML 用例图中，“支付平台”可以作为参与者的条件是什么？",
                    "围绕参与者目标组织 UML 用例",
                    "用例图"
            ),
            new CaseItem(
                    "SRS_TRACE",
                    "为需求 REQ-BOOK-03 建立到设计项与测试用例的对应关系，主要有什么作用？",
                    "把需求规格变成设计与验收的共同依据",
                    "软件需求规格说明书"
            ),
            new CaseItem(
                    "DFD_STORE",
                    "DFD 中的“订单记录”数据存储表示什么？",
                    "从逻辑数据集合走向一致的数据设计",
                    "数据存储"
            ),
            new CaseItem(
                    "LIFE_INCREMENT",
                    "团队先交付航班查询，随后加入预订和退款功能，这主要体现什么？",
                    "按风险与反馈安排软件生命周期活动",
                    "软件生命周期"
            ),
            new CaseItem(
                    "MAINT_ADAPT",
                    "为兼容支付平台新版本协议而修改接口，主要属于哪类维护？",
                    "让交付后的改变保持可控",
                    "软件维护"
            ),
            new CaseItem(
                    "ACCEPTANCE_SCENARIOS",
                    "对“创建机票预订订单”进行验收时，哪些场景值得覆盖？",
                    "贯穿需求、建模与验证的机票预订案例",
                    "机票预定系统"
            )
    );

    public static SuiteResponse getSuite() {
        return new SuiteResponse(SUITE_VERSION, CASES);
    }
}
