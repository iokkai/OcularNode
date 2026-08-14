package io.github.iokkai.ocularnode.data

import android.content.Context
import androidx.annotation.StringRes
import io.github.iokkai.ocularnode.R

enum class NotificationCategory(@StringRes val titleRes: Int, val displayName: String) {
    HUMAN_AND_ACTIVITY(R.string.category_human_and_activity, "Human & Activity"),
    PET_AND_ANIMAL(R.string.category_pet_and_animal, "Pet & Animal"),
    VEHICLE_AND_TRANSPORT(R.string.category_vehicle_and_transport, "Vehicle & Transport"),
    HOUSEHOLD_ITEM(R.string.category_household_item, "Household Item"),
    ENVIRONMENT_AND_NATURE(R.string.category_environment_and_nature, "Environment & Nature"),
    OTHER(R.string.category_other, "Other");

    fun getLocalizedTitle(context: Context): String {
        return context.getString(titleRes)
    }
}

object LabelMapper {
    // 預先構建靜態 Map，達成 O(1) 零記憶體配置查詢
    private val categoryMap: Map<String, NotificationCategory> = buildMap {
        // 1. 人類與活動 (HUMAN_AND_ACTIVITY)
        listOf(
            "person", "human", "man", "woman", "boy", "girl", "child", "people", "hand", 
            "dude", "grandparent", "baby", "crowd", "clown", "skateboarder", "deejay", "musician", 
            "singer", "superhero", "model", "groom", "bride", "joker", "supervillain", "team", 
            "graduation", "interaction", "eating", "laugh", "smile", "dance", "vacation", "standing", 
            "bathing", "gymnastics", "balance", "backpacking", "marriage", "party", "news", "leisure", 
            "sports", "running", "swimming", "skiing", "sleep", "fun", "carnival", "surfing", 
            "snowboarding", "waterskiing", "wakeboarding", "fishing", "scuba diving", "snorkeling", 
            "camping", "picnic", "caving", "selfie", "beard", "sitting", "foot", "toe", "ear", 
            "eyelash", "hair", "muscle", "flesh", "boxing", "boxer", "curling", "soccer", "badminton", 
            "bullfighting", "prom", "concert", "competition", "safari", "rafting", "windsurfing", 
            "sledding", "knitting", "crochet", "archery"
        ).forEach { put(it, NotificationCategory.HUMAN_AND_ACTIVITY) }

        // 2. 寵物與動物 (PET_AND_ANIMAL)
        listOf(
            "dog", "cat", "pet", "animal", "canine", "feline", "puppy", "kitten", "mammal", 
            "bird", "carnivore", "fauna", "shetland sheepdog", "gerbil", "bear", "dalmatian", "ragdoll", 
            "cairn terrier", "pixie-bob", "horse", "penguin", "shikoku", "duck", "turtle", "crocodile", 
            "bull", "butterfly", "larva", "sphynx", "basset hound", "seal", "pomacentridae", "waterfowl", 
            "insect", "cattle", "herd", "dinosaur", "dragon", "himalayan", "cavalier", "fish", "whale", 
            "shark", "lion", "tiger", "wolf", "fox", "rabbit", "bunny", "hamster", "mouse", "rat", 
            "squirrel", "deer", "sheep", "goat", "pig", "cow", "chicken", "rooster", "eagle", "owl", "parrot"
        ).forEach { put(it, NotificationCategory.PET_AND_ANIMAL) }

        // 3. 交通工具 (VEHICLE_AND_TRANSPORT)
        listOf(
            "car", "vehicle", "bus", "truck", "motorcycle", "bicycle", "boat", "ship", 
            "airplane", "aircraft", "helicopter", "train", "submarine", "tractor", "sled", "brig", 
            "sailboat", "speedboat", "skiff", "clipper", "canoe", "kayak", "van", "airliner", 
            "rocket", "wheel", "tire", "bumper", "windshield", "odometer", "rickshaw", "frigate", 
            "shipwreck", "ferris wheel", "wheelbarrow", "roller", "longboard", "scooter", "tram", 
            "trolley", "locomotive"
        ).forEach { put(it, NotificationCategory.VEHICLE_AND_TRANSPORT) }

        // 4. 居家物品 (HOUSEHOLD_ITEM)
        listOf(
            "sink", "toy", "cushion", "chair", "desk", "couch", "bunk bed", "bed", 
            "cabinetry", "drawer", "shelf", "computer", "mobile phone", "television", "lampshade", 
            "cookware and bakeware", "tableware", "cutlery", "glass", "cup", "saucer", "plate", 
            "pillow", "curtain", "tablecloth", "placemat", "mirror", "clock", "scissors", "toothbrush", 
            "bathtub", "toilet", "refrigerator", "oven", "microwave", "loveseat", "piano", 
            "musical instrument", "stuffed toy", "plush", "box", "bag", "handbag", "suitcase", "shoe", 
            "sneakers", "clothing", "dress", "shorts", "swimwear", "jacket", "outerwear", "jeans", "denim", 
            "tuxedo", "shirt", "hat", "cap", "beanie", "sunglasses", "glasses", "goggles", "necklace", 
            "bracelet", "ring", "jewellery", "umbrella", "book", "newspaper", "magazine", "paper", 
            "aquarium", "porcelain", "helmet", "tights", "bento", "cheeseburger", "hot dog", "fast food", 
            "pasteles", "cookie", "gelato", "food", "cuisine", "coffee", "juice", "cola", "alcohol", 
            "wine", "beer", "pie", "bread", "meal", "fruit", "pho", "couscous", "blazer", "jersey", 
            "scarf", "tie", "leggings", "wetsuit", "sari", "bangle", "bottle", "can", "pot", "pan", 
            "kettle", "knife", "fork", "spoon", "remote control", "keyboard", "mouse", "laptop", 
            "monitor", "speaker", "camera", "headphone", "earphone", "lamp", "light", "candle", 
            "vase", "blanket", "towel", "soap", "shampoo", "broom", "mop", "trash can", "bin", 
            "rug", "carpet", "door", "window", "key", "wallet", "coin", "credit card", "passport", 
            "balloon", "wreath", "brick", "tile", "whiteboard", "blackboard", "poster", "stairs", 
            "receipt", "lego", "chain", "strap", "bridle", "leather", "metal", "nail", "pocket", "flap"
        ).forEach { put(it, NotificationCategory.HOUSEHOLD_ITEM) }

        // 5. 環境與自然 (ENVIRONMENT_AND_NATURE)
        listOf(
            "sky", "skyline", "sunset", "sun", "moon", "star", "nebula", "comet", 
            "aurora", "space", "cloud", "fog", "storm", "lightning", "water", "lake", "river", "ocean", 
            "sea", "beach", "sand", "soil", "rock", "cliff", "mountain", "iceberg", "glacier", "snow", 
            "ice", "icicle", "waterfall", "swamp", "forest", "jungle", "tree", "branch", "twig", "leaf", 
            "plant", "flower", "petal", "flora", "garden", "grass", "field", "prairie", "park", "rainbow", 
            "cave", "dune", "canyon", "reef", "underwater", "volcano", "bonfire", "fire", "sparkler", 
            "glitter", "shell", "ruins", "monument", "lighthouse", "pier", "barn", "factory", 
            "bridge", "tower", "castle", "mosque", "church", "infrastructure", "stadium"
        ).forEach { put(it, NotificationCategory.ENVIRONMENT_AND_NATURE) }
    }

    fun getCategory(label: String): NotificationCategory {
        val lowerLabel = label.lowercase().trim()
        if (lowerLabel.isEmpty()) return NotificationCategory.OTHER

        // 1. O(1) 快速完整匹配
        categoryMap[lowerLabel]?.let { return it }

        // 2. 複合片語拆詞比對 (例如 "domestic cat" -> 逐詞比對 "domestic", "cat")
        val tokens = lowerLabel.split(" ", "-", "_")
        for (token in tokens) {
            if (token.isNotEmpty()) {
                categoryMap[token]?.let { return it }
            }
        }

        return NotificationCategory.OTHER
    }
}
