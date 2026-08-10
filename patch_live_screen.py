with open("app/src/main/java/com/example/ui/viewer/LiveMonitorScreen.kt", "r") as f:
    content = f.read()

old_call = """        RemoteSettingsScreen(
            cameraName = camera.name,
            cameraStatusJson = cameraStatusJson,
            onSendCommand = { cmd, valStr -> viewModel.sendControlCommand(cmd, valStr) },
            onSyncTelegram = { viewModel.syncTelegramToCurrentCamera() },
            onNavigateBack = { showRemoteSettingsDialog = false }
        )"""

new_call = """        RemoteSettingsScreen(
            cameraName = camera.name,
            cameraStatusJson = cameraStatusJson,
            onSendCommand = { cmd, valStr -> viewModel.sendControlCommand(cmd, valStr) },
            onSyncTelegram = { viewModel.syncTelegramToCurrentCamera() },
            onNavigateBack = { showRemoteSettingsDialog = false },
            onFetchLogs = { viewModel.fetchRemoteLogs(camera) }
        )"""

content = content.replace(old_call, new_call)

with open("app/src/main/java/com/example/ui/viewer/LiveMonitorScreen.kt", "w") as f:
    f.write(content)
