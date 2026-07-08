import 'package:dio/dio.dart';
import '../platform/platform_info.dart';

class ApiClient {
  ApiClient({String baseUrl = 'http://localhost:8080'})
      : _dio = Dio(BaseOptions(
          baseUrl: baseUrl,
          connectTimeout: const Duration(seconds: 10),
          receiveTimeout: const Duration(seconds: 10),
        ));

  final Dio _dio;

  String get _prefix => PlatformInfo.apiPrefix;

  Future<Map<String, dynamic>> getInfo() async {
    final res = await _dio.get('$_prefix/info');
    return Map<String, dynamic>.from(res.data['data'] as Map);
  }

  Future<Map<String, dynamic>> getStatus() async {
    if (PlatformInfo.isMobile) {
      final res = await _dio.get('$_prefix/devices/summary');
      return Map<String, dynamic>.from(res.data['data'] as Map);
    }
    final res = await _dio.get('$_prefix/status');
    return Map<String, dynamic>.from(res.data['data'] as Map);
  }
}
