import re

with open("app/src/main/java/com/example/ui/viewer/RemoteSettingsDialog.kt", "r") as f:
    text = f.read()

# Replace onSendCommand signature
text = text.replace("onSendCommand: (String, String) -> Unit", "onSendCommand: suspend (String, String) -> Boolean")

def patch_switch(pattern, var_name, cmd_name, text):
    # This handles standard switches
    match_str = r"(onCheckedChange = \{\s*checked ->\s*)\n\s*" + var_name + r"\s*=\s*checked\n\s*onSendCommand\(\"" + cmd_name + r"\", if \(checked\) \"on\" else \"off\"\)\n\s*Toast\.makeText\(context,\s*(.*?),\s*Toast\.LENGTH_SHORT\)\.show\(\)\n\s*\}"
    
    replacement = r"\1\n                                                coroutineScope.launch {\n                                                    val success = onSendCommand(\"" + cmd_name + r"\", if (checked) \"on\" else \"off\")\n                                                    if (success) {\n                                                        " + var_name + r" = checked\n                                                        Toast.makeText(context, \2, Toast.LENGTH_SHORT).show()\n                                                    } else {\n                                                        Toast.makeText(context, \"設定失敗\", Toast.LENGTH_SHORT).show()\n                                                    }\n                                                }\n                                            }"
    
    return re.sub(match_str, replacement, text)

# Patch Switches
switches = [
    ("localIsMotion", "motion"),
    ("localMlKitEnabled", "mlkit_filter"),
    ("localPlayLocalAlarm", "play_alarm_setting"),
    ("localAutoCleanup", "auto_cleanup"),
    ("localAutoStartOnBoot", "auto_start_boot"),
    ("localPowerCutAlert", "power_cut_alert"),
    ("localSystemLogEnabled", "system_log_enabled")
]
for var_name, cmd_name in switches:
    text = patch_switch("", var_name, cmd_name, text)

def patch_button(match_str, replacement, text):
    return re.sub(match_str, replacement, text)

# Mode buttons
text = patch_button(
    r"onClick = \{\n\s*localOpMode = \"monitor\"\n\s*onSendCommand\(\"mode\", \"monitor\"\)\n\s*Toast\.makeText\(context,\s*\"(.*?)\",\s*Toast\.LENGTH_SHORT\)\.show\(\)\n\s*\}",
    r"onClick = {\n                                                coroutineScope.launch {\n                                                    val success = onSendCommand(\"mode\", \"monitor\")\n                                                    if (success) {\n                                                        localOpMode = \"monitor\"\n                                                        Toast.makeText(context, \"\1\", Toast.LENGTH_SHORT).show()\n                                                    } else {\n                                                        Toast.makeText(context, \"設定失敗\", Toast.LENGTH_SHORT).show()\n                                                    }\n                                                }\n                                            }",
    text
)

text = patch_button(
    r"onClick = \{\n\s*localOpMode = \"detection\"\n\s*onSendCommand\(\"mode\", \"detection\"\)\n\s*Toast\.makeText\(context,\s*\"(.*?)\",\s*Toast\.LENGTH_SHORT\)\.show\(\)\n\s*\}",
    r"onClick = {\n                                                coroutineScope.launch {\n                                                    val success = onSendCommand(\"mode\", \"detection\")\n                                                    if (success) {\n                                                        localOpMode = \"detection\"\n                                                        Toast.makeText(context, \"\1\", Toast.LENGTH_SHORT).show()\n                                                    } else {\n                                                        Toast.makeText(context, \"設定失敗\", Toast.LENGTH_SHORT).show()\n                                                    }\n                                                }\n                                            }",
    text
)

# Camera switch
text = patch_button(
    r"onClick = \{\n\s*localLensFacing = if \(localLensFacing == \"back\"\) \"front\" else \"back\"\n\s*onSendCommand\(\"camera\", \"switch\"\)\n\s*Toast\.makeText\(context,\s*\"(.*?)\",\s*Toast\.LENGTH_SHORT\)\.show\(\)\n\s*\}",
    r"onClick = {\n                                                coroutineScope.launch {\n                                                    val success = onSendCommand(\"camera\", \"switch\")\n                                                    if (success) {\n                                                        localLensFacing = if (localLensFacing == \"back\") \"front\" else \"back\"\n                                                        Toast.makeText(context, \"\1\", Toast.LENGTH_SHORT).show()\n                                                    } else {\n                                                        Toast.makeText(context, \"設定失敗\", Toast.LENGTH_SHORT).show()\n                                                    }\n                                                }\n                                            }",
    text
)

