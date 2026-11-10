import 'dart:js_interop';
import 'dart:typed_data';

import 'package:web/web.dart' as web;

/// 브라우저에서 파일을 내려받습니다. package:web 사용(JS·WebAssembly 빌드 모두 지원).
Future<void> downloadBytes({
  required String filename,
  required List<int> bytes,
  required String mimeType,
}) async {
  final data = Uint8List.fromList(bytes).toJS;
  final blob = web.Blob(
    <JSAny>[data].toJS,
    web.BlobPropertyBag(type: mimeType),
  );
  final url = web.URL.createObjectURL(blob);
  final anchor = web.HTMLAnchorElement()
    ..href = url
    ..download = filename
    ..style.display = 'none';
  web.document.body?.append(anchor);
  anchor.click();
  anchor.remove();
  web.URL.revokeObjectURL(url);
}
