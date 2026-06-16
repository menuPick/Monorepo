package menupick.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MenuCategoryClassifier {
    public static final String UNKNOWN = "미분류";
    public static final List<String> CATEGORIES = List.of(
            "주식",
            "국/찌개",
            "주찬(메인 반찬)",
            "부찬(보조 반찬)",
            "김치류",
            "후식(디저트)",
            UNKNOWN
    );

    private static final Map<String, List<String>> KEYWORDS = new LinkedHashMap<>();

    static {
        KEYWORDS.put("주식", List.of("볶음밥", "덮밥", "초밥", "유부초밥", "파스타", "국밥", "밥", "죽", "국수", "라면", "냉면", "비빔면", "짜장면"));
        KEYWORDS.put("국/찌개", List.of("찌개", "마라탕", "탕", "국"));
        KEYWORDS.put("김치류", List.of("김치", "깍두기"));
        KEYWORDS.put("주찬(메인 반찬)", List.of(
                "고기", "제육", "생선", "명태", "강정", "닭", "치킨", "황금올리브",
                "돼지", "소고기", "스테이크", "새우", "쉬림프", "연어", "육회", "육바연", "홍어"
        ));
        KEYWORDS.put("부찬(보조 반찬)", List.of("감자채", "감자튀김", "채소", "무침", "조림", "전", "볶음"));
        KEYWORDS.put("후식(디저트)", List.of("과일", "음료", "떡", "과자", "디저트"));
    }

    private MenuCategoryClassifier() {
    }

    public static String classify(String menuName, String recommendedMenu) {
        String text = (defaultString(menuName) + " " + defaultString(recommendedMenu)).trim();
        if (text.isEmpty()) {
            return UNKNOWN;
        }

        for (Map.Entry<String, List<String>> entry : KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (text.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }

        return UNKNOWN;
    }

    public static String normalize(String category, String menuName, String recommendedMenu) {
        String value = defaultString(category);
        if (CATEGORIES.contains(value)) {
            return value;
        }
        return classify(menuName, recommendedMenu);
    }

    private static String defaultString(String value) {
        return value == null ? "" : value.trim();
    }
}
