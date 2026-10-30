package com.notee.pinyin.input

/**
 * Country names and one-word place names (docs/01-0 英文).
 *
 * There is no separate gazetteer file. The English table stores whatever spelling it has;
 * this list is what may become title case when the user finishes the word in lowercase.
 * Ordinary words that are not places (`march`, `may`) are absent. So are place names that
 * are also everyday words (`mobile`, `nice`, `reading`, `split`, `bath`).
 */
object EnglishPlaces {
    private val NAME = Regex("[A-Z][a-z]+(?:'[a-z]+)?")

    private val keys: Array<String>
    private val spellings: Array<String>

    init {
        val pairs = NAMES.split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .map { name ->
                require(name.length >= 4 && NAME.matches(name)) { name }
                name.lowercase() to name
            }
            .sortedBy { it.first }
        keys = Array(pairs.size) { pairs[it].first }
        spellings = Array(pairs.size) { pairs[it].second }
    }

    /**
     * Title case for [typed] when it is a place written with no capitals.
     * Null keeps the letters the user already typed.
     */
    fun rewrite(typed: String): String? {
        if (typed.length < 4 || typed.any { it.isUpperCase() }) return null
        val index = exact(typed.lowercase())
        if (index < 0) return null
        val spelled = spellings[index]
        return spelled.takeIf { it != typed }
    }

    /** Place names strictly longer than [typed] that start with it, in alphabetical order. */
    fun longerThan(typed: String, limit: Int): List<String> {
        if (typed.length < 2 || limit <= 0) return emptyList()
        val prefix = typed.lowercase()
        var index = lowerBound(prefix)
        val out = ArrayList<String>(minOf(limit, 8))
        while (index < keys.size && keys[index].startsWith(prefix) && out.size < limit) {
            val spelled = spellings[index]
            if (spelled.length > typed.length) out += spelled
            index++
        }
        return out
    }

    private fun exact(key: String): Int {
        val index = lowerBound(key)
        return if (index < keys.size && keys[index] == key) index else -1
    }

