import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../api/api_client.dart';
import '../api/local_service_client.dart';
import '../config/app_config.dart';

final serverUrlProvider = FutureProvider<String>((ref) => AppConfig.getServerUrl());

final localStatusProvider = FutureProvider<LocalServiceStatus?>((ref) async {
  final client = LocalServiceClient();
  return client.getStatus();
});

final clientApiProvider = FutureProvider<ApiClient?>((ref) async {
  final baseUrl = await ref.watch(serverUrlProvider.future);
  final local = await ref.watch(localStatusProvider.future);
  final clientId = local?.clientId;
  return ApiClient(baseUrl: baseUrl, clientId: clientId);
});

final cloudStatusProvider = FutureProvider<Map<String, dynamic>?>((ref) async {
  final api = await ref.watch(clientApiProvider.future);
  if (api == null) return null;
  try {
    return await api.getStatus();
  } catch (_) {
    return null;
  }
});

final complianceProvider = FutureProvider<Map<String, dynamic>?>((ref) async {
  final api = await ref.watch(clientApiProvider.future);
  if (api == null) return null;
  try {
    return await api.getCompliance();
  } catch (_) {
    return null;
  }
});

final localAssetsProvider = FutureProvider<Map<String, dynamic>?>((ref) async {
  final client = LocalServiceClient();
  return client.getAssets();
});
