package application.gui.workbench.board;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.LinkedHashMap;
import java.util.Map;
import utility.SvgPathParser;

/**
 * Vector glyphs for annotation markers whose shapes should not depend on font
 * rendering.
 *
 * <p>Every marker is described by an SVG path string (Lichess-compatible), which
 * is the single source of truth shared by the on-screen Java2D painter and the
 * SVG exporter. Filled markers (move/observation symbols) carry a {@code fill}
 * path; stroked markers (position evaluations, only-move, arrow stems) carry a
 * {@code stroke} path; combined markers (arrows, fork) carry both. The zugzwang
 * marker is the one exception drawn from concentric rings.</p>
 */
public final class AnnotationGlyphs {

    /**
     * Glyph token for the good-move marker.
     */
    public static final String EXCELLENT_MOVE = "!";

    /**
     * Glyph token for the mistake marker.
     */
    public static final String MISTAKE_MOVE = "?";

    /**
     * Glyph token for the brilliant-move marker.
     */
    public static final String BRILLIANT_MOVE = "!!";

    /**
     * Glyph token for the blunder marker.
     */
    public static final String BLUNDER_MOVE = "??";

    /**
     * Glyph token for the interesting-move marker.
     */
    public static final String INTERESTING_MOVE = "!?";

    /**
     * Glyph token for the dubious-move marker.
     */
    public static final String DUBIOUS_MOVE = "?!";

    /**
     * Glyph token for the only-move marker.
     */
    public static final String ONLY_MOVE = "□";

    /**
     * Glyph token for the zugzwang marker.
     */
    public static final String ZUGZWANG = "Zz";

    /**
     * Glyph token for the equal-position marker.
     */
    public static final String EQUAL_POSITION = "=";

    /**
     * Glyph token for the unclear-position marker.
     */
    public static final String UNCLEAR_POSITION = "∞";

    /**
     * Glyph token for the white-slightly-better marker.
     */
    public static final String WHITE_SLIGHTLY_BETTER = "±";

    /**
     * Glyph token for the black-slightly-better marker.
     */
    public static final String BLACK_SLIGHTLY_BETTER = "∓";

    /**
     * Glyph token for the white-is-better marker.
     */
    public static final String WHITE_BETTER = "+=";

    /**
     * Glyph token for the black-is-better marker.
     */
    public static final String BLACK_BETTER = "=+";

    /**
     * Glyph token for the white-is-winning marker.
     */
    public static final String WHITE_WINNING = "+-";

    /**
     * Glyph token for the black-is-winning marker.
     */
    public static final String BLACK_WINNING = "-+";

    /**
     * Glyph token for the novelty marker.
     */
    public static final String NOVELTY = "N";

    /**
     * Glyph token for the development marker.
     */
    public static final String DEVELOPMENT = "↑↑";

    /**
     * Glyph token for the initiative marker.
     */
    public static final String INITIATIVE = "↑";

    /**
     * Glyph token for the attack marker.
     */
    public static final String ATTACK = "→";

    /**
     * Glyph token for the counterplay marker.
     */
    public static final String COUNTERPLAY = "⇆";

    /**
     * Glyph token for the time-trouble marker.
     */
    public static final String TIME_TROUBLE = "⊕";

    /**
     * Glyph token for the with-compensation marker.
     */
    public static final String WITH_COMPENSATION = "=∞";

    /**
     * Glyph token for the with-the-idea marker.
     */
    public static final String WITH_IDEA = "△";

    /**
     * Glyph token for the fork marker.
     */
    public static final String FORK = "Fk";

    /**
     * Glyph token for the pin marker.
     */
    public static final String PIN = "Pin";

    /**
     * Glyph token for the book-move (opening theory / ECO) marker.
     */
    public static final String BOOK_MOVE = "Bk";

    /**
     * Glyph token for the check marker.
     */
    public static final String CHECK = "+";

    /**
     * Glyph token for the checkmate marker.
     */
    public static final String MATE = "#";

    /**
     * Glyph token for the double-check marker.
     */
    public static final String DOUBLE_CHECK = "++";

    /**
     * Glyph token for the countering (counter-plan) marker.
     */
    public static final String COUNTERING = "∇";

    /**
     * Glyph token for the missed-win marker.
     */
    public static final String MISSED_WIN = "MW";

    /**
     * Glyph token for the skewer tactic marker.
     */
    public static final String SKEWER = "Sk";

    /**
     * Glyph token for the discovered-attack tactic marker.
     */
    public static final String DISCOVERED_ATTACK = "Dsc";

    /**
     * Glyph token for the double-attack tactic marker.
     */
    public static final String DOUBLE_ATTACK = "Dbl";

    /**
     * Glyph token for the x-ray tactic marker.
     */
    public static final String XRAY = "Xr";

    /**
     * Glyph token for the battery tactic marker.
     */
    public static final String BATTERY = "Bt";

    /**
     * Glyph token for the drawn-result marker.
     */
    public static final String DRAW_RESULT = "½";

    /**
     * Glyph token for the white-checkmated marker.
     */
    public static final String WHITE_CHECKMATED = "W#";

    /**
     * Glyph token for the black-checkmated marker.
     */
    public static final String BLACK_CHECKMATED = "B#";

    /**
     * SVG path for the good-move marker in a 100x100 badge box.
     */
    public static final String EXCELLENT_MOVE_SVG_PATH =
            "M54.967 62.349h-9.75l-2.049-39.083h13.847z"
                    + "M43.004 76.032q0-3.77 2.049-5.244 2.048-1.557 4.998-1.557"
                    + " 2.867 0 4.916 1.557 2.048 1.475 2.048 5.244 0 3.605-2.048 5.244"
                    + "-2.049 1.556-4.916 1.556-2.95 0-4.998-1.556-2.049-1.64-2.049-5.244z";

