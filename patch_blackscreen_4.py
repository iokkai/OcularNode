with open("app/src/main/java/com/example/ui/camera/BlackScreenOverlay.kt", "r") as f:
    content = f.read()

import re

old_ui = re.search(r"        AnimatedVisibility\(\n            visible = showHint,.*?\n            \}\n        \}\n    \}\n\}", content, re.DOTALL).group(0)

new_ui = """        // Simple Digital Clock
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = currentTime,
                color = Color.White.copy(alpha = 0.3f), // Dim clock to prevent screen burn-in
                fontSize = 48.sp,
                fontWeight = FontWeight.Light
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Live",
                    tint = Color.Red.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "LIVE • Battery $batteryPct%",
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        AnimatedVisibility(
            visible = showHint,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Power Saving Lock",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "省電模式運作中",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "連按兩下 (Double Tap) 解除省電模式",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
            }
        }
    }
}"""

content = content.replace(old_ui, new_ui)

with open("app/src/main/java/com/example/ui/camera/BlackScreenOverlay.kt", "w") as f:
    f.write(content)

