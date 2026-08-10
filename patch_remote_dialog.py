import re

with open("app/src/main/java/com/example/ui/viewer/RemoteSettingsDialog.kt", "r") as f:
    content = f.read()

replacements = {
    "isMotion": ("localIsMotion", "isMotion"),
    "nightMode": ("localNightMode", "nightMode"),
    "currentOpMode": ("localOpMode", "currentOpMode"),
    "isTorchOn": ("localTorchOn", "isTorchOn"),
    "lensFacing": ("localLensFacing", "lensFacing"),
    "playLocalAlarm": ("localPlayLocalAlarm", "playLocalAlarm"),
    "mlKitEnabled": ("localMlKitEnabled", "mlKitEnabled"),
    "autoCleanup": ("localAutoCleanup", "autoCleanup"),
    "autoStartOnBoot": ("localAutoStartOnBoot", "autoStartOnBoot"),
    "powerCutAlertEnabled": ("localPowerCutAlert", "powerCutAlertEnabled"),
    "systemLogEnabled": ("localSystemLogEnabled", "systemLogEnabled")
}

insert_vars = []
for k, (loc_k, orig_k) in replacements.items():
    insert_vars.append(f"    var {loc_k} by remember({orig_k}) {{ mutableStateOf({orig_k}) }}")

insertion = "\n".join(insert_vars)

old_locals = """    var localStorageGB by remember(storageLimitGB) { mutableFloatStateOf(storageLimitGB) }
    var localMaxEvents by remember(maxEventCount) { mutableFloatStateOf(maxEventCount.toFloat()) }
    val systemLogEnabled = cameraStatusJson?.optBoolean("systemLogEnabled", true) ?: true"""

new_locals = f"""    var localStorageGB by remember(storageLimitGB) {{ mutableFloatStateOf(storageLimitGB) }}
    var localMaxEvents by remember(maxEventCount) {{ mutableFloatStateOf(maxEventCount.toFloat()) }}
    val systemLogEnabled = cameraStatusJson?.optBoolean("systemLogEnabled", true) ?: true

{insertion}"""
content = content.replace(old_locals, new_locals)

with open("app/src/main/java/com/example/ui/viewer/RemoteSettingsDialog.kt", "w") as f:
    f.write(content)
