import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:model_viewer_plus/model_viewer_plus.dart';

const bool kIsWidgetTest = bool.fromEnvironment('FLUTTER_TEST');
const Key kModelBannerKey = Key('model-3d-banner');

class ModelBannerConfig {
  static bool forcePlaceholder = false;
}

class ModelBanner extends StatelessWidget {
  const ModelBanner({super.key, this.height = 160});

  final double height;

  @override
  Widget build(BuildContext context) {
    if (ModelBannerConfig.forcePlaceholder || kIsWidgetTest) {
      return Container(
        key: kModelBannerKey,
        height: height,
        width: double.infinity,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: Theme.of(context).brightness == Brightness.dark
              ? const Color(0xFF1B1B1B)
              : const Color(0xFFF2F2F2),
          borderRadius: BorderRadius.circular(16),
        ),
        child: const Icon(Icons.pets, size: 48, color: Colors.grey),
      );
    }

    final assetSrc = kIsWeb
        ? Uri.base.resolve('assets/assets/3d/cute_cat.glb').toString()
        : 'assets/3d/cute_cat.glb';

    return SizedBox(
      key: kModelBannerKey,
      height: height,
      width: double.infinity,
      child: ModelViewer(
        src: assetSrc,
        autoRotate: true,
        cameraControls: true,
        cameraOrbit: '90deg 60deg 2.5m',
        cameraTarget: '0m 0.4m 0m',
        backgroundColor: Colors.transparent,
        disableZoom: false,
      ),
    );
  }
}
