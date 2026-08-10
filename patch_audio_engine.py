import re

with open("app/src/main/java/com/example/audio/AudioEngine.kt", "r") as f:
    text = f.read()

# Replace enableSpeakerphone implementation
old_speakerphone = """    fun enableSpeakerphone(context: Context, enable: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                audioManager.mode = if (enable) AudioManager.MODE_IN_COMMUNICATION else AudioManager.MODE_NORMAL
                audioManager.isSpeakerphoneOn = enable
            }
        } catch (e: Exception) {
            Log.w("AudioEngine", "Error setting speakerphone state", e)
        }
    }"""

new_speakerphone = """    fun enableSpeakerphone(context: Context, enable: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                // If we use USAGE_MEDIA, we don't need to force MODE_IN_COMMUNICATION which degrades audio.
                // We just let it play as normal media.
                audioManager.mode = AudioManager.MODE_NORMAL
                if (enable) {
                    audioManager.isSpeakerphoneOn = true
                } else {
                    audioManager.isSpeakerphoneOn = false
                }
            }
        } catch (e: Exception) {
            Log.w("AudioEngine", "Error setting speakerphone state", e)
        }
    }"""

text = text.replace(old_speakerphone, new_speakerphone)

with open("app/src/main/java/com/example/audio/AudioEngine.kt", "w") as f:
    f.write(text)