    /**
     * SVG path for the mistake marker in a 100x100 badge box.
     */
    public static final String MISTAKE_MOVE_SVG_PATH =
            "M40.436 60.851q0-4.66 1.957-7.83 1.958-3.17 6.712-6.619 4.195-2.983 5.967-5.127"
                    + " 1.864-2.237 1.864-5.22 0-2.983-2.237-4.475-2.144-1.585-6.06-1.585"
                    + "-3.915 0-7.737 1.212t-7.83 3.263l-4.941-9.975q4.568-2.517 9.881-4.101"
                    + " 5.314-1.585 11.653-1.585 9.695 0 15.008 4.661 5.407 4.661 5.407 11.839"
                    + " 0 3.822-1.212 6.619-1.212 2.796-3.635 5.22-2.424 2.33-6.06 5.034"
                    + "-2.703 1.958-4.195 3.356-1.491 1.398-2.05 2.703-.467 1.305-.467 3.263v2.703H40.436z"
                    + "m-1.492 18.924q0-4.288 2.33-5.966 2.331-1.771 5.687-1.771 3.263 0 5.594 1.771"
                    + " 2.33 1.678 2.33 5.966 0 4.102-2.33 5.966-2.331 1.772-5.594 1.772"
                    + "-3.356 0-5.686-1.772-2.33-1.864-2.33-5.966z";

    /**
     * SVG path for the brilliant-move marker in a 100x100 badge box.
     */
    public static final String BRILLIANT_MOVE_SVG_PATH =
            "M71.967 62.349h-9.75l-2.049-39.083h13.847z"
                    + "M60.004 76.032q0-3.77 2.049-5.244 2.048-1.557 4.998-1.557"
                    + " 2.867 0 4.916 1.557 2.048 1.475 2.048 5.244 0 3.605-2.048 5.244"
                    + "-2.049 1.556-4.916 1.556-2.95 0-4.998-1.556-2.049-1.64-2.049-5.244z"
                    + "M37.967 62.349h-9.75l-2.049-39.083h13.847z"
                    + "M26.004 76.032q0-3.77 2.049-5.244 2.048-1.557 4.998-1.557"
                    + " 2.867 0 4.916 1.557 2.048 1.475 2.048 5.244 0 3.605-2.048 5.244"
                    + "-2.049 1.556-4.916 1.556-2.95 0-4.998-1.556-2.049-1.64-2.049-5.244z";

    /**
     * SVG path for the blunder marker in a 100x100 badge box.
     */
    public static final String BLUNDER_MOVE_SVG_PATH =
            "M31.8 22.22c-3.675 0-7.052.46-10.132 1.38-3.08.918-5.945 2.106-8.593 3.565"
                    + "l4.298 8.674c2.323-1.189 4.592-2.136 6.808-2.838a22.138 22.138 0 0 1 6.728-1.053"
                    + "c2.27 0 4.025.46 5.268 1.378 1.297.865 1.946 2.16 1.946 3.89s-.541 3.242-1.622 4.539"
                    + "c-1.027 1.243-2.756 2.73-5.188 4.458-2.756 2-4.7 3.918-5.836 5.755"
                    + "-1.134 1.837-1.702 4.107-1.702 6.808v2.92h10.457v-2.35c0-1.135.135-2.082.406-2.839"
                    + ".324-.756.918-1.54 1.783-2.35.864-.81 2.079-1.784 3.646-2.918 2.107-1.568 3.863-3.026"
                    + " 5.268-4.376 1.405-1.405 2.46-2.92 3.162-4.541.703-1.621 1.054-3.54 1.054-5.755"
                    + " 0-4.161-1.568-7.592-4.702-10.294-3.08-2.702-7.43-4.052-13.05-4.052z"
                    + "m38.664 0c-3.675 0-7.053.46-10.133 1.38-3.08.918-5.944 2.106-8.591 3.565"
                    + "l4.295 8.674c2.324-1.189 4.593-2.136 6.808-2.838a22.138 22.138 0 0 1 6.728-1.053"
                    + "c2.27 0 4.026.46 5.269 1.378 1.297.865 1.946 2.16 1.946 3.89s-.54 3.242-1.62 4.539"
                    + "c-1.027 1.243-2.757 2.73-5.189 4.458-2.756 2-4.7 3.918-5.835 5.755"
                    + "-1.135 1.837-1.703 4.107-1.703 6.808v2.92h10.457v-2.35c0-1.135.134-2.082.404-2.839"
                    + ".324-.756.918-1.54 1.783-2.35.865-.81 2.081-1.784 3.648-2.918 2.108-1.568 3.864-3.026"
                    + " 5.269-4.376 1.405-1.405 2.46-2.92 3.162-4.541.702-1.621 1.053-3.54 1.053-5.755"
                    + " 0-4.161-1.567-7.592-4.702-10.294-3.08-2.702-7.43-4.052-13.05-4.052z"
                    + "M29.449 68.504c-1.945 0-3.593.513-4.944 1.54-1.351.973-2.027 2.703-2.027 5.188"
                    + " 0 2.378.676 4.108 2.027 5.188 1.35 1.027 3 1.54 4.944 1.54 1.892 0 3.512-.513 4.863-1.54"
                    + " 1.35-1.08 2.026-2.81 2.026-5.188 0-2.485-.675-4.215-2.026-5.188"
                    + "-1.351-1.027-2.971-1.54-4.863-1.54z"
                    + "m38.663 0c-1.945 0-3.592.513-4.943 1.54-1.35.973-2.026 2.703-2.026 5.188"
                    + " 0 2.378.675 4.108 2.026 5.188 1.351 1.027 2.998 1.54 4.943 1.54 1.891 0 3.513-.513 4.864-1.54"
                    + " 1.351-1.08 2.027-2.81 2.027-5.188 0-2.485-.676-4.215-2.027-5.188"
                    + "-1.35-1.027-2.973-1.54-4.864-1.54z";

