package com.sybbox.ui.components

val ISO_CODES: Set<String> = setOf(
    "ad","ae","af","ag","ai","al","am","ao","ar","as","at","au","aw","ax","az",
    "ba","bb","bd","be","bf","bg","bh","bi","bj","bl","bm","bn","bo","bq","br",
    "bs","bt","bv","bw","by","bz","ca","cc","cd","cf","cg","ch","ci","ck","cl",
    "cm","cn","co","cr","cu","cv","cw","cx","cy","cz","de","dj","dk","dm","do",
    "dz","ec","ee","eg","eh","er","es","et","fi","fj","fk","fm","fo","fr","ga",
    "gb","gd","ge","gf","gg","gh","gi","gl","gm","gn","gp","gq","gr","gs","gt",
    "gu","gw","gy","hk","hm","hn","hr","ht","hu","id","ie","il","im","in","io",
    "iq","ir","is","it","je","jm","jo","jp","ke","kg","kh","ki","km","kn","kp",
    "kr","kw","ky","kz","la","lb","lc","li","lk","lr","ls","lt","lu","lv","ly",
    "ma","mc","md","me","mf","mg","mh","mk","ml","mm","mn","mo","mp","mq","mr",
    "ms","mt","mu","mv","mw","mx","my","mz","na","nc","ne","nf","ng","ni","nl",
    "no","np","nr","nu","nz","om","pa","pe","pf","pg","ph","pk","pl","pm","pn",
    "pr","ps","pt","pw","py","qa","re","ro","rs","ru","rw","sa","sb","sc","sd",
    "se","sg","sh","si","sj","sk","sl","sm","sn","so","sr","ss","st","sv","sx",
    "sy","sz","tc","td","tf","tg","th","tj","tk","tl","tm","tn","to","tr","tt",
    "tv","tw","tz","ua","ug","um","us","uy","uz","va","vc","ve","vg","vi","vn",
    "vu","wf","ws","ye","yt","za","zm","zw",
)

val COUNTRY_KEYWORDS: Map<String, String> = mapOf(
    "стокгольм" to "se", "sweden" to "se", "швеция" to "se",
    "париж" to "fr", "france" to "fr", "франция" to "fr",
    "прага" to "cz", "prague" to "cz", "чехия" to "cz", "czech" to "cz",
    "амстердам" to "nl", "amsterdam" to "nl", "нидерланды" to "nl", "netherlands" to "nl",
    "франкфурт" to "de", "frankfurt" to "de", "германия" to "de", "germany" to "de",
    "лондон" to "gb", "london" to "gb", "британия" to "gb", "britain" to "gb", "england" to "gb",
    "нью-йорк" to "us", "нью йорк" to "us", "usa" to "us", "сша" to "us", "даллас" to "us", "майами" to "us",
    "хельсинки" to "fi", "финляндия" to "fi", "finland" to "fi",
    "варшава" to "pl", "польша" to "pl", "warsaw" to "pl", "poland" to "pl",
    "вена" to "at", "австрия" to "at", "vienna" to "at", "austria" to "at",
    "мадрид" to "es", "испания" to "es", "spain" to "es",
    "рига" to "lv", "латвия" to "lv", "riga" to "lv", "latvia" to "lv",
    "вильнюс" to "lt", "литва" to "lt", "vilnius" to "lt", "lithuania" to "lt",
    "таллин" to "ee", "эстония" to "ee", "tallinn" to "ee", "estonia" to "ee",
    "киев" to "ua", "украина" to "ua", "kyiv" to "ua", "ukraine" to "ua",
    "стамбул" to "tr", "турция" to "tr", "istanbul" to "tr", "turkey" to "tr",
    "дубай" to "ae", "оаэ" to "ae", "dubai" to "ae",
    "токио" to "jp", "япония" to "jp", "tokyo" to "jp", "japan" to "jp",
    "сингапур" to "sg", "singapore" to "sg",
    "гонконг" to "hk", "hongkong" to "hk", "hong kong" to "hk",
    "сеул" to "kr", "корея" to "kr", "seoul" to "kr", "korea" to "kr",
    "торонто" to "ca", "канада" to "ca", "toronto" to "ca", "canada" to "ca",
    "бразилия" to "br", "brazil" to "br",
    "индия" to "in", "india" to "in", "мумбаи" to "in",
    "молдова" to "md", "кишинёв" to "md", "кишинев" to "md", "moldova" to "md",
    "грузия" to "ge", "тбилиси" to "ge", "georgia" to "ge",
    "сербия" to "rs", "белград" to "rs", "serbia" to "rs",
    "болгария" to "bg", "bulgaria" to "bg",
    "румыния" to "ro", "бухарест" to "ro", "romania" to "ro",
    "венгрия" to "hu", "будапешт" to "hu", "hungary" to "hu", "budapest" to "hu",
    "словакия" to "sk", "slovakia" to "sk",
    "хорватия" to "hr", "croatia" to "hr",
    "швейцария" to "ch", "цюрих" to "ch", "switzerland" to "ch",
    "италия" to "it", "милан" to "it", "italy" to "it", "milan" to "it",
    "норвегия" to "no", "осло" to "no", "norway" to "no", "oslo" to "no",
    "дания" to "dk", "копенгаген" to "dk", "denmark" to "dk",
    "израиль" to "il", "israel" to "il",
    "австралия" to "au", "australia" to "au",
    "аргентина" to "ar", "argentina" to "ar",
    "мексика" to "mx", "mexico" to "mx",
    "казахстан" to "kz", "алматы" to "kz", "kazakhstan" to "kz",
    "азербайджан" to "az", "баку" to "az",
    "албания" to "al", "albania" to "al",
    "армения" to "am", "ереван" to "am", "armenia" to "am",
    "бельгия" to "be", "brussels" to "be",
    "ирландия" to "ie", "ирландия" to "ie",
    "португалия" to "pt", "лиссабон" to "pt", "portugal" to "pt",
    "люксембург" to "lu", "luxembourg" to "lu",
    "черногория" to "me", "montenegro" to "me",
    "словения" to "si", "slovenia" to "si",
    "босния" to "ba", "bosnia" to "ba",
)

