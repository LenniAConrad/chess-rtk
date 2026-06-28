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
	 * Formats win/draw/loss percentages into a single line.
	 * Uses the same formatting as the individual helpers.
	 *
	 * @return formatted chance summary string
	 */
	@Override
	public String toString() {
		return "win " + win / PERCENTILE_DIVIDER + "% draw " + draw / PERCENTILE_DIVIDER + "% loss "
				+ loss / PERCENTILE_DIVIDER + "%";
	}

	/**
	 * Formats this WDL triple as a compact, whitespace-free DSL token.
	 *
	 * @return canonical WDL text in {@code win/draw/loss} basis-point form
	 */
	public String toUciString() {
		return win + "/" + draw + "/" + loss;
	}

	/**
	 * Compares this instance against another {@code Chances} object using the three
	 * supplied operators.
	 *
	 * <p>
	 * Each {@link ComparisonOperator} is applied to the corresponding win/draw/loss
	 * component, so the method returns {@code true} only when all three comparisons
	 * pass simultaneously.
	 * </p>
	 *
	 * @param win     operator applied to the win chance
	 * @param draw    operator applied to the draw chance
	 * @param loss    operator applied to the loss chance
	 * @param chances the target instance to compare against (must not be {@code null})
	 * @return {@code false} if any operator fails, if {@code chances} is {@code null},
	 *         or if any argument is {@code null}; {@code true} otherwise
	 */
	public boolean compare(ComparisonOperator win, ComparisonOperator draw, ComparisonOperator loss, Chances chances) {
		if (win == null || draw == null || loss == null || chances == null) {
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
	 * Copy constructor creating a deep clone of another {@code Chances} instance.
	 *
	 * @param other the source {@code Chances}; must not be {@code null}
	 */
	public Chances(Chances other) {
		this.win = other.win;
		this.draw = other.draw;
		this.loss = other.loss;
	}

	/**
	 * Compares the current {@code Chances} against another instance according to
	 * win/draw precedence.
	 *
	 * <p>
	 * The implementation subtracts {@code win} and {@code draw} successes so that
	 * higher win/draw totals sort ahead of weaker evaluations.
	 * </p>
	 *
	 * @param chances the other {@code Chances} instance to compare with
	 * @return negative if this is weaker, zero if equal, positive if stronger
	 * @throws NullPointerException if {@code chances} is {@code null}
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
	 * Parses a textual win-draw-loss (W/D/L) triple into a normalized
	 * {@code Chances} instance.
	 *
	 * <p>
	 * Supported syntaxes (case-insensitive, extra words ignored):
	 * </p>
	 * <ul>
	 * <li>{@code 1000/0/0}, {@code 790/200/10}, etc.</li>
	 * <li>{@code wdl 790 200 10}</li>
	 * <li>{@code win 79% draw 20% loss 1%}</li>
	 * <li>{@code 79 20 1} (interpreted as percentages when the sum is 100)</li>
	 * </ul>
	 *
	 * <p>
	 * Parsing steps:
	 * <ol>
	 * <li>Extract the first three integers.</li>
	 * <li>If percentages or the sum equals 100, scale to basis points (0..1000).</li>
	 * <li>If the sum differs from 1000, normalize proportionally so it becomes 1000.</li>
	 * <li>Validate each value is within 0..1000.</li>
	 * </ol>
	 * </p>
	 *
	 * <p>
	 * Examples:
	 * </p>
	 *
	 * <pre>{@code
	 * Chances.parse("wdl 790 200 10")        // -> 790/200/10
	 * Chances.parse("79 20 1")               // -> 790/200/10
	 * Chances.parse("win 55% draw 25% loss 20%") // -> 550/250/200
	 * Chances.parse("1000/0/0")              // -> 1000/0/0
	 * }</pre>
	 *
	 * @param s textual W/D/L triple in one of the accepted formats
	 * @return normalized {@code Chances} whose three components sum to 1000
	 * @throws IllegalArgumentException if {@code s} is {@code null}, fewer than
	 *                                  three integers are found, the pre-normalized
	 *                                  sum is non-positive, or any component falls
	 *                                  outside {@code 0..1000} after normalization
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
