package com.notee.pinyin.festival

/**
 * One origin and five blessings for each festival, Simplified and English.
 * A tap picks one blessing on the device. Chinese stays within 50 characters,
 * English within 100 (docs/02-0 §5).
 */
object BlessingCopy {
    const val COUNT = 5

    fun origin(festival: Festival, english: Boolean): String = lines(festival).let { if (english) it.originEn else it.originZh }

    fun blessing(festival: Festival, english: Boolean, index: Int): String {
        val list = lines(festival).let { if (english) it.en else it.zh }
        return list[index]
    }

    private fun lines(festival: Festival): Lines = when (festival) {
        Festival.NEW_YEAR -> Lines(
            "公历岁末与元旦，把旧的一年轻轻放下。",
            "The last night of the year and the first morning, for setting the old year down.",
            listOf(
                "愿新的一年，日子平稳，心里有光。",
                "旧岁已妥帖放下，新的一天请慢些来。",
                "愿你所念之人都安好，来年常得相见。",
                "把门轻轻带上，把好的带进新的一年。",
                "愿这一年，忙有着落，闲也踏实。",
            ),
            listOf(
                "May the new year be steady, and may there be light in it.",
                "Set the old year down. Let the new day arrive slowly.",
                "May the people you miss be well, and near enough to see.",
                "Close one door gently, and carry the good things through.",
                "May the busy days land, and the quiet ones feel solid.",
            ),
        )
        Festival.VALENTINE -> Lines(
            "二月十四日，把心里的话交给亲近的人。",
            "February 14, a day for saying the true thing to someone close.",
            listOf(
                "喜欢可以说得轻一点，但请说出来。",
                "愿你身边有人，也愿你待自己好。",
                "今日宜把一句真话，交给想告诉的人。",
                "一朵花，一封信，都算数。",
                "愿相爱的人彼此看顾，不必声张。",
            ),
            listOf(
                "Say it lightly if you like, but do say it.",
                "May someone be near you, and may you be kind to yourself.",
                "Today is a good day for one true sentence.",
                "A flower or a letter is enough to count.",
                "May people who love each other look after one another.",
            ),
        )
        Festival.EASTER -> Lines(
            "春分满月后的星期日，西方纪念重生的日子。",
            "The Sunday after the spring full moon, kept in the West for beginning again.",
            listOf(
                "春天把颜色还回来了，愿你也缓一缓。",
                "过了长冬，就允许自己重新开始。",
                "愿失去的被温柔记住，新的慢慢长出来。",
                "一枝花就够，不必赶着热闹。",
                "愿你眼里有春光，脚下有软土。",
            ),
            listOf(
                "Spring is handing the colors back. You may slow down too.",
                "The long winter is over. Beginning again is allowed.",
                "May what was lost be remembered gently, and the new grow slowly.",
                "One sprig is enough. There is no need to hurry the noise.",
                "May there be spring in your eyes, and soft ground underfoot.",
            ),
        )
        Festival.INDEPENDENCE -> Lines(
            "七月四日，美国纪念建国的日子。",
            "July 4, the day the United States marks its founding.",
            listOf(
                "愿你有自己的节奏，也不孤身硬撑。",
                "能选择善良，也能选择休息，就很好。",
                "今日记得：你走过的路，都算数。",
                "愿烟火散去以后，日子依然明亮。",
                "把这一天过得简单，也过得高兴。",
            ),
            listOf(
                "May you keep your own pace, and not have to hold it all alone.",
                "It is enough to be able to choose kindness, and also rest.",
                "Remember today: the road you have walked still counts.",
                "May the day stay bright after the fireworks go out.",
                "Let this day be simple, and let it be glad.",
            ),
        )
        Festival.HALLOWEEN -> Lines(
            "十月三十一日夜，装扮与南瓜灯的西方节日。",
            "October 31, an evening of costumes, pumpkin lanterns, and playful dark.",
            listOf(
                "灯亮着，好玩就好，不必真的害怕。",
                "愿今晚的装扮都开心，回家的路都安全。",
                "南瓜灯暖一点，故事就不必太吓人。",
                "和朋友闹一闹，然后好好睡一觉。",
                "暗处有灯，就没有那么暗。",
            ),
            listOf(
                "Keep a lamp lit. Fun is enough; there is no need to be afraid.",
                "May the costumes be joyful, and the way home be safe.",
                "A warmer pumpkin means the story need not be so frightening.",
                "Make a little noise with friends, then sleep well.",
                "Where there is a light, the dark is not so dark.",
            ),
        )
        Festival.THANKSGIVING -> Lines(
            "十一月第四个星期四，家人围坐致谢的日子。",
            "The fourth Thursday of November, a table set for giving thanks.",
            listOf(
                "谢一顿热饭，也谢陪你坐着的人。",
                "把谢谢说出口，比放在心里更暖。",
                "愿桌上有余，屋里有人，心里有底。",
                "今日宜慢食，宜回头看看这一年。",
                "小小的好，也值得认真谢一次。",
            ),
            listOf(
                "Thank the hot meal, and thank the person sitting with you.",
                "A thank-you said aloud is warmer than one kept inside.",
                "May the table have enough, the room have people, the heart have ground.",
                "Today, eat slowly, and look back over the year.",
                "A small good thing is still worth thanking properly.",
            ),
        )
        Festival.BONFIRE -> Lines(
            "十一月五日，英国以烟火和篝火度过的夜。",
            "November 5 in Britain, a night of bonfires and fireworks.",
            listOf(
                "火焰旺一点，夜就短一点。",
                "愿烟火照见你想再见的人。",
                "站远一点看火，也站近一点看朋友。",
                "热闹过后，愿家里依然温暖。",
                "把这一夜过亮，把明天留给平常。",
            ),
            listOf(
                "A brighter fire makes a shorter night.",
                "May the fireworks show you the people you want to see again.",
                "Stand back to watch the fire, and close to watch your friends.",
                "After the noise, may home still be warm.",
                "Let tonight be bright, and leave tomorrow ordinary.",
            ),
        )
        Festival.CHRISTMAS -> Lines(
            "十二月二十四日平安夜，与二十五日圣诞。",
            "Christmas Eve on December 24, and Christmas Day on the 25th.",
            listOf(
                "平安夜也好，圣诞日也好，愿你被好好对待。",
                "灯可以少一盏，人要在就好。",
                "愿礼物之外，你还收到一句真心的话。",
                "冷的天里，给自己留一窗暖光。",
                "愿今夜安稳，明日也有人记得你。",
            ),
            listOf(
                "Eve or Christmas Day, may you be treated with care.",
                "One lamp fewer is fine, so long as the people are here.",
                "Besides a gift, may you receive one sentence meant truly.",
                "In the cold, leave one window of warm light for yourself.",
                "May tonight be peaceful, and tomorrow someone remember you.",
            ),
        )
        Festival.SPRING -> Lines(
            "除夕守岁，正月初一迎新，一年里最重的团圆。",
            "New Year's Eve and the first lunar morning, the year's great reunion.",
            listOf(
                "团圆若在眼前，就好好吃这一顿。",
                "回不去的人，也把祝福寄到。",
                "爆竹响过，愿家里一年都安宁。",
                "新春不问远近，只问你是否安好。",
                "愿门上的红还在，心里的热也还在。",
            ),
            listOf(
                "If the family is at the table, eat this meal well.",
                "For those who cannot come back, send the wish anyway.",
                "After the firecrackers, may the house be peaceful all year.",
                "The new spring does not ask how far. It asks if you are well.",
                "May the red on the door still be there, and the warmth too.",
            ),
        )
        Festival.LANTERN -> Lines(
            "正月十五，灯市与圆月相会的一夜。",
            "The fifteenth of the first lunar month, when lanterns meet the full moon.",
            listOf(
                "灯会散，月还在，人慢慢走回家。",
                "一碗汤圆，把这一日收圆。",
                "愿你抬头看见的灯，都为你亮着。",
                "正月将尽，好的事情才刚开始。",
                "和谁一起看灯，就把这一夜记住。",
            ),
            listOf(
                "The fair ends, the moon stays, and people walk slowly home.",
                "One bowl of rice balls rounds the day off.",
                "May every lamp you look up at be lit for you.",
                "The first month is ending. The good things are just starting.",
                "Whoever you watch the lanterns with, remember this night.",
            ),
        )
        Festival.DRAGON_BOAT -> Lines(
            "五月初五，龙舟、粽子与艾草的日子。",
            "The fifth of the fifth lunar month: boats, rice dumplings, and mugwort.",
            listOf(
                "粽叶香里，愿你吃得饱，也过得稳。",
                "龙舟竞渡，生活不必事事争先。",
                "艾草挂上门，愿这一夏少些烦。",
                "记得一餐热饭，也记得该歇的时候。",
                "愿江河安澜，你也一路顺。",
            ),
            listOf(
                "In the scent of bamboo leaves, may you eat well and live steady.",
                "The boats race. Ordinary life need not win every heat.",
                "Mugwort on the door: may this summer be a little less troubled.",
                "Remember a hot meal, and remember when to stop.",
                "May the rivers stay calm, and your own road run smooth.",
            ),
        )
        Festival.QIXI -> Lines(
            "七月初七，牛郎织女渡河相会的日子。",
            "The seventh of the seventh month, when the weaver and the cowherd meet.",
            listOf(
                "一年一会也好，平日的惦记更好。",
                "愿你把喜欢放在心上，也不亏待自己。",
                "银河很宽，一句问候可以先到。",
                "今日宜对重要的人，温柔一点。",
                "星光不用很多，有一颗就够亮。",
            ),
            listOf(
                "Once a year is something. Being thought of on ordinary days is more.",
                "Keep what you love close, and do not shortchange yourself.",
                "The river of stars is wide. A greeting can cross it first.",
                "Today, be a little gentler with the person who matters.",
                "You do not need many stars. One is bright enough.",
            ),
        )
        Festival.MID_AUTUMN -> Lines(
            "八月十五，月圆，人也盼着团圆。",
            "The fifteenth of the eighth month, a full moon and a hope of reunion.",
            listOf(
                "月圆与否，人都要吃饭，要见面。",
                "一块月饼分开，甜就在这一半里。",
                "愿远方的人今夜也抬头。",
                "窗可开着，灯可留着，等人回来。",
                "这一轮月，替你把想念照亮一点。",
            ),
            listOf(
                "Moon or no moon, people still need a meal and a meeting.",
                "Break the mooncake. The sweetness is in the half you share.",
                "May the person far away look up tonight as well.",
                "Leave the window open and the lamp on, for someone coming home.",
                "Let this moon light the missing, just a little.",
            ),
        )
        Festival.DOUBLE_NINTH -> Lines(
            "九月初九，登高、赏菊、思念长辈。",
            "The ninth of the ninth month, for climbing, chrysanthemums, and elders.",
            listOf(
                "登一步高，把远处的人想想。",
                "愿长辈安康，也愿你被后辈惦记。",
                "菊花开了，秋天就没有那么凉。",
                "今日宜慢走，宜给家里打一个电话。",
                "岁岁重阳，愿你步子仍稳。",
            ),
            listOf(
                "Climb one step higher, and think of the people far off.",
                "May the elders be well, and may the young remember you.",
                "Once the chrysanthemums open, autumn is not so cold.",
                "Walk slowly today, and call home.",
                "Double Ninth after Double Ninth, may your step stay sure.",
            ),
        )
        Festival.MAKHA -> Lines(
            "泰历三月十五，僧众诵经、信众持戒的日子。",
            "The full moon of the third Thai month, a day of chanting and quiet vows.",
            listOf(
                "静下一刻，比赶路更要紧。",
                "愿你心定，也愿周围的人平安。",
                "一朵莲就够，不必声张。",
                "把今日过得干净，明日会轻一点。",
                "善不必很大，持续就好。",
            ),
            listOf(
                "One quiet moment matters more than hurrying on.",
                "May your mind settle, and the people around you be safe.",
                "One lotus is enough. There is no need to announce it.",
                "Keep today clean, and tomorrow will weigh a little less.",
                "Goodness need not be large. It needs to continue.",
            ),
        )
        Festival.SONGKRAN -> Lines(
            "四月十三至十五，泰国以清水互祝的新年。",
            "April 13 to 15, the Thai new year, when people bless each other with water.",
            listOf(
                "清水泼过来，是祝福，就笑着接住。",
                "愿这一年像被洗净一样，重新开始。",
                "玩得尽兴，也记得把长辈的手扶好。",
                "水声热闹，回家的路要平稳。",
                "愿新的一年，热天里也有清凉。",
            ),
            listOf(
                "When the clear water comes, it is a blessing. Catch it smiling.",
                "May the year start again, as if it had been washed clean.",
                "Play fully, and still take an elder's hand with care.",
                "The water is loud. The road home should be steady.",
                "May the new year hold some cool, even in the heat.",
            ),
        )
        Festival.VISAKHA -> Lines(
            "泰历六月十五，纪念佛陀诞生、悟道与涅槃。",
            "The full moon of the sixth Thai month, remembering the Buddha's life.",
            listOf(
                "生、悟与寂，都在这一日被记住。",
                "愿你遇事能静，对人也软。",
                "灯火不多，心亮着就好。",
                "今日宜少言，宜把一件好事做完。",
                "愿你脚下有路，心里有灯。",
            ),
            listOf(
                "Birth, awakening, and peace are remembered on this one day.",
                "May you be quiet with events, and gentle with people.",
                "Few lamps are needed, if the mind stays lit.",
                "Speak a little less today, and finish one good thing.",
                "May there be a road under your feet, and a lamp in your heart.",
            ),
        )
        Festival.LOY_KRATHONG -> Lines(
            "泰历十二月十五，放一盏灯，把心愿托给河水。",
            "The full moon of the twelfth Thai month, a lantern set upon the water.",
            listOf(
                "把灯放下，也把放不下的放下一点。",
                "愿河水带走烦恼，留下你想留的。",
                "一盏灯很小，心愿可以很大。",
                "看着它走远，就允许这一年过去。",
                "愿你放出去的，都有回声。",
            ),
            listOf(
                "Set the lantern down, and set a little of the rest down too.",
                "May the river take the trouble, and leave what you hope to keep.",
                "A lantern is small. A wish may be large.",
                "Watch it go, and let this year go with it.",
                "May what you send out find an echo.",
            ),
        )
    }

    private class Lines(
        val originZh: String,
        val originEn: String,
        val zh: List<String>,
        val en: List<String>,
    )
}
