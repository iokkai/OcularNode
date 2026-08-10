import re

with open("app/src/main/java/com/example/ui/viewer/RemoteSettingsDialog.kt", "r") as f:
    content = f.read()

# isMotion
content = content.replace("checked = isMotion,", "checked = localIsMotion,")
content = re.sub(
    r"(onCheckedChange = \{\s*checked ->\s*)(onSendCommand\(\"motion\",)",
    r"\1localIsMotion = checked\n                                                \2",
    content
)

# playLocalAlarm
content = content.replace("checked = playLocalAlarm,", "checked = localPlayLocalAlarm,")
content = re.sub(
    r"(onCheckedChange = \{\s*checked ->\s*)(onSendCommand\(\"play_alarm_setting\",)",
    r"\1localPlayLocalAlarm = checked\n                                                \2",
    content
)

# mlKitEnabled
content = content.replace("checked = mlKitEnabled,", "checked = localMlKitEnabled,")
content = re.sub(
    r"(onCheckedChange = \{\s*checked ->\s*)(onSendCommand\(\"mlkit_filter\",)",
    r"\1localMlKitEnabled = checked\n                                                \2",
    content
)

# autoCleanup
content = content.replace("checked = autoCleanup,", "checked = localAutoCleanup,")
content = re.sub(
    r"(onCheckedChange = \{\s*checked ->\s*)(onSendCommand\(\"auto_cleanup\",)",
    r"\1localAutoCleanup = checked\n                                                \2",
    content
)

# autoStartOnBoot
content = content.replace("checked = autoStartOnBoot,", "checked = localAutoStartOnBoot,")
content = re.sub(
    r"(onCheckedChange = \{\s*checked ->\s*)(onSendCommand\(\"auto_start_boot\",)",
    r"\1localAutoStartOnBoot = checked\n                                                \2",
    content
)

# powerCutAlertEnabled
content = content.replace("checked = powerCutAlertEnabled,", "checked = localPowerCutAlert,")
content = re.sub(
    r"(onCheckedChange = \{\s*checked ->\s*)(onSendCommand\(\"power_cut_alert\",)",
    r"\1localPowerCutAlert = checked\n                                                \2",
    content
)

# systemLogEnabled
content = content.replace("checked = systemLogEnabled,", "checked = localSystemLogEnabled,")
content = re.sub(
    r"(onCheckedChange = \{\s*checked ->\s*)(onSendCommand\(\"system_log_enabled\",)",
    r"\1localSystemLogEnabled = checked\n                                                \2",
    content
)

# mode (localOpMode) -> FilterChip selected = currentOpMode == "monitor"
content = content.replace("selected = currentOpMode == \"monitor\",", "selected = localOpMode == \"monitor\",")
content = content.replace("selected = currentOpMode == \"detection\",", "selected = localOpMode == \"detection\",")
content = re.sub(
    r"(onClick = \{\s*)(onSendCommand\(\"mode\", \"monitor\"\))",
    r"\1localOpMode = \"monitor\"\n                                                \2",
    content
)
content = re.sub(
    r"(onClick = \{\s*)(onSendCommand\(\"mode\", \"detection\"\))",
    r"\1localOpMode = \"detection\"\n                                                \2",
    content
)

# lensFacing
content = content.replace("Text(\"切換為${if (lensFacing == \"back\") \"前置\" else \"後置\"}鏡頭\")", "Text(\"切換為${if (localLensFacing == \"back\") \"前置\" else \"後置\"}鏡頭\")")
content = re.sub(
    r"(onClick = \{\s*)(onSendCommand\(\"camera\", \"switch\"\))",
    r"\1localLensFacing = if (localLensFacing == \"back\") \"front\" else \"back\"\n                                                \2",
    content
)

# isTorchOn
content = content.replace("Text(if (isTorchOn) \"關閉補光燈\" else \"開啟補光燈\")", "Text(if (localTorchOn) \"關閉補光燈\" else \"開啟補光燈\")")
content = re.sub(
    r"(onClick = \{\s*)(onSendCommand\(\"torch\", if \(isTorchOn\) \"off\" else \"on\"\))",
    r"\1localTorchOn = !localTorchOn\n                                                onSendCommand(\"torch\", if (localTorchOn) \"on\" else \"off\")",
    content
)

# nightMode
content = content.replace("selected = nightMode == modeKey,", "selected = localNightMode == modeKey,")
content = re.sub(
    r"(onClick = \{\s*)(onSendCommand\(\"night_vision\", modeKey\))",
    r"\1localNightMode = modeKey\n                                                    \2",
    content
)


with open("app/src/main/java/com/example/ui/viewer/RemoteSettingsDialog.kt", "w") as f:
    f.write(content)
