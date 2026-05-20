import 'package:flutter/material.dart';
import 'package:user/data/user_api.dart';
import 'package:user/widgets/app_drawer.dart';
import 'package:user/widgets/entrance_animations.dart';
import 'package:user/widgets/model_banner.dart';

class RecommendationResultPage extends StatefulWidget {
  const RecommendationResultPage({super.key});

  @override
  State<RecommendationResultPage> createState() => _RecommendationResultPageState();
}

class _RecommendationResultPageState extends State<RecommendationResultPage> {
  late Future<_LatestState> _latestFuture;

  final UserApi _api = UserApi();

  @override
  void initState() {
    super.initState();
    _latestFuture = _loadLatest();
  }

  Future<_LatestState> _loadLatest() async {
    // 위젯 테스트에서는 플러그인(sqflite/http) 의존을 제거해 안정적으로 동작하도록
    // 즉시 placeholder만 렌더링합니다.
    if (kIsFlutterTest) {
      return const _LatestState.legacy(null);
    }

    try {
      final dto = await _api.fetchLatestRecommendation();
      return _LatestState.published(_LatestResult.fromApi(dto));
    } on NotPublishedException catch (_) {
      // 서버가 not_published를 내려준 경우는 아래 2가지입니다.
      // 1) 관리자가 아직 결정/공개를 설정하지 않음
      // 2) 관리자가 결정/공개를 설정했지만 아직 공개 시간이 아님
      // 사용자 플로우 상, 이 두 경우 모두 '관리자가 결정 중입니다'로 통일합니다.
      // (날짜/남은시간 표시 및 추천 직후 즉시 노출 방지)
      return const _LatestState.deciding();
    } on UserApiException catch (e) {
      return _LatestState.error('서버 응답 오류: ${e.message}');
    } catch (_) {
      // 서버 미실행 / 네트워크 차단(CORS 등) / 주소 오입력 등
      return const _LatestState.error(
        '서버에 연결할 수 없습니다.\n\n'
        '1) 서버가 실행 중인지 확인하세요.\n'
        '2) 웹 실행 시 API_BASE_URL이 올바른지 확인하세요.\n'
        '   예) flutter run --dart-define=API_BASE_URL=http://localhost:8080'
      );
    }
  }

  void _reload() {
    setState(() {
      _latestFuture = _loadLatest();
    });
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final backgroundColor = isDark ? Colors.black : Colors.white;
    final borderColor = isDark ? const Color(0xFFB5B5B5) : const Color(0xFFB8B8B8);
    final textColor = isDark ? Colors.white : Colors.black;
    final hintColor = isDark ? const Color(0xFF8F8F8F) : const Color(0xFFA8A8A8);

    return Scaffold(
      backgroundColor: backgroundColor,
      drawer: const AppDrawer(),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(24, 18, 24, 28),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              EntranceFadeSlide(
                fromYOffset: 10,
                duration: const Duration(milliseconds: 420),
                child: Builder(
                  builder: (context) => _MenuButton(
                    color: borderColor,
                    onTap: () => Scaffold.of(context).openDrawer(),
                  ),
                ),
              ),
              const SizedBox(height: 16),
              const ModelBanner(),
              const SizedBox(height: 12),
              const SizedBox(height: 18),
              EntranceFadeSlide(
                delay: const Duration(milliseconds: 90),
                fromYOffset: 14,
                duration: const Duration(milliseconds: 520),
                child: Text(
                  '결정된 메뉴는?',
                  style: TextStyle(
                    color: textColor,
                    fontSize: 34,
                    height: 1.1,
                    fontWeight: FontWeight.w900,
                  ),
                ),
              ),
              const SizedBox(height: 26),
              EntranceFadeSlide(
                delay: const Duration(milliseconds: 170),
                fromYOffset: 18,
                duration: const Duration(milliseconds: 560),
                child: FutureBuilder<_LatestState>(
                  future: _latestFuture,
                  builder: (context, snapshot) {
                    final state = snapshot.data;

                  // 로딩 중에도 테스트/실사용 모두에서 화면이 즉시 그려지도록
                  // 애니메이션 대신 정적인 placeholder를 사용합니다.
                  if (snapshot.connectionState != ConnectionState.done || state == null) {
                    return Column(
                      children: [
                        _ValueBox(
                          value: '불러오는 중입니다',
                          borderColor: borderColor,
                          hintColor: hintColor,
                          height: 72,
                        ),
                        const SizedBox(height: 24),
                        _ValueBox(
                          value: '불러오는 중입니다',
                          borderColor: borderColor,
                          hintColor: hintColor,
                          height: 260,
                        ),
                      ],
                    );
                  }

                  String menuText;
                  String reasonText;

                  if (state.kind == _LatestStateKind.deciding) {
                    menuText = '관리자가 결정 중입니다';
                    reasonText = '관리자가 결정 중입니다';
                  } else if (state.kind == _LatestStateKind.error) {
                    menuText = state.message ?? '서버 오류가 발생했습니다.';
                    reasonText = state.message ?? '서버 오류가 발생했습니다.';
                  } else {
                    final latest = state.result;
                    menuText = latest == null ? '결정메뉴가 아직 나오지 않았어요' : latest.recommendedMenu;
                    reasonText = latest == null
                        ? '결정메뉴가 아직 나오지 않았어요'
                        : '원한 메뉴: ${latest.menuName}\n이유: ${latest.reason}';
                  }

                    return Column(
                      children: [
                        _ValueBox(
                          value: menuText,
                          borderColor: borderColor,
                          hintColor: hintColor,
                          height: 72,
                        ),
                        const SizedBox(height: 24),
                        _ValueBox(
                          value: reasonText,
                          borderColor: borderColor,
                          hintColor: hintColor,
                          height: 260,
                        ),
                      ],
                    );
                  },
                ),
              ),
              const SizedBox(height: 28),
              EntranceFadeSlide(
                delay: const Duration(milliseconds: 260),
                fromYOffset: 14,
                duration: const Duration(milliseconds: 560),
                child: _PrimaryButton(
                  label: '맛있게 드십시오!',
                  backgroundColor: const Color(0xFF0B18F1),
                  textColor: Colors.white,
                  // 서버 메뉴가 아직 공개되지 않았거나(결정 중),
                  // 네트워크 오류가 발생한 경우에도 사용자가 다시 시도할 수 있도록
                  // 버튼을 새로고침으로 동작시킵니다.
                  onTap: _reload,
                ),
              ),
              const SizedBox(height: 20),
              EntranceFadeSlide(
                delay: const Duration(milliseconds: 300),
                fromYOffset: 14,
                duration: const Duration(milliseconds: 560),
                child: _PrimaryButton(
                  label: 'AI에게 추천받기',
                  backgroundColor: isDark ? const Color(0xFFB6B8F3) : const Color(0xFFB6B8F3),
                  textColor: Colors.white,
                  onTap: () {},
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _MenuButton extends StatelessWidget {
  const _MenuButton({required this.onTap, required this.color});

  final VoidCallback onTap;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 50,
        height: 50,
        decoration: BoxDecoration(
          color: Theme.of(context).brightness == Brightness.dark ? Colors.black : Colors.white,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: color, width: 1),
        ),
        child: Icon(
          Icons.menu_rounded,
          color: Theme.of(context).brightness == Brightness.dark ? Colors.white : Colors.black,
          size: 34,
        ),
      ),
    );
  }
}

class _ValueBox extends StatelessWidget {
  const _ValueBox({
    required this.value,
    required this.borderColor,
    required this.hintColor,
    required this.height,
  });

