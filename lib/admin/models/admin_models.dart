class AdminSession {
  const AdminSession({
    required this.token,
    required this.expiresAt,
    required this.adminEmail,
  });

  final String token;
  final DateTime expiresAt;
  final String adminEmail;

  bool get isExpired => DateTime.now().isAfter(expiresAt);
}

class RecommendationItem {
  const RecommendationItem({
    required this.id,
    required this.menuName,
    required this.reason,
    required this.recommendedMenu,
    required this.category,
    required this.createdAt,
  });

  final int id;
  final String menuName;
  final String reason;
  final String recommendedMenu;
  final String category;
  final DateTime createdAt;

  factory RecommendationItem.fromJson(Map<String, dynamic> json) {
    return RecommendationItem(
      id: (json['id'] as num?)?.toInt() ?? 0,
      menuName: (json['menuName'] as String?) ?? '',
      reason: (json['reason'] as String?) ?? '',
      recommendedMenu: (json['recommendedMenu'] as String?) ?? '',
      category: (json['category'] as String?) ?? '',
      createdAt:
          DateTime.tryParse((json['createdAt'] as String?) ?? '') ??
          DateTime.fromMillisecondsSinceEpoch(0),
    );
  }
}

/// AI 위험 판별 결과. AI는 점수와 근거만 주고, 삭제·공개는 관리자가 결정합니다.
class AiAssessmentItem {
  const AiAssessmentItem({
    required this.recommendationId,
    required this.riskScore,
    required this.riskLevel,
    required this.flags,
    required this.reasons,
    required this.engine,
  });

  final int recommendationId;
  final double riskScore;
  final String riskLevel;
  final List<String> flags;
  final List<String> reasons;
  final String engine;

  String get levelLabel => switch (riskLevel) {
    'high' => '위험 높음',
    'medium' => '주의',
    _ => '안전',
  };

  static const Map<String, String> flagLabels = {
    'allergen': '알레르기',
    'food_safety': '식품 안전',
    'not_food': '음식 아님',
    'inappropriate': '부적절',
    'prompt_injection': '조작 시도',
    'unhealthy': '건강 주의',
  };

  factory AiAssessmentItem.fromJson(Map<String, dynamic> json) {
    List<String> strings(Object? value) =>
        (value as List?)?.map((e) => e.toString()).toList(growable: false) ??
        const [];
    return AiAssessmentItem(
      recommendationId: (json['recommendationId'] as num?)?.toInt() ?? 0,
      riskScore: (json['riskScore'] as num?)?.toDouble() ?? 0,
      riskLevel: (json['riskLevel'] as String?) ?? 'low',
      flags: strings(json['flags']),
      reasons: strings(json['reasons']),
      engine: (json['engine'] as String?) ?? '',
    );
  }
}

class InquiryItem {
  const InquiryItem({
    required this.id,
    required this.message,
    required this.adminEmail,
    required this.createdAt,
  });

  final int id;
  final String message;
  final String adminEmail;
  final DateTime createdAt;

  factory InquiryItem.fromJson(Map<String, dynamic> json) {
    return InquiryItem(
      id: (json['id'] as num?)?.toInt() ?? 0,
      message: (json['message'] as String?) ?? '',
      adminEmail: (json['adminEmail'] as String?) ?? '',
      createdAt:
          DateTime.tryParse((json['createdAt'] as String?) ?? '') ??
          DateTime.fromMillisecondsSinceEpoch(0),
    );
  }
}
