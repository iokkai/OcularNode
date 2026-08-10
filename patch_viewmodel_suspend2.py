with open("app/src/main/java/com/example/ui/viewer/ViewerViewModel.kt", "r") as f:
    text = f.read()

new_func = """    suspend fun sendControlCommandToCameraSuspend(camera: CameraDevice, command: String, value: String): Boolean {
        return streamClient.sendControlCommand(camera, command, value)
    }

    fun sendControlCommandToCamera(camera: CameraDevice, command: String, value: String) {"""

text = text.replace("    fun sendControlCommandToCamera(camera: CameraDevice, command: String, value: String) {", new_func)

with open("app/src/main/java/com/example/ui/viewer/ViewerViewModel.kt", "w") as f:
    f.write(text)
