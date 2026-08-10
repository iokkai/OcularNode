import re

with open("app/src/main/java/com/example/ui/viewer/ViewerListScreen.kt", "r") as f:
    content = f.read()

content = content.replace("containerColor = Color(0xFFFDF8FF)", "containerColor = Color(0xFFFDF8FF),\n        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)")
content = content.replace(".padding(16.dp)", ".padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)")

with open("app/src/main/java/com/example/ui/viewer/ViewerListScreen.kt", "w") as f:
    f.write(content)
