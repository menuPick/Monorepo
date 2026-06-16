import '../models/admin_models.dart';

class CategoryClassifier {
  static const String unknown = '미분류';

  static const List<String> categoryOrder = [
    '주식',
    '국/찌개',
    '주찬(메인 반찬)',
    '부찬(보조 반찬)',
    '김치류',
    '후식(디저트)',
    unknown,
  ];

  static final Map<String, List<String>> _keywords = {
    '주식': [
      '볶음밥',
      '덮밥',
      '초밥',
      '유부초밥',
      '파스타',
      '국밥',
      '밥',
      '죽',
      '국수',
      '라면',
      '냉면',
      '비빔면',
      '짜장면',
    ],
    '국/찌개': ['찌개', '마라탕', '탕', '국', '된장국', '미역국'],
    '김치류': ['김치', '배추김치', '깍두기'],
    '주찬(메인 반찬)': [
      '고기',
      '고기볶음',
      '제육',
      '생선',
      '생선구이',
      '명태',
      '강정',
      '닭',
      '치킨',
      '황금올리브',
      '돼지',
      '소고기',
      '스테이크',
      '새우',
      '쉬림프',
      '연어',
      '육회',
      '육바연',
      '홍어',
    ],
    '부찬(보조 반찬)': ['감자채', '감자튀김', '채소', '무침', '조림', '전', '볶음'],
    '후식(디저트)': ['과일', '음료', '떡', '과자', '디저트'],
  };

  static String classify(String menuName, String recommendedMenu) {
    final text = '${menuName.trim()} ${recommendedMenu.trim()}'.trim();
    if (text.isEmpty) return unknown;

    for (final entry in _keywords.entries) {
      for (final keyword in entry.value) {
        if (text.contains(keyword)) {
          return entry.key;
        }
      }
    }

    return unknown;
  }

  static Map<String, int> summarize(List<RecommendationItem> items) {
    final Map<String, int> counts = {for (final c in categoryOrder) c: 0};
    for (final item in items) {
      final category = classify(item.menuName, item.recommendedMenu);
      counts[category] = (counts[category] ?? 0) + 1;
    }
    return counts;
  }
}
