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
    '주식': ['밥', '죽', '볶음밥'],
    '국/찌개': ['국', '찌개', '된장국', '미역국'],
    '주찬(메인 반찬)': ['고기', '고기볶음', '생선', '생선구이', '닭', '돼지', '소고기'],
    '부찬(보조 반찬)': ['채소', '무침', '조림', '전'],
    '김치류': ['김치', '배추김치', '깍두기'],
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

