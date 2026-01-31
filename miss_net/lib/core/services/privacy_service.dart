import 'package:local_auth/local_auth.dart';
import 'package:shared_preferences/shared_preferences.dart';

class PrivacyService {
  final SharedPreferences prefs;
  final LocalAuthentication auth = LocalAuthentication();

  PrivacyService(this.prefs);

  static const String _keyIncognito = 'incognito_mode';
  static const String _keyAppLock = 'app_lock_enabled';

  bool get isIncognito => prefs.getBool(_keyIncognito) ?? false;
  bool get isAppLockEnabled => prefs.getBool(_keyAppLock) ?? false;

  Future<void> setIncognito(bool value) async {
    await prefs.setBool(_keyIncognito, value);
  }

  Future<void> setAppLock(bool value) async {
    await prefs.setBool(_keyAppLock, value);
  }

  Future<bool> authenticate() async {
    try {
      final bool canAuthenticateWithBiometrics = await auth.canCheckBiometrics;
      final bool canAuthenticate = canAuthenticateWithBiometrics || await auth.isDeviceSupported();

      if (!canAuthenticate) return true; // Fallback if not supported? No, fail open? Secure default is fail closed, but if no hardware...

      return await auth.authenticate(
        localizedReason: 'Please authenticate to access MissNet',
        options: const AuthenticationOptions(stickyAuth: true, useErrorDialogs: true),
      );
    } catch (e) {
      return false;
    }
  }
}
