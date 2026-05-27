import 'dart:convert';

import 'package:http/http.dart' as http;

import '../models/admin_models.dart';

class AdminApi {
  AdminApi({required this.baseUrl});

  /// 예) http://localhost:8080
  final String baseUrl;

  static String normalizeBaseUrl(String raw) {
    final trimmed = raw.trim();
    if (trimmed.isEmpty) return trimmed;
    return trimmed.endsWith('/') ? trimmed.substring(0, trimmed.length - 1) : trimmed;
  }

  Uri _uri(String path, [Map<String, String>? query]) {
    return Uri.parse('$baseUrl$path').replace(queryParameters: query);
  }

  Future<AdminSession> login({required String id}) async {
    final response = await http.post(
      _uri('/api/admin/login'),
      headers: const {
        'Content-Type': 'application/json; charset=utf-8',
      },
      body: jsonEncode({'id': id}),
    );

    final body = _decodeJson(response);
    if (response.statusCode != 200) {
      throw AdminApiException(body['message']?.toString() ?? '로그인 실패');
    }

    final token = body['token']?.toString() ?? '';
    final expiresAtRaw = body['expiresAt']?.toString() ?? '';
    final adminEmail = body['adminEmail']?.toString() ?? '';

    final expiresAt = DateTime.tryParse(expiresAtRaw);
    if (token.isEmpty || expiresAt == null) {
      throw AdminApiException('서버 응답이 올바르지 않습니다.');
    }

    return AdminSession(token: token, expiresAt: expiresAt, adminEmail: adminEmail);
  }

  Future<List<RecommendationItem>> fetchRecommendations({required String token, int limit = 50}) async {
    final response = await http.get(
      _uri('/api/admin/recommendations', {'limit': '$limit'}),
      headers: {
        'Authorization': 'Bearer $token',
      },
    );

    final body = _decodeJson(response);
    if (response.statusCode == 401) {
      throw AdminUnauthorizedException(body['message']?.toString() ?? '세션이 만료되었습니다. 다시 로그인해주세요.');
    }
    if (response.statusCode != 200) {
      throw AdminApiException(body['message']?.toString() ?? '추천 목록 조회 실패');
    }

    final items = (body['items'] as List?) ?? const [];
    return items
        .whereType<Map<String, dynamic>>()
        .map(RecommendationItem.fromJson)
        .toList(growable: false);
  }

  Future<List<InquiryItem>> fetchInquiries({required String token, int limit = 50}) async {
    final response = await http.get(
      _uri('/api/admin/inquiries', {'limit': '$limit'}),
      headers: {
        'Authorization': 'Bearer $token',
      },
    );

    final body = _decodeJson(response);
    if (response.statusCode == 401) {
      throw AdminUnauthorizedException(body['message']?.toString() ?? '세션이 만료되었습니다. 다시 로그인해주세요.');
    }
    if (response.statusCode != 200) {
      throw AdminApiException(body['message']?.toString() ?? '문의 목록 조회 실패');
    }

    final items = (body['items'] as List?) ?? const [];
    return items
        .whereType<Map<String, dynamic>>()
        .map(InquiryItem.fromJson)
        .toList(growable: false);
  }

  Future<void> setDecision({
    required String token,
    required int recommendationId,
    required DateTime publishAt,
  }) async {
    final response = await http.post(
      _uri('/api/admin/decision'),
      headers: {
        'Content-Type': 'application/json; charset=utf-8',
        'Authorization': 'Bearer $token',
      },
      body: jsonEncode({
        'recommendationId': recommendationId,
        // 서버는 Instant.parse 가능한 ISO-8601(Z) 문자열을 요구합니다.
        'publishAt': publishAt.toUtc().toIso8601String(),
      }),
    );

    final body = _decodeJson(response);
    if (response.statusCode == 401) {
      throw AdminUnauthorizedException(body['message']?.toString() ?? '세션이 만료되었습니다. 다시 로그인해주세요.');
    }
    if (response.statusCode != 200) {
      throw AdminApiException(body['message']?.toString() ?? '결정 메뉴 설정 실패');
    }
  }

  /// 선택한 추천 항목들을 삭제합니다.
  ///
  /// 서버: POST /api/admin/recommendations
  /// body: {"ids":[1,2,3]}
  Future<int> deleteRecommendations({required String token, required List<int> ids}) async {
    if (ids.isEmpty) return 0;

    final response = await http.post(
      _uri('/api/admin/recommendations'),
      headers: {
        'Content-Type': 'application/json; charset=utf-8',
        'Authorization': 'Bearer $token',
      },
      body: jsonEncode({'ids': ids}),
    );

    final body = _decodeJson(response);
    if (response.statusCode == 401) {
      throw AdminUnauthorizedException(body['message']?.toString() ?? '세션이 만료되었습니다. 다시 로그인해주세요.');
    }
    if (response.statusCode == 405) {
      throw AdminApiException('서버가 삭제 기능(POST/DELETE)을 지원하지 않습니다. 서버를 업데이트/재시작한 뒤 다시 시도해주세요.');
    }
    if (response.statusCode != 200) {
      throw AdminApiException(body['message']?.toString() ?? '삭제 실패');
    }

    return (body['deleted'] as num?)?.toInt() ?? 0;
  }

  /// 선택한 문의 항목들을 삭제합니다.
  ///
  /// 서버: POST /api/admin/inquiries
  /// body: {"ids":[1,2,3]}
  Future<int> deleteInquiries({required String token, required List<int> ids}) async {
    if (ids.isEmpty) return 0;

    final response = await http.post(
      _uri('/api/admin/inquiries'),
      headers: {
        'Content-Type': 'application/json; charset=utf-8',
        'Authorization': 'Bearer $token',
      },
      body: jsonEncode({'ids': ids}),
    );

    final body = _decodeJson(response);
    if (response.statusCode == 401) {
      throw AdminUnauthorizedException(body['message']?.toString() ?? '세션이 만료되었습니다. 다시 로그인해주세요.');
    }
    if (response.statusCode == 405) {
      throw AdminApiException('서버가 삭제 기능(POST/DELETE)을 지원하지 않습니다. 서버를 업데이트/재시작한 뒤 다시 시도해주세요.');
    }
    if (response.statusCode != 200) {
      throw AdminApiException(body['message']?.toString() ?? '삭제 실패');
    }

    return (body['deleted'] as num?)?.toInt() ?? 0;
  }

  Map<String, dynamic> _decodeJson(http.Response response) {
    try {
      final decoded = jsonDecode(utf8.decode(response.bodyBytes));
      if (decoded is Map<String, dynamic>) {
        return decoded;
      }
      return <String, dynamic>{'message': 'Invalid response'};
    } catch (_) {
      return <String, dynamic>{'message': 'Invalid response'};
    }
  }
}

class AdminApiException implements Exception {
  AdminApiException(this.message);
  final String message;

  @override
  String toString() => message;
}

class AdminUnauthorizedException extends AdminApiException {
  AdminUnauthorizedException(super.message);
}

