with open("app/src/main/java/com/example/ui/viewer/RemoteSettingsDialog.kt", "r") as f:
    content = f.read()

if "import kotlinx.coroutines.launch" not in content:
    content = content.replace("import kotlinx.coroutines.flow.update", "import kotlinx.coroutines.flow.update\nimport kotlinx.coroutines.launch")
    
if "import kotlinx.coroutines.launch" not in content:
    content = content.replace("import com.example.data.NotificationCategory", "import kotlinx.coroutines.launch\nimport com.example.data.NotificationCategory")

with open("app/src/main/java/com/example/ui/viewer/RemoteSettingsDialog.kt", "w") as f:
    f.write(content)
