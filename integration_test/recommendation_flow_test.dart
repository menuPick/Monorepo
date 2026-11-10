import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:user/admin/admin_app.dart';
import 'package:user/admin/services/admin_session_store.dart';
import 'package:user/main.dart';
import 'package:user/widgets/model_banner.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(() {
    ModelBannerConfig.forcePlaceholder = false;
    ModelBannerConfig.forceRealModel = true;
  });

  testWidgets('사용자 앱의 첫 화면과 하단 메뉴를 실제 기기에서 이동한다', (WidgetTester tester) async {
    await tester.pumpWidget(const MyApp());
    await tester.pumpAndSettle(const Duration(seconds: 2));

    expect(find.text('환영합니다'), findsOneWidget);
    expect(find.text('메뉴를 추천 받아요.'), findsOneWidget);
    expect(find.text('추천메뉴를 확인하세요!'), findsOneWidget);

    await tester.tap(find.text('메뉴추천'));
    await tester.pumpAndSettle();
    expect(find.text('이 메뉴 먹고 싶어요!'), findsOneWidget);

    await tester.tap(find.text('문의'));
    await tester.pumpAndSettle();
    expect(find.text('관리자 계정 이메일'), findsOneWidget);

    await tester.tap(find.text('모드'));
    await tester.pumpAndSettle();
    expect(find.text('문의'), findsWidgets);
  });

  testWidgets('사용자가 실제 기기에서 메뉴를 입력하고 추천 결과로 이동한다', (WidgetTester tester) async {
    await tester.pumpWidget(const MyApp());
    await tester.pumpAndSettle(const Duration(seconds: 2));

    await tester.tap(find.text('메뉴를 추천 받아요.'));
    await tester.pumpAndSettle();

    final fields = find.byType(TextField);
    expect(fields, findsNWidgets(2));
    await tester.enterText(fields.at(0), '라면');
    await tester.enterText(fields.at(1), '간단하고 따뜻한 음식이 먹고 싶어요');

    final submit = find.text('메뉴를 올려봅시다.');
    await tester.ensureVisible(submit);
    await tester.tap(submit);
    await tester.pump(const Duration(milliseconds: 500));
    expect(find.text('AI 답변 생성 중'), findsOneWidget);

    // AI(로컬 LLM) 응답을 기다립니다. 로딩 페이지는 애니메이션이 계속돼 pumpAndSettle을 쓰지 않습니다.
    final deadline = DateTime.now().add(const Duration(seconds: 120));
    while (find.text('AI 답변 생성 중').evaluate().isNotEmpty &&
        DateTime.now().isBefore(deadline)) {
      await tester.pump(const Duration(milliseconds: 500));
    }
    await tester.pumpAndSettle(const Duration(seconds: 2));

    expect(find.text('관리자가 결정한 메뉴를 확인하세요'), findsOneWidget);
  });

  testWidgets('사용자가 실제 기기에서 문의를 입력하고 저장한다', (WidgetTester tester) async {
    await tester.pumpWidget(const MyApp());
    await tester.pumpAndSettle(const Duration(seconds: 2));

    await tester.tap(find.text('문의'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byType(TextField),
      'integration test 문의 ${DateTime.now().millisecondsSinceEpoch}',
    );
    await tester.tap(find.text('문의 저장'));
    await tester.pumpAndSettle(const Duration(seconds: 8));

    expect(find.text('문의가 서버에 저장되었습니다.'), findsOneWidget);
  });

  testWidgets('관리자가 실제 기기에서 로그인하고 관리 화면을 이동한다', (WidgetTester tester) async {
    await AdminSessionStore.clear();
    await tester.pumpWidget(const AdminApp());
    await tester.pumpAndSettle(const Duration(seconds: 2));

    expect(find.text('MenuPick Admin'), findsOneWidget);
    await tester.enterText(find.byType(TextField), 'admin');
    await tester.tap(find.text('로그인'));
    await tester.pumpAndSettle(const Duration(seconds: 8));

    expect(find.text('추천 관리'), findsWidgets);

    await tester.tap(find.byIcon(Icons.menu));
    await tester.pumpAndSettle();
    expect(find.text('문의 관리'), findsOneWidget);
    await tester.tap(find.text('문의 관리'));
    await tester.pumpAndSettle(const Duration(seconds: 3));
    expect(find.text('문의 관리'), findsWidgets);

    final themeButton = find.byTooltip('모드 전환').first;
    await tester.tap(themeButton);
    await tester.pumpAndSettle();

    final logoutButton = find.byTooltip('로그아웃').first;
    await tester.tap(logoutButton);
    await tester.pumpAndSettle(const Duration(seconds: 2));
    expect(find.text('MenuPick Admin'), findsOneWidget);
  });
}
