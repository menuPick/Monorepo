import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';

class RecommendationRecord {
  const RecommendationRecord({
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

  factory RecommendationRecord.fromMap(Map<String, Object?> map) {
    return RecommendationRecord(
      id: map['id'] as int,
      menuName: (map['menu_name'] as String?) ?? '',
      reason: (map['reason'] as String?) ?? '',
      recommendedMenu: (map['recommended_menu'] as String?) ?? '',
      createdAt: (map['created_at'] as String?) ?? '',
    );
  }
}

class RecommendationLocalDb {
  RecommendationLocalDb._();

  static final RecommendationLocalDb instance = RecommendationLocalDb._();

  Database? _database;

  Future<Database> get database async {
    if (_database != null) {
      return _database!;
    }

    final dbPath = await getDatabasesPath();
    final path = p.join(dbPath, 'menu_pick.db');

    _database = await openDatabase(
      path,
      version: 1,
      onCreate: (db, version) async {
        await db.execute('''
          CREATE TABLE recommendations (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            menu_name TEXT NOT NULL,
            reason TEXT NOT NULL,
            recommended_menu TEXT NOT NULL,
            created_at TEXT NOT NULL
          )
        ''');
      },
    );

    return _database!;
  }

  Future<void> saveRecommendation({
    required String menuName,
    required String reason,
    required String recommendedMenu,
  }) async {
    final db = await database;
    await db.insert('recommendations', {
      'menu_name': menuName,
      'reason': reason,
      'recommended_menu': recommendedMenu,
      'created_at': DateTime.now().toIso8601String(),
    });
  }

  Future<RecommendationRecord?> getLatestRecommendation() async {
    final db = await database;
    final rows = await db.query(
      'recommendations',
      orderBy: 'id DESC',
      limit: 1,
    );

    if (rows.isEmpty) {
      return null;
    }

    return RecommendationRecord.fromMap(rows.first);
  }
}

