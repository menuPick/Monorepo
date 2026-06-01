Future<void> downloadBytes({
  required String filename,
  required List<int> bytes,
  required String mimeType,
}) async {
  throw UnsupportedError('File download is only supported on web.');
}

