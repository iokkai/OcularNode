with open("app/src/main/java/com/example/server/MjpegHttpServer.kt", "r") as f:
    text = f.read()

# Fix the remaining reader usage in /control
old_control = """                path.startsWith("/control") -> {
                    // Read headers to find Content-Length
                    var contentLength = 0
                    var headerLine: String?
                    while (reader.readLine().also { headerLine = it } != null && headerLine!!.isNotBlank()) {
                        if (headerLine!!.lowercase().startsWith("content-length:")) {
                            contentLength = headerLine!!.substringAfter(":").trim().toIntOrNull() ?: 0
                        }
                    }
                    var body = ""
                    if (contentLength > 0) {
                        val bodyChars = CharArray(contentLength)
                        reader.read(bodyChars, 0, contentLength)
                        body = String(bodyChars)
                    }"""

new_control = """                path.startsWith("/control") -> {
                    // Read headers to find Content-Length
                    var contentLength = 0
                    var headerLine: String?
                    while (readLineStr(input).also { headerLine = it } != null && headerLine!!.isNotBlank()) {
                        if (headerLine!!.lowercase().startsWith("content-length:")) {
                            contentLength = headerLine!!.substringAfter(":").trim().toIntOrNull() ?: 0
                        }
                    }
                    var body = ""
                    if (contentLength > 0) {
                        val bodyBytes = ByteArray(contentLength)
                        var totalRead = 0
                        while (totalRead < contentLength) {
                            val r = input.read(bodyBytes, totalRead, contentLength - totalRead)
                            if (r == -1) break
                            totalRead += r
                        }
                        body = String(bodyBytes, Charsets.UTF_8)
                    }"""

text = text.replace(old_control, new_control)

with open("app/src/main/java/com/example/server/MjpegHttpServer.kt", "w") as f:
    f.write(text)
