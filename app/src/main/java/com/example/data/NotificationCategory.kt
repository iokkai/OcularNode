package com.example.data

enum class NotificationCategory(val displayName: String) {
    HUMAN_AND_ACTIVITY("人類與活動"),
    PET_AND_ANIMAL("寵物與動物"),
    VEHICLE_AND_TRANSPORT("交通工具"),
    HOUSEHOLD_ITEM("居家物品"),
    ENVIRONMENT_AND_NATURE("環境與自然"),
    OTHER("其他")
}

object LabelMapper {
    fun getCategory(label: String): NotificationCategory {
        val lowerLabel = label.lowercase()
        return when {
            // Human & Activities
            lowerLabel in listOf("person", "human", "man", "woman", "boy", "girl", "child", "people", "hand", 
                "dude", "grandparent", "baby", "crowd", "clown", "skateboarder", "deejay", "musician", "singer", 
                "superhero", "model", "groom", "bride", "joker", "supervillain", "team", "graduation", "interaction", 
                "eating", "laugh", "smile", "dance", "vacation", "standing", "bathing", "gymnastics", "balance", 
                "backpacking", "marriage", "party", "news", "leisure", "sports", "running", "swimming", "skiing", 
                "sleep", "fun", "carnival", "surfing", "snowboarding", "waterskiing", "wakeboarding", "fishing", 
                "scuba diving", "snorkeling", "camping", "picnic", "caving") -> NotificationCategory.HUMAN_AND_ACTIVITY

            // Pets & Animals
            lowerLabel in listOf("dog", "cat", "pet", "animal", "canine", "feline", "puppy", "kitten", "mammal", 
                "bird", "carnivore", "fauna", "shetland sheepdog", "gerbil", "bear", "dalmatian", "ragdoll", 
                "cairn terrier", "pixie-bob", "horse", "penguin", "shikoku", "duck", "turtle", "crocodile", 
                "bull", "butterfly", "larva", "sphynx", "basset hound", "seal", "pomacentridae", "waterfowl", 
                "insect", "cattle", "herd", "dinosaur", "dragon") -> NotificationCategory.PET_AND_ANIMAL

            // Vehicles & Transport
            lowerLabel in listOf("car", "vehicle", "bus", "truck", "motorcycle", "bicycle", "boat", "ship", 
                "airplane", "aircraft", "helicopter", "train", "submarine", "tractor", "sled", "rafting", 
                "brig", "sailboat", "speedboat", "skiff", "clipper", "canoe", "kayak", "van", "airliner", 
                "rocket", "wheel", "tire", "bumper", "windshield", "odometer") -> NotificationCategory.VEHICLE_AND_TRANSPORT

            // Household Items
            lowerLabel in listOf("sink", "toy", "cushion", "chair", "desk", "couch", "bunk bed", "bed", 
                "cabinetry", "drawer", "shelf", "computer", "mobile phone", "television", "lampshade", 
                "cookware and bakeware", "tableware", "cutlery", "glass", "cup", "saucer", "plate", 
                "pillow", "curtain", "tablecloth", "placemat", "mirror", "clock", "scissors", "toothbrush", 
                "bathtub", "toilet", "refrigerator", "oven", "microwave", "couch", "loveseat", "piano", 
                "musical instrument", "stuffed toy", "plush", "box", "bag", "handbag", "suitcase", "shoe", 
                "sneakers", "clothing", "dress", "shorts", "swimwear", "jacket", "outerwear", "jeans", "denim", 
                "tuxedo", "shirt", "hat", "cap", "beanie", "sunglasses", "glasses", "goggles", "necklace", 
                "bracelet", "ring", "jewellery", "umbrella", "book", "newspaper", "magazine", "paper") -> NotificationCategory.HOUSEHOLD_ITEM

            // Environment & Nature
            lowerLabel in listOf("sky", "skyline", "sunset", "sun", "moon", "star", "nebula", "comet", 
                "aurora", "space", "cloud", "fog", "storm", "lightning", "water", "lake", "river", "ocean", 
                "sea", "beach", "sand", "soil", "rock", "cliff", "mountain", "iceberg", "glacier", "snow", 
                "ice", "icicle", "waterfall", "swamp", "forest", "jungle", "tree", "branch", "twig", "leaf", 
                "plant", "flower", "petal", "flora", "garden", "grass", "field", "prairie", "park", "rainbow", 
                "cave", "dune", "canyon", "reef", "underwater", "volcano") -> NotificationCategory.ENVIRONMENT_AND_NATURE

            else -> NotificationCategory.OTHER
        }
    }
}
