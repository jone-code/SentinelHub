import 'package:dio/dio.dart';
import '../platform/platform_info.dart';

/// Reads PC background service state via local IPC (Node orchestration layer).
class LocalServiceClient {
  LocalServiceClient({String baseUrl = 'http://127.0.0.1:39201'})
      : _dio = Dio(BaseOptions(
          baseUrl: baseUrl,
          connectTimeout: const Duration(seconds: 2),
          receiveTimeout: const Duration(seconds: 2),
        ));

  final Dio _dio;

  static bool get isSupported => PlatformInfo.isDesktop;

  Future<LocalServiceStatus?> getStatus() async {
    if (!isSupported) return null;
    try {
      final res = await _dio.get<Map<String, dynamic>>('/local/status');
      return LocalServiceStatus.fromJson(res.data ?? {});
    } on DioException {
      return null;
    }
  }
}

class LocalServiceStatus {
  const LocalServiceStatus({
    required this.running,
    required this.cloudConnected,
    required this.nativeAvailable,
    required this.version,
    this.clientId,
    this.softwareCount = 0,
  });

  factory LocalServiceStatus.fromJson(Map<String, dynamic> json) {
    final cloud = json['cloud'] as Map<String, dynamic>? ?? {};
    final native = json['native'] as Map<String, dynamic>? ?? {};
    final assets = json['assets'] as Map<String, dynamic>? ?? {};
    return LocalServiceStatus(
      running: json['running'] as bool? ?? false,
      version: json['version'] as String? ?? '',
      clientId: json['client_id'] as String?,
      cloudConnected: cloud['connected'] as bool? ?? false,
      nativeAvailable: native['available'] as bool? ?? false,
      softwareCount: assets['software_count'] as int? ?? 0,
    );
  }

  final bool running;
  final bool cloudConnected;
  final bool nativeAvailable;
  final String version;
  final String? clientId;
  final int softwareCount;

  bool get isHealthy => running && cloudConnected;
}
