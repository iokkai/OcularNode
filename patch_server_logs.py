import re

with open("app/src/main/java/com/example/server/MjpegHttpServer.kt", "r") as f:
    content = f.read()

logs_endpoint = """
                path.startsWith("/logs") -> {
                    val logsList = com.example.util.AppLogger.logs.value
                    val jsonArray = org.json.JSONArray(logsList)
                    val json = org.json.JSONObject().apply {
                        put("status", "ok")
                        put("logs", jsonArray)
                    }
                    sendJsonResponse(output, 200, json.toString())
                    socket.close()
                }

                path.startsWith("/audio") -> {"""

content = content.replace("path.startsWith(\"/audio\") -> {", logs_endpoint)

with open("app/src/main/java/com/example/server/MjpegHttpServer.kt", "w") as f:
    f.write(content)
