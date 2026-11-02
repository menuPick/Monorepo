import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:user/main.dart';
import 'package:user/widgets/model_banner.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('실제 기기에서 메뉴를 입력하고 추천 결과 화면으로 이동한다', (
    WidgetTester tester,
  ) async {
    ModelBannerConfig.forcePlaceholder = true;
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
    await tester.pumpAndSettle(const Duration(seconds: 8));

    expect(find.text('관리자가 결정한 메뉴를 확인하세요'), findsOneWidget);
  });
}
