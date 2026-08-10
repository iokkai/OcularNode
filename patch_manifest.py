with open("app/src/main/AndroidManifest.xml", "r") as f:
    content = f.read()

if "android:resizeableActivity" not in content:
    content = content.replace("<application", "<application\n        android:resizeableActivity=\"true\"")
    with open("app/src/main/AndroidManifest.xml", "w") as f:
        f.write(content)
