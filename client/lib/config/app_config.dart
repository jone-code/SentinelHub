import 'package:shared_preferences/shared_preferences.dart';

class AppConfig {
  static const _serverUrlKey = 'server_url';

  static Future<String> getServerUrl() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_serverUrlKey) ?? 'http://localhost:8080';
  }

  static Future<void> setServerUrl(String url) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_serverUrlKey, url);
  }
}