    private fun lowerBound(key: String): Int {
        var low = 0
        var high = keys.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (keys[mid] < key) low = mid + 1 else high = mid
        }
        return low
    }

    private const val NAMES = """
        Abudhabi Accra Addisababa Adelaide Afghanistan Agra Alaska Albania Algeria Amman Amsterdam Andorra Angola Ankara
        Antwerp Argentina Armenia Athens Atlanta Auckland Austin Australia Austria Ayutthaya Azerbaijan
        Baghdad Bahamas Bahrain Baku Bali Bangkok Bangladesh Bangalore Barbados Barcelona Beijing Beirut Belarus Belgium
        Belgrade Belize Bengaluru Benin Berlin Bermuda Bern Bhutan Birmingham Bogota Bolivia Bombay Bordeaux Bosnia
        Boston Botswana Brasilia Bratislava Brazil Brisbane Britain Brunei Brussels Bucharest Budapest Buenosaires
        Bulgaria Burkina Burundi Busan
        Cairo Calcutta Calgary California Cambodia Cameroon Canada Cancun Capetown Caracas Casablanca Cebu Chad
        Changchun Changsha Chennai Chengdu Chiangmai Chiangrai Chicago Chile China Chittagong Chonburi Chongqing Cologne
        Colombia Colombo Colorado Comoros Congo Copenhagen Corsica Costarica Croatia Cuba Cyprus Czechia
        Daegu Daejeon Dakar Dalian Dallas Damascus Danang Davao Delhi Denmark Denver Detroit Dhaka Djibouti Doha
        Dominica Dongguan Dubai Dublin Durban Dusseldorf
        Ecuador Edinburgh Egypt Elsalvador England Eritrea Estonia Eswatini Ethiopia
        Fiji Finland Florence Florida Foshan France Frankfurt Fukuoka Fuzhou
        Gabon Gambia Geneva Georgia Germany Ghana Glasgow Greece Grenada Guangzhou Guatemala Guilin Guinea Guiyang Guyana
        Gwangju
        Haikou Haiti Hamburg Hangzhou Hanoi Harbin Hatyai Hawaii Hefei Helsinki Hiroshima Hochiminh Hohhot Holland
        Honduras Hongkong Honolulu Houston Huahin Hungary Hyderabad
        Iceland Incheon India Indonesia Innsbruck Iran Iraq Ireland Islamabad Israel Istanbul Italy
        Jaipur Jakarta Jamaica Japan Jeddah Jerusalem Jinan Johannesburg Johor Jordan
        Kabul Kanchanaburi Kandy Kaohsiung Karachi Kathmandu Kazakhstan Kenya Khartoum Khonkaen Kiev Kiribati Kobe
        Kolkata Korat Kosovo Krabi Krakow Kualalumpur Kuching Kunming Kuwait Kyiv Kyoto Kyrgyzstan
        Lagos Lahore Lanzhou Laos Lasvegas Latvia Lebanon Leeds Leipzig Lesotho Lhasa Liberia Libya Liechtenstein
        Lijiang Lima Lisbon Lithuania Liverpool Ljubljana London Losangeles Luangprabang Luxembourg Lyon
        Macao Macau Madagascar Madrid Malacca Malawi Malaysia Maldives Mali Malaga Malta Managua Manchester Mandalay
        Manila Marrakech Marseille Mauritania Mauritius Mecca Medina Melbourne Mexico Miami Milan Minneapolis Minsk
        Mogadishu Moldova Monaco Mongolia Montenegro Montreal Morocco Moscow Mumbai Munich Muscat Myanmar
        Nagoya Nairobi Nanjing Nanchang Nanning Naples Nara Nauru Naypyidaw Nepal Netherlands Neworleans Newyork
        Newzealand Nicaragua Niger Nigeria Ningbo Nonthaburi Northkorea Norway
        Odessa Oman Ontario Osaka Oslo Ottawa
        Pakistan Palau Palestine Palma Panama Papua Paraguay Paris Pattaya Penang Perth Peru Phangan Philadelphia
        Philippines Phitsanulok Phnompenh Phoenix Phuket Pokhara Poland Portland Porto Portugal Prague Pretoria Pune
        Puertorico Pyongyang
        Qatar Qingdao Quebec Quito
        Rabat Rayong Reykjavik Riga Riodejaneiro Riyadh Romania Rome Rotterdam Russia Rwanda
        Saigon Salzburg Samui Sandiego Sanfrancisco Santiago Saopaulo Sapporo Sarajevo Saudiarabia Scotland Seattle
        Sendai Senegal Seoul Serbia Seville Seychelles Shanghai Shenyang Shenzhen Shijiazhuang Siberia Sicily Siemreap
        Singapore Slovakia Slovenia Sofia Somalia Southafrica Southkorea Spain Srilanka Stockholm Stuttgart Sudan
        Surabaya Suratthani Suriname Suzhou Sweden Switzerland Sydney Syria
        Taichung Tainan Taipei Taiwan Taiyuan Tajikistan Tallinn Tanzania Tashkent Tbilisi Tehran Telaviv Texas
        Thailand Tianjin Togo Tokyo Tonga Toronto Toulouse Trang Tunis Tunisia Turkey Turkmenistan Tuvalu
        Udonthani Uganda Ukraine Ulaanbaatar Uruguay Urumqi Uzbekistan
        Valencia Vancouver Vanuatu Vatican Vegas Venezuela Venice Vienna Vientiane Vietnam Vilnius
        Wales Warsaw Washington Wellington Wuhan Wuxi
        Xiamen Xian Xi'an Xining
        Yangon Yemen Yerevan Yinchuan Yokohama
        Zagreb Zambia Zhengzhou Zhuhai Zimbabwe Zurich
        """
}
