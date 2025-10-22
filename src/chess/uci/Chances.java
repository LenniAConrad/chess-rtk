package chess.uci;

import chess.uci.Chances;

/**
 * Used for storing UCI win-draw-loss chances. In any given chess position, that
 * can be played onwards, a position can be evaluated using a win-chance,
 * draw-chance and a loss-chance. UCI chess engines return these chances as
 * follows.
 * 
 * <p>
 * Example on how a chess engine might return the chances:
 * </p>
 * 
 * <blockquote>wdl 790 200 10</blockquote>
 * 
 * <p>
 * Here, the position has a 79% win-chance, 20% draw-chance and a 1% loss-chance
 * for the side that is to move.
 * </p>
 * 
 * Unlike centipawns, chances can tell, how "dead" a position is. Chess engines,
 * such as Stockfish or Leela Chess Zero, generate their centipawn value by
 * converting their chances using a custom formula. These formulas can be very
 * different from one and another. It is easier to compare two centipawn values
 * to eachother, but less accurate, since chances and centipawn values can be
 * very different for each engine.
 * 
 * @since 2023
 * @author Lennart A. Conrad
 */
public class Chances implements Comparable<Chances> {

	/**
	 * The total sum of all chances added up. Used for division in other methods.
	 */
	private static final double TOTAL_CHANCES = 1000.0;

	/**
	 * The divider for dividing chances into percentages.
	 */
	private static final double PERCENTILE_DIVIDER = 10.0;

	/**
	 * The chance of the game resulting in a win.
	 */
	private short win = 0;
	/**
	 * The chance of the game resulting in a draw.
	 */
	private short draw = 0;
	/**
	 * The chance of the game resulting in a loss.
	 */
	private short loss = 0;

	/**
	 * Used for comparing two {@code Chances} to one and another.
	 * <p>
	 * Meanings:
	 * </p>
	 * <ul>
	 * <li>{@code Greater}: A value is greater than another</li>
	 * <li>{@code GreaterEqual}: A value is greater or equal to another</li>
	 * <li>{@code Equal}: A value is equal to another</li>
	 * <li>{@code LessEqual}: A value is less or equal to another</li>
	 * <li>{@code Less}: A value is less than another</li>
	 * </ul>
	 * 
	 * @see applies
	 */
	public enum ComparisonOperator {
		/**
		 * A value is greater than another
		 */
		GREATER,
		/**
		 * A value is greater or equal to another
		 */
		GREATER_EQUAL,
		/**
		 * A value is equal to another
		 */
		EQUAL,
		/**
		 * A value is less or equal to another
		 */
		LESS_EQUAL,
		/**
		 * A value is less than another
		 */
		LESS
	}

