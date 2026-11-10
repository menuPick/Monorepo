package menupick.domain;

import java.util.List;

/**
 * AI 위험 판별 결과. 추천 항목당 하나(재평가 시 덮어씀).
 * AI는 점수와 근거만 남기고, 삭제·공개는 관리자가 결정합니다(docs/ai-design.md).
 */
public record AiAssessment(
        long recommendationId,
        double riskScore,
        String riskLevel,
        List<String> flags,
        List<String> reasons,
        String engine,
        String assessedAt
) {
    public AiAssessment {
        riskLevel = riskLevel == null ? "low" : riskLevel;
        flags = flags == null ? List.of() : List.copyOf(flags);
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        engine = engine == null ? "" : engine;
        assessedAt = assessedAt == null ? "" : assessedAt;
    }
}
