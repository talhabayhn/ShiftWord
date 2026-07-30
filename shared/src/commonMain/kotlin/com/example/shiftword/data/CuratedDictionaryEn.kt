package com.example.shiftword.data

/**
 * Phase 9: English word pool, parallel to [CURATED_DICTIONARY_SEED_WORDS] (Turkish). Sourced
 * from hermitdave/FrequencyWords' `content/2018/en/en_50k.txt` (MIT License, Copyright (c) 2016
 * Hermit Dave): https://github.com/hermitdave/FrequencyWords -- a frequency-ranked word list
 * built from OpenSubtitles-derived corpora, license verified by fetching the actual LICENSE file
 * text directly (not just the GitHub API's license field), same standard as DICTIONARY_SOURCING.md.
 *
 * Pipeline: 50,000 frequency-ranked tokens -> 10,790 candidates (4-5 letters, pure-alphabetic,
 * i.e. no apostrophes/numbers) -> kept only tokens ranked in the top 3,000 most frequent overall
 * (a real frequency cutoff, not a guess at commonness) -> 1,238 -> excluded 11 words matching
 * LDNOOBW's English bad-words list (CC-BY-4.0, used only as a filtering aid, not redistributed)
 * -> excluded 27 more words with violent/heavy/offensive-adjacent connotations found on manual
 * review (KILL, GUNS, BOMB, JAIL, HELL, DAMN, CRAP, PISS, BURY, DEATH, BLOOD, SHOOT, CRIME,
 * KNIFE, DEVIL, CURSE, SCREW, NAKED, GRAVE, WOUND, ARMED, SLAVE, CRUEL, KILLS, DEMON, FLESH,
 * DRUGS) -> excluded 111 personal names and place names (JOHN, MARY, DAVID, PARIS, JAPAN, etc.)
 * that a raw subtitle-dialogue frequency corpus inevitably surfaces at high rank but that aren't
 * general vocabulary words -> excluded 16 apostrophe-stripped contraction fragments and eye-dialect
 * spellings that are artifacts of the source's tokenization, not real standalone words (DOESN,
 * HAVEN, WEREN, MUSTN, AREN, DIDN, HADN, HASN, WASN, GOIN, DOIN, GONNA, WANNA, GOTTA, OUTTA, KINDA)
 * -> 1,073 final (518 four-letter + 555 five-letter).
 *
 * Known limitation: unlike Zemberek's Turkish lemma dictionary, this source is raw dialogue-
 * frequency data, not a curated lexicon -- the proper-noun and contraction-artifact exclusions
 * above were caught by manual review, not a POS-tagged filter, so some may remain undetected.
 * A handful of common nouns that are also personal names (GRACE, GRANT, PENNY, CAROL, TEDDY,
 * MASON, ROBIN, KITTY, SAINT, MADAM, CHUCK, FRANK, MARK, BILL, DUKE, JUNE) were deliberately kept
 * since they're legitimate standalone vocabulary words, not just names.
 */
