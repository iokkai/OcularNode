import re

with open("app/src/main/java/com/example/server/MjpegHttpServer.kt", "r") as f:
    text = f.read()

helper_func = """    private fun readLineStr(input: InputStream): String? {
        val baos = java.io.ByteArrayOutputStream()
        var c = input.read()
        if (c == -1) return null
        while (c != -1 && c != '\\n'.code) {
            if (c != '\\r'.code) {
                baos.write(c)
            }
            c = input.read()
        }
        return baos.toString("UTF-8")
    }

    private fun handleClient"""

if "readLineStr" not in text:
    text = text.replace("    private fun handleClient", helper_func)

old_handle = """    private fun handleClient(socket: Socket, scope: CoroutineScope) {
        try {
            socket.soTimeout = 10000
            val input = socket.getInputStream()
            val reader = BufferedReader(InputStreamReader(input))
            val requestLine = reader.readLine() ?: return socket.close()

            val parts = requestLine.split(" ")
            if (parts.size < 2) return socket.close()"""

new_handle = """    private fun handleClient(socket: Socket, scope: CoroutineScope) {
        try {
            socket.soTimeout = 10000
            val input = socket.getInputStream()
            val requestLine = readLineStr(input) ?: return socket.close()

            val parts = requestLine.split(" ")
            if (parts.size < 2) return socket.close()"""

text = text.replace(old_handle, new_handle)

# Fix control
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