    /**
     * SVG path for the interesting-move marker in a 100x100 badge box.
     */
    public static final String INTERESTING_MOVE_SVG_PATH =
            "M60.823 58.9q0-4.098 1.72-6.883 1.721-2.786 5.9-5.818 3.687-2.622 5.243-4.506"
                    + " 1.64-1.966 1.64-4.588t-1.967-3.933q-1.885-1.393-5.326-1.393t-6.8 1.065"
                    + "q-3.36 1.065-6.883 2.868l-4.343-8.767q4.015-2.212 8.685-3.605 4.67-1.393 10.242-1.393"
                    + " 8.521 0 13.192 4.097 4.752 4.096 4.752 10.405 0 3.36-1.065 5.818-1.066 2.458-3.196 4.588"
                    + "-2.13 2.048-5.326 4.424-2.376 1.72-3.687 2.95-1.31 1.229-1.802 2.376-.41 1.147-.41 2.868"
                    + "v2.376h-10.57z"
                    + "m-1.311 16.632q0-3.77 2.048-5.244 2.049-1.557 4.998-1.557 2.868 0 4.916 1.557"
                    + " 2.049 1.475 2.049 5.244 0 3.605-2.049 5.244-2.048 1.556-4.916 1.556-2.95 0-4.998-1.556"
                    + "-2.048-1.64-2.048-5.244z"
                    + "M36.967 61.849h-9.75l-2.049-39.083h13.847z"
                    + "M25.004 75.532q0-3.77 2.049-5.244 2.048-1.557 4.998-1.557 2.867 0 4.916 1.557"
                    + " 2.048 1.475 2.048 5.244 0 3.605-2.048 5.244-2.049 1.556-4.916 1.556-2.95 0-4.998-1.556"
                    + "-2.049-1.64-2.049-5.244z";

    /**
     * SVG path for the dubious-move marker in a 100x100 badge box.
     */
    public static final String DUBIOUS_MOVE_SVG_PATH =
            "M37.734 21.947c-3.714 0-7.128.464-10.242 1.393-3.113.928-6.009 2.13-8.685 3.605"
                    + "l4.343 8.766c2.35-1.202 4.644-2.157 6.883-2.867a22.366 22.366 0 0 1 6.799-1.065"
                    + "c2.294 0 4.07.464 5.326 1.393 1.311.874 1.967 2.186 1.967 3.933 0 1.748-.546 3.277-1.639 4.588"
                    + "-1.038 1.257-2.786 2.758-5.244 4.506-2.786 2.021-4.751 3.961-5.898 5.819"
                    + "-1.147 1.857-1.721 4.15-1.721 6.88v2.952h10.568v-2.377c0-1.147.137-2.103.41-2.868"
                    + ".328-.764.93-1.557 1.803-2.376.874-.82 2.104-1.803 3.688-2.95 2.13-1.584 3.906-3.058 5.326-4.424"
                    + " 1.42-1.42 2.485-2.95 3.195-4.59.71-1.638 1.065-3.576 1.065-5.816 0-4.206-1.584-7.675-4.752-10.406"
                    + "-3.114-2.731-7.51-4.096-13.192-4.096z"
                    + "m24.745.819 2.048 39.084h9.75l2.047-39.084z"
                    + "M35.357 68.73c-1.966 0-3.632.52-4.998 1.557-1.365.983-2.047 2.732-2.047 5.244"
                    + " 0 2.404.682 4.152 2.047 5.244 1.366 1.038 3.032 1.557 4.998 1.557 1.912 0 3.55-.519 4.916-1.557"
                    + " 1.366-1.092 2.05-2.84 2.05-5.244 0-2.512-.684-4.26-2.05-5.244-1.365-1.038-3.004-1.557-4.916-1.557z"
                    + "m34.004 0c-1.966 0-3.632.52-4.998 1.557-1.365.983-2.049 2.732-2.049 5.244"
                    + " 0 2.404.684 4.152 2.05 5.244 1.365 1.038 3.03 1.557 4.997 1.557 1.912 0 3.55-.519 4.916-1.557"
                    + " 1.366-1.092 2.047-2.84 2.047-5.244 0-2.512-.681-4.26-2.047-5.244-1.365-1.038-3.004-1.557-4.916-1.557z";

    /**
     * SVG path for the novelty marker in a 100x100 badge box.
     */
    public static final String NOVELTY_SVG_PATH =
            "M21.7 85.7V14.3h10.4l38.1 59.1h.4q-.1-1.6-.25-4.8-.15-3.2-.3-7t-.15-7V14.3h8.4v71.4"
                    + "H67.8L29.6 26.4h-.4q.3 3.5.55 8.7.25 5.2.25 10.7v39.9h-8.3z";

    /**
     * SVG path for the with-the-idea triangle marker in a 100x100 badge box.
     */
    public static final String WITH_IDEA_SVG_PATH =
            "M22.95 85.7v-5.5l22.5-65.9h9l22.6 66v5.4h-54.1"
                    + "m9.6-7.9h34.6l-12.5-37.2q-3.3-9.9-4.8-16.5-1.3 4.9-2.4 8.9-1.1 4-2.2 7.2l-12.7 37.6z";

