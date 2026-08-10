with open("app/src/main/java/com/example/ui/viewer/ViewerViewModel.kt", "r") as f:
    content = f.read()

new_func = """    suspend fun fetchCameraStatus(camera: CameraDevice): org.json.JSONObject? {
        return streamClient.fetchCameraStatus(camera)
    }

    suspend fun fetchRemoteLogs(camera: CameraDevice): List<String> {
        return streamClient.fetchRemoteLogs(camera)
    }"""

content = content.replace("""    suspend fun fetchCameraStatus(camera: CameraDevice): org.json.JSONObject? {
        return streamClient.fetchCameraStatus(camera)
    }""", new_func)

with open("app/src/main/java/com/example/ui/viewer/ViewerViewModel.kt", "w") as f:
    f.write(content)
