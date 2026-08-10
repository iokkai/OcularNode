with open("app/src/main/java/com/example/client/CameraStreamClient.kt", "r") as f:
    content = f.read()

bad_func = """    suspend fun fetchCameraStatus(cameraDevice: CameraDevice): JSONObject? = withContext(Dispatchers.IO) {

    suspend fun fetchRemoteLogs(cameraDevice: CameraDevice): List<String> = withContext(Dispatchers.IO) {
        try {
            val logUrl = "http://${cameraDevice.ipAddress}:${cameraDevice.port}/logs"
            val request = Request.Builder().url(logUrl).get().build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                val bodyStr = response.body!!.string()
                val json = JSONObject(bodyStr)
                response.close()
                val logsArray = json.optJSONArray("logs")
                val logsList = mutableListOf<String>()
                if (logsArray != null) {
                    for (i in 0 until logsArray.length()) {
                        logsList.add(logsArray.getString(i))
                    }
                }
                return@withContext logsList
            }
            response.close()
            return@withContext emptyList()
        } catch (e: Exception) {
            return@withContext emptyList()
        }
    }"""

good_func = """    suspend fun fetchCameraStatus(cameraDevice: CameraDevice): JSONObject? = withContext(Dispatchers.IO) {"""

content = content.replace(bad_func, good_func)

append_func = """
    suspend fun fetchRemoteLogs(cameraDevice: CameraDevice): List<String> = withContext(Dispatchers.IO) {
        try {
            val logUrl = "http://${cameraDevice.ipAddress}:${cameraDevice.port}/logs"
            val request = Request.Builder().url(logUrl).get().build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                val bodyStr = response.body!!.string()
                val json = JSONObject(bodyStr)
                response.close()
                val logsArray = json.optJSONArray("logs")
                val logsList = mutableListOf<String>()
                if (logsArray != null) {
                    for (i in 0 until logsArray.length()) {
                        logsList.add(logsArray.getString(i))
                    }
                }
                return@withContext logsList
            }
            response.close()
            return@withContext emptyList()
        } catch (e: Exception) {
            return@withContext emptyList()
        }
    }
"""

content = content.replace("    private fun startHeartbeatLoop", append_func + "\n    private fun startHeartbeatLoop")

with open("app/src/main/java/com/example/client/CameraStreamClient.kt", "w") as f:
    f.write(content)
