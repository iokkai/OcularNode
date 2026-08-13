package io.github.iokkai.ocularnode.util

import android.content.Context
import io.github.iokkai.ocularnode.data.NotificationCategory
import io.github.iokkai.ocularnode.data.LabelMapper
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage

import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class MlKitAnalysisResult(
    val hasPerson: Boolean,
    val hasPet: Boolean,
    val detectedLabels: List<String>,
    val shouldSuppressNotification: Boolean, // true if Person only (主人在家 -> 攔截推播)
    val shouldTriggerRecording: Boolean,
    val summaryText: String
)

object MlKitFilterHelper {
    private const val TAG = "MlKitFilterHelper"

    private val objectDetector by lazy {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
        ObjectDetection.getClient(options)
    }


    private val imageLabeler by lazy {
        val options = ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.40f)
            .build()
        ImageLabeling.getClient(options)
    }

    private     val labelTranslations = mapOf(
        "team" to "團隊",
        "bonfire" to "營火",
        "comics" to "漫畫",
        "himalayan" to "喜馬拉雅貓",
        "iceberg" to "冰山",
        "bento" to "便當",
        "sink" to "水槽",
        "toy" to "玩具",
        "statue" to "雕像",
        "cheeseburger" to "起司漢堡",
        "tractor" to "拖拉機",
        "sled" to "雪橇",
        "aquarium" to "水族箱",
        "circus" to "馬戲團",
        "sitting" to "坐著",
        "beard" to "鬍子",
        "bridge" to "橋樑",
        "tights" to "緊身褲",
        "bird" to "鳥",
        "rafting" to "泛舟",
        "park" to "公園",
        "factory" to "工廠",
        "graduation" to "畢業",
        "porcelain" to "瓷器",
        "twig" to "樹枝",
        "petal" to "花瓣",
        "cushion" to "靠墊",
        "sunglasses" to "太陽眼鏡",
        "infrastructure" to "基礎設施",
        "ferris wheel" to "摩天輪",
        "pomacentridae" to "雀鯛",
        "wetsuit" to "潛水服",
        "shetland sheepdog" to "喜樂蒂牧羊犬",
        "brig" to "雙桅帆船",
        "watercolor paint" to "水彩畫",
        "competition" to "比賽",
        "cliff" to "懸崖",
        "badminton" to "羽毛球",
        "safari" to "狩獵旅行",
        "bicycle" to "腳踏車",
        "stadium" to "體育場",
        "boat" to "船",
        "smile" to "微笑",
        "surfboard" to "衝浪板",
        "fast food" to "速食",
        "sunset" to "夕陽",
        "hot dog" to "熱狗",
        "shorts" to "短褲",
        "bus" to "公車",
        "bullfighting" to "鬥牛",
        "sky" to "天空",
        "gerbil" to "沙鼠",
        "rock" to "岩石",
        "interaction" to "互動",
        "dress" to "洋裝",
        "toe" to "腳趾",
        "bear" to "熊",
        "eating" to "進食",
        "tower" to "塔",
        "brick" to "磚塊",
        "junk" to "垃圾/廢棄物",
        "person" to "人",
        "windsurfing" to "風帆衝浪",
        "swimwear" to "泳裝",
        "roller" to "滾筒",
        "camping" to "露營",
        "playground" to "遊樂場",
        "bathroom" to "浴室",
        "laugh" to "大笑",
        "balloon" to "氣球",
        "concert" to "演唱會",
        "prom" to "舞會",
        "construction" to "建築",
        "product" to "產品",
        "reef" to "礁石",
        "picnic" to "野餐",
        "wreath" to "花圈",
        "wheelbarrow" to "獨輪手推車",
        "boxer" to "拳擊手/拳師犬",
        "necklace" to "項鍊",
        "bracelet" to "手鍊",
        "casino" to "賭場",
        "windshield" to "擋風玻璃",
        "stairs" to "樓梯",
        "computer" to "電腦",
        "cookware and bakeware" to "鍋具與烘焙用具",
        "monochrome" to "單色",
        "chair" to "椅子",
        "poster" to "海報",
        "bar" to "酒吧",
        "shipwreck" to "沉船",
        "pier" to "碼頭",
        "community" to "社區",
        "caving" to "洞穴探險",
        "cave" to "洞穴",
        "tie" to "領帶",
        "cabinetry" to "櫥櫃",
        "underwater" to "水下",
        "clown" to "小丑",
        "nightclub" to "夜店",
        "cycling" to "自行車運動",
        "comet" to "彗星",
        "mortarboard" to "學位帽",
        "track" to "軌道/跑道",
        "christmas" to "聖誕節",
        "church" to "教堂",
        "clock" to "時鐘",
        "dude" to "傢伙",
        "cattle" to "牛",
        "jungle" to "叢林",
        "desk" to "書桌",
        "curling" to "冰壺",
        "cuisine" to "料理",
        "cat" to "貓",
        "juice" to "果汁",
        "couscous" to "古斯米",
        "screenshot" to "螢幕截圖",
        "crew" to "船員/團隊",
        "skyline" to "天際線",
        "stuffed toy" to "絨毛玩具",
        "cookie" to "餅乾",
        "tile" to "磁磚",
        "hanukkah" to "光明節",
        "crochet" to "鉤針編織",
        "skateboarder" to "滑板者",
        "clipper" to "快艇/帆船",
        "nail" to "指甲/釘子",
        "cola" to "可樂",
        "cutlery" to "餐具",
        "menu" to "菜單",
        "sari" to "紗麗",
        "plush" to "絨毛",
        "pocket" to "口袋",
        "neon" to "霓虹燈",
        "icicle" to "冰柱",
        "pasteles" to "糕點",
        "chain" to "鏈條",
        "dance" to "舞蹈",
        "dune" to "沙丘",
        "santa claus" to "聖誕老人",
        "thanksgiving" to "感恩節",
        "tuxedo" to "燕尾服",
        "mouth" to "嘴巴",
        "desert" to "沙漠",
        "dinosaur" to "恐龍",
        "mufti" to "便服",
        "fire" to "火",
        "bedroom" to "臥室",
        "goggles" to "護目鏡",
        "dragon" to "龍",
        "couch" to "沙發",
        "sledding" to "滑雪橇",
        "cap" to "帽子",
        "whiteboard" to "白板",
        "hat" to "帽子",
        "gelato" to "義式冰淇淋",
        "cavalier" to "騎士/查理斯王騎士犬",
        "beanie" to "毛帽",
        "jersey" to "運動衫",
        "scarf" to "圍巾",
        "vacation" to "假期",
        "pitch" to "球場/投球",
        "blackboard" to "黑板",
        "deejay" to "DJ",
        "monument" to "紀念碑",
        "bumper" to "保險桿",
        "longboard" to "長板",
        "waterfowl" to "水禽",
        "flesh" to "肉",
        "net" to "網",
        "icing" to "糖霜",
        "dalmatian" to "大麥町犬",
        "speedboat" to "快艇",
        "trunk" to "樹幹/後車廂",
        "coffee" to "咖啡",
        "soccer" to "足球",
        "ragdoll" to "布偶貓",
        "food" to "食物",
        "standing" to "站立",
        "fiction" to "虛構/小說",
        "fruit" to "水果",
        "pho" to "越南河粉",
        "sparkler" to "仙女棒",
        "presentation" to "簡報",
        "swing" to "鞦韆",
        "cairn terrier" to "凱恩㹴",
        "forest" to "森林",
        "flag" to "旗幟",
        "frigate" to "巡防艦",
        "foot" to "腳",
        "jacket" to "夾克",
        "pillow" to "枕頭",
        "bathing" to "沐浴",
        "glacier" to "冰川",
        "gymnastics" to "體操",
        "ear" to "耳朵",
        "flora" to "植物",
        "shell" to "貝殼",
        "grandparent" to "祖父母",
        "ruins" to "廢墟",
        "eyelash" to "睫毛",
        "bunk bed" to "雙層床",
        "balance" to "平衡",
        "backpacking" to "背包客旅行",
        "horse" to "馬",
        "glitter" to "閃光",
        "saucer" to "茶碟",
        "hair" to "頭髮",
        "miniature" to "微縮模型",
        "crowd" to "人群",
        "curtain" to "窗簾",
        "icon" to "圖示",
        "pixie-bob" to "北美洲短尾貓",
        "herd" to "獸群",
        "insect" to "昆蟲",
        "ice" to "冰",
        "bangle" to "手鐲",
        "flap" to "翻蓋",
        "jewellery" to "珠寶",
        "knitting" to "編織",
        "centrepiece" to "裝飾擺設",
        "outerwear" to "外套",
        "love" to "愛",
        "muscle" to "肌肉",
        "motorcycle" to "摩托車",
        "money" to "錢",
        "mosque" to "清真寺",
        "tableware" to "餐具",
        "ballroom" to "舞廳",
        "kayak" to "獨木舟",
        "leisure" to "休閒",
        "receipt" to "收據",
        "lake" to "湖泊",
        "lighthouse" to "燈塔",
        "bridle" to "馬韁繩",
        "leather" to "皮革",
        "horn" to "喇叭/角",
        "strap" to "帶子",
        "lego" to "樂高",
        "scuba diving" to "水肺潛水",
        "leggings" to "內搭褲",
        "pool" to "游泳池",
        "musical instrument" to "樂器",
        "musical" to "音樂劇",
        "metal" to "金屬",
        "moon" to "月亮",
        "blazer" to "西裝外套",
        "marriage" to "婚姻",
        "mobile phone" to "手機",
        "militia" to "民兵",
        "tablecloth" to "桌布",
        "party" to "派對",
        "nebula" to "星雲",
        "news" to "新聞",
        "newspaper" to "報紙",
        "piano" to "鋼琴",
        "plant" to "植物",
        "passport" to "護照",
        "penguin" to "企鵝",
        "shikoku" to "四國犬",
        "palace" to "宮殿",
        "doily" to "蕾絲桌墊",
        "polo" to "馬球",
        "paper" to "紙",
        "pop music" to "流行音樂",
        "skiff" to "小艇",
        "pizza" to "披薩",
        "pet" to "寵物",
        "quilting" to "絎縫",
        "cage" to "籠子",
        "skateboard" to "滑板",
        "surfing" to "衝浪",
        "rugby" to "橄欖球",
        "lipstick" to "口紅",
        "river" to "河流",
        "race" to "競速",
        "rowing" to "划船",
        "road" to "道路",
        "running" to "跑步",
        "room" to "房間",
        "roof" to "屋頂",
        "star" to "星星",
        "sports" to "運動",
        "shoe" to "鞋子",
        "tubing" to "輪胎漂流",
        "space" to "太空",
        "sleep" to "睡眠",
        "skin" to "皮膚",
        "swimming" to "游泳",
        "school" to "學校",
        "sushi" to "壽司",
        "loveseat" to "雙人沙發",
        "superman" to "超人",
        "cool" to "酷",
        "skiing" to "滑雪",
        "submarine" to "潛水艇",
        "song" to "歌曲",
        "class" to "班級",
        "skyscraper" to "摩天大樓",
        "volcano" to "火山",
        "television" to "電視",
        "rein" to "韁繩",
        "tattoo" to "刺青",
        "train" to "火車",
        "handrail" to "扶手",
        "cup" to "杯子",
        "vehicle" to "車輛",
        "handbag" to "手提包",
        "lampshade" to "燈罩",
        "event" to "活動",
        "wine" to "葡萄酒",
        "wing" to "翅膀",
        "wheel" to "輪子",
        "wakeboarding" to "寬板滑水",
        "web page" to "網頁",
        "ranch" to "牧場",
        "fishing" to "釣魚",
        "heart" to "心",
        "cotton" to "棉花",
        "cappuccino" to "卡布奇諾",
        "bread" to "麵包",
        "sand" to "沙",
        "museum" to "博物館",
        "helicopter" to "直升機",
        "mountain" to "山",
        "duck" to "鴨子",
        "soil" to "土壤",
        "turtle" to "烏龜",
        "crocodile" to "鱷魚",
        "musician" to "音樂家",
        "sneakers" to "運動鞋",
        "wool" to "羊毛",
        "ring" to "戒指",
        "singer" to "歌手",
        "carnival" to "嘉年華",
        "snowboarding" to "單板滑雪",
        "waterskiing" to "滑水",
        "wall" to "牆壁",
        "rocket" to "火箭",
        "countertop" to "流理台",
        "beach" to "海灘",
        "rainbow" to "彩虹",
        "branch" to "樹枝",
        "moustache" to "鬍鬚",
        "garden" to "花園",
        "gown" to "禮服",
        "field" to "田野",
        "dog" to "狗",
        "superhero" to "超級英雄",
        "flower" to "花",
        "placemat" to "餐墊",
        "subwoofer" to "重低音喇叭",
        "cathedral" to "大教堂",
        "building" to "建築物",
        "airplane" to "飛機",
        "fur" to "毛皮",
        "bull" to "公牛",
        "bench" to "長椅",
        "temple" to "寺廟",
        "butterfly" to "蝴蝶",
        "model" to "模型/模特兒",
        "marathon" to "馬拉松",
        "needlework" to "針線活",
        "kitchen" to "廚房",
        "castle" to "城堡",
        "aurora" to "極光",
        "larva" to "幼蟲",
        "racing" to "賽車",
        "airliner" to "客機",
        "dam" to "水壩",
        "textile" to "紡織品",
        "groom" to "新郎",
        "fun" to "樂趣",
        "steaming" to "蒸氣",
        "vegetable" to "蔬菜",
        "unicycle" to "單輪車",
        "jeans" to "牛仔褲",
        "flowerpot" to "花盆",
        "drawer" to "抽屜",
        "cake" to "蛋糕",
        "armrest" to "扶手",
        "aviation" to "航空",
        "fog" to "霧",
        "fireworks" to "煙火",
        "farm" to "農場",
        "seal" to "海豹",
        "shelf" to "架子",
        "bangs" to "瀏海",
        "lightning" to "閃電",
        "van" to "箱型車",
        "sphynx" to "斯芬克斯貓",
        "tire" to "輪胎",
        "denim" to "丹寧布",
        "prairie" to "草原",
        "snorkeling" to "浮潛",
        "umbrella" to "雨傘",
        "asphalt" to "柏油路",
        "sailboat" to "帆船",
        "basset hound" to "巴吉度獵犬",
        "pattern" to "圖案",
        "supper" to "晚餐",
        "veil" to "面紗",
        "waterfall" to "瀑布",
        "lunch" to "午餐",
        "odometer" to "里程表",
        "baby" to "嬰兒",
        "glasses" to "眼鏡",
        "car" to "汽車",
        "aircraft" to "航空器",
        "hand" to "手",
        "rodeo" to "競技",
        "canyon" to "峽谷",
        "meal" to "膳食",
        "softball" to "壘球",
        "alcohol" to "酒精",
        "bride" to "新娘",
        "swamp" to "沼澤",
        "pie" to "派",
        "bag" to "包包",
        "joker" to "小丑",
        "supervillain" to "超級反派",
        "army" to "軍隊",
        "canoe" to "獨木舟",
        "selfie" to "自拍",
        "rickshaw" to "人力車",
        "barn" to "穀倉",
        "archery" to "射箭",
        "aerospace engineering" to "航太工程",
        "storm" to "風暴",
        "helmet" to "安全帽",
    )

    /**
     * @param enabledCategories: 請從 ViewModel 或外部傳入當前使用者開啟的類別，切勿在此函式內讀取 DataStore！
     */
    suspend fun analyzeFrame(
        context: Context, 
        bitmap: Bitmap, 
        enabledCategories: Set<NotificationCategory>,
        enabledRecordingCategories: Set<NotificationCategory>
    ): MlKitAnalysisResult = withContext(Dispatchers.IO) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            // 使用 kotlinx.coroutines.tasks.await() 來處理 Play Services Task
            val detectedObjects = try {
                objectDetector.process(inputImage).await()
            } catch (e: Exception) {
                emptyList()
            }

