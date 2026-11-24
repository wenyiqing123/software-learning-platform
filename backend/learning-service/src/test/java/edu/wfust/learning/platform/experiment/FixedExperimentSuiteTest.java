package edu.wfust.learning.platform.experiment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig(FixedExperimentSuiteTest.Config.class)
@WebAppConfiguration
public class FixedExperimentSuiteTest {

    @Configuration
    @EnableWebMvc
    @EnableMethodSecurity
    @EnableWebSecurity
    static class Config {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .build();
        }

        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }

        @Bean
        FixedExperimentSuiteController fixedExperimentSuiteController() {
            return new FixedExperimentSuiteController();
        }
    }

    @Autowired
    private WebApplicationContext wac;

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(springSecurity())
                .build();
    }

    @Test
    void suiteContainsExactEightSeedCasesWithExpectedMetadata() {
        var suite = FixedExperimentSuite.getSuite();
        assertThat(suite.suiteVersion()).isEqualTo("2026.1");
        assertThat(suite.cases()).hasSize(8);

        var caseIds = suite.cases().stream().map(FixedExperimentSuite.CaseItem::caseId).toList();
        assertThat(caseIds).containsExactly(
                "REQ_ACCEPTANCE",
                "DFD_BALANCE",
                "UML_ACTOR",
                "SRS_TRACE",
                "DFD_STORE",
                "LIFE_INCREMENT",
                "MAINT_ADAPT",
                "ACCEPTANCE_SCENARIOS"
        );

        // 逐条断言与 V4 seed 一致的题干、章节和知识点
        var req = suite.cases().get(0);
        assertThat(req.sourceSection()).isEqualTo("从真实问题到可验证的需求");
        assertThat(req.knowledgePoint()).isEqualTo("需求分析");
        assertThat(req.question()).isEqualTo("下列哪条需求最容易通过明确的验收测试验证？");

        var dfd = suite.cases().get(1);
        assertThat(dfd.sourceSection()).isEqualTo("用数据流图解释业务数据如何变换");
        assertThat(dfd.knowledgePoint()).isEqualTo("数据流图DFD");
        assertThat(dfd.question()).isEqualTo("将 DFD 中的一个加工分解为子图时，应优先检查什么？");

        var uml = suite.cases().get(2);
        assertThat(uml.sourceSection()).isEqualTo("围绕参与者目标组织 UML 用例");
        assertThat(uml.knowledgePoint()).isEqualTo("用例图");
        assertThat(uml.question()).isEqualTo("在软件系统的 UML 用例图中，“支付平台”可以作为参与者的条件是什么？");

        var srs = suite.cases().get(3);
        assertThat(srs.sourceSection()).isEqualTo("把需求规格变成设计与验收的共同依据");
        assertThat(srs.knowledgePoint()).isEqualTo("软件需求规格说明书");
        assertThat(srs.question()).isEqualTo("为需求 REQ-BOOK-03 建立到设计项与测试用例的对应关系，主要有什么作用？");

        var store = suite.cases().get(4);
        assertThat(store.sourceSection()).isEqualTo("从逻辑数据集合走向一致的数据设计");
        assertThat(store.knowledgePoint()).isEqualTo("数据存储");
        assertThat(store.question()).isEqualTo("DFD 中的“订单记录”数据存储表示什么？");

        var life = suite.cases().get(5);
        assertThat(life.sourceSection()).isEqualTo("按风险与反馈安排软件生命周期活动");
        assertThat(life.knowledgePoint()).isEqualTo("软件生命周期");
        assertThat(life.question()).isEqualTo("团队先交付航班查询，随后加入预订和退款功能，这主要体现什么？");

        var maint = suite.cases().get(6);
        assertThat(maint.sourceSection()).isEqualTo("让交付后的改变保持可控");
        assertThat(maint.knowledgePoint()).isEqualTo("软件维护");
        assertThat(maint.question()).isEqualTo("为兼容支付平台新版本协议而修改接口，主要属于哪类维护？");

        var acc = suite.cases().get(7);
        assertThat(acc.sourceSection()).isEqualTo("贯穿需求、建模与验证的机票预订案例");
        assertThat(acc.knowledgePoint()).isEqualTo("机票预定系统");
        assertThat(acc.question()).isEqualTo("对“创建机票预订订单”进行验收时，哪些场景值得覆盖？");
    }

    @Test
    void caseRecordFieldsDoNotContainAnswerRubricOrPromptProperties() {
        Set<String> forbidden = Set.of(
                "answer", "correctanswer", "explanation", "referenceanswer",
                "expectedentities", "prompt", "systemprompt", "modeloutput", "credentials"
        );
        for (Field f : FixedExperimentSuite.CaseItem.class.getDeclaredFields()) {
            assertThat(forbidden).doesNotContain(f.getName().toLowerCase());
        }
        for (Method m : FixedExperimentSuite.CaseItem.class.getDeclaredMethods()) {
            assertThat(forbidden).doesNotContain(m.getName().toLowerCase());
        }
    }

    @Test
    void adminCanAccessFixedSuiteSuccessfully() throws Exception {
        mvc.perform(get("/api/admin/experiments/fixed-suite")
                        .with(user("1").roles("ADMIN"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suiteVersion").value("2026.1"))
                .andExpect(jsonPath("$.cases.length()").value(8))
                .andExpect(jsonPath("$.cases[0].caseId").value("REQ_ACCEPTANCE"))
                .andExpect(jsonPath("$.cases[0].question").value("下列哪条需求最容易通过明确的验收测试验证？"))
                .andExpect(jsonPath("$.cases[0].sourceSection").value("从真实问题到可验证的需求"))
                .andExpect(jsonPath("$.cases[0].knowledgePoint").value("需求分析"))
                .andExpect(jsonPath("$.cases[0].answer").doesNotExist())
                .andExpect(jsonPath("$.cases[0].referenceAnswer").doesNotExist())
                .andExpect(jsonPath("$.cases[0].explanation").doesNotExist())
                .andExpect(jsonPath("$.cases[0].prompt").doesNotExist());
    }

    @Test
    void studentCannotAccessFixedSuite() throws Exception {
        mvc.perform(get("/api/admin/experiments/fixed-suite")
                        .with(user("2").roles("STUDENT"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}
