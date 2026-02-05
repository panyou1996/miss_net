package com.panyou.missnet

import io.flutter.embedding.android.FlutterActivity

class MainActivity: FlutterActivity()
    private val CHANNEL = "flutter.io/pip"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            if (call.method == "enterPipMode") {
                enterPipMode()
                result.success(null)
            } else {
                result.notImplemented()
            }
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
                initiatePictureInPicture(params)
        }
    }

    private fun initiatePictureInPicture(params: PictureInPictureParams) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(params)
        }
    }
}