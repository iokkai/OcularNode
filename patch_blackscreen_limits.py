with open("app/src/main/java/com/example/ui/camera/BlackScreenOverlay.kt", "r") as f:
    content = f.read()

import re

old_block = """            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)"""

new_block = """            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            
            val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)"""

content = content.replace(old_block, new_block)

old_clear = """                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)"""

new_clear = """                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

                val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)"""

content = content.replace(old_clear, new_clear)

with open("app/src/main/java/com/example/ui/camera/BlackScreenOverlay.kt", "w") as f:
    f.write(content)

