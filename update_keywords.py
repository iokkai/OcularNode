import re

with open("app/src/main/java/com/example/util/MlKitFilterHelper.kt", "r") as f:
    content = f.read()

person_keywords = '"person", "human", "man", "woman", "boy", "girl", "child", "people", "hand", "dude", "clown", "skateboarder", "deejay", "grandparent", "crowd", "musician", "singer", "superhero", "model", "groom", "baby", "bride", "joker", "supervillain"'

content = re.sub(
    r'val personKeywords = listOf\([^\)]+\)',
    f'val personKeywords = listOf({person_keywords})',
    content,
    flags=re.DOTALL
)

with open("app/src/main/java/com/example/util/MlKitFilterHelper.kt", "w") as f:
    f.write(content)