    /**
     * SVG path for the pin marker in a 100x100 badge box.
     */
    public static final String PIN_SVG_PATH =
            "M31 21V28L37 30 34 47 27 50V57H45V77L50 86 55 77V57H73V50L66 47 63 30 69 28V21Z";

    /**
     * SVG path for the book-move open-book marker in a 100x100 badge box.
     */
    public static final String BOOK_MOVE_SVG_PATH =
            "M47 32C38 27 24 26 14 30L14 66C24 62 38 63 47 68Z"
                    + "M53 32C62 27 76 26 86 30L86 66C76 62 62 63 53 68Z";

    /**
     * SVG path for the check (plus) marker in a 100x100 badge box.
     */
    public static final String CHECK_SVG_PATH = "M50 24V76M24 50H76";

    /**
     * SVG path for the checkmate (hash) marker in a 100x100 badge box.
     */
    public static final String MATE_SVG_PATH = "M40 24V76M60 24V76M22 40H78M22 60H78";

    /**
     * SVG path for the missed-win (cross) marker in a 100x100 badge box.
     */
    public static final String MISSED_WIN_SVG_PATH = "M30 30L70 70M70 30L30 70";

    /**
     * SVG path for the double-check (two plus signs) marker in a 100x100 badge box.
     */
    public static final String DOUBLE_CHECK_SVG_PATH = "M30 38V62M18 50H42M70 38V62M58 50H82";

    /**
     * SVG path for the countering down-triangle marker in a 100x100 badge box.
     */
    public static final String COUNTERING_SVG_PATH = "M19 32L81 32L50 86Z";

    /**
     * Filled SVG path for the skewer's shaft, threaded piece and needle point in a 100x100 badge box.
     *
     * <p>Drawn on the diagonal: a bold shaft runs from the eye handle down through a single threaded
     * food piece (the skewered piece) and tapers to a sharp needle point. The tip is a narrow tool
     * spike — deliberately NOT the wide attack arrowhead — and sits inside the badge circle so it is
     * never clipped.</p>
     */
    public static final String SKEWER_FILL_SVG_PATH =
            "M31.5 36.5L36.5 31.5L69 64L64 69Z"
                    + "M63 49L79 65L65 79L49 63Z"
                    + "M70.5 62.5L82 82L62.5 70.5Z";

    /**
     * Stroked SVG path for the skewer's loop/eye handle in a 100x100 badge box.
     *
     * <p>Styled as an actual skewer (like the pin glyph is an actual pushpin): the hollow ring at the
     * top-left is the eye handle the shaft fairs out of.</p>
     */
    public static final String SKEWER_STROKE_SVG_PATH =
            "M27 27 m-10 0 a10 10 0 1 0 20 0 a10 10 0 1 0 -20 0";

    /**
     * Filled SVG path for the x-ray allied endpoint pieces in a 100x100 badge box.
     *
     * <p>The two solid endpoint circles are the two allied pieces, equal in size because the X-ray
     * relation is mutual defense, not a forcing attack against a front/back target pair.</p>
     */
    public static final String XRAY_FILL_SVG_PATH =
            "M6 50a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z"
                    + "M74 50a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z";

    /**
     * Stroked SVG path for the x-ray through-connection and obstructing enemy piece in a 100x100
     * badge box.
     *
     * <p>The horizontal line shows the two allied pieces connected through the obstruction; the
     * hollow center ring is the enemy piece between them. There is deliberately no arrowhead so it
     * reads as a through-defense, not a skewer, attack, pin, or battery.</p>
     */
    public static final String XRAY_STROKE_SVG_PATH =
            "M16 50H38"
                    + "M62 50H84"
                    + "M38 50a12 12 0 1 0 24 0a12 12 0 1 0 -24 0Z";

    /**
     * Filled SVG path for the double-attack source piece and fixed arrowheads in a 100x100 badge box.
     *
     * <p>The filled dot is the single attacking piece; the upward and rightward triangular arrowheads
     * are its two simultaneous threats. It deliberately avoids target markers — it should read as
     * "one piece, two threats", not a full board diagram, and stays distinct from the Fork's split.</p>
     */
    public static final String DOUBLE_ATTACK_FILL_SVG_PATH =
            "M22 70a8 8 0 1 0 16 0a8 8 0 1 0 -16 0Z"
                    + "M30 14L17 30L43 30Z"
                    + "M90 70L74 57L74 83Z";

    /**
     * Stroked SVG path for the two double-attack rays in a 100x100 badge box.
     *
     * <p>The vertical ray rises from the top edge of the source dot; the horizontal ray runs from its
     * right edge. Both use the same stroke weight so the two threats feel equal.</p>
     */
    public static final String DOUBLE_ATTACK_STROKE_SVG_PATH = "M30 62V28M38 70H76";

    /**
     * Filled SVG path for the battery pieces and arrowhead in a 100x100 badge box.
     */
    public static final String BATTERY_FILL_SVG_PATH =
            "M12 50a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z"
                    + "M34 50a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z"
                    + "M94 50L78 37L78 63Z";

    /**
     * Stroked SVG path for the battery firing line in a 100x100 badge box.
     */
    public static final String BATTERY_STROKE_SVG_PATH = "M44 50H84";