fun stripFlagEmoji(name: String): String {
    val sb = StringBuilder(name.length)
    var i = 0
    while (i < name.length) {
        val cp = name.codePointAt(i)
        if (cp in 0x1F1E6..0x1F1FF) {
            val nextIndex = i + Character.charCount(cp)
            if (nextIndex < name.length && name.codePointAt(nextIndex) in 0x1F1E6..0x1F1FF) {
                i = nextIndex + Character.charCount(name.codePointAt(nextIndex))
                if (i < name.length && name[i] == ' ') i++
                continue
            }
        }
        sb.appendCodePoint(cp)
        i += Character.charCount(cp)
    }
    return sb.toString().trim()
}

fun flagEmojiIn(name: String): String? = countryCodeFromName(name)?.let { code ->
    val first = 0x1F1E6 + (code[0] - 'a')
    val second = 0x1F1E6 + (code[1] - 'a')
    String(Character.toChars(first)) + String(Character.toChars(second))
}

fun countryCodeFromName(name: String): String? {
    if (name.isEmpty()) return null

    var i = 0
    while (i < name.length) {
        val cp = name.codePointAt(i)
        if (cp in 0x1F1E6..0x1F1FF) {
            val nextIndex = i + Character.charCount(cp)
            if (nextIndex < name.length) {
                val next = name.codePointAt(nextIndex)
                if (next in 0x1F1E6..0x1F1FF) {
                    val code = buildString {
                        append(('a' + (cp - 0x1F1E6)))
                        append(('a' + (next - 0x1F1E6)))
                    }
                    if (code in ISO_CODES) return code
                }
            }
        }
        i += Character.charCount(cp)
    }

    val lower = name.lowercase()
    for ((keyword, code) in COUNTRY_KEYWORDS) {
        var from = 0
        while (true) {
            val idx = lower.indexOf(keyword, from)
            if (idx < 0) break
            val beforeOk = idx == 0 || !lower[idx - 1].isLetter()
            val afterIdx = idx + keyword.length
            val afterOk = afterIdx >= lower.length || !lower[afterIdx].isLetter()
            if (beforeOk && afterOk) return code
            from = idx + 1
        }
    }

    val sb = StringBuilder()
    for ((index, ch) in name.withIndex()) {
        if (ch.isUpperCase() && ch.code < 128) {
            sb.clear()
            sb.append(ch)
            var j = index + 1
            while (j < name.length && sb.length < 2) {
                val c = name[j]
                if (c.isUpperCase() && c.code < 128) {
                    sb.append(c)
                    j++
                } else break
            }
            if (sb.length == 2) {
                val beforeOk = index == 0 || !name[index - 1].isLetter()
                val afterOk = j >= name.length || !name[j].isLetter()
                if (beforeOk && afterOk && sb.toString().lowercase() in ISO_CODES) {
                    return sb.toString().lowercase()
                }
            }
        }
    }
    return null
}
