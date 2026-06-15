import '../models/admin_models.dart';

class AdminSessionStore {
  static AdminSession? _session;

  static Future<AdminSession?> load() async {
    final session = _session;
    if (session == null) return null;
    if (session.isExpired) {
      await clear();
      return null;
    }

    return session;
  }

  static Future<void> save(AdminSession session) async {
    _session = session;
  }

  static Future<void> clear() async {
    _session = null;
  }
}