# Torch switch
text = patch_button(
    r"onClick = \{\n\s*localTorchOn = !localTorchOn\n\s*onSendCommand\(\"torch\", if \(localTorchOn\) \"on\" else \"off\"\)\n\s*Toast\.makeText\(context,\s*(.*?),\s*Toast\.LENGTH_SHORT\)\.show\(\)\n\s*\}",
    r"onClick = {\n                                                coroutineScope.launch {\n                                                    val targetState = !localTorchOn\n                                                    val success = onSendCommand(\"torch\", if (targetState) \"on\" else \"off\")\n                                                    if (success) {\n                                                        localTorchOn = targetState\n                                                        Toast.makeText(context, \1, Toast.LENGTH_SHORT).show()\n                                                    } else {\n                                                        Toast.makeText(context, \"設定失敗\", Toast.LENGTH_SHORT).show()\n                                                    }\n                                                }\n                                            }",
    text
)

# Night mode chips
text = patch_button(
    r"onClick = \{\n\s*localNightMode = modeKey\n\s*onSendCommand\(\"night_vision\", modeKey\)\n\s*Toast\.makeText\(context,\s*\"(.*?)\",\s*Toast\.LENGTH_SHORT\)\.show\(\)\n\s*\}",
    r"onClick = {\n                                                    coroutineScope.launch {\n                                                        val success = onSendCommand(\"night_vision\", modeKey)\n                                                        if (success) {\n                                                            localNightMode = modeKey\n                                                            Toast.makeText(context, \"\1\", Toast.LENGTH_SHORT).show()\n                                                        } else {\n                                                            Toast.makeText(context, \"設定失敗\", Toast.LENGTH_SHORT).show()\n                                                        }\n                                                    }\n                                                }",
    text
)

# Sliders (reverting to original variable)
def patch_slider(var_name, orig_var, cmd_name, fmt_val, text):
    match_str = r"(onValueChangeFinished = \{\s*)\n\s*onSendCommand\(\"" + cmd_name + r"\", " + fmt_val + r"\)\n\s*Toast\.makeText\(context,\s*(.*?),\s*Toast\.LENGTH_SHORT\)\.show\(\)\n\s*\}"
    replacement = r"\1\n                                                coroutineScope.launch {\n                                                    val success = onSendCommand(\"" + cmd_name + r"\", " + fmt_val + r")\n                                                    if (success) {\n                                                        Toast.makeText(context, \2, Toast.LENGTH_SHORT).show()\n                                                    } else {\n                                                        " + var_name + " = " + orig_var + r"\n                                                        Toast.makeText(context, \"設定失敗\", Toast.LENGTH_SHORT).show()\n                                                    }\n                                                }\n                                            }"
    return re.sub(match_str, replacement, text)

text = patch_slider("localQuality", "currentQuality.toFloat()", "quality", "\"\$\{localQuality.toInt()\}\"", text)
text = patch_slider("localSens", "motionSensitivity", "sensitivity", "String.format\(\"%\.1f\", localSens\)", text)
text = patch_slider("localCooldown", "motionCooldown.toFloat()", "cooldown", "\"\$\{localCooldown.toInt()\}\"", text)
text = patch_slider("localNightLuma", "nightLuma", "night_vision_luma", "\"\$\{localNightLuma.toInt()\}\"", text)
text = patch_slider("localStorageGB", "storageLimitGB", "storage_limit_gb", "String.format\(\"%\.1f\", localStorageGB\)", text)
text = patch_slider("localMaxEvents", "maxEventCount.toFloat()", "max_event_count", "\"\$\{localMaxEvents.toInt()\}\"", text)