    /**
     * Filled SVG path for the discovered-attack source piece, moved blocker, and fixed attack
     * arrowhead in a 100x100 badge box.
     *
     * <p>The left dot is the hidden attacking piece, the upper dot is the piece that moved out of the
     * attack line, and the triangular arrowhead is the newly revealed attack. It deliberately avoids a
     * target ring, ghost circle, dotted trail, and second movement arrow so it reads at small sizes.</p>
     */
    public static final String DISCOVERED_ATTACK_FILL_SVG_PATH =
            "M12 50a8 8 0 1 0 16 0a8 8 0 1 0 -16 0Z"
                    + "M43 31a7 7 0 1 0 14 0a7 7 0 1 0 -14 0Z"
                    + "M78 37L94 50L78 63Z";

    /**
     * Stroked SVG path for the discovered-attack revealed line and simple move-away cue in a 100x100
     * badge box.
     *
     * <p>The horizontal stroke is the uncovered attack line; the short vertical stroke points from the
     * old blocking square toward the moved piece. The cue is plain so the attack ray stays dominant.</p>
     */
    public static final String DISCOVERED_ATTACK_STROKE_SVG_PATH = "M20 50H84M50 50V40";

    /**
     * SVG path for the only-move rectangle marker in a 100x100 badge box.
     */
    public static final String ONLY_MOVE_SVG_PATH = "M30,30 H70 V70 H30 Z";

    /**
     * SVG path for the equal-position marker in a 100x100 badge box.
     */
    public static final String EQUAL_POSITION_SVG_PATH = "M27 40h46M27 60h46";

    /**
     * SVG path for the unclear-position marker in a 100x100 badge box.
     */
    public static final String UNCLEAR_POSITION_SVG_PATH =
            "M40 40A14.14 14.14 0 1 0 40 60L60 40A14.14 14.14 0 1 1 60 60L40 40";

    /**
     * SVG path for the white-slightly-better marker in a 100x100 badge box.
     */
    public static final String WHITE_SLIGHTLY_BETTER_SVG_PATH = "M50 51V5M27 28h46M27 64h46M27 78h46";

    /**
     * SVG path for the black-slightly-better marker in a 100x100 badge box.
     */
    public static final String BLACK_SLIGHTLY_BETTER_SVG_PATH = "M50 49v46M27 72h46M27 36h46M27 22h46";

    /**
     * SVG path for the white-is-better marker in a 100x100 badge box.
     */
    public static final String WHITE_BETTER_SVG_PATH = "M50 59V13M27 36h46M27 72h46";

    /**
     * SVG path for the black-is-better marker in a 100x100 badge box.
     */
    public static final String BLACK_BETTER_SVG_PATH = "M50 41v46M27 64h46M27 28h46";

    /**
     * SVG path for the white-is-winning marker in a 100x100 badge box.
     */
    public static final String WHITE_WINNING_SVG_PATH = "M29 27v46M6 50h46m8 0h36";

    /**
     * SVG path for the black-is-winning marker in a 100x100 badge box.
     */
    public static final String BLACK_WINNING_SVG_PATH = "M71 27v46m23-23H48m-8 0H4";

    /**
     * SVG path for the time-trouble ring-and-cross marker in a 100x100 badge box.
     */
    public static final String TIME_TROUBLE_SVG_PATH =
            "M25 50A25 25 0 1 0 75 50A25 25 0 1 0 25 50M50 25v50M25 50h50";

    /**
     * SVG path for the with-compensation marker in a 100x100 badge box.
     */
    public static final String WITH_COMPENSATION_SVG_PATH =
            "M24 24h52M24 42h52"
                    + "M42.8 61.8C38.82 57.83 32.38 57.83 28.4 61.8C24.43 65.78 24.43 72.22 28.4 76.2"
                    + "C32.38 80.17 38.82 80.17 42.8 76.2L57.2 61.8C61.18 57.83 67.62 57.83 71.6 61.8"
                    + "C75.57 65.78 75.57 72.22 71.6 76.2C67.62 80.17 61.18 80.17 57.2 76.2L42.8 61.8";

    /**
     * Filled SVG path for the development arrowheads in a 100x100 badge box.
     */
    public static final String DEVELOPMENT_FILL_SVG_PATH =
            "M32 14L19 30L45 30Z"
                    + "M68 14L55 30L81 30Z";

    /**
     * Stroked SVG path for the development arrow stems in a 100x100 badge box.
     */
    public static final String DEVELOPMENT_STROKE_SVG_PATH = "M32 86V28M68 86V28";

    /**
     * Filled SVG path for the initiative arrowhead in a 100x100 badge box.
     */
    public static final String INITIATIVE_FILL_SVG_PATH = "M50 14L37 30L63 30Z";

    /**
     * Stroked SVG path for the initiative arrow stem in a 100x100 badge box.
     */
    public static final String INITIATIVE_STROKE_SVG_PATH = "M50 86V28";

    /**
     * Filled SVG path for the attack arrowhead in a 100x100 badge box.
     */
    public static final String ATTACK_FILL_SVG_PATH = "M94 50L78 37L78 63Z";

    /**
     * Stroked SVG path for the attack arrow shaft in a 100x100 badge box.
     */
    public static final String ATTACK_STROKE_SVG_PATH = "M14 50H80";

    /**
     * Filled SVG path for the counterplay arrowheads in a 100x100 badge box.
     */
    public static final String COUNTERPLAY_FILL_SVG_PATH =
            "M88 34L72 21L72 47Z"
                    + "M12 66L28 53L28 79Z";

    /**
     * Stroked SVG path for the counterplay lanes in a 100x100 badge box.
     */
    public static final String COUNTERPLAY_STROKE_SVG_PATH = "M22 34H74M26 66H78";

    /**
     * Filled SVG path for the fork arrowheads in a 100x100 badge box.
     */
    public static final String FORK_FILL_SVG_PATH =
            "M25 14L12 30L38 30Z"
                    + "M75 14L62 30L88 30Z";

