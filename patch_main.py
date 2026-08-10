import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

content = content.replace("containerColor = Color(0xFFFDF8FF)", "containerColor = Color(0xFFFDF8FF),\n        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)")

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)
