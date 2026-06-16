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