val imageLabels = try {
                imageLabeler.process(inputImage).await()
            } catch (e: Exception) {
                emptyList()
            }

            // 統合所有的 Label，統一轉小寫並去除重複
            val allLabelTexts = (detectedObjects.flatMap { it.labels }.map { it.text } + imageLabels.map { it.text })
                                .map { it.lowercase() }
                                .distinct()

            // 1. 判斷是否有匹配到「開啟」或「關閉」的類別
            var hasEnabledCategory = false
            var hasDisabledCategory = false
            var triggerCategoryName = ""

            for (label in allLabelTexts) {
                val cat = LabelMapper.getCategory(label)
                if (cat != NotificationCategory.OTHER) {
                    if (enabledCategories.contains(cat)) {
                        hasEnabledCategory = true
                        triggerCategoryName = cat.displayName // 記錄觸發的類別名稱用於 Log
                        break // 只要有一個開啟的類別命中，就確定放行，不用再檢查了！
                    } else {
                        hasDisabledCategory = true
                    }
                }
            }

            // 判斷該次推播是否因為類別設定被攔截
            // 邏輯：如果有找到標籤，且「沒有任何開啟的類別」但「有關閉的類別」，則攔截。
            val isCategoryEnabled = if (allLabelTexts.isNotEmpty() && !hasEnabledCategory && hasDisabledCategory) {
                false
            } else {
                true // 包含完全沒辨識出具體類別 (OTHER) 的情況，預設放行
            }

            val personKeywords = listOf("person", "human", "man", "woman", "boy", "girl", "child", "people", "hand", "dude", "clown", "face", "head", "portrait", "hair", "skin", "nose", "eye", "mouth", "smile", "skateboarder", "deejay", "grandparent", "crowd", "musician", "singer", "superhero", "model", "groom", "baby", "bride", "joker", "supervillain")
            val petKeywords = listOf(
                "dog", "cat", "pet", "animal", "canine", "feline", "puppy", "kitten", "mammal", 
                "bird", "carnivore", "fauna", "shetland sheepdog", "gerbil", "bear", "dalmatian", 
                "ragdoll", "cairn terrier", "pixie-bob", "horse", "penguin", "shikoku", "duck", 
                "turtle", "crocodile", "bull", "butterfly", "larva", "sphynx", "basset hound", "seal"
            )

            val hasPerson = allLabelTexts.any { label -> personKeywords.any { label.contains(it) } }
            val hasPet = allLabelTexts.any { label -> petKeywords.any { label.contains(it) } }

            // 二階段判斷邏輯：
            // - 若類別被關閉 (!isCategoryEnabled)：攔截推播
            // - 若識別只有人類 (hasPerson && !hasPet)：視為主人在家，攔截推播通知 (進入冷卻，不警報)
            // - 若有寵物 (hasPet) 或未辨識出人類 (!hasPerson)：正常發送事件推播
            var hasEnabledRecordingCategory = false
            var hasDisabledRecordingCategory = false
            for (label in allLabelTexts) {
                val cat = LabelMapper.getCategory(label)
                if (cat != NotificationCategory.OTHER) {
                    if (enabledRecordingCategories.contains(cat)) {
                        hasEnabledRecordingCategory = true
                        break
                    } else {
                        hasDisabledRecordingCategory = true
                    }
                }
            }
            
            val isRecordingCategoryEnabled = if (allLabelTexts.isNotEmpty() && !hasEnabledRecordingCategory && hasDisabledRecordingCategory) {
                false
            } else {
                true
            }

            val shouldSuppress = !isCategoryEnabled || (hasPerson && !hasPet)
            val shouldTriggerRecording = isRecordingCategoryEnabled && !(hasPerson && !hasPet)
            
            val translatedLabels = allLabelTexts.map { labelTranslations[it] ?: it }
            
            val summaryText = when {
                !isCategoryEnabled -> "$allLabelTexts 類別停用通知"
                hasPerson && hasPet -> "人類 + 寵物 (發送警報)"
                hasPerson -> "已過濾純人類畫面"
                hasPet -> "偵測到寵物 (發送警報)"
                translatedLabels.isNotEmpty() -> "畫面異動 [${translatedLabels.take(3).joinToString()}]"
                else -> "動態異動 (未分類)"
            }

            AppLogger.d(TAG, "原始物件標籤: $allLabelTexts")
            AppLogger.d(TAG, "命中開啟類別: $hasEnabledCategory (觸發類別: $triggerCategoryName), 命中關閉類別: $hasDisabledCategory")
            AppLogger.d(TAG, "是否包含人臉/人類 (hasPerson): $hasPerson, 是否包含寵物 (hasPet): $hasPet")
            AppLogger.d(TAG, "最終過濾決定: suppress=$shouldSuppress (原因: $summaryText)")

            MlKitAnalysisResult(
                hasPerson = hasPerson,
                hasPet = hasPet,
                detectedLabels = translatedLabels,
                shouldSuppressNotification = shouldSuppress,
                shouldTriggerRecording = shouldTriggerRecording,
                summaryText = summaryText
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "ML Kit 分析失敗", e)
            MlKitAnalysisResult(
                hasPerson = false,
                hasPet = false,
                detectedLabels = emptyList(),
                shouldSuppressNotification = false,
                shouldTriggerRecording = true, // 發生錯誤時預設允許錄影
                summaryText = "畫面異動"
            )
        }
    }
}
