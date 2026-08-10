with open("app/src/main/java/com/example/ui/viewer/RemoteSettingsDialog.kt", "r") as f:
    text = f.read()

old = """                                            onValueChangeFinished = {
                                                onSendCommand("max_event_count", "${localMaxEvents.toInt()}")
                                                Toast.makeText(context, "最高事件筆數設為: ${localMaxEvents.toInt()} 筆", Toast.LENGTH_SHORT).show()
                                            },"""

new = """                                            onValueChangeFinished = {
                                                coroutineScope.launch {
                                                    val success = onSendCommand("max_event_count", "${localMaxEvents.toInt()}")
                                                    if (success) {
                                                        Toast.makeText(context, "最高事件筆數設為: ${localMaxEvents.toInt()} 筆", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        localMaxEvents = maxEventCount.toFloat()
                                                        Toast.makeText(context, "設定失敗", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },"""

text = text.replace(old, new)

with open("app/src/main/java/com/example/ui/viewer/RemoteSettingsDialog.kt", "w") as f:
    f.write(text)
