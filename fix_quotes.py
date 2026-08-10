import re

with open("app/src/main/java/com/example/server/MjpegHttpServer.kt", "r") as f:
    text = f.read()

text = text.replace('sendJsonResponse(output, 200, "{\\"status\\":\\"cleared\\"}")', 'sendJsonResponse(output, 200, "{\\"status\\":\\"cleared\\"}")')
text = text.replace('sendJsonResponse(output, 500, "{\\"error\\":\\"Internal Server Error\\"}")', 'sendJsonResponse(output, 500, "{\\"error\\":\\"Internal Server Error\\"}")')

# Wait, if I used double quotes in my python script before...
# Let's just fix it by replacing the bad strings directly.
