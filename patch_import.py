with open("app/src/main/java/com/example/camera/CameraManagerHelper.kt", "r") as f:
    content = f.read()

if "import android.os.SystemClock" not in content:
    content = content.replace("import android.util.Log", "import android.os.SystemClock\nimport android.util.Log")
    with open("app/src/main/java/com/example/camera/CameraManagerHelper.kt", "w") as f:
        f.write(content)
