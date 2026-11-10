import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:user/navigation/app_routes.dart';
import 'package:user/pages/ai_loading_page.dart';

Widget _app(Future<String?> Function() task, ValueChanged<String?> onResult) {
  return MaterialApp(
    routes: {AppRoutes.result: (_) => const Text('결과 페이지')},
    home: Builder(
      builder: (context) => TextButton(
        onPressed: () async {
          final result = await Navigator.of(context).push<String>(
            MaterialPageRoute(builder: (_) => AiLoadingPage(task: task)),
          );
          onResult(result);
        },
        child: const Text('올리기'),
      ),
    ),
  );
}

void main() {
  testWidgets('AI 응답을 기다리는 동안 로딩 문구를 보여주고, 성공하면 결과 페이지로 간다', (tester) async {
    final completer = Completer<String?>();
    await tester.pumpWidget(_app(() => completer.future, (_) {}));
    await tester.tap(find.text('올리기'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));

    expect(find.text('AI 답변 생성 중'), findsOneWidget);
    await tester.pump(const Duration(seconds: 3));
    expect(find.textContaining('3초 경과'), findsOneWidget);

    completer.complete(null);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pump(const Duration(seconds: 1)); // 화면 전환 애니메이션 종료
    expect(find.text('결과 페이지'), findsOneWidget);
    expect(find.text('AI 답변 생성 중'), findsNothing);
  });

  testWidgets('실패하면 오류 메시지를 들고 이전 화면으로 돌아간다', (tester) async {
    String? returned;
    await tester.pumpWidget(_app(() async => '서버 저장 실패', (r) => returned = r));
    await tester.tap(find.text('올리기'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));

    expect(find.text('올리기'), findsOneWidget);
    expect(returned, '서버 저장 실패');
  });
}
