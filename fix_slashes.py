with open("app/src/main/java/com/example/ui/viewer/RemoteSettingsDialog.kt", "r") as f:
    text = f.read()

text = text.replace(r'\"', '"')
text = text.replace(r'\%', '%')
text = text.replace(r'\.', '.')
text = text.replace(r'\}', '}')
text = text.replace(r'\{', '{')
text = text.replace(r'\$', '$')
text = text.replace(r'\(', '(')
text = text.replace(r'\)', ')')

with open("app/src/main/java/com/example/ui/viewer/RemoteSettingsDialog.kt", "w") as f:
    f.write(text)
