package org.mz.mzdkplayer.tool


internal object LanguageTools {
    /**
     * 将语言代码转换为中文语言名称（特别细化中文区分）
     */
    /**
     * 将语言代码转换为中文语言名称（特别细化中文区分，包括字幕语言）
     */
    fun getFullLanguageName(languageCode: String?): String {
        if (languageCode.isNullOrEmpty() || languageCode == "und") {
            return "未知语言"
        }

        // 特别处理中文的细分（包括字幕语言代码）
        return when (// 简体中文（视频轨道和字幕轨道）
            val lowerCode = languageCode.lowercase()) {
            "zh-hans", "zh-cn", "zh-sg", "chs", "sc", "chi_sim" -> "简体中文"

            // 繁体中文（视频轨道和字幕轨道）
            "zh-hant", "zh-tw", "zh-hk", "zh-mo", "cht", "tc", "chi_tra" -> "繁体中文"

            // 一般中文代码（无法区分简繁体时）
            "zh", "zho", "chi" -> "中文"

            // 其他语言保持不变
            else -> getOtherLanguageName(lowerCode)
        }
    }

    /**
     * 处理其他语言的名称转换
     */
    private fun getOtherLanguageName(languageCode: String): String {
        return when (languageCode) {
            "en", "eng" -> "英语"
            "jp", "jpn", "ja" -> "日语"
            "ko", "kor" -> "韩语"
            "fr", "fra", "fre" -> "法语"
            "de", "deu", "ger" -> "德语"
            "es", "spa" -> "西班牙语"
            "it", "ita" -> "意大利语"
            "ru", "rus" -> "俄语"
            "pt", "por" -> "葡萄牙语"
            "ar", "ara" -> "阿拉伯语"
            "hi", "hin" -> "印地语"
            "tr", "tur" -> "土耳其语"
            "nl", "nld", "dut" -> "荷兰语"
            "sv", "swe" -> "瑞典语"
            "pl", "pol" -> "波兰语"
            "th", "tha" -> "泰语"
            "vi", "vie" -> "越南语"
            "id", "ind" -> "印尼语"
            "ms", "msa", "may" -> "马来语"
            "fa", "fas", "per" -> "波斯语"
            "he", "heb" -> "希伯来语"
            "el", "ell", "gre" -> "希腊语"
            "da", "dan" -> "丹麦语"
            "fi", "fin" -> "芬兰语"
            "no", "nor" -> "挪威语"
            "cs", "ces", "cze" -> "捷克语"
            "hu", "hun" -> "匈牙利语"
            "ro", "ron", "rum" -> "罗马尼亚语"
            "sk", "slk", "slo" -> "斯洛伐克语"
            "bg", "bul" -> "保加利亚语"
            "uk", "ukr" -> "乌克兰语"
            "ca", "cat" -> "加泰罗尼亚语"
            "hr", "hrv" -> "克罗地亚语"
            "sr", "srp" -> "塞尔维亚语"
            "sl", "slv" -> "斯洛文尼亚语"
            "lt", "lit" -> "立陶宛语"
            "lv", "lav" -> "拉脱维亚语"
            "et", "est" -> "爱沙尼亚语"
            "is", "isl", "ice" -> "冰岛语"
            "mt", "mlt" -> "马耳他语"
            "ga", "gle" -> "爱尔兰语"
            "gd", "gla" -> "苏格兰盖尔语"
            "cy", "cym", "wel" -> "威尔士语"
            "eu", "eus", "baq" -> "巴斯克语"
            "gl", "glg" -> "加利西亚语"
            "af", "afr" -> "南非荷兰语"
            "sw", "swa" -> "斯瓦希里语"
            "zu", "zul" -> "祖鲁语"
            "xh", "xho" -> "科萨语"
            "st", "sot" -> "南索托语"
            "tn", "tsn" -> "茨瓦纳语"
            "ss", "ssw" -> "斯威士语"
            "ve", "ven" -> "文达语"
            "ts", "tso" -> "聪加语"
            "ne", "nep" -> "尼泊尔语"
            "si", "sin" -> "僧伽罗语"
            "my", "mya", "bur" -> "缅甸语"
            "km", "khm" -> "高棉语"
            "lo", "lao" -> "老挝语"
            "mn", "mon" -> "蒙古语"
            "bo", "bod", "tib" -> "藏语"
            "ug", "uig" -> "维吾尔语"
            "sd", "snd" -> "信德语"
            "ps", "pus" -> "普什图语"
            "ku", "kur" -> "库尔德语"
            "tk", "tuk" -> "土库曼语"
            "uz", "uzb" -> "乌兹别克语"
            "kk", "kaz" -> "哈萨克语"
            "ky", "kir" -> "吉尔吉斯语"
            "tg", "tgk" -> "塔吉克语"
            "hy", "hye", "arm" -> "亚美尼亚语"
            "ka", "kat", "geo" -> "格鲁吉亚语"
            "am", "amh" -> "阿姆哈拉语"
            "ti", "tir" -> "提格里尼亚语"
            "om", "orm" -> "奥罗莫语"
            "so", "som" -> "索马里语"
            "mg", "mlg" -> "马拉加斯语"
            "yo", "yor" -> "约鲁巴语"
            "ig", "ibo" -> "伊博语"
            "ha", "hau" -> "豪萨语"
            "ff", "ful" -> "富拉语"
            "wo", "wol" -> "沃洛夫语"
            "sn", "sna" -> "绍纳语"
            "rw", "kin" -> "卢旺达语"
            "ny", "nya" -> "齐切瓦语"
            "ak", "aka" -> "阿坎语"
            "lg", "lug" -> "卢干达语"
            "mh", "mah" -> "马绍尔语"
            "sm", "smo" -> "萨摩亚语"
            "to", "ton" -> "汤加语"
            "mi", "mri", "mao" -> "毛利语"
            "fj", "fij" -> "斐济语"
            "haw" -> "夏威夷语"
            // 其他语言代码...
            else -> languageCode.uppercase()
        }
    }

    fun getCountryName(countryCode: String): String {
        return when (countryCode.uppercase()) {
            "US" -> "美国"
            "CN" -> "中国"
            "JP" -> "日本"
            "KR" -> "韩国"
            "GB" -> "英国"
            "FR" -> "法国"
            "DE" -> "德国"
            "IT" -> "意大利"
            "ES" -> "西班牙"
            "RU" -> "俄罗斯"
            "CA" -> "加拿大"
            "AU" -> "澳大利亚"
            "BR" -> "巴西"
            "IN" -> "印度"
            "MX" -> "墨西哥"
            "NL" -> "荷兰"
            "SE" -> "瑞典"
            "CH" -> "瑞士"
            "BE" -> "比利时"
            "AT" -> "奥地利"
            "PL" -> "波兰"
            "TR" -> "土耳其"
            "ZA" -> "南非"
            "SG" -> "新加坡"
            "NZ" -> "新西兰"
            "TH" -> "泰国"
            "MY" -> "马来西亚"
            "PH" -> "菲律宾"
            "ID" -> "印度尼西亚"
            "VN" -> "越南"
            "HK" -> "中国香港"
            "TW" -> "中国台湾"
            "MO" -> "中国澳门"
            else -> countryCode // 或者返回 "未知"、"" 等，根据需求
        }
    }
}