    /**
     * Stroked SVG path for the fork stem and branches in a 100x100 badge box.
     */
    public static final String FORK_STROKE_SVG_PATH = "M50 84V59M25 24V59H75V24";

    /**
     * SVG path for the drawn-result marker in a 100x100 badge box.
     */
    public static final String DRAW_RESULT_SVG_PATH =
            "M37.77 79.03L29.37 79.03L64.38 20.97L72.77 20.97L37.77 79.03Z"
                    + "M72.14 71.64L86 71.64L86 77.94L60.78 77.94L60.78 72.2L72.51 62.24"
                    + "Q74.79 60.25 75.76 58.75Q76.74 57.25 76.74 55.75Q76.74 53.88 75.38 52.72"
                    + "Q74.01 51.55 71.79 51.55Q69.48 51.55 66.81 52.4Q64.15 53.24 61 55.01L61 48.04"
                    + "Q64.26 47.02 67.32 46.52Q70.37 46.01 73.18 46.01Q79.1 46.01 82.47 48.44"
                    + "Q85.84 50.85 85.84 55.01Q85.84 57.7 84.51 59.97Q83.18 62.24 79.4 65.46L72.14 71.64Z"
                    + "M14.68 47.02L22.44 47.02L22.44 27.5L14 29.44L14 23.44L22.59 21.61L31.05 21.61"
                    + "L31.05 47.02L38.7 47.02L38.7 52.9L14.68 52.9L14.68 47.02Z";

    /**
     * SVG path for the white-checkmated marker in a 100x100 badge box.
     */
    public static final String WHITE_CHECKMATED_SVG_PATH =
            "M40 24V76M60 24V76M22 40H78M22 60H78";

    /**
     * SVG path for the black-checkmated marker in a 100x100 badge box.
     *
     * <p>Renders the same checkmate hash as {@link #WHITE_CHECKMATED_SVG_PATH};
     * the stroke segments are listed in a different order only so each glyph's
     * exported path remains a distinct, separately locatable string.</p>
     */
    public static final String BLACK_CHECKMATED_SVG_PATH =
            "M22 40H78M22 60H78M40 24V76M60 24V76";

    /**
     * Radius of the zugzwang outer ring in the 100x100 badge box. Chosen so the
     * stroked ring's outer edge sits flush with the badge edge (radius + half the
     * 7-wide stroke = 50), leaving no badge color visible outside the white ring.
     */
    public static final double ZUGZWANG_OUTER_RADIUS = 46.5;

    /**
     * Radius of the zugzwang filled inner dot in the 100x100 badge box.
     */
    public static final double ZUGZWANG_INNER_RADIUS = 7.0;

    /**
     * Default vector stroke width in the 100x100 badge coordinate system.
     */
    private static final float DEFAULT_STROKE_WIDTH = 7f;

    /**
     * Heavier vector stroke width used by the black-is-winning marker.
     */
    private static final float HEAVY_STROKE_WIDTH = 8f;

    /**
     * Registry of custom vector markers keyed by glyph token.
     */
    private static final Map<String, Mark> MARKS = buildMarks();

    /**
     * Cached parsed fill paths keyed by glyph token.
     */
    private static final Map<String, Path2D.Double> FILL_PATHS = new LinkedHashMap<>();

    /**
     * Cached parsed stroke paths keyed by glyph token.
     */
    private static final Map<String, Path2D.Double> STROKE_PATHS = new LinkedHashMap<>();

    static {
        for (Map.Entry<String, Mark> entry : MARKS.entrySet()) {
            Mark mark = entry.getValue();
            if (mark.fillPath() != null) {
                int rule = mark.fillEvenOdd() ? Path2D.WIND_EVEN_ODD : Path2D.WIND_NON_ZERO;
                FILL_PATHS.put(entry.getKey(), SvgPathParser.parse(mark.fillPath(), rule));
            }
            if (mark.strokePath() != null) {
                STROKE_PATHS.put(entry.getKey(), SvgPathParser.parse(mark.strokePath()));
            }
        }
    }

    /**
     * Prevents instantiation.
     */
    private AnnotationGlyphs() {
        // utility
    }

    /**
     * One custom vector marker.
     *
     * @param fillPath filled SVG sub-path, or {@code null}
     * @param strokePath stroked SVG sub-path, or {@code null}
     * @param strokeWidth stroke width in the 100x100 badge coordinate system
     * @param roundCap true when the stroked path uses round caps and joins
     * @param fillEvenOdd true when the filled path uses the even-odd winding rule
     */
    private record Mark(String fillPath, String strokePath, float strokeWidth, boolean roundCap,
            boolean fillEvenOdd) {
    }

