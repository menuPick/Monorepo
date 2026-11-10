import 'dart:async';

import 'package:flutter/material.dart';

import 'package:user/navigation/app_routes.dart';

/// 메뉴 추천을 올린 뒤 AI(로컬 LLM)가 답을 만드는 동안 보여주는 로딩 페이지.
///
/// [task]는 성공하면 null, 실패하면 사용자에게 보여줄 오류 메시지를 돌려줍니다.
/// - 성공: 결과 페이지로 교체 이동합니다(뒤로 가기 시 입력 페이지).
/// - 실패: 입력 페이지로 돌아가며 오류 메시지를 넘깁니다.
/// 작업 중에는 뒤로 가기를 막아 같은 추천이 두 번 올라가지 않게 합니다.
class AiLoadingPage extends StatefulWidget {
  const AiLoadingPage({super.key, required this.task});

  final Future<String?> Function() task;

  @override
  State<AiLoadingPage> createState() => _AiLoadingPageState();
}

class _AiLoadingPageState extends State<AiLoadingPage>
    with SingleTickerProviderStateMixin {
  static const List<String> _steps = [
    '입력한 메뉴와 이유를 읽고 있어요',
    '급식으로 알맞은 메뉴를 고르고 있어요',
    '안전한 메뉴인지 확인하고 있어요',
    '답변을 정리하고 있어요',
  ];

  late final AnimationController _pulse = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1200),
  )..repeat(reverse: true);
  late final Timer _ticker;
  int _elapsedSeconds = 0;

  @override
  void initState() {
    super.initState();
    _ticker = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted) setState(() => _elapsedSeconds++);
    });
    _run();
  }

  Future<void> _run() async {
    final error = await widget.task();
    if (!mounted) return;
    if (error == null) {
      Navigator.of(context).pushReplacementNamed(AppRoutes.result);
    } else {
      Navigator.of(context).pop(error);
    }
  }

  @override
  void dispose() {
    _ticker.cancel();
    _pulse.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final background = isDark ? Colors.black : Colors.white;
    final textColor = isDark ? Colors.white : Colors.black;
    final subColor = isDark ? const Color(0xFFB5B5B5) : const Color(0xFF6B6B6B);
    const accent = Color(0xFF0B18F1);
    final step = _steps[(_elapsedSeconds ~/ 3) % _steps.length];

    return PopScope(
      canPop: false,
      child: Scaffold(
        backgroundColor: background,
        body: SafeArea(
          child: Center(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 32),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  ScaleTransition(
                    scale: Tween(begin: 0.88, end: 1.08).animate(
                      CurvedAnimation(parent: _pulse, curve: Curves.easeInOut),
                    ),
                    child: Container(
                      width: 112,
                      height: 112,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: accent.withValues(alpha: isDark ? 0.28 : 0.1),
                      ),
                      child: Icon(
                        Icons.auto_awesome_rounded,
                        size: 52,
                        color: isDark ? const Color(0xFFBFC4FF) : accent,
                      ),
                    ),
                  ),
                  const SizedBox(height: 32),
                  Text(
                    'AI 답변 생성 중',
                    style: TextStyle(
                      color: textColor,
                      fontSize: 30,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                  const SizedBox(height: 14),
                  AnimatedSwitcher(
                    duration: const Duration(milliseconds: 350),
                    child: Text(
                      step,
                      key: ValueKey(step),
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        color: subColor,
                        fontSize: 17,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                  const SizedBox(height: 32),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(999),
                    child: LinearProgressIndicator(
                      minHeight: 6,
                      color: isDark ? const Color(0xFFBFC4FF) : accent,
                      backgroundColor: accent.withValues(alpha: 0.12),
                    ),
                  ),
                  const SizedBox(height: 16),
                  Text(
                    '$_elapsedSeconds초 경과 · 보통 10~40초 걸려요',
                    style: TextStyle(color: subColor, fontSize: 14),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
