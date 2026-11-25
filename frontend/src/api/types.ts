export interface GraphNode { id: string; label: string; category?: string }
export interface GraphEdge { source: string; target: string; label: string; score?: number }
export interface GraphResponse { nodes: GraphNode[]; edges: GraphEdge[]; evidence: unknown[]; available: boolean }
export interface KnowledgePoint { id: number; courseId: number; chapterId: number; name: string; graphEntity: string; description?: string; sortOrder: number; mastery: number }
export interface PrerequisitePathNode { id: number; courseId: number; chapterId: number; name: string; graphEntity: string; mastery: number; prerequisiteIds: number[]; status: 'MASTERED' | 'IN_PROGRESS' | 'UP_NEXT' | 'BLOCKED' }
export interface PrerequisitePath { targetId: number; courseId: number; nodes: PrerequisitePathNode[]; cycleDetected: boolean }
export interface PrerequisiteQualityIssue { code: string; severity: 'ERROR' | 'WARNING'; pointId: number | null; pointName: string | null; relatedPointId: number | null; relatedPointName: string | null; message: string }
export interface PrerequisiteQualityReport { courseId: number; pointCount: number; validEdgeCount: number; errorCount: number; warningCount: number; valid: boolean; issues: PrerequisiteQualityIssue[] }
export interface MasteryMapNode { id: number; courseId: number; chapterId?: number; name: string; graphEntity: string; mastery: number; attemptCount: number; correctCount: number; dueReviewCount: number; status: 'MASTERED' | 'IN_PROGRESS' | 'BLOCKED' | 'UP_NEXT'; prerequisiteIds: number[] }
export interface MasteryMapEdge { source: number; target: number; relation: string }
export interface MasteryMap { courseId: number; courseName: string; nodes: MasteryMapNode[]; edges: MasteryMapEdge[] }
export interface LearningReport { from: string; to: string; days: number; courseId?: number; courseName: string; reading: { totalSections: number; completedSections: number; completedInPeriod: number }; practice: { attempts: number; correct: number; accuracy: number }; reviews: { reviews: number; correct: number; accuracy: number }; plans: { totalTasks: number; completedTasks: number; plannedMinutes: number; completedMinutes: number }; dailyActivity: { date: string; attempts: number; correct: number; readingCompleted: number }[]; chapters: { chapterId: number; chapterName: string; totalSections: number; completedSections: number; completedInPeriod: number }[]; knowledge: { pointId: number; pointName: string; mastery: number; attempts: number; correct: number; dueReviewCount: number }[] }
export interface LearningNote { id: number; sectionId?: number; knowledgePointId?: number; title: string; content: string; tags: string[]; createdAt?: string; updatedAt?: string }
export interface LearningBookmark { targetType: 'SECTION' | 'KNOWLEDGE_POINT'; targetId: number; bookmarked: boolean }
export interface TeachingDashboard { from: string; to: string; days: number; courseId?: number; courseName: string; overview: { activeLearners: number; totalSections: number; completedReads: number; attempts: number; correct: number; openWrong: number; averageMastery: number }; dailyActivity: { date: string; activeLearners: number; attempts: number; correct: number; completedReads: number }[]; weakPoints: { pointId: number; pointName: string; averageMastery: number; attempts: number; learners: number }[] }
export interface Course { id: number; code: string; name: string; term: string; description?: string; graphSpaceId?: number; enabled?: boolean }
export interface QuestionBankVersion { id: number; courseId: number; courseName: string; versionName: string; status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'; sourceFilename: string; createdBy?: number; createdAt?: string; publishedAt?: string; questionCount: number }
export interface QuestionBankImportError { rowNumber: number; message: string }
export interface QuestionBankImportResult { importId?: number; versionId: number; totalRows: number; successCount: number; errorCount: number; errors: QuestionBankImportError[] }
export interface MicroLessonTriple { subject: string; predicate: string; object: string; score: number }
export interface MicroLessonCommonError { stem: string; explanation: string; wrongCount: number }
export interface MicroLessonSelfTest { questionId: number; stem: string; options: { key: string; text: string }[] }
export interface MicroLessonPrerequisite { id: number; name: string; graphEntity: string }
export interface MicroLessonDraft { id: number; courseId: number; sectionId: number; title: string; status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'; summary?: string; generatedText: string; objectives: string[]; triples: MicroLessonTriple[]; commonErrors: MicroLessonCommonError[]; selfTests: MicroLessonSelfTest[]; prerequisites: MicroLessonPrerequisite[]; evidence?: { courseId?: number; sectionId?: number; knowledgePointId?: number; graphEntity: string; tripleCount: number; generatedAt?: string }; createdAt?: string; publishedAt?: string }
export interface ExperimentTask { id: number; title: string; description: string; experimentType: 'METHODS' | 'QWEN_MATRIX'; question: string; referenceAnswer: string; expectedEntities: string[]; includeAblations: boolean; status: 'DRAFT' | 'OPEN' | 'CLOSED'; createdBy?: number; groupCount: number; assignmentCount: number }
export interface ExperimentGroup { id: number; taskId: number; name: string; conditionKey: string; sortOrder: number; taskOrder?: 'A→B' | 'B→A' }
export interface ExperimentAssignment { id: number; taskId: number; groupId: number; userId?: number; status: 'ASSIGNED' | 'IN_PROGRESS' | 'COMPLETED'; title: string; description: string; experimentType: 'METHODS' | 'QWEN_MATRIX'; question: string; referenceAnswer?: string; taskStatus: string; groupName: string; conditionKey: string; taskOrder?: 'A→B' | 'B→A'; startedAt?: string; completedAt?: string }
export interface SpacedReviewCard { wrongQuestionId: number; questionId: number; question: string; knowledgePoint?: string; questionType: string; options: { key: string; text: string }[]; wrongCount: number; dueAt: string; previousIntervalMinutes: number; sectionId?: number }
export interface SpacedReviewResult { attemptId: number; questionId: number; correct: boolean; correctAnswer: string[]; explanation?: string; recallRating: number; intervalMinutes: number; nextDueAt: string; mastered: boolean; consecutiveSuccessfulReviews: number }
export interface StudyPlanTask { id: number; type: 'REVIEW' | 'READING' | 'PRACTICE'; targetId: number; title: string; detail: string; minutes: number; reason: string; state: 'PENDING' | 'COMPLETED' | 'SKIPPED'; path: string }
export interface StudyPlan { id: number; date: string; budgetMinutes: number; tasks: StudyPlanTask[] }
export interface AdaptivePracticeItem { questionId: number; stem: string; knowledgePointId?: number; knowledgePointName?: string; difficulty?: number; mastery: number; wrongCount: number; due: boolean; score: number; reason: string }
export interface QuizDiagnosis { optionKey: string; optionText: string; knowledgePointId: number; knowledgePointName: string; rationale: string; sectionId?: number; sectionTitle?: string; practiceQuestionId?: number }
export interface ConfiguredQuizDiagnosis { id: number; optionKey: string; optionText: string; knowledgePointId: number; knowledgePointName: string; rationale: string; enabled: boolean }
export interface EvaluationSession { id: number; conditionKey: string; taskKey: string; taskOrder?: string; assignmentId?: number; consentVersion: string; startedAt: string; completedAt?: string; elapsedSeconds?: number; psychologicalEffort?: number; feedback?: string; completedPhases?: string[] }
export interface EvaluationSummary { conditionKey: string; sampleCount: number; meanPreScore?: number; medianPreScore?: number; stddevPreScore?: number; iqrPreScore?: number; meanPostScore?: number; medianPostScore?: number; stddevPostScore?: number; iqrPostScore?: number; meanDelayedScore?: number; medianDelayedScore?: number; stddevDelayedScore?: number; iqrDelayedScore?: number; meanGain?: number; meanEffort?: number; medianEffort?: number; stddevEffort?: number; iqrEffort?: number; meanElapsedSeconds?: number; medianElapsedSeconds?: number; stddevElapsedSeconds?: number; iqrElapsedSeconds?: number; feedbackCount: number }
export interface TutorHintEvidence { subject: string; predicate: string; object: string; score: number }
export interface FixedExperimentCase { caseId: string; question: string; sourceSection: string; knowledgePoint: string }
export interface FixedExperimentSuite { suiteVersion: string; cases: FixedExperimentCase[] }
export interface TutorHintResult { question: string; level: number; content: string; hasEvidence: boolean; evidence: TutorHintEvidence[]; nextLevelAvailable: boolean; source: string }
export interface AdminUserSummary {
  id: number
  username: string
  displayName: string
  role: 'STUDENT' | 'ADMIN'
  enabled: boolean
  createdAt?: string
  completedSections: number
  attemptCount: number
  correctCount: number
  accuracy: number
  averageMastery: number
  openWrongCount: number
  dueReviewCount: number
  activeTokenCount: number
  lastActiveAt?: string
}
export interface AdminUserPage {
  items: AdminUserSummary[]
  total: number
  page: number
  size: number
}
export interface AdminUserProfile {
  account: {
    id: number
    username: string
    displayName: string
    role: 'STUDENT' | 'ADMIN'
    enabled: boolean
    createdAt?: string
    updatedAt?: string
    activeSessionCount: number
  }
  summary: {
    completedSections: number
    totalSections: number
    attemptCount: number
    correctCount: number
    accuracy: number
    averageMastery: number
    openWrongCount: number
    dueReviewCount: number
    aiSessionCount: number
    aiQuestionCount: number
    lastStudiedAt?: string
  }
  courses: {
    courseId: number
    courseName: string
    completedSections: number
    totalSections: number
    lastStudiedAt?: string
  }[]
  knowledgeMastery: {
    pointId: number
    pointName: string
    courseId: number
    courseName: string
    masteryScore: number
    attemptCount: number
    correctCount: number
    lastStudiedAt?: string
  }[]
  wrongQuestions: {
    wrongQuestionId: number
    questionId: number
    courseName: string
    pointName: string
    stem: string
    wrongCount: number
    status: string
    reviewStreak: number
    dueAt?: string
    lastWrongAt?: string
  }[]
  assignments: {
    taskId: number
    taskTitle: string
    groupName: string
    conditionKey: string
    status: 'ASSIGNED' | 'IN_PROGRESS' | 'COMPLETED'
    assignedAt?: string
    startedAt?: string
    completedAt?: string
  }[]
  aiActivity: {
    sessionId: number
    title: string
    userMessageCount: number
    updatedAt?: string
  }[]
}
