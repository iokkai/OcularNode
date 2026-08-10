with open("app/src/main/java/com/example/ui/viewer/ViewerViewModel.kt", "r") as f:
    content = f.read()

new_func = """    suspend fun sendControlCommandSuspend(command: String, value: String): Boolean {
        val camera = _selectedCamera.value ?: return false
        return streamClient.sendControlCommand(camera, command, value)
    }

    fun sendControlCommand(command: String, value: String) {"""

content = content.replace("    fun sendControlCommand(command: String, value: String) {", new_func)

with open("app/src/main/java/com/example/ui/viewer/ViewerViewModel.kt", "w") as f:
    f.write(content)
