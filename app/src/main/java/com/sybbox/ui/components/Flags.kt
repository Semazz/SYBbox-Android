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
    "стокгольм" to "se", "sweden" to "se", "швеция" to "se", "swedish" to "se",
    "париж" to "fr", "france" to "fr", "франция" to "fr", "paris" to "fr",
    "прага" to "cz", "prague" to "cz", "чехия" to "cz", "czech" to "cz",
    "амстердам" to "nl", "amsterdam" to "nl", "нидерланды" to "nl", "netherlands" to "nl", "голландия" to "nl",
    "франкфурт" to "de", "frankfurt" to "de", "германия" to "de", "germany" to "de", "берлин" to "de",
    "лондон" to "gb", "london" to "gb", "британия" to "gb", "britain" to "gb", "england" to "gb", "uk" to "gb",
    "нью-йорк" to "us", "нью йорк" to "us", "usa" to "us", "сша" to "us", "даллас" to "us", "майами" to "us", "лос анджелес" to "us", "лос-анджелес" to "us", "los angeles" to "us",
    "хельсинки" to "fi", "финляндия" to "fi", "finland" to "fi", "helsinki" to "fi",
    "варшава" to "pl", "польша" to "pl", "warsaw" to "pl", "poland" to "pl",
    "вена" to "at", "австрия" to "at", "vienna" to "at", "austria" to "at",
    "мадрид" to "es", "испания" to "es", "spain" to "es", "барселона" to "es", "barcelona" to "es",
    "рига" to "lv", "латвия" to "lv", "riga" to "lv", "latvia" to "lv",
    "вильнюс" to "lt", "литва" to "lt", "vilnius" to "lt", "lithuania" to "lt",
    "таллин" to "ee", "эстония" to "ee", "tallinn" to "ee", "estonia" to "ee",
    "киев" to "ua", "украина" to "ua", "kyiv" to "ua", "ukraine" to "ua",
    "стамбул" to "tr", "турция" to "tr", "istanbul" to "tr", "turkey" to "tr",
    "дубай" to "ae", "оаэ" to "ae", "dubai" to "ae", "эмираты" to "ae",
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
    "болгария" to "bg", "bulgaria" to "bg", "софия" to "bg",
    "румыния" to "ro", "бухарест" to "ro", "romania" to "ro", "bucharest" to "ro",
    "венгрия" to "hu", "будапешт" to "hu", "hungary" to "hu", "budapest" to "hu",
    "словакия" to "sk", "словаки" to "sk", "slovakia" to "sk", "братислава" to "sk", "bratislava" to "sk",
    "хорватия" to "hr", "croatia" to "hr", "загреб" to "hr",
    "швейцария" to "ch", "цюрих" to "ch", "switzerland" to "ch", "женева" to "ch", "geneva" to "ch",
    "италия" to "it", "милан" to "it", "italy" to "it", "milan" to "it", "рим" to "it",
    "норвегия" to "no", "осло" to "no", "norway" to "no", "oslo" to "no",
    "дания" to "dk", "копенгаген" to "dk", "denmark" to "dk", "copenhagen" to "dk",
    "израиль" to "il", "israel" to "il", "тель-авив" to "il", "тель авив" to "il", "tel aviv" to "il",
    "австралия" to "au", "australia" to "au", "сидней" to "au",
    "аргентина" to "ar", "argentina" to "ar",
    "мексика" to "mx", "mexico" to "mx",
    "казахстан" to "kz", "алматы" to "kz", "kazakhstan" to "kz", "астана" to "kz",
    "азербайджан" to "az", "баку" to "az",
    "албания" to "al", "albania" to "al",
    "армения" to "am", "ереван" to "am", "armenia" to "am",
    "бельгия" to "be", "brussels" to "be", "брюссель" to "be",
    "ирландия" to "ie", "ирландия" to "ie", "дублин" to "ie",
    "португалия" to "pt", "лиссабон" to "pt", "portugal" to "pt",
    "люксембург" to "lu", "luxembourg" to "lu",
    "черногория" to "me", "montenegro" to "me",
    "словения" to "si", "slovenia" to "si",
    "босния" to "ba", "bosnia" to "ba",
    "москва" to "ru", "россия" to "ru", "russia" to "ru", "moscow" to "ru", "московский" to "ru",
    "торрент" to "ru", "torrent" to "ru",
    "мобильный" to "ru", "mobile" to "ru",
    "алматы" to "kz",
)