	/**
	 * Used for inverting the current {@code Chances}.
	 * 
	 * <p>
	 * Inverting {@code Chances} can be useful if you want to skip evaluating
	 * certain positions. The following {@code Position}
	 * '8/p1p5/1p4r1/8/6p1/P7/1P1Q1k2/K7 b - - 0 11' is evaluated with:
	 * </p>
	 * 
	 * <blockquote> info depth 32 seldepth 69 multipv 1 score cp 544 wdl 1000 0 0
	 * nodes 17065467 nps 1333656 hashfull 998 tbhits 0 time 12796 pv g7h8 f4f3 h2h5
	 * g5f4 h8d4 f5e4 d4d2 e4e3 d2h2 f4e4 h2e5 e4d3 e5c3 d3e2 c3c2 e3d2 h5e5 e2f1
	 * c2d2 f3f2 d2d3 f1g2 d3e4 g2g1 e4g6 f2f1q a1a2 f1c4 b2b3 c4f4 g6b1 g1f2 b1c2
	 * f2g3 e5e2 c7c5 c2d3 g3h4 d3h7 h4g5 h7a7 f4d6 a7g7 g5h4 g7h8 h4g5 h8g8 g5h4
	 * g8c4 h4g5 c4c1 g5g6 c1e3 d6d4 </blockquote>
	 * 
	 * <p>
	 * A chess {@code Engine}, that is perfect would only ever evaluate a position
	 * with <i>Mate for White</i>, <i>Draw</i> or <i>Mate for Black</i>. Chess
	 * {@code Engines}, however, are not perfect. If we now play the move Qh8, we
	 * reach the {@code Position} ' 7Q/p1p5/1p4r1/5qk1/5pp1/P7/1P5R/K7 b - - 1 1'
	 * and can conclude that our synthesized {@code Chances} should look like this:
	 * </p>
	 * 
	 * <blockquote> info depth 31 seldepth 53 multipv 1 score cp -544 wdl 0 0 1000
	 * nodes ??? nps 1333656 hashfull 998 tbhits 0 time 12796 pv f4f3 h2h5 g5f4 h8d4
	 * f5e4 d4d2 e4e3 d2h2 f4e4 h2e5 e4d3 e5c3 d3e2 c3c2 e3d2 h5e5 e2f1 c2d2 f3f2
	 * d2d3 f1g2 d3e4 g2g1 e4g6 f2f1q a1a2 f1c4 b2b3 c4f4 g6b1 g1f2 b1c2 f2g3 e5e2
	 * c7c5 c2d3 g3h4 d3h7 h4g5 h7a7 f4d6 a7g7 g5h4 g7h8 h4g5 h8g8 g5h4 g8c4 h4g5
	 * c4c1 g5g6 c1e3 d6d4 </blockquote>
	 * 
	 * <p>
	 * The {@code Chances} have just switched, a {@code Position} that is good for
	 * White must be bad for Black. The {@code Chances} however, is not perfect. It
	 * is just a conclusion made from the {@code Engine} {@code Output}.
	 * <p>
	 * 
	 * <p>
	 * Inversion examples:
	 * </p>
	 * 
	 * <style> table { border-collapse: collapse; } th, td { padding: 3px; }
	 * </style>
	 * 
	 * <pre>
	 * 	<table border=1>
	 * 		<tr> <th colspan="3"> Original </th> <th colspan=
	"3"> Inverted </th> </tr>
	 * 		<tr> <th> Win </th> <th> Draw </th> <th> Loss </th> <th> Win </th> <th> Draw </th> <th> Loss </th> </tr>
	 * 		<tr> <td> 1000 </td> <td> 0 </td> <td> 0 </td> <td> 0 </td> <td> 0 </td> <td> 1000 </td> </tr>
	 *   	<tr> <td> 100 </td> <td> 400 </td> <td> 500 </td> <td> 500 </td> <td> 400 </td> <td> 100 </td> </tr>
	 *    	<tr> <td> 0 </td> <td> 300 </td> <td> 700 </td> <td> 700 </td> <td> 300 </td> <td> 0 </td> </tr> 
	 * 	</table>
	 * </pre>
	 */
	public void invert() {
		short buffer = win;
		win = loss;
		loss = buffer;
	}

	/**
	 * Used for constructing a {@code Chances} instance from individual win, draw
	 * and loss
	 * values.
	 *
	 * @param win  the number of win chances
	 * @param draw the number of draw chances
	 * @param loss the number of loss chances
	 * @throws IllegalArgumentException if any value is negative or the three do not
	 *                                  sum to
	 *                                  {@link #TOTAL_CHANCES}.
	 */
	public Chances(short win, short draw, short loss) {
		this.win = win;
		this.draw = draw;
		this.loss = loss;

		if (!isValid()) {
			throw new IllegalArgumentException(
					"win, draw and loss must be non-negative and sum to " + TOTAL_CHANCES + '.');
		}
	}

	/**
	 * Used for retrieving the win chance.
	 * 
	 * @return The chance of winning the game.
	 */
	public short getWinChance() {
		return win;
	}

	/**
	 * Used for retrieving the draw chance.
	 * 
	 * @return The chance of drawing the game.
	 */
	public short getDrawChance() {
		return draw;
	}

	/**
	 * 
	 * Used for retrieving the loss chance.
	 * 
	 * @return the chance of losing the game
	 */
	public short getLossChance() {
		return loss;
	}

	/**
	 * Used for determining whether the current {@code Chance} instance is valid. A
	 * chance is valid
	 * when each of {@code win}, {@code draw} and {@code loss} is non-negative and
	 * their sum equals
	 * {@link #TOTAL_CHANCES}.
	 *
	 * @return {@code true} if the current {@code Chance} is valid; {@code false}
	 *         otherwise.
	 */
	private boolean isValid() {
		return win >= 0 && draw >= 0 && loss >= 0 && win + draw + loss == TOTAL_CHANCES;
	}

	/**
	 * Used for turning the win chance into a {@code Double}.
	 * 
	 * @return A {@code Double} between 0 and 1 that represents the win chance.
	 */
	public double winChanceToDouble() {
		return win / TOTAL_CHANCES;
	}

	/**
	 * Used for turning the draw chance into a {@code Double}.
	 * 
	 * @return A {@code Double} between 0 and 1 that represents the draw chance.
	 */
	public double drawChanceToDouble() {
		return draw / TOTAL_CHANCES;
	}

