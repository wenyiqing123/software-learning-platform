package edu.wfust.learning.platform.admin;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.HashSet;
import java.util.Set;

/** Domain invariants also apply to imported/seeded questions, independent of HTTP validation. */
public final class QuestionPayloadValidator {
    private QuestionPayloadValidator() { }
    public static void validate(AdminPayloads.QuestionRequest request) {
        require(request != null, "题目不能为空");
        require(request.courseId() != null && request.courseId() > 0 && request.knowledgePointId() != null && request.knowledgePointId() > 0, "请选择课程与知识点");
        require(Set.of("SINGLE", "MULTIPLE", "TRUE_FALSE").contains(request.type() == null ? "" : request.type()), "仅支持单选、多选与判断题");
        require(request.stem() != null && !request.stem().isBlank() && request.stem().length() <= 10000, "题干不能为空或过长");
        require(request.explanation() != null && !request.explanation().isBlank() && request.explanation().length() <= 10000, "请填写答案解析");
        require(request.difficulty() != null && request.difficulty() >= 1 && request.difficulty() <= 5, "难度必须为 1 到 5");
        require(request.options() != null && request.options().size() >= 2 && request.options().size() <= 8, "选项数量必须为 2 到 8");
        Set<String> keys = new HashSet<>();
        for (var option : request.options()) {
            require(option != null && option.key() != null && option.key().matches("[A-H]|TRUE|FALSE"), "选项键必须为 A-H 或 TRUE/FALSE");
            require(keys.add(option.key()), "选项键不能重复");
            require(option.text() != null && !option.text().isBlank() && option.text().length() <= 2000, "选项文本不能为空或过长");
        }
        require(request.answer() != null && !request.answer().isEmpty(), "请设置正确答案");
        require(new HashSet<>(request.answer()).size() == request.answer().size(), "正确答案不能重复");
        require(request.answer().stream().allMatch(key -> key != null && keys.contains(key)), "正确答案必须对应现有选项");
        if ("TRUE_FALSE".equals(request.type())) {
            require(keys.equals(Set.of("TRUE", "FALSE")), "判断题必须且只能包含 TRUE 与 FALSE 选项");
        } else {
            require(keys.stream().allMatch(key -> key.matches("[A-H]")), "选择题选项键必须为 A-H");
        }
        require("MULTIPLE".equals(request.type()) ? request.answer().size() >= 2 : request.answer().size() == 1,
            "多选题至少有两个正确选项；单选与判断题只能有一个正确选项");
    }
    private static void require(boolean condition, String reason) {
        if (!condition) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
