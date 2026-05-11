import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

/// 빌드/실행 시 아래처럼 서버 주소를 바꿀 수 있습니다.
/// flutter run --dart-define=API_BASE_URL=http://localhost:8080
const String kApiBaseUrl = String.fromEnvironment(
  'API_BASE_URL',
  defaultValue: 'http://localhost:8080',
);

/// 위젯 테스트에서는 실제 네트워크 호출이 불필요하고,
/// 오히려 테스트를 느리게 만들 수 있으므로 차단합니다.
const bool kIsFlutterTest = bool.fromEnvironment('FLUTTER_TEST');

class UserApi {
  UserApi({http.Client? client, String? baseUrl})
      : _client = client ?? http.Client(),
        baseUrl = _normalizeBaseUrl(baseUrl ?? kApiBaseUrl);

  final http.Client _client;
  final String baseUrl;
  static const _kAnonymousUserId = 'anonymous_recommendation_user_id';

  Uri _uri(String path, [Map<String, String>? query]) {
    final normalizedPath = path.startsWith('/') ? path.substring(1) : path;
    // baseUrl 뒤에 / 를 강제해서 resolve가 안정적으로 동작하도록 합니다.
    return Uri.parse('$baseUrl/')
        .resolve(normalizedPath)
        .replace(queryParameters: query);
  }

  static String _normalizeBaseUrl(String raw) {
    var value = raw.trim();
    if (value.isEmpty) {
      // 배포 환경에서 API를 같은 오리진으로 붙이는 경우를 지원합니다.
      return Uri.base.origin;
    }

    // "//example.com" 형태 지원
    if (value.startsWith('//')) {
      return '${Uri.base.scheme}:$value';
    }

    final lower = value.toLowerCase();
    final hasScheme = lower.startsWith('http://') || lower.startsWith('https://');
    if (!hasScheme) {
      // 스킴이 없으면 개발 환경(로컬호스트/IP)은 http, 그 외는 https로 보정합니다.
      final isLocal = lower.contains('localhost') ||
          lower.startsWith('127.0.0.1') ||
          RegExp(r'^\d+\.\d+\.\d+\.\d+').hasMatch(lower);
      value = '${isLocal ? 'http' : 'https'}://$value';
    }

    // trailing slash 제거
    value = value.replaceAll(RegExp(r'/*$'), '');
    return value;
  }

  Future<RecommendationDto> submitRecommendation({
    required String menuName,
    required String reason,
  }) async {
    final userId = await _anonymousUserId();
    final response = await _client
        .post(
          _uri('/api/recommendations'),
          headers: {
            'Content-Type': 'application/json; charset=utf-8',
            'X-MenuPick-User-Id': userId,
          },
          body: jsonEncode({
            'menuName': menuName,
            'reason': reason,
            // 비워두면 서버가 추천 메뉴를 생성합니다.
            'recommendedMenu': '',
          }),
        )
        .timeout(const Duration(seconds: 6));

    final body = _decodeJson(response);
    if (response.statusCode == 429 && body['error']?.toString() == 'monthly_limit_exceeded') {
      throw MonthlyRecommendationLimitException(
        body['message']?.toString() ?? '메뉴 추천은 한 달에 한 번만 올릴 수 있습니다.',
      );
    }
    if (response.statusCode != 201) {
      throw UserApiException(body['message']?.toString() ?? '추천 저장 실패');
    }

    return RecommendationDto.fromJson(body);
  }

  Future<RecommendationDto> fetchLatestRecommendation() async {
    final response = await _client
        .get(_uri('/api/recommendations/latest'))
        .timeout(const Duration(seconds: 6));

    final body = _decodeJson(response);
    // 결정메뉴가 아직 공개되지 않은 경우(또는 설정되지 않은 경우)
    // 사용자 화면에서는 기존 방식대로 placeholder를 보여주기 위해 null 처리합니다.
    if (response.statusCode == 404 && body['error']?.toString() == 'not_published') {
      throw NotPublishedException(body['publishAt']?.toString());
    }
    if (response.statusCode != 200) {
      throw UserApiException(body['message']?.toString() ?? '추천 조회 실패');
    }

    return RecommendationDto.fromJson(body);
  }

  Future<void> submitInquiry({required String message}) async {
    final response = await _client
        .post(
          _uri('/api/inquiries'),
          headers: const {'Content-Type': 'application/json; charset=utf-8'},
          body: jsonEncode({'message': message}),
        )
        .timeout(const Duration(seconds: 6));

    final body = _decodeJson(response);
    if (response.statusCode != 201) {
      throw UserApiException(body['message']?.toString() ?? '문의 저장 실패');
    }
  }

  Map<String, dynamic> _decodeJson(http.Response response) {
    try {
      final decoded = jsonDecode(utf8.decode(response.bodyBytes));
      return decoded is Map<String, dynamic>
          ? decoded
          : <String, dynamic>{'message': 'Invalid response'};
    } catch (_) {
      return <String, dynamic>{'message': 'Invalid response'};
    }
  }

  Future<String> _anonymousUserId() async {
    final prefs = await SharedPreferences.getInstance();
    final saved = prefs.getString(_kAnonymousUserId);
    if (saved != null && saved.isNotEmpty) {
      return saved;
    }

    final random = Random.secure();
    final bytes = List<int>.generate(24, (_) => random.nextInt(256));
    final userId = base64UrlEncode(bytes).replaceAll('=', '');
    await prefs.setString(_kAnonymousUserId, userId);
    return userId;
  }
}

class NotPublishedException implements Exception {
  const NotPublishedException(this.publishAt);

  /// 서버가 함께 내려준 공개 예정 시각(있을 수도/없을 수도 있음)
  final String? publishAt;

  @override
  String toString() => 'not_published';
}

class RecommendationDto {
  const RecommendationDto({
    required this.id,
    required this.menuName,
    required this.reason,
    required this.recommendedMenu,
    required this.createdAt,
  });

  final int id;
  final String menuName;
  final String reason;
  final String recommendedMenu;
  final String createdAt;

  factory RecommendationDto.fromJson(Map<String, dynamic> json) {
    return RecommendationDto(
      id: (json['id'] as num?)?.toInt() ?? 0,
      menuName: (json['menuName'] as String?) ?? '',
      reason: (json['reason'] as String?) ?? '',
      recommendedMenu: (json['recommendedMenu'] as String?) ?? '',
      createdAt: (json['createdAt'] as String?) ?? '',
    );
  }
}

class UserApiException implements Exception {
  UserApiException(this.message);
  final String message;

  @override
  String toString() => message;
}

class MonthlyRecommendationLimitException extends UserApiException {
  MonthlyRecommendationLimitException(super.message);
}
