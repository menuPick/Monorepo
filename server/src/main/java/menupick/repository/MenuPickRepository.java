package menupick.repository;

import menupick.domain.AiAssessment;
import menupick.domain.Inquiry;
import menupick.domain.MenuDecision;
import menupick.domain.Recommendation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 저장소 경계. DB_URL이 없으면 {@link FileMenuPickRepository}, 있으면 {@link JdbcMenuPickRepository}가 사용됩니다.
 */
public interface MenuPickRepository {

    /** 사용자·월 단위로 한 번만 저장합니다. 이미 저장한 달이면 empty를 돌려줍니다. */
    Optional<Recommendation> saveMonthlyRecommendation(String userKey, String monthKey, NewRecommendation recommendation);

    /** 월 1회 제한 없이 저장합니다(제한을 끈 로컬 개발용). */
    Recommendation saveRecommendation(NewRecommendation recommendation);

    Optional<Recommendation> findLatestRecommendation();

    Optional<Recommendation> findRecommendationById(long id);

    /** 최신순 목록. */
    List<Recommendation> findRecentRecommendations(int limit);

    /** 삭제 대상에 결정 메뉴가 포함되면 결정 설정도 함께 제거합니다. */
    int deleteRecommendations(Collection<Long> ids);

    Optional<MenuDecision> findDecision();

    void saveDecision(long recommendationId, String publishAt);

    Inquiry saveInquiry(String message, String adminEmail);

    Optional<Inquiry> findLatestInquiry();

    List<Inquiry> findRecentInquiries(int limit);

    int deleteInquiries(Collection<Long> ids);

    /** 추천 항목당 하나. 이미 있으면 덮어씁니다. 추천을 삭제하면 함께 삭제됩니다. */
    void saveAiAssessment(AiAssessment assessment);

    List<AiAssessment> findAiAssessments(Collection<Long> recommendationIds);

    record NewRecommendation(String menuName, String reason, String recommendedMenu, String category) {
    }
}
