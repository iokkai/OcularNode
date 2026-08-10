with open("app/src/main/java/com/example/ui/camera/BlackScreenOverlay.kt", "r") as f:
    content = f.read()

import re

old_disposable_1 = re.search(r"    // Update screen brightness on enter/exit\n    DisposableEffect\(isBlackScreenActive\) \{.*?\n    \}\n", content, re.DOTALL).group(0)

new_disposable_1 = """    // Update screen brightness and immersive mode on enter/exit
    DisposableEffect(isBlackScreenActive) {
        val activity = context as? Activity
        val originalLayoutParams = activity?.window?.attributes
        val originalBrightness = originalLayoutParams?.screenBrightness ?: -1f
        var originalStatusBarColor = android.graphics.Color.TRANSPARENT
        var originalNavigationBarColor = android.graphics.Color.TRANSPARENT

        if (isBlackScreenActive && activity != null) {
            originalStatusBarColor = activity.window.statusBarColor
            originalNavigationBarColor = activity.window.navigationBarColor
            
            val params = activity.window.attributes
            params.screenBrightness = 0.01f // Minimum brightness 1%
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            activity.window.attributes = params
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.window.statusBarColor = android.graphics.Color.BLACK
            activity.window.navigationBarColor = android.graphics.Color.BLACK

            val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            if (activity != null) {
                val params = activity.window.attributes
                params.screenBrightness = originalBrightness
                activity.window.attributes = params
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                activity.window.statusBarColor = originalStatusBarColor
                activity.window.navigationBarColor = originalNavigationBarColor
                
                val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
"""

content = content.replace(old_disposable_1, new_disposable_1)

old_disposable_2 = re.search(r"    DisposableEffect\(Unit\) \{\n        val activityWindow.*?onDispose \{\}\n    \}\n", content, re.DOTALL).group(0)

content = content.replace(old_disposable_2, "")

with open("app/src/main/java/com/example/ui/camera/BlackScreenOverlay.kt", "w") as f:
    f.write(content)