    /**
     * Builds the immutable registry of custom vector markers.
     *
     * @return marker registry keyed by glyph token
     */
    private static Map<String, Mark> buildMarks() {
        Map<String, Mark> marks = new LinkedHashMap<>();
        marks.put(EXCELLENT_MOVE, fill(EXCELLENT_MOVE_SVG_PATH));
        marks.put(MISTAKE_MOVE, fill(MISTAKE_MOVE_SVG_PATH));
        marks.put(BRILLIANT_MOVE, fill(BRILLIANT_MOVE_SVG_PATH));
        marks.put(BLUNDER_MOVE, fill(BLUNDER_MOVE_SVG_PATH));
        marks.put(INTERESTING_MOVE, fill(INTERESTING_MOVE_SVG_PATH));
        marks.put(DUBIOUS_MOVE, fill(DUBIOUS_MOVE_SVG_PATH));
        marks.put(NOVELTY, fill(NOVELTY_SVG_PATH));
        marks.put(WITH_IDEA, fillEvenOddMark(WITH_IDEA_SVG_PATH));
        marks.put(PIN, fill(PIN_SVG_PATH));
        marks.put(BOOK_MOVE, fill(BOOK_MOVE_SVG_PATH));
        marks.put(CHECK, strokeRound(CHECK_SVG_PATH, 11f));
        marks.put(MATE, stroke(MATE_SVG_PATH, DEFAULT_STROKE_WIDTH));
        marks.put(DOUBLE_CHECK, strokeRound(DOUBLE_CHECK_SVG_PATH, 9f));
        marks.put(COUNTERING, stroke(COUNTERING_SVG_PATH, DEFAULT_STROKE_WIDTH));
        marks.put(MISSED_WIN, strokeRound(MISSED_WIN_SVG_PATH, 11f));
        marks.put(SKEWER, new Mark(SKEWER_FILL_SVG_PATH, SKEWER_STROKE_SVG_PATH, DEFAULT_STROKE_WIDTH, true, false));
        marks.put(XRAY, new Mark(XRAY_FILL_SVG_PATH, XRAY_STROKE_SVG_PATH, DEFAULT_STROKE_WIDTH, false, false));
        marks.put(DOUBLE_ATTACK, new Mark(DOUBLE_ATTACK_FILL_SVG_PATH, DOUBLE_ATTACK_STROKE_SVG_PATH,
                DEFAULT_STROKE_WIDTH, false, false));
        marks.put(BATTERY, new Mark(BATTERY_FILL_SVG_PATH, BATTERY_STROKE_SVG_PATH, DEFAULT_STROKE_WIDTH, false, false));
        marks.put(DISCOVERED_ATTACK, new Mark(DISCOVERED_ATTACK_FILL_SVG_PATH, DISCOVERED_ATTACK_STROKE_SVG_PATH,
                DEFAULT_STROKE_WIDTH, false, false));
        marks.put(ONLY_MOVE, stroke(ONLY_MOVE_SVG_PATH, DEFAULT_STROKE_WIDTH));
        marks.put(EQUAL_POSITION, stroke(EQUAL_POSITION_SVG_PATH, DEFAULT_STROKE_WIDTH));
        marks.put(UNCLEAR_POSITION, stroke(UNCLEAR_POSITION_SVG_PATH, DEFAULT_STROKE_WIDTH));
        marks.put(WHITE_SLIGHTLY_BETTER, stroke(WHITE_SLIGHTLY_BETTER_SVG_PATH, DEFAULT_STROKE_WIDTH));
        marks.put(BLACK_SLIGHTLY_BETTER, stroke(BLACK_SLIGHTLY_BETTER_SVG_PATH, DEFAULT_STROKE_WIDTH));
        marks.put(WHITE_BETTER, stroke(WHITE_BETTER_SVG_PATH, DEFAULT_STROKE_WIDTH));
        marks.put(BLACK_BETTER, stroke(BLACK_BETTER_SVG_PATH, DEFAULT_STROKE_WIDTH));
        marks.put(WHITE_WINNING, stroke(WHITE_WINNING_SVG_PATH, DEFAULT_STROKE_WIDTH));
        marks.put(BLACK_WINNING, stroke(BLACK_WINNING_SVG_PATH, HEAVY_STROKE_WIDTH));
        marks.put(TIME_TROUBLE, stroke(TIME_TROUBLE_SVG_PATH, DEFAULT_STROKE_WIDTH));
        marks.put(WITH_COMPENSATION, stroke(WITH_COMPENSATION_SVG_PATH, DEFAULT_STROKE_WIDTH));
        marks.put(DRAW_RESULT, fill(DRAW_RESULT_SVG_PATH));
        marks.put(WHITE_CHECKMATED, stroke(WHITE_CHECKMATED_SVG_PATH, DEFAULT_STROKE_WIDTH));
        marks.put(BLACK_CHECKMATED, stroke(BLACK_CHECKMATED_SVG_PATH, DEFAULT_STROKE_WIDTH));
        marks.put(DEVELOPMENT,
                new Mark(DEVELOPMENT_FILL_SVG_PATH, DEVELOPMENT_STROKE_SVG_PATH, DEFAULT_STROKE_WIDTH, false, false));
        marks.put(INITIATIVE,
                new Mark(INITIATIVE_FILL_SVG_PATH, INITIATIVE_STROKE_SVG_PATH, DEFAULT_STROKE_WIDTH, false, false));
        marks.put(ATTACK, new Mark(ATTACK_FILL_SVG_PATH, ATTACK_STROKE_SVG_PATH, DEFAULT_STROKE_WIDTH, false, false));
        marks.put(COUNTERPLAY,
                new Mark(COUNTERPLAY_FILL_SVG_PATH, COUNTERPLAY_STROKE_SVG_PATH, DEFAULT_STROKE_WIDTH, false, false));
        marks.put(FORK, new Mark(FORK_FILL_SVG_PATH, FORK_STROKE_SVG_PATH, DEFAULT_STROKE_WIDTH, false, false));
        return marks;
    }

    /**
     * Creates a fill-only marker.
     *
     * @param fillPath filled SVG sub-path
     * @return fill marker
     */
    private static Mark fill(String fillPath) {
        return new Mark(fillPath, null, 0f, false, false);
    }

    /**
     * Creates a fill-only marker that uses the even-odd winding rule so inner
     * sub-paths cut holes (e.g. a hollow triangle outline).
     *
     * @param fillPath filled SVG sub-path
     * @return even-odd fill marker
     */
    private static Mark fillEvenOddMark(String fillPath) {
        return new Mark(fillPath, null, 0f, false, true);
    }