fun stripFlagEmoji(name: String): String {
    // Remove leading flag emojis, pirate/white flags, recycle etc., preserving the rest.
    // First, strip classic regional indicator flags
    val sb = StringBuilder(name.length)
    var i = 0
    while (i < name.length) {
        val cp = name.codePointAt(i)
        if (cp in 0x1F1E6..0x1F1FF) {
            val nextIndex = i + Character.charCount(cp)
            if (nextIndex < name.length && name.codePointAt(nextIndex) in 0x1F1E6..0x1F1FF) {
                i = nextIndex + Character.charCount(name.codePointAt(nextIndex))
                // skip ZWJ/variation/fe0f/spaces after flag
                while (i < name.length && (name.codePointAt(i) == 0x200D || name.codePointAt(i) == 0xFE0F || name[i] == ' ' || name[i] == '\uFE0F')) {
                    i += Character.charCount(name.codePointAt(i))
                }
                continue
            }
        }
        break
    }
    // Now strip any other leading emoji/non-letter sequence (🏴‍☠️, 🏳️, 🔁, etc.) up to first letter
    var start = i
    while (start < name.length) {
        val cp = name.codePointAt(start)
        // If it's a letter or digit, stop
        if (Character.isLetterOrDigit(cp)) break
        // If it's variation selector, ZWJ, emoji modifiers, skip
        if (cp == 0xFE0F || cp == 0x200D || cp == 0xFE0E) {
            start += Character.charCount(cp)
            continue
        }
        // For any non-letter including emoji, skip one codepoint
        // But also skip following spaces
        if (!Character.isLetterOrDigit(cp) && cp != ' '.code) {
            // emoji range check: skip
            start += Character.charCount(cp)
            // skip following variation/ZWJ chain
            while (start < name.length) {
                val ncp = name.codePointAt(start)
                if (ncp == 0x200D || ncp == 0xFE0F || ncp == 0xFE0E || (ncp in 0x1F3FB..0x1F3FF)) {
                    start += Character.charCount(ncp)
                } else break
            }
            // skip space after emoji
            if (start < name.length && name[start] == ' ') start++
            continue
        }
        if (cp == ' '.code) {
            start += Character.charCount(cp)
            continue
        }
        break
    }
    // If we stripped leading emojis, return remainder; otherwise process full name with classic loop for embedded flags
    if (start > 0) {
        // For remaining string, still strip any embedded country flags (should not happen but safe)
        var j = start
        while (j < name.length) {
            val cp = name.codePointAt(j)
            if (cp in 0x1F1E6..0x1F1FF) {
                val nextIndex = j + Character.charCount(cp)
                if (nextIndex < name.length && name.codePointAt(nextIndex) in 0x1F1E6..0x1F1FF) {
                    j = nextIndex + Character.charCount(name.codePointAt(nextIndex))
                    if (j < name.length && name[j] == ' ') j++
                    continue
                }
            }
            sb.appendCodePoint(cp)
            j += Character.charCount(cp)
        }
        return sb.toString().trim()
    }
    // No leading emoji stripped, use original logic for whole string
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

    // Fallback: check address/host for country code like se.example.com
    val addressLower = name.lowercase()
    for (code in ISO_CODES) {
        if (addressLower.contains(".$code.") || addressLower.startsWith("$code.") || addressLower.contains("-$code-") || addressLower.contains("_$code")) {
            return code
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

fun countryCodeForProfile(name: String, address: String): String? {
    return countryCodeFromName(name) ?: countryCodeFromName(address)
}