	/**
	 * Used for turning the loss chance into a {@code Double}.
	 * 
	 * @return A {@code Double} between 0 and 1 that represents the loss chance.
	 */
	public double lossChanceToDouble() {
		return loss / TOTAL_CHANCES;
	}

	/**
	 * Used for turning the win chance into a {@code String}.
	 * 
	 * @return A {@code String} displaying the win chance.
	 */
	public String winChanceToString() {
		return win / PERCENTILE_DIVIDER + "%";
	}

	/**
	 * Used for turning the draw chance into a {@code String}.
	 * 
	 * @return A {@code String} displaying the draw chance.
	 */
	public String drawChanceToString() {
		return draw / PERCENTILE_DIVIDER + "%";
	}

	/**
	 * Used for turning the loss chance into a {@code String}.
	 * 
	 * @return A {@code String} displaying the loss chance.
	 */
	public String lossChanceToString() {
		return loss / PERCENTILE_DIVIDER + "%";
	}

	@Override
	public String toString() {
		return "win " + win / PERCENTILE_DIVIDER + "% draw " + draw / PERCENTILE_DIVIDER + "% loss "
				+ loss / PERCENTILE_DIVIDER + "%";
	}

	/**
	 * Used for directly comparing two {@code Chances} to one and another. The win,
	 * draw and loss {@code ComparisonOperators} will compare the win, draw and loss
	 * chances of each object with the other.
	 * 
	 * @param win
	 * @param draw
	 * @param loss
	 * @param chances
	 * @return {@code false} if none of the {@code ComparisonOperators} apply, or if
	 *         any of the input parameters is {@code null}, else {@code true}
	 */
	public boolean compare(ComparisonOperator win, ComparisonOperator draw, ComparisonOperator loss, Chances chances) {
		if (win == null || draw == null || loss == null) {
			return false;
		}
		boolean winapplies = false;
		boolean drawapplies = false;
		boolean lossapplies = false;
		switch (win) {
			case GREATER: {
				winapplies = this.win > chances.win;
				break;
			}
			case GREATER_EQUAL: {
				winapplies = this.win >= chances.win;
				break;
			}
			case EQUAL: {
				winapplies = this.win == chances.win;
				break;
			}
			case LESS_EQUAL: {
				winapplies = this.win <= chances.win;
				break;
			}
			case LESS: {
				winapplies = this.win < chances.win;
				break;
			}
		}
		switch (draw) {
			case GREATER: {
				drawapplies = this.draw > chances.draw;
				break;
			}
			case GREATER_EQUAL: {
				drawapplies = this.draw >= chances.draw;
				break;
			}
			case EQUAL: {
				drawapplies = this.draw == chances.draw;
				break;
			}
			case LESS_EQUAL: {
				drawapplies = this.draw <= chances.draw;
				break;
			}
			case LESS: {
				drawapplies = this.draw < chances.draw;
				break;
			}
		}
		switch (loss) {
			case GREATER: {
				lossapplies = this.loss > chances.loss;
				break;
			}
			case GREATER_EQUAL: {
				lossapplies = this.loss >= chances.loss;
				break;
			}
			case EQUAL: {
				lossapplies = this.loss == chances.loss;
				break;
			}
			case LESS_EQUAL: {
				lossapplies = this.loss <= chances.loss;
				break;
			}
			case LESS: {
				lossapplies = this.loss < chances.loss;
				break;
			}
		}
		return winapplies && drawapplies && lossapplies;
	}

	/**
	 * Copy constructor for creating a new {@code Chances} instance with the same
	 * values.
	 * 
	 * @param other The {@code Chances} instance to copy.
	 */
	public Chances(Chances other) {
		this.win = other.win;
		this.draw = other.draw;
		this.loss = other.loss;
	}

	/**
	 * Used for comparing two {@code Chances} to one and another.
	 *
	 * @param chances the other Chances instance to compare
	 * @return a negative, zero, or positive integer as this object is less than,
	 *         equal to, or greater
	 *         than the specified object
	 */
	@Override
	public int compareTo(Chances chances) {
		return chances.win + chances.win + chances.draw - win - win - draw;
	}

	/**
	 * Used for checking equality based on win, draw, and loss fields.
	 *
	 * @param obj the object to compare with
	 * @return true if this object is the same as the obj argument; false otherwise
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		Chances chances = (Chances) obj;
		return win == chances.win && draw == chances.draw && loss == chances.loss;
	}

	/**
	 * Used for generating a hash code consistent with equals.
	 *
	 * @return hash code value for the object
	 */
	@Override
	public int hashCode() {
		int result = Short.hashCode(win);
		result = 31 * result + Short.hashCode(draw);
		result = 31 * result + Short.hashCode(loss);
		return result;
	}

