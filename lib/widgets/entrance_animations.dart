import 'dart:math' as math;
import 'dart:ui';

import 'package:flutter/material.dart';

/// 간단한 온보딩/페이지 진입 애니메이션용 위젯들.
///
/// - 별도의 Timer 없이 `TweenAnimationBuilder` + (delay + duration)로 동작합니다.
/// - 테스트 환경에서도 영구 애니메이션이 남지 않도록 한 번만 재생됩니다.
class EntranceFadeSlide extends StatelessWidget {
  const EntranceFadeSlide({
    super.key,
    required this.child,
    this.delay = Duration.zero,
    this.duration = const Duration(milliseconds: 520),
    this.curve = Curves.easeOutCubic,
    this.fromYOffset = 22,
  });

  final Widget child;
  final Duration delay;
  final Duration duration;
  final Curve curve;
  final double fromYOffset;

  @override
  Widget build(BuildContext context) {
    final total = delay + duration;
    if (total == Duration.zero) {
      return child;
    }

    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0, end: 1),
      duration: total,
      curve: Curves.linear,
      child: child,
      builder: (context, raw, child) {
        final t = _applyDelay(raw, delay, total);
        final eased = curve.transform(t);

        return Opacity(
          opacity: eased,
          child: Transform.translate(
            offset: Offset(0, (1 - eased) * fromYOffset),
            child: child,
          ),
        );
      },
    );
  }
}

class EntranceBlurSlide extends StatelessWidget {
  const EntranceBlurSlide({
    super.key,
    required this.child,
    this.delay = Duration.zero,
    this.duration = const Duration(milliseconds: 650),
    this.curve = Curves.easeOutCubic,
    this.fromYOffset = 40,
    this.maxBlurSigma = 14,
    this.enableOpacity = true,
  });

  final Widget child;
  final Duration delay;
  final Duration duration;
  final Curve curve;
  final double fromYOffset;
  final double maxBlurSigma;
  final bool enableOpacity;

  @override
  Widget build(BuildContext context) {
    final total = delay + duration;
    if (total == Duration.zero) {
      return child;
    }

    // 접근성 설정(애니메이션 줄이기)이 켜져 있으면 정적으로 렌더링.
    final reduceMotion = MediaQuery.maybeOf(context)?.accessibleNavigation ?? false;
    if (reduceMotion) {
      return child;
    }

    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0, end: 1),
      duration: total,
      curve: Curves.linear,
      child: child,
      builder: (context, raw, child) {
        final t = _applyDelay(raw, delay, total);
        final eased = curve.transform(t);
        final blur = (1 - eased) * maxBlurSigma;
        final sigma = math.max(0.0, blur);

        Widget current = Transform.translate(
          offset: Offset(0, (1 - eased) * fromYOffset),
          child: ImageFiltered(
            imageFilter: ImageFilter.blur(sigmaX: sigma, sigmaY: sigma),
            child: child,
          ),
        );

        if (enableOpacity) {
          current = Opacity(opacity: eased, child: current);
        }

        return current;
      },
    );
  }
}

double _applyDelay(double raw, Duration delay, Duration total) {
  final totalUs = total.inMicroseconds;
  if (totalUs <= 0) return 1;

  final delayUs = delay.inMicroseconds;
  if (delayUs <= 0) return raw.clamp(0, 1);

  final start = delayUs / totalUs;
  if (raw <= start) {
    return 0;
  }
  final normalized = (raw - start) / (1 - start);
  return normalized.clamp(0, 1);
}