val CURATED_DICTIONARY_SEED_WORDS_EN: List<String> = listOf(
    // 4-letter words (518)
    "ABLE", "AGES", "ALSO", "AMEN", "AREA", "ARMS", "ARMY", "ASKS", "AUNT", "AWAY",
    "BABE", "BABY", "BACK", "BAGS", "BALL", "BAND", "BANG", "BANK", "BARS", "BASE",
    "BATH", "BEAR", "BEAT", "BEEN", "BEER", "BELL", "BELT", "BEST", "BIKE", "BILL",
    "BIRD", "BITE", "BLEW", "BLOW", "BLUE", "BOAT", "BODY", "BONE", "BOOK", "BOOM",
    "BORN", "BOSS", "BOTH", "BOWL", "BOYS", "BULL", "BURN", "BUST", "BUSY", "CAKE",
    "CALL", "CALM", "CAME", "CAMP", "CARD", "CARE", "CARS", "CASE", "CASH", "CAST",
    "CAVE", "CELL", "CHAT", "CHEF", "CITY", "CLUB", "CLUE", "COAT", "CODE", "COLD",
    "COME", "COOK", "COOL", "COPS", "COPY", "COST", "CREW", "CURE", "CUTE", "DARE",
    "DARK", "DATA", "DATE", "DAWN", "DAYS", "DEAD", "DEAL", "DEAR", "DEBT", "DEEP",
    "DENY", "DESK", "DIED", "DIES", "DIRT", "DISH", "DOES", "DOGS", "DOLL", "DONE",
    "DOOR", "DOWN", "DRAG", "DRAW", "DROP", "DRUG", "DUCK", "DUDE", "DUKE", "DUMB",
    "DUMP", "DUST", "DUTY", "EACH", "EARN", "EARS", "EAST", "EASY", "EDGE", "EGGS",
    "ELSE", "ENDS", "EVEN", "EVER", "EVIL", "EYES", "FACE", "FACT", "FAIL", "FAIR",
    "FAKE", "FALL", "FARM", "FAST", "FATE", "FEAR", "FEED", "FEEL", "FEET", "FELL",
    "FELT", "FILE", "FILL", "FILM", "FIND", "FINE", "FIRE", "FIRM", "FISH", "FIVE",
    "FLAG", "FLAT", "FOOD", "FOOL", "FOOT", "FORM", "FOUR", "FREE", "FROM", "FULL",
    "GAME", "GANG", "GATE", "GAVE", "GEAR", "GETS", "GIFT", "GIRL", "GIVE", "GLAD",
    "GOAL", "GODS", "GOES", "GOLD", "GONE", "GOOD", "GOSH", "GRAB", "GREW", "GROW",
    "GUTS", "GUYS", "HAIR", "HALF", "HALL", "HAND", "HANG", "HARD", "HARM", "HATE",
    "HAVE", "HEAD", "HEAR", "HEAT", "HELD", "HELP", "HERE", "HERO", "HIDE", "HIGH",
    "HILL", "HIRE", "HITS", "HOLD", "HOLE", "HOLY", "HOME", "HOOK", "HOPE", "HORN",
    "HOUR", "HUGE", "HUNG", "HUNT", "HURT", "IDEA", "INTO", "IRON", "JERK", "JOBS",
    "JOIN", "JOKE", "JUMP", "JUNE", "JURY", "JUST", "KEEP", "KEPT", "KEYS", "KICK",
    "KIDS", "KIND", "KING", "KISS", "KNEW", "KNOW", "LACK", "LADY", "LAID", "LAKE",
    "LAND", "LANE", "LAST", "LATE", "LAWS", "LEAD", "LEFT", "LEGS", "LESS", "LETS",
    "LIAR", "LIED", "LIES", "LIFE", "LIFT", "LIKE", "LINE", "LION", "LIPS", "LIST",
    "LIVE", "LOAD", "LOAN", "LOCK", "LONG", "LOOK", "LORD", "LOSE", "LOSS", "LOST",
    "LOTS", "LOUD", "LOVE", "LUCK", "MADE", "MAID", "MAIL", "MAIN", "MAKE", "MALE",
    "MAMA", "MANY", "MARK", "MASK", "MASS", "MATE", "MEAL", "MEAN", "MEAT", "MEET",
    "MESS", "MILK", "MIND", "MINE", "MISS", "MOOD", "MOON", "MORE", "MOST", "MOVE",
    "MUCH", "MUST", "NAME", "NAVY", "NEAR", "NECK", "NEED", "NEWS", "NEXT", "NICE",
    "NINE", "NONE", "NOPE", "NOSE", "NOTE", "NUTS", "OKAY", "ONCE", "ONES", "ONLY",
    "ONTO", "OPEN", "OURS", "OVER", "PACK", "PAGE", "PAID", "PAIN", "PAIR", "PAPA",
    "PARK", "PART", "PASS", "PAST", "PATH", "PICK", "PINK", "PITY", "PLAN", "PLAY",
    "PLUS", "POOL", "POOR", "POST", "PRAY", "PULL", "PURE", "PUSH", "PUTS", "QUIT",
    "RACE", "RAIN", "RARE", "RATE", "READ", "REAL", "RENT", "REST", "RICE", "RICH",
    "RIDE", "RING", "RISE", "RISK", "ROAD", "ROCK", "ROLE", "ROLL", "ROOF", "ROOM",
    "ROPE", "ROSE", "RUDE", "RUIN", "RULE", "RUNS", "RUSH", "SAFE", "SAID", "SAKE",
    "SALE", "SALT", "SAME", "SAND", "SAVE", "SAYS", "SEAT", "SEEK", "SEEM", "SEEN",
    "SEES", "SELF", "SELL", "SEND", "SENT", "SHIP", "SHOE", "SHOP", "SHOT", "SHOW",
    "SHUT", "SICK", "SIDE", "SIGN", "SING", "SITE", "SIZE", "SKIN", "SLIP", "SLOW",
    "SNOW", "SOFT", "SOLD", "SOME", "SONG", "SONS", "SOON", "SORT", "SOUL", "SOUP",
    "SPOT", "STAR", "STAY", "STEP", "STOP", "SUCH", "SUIT", "SURE", "SWIM", "TAIL",
    "TAKE", "TALK", "TALL", "TANK", "TAPE", "TASK", "TAXI", "TEAM", "TEAR", "TELL",
    "TERM", "TEST", "TEXT", "THAN", "THAT", "THEE", "THEM", "THEN", "THEY", "THIN",
    "THIS", "THOU", "TIED", "TILL", "TIME", "TINY", "TOLD", "TONE", "TOOK", "TOUR",
    "TOWN", "TRAP", "TREE", "TRIP", "TRUE", "TURN", "TYPE", "UGLY", "UNIT", "UPON",
    "USED", "VERY", "VIEW", "VOTE", "WAIT", "WAKE", "WALK", "WALL", "WANT", "WARM",
    "WARN", "WASH", "WAVE", "WAYS", "WEAK", "WEAR", "WEEK", "WELL", "WENT", "WERE",
    "WEST", "WHAT", "WHEN", "WHOA", "WHOM", "WHOO", "WIDE", "WIFE", "WILD", "WILL",
    "WIND", "WINE", "WINS", "WIRE", "WISE", "WISH", "WITH", "WOKE", "WOLF", "WOOD",
    "WORD", "WORK", "YARD", "YEAH", "YEAR", "YOUR", "ZERO", "ZONE",
    // 5-letter words (555)
    "ABOUT", "ABOVE", "ACTOR", "ADMIT", "ADULT", "AFTER", "AGAIN", "AGENT", "AGREE", "AHEAD",
    "ALARM", "ALERT", "ALIEN", "ALIVE", "ALLOW", "ALONE", "ALONG", "AMONG", "ANGEL", "ANGER",
    "ANGRY", "APART", "APPLE", "APRIL", "ARGUE", "ASIDE", "ASKED", "AVOID", "AWAKE", "AWARE",
    "AWFUL", "BADLY", "BALLS", "BASED", "BEACH", "BEAST", "BEEPS", "BEGAN", "BEGIN", "BEING",
    "BELOW", "BIRDS", "BIRTH", "BLACK", "BLAME", "BLAST", "BLEEP", "BLESS", "BLIND", "BLOCK",
    "BLOWS", "BOARD", "BONES", "BOOKS", "BOOTS", "BORED", "BOUND", "BRAIN", "BRAVE", "BRAVO",
    "BREAD", "BREAK", "BRIDE", "BRING", "BROKE", "BROWN", "BUCKS", "BUDDY", "BUILD", "BUILT",
    "BUNCH", "CALLS", "CANDY", "CARDS", "CARES", "CAROL", "CARRY", "CASES", "CATCH", "CAUSE",
    "CHAIN", "CHAIR", "CHASE", "CHEAP", "CHECK", "CHEER", "CHEST", "CHICK", "CHIEF", "CHILD",
    "CHOSE", "CHUCK", "CIVIL", "CLAIM", "CLASS", "CLEAN", "CLEAR", "CLIMB", "CLOCK", "CLOSE",
    "COACH", "COAST", "COLOR", "COMES", "COSTS", "COUCH", "COULD", "COUNT", "COURT", "COVER",
    "CRACK", "CRASH", "CRAZY", "CREAM", "CROSS", "CROWD", "CROWN", "CRUSH", "DADDY", "DAILY",
    "DANCE", "DIRTY", "DOING", "DOORS", "DOUBT", "DOZEN", "DRAMA", "DREAM", "DRESS", "DRINK",
    "DRIVE", "DROVE", "DRUNK", "DYING", "EARLY", "EARTH", "EATEN", "EIGHT", "EMPTY", "ENDED",
    "ENEMY", "ENJOY", "ENTER", "EVENT", "EVERY", "EXACT", "EXIST", "EXTRA", "FACES", "FACTS",
    "FAITH", "FALLS", "FALSE", "FANCY", "FAULT", "FAVOR", "FEELS", "FELLA", "FEVER", "FIELD",
    "FIFTH", "FIGHT", "FILES", "FILMS", "FINAL", "FINDS", "FIRED", "FIRST", "FIXED", "FLASH",
    "FLOOR", "FOCUS", "FOLKS", "FORCE", "FOUND", "FRANK", "FREAK", "FRESH", "FRONT", "FRUIT",
    "FULLY", "FUNNY", "GAMES", "GASPS", "GHOST", "GIANT", "GIRLS", "GIVEN", "GIVES", "GLASS",
    "GLORY", "GOING", "GRACE", "GRADE", "GRAND", "GRANT", "GREAT", "GREEN", "GROUP", "GROWN",
    "GUARD", "GUESS", "GUEST", "GUIDE", "HANDS", "HAPPY", "HATED", "HATES", "HEADS", "HEARD",
    "HEART", "HEAVY", "HELLO", "HELPS", "HIRED", "HONEY", "HONOR", "HORSE", "HOTEL", "HOURS",
    "HOUSE", "HUMAN", "HURRY", "HURTS", "IDEAS", "IDIOT", "IMAGE", "ISSUE", "JOINT", "JOKES",
    "JUDGE", "JUICE", "KEEPS", "KINDS", "KITTY", "KNEES", "KNOCK", "KNOWN", "KNOWS", "LARGE",
    "LATER", "LAUGH", "LEADS", "LEARN", "LEAST", "LEAVE", "LEGAL", "LEVEL", "LIGHT", "LIKED",
    "LIKES", "LINES", "LIVED", "LIVES", "LOCAL", "LOOKS", "LOOSE", "LOSER", "LOVED", "LOVER",
    "LOVES", "LOWER", "LUCKY", "LUNCH", "LYING", "MADAM", "MAGIC", "MAJOR", "MAKES", "MARCH",
    "MARKS", "MARRY", "MASON", "MATCH", "MAYBE", "MAYOR", "MEANS", "MEANT", "MEDIA", "MERCY",
    "MERRY", "METAL", "MIGHT", "MILES", "MINDS", "MIXED", "MODEL", "MOMMY", "MONEY", "MONTH",
    "MOUTH", "MOVED", "MOVES", "MOVIE", "MUMMY", "MUSIC", "NAMED", "NAMES", "NASTY", "NEEDS",
    "NEVER", "NIGHT", "NOISE", "NORTH", "NOTES", "NURSE", "OCEAN", "OFFER", "OFTEN", "OLDER",
    "OPENS", "ORDER", "OTHER", "OUGHT", "OWNER", "PAINT", "PANIC", "PANTS", "PAPER", "PARTS",
    "PARTY", "PEACE", "PENNY", "PHONE", "PHOTO", "PIANO", "PIECE", "PILLS", "PILOT", "PIZZA",
    "PLACE", "PLANE", "PLANS", "PLANT", "PLATE", "PLAYS", "POINT", "POWER", "PRESS", "PRICE",
    "PRIDE", "PRIME", "PRIZE", "PROOF", "PROUD", "PROVE", "PUNCH", "QUEEN", "QUICK", "QUIET",
    "QUITE", "RADIO", "RAISE", "RANGE", "REACH", "READY", "RELAX", "RIGHT", "RINGS", "RIVER",
    "ROBIN", "ROCKS", "ROOMS", "ROUGH", "ROUND", "ROUTE", "ROYAL", "RULES", "SAINT", "SAVED",
    "SCARE", "SCARY", "SCENE", "SCORE", "SEEMS", "SENSE", "SERVE", "SEVEN", "SHAKE", "SHALL",
    "SHAME", "SHAPE", "SHARE", "SHARP", "SHIFT", "SHINE", "SHIPS", "SHIRT", "SHOCK", "SHOES",
    "SHORT", "SHOTS", "SHOUT", "SHOWS", "SIDES", "SIGHS", "SIGHT", "SIGNS", "SILLY", "SINCE",
    "SIREN", "SLEEP", "SLEPT", "SMALL", "SMART", "SMELL", "SMILE", "SMOKE", "SNAKE", "SOLID",
    "SOLVE", "SONGS", "SORRY", "SOUND", "SOUTH", "SPACE", "SPARE", "SPEAK", "SPEED", "SPELL",
    "SPEND", "SPENT", "SPLIT", "SPOKE", "SQUAD", "STAFF", "STAGE", "STAND", "STARS", "START",
    "STATE", "STAYS", "STEAL", "STEPS", "STICK", "STILL", "STOCK", "STOLE", "STONE", "STOOD",
    "STOPS", "STORE", "STORM", "STORY", "STUCK", "STUDY", "STUFF", "STYLE", "SUGAR", "SUITS",
    "SUPER", "SWEAR", "SWEAT", "SWEET", "SWING", "SWORD", "TABLE", "TAKEN", "TAKES", "TALKS",
    "TASTE", "TEACH", "TEARS", "TEDDY", "TEETH", "TELLS", "TERMS", "TESTS", "THANK", "THEIR",
    "THEME", "THERE", "THESE", "THIEF", "THING", "THINK", "THIRD", "THOSE", "THREE", "THREW",
    "THROW", "TIGER", "TIGHT", "TIMES", "TIRED", "TIRES", "TITLE", "TOAST", "TODAY", "TOTAL",
    "TOUCH", "TOUGH", "TOWER", "TRACE", "TRACK", "TRADE", "TRAIL", "TRAIN", "TRASH", "TREAT",
    "TREES", "TRIAL", "TRICK", "TRIED", "TRUCK", "TRULY", "TRUST", "TRUTH", "TURNS", "TWICE",
    "UNCLE", "UNDER", "UNION", "UNTIL", "UPSET", "USING", "USUAL", "VALUE", "VIDEO", "VISIT",
    "VOICE", "WALLS", "WANTS", "WASTE", "WATCH", "WATER", "WEEKS", "WEIRD", "WHEEL", "WHERE",
    "WHICH", "WHILE", "WHITE", "WHOLE", "WHOSE", "WINGS", "WITCH", "WOMAN", "WOMEN", "WOODS",
    "WORDS", "WORKS", "WORLD", "WORRY", "WORSE", "WORST", "WORTH", "WOULD", "WRITE", "WRONG",
    "WROTE", "YEARS", "YOUNG", "YOURS", "YOUTH",
)
