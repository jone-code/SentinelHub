import 'dart:io' show Platform;
import 'package:flutter/foundation.dart';

/// Platform detection for adaptive mobile / desktop UI.
class PlatformInfo {
  static bool get isDesktop {
    if (kIsWeb) return false;
    return Platform.isWindows || Platform.isMacOS || Platform.isLinux;
  }

  static bool get isMobile {
    if (kIsWeb) return false;
    return Platform.isAndroid || Platform.isIOS;
  }

  /// API channel prefix based on platform.
  static String get apiPrefix => isDesktop ? '/api/client/v1' : '/api/app/v1';
}
