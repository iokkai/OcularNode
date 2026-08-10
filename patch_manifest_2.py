with open("app/src/main/AndroidManifest.xml", "r") as f:
    content = f.read()

if "android.max_aspect" not in content:
    content = content.replace("</application>", "    <meta-data android:name=\"android.max_aspect\" android:value=\"2.4\" />\n    </application>")
    with open("app/src/main/AndroidManifest.xml", "w") as f:
        f.write(content)
