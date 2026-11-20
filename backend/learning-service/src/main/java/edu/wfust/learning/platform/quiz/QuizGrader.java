package edu.wfust.learning.platform.quiz;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class QuizGrader {
    public boolean isCorrect(List<String> submitted, List<String> expected) {
        if (submitted == null || expected == null || submitted.isEmpty() || expected.isEmpty()) return false;
        Set<String> submittedSet = new HashSet<>(submitted);
        Set<String> expectedSet = new HashSet<>(expected);
        return submittedSet.size() == submitted.size()
                && expectedSet.size() == expected.size()
                && submittedSet.equals(expectedSet);
    }
}
