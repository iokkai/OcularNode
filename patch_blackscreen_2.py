with open("app/src/main/java/com/example/ui/camera/BlackScreenOverlay.kt", "r") as f:
    content = f.read()

old_code = """        if (activityWindow != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                activityWindow.attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }"""

new_code = """        if (activityWindow != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val params = activityWindow.attributes
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                activityWindow.attributes = params
            }"""

content = content.replace(old_code, new_code)
with open("app/src/main/java/com/example/ui/camera/BlackScreenOverlay.kt", "w") as f:
    f.write(content)
