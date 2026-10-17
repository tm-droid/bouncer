package com.tanush.bouncer.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Hand-picked kit colours, primary to secondary, keyed by [TeamColorRegistry]'s normalized name.
 *
 * These are what a team plays in, not what its flag is: Australia is gold, India is blue, New Zealand
 * is black. They win over the colours extracted from crests (`assets/TeamColors.json`), which are
 * right for most associate and domestic sides and wrong for exactly these.
 */
internal val CURATED_TEAM_COLORS: Map<String, Pair<Color, Color>> = mapOf(
    // Full members
    "india" to (Color(0xFF0057B8) to Color(0xFFFF9933)),
    "australia" to (Color(0xFFFFC72C) to Color(0xFF006F3D)),
    "england" to (Color(0xFF00247D) to Color(0xFFCF142B)),
    "pakistan" to (Color(0xFF01411C) to Color(0xFFFFFFFF)),
    "south africa" to (Color(0xFF007A4D) to Color(0xFFFFB81C)),
    "new zealand" to (Color(0xFF111111) to Color(0xFFC8102E)),
    "west indies" to (Color(0xFF7B0031) to Color(0xFFFFC72C)),
    "sri lanka" to (Color(0xFF1F3A93) to Color(0xFFFFBE00)),
    "bangladesh" to (Color(0xFF006A4E) to Color(0xFFF42A41)),
    "afghanistan" to (Color(0xFF0057B8) to Color(0xFFD32011)),
    "ireland" to (Color(0xFF169B62) to Color(0xFF0B2545)),
    "zimbabwe" to (Color(0xFFD40000) to Color(0xFFFFD200)),
    // Associates
    "netherlands" to (Color(0xFFFF6F00) to Color(0xFF21468B)),
    "scotland" to (Color(0xFF6A1B9A) to Color(0xFF005EB8)),
    "nepal" to (Color(0xFF003893) to Color(0xFFDC143C)),
    "united arab emirates" to (Color(0xFF00732F) to Color(0xFFE4002B)),
    "united states of america" to (Color(0xFF0A3161) to Color(0xFFB31942)),
    "namibia" to (Color(0xFF003580) to Color(0xFFD21034)),
    "oman" to (Color(0xFFDB161B) to Color(0xFF008000)),
    "canada" to (Color(0xFFD80621) to Color(0xFF1A1A1A)),
    "hong kong" to (Color(0xFFDE2910) to Color(0xFF1A1A1A)),
    "papua new guinea" to (Color(0xFFCE1126) to Color(0xFFFCD116)),
    // IPL
    "mumbai indians" to (Color(0xFF004BA0) to Color(0xFFD1A23C)),
    "chennai super kings" to (Color(0xFFFDB913) to Color(0xFF0078BC)),
    "royal challengers bengaluru" to (Color(0xFFD71920) to Color(0xFF0B0B0B)),
    "kolkata knight riders" to (Color(0xFF3A225D) to Color(0xFFB4975A)),
    "rajasthan royals" to (Color(0xFFE91E63) to Color(0xFF003594)),
    "punjab kings" to (Color(0xFFED1B24) to Color(0xFFFFFFFF)),
    "sunrisers hyderabad" to (Color(0xFFFF822A) to Color(0xFF1A1A1A)),
    "delhi capitals" to (Color(0xFF0078BC) to Color(0xFFEF1C25)),
    "gujarat titans" to (Color(0xFF1C4E9D) to Color(0xFF47C1BF)),
    "lucknow super giants" to (Color(0xFF004C93) to Color(0xFFA5D9F7)),
    // BBL
    "adelaide strikers" to (Color(0xFF0084D6) to Color(0xFFFFD700)),
    "brisbane heat" to (Color(0xFF00A6CE) to Color(0xFFE4002B)),
    "hobart hurricanes" to (Color(0xFF6A2C91) to Color(0xFFF5821F)),
    "melbourne renegades" to (Color(0xFFE4002B) to Color(0xFF1A1A1A)),
    "melbourne stars" to (Color(0xFF00A651) to Color(0xFF1A1A1A)),
    "perth scorchers" to (Color(0xFFF26522) to Color(0xFF1A1A1A)),
    "sydney sixers" to (Color(0xFFE6007E) to Color(0xFF1A1A1A)),
    "sydney thunder" to (Color(0xFF8CC63F) to Color(0xFF1A1A1A)),
    // PSL
    "karachi kings" to (Color(0xFF0B4EA2) to Color(0xFFE31E24)),
    "lahore qalandars" to (Color(0xFF00A651) to Color(0xFFE31E24)),
    "islamabad united" to (Color(0xFFE31E24) to Color(0xFF1A1A1A)),
    "peshawar zalmi" to (Color(0xFFFFD100) to Color(0xFF1A1A1A)),
    "quetta gladiators" to (Color(0xFF5B2C83) to Color(0xFFFFD100)),
    "multan sultans" to (Color(0xFF00804A) to Color(0xFFFFD100)),
    // SA20
    "mi cape town" to (Color(0xFF004BA0) to Color(0xFFD1A23C)),
    "joburg super kings" to (Color(0xFFFDB913) to Color(0xFF0078BC)),
    "durban super giants" to (Color(0xFF0077C8) to Color(0xFFA5D9F7)),
    "pretoria capitals" to (Color(0xFF0078BC) to Color(0xFFEF1C25)),
    "paarl royals" to (Color(0xFFE91E63) to Color(0xFF003594)),
    "sunrisers eastern cape" to (Color(0xFFFF822A) to Color(0xFF1A1A1A)),
    // CPL
    "trinbago knight riders" to (Color(0xFFE31E24) to Color(0xFF1A1A1A)),
    "guyana amazon warriors" to (Color(0xFF00A651) to Color(0xFFFFD100)),
    "barbados royals" to (Color(0xFFE91E63) to Color(0xFF003594)),
    "jamaica tallawahs" to (Color(0xFF006B3F) to Color(0xFFFFD100)),
    "saint lucia kings" to (Color(0xFF0078BC) to Color(0xFFFFD100)),
    "st kitts and nevis patriots" to (Color(0xFFE31E24) to Color(0xFF00A651)),
    "antigua and barbuda falcons" to (Color(0xFF1A1A1A) to Color(0xFFE31E24)),
    // The Hundred
    "oval invincibles" to (Color(0xFF009A44) to Color(0xFFD4AF37)),
    "london spirit" to (Color(0xFF1E2E5B) to Color(0xFFD4AF37)),
    "southern brave" to (Color(0xFF00A9CE) to Color(0xFF1A1A1A)),
    "welsh fire" to (Color(0xFFE31E24) to Color(0xFF1A1A1A)),
    "manchester originals" to (Color(0xFF1A1A1A) to Color(0xFFFF6F00)),
    "northern superchargers" to (Color(0xFF5B2C83) to Color(0xFFFFD100)),
    "trent rockets" to (Color(0xFFFFD100) to Color(0xFF1A1A1A)),
    "birmingham phoenix" to (Color(0xFFFF6F00) to Color(0xFF1A1A1A)),
    // MLC
    "texas super kings" to (Color(0xFFFDB913) to Color(0xFF0078BC)),
    "mi new york" to (Color(0xFF004BA0) to Color(0xFFD1A23C)),
    "los angeles knight riders" to (Color(0xFF3A225D) to Color(0xFFB4975A)),
    "san francisco unicorns" to (Color(0xFFD50032) to Color(0xFF1A1A1A)),
    "seattle orcas" to (Color(0xFF003B5C) to Color(0xFF00A3AD)),
    "washington freedom" to (Color(0xFF0A3161) to Color(0xFFB31942)),
    // ILT20
    "mi emirates" to (Color(0xFF004BA0) to Color(0xFFD1A23C)),
    "abu dhabi knight riders" to (Color(0xFF3A225D) to Color(0xFFB4975A)),
    "desert vipers" to (Color(0xFFE31E24) to Color(0xFF1A1A1A)),
    "dubai capitals" to (Color(0xFF0078BC) to Color(0xFFEF1C25)),
    "gulf giants" to (Color(0xFFFF822A) to Color(0xFF1A1A1A)),
    "sharjah warriorz" to (Color(0xFF0B4EA2) to Color(0xFFE31E24)),
)