	/**
	 * Used for parsing a textual win-draw-loss (W/D/L) triple into a
	 * {@code Chances}
	 * instance.
	 *
	 * <p>
	 * Accepts the following notations (case-insensitive; extra words are ignored):
	 * </p>
	 * <ul>
	 * <li>{@code 1000/0/0} or {@code 790/200/10}</li>
	 * <li>{@code wdl 790 200 10}</li>
	 * <li>{@code win 79% draw 20% loss 1%}</li>
	 * <li>{@code 79 20 1} (interpreted as percentages when the three sum to
	 * 100)</li>
	 * </ul>
	 *
	 * <p>
	 * Parsing and normalization steps:
	 * </p>
	 * <ol>
	 * <li>Extract the first three integers found in the input string.</li>
	 * <li>If a percent sign is present anywhere, or the three values sum to 100,
	 * treat them as percentages and convert to a 0..1000 (basis points) scale.</li>
	 * <li>If the triple does not sum to 1000, scale proportionally and round so the
	 * sum
	 * becomes exactly 1000.</li>
	 * <li>Validate that each component lies within {@code 0..1000} and construct
	 * the result.</li>
	 * </ol>
	 *
	 * <p>
	 * Examples:
	 * </p>
	 * 
	 * <pre>{@code
	 * Chances.parse("wdl 790 200 10")                   // -> 790/200/10
	 * Chances.parse("79 20 1")                          // -> 790/200/10
	 * Chances.parse("win 55% draw 25% loss 20%")        // -> 550/250/200
	 * Chances.parse("1000/0/0")                         // -> 1000/0/0
	 * }</pre>
	 *
	 * @param s Used for supplying a string containing a W/D/L triple in one of the
	 *          accepted
	 *          formats.
	 * @return Used for returning a {@code Chances} normalized to a 0..1000 scale
	 *         whose
	 *         components sum to 1000.
	 * @throws IllegalArgumentException Used for indicating malformed input: when
	 *                                  {@code s} is
	 *                                  {@code null}; when fewer than three integers
	 *                                  are found; when the pre-normalized
	 *                                  sum is non-positive; or when any component
	 *                                  falls outside {@code 0..1000} after
	 *                                  normalization.
	 */
	public static Chances parse(String s) {
		if (s == null)
			throw new IllegalArgumentException("chances string is null");
		String in = s.trim().toLowerCase();

		// Accept common notations:
		// - "1000/0/0" or "790/200/10"
		// - "wdl 790 200 10"
		// - "win 79% draw 20% loss 1%" (percentages)
		// - "79 20 1" (interpreted as percentages if they sum to 100)
		// Extract the first three integers we find.
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(-?\\d+)").matcher(in);
		int[] vals = new int[3];
		int i = 0;
		while (m.find() && i < 3) {
			vals[i++] = Integer.parseInt(m.group(1));
		}
		if (i < 3) {
			throw new IllegalArgumentException("Could not parse three values from: " + s);
		}

		int w = vals[0];
		int d = vals[1];
		int l = vals[2];

		// If there are '%' signs OR the triple sums to 100, treat as percentages.
		boolean hasPercent = in.indexOf('%') >= 0;
		int sum = w + d + l;

		if (hasPercent || sum == 100) {
			w *= 10;
			d *= 10;
			l *= 10; // convert 0..100% → 0..1000 basis points
			sum = w + d + l;
		}

		// If still not exactly 1000, normalize proportionally (round) to keep
		// invariants.
		if (sum != 1000) {
			if (sum <= 0)
				throw new IllegalArgumentException("Sum must be positive: " + s);
			double scale = 1000.0 / sum;
			int nw = (int) Math.round(w * scale);
			int nd = (int) Math.round(d * scale);
			int nl = 1000 - nw - nd; // force exact sum to 1000
			// Fix rounding drift if any component fell outside [0,1000]
			if (nl < 0) {
				nd += nl;
				nl = 0;
			}
			if (nd < 0) {
				nw += nd;
				nd = 0;
			}
			w = nw;
			d = nd;
			l = nl;
		}

		// Bounds check before casting
		if (w < 0 || d < 0 || l < 0 || w > 1000 || d > 1000 || l > 1000)
			throw new IllegalArgumentException("Values out of range 0..1000: " + w + "," + d + "," + l);

		return new Chances((short) w, (short) d, (short) l);
	}

}