  final String value;
  final Color borderColor;
  final Color hintColor;
  final double height;

  @override
  Widget build(BuildContext context) {
    return Container(
      height: height,
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 18),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(32),
        border: Border.all(color: borderColor, width: 1),
      ),
      child: Align(
        alignment: Alignment.topLeft,
        child: Text(
          value,
          style: TextStyle(
            color: hintColor,
            fontSize: 18,
            fontWeight: FontWeight.w400,
            height: 1.45,
          ),
        ),
      ),
    );
  }
}

class _PrimaryButton extends StatelessWidget {
  const _PrimaryButton({
    required this.label,
    required this.backgroundColor,
    required this.textColor,
    required this.onTap,
  });

  final String label;
  final Color backgroundColor;
  final Color textColor;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      height: 70,
      child: ElevatedButton(
        onPressed: onTap,
        style: ElevatedButton.styleFrom(
          backgroundColor: backgroundColor,
          foregroundColor: textColor,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(999),
          ),
        ),
        child: Text(
          label,
          style: const TextStyle(
            fontSize: 18,
            fontWeight: FontWeight.w800,
          ),
        ),
      ),
    );
  }
}

class _LatestResult {
  const _LatestResult({
    required this.menuName,
    required this.reason,
    required this.recommendedMenu,
  });

  final String menuName;
  final String reason;
  final String recommendedMenu;

  factory _LatestResult.fromApi(RecommendationDto dto) {
    return _LatestResult(
      menuName: dto.menuName,
      reason: dto.reason,
      recommendedMenu: dto.recommendedMenu,
    );
  }
}

enum _LatestStateKind {
  published,
  deciding,
  legacy,
  error,
}

class _LatestState {
  const _LatestState._(this.kind, this.result, this.message);

  final _LatestStateKind kind;

  /// published 또는 legacy(기존 방식)에서 표시할 값. deciding 상태에서는 null.
  final _LatestResult? result;

  /// error 상태에서 표시할 메시지
  final String? message;

  const _LatestState.published(_LatestResult value)
      : this._(_LatestStateKind.published, value, null);

  const _LatestState.legacy(_LatestResult? value)
      : this._(_LatestStateKind.legacy, value, null);

  const _LatestState.deciding() : this._(_LatestStateKind.deciding, null, null);

  const _LatestState.error(String message)
      : this._(_LatestStateKind.error, null, message);
}
