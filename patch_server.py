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

    private fun handleClient(socket: Socket, scope: CoroutineScope) {"""

text = text.replace("    private fun handleClient(socket: Socket, scope: CoroutineScope) {", helper_func)

old_handle = """    private fun handleClient(socket: Socket, scope: CoroutineScope) {
        try {
            socket.soTimeout = 10000
            val input = socket.getInputStream()
            val reader = BufferedReader(InputStreamReader(input))
            val requestLine = reader.readLine() ?: return socket.close()
            val parts = requestLine.split(" ")
            if (parts.size < 2) return socket.close()
            val method = parts[0].uppercase()
            val path = parts[1]"""

new_handle = """    private fun handleClient(socket: Socket, scope: CoroutineScope) {
        try {
            socket.soTimeout = 10000
            val input = socket.getInputStream()
            val requestLine = readLineStr(input) ?: return socket.close()
            val parts = requestLine.split(" ")
            if (parts.size < 2) return socket.close()
            val method = parts[0].uppercase()
            val path = parts[1]"""
            
text = text.replace(old_handle, new_handle)

# Replace reader.readLine() in /control
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

# We need to skip headers for /speak!
old_speak = """                path.startsWith("/speak") -> {
                    // Receive PCM Audio stream from Viewer and output to Camera Speakerphone
                    socket.tcpNoDelay = true
                    output.write(("HTTP/1.1 200 OK\\r\\n" +
                            "Access-Control-Allow-Origin: *\\r\\n\\r\\n").toByteArray())
                    output.flush()

                    audioEngine.startPlaying(context)
                    val buffer = ByteArray(640)
                    var read: Int
                    try {
                        while (input.read(buffer).also { read = it } != -1) {
                            if (read > 0) {
                                audioEngine.playChunk(buffer, read)
                            }
                        }"""

new_speak = """                path.startsWith("/speak") -> {
                    // Skip remaining headers
                    while (true) {
                        val line = readLineStr(input)
                        if (line.isNullOrEmpty()) break
                    }
                    
                    // Receive PCM Audio stream from Viewer and output to Camera Speakerphone
                    socket.tcpNoDelay = true
                    output.write(("HTTP/1.1 200 OK\\r\\n" +
                            "Access-Control-Allow-Origin: *\\r\\n\\r\\n").toByteArray())
                    output.flush()

                    audioEngine.startPlaying(context)
                    val buffer = ByteArray(640)
                    var read: Int
                    try {
                        socket.soTimeout = 0 // No timeout for continuous streaming
                        while (input.read(buffer).also { read = it } != -1) {
                            if (read > 0) {
                                audioEngine.playChunk(buffer, read)
                            }
                        }"""

text = text.replace(old_speak, new_speak)

# Also fix /audio to NOT use chunked encoding!
old_audio = """                path.startsWith("/audio") -> {
                    // Stream PCM Audio from Camera MIC to Viewer
                    socket.tcpNoDelay = true
                    output.write(("HTTP/1.1 200 OK\\r\\n" +
                            "Access-Control-Allow-Origin: *\\r\\n" +
                            "Content-Type: audio/pcm\\r\\n" +
                            "Transfer-Encoding: chunked\\r\\n\\r\\n").toByteArray())
                    output.flush()

                    audioEngine.startRecording(scope)
                    val collectorJob = scope.launch(Dispatchers.IO) {
                        audioEngine.audioBufferFlow.collectLatest { chunk ->
                            try {
                                val chunkSizeHex = Integer.toHexString(chunk.size) + "\\r\\n"
                                output.write(chunkSizeHex.toByteArray())
                                output.write(chunk)
                                output.write("\\r\\n".toByteArray())
                                output.flush()
                            } catch (e: Exception) {
                                try { socket.close() } catch (_: Exception) {}
                            }
                        }
                    }"""

new_audio = """                path.startsWith("/audio") -> {
                    // Skip remaining headers
                    while (true) {
                        val line = readLineStr(input)
                        if (line.isNullOrEmpty()) break
                    }
                    // Stream PCM Audio from Camera MIC to Viewer
                    socket.tcpNoDelay = true
                    output.write(("HTTP/1.1 200 OK\\r\\n" +
                            "Access-Control-Allow-Origin: *\\r\\n" +
                            "Content-Type: audio/pcm\\r\\n" +
                            "Connection: close\\r\\n\\r\\n").toByteArray())
                    output.flush()

                    audioEngine.startRecording(scope)
                    val collectorJob = scope.launch(Dispatchers.IO) {
                        audioEngine.audioBufferFlow.collect { chunk ->
                            try {
                                output.write(chunk)
                                output.flush()
                            } catch (e: Exception) {
                                try { socket.close() } catch (_: Exception) {}
                            }
                        }
                    }"""

text = text.replace(old_audio, new_audio)

with open("app/src/main/java/com/example/server/MjpegHttpServer.kt", "w") as f:
    f.write(text)