/**
 * Codes and former names that resolve to a [CURATED_TEAM_COLORS] key.
 *
 * Codes are matched upper-cased and whole, so a two-letter code never fuzzy-matches anything.
 * "royal challengers bangalore" stays as an alias: the franchise renamed in 2024 and archived
 * matches still carry the old name.
 */
internal val TEAM_COLOR_ALIASES: Map<String, String> = mapOf(
    "IND" to "india",
    "AUS" to "australia",
    "ENG" to "england",
    "PAK" to "pakistan",
    "SA" to "south africa",
    "RSA" to "south africa",
    "NZ" to "new zealand",
    "WI" to "west indies",
    "SL" to "sri lanka",
    "BAN" to "bangladesh",
    "AFG" to "afghanistan",
    "IRE" to "ireland",
    "ZIM" to "zimbabwe",
    "NED" to "netherlands",
    "SCO" to "scotland",
    "NEP" to "nepal",
    "UAE" to "united arab emirates",
    "USA" to "united states of america",
    "UNITED STATES" to "united states of america",
    "NAM" to "namibia",
    "OMA" to "oman",
    "CAN" to "canada",
    "HK" to "hong kong",
    "PNG" to "papua new guinea",
    "MI" to "mumbai indians",
    "CSK" to "chennai super kings",
    "RCB" to "royal challengers bengaluru",
    "ROYAL CHALLENGERS BANGALORE" to "royal challengers bengaluru",
    "KKR" to "kolkata knight riders",
    "RR" to "rajasthan royals",
    "PBKS" to "punjab kings",
    "KXIP" to "punjab kings",
    "KINGS XI PUNJAB" to "punjab kings",
    "SRH" to "sunrisers hyderabad",
    "DC" to "delhi capitals",
    "DD" to "delhi capitals",
    "DELHI DAREDEVILS" to "delhi capitals",
    "GT" to "gujarat titans",
    "LSG" to "lucknow super giants",
    "DURBAN S SUPER GIANTS" to "durban super giants",
    "ST LUCIA KINGS" to "saint lucia kings",
)
