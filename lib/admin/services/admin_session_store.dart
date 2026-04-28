import 'package:shared_preferences/shared_preferences.dart';

import '../models/admin_models.dart';

class AdminSessionStore {
  static const _kToken = 'admin_token';
  static const _kExpiresAt = 'admin_expires_at';
  static const _kAdminEmail = 'admin_email';

  static Future<AdminSession?> load() async {
    final prefs = await SharedPreferences.getInstance();
    final token = prefs.getString(_kToken);
    final expiresAtRaw = prefs.getString(_kExpiresAt);
    final adminEmail = prefs.getString(_kAdminEmail);

    if (token == null || expiresAtRaw == null || adminEmail == null) {
      return null;
    }

    final expiresAt = DateTime.tryParse(expiresAtRaw);
    if (expiresAt == null) {
      await clear();
      return null;
    }

    final session = AdminSession(token: token, expiresAt: expiresAt, adminEmail: adminEmail);
    if (session.isExpired) {
      await clear();
      return null;
    }

    return session;
  }

  static Future<void> save(AdminSession session) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_kToken, session.token);
    await prefs.setString(_kExpiresAt, session.expiresAt.toIso8601String());
    await prefs.setString(_kAdminEmail, session.adminEmail);
  }

  static Future<void> clear() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_kToken);
    await prefs.remove(_kExpiresAt);
    await prefs.remove(_kAdminEmail);
  }
}

