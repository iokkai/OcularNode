with open("app/src/main/java/com/example/ui/viewer/ViewerListScreen.kt", "r") as f:
    text = f.read()

text = text.replace("onSendCommand = { cmd, value -> viewModel.sendControlCommandToCamera(it, cmd, value) },",
                    "onSendCommand = { cmd, value -> viewModel.sendControlCommandToCameraSuspend(it, cmd, value) },")

with open("app/src/main/java/com/example/ui/viewer/ViewerListScreen.kt", "w") as f:
    f.write(text)
