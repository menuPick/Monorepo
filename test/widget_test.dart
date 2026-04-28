import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:user/main.dart';

void main() {
  testWidgets('shows the menu recommendation home screen', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(const MyApp());

    expect(find.text('환영합니다'), findsOneWidget);
    expect(find.text('식단 추천 앱'), findsOneWidget);
    expect(find.text('메뉴픽'), findsOneWidget);
    expect(find.byIcon(Icons.menu_rounded), findsOneWidget);
    expect(find.text('메뉴를 추천 받아요.'), findsOneWidget);
    expect(find.text('추천메뉴를 확인하세요!'), findsOneWidget);
  });

  testWidgets('opens the drawer from the home menu button', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(const MyApp());

    await tester.tap(find.byIcon(Icons.menu_rounded));
    await tester.pumpAndSettle();

    expect(find.text('메뉴픽'), findsWidgets);
    expect(find.text('메뉴추천'), findsOneWidget);
    expect(find.text('메뉴확인'), findsOneWidget);
    expect(find.text('문의'), findsOneWidget);
    expect(find.text('모드'), findsOneWidget);
  });

  testWidgets('returns to home when tapping menu pick in the drawer', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(const MyApp());

    await tester.tap(find.text('메뉴를 추천 받아요.'));
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.menu_rounded));
    await tester.pumpAndSettle();
    await tester.tap(find.text('메뉴픽').first);
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

    await tester.tap(find.text('추천메뉴를 확인하세요!'));
    await tester.pumpAndSettle();

    expect(find.text('결정된 메뉴는?'), findsOneWidget);
    expect(find.text('맛있게 드십시오!'), findsOneWidget);
  });

  testWidgets('navigates to the about page from the drawer', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(const MyApp());

    await tester.tap(find.byIcon(Icons.menu_rounded));
    await tester.pumpAndSettle();

    await tester.tap(find.text('문의'));
    await tester.pumpAndSettle();

    expect(find.text('문의'), findsOneWidget);
    expect(find.text('관리자 계정 이메일'), findsOneWidget);
    expect(find.text('junsumon090608@dgsw.hs.kr'), findsOneWidget);
  });

  testWidgets('toggles dark mode from drawer mode menu', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(const MyApp());

    await tester.tap(find.byIcon(Icons.menu_rounded));
    await tester.pumpAndSettle();
    await tester.tap(find.text('모드'));
    await tester.pumpAndSettle();

    final welcomeText = tester.widget<Text>(find.text('환영합니다'));
    expect(welcomeText.style?.color, Colors.white);
  });
}
