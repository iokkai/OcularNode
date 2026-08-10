import re

with open("app/src/main/java/com/example/data/MotionEvent.kt", "r") as f:
    text = f.read()

if "val remoteId: Long? = null" not in text:
    text = text.replace("val videoPath: String? = null\n)", "val videoPath: String? = null,\n    val remoteId: Long? = null\n)")
    with open("app/src/main/java/com/example/data/MotionEvent.kt", "w") as f:
        f.write(text)
