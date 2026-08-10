with open("app/src/main/java/com/example/ui/viewer/RemoteSettingsDialog.kt", "r") as f:
    text = f.read()

# 1. device_name
old_1 = """                                        Button(
                                            onClick = {
                                                onSendCommand("device_name", editingName)
                                                Toast.makeText(context, "已更新鏡頭名稱: $editingName", Toast.LENGTH_SHORT).show()
                                            },"""
new_1 = """                                        Button(
                                            onClick = {
                                                if (editingName.isNotBlank()) {
                                                    coroutineScope.launch {
                                                        val success = onSendCommand("device_name", editingName)
                                                        if (success) {
                                                            Toast.makeText(context, "已更新鏡頭名稱: $editingName", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            Toast.makeText(context, "設定失敗", Toast.LENGTH_SHORT).show()
                                                            editingName = deviceNameStr
                                                        }
                                                    }
                                                }
                                            },"""
text = text.replace(old_1, new_1)

# 2. quality
old_2 = """                                            onValueChangeFinished = {
                                                onSendCommand("quality", "${localQuality.toInt()}")
                                                Toast.makeText(context, "品質已設定為: ${localQuality.toInt()}%", Toast.LENGTH_SHORT).show()
                                            },"""
new_2 = """                                            onValueChangeFinished = {
                                                coroutineScope.launch {
                                                    val success = onSendCommand("quality", "${localQuality.toInt()}")
                                                    if (success) {
                                                        Toast.makeText(context, "品質已設定為: ${localQuality.toInt()}%", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        localQuality = currentQuality.toFloat()
                                                        Toast.makeText(context, "設定失敗", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },"""
text = text.replace(old_2, new_2)

# 3. night_vision_luma
old_3 = """                                                onValueChangeFinished = {
                                                    onSendCommand("night_vision_luma", "${localNightLuma.toInt()}")
                                                    Toast.makeText(context, "夜視亮度閥值已設為: ${localNightLuma.toInt()}", Toast.LENGTH_SHORT).show()
                                                },"""
new_3 = """                                                onValueChangeFinished = {
                                                    coroutineScope.launch {
                                                        val success = onSendCommand("night_vision_luma", "${localNightLuma.toInt()}")
                                                        if (success) {
                                                            Toast.makeText(context, "夜視亮度閥值已設為: ${localNightLuma.toInt()}", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            localNightLuma = nightLuma
                                                            Toast.makeText(context, "設定失敗", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },"""
text = text.replace(old_3, new_3)

# 4. cooldown
old_4 = """                                            onValueChangeFinished = {
                                                onSendCommand("cooldown", "${localCooldown.toInt()}")
                                                Toast.makeText(context, "冷卻時間設為: ${localCooldown.toInt()} 秒", Toast.LENGTH_SHORT).show()
                                            },"""
new_4 = """                                            onValueChangeFinished = {
                                                coroutineScope.launch {
                                                    val success = onSendCommand("cooldown", "${localCooldown.toInt()}")
                                                    if (success) {
                                                        Toast.makeText(context, "冷卻時間設為: ${localCooldown.toInt()} 秒", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        localCooldown = motionCooldown.toFloat()
                                                        Toast.makeText(context, "設定失敗", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },"""
text = text.replace(old_4, new_4)

with open("app/src/main/java/com/example/ui/viewer/RemoteSettingsDialog.kt", "w") as f:
    f.write(text)
