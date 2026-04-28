package menupick.db;

import menupick.model.InquiryRecord;
import menupick.model.MenuDecisionSetting;
import menupick.model.RecommendationRecord;

import java.io.IOException;
import java.util.List;

public interface DatabaseRepository {
    RecommendationRecord saveRecommendation(String menuName, String reason, String recommendedMenu) throws IOException;

    RecommendationRecord getLatestRecommendation() throws IOException;

    RecommendationRecord getRecommendationById(long id) throws IOException;

    MenuDecisionSetting getMenuDecisionSetting() throws IOException;

    void upsertMenuDecisionSetting(long recommendationId, String publishAt) throws IOException;

    InquiryRecord saveInquiry(String message, String adminEmail) throws IOException;

    InquiryRecord getLatestInquiry() throws IOException;

    List<RecommendationRecord> listRecommendations(int limit) throws IOException;

    /**
     * 추천을 id 기준으로 삭제합니다.
     *
     * @return 실제로 삭제된 recommendations 행 개수
     */
    int deleteRecommendations(List<Long> ids) throws IOException;

    List<InquiryRecord> listInquiries(int limit) throws IOException;

    default void close() throws IOException {
        // no-op
    }
}

