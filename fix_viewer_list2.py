import re
with open("app/src/main/java/com/example/ui/viewer/ViewerListScreen.kt", "r") as f:
    text = f.read()

text = text.replace("viewModel.sendControlCommandToCamera(camera, cmd, valStr)", "viewModel.sendControlCommandToCameraSuspend(camera, cmd, valStr)")

with open("app/src/main/java/com/example/ui/viewer/ViewerListScreen.kt", "w") as f:
    f.write(text)