# Resolution Picker
text = re.sub(
    r"(onSelect = \{ res ->\s*)\n\s*onSendCommand\(\"resolution\", res\)\n\s*Toast\.makeText\(context,\s*(.*?),\s*Toast\.LENGTH_SHORT\)\.show\(\)\n\s*(showResPicker = false)\n\s*\}",
    r"\1\n                coroutineScope.launch {\n                    val success = onSendCommand(\"resolution\", res)\n                    if (success) {\n                        Toast.makeText(context, \2, Toast.LENGTH_SHORT).show()\n                    } else {\n                        Toast.makeText(context, \"設定失敗\", Toast.LENGTH_SHORT).show()\n                    }\n                    \3\n                }\n            }",
    text
)

# Test alarm button
text = re.sub(
    r"(onClick = \{\s*)\n\s*onSendCommand\(\"alarm\", \"trigger\"\)\n\s*Toast\.makeText\(context,\s*(.*?),\s*Toast\.LENGTH_SHORT\)\.show\(\)\n\s*\}",
    r"\1\n                                            coroutineScope.launch {\n                                                val success = onSendCommand(\"alarm\", \"trigger\")\n                                                if (success) {\n                                                    Toast.makeText(context, \2, Toast.LENGTH_SHORT).show()\n                                                } else {\n                                                    Toast.makeText(context, \"設定失敗\", Toast.LENGTH_SHORT).show()\n                                                }\n                                            }\n                                        }",
    text
)

# Save device name
text = re.sub(
    r"(onClick = \{\s*)\n\s*if\s*\(editingName\.isNotBlank\(\)\)\s*\{\n\s*onSendCommand\(\"device_name\", editingName\)\n\s*Toast\.makeText\(context,\s*(.*?),\s*Toast\.LENGTH_SHORT\)\.show\(\)\n\s*\}\n\s*\}",
    r"\1\n                                                if (editingName.isNotBlank()) {\n                                                    coroutineScope.launch {\n                                                        val success = onSendCommand(\"device_name\", editingName)\n                                                        if (success) {\n                                                            Toast.makeText(context, \2, Toast.LENGTH_SHORT).show()\n                                                        } else {\n                                                            Toast.makeText(context, \"設定失敗\", Toast.LENGTH_SHORT).show()\n                                                            editingName = deviceNameStr\n                                                        }\n                                                    }\n                                                }\n                                            }",
    text
)

# Category Switches
text = re.sub(
    r"(onCheckedChange = \{\s*checked ->\s*)\n\s*categoryStates\[category\] = checked\n\s*val payload = JSONObject\(\)\.apply \{\n\s*put\(\"category\", category\.name\)\n\s*put\(\"enabled\", checked\)\n\s*\}\.toString\(\)\n\s*onSendCommand\(\"cat_toggle\", payload\)\n\s*\}",
    r"\1\n                                                        coroutineScope.launch {\n                                                            val payload = JSONObject().apply {\n                                                                put(\"category\", category.name)\n                                                                put(\"enabled\", checked)\n                                                            }.toString()\n                                                            val success = onSendCommand(\"cat_toggle\", payload)\n                                                            if (success) {\n                                                                categoryStates[category] = checked\n                                                            } else {\n                                                                Toast.makeText(context, \"設定失敗\", Toast.LENGTH_SHORT).show()\n                                                            }\n                                                        }\n                                                    }",
    text
)

text = re.sub(
    r"(onCheckedChange = \{\s*checked ->\s*)\n\s*categoryRecordStates\[category\] = checked\n\s*val payload = JSONObject\(\)\.apply \{\n\s*put\(\"category\", category\.name\)\n\s*put\(\"enabled\", checked\)\n\s*\}\.toString\(\)\n\s*onSendCommand\(\"cat_record_toggle\", payload\)\n\s*\}",
    r"\1\n                                                        coroutineScope.launch {\n                                                            val payload = JSONObject().apply {\n                                                                put(\"category\", category.name)\n                                                                put(\"enabled\", checked)\n                                                            }.toString()\n                                                            val success = onSendCommand(\"cat_record_toggle\", payload)\n                                                            if (success) {\n                                                                categoryRecordStates[category] = checked\n                                                            } else {\n                                                                Toast.makeText(context, \"設定失敗\", Toast.LENGTH_SHORT).show()\n                                                            }\n                                                        }\n                                                    }",
    text
)

with open("app/src/main/java/com/example/ui/viewer/RemoteSettingsDialog.kt", "w") as f:
    f.write(text)
