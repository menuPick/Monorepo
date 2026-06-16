import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:user/admin/pages/admin_login_page.dart';
import 'package:user/main.dart';
import 'package:user/widgets/model_banner.dart';

void main() {
  setUpAll(() {
    ModelBannerConfig.forcePlaceholder = true;
  });

  testWidgets('shows the menu recommendation home screen', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(const MyApp());

    expect(find.byKey(kModelBannerKey), findsOneWidget);
    expect(find.text('환영합니다'), findsOneWidget);
    expect(find.text('식단 추천 앱'), findsOneWidget);
    expect(find.text('메뉴픽'), findsWidgets);
    expect(find.text('메뉴를 추천 받아요.'), findsOneWidget);
    expect(find.text('추천메뉴를 확인하세요!'), findsOneWidget);
    expect(find.text('메뉴추천'), findsOneWidget);
    expect(find.text('메뉴확인'), findsOneWidget);
    expect(find.text('문의'), findsOneWidget);
    expect(find.text('모드'), findsOneWidget);
  });

  testWidgets('switches tabs using the bottom navigation bar', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(const MyApp());

    await tester.tap(find.text('메뉴추천'));
    await tester.pumpAndSettle();

    expect(find.text('이 메뉴 먹고 싶어요!'), findsOneWidget);

    await tester.tap(find.text('메뉴픽'));
    await tester.pumpAndSettle();

    expect(find.text('환영합니다'), findsOneWidget);
  });

  testWidgets('returns to home using the bottom navigation bar', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(const MyApp());

    await tester.tap(find.text('메뉴추천'));
    await tester.pumpAndSettle();

    await tester.tap(find.text('메뉴픽'));
    await tester.pumpAndSettle();

    expect(find.text('환영합니다'), findsOneWidget);
    expect(find.text('메뉴를 추천 받아요.'), findsOneWidget);
  });

  testWidgets('navigates to the recommendation input page', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(const MyApp());

    await tester.tap(find.text('메뉴를 추천 받아요.'));
    await tester.pumpAndSettle();

    expect(find.text('이 메뉴 먹고 싶어요!'), findsOneWidget);
    expect(find.text('메뉴를 올려봅시다.'), findsOneWidget);
  });

  testWidgets('navigates to the recommendation result page', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(const MyApp());

    await tester.scrollUntilVisible(find.text('추천메뉴를 확인하세요!'), 200);
    await tester.tap(find.text('추천메뉴를 확인하세요!'));
    await tester.pumpAndSettle();

    expect(find.text('관리자가 결정한 메뉴를 확인하세요'), findsOneWidget);
  });

  testWidgets('navigates to the about page from the bottom navigation', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(const MyApp());

    await tester.tap(find.text('문의'));
    await tester.pumpAndSettle();

    expect(find.text('관리자 계정 이메일'), findsOneWidget);
    expect(find.text('junsumon090608@dgsw.hs.kr'), findsOneWidget);
  });

  testWidgets('toggles dark mode from mode menu', (WidgetTester tester) async {
    await tester.pumpWidget(const MyApp());

    await tester.tap(find.text('모드'));
    await tester.pumpAndSettle();

    final welcomeText = tester.widget<Text>(find.text('환영합니다'));
    expect(welcomeText.style?.color, Colors.white);
  });

  testWidgets('admin login masks the admin ID and can reveal it', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(home: AdminLoginPage(onLoginSuccess: () {})),
    );

    TextField adminIdField = tester.widget<TextField>(find.byType(TextField));
    expect(adminIdField.obscureText, isTrue);
    expect(find.byIcon(Icons.visibility_outlined), findsOneWidget);

    await tester.enterText(find.byType(TextField), 'secret-admin');
    await tester.tap(find.byTooltip('관리자 ID 보기'));
    await tester.pump();

    adminIdField = tester.widget<TextField>(find.byType(TextField));
    expect(adminIdField.obscureText, isFalse);
    expect(find.byIcon(Icons.visibility_off_outlined), findsOneWidget);
  });
}
