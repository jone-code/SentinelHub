import 'package:dio/dio.dart';
import '../platform/platform_info.dart';

class ApiClient {
  ApiClient({required String baseUrl, String? clientId})
      : _baseUrl = baseUrl,
        _clientId = clientId,
        _dio = Dio(BaseOptions(
          baseUrl: baseUrl,
          connectTimeout: const Duration(seconds: 10),
          receiveTimeout: const Duration(seconds: 10),
          headers: clientId != null ? {'X-Client-Id': clientId} : null,
        ));

  final String _baseUrl;
  final String? _clientId;
  final Dio _dio;

  String get baseUrl => _baseUrl;
  String? get clientId => _clientId;

  String get _prefix => PlatformInfo.apiPrefix;

  Future<Map<String, dynamic>> getInfo() async {
    final res = await _dio.get('$_prefix/info');
    return _data(res);
  }

  Future<Map<String, dynamic>> getStatus() async {
    if (PlatformInfo.isMobile) {
      final res = await _dio.get('$_prefix/devices/summary');
      return _data(res);
    }
    final res = await _dio.get('$_prefix/status');
    return _data(res);
  }

  Future<Map<String, dynamic>> getCompliance() async {
    final res = await _dio.get('$_prefix/compliance');
    return _data(res);
  }

  Map<String, dynamic> _data(Response res) {
    return Map<String, dynamic>.from(res.data['data'] as Map);
  }
}
