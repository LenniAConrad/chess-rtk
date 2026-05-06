package chess.tag.core;

/**
 * Central repository for literal strings and symbols used by the tagging
 * package.
 * <p>
 * These constants define the canonical tag vocabulary, JSON field names,
 * punctuation, and formatting fragments shared across tag builders and parsers.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class Literals {

    /**
     * The empty string literal.
     */
    public static final String EMPTY = "";

    /**
     * A single space.
     */
    public static final String SPACE_TEXT = " ";

    /**
     * A colon followed by a space.
     */
    public static final String COLON_SPACE = ": ";

    /**
     * A comma followed by a space.
     */
    public static final String COMMA_SPACE = ", ";

    /**
     * A conjunction used in human-readable descriptions.
     */
    public static final String AND = " and ";

    /**
     * A directional separator used in move descriptions.
     */
    public static final String TO = " to ";

    /**
     * A connective phrase used in descriptive text.
     */
    public static final String WITH = " with ";

    /**
     * A trailing phrase used in some descriptive tags.
     */
    public static final String BEHIND = " behind";

    /**
     * The wording used when describing enabling relationships.
     */
    public static final String ENABLES_ELLIPSIS = " enables ... ";

    /**
     * The wording used when describing disabling relationships.
     */
    public static final String DISABLES_ELLIPSIS = " disables ... ";

    /**
     * A regular expression that matches whitespace.
     */
    public static final String SPACE_REGEX = "\\s+";

    /**
     * An escaped quote sequence for serialized text.
     */
    public static final String ESCAPED_QUOTE = "\\\"";

    /**
     * An escaped backslash sequence for serialized text.
     */
    public static final String ESCAPED_BACKSLASH = "\\\\";

    /**
     * A slash used in compact ratio-like values.
     */
    public static final String SLASH_SEPARATOR = "/";

    /**
     * A dot followed by a space.
     */
    public static final String DOT_SPACE = ". ";

    /**
     * An ellipsis followed by a space.
     */
    public static final String ELLIPSIS_SPACE = "... ";

    /**
     * The prefix used before parenthesized detail blocks.
     */
    public static final String OPEN_PAREN_PREFIX = " (";

    /**
     * The format string for two-decimal rendering.
     */
    public static final String FORMAT_TWO_DECIMALS = "%.2f";

    /**
     * The name of the shared evaluator shutdown hook thread.
     */
    public static final String CRTK_TAG_EVALUATOR_SHUTDOWN = "crtk-tag-evaluator-shutdown";

    /**
     * The default ECO book configuration path.
     */
    public static final String ECO_BOOK_PATH = "config/book.eco.toml";

    /**
     * The warning prefix emitted when opening tags cannot be loaded.
     */
    public static final String OPENING_DISABLED_LOG = "Opening tags disabled: unable to load ECO book (";

    /**
     * The FEN token used to indicate White to move.
     */
    public static final String FEN_WHITE_TO_MOVE = "w";

    /**
     * The FEN token used to indicate Black to move.
     */
    public static final String FEN_BLACK_TO_MOVE = "b";

    /**
     * The SAN notation for kingside castling.
     */
    public static final String CASTLE_SAN_KINGSIDE = "O-O";

    /**
     * The SAN notation for queenside castling.
     */
    public static final String CASTLE_SAN_QUEENSIDE = "O-O-O";

    /**
     * The textual prefix used when displaying difficulty.
     */
    public static final String DIFFICULTY_DISPLAY_PREFIX = "difficulty: ";

    /**
     * The leading text for parenthesized output fragments.
     */
    public static final String OPEN_PAREN_TEXT = " (";

    /**
     * The required parameter name for a position.
     */
    public static final String POSITION = "position";

    /**
     * The required parameter name for an evaluator.
     */
    public static final String EVALUATOR = "evaluator";

    /**
     * The required parameter name for move lists.
     */
    public static final String MOVES = "moves";

    /**
     * The required parameter name for position lists.
     */
    public static final String POSITIONS = "positions";

    /**
     * The lowercase name for White.
     */
    public static final String WHITE = "white";

    /**
     * The lowercase name for Black.
     */
    public static final String BLACK = "black";

    /**
     * The canonical equal-side label.
     */
    public static final String EQUAL = "equal";

    /**
     * The label used for balanced positions.
     */
    public static final String BALANCED = "balanced";

    /**
     * The label used when no value is available.
     */
    public static final String NONE = "none";

    /**
     * The affirmative label.
     */
    public static final String YES = "yes";

    /**
     * The negative label.
     */
    public static final String NO = "no";

    /**
     * The FACT family name.
     */
    public static final String FACT = "FACT";

    /**
     * The META family name.
     */
    public static final String META = "META";

    /**
     * The THREAT family name.
     */
    public static final String THREAT = "THREAT";

    /**
     * The candidate move family name.
     */
    public static final String CAND = "CAND";

    /**
     * The principal variation family name.
     */
    public static final String PV = "PV";

    /**
     * The idea family name.
     */
    public static final String IDEA = "IDEA";

    /**
     * The tactical family name.
     */
    public static final String TACTIC = "TACTIC";

    /**
     * The checkmate family name.
     */
    public static final String CHECKMATE = "CHECKMATE";

    /**
     * The piece family name.
     */
    public static final String PIECE = "PIECE";

    /**
     * The king family name.
     */
    public static final String KING = "KING";

    /**
     * The lowercase pawn label used in some outputs.
     */
    public static final String PAWN = "pawn";

    /**
     * The uppercase pawn-family name.
     */
    public static final String PAWN_FAMILY = "PAWN";

    /**
     * The material family name.
     */
    public static final String MATERIAL = "MATERIAL";

    /**
     * The move-facts family name.
     */
    public static final String MOVE_FAMILY = "MOVE";

    /**
     * The space family name.
     */
    public static final String SPACE = "SPACE";

    /**
     * The initiative family name.
     */
    public static final String INITIATIVE = "INITIATIVE";

    /**
     * The development family name.
     */
    public static final String DEVELOPMENT = "DEVELOPMENT";

    /**
     * The mobility family name.
     */
    public static final String MOBILITY = "MOBILITY";

    /**
     * The outpost family name.
     */
    public static final String OUTPOST = "OUTPOST";

    /**
     * The endgame family name.
     */
    public static final String ENDGAME = "ENDGAME";

    /**
     * The opening family name.
     */
    public static final String OPENING = "OPENING";

    /**
     * The FACT prefix.
     */
    public static final String FACT_PREFIX = FACT + ": ";

    /**
     * The META prefix.
     */
    public static final String META_PREFIX = META + ": ";

    /**
     * The FACT header without trailing space.
     */
    public static final String FACT_HEADER = FACT + ":";

    /**
     * The META header without trailing space.
     */
    public static final String META_HEADER = META + ":";

    /**
     * The status fact prefix.
     */
    public static final String STATUS_PREFIX = FACT_PREFIX + "status=";

    /**
     * The center-control fact prefix.
     */
    public static final String CENTER_CONTROL_PREFIX = FACT_PREFIX + "center_control=";

    /**
     * The space-advantage fact prefix.
     */
    public static final String SPACE_ADVANTAGE_PREFIX = FACT_PREFIX + "space_advantage=";

    /**
     * The pawn-majority prefix.
     */
    public static final String PAWN_MAJORITY_PREFIX = "PAWN: majority=";

    /**
     * The suffix used when appending a count field.
     */
    public static final String COUNT_FIELD = " count=";

    /**
     * The suffix used when appending a side field.
     */
    public static final String SIDE_FIELD = " side=";

    /**
     * The suffix used when appending a square field.
     */
    public static final String SQUARE_FIELD = " square=";

    /**
     * The suffix used when appending a file field.
     */
    public static final String FILE_FIELD = " file=";

    /**
     * The suffix used when appending a squares field.
     */
    public static final String SQUARES_FIELD = " squares=";

    /**
     * The suffix used when appending a rook-side field.
     */
    public static final String ROOK_SIDE_FIELD = " rook_side=";

    /**
     * The side field key.
     */
    public static final String SIDE = "side";

    /**
     * The piece field key.
     */
    public static final String PIECE_KEY = "piece";

    /**
     * The type field key.
     */
    public static final String TYPE = "type";

    /**
     * The count field key.
     */
    public static final String COUNT = "count";

    /**
     * The square field key.
     */
    public static final String SQUARE = "square";

    /**
     * The squares field key.
     */
    public static final String SQUARES = "squares";

    /**
     * The file field key.
     */
    public static final String FILE = "file";

    /**
     * The status field key.
     */
    public static final String STATUS = "status";

    /**
     * The structure field key.
     */
    public static final String STRUCTURE = "structure";

    /**
     * The majority field key.
     */
    public static final String MAJORITY = "majority";

    /**
     * The islands field key.
     */
    public static final String ISLANDS = "islands";

    /**
     * The rook-side field key.
     */
    public static final String ROOK_SIDE = "rook_side";

    /**
     * The ECO field key.
     */
    public static final String ECO = "eco";

    /**
     * The detail field key.
     */
    public static final String DETAIL = "detail";

    /**
     * The motif field key.
     */
    public static final String MOTIF = "motif";

    /**
     * The tier field key.
     */
    public static final String TIER = "tier";

    /**
     * The activity field key.
     */
    public static final String ACTIVITY = "activity";

    /**
     * The extreme field key.
     */
    public static final String EXTREME = "extreme";

    /**
     * The name field key.
     */
    public static final String NAME = "name";

    /**
     * The source field key.
     */
    public static final String SOURCE = "source";

    /**
     * The role field key.
     */
    public static final String ROLE = "role";

    /**
     * The move field key.
     */
    public static final String MOVE = "move";

    /**
     * The legal-move-count field key.
     */
    public static final String LEGAL = "legal";

    /**
     * The capture-count field key.
     */
    public static final String CAPTURES = "captures";

    /**
     * The checking-move-count field key.
     */
    public static final String CHECKS = "checks";

    /**
     * The mate-in-one move-count field key.
     */
    public static final String MATES = "mates";

    /**
     * The promotion-move-count field key.
     */
    public static final String PROMOTIONS = "promotions";

    /**
     * The castling-move-count field key.
     */
    public static final String CASTLES = "castles";

    /**
     * The quiet-move-count field key.
     */
    public static final String QUIET = "quiet";

    /**
     * The only-legal-move field key.
     */
    public static final String ONLY = "only";

    /**
     * The forced-move field key.
     */
    public static final String FORCED = "forced";

    /**
     * The legal evasion-count field key.
     */
    public static final String EVASIONS = "evasions";

    /**
     * The underpromotion-count field key.
     */
    public static final String UNDERPROMOTIONS = "underpromotions";

    /**
     * The note field key.
     */
    public static final String NOTE = "note";

    /**
     * The balance field key.
     */
    public static final String BALANCE = "balance";

    /**
     * The imbalance field key.
     */
    public static final String IMBALANCE = "imbalance";

    /**
     * The phase field key.
     */
    public static final String PHASE = "phase";

    /**
     * The to-move field key.
     */
    public static final String TO_MOVE = "to_move";

    /**
     * The centipawn evaluation field key.
     */
    public static final String EVAL_CP = "eval_cp";

    /**
     * The evaluation-bucket field key.
     */
    public static final String EVAL_BUCKET = "eval_bucket";

    /**
     * The mate-in field key.
     */
    public static final String MATE_IN = "mate_in";

    /**
     * The mated-in field key.
     */
    public static final String MATED_IN = "mated_in";

    /**
     * The checkmate-pattern field key.
     */
    public static final String PATTERN = "pattern";

    /**
     * The checkmate winner field key.
     */
    public static final String WINNER = "winner";

    /**
     * The checkmate defender field key.
     */
    public static final String DEFENDER = "defender";

    /**
     * The checkmate delivery-piece field key.
     */
    public static final String DELIVERY = "delivery";

    /**
     * The WDL field key.
     */
    public static final String WDL = "wdl";

    /**
     * The in-check field key.
     */
    public static final String IN_CHECK = "in_check";

    /**
     * The castle-rights field key.
     */
    public static final String CASTLE_RIGHTS = "castle_rights";

    /**
     * The en-passant field key.
     */
    public static final String EN_PASSANT = "en_passant";

    /**
     * The endgame field key.
     */
    public static final String ENDGAME_KEY = "endgame";

    /**
     * The castled field key.
     */
    public static final String CASTLED_KEY = "castled";

    /**
     * The king shelter field key.
     */
    public static final String SHELTER = "shelter";

    /**
     * The king safety field key.
     */
    public static final String SAFETY = "safety";

    /**
     * The severity field key.
     */
    public static final String SEVERITY = "severity";

    /**
     * The puzzle field key.
     */
    public static final String PUZZLE = "puzzle";

    /**
     * The JSON added-array key.
     */
    public static final String JSON_ADDED = "added";

    /**
     * The JSON removed-array key.
     */
    public static final String JSON_REMOVED = "removed";

    /**
     * The JSON changed-array key.
     */
    public static final String JSON_CHANGED = "changed";

    /**
     * The JSON object key field name.
     */
    public static final String JSON_KEY = "key";

    /**
     * The JSON from field name.
     */
    public static final String JSON_FROM = "from";

    /**
     * The JSON to field name.
     */
    public static final String JSON_TO = "to";

    /**
     * The separator used between JSON string names and values.
     */
    public static final String JSON_STRING_SEPARATOR = "\":\"";

    /**
     * The separator used between JSON field names and values.
     */
    public static final String JSON_NAME_SEPARATOR = "\":";

     /**
     * Shared center control constant.
     */
     public static final String CENTER_CONTROL = "center_control";

    /**
     * The space-advantage field key.
     */
    public static final String SPACE_ADVANTAGE = "space_advantage";

    /**
     * The center-state field key.
     */
    public static final String CENTER_STATE = "center_state";

    /**
     * The piece-count family label.
     */
    public static final String PIECE_COUNT = "piece_count";

    /**
     * The pawn-structure family label.
     */
    public static final String PAWN_STRUCTURE = "pawn_structure";

    /**
     * The promotion-availability family label.
     */
    public static final String PROMOTION_AVAILABLE = "promotion_available";

    /**
     * The tactical family label.
     */
    public static final String TACTICAL = "tactical";

    /**
     * The analysis source label.
     */
    public static final String ANALYSIS = "analysis";

    /**
     * The engine source label.
     */
    public static final String ENGINE = "engine";

    /**
     * The kingside castling label.
     */
    public static final String KINGSIDE = "kingside";

    /**
     * The queenside castling label.
     */
    public static final String QUEENSIDE = "queenside";

    /**
     * The castled state label.
     */
    public static final String CASTLED = "castled";

    /**
     * The uncastled state label.
     */
    public static final String UNCASTLED = "uncastled";

    /**
     * The open king-safety label.
     */
    public static final String OPEN = "open";

    /**
     * The closed king-safety label.
     */
    public static final String CLOSED = "closed";

    /**
     * The weakened king-safety label.
     */
    public static final String WEAKENED = "weakened";

    /**
     * The check status label.
     */
    public static final String CHECK = "check";

    /**
     * The checkmated status label.
     */
    public static final String CHECKMATED = "checkmated";

    /**
     * The stalemate status label.
     */
    public static final String STALEMATE = "stalemate";

    /**
     * The normal status label.
     */
    public static final String NORMAL = "normal";

    /**
     * The insufficient-material status label.
     */
    public static final String INSUFFICIENT = "insufficient";

    /**
     * The neutral difficulty label.
     */
    public static final String NEUTRAL = "neutral";

    /**
     * The strong difficulty label.
     */
    public static final String STRONG = "strong";

    /**
     * The weak difficulty label.
     */
    public static final String WEAK = "weak";

    /**
     * The easy difficulty label.
     */
    public static final String EASY = "easy";

    /**
     * The medium difficulty label.
     */
    public static final String MEDIUM = "medium";

    /**
     * The hard difficulty label.
     */
    public static final String HARD = "hard";

    /**
     * The very-easy difficulty label.
     */
    public static final String VERY_EASY = "very_easy";

    /**
     * The very-hard difficulty label.
     */
    public static final String VERY_HARD = "very_hard";

    /**
     * The human-readable very-easy text.
     */
    public static final String VERY_EASY_TEXT = "very easy";

    /**
     * The human-readable hard text.
     */
    public static final String HARD_TEXT = "hard";

    /**
     * The human-readable very-hard text.
     */
    public static final String VERY_HARD_TEXT = "very hard";

    /**
     * The human-readable very-strong text.
     */
    public static final String VERY_STRONG_TEXT = "very strong";

    /**
     * The human-readable slightly-strong text.
     */
    public static final String SLIGHTLY_STRONG_TEXT = "slightly strong";

    /**
     * The human-readable slightly-weak text.
     */
    public static final String SLIGHTLY_WEAK_TEXT = "slightly weak";

    /**
     * The human-readable very-weak text.
     */
    public static final String VERY_WEAK_TEXT = "very weak";

    /**
     * The opening phase label.
     */
    public static final String OPENING_LOWER = "opening";

    /**
     * The middlegame phase label.
     */
    public static final String MIDDLEGAME = "middlegame";

    /**
     * The endgame phase label.
     */
    public static final String ENDGAME_LOWER = "endgame";

    /**
     * The draw label.
     */
    public static final String DRAW = "draw";

    /**
     * The winning label.
     */
    public static final String WINNING = "winning";

    /**
     * The boolean true label.
     */
    public static final String TRUE = "true";

    /**
     * The label used when multiple pieces deliver mate.
     */
    public static final String MULTIPLE = "multiple";

    /**
     * The double-check mate pattern label.
     */
    public static final String DOUBLE_CHECK = "double_check";

    /**
     * The back-rank mate pattern label.
     */
    public static final String BACK_RANK_MATE = "back_rank_mate";

    /**
     * The smothered-mate pattern label.
     */
    public static final String SMOTHERED_MATE = "smothered_mate";

    /**
     * The corner-mate pattern label.
     */
    public static final String CORNER_MATE = "corner_mate";

    /**
     * The support-mate pattern label.
     */
    public static final String SUPPORT_MATE = "support_mate";

    /**
     * The immediate severity label.
     */
    public static final String IMMEDIATE = "immediate";

    /**
     * The promote action label.
     */
    public static final String PROMOTE = "promote";

    /**
     * The best candidate role label.
     */
    public static final String BEST = "best";

    /**
     * The alternate candidate role label.
     */
    public static final String ALT = "alt";

    /**
     * The slight- prefix used for evaluation buckets.
     */
    public static final String SLIGHT_PREFIX = "slight_";

    /**
     * The clear- prefix used for evaluation buckets.
     */
    public static final String CLEAR_PREFIX = "clear_";

    /**
     * The winning- prefix used for evaluation buckets.
     */
    public static final String WINNING_PREFIX = "winning_";

    /**
     * The crushing- prefix used for evaluation buckets.
     */
    public static final String CRUSHING_PREFIX = "crushing_";

    /**
     * The suffix used for material-up-by-a-pawn labels.
     */
    public static final String UP_PAWN_SUFFIX = "_up_pawn";

    /**
     * The suffix used for material-up-by-a-minor labels.
     */
    public static final String UP_MINOR_SUFFIX = "_up_minor";

    /**
     * The suffix used for material-up-by-an-exchange labels.
     */
    public static final String UP_EXCHANGE_SUFFIX = "_up_exchange";

    /**
     * The suffix used for material-up-by-a-queen labels.
     */
    public static final String UP_QUEEN_SUFFIX = "_up_queen";

    /**
     * The bishop-pair label for White.
     */
    public static final String BISHOP_PAIR_WHITE = "bishop_pair_white";

    /**
     * The bishop-pair label for Black.
     */
    public static final String BISHOP_PAIR_BLACK = "bishop_pair_black";

    /**
     * The queenless material label.
     */
    public static final String QUEENLESS = "queenless";

    /**
     * The rookless material label.
     */
    public static final String ROOKLESS = "rookless";

    /**
     * The opposite-colored bishop label.
     */
    public static final String OPPOSITE_COLOR_BISHOPS = "opposite_color_bishops";

    /**
     * The same-colored bishop label.
     */
    public static final String SAME_COLOR_BISHOPS = "same_color_bishops";

    /**
     * The shorter opposite-bishops label used in endgame tags.
     */
    public static final String OPPOSITE_BISHOPS = "opposite_bishops";

    /**
     * The rook-endgame label.
     */
    public static final String ROOK_ENDGAME = "rook_endgame";

    /**
     * The minor-piece endgame label.
     */
    public static final String MINOR_PIECE_ENDGAME = "minor_piece_endgame";

    /**
     * The short rook-endgame label.
     */
    public static final String ROOK_ENDGAME_SHORT = "rook";

    /**
     * The short minor-piece-endgame label.
     */
    public static final String MINOR_ENDGAME_SHORT = "minor";

    /**
     * The check text used in move descriptions.
     */
    public static final String CHECK_TEXT = "check";

    /**
     * The checkmate text used in move descriptions.
     */
    public static final String CHECKMATE_TEXT = "checkmate";

    /**
     * The stalemate text used in move descriptions.
     */
    public static final String STALEMATE_TEXT = "stalemate";

    /**
     * The underpromotion label.
     */
    public static final String UNDERPROMOTION = "underpromotion";

    /**
     * The label for escaping attack.
     */
    public static final String ESCAPES_ATTACK = "escapes attack";

    /**
     * The label for moving into attack.
     */
    public static final String MOVES_INTO_ATTACK = "moves into attack";

    /**
     * The strongest label.
     */
    public static final String STRONGEST = "strongest";

    /**
     * The weakest label.
     */
    public static final String WEAKEST = "weakest";

    /**
     * The strongest-white label.
     */
    public static final String STRONGEST_WHITE = "strongest white";

    /**
     * The weakest-white label.
     */
    public static final String WEAKEST_WHITE = "weakest white";

    /**
     * The strongest-black label.
     */
    public static final String STRONGEST_BLACK = "strongest black";

    /**
     * The weakest-black label.
     */
    public static final String WEAKEST_BLACK = "weakest black";

    /**
     * The isolated-pawn label.
     */
    public static final String ISOLATED = "isolated";

    /**
     * The passed-pawn label.
     */
    public static final String PASSED = "passed";

    /**
     * The backward-pawn label.
     */
    public static final String BACKWARD = "backward";

    /**
     * The doubled-pawn label.
     */
    public static final String DOUBLED = "doubled";

    /**
     * The connected-passed-pawn label.
     */
    public static final String CONNECTED_PASSED = "connected_passed";

    /**
     * The normalized very-strong label.
     */
    public static final String VERY_STRONG = "very_strong";

    /**
     * The normalized slightly-strong label.
     */
    public static final String SLIGHTLY_STRONG = "slightly_strong";

    /**
     * The normalized slightly-weak label.
     */
    public static final String SLIGHTLY_WEAK = "slightly_weak";

    /**
     * The normalized very-weak label.
     */
    public static final String VERY_WEAK = "very_weak";

    /**
     * The pin motif label.
     */
    public static final String PIN = "pin";

    /**
     * The skewer motif label.
     */
    public static final String SKEWER = "skewer";

    /**
     * The discovered-attack motif label.
     */
    public static final String DISCOVERED_ATTACK = "discovered_attack";

    /**
     * The overload motif label.
     */
    public static final String OVERLOAD = "overload";

    /**
     * The hanging motif label.
     */
    public static final String HANGING = "hanging";

    /**
     * The trapped label.
     */
    public static final String TRAPPED = "trapped";

    /**
     * The low-mobility label.
     */
    public static final String LOW_MOBILITY = "low_mobility";

    /**
     * The high-mobility label.
     */
    public static final String HIGH_MOBILITY = "high_mobility";

    /**
     * The center label.
     */
    public static final String CENTER = "center";

    /**
     * The knight piece label.
     */
    public static final String KNIGHT = "knight";

    /**
     * The bishop piece label.
     */
    public static final String BISHOP = "bishop";

    /**
     * The rook piece label.
     */
    public static final String ROOK = "rook";

    /**
     * The queen piece label.
     */
    public static final String QUEEN = "queen";

    /**
     * The king piece label.
     */
    public static final String KING_NAME = "king";

    /**
     * The prefix used for outpost tags.
     */
    public static final String OUTPOST_PREFIX = "outpost: ";

    /**
     * The phrase used when describing an open file near a king.
     */
    public static final String OPEN_FILE_NEAR = "open file near ";

    /**
     * The phrase used when describing an open file near the White king.
     */
    public static final String OPEN_FILE_NEAR_WHITE_KING = OPEN_FILE_NEAR + WHITE + " " + KING_NAME;

    /**
     * The phrase used when describing an open file near the Black king.
     */
    public static final String OPEN_FILE_NEAR_BLACK_KING = OPEN_FILE_NEAR + BLACK + " " + KING_NAME;

    /**
     * The phrase used when describing a weakened pawn shield.
     */
    public static final String PAWN_SHIELD_WEAKENED = "pawn shield weakened";

    /**
     * The phrase used when describing an exposed king.
     */
    public static final String KING_EXPOSED = "king exposed";

    /**
     * The king-safety label used when pawns remain intact.
     */
    public static final String PAWNS_INTACT = "pawns_intact";

    /**
     * The safe king-safety label.
     */
    public static final String SAFE = "safe";

    /**
     * The unsafe king-safety label.
     */
    public static final String UNSAFE = "unsafe";

    /**
     * The very-safe king-safety label.
     */
    public static final String VERY_SAFE = "very_safe";

    /**
     * The very-unsafe king-safety label.
     */
    public static final String VERY_UNSAFE = "very_unsafe";

    /**
     * The pin prefix used for raw tactical text.
     */
    public static final String PIN_PREFIX = "pin: ";

    /**
     * The skewer prefix used for raw tactical text.
     */
    public static final String SKEWER_PREFIX = "skewer: ";

    /**
     * The discovered-attack prefix used for raw tactical text.
     */
    public static final String DISCOVERED_ATTACK_PREFIX = "discovered attack: ";

    /**
     * The overloaded-defender prefix used for raw tactical text.
     */
    public static final String OVERLOADED_DEFENDER_PREFIX = "overloaded defender: ";

    /**
     * The hanging prefix used for raw tactical text.
     */
    public static final String HANGING_PREFIX = "hanging ";

    /**
     * The pin header used when matching raw tactical text.
     */
    public static final String PIN_HEADER = "pin:";

    /**
     * The skewer header used when matching raw tactical text.
     */
    public static final String SKEWER_HEADER = "skewer:";

    /**
     * The discovered-attack header used when matching raw tactical text.
     */
    public static final String DISCOVERED_ATTACK_HEADER = "discovered attack:";

    /**
     * The overloaded-defender header used when matching raw tactical text.
     */
    public static final String OVERLOADED_DEFENDER_HEADER = "overloaded defender:";

    /**
     * The castling-right prefix used by tag output.
     */
    public static final String CASTLING_RIGHT_PREFIX = FACT_PREFIX + "castling_right side=";

    /**
     * The can-castle prefix used by tag output.
     */
    public static final String CAN_CASTLE_PREFIX = FACT_PREFIX + "can_castle side=";

    /**
     * The castling-status prefix used by tag output.
     */
    public static final String CASTLING_STATUS_PREFIX = FACT_PREFIX + "castling_status side=";

    /**
     * The META to-move prefix.
     */
    public static final String META_TO_MOVE_PREFIX = META_PREFIX + TO_MOVE + "=";

    /**
     * The META phase prefix.
     */
    public static final String META_PHASE_PREFIX = META_PREFIX + PHASE + "=";

    /**
     * The META source prefix.
     */
    public static final String META_SOURCE_PREFIX = META_PREFIX + SOURCE + "=";

    /**
     * The META centipawn-evaluation prefix.
     */
    public static final String META_EVAL_CP_PREFIX = META_PREFIX + EVAL_CP + "=";

    /**
     * The META mate-in prefix.
     */
    public static final String META_MATE_IN_PREFIX = META_PREFIX + MATE_IN + "=";

    /**
     * The META mated-in prefix.
     */
    public static final String META_MATED_IN_PREFIX = META_PREFIX + MATED_IN + "=";

    /**
     * The META WDL prefix.
     */
    public static final String META_WDL_PREFIX = META_PREFIX + WDL + "=";

    /**
     * The META difficulty prefix.
     */
    public static final String META_DIFFICULTY_PREFIX = META_PREFIX + "difficulty=";

    /**
     * The META material-count prefix for White.
     */
    public static final String META_MATERIAL_CP_WHITE_PREFIX = META_PREFIX + "material_cp_white=";

    /**
     * The META material-count prefix for Black.
     */
    public static final String META_MATERIAL_CP_BLACK_PREFIX = META_PREFIX + "material_cp_black=";

    /**
     * The META material-discrepancy prefix.
     */
    public static final String META_MATERIAL_DISCREPANCY_CP_PREFIX = META_PREFIX + "material_discrepancy_cp=";

    /**
     * The META ECO prefix.
     */
    public static final String META_ECO_PREFIX = META_PREFIX + ECO + "=";

    /**
     * The META opening-name prefix.
     */
    public static final String META_OPENING_PREFIX = META_PREFIX + OPENING_LOWER + "=";

    /**
     * The META opening-name prefix with an opening quote.
     */
    public static final String META_OPENING_NAME_PREFIX = META_PREFIX + OPENING_LOWER + "=\"";

    /**
     * The META evaluation-bucket prefix.
     */
    public static final String META_EVAL_BUCKET_PREFIX = META_PREFIX + EVAL_BUCKET + "=";

    /**
     * The in-check fact prefix.
     */
    public static final String FACT_IN_CHECK_PREFIX = FACT_PREFIX + IN_CHECK + "=";

    /**
     * The castle-rights fact prefix.
     */
    public static final String FACT_CASTLE_RIGHTS_PREFIX = FACT_PREFIX + CASTLE_RIGHTS + "=";

    /**
     * The en-passant fact prefix.
     */
    public static final String FACT_EN_PASSANT_PREFIX = FACT_PREFIX + EN_PASSANT + "=";

    /**
     * The center-state fact prefix.
     */
    public static final String FACT_CENTER_STATE_PREFIX = FACT_PREFIX + CENTER_STATE + "=";

    /**
     * The endgame fact prefix.
     */
    public static final String FACT_ENDGAME_PREFIX = FACT_PREFIX + ENDGAME_KEY + "=";

    /**
     * The opposite-colored bishops fact prefix.
     */
    public static final String FACT_OPPOSITE_COLORED_BISHOPS_PREFIX = FACT_PREFIX + "opposite_colored_bishops=";

    /**
     * The piece-count fact prefix.
     */
    public static final String FACT_PIECE_COUNT_PREFIX = FACT_PREFIX + PIECE_COUNT + SIDE_FIELD;

    /**
     * The pawn-structure fact prefix.
     */
    public static final String FACT_PAWN_STRUCTURE_PREFIX = FACT_PREFIX + PAWN_STRUCTURE + " type=";

    /**
     * The promotion-availability fact prefix.
     */
    public static final String FACT_PROMOTION_AVAILABLE_PREFIX = FACT_PREFIX + PROMOTION_AVAILABLE + SIDE_FIELD;

    /**
     * The tactical fact prefix.
     */
    public static final String FACT_TACTICAL_PREFIX = FACT_PREFIX + TACTICAL + "=\"";

    /**
     * The winning puzzle fact.
     */
    public static final String FACT_PUZZLE_WINNING = FACT_PREFIX + PUZZLE + "=" + WINNING;

    /**
     * The draw puzzle fact.
     */
    public static final String FACT_PUZZLE_DRAW = FACT_PREFIX + PUZZLE + "=" + DRAW;

    /**
     * The material-balance prefix.
     */
    public static final String MATERIAL_BALANCE_PREFIX = MATERIAL + COLON_SPACE + BALANCE + "=";

    /**
     * The material-imbalance prefix.
     */
    public static final String MATERIAL_IMBALANCE_PREFIX = MATERIAL + COLON_SPACE + IMBALANCE + "=";

    /**
     * The material piece-count prefix.
     */
    public static final String MATERIAL_PIECE_COUNT_PREFIX = MATERIAL + COLON_SPACE + PIECE_COUNT + SIDE_FIELD;

    /**
     * The space-side prefix.
     */
    public static final String SPACE_SIDE_PREFIX = SPACE + COLON_SPACE + SIDE + "=";

    /**
     * The development-side prefix.
     */
    public static final String DEVELOPMENT_SIDE_PREFIX = DEVELOPMENT + COLON_SPACE + SIDE + "=";

    /**
     * The mobility-side prefix.
     */
    public static final String MOBILITY_SIDE_PREFIX = MOBILITY + COLON_SPACE + SIDE + "=";

    /**
     * The initiative-side prefix.
     */
    public static final String INITIATIVE_SIDE_PREFIX = INITIATIVE + COLON_SPACE + SIDE + "=";

    /**
     * The space center-control prefix.
     */
    public static final String SPACE_CENTER_CONTROL_PREFIX = SPACE + COLON_SPACE + CENTER_CONTROL + SIDE_FIELD;

    /**
     * The undeveloped-piece field key for the DEVELOPMENT family.
     */
    public static final String UNDEVELOPED = "undeveloped";

    /**
     * The king-uncastled field key for the DEVELOPMENT family.
     */
    public static final String KING_UNCASTLED = "king_uncastled";

    /**
     * The forcing-moves field key for the INITIATIVE family.
     */
    public static final String FORCING_MOVES = "forcing_moves";

    /**
     * The restricted field key for the MOBILITY family.
     */
    public static final String RESTRICTED = "restricted";

    /**
     * The endgame type prefix.
     */
    public static final String ENDGAME_TYPE_PREFIX = ENDGAME + COLON_SPACE + TYPE + "=";

    /**
     * The opening ECO prefix.
     */
    public static final String OPENING_ECO_PREFIX = OPENING + COLON_SPACE + ECO + "=";

    /**
     * The opening name prefix.
     */
    public static final String OPENING_NAME_PREFIX = OPENING + COLON_SPACE + NAME + "=\"";

    /**
     * The king-castled prefix.
     */
    public static final String KING_CASTLED_PREFIX = KING + COLON_SPACE + CASTLED_KEY + "=";

    /**
     * The king-shelter prefix.
     */
    public static final String KING_SHELTER_PREFIX = KING + COLON_SPACE + SHELTER + "=";

    /**
     * The king-safety prefix.
     */
    public static final String KING_SAFETY_PREFIX = KING + COLON_SPACE + SAFETY + "=";

    /**
     * The pawn-islands prefix.
     */
    public static final String PAWN_ISLANDS_PREFIX = PAWN_FAMILY + COLON_SPACE + ISLANDS + SIDE_FIELD;

    /**
     * The promotion-threat prefix.
     */
    public static final String THREAT_PROMOTION_PREFIX = THREAT + COLON_SPACE + TYPE + "=" + PROMOTE + SIDE_FIELD;

    /**
     * The tactical motif prefix.
     */
    public static final String TACTIC_MOTIF_PREFIX = TACTIC + COLON_SPACE + MOTIF + "=";

    /**
     * The tactical detail field prefix.
     */
    public static final String TACTIC_DETAIL_FIELD = SPACE_TEXT + DETAIL + "=\"";

    /**
     * The piece-tier prefix.
     */
    public static final String PIECE_TIER_PREFIX = PIECE + COLON_SPACE + TIER + "=";

    /**
     * The piece-extreme prefix.
     */
    public static final String PIECE_EXTREME_PREFIX = PIECE + COLON_SPACE + EXTREME + "=";

    /**
     * The piece-activity prefix.
     */
    public static final String PIECE_ACTIVITY_PREFIX = PIECE + COLON_SPACE + ACTIVITY + "=";

    /**
     * The outpost tag prefix.
     */
    public static final String OUTPOST_TAG_PREFIX = OUTPOST + COLON_SPACE + SIDE + "=";

    /**
     * The candidate tag prefix.
     */
    public static final String CAND_PREFIX = CAND + COLON_SPACE + ROLE + "=";

    /**
     * The candidate move field.
     */
    public static final String CAND_MOVE_FIELD = SPACE_TEXT + MOVE + "=";

    /**
     * The candidate evaluation field.
     */
    public static final String CAND_EVAL_CP_FIELD = SPACE_TEXT + EVAL_CP + "=";

    /**
     * The empty candidate note field.
     */
    public static final String CAND_EMPTY_NOTE_FIELD = SPACE_TEXT + NOTE + "=\"\"";

    /**
     * The principal-variation prefix.
     */
    public static final String PV_PREFIX = PV + COLON_SPACE;

    /**
     * The move tag prefix.
     */
    public static final String MOVE_TAG_PREFIX = "move: ";

    /**
     * The best-continuation prefix.
     */
    public static final String BEST_CONTINUATION_PREFIX = "best continuation: ";

    /**
     * The captures-on prefix.
     */
    public static final String CAPTURES_ON_PREFIX = "captures on ";

    /**
     * The captures prefix.
     */
    public static final String CAPTURES_PREFIX = "captures ";

    /**
     * The en-passant capture prefix.
     */
    public static final String CAPTURES_EN_PASSANT_PREFIX = "captures en passant ";

    /**
     * The pawn suffix used in descriptions.
     */
    public static final String PAWN_SUFFIX = " pawn ";

    /**
     * The promotion-to prefix.
     */
    public static final String PROMOTION_TO_PREFIX = "promotion to ";

    /**
     * The castle word used in descriptions.
     */
    public static final String CASTLE_WORD = "castle";

    /**
     * The rook word used in descriptions.
     */
    public static final String ROOK_WORD = "rook";

    /**
     * The pinned suffix used in raw tactical text.
     */
    public static final String PINNED_SUFFIX = " pinned";

    /**
     * The trapped prefix used in raw tactical text.
     */
    public static final String TRAPPED_PREFIX = "trapped ";
     /**
     * Shared low mobility prefix constant.
     */
     public static final String LOW_MOBILITY_PREFIX = "low mobility ";

    /**
     * The high-mobility prefix used in raw tactical text.
     */
    public static final String HIGH_MOBILITY_PREFIX = "high mobility ";

    /**
     * The attacks phrase used in raw tactical text.
     */
    public static final String ATTACKS = " attacks ";

    /**
     * The forking phrase used in raw tactical text.
     */
    public static final String IS_FORKING = " is forking ";

    /**
     * The pins phrase used in raw tactical text.
     */
    public static final String PINS = " pins ";

    /**
     * The phrase used when a tactical pattern targets the king.
     */
    public static final String TO_KING = " to king";

    /**
     * The skewers phrase used in raw tactical text.
     */
    public static final String SKEWERS = " skewers ";

    /**
     * The moving prefix used in raw tactical text.
     */
    public static final String MOVING = "moving ";

    /**
     * The reveals phrase used in raw tactical text.
     */
    public static final String REVEALS = " reveals ";

    /**
     * The attack-on phrase used in raw tactical text.
     */
    public static final String ATTACK_ON = " attack on ";

    /**
     * The defends phrase used in raw tactical text.
     */
    public static final String DEFENDS = " defends ";

    /**
     * The SAN suffix that marks check.
     */
    public static final String CHECK_SUFFIX = "+";

    /**
     * The SAN suffix that marks checkmate.
     */
    public static final String CHECKMATE_SUFFIX = "#";

    /**
     * The colon character.
     */
    public static final char COLON = ':';

    /**
     * The equals character.
     */
    public static final char EQUAL_SIGN = '=';

    /**
     * The double-quote character.
     */
    public static final char QUOTE = '"';

    /**
     * The backslash character.
     */
    public static final char BACKSLASH = '\\';

    /**
     * The space character.
     */
    public static final char SPACE_CHAR = ' ';

    /**
     * The underscore character.
     */
    public static final char UNDERSCORE = '_';

    /**
     * The comma character.
     */
    public static final char COMMA = ',';

    /**
     * The file-a character.
     */
    public static final char FILE_A = 'a';

    /**
     * The dot character.
     */
    public static final char DOT = '.';

    /**
     * The opening parenthesis character.
     */
    public static final char OPEN_PAREN = '(';

    /**
     * The closing parenthesis character.
     */
    public static final char CLOSE_PAREN = ')';

    /**
     * The opening brace character.
     */
    public static final char OPEN_BRACE = '{';

    /**
     * The closing brace character.
     */
    public static final char CLOSE_BRACE = '}';

    /**
     * The opening bracket character.
     */
    public static final char OPEN_BRACKET = '[';

    /**
     * The closing bracket character.
     */
    public static final char CLOSE_BRACKET = ']';

    /**
     * The white kingside castling-right character.
     */
    public static final char WHITE_KINGSIDE_RIGHT = 'K';

    /**
     * The white queenside castling-right character.
     */
    public static final char WHITE_QUEENSIDE_RIGHT = 'Q';

    /**
     * The black kingside castling-right character.
     */
    public static final char BLACK_KINGSIDE_RIGHT = 'k';

    /**
     * The black queenside castling-right character.
     */
    public static final char BLACK_QUEENSIDE_RIGHT = 'q';

    /**
     * Prevents instantiation of this utility class.
     */
    private Literals() {
        // utility
    }
}
