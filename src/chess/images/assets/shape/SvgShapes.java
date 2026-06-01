package chess.images.assets.shape;

/**
 * Embedded SVG sources for chess board artwork and pieces.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class SvgShapes {

  /**
   * Hidden constructor for the utility holder.
   */
  private SvgShapes() {
    // utility holder
  }

  /**
   * Board SVG width and height.
   */
  private static final int BOARD_SIZE = 1600;

  /**
   * Board tile size.
   */
  private static final int BOARD_TILE = 200;

  /**
   * Half-width of each internal board separator.
   */
  private static final int BOARD_GRID_HALF = 3;

  /**
   * Board separator fill.
   */
  private static final String BOARD_GRID_FILL = "#b2b2b2";

  /**
   * Light square fill.
   */
  private static final String BOARD_LIGHT_FILL = "#e5e5e5";

  /**
   * Dark square fill.
   */
  private static final String BOARD_DARK_FILL = "#cccccc";

  /**
   * Default outer frame fill (matches {@code Render.DEFAULT_FRAME}).
   */
  private static final String BOARD_FRAME_FILL = "#646464";

  /**
   * Returns the embedded SVG source for the chessboard.
   *
   * @return SVG source text
   */
  public static String board() {
    return board(BOARD_LIGHT_FILL, BOARD_DARK_FILL, BOARD_GRID_FILL);
  }

  /**
   * Returns the embedded SVG source for the chessboard tinted by an accent color.
   *
   * <p>A {@code null} or blank accent falls back to the default neutral grays. The
   * accent is blended with white at three fixed mix levels to keep the
   * light/dark/grid relationship consistent across hues.</p>
   *
   * @param accentHex CSS-style hex color (e.g. {@code "#4ab66f"}); {@code null} for default
   * @return SVG source text
   */
  public static String boardWithAccent(String accentHex) {
    AccentColors colors = accentColors(accentHex);
    return board(colors.light(), colors.dark(), colors.grid());
  }

  /**
   * Returns the embedded SVG source for the chessboard with explicit fill colors.
   *
   * @param lightFill CSS color for light squares
   * @param darkFill  CSS color for dark squares
   * @param gridFill  CSS color for the separator grid
   * @return SVG source text
   */
  public static String board(String lightFill, String darkFill, String gridFill) {
    String light = lightFill == null ? BOARD_LIGHT_FILL : lightFill;
    String dark = darkFill == null ? BOARD_DARK_FILL : darkFill;
    String grid = gridFill == null ? BOARD_GRID_FILL : gridFill;
    StringBuilder svg = new StringBuilder(6400);
    svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
        .append(BOARD_SIZE).append(' ').append(BOARD_SIZE).append("\" ")
        .append("role=\"img\" aria-labelledby=\"title\">\n")
        .append("  <title id=\"title\">Chessboard</title>\n")
        .append("  <g shape-rendering=\"crispEdges\">\n");
    appendBoardBackground(svg, grid);
    appendBoardSquares(svg, light, dark);
    svg.append("  </g>\n")
        .append("</svg>\n");
    return svg.toString();
  }

  /**
   * Derives the light/dark/grid fill colors for a given accent hex.
   *
   * @param accentHex CSS-style hex color; {@code null}/blank yields the default neutral palette
   * @return resolved palette
   */
  public static AccentColors accentColors(String accentHex) {
    if (accentHex == null || accentHex.isBlank()) {
      return new AccentColors(BOARD_LIGHT_FILL, BOARD_DARK_FILL, BOARD_GRID_FILL, BOARD_FRAME_FILL);
    }
    int rgb = parseHexColor(accentHex);
    return new AccentColors(
        mixWithWhiteHex(rgb, LIGHT_MIX),
        mixWithWhiteHex(rgb, DARK_MIX),
        mixWithWhiteHex(rgb, GRID_MIX),
        mixWithBlackHex(rgb, FRAME_BLACK_MIX));
  }

  /**
   * Resolved board palette: light squares, dark squares, separator grid, outer frame.
   *
   * @param light light square fill
   * @param dark  dark square fill
   * @param grid  separator grid fill
   * @param frame outer frame fill
   */
  public record AccentColors(String light, String dark, String grid, String frame) {}

  /**
   * White-mix factor used to derive the light-square fill from an accent color.
   */
  private static final double LIGHT_MIX = 0.85;

  /**
   * White-mix factor used to derive the dark-square fill from an accent color.
   */
  private static final double DARK_MIX = 0.70;

  /**
   * White-mix factor used to derive the separator-grid fill from an accent color.
   */
  private static final double GRID_MIX = 0.55;

  /**
   * Black-mix factor used to derive the outer-frame fill from an accent color
   * (0 keeps the accent, 1 returns black; 0.30 yields a deep, hue-true frame).
   */
  private static final double FRAME_BLACK_MIX = 0.30;

  /**
   * Parses a CSS-style hex color into a 0xRRGGBB integer. Accepts an optional
   * leading {@code #} and either 3-digit or 6-digit forms.
   *
   * @param hex CSS-style hex color
   * @return packed 0xRRGGBB value
   */
  private static int parseHexColor(String hex) {
    String s = hex.trim();
    if (s.startsWith("#")) {
      s = s.substring(1);
    }
    if (s.length() == 3) {
      StringBuilder expanded = new StringBuilder(6);
      for (int i = 0; i < 3; i++) {
        char c = s.charAt(i);
        expanded.append(c).append(c);
      }
      s = expanded.toString();
    }
    if (s.length() != 6) {
      throw new IllegalArgumentException("invalid accent color: " + hex);
    }
    try {
      return Integer.parseInt(s, 16) & 0xFFFFFF;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("invalid accent color: " + hex, e);
    }
  }

  /**
   * Linearly blends a packed RGB color with white and renders the result as a
   * lowercase {@code #rrggbb} string.
   *
   * @param rgb       packed source color
   * @param whiteMix  weight of white in {@code [0,1]} (0 keeps the accent, 1 returns white)
   * @return CSS-style hex string
   */
  private static String mixWithWhiteHex(int rgb, double whiteMix) {
    double t = Math.max(0.0, Math.min(1.0, whiteMix));
    int r = (rgb >> 16) & 0xFF;
    int g = (rgb >> 8) & 0xFF;
    int b = rgb & 0xFF;
    int mr = (int) Math.round(r + (255 - r) * t);
    int mg = (int) Math.round(g + (255 - g) * t);
    int mb = (int) Math.round(b + (255 - b) * t);
    return String.format("#%02x%02x%02x", mr, mg, mb);
  }

  /**
   * Linearly blends a packed RGB color with black and renders the result as a
   * lowercase {@code #rrggbb} string.
   *
   * @param rgb      packed source color
   * @param blackMix weight of black in {@code [0,1]} (0 keeps the accent, 1 returns black)
   * @return CSS-style hex string
   */
  private static String mixWithBlackHex(int rgb, double blackMix) {
    double t = Math.max(0.0, Math.min(1.0, blackMix));
    double keep = 1.0 - t;
    int r = (rgb >> 16) & 0xFF;
    int g = (rgb >> 8) & 0xFF;
    int b = rgb & 0xFF;
    int mr = (int) Math.round(r * keep);
    int mg = (int) Math.round(g * keep);
    int mb = (int) Math.round(b * keep);
    return String.format("#%02x%02x%02x", mr, mg, mb);
  }

  /**
   * Appends the full-board separator background rectangle.
   *
   * @param svg      builder receiving the background rectangle
   * @param gridFill grid color
   */
  private static void appendBoardBackground(StringBuilder svg, String gridFill) {
    svg.append("    <rect x=\"0\" y=\"0\" width=\"").append(BOARD_SIZE)
        .append("\" height=\"").append(BOARD_SIZE)
        .append("\" fill=\"").append(gridFill).append("\"/>\n");
  }

  /**
   * Appends the board square paths, preserving the existing separator geometry.
   *
   * @param svg       builder receiving square path elements
   * @param lightFill light square color
   * @param darkFill  dark square color
   */
  private static void appendBoardSquares(StringBuilder svg, String lightFill, String darkFill) {
    for (int rank = 0; rank < 8; rank++) {
      for (int file = 0; file < 8; file++) {
        appendBoardSquare(svg, file, rank, lightFill, darkFill);
      }
    }
  }

  /**
   * Appends one board square path.
   *
   * @param svg       builder receiving the square path
   * @param file      square file from 0 to 7
   * @param rank      square rank from 0 to 7
   * @param lightFill light square color
   * @param darkFill  dark square color
   */
  private static void appendBoardSquare(StringBuilder svg, int file, int rank,
      String lightFill, String darkFill) {
    int x0 = squareStart(file);
    int y0 = squareStart(rank);
    int x1 = squareEnd(file);
    int y1 = squareEnd(rank);
    svg.append("    <path fill=\"").append(squareFill(file, rank, lightFill, darkFill))
        .append("\" stroke=\"none\" d=\"")
        .append("M").append(x0).append(' ').append(y0)
        .append(" L").append(x1).append(' ').append(y0)
        .append(" L").append(x1).append(' ').append(y1)
        .append(" L").append(x0).append(' ').append(y1)
        .append(" Z\"/>\n");
  }

  /**
   * Computes a square's leading coordinate while preserving edge-to-edge board coverage.
   *
   * @param index file or rank index
   * @return leading coordinate
   */
  private static int squareStart(int index) {
    return index * BOARD_TILE + edgeInset(index);
  }

  /**
   * Computes a square's trailing coordinate while preserving edge-to-edge board coverage.
   *
   * @param index file or rank index
   * @return trailing coordinate
   */
  private static int squareEnd(int index) {
    return (index + 1) * BOARD_TILE - edgeInset(7 - index);
  }

  /**
   * Returns the grid inset for internal square edges.
   *
   * @param distanceFromEdge distance from the outer board edge
   * @return grid inset
   */
  private static int edgeInset(int distanceFromEdge) {
    return distanceFromEdge == 0 ? 0 : BOARD_GRID_HALF;
  }

  /**
   * Resolves the fill color for a board square.
   *
   * @param file      square file from 0 to 7
   * @param rank      square rank from 0 to 7
   * @param lightFill color for light squares
   * @param darkFill  color for dark squares
   * @return SVG fill color
   */
  private static String squareFill(int file, int rank, String lightFill, String darkFill) {
    return ((file + rank) & 1) == 0 ? lightFill : darkFill;
  }

  /**
   * Returns the embedded SVG source for black-bishop.
   *
   * @return SVG source text
   */
  public static String blackBishop() {
    return BLACK_BISHOP;
  }

  /**
   * Embedded SVG source for black-bishop.
   */
  private static final String BLACK_BISHOP = """
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" role="img" aria-labelledby="title">
        <title id="title">Black Bishop</title>
        <g shape-rendering="geometricPrecision">
          <g transform="translate(0.000000,200.000000) scale(0.100000,-0.100000)" fill="#000000" stroke="none">
            <path d="M955 1651 c-83 -36 -104 -137 -44 -206 l30 -33 -49 -59 c-26 -32 -114 -139 -194 -238 l-146 -181 84 -109 c99 -130 101 -135 74 -231 -26 -91 -22 -98 76 -140 70 -30 73 -33 45 -58 -31 -28 -80 -29 -216 -5 -247 43 -358 28 -421 -58 l-22 -29 40 -67 c44 -76 45 -76 112 -42 59 30 144 33 301 10 221 -32 365 1 365 84 0 12 5 21 10 21 6 0 10 -9 10 -21 0 -83 144 -116 365 -84 157 23 242 20 301 -10 67 -34 68 -34 112 42 l40 67 -22 29 c-63 86 -174 101 -421 58 -136 -24 -185 -23 -216 5 -28 25 -25 28 45 58 98 42 102 49 76 140 -27 96 -25 101 74 231 l84 109 -146 181 c-80 99 -168 206 -195 239 l-48 59 25 26 c94 98 -8 265 -129 212z"/>
          </g>
          <g transform="translate(0.000000,200.000000) scale(0.100000,-0.100000)" fill="#4c4c4c" stroke="none">
            <path d="M949 1581 c-35 -36 -37 -61 -6 -97 44 -51 137 -19 137 47 0 27 -53 79 -80 79 -12 0 -35 -13 -51 -29z M907 1293 c-71 -84 -252 -302 -280 -340 -20 -27 124 -218 157 -209 115 34 317 34 432 0 33 -9 177 182 157 209 -83 111 -361 437 -373 437 -5 0 -47 -44 -93 -97z m123 -218 l0 -45 45 0 c43 0 45 -1 45 -30 0 -29 -2 -30 -45 -30 l-45 0 0 -45 c0 -43 -1 -45 -30 -45 -29 0 -30 2 -30 45 l0 45 -45 0 c-43 0 -45 1 -45 30 0 29 2 30 45 30 l45 0 0 45 c0 43 1 45 30 45 29 0 30 -2 30 -45z M855 704 c-61 -15 -75 -27 -89 -73 -17 -57 -20 -55 55 -41 82 16 276 16 358 0 75 -14 72 -16 55 41 -19 65 -39 72 -214 75 -80 2 -154 1 -165 -2z M863 540 c-120 -18 -128 -30 -35 -49 106 -22 375 -9 406 19 29 28 -238 49 -371 30z M909 413 c-9 -2 -21 -15 -28 -29 -25 -56 -124 -67 -306 -34 -239 44 -379 8 -329 -85 21 -40 20 -40 68 -20 65 27 169 29 326 5 237 -35 302 -9 323 133 6 39 -3 44 -54 30z M1037 383 c21 -142 86 -168 323 -133 157 24 261 22 326 -5 48 -20 47 -20 68 20 50 93 -90 129 -329 85 -181 -33 -281 -22 -306 34 -11 23 -35 36 -71 36 -14 0 -16 -7 -11 -37z"/>
          </g>
        </g>
      </svg>
      """;

  /**
   * Returns the embedded SVG source for black-king.
   *
   * @return SVG source text
   */
  public static String blackKing() {
    return BLACK_KING;
  }

  /**
   * Embedded SVG source for black-king.
   */
  private static final String BLACK_KING = """
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" role="img" aria-labelledby="title">
        <title id="title">Black King</title>
        <g shape-rendering="geometricPrecision">
          <g transform="translate(0.000000,200.000000) scale(0.100000,-0.100000)" fill="#000000" stroke="none">
            <path d="M985 1718 c-2 -7 -6 -29 -7 -48 -3 -34 -5 -35 -48 -40 -31 -3 -45 -10 -45 -20 0 -10 14 -17 45 -20 64 -7 75 -77 16 -98 -46 -17 -93 -65 -113 -114 -8 -21 -18 -37 -22 -36 -3 2 -29 15 -58 30 -452 238 -810 -355 -384 -636 64 -42 72 -51 85 -97 28 -93 28 -111 2 -208 -43 -159 61 -196 544 -196 483 0 587 37 544 196 -26 97 -26 115 2 208 13 46 21 55 85 97 426 281 68 874 -384 636 -29 -15 -55 -28 -58 -30 -4 -1 -14 15 -22 36 -20 49 -67 97 -113 114 -59 21 -48 91 16 98 31 3 45 10 45 20 0 10 -14 17 -45 20 -45 5 -45 5 -50 49 -5 43 -25 66 -35 39z"/>
          </g>
          <g transform="translate(0.000000,200.000000) scale(0.100000,-0.100000)" fill="#4c4c4c" stroke="none">
            <path d="M965 1444 c-59 -14 -95 -58 -95 -119 0 -25 117 -265 130 -265 12 0 130 239 130 264 0 84 -77 140 -165 120z M460 1366 c-294 -85 -325 -450 -51 -599 l47 -26 80 24 c116 33 254 55 351 55 l83 0 0 81 0 82 -67 125 c-129 237 -257 312 -443 258z M1345 1366 c-106 -34 -151 -80 -247 -258 l-68 -125 0 -82 0 -81 83 0 c97 0 235 -22 351 -55 l80 -24 47 26 c359 195 143 722 -246 599z M833 760 c-246 -22 -328 -52 -327 -119 2 -66 1 -65 65 -47 202 56 656 56 858 0 64 -18 63 -19 65 47 l1 53 -60 18 c-139 43 -415 65 -602 48z M745 569 c-38 -5 -103 -17 -144 -27 l-75 -17 -17 -70 c-21 -83 -27 -76 46 -60 204 47 686 47 890 0 73 -16 67 -23 46 60 l-17 70 -100 23 c-113 26 -496 39 -629 21z M725 364 c-168 -17 -215 -36 -133 -54 223 -47 593 -47 816 1 64 13 58 23 -23 39 -58 12 -574 22 -660 14z"/>
          </g>
        </g>
      </svg>
      """;

  /**
   * Returns the embedded SVG source for black-knight.
   *
   * @return SVG source text
   */
  public static String blackKnight() {
    return BLACK_KNIGHT;
  }

  /**
   * Embedded SVG source for black-knight.
   */
  private static final String BLACK_KNIGHT = """
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" role="img" aria-labelledby="title">
        <title id="title">Black Knight</title>
        <g shape-rendering="geometricPrecision">
          <g transform="translate(0.000000,200.000000) scale(0.100000,-0.100000)" fill="#000000" stroke="none">
            <path d="M515 1605 c-32 -31 -33 -83 -4 -160 21 -56 21 -57 1 -78 -35 -39 -54 -89 -63 -162 -9 -82 -40 -158 -108 -265 -112 -175 -133 -255 -88 -333 44 -74 138 -127 189 -106 22 9 35 9 58 -1 51 -21 75 -7 119 69 51 86 102 132 225 200 53 29 107 67 121 83 34 40 36 35 24 -46 -14 -103 -47 -154 -169 -261 -157 -138 -190 -198 -190 -347 l0 -68 373 0 c204 0 456 3 558 7 l187 6 12 112 c74 663 -238 1197 -724 1241 -74 7 -76 7 -86 40 -38 113 -132 108 -195 -10 -18 -34 -35 -47 -35 -28 0 68 -161 152 -205 107z"/>
          </g>
          <g transform="translate(0.000000,200.000000) scale(0.100000,-0.100000)" fill="#4c4c4c" stroke="none">
            <path d="M538 1558 c-6 -36 12 -101 33 -124 26 -28 25 -34 -20 -94 -33 -44 -41 -66 -56 -145 -18 -99 -54 -186 -119 -288 -127 -197 -126 -303 3 -356 38 -16 50 -8 97 64 69 109 118 92 59 -19 -53 -99 -7 -79 63 27 51 78 111 130 215 184 97 51 139 94 167 171 32 88 79 95 73 10 -22 -297 -24 -302 -193 -468 -160 -157 -189 -209 -190 -337 0 -10 110 -13 519 -13 l518 0 7 61 c40 392 -67 819 -254 1010 -145 148 -460 272 -480 189 -14 -57 -51 -31 -65 45 -23 132 -69 127 -139 -13 -43 -85 -76 -98 -87 -32 -14 90 -140 196 -151 128z m165 -363 c-2 -61 -36 -98 -87 -93 -57 6 -48 72 16 118 58 42 73 37 71 -25z m-305 -452 c5 -31 -15 -63 -38 -63 -23 0 -27 37 -9 71 13 26 43 20 47 -8z"/>
          </g>
        </g>
      </svg>
      """;

  /**
   * Returns the embedded SVG source for black-pawn.
   *
   * @return SVG source text
   */
  public static String blackPawn() {
    return BLACK_PAWN;
  }

  /**
   * Embedded SVG source for black-pawn.
   */
  private static final String BLACK_PAWN = """
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" role="img" aria-labelledby="title">
        <title id="title">Black Pawn</title>
        <g shape-rendering="geometricPrecision">
          <g transform="translate(0.000000,200.000000) scale(0.100000,-0.100000)" fill="#000000" stroke="none">
            <path d="M919 1524 c-181 -55 -245 -281 -128 -446 l22 -30 -37 -32 c-54 -45 -76 -84 -76 -133 l0 -43 60 0 60 0 0 -33 c0 -44 -40 -105 -140 -213 -179 -195 -238 -354 -142 -382 35 -9 889 -9 924 0 96 28 37 187 -142 382 -100 108 -140 169 -140 213 l0 33 60 0 60 0 0 43 c0 49 -22 88 -76 133 l-37 32 22 30 c162 228 -31 526 -290 446z"/>
          </g>
          <g transform="translate(0.000000,200.000000) scale(0.100000,-0.100000)" fill="#4c4c4c" stroke="none">
            <path d="M938 1481 c-250 -81 -199 -444 62 -444 231 0 313 302 114 419 -47 28 -131 39 -176 25z M806 981 c-30 -20 -66 -68 -66 -88 0 -10 59 -13 260 -13 281 0 275 -1 240 55 -33 54 -78 71 -143 56 -51 -13 -135 -11 -227 4 -25 4 -43 0 -64 -14z M876 818 c-3 -7 -12 -37 -21 -65 -13 -40 -37 -76 -103 -149 -163 -181 -202 -242 -202 -316 l0 -38 450 0 450 0 0 38 c0 74 -39 135 -202 316 -73 82 -91 108 -107 159 l-19 62 -121 3 c-92 2 -122 0 -125 -10z"/>
          </g>
        </g>
      </svg>
      """;

  /**
   * Returns the embedded SVG source for black-queen.
   *
   * @return SVG source text
   */
  public static String blackQueen() {
    return BLACK_QUEEN;
  }

  /**
   * Embedded SVG source for black-queen.
   */
  private static final String BLACK_QUEEN = """
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" role="img" aria-labelledby="title">
        <title id="title">Black Queen</title>
        <g shape-rendering="geometricPrecision">
          <g transform="translate(0.000000,200.000000) scale(0.100000,-0.100000)" fill="#000000" stroke="none">
            <path d="M945 1682 c-84 -52 -92 -137 -21 -211 l34 -36 -42 -240 c-23 -132 -45 -235 -49 -229 -3 6 -49 109 -102 229 l-95 219 25 26 c97 101 -9 264 -132 203 -103 -51 -103 -172 0 -228 l37 -20 0 -237 c0 -131 -3 -238 -7 -238 -5 0 -68 85 -141 189 l-132 189 21 37 c79 133 -86 272 -191 160 -67 -71 -15 -215 79 -215 26 0 28 -9 104 -347 l64 -281 56 -88 c65 -100 63 -89 37 -174 -51 -166 36 -200 510 -200 474 0 561 34 510 200 -26 85 -28 74 37 174 l56 88 64 281 c76 338 78 347 104 347 94 0 146 144 79 215 -105 112 -270 -27 -191 -160 l21 -37 -132 -189 c-73 -104 -136 -189 -140 -189 -5 0 -8 107 -8 238 l0 237 37 20 c103 56 103 177 0 228 -123 61 -229 -102 -132 -203 l25 -26 -95 -219 c-53 -120 -99 -223 -102 -229 -4 -6 -26 97 -49 229 l-42 240 34 36 c107 111 -4 288 -131 211z"/>
          </g>
          <g transform="translate(0.000000,200.000000) scale(0.100000,-0.100000)" fill="#4c4c4c" stroke="none">
            <path d="M949 1621 c-35 -36 -37 -61 -6 -97 44 -51 137 -19 137 47 0 27 -53 79 -80 79 -12 0 -35 -13 -51 -29z M559 1581 c-35 -36 -37 -61 -6 -97 46 -53 130 -23 130 46 0 71 -73 102 -124 51z M1340 1585 c-47 -50 -17 -128 51 -128 69 0 99 74 50 124 -36 36 -70 37 -101 4z M189 1461 c-35 -36 -37 -61 -6 -97 46 -53 130 -23 130 46 0 71 -73 102 -124 51z M1710 1465 c-47 -50 -17 -128 51 -128 69 0 99 74 50 124 -36 36 -70 37 -101 4z M957 1159 c-66 -370 -68 -370 -228 3 -40 93 -75 165 -78 162 -3 -3 -8 -114 -11 -247 l-5 -242 -24 0 c-19 0 -44 28 -130 151 -58 83 -118 166 -133 185 l-27 34 13 -60 c8 -33 33 -147 57 -253 l42 -193 34 33 33 32 35 -17 c60 -28 108 -23 151 18 l38 36 57 -26 c87 -39 129 -30 188 42 l31 38 31 -38 c59 -72 101 -81 188 -42 l57 26 38 -36 c43 -41 91 -46 151 -18 l35 17 33 -32 34 -33 42 193 c24 106 49 220 57 253 l13 60 -27 -34 c-15 -19 -75 -102 -133 -185 -86 -123 -111 -151 -130 -151 l-24 0 -5 242 c-3 133 -8 244 -11 247 -3 3 -38 -69 -78 -162 -160 -373 -162 -373 -228 -3 -20 116 -40 211 -43 211 -3 0 -23 -95 -43 -211z M953 724 c-50 -30 -120 -34 -178 -10 -36 15 -38 15 -72 -11 -40 -31 -126 -43 -163 -23 -69 37 -82 -51 -19 -129 l20 -24 117 21 c254 47 430 47 684 0 l117 -21 20 24 c63 78 50 166 -19 129 -37 -20 -123 -8 -163 23 -34 26 -36 26 -72 11 -58 -24 -128 -20 -179 10 -53 31 -42 31 -93 0z M774 510 c-211 -32 -224 -40 -241 -145 l-5 -32 88 20 c132 30 636 30 768 0 l88 -20 -5 32 c-17 106 -28 113 -250 146 -161 23 -284 23 -443 -1z M760 314 c-179 -22 -214 -34 -153 -52 174 -52 871 -36 816 19 -25 24 -527 49 -663 33z"/>
          </g>
        </g>
      </svg>
      """;

  /**
   * Returns the embedded SVG source for black-rook.
   *
   * @return SVG source text
   */
  public static String blackRook() {
    return BLACK_ROOK;
  }

  /**
   * Embedded SVG source for black-rook.
   */
  private static final String BLACK_ROOK = """
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" role="img" aria-labelledby="title">
        <title id="title">Black Rook</title>
        <g shape-rendering="geometricPrecision">
          <g transform="translate(0.000000,200.000000) scale(0.100000,-0.100000)" fill="#000000" stroke="none">
            <path d="M490 1426 l0 -153 76 -64 c122 -102 114 -81 114 -307 l0 -194 -70 -77 -69 -78 -3 -79 -3 -79 -69 -3 c-65 -3 -68 -4 -62 -25 3 -12 6 -59 6 -104 l0 -83 590 0 590 0 0 83 c0 45 3 92 6 104 6 21 3 22 -62 25 l-69 3 -3 79 -3 79 -69 78 -70 77 0 194 c0 226 -8 205 114 307 l76 64 0 153 0 154 -119 0 -120 0 -3 -52 -3 -53 -60 0 -60 0 -3 53 -3 52 -139 0 -139 0 -3 -52 -3 -53 -60 0 -60 0 -3 53 -3 52 -120 0 -119 0 0 -154z"/>
          </g>
          <g transform="translate(0.000000,200.000000) scale(0.100000,-0.100000)" fill="#4c4c4c" stroke="none">
            <path d="M530 1425 l0 -115 470 0 470 0 0 115 0 115 -80 0 -80 0 0 -55 0 -55 -105 0 -105 0 0 55 0 55 -100 0 -100 0 0 -55 0 -55 -105 0 -105 0 0 55 0 55 -80 0 -80 0 0 -115z M720 1250 l-125 -5 58 -47 59 -48 288 0 288 0 59 48 58 47 -140 6 c-162 8 -367 7 -545 -1z M720 910 l0 -190 280 0 280 0 0 190 0 190 -280 0 -280 0 0 -190z M839 662 l-137 -3 -43 -47 -44 -47 193 -3 c105 -1 279 -1 385 0 l192 3 -45 47 -45 46 -160 4 c-88 2 -221 2 -296 0z M580 455 l0 -55 420 0 420 0 0 55 0 55 -420 0 -420 0 0 -55z M450 285 l0 -65 550 0 550 0 0 65 0 65 -550 0 -550 0 0 -65z"/>
          </g>
        </g>
      </svg>
      """;

  /**
   * Returns the embedded SVG source for white-bishop.
   *
   * @return SVG source text
   */
  public static String whiteBishop() {
    return WHITE_BISHOP;
  }

  /**
   * Embedded SVG source for white-bishop.
   */
  private static final String WHITE_BISHOP = """
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" role="img" aria-labelledby="title">
        <title id="title">White Bishop</title>
        <g shape-rendering="geometricPrecision">
          <g transform="translate(0.000000,200.000000) scale(0.100000,-0.100000)" fill="#000000" stroke="none">
            <path d="M955 1651 c-83 -36 -104 -137 -44 -206 l30 -33 -49 -59 c-26 -32 -114 -139 -194 -238 l-146 -181 84 -109 c99 -130 101 -135 74 -231 -26 -91 -22 -98 76 -140 70 -30 73 -33 45 -58 -31 -28 -80 -29 -216 -5 -247 43 -358 28 -421 -58 l-22 -29 40 -67 c44 -76 45 -76 112 -42 59 30 144 33 301 10 221 -32 365 1 365 84 0 12 5 21 10 21 6 0 10 -9 10 -21 0 -83 144 -116 365 -84 157 23 242 20 301 -10 67 -34 68 -34 112 42 l40 67 -22 29 c-63 86 -174 101 -421 58 -136 -24 -185 -23 -216 5 -28 25 -25 28 45 58 98 42 102 49 76 140 -27 96 -25 101 74 231 l84 109 -146 181 c-80 99 -168 206 -195 239 l-48 59 25 26 c94 98 -8 265 -129 212z"/>
          </g>
          <g transform="translate(0.000000,200.000000) scale(0.100000,-0.100000)" fill="#ffffff" stroke="none">
            <path d="M949 1581 c-35 -36 -37 -61 -6 -97 44 -51 137 -19 137 47 0 27 -53 79 -80 79 -12 0 -35 -13 -51 -29z M907 1293 c-71 -84 -252 -302 -280 -340 -20 -27 124 -218 157 -209 115 34 317 34 432 0 33 -9 177 182 157 209 -83 111 -361 437 -373 437 -5 0 -47 -44 -93 -97z m123 -218 l0 -45 45 0 c43 0 45 -1 45 -30 0 -29 -2 -30 -45 -30 l-45 0 0 -45 c0 -43 -1 -45 -30 -45 -29 0 -30 2 -30 45 l0 45 -45 0 c-43 0 -45 1 -45 30 0 29 2 30 45 30 l45 0 0 45 c0 43 1 45 30 45 29 0 30 -2 30 -45z M855 704 c-61 -15 -75 -27 -89 -73 -17 -57 -20 -55 55 -41 82 16 276 16 358 0 75 -14 72 -16 55 41 -19 65 -39 72 -214 75 -80 2 -154 1 -165 -2z M863 540 c-120 -18 -128 -30 -35 -49 106 -22 375 -9 406 19 29 28 -238 49 -371 30z M909 413 c-9 -2 -21 -15 -28 -29 -25 -56 -124 -67 -306 -34 -239 44 -379 8 -329 -85 21 -40 20 -40 68 -20 65 27 169 29 326 5 237 -35 302 -9 323 133 6 39 -3 44 -54 30z M1037 383 c21 -142 86 -168 323 -133 157 24 261 22 326 -5 48 -20 47 -20 68 20 50 93 -90 129 -329 85 -181 -33 -281 -22 -306 34 -11 23 -35 36 -71 36 -14 0 -16 -7 -11 -37z"/>
          </g>
        </g>
      </svg>
      """;

  /**
   * Returns the embedded SVG source for white-king.
   *
   * @return SVG source text
   */
  public static String whiteKing() {
    return WHITE_KING;
  }

  /**
   * Embedded SVG source for white-king.
   */
  private static final String WHITE_KING = """
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" role="img" aria-labelledby="title">
        <title id="title">White King</title>
        <g shape-rendering="geometricPrecision">
          <g transform="translate(0.000000,200.000000) scale(0.100000,-0.100000)" fill="#000000" stroke="none">
            <path d="M985 1718 c-2 -7 -6 -29 -7 -48 -3 -34 -5 -35 -48 -40 -31 -3 -45 -10 -45 -20 0 -10 14 -17 45 -20 64 -7 75 -77 16 -98 -46 -17 -93 -65 -113 -114 -8 -21 -18 -37 -22 -36 -3 2 -29 15 -58 30 -452 238 -810 -355 -384 -636 64 -42 72 -51 85 -97 28 -93 28 -111 2 -208 -43 -159 61 -196 544 -196 483 0 587 37 544 196 -26 97 -26 115 2 208 13 46 21 55 85 97 426 281 68 874 -384 636 -29 -15 -55 -28 -58 -30 -4 -1 -14 15 -22 36 -20 49 -67 97 -113 114 -59 21 -48 91 16 98 31 3 45 10 45 20 0 10 -14 17 -45 20 -45 5 -45 5 -50 49 -5 43 -25 66 -35 39z"/>
          </g>
          <g transform="translate(0.000000,200.000000) scale(0.100000,-0.100000)" fill="#ffffff" stroke="none">
            <path d="M965 1444 c-59 -14 -95 -58 -95 -119 0 -25 117 -265 130 -265 12 0 130 239 130 264 0 84 -77 140 -165 120z M460 1366 c-294 -85 -325 -450 -51 -599 l47 -26 80 24 c116 33 254 55 351 55 l83 0 0 81 0 82 -67 125 c-129 237 -257 312 -443 258z M1345 1366 c-106 -34 -151 -80 -247 -258 l-68 -125 0 -82 0 -81 83 0 c97 0 235 -22 351 -55 l80 -24 47 26 c359 195 143 722 -246 599z M833 760 c-246 -22 -328 -52 -327 -119 2 -66 1 -65 65 -47 202 56 656 56 858 0 64 -18 63 -19 65 47 l1 53 -60 18 c-139 43 -415 65 -602 48z M745 569 c-38 -5 -103 -17 -144 -27 l-75 -17 -17 -70 c-21 -83 -27 -76 46 -60 204 47 686 47 890 0 73 -16 67 -23 46 60 l-17 70 -100 23 c-113 26 -496 39 -629 21z M725 364 c-168 -17 -215 -36 -133 -54 223 -47 593 -47 816 1 64 13 58 23 -23 39 -58 12 -574 22 -660 14z"/>
          </g>
        </g>
      </svg>
      """;

  /**
   * Returns the embedded SVG source for white-knight.
   *
   * @return SVG source text
   */
  public static String whiteKnight() {
    return WHITE_KNIGHT;
  }

  /**
   * Embedded SVG source for white-knight.
   */
  private static final String WHITE_KNIGHT = """
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" role="img" aria-labelledby="title">
        <title id="title">White Knight</title>
        <g shape-rendering="geometricPrecision">
          <g transform="translate(0.000000,200.000000) scale(0.100000,-0.100000)" fill="#000000" stroke="none">
            <path d="M515 1605 c-32 -31 -33 -83 -4 -160 21 -56 21 -57 1 -78 -35 -39 -54 -89 -63 -162 -9 -82 -40 -158 -108 -265 -112 -175 -133 -255 -88 -333 44 -74 138 -127 189 -106 22 9 35 9 58 -1 51 -21 75 -7 119 69 51 86 102 132 225 200 53 29 107 67 121 83 34 40 36 35 24 -46 -14 -103 -47 -154 -169 -261 -157 -138 -190 -198 -190 -347 l0 -68 373 0 c204 0 456 3 558 7 l187 6 12 112 c74 663 -238 1197 -724 1241 -74 7 -76 7 -86 40 -38 113 -132 108 -195 -10 -18 -34 -35 -47 -35 -28 0 68 -161 152 -205 107z"/>
          </g>
          <g transform="translate(0.000000,200.000000) scale(0.100000,-0.100000)" fill="#ffffff" stroke="none">
            <path d="M538 1558 c-6 -36 12 -101 33 -124 26 -28 25 -34 -20 -94 -33 -44 -41 -66 -56 -145 -18 -99 -54 -186 -119 -288 -127 -197 -126 -303 3 -356 38 -16 50 -8 97 64 69 109 118 92 59 -19 -53 -99 -7 -79 63 27 51 78 111 130 215 184 97 51 139 94 167 171 32 88 79 95 73 10 -22 -297 -24 -302 -193 -468 -160 -157 -189 -209 -190 -337 0 -10 110 -13 519 -13 l518 0 7 61 c40 392 -67 819 -254 1010 -145 148 -460 272 -480 189 -14 -57 -51 -31 -65 45 -23 132 -69 127 -139 -13 -43 -85 -76 -98 -87 -32 -14 90 -140 196 -151 128z m165 -363 c-2 -61 -36 -98 -87 -93 -57 6 -48 72 16 118 58 42 73 37 71 -25z m-305 -452 c5 -31 -15 -63 -38 -63 -23 0 -27 37 -9 71 13 26 43 20 47 -8z"/>
          </g>
        </g>
      </svg>
      """;

  /**
   * Returns the embedded SVG source for white-pawn.
   *
   * @return SVG source text
   */
  public static String whitePawn() {
    return WHITE_PAWN;
  }

  /**
   * Embedded SVG source for white-pawn.
   */
  private static final String WHITE_PAWN = """
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" role="img" aria-labelledby="title">
        <title id="title">White Pawn</title>
        <g shape-rendering="geometricPrecision">
          <g transform="translate(0.000000,200.000000) scale(0.100000,-0.100000)" fill="#000000" stroke="none">
            <path d="M919 1524 c-181 -55 -245 -281 -128 -446 l22 -30 -37 -32 c-54 -45 -76 -84 -76 -133 l0 -43 60 0 60 0 0 -33 c0 -44 -40 -105 -140 -213 -179 -195 -238 -354 -142 -382 35 -9 889 -9 924 0 96 28 37 187 -142 382 -100 108 -140 169 -140 213 l0 33 60 0 60 0 0 43 c0 49 -22 88 -76 133 l-37 32 22 30 c162 228 -31 526 -290 446z"/>
          </g>
          <g transform="translate(0.000000,200.000000) scale(0.100000,-0.100000)" fill="#ffffff" stroke="none">
            <path d="M938 1481 c-250 -81 -199 -444 62 -444 231 0 313 302 114 419 -47 28 -131 39 -176 25z M806 981 c-30 -20 -66 -68 -66 -88 0 -10 59 -13 260 -13 281 0 275 -1 240 55 -33 54 -78 71 -143 56 -51 -13 -135 -11 -227 4 -25 4 -43 0 -64 -14z M876 818 c-3 -7 -12 -37 -21 -65 -13 -40 -37 -76 -103 -149 -163 -181 -202 -242 -202 -316 l0 -38 450 0 450 0 0 38 c0 74 -39 135 -202 316 -73 82 -91 108 -107 159 l-19 62 -121 3 c-92 2 -122 0 -125 -10z"/>
          </g>
        </g>
      </svg>
      """;

  /**
   * Returns the embedded SVG source for white-queen.
   *
   * @return SVG source text
   */
  public static String whiteQueen() {
    return WHITE_QUEEN;
  }

  /**
   * Embedded SVG source for white-queen.
   */
  private static final String WHITE_QUEEN = """
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" role="img" aria-labelledby="title">
        <title id="title">White Queen</title>
        <g shape-rendering="geometricPrecision">
          <g transform="translate(0.000000,200.000000) scale(0.100000,-0.100000)" fill="#000000" stroke="none">
            <path d="M945 1682 c-84 -52 -92 -137 -21 -211 l34 -36 -42 -240 c-23 -132 -45 -235 -49 -229 -3 6 -49 109 -102 229 l-95 219 25 26 c97 101 -9 264 -132 203 -103 -51 -103 -172 0 -228 l37 -20 0 -237 c0 -131 -3 -238 -7 -238 -5 0 -68 85 -141 189 l-132 189 21 37 c79 133 -86 272 -191 160 -67 -71 -15 -215 79 -215 26 0 28 -9 104 -347 l64 -281 56 -88 c65 -100 63 -89 37 -174 -51 -166 36 -200 510 -200 474 0 561 34 510 200 -26 85 -28 74 37 174 l56 88 64 281 c76 338 78 347 104 347 94 0 146 144 79 215 -105 112 -270 -27 -191 -160 l21 -37 -132 -189 c-73 -104 -136 -189 -140 -189 -5 0 -8 107 -8 238 l0 237 37 20 c103 56 103 177 0 228 -123 61 -229 -102 -132 -203 l25 -26 -95 -219 c-53 -120 -99 -223 -102 -229 -4 -6 -26 97 -49 229 l-42 240 34 36 c107 111 -4 288 -131 211z"/>
          </g>
          <g transform="translate(0.000000,200.000000) scale(0.100000,-0.100000)" fill="#ffffff" stroke="none">
            <path d="M949 1621 c-35 -36 -37 -61 -6 -97 44 -51 137 -19 137 47 0 27 -53 79 -80 79 -12 0 -35 -13 -51 -29z M559 1581 c-35 -36 -37 -61 -6 -97 46 -53 130 -23 130 46 0 71 -73 102 -124 51z M1340 1585 c-47 -50 -17 -128 51 -128 69 0 99 74 50 124 -36 36 -70 37 -101 4z M189 1461 c-35 -36 -37 -61 -6 -97 46 -53 130 -23 130 46 0 71 -73 102 -124 51z M1710 1465 c-47 -50 -17 -128 51 -128 69 0 99 74 50 124 -36 36 -70 37 -101 4z M957 1159 c-66 -370 -68 -370 -228 3 -40 93 -75 165 -78 162 -3 -3 -8 -114 -11 -247 l-5 -242 -24 0 c-19 0 -44 28 -130 151 -58 83 -118 166 -133 185 l-27 34 13 -60 c8 -33 33 -147 57 -253 l42 -193 34 33 33 32 35 -17 c60 -28 108 -23 151 18 l38 36 57 -26 c87 -39 129 -30 188 42 l31 38 31 -38 c59 -72 101 -81 188 -42 l57 26 38 -36 c43 -41 91 -46 151 -18 l35 17 33 -32 34 -33 42 193 c24 106 49 220 57 253 l13 60 -27 -34 c-15 -19 -75 -102 -133 -185 -86 -123 -111 -151 -130 -151 l-24 0 -5 242 c-3 133 -8 244 -11 247 -3 3 -38 -69 -78 -162 -160 -373 -162 -373 -228 -3 -20 116 -40 211 -43 211 -3 0 -23 -95 -43 -211z M953 724 c-50 -30 -120 -34 -178 -10 -36 15 -38 15 -72 -11 -40 -31 -126 -43 -163 -23 -69 37 -82 -51 -19 -129 l20 -24 117 21 c254 47 430 47 684 0 l117 -21 20 24 c63 78 50 166 -19 129 -37 -20 -123 -8 -163 23 -34 26 -36 26 -72 11 -58 -24 -128 -20 -179 10 -53 31 -42 31 -93 0z M774 510 c-211 -32 -224 -40 -241 -145 l-5 -32 88 20 c132 30 636 30 768 0 l88 -20 -5 32 c-17 106 -28 113 -250 146 -161 23 -284 23 -443 -1z M760 314 c-179 -22 -214 -34 -153 -52 174 -52 871 -36 816 19 -25 24 -527 49 -663 33z"/>
          </g>
        </g>
      </svg>
      """;

  /**
   * Returns the embedded SVG source for white-rook.
   *
   * @return SVG source text
   */
  public static String whiteRook() {
    return WHITE_ROOK;
  }

  /**
   * Embedded SVG source for white-rook.
   */
  private static final String WHITE_ROOK = """
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" role="img" aria-labelledby="title">
        <title id="title">White Rook</title>
        <g shape-rendering="geometricPrecision">
          <g transform="translate(0.000000,200.000000) scale(0.100000,-0.100000)" fill="#000000" stroke="none">
            <path d="M490 1426 l0 -153 76 -64 c122 -102 114 -81 114 -307 l0 -194 -70 -77 -69 -78 -3 -79 -3 -79 -69 -3 c-65 -3 -68 -4 -62 -25 3 -12 6 -59 6 -104 l0 -83 590 0 590 0 0 83 c0 45 3 92 6 104 6 21 3 22 -62 25 l-69 3 -3 79 -3 79 -69 78 -70 77 0 194 c0 226 -8 205 114 307 l76 64 0 153 0 154 -119 0 -120 0 -3 -52 -3 -53 -60 0 -60 0 -3 53 -3 52 -139 0 -139 0 -3 -52 -3 -53 -60 0 -60 0 -3 53 -3 52 -120 0 -119 0 0 -154z"/>
          </g>
          <g transform="translate(0.000000,200.000000) scale(0.100000,-0.100000)" fill="#ffffff" stroke="none">
            <path d="M530 1425 l0 -115 470 0 470 0 0 115 0 115 -80 0 -80 0 0 -55 0 -55 -105 0 -105 0 0 55 0 55 -100 0 -100 0 0 -55 0 -55 -105 0 -105 0 0 55 0 55 -80 0 -80 0 0 -115z M720 1250 l-125 -5 58 -47 59 -48 288 0 288 0 59 48 58 47 -140 6 c-162 8 -367 7 -545 -1z M720 910 l0 -190 280 0 280 0 0 190 0 190 -280 0 -280 0 0 -190z M839 662 l-137 -3 -43 -47 -44 -47 193 -3 c105 -1 279 -1 385 0 l192 3 -45 47 -45 46 -160 4 c-88 2 -221 2 -296 0z M580 455 l0 -55 420 0 420 0 0 55 0 55 -420 0 -420 0 0 -55z M450 285 l0 -65 550 0 550 0 0 65 0 65 -550 0 -550 0 0 -65z"/>
          </g>
        </g>
      </svg>
      """;


  /**
   * Returns the embedded ChessRTK logo as SVG source.
   *
   * <p>Flat, single-color fills only, so the minimal in-house renderer can
   * draw it the same way it draws the chess pieces.</p>
   *
   * @return SVG source text
   */
  public static String logo() {
    return LOGO;
  }

  /**
   * Embedded ChessRTK logo: squircle tile, knight, and flask in flat brand fills.
   */
  private static final String LOGO = """
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" role="img" aria-label="ChessRTK logo"><path fill="#2b88a4" d="M948.0 512.0 L947.9 631.4 L947.5 669.5 L946.8 697.2 L945.8 719.6 L944.6 738.8 L943.1 755.6 L941.4 770.8 L939.3 784.6 L937.0 797.2 L934.4 808.9 L931.5 819.8 L928.3 829.9 L924.8 839.4 L921.1 848.3 L917.0 856.7 L912.6 864.5 L907.8 871.9 L902.8 878.9 L897.3 885.4 L891.6 891.6 L885.4 897.3 L878.9 902.8 L871.9 907.8 L864.5 912.6 L856.7 917.0 L848.3 921.1 L839.4 924.8 L829.9 928.3 L819.8 931.5 L808.9 934.4 L797.2 937.0 L784.6 939.3 L770.8 941.4 L755.6 943.1 L738.8 944.6 L719.6 945.8 L697.2 946.8 L669.5 947.5 L631.4 947.9 L512.0 948.0 L392.6 947.9 L354.5 947.5 L326.8 946.8 L304.4 945.8 L285.2 944.6 L268.4 943.1 L253.2 941.4 L239.4 939.3 L226.8 937.0 L215.1 934.4 L204.2 931.5 L194.1 928.3 L184.6 924.8 L175.7 921.1 L167.3 917.0 L159.5 912.6 L152.1 907.8 L145.1 902.8 L138.6 897.3 L132.4 891.6 L126.7 885.4 L121.2 878.9 L116.2 871.9 L111.4 864.5 L107.0 856.7 L102.9 848.3 L99.2 839.4 L95.7 829.9 L92.5 819.8 L89.6 808.9 L87.0 797.2 L84.7 784.6 L82.6 770.8 L80.9 755.6 L79.4 738.8 L78.2 719.6 L77.2 697.2 L76.5 669.5 L76.1 631.4 L76.0 512.0 L76.1 392.6 L76.5 354.5 L77.2 326.8 L78.2 304.4 L79.4 285.2 L80.9 268.4 L82.6 253.2 L84.7 239.4 L87.0 226.8 L89.6 215.1 L92.5 204.2 L95.7 194.1 L99.2 184.6 L102.9 175.7 L107.0 167.3 L111.4 159.5 L116.2 152.1 L121.2 145.1 L126.7 138.6 L132.4 132.4 L138.6 126.7 L145.1 121.2 L152.1 116.2 L159.5 111.4 L167.3 107.0 L175.7 102.9 L184.6 99.2 L194.1 95.7 L204.2 92.5 L215.1 89.6 L226.8 87.0 L239.4 84.7 L253.2 82.6 L268.4 80.9 L285.2 79.4 L304.4 78.2 L326.8 77.2 L354.5 76.5 L392.6 76.1 L512.0 76.0 L631.4 76.1 L669.5 76.5 L697.2 77.2 L719.6 78.2 L738.8 79.4 L755.6 80.9 L770.8 82.6 L784.6 84.7 L797.2 87.0 L808.9 89.6 L819.8 92.5 L829.9 95.7 L839.4 99.2 L848.3 102.9 L856.7 107.0 L864.5 111.4 L871.9 116.2 L878.9 121.2 L885.4 126.7 L891.6 132.4 L897.3 138.6 L902.8 145.1 L907.8 152.1 L912.6 159.5 L917.0 167.3 L921.1 175.7 L924.8 184.6 L928.3 194.1 L931.5 204.2 L934.4 215.1 L937.0 226.8 L939.3 239.4 L941.4 253.2 L943.1 268.4 L944.6 285.2 L945.8 304.4 L946.8 326.8 L947.5 354.5 L947.9 392.6 Z"/><g transform="translate(150 162) scale(2.89)"><g shape-rendering="geometricPrecision">
    <g transform="translate(0.000000,200.000000) scale(0.100000,-0.100000)" fill="#0e3b4d" stroke="none">
      <path d="M515 1605 c-32 -31 -33 -83 -4 -160 21 -56 21 -57 1 -78 -35 -39 -54 -89 -63 -162 -9 -82 -40 -158 -108 -265 -112 -175 -133 -255 -88 -333 44 -74 138 -127 189 -106 22 9 35 9 58 -1 51 -21 75 -7 119 69 51 86 102 132 225 200 53 29 107 67 121 83 34 40 36 35 24 -46 -14 -103 -47 -154 -169 -261 -157 -138 -190 -198 -190 -347 l0 -68 373 0 c204 0 456 3 558 7 l187 6 12 112 c74 663 -238 1197 -724 1241 -74 7 -76 7 -86 40 -38 113 -132 108 -195 -10 -18 -34 -35 -47 -35 -28 0 68 -161 152 -205 107z"/>
    </g>
    <g transform="translate(0.000000,200.000000) scale(0.100000,-0.100000)" fill="#ffffff" stroke="none">
      <path d="M538 1558 c-6 -36 12 -101 33 -124 26 -28 25 -34 -20 -94 -33 -44 -41 -66 -56 -145 -18 -99 -54 -186 -119 -288 -127 -197 -126 -303 3 -356 38 -16 50 -8 97 64 69 109 118 92 59 -19 -53 -99 -7 -79 63 27 51 78 111 130 215 184 97 51 139 94 167 171 32 88 79 95 73 10 -22 -297 -24 -302 -193 -468 -160 -157 -189 -209 -190 -337 0 -10 110 -13 519 -13 l518 0 7 61 c40 392 -67 819 -254 1010 -145 148 -460 272 -480 189 -14 -57 -51 -31 -65 45 -23 132 -69 127 -139 -13 -43 -85 -76 -98 -87 -32 -14 90 -140 196 -151 128z m165 -363 c-2 -61 -36 -98 -87 -93 -57 6 -48 72 16 118 58 42 73 37 71 -25z m-305 -452 c5 -31 -15 -63 -38 -63 -23 0 -27 37 -9 71 13 26 43 20 47 -8z"/>
    </g>
  </g>
</g><g transform="translate(426 240) scale(0.9732 0.9727)"><g transform="translate(-180.31 -244.48)"><path fill="#16566d" d="m333.27 244.48c-9.0304 0-16.342 10.519-16.342 19.55l 0 7.7384c0 6.3886 3.685 11.885 9.0111 14.56-0.0798 1.0586-0.15314 2.1275-0.15314 3.2074l 0 153.8c-2.4178 4.5548-4.7734 9.2986-7.3312 14l-132.17 239.08c-21.284 39.127 19.219 72.395 43.071 72.395l 330.72 0c23.851 0 64.789-32.472 43.071-72.395l-132.16-239.08c-2.5578-4.7019-4.9134-9.4456-7.3312-14l 0 -153.8c0-1.0798-0.0733-2.1487-0.15314-3.2074 5.3262-2.6758 9.0111-8.1719 9.0111-14.56l 0 -7.7384c0-9.0306-7.2608-19.55-16.291-19.55z"/><g fill="#f4fbfd" transform="matrix(1.6291 0 0 1.6291 -1390.6 -814.74)"><path d="m1080.3 768.8l 31.307 0c12.456 0 16.531 11.607 22.483 22.611l70.701 130.71c14.28 26.399-10.028 38.012-22.484 38.012l -172.71 0c-12.456 0-35.208-14.489-22.483-38.012l70.701-130.71c5.9522-11.004 10.028-22.611 22.483-22.611z"/><rect x="1063.8" y="670.49" width="64.23" height="117.7"/><rect transform="rotate(-90)" x="-671.96" y="1058.1" width="13.263" height="75.529"/></g><path fill="#1487a6" d="m461.99 505.91 91.927 174.75c20.125 38.256-14.442 54.746-32.381 54.746l -248.74 0c-17.939 0-50.781-20.907-32.381-54.746l90.127-165.75c79.68 31.256 108.66-55.62 131.45-8.999z"/></g></g></svg>
""";
}