    /**
     * Creates a stroke-only marker.
     *
     * @param strokePath stroked SVG sub-path
     * @param strokeWidth stroke width
     * @return stroke marker
     */
    private static Mark stroke(String strokePath, float strokeWidth) {
        return new Mark(null, strokePath, strokeWidth, false, false);
    }

    /**
     * Creates a stroke-only marker with round caps and joins.
     *
     * @param strokePath stroked SVG sub-path
     * @param strokeWidth stroke width
     * @return round-capped stroke marker
     */
    private static Mark strokeRound(String strokePath, float strokeWidth) {
        return new Mark(null, strokePath, strokeWidth, true, false);
    }

    /**
     * Returns true when the glyph has a custom vector form.
     *
     * @param glyph annotation glyph token
     * @return true if the glyph has a custom vector form
     */
    public static boolean isCustom(String glyph) {
        return ZUGZWANG.equals(glyph) || MARKS.containsKey(glyph);
    }

    /**
     * Returns the filled SVG sub-path for a glyph, or {@code null} when it has no
     * fill component.
     *
     * @param glyph annotation glyph token
     * @return filled SVG sub-path, or {@code null}
     */
    public static String fillPath(String glyph) {
        Mark mark = MARKS.get(glyph);
        return mark == null ? null : mark.fillPath();
    }

    /**
     * Returns the stroked SVG sub-path for a glyph, or {@code null} when it has no
     * stroke component.
     *
     * @param glyph annotation glyph token
     * @return stroked SVG sub-path, or {@code null}
     */
    public static String strokePath(String glyph) {
        Mark mark = MARKS.get(glyph);
        return mark == null ? null : mark.strokePath();
    }

    /**
     * Returns whether a glyph's stroked sub-path uses round caps and joins.
     *
     * @param glyph annotation glyph token
     * @return true when the stroke uses round caps and joins
     */
    public static boolean strokeRoundCap(String glyph) {
        Mark mark = MARKS.get(glyph);
        return mark != null && mark.roundCap();
    }

    /**
     * Returns whether a glyph's filled sub-path uses the even-odd winding rule.
     *
     * @param glyph annotation glyph token
     * @return true when the fill uses the even-odd winding rule
     */
    public static boolean fillEvenOdd(String glyph) {
        Mark mark = MARKS.get(glyph);
        return mark != null && mark.fillEvenOdd();
    }

    /**
     * Returns the vector-space stroke width for a glyph's stroked sub-path.
     *
     * @param glyph annotation glyph token
     * @return stroke width in the 100x100 badge coordinate system
     */
    public static float vectorStrokeWidth(String glyph) {
        if (ZUGZWANG.equals(glyph)) {
            return DEFAULT_STROKE_WIDTH;
        }
        Mark mark = MARKS.get(glyph);
        return mark == null ? 0f : mark.strokeWidth();
    }

    /**
     * Returns a fully opaque copy of a color, so glyph badges never render
     * translucent regardless of the supplied brush alpha.
     *
     * @param color source color
     * @return opaque color
     */
    private static Color opaque(Color color) {
        return color.getAlpha() == 255 ? color : new Color(color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Paints a custom vector badge using the provided badge colors. The badge
     * and its mark are always rendered fully opaque.
     *
     * @param g graphics context
     * @param glyph annotation glyph token
     * @param x badge x coordinate
     * @param y badge y coordinate
     * @param diameter badge diameter
     * @param fill badge fill color
     * @param mark glyph and border color
     * @param borderWidth border width in pixels
     */
    public static void paintCustom(Graphics2D g, String glyph, int x, int y, int diameter,
            Color fill, Color mark, float borderWidth) {
        AffineTransform savedTransform = g.getTransform();
        Stroke savedStroke = g.getStroke();
        Color savedColor = g.getColor();
        Color opaqueFill = opaque(fill);
        Color opaqueMark = opaque(mark);
        try {
            g.setColor(opaqueFill);
            g.fillOval(x, y, diameter, diameter);
            g.translate(x, y);
            g.scale(diameter / 100.0, diameter / 100.0);
            g.setColor(opaqueMark);
            if (ZUGZWANG.equals(glyph)) {
                g.setStroke(new BasicStroke(vectorStrokeWidth(glyph), BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                g.draw(new Ellipse2D.Double(50 - ZUGZWANG_OUTER_RADIUS, 50 - ZUGZWANG_OUTER_RADIUS,
                        ZUGZWANG_OUTER_RADIUS * 2, ZUGZWANG_OUTER_RADIUS * 2));
                g.fill(new Ellipse2D.Double(50 - ZUGZWANG_INNER_RADIUS, 50 - ZUGZWANG_INNER_RADIUS,
                        ZUGZWANG_INNER_RADIUS * 2, ZUGZWANG_INNER_RADIUS * 2));
                return;
            }
            Path2D.Double fillPath = FILL_PATHS.get(glyph);
            if (fillPath != null) {
                g.fill(fillPath);
            }
            Path2D.Double strokePath = STROKE_PATHS.get(glyph);
            if (strokePath != null) {
                boolean round = strokeRoundCap(glyph);
                g.setStroke(new BasicStroke(vectorStrokeWidth(glyph),
                        round ? BasicStroke.CAP_ROUND : BasicStroke.CAP_BUTT,
                        round ? BasicStroke.JOIN_ROUND : BasicStroke.JOIN_MITER));
                g.draw(strokePath);
            }
        } finally {
            g.setTransform(savedTransform);
            g.setStroke(savedStroke);
            g.setColor(savedColor);
        }
    }
}
