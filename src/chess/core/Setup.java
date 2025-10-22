package chess.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Container class containing the standard chess setup, as well as all Chess960
 * board setups.
 * 
 * @since 2024
 * @author Lennart A. Conrad
 * @see Position
 */
public class Setup {

	/**
	 * Private constructor to prevent instantiation of this class.
	 */
	private Setup() {
		// Prevent instantiation
	}

	/**
	 * The standard starting position in FEN format.
	 */
	private static final String STANDARD_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

	/**
	 * An array containing all Chess960 starting {@code Positions}. Each
	 * {@code Position} is indexed with their corresponding ID numbers.
	 * <p>
	 * Also, the {@code Position} at index 518 is equivalent to the standard
	 * starting {@code Position} in normal chess. The only difference is that its
	 * Chess960 attribute is set to true and that the castling indexes are changed
	 * to the rook positions, but apart from that it is completely identical.
	 * </p>
	 */
	private static final Position[] CHESS_960_POSITIONS = new Position[] {
			new Position("bbqnnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNNRKR w HFhf - 0 1"),
			new Position("bqnbnrkr/pppppppp/8/8/8/8/PPPPPPPP/BQNBNRKR w HFhf - 0 1"),
			new Position("bqnnrbkr/pppppppp/8/8/8/8/PPPPPPPP/BQNNRBKR w HEhe - 0 1"),
			new Position("bqnnrkrb/pppppppp/8/8/8/8/PPPPPPPP/BQNNRKRB w GEge - 0 1"),
			new Position("qbbnnrkr/pppppppp/8/8/8/8/PPPPPPPP/QBBNNRKR w HFhf - 0 1"),
			new Position("qnbbnrkr/pppppppp/8/8/8/8/PPPPPPPP/QNBBNRKR w HFhf - 0 1"),
			new Position("qnbnrbkr/pppppppp/8/8/8/8/PPPPPPPP/QNBNRBKR w HEhe - 0 1"),
			new Position("qnbnrkrb/pppppppp/8/8/8/8/PPPPPPPP/QNBNRKRB w GEge - 0 1"),
			new Position("qbnnbrkr/pppppppp/8/8/8/8/PPPPPPPP/QBNNBRKR w HFhf - 0 1"),
			new Position("qnnbbrkr/pppppppp/8/8/8/8/PPPPPPPP/QNNBBRKR w HFhf - 0 1"),
			new Position("qnnrbbkr/pppppppp/8/8/8/8/PPPPPPPP/QNNRBBKR w HDhd - 0 1"),
			new Position("qnnrbkrb/pppppppp/8/8/8/8/PPPPPPPP/QNNRBKRB w GDgd - 0 1"),
			new Position("qbnnrkbr/pppppppp/8/8/8/8/PPPPPPPP/QBNNRKBR w HEhe - 0 1"),
			new Position("qnnbrkbr/pppppppp/8/8/8/8/PPPPPPPP/QNNBRKBR w HEhe - 0 1"),
			new Position("qnnrkbbr/pppppppp/8/8/8/8/PPPPPPPP/QNNRKBBR w HDhd - 0 1"),
			new Position("qnnrkrbb/pppppppp/8/8/8/8/PPPPPPPP/QNNRKRBB w FDfd - 0 1"),
			new Position("bbnqnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBNQNRKR w HFhf - 0 1"),
			new Position("bnqbnrkr/pppppppp/8/8/8/8/PPPPPPPP/BNQBNRKR w HFhf - 0 1"),
			new Position("bnqnrbkr/pppppppp/8/8/8/8/PPPPPPPP/BNQNRBKR w HEhe - 0 1"),
			new Position("bnqnrkrb/pppppppp/8/8/8/8/PPPPPPPP/BNQNRKRB w GEge - 0 1"),
			new Position("nbbqnrkr/pppppppp/8/8/8/8/PPPPPPPP/NBBQNRKR w HFhf - 0 1"),
			new Position("nqbbnrkr/pppppppp/8/8/8/8/PPPPPPPP/NQBBNRKR w HFhf - 0 1"),
			new Position("nqbnrbkr/pppppppp/8/8/8/8/PPPPPPPP/NQBNRBKR w HEhe - 0 1"),
			new Position("nqbnrkrb/pppppppp/8/8/8/8/PPPPPPPP/NQBNRKRB w GEge - 0 1"),
			new Position("nbqnbrkr/pppppppp/8/8/8/8/PPPPPPPP/NBQNBRKR w HFhf - 0 1"),
			new Position("nqnbbrkr/pppppppp/8/8/8/8/PPPPPPPP/NQNBBRKR w HFhf - 0 1"),
			new Position("nqnrbbkr/pppppppp/8/8/8/8/PPPPPPPP/NQNRBBKR w HDhd - 0 1"),
			new Position("nqnrbkrb/pppppppp/8/8/8/8/PPPPPPPP/NQNRBKRB w GDgd - 0 1"),
			new Position("nbqnrkbr/pppppppp/8/8/8/8/PPPPPPPP/NBQNRKBR w HEhe - 0 1"),
			new Position("nqnbrkbr/pppppppp/8/8/8/8/PPPPPPPP/NQNBRKBR w HEhe - 0 1"),
			new Position("nqnrkbbr/pppppppp/8/8/8/8/PPPPPPPP/NQNRKBBR w HDhd - 0 1"),
			new Position("nqnrkrbb/pppppppp/8/8/8/8/PPPPPPPP/NQNRKRBB w FDfd - 0 1"),
			new Position("bbnnqrkr/pppppppp/8/8/8/8/PPPPPPPP/BBNNQRKR w HFhf - 0 1"),
			new Position("bnnbqrkr/pppppppp/8/8/8/8/PPPPPPPP/BNNBQRKR w HFhf - 0 1"),
			new Position("bnnqrbkr/pppppppp/8/8/8/8/PPPPPPPP/BNNQRBKR w HEhe - 0 1"),
			new Position("bnnqrkrb/pppppppp/8/8/8/8/PPPPPPPP/BNNQRKRB w GEge - 0 1"),
			new Position("nbbnqrkr/pppppppp/8/8/8/8/PPPPPPPP/NBBNQRKR w HFhf - 0 1"),
			new Position("nnbbqrkr/pppppppp/8/8/8/8/PPPPPPPP/NNBBQRKR w HFhf - 0 1"),
			new Position("nnbqrbkr/pppppppp/8/8/8/8/PPPPPPPP/NNBQRBKR w HEhe - 0 1"),
			new Position("nnbqrkrb/pppppppp/8/8/8/8/PPPPPPPP/NNBQRKRB w GEge - 0 1"),
			new Position("nbnqbrkr/pppppppp/8/8/8/8/PPPPPPPP/NBNQBRKR w HFhf - 0 1"),
			new Position("nnqbbrkr/pppppppp/8/8/8/8/PPPPPPPP/NNQBBRKR w HFhf - 0 1"),
			new Position("nnqrbbkr/pppppppp/8/8/8/8/PPPPPPPP/NNQRBBKR w HDhd - 0 1"),
			new Position("nnqrbkrb/pppppppp/8/8/8/8/PPPPPPPP/NNQRBKRB w GDgd - 0 1"),
			new Position("nbnqrkbr/pppppppp/8/8/8/8/PPPPPPPP/NBNQRKBR w HEhe - 0 1"),
			new Position("nnqbrkbr/pppppppp/8/8/8/8/PPPPPPPP/NNQBRKBR w HEhe - 0 1"),
			new Position("nnqrkbbr/pppppppp/8/8/8/8/PPPPPPPP/NNQRKBBR w HDhd - 0 1"),
			new Position("nnqrkrbb/pppppppp/8/8/8/8/PPPPPPPP/NNQRKRBB w FDfd - 0 1"),
			new Position("bbnnrqkr/pppppppp/8/8/8/8/PPPPPPPP/BBNNRQKR w HEhe - 0 1"),
			new Position("bnnbrqkr/pppppppp/8/8/8/8/PPPPPPPP/BNNBRQKR w HEhe - 0 1"),
			new Position("bnnrqbkr/pppppppp/8/8/8/8/PPPPPPPP/BNNRQBKR w HDhd - 0 1"),
			new Position("bnnrqkrb/pppppppp/8/8/8/8/PPPPPPPP/BNNRQKRB w GDgd - 0 1"),
			new Position("nbbnrqkr/pppppppp/8/8/8/8/PPPPPPPP/NBBNRQKR w HEhe - 0 1"),
			new Position("nnbbrqkr/pppppppp/8/8/8/8/PPPPPPPP/NNBBRQKR w HEhe - 0 1"),
			new Position("nnbrqbkr/pppppppp/8/8/8/8/PPPPPPPP/NNBRQBKR w HDhd - 0 1"),
			new Position("nnbrqkrb/pppppppp/8/8/8/8/PPPPPPPP/NNBRQKRB w GDgd - 0 1"),
			new Position("nbnrbqkr/pppppppp/8/8/8/8/PPPPPPPP/NBNRBQKR w HDhd - 0 1"),
			new Position("nnrbbqkr/pppppppp/8/8/8/8/PPPPPPPP/NNRBBQKR w HChc - 0 1"),
			new Position("nnrqbbkr/pppppppp/8/8/8/8/PPPPPPPP/NNRQBBKR w HChc - 0 1"),
			new Position("nnrqbkrb/pppppppp/8/8/8/8/PPPPPPPP/NNRQBKRB w GCgc - 0 1"),
			new Position("nbnrqkbr/pppppppp/8/8/8/8/PPPPPPPP/NBNRQKBR w HDhd - 0 1"),
			new Position("nnrbqkbr/pppppppp/8/8/8/8/PPPPPPPP/NNRBQKBR w HChc - 0 1"),
			new Position("nnrqkbbr/pppppppp/8/8/8/8/PPPPPPPP/NNRQKBBR w HChc - 0 1"),
			new Position("nnrqkrbb/pppppppp/8/8/8/8/PPPPPPPP/NNRQKRBB w FCfc - 0 1"),
			new Position("bbnnrkqr/pppppppp/8/8/8/8/PPPPPPPP/BBNNRKQR w HEhe - 0 1"),
			new Position("bnnbrkqr/pppppppp/8/8/8/8/PPPPPPPP/BNNBRKQR w HEhe - 0 1"),
			new Position("bnnrkbqr/pppppppp/8/8/8/8/PPPPPPPP/BNNRKBQR w HDhd - 0 1"),
			new Position("bnnrkqrb/pppppppp/8/8/8/8/PPPPPPPP/BNNRKQRB w GDgd - 0 1"),
			new Position("nbbnrkqr/pppppppp/8/8/8/8/PPPPPPPP/NBBNRKQR w HEhe - 0 1"),
			new Position("nnbbrkqr/pppppppp/8/8/8/8/PPPPPPPP/NNBBRKQR w HEhe - 0 1"),
			new Position("nnbrkbqr/pppppppp/8/8/8/8/PPPPPPPP/NNBRKBQR w HDhd - 0 1"),
			new Position("nnbrkqrb/pppppppp/8/8/8/8/PPPPPPPP/NNBRKQRB w GDgd - 0 1"),
			new Position("nbnrbkqr/pppppppp/8/8/8/8/PPPPPPPP/NBNRBKQR w HDhd - 0 1"),
			new Position("nnrbbkqr/pppppppp/8/8/8/8/PPPPPPPP/NNRBBKQR w HChc - 0 1"),
			new Position("nnrkbbqr/pppppppp/8/8/8/8/PPPPPPPP/NNRKBBQR w HChc - 0 1"),
			new Position("nnrkbqrb/pppppppp/8/8/8/8/PPPPPPPP/NNRKBQRB w GCgc - 0 1"),
			new Position("nbnrkqbr/pppppppp/8/8/8/8/PPPPPPPP/NBNRKQBR w HDhd - 0 1"),
			new Position("nnrbkqbr/pppppppp/8/8/8/8/PPPPPPPP/NNRBKQBR w HChc - 0 1"),
			new Position("nnrkqbbr/pppppppp/8/8/8/8/PPPPPPPP/NNRKQBBR w HChc - 0 1"),
			new Position("nnrkqrbb/pppppppp/8/8/8/8/PPPPPPPP/NNRKQRBB w FCfc - 0 1"),
			new Position("bbnnrkrq/pppppppp/8/8/8/8/PPPPPPPP/BBNNRKRQ w GEge - 0 1"),
			new Position("bnnbrkrq/pppppppp/8/8/8/8/PPPPPPPP/BNNBRKRQ w GEge - 0 1"),
			new Position("bnnrkbrq/pppppppp/8/8/8/8/PPPPPPPP/BNNRKBRQ w GDgd - 0 1"),
			new Position("bnnrkrqb/pppppppp/8/8/8/8/PPPPPPPP/BNNRKRQB w FDfd - 0 1"),
			new Position("nbbnrkrq/pppppppp/8/8/8/8/PPPPPPPP/NBBNRKRQ w GEge - 0 1"),
			new Position("nnbbrkrq/pppppppp/8/8/8/8/PPPPPPPP/NNBBRKRQ w GEge - 0 1"),
			new Position("nnbrkbrq/pppppppp/8/8/8/8/PPPPPPPP/NNBRKBRQ w GDgd - 0 1"),
			new Position("nnbrkrqb/pppppppp/8/8/8/8/PPPPPPPP/NNBRKRQB w FDfd - 0 1"),
			new Position("nbnrbkrq/pppppppp/8/8/8/8/PPPPPPPP/NBNRBKRQ w GDgd - 0 1"),
			new Position("nnrbbkrq/pppppppp/8/8/8/8/PPPPPPPP/NNRBBKRQ w GCgc - 0 1"),
			new Position("nnrkbbrq/pppppppp/8/8/8/8/PPPPPPPP/NNRKBBRQ w GCgc - 0 1"),
			new Position("nnrkbrqb/pppppppp/8/8/8/8/PPPPPPPP/NNRKBRQB w FCfc - 0 1"),
			new Position("nbnrkrbq/pppppppp/8/8/8/8/PPPPPPPP/NBNRKRBQ w FDfd - 0 1"),
			new Position("nnrbkrbq/pppppppp/8/8/8/8/PPPPPPPP/NNRBKRBQ w FCfc - 0 1"),
			new Position("nnrkrbbq/pppppppp/8/8/8/8/PPPPPPPP/NNRKRBBQ w ECec - 0 1"),
			new Position("nnrkrqbb/pppppppp/8/8/8/8/PPPPPPPP/NNRKRQBB w ECec - 0 1"),
			new Position("bbqnrnkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNRNKR w HEhe - 0 1"),
			new Position("bqnbrnkr/pppppppp/8/8/8/8/PPPPPPPP/BQNBRNKR w HEhe - 0 1"),
			new Position("bqnrnbkr/pppppppp/8/8/8/8/PPPPPPPP/BQNRNBKR w HDhd - 0 1"),
			new Position("bqnrnkrb/pppppppp/8/8/8/8/PPPPPPPP/BQNRNKRB w GDgd - 0 1"),
			new Position("qbbnrnkr/pppppppp/8/8/8/8/PPPPPPPP/QBBNRNKR w HEhe - 0 1"),
			new Position("qnbbrnkr/pppppppp/8/8/8/8/PPPPPPPP/QNBBRNKR w HEhe - 0 1"),
			new Position("qnbrnbkr/pppppppp/8/8/8/8/PPPPPPPP/QNBRNBKR w HDhd - 0 1"),
			new Position("qnbrnkrb/pppppppp/8/8/8/8/PPPPPPPP/QNBRNKRB w GDgd - 0 1"),
			new Position("qbnrbnkr/pppppppp/8/8/8/8/PPPPPPPP/QBNRBNKR w HDhd - 0 1"),
			new Position("qnrbbnkr/pppppppp/8/8/8/8/PPPPPPPP/QNRBBNKR w HChc - 0 1"),
			new Position("qnrnbbkr/pppppppp/8/8/8/8/PPPPPPPP/QNRNBBKR w HChc - 0 1"),
			new Position("qnrnbkrb/pppppppp/8/8/8/8/PPPPPPPP/QNRNBKRB w GCgc - 0 1"),
			new Position("qbnrnkbr/pppppppp/8/8/8/8/PPPPPPPP/QBNRNKBR w HDhd - 0 1"),
			new Position("qnrbnkbr/pppppppp/8/8/8/8/PPPPPPPP/QNRBNKBR w HChc - 0 1"),
			new Position("qnrnkbbr/pppppppp/8/8/8/8/PPPPPPPP/QNRNKBBR w HChc - 0 1"),
			new Position("qnrnkrbb/pppppppp/8/8/8/8/PPPPPPPP/QNRNKRBB w FCfc - 0 1"),
			new Position("bbnqrnkr/pppppppp/8/8/8/8/PPPPPPPP/BBNQRNKR w HEhe - 0 1"),
			new Position("bnqbrnkr/pppppppp/8/8/8/8/PPPPPPPP/BNQBRNKR w HEhe - 0 1"),
			new Position("bnqrnbkr/pppppppp/8/8/8/8/PPPPPPPP/BNQRNBKR w HDhd - 0 1"),
			new Position("bnqrnkrb/pppppppp/8/8/8/8/PPPPPPPP/BNQRNKRB w GDgd - 0 1"),
			new Position("nbbqrnkr/pppppppp/8/8/8/8/PPPPPPPP/NBBQRNKR w HEhe - 0 1"),
			new Position("nqbbrnkr/pppppppp/8/8/8/8/PPPPPPPP/NQBBRNKR w HEhe - 0 1"),
			new Position("nqbrnbkr/pppppppp/8/8/8/8/PPPPPPPP/NQBRNBKR w HDhd - 0 1"),
			new Position("nqbrnkrb/pppppppp/8/8/8/8/PPPPPPPP/NQBRNKRB w GDgd - 0 1"),
			new Position("nbqrbnkr/pppppppp/8/8/8/8/PPPPPPPP/NBQRBNKR w HDhd - 0 1"),
			new Position("nqrbbnkr/pppppppp/8/8/8/8/PPPPPPPP/NQRBBNKR w HChc - 0 1"),
			new Position("nqrnbbkr/pppppppp/8/8/8/8/PPPPPPPP/NQRNBBKR w HChc - 0 1"),
			new Position("nqrnbkrb/pppppppp/8/8/8/8/PPPPPPPP/NQRNBKRB w GCgc - 0 1"),
			new Position("nbqrnkbr/pppppppp/8/8/8/8/PPPPPPPP/NBQRNKBR w HDhd - 0 1"),
			new Position("nqrbnkbr/pppppppp/8/8/8/8/PPPPPPPP/NQRBNKBR w HChc - 0 1"),
			new Position("nqrnkbbr/pppppppp/8/8/8/8/PPPPPPPP/NQRNKBBR w HChc - 0 1"),
			new Position("nqrnkrbb/pppppppp/8/8/8/8/PPPPPPPP/NQRNKRBB w FCfc - 0 1"),
			new Position("bbnrqnkr/pppppppp/8/8/8/8/PPPPPPPP/BBNRQNKR w HDhd - 0 1"),
			new Position("bnrbqnkr/pppppppp/8/8/8/8/PPPPPPPP/BNRBQNKR w HChc - 0 1"),
			new Position("bnrqnbkr/pppppppp/8/8/8/8/PPPPPPPP/BNRQNBKR w HChc - 0 1"),
			new Position("bnrqnkrb/pppppppp/8/8/8/8/PPPPPPPP/BNRQNKRB w GCgc - 0 1"),
			new Position("nbbrqnkr/pppppppp/8/8/8/8/PPPPPPPP/NBBRQNKR w HDhd - 0 1"),
			new Position("nrbbqnkr/pppppppp/8/8/8/8/PPPPPPPP/NRBBQNKR w HBhb - 0 1"),
			new Position("nrbqnbkr/pppppppp/8/8/8/8/PPPPPPPP/NRBQNBKR w HBhb - 0 1"),
			new Position("nrbqnkrb/pppppppp/8/8/8/8/PPPPPPPP/NRBQNKRB w GBgb - 0 1"),
			new Position("nbrqbnkr/pppppppp/8/8/8/8/PPPPPPPP/NBRQBNKR w HChc - 0 1"),
			new Position("nrqbbnkr/pppppppp/8/8/8/8/PPPPPPPP/NRQBBNKR w HBhb - 0 1"),
			new Position("nrqnbbkr/pppppppp/8/8/8/8/PPPPPPPP/NRQNBBKR w HBhb - 0 1"),
			new Position("nrqnbkrb/pppppppp/8/8/8/8/PPPPPPPP/NRQNBKRB w GBgb - 0 1"),
			new Position("nbrqnkbr/pppppppp/8/8/8/8/PPPPPPPP/NBRQNKBR w HChc - 0 1"),
			new Position("nrqbnkbr/pppppppp/8/8/8/8/PPPPPPPP/NRQBNKBR w HBhb - 0 1"),
			new Position("nrqnkbbr/pppppppp/8/8/8/8/PPPPPPPP/NRQNKBBR w HBhb - 0 1"),
			new Position("nrqnkrbb/pppppppp/8/8/8/8/PPPPPPPP/NRQNKRBB w FBfb - 0 1"),
			new Position("bbnrnqkr/pppppppp/8/8/8/8/PPPPPPPP/BBNRNQKR w HDhd - 0 1"),
			new Position("bnrbnqkr/pppppppp/8/8/8/8/PPPPPPPP/BNRBNQKR w HChc - 0 1"),
			new Position("bnrnqbkr/pppppppp/8/8/8/8/PPPPPPPP/BNRNQBKR w HChc - 0 1"),
			new Position("bnrnqkrb/pppppppp/8/8/8/8/PPPPPPPP/BNRNQKRB w GCgc - 0 1"),
			new Position("nbbrnqkr/pppppppp/8/8/8/8/PPPPPPPP/NBBRNQKR w HDhd - 0 1"),
			new Position("nrbbnqkr/pppppppp/8/8/8/8/PPPPPPPP/NRBBNQKR w HBhb - 0 1"),
			new Position("nrbnqbkr/pppppppp/8/8/8/8/PPPPPPPP/NRBNQBKR w HBhb - 0 1"),
			new Position("nrbnqkrb/pppppppp/8/8/8/8/PPPPPPPP/NRBNQKRB w GBgb - 0 1"),
			new Position("nbrnbqkr/pppppppp/8/8/8/8/PPPPPPPP/NBRNBQKR w HChc - 0 1"),
			new Position("nrnbbqkr/pppppppp/8/8/8/8/PPPPPPPP/NRNBBQKR w HBhb - 0 1"),
			new Position("nrnqbbkr/pppppppp/8/8/8/8/PPPPPPPP/NRNQBBKR w HBhb - 0 1"),
			new Position("nrnqbkrb/pppppppp/8/8/8/8/PPPPPPPP/NRNQBKRB w GBgb - 0 1"),
			new Position("nbrnqkbr/pppppppp/8/8/8/8/PPPPPPPP/NBRNQKBR w HChc - 0 1"),
			new Position("nrnbqkbr/pppppppp/8/8/8/8/PPPPPPPP/NRNBQKBR w HBhb - 0 1"),
			new Position("nrnqkbbr/pppppppp/8/8/8/8/PPPPPPPP/NRNQKBBR w HBhb - 0 1"),
			new Position("nrnqkrbb/pppppppp/8/8/8/8/PPPPPPPP/NRNQKRBB w FBfb - 0 1"),
			new Position("bbnrnkqr/pppppppp/8/8/8/8/PPPPPPPP/BBNRNKQR w HDhd - 0 1"),
			new Position("bnrbnkqr/pppppppp/8/8/8/8/PPPPPPPP/BNRBNKQR w HChc - 0 1"),
			new Position("bnrnkbqr/pppppppp/8/8/8/8/PPPPPPPP/BNRNKBQR w HChc - 0 1"),
			new Position("bnrnkqrb/pppppppp/8/8/8/8/PPPPPPPP/BNRNKQRB w GCgc - 0 1"),
			new Position("nbbrnkqr/pppppppp/8/8/8/8/PPPPPPPP/NBBRNKQR w HDhd - 0 1"),
			new Position("nrbbnkqr/pppppppp/8/8/8/8/PPPPPPPP/NRBBNKQR w HBhb - 0 1"),
			new Position("nrbnkbqr/pppppppp/8/8/8/8/PPPPPPPP/NRBNKBQR w HBhb - 0 1"),
			new Position("nrbnkqrb/pppppppp/8/8/8/8/PPPPPPPP/NRBNKQRB w GBgb - 0 1"),
			new Position("nbrnbkqr/pppppppp/8/8/8/8/PPPPPPPP/NBRNBKQR w HChc - 0 1"),
			new Position("nrnbbkqr/pppppppp/8/8/8/8/PPPPPPPP/NRNBBKQR w HBhb - 0 1"),
			new Position("nrnkbbqr/pppppppp/8/8/8/8/PPPPPPPP/NRNKBBQR w HBhb - 0 1"),
			new Position("nrnkbqrb/pppppppp/8/8/8/8/PPPPPPPP/NRNKBQRB w GBgb - 0 1"),
			new Position("nbrnkqbr/pppppppp/8/8/8/8/PPPPPPPP/NBRNKQBR w HChc - 0 1"),
			new Position("nrnbkqbr/pppppppp/8/8/8/8/PPPPPPPP/NRNBKQBR w HBhb - 0 1"),
			new Position("nrnkqbbr/pppppppp/8/8/8/8/PPPPPPPP/NRNKQBBR w HBhb - 0 1"),
			new Position("nrnkqrbb/pppppppp/8/8/8/8/PPPPPPPP/NRNKQRBB w FBfb - 0 1"),
			new Position("bbnrnkrq/pppppppp/8/8/8/8/PPPPPPPP/BBNRNKRQ w GDgd - 0 1"),
			new Position("bnrbnkrq/pppppppp/8/8/8/8/PPPPPPPP/BNRBNKRQ w GCgc - 0 1"),
			new Position("bnrnkbrq/pppppppp/8/8/8/8/PPPPPPPP/BNRNKBRQ w GCgc - 0 1"),
			new Position("bnrnkrqb/pppppppp/8/8/8/8/PPPPPPPP/BNRNKRQB w FCfc - 0 1"),
			new Position("nbbrnkrq/pppppppp/8/8/8/8/PPPPPPPP/NBBRNKRQ w GDgd - 0 1"),
			new Position("nrbbnkrq/pppppppp/8/8/8/8/PPPPPPPP/NRBBNKRQ w GBgb - 0 1"),
			new Position("nrbnkbrq/pppppppp/8/8/8/8/PPPPPPPP/NRBNKBRQ w GBgb - 0 1"),
			new Position("nrbnkrqb/pppppppp/8/8/8/8/PPPPPPPP/NRBNKRQB w FBfb - 0 1"),
			new Position("nbrnbkrq/pppppppp/8/8/8/8/PPPPPPPP/NBRNBKRQ w GCgc - 0 1"),
			new Position("nrnbbkrq/pppppppp/8/8/8/8/PPPPPPPP/NRNBBKRQ w GBgb - 0 1"),
			new Position("nrnkbbrq/pppppppp/8/8/8/8/PPPPPPPP/NRNKBBRQ w GBgb - 0 1"),
			new Position("nrnkbrqb/pppppppp/8/8/8/8/PPPPPPPP/NRNKBRQB w FBfb - 0 1"),
			new Position("nbrnkrbq/pppppppp/8/8/8/8/PPPPPPPP/NBRNKRBQ w FCfc - 0 1"),
			new Position("nrnbkrbq/pppppppp/8/8/8/8/PPPPPPPP/NRNBKRBQ w FBfb - 0 1"),
			new Position("nrnkrbbq/pppppppp/8/8/8/8/PPPPPPPP/NRNKRBBQ w EBeb - 0 1"),
			new Position("nrnkrqbb/pppppppp/8/8/8/8/PPPPPPPP/NRNKRQBB w EBeb - 0 1"),
			new Position("bbqnrknr/pppppppp/8/8/8/8/PPPPPPPP/BBQNRKNR w HEhe - 0 1"),
			new Position("bqnbrknr/pppppppp/8/8/8/8/PPPPPPPP/BQNBRKNR w HEhe - 0 1"),
			new Position("bqnrkbnr/pppppppp/8/8/8/8/PPPPPPPP/BQNRKBNR w HDhd - 0 1"),
			new Position("bqnrknrb/pppppppp/8/8/8/8/PPPPPPPP/BQNRKNRB w GDgd - 0 1"),
			new Position("qbbnrknr/pppppppp/8/8/8/8/PPPPPPPP/QBBNRKNR w HEhe - 0 1"),
			new Position("qnbbrknr/pppppppp/8/8/8/8/PPPPPPPP/QNBBRKNR w HEhe - 0 1"),
			new Position("qnbrkbnr/pppppppp/8/8/8/8/PPPPPPPP/QNBRKBNR w HDhd - 0 1"),
			new Position("qnbrknrb/pppppppp/8/8/8/8/PPPPPPPP/QNBRKNRB w GDgd - 0 1"),
			new Position("qbnrbknr/pppppppp/8/8/8/8/PPPPPPPP/QBNRBKNR w HDhd - 0 1"),
			new Position("qnrbbknr/pppppppp/8/8/8/8/PPPPPPPP/QNRBBKNR w HChc - 0 1"),
			new Position("qnrkbbnr/pppppppp/8/8/8/8/PPPPPPPP/QNRKBBNR w HChc - 0 1"),
			new Position("qnrkbnrb/pppppppp/8/8/8/8/PPPPPPPP/QNRKBNRB w GCgc - 0 1"),
			new Position("qbnrknbr/pppppppp/8/8/8/8/PPPPPPPP/QBNRKNBR w HDhd - 0 1"),
			new Position("qnrbknbr/pppppppp/8/8/8/8/PPPPPPPP/QNRBKNBR w HChc - 0 1"),
			new Position("qnrknbbr/pppppppp/8/8/8/8/PPPPPPPP/QNRKNBBR w HChc - 0 1"),
			new Position("qnrknrbb/pppppppp/8/8/8/8/PPPPPPPP/QNRKNRBB w FCfc - 0 1"),
			new Position("bbnqrknr/pppppppp/8/8/8/8/PPPPPPPP/BBNQRKNR w HEhe - 0 1"),
			new Position("bnqbrknr/pppppppp/8/8/8/8/PPPPPPPP/BNQBRKNR w HEhe - 0 1"),
			new Position("bnqrkbnr/pppppppp/8/8/8/8/PPPPPPPP/BNQRKBNR w HDhd - 0 1"),
			new Position("bnqrknrb/pppppppp/8/8/8/8/PPPPPPPP/BNQRKNRB w GDgd - 0 1"),
			new Position("nbbqrknr/pppppppp/8/8/8/8/PPPPPPPP/NBBQRKNR w HEhe - 0 1"),
			new Position("nqbbrknr/pppppppp/8/8/8/8/PPPPPPPP/NQBBRKNR w HEhe - 0 1"),
			new Position("nqbrkbnr/pppppppp/8/8/8/8/PPPPPPPP/NQBRKBNR w HDhd - 0 1"),
			new Position("nqbrknrb/pppppppp/8/8/8/8/PPPPPPPP/NQBRKNRB w GDgd - 0 1"),
			new Position("nbqrbknr/pppppppp/8/8/8/8/PPPPPPPP/NBQRBKNR w HDhd - 0 1"),
			new Position("nqrbbknr/pppppppp/8/8/8/8/PPPPPPPP/NQRBBKNR w HChc - 0 1"),
			new Position("nqrkbbnr/pppppppp/8/8/8/8/PPPPPPPP/NQRKBBNR w HChc - 0 1"),
			new Position("nqrkbnrb/pppppppp/8/8/8/8/PPPPPPPP/NQRKBNRB w GCgc - 0 1"),
			new Position("nbqrknbr/pppppppp/8/8/8/8/PPPPPPPP/NBQRKNBR w HDhd - 0 1"),
			new Position("nqrbknbr/pppppppp/8/8/8/8/PPPPPPPP/NQRBKNBR w HChc - 0 1"),
			new Position("nqrknbbr/pppppppp/8/8/8/8/PPPPPPPP/NQRKNBBR w HChc - 0 1"),
			new Position("nqrknrbb/pppppppp/8/8/8/8/PPPPPPPP/NQRKNRBB w FCfc - 0 1"),
			new Position("bbnrqknr/pppppppp/8/8/8/8/PPPPPPPP/BBNRQKNR w HDhd - 0 1"),
			new Position("bnrbqknr/pppppppp/8/8/8/8/PPPPPPPP/BNRBQKNR w HChc - 0 1"),
			new Position("bnrqkbnr/pppppppp/8/8/8/8/PPPPPPPP/BNRQKBNR w HChc - 0 1"),
			new Position("bnrqknrb/pppppppp/8/8/8/8/PPPPPPPP/BNRQKNRB w GCgc - 0 1"),
			new Position("nbbrqknr/pppppppp/8/8/8/8/PPPPPPPP/NBBRQKNR w HDhd - 0 1"),
			new Position("nrbbqknr/pppppppp/8/8/8/8/PPPPPPPP/NRBBQKNR w HBhb - 0 1"),
			new Position("nrbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/NRBQKBNR w HBhb - 0 1"),
			new Position("nrbqknrb/pppppppp/8/8/8/8/PPPPPPPP/NRBQKNRB w GBgb - 0 1"),
			new Position("nbrqbknr/pppppppp/8/8/8/8/PPPPPPPP/NBRQBKNR w HChc - 0 1"),
			new Position("nrqbbknr/pppppppp/8/8/8/8/PPPPPPPP/NRQBBKNR w HBhb - 0 1"),
			new Position("nrqkbbnr/pppppppp/8/8/8/8/PPPPPPPP/NRQKBBNR w HBhb - 0 1"),
			new Position("nrqkbnrb/pppppppp/8/8/8/8/PPPPPPPP/NRQKBNRB w GBgb - 0 1"),
			new Position("nbrqknbr/pppppppp/8/8/8/8/PPPPPPPP/NBRQKNBR w HChc - 0 1"),
			new Position("nrqbknbr/pppppppp/8/8/8/8/PPPPPPPP/NRQBKNBR w HBhb - 0 1"),
			new Position("nrqknbbr/pppppppp/8/8/8/8/PPPPPPPP/NRQKNBBR w HBhb - 0 1"),
			new Position("nrqknrbb/pppppppp/8/8/8/8/PPPPPPPP/NRQKNRBB w FBfb - 0 1"),
			new Position("bbnrkqnr/pppppppp/8/8/8/8/PPPPPPPP/BBNRKQNR w HDhd - 0 1"),
			new Position("bnrbkqnr/pppppppp/8/8/8/8/PPPPPPPP/BNRBKQNR w HChc - 0 1"),
			new Position("bnrkqbnr/pppppppp/8/8/8/8/PPPPPPPP/BNRKQBNR w HChc - 0 1"),
			new Position("bnrkqnrb/pppppppp/8/8/8/8/PPPPPPPP/BNRKQNRB w GCgc - 0 1"),
			new Position("nbbrkqnr/pppppppp/8/8/8/8/PPPPPPPP/NBBRKQNR w HDhd - 0 1"),
			new Position("nrbbkqnr/pppppppp/8/8/8/8/PPPPPPPP/NRBBKQNR w HBhb - 0 1"),
			new Position("nrbkqbnr/pppppppp/8/8/8/8/PPPPPPPP/NRBKQBNR w HBhb - 0 1"),
			new Position("nrbkqnrb/pppppppp/8/8/8/8/PPPPPPPP/NRBKQNRB w GBgb - 0 1"),
			new Position("nbrkbqnr/pppppppp/8/8/8/8/PPPPPPPP/NBRKBQNR w HChc - 0 1"),
			new Position("nrkbbqnr/pppppppp/8/8/8/8/PPPPPPPP/NRKBBQNR w HBhb - 0 1"),
			new Position("nrkqbbnr/pppppppp/8/8/8/8/PPPPPPPP/NRKQBBNR w HBhb - 0 1"),
			new Position("nrkqbnrb/pppppppp/8/8/8/8/PPPPPPPP/NRKQBNRB w GBgb - 0 1"),
			new Position("nbrkqnbr/pppppppp/8/8/8/8/PPPPPPPP/NBRKQNBR w HChc - 0 1"),
			new Position("nrkbqnbr/pppppppp/8/8/8/8/PPPPPPPP/NRKBQNBR w HBhb - 0 1"),
			new Position("nrkqnbbr/pppppppp/8/8/8/8/PPPPPPPP/NRKQNBBR w HBhb - 0 1"),
			new Position("nrkqnrbb/pppppppp/8/8/8/8/PPPPPPPP/NRKQNRBB w FBfb - 0 1"),
			new Position("bbnrknqr/pppppppp/8/8/8/8/PPPPPPPP/BBNRKNQR w HDhd - 0 1"),
			new Position("bnrbknqr/pppppppp/8/8/8/8/PPPPPPPP/BNRBKNQR w HChc - 0 1"),
			new Position("bnrknbqr/pppppppp/8/8/8/8/PPPPPPPP/BNRKNBQR w HChc - 0 1"),
			new Position("bnrknqrb/pppppppp/8/8/8/8/PPPPPPPP/BNRKNQRB w GCgc - 0 1"),
			new Position("nbbrknqr/pppppppp/8/8/8/8/PPPPPPPP/NBBRKNQR w HDhd - 0 1"),
			new Position("nrbbknqr/pppppppp/8/8/8/8/PPPPPPPP/NRBBKNQR w HBhb - 0 1"),
			new Position("nrbknbqr/pppppppp/8/8/8/8/PPPPPPPP/NRBKNBQR w HBhb - 0 1"),
			new Position("nrbknqrb/pppppppp/8/8/8/8/PPPPPPPP/NRBKNQRB w GBgb - 0 1"),
			new Position("nbrkbnqr/pppppppp/8/8/8/8/PPPPPPPP/NBRKBNQR w HChc - 0 1"),
			new Position("nrkbbnqr/pppppppp/8/8/8/8/PPPPPPPP/NRKBBNQR w HBhb - 0 1"),
			new Position("nrknbbqr/pppppppp/8/8/8/8/PPPPPPPP/NRKNBBQR w HBhb - 0 1"),
			new Position("nrknbqrb/pppppppp/8/8/8/8/PPPPPPPP/NRKNBQRB w GBgb - 0 1"),
			new Position("nbrknqbr/pppppppp/8/8/8/8/PPPPPPPP/NBRKNQBR w HChc - 0 1"),
			new Position("nrkbnqbr/pppppppp/8/8/8/8/PPPPPPPP/NRKBNQBR w HBhb - 0 1"),
			new Position("nrknqbbr/pppppppp/8/8/8/8/PPPPPPPP/NRKNQBBR w HBhb - 0 1"),
			new Position("nrknqrbb/pppppppp/8/8/8/8/PPPPPPPP/NRKNQRBB w FBfb - 0 1"),
			new Position("bbnrknrq/pppppppp/8/8/8/8/PPPPPPPP/BBNRKNRQ w GDgd - 0 1"),
			new Position("bnrbknrq/pppppppp/8/8/8/8/PPPPPPPP/BNRBKNRQ w GCgc - 0 1"),
			new Position("bnrknbrq/pppppppp/8/8/8/8/PPPPPPPP/BNRKNBRQ w GCgc - 0 1"),
			new Position("bnrknrqb/pppppppp/8/8/8/8/PPPPPPPP/BNRKNRQB w FCfc - 0 1"),
			new Position("nbbrknrq/pppppppp/8/8/8/8/PPPPPPPP/NBBRKNRQ w GDgd - 0 1"),
			new Position("nrbbknrq/pppppppp/8/8/8/8/PPPPPPPP/NRBBKNRQ w GBgb - 0 1"),
			new Position("nrbknbrq/pppppppp/8/8/8/8/PPPPPPPP/NRBKNBRQ w GBgb - 0 1"),
			new Position("nrbknrqb/pppppppp/8/8/8/8/PPPPPPPP/NRBKNRQB w FBfb - 0 1"),
			new Position("nbrkbnrq/pppppppp/8/8/8/8/PPPPPPPP/NBRKBNRQ w GCgc - 0 1"),
			new Position("nrkbbnrq/pppppppp/8/8/8/8/PPPPPPPP/NRKBBNRQ w GBgb - 0 1"),
			new Position("nrknbbrq/pppppppp/8/8/8/8/PPPPPPPP/NRKNBBRQ w GBgb - 0 1"),
			new Position("nrknbrqb/pppppppp/8/8/8/8/PPPPPPPP/NRKNBRQB w FBfb - 0 1"),
			new Position("nbrknrbq/pppppppp/8/8/8/8/PPPPPPPP/NBRKNRBQ w FCfc - 0 1"),
			new Position("nrkbnrbq/pppppppp/8/8/8/8/PPPPPPPP/NRKBNRBQ w FBfb - 0 1"),
			new Position("nrknrbbq/pppppppp/8/8/8/8/PPPPPPPP/NRKNRBBQ w EBeb - 0 1"),
			new Position("nrknrqbb/pppppppp/8/8/8/8/PPPPPPPP/NRKNRQBB w EBeb - 0 1"),
			new Position("bbqnrkrn/pppppppp/8/8/8/8/PPPPPPPP/BBQNRKRN w GEge - 0 1"),
			new Position("bqnbrkrn/pppppppp/8/8/8/8/PPPPPPPP/BQNBRKRN w GEge - 0 1"),
			new Position("bqnrkbrn/pppppppp/8/8/8/8/PPPPPPPP/BQNRKBRN w GDgd - 0 1"),
			new Position("bqnrkrnb/pppppppp/8/8/8/8/PPPPPPPP/BQNRKRNB w FDfd - 0 1"),
			new Position("qbbnrkrn/pppppppp/8/8/8/8/PPPPPPPP/QBBNRKRN w GEge - 0 1"),
			new Position("qnbbrkrn/pppppppp/8/8/8/8/PPPPPPPP/QNBBRKRN w GEge - 0 1"),
			new Position("qnbrkbrn/pppppppp/8/8/8/8/PPPPPPPP/QNBRKBRN w GDgd - 0 1"),
			new Position("qnbrkrnb/pppppppp/8/8/8/8/PPPPPPPP/QNBRKRNB w FDfd - 0 1"),
			new Position("qbnrbkrn/pppppppp/8/8/8/8/PPPPPPPP/QBNRBKRN w GDgd - 0 1"),
			new Position("qnrbbkrn/pppppppp/8/8/8/8/PPPPPPPP/QNRBBKRN w GCgc - 0 1"),
			new Position("qnrkbbrn/pppppppp/8/8/8/8/PPPPPPPP/QNRKBBRN w GCgc - 0 1"),
			new Position("qnrkbrnb/pppppppp/8/8/8/8/PPPPPPPP/QNRKBRNB w FCfc - 0 1"),
			new Position("qbnrkrbn/pppppppp/8/8/8/8/PPPPPPPP/QBNRKRBN w FDfd - 0 1"),
			new Position("qnrbkrbn/pppppppp/8/8/8/8/PPPPPPPP/QNRBKRBN w FCfc - 0 1"),
			new Position("qnrkrbbn/pppppppp/8/8/8/8/PPPPPPPP/QNRKRBBN w ECec - 0 1"),
			new Position("qnrkrnbb/pppppppp/8/8/8/8/PPPPPPPP/QNRKRNBB w ECec - 0 1"),
			new Position("bbnqrkrn/pppppppp/8/8/8/8/PPPPPPPP/BBNQRKRN w GEge - 0 1"),
			new Position("bnqbrkrn/pppppppp/8/8/8/8/PPPPPPPP/BNQBRKRN w GEge - 0 1"),
			new Position("bnqrkbrn/pppppppp/8/8/8/8/PPPPPPPP/BNQRKBRN w GDgd - 0 1"),
			new Position("bnqrkrnb/pppppppp/8/8/8/8/PPPPPPPP/BNQRKRNB w FDfd - 0 1"),
			new Position("nbbqrkrn/pppppppp/8/8/8/8/PPPPPPPP/NBBQRKRN w GEge - 0 1"),
			new Position("nqbbrkrn/pppppppp/8/8/8/8/PPPPPPPP/NQBBRKRN w GEge - 0 1"),
			new Position("nqbrkbrn/pppppppp/8/8/8/8/PPPPPPPP/NQBRKBRN w GDgd - 0 1"),
			new Position("nqbrkrnb/pppppppp/8/8/8/8/PPPPPPPP/NQBRKRNB w FDfd - 0 1"),
			new Position("nbqrbkrn/pppppppp/8/8/8/8/PPPPPPPP/NBQRBKRN w GDgd - 0 1"),
			new Position("nqrbbkrn/pppppppp/8/8/8/8/PPPPPPPP/NQRBBKRN w GCgc - 0 1"),
			new Position("nqrkbbrn/pppppppp/8/8/8/8/PPPPPPPP/NQRKBBRN w GCgc - 0 1"),
			new Position("nqrkbrnb/pppppppp/8/8/8/8/PPPPPPPP/NQRKBRNB w FCfc - 0 1"),
			new Position("nbqrkrbn/pppppppp/8/8/8/8/PPPPPPPP/NBQRKRBN w FDfd - 0 1"),
			new Position("nqrbkrbn/pppppppp/8/8/8/8/PPPPPPPP/NQRBKRBN w FCfc - 0 1"),
			new Position("nqrkrbbn/pppppppp/8/8/8/8/PPPPPPPP/NQRKRBBN w ECec - 0 1"),
			new Position("nqrkrnbb/pppppppp/8/8/8/8/PPPPPPPP/NQRKRNBB w ECec - 0 1"),
			new Position("bbnrqkrn/pppppppp/8/8/8/8/PPPPPPPP/BBNRQKRN w GDgd - 0 1"),
			new Position("bnrbqkrn/pppppppp/8/8/8/8/PPPPPPPP/BNRBQKRN w GCgc - 0 1"),
			new Position("bnrqkbrn/pppppppp/8/8/8/8/PPPPPPPP/BNRQKBRN w GCgc - 0 1"),
			new Position("bnrqkrnb/pppppppp/8/8/8/8/PPPPPPPP/BNRQKRNB w FCfc - 0 1"),
			new Position("nbbrqkrn/pppppppp/8/8/8/8/PPPPPPPP/NBBRQKRN w GDgd - 0 1"),
			new Position("nrbbqkrn/pppppppp/8/8/8/8/PPPPPPPP/NRBBQKRN w GBgb - 0 1"),
			new Position("nrbqkbrn/pppppppp/8/8/8/8/PPPPPPPP/NRBQKBRN w GBgb - 0 1"),
			new Position("nrbqkrnb/pppppppp/8/8/8/8/PPPPPPPP/NRBQKRNB w FBfb - 0 1"),
			new Position("nbrqbkrn/pppppppp/8/8/8/8/PPPPPPPP/NBRQBKRN w GCgc - 0 1"),
			new Position("nrqbbkrn/pppppppp/8/8/8/8/PPPPPPPP/NRQBBKRN w GBgb - 0 1"),
			new Position("nrqkbbrn/pppppppp/8/8/8/8/PPPPPPPP/NRQKBBRN w GBgb - 0 1"),
			new Position("nrqkbrnb/pppppppp/8/8/8/8/PPPPPPPP/NRQKBRNB w FBfb - 0 1"),
			new Position("nbrqkrbn/pppppppp/8/8/8/8/PPPPPPPP/NBRQKRBN w FCfc - 0 1"),
			new Position("nrqbkrbn/pppppppp/8/8/8/8/PPPPPPPP/NRQBKRBN w FBfb - 0 1"),
			new Position("nrqkrbbn/pppppppp/8/8/8/8/PPPPPPPP/NRQKRBBN w EBeb - 0 1"),
			new Position("nrqkrnbb/pppppppp/8/8/8/8/PPPPPPPP/NRQKRNBB w EBeb - 0 1"),
			new Position("bbnrkqrn/pppppppp/8/8/8/8/PPPPPPPP/BBNRKQRN w GDgd - 0 1"),
			new Position("bnrbkqrn/pppppppp/8/8/8/8/PPPPPPPP/BNRBKQRN w GCgc - 0 1"),
			new Position("bnrkqbrn/pppppppp/8/8/8/8/PPPPPPPP/BNRKQBRN w GCgc - 0 1"),
			new Position("bnrkqrnb/pppppppp/8/8/8/8/PPPPPPPP/BNRKQRNB w FCfc - 0 1"),
			new Position("nbbrkqrn/pppppppp/8/8/8/8/PPPPPPPP/NBBRKQRN w GDgd - 0 1"),
			new Position("nrbbkqrn/pppppppp/8/8/8/8/PPPPPPPP/NRBBKQRN w GBgb - 0 1"),
			new Position("nrbkqbrn/pppppppp/8/8/8/8/PPPPPPPP/NRBKQBRN w GBgb - 0 1"),
			new Position("nrbkqrnb/pppppppp/8/8/8/8/PPPPPPPP/NRBKQRNB w FBfb - 0 1"),
			new Position("nbrkbqrn/pppppppp/8/8/8/8/PPPPPPPP/NBRKBQRN w GCgc - 0 1"),
			new Position("nrkbbqrn/pppppppp/8/8/8/8/PPPPPPPP/NRKBBQRN w GBgb - 0 1"),
			new Position("nrkqbbrn/pppppppp/8/8/8/8/PPPPPPPP/NRKQBBRN w GBgb - 0 1"),
			new Position("nrkqbrnb/pppppppp/8/8/8/8/PPPPPPPP/NRKQBRNB w FBfb - 0 1"),
			new Position("nbrkqrbn/pppppppp/8/8/8/8/PPPPPPPP/NBRKQRBN w FCfc - 0 1"),
			new Position("nrkbqrbn/pppppppp/8/8/8/8/PPPPPPPP/NRKBQRBN w FBfb - 0 1"),
			new Position("nrkqrbbn/pppppppp/8/8/8/8/PPPPPPPP/NRKQRBBN w EBeb - 0 1"),
			new Position("nrkqrnbb/pppppppp/8/8/8/8/PPPPPPPP/NRKQRNBB w EBeb - 0 1"),
			new Position("bbnrkrqn/pppppppp/8/8/8/8/PPPPPPPP/BBNRKRQN w FDfd - 0 1"),
			new Position("bnrbkrqn/pppppppp/8/8/8/8/PPPPPPPP/BNRBKRQN w FCfc - 0 1"),
			new Position("bnrkrbqn/pppppppp/8/8/8/8/PPPPPPPP/BNRKRBQN w ECec - 0 1"),
			new Position("bnrkrqnb/pppppppp/8/8/8/8/PPPPPPPP/BNRKRQNB w ECec - 0 1"),
			new Position("nbbrkrqn/pppppppp/8/8/8/8/PPPPPPPP/NBBRKRQN w FDfd - 0 1"),
			new Position("nrbbkrqn/pppppppp/8/8/8/8/PPPPPPPP/NRBBKRQN w FBfb - 0 1"),
			new Position("nrbkrbqn/pppppppp/8/8/8/8/PPPPPPPP/NRBKRBQN w EBeb - 0 1"),
			new Position("nrbkrqnb/pppppppp/8/8/8/8/PPPPPPPP/NRBKRQNB w EBeb - 0 1"),
			new Position("nbrkbrqn/pppppppp/8/8/8/8/PPPPPPPP/NBRKBRQN w FCfc - 0 1"),
			new Position("nrkbbrqn/pppppppp/8/8/8/8/PPPPPPPP/NRKBBRQN w FBfb - 0 1"),
			new Position("nrkrbbqn/pppppppp/8/8/8/8/PPPPPPPP/NRKRBBQN w DBdb - 0 1"),
			new Position("nrkrbqnb/pppppppp/8/8/8/8/PPPPPPPP/NRKRBQNB w DBdb - 0 1"),
			new Position("nbrkrqbn/pppppppp/8/8/8/8/PPPPPPPP/NBRKRQBN w ECec - 0 1"),
			new Position("nrkbrqbn/pppppppp/8/8/8/8/PPPPPPPP/NRKBRQBN w EBeb - 0 1"),
			new Position("nrkrqbbn/pppppppp/8/8/8/8/PPPPPPPP/NRKRQBBN w DBdb - 0 1"),
			new Position("nrkrqnbb/pppppppp/8/8/8/8/PPPPPPPP/NRKRQNBB w DBdb - 0 1"),
			new Position("bbnrkrnq/pppppppp/8/8/8/8/PPPPPPPP/BBNRKRNQ w FDfd - 0 1"),
			new Position("bnrbkrnq/pppppppp/8/8/8/8/PPPPPPPP/BNRBKRNQ w FCfc - 0 1"),
			new Position("bnrkrbnq/pppppppp/8/8/8/8/PPPPPPPP/BNRKRBNQ w ECec - 0 1"),
			new Position("bnrkrnqb/pppppppp/8/8/8/8/PPPPPPPP/BNRKRNQB w ECec - 0 1"),
			new Position("nbbrkrnq/pppppppp/8/8/8/8/PPPPPPPP/NBBRKRNQ w FDfd - 0 1"),
			new Position("nrbbkrnq/pppppppp/8/8/8/8/PPPPPPPP/NRBBKRNQ w FBfb - 0 1"),
			new Position("nrbkrbnq/pppppppp/8/8/8/8/PPPPPPPP/NRBKRBNQ w EBeb - 0 1"),
			new Position("nrbkrnqb/pppppppp/8/8/8/8/PPPPPPPP/NRBKRNQB w EBeb - 0 1"),
			new Position("nbrkbrnq/pppppppp/8/8/8/8/PPPPPPPP/NBRKBRNQ w FCfc - 0 1"),
			new Position("nrkbbrnq/pppppppp/8/8/8/8/PPPPPPPP/NRKBBRNQ w FBfb - 0 1"),
			new Position("nrkrbbnq/pppppppp/8/8/8/8/PPPPPPPP/NRKRBBNQ w DBdb - 0 1"),
			new Position("nrkrbnqb/pppppppp/8/8/8/8/PPPPPPPP/NRKRBNQB w DBdb - 0 1"),
			new Position("nbrkrnbq/pppppppp/8/8/8/8/PPPPPPPP/NBRKRNBQ w ECec - 0 1"),
			new Position("nrkbrnbq/pppppppp/8/8/8/8/PPPPPPPP/NRKBRNBQ w EBeb - 0 1"),
			new Position("nrkrnbbq/pppppppp/8/8/8/8/PPPPPPPP/NRKRNBBQ w DBdb - 0 1"),
			new Position("nrkrnqbb/pppppppp/8/8/8/8/PPPPPPPP/NRKRNQBB w DBdb - 0 1"),
			new Position("bbqrnnkr/pppppppp/8/8/8/8/PPPPPPPP/BBQRNNKR w HDhd - 0 1"),
			new Position("bqrbnnkr/pppppppp/8/8/8/8/PPPPPPPP/BQRBNNKR w HChc - 0 1"),
			new Position("bqrnnbkr/pppppppp/8/8/8/8/PPPPPPPP/BQRNNBKR w HChc - 0 1"),
			new Position("bqrnnkrb/pppppppp/8/8/8/8/PPPPPPPP/BQRNNKRB w GCgc - 0 1"),
			new Position("qbbrnnkr/pppppppp/8/8/8/8/PPPPPPPP/QBBRNNKR w HDhd - 0 1"),
			new Position("qrbbnnkr/pppppppp/8/8/8/8/PPPPPPPP/QRBBNNKR w HBhb - 0 1"),
			new Position("qrbnnbkr/pppppppp/8/8/8/8/PPPPPPPP/QRBNNBKR w HBhb - 0 1"),
			new Position("qrbnnkrb/pppppppp/8/8/8/8/PPPPPPPP/QRBNNKRB w GBgb - 0 1"),
			new Position("qbrnbnkr/pppppppp/8/8/8/8/PPPPPPPP/QBRNBNKR w HChc - 0 1"),
			new Position("qrnbbnkr/pppppppp/8/8/8/8/PPPPPPPP/QRNBBNKR w HBhb - 0 1"),
			new Position("qrnnbbkr/pppppppp/8/8/8/8/PPPPPPPP/QRNNBBKR w HBhb - 0 1"),
			new Position("qrnnbkrb/pppppppp/8/8/8/8/PPPPPPPP/QRNNBKRB w GBgb - 0 1"),
			new Position("qbrnnkbr/pppppppp/8/8/8/8/PPPPPPPP/QBRNNKBR w HChc - 0 1"),
			new Position("qrnbnkbr/pppppppp/8/8/8/8/PPPPPPPP/QRNBNKBR w HBhb - 0 1"),
			new Position("qrnnkbbr/pppppppp/8/8/8/8/PPPPPPPP/QRNNKBBR w HBhb - 0 1"),
			new Position("qrnnkrbb/pppppppp/8/8/8/8/PPPPPPPP/QRNNKRBB w FBfb - 0 1"),
			new Position("bbrqnnkr/pppppppp/8/8/8/8/PPPPPPPP/BBRQNNKR w HChc - 0 1"),
			new Position("brqbnnkr/pppppppp/8/8/8/8/PPPPPPPP/BRQBNNKR w HBhb - 0 1"),
			new Position("brqnnbkr/pppppppp/8/8/8/8/PPPPPPPP/BRQNNBKR w HBhb - 0 1"),
			new Position("brqnnkrb/pppppppp/8/8/8/8/PPPPPPPP/BRQNNKRB w GBgb - 0 1"),
			new Position("rbbqnnkr/pppppppp/8/8/8/8/PPPPPPPP/RBBQNNKR w HAha - 0 1"),
			new Position("rqbbnnkr/pppppppp/8/8/8/8/PPPPPPPP/RQBBNNKR w HAha - 0 1"),
			new Position("rqbnnbkr/pppppppp/8/8/8/8/PPPPPPPP/RQBNNBKR w HAha - 0 1"),
			new Position("rqbnnkrb/pppppppp/8/8/8/8/PPPPPPPP/RQBNNKRB w GAga - 0 1"),
			new Position("rbqnbnkr/pppppppp/8/8/8/8/PPPPPPPP/RBQNBNKR w HAha - 0 1"),
			new Position("rqnbbnkr/pppppppp/8/8/8/8/PPPPPPPP/RQNBBNKR w HAha - 0 1"),
			new Position("rqnnbbkr/pppppppp/8/8/8/8/PPPPPPPP/RQNNBBKR w HAha - 0 1"),
			new Position("rqnnbkrb/pppppppp/8/8/8/8/PPPPPPPP/RQNNBKRB w GAga - 0 1"),
			new Position("rbqnnkbr/pppppppp/8/8/8/8/PPPPPPPP/RBQNNKBR w HAha - 0 1"),
			new Position("rqnbnkbr/pppppppp/8/8/8/8/PPPPPPPP/RQNBNKBR w HAha - 0 1"),
			new Position("rqnnkbbr/pppppppp/8/8/8/8/PPPPPPPP/RQNNKBBR w HAha - 0 1"),
			new Position("rqnnkrbb/pppppppp/8/8/8/8/PPPPPPPP/RQNNKRBB w FAfa - 0 1"),
			new Position("bbrnqnkr/pppppppp/8/8/8/8/PPPPPPPP/BBRNQNKR w HChc - 0 1"),
			new Position("brnbqnkr/pppppppp/8/8/8/8/PPPPPPPP/BRNBQNKR w HBhb - 0 1"),
			new Position("brnqnbkr/pppppppp/8/8/8/8/PPPPPPPP/BRNQNBKR w HBhb - 0 1"),
			new Position("brnqnkrb/pppppppp/8/8/8/8/PPPPPPPP/BRNQNKRB w GBgb - 0 1"),
			new Position("rbbnqnkr/pppppppp/8/8/8/8/PPPPPPPP/RBBNQNKR w HAha - 0 1"),
			new Position("rnbbqnkr/pppppppp/8/8/8/8/PPPPPPPP/RNBBQNKR w HAha - 0 1"),
			new Position("rnbqnbkr/pppppppp/8/8/8/8/PPPPPPPP/RNBQNBKR w HAha - 0 1"),
			new Position("rnbqnkrb/pppppppp/8/8/8/8/PPPPPPPP/RNBQNKRB w GAga - 0 1"),
			new Position("rbnqbnkr/pppppppp/8/8/8/8/PPPPPPPP/RBNQBNKR w HAha - 0 1"),
			new Position("rnqbbnkr/pppppppp/8/8/8/8/PPPPPPPP/RNQBBNKR w HAha - 0 1"),
			new Position("rnqnbbkr/pppppppp/8/8/8/8/PPPPPPPP/RNQNBBKR w HAha - 0 1"),
			new Position("rnqnbkrb/pppppppp/8/8/8/8/PPPPPPPP/RNQNBKRB w GAga - 0 1"),
			new Position("rbnqnkbr/pppppppp/8/8/8/8/PPPPPPPP/RBNQNKBR w HAha - 0 1"),
			new Position("rnqbnkbr/pppppppp/8/8/8/8/PPPPPPPP/RNQBNKBR w HAha - 0 1"),
			new Position("rnqnkbbr/pppppppp/8/8/8/8/PPPPPPPP/RNQNKBBR w HAha - 0 1"),
			new Position("rnqnkrbb/pppppppp/8/8/8/8/PPPPPPPP/RNQNKRBB w FAfa - 0 1"),
			new Position("bbrnnqkr/pppppppp/8/8/8/8/PPPPPPPP/BBRNNQKR w HChc - 0 1"),
			new Position("brnbnqkr/pppppppp/8/8/8/8/PPPPPPPP/BRNBNQKR w HBhb - 0 1"),
			new Position("brnnqbkr/pppppppp/8/8/8/8/PPPPPPPP/BRNNQBKR w HBhb - 0 1"),
			new Position("brnnqkrb/pppppppp/8/8/8/8/PPPPPPPP/BRNNQKRB w GBgb - 0 1"),
			new Position("rbbnnqkr/pppppppp/8/8/8/8/PPPPPPPP/RBBNNQKR w HAha - 0 1"),
			new Position("rnbbnqkr/pppppppp/8/8/8/8/PPPPPPPP/RNBBNQKR w HAha - 0 1"),
			new Position("rnbnqbkr/pppppppp/8/8/8/8/PPPPPPPP/RNBNQBKR w HAha - 0 1"),
			new Position("rnbnqkrb/pppppppp/8/8/8/8/PPPPPPPP/RNBNQKRB w GAga - 0 1"),
			new Position("rbnnbqkr/pppppppp/8/8/8/8/PPPPPPPP/RBNNBQKR w HAha - 0 1"),
			new Position("rnnbbqkr/pppppppp/8/8/8/8/PPPPPPPP/RNNBBQKR w HAha - 0 1"),
			new Position("rnnqbbkr/pppppppp/8/8/8/8/PPPPPPPP/RNNQBBKR w HAha - 0 1"),
			new Position("rnnqbkrb/pppppppp/8/8/8/8/PPPPPPPP/RNNQBKRB w GAga - 0 1"),
			new Position("rbnnqkbr/pppppppp/8/8/8/8/PPPPPPPP/RBNNQKBR w HAha - 0 1"),
			new Position("rnnbqkbr/pppppppp/8/8/8/8/PPPPPPPP/RNNBQKBR w HAha - 0 1"),
			new Position("rnnqkbbr/pppppppp/8/8/8/8/PPPPPPPP/RNNQKBBR w HAha - 0 1"),
			new Position("rnnqkrbb/pppppppp/8/8/8/8/PPPPPPPP/RNNQKRBB w FAfa - 0 1"),
			new Position("bbrnnkqr/pppppppp/8/8/8/8/PPPPPPPP/BBRNNKQR w HChc - 0 1"),
			new Position("brnbnkqr/pppppppp/8/8/8/8/PPPPPPPP/BRNBNKQR w HBhb - 0 1"),
			new Position("brnnkbqr/pppppppp/8/8/8/8/PPPPPPPP/BRNNKBQR w HBhb - 0 1"),
			new Position("brnnkqrb/pppppppp/8/8/8/8/PPPPPPPP/BRNNKQRB w GBgb - 0 1"),
			new Position("rbbnnkqr/pppppppp/8/8/8/8/PPPPPPPP/RBBNNKQR w HAha - 0 1"),
			new Position("rnbbnkqr/pppppppp/8/8/8/8/PPPPPPPP/RNBBNKQR w HAha - 0 1"),
			new Position("rnbnkbqr/pppppppp/8/8/8/8/PPPPPPPP/RNBNKBQR w HAha - 0 1"),
			new Position("rnbnkqrb/pppppppp/8/8/8/8/PPPPPPPP/RNBNKQRB w GAga - 0 1"),
			new Position("rbnnbkqr/pppppppp/8/8/8/8/PPPPPPPP/RBNNBKQR w HAha - 0 1"),
			new Position("rnnbbkqr/pppppppp/8/8/8/8/PPPPPPPP/RNNBBKQR w HAha - 0 1"),
			new Position("rnnkbbqr/pppppppp/8/8/8/8/PPPPPPPP/RNNKBBQR w HAha - 0 1"),
			new Position("rnnkbqrb/pppppppp/8/8/8/8/PPPPPPPP/RNNKBQRB w GAga - 0 1"),
			new Position("rbnnkqbr/pppppppp/8/8/8/8/PPPPPPPP/RBNNKQBR w HAha - 0 1"),
			new Position("rnnbkqbr/pppppppp/8/8/8/8/PPPPPPPP/RNNBKQBR w HAha - 0 1"),
			new Position("rnnkqbbr/pppppppp/8/8/8/8/PPPPPPPP/RNNKQBBR w HAha - 0 1"),
			new Position("rnnkqrbb/pppppppp/8/8/8/8/PPPPPPPP/RNNKQRBB w FAfa - 0 1"),
			new Position("bbrnnkrq/pppppppp/8/8/8/8/PPPPPPPP/BBRNNKRQ w GCgc - 0 1"),
			new Position("brnbnkrq/pppppppp/8/8/8/8/PPPPPPPP/BRNBNKRQ w GBgb - 0 1"),
			new Position("brnnkbrq/pppppppp/8/8/8/8/PPPPPPPP/BRNNKBRQ w GBgb - 0 1"),
			new Position("brnnkrqb/pppppppp/8/8/8/8/PPPPPPPP/BRNNKRQB w FBfb - 0 1"),
			new Position("rbbnnkrq/pppppppp/8/8/8/8/PPPPPPPP/RBBNNKRQ w GAga - 0 1"),
			new Position("rnbbnkrq/pppppppp/8/8/8/8/PPPPPPPP/RNBBNKRQ w GAga - 0 1"),
			new Position("rnbnkbrq/pppppppp/8/8/8/8/PPPPPPPP/RNBNKBRQ w GAga - 0 1"),
			new Position("rnbnkrqb/pppppppp/8/8/8/8/PPPPPPPP/RNBNKRQB w FAfa - 0 1"),
			new Position("rbnnbkrq/pppppppp/8/8/8/8/PPPPPPPP/RBNNBKRQ w GAga - 0 1"),
			new Position("rnnbbkrq/pppppppp/8/8/8/8/PPPPPPPP/RNNBBKRQ w GAga - 0 1"),
			new Position("rnnkbbrq/pppppppp/8/8/8/8/PPPPPPPP/RNNKBBRQ w GAga - 0 1"),
			new Position("rnnkbrqb/pppppppp/8/8/8/8/PPPPPPPP/RNNKBRQB w FAfa - 0 1"),
			new Position("rbnnkrbq/pppppppp/8/8/8/8/PPPPPPPP/RBNNKRBQ w FAfa - 0 1"),
			new Position("rnnbkrbq/pppppppp/8/8/8/8/PPPPPPPP/RNNBKRBQ w FAfa - 0 1"),
			new Position("rnnkrbbq/pppppppp/8/8/8/8/PPPPPPPP/RNNKRBBQ w EAea - 0 1"),
			new Position("rnnkrqbb/pppppppp/8/8/8/8/PPPPPPPP/RNNKRQBB w EAea - 0 1"),
			new Position("bbqrnknr/pppppppp/8/8/8/8/PPPPPPPP/BBQRNKNR w HDhd - 0 1"),
			new Position("bqrbnknr/pppppppp/8/8/8/8/PPPPPPPP/BQRBNKNR w HChc - 0 1"),
			new Position("bqrnkbnr/pppppppp/8/8/8/8/PPPPPPPP/BQRNKBNR w HChc - 0 1"),
			new Position("bqrnknrb/pppppppp/8/8/8/8/PPPPPPPP/BQRNKNRB w GCgc - 0 1"),
			new Position("qbbrnknr/pppppppp/8/8/8/8/PPPPPPPP/QBBRNKNR w HDhd - 0 1"),
			new Position("qrbbnknr/pppppppp/8/8/8/8/PPPPPPPP/QRBBNKNR w HBhb - 0 1"),
			new Position("qrbnkbnr/pppppppp/8/8/8/8/PPPPPPPP/QRBNKBNR w HBhb - 0 1"),
			new Position("qrbnknrb/pppppppp/8/8/8/8/PPPPPPPP/QRBNKNRB w GBgb - 0 1"),
			new Position("qbrnbknr/pppppppp/8/8/8/8/PPPPPPPP/QBRNBKNR w HChc - 0 1"),
			new Position("qrnbbknr/pppppppp/8/8/8/8/PPPPPPPP/QRNBBKNR w HBhb - 0 1"),
			new Position("qrnkbbnr/pppppppp/8/8/8/8/PPPPPPPP/QRNKBBNR w HBhb - 0 1"),
			new Position("qrnkbnrb/pppppppp/8/8/8/8/PPPPPPPP/QRNKBNRB w GBgb - 0 1"),
			new Position("qbrnknbr/pppppppp/8/8/8/8/PPPPPPPP/QBRNKNBR w HChc - 0 1"),
			new Position("qrnbknbr/pppppppp/8/8/8/8/PPPPPPPP/QRNBKNBR w HBhb - 0 1"),
			new Position("qrnknbbr/pppppppp/8/8/8/8/PPPPPPPP/QRNKNBBR w HBhb - 0 1"),
			new Position("qrnknrbb/pppppppp/8/8/8/8/PPPPPPPP/QRNKNRBB w FBfb - 0 1"),
			new Position("bbrqnknr/pppppppp/8/8/8/8/PPPPPPPP/BBRQNKNR w HChc - 0 1"),
			new Position("brqbnknr/pppppppp/8/8/8/8/PPPPPPPP/BRQBNKNR w HBhb - 0 1"),
			new Position("brqnkbnr/pppppppp/8/8/8/8/PPPPPPPP/BRQNKBNR w HBhb - 0 1"),
			new Position("brqnknrb/pppppppp/8/8/8/8/PPPPPPPP/BRQNKNRB w GBgb - 0 1"),
			new Position("rbbqnknr/pppppppp/8/8/8/8/PPPPPPPP/RBBQNKNR w HAha - 0 1"),
			new Position("rqbbnknr/pppppppp/8/8/8/8/PPPPPPPP/RQBBNKNR w HAha - 0 1"),
			new Position("rqbnkbnr/pppppppp/8/8/8/8/PPPPPPPP/RQBNKBNR w HAha - 0 1"),
			new Position("rqbnknrb/pppppppp/8/8/8/8/PPPPPPPP/RQBNKNRB w GAga - 0 1"),
			new Position("rbqnbknr/pppppppp/8/8/8/8/PPPPPPPP/RBQNBKNR w HAha - 0 1"),
			new Position("rqnbbknr/pppppppp/8/8/8/8/PPPPPPPP/RQNBBKNR w HAha - 0 1"),
			new Position("rqnkbbnr/pppppppp/8/8/8/8/PPPPPPPP/RQNKBBNR w HAha - 0 1"),
			new Position("rqnkbnrb/pppppppp/8/8/8/8/PPPPPPPP/RQNKBNRB w GAga - 0 1"),
			new Position("rbqnknbr/pppppppp/8/8/8/8/PPPPPPPP/RBQNKNBR w HAha - 0 1"),
			new Position("rqnbknbr/pppppppp/8/8/8/8/PPPPPPPP/RQNBKNBR w HAha - 0 1"),
			new Position("rqnknbbr/pppppppp/8/8/8/8/PPPPPPPP/RQNKNBBR w HAha - 0 1"),
			new Position("rqnknrbb/pppppppp/8/8/8/8/PPPPPPPP/RQNKNRBB w FAfa - 0 1"),
			new Position("bbrnqknr/pppppppp/8/8/8/8/PPPPPPPP/BBRNQKNR w HChc - 0 1"),
			new Position("brnbqknr/pppppppp/8/8/8/8/PPPPPPPP/BRNBQKNR w HBhb - 0 1"),
			new Position("brnqkbnr/pppppppp/8/8/8/8/PPPPPPPP/BRNQKBNR w HBhb - 0 1"),
			new Position("brnqknrb/pppppppp/8/8/8/8/PPPPPPPP/BRNQKNRB w GBgb - 0 1"),
			new Position("rbbnqknr/pppppppp/8/8/8/8/PPPPPPPP/RBBNQKNR w HAha - 0 1"),
			new Position("rnbbqknr/pppppppp/8/8/8/8/PPPPPPPP/RNBBQKNR w HAha - 0 1"),
			new Position("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w HAha - 0 1"),
			new Position("rnbqknrb/pppppppp/8/8/8/8/PPPPPPPP/RNBQKNRB w GAga - 0 1"),
			new Position("rbnqbknr/pppppppp/8/8/8/8/PPPPPPPP/RBNQBKNR w HAha - 0 1"),
			new Position("rnqbbknr/pppppppp/8/8/8/8/PPPPPPPP/RNQBBKNR w HAha - 0 1"),
			new Position("rnqkbbnr/pppppppp/8/8/8/8/PPPPPPPP/RNQKBBNR w HAha - 0 1"),
			new Position("rnqkbnrb/pppppppp/8/8/8/8/PPPPPPPP/RNQKBNRB w GAga - 0 1"),
			new Position("rbnqknbr/pppppppp/8/8/8/8/PPPPPPPP/RBNQKNBR w HAha - 0 1"),
			new Position("rnqbknbr/pppppppp/8/8/8/8/PPPPPPPP/RNQBKNBR w HAha - 0 1"),
			new Position("rnqknbbr/pppppppp/8/8/8/8/PPPPPPPP/RNQKNBBR w HAha - 0 1"),
			new Position("rnqknrbb/pppppppp/8/8/8/8/PPPPPPPP/RNQKNRBB w FAfa - 0 1"),
			new Position("bbrnkqnr/pppppppp/8/8/8/8/PPPPPPPP/BBRNKQNR w HChc - 0 1"),
			new Position("brnbkqnr/pppppppp/8/8/8/8/PPPPPPPP/BRNBKQNR w HBhb - 0 1"),
			new Position("brnkqbnr/pppppppp/8/8/8/8/PPPPPPPP/BRNKQBNR w HBhb - 0 1"),
			new Position("brnkqnrb/pppppppp/8/8/8/8/PPPPPPPP/BRNKQNRB w GBgb - 0 1"),
			new Position("rbbnkqnr/pppppppp/8/8/8/8/PPPPPPPP/RBBNKQNR w HAha - 0 1"),
			new Position("rnbbkqnr/pppppppp/8/8/8/8/PPPPPPPP/RNBBKQNR w HAha - 0 1"),
			new Position("rnbkqbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBKQBNR w HAha - 0 1"),
			new Position("rnbkqnrb/pppppppp/8/8/8/8/PPPPPPPP/RNBKQNRB w GAga - 0 1"),
			new Position("rbnkbqnr/pppppppp/8/8/8/8/PPPPPPPP/RBNKBQNR w HAha - 0 1"),
			new Position("rnkbbqnr/pppppppp/8/8/8/8/PPPPPPPP/RNKBBQNR w HAha - 0 1"),
			new Position("rnkqbbnr/pppppppp/8/8/8/8/PPPPPPPP/RNKQBBNR w HAha - 0 1"),
			new Position("rnkqbnrb/pppppppp/8/8/8/8/PPPPPPPP/RNKQBNRB w GAga - 0 1"),
			new Position("rbnkqnbr/pppppppp/8/8/8/8/PPPPPPPP/RBNKQNBR w HAha - 0 1"),
			new Position("rnkbqnbr/pppppppp/8/8/8/8/PPPPPPPP/RNKBQNBR w HAha - 0 1"),
			new Position("rnkqnbbr/pppppppp/8/8/8/8/PPPPPPPP/RNKQNBBR w HAha - 0 1"),
			new Position("rnkqnrbb/pppppppp/8/8/8/8/PPPPPPPP/RNKQNRBB w FAfa - 0 1"),
			new Position("bbrnknqr/pppppppp/8/8/8/8/PPPPPPPP/BBRNKNQR w HChc - 0 1"),
			new Position("brnbknqr/pppppppp/8/8/8/8/PPPPPPPP/BRNBKNQR w HBhb - 0 1"),
			new Position("brnknbqr/pppppppp/8/8/8/8/PPPPPPPP/BRNKNBQR w HBhb - 0 1"),
			new Position("brnknqrb/pppppppp/8/8/8/8/PPPPPPPP/BRNKNQRB w GBgb - 0 1"),
			new Position("rbbnknqr/pppppppp/8/8/8/8/PPPPPPPP/RBBNKNQR w HAha - 0 1"),
			new Position("rnbbknqr/pppppppp/8/8/8/8/PPPPPPPP/RNBBKNQR w HAha - 0 1"),
			new Position("rnbknbqr/pppppppp/8/8/8/8/PPPPPPPP/RNBKNBQR w HAha - 0 1"),
			new Position("rnbknqrb/pppppppp/8/8/8/8/PPPPPPPP/RNBKNQRB w GAga - 0 1"),
			new Position("rbnkbnqr/pppppppp/8/8/8/8/PPPPPPPP/RBNKBNQR w HAha - 0 1"),
			new Position("rnkbbnqr/pppppppp/8/8/8/8/PPPPPPPP/RNKBBNQR w HAha - 0 1"),
			new Position("rnknbbqr/pppppppp/8/8/8/8/PPPPPPPP/RNKNBBQR w HAha - 0 1"),
			new Position("rnknbqrb/pppppppp/8/8/8/8/PPPPPPPP/RNKNBQRB w GAga - 0 1"),
			new Position("rbnknqbr/pppppppp/8/8/8/8/PPPPPPPP/RBNKNQBR w HAha - 0 1"),
			new Position("rnkbnqbr/pppppppp/8/8/8/8/PPPPPPPP/RNKBNQBR w HAha - 0 1"),
			new Position("rnknqbbr/pppppppp/8/8/8/8/PPPPPPPP/RNKNQBBR w HAha - 0 1"),
			new Position("rnknqrbb/pppppppp/8/8/8/8/PPPPPPPP/RNKNQRBB w FAfa - 0 1"),
			new Position("bbrnknrq/pppppppp/8/8/8/8/PPPPPPPP/BBRNKNRQ w GCgc - 0 1"),
			new Position("brnbknrq/pppppppp/8/8/8/8/PPPPPPPP/BRNBKNRQ w GBgb - 0 1"),
			new Position("brnknbrq/pppppppp/8/8/8/8/PPPPPPPP/BRNKNBRQ w GBgb - 0 1"),
			new Position("brnknrqb/pppppppp/8/8/8/8/PPPPPPPP/BRNKNRQB w FBfb - 0 1"),
			new Position("rbbnknrq/pppppppp/8/8/8/8/PPPPPPPP/RBBNKNRQ w GAga - 0 1"),
			new Position("rnbbknrq/pppppppp/8/8/8/8/PPPPPPPP/RNBBKNRQ w GAga - 0 1"),
			new Position("rnbknbrq/pppppppp/8/8/8/8/PPPPPPPP/RNBKNBRQ w GAga - 0 1"),
			new Position("rnbknrqb/pppppppp/8/8/8/8/PPPPPPPP/RNBKNRQB w FAfa - 0 1"),
			new Position("rbnkbnrq/pppppppp/8/8/8/8/PPPPPPPP/RBNKBNRQ w GAga - 0 1"),
			new Position("rnkbbnrq/pppppppp/8/8/8/8/PPPPPPPP/RNKBBNRQ w GAga - 0 1"),
			new Position("rnknbbrq/pppppppp/8/8/8/8/PPPPPPPP/RNKNBBRQ w GAga - 0 1"),
			new Position("rnknbrqb/pppppppp/8/8/8/8/PPPPPPPP/RNKNBRQB w FAfa - 0 1"),
			new Position("rbnknrbq/pppppppp/8/8/8/8/PPPPPPPP/RBNKNRBQ w FAfa - 0 1"),
			new Position("rnkbnrbq/pppppppp/8/8/8/8/PPPPPPPP/RNKBNRBQ w FAfa - 0 1"),
			new Position("rnknrbbq/pppppppp/8/8/8/8/PPPPPPPP/RNKNRBBQ w EAea - 0 1"),
			new Position("rnknrqbb/pppppppp/8/8/8/8/PPPPPPPP/RNKNRQBB w EAea - 0 1"),
			new Position("bbqrnkrn/pppppppp/8/8/8/8/PPPPPPPP/BBQRNKRN w GDgd - 0 1"),
			new Position("bqrbnkrn/pppppppp/8/8/8/8/PPPPPPPP/BQRBNKRN w GCgc - 0 1"),
			new Position("bqrnkbrn/pppppppp/8/8/8/8/PPPPPPPP/BQRNKBRN w GCgc - 0 1"),
			new Position("bqrnkrnb/pppppppp/8/8/8/8/PPPPPPPP/BQRNKRNB w FCfc - 0 1"),
			new Position("qbbrnkrn/pppppppp/8/8/8/8/PPPPPPPP/QBBRNKRN w GDgd - 0 1"),
			new Position("qrbbnkrn/pppppppp/8/8/8/8/PPPPPPPP/QRBBNKRN w GBgb - 0 1"),
			new Position("qrbnkbrn/pppppppp/8/8/8/8/PPPPPPPP/QRBNKBRN w GBgb - 0 1"),
			new Position("qrbnkrnb/pppppppp/8/8/8/8/PPPPPPPP/QRBNKRNB w FBfb - 0 1"),
			new Position("qbrnbkrn/pppppppp/8/8/8/8/PPPPPPPP/QBRNBKRN w GCgc - 0 1"),
			new Position("qrnbbkrn/pppppppp/8/8/8/8/PPPPPPPP/QRNBBKRN w GBgb - 0 1"),
			new Position("qrnkbbrn/pppppppp/8/8/8/8/PPPPPPPP/QRNKBBRN w GBgb - 0 1"),
			new Position("qrnkbrnb/pppppppp/8/8/8/8/PPPPPPPP/QRNKBRNB w FBfb - 0 1"),
			new Position("qbrnkrbn/pppppppp/8/8/8/8/PPPPPPPP/QBRNKRBN w FCfc - 0 1"),
			new Position("qrnbkrbn/pppppppp/8/8/8/8/PPPPPPPP/QRNBKRBN w FBfb - 0 1"),
			new Position("qrnkrbbn/pppppppp/8/8/8/8/PPPPPPPP/QRNKRBBN w EBeb - 0 1"),
			new Position("qrnkrnbb/pppppppp/8/8/8/8/PPPPPPPP/QRNKRNBB w EBeb - 0 1"),
			new Position("bbrqnkrn/pppppppp/8/8/8/8/PPPPPPPP/BBRQNKRN w GCgc - 0 1"),
			new Position("brqbnkrn/pppppppp/8/8/8/8/PPPPPPPP/BRQBNKRN w GBgb - 0 1"),
			new Position("brqnkbrn/pppppppp/8/8/8/8/PPPPPPPP/BRQNKBRN w GBgb - 0 1"),
			new Position("brqnkrnb/pppppppp/8/8/8/8/PPPPPPPP/BRQNKRNB w FBfb - 0 1"),
			new Position("rbbqnkrn/pppppppp/8/8/8/8/PPPPPPPP/RBBQNKRN w GAga - 0 1"),
			new Position("rqbbnkrn/pppppppp/8/8/8/8/PPPPPPPP/RQBBNKRN w GAga - 0 1"),
			new Position("rqbnkbrn/pppppppp/8/8/8/8/PPPPPPPP/RQBNKBRN w GAga - 0 1"),
			new Position("rqbnkrnb/pppppppp/8/8/8/8/PPPPPPPP/RQBNKRNB w FAfa - 0 1"),
			new Position("rbqnbkrn/pppppppp/8/8/8/8/PPPPPPPP/RBQNBKRN w GAga - 0 1"),
			new Position("rqnbbkrn/pppppppp/8/8/8/8/PPPPPPPP/RQNBBKRN w GAga - 0 1"),
			new Position("rqnkbbrn/pppppppp/8/8/8/8/PPPPPPPP/RQNKBBRN w GAga - 0 1"),
			new Position("rqnkbrnb/pppppppp/8/8/8/8/PPPPPPPP/RQNKBRNB w FAfa - 0 1"),
			new Position("rbqnkrbn/pppppppp/8/8/8/8/PPPPPPPP/RBQNKRBN w FAfa - 0 1"),
			new Position("rqnbkrbn/pppppppp/8/8/8/8/PPPPPPPP/RQNBKRBN w FAfa - 0 1"),
			new Position("rqnkrbbn/pppppppp/8/8/8/8/PPPPPPPP/RQNKRBBN w EAea - 0 1"),
			new Position("rqnkrnbb/pppppppp/8/8/8/8/PPPPPPPP/RQNKRNBB w EAea - 0 1"),
			new Position("bbrnqkrn/pppppppp/8/8/8/8/PPPPPPPP/BBRNQKRN w GCgc - 0 1"),
			new Position("brnbqkrn/pppppppp/8/8/8/8/PPPPPPPP/BRNBQKRN w GBgb - 0 1"),
			new Position("brnqkbrn/pppppppp/8/8/8/8/PPPPPPPP/BRNQKBRN w GBgb - 0 1"),
			new Position("brnqkrnb/pppppppp/8/8/8/8/PPPPPPPP/BRNQKRNB w FBfb - 0 1"),
			new Position("rbbnqkrn/pppppppp/8/8/8/8/PPPPPPPP/RBBNQKRN w GAga - 0 1"),
			new Position("rnbbqkrn/pppppppp/8/8/8/8/PPPPPPPP/RNBBQKRN w GAga - 0 1"),
			new Position("rnbqkbrn/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBRN w GAga - 0 1"),
			new Position("rnbqkrnb/pppppppp/8/8/8/8/PPPPPPPP/RNBQKRNB w FAfa - 0 1"),
			new Position("rbnqbkrn/pppppppp/8/8/8/8/PPPPPPPP/RBNQBKRN w GAga - 0 1"),
			new Position("rnqbbkrn/pppppppp/8/8/8/8/PPPPPPPP/RNQBBKRN w GAga - 0 1"),
			new Position("rnqkbbrn/pppppppp/8/8/8/8/PPPPPPPP/RNQKBBRN w GAga - 0 1"),
			new Position("rnqkbrnb/pppppppp/8/8/8/8/PPPPPPPP/RNQKBRNB w FAfa - 0 1"),
			new Position("rbnqkrbn/pppppppp/8/8/8/8/PPPPPPPP/RBNQKRBN w FAfa - 0 1"),
			new Position("rnqbkrbn/pppppppp/8/8/8/8/PPPPPPPP/RNQBKRBN w FAfa - 0 1"),
			new Position("rnqkrbbn/pppppppp/8/8/8/8/PPPPPPPP/RNQKRBBN w EAea - 0 1"),
			new Position("rnqkrnbb/pppppppp/8/8/8/8/PPPPPPPP/RNQKRNBB w EAea - 0 1"),
			new Position("bbrnkqrn/pppppppp/8/8/8/8/PPPPPPPP/BBRNKQRN w GCgc - 0 1"),
			new Position("brnbkqrn/pppppppp/8/8/8/8/PPPPPPPP/BRNBKQRN w GBgb - 0 1"),
			new Position("brnkqbrn/pppppppp/8/8/8/8/PPPPPPPP/BRNKQBRN w GBgb - 0 1"),
			new Position("brnkqrnb/pppppppp/8/8/8/8/PPPPPPPP/BRNKQRNB w FBfb - 0 1"),
			new Position("rbbnkqrn/pppppppp/8/8/8/8/PPPPPPPP/RBBNKQRN w GAga - 0 1"),
			new Position("rnbbkqrn/pppppppp/8/8/8/8/PPPPPPPP/RNBBKQRN w GAga - 0 1"),
			new Position("rnbkqbrn/pppppppp/8/8/8/8/PPPPPPPP/RNBKQBRN w GAga - 0 1"),
			new Position("rnbkqrnb/pppppppp/8/8/8/8/PPPPPPPP/RNBKQRNB w FAfa - 0 1"),
			new Position("rbnkbqrn/pppppppp/8/8/8/8/PPPPPPPP/RBNKBQRN w GAga - 0 1"),
			new Position("rnkbbqrn/pppppppp/8/8/8/8/PPPPPPPP/RNKBBQRN w GAga - 0 1"),
			new Position("rnkqbbrn/pppppppp/8/8/8/8/PPPPPPPP/RNKQBBRN w GAga - 0 1"),
			new Position("rnkqbrnb/pppppppp/8/8/8/8/PPPPPPPP/RNKQBRNB w FAfa - 0 1"),
			new Position("rbnkqrbn/pppppppp/8/8/8/8/PPPPPPPP/RBNKQRBN w FAfa - 0 1"),
			new Position("rnkbqrbn/pppppppp/8/8/8/8/PPPPPPPP/RNKBQRBN w FAfa - 0 1"),
			new Position("rnkqrbbn/pppppppp/8/8/8/8/PPPPPPPP/RNKQRBBN w EAea - 0 1"),
			new Position("rnkqrnbb/pppppppp/8/8/8/8/PPPPPPPP/RNKQRNBB w EAea - 0 1"),
			new Position("bbrnkrqn/pppppppp/8/8/8/8/PPPPPPPP/BBRNKRQN w FCfc - 0 1"),
			new Position("brnbkrqn/pppppppp/8/8/8/8/PPPPPPPP/BRNBKRQN w FBfb - 0 1"),
			new Position("brnkrbqn/pppppppp/8/8/8/8/PPPPPPPP/BRNKRBQN w EBeb - 0 1"),
			new Position("brnkrqnb/pppppppp/8/8/8/8/PPPPPPPP/BRNKRQNB w EBeb - 0 1"),
			new Position("rbbnkrqn/pppppppp/8/8/8/8/PPPPPPPP/RBBNKRQN w FAfa - 0 1"),
			new Position("rnbbkrqn/pppppppp/8/8/8/8/PPPPPPPP/RNBBKRQN w FAfa - 0 1"),
			new Position("rnbkrbqn/pppppppp/8/8/8/8/PPPPPPPP/RNBKRBQN w EAea - 0 1"),
			new Position("rnbkrqnb/pppppppp/8/8/8/8/PPPPPPPP/RNBKRQNB w EAea - 0 1"),
			new Position("rbnkbrqn/pppppppp/8/8/8/8/PPPPPPPP/RBNKBRQN w FAfa - 0 1"),
			new Position("rnkbbrqn/pppppppp/8/8/8/8/PPPPPPPP/RNKBBRQN w FAfa - 0 1"),
			new Position("rnkrbbqn/pppppppp/8/8/8/8/PPPPPPPP/RNKRBBQN w DAda - 0 1"),
			new Position("rnkrbqnb/pppppppp/8/8/8/8/PPPPPPPP/RNKRBQNB w DAda - 0 1"),
			new Position("rbnkrqbn/pppppppp/8/8/8/8/PPPPPPPP/RBNKRQBN w EAea - 0 1"),
			new Position("rnkbrqbn/pppppppp/8/8/8/8/PPPPPPPP/RNKBRQBN w EAea - 0 1"),
			new Position("rnkrqbbn/pppppppp/8/8/8/8/PPPPPPPP/RNKRQBBN w DAda - 0 1"),
			new Position("rnkrqnbb/pppppppp/8/8/8/8/PPPPPPPP/RNKRQNBB w DAda - 0 1"),
			new Position("bbrnkrnq/pppppppp/8/8/8/8/PPPPPPPP/BBRNKRNQ w FCfc - 0 1"),
			new Position("brnbkrnq/pppppppp/8/8/8/8/PPPPPPPP/BRNBKRNQ w FBfb - 0 1"),
			new Position("brnkrbnq/pppppppp/8/8/8/8/PPPPPPPP/BRNKRBNQ w EBeb - 0 1"),
			new Position("brnkrnqb/pppppppp/8/8/8/8/PPPPPPPP/BRNKRNQB w EBeb - 0 1"),
			new Position("rbbnkrnq/pppppppp/8/8/8/8/PPPPPPPP/RBBNKRNQ w FAfa - 0 1"),
			new Position("rnbbkrnq/pppppppp/8/8/8/8/PPPPPPPP/RNBBKRNQ w FAfa - 0 1"),
			new Position("rnbkrbnq/pppppppp/8/8/8/8/PPPPPPPP/RNBKRBNQ w EAea - 0 1"),
			new Position("rnbkrnqb/pppppppp/8/8/8/8/PPPPPPPP/RNBKRNQB w EAea - 0 1"),
			new Position("rbnkbrnq/pppppppp/8/8/8/8/PPPPPPPP/RBNKBRNQ w FAfa - 0 1"),
			new Position("rnkbbrnq/pppppppp/8/8/8/8/PPPPPPPP/RNKBBRNQ w FAfa - 0 1"),
			new Position("rnkrbbnq/pppppppp/8/8/8/8/PPPPPPPP/RNKRBBNQ w DAda - 0 1"),
			new Position("rnkrbnqb/pppppppp/8/8/8/8/PPPPPPPP/RNKRBNQB w DAda - 0 1"),
			new Position("rbnkrnbq/pppppppp/8/8/8/8/PPPPPPPP/RBNKRNBQ w EAea - 0 1"),
			new Position("rnkbrnbq/pppppppp/8/8/8/8/PPPPPPPP/RNKBRNBQ w EAea - 0 1"),
			new Position("rnkrnbbq/pppppppp/8/8/8/8/PPPPPPPP/RNKRNBBQ w DAda - 0 1"),
			new Position("rnkrnqbb/pppppppp/8/8/8/8/PPPPPPPP/RNKRNQBB w DAda - 0 1"),
			new Position("bbqrknnr/pppppppp/8/8/8/8/PPPPPPPP/BBQRKNNR w HDhd - 0 1"),
			new Position("bqrbknnr/pppppppp/8/8/8/8/PPPPPPPP/BQRBKNNR w HChc - 0 1"),
			new Position("bqrknbnr/pppppppp/8/8/8/8/PPPPPPPP/BQRKNBNR w HChc - 0 1"),
			new Position("bqrknnrb/pppppppp/8/8/8/8/PPPPPPPP/BQRKNNRB w GCgc - 0 1"),
			new Position("qbbrknnr/pppppppp/8/8/8/8/PPPPPPPP/QBBRKNNR w HDhd - 0 1"),
			new Position("qrbbknnr/pppppppp/8/8/8/8/PPPPPPPP/QRBBKNNR w HBhb - 0 1"),
			new Position("qrbknbnr/pppppppp/8/8/8/8/PPPPPPPP/QRBKNBNR w HBhb - 0 1"),
			new Position("qrbknnrb/pppppppp/8/8/8/8/PPPPPPPP/QRBKNNRB w GBgb - 0 1"),
			new Position("qbrkbnnr/pppppppp/8/8/8/8/PPPPPPPP/QBRKBNNR w HChc - 0 1"),
			new Position("qrkbbnnr/pppppppp/8/8/8/8/PPPPPPPP/QRKBBNNR w HBhb - 0 1"),
			new Position("qrknbbnr/pppppppp/8/8/8/8/PPPPPPPP/QRKNBBNR w HBhb - 0 1"),
			new Position("qrknbnrb/pppppppp/8/8/8/8/PPPPPPPP/QRKNBNRB w GBgb - 0 1"),
			new Position("qbrknnbr/pppppppp/8/8/8/8/PPPPPPPP/QBRKNNBR w HChc - 0 1"),
			new Position("qrkbnnbr/pppppppp/8/8/8/8/PPPPPPPP/QRKBNNBR w HBhb - 0 1"),
			new Position("qrknnbbr/pppppppp/8/8/8/8/PPPPPPPP/QRKNNBBR w HBhb - 0 1"),
			new Position("qrknnrbb/pppppppp/8/8/8/8/PPPPPPPP/QRKNNRBB w FBfb - 0 1"),
			new Position("bbrqknnr/pppppppp/8/8/8/8/PPPPPPPP/BBRQKNNR w HChc - 0 1"),
			new Position("brqbknnr/pppppppp/8/8/8/8/PPPPPPPP/BRQBKNNR w HBhb - 0 1"),
			new Position("brqknbnr/pppppppp/8/8/8/8/PPPPPPPP/BRQKNBNR w HBhb - 0 1"),
			new Position("brqknnrb/pppppppp/8/8/8/8/PPPPPPPP/BRQKNNRB w GBgb - 0 1"),
			new Position("rbbqknnr/pppppppp/8/8/8/8/PPPPPPPP/RBBQKNNR w HAha - 0 1"),
			new Position("rqbbknnr/pppppppp/8/8/8/8/PPPPPPPP/RQBBKNNR w HAha - 0 1"),
			new Position("rqbknbnr/pppppppp/8/8/8/8/PPPPPPPP/RQBKNBNR w HAha - 0 1"),
			new Position("rqbknnrb/pppppppp/8/8/8/8/PPPPPPPP/RQBKNNRB w GAga - 0 1"),
			new Position("rbqkbnnr/pppppppp/8/8/8/8/PPPPPPPP/RBQKBNNR w HAha - 0 1"),
			new Position("rqkbbnnr/pppppppp/8/8/8/8/PPPPPPPP/RQKBBNNR w HAha - 0 1"),
			new Position("rqknbbnr/pppppppp/8/8/8/8/PPPPPPPP/RQKNBBNR w HAha - 0 1"),
			new Position("rqknbnrb/pppppppp/8/8/8/8/PPPPPPPP/RQKNBNRB w GAga - 0 1"),
			new Position("rbqknnbr/pppppppp/8/8/8/8/PPPPPPPP/RBQKNNBR w HAha - 0 1"),
			new Position("rqkbnnbr/pppppppp/8/8/8/8/PPPPPPPP/RQKBNNBR w HAha - 0 1"),
			new Position("rqknnbbr/pppppppp/8/8/8/8/PPPPPPPP/RQKNNBBR w HAha - 0 1"),
			new Position("rqknnrbb/pppppppp/8/8/8/8/PPPPPPPP/RQKNNRBB w FAfa - 0 1"),
			new Position("bbrkqnnr/pppppppp/8/8/8/8/PPPPPPPP/BBRKQNNR w HChc - 0 1"),
			new Position("brkbqnnr/pppppppp/8/8/8/8/PPPPPPPP/BRKBQNNR w HBhb - 0 1"),
			new Position("brkqnbnr/pppppppp/8/8/8/8/PPPPPPPP/BRKQNBNR w HBhb - 0 1"),
			new Position("brkqnnrb/pppppppp/8/8/8/8/PPPPPPPP/BRKQNNRB w GBgb - 0 1"),
			new Position("rbbkqnnr/pppppppp/8/8/8/8/PPPPPPPP/RBBKQNNR w HAha - 0 1"),
			new Position("rkbbqnnr/pppppppp/8/8/8/8/PPPPPPPP/RKBBQNNR w HAha - 0 1"),
			new Position("rkbqnbnr/pppppppp/8/8/8/8/PPPPPPPP/RKBQNBNR w HAha - 0 1"),
			new Position("rkbqnnrb/pppppppp/8/8/8/8/PPPPPPPP/RKBQNNRB w GAga - 0 1"),
			new Position("rbkqbnnr/pppppppp/8/8/8/8/PPPPPPPP/RBKQBNNR w HAha - 0 1"),
			new Position("rkqbbnnr/pppppppp/8/8/8/8/PPPPPPPP/RKQBBNNR w HAha - 0 1"),
			new Position("rkqnbbnr/pppppppp/8/8/8/8/PPPPPPPP/RKQNBBNR w HAha - 0 1"),
			new Position("rkqnbnrb/pppppppp/8/8/8/8/PPPPPPPP/RKQNBNRB w GAga - 0 1"),
			new Position("rbkqnnbr/pppppppp/8/8/8/8/PPPPPPPP/RBKQNNBR w HAha - 0 1"),
			new Position("rkqbnnbr/pppppppp/8/8/8/8/PPPPPPPP/RKQBNNBR w HAha - 0 1"),
			new Position("rkqnnbbr/pppppppp/8/8/8/8/PPPPPPPP/RKQNNBBR w HAha - 0 1"),
			new Position("rkqnnrbb/pppppppp/8/8/8/8/PPPPPPPP/RKQNNRBB w FAfa - 0 1"),
			new Position("bbrknqnr/pppppppp/8/8/8/8/PPPPPPPP/BBRKNQNR w HChc - 0 1"),
			new Position("brkbnqnr/pppppppp/8/8/8/8/PPPPPPPP/BRKBNQNR w HBhb - 0 1"),
			new Position("brknqbnr/pppppppp/8/8/8/8/PPPPPPPP/BRKNQBNR w HBhb - 0 1"),
			new Position("brknqnrb/pppppppp/8/8/8/8/PPPPPPPP/BRKNQNRB w GBgb - 0 1"),
			new Position("rbbknqnr/pppppppp/8/8/8/8/PPPPPPPP/RBBKNQNR w HAha - 0 1"),
			new Position("rkbbnqnr/pppppppp/8/8/8/8/PPPPPPPP/RKBBNQNR w HAha - 0 1"),
			new Position("rkbnqbnr/pppppppp/8/8/8/8/PPPPPPPP/RKBNQBNR w HAha - 0 1"),
			new Position("rkbnqnrb/pppppppp/8/8/8/8/PPPPPPPP/RKBNQNRB w GAga - 0 1"),
			new Position("rbknbqnr/pppppppp/8/8/8/8/PPPPPPPP/RBKNBQNR w HAha - 0 1"),
			new Position("rknbbqnr/pppppppp/8/8/8/8/PPPPPPPP/RKNBBQNR w HAha - 0 1"),
			new Position("rknqbbnr/pppppppp/8/8/8/8/PPPPPPPP/RKNQBBNR w HAha - 0 1"),
			new Position("rknqbnrb/pppppppp/8/8/8/8/PPPPPPPP/RKNQBNRB w GAga - 0 1"),
			new Position("rbknqnbr/pppppppp/8/8/8/8/PPPPPPPP/RBKNQNBR w HAha - 0 1"),
			new Position("rknbqnbr/pppppppp/8/8/8/8/PPPPPPPP/RKNBQNBR w HAha - 0 1"),
			new Position("rknqnbbr/pppppppp/8/8/8/8/PPPPPPPP/RKNQNBBR w HAha - 0 1"),
			new Position("rknqnrbb/pppppppp/8/8/8/8/PPPPPPPP/RKNQNRBB w FAfa - 0 1"),
			new Position("bbrknnqr/pppppppp/8/8/8/8/PPPPPPPP/BBRKNNQR w HChc - 0 1"),
			new Position("brkbnnqr/pppppppp/8/8/8/8/PPPPPPPP/BRKBNNQR w HBhb - 0 1"),
			new Position("brknnbqr/pppppppp/8/8/8/8/PPPPPPPP/BRKNNBQR w HBhb - 0 1"),
			new Position("brknnqrb/pppppppp/8/8/8/8/PPPPPPPP/BRKNNQRB w GBgb - 0 1"),
			new Position("rbbknnqr/pppppppp/8/8/8/8/PPPPPPPP/RBBKNNQR w HAha - 0 1"),
			new Position("rkbbnnqr/pppppppp/8/8/8/8/PPPPPPPP/RKBBNNQR w HAha - 0 1"),
			new Position("rkbnnbqr/pppppppp/8/8/8/8/PPPPPPPP/RKBNNBQR w HAha - 0 1"),
			new Position("rkbnnqrb/pppppppp/8/8/8/8/PPPPPPPP/RKBNNQRB w GAga - 0 1"),
			new Position("rbknbnqr/pppppppp/8/8/8/8/PPPPPPPP/RBKNBNQR w HAha - 0 1"),
			new Position("rknbbnqr/pppppppp/8/8/8/8/PPPPPPPP/RKNBBNQR w HAha - 0 1"),
			new Position("rknnbbqr/pppppppp/8/8/8/8/PPPPPPPP/RKNNBBQR w HAha - 0 1"),
			new Position("rknnbqrb/pppppppp/8/8/8/8/PPPPPPPP/RKNNBQRB w GAga - 0 1"),
			new Position("rbknnqbr/pppppppp/8/8/8/8/PPPPPPPP/RBKNNQBR w HAha - 0 1"),
			new Position("rknbnqbr/pppppppp/8/8/8/8/PPPPPPPP/RKNBNQBR w HAha - 0 1"),
			new Position("rknnqbbr/pppppppp/8/8/8/8/PPPPPPPP/RKNNQBBR w HAha - 0 1"),
			new Position("rknnqrbb/pppppppp/8/8/8/8/PPPPPPPP/RKNNQRBB w FAfa - 0 1"),
			new Position("bbrknnrq/pppppppp/8/8/8/8/PPPPPPPP/BBRKNNRQ w GCgc - 0 1"),
			new Position("brkbnnrq/pppppppp/8/8/8/8/PPPPPPPP/BRKBNNRQ w GBgb - 0 1"),
			new Position("brknnbrq/pppppppp/8/8/8/8/PPPPPPPP/BRKNNBRQ w GBgb - 0 1"),
			new Position("brknnrqb/pppppppp/8/8/8/8/PPPPPPPP/BRKNNRQB w FBfb - 0 1"),
			new Position("rbbknnrq/pppppppp/8/8/8/8/PPPPPPPP/RBBKNNRQ w GAga - 0 1"),
			new Position("rkbbnnrq/pppppppp/8/8/8/8/PPPPPPPP/RKBBNNRQ w GAga - 0 1"),
			new Position("rkbnnbrq/pppppppp/8/8/8/8/PPPPPPPP/RKBNNBRQ w GAga - 0 1"),
			new Position("rkbnnrqb/pppppppp/8/8/8/8/PPPPPPPP/RKBNNRQB w FAfa - 0 1"),
			new Position("rbknbnrq/pppppppp/8/8/8/8/PPPPPPPP/RBKNBNRQ w GAga - 0 1"),
			new Position("rknbbnrq/pppppppp/8/8/8/8/PPPPPPPP/RKNBBNRQ w GAga - 0 1"),
			new Position("rknnbbrq/pppppppp/8/8/8/8/PPPPPPPP/RKNNBBRQ w GAga - 0 1"),
			new Position("rknnbrqb/pppppppp/8/8/8/8/PPPPPPPP/RKNNBRQB w FAfa - 0 1"),
			new Position("rbknnrbq/pppppppp/8/8/8/8/PPPPPPPP/RBKNNRBQ w FAfa - 0 1"),
			new Position("rknbnrbq/pppppppp/8/8/8/8/PPPPPPPP/RKNBNRBQ w FAfa - 0 1"),
			new Position("rknnrbbq/pppppppp/8/8/8/8/PPPPPPPP/RKNNRBBQ w EAea - 0 1"),
			new Position("rknnrqbb/pppppppp/8/8/8/8/PPPPPPPP/RKNNRQBB w EAea - 0 1"),
			new Position("bbqrknrn/pppppppp/8/8/8/8/PPPPPPPP/BBQRKNRN w GDgd - 0 1"),
			new Position("bqrbknrn/pppppppp/8/8/8/8/PPPPPPPP/BQRBKNRN w GCgc - 0 1"),
			new Position("bqrknbrn/pppppppp/8/8/8/8/PPPPPPPP/BQRKNBRN w GCgc - 0 1"),
			new Position("bqrknrnb/pppppppp/8/8/8/8/PPPPPPPP/BQRKNRNB w FCfc - 0 1"),
			new Position("qbbrknrn/pppppppp/8/8/8/8/PPPPPPPP/QBBRKNRN w GDgd - 0 1"),
			new Position("qrbbknrn/pppppppp/8/8/8/8/PPPPPPPP/QRBBKNRN w GBgb - 0 1"),
			new Position("qrbknbrn/pppppppp/8/8/8/8/PPPPPPPP/QRBKNBRN w GBgb - 0 1"),
			new Position("qrbknrnb/pppppppp/8/8/8/8/PPPPPPPP/QRBKNRNB w FBfb - 0 1"),
			new Position("qbrkbnrn/pppppppp/8/8/8/8/PPPPPPPP/QBRKBNRN w GCgc - 0 1"),
			new Position("qrkbbnrn/pppppppp/8/8/8/8/PPPPPPPP/QRKBBNRN w GBgb - 0 1"),
			new Position("qrknbbrn/pppppppp/8/8/8/8/PPPPPPPP/QRKNBBRN w GBgb - 0 1"),
			new Position("qrknbrnb/pppppppp/8/8/8/8/PPPPPPPP/QRKNBRNB w FBfb - 0 1"),
			new Position("qbrknrbn/pppppppp/8/8/8/8/PPPPPPPP/QBRKNRBN w FCfc - 0 1"),
			new Position("qrkbnrbn/pppppppp/8/8/8/8/PPPPPPPP/QRKBNRBN w FBfb - 0 1"),
			new Position("qrknrbbn/pppppppp/8/8/8/8/PPPPPPPP/QRKNRBBN w EBeb - 0 1"),
			new Position("qrknrnbb/pppppppp/8/8/8/8/PPPPPPPP/QRKNRNBB w EBeb - 0 1"),
			new Position("bbrqknrn/pppppppp/8/8/8/8/PPPPPPPP/BBRQKNRN w GCgc - 0 1"),
			new Position("brqbknrn/pppppppp/8/8/8/8/PPPPPPPP/BRQBKNRN w GBgb - 0 1"),
			new Position("brqknbrn/pppppppp/8/8/8/8/PPPPPPPP/BRQKNBRN w GBgb - 0 1"),
			new Position("brqknrnb/pppppppp/8/8/8/8/PPPPPPPP/BRQKNRNB w FBfb - 0 1"),
			new Position("rbbqknrn/pppppppp/8/8/8/8/PPPPPPPP/RBBQKNRN w GAga - 0 1"),
			new Position("rqbbknrn/pppppppp/8/8/8/8/PPPPPPPP/RQBBKNRN w GAga - 0 1"),
			new Position("rqbknbrn/pppppppp/8/8/8/8/PPPPPPPP/RQBKNBRN w GAga - 0 1"),
			new Position("rqbknrnb/pppppppp/8/8/8/8/PPPPPPPP/RQBKNRNB w FAfa - 0 1"),
			new Position("rbqkbnrn/pppppppp/8/8/8/8/PPPPPPPP/RBQKBNRN w GAga - 0 1"),
			new Position("rqkbbnrn/pppppppp/8/8/8/8/PPPPPPPP/RQKBBNRN w GAga - 0 1"),
			new Position("rqknbbrn/pppppppp/8/8/8/8/PPPPPPPP/RQKNBBRN w GAga - 0 1"),
			new Position("rqknbrnb/pppppppp/8/8/8/8/PPPPPPPP/RQKNBRNB w FAfa - 0 1"),
			new Position("rbqknrbn/pppppppp/8/8/8/8/PPPPPPPP/RBQKNRBN w FAfa - 0 1"),
			new Position("rqkbnrbn/pppppppp/8/8/8/8/PPPPPPPP/RQKBNRBN w FAfa - 0 1"),
			new Position("rqknrbbn/pppppppp/8/8/8/8/PPPPPPPP/RQKNRBBN w EAea - 0 1"),
			new Position("rqknrnbb/pppppppp/8/8/8/8/PPPPPPPP/RQKNRNBB w EAea - 0 1"),
			new Position("bbrkqnrn/pppppppp/8/8/8/8/PPPPPPPP/BBRKQNRN w GCgc - 0 1"),
			new Position("brkbqnrn/pppppppp/8/8/8/8/PPPPPPPP/BRKBQNRN w GBgb - 0 1"),
			new Position("brkqnbrn/pppppppp/8/8/8/8/PPPPPPPP/BRKQNBRN w GBgb - 0 1"),
			new Position("brkqnrnb/pppppppp/8/8/8/8/PPPPPPPP/BRKQNRNB w FBfb - 0 1"),
			new Position("rbbkqnrn/pppppppp/8/8/8/8/PPPPPPPP/RBBKQNRN w GAga - 0 1"),
			new Position("rkbbqnrn/pppppppp/8/8/8/8/PPPPPPPP/RKBBQNRN w GAga - 0 1"),
			new Position("rkbqnbrn/pppppppp/8/8/8/8/PPPPPPPP/RKBQNBRN w GAga - 0 1"),
			new Position("rkbqnrnb/pppppppp/8/8/8/8/PPPPPPPP/RKBQNRNB w FAfa - 0 1"),
			new Position("rbkqbnrn/pppppppp/8/8/8/8/PPPPPPPP/RBKQBNRN w GAga - 0 1"),
			new Position("rkqbbnrn/pppppppp/8/8/8/8/PPPPPPPP/RKQBBNRN w GAga - 0 1"),
			new Position("rkqnbbrn/pppppppp/8/8/8/8/PPPPPPPP/RKQNBBRN w GAga - 0 1"),
			new Position("rkqnbrnb/pppppppp/8/8/8/8/PPPPPPPP/RKQNBRNB w FAfa - 0 1"),
			new Position("rbkqnrbn/pppppppp/8/8/8/8/PPPPPPPP/RBKQNRBN w FAfa - 0 1"),
			new Position("rkqbnrbn/pppppppp/8/8/8/8/PPPPPPPP/RKQBNRBN w FAfa - 0 1"),
			new Position("rkqnrbbn/pppppppp/8/8/8/8/PPPPPPPP/RKQNRBBN w EAea - 0 1"),
			new Position("rkqnrnbb/pppppppp/8/8/8/8/PPPPPPPP/RKQNRNBB w EAea - 0 1"),
			new Position("bbrknqrn/pppppppp/8/8/8/8/PPPPPPPP/BBRKNQRN w GCgc - 0 1"),
			new Position("brkbnqrn/pppppppp/8/8/8/8/PPPPPPPP/BRKBNQRN w GBgb - 0 1"),
			new Position("brknqbrn/pppppppp/8/8/8/8/PPPPPPPP/BRKNQBRN w GBgb - 0 1"),
			new Position("brknqrnb/pppppppp/8/8/8/8/PPPPPPPP/BRKNQRNB w FBfb - 0 1"),
			new Position("rbbknqrn/pppppppp/8/8/8/8/PPPPPPPP/RBBKNQRN w GAga - 0 1"),
			new Position("rkbbnqrn/pppppppp/8/8/8/8/PPPPPPPP/RKBBNQRN w GAga - 0 1"),
			new Position("rkbnqbrn/pppppppp/8/8/8/8/PPPPPPPP/RKBNQBRN w GAga - 0 1"),
			new Position("rkbnqrnb/pppppppp/8/8/8/8/PPPPPPPP/RKBNQRNB w FAfa - 0 1"),
			new Position("rbknbqrn/pppppppp/8/8/8/8/PPPPPPPP/RBKNBQRN w GAga - 0 1"),
			new Position("rknbbqrn/pppppppp/8/8/8/8/PPPPPPPP/RKNBBQRN w GAga - 0 1"),
			new Position("rknqbbrn/pppppppp/8/8/8/8/PPPPPPPP/RKNQBBRN w GAga - 0 1"),
			new Position("rknqbrnb/pppppppp/8/8/8/8/PPPPPPPP/RKNQBRNB w FAfa - 0 1"),
			new Position("rbknqrbn/pppppppp/8/8/8/8/PPPPPPPP/RBKNQRBN w FAfa - 0 1"),
			new Position("rknbqrbn/pppppppp/8/8/8/8/PPPPPPPP/RKNBQRBN w FAfa - 0 1"),
			new Position("rknqrbbn/pppppppp/8/8/8/8/PPPPPPPP/RKNQRBBN w EAea - 0 1"),
			new Position("rknqrnbb/pppppppp/8/8/8/8/PPPPPPPP/RKNQRNBB w EAea - 0 1"),
			new Position("bbrknrqn/pppppppp/8/8/8/8/PPPPPPPP/BBRKNRQN w FCfc - 0 1"),
			new Position("brkbnrqn/pppppppp/8/8/8/8/PPPPPPPP/BRKBNRQN w FBfb - 0 1"),
			new Position("brknrbqn/pppppppp/8/8/8/8/PPPPPPPP/BRKNRBQN w EBeb - 0 1"),
			new Position("brknrqnb/pppppppp/8/8/8/8/PPPPPPPP/BRKNRQNB w EBeb - 0 1"),
			new Position("rbbknrqn/pppppppp/8/8/8/8/PPPPPPPP/RBBKNRQN w FAfa - 0 1"),
			new Position("rkbbnrqn/pppppppp/8/8/8/8/PPPPPPPP/RKBBNRQN w FAfa - 0 1"),
			new Position("rkbnrbqn/pppppppp/8/8/8/8/PPPPPPPP/RKBNRBQN w EAea - 0 1"),
			new Position("rkbnrqnb/pppppppp/8/8/8/8/PPPPPPPP/RKBNRQNB w EAea - 0 1"),
			new Position("rbknbrqn/pppppppp/8/8/8/8/PPPPPPPP/RBKNBRQN w FAfa - 0 1"),
			new Position("rknbbrqn/pppppppp/8/8/8/8/PPPPPPPP/RKNBBRQN w FAfa - 0 1"),
			new Position("rknrbbqn/pppppppp/8/8/8/8/PPPPPPPP/RKNRBBQN w DAda - 0 1"),
			new Position("rknrbqnb/pppppppp/8/8/8/8/PPPPPPPP/RKNRBQNB w DAda - 0 1"),
			new Position("rbknrqbn/pppppppp/8/8/8/8/PPPPPPPP/RBKNRQBN w EAea - 0 1"),
			new Position("rknbrqbn/pppppppp/8/8/8/8/PPPPPPPP/RKNBRQBN w EAea - 0 1"),
			new Position("rknrqbbn/pppppppp/8/8/8/8/PPPPPPPP/RKNRQBBN w DAda - 0 1"),
			new Position("rknrqnbb/pppppppp/8/8/8/8/PPPPPPPP/RKNRQNBB w DAda - 0 1"),
			new Position("bbrknrnq/pppppppp/8/8/8/8/PPPPPPPP/BBRKNRNQ w FCfc - 0 1"),
			new Position("brkbnrnq/pppppppp/8/8/8/8/PPPPPPPP/BRKBNRNQ w FBfb - 0 1"),
			new Position("brknrbnq/pppppppp/8/8/8/8/PPPPPPPP/BRKNRBNQ w EBeb - 0 1"),
			new Position("brknrnqb/pppppppp/8/8/8/8/PPPPPPPP/BRKNRNQB w EBeb - 0 1"),
			new Position("rbbknrnq/pppppppp/8/8/8/8/PPPPPPPP/RBBKNRNQ w FAfa - 0 1"),
			new Position("rkbbnrnq/pppppppp/8/8/8/8/PPPPPPPP/RKBBNRNQ w FAfa - 0 1"),
			new Position("rkbnrbnq/pppppppp/8/8/8/8/PPPPPPPP/RKBNRBNQ w EAea - 0 1"),
			new Position("rkbnrnqb/pppppppp/8/8/8/8/PPPPPPPP/RKBNRNQB w EAea - 0 1"),
			new Position("rbknbrnq/pppppppp/8/8/8/8/PPPPPPPP/RBKNBRNQ w FAfa - 0 1"),
			new Position("rknbbrnq/pppppppp/8/8/8/8/PPPPPPPP/RKNBBRNQ w FAfa - 0 1"),
			new Position("rknrbbnq/pppppppp/8/8/8/8/PPPPPPPP/RKNRBBNQ w DAda - 0 1"),
			new Position("rknrbnqb/pppppppp/8/8/8/8/PPPPPPPP/RKNRBNQB w DAda - 0 1"),
			new Position("rbknrnbq/pppppppp/8/8/8/8/PPPPPPPP/RBKNRNBQ w EAea - 0 1"),
			new Position("rknbrnbq/pppppppp/8/8/8/8/PPPPPPPP/RKNBRNBQ w EAea - 0 1"),
			new Position("rknrnbbq/pppppppp/8/8/8/8/PPPPPPPP/RKNRNBBQ w DAda - 0 1"),
			new Position("rknrnqbb/pppppppp/8/8/8/8/PPPPPPPP/RKNRNQBB w DAda - 0 1"),
			new Position("bbqrkrnn/pppppppp/8/8/8/8/PPPPPPPP/BBQRKRNN w FDfd - 0 1"),
			new Position("bqrbkrnn/pppppppp/8/8/8/8/PPPPPPPP/BQRBKRNN w FCfc - 0 1"),
			new Position("bqrkrbnn/pppppppp/8/8/8/8/PPPPPPPP/BQRKRBNN w ECec - 0 1"),
			new Position("bqrkrnnb/pppppppp/8/8/8/8/PPPPPPPP/BQRKRNNB w ECec - 0 1"),
			new Position("qbbrkrnn/pppppppp/8/8/8/8/PPPPPPPP/QBBRKRNN w FDfd - 0 1"),
			new Position("qrbbkrnn/pppppppp/8/8/8/8/PPPPPPPP/QRBBKRNN w FBfb - 0 1"),
			new Position("qrbkrbnn/pppppppp/8/8/8/8/PPPPPPPP/QRBKRBNN w EBeb - 0 1"),
			new Position("qrbkrnnb/pppppppp/8/8/8/8/PPPPPPPP/QRBKRNNB w EBeb - 0 1"),
			new Position("qbrkbrnn/pppppppp/8/8/8/8/PPPPPPPP/QBRKBRNN w FCfc - 0 1"),
			new Position("qrkbbrnn/pppppppp/8/8/8/8/PPPPPPPP/QRKBBRNN w FBfb - 0 1"),
			new Position("qrkrbbnn/pppppppp/8/8/8/8/PPPPPPPP/QRKRBBNN w DBdb - 0 1"),
			new Position("qrkrbnnb/pppppppp/8/8/8/8/PPPPPPPP/QRKRBNNB w DBdb - 0 1"),
			new Position("qbrkrnbn/pppppppp/8/8/8/8/PPPPPPPP/QBRKRNBN w ECec - 0 1"),
			new Position("qrkbrnbn/pppppppp/8/8/8/8/PPPPPPPP/QRKBRNBN w EBeb - 0 1"),
			new Position("qrkrnbbn/pppppppp/8/8/8/8/PPPPPPPP/QRKRNBBN w DBdb - 0 1"),
			new Position("qrkrnnbb/pppppppp/8/8/8/8/PPPPPPPP/QRKRNNBB w DBdb - 0 1"),
			new Position("bbrqkrnn/pppppppp/8/8/8/8/PPPPPPPP/BBRQKRNN w FCfc - 0 1"),
			new Position("brqbkrnn/pppppppp/8/8/8/8/PPPPPPPP/BRQBKRNN w FBfb - 0 1"),
			new Position("brqkrbnn/pppppppp/8/8/8/8/PPPPPPPP/BRQKRBNN w EBeb - 0 1"),
			new Position("brqkrnnb/pppppppp/8/8/8/8/PPPPPPPP/BRQKRNNB w EBeb - 0 1"),
			new Position("rbbqkrnn/pppppppp/8/8/8/8/PPPPPPPP/RBBQKRNN w FAfa - 0 1"),
			new Position("rqbbkrnn/pppppppp/8/8/8/8/PPPPPPPP/RQBBKRNN w FAfa - 0 1"),
			new Position("rqbkrbnn/pppppppp/8/8/8/8/PPPPPPPP/RQBKRBNN w EAea - 0 1"),
			new Position("rqbkrnnb/pppppppp/8/8/8/8/PPPPPPPP/RQBKRNNB w EAea - 0 1"),
			new Position("rbqkbrnn/pppppppp/8/8/8/8/PPPPPPPP/RBQKBRNN w FAfa - 0 1"),
			new Position("rqkbbrnn/pppppppp/8/8/8/8/PPPPPPPP/RQKBBRNN w FAfa - 0 1"),
			new Position("rqkrbbnn/pppppppp/8/8/8/8/PPPPPPPP/RQKRBBNN w DAda - 0 1"),
			new Position("rqkrbnnb/pppppppp/8/8/8/8/PPPPPPPP/RQKRBNNB w DAda - 0 1"),
			new Position("rbqkrnbn/pppppppp/8/8/8/8/PPPPPPPP/RBQKRNBN w EAea - 0 1"),
			new Position("rqkbrnbn/pppppppp/8/8/8/8/PPPPPPPP/RQKBRNBN w EAea - 0 1"),
			new Position("rqkrnbbn/pppppppp/8/8/8/8/PPPPPPPP/RQKRNBBN w DAda - 0 1"),
			new Position("rqkrnnbb/pppppppp/8/8/8/8/PPPPPPPP/RQKRNNBB w DAda - 0 1"),
			new Position("bbrkqrnn/pppppppp/8/8/8/8/PPPPPPPP/BBRKQRNN w FCfc - 0 1"),
			new Position("brkbqrnn/pppppppp/8/8/8/8/PPPPPPPP/BRKBQRNN w FBfb - 0 1"),
			new Position("brkqrbnn/pppppppp/8/8/8/8/PPPPPPPP/BRKQRBNN w EBeb - 0 1"),
			new Position("brkqrnnb/pppppppp/8/8/8/8/PPPPPPPP/BRKQRNNB w EBeb - 0 1"),
			new Position("rbbkqrnn/pppppppp/8/8/8/8/PPPPPPPP/RBBKQRNN w FAfa - 0 1"),
			new Position("rkbbqrnn/pppppppp/8/8/8/8/PPPPPPPP/RKBBQRNN w FAfa - 0 1"),
			new Position("rkbqrbnn/pppppppp/8/8/8/8/PPPPPPPP/RKBQRBNN w EAea - 0 1"),
			new Position("rkbqrnnb/pppppppp/8/8/8/8/PPPPPPPP/RKBQRNNB w EAea - 0 1"),
			new Position("rbkqbrnn/pppppppp/8/8/8/8/PPPPPPPP/RBKQBRNN w FAfa - 0 1"),
			new Position("rkqbbrnn/pppppppp/8/8/8/8/PPPPPPPP/RKQBBRNN w FAfa - 0 1"),
			new Position("rkqrbbnn/pppppppp/8/8/8/8/PPPPPPPP/RKQRBBNN w DAda - 0 1"),
			new Position("rkqrbnnb/pppppppp/8/8/8/8/PPPPPPPP/RKQRBNNB w DAda - 0 1"),
			new Position("rbkqrnbn/pppppppp/8/8/8/8/PPPPPPPP/RBKQRNBN w EAea - 0 1"),
			new Position("rkqbrnbn/pppppppp/8/8/8/8/PPPPPPPP/RKQBRNBN w EAea - 0 1"),
			new Position("rkqrnbbn/pppppppp/8/8/8/8/PPPPPPPP/RKQRNBBN w DAda - 0 1"),
			new Position("rkqrnnbb/pppppppp/8/8/8/8/PPPPPPPP/RKQRNNBB w DAda - 0 1"),
			new Position("bbrkrqnn/pppppppp/8/8/8/8/PPPPPPPP/BBRKRQNN w ECec - 0 1"),
			new Position("brkbrqnn/pppppppp/8/8/8/8/PPPPPPPP/BRKBRQNN w EBeb - 0 1"),
			new Position("brkrqbnn/pppppppp/8/8/8/8/PPPPPPPP/BRKRQBNN w DBdb - 0 1"),
			new Position("brkrqnnb/pppppppp/8/8/8/8/PPPPPPPP/BRKRQNNB w DBdb - 0 1"),
			new Position("rbbkrqnn/pppppppp/8/8/8/8/PPPPPPPP/RBBKRQNN w EAea - 0 1"),
			new Position("rkbbrqnn/pppppppp/8/8/8/8/PPPPPPPP/RKBBRQNN w EAea - 0 1"),
			new Position("rkbrqbnn/pppppppp/8/8/8/8/PPPPPPPP/RKBRQBNN w DAda - 0 1"),
			new Position("rkbrqnnb/pppppppp/8/8/8/8/PPPPPPPP/RKBRQNNB w DAda - 0 1"),
			new Position("rbkrbqnn/pppppppp/8/8/8/8/PPPPPPPP/RBKRBQNN w DAda - 0 1"),
			new Position("rkrbbqnn/pppppppp/8/8/8/8/PPPPPPPP/RKRBBQNN w CAca - 0 1"),
			new Position("rkrqbbnn/pppppppp/8/8/8/8/PPPPPPPP/RKRQBBNN w CAca - 0 1"),
			new Position("rkrqbnnb/pppppppp/8/8/8/8/PPPPPPPP/RKRQBNNB w CAca - 0 1"),
			new Position("rbkrqnbn/pppppppp/8/8/8/8/PPPPPPPP/RBKRQNBN w DAda - 0 1"),
			new Position("rkrbqnbn/pppppppp/8/8/8/8/PPPPPPPP/RKRBQNBN w CAca - 0 1"),
			new Position("rkrqnbbn/pppppppp/8/8/8/8/PPPPPPPP/RKRQNBBN w CAca - 0 1"),
			new Position("rkrqnnbb/pppppppp/8/8/8/8/PPPPPPPP/RKRQNNBB w CAca - 0 1"),
			new Position("bbrkrnqn/pppppppp/8/8/8/8/PPPPPPPP/BBRKRNQN w ECec - 0 1"),
			new Position("brkbrnqn/pppppppp/8/8/8/8/PPPPPPPP/BRKBRNQN w EBeb - 0 1"),
			new Position("brkrnbqn/pppppppp/8/8/8/8/PPPPPPPP/BRKRNBQN w DBdb - 0 1"),
			new Position("brkrnqnb/pppppppp/8/8/8/8/PPPPPPPP/BRKRNQNB w DBdb - 0 1"),
			new Position("rbbkrnqn/pppppppp/8/8/8/8/PPPPPPPP/RBBKRNQN w EAea - 0 1"),
			new Position("rkbbrnqn/pppppppp/8/8/8/8/PPPPPPPP/RKBBRNQN w EAea - 0 1"),
			new Position("rkbrnbqn/pppppppp/8/8/8/8/PPPPPPPP/RKBRNBQN w DAda - 0 1"),
			new Position("rkbrnqnb/pppppppp/8/8/8/8/PPPPPPPP/RKBRNQNB w DAda - 0 1"),
			new Position("rbkrbnqn/pppppppp/8/8/8/8/PPPPPPPP/RBKRBNQN w DAda - 0 1"),
			new Position("rkrbbnqn/pppppppp/8/8/8/8/PPPPPPPP/RKRBBNQN w CAca - 0 1"),
			new Position("rkrnbbqn/pppppppp/8/8/8/8/PPPPPPPP/RKRNBBQN w CAca - 0 1"),
			new Position("rkrnbqnb/pppppppp/8/8/8/8/PPPPPPPP/RKRNBQNB w CAca - 0 1"),
			new Position("rbkrnqbn/pppppppp/8/8/8/8/PPPPPPPP/RBKRNQBN w DAda - 0 1"),
			new Position("rkrbnqbn/pppppppp/8/8/8/8/PPPPPPPP/RKRBNQBN w CAca - 0 1"),
			new Position("rkrnqbbn/pppppppp/8/8/8/8/PPPPPPPP/RKRNQBBN w CAca - 0 1"),
			new Position("rkrnqnbb/pppppppp/8/8/8/8/PPPPPPPP/RKRNQNBB w CAca - 0 1"),
			new Position("bbrkrnnq/pppppppp/8/8/8/8/PPPPPPPP/BBRKRNNQ w ECec - 0 1"),
			new Position("brkbrnnq/pppppppp/8/8/8/8/PPPPPPPP/BRKBRNNQ w EBeb - 0 1"),
			new Position("brkrnbnq/pppppppp/8/8/8/8/PPPPPPPP/BRKRNBNQ w DBdb - 0 1"),
			new Position("brkrnnqb/pppppppp/8/8/8/8/PPPPPPPP/BRKRNNQB w DBdb - 0 1"),
			new Position("rbbkrnnq/pppppppp/8/8/8/8/PPPPPPPP/RBBKRNNQ w EAea - 0 1"),
			new Position("rkbbrnnq/pppppppp/8/8/8/8/PPPPPPPP/RKBBRNNQ w EAea - 0 1"),
			new Position("rkbrnbnq/pppppppp/8/8/8/8/PPPPPPPP/RKBRNBNQ w DAda - 0 1"),
			new Position("rkbrnnqb/pppppppp/8/8/8/8/PPPPPPPP/RKBRNNQB w DAda - 0 1"),
			new Position("rbkrbnnq/pppppppp/8/8/8/8/PPPPPPPP/RBKRBNNQ w DAda - 0 1"),
			new Position("rkrbbnnq/pppppppp/8/8/8/8/PPPPPPPP/RKRBBNNQ w CAca - 0 1"),
			new Position("rkrnbbnq/pppppppp/8/8/8/8/PPPPPPPP/RKRNBBNQ w CAca - 0 1"),
			new Position("rkrnbnqb/pppppppp/8/8/8/8/PPPPPPPP/RKRNBNQB w CAca - 0 1"),
			new Position("rbkrnnbq/pppppppp/8/8/8/8/PPPPPPPP/RBKRNNBQ w DAda - 0 1"),
			new Position("rkrbnnbq/pppppppp/8/8/8/8/PPPPPPPP/RKRBNNBQ w CAca - 0 1"),
			new Position("rkrnnbbq/pppppppp/8/8/8/8/PPPPPPPP/RKRNNBBQ w CAca - 0 1"),
			new Position("rkrnnqbb/pppppppp/8/8/8/8/PPPPPPPP/RKRNNQBB w CAca - 0 1")
	};

	/**
	 * The normal chess starting {@code Position} of any game.
	 */
	private static final Position STANDARD_START_POSITION = new Position(
			STANDARD_FEN);

	/**
	 * Used for retrieving a random Chess960 {@code Position}.
	 * 
	 * @return A random Chess960 {@code Position}
	 * @see #getChess960ByIndex(int)
	 */
	public static Position getRandomChess960() {
		return getChess960ByIndex(ThreadLocalRandom.current().nextInt(0, CHESS_960_POSITIONS.length));
	}

	/**
	 * Used for retrieving a Chess960 {@code Position} by its index.
	 * 
	 * <p>
	 * The following table contains all Chess960 layouts and their corresponding ID
	 * and FEN. Each layout character corresponds to a different piece:
	 * </p>
	 * 
	 * <ul>
	 * <li>'<i>B</i>' stands for the bishop</li>
	 * <li>'<i>K</i>' stands for the king</li>
	 * <li>'<i>N</i>' stands for the knight</li>
	 * <li>'<i>Q</i>' stands for the queen</li>
	 * <li>'<i>R</i>' stands for the rook</li>
	 * </ul>
	 * 
	 * <style> table { border-collapse: collapse; } th, td { padding: 3px; } }
	 * </style>
	 * 
	 * <pre>
	 *	<table border="1">
	 *	 	<tr>
	 *	 		<th>ID</th>
	 *	 		<th>Layout</th>
	 *			<th>FEN</th>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>000</td>
	 *	 		<td>BBQNNRKR</td>
	 *	 		<td>bbqnnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNNRKR w HFhf - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>001</td>
	 *	 		<td>BQNBNRKR</td>
	 *	 		<td>bqnbnrkr/pppppppp/8/8/8/8/PPPPPPPP/BQNBNRKR w HFhf - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>002</td>
	 *	 		<td>BQNNRBKR</td>
	 *	 		<td>bqnnrbkr/pppppppp/8/8/8/8/PPPPPPPP/BQNNRBKR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>003</td>
	 *	 		<td>BQNNRKRB</td>
	 *	 		<td>bqnnrkrb/pppppppp/8/8/8/8/PPPPPPPP/BQNNRKRB w GEge - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>004</td>
	 *	 		<td>QBBNNRKR</td>
	 *	 		<td>qbbnnrkr/pppppppp/8/8/8/8/PPPPPPPP/QBBNNRKR w HFhf - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>005</td>
	 *	 		<td>QNBBNRKR</td>
	 *	 		<td>qnbbnrkr/pppppppp/8/8/8/8/PPPPPPPP/QNBBNRKR w HFhf - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>006</td>
	 *	 		<td>QNBNRBKR</td>
	 *	 		<td>qnbnrbkr/pppppppp/8/8/8/8/PPPPPPPP/QNBNRBKR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>007</td>
	 *	 		<td>QNBNRKRB</td>
	 *	 		<td>qnbnrkrb/pppppppp/8/8/8/8/PPPPPPPP/QNBNRKRB w GEge - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>008</td>
	 *	 		<td>QBNNBRKR</td>
	 *	 		<td>qbnnbrkr/pppppppp/8/8/8/8/PPPPPPPP/QBNNBRKR w HFhf - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>009</td>
	 *	 		<td>QNNBBRKR</td>
	 *	 		<td>qnnbbrkr/pppppppp/8/8/8/8/PPPPPPPP/QNNBBRKR w HFhf - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>010</td>
	 *	 		<td>QNNRBBKR</td>
	 *	 		<td>qnnrbbkr/pppppppp/8/8/8/8/PPPPPPPP/QNNRBBKR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>011</td>
	 *	 		<td>QNNRBKRB</td>
	 *	 		<td>qnnrbkrb/pppppppp/8/8/8/8/PPPPPPPP/QNNRBKRB w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>012</td>
	 *	 		<td>QBNNRKBR</td>
	 *	 		<td>qbnnrkbr/pppppppp/8/8/8/8/PPPPPPPP/QBNNRKBR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>013</td>
	 *	 		<td>QNNBRKBR</td>
	 *	 		<td>qnnbrkbr/pppppppp/8/8/8/8/PPPPPPPP/QNNBRKBR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>014</td>
	 *	 		<td>QNNRKBBR</td>
	 *	 		<td>qnnrkbbr/pppppppp/8/8/8/8/PPPPPPPP/QNNRKBBR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>015</td>
	 *	 		<td>QNNRKRBB</td>
	 *	 		<td>qnnrkrbb/pppppppp/8/8/8/8/PPPPPPPP/QNNRKRBB w FDfd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>016</td>
	 *	 		<td>BBNQNRKR</td>
	 *	 		<td>bbnqnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBNQNRKR w HFhf - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>017</td>
	 *	 		<td>BNQBNRKR</td>
	 *	 		<td>bnqbnrkr/pppppppp/8/8/8/8/PPPPPPPP/BNQBNRKR w HFhf - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>018</td>
	 *	 		<td>BNQNRBKR</td>
	 *	 		<td>bnqnrbkr/pppppppp/8/8/8/8/PPPPPPPP/BNQNRBKR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>019</td>
	 *	 		<td>BNQNRKRB</td>
	 *	 		<td>bnqnrkrb/pppppppp/8/8/8/8/PPPPPPPP/BNQNRKRB w GEge - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>020</td>
	 *	 		<td>NBBQNRKR</td>
	 *	 		<td>nbbqnrkr/pppppppp/8/8/8/8/PPPPPPPP/NBBQNRKR w HFhf - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>021</td>
	 *	 		<td>NQBBNRKR</td>
	 *	 		<td>nqbbnrkr/pppppppp/8/8/8/8/PPPPPPPP/NQBBNRKR w HFhf - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>022</td>
	 *	 		<td>NQBNRBKR</td>
	 *	 		<td>nqbnrbkr/pppppppp/8/8/8/8/PPPPPPPP/NQBNRBKR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>023</td>
	 *	 		<td>NQBNRKRB</td>
	 *	 		<td>nqbnrkrb/pppppppp/8/8/8/8/PPPPPPPP/NQBNRKRB w GEge - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>024</td>
	 *	 		<td>NBQNBRKR</td>
	 *	 		<td>nbqnbrkr/pppppppp/8/8/8/8/PPPPPPPP/NBQNBRKR w HFhf - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>025</td>
	 *	 		<td>NQNBBRKR</td>
	 *	 		<td>nqnbbrkr/pppppppp/8/8/8/8/PPPPPPPP/NQNBBRKR w HFhf - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>026</td>
	 *	 		<td>NQNRBBKR</td>
	 *	 		<td>nqnrbbkr/pppppppp/8/8/8/8/PPPPPPPP/NQNRBBKR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>027</td>
	 *	 		<td>NQNRBKRB</td>
	 *	 		<td>nqnrbkrb/pppppppp/8/8/8/8/PPPPPPPP/NQNRBKRB w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>028</td>
	 *	 		<td>NBQNRKBR</td>
	 *	 		<td>nbqnrkbr/pppppppp/8/8/8/8/PPPPPPPP/NBQNRKBR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>029</td>
	 *	 		<td>NQNBRKBR</td>
	 *	 		<td>nqnbrkbr/pppppppp/8/8/8/8/PPPPPPPP/NQNBRKBR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>030</td>
	 *	 		<td>NQNRKBBR</td>
	 *	 		<td>nqnrkbbr/pppppppp/8/8/8/8/PPPPPPPP/NQNRKBBR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>031</td>
	 *	 		<td>NQNRKRBB</td>
	 *	 		<td>nqnrkrbb/pppppppp/8/8/8/8/PPPPPPPP/NQNRKRBB w FDfd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>032</td>
	 *	 		<td>BBNNQRKR</td>
	 *	 		<td>bbnnqrkr/pppppppp/8/8/8/8/PPPPPPPP/BBNNQRKR w HFhf - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>033</td>
	 *	 		<td>BNNBQRKR</td>
	 *	 		<td>bnnbqrkr/pppppppp/8/8/8/8/PPPPPPPP/BNNBQRKR w HFhf - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>034</td>
	 *	 		<td>BNNQRBKR</td>
	 *	 		<td>bnnqrbkr/pppppppp/8/8/8/8/PPPPPPPP/BNNQRBKR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>035</td>
	 *	 		<td>BNNQRKRB</td>
	 *	 		<td>bnnqrkrb/pppppppp/8/8/8/8/PPPPPPPP/BNNQRKRB w GEge - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>036</td>
	 *	 		<td>NBBNQRKR</td>
	 *	 		<td>nbbnqrkr/pppppppp/8/8/8/8/PPPPPPPP/NBBNQRKR w HFhf - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>037</td>
	 *	 		<td>NNBBQRKR</td>
	 *	 		<td>nnbbqrkr/pppppppp/8/8/8/8/PPPPPPPP/NNBBQRKR w HFhf - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>038</td>
	 *	 		<td>NNBQRBKR</td>
	 *	 		<td>nnbqrbkr/pppppppp/8/8/8/8/PPPPPPPP/NNBQRBKR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>039</td>
	 *	 		<td>NNBQRKRB</td>
	 *	 		<td>nnbqrkrb/pppppppp/8/8/8/8/PPPPPPPP/NNBQRKRB w GEge - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>040</td>
	 *	 		<td>NBNQBRKR</td>
	 *	 		<td>nbnqbrkr/pppppppp/8/8/8/8/PPPPPPPP/NBNQBRKR w HFhf - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>041</td>
	 *	 		<td>NNQBBRKR</td>
	 *	 		<td>nnqbbrkr/pppppppp/8/8/8/8/PPPPPPPP/NNQBBRKR w HFhf - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>042</td>
	 *	 		<td>NNQRBBKR</td>
	 *	 		<td>nnqrbbkr/pppppppp/8/8/8/8/PPPPPPPP/NNQRBBKR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>043</td>
	 *	 		<td>NNQRBKRB</td>
	 *	 		<td>nnqrbkrb/pppppppp/8/8/8/8/PPPPPPPP/NNQRBKRB w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>044</td>
	 *	 		<td>NBNQRKBR</td>
	 *	 		<td>nbnqrkbr/pppppppp/8/8/8/8/PPPPPPPP/NBNQRKBR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>045</td>
	 *	 		<td>NNQBRKBR</td>
	 *	 		<td>nnqbrkbr/pppppppp/8/8/8/8/PPPPPPPP/NNQBRKBR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>046</td>
	 *	 		<td>NNQRKBBR</td>
	 *	 		<td>nnqrkbbr/pppppppp/8/8/8/8/PPPPPPPP/NNQRKBBR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>047</td>
	 *	 		<td>NNQRKRBB</td>
	 *	 		<td>nnqrkrbb/pppppppp/8/8/8/8/PPPPPPPP/NNQRKRBB w FDfd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>048</td>
	 *	 		<td>BBNNRQKR</td>
	 *	 		<td>bbnnrqkr/pppppppp/8/8/8/8/PPPPPPPP/BBNNRQKR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>049</td>
	 *	 		<td>BNNBRQKR</td>
	 *	 		<td>bnnbrqkr/pppppppp/8/8/8/8/PPPPPPPP/BNNBRQKR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>050</td>
	 *	 		<td>BNNRQBKR</td>
	 *	 		<td>bnnrqbkr/pppppppp/8/8/8/8/PPPPPPPP/BNNRQBKR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>051</td>
	 *	 		<td>BNNRQKRB</td>
	 *	 		<td>bnnrqkrb/pppppppp/8/8/8/8/PPPPPPPP/BNNRQKRB w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>052</td>
	 *	 		<td>NBBNRQKR</td>
	 *	 		<td>nbbnrqkr/pppppppp/8/8/8/8/PPPPPPPP/NBBNRQKR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>053</td>
	 *	 		<td>NNBBRQKR</td>
	 *	 		<td>nnbbrqkr/pppppppp/8/8/8/8/PPPPPPPP/NNBBRQKR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>054</td>
	 *	 		<td>NNBRQBKR</td>
	 *	 		<td>nnbrqbkr/pppppppp/8/8/8/8/PPPPPPPP/NNBRQBKR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>055</td>
	 *	 		<td>NNBRQKRB</td>
	 *	 		<td>nnbrqkrb/pppppppp/8/8/8/8/PPPPPPPP/NNBRQKRB w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>056</td>
	 *	 		<td>NBNRBQKR</td>
	 *	 		<td>nbnrbqkr/pppppppp/8/8/8/8/PPPPPPPP/NBNRBQKR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>057</td>
	 *	 		<td>NNRBBQKR</td>
	 *	 		<td>nnrbbqkr/pppppppp/8/8/8/8/PPPPPPPP/NNRBBQKR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>058</td>
	 *	 		<td>NNRQBBKR</td>
	 *	 		<td>nnrqbbkr/pppppppp/8/8/8/8/PPPPPPPP/NNRQBBKR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>059</td>
	 *	 		<td>NNRQBKRB</td>
	 *	 		<td>nnrqbkrb/pppppppp/8/8/8/8/PPPPPPPP/NNRQBKRB w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>060</td>
	 *	 		<td>NBNRQKBR</td>
	 *	 		<td>nbnrqkbr/pppppppp/8/8/8/8/PPPPPPPP/NBNRQKBR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>061</td>
	 *	 		<td>NNRBQKBR</td>
	 *	 		<td>nnrbqkbr/pppppppp/8/8/8/8/PPPPPPPP/NNRBQKBR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>062</td>
	 *	 		<td>NNRQKBBR</td>
	 *	 		<td>nnrqkbbr/pppppppp/8/8/8/8/PPPPPPPP/NNRQKBBR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>063</td>
	 *	 		<td>NNRQKRBB</td>
	 *	 		<td>nnrqkrbb/pppppppp/8/8/8/8/PPPPPPPP/NNRQKRBB w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>064</td>
	 *	 		<td>BBNNRKQR</td>
	 *	 		<td>bbnnrkqr/pppppppp/8/8/8/8/PPPPPPPP/BBNNRKQR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>065</td>
	 *	 		<td>BNNBRKQR</td>
	 *	 		<td>bnnbrkqr/pppppppp/8/8/8/8/PPPPPPPP/BNNBRKQR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>066</td>
	 *	 		<td>BNNRKBQR</td>
	 *	 		<td>bnnrkbqr/pppppppp/8/8/8/8/PPPPPPPP/BNNRKBQR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>067</td>
	 *	 		<td>BNNRKQRB</td>
	 *	 		<td>bnnrkqrb/pppppppp/8/8/8/8/PPPPPPPP/BNNRKQRB w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>068</td>
	 *	 		<td>NBBNRKQR</td>
	 *	 		<td>nbbnrkqr/pppppppp/8/8/8/8/PPPPPPPP/NBBNRKQR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>069</td>
	 *	 		<td>NNBBRKQR</td>
	 *	 		<td>nnbbrkqr/pppppppp/8/8/8/8/PPPPPPPP/NNBBRKQR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>070</td>
	 *	 		<td>NNBRKBQR</td>
	 *	 		<td>nnbrkbqr/pppppppp/8/8/8/8/PPPPPPPP/NNBRKBQR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>071</td>
	 *	 		<td>NNBRKQRB</td>
	 *	 		<td>nnbrkqrb/pppppppp/8/8/8/8/PPPPPPPP/NNBRKQRB w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>072</td>
	 *	 		<td>NBNRBKQR</td>
	 *	 		<td>nbnrbkqr/pppppppp/8/8/8/8/PPPPPPPP/NBNRBKQR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>073</td>
	 *	 		<td>NNRBBKQR</td>
	 *	 		<td>nnrbbkqr/pppppppp/8/8/8/8/PPPPPPPP/NNRBBKQR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>074</td>
	 *	 		<td>NNRKBBQR</td>
	 *	 		<td>nnrkbbqr/pppppppp/8/8/8/8/PPPPPPPP/NNRKBBQR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>075</td>
	 *	 		<td>NNRKBQRB</td>
	 *	 		<td>nnrkbqrb/pppppppp/8/8/8/8/PPPPPPPP/NNRKBQRB w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>076</td>
	 *	 		<td>NBNRKQBR</td>
	 *	 		<td>nbnrkqbr/pppppppp/8/8/8/8/PPPPPPPP/NBNRKQBR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>077</td>
	 *	 		<td>NNRBKQBR</td>
	 *	 		<td>nnrbkqbr/pppppppp/8/8/8/8/PPPPPPPP/NNRBKQBR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>078</td>
	 *	 		<td>NNRKQBBR</td>
	 *	 		<td>nnrkqbbr/pppppppp/8/8/8/8/PPPPPPPP/NNRKQBBR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>079</td>
	 *	 		<td>NNRKQRBB</td>
	 *	 		<td>nnrkqrbb/pppppppp/8/8/8/8/PPPPPPPP/NNRKQRBB w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>080</td>
	 *	 		<td>BBNNRKRQ</td>
	 *	 		<td>bbnnrkrq/pppppppp/8/8/8/8/PPPPPPPP/BBNNRKRQ w GEge - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>081</td>
	 *	 		<td>BNNBRKRQ</td>
	 *	 		<td>bnnbrkrq/pppppppp/8/8/8/8/PPPPPPPP/BNNBRKRQ w GEge - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>082</td>
	 *	 		<td>BNNRKBRQ</td>
	 *	 		<td>bnnrkbrq/pppppppp/8/8/8/8/PPPPPPPP/BNNRKBRQ w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>083</td>
	 *	 		<td>BNNRKRQB</td>
	 *	 		<td>bnnrkrqb/pppppppp/8/8/8/8/PPPPPPPP/BNNRKRQB w FDfd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>084</td>
	 *	 		<td>NBBNRKRQ</td>
	 *	 		<td>nbbnrkrq/pppppppp/8/8/8/8/PPPPPPPP/NBBNRKRQ w GEge - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>085</td>
	 *	 		<td>NNBBRKRQ</td>
	 *	 		<td>nnbbrkrq/pppppppp/8/8/8/8/PPPPPPPP/NNBBRKRQ w GEge - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>086</td>
	 *	 		<td>NNBRKBRQ</td>
	 *	 		<td>nnbrkbrq/pppppppp/8/8/8/8/PPPPPPPP/NNBRKBRQ w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>087</td>
	 *	 		<td>NNBRKRQB</td>
	 *	 		<td>nnbrkrqb/pppppppp/8/8/8/8/PPPPPPPP/NNBRKRQB w FDfd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>088</td>
	 *	 		<td>NBNRBKRQ</td>
	 *	 		<td>nbnrbkrq/pppppppp/8/8/8/8/PPPPPPPP/NBNRBKRQ w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>089</td>
	 *	 		<td>NNRBBKRQ</td>
	 *	 		<td>nnrbbkrq/pppppppp/8/8/8/8/PPPPPPPP/NNRBBKRQ w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>090</td>
	 *	 		<td>NNRKBBRQ</td>
	 *	 		<td>nnrkbbrq/pppppppp/8/8/8/8/PPPPPPPP/NNRKBBRQ w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>091</td>
	 *	 		<td>NNRKBRQB</td>
	 *	 		<td>nnrkbrqb/pppppppp/8/8/8/8/PPPPPPPP/NNRKBRQB w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>092</td>
	 *	 		<td>NBNRKRBQ</td>
	 *	 		<td>nbnrkrbq/pppppppp/8/8/8/8/PPPPPPPP/NBNRKRBQ w FDfd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>093</td>
	 *	 		<td>NNRBKRBQ</td>
	 *	 		<td>nnrbkrbq/pppppppp/8/8/8/8/PPPPPPPP/NNRBKRBQ w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>094</td>
	 *	 		<td>NNRKRBBQ</td>
	 *	 		<td>nnrkrbbq/pppppppp/8/8/8/8/PPPPPPPP/NNRKRBBQ w ECec - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>095</td>
	 *	 		<td>NNRKRQBB</td>
	 *	 		<td>nnrkrqbb/pppppppp/8/8/8/8/PPPPPPPP/NNRKRQBB w ECec - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>096</td>
	 *	 		<td>BBQNRNKR</td>
	 *	 		<td>bbqnrnkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNRNKR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>097</td>
	 *	 		<td>BQNBRNKR</td>
	 *	 		<td>bqnbrnkr/pppppppp/8/8/8/8/PPPPPPPP/BQNBRNKR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>098</td>
	 *	 		<td>BQNRNBKR</td>
	 *	 		<td>bqnrnbkr/pppppppp/8/8/8/8/PPPPPPPP/BQNRNBKR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>099</td>
	 *	 		<td>BQNRNKRB</td>
	 *	 		<td>bqnrnkrb/pppppppp/8/8/8/8/PPPPPPPP/BQNRNKRB w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>100</td>
	 *	 		<td>QBBNRNKR</td>
	 *	 		<td>qbbnrnkr/pppppppp/8/8/8/8/PPPPPPPP/QBBNRNKR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>101</td>
	 *	 		<td>QNBBRNKR</td>
	 *	 		<td>qnbbrnkr/pppppppp/8/8/8/8/PPPPPPPP/QNBBRNKR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>102</td>
	 *	 		<td>QNBRNBKR</td>
	 *	 		<td>qnbrnbkr/pppppppp/8/8/8/8/PPPPPPPP/QNBRNBKR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>103</td>
	 *	 		<td>QNBRNKRB</td>
	 *	 		<td>qnbrnkrb/pppppppp/8/8/8/8/PPPPPPPP/QNBRNKRB w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>104</td>
	 *	 		<td>QBNRBNKR</td>
	 *	 		<td>qbnrbnkr/pppppppp/8/8/8/8/PPPPPPPP/QBNRBNKR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>105</td>
	 *	 		<td>QNRBBNKR</td>
	 *	 		<td>qnrbbnkr/pppppppp/8/8/8/8/PPPPPPPP/QNRBBNKR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>106</td>
	 *	 		<td>QNRNBBKR</td>
	 *	 		<td>qnrnbbkr/pppppppp/8/8/8/8/PPPPPPPP/QNRNBBKR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>107</td>
	 *	 		<td>QNRNBKRB</td>
	 *	 		<td>qnrnbkrb/pppppppp/8/8/8/8/PPPPPPPP/QNRNBKRB w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>108</td>
	 *	 		<td>QBNRNKBR</td>
	 *	 		<td>qbnrnkbr/pppppppp/8/8/8/8/PPPPPPPP/QBNRNKBR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>109</td>
	 *	 		<td>QNRBNKBR</td>
	 *	 		<td>qnrbnkbr/pppppppp/8/8/8/8/PPPPPPPP/QNRBNKBR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>110</td>
	 *	 		<td>QNRNKBBR</td>
	 *	 		<td>qnrnkbbr/pppppppp/8/8/8/8/PPPPPPPP/QNRNKBBR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>111</td>
	 *	 		<td>QNRNKRBB</td>
	 *	 		<td>qnrnkrbb/pppppppp/8/8/8/8/PPPPPPPP/QNRNKRBB w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>112</td>
	 *	 		<td>BBNQRNKR</td>
	 *	 		<td>bbnqrnkr/pppppppp/8/8/8/8/PPPPPPPP/BBNQRNKR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>113</td>
	 *	 		<td>BNQBRNKR</td>
	 *	 		<td>bnqbrnkr/pppppppp/8/8/8/8/PPPPPPPP/BNQBRNKR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>114</td>
	 *	 		<td>BNQRNBKR</td>
	 *	 		<td>bnqrnbkr/pppppppp/8/8/8/8/PPPPPPPP/BNQRNBKR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>115</td>
	 *	 		<td>BNQRNKRB</td>
	 *	 		<td>bnqrnkrb/pppppppp/8/8/8/8/PPPPPPPP/BNQRNKRB w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>116</td>
	 *	 		<td>NBBQRNKR</td>
	 *	 		<td>nbbqrnkr/pppppppp/8/8/8/8/PPPPPPPP/NBBQRNKR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>117</td>
	 *	 		<td>NQBBRNKR</td>
	 *	 		<td>nqbbrnkr/pppppppp/8/8/8/8/PPPPPPPP/NQBBRNKR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>118</td>
	 *	 		<td>NQBRNBKR</td>
	 *	 		<td>nqbrnbkr/pppppppp/8/8/8/8/PPPPPPPP/NQBRNBKR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>119</td>
	 *	 		<td>NQBRNKRB</td>
	 *	 		<td>nqbrnkrb/pppppppp/8/8/8/8/PPPPPPPP/NQBRNKRB w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>120</td>
	 *	 		<td>NBQRBNKR</td>
	 *	 		<td>nbqrbnkr/pppppppp/8/8/8/8/PPPPPPPP/NBQRBNKR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>121</td>
	 *	 		<td>NQRBBNKR</td>
	 *	 		<td>nqrbbnkr/pppppppp/8/8/8/8/PPPPPPPP/NQRBBNKR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>122</td>
	 *	 		<td>NQRNBBKR</td>
	 *	 		<td>nqrnbbkr/pppppppp/8/8/8/8/PPPPPPPP/NQRNBBKR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>123</td>
	 *	 		<td>NQRNBKRB</td>
	 *	 		<td>nqrnbkrb/pppppppp/8/8/8/8/PPPPPPPP/NQRNBKRB w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>124</td>
	 *	 		<td>NBQRNKBR</td>
	 *	 		<td>nbqrnkbr/pppppppp/8/8/8/8/PPPPPPPP/NBQRNKBR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>125</td>
	 *	 		<td>NQRBNKBR</td>
	 *	 		<td>nqrbnkbr/pppppppp/8/8/8/8/PPPPPPPP/NQRBNKBR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>126</td>
	 *	 		<td>NQRNKBBR</td>
	 *	 		<td>nqrnkbbr/pppppppp/8/8/8/8/PPPPPPPP/NQRNKBBR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>127</td>
	 *	 		<td>NQRNKRBB</td>
	 *	 		<td>nqrnkrbb/pppppppp/8/8/8/8/PPPPPPPP/NQRNKRBB w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>128</td>
	 *	 		<td>BBNRQNKR</td>
	 *	 		<td>bbnrqnkr/pppppppp/8/8/8/8/PPPPPPPP/BBNRQNKR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>129</td>
	 *	 		<td>BNRBQNKR</td>
	 *	 		<td>bnrbqnkr/pppppppp/8/8/8/8/PPPPPPPP/BNRBQNKR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>130</td>
	 *	 		<td>BNRQNBKR</td>
	 *	 		<td>bnrqnbkr/pppppppp/8/8/8/8/PPPPPPPP/BNRQNBKR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>131</td>
	 *	 		<td>BNRQNKRB</td>
	 *	 		<td>bnrqnkrb/pppppppp/8/8/8/8/PPPPPPPP/BNRQNKRB w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>132</td>
	 *	 		<td>NBBRQNKR</td>
	 *	 		<td>nbbrqnkr/pppppppp/8/8/8/8/PPPPPPPP/NBBRQNKR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>133</td>
	 *	 		<td>NRBBQNKR</td>
	 *	 		<td>nrbbqnkr/pppppppp/8/8/8/8/PPPPPPPP/NRBBQNKR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>134</td>
	 *	 		<td>NRBQNBKR</td>
	 *	 		<td>nrbqnbkr/pppppppp/8/8/8/8/PPPPPPPP/NRBQNBKR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>135</td>
	 *	 		<td>NRBQNKRB</td>
	 *	 		<td>nrbqnkrb/pppppppp/8/8/8/8/PPPPPPPP/NRBQNKRB w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>136</td>
	 *	 		<td>NBRQBNKR</td>
	 *	 		<td>nbrqbnkr/pppppppp/8/8/8/8/PPPPPPPP/NBRQBNKR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>137</td>
	 *	 		<td>NRQBBNKR</td>
	 *	 		<td>nrqbbnkr/pppppppp/8/8/8/8/PPPPPPPP/NRQBBNKR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>138</td>
	 *	 		<td>NRQNBBKR</td>
	 *	 		<td>nrqnbbkr/pppppppp/8/8/8/8/PPPPPPPP/NRQNBBKR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>139</td>
	 *	 		<td>NRQNBKRB</td>
	 *	 		<td>nrqnbkrb/pppppppp/8/8/8/8/PPPPPPPP/NRQNBKRB w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>140</td>
	 *	 		<td>NBRQNKBR</td>
	 *	 		<td>nbrqnkbr/pppppppp/8/8/8/8/PPPPPPPP/NBRQNKBR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>141</td>
	 *	 		<td>NRQBNKBR</td>
	 *	 		<td>nrqbnkbr/pppppppp/8/8/8/8/PPPPPPPP/NRQBNKBR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>142</td>
	 *	 		<td>NRQNKBBR</td>
	 *	 		<td>nrqnkbbr/pppppppp/8/8/8/8/PPPPPPPP/NRQNKBBR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>143</td>
	 *	 		<td>NRQNKRBB</td>
	 *	 		<td>nrqnkrbb/pppppppp/8/8/8/8/PPPPPPPP/NRQNKRBB w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>144</td>
	 *	 		<td>BBNRNQKR</td>
	 *	 		<td>bbnrnqkr/pppppppp/8/8/8/8/PPPPPPPP/BBNRNQKR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>145</td>
	 *	 		<td>BNRBNQKR</td>
	 *	 		<td>bnrbnqkr/pppppppp/8/8/8/8/PPPPPPPP/BNRBNQKR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>146</td>
	 *	 		<td>BNRNQBKR</td>
	 *	 		<td>bnrnqbkr/pppppppp/8/8/8/8/PPPPPPPP/BNRNQBKR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>147</td>
	 *	 		<td>BNRNQKRB</td>
	 *	 		<td>bnrnqkrb/pppppppp/8/8/8/8/PPPPPPPP/BNRNQKRB w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>148</td>
	 *	 		<td>NBBRNQKR</td>
	 *	 		<td>nbbrnqkr/pppppppp/8/8/8/8/PPPPPPPP/NBBRNQKR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>149</td>
	 *	 		<td>NRBBNQKR</td>
	 *	 		<td>nrbbnqkr/pppppppp/8/8/8/8/PPPPPPPP/NRBBNQKR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>150</td>
	 *	 		<td>NRBNQBKR</td>
	 *	 		<td>nrbnqbkr/pppppppp/8/8/8/8/PPPPPPPP/NRBNQBKR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>151</td>
	 *	 		<td>NRBNQKRB</td>
	 *	 		<td>nrbnqkrb/pppppppp/8/8/8/8/PPPPPPPP/NRBNQKRB w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>152</td>
	 *	 		<td>NBRNBQKR</td>
	 *	 		<td>nbrnbqkr/pppppppp/8/8/8/8/PPPPPPPP/NBRNBQKR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>153</td>
	 *	 		<td>NRNBBQKR</td>
	 *	 		<td>nrnbbqkr/pppppppp/8/8/8/8/PPPPPPPP/NRNBBQKR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>154</td>
	 *	 		<td>NRNQBBKR</td>
	 *	 		<td>nrnqbbkr/pppppppp/8/8/8/8/PPPPPPPP/NRNQBBKR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>155</td>
	 *	 		<td>NRNQBKRB</td>
	 *	 		<td>nrnqbkrb/pppppppp/8/8/8/8/PPPPPPPP/NRNQBKRB w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>156</td>
	 *	 		<td>NBRNQKBR</td>
	 *	 		<td>nbrnqkbr/pppppppp/8/8/8/8/PPPPPPPP/NBRNQKBR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>157</td>
	 *	 		<td>NRNBQKBR</td>
	 *	 		<td>nrnbqkbr/pppppppp/8/8/8/8/PPPPPPPP/NRNBQKBR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>158</td>
	 *	 		<td>NRNQKBBR</td>
	 *	 		<td>nrnqkbbr/pppppppp/8/8/8/8/PPPPPPPP/NRNQKBBR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>159</td>
	 *	 		<td>NRNQKRBB</td>
	 *	 		<td>nrnqkrbb/pppppppp/8/8/8/8/PPPPPPPP/NRNQKRBB w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>160</td>
	 *	 		<td>BBNRNKQR</td>
	 *	 		<td>bbnrnkqr/pppppppp/8/8/8/8/PPPPPPPP/BBNRNKQR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>161</td>
	 *	 		<td>BNRBNKQR</td>
	 *	 		<td>bnrbnkqr/pppppppp/8/8/8/8/PPPPPPPP/BNRBNKQR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>162</td>
	 *	 		<td>BNRNKBQR</td>
	 *	 		<td>bnrnkbqr/pppppppp/8/8/8/8/PPPPPPPP/BNRNKBQR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>163</td>
	 *	 		<td>BNRNKQRB</td>
	 *	 		<td>bnrnkqrb/pppppppp/8/8/8/8/PPPPPPPP/BNRNKQRB w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>164</td>
	 *	 		<td>NBBRNKQR</td>
	 *	 		<td>nbbrnkqr/pppppppp/8/8/8/8/PPPPPPPP/NBBRNKQR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>165</td>
	 *	 		<td>NRBBNKQR</td>
	 *	 		<td>nrbbnkqr/pppppppp/8/8/8/8/PPPPPPPP/NRBBNKQR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>166</td>
	 *	 		<td>NRBNKBQR</td>
	 *	 		<td>nrbnkbqr/pppppppp/8/8/8/8/PPPPPPPP/NRBNKBQR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>167</td>
	 *	 		<td>NRBNKQRB</td>
	 *	 		<td>nrbnkqrb/pppppppp/8/8/8/8/PPPPPPPP/NRBNKQRB w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>168</td>
	 *	 		<td>NBRNBKQR</td>
	 *	 		<td>nbrnbkqr/pppppppp/8/8/8/8/PPPPPPPP/NBRNBKQR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>169</td>
	 *	 		<td>NRNBBKQR</td>
	 *	 		<td>nrnbbkqr/pppppppp/8/8/8/8/PPPPPPPP/NRNBBKQR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>170</td>
	 *	 		<td>NRNKBBQR</td>
	 *	 		<td>nrnkbbqr/pppppppp/8/8/8/8/PPPPPPPP/NRNKBBQR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>171</td>
	 *	 		<td>NRNKBQRB</td>
	 *	 		<td>nrnkbqrb/pppppppp/8/8/8/8/PPPPPPPP/NRNKBQRB w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>172</td>
	 *	 		<td>NBRNKQBR</td>
	 *	 		<td>nbrnkqbr/pppppppp/8/8/8/8/PPPPPPPP/NBRNKQBR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>173</td>
	 *	 		<td>NRNBKQBR</td>
	 *	 		<td>nrnbkqbr/pppppppp/8/8/8/8/PPPPPPPP/NRNBKQBR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>174</td>
	 *	 		<td>NRNKQBBR</td>
	 *	 		<td>nrnkqbbr/pppppppp/8/8/8/8/PPPPPPPP/NRNKQBBR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>175</td>
	 *	 		<td>NRNKQRBB</td>
	 *	 		<td>nrnkqrbb/pppppppp/8/8/8/8/PPPPPPPP/NRNKQRBB w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>176</td>
	 *	 		<td>BBNRNKRQ</td>
	 *	 		<td>bbnrnkrq/pppppppp/8/8/8/8/PPPPPPPP/BBNRNKRQ w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>177</td>
	 *	 		<td>BNRBNKRQ</td>
	 *	 		<td>bnrbnkrq/pppppppp/8/8/8/8/PPPPPPPP/BNRBNKRQ w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>178</td>
	 *	 		<td>BNRNKBRQ</td>
	 *	 		<td>bnrnkbrq/pppppppp/8/8/8/8/PPPPPPPP/BNRNKBRQ w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>179</td>
	 *	 		<td>BNRNKRQB</td>
	 *	 		<td>bnrnkrqb/pppppppp/8/8/8/8/PPPPPPPP/BNRNKRQB w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>180</td>
	 *	 		<td>NBBRNKRQ</td>
	 *	 		<td>nbbrnkrq/pppppppp/8/8/8/8/PPPPPPPP/NBBRNKRQ w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>181</td>
	 *	 		<td>NRBBNKRQ</td>
	 *	 		<td>nrbbnkrq/pppppppp/8/8/8/8/PPPPPPPP/NRBBNKRQ w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>182</td>
	 *	 		<td>NRBNKBRQ</td>
	 *	 		<td>nrbnkbrq/pppppppp/8/8/8/8/PPPPPPPP/NRBNKBRQ w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>183</td>
	 *	 		<td>NRBNKRQB</td>
	 *	 		<td>nrbnkrqb/pppppppp/8/8/8/8/PPPPPPPP/NRBNKRQB w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>184</td>
	 *	 		<td>NBRNBKRQ</td>
	 *	 		<td>nbrnbkrq/pppppppp/8/8/8/8/PPPPPPPP/NBRNBKRQ w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>185</td>
	 *	 		<td>NRNBBKRQ</td>
	 *	 		<td>nrnbbkrq/pppppppp/8/8/8/8/PPPPPPPP/NRNBBKRQ w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>186</td>
	 *	 		<td>NRNKBBRQ</td>
	 *	 		<td>nrnkbbrq/pppppppp/8/8/8/8/PPPPPPPP/NRNKBBRQ w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>187</td>
	 *	 		<td>NRNKBRQB</td>
	 *	 		<td>nrnkbrqb/pppppppp/8/8/8/8/PPPPPPPP/NRNKBRQB w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>188</td>
	 *	 		<td>NBRNKRBQ</td>
	 *	 		<td>nbrnkrbq/pppppppp/8/8/8/8/PPPPPPPP/NBRNKRBQ w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>189</td>
	 *	 		<td>NRNBKRBQ</td>
	 *	 		<td>nrnbkrbq/pppppppp/8/8/8/8/PPPPPPPP/NRNBKRBQ w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>190</td>
	 *	 		<td>NRNKRBBQ</td>
	 *	 		<td>nrnkrbbq/pppppppp/8/8/8/8/PPPPPPPP/NRNKRBBQ w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>191</td>
	 *	 		<td>NRNKRQBB</td>
	 *	 		<td>nrnkrqbb/pppppppp/8/8/8/8/PPPPPPPP/NRNKRQBB w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>192</td>
	 *	 		<td>BBQNRKNR</td>
	 *	 		<td>bbqnrknr/pppppppp/8/8/8/8/PPPPPPPP/BBQNRKNR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>193</td>
	 *	 		<td>BQNBRKNR</td>
	 *	 		<td>bqnbrknr/pppppppp/8/8/8/8/PPPPPPPP/BQNBRKNR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>194</td>
	 *	 		<td>BQNRKBNR</td>
	 *	 		<td>bqnrkbnr/pppppppp/8/8/8/8/PPPPPPPP/BQNRKBNR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>195</td>
	 *	 		<td>BQNRKNRB</td>
	 *	 		<td>bqnrknrb/pppppppp/8/8/8/8/PPPPPPPP/BQNRKNRB w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>196</td>
	 *	 		<td>QBBNRKNR</td>
	 *	 		<td>qbbnrknr/pppppppp/8/8/8/8/PPPPPPPP/QBBNRKNR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>197</td>
	 *	 		<td>QNBBRKNR</td>
	 *	 		<td>qnbbrknr/pppppppp/8/8/8/8/PPPPPPPP/QNBBRKNR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>198</td>
	 *	 		<td>QNBRKBNR</td>
	 *	 		<td>qnbrkbnr/pppppppp/8/8/8/8/PPPPPPPP/QNBRKBNR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>199</td>
	 *	 		<td>QNBRKNRB</td>
	 *	 		<td>qnbrknrb/pppppppp/8/8/8/8/PPPPPPPP/QNBRKNRB w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>200</td>
	 *	 		<td>QBNRBKNR</td>
	 *	 		<td>qbnrbknr/pppppppp/8/8/8/8/PPPPPPPP/QBNRBKNR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>201</td>
	 *	 		<td>QNRBBKNR</td>
	 *	 		<td>qnrbbknr/pppppppp/8/8/8/8/PPPPPPPP/QNRBBKNR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>202</td>
	 *	 		<td>QNRKBBNR</td>
	 *	 		<td>qnrkbbnr/pppppppp/8/8/8/8/PPPPPPPP/QNRKBBNR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>203</td>
	 *	 		<td>QNRKBNRB</td>
	 *	 		<td>qnrkbnrb/pppppppp/8/8/8/8/PPPPPPPP/QNRKBNRB w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>204</td>
	 *	 		<td>QBNRKNBR</td>
	 *	 		<td>qbnrknbr/pppppppp/8/8/8/8/PPPPPPPP/QBNRKNBR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>205</td>
	 *	 		<td>QNRBKNBR</td>
	 *	 		<td>qnrbknbr/pppppppp/8/8/8/8/PPPPPPPP/QNRBKNBR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>206</td>
	 *	 		<td>QNRKNBBR</td>
	 *	 		<td>qnrknbbr/pppppppp/8/8/8/8/PPPPPPPP/QNRKNBBR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>207</td>
	 *	 		<td>QNRKNRBB</td>
	 *	 		<td>qnrknrbb/pppppppp/8/8/8/8/PPPPPPPP/QNRKNRBB w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>208</td>
	 *	 		<td>BBNQRKNR</td>
	 *	 		<td>bbnqrknr/pppppppp/8/8/8/8/PPPPPPPP/BBNQRKNR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>209</td>
	 *	 		<td>BNQBRKNR</td>
	 *	 		<td>bnqbrknr/pppppppp/8/8/8/8/PPPPPPPP/BNQBRKNR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>210</td>
	 *	 		<td>BNQRKBNR</td>
	 *	 		<td>bnqrkbnr/pppppppp/8/8/8/8/PPPPPPPP/BNQRKBNR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>211</td>
	 *	 		<td>BNQRKNRB</td>
	 *	 		<td>bnqrknrb/pppppppp/8/8/8/8/PPPPPPPP/BNQRKNRB w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>212</td>
	 *	 		<td>NBBQRKNR</td>
	 *	 		<td>nbbqrknr/pppppppp/8/8/8/8/PPPPPPPP/NBBQRKNR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>213</td>
	 *	 		<td>NQBBRKNR</td>
	 *	 		<td>nqbbrknr/pppppppp/8/8/8/8/PPPPPPPP/NQBBRKNR w HEhe - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>214</td>
	 *	 		<td>NQBRKBNR</td>
	 *	 		<td>nqbrkbnr/pppppppp/8/8/8/8/PPPPPPPP/NQBRKBNR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>215</td>
	 *	 		<td>NQBRKNRB</td>
	 *	 		<td>nqbrknrb/pppppppp/8/8/8/8/PPPPPPPP/NQBRKNRB w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>216</td>
	 *	 		<td>NBQRBKNR</td>
	 *	 		<td>nbqrbknr/pppppppp/8/8/8/8/PPPPPPPP/NBQRBKNR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>217</td>
	 *	 		<td>NQRBBKNR</td>
	 *	 		<td>nqrbbknr/pppppppp/8/8/8/8/PPPPPPPP/NQRBBKNR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>218</td>
	 *	 		<td>NQRKBBNR</td>
	 *	 		<td>nqrkbbnr/pppppppp/8/8/8/8/PPPPPPPP/NQRKBBNR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>219</td>
	 *	 		<td>NQRKBNRB</td>
	 *	 		<td>nqrkbnrb/pppppppp/8/8/8/8/PPPPPPPP/NQRKBNRB w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>220</td>
	 *	 		<td>NBQRKNBR</td>
	 *	 		<td>nbqrknbr/pppppppp/8/8/8/8/PPPPPPPP/NBQRKNBR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>221</td>
	 *	 		<td>NQRBKNBR</td>
	 *	 		<td>nqrbknbr/pppppppp/8/8/8/8/PPPPPPPP/NQRBKNBR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>222</td>
	 *	 		<td>NQRKNBBR</td>
	 *	 		<td>nqrknbbr/pppppppp/8/8/8/8/PPPPPPPP/NQRKNBBR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>223</td>
	 *	 		<td>NQRKNRBB</td>
	 *	 		<td>nqrknrbb/pppppppp/8/8/8/8/PPPPPPPP/NQRKNRBB w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>224</td>
	 *	 		<td>BBNRQKNR</td>
	 *	 		<td>bbnrqknr/pppppppp/8/8/8/8/PPPPPPPP/BBNRQKNR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>225</td>
	 *	 		<td>BNRBQKNR</td>
	 *	 		<td>bnrbqknr/pppppppp/8/8/8/8/PPPPPPPP/BNRBQKNR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>226</td>
	 *	 		<td>BNRQKBNR</td>
	 *	 		<td>bnrqkbnr/pppppppp/8/8/8/8/PPPPPPPP/BNRQKBNR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>227</td>
	 *	 		<td>BNRQKNRB</td>
	 *	 		<td>bnrqknrb/pppppppp/8/8/8/8/PPPPPPPP/BNRQKNRB w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>228</td>
	 *	 		<td>NBBRQKNR</td>
	 *	 		<td>nbbrqknr/pppppppp/8/8/8/8/PPPPPPPP/NBBRQKNR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>229</td>
	 *	 		<td>NRBBQKNR</td>
	 *	 		<td>nrbbqknr/pppppppp/8/8/8/8/PPPPPPPP/NRBBQKNR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>230</td>
	 *	 		<td>NRBQKBNR</td>
	 *	 		<td>nrbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/NRBQKBNR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>231</td>
	 *	 		<td>NRBQKNRB</td>
	 *	 		<td>nrbqknrb/pppppppp/8/8/8/8/PPPPPPPP/NRBQKNRB w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>232</td>
	 *	 		<td>NBRQBKNR</td>
	 *	 		<td>nbrqbknr/pppppppp/8/8/8/8/PPPPPPPP/NBRQBKNR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>233</td>
	 *	 		<td>NRQBBKNR</td>
	 *	 		<td>nrqbbknr/pppppppp/8/8/8/8/PPPPPPPP/NRQBBKNR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>234</td>
	 *	 		<td>NRQKBBNR</td>
	 *	 		<td>nrqkbbnr/pppppppp/8/8/8/8/PPPPPPPP/NRQKBBNR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>235</td>
	 *	 		<td>NRQKBNRB</td>
	 *	 		<td>nrqkbnrb/pppppppp/8/8/8/8/PPPPPPPP/NRQKBNRB w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>236</td>
	 *	 		<td>NBRQKNBR</td>
	 *	 		<td>nbrqknbr/pppppppp/8/8/8/8/PPPPPPPP/NBRQKNBR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>237</td>
	 *	 		<td>NRQBKNBR</td>
	 *	 		<td>nrqbknbr/pppppppp/8/8/8/8/PPPPPPPP/NRQBKNBR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>238</td>
	 *	 		<td>NRQKNBBR</td>
	 *	 		<td>nrqknbbr/pppppppp/8/8/8/8/PPPPPPPP/NRQKNBBR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>239</td>
	 *	 		<td>NRQKNRBB</td>
	 *	 		<td>nrqknrbb/pppppppp/8/8/8/8/PPPPPPPP/NRQKNRBB w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>240</td>
	 *	 		<td>BBNRKQNR</td>
	 *	 		<td>bbnrkqnr/pppppppp/8/8/8/8/PPPPPPPP/BBNRKQNR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>241</td>
	 *	 		<td>BNRBKQNR</td>
	 *	 		<td>bnrbkqnr/pppppppp/8/8/8/8/PPPPPPPP/BNRBKQNR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>242</td>
	 *	 		<td>BNRKQBNR</td>
	 *	 		<td>bnrkqbnr/pppppppp/8/8/8/8/PPPPPPPP/BNRKQBNR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>243</td>
	 *	 		<td>BNRKQNRB</td>
	 *	 		<td>bnrkqnrb/pppppppp/8/8/8/8/PPPPPPPP/BNRKQNRB w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>244</td>
	 *	 		<td>NBBRKQNR</td>
	 *	 		<td>nbbrkqnr/pppppppp/8/8/8/8/PPPPPPPP/NBBRKQNR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>245</td>
	 *	 		<td>NRBBKQNR</td>
	 *	 		<td>nrbbkqnr/pppppppp/8/8/8/8/PPPPPPPP/NRBBKQNR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>246</td>
	 *	 		<td>NRBKQBNR</td>
	 *	 		<td>nrbkqbnr/pppppppp/8/8/8/8/PPPPPPPP/NRBKQBNR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>247</td>
	 *	 		<td>NRBKQNRB</td>
	 *	 		<td>nrbkqnrb/pppppppp/8/8/8/8/PPPPPPPP/NRBKQNRB w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>248</td>
	 *	 		<td>NBRKBQNR</td>
	 *	 		<td>nbrkbqnr/pppppppp/8/8/8/8/PPPPPPPP/NBRKBQNR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>249</td>
	 *	 		<td>NRKBBQNR</td>
	 *	 		<td>nrkbbqnr/pppppppp/8/8/8/8/PPPPPPPP/NRKBBQNR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>250</td>
	 *	 		<td>NRKQBBNR</td>
	 *	 		<td>nrkqbbnr/pppppppp/8/8/8/8/PPPPPPPP/NRKQBBNR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>251</td>
	 *	 		<td>NRKQBNRB</td>
	 *	 		<td>nrkqbnrb/pppppppp/8/8/8/8/PPPPPPPP/NRKQBNRB w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>252</td>
	 *	 		<td>NBRKQNBR</td>
	 *	 		<td>nbrkqnbr/pppppppp/8/8/8/8/PPPPPPPP/NBRKQNBR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>253</td>
	 *	 		<td>NRKBQNBR</td>
	 *	 		<td>nrkbqnbr/pppppppp/8/8/8/8/PPPPPPPP/NRKBQNBR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>254</td>
	 *	 		<td>NRKQNBBR</td>
	 *	 		<td>nrkqnbbr/pppppppp/8/8/8/8/PPPPPPPP/NRKQNBBR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>255</td>
	 *	 		<td>NRKQNRBB</td>
	 *	 		<td>nrkqnrbb/pppppppp/8/8/8/8/PPPPPPPP/NRKQNRBB w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>256</td>
	 *	 		<td>BBNRKNQR</td>
	 *	 		<td>bbnrknqr/pppppppp/8/8/8/8/PPPPPPPP/BBNRKNQR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>257</td>
	 *	 		<td>BNRBKNQR</td>
	 *	 		<td>bnrbknqr/pppppppp/8/8/8/8/PPPPPPPP/BNRBKNQR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>258</td>
	 *	 		<td>BNRKNBQR</td>
	 *	 		<td>bnrknbqr/pppppppp/8/8/8/8/PPPPPPPP/BNRKNBQR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>259</td>
	 *	 		<td>BNRKNQRB</td>
	 *	 		<td>bnrknqrb/pppppppp/8/8/8/8/PPPPPPPP/BNRKNQRB w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>260</td>
	 *	 		<td>NBBRKNQR</td>
	 *	 		<td>nbbrknqr/pppppppp/8/8/8/8/PPPPPPPP/NBBRKNQR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>261</td>
	 *	 		<td>NRBBKNQR</td>
	 *	 		<td>nrbbknqr/pppppppp/8/8/8/8/PPPPPPPP/NRBBKNQR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>262</td>
	 *	 		<td>NRBKNBQR</td>
	 *	 		<td>nrbknbqr/pppppppp/8/8/8/8/PPPPPPPP/NRBKNBQR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>263</td>
	 *	 		<td>NRBKNQRB</td>
	 *	 		<td>nrbknqrb/pppppppp/8/8/8/8/PPPPPPPP/NRBKNQRB w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>264</td>
	 *	 		<td>NBRKBNQR</td>
	 *	 		<td>nbrkbnqr/pppppppp/8/8/8/8/PPPPPPPP/NBRKBNQR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>265</td>
	 *	 		<td>NRKBBNQR</td>
	 *	 		<td>nrkbbnqr/pppppppp/8/8/8/8/PPPPPPPP/NRKBBNQR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>266</td>
	 *	 		<td>NRKNBBQR</td>
	 *	 		<td>nrknbbqr/pppppppp/8/8/8/8/PPPPPPPP/NRKNBBQR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>267</td>
	 *	 		<td>NRKNBQRB</td>
	 *	 		<td>nrknbqrb/pppppppp/8/8/8/8/PPPPPPPP/NRKNBQRB w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>268</td>
	 *	 		<td>NBRKNQBR</td>
	 *	 		<td>nbrknqbr/pppppppp/8/8/8/8/PPPPPPPP/NBRKNQBR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>269</td>
	 *	 		<td>NRKBNQBR</td>
	 *	 		<td>nrkbnqbr/pppppppp/8/8/8/8/PPPPPPPP/NRKBNQBR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>270</td>
	 *	 		<td>NRKNQBBR</td>
	 *	 		<td>nrknqbbr/pppppppp/8/8/8/8/PPPPPPPP/NRKNQBBR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>271</td>
	 *	 		<td>NRKNQRBB</td>
	 *	 		<td>nrknqrbb/pppppppp/8/8/8/8/PPPPPPPP/NRKNQRBB w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>272</td>
	 *	 		<td>BBNRKNRQ</td>
	 *	 		<td>bbnrknrq/pppppppp/8/8/8/8/PPPPPPPP/BBNRKNRQ w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>273</td>
	 *	 		<td>BNRBKNRQ</td>
	 *	 		<td>bnrbknrq/pppppppp/8/8/8/8/PPPPPPPP/BNRBKNRQ w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>274</td>
	 *	 		<td>BNRKNBRQ</td>
	 *	 		<td>bnrknbrq/pppppppp/8/8/8/8/PPPPPPPP/BNRKNBRQ w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>275</td>
	 *	 		<td>BNRKNRQB</td>
	 *	 		<td>bnrknrqb/pppppppp/8/8/8/8/PPPPPPPP/BNRKNRQB w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>276</td>
	 *	 		<td>NBBRKNRQ</td>
	 *	 		<td>nbbrknrq/pppppppp/8/8/8/8/PPPPPPPP/NBBRKNRQ w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>277</td>
	 *	 		<td>NRBBKNRQ</td>
	 *	 		<td>nrbbknrq/pppppppp/8/8/8/8/PPPPPPPP/NRBBKNRQ w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>278</td>
	 *	 		<td>NRBKNBRQ</td>
	 *	 		<td>nrbknbrq/pppppppp/8/8/8/8/PPPPPPPP/NRBKNBRQ w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>279</td>
	 *	 		<td>NRBKNRQB</td>
	 *	 		<td>nrbknrqb/pppppppp/8/8/8/8/PPPPPPPP/NRBKNRQB w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>280</td>
	 *	 		<td>NBRKBNRQ</td>
	 *	 		<td>nbrkbnrq/pppppppp/8/8/8/8/PPPPPPPP/NBRKBNRQ w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>281</td>
	 *	 		<td>NRKBBNRQ</td>
	 *	 		<td>nrkbbnrq/pppppppp/8/8/8/8/PPPPPPPP/NRKBBNRQ w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>282</td>
	 *	 		<td>NRKNBBRQ</td>
	 *	 		<td>nrknbbrq/pppppppp/8/8/8/8/PPPPPPPP/NRKNBBRQ w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>283</td>
	 *	 		<td>NRKNBRQB</td>
	 *	 		<td>nrknbrqb/pppppppp/8/8/8/8/PPPPPPPP/NRKNBRQB w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>284</td>
	 *	 		<td>NBRKNRBQ</td>
	 *	 		<td>nbrknrbq/pppppppp/8/8/8/8/PPPPPPPP/NBRKNRBQ w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>285</td>
	 *	 		<td>NRKBNRBQ</td>
	 *	 		<td>nrkbnrbq/pppppppp/8/8/8/8/PPPPPPPP/NRKBNRBQ w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>286</td>
	 *	 		<td>NRKNRBBQ</td>
	 *	 		<td>nrknrbbq/pppppppp/8/8/8/8/PPPPPPPP/NRKNRBBQ w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>287</td>
	 *	 		<td>NRKNRQBB</td>
	 *	 		<td>nrknrqbb/pppppppp/8/8/8/8/PPPPPPPP/NRKNRQBB w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>288</td>
	 *	 		<td>BBQNRKRN</td>
	 *	 		<td>bbqnrkrn/pppppppp/8/8/8/8/PPPPPPPP/BBQNRKRN w GEge - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>289</td>
	 *	 		<td>BQNBRKRN</td>
	 *	 		<td>bqnbrkrn/pppppppp/8/8/8/8/PPPPPPPP/BQNBRKRN w GEge - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>290</td>
	 *	 		<td>BQNRKBRN</td>
	 *	 		<td>bqnrkbrn/pppppppp/8/8/8/8/PPPPPPPP/BQNRKBRN w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>291</td>
	 *	 		<td>BQNRKRNB</td>
	 *	 		<td>bqnrkrnb/pppppppp/8/8/8/8/PPPPPPPP/BQNRKRNB w FDfd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>292</td>
	 *	 		<td>QBBNRKRN</td>
	 *	 		<td>qbbnrkrn/pppppppp/8/8/8/8/PPPPPPPP/QBBNRKRN w GEge - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>293</td>
	 *	 		<td>QNBBRKRN</td>
	 *	 		<td>qnbbrkrn/pppppppp/8/8/8/8/PPPPPPPP/QNBBRKRN w GEge - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>294</td>
	 *	 		<td>QNBRKBRN</td>
	 *	 		<td>qnbrkbrn/pppppppp/8/8/8/8/PPPPPPPP/QNBRKBRN w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>295</td>
	 *	 		<td>QNBRKRNB</td>
	 *	 		<td>qnbrkrnb/pppppppp/8/8/8/8/PPPPPPPP/QNBRKRNB w FDfd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>296</td>
	 *	 		<td>QBNRBKRN</td>
	 *	 		<td>qbnrbkrn/pppppppp/8/8/8/8/PPPPPPPP/QBNRBKRN w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>297</td>
	 *	 		<td>QNRBBKRN</td>
	 *	 		<td>qnrbbkrn/pppppppp/8/8/8/8/PPPPPPPP/QNRBBKRN w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>298</td>
	 *	 		<td>QNRKBBRN</td>
	 *	 		<td>qnrkbbrn/pppppppp/8/8/8/8/PPPPPPPP/QNRKBBRN w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>299</td>
	 *	 		<td>QNRKBRNB</td>
	 *	 		<td>qnrkbrnb/pppppppp/8/8/8/8/PPPPPPPP/QNRKBRNB w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>300</td>
	 *	 		<td>QBNRKRBN</td>
	 *	 		<td>qbnrkrbn/pppppppp/8/8/8/8/PPPPPPPP/QBNRKRBN w FDfd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>301</td>
	 *	 		<td>QNRBKRBN</td>
	 *	 		<td>qnrbkrbn/pppppppp/8/8/8/8/PPPPPPPP/QNRBKRBN w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>302</td>
	 *	 		<td>QNRKRBBN</td>
	 *	 		<td>qnrkrbbn/pppppppp/8/8/8/8/PPPPPPPP/QNRKRBBN w ECec - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>303</td>
	 *	 		<td>QNRKRNBB</td>
	 *	 		<td>qnrkrnbb/pppppppp/8/8/8/8/PPPPPPPP/QNRKRNBB w ECec - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>304</td>
	 *	 		<td>BBNQRKRN</td>
	 *	 		<td>bbnqrkrn/pppppppp/8/8/8/8/PPPPPPPP/BBNQRKRN w GEge - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>305</td>
	 *	 		<td>BNQBRKRN</td>
	 *	 		<td>bnqbrkrn/pppppppp/8/8/8/8/PPPPPPPP/BNQBRKRN w GEge - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>306</td>
	 *	 		<td>BNQRKBRN</td>
	 *	 		<td>bnqrkbrn/pppppppp/8/8/8/8/PPPPPPPP/BNQRKBRN w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>307</td>
	 *	 		<td>BNQRKRNB</td>
	 *	 		<td>bnqrkrnb/pppppppp/8/8/8/8/PPPPPPPP/BNQRKRNB w FDfd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>308</td>
	 *	 		<td>NBBQRKRN</td>
	 *	 		<td>nbbqrkrn/pppppppp/8/8/8/8/PPPPPPPP/NBBQRKRN w GEge - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>309</td>
	 *	 		<td>NQBBRKRN</td>
	 *	 		<td>nqbbrkrn/pppppppp/8/8/8/8/PPPPPPPP/NQBBRKRN w GEge - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>310</td>
	 *	 		<td>NQBRKBRN</td>
	 *	 		<td>nqbrkbrn/pppppppp/8/8/8/8/PPPPPPPP/NQBRKBRN w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>311</td>
	 *	 		<td>NQBRKRNB</td>
	 *	 		<td>nqbrkrnb/pppppppp/8/8/8/8/PPPPPPPP/NQBRKRNB w FDfd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>312</td>
	 *	 		<td>NBQRBKRN</td>
	 *	 		<td>nbqrbkrn/pppppppp/8/8/8/8/PPPPPPPP/NBQRBKRN w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>313</td>
	 *	 		<td>NQRBBKRN</td>
	 *	 		<td>nqrbbkrn/pppppppp/8/8/8/8/PPPPPPPP/NQRBBKRN w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>314</td>
	 *	 		<td>NQRKBBRN</td>
	 *	 		<td>nqrkbbrn/pppppppp/8/8/8/8/PPPPPPPP/NQRKBBRN w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>315</td>
	 *	 		<td>NQRKBRNB</td>
	 *	 		<td>nqrkbrnb/pppppppp/8/8/8/8/PPPPPPPP/NQRKBRNB w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>316</td>
	 *	 		<td>NBQRKRBN</td>
	 *	 		<td>nbqrkrbn/pppppppp/8/8/8/8/PPPPPPPP/NBQRKRBN w FDfd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>317</td>
	 *	 		<td>NQRBKRBN</td>
	 *	 		<td>nqrbkrbn/pppppppp/8/8/8/8/PPPPPPPP/NQRBKRBN w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>318</td>
	 *	 		<td>NQRKRBBN</td>
	 *	 		<td>nqrkrbbn/pppppppp/8/8/8/8/PPPPPPPP/NQRKRBBN w ECec - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>319</td>
	 *	 		<td>NQRKRNBB</td>
	 *	 		<td>nqrkrnbb/pppppppp/8/8/8/8/PPPPPPPP/NQRKRNBB w ECec - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>320</td>
	 *	 		<td>BBNRQKRN</td>
	 *	 		<td>bbnrqkrn/pppppppp/8/8/8/8/PPPPPPPP/BBNRQKRN w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>321</td>
	 *	 		<td>BNRBQKRN</td>
	 *	 		<td>bnrbqkrn/pppppppp/8/8/8/8/PPPPPPPP/BNRBQKRN w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>322</td>
	 *	 		<td>BNRQKBRN</td>
	 *	 		<td>bnrqkbrn/pppppppp/8/8/8/8/PPPPPPPP/BNRQKBRN w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>323</td>
	 *	 		<td>BNRQKRNB</td>
	 *	 		<td>bnrqkrnb/pppppppp/8/8/8/8/PPPPPPPP/BNRQKRNB w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>324</td>
	 *	 		<td>NBBRQKRN</td>
	 *	 		<td>nbbrqkrn/pppppppp/8/8/8/8/PPPPPPPP/NBBRQKRN w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>325</td>
	 *	 		<td>NRBBQKRN</td>
	 *	 		<td>nrbbqkrn/pppppppp/8/8/8/8/PPPPPPPP/NRBBQKRN w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>326</td>
	 *	 		<td>NRBQKBRN</td>
	 *	 		<td>nrbqkbrn/pppppppp/8/8/8/8/PPPPPPPP/NRBQKBRN w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>327</td>
	 *	 		<td>NRBQKRNB</td>
	 *	 		<td>nrbqkrnb/pppppppp/8/8/8/8/PPPPPPPP/NRBQKRNB w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>328</td>
	 *	 		<td>NBRQBKRN</td>
	 *	 		<td>nbrqbkrn/pppppppp/8/8/8/8/PPPPPPPP/NBRQBKRN w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>329</td>
	 *	 		<td>NRQBBKRN</td>
	 *	 		<td>nrqbbkrn/pppppppp/8/8/8/8/PPPPPPPP/NRQBBKRN w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>330</td>
	 *	 		<td>NRQKBBRN</td>
	 *	 		<td>nrqkbbrn/pppppppp/8/8/8/8/PPPPPPPP/NRQKBBRN w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>331</td>
	 *	 		<td>NRQKBRNB</td>
	 *	 		<td>nrqkbrnb/pppppppp/8/8/8/8/PPPPPPPP/NRQKBRNB w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>332</td>
	 *	 		<td>NBRQKRBN</td>
	 *	 		<td>nbrqkrbn/pppppppp/8/8/8/8/PPPPPPPP/NBRQKRBN w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>333</td>
	 *	 		<td>NRQBKRBN</td>
	 *	 		<td>nrqbkrbn/pppppppp/8/8/8/8/PPPPPPPP/NRQBKRBN w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>334</td>
	 *	 		<td>NRQKRBBN</td>
	 *	 		<td>nrqkrbbn/pppppppp/8/8/8/8/PPPPPPPP/NRQKRBBN w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>335</td>
	 *	 		<td>NRQKRNBB</td>
	 *	 		<td>nrqkrnbb/pppppppp/8/8/8/8/PPPPPPPP/NRQKRNBB w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>336</td>
	 *	 		<td>BBNRKQRN</td>
	 *	 		<td>bbnrkqrn/pppppppp/8/8/8/8/PPPPPPPP/BBNRKQRN w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>337</td>
	 *	 		<td>BNRBKQRN</td>
	 *	 		<td>bnrbkqrn/pppppppp/8/8/8/8/PPPPPPPP/BNRBKQRN w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>338</td>
	 *	 		<td>BNRKQBRN</td>
	 *	 		<td>bnrkqbrn/pppppppp/8/8/8/8/PPPPPPPP/BNRKQBRN w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>339</td>
	 *	 		<td>BNRKQRNB</td>
	 *	 		<td>bnrkqrnb/pppppppp/8/8/8/8/PPPPPPPP/BNRKQRNB w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>340</td>
	 *	 		<td>NBBRKQRN</td>
	 *	 		<td>nbbrkqrn/pppppppp/8/8/8/8/PPPPPPPP/NBBRKQRN w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>341</td>
	 *	 		<td>NRBBKQRN</td>
	 *	 		<td>nrbbkqrn/pppppppp/8/8/8/8/PPPPPPPP/NRBBKQRN w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>342</td>
	 *	 		<td>NRBKQBRN</td>
	 *	 		<td>nrbkqbrn/pppppppp/8/8/8/8/PPPPPPPP/NRBKQBRN w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>343</td>
	 *	 		<td>NRBKQRNB</td>
	 *	 		<td>nrbkqrnb/pppppppp/8/8/8/8/PPPPPPPP/NRBKQRNB w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>344</td>
	 *	 		<td>NBRKBQRN</td>
	 *	 		<td>nbrkbqrn/pppppppp/8/8/8/8/PPPPPPPP/NBRKBQRN w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>345</td>
	 *	 		<td>NRKBBQRN</td>
	 *	 		<td>nrkbbqrn/pppppppp/8/8/8/8/PPPPPPPP/NRKBBQRN w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>346</td>
	 *	 		<td>NRKQBBRN</td>
	 *	 		<td>nrkqbbrn/pppppppp/8/8/8/8/PPPPPPPP/NRKQBBRN w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>347</td>
	 *	 		<td>NRKQBRNB</td>
	 *	 		<td>nrkqbrnb/pppppppp/8/8/8/8/PPPPPPPP/NRKQBRNB w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>348</td>
	 *	 		<td>NBRKQRBN</td>
	 *	 		<td>nbrkqrbn/pppppppp/8/8/8/8/PPPPPPPP/NBRKQRBN w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>349</td>
	 *	 		<td>NRKBQRBN</td>
	 *	 		<td>nrkbqrbn/pppppppp/8/8/8/8/PPPPPPPP/NRKBQRBN w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>350</td>
	 *	 		<td>NRKQRBBN</td>
	 *	 		<td>nrkqrbbn/pppppppp/8/8/8/8/PPPPPPPP/NRKQRBBN w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>351</td>
	 *	 		<td>NRKQRNBB</td>
	 *	 		<td>nrkqrnbb/pppppppp/8/8/8/8/PPPPPPPP/NRKQRNBB w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>352</td>
	 *	 		<td>BBNRKRQN</td>
	 *	 		<td>bbnrkrqn/pppppppp/8/8/8/8/PPPPPPPP/BBNRKRQN w FDfd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>353</td>
	 *	 		<td>BNRBKRQN</td>
	 *	 		<td>bnrbkrqn/pppppppp/8/8/8/8/PPPPPPPP/BNRBKRQN w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>354</td>
	 *	 		<td>BNRKRBQN</td>
	 *	 		<td>bnrkrbqn/pppppppp/8/8/8/8/PPPPPPPP/BNRKRBQN w ECec - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>355</td>
	 *	 		<td>BNRKRQNB</td>
	 *	 		<td>bnrkrqnb/pppppppp/8/8/8/8/PPPPPPPP/BNRKRQNB w ECec - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>356</td>
	 *	 		<td>NBBRKRQN</td>
	 *	 		<td>nbbrkrqn/pppppppp/8/8/8/8/PPPPPPPP/NBBRKRQN w FDfd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>357</td>
	 *	 		<td>NRBBKRQN</td>
	 *	 		<td>nrbbkrqn/pppppppp/8/8/8/8/PPPPPPPP/NRBBKRQN w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>358</td>
	 *	 		<td>NRBKRBQN</td>
	 *	 		<td>nrbkrbqn/pppppppp/8/8/8/8/PPPPPPPP/NRBKRBQN w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>359</td>
	 *	 		<td>NRBKRQNB</td>
	 *	 		<td>nrbkrqnb/pppppppp/8/8/8/8/PPPPPPPP/NRBKRQNB w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>360</td>
	 *	 		<td>NBRKBRQN</td>
	 *	 		<td>nbrkbrqn/pppppppp/8/8/8/8/PPPPPPPP/NBRKBRQN w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>361</td>
	 *	 		<td>NRKBBRQN</td>
	 *	 		<td>nrkbbrqn/pppppppp/8/8/8/8/PPPPPPPP/NRKBBRQN w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>362</td>
	 *	 		<td>NRKRBBQN</td>
	 *	 		<td>nrkrbbqn/pppppppp/8/8/8/8/PPPPPPPP/NRKRBBQN w DBdb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>363</td>
	 *	 		<td>NRKRBQNB</td>
	 *	 		<td>nrkrbqnb/pppppppp/8/8/8/8/PPPPPPPP/NRKRBQNB w DBdb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>364</td>
	 *	 		<td>NBRKRQBN</td>
	 *	 		<td>nbrkrqbn/pppppppp/8/8/8/8/PPPPPPPP/NBRKRQBN w ECec - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>365</td>
	 *	 		<td>NRKBRQBN</td>
	 *	 		<td>nrkbrqbn/pppppppp/8/8/8/8/PPPPPPPP/NRKBRQBN w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>366</td>
	 *	 		<td>NRKRQBBN</td>
	 *	 		<td>nrkrqbbn/pppppppp/8/8/8/8/PPPPPPPP/NRKRQBBN w DBdb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>367</td>
	 *	 		<td>NRKRQNBB</td>
	 *	 		<td>nrkrqnbb/pppppppp/8/8/8/8/PPPPPPPP/NRKRQNBB w DBdb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>368</td>
	 *	 		<td>BBNRKRNQ</td>
	 *	 		<td>bbnrkrnq/pppppppp/8/8/8/8/PPPPPPPP/BBNRKRNQ w FDfd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>369</td>
	 *	 		<td>BNRBKRNQ</td>
	 *	 		<td>bnrbkrnq/pppppppp/8/8/8/8/PPPPPPPP/BNRBKRNQ w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>370</td>
	 *	 		<td>BNRKRBNQ</td>
	 *	 		<td>bnrkrbnq/pppppppp/8/8/8/8/PPPPPPPP/BNRKRBNQ w ECec - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>371</td>
	 *	 		<td>BNRKRNQB</td>
	 *	 		<td>bnrkrnqb/pppppppp/8/8/8/8/PPPPPPPP/BNRKRNQB w ECec - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>372</td>
	 *	 		<td>NBBRKRNQ</td>
	 *	 		<td>nbbrkrnq/pppppppp/8/8/8/8/PPPPPPPP/NBBRKRNQ w FDfd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>373</td>
	 *	 		<td>NRBBKRNQ</td>
	 *	 		<td>nrbbkrnq/pppppppp/8/8/8/8/PPPPPPPP/NRBBKRNQ w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>374</td>
	 *	 		<td>NRBKRBNQ</td>
	 *	 		<td>nrbkrbnq/pppppppp/8/8/8/8/PPPPPPPP/NRBKRBNQ w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>375</td>
	 *	 		<td>NRBKRNQB</td>
	 *	 		<td>nrbkrnqb/pppppppp/8/8/8/8/PPPPPPPP/NRBKRNQB w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>376</td>
	 *	 		<td>NBRKBRNQ</td>
	 *	 		<td>nbrkbrnq/pppppppp/8/8/8/8/PPPPPPPP/NBRKBRNQ w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>377</td>
	 *	 		<td>NRKBBRNQ</td>
	 *	 		<td>nrkbbrnq/pppppppp/8/8/8/8/PPPPPPPP/NRKBBRNQ w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>378</td>
	 *	 		<td>NRKRBBNQ</td>
	 *	 		<td>nrkrbbnq/pppppppp/8/8/8/8/PPPPPPPP/NRKRBBNQ w DBdb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>379</td>
	 *	 		<td>NRKRBNQB</td>
	 *	 		<td>nrkrbnqb/pppppppp/8/8/8/8/PPPPPPPP/NRKRBNQB w DBdb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>380</td>
	 *	 		<td>NBRKRNBQ</td>
	 *	 		<td>nbrkrnbq/pppppppp/8/8/8/8/PPPPPPPP/NBRKRNBQ w ECec - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>381</td>
	 *	 		<td>NRKBRNBQ</td>
	 *	 		<td>nrkbrnbq/pppppppp/8/8/8/8/PPPPPPPP/NRKBRNBQ w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>382</td>
	 *	 		<td>NRKRNBBQ</td>
	 *	 		<td>nrkrnbbq/pppppppp/8/8/8/8/PPPPPPPP/NRKRNBBQ w DBdb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>383</td>
	 *	 		<td>NRKRNQBB</td>
	 *	 		<td>nrkrnqbb/pppppppp/8/8/8/8/PPPPPPPP/NRKRNQBB w DBdb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>384</td>
	 *	 		<td>BBQRNNKR</td>
	 *	 		<td>bbqrnnkr/pppppppp/8/8/8/8/PPPPPPPP/BBQRNNKR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>385</td>
	 *	 		<td>BQRBNNKR</td>
	 *	 		<td>bqrbnnkr/pppppppp/8/8/8/8/PPPPPPPP/BQRBNNKR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>386</td>
	 *	 		<td>BQRNNBKR</td>
	 *	 		<td>bqrnnbkr/pppppppp/8/8/8/8/PPPPPPPP/BQRNNBKR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>387</td>
	 *	 		<td>BQRNNKRB</td>
	 *	 		<td>bqrnnkrb/pppppppp/8/8/8/8/PPPPPPPP/BQRNNKRB w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>388</td>
	 *	 		<td>QBBRNNKR</td>
	 *	 		<td>qbbrnnkr/pppppppp/8/8/8/8/PPPPPPPP/QBBRNNKR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>389</td>
	 *	 		<td>QRBBNNKR</td>
	 *	 		<td>qrbbnnkr/pppppppp/8/8/8/8/PPPPPPPP/QRBBNNKR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>390</td>
	 *	 		<td>QRBNNBKR</td>
	 *	 		<td>qrbnnbkr/pppppppp/8/8/8/8/PPPPPPPP/QRBNNBKR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>391</td>
	 *	 		<td>QRBNNKRB</td>
	 *	 		<td>qrbnnkrb/pppppppp/8/8/8/8/PPPPPPPP/QRBNNKRB w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>392</td>
	 *	 		<td>QBRNBNKR</td>
	 *	 		<td>qbrnbnkr/pppppppp/8/8/8/8/PPPPPPPP/QBRNBNKR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>393</td>
	 *	 		<td>QRNBBNKR</td>
	 *	 		<td>qrnbbnkr/pppppppp/8/8/8/8/PPPPPPPP/QRNBBNKR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>394</td>
	 *	 		<td>QRNNBBKR</td>
	 *	 		<td>qrnnbbkr/pppppppp/8/8/8/8/PPPPPPPP/QRNNBBKR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>395</td>
	 *	 		<td>QRNNBKRB</td>
	 *	 		<td>qrnnbkrb/pppppppp/8/8/8/8/PPPPPPPP/QRNNBKRB w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>396</td>
	 *	 		<td>QBRNNKBR</td>
	 *	 		<td>qbrnnkbr/pppppppp/8/8/8/8/PPPPPPPP/QBRNNKBR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>397</td>
	 *	 		<td>QRNBNKBR</td>
	 *	 		<td>qrnbnkbr/pppppppp/8/8/8/8/PPPPPPPP/QRNBNKBR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>398</td>
	 *	 		<td>QRNNKBBR</td>
	 *	 		<td>qrnnkbbr/pppppppp/8/8/8/8/PPPPPPPP/QRNNKBBR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>399</td>
	 *	 		<td>QRNNKRBB</td>
	 *	 		<td>qrnnkrbb/pppppppp/8/8/8/8/PPPPPPPP/QRNNKRBB w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>400</td>
	 *	 		<td>BBRQNNKR</td>
	 *	 		<td>bbrqnnkr/pppppppp/8/8/8/8/PPPPPPPP/BBRQNNKR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>401</td>
	 *	 		<td>BRQBNNKR</td>
	 *	 		<td>brqbnnkr/pppppppp/8/8/8/8/PPPPPPPP/BRQBNNKR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>402</td>
	 *	 		<td>BRQNNBKR</td>
	 *	 		<td>brqnnbkr/pppppppp/8/8/8/8/PPPPPPPP/BRQNNBKR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>403</td>
	 *	 		<td>BRQNNKRB</td>
	 *	 		<td>brqnnkrb/pppppppp/8/8/8/8/PPPPPPPP/BRQNNKRB w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>404</td>
	 *	 		<td>RBBQNNKR</td>
	 *	 		<td>rbbqnnkr/pppppppp/8/8/8/8/PPPPPPPP/RBBQNNKR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>405</td>
	 *	 		<td>RQBBNNKR</td>
	 *	 		<td>rqbbnnkr/pppppppp/8/8/8/8/PPPPPPPP/RQBBNNKR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>406</td>
	 *	 		<td>RQBNNBKR</td>
	 *	 		<td>rqbnnbkr/pppppppp/8/8/8/8/PPPPPPPP/RQBNNBKR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>407</td>
	 *	 		<td>RQBNNKRB</td>
	 *	 		<td>rqbnnkrb/pppppppp/8/8/8/8/PPPPPPPP/RQBNNKRB w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>408</td>
	 *	 		<td>RBQNBNKR</td>
	 *	 		<td>rbqnbnkr/pppppppp/8/8/8/8/PPPPPPPP/RBQNBNKR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>409</td>
	 *	 		<td>RQNBBNKR</td>
	 *	 		<td>rqnbbnkr/pppppppp/8/8/8/8/PPPPPPPP/RQNBBNKR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>410</td>
	 *	 		<td>RQNNBBKR</td>
	 *	 		<td>rqnnbbkr/pppppppp/8/8/8/8/PPPPPPPP/RQNNBBKR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>411</td>
	 *	 		<td>RQNNBKRB</td>
	 *	 		<td>rqnnbkrb/pppppppp/8/8/8/8/PPPPPPPP/RQNNBKRB w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>412</td>
	 *	 		<td>RBQNNKBR</td>
	 *	 		<td>rbqnnkbr/pppppppp/8/8/8/8/PPPPPPPP/RBQNNKBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>413</td>
	 *	 		<td>RQNBNKBR</td>
	 *	 		<td>rqnbnkbr/pppppppp/8/8/8/8/PPPPPPPP/RQNBNKBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>414</td>
	 *	 		<td>RQNNKBBR</td>
	 *	 		<td>rqnnkbbr/pppppppp/8/8/8/8/PPPPPPPP/RQNNKBBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>415</td>
	 *	 		<td>RQNNKRBB</td>
	 *	 		<td>rqnnkrbb/pppppppp/8/8/8/8/PPPPPPPP/RQNNKRBB w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>416</td>
	 *	 		<td>BBRNQNKR</td>
	 *	 		<td>bbrnqnkr/pppppppp/8/8/8/8/PPPPPPPP/BBRNQNKR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>417</td>
	 *	 		<td>BRNBQNKR</td>
	 *	 		<td>brnbqnkr/pppppppp/8/8/8/8/PPPPPPPP/BRNBQNKR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>418</td>
	 *	 		<td>BRNQNBKR</td>
	 *	 		<td>brnqnbkr/pppppppp/8/8/8/8/PPPPPPPP/BRNQNBKR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>419</td>
	 *	 		<td>BRNQNKRB</td>
	 *	 		<td>brnqnkrb/pppppppp/8/8/8/8/PPPPPPPP/BRNQNKRB w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>420</td>
	 *	 		<td>RBBNQNKR</td>
	 *	 		<td>rbbnqnkr/pppppppp/8/8/8/8/PPPPPPPP/RBBNQNKR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>421</td>
	 *	 		<td>RNBBQNKR</td>
	 *	 		<td>rnbbqnkr/pppppppp/8/8/8/8/PPPPPPPP/RNBBQNKR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>422</td>
	 *	 		<td>RNBQNBKR</td>
	 *	 		<td>rnbqnbkr/pppppppp/8/8/8/8/PPPPPPPP/RNBQNBKR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>423</td>
	 *	 		<td>RNBQNKRB</td>
	 *	 		<td>rnbqnkrb/pppppppp/8/8/8/8/PPPPPPPP/RNBQNKRB w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>424</td>
	 *	 		<td>RBNQBNKR</td>
	 *	 		<td>rbnqbnkr/pppppppp/8/8/8/8/PPPPPPPP/RBNQBNKR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>425</td>
	 *	 		<td>RNQBBNKR</td>
	 *	 		<td>rnqbbnkr/pppppppp/8/8/8/8/PPPPPPPP/RNQBBNKR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>426</td>
	 *	 		<td>RNQNBBKR</td>
	 *	 		<td>rnqnbbkr/pppppppp/8/8/8/8/PPPPPPPP/RNQNBBKR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>427</td>
	 *	 		<td>RNQNBKRB</td>
	 *	 		<td>rnqnbkrb/pppppppp/8/8/8/8/PPPPPPPP/RNQNBKRB w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>428</td>
	 *	 		<td>RBNQNKBR</td>
	 *	 		<td>rbnqnkbr/pppppppp/8/8/8/8/PPPPPPPP/RBNQNKBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>429</td>
	 *	 		<td>RNQBNKBR</td>
	 *	 		<td>rnqbnkbr/pppppppp/8/8/8/8/PPPPPPPP/RNQBNKBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>430</td>
	 *	 		<td>RNQNKBBR</td>
	 *	 		<td>rnqnkbbr/pppppppp/8/8/8/8/PPPPPPPP/RNQNKBBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>431</td>
	 *	 		<td>RNQNKRBB</td>
	 *	 		<td>rnqnkrbb/pppppppp/8/8/8/8/PPPPPPPP/RNQNKRBB w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>432</td>
	 *	 		<td>BBRNNQKR</td>
	 *	 		<td>bbrnnqkr/pppppppp/8/8/8/8/PPPPPPPP/BBRNNQKR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>433</td>
	 *	 		<td>BRNBNQKR</td>
	 *	 		<td>brnbnqkr/pppppppp/8/8/8/8/PPPPPPPP/BRNBNQKR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>434</td>
	 *	 		<td>BRNNQBKR</td>
	 *	 		<td>brnnqbkr/pppppppp/8/8/8/8/PPPPPPPP/BRNNQBKR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>435</td>
	 *	 		<td>BRNNQKRB</td>
	 *	 		<td>brnnqkrb/pppppppp/8/8/8/8/PPPPPPPP/BRNNQKRB w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>436</td>
	 *	 		<td>RBBNNQKR</td>
	 *	 		<td>rbbnnqkr/pppppppp/8/8/8/8/PPPPPPPP/RBBNNQKR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>437</td>
	 *	 		<td>RNBBNQKR</td>
	 *	 		<td>rnbbnqkr/pppppppp/8/8/8/8/PPPPPPPP/RNBBNQKR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>438</td>
	 *	 		<td>RNBNQBKR</td>
	 *	 		<td>rnbnqbkr/pppppppp/8/8/8/8/PPPPPPPP/RNBNQBKR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>439</td>
	 *	 		<td>RNBNQKRB</td>
	 *	 		<td>rnbnqkrb/pppppppp/8/8/8/8/PPPPPPPP/RNBNQKRB w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>440</td>
	 *	 		<td>RBNNBQKR</td>
	 *	 		<td>rbnnbqkr/pppppppp/8/8/8/8/PPPPPPPP/RBNNBQKR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>441</td>
	 *	 		<td>RNNBBQKR</td>
	 *	 		<td>rnnbbqkr/pppppppp/8/8/8/8/PPPPPPPP/RNNBBQKR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>442</td>
	 *	 		<td>RNNQBBKR</td>
	 *	 		<td>rnnqbbkr/pppppppp/8/8/8/8/PPPPPPPP/RNNQBBKR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>443</td>
	 *	 		<td>RNNQBKRB</td>
	 *	 		<td>rnnqbkrb/pppppppp/8/8/8/8/PPPPPPPP/RNNQBKRB w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>444</td>
	 *	 		<td>RBNNQKBR</td>
	 *	 		<td>rbnnqkbr/pppppppp/8/8/8/8/PPPPPPPP/RBNNQKBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>445</td>
	 *	 		<td>RNNBQKBR</td>
	 *	 		<td>rnnbqkbr/pppppppp/8/8/8/8/PPPPPPPP/RNNBQKBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>446</td>
	 *	 		<td>RNNQKBBR</td>
	 *	 		<td>rnnqkbbr/pppppppp/8/8/8/8/PPPPPPPP/RNNQKBBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>447</td>
	 *	 		<td>RNNQKRBB</td>
	 *	 		<td>rnnqkrbb/pppppppp/8/8/8/8/PPPPPPPP/RNNQKRBB w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>448</td>
	 *	 		<td>BBRNNKQR</td>
	 *	 		<td>bbrnnkqr/pppppppp/8/8/8/8/PPPPPPPP/BBRNNKQR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>449</td>
	 *	 		<td>BRNBNKQR</td>
	 *	 		<td>brnbnkqr/pppppppp/8/8/8/8/PPPPPPPP/BRNBNKQR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>450</td>
	 *	 		<td>BRNNKBQR</td>
	 *	 		<td>brnnkbqr/pppppppp/8/8/8/8/PPPPPPPP/BRNNKBQR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>451</td>
	 *	 		<td>BRNNKQRB</td>
	 *	 		<td>brnnkqrb/pppppppp/8/8/8/8/PPPPPPPP/BRNNKQRB w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>452</td>
	 *	 		<td>RBBNNKQR</td>
	 *	 		<td>rbbnnkqr/pppppppp/8/8/8/8/PPPPPPPP/RBBNNKQR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>453</td>
	 *	 		<td>RNBBNKQR</td>
	 *	 		<td>rnbbnkqr/pppppppp/8/8/8/8/PPPPPPPP/RNBBNKQR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>454</td>
	 *	 		<td>RNBNKBQR</td>
	 *	 		<td>rnbnkbqr/pppppppp/8/8/8/8/PPPPPPPP/RNBNKBQR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>455</td>
	 *	 		<td>RNBNKQRB</td>
	 *	 		<td>rnbnkqrb/pppppppp/8/8/8/8/PPPPPPPP/RNBNKQRB w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>456</td>
	 *	 		<td>RBNNBKQR</td>
	 *	 		<td>rbnnbkqr/pppppppp/8/8/8/8/PPPPPPPP/RBNNBKQR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>457</td>
	 *	 		<td>RNNBBKQR</td>
	 *	 		<td>rnnbbkqr/pppppppp/8/8/8/8/PPPPPPPP/RNNBBKQR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>458</td>
	 *	 		<td>RNNKBBQR</td>
	 *	 		<td>rnnkbbqr/pppppppp/8/8/8/8/PPPPPPPP/RNNKBBQR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>459</td>
	 *	 		<td>RNNKBQRB</td>
	 *	 		<td>rnnkbqrb/pppppppp/8/8/8/8/PPPPPPPP/RNNKBQRB w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>460</td>
	 *	 		<td>RBNNKQBR</td>
	 *	 		<td>rbnnkqbr/pppppppp/8/8/8/8/PPPPPPPP/RBNNKQBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>461</td>
	 *	 		<td>RNNBKQBR</td>
	 *	 		<td>rnnbkqbr/pppppppp/8/8/8/8/PPPPPPPP/RNNBKQBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>462</td>
	 *	 		<td>RNNKQBBR</td>
	 *	 		<td>rnnkqbbr/pppppppp/8/8/8/8/PPPPPPPP/RNNKQBBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>463</td>
	 *	 		<td>RNNKQRBB</td>
	 *	 		<td>rnnkqrbb/pppppppp/8/8/8/8/PPPPPPPP/RNNKQRBB w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>464</td>
	 *	 		<td>BBRNNKRQ</td>
	 *	 		<td>bbrnnkrq/pppppppp/8/8/8/8/PPPPPPPP/BBRNNKRQ w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>465</td>
	 *	 		<td>BRNBNKRQ</td>
	 *	 		<td>brnbnkrq/pppppppp/8/8/8/8/PPPPPPPP/BRNBNKRQ w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>466</td>
	 *	 		<td>BRNNKBRQ</td>
	 *	 		<td>brnnkbrq/pppppppp/8/8/8/8/PPPPPPPP/BRNNKBRQ w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>467</td>
	 *	 		<td>BRNNKRQB</td>
	 *	 		<td>brnnkrqb/pppppppp/8/8/8/8/PPPPPPPP/BRNNKRQB w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>468</td>
	 *	 		<td>RBBNNKRQ</td>
	 *	 		<td>rbbnnkrq/pppppppp/8/8/8/8/PPPPPPPP/RBBNNKRQ w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>469</td>
	 *	 		<td>RNBBNKRQ</td>
	 *	 		<td>rnbbnkrq/pppppppp/8/8/8/8/PPPPPPPP/RNBBNKRQ w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>470</td>
	 *	 		<td>RNBNKBRQ</td>
	 *	 		<td>rnbnkbrq/pppppppp/8/8/8/8/PPPPPPPP/RNBNKBRQ w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>471</td>
	 *	 		<td>RNBNKRQB</td>
	 *	 		<td>rnbnkrqb/pppppppp/8/8/8/8/PPPPPPPP/RNBNKRQB w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>472</td>
	 *	 		<td>RBNNBKRQ</td>
	 *	 		<td>rbnnbkrq/pppppppp/8/8/8/8/PPPPPPPP/RBNNBKRQ w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>473</td>
	 *	 		<td>RNNBBKRQ</td>
	 *	 		<td>rnnbbkrq/pppppppp/8/8/8/8/PPPPPPPP/RNNBBKRQ w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>474</td>
	 *	 		<td>RNNKBBRQ</td>
	 *	 		<td>rnnkbbrq/pppppppp/8/8/8/8/PPPPPPPP/RNNKBBRQ w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>475</td>
	 *	 		<td>RNNKBRQB</td>
	 *	 		<td>rnnkbrqb/pppppppp/8/8/8/8/PPPPPPPP/RNNKBRQB w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>476</td>
	 *	 		<td>RBNNKRBQ</td>
	 *	 		<td>rbnnkrbq/pppppppp/8/8/8/8/PPPPPPPP/RBNNKRBQ w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>477</td>
	 *	 		<td>RNNBKRBQ</td>
	 *	 		<td>rnnbkrbq/pppppppp/8/8/8/8/PPPPPPPP/RNNBKRBQ w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>478</td>
	 *	 		<td>RNNKRBBQ</td>
	 *	 		<td>rnnkrbbq/pppppppp/8/8/8/8/PPPPPPPP/RNNKRBBQ w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>479</td>
	 *	 		<td>RNNKRQBB</td>
	 *	 		<td>rnnkrqbb/pppppppp/8/8/8/8/PPPPPPPP/RNNKRQBB w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>480</td>
	 *	 		<td>BBQRNKNR</td>
	 *	 		<td>bbqrnknr/pppppppp/8/8/8/8/PPPPPPPP/BBQRNKNR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>481</td>
	 *	 		<td>BQRBNKNR</td>
	 *	 		<td>bqrbnknr/pppppppp/8/8/8/8/PPPPPPPP/BQRBNKNR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>482</td>
	 *	 		<td>BQRNKBNR</td>
	 *	 		<td>bqrnkbnr/pppppppp/8/8/8/8/PPPPPPPP/BQRNKBNR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>483</td>
	 *	 		<td>BQRNKNRB</td>
	 *	 		<td>bqrnknrb/pppppppp/8/8/8/8/PPPPPPPP/BQRNKNRB w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>484</td>
	 *	 		<td>QBBRNKNR</td>
	 *	 		<td>qbbrnknr/pppppppp/8/8/8/8/PPPPPPPP/QBBRNKNR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>485</td>
	 *	 		<td>QRBBNKNR</td>
	 *	 		<td>qrbbnknr/pppppppp/8/8/8/8/PPPPPPPP/QRBBNKNR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>486</td>
	 *	 		<td>QRBNKBNR</td>
	 *	 		<td>qrbnkbnr/pppppppp/8/8/8/8/PPPPPPPP/QRBNKBNR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>487</td>
	 *	 		<td>QRBNKNRB</td>
	 *	 		<td>qrbnknrb/pppppppp/8/8/8/8/PPPPPPPP/QRBNKNRB w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>488</td>
	 *	 		<td>QBRNBKNR</td>
	 *	 		<td>qbrnbknr/pppppppp/8/8/8/8/PPPPPPPP/QBRNBKNR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>489</td>
	 *	 		<td>QRNBBKNR</td>
	 *	 		<td>qrnbbknr/pppppppp/8/8/8/8/PPPPPPPP/QRNBBKNR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>490</td>
	 *	 		<td>QRNKBBNR</td>
	 *	 		<td>qrnkbbnr/pppppppp/8/8/8/8/PPPPPPPP/QRNKBBNR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>491</td>
	 *	 		<td>QRNKBNRB</td>
	 *	 		<td>qrnkbnrb/pppppppp/8/8/8/8/PPPPPPPP/QRNKBNRB w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>492</td>
	 *	 		<td>QBRNKNBR</td>
	 *	 		<td>qbrnknbr/pppppppp/8/8/8/8/PPPPPPPP/QBRNKNBR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>493</td>
	 *	 		<td>QRNBKNBR</td>
	 *	 		<td>qrnbknbr/pppppppp/8/8/8/8/PPPPPPPP/QRNBKNBR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>494</td>
	 *	 		<td>QRNKNBBR</td>
	 *	 		<td>qrnknbbr/pppppppp/8/8/8/8/PPPPPPPP/QRNKNBBR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>495</td>
	 *	 		<td>QRNKNRBB</td>
	 *	 		<td>qrnknrbb/pppppppp/8/8/8/8/PPPPPPPP/QRNKNRBB w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>496</td>
	 *	 		<td>BBRQNKNR</td>
	 *	 		<td>bbrqnknr/pppppppp/8/8/8/8/PPPPPPPP/BBRQNKNR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>497</td>
	 *	 		<td>BRQBNKNR</td>
	 *	 		<td>brqbnknr/pppppppp/8/8/8/8/PPPPPPPP/BRQBNKNR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>498</td>
	 *	 		<td>BRQNKBNR</td>
	 *	 		<td>brqnkbnr/pppppppp/8/8/8/8/PPPPPPPP/BRQNKBNR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>499</td>
	 *	 		<td>BRQNKNRB</td>
	 *	 		<td>brqnknrb/pppppppp/8/8/8/8/PPPPPPPP/BRQNKNRB w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>500</td>
	 *	 		<td>RBBQNKNR</td>
	 *	 		<td>rbbqnknr/pppppppp/8/8/8/8/PPPPPPPP/RBBQNKNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>501</td>
	 *	 		<td>RQBBNKNR</td>
	 *	 		<td>rqbbnknr/pppppppp/8/8/8/8/PPPPPPPP/RQBBNKNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>502</td>
	 *	 		<td>RQBNKBNR</td>
	 *	 		<td>rqbnkbnr/pppppppp/8/8/8/8/PPPPPPPP/RQBNKBNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>503</td>
	 *	 		<td>RQBNKNRB</td>
	 *	 		<td>rqbnknrb/pppppppp/8/8/8/8/PPPPPPPP/RQBNKNRB w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>504</td>
	 *	 		<td>RBQNBKNR</td>
	 *	 		<td>rbqnbknr/pppppppp/8/8/8/8/PPPPPPPP/RBQNBKNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>505</td>
	 *	 		<td>RQNBBKNR</td>
	 *	 		<td>rqnbbknr/pppppppp/8/8/8/8/PPPPPPPP/RQNBBKNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>506</td>
	 *	 		<td>RQNKBBNR</td>
	 *	 		<td>rqnkbbnr/pppppppp/8/8/8/8/PPPPPPPP/RQNKBBNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>507</td>
	 *	 		<td>RQNKBNRB</td>
	 *	 		<td>rqnkbnrb/pppppppp/8/8/8/8/PPPPPPPP/RQNKBNRB w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>508</td>
	 *	 		<td>RBQNKNBR</td>
	 *	 		<td>rbqnknbr/pppppppp/8/8/8/8/PPPPPPPP/RBQNKNBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>509</td>
	 *	 		<td>RQNBKNBR</td>
	 *	 		<td>rqnbknbr/pppppppp/8/8/8/8/PPPPPPPP/RQNBKNBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>510</td>
	 *	 		<td>RQNKNBBR</td>
	 *	 		<td>rqnknbbr/pppppppp/8/8/8/8/PPPPPPPP/RQNKNBBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>511</td>
	 *	 		<td>RQNKNRBB</td>
	 *	 		<td>rqnknrbb/pppppppp/8/8/8/8/PPPPPPPP/RQNKNRBB w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>512</td>
	 *	 		<td>BBRNQKNR</td>
	 *	 		<td>bbrnqknr/pppppppp/8/8/8/8/PPPPPPPP/BBRNQKNR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>513</td>
	 *	 		<td>BRNBQKNR</td>
	 *	 		<td>brnbqknr/pppppppp/8/8/8/8/PPPPPPPP/BRNBQKNR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>514</td>
	 *	 		<td>BRNQKBNR</td>
	 *	 		<td>brnqkbnr/pppppppp/8/8/8/8/PPPPPPPP/BRNQKBNR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>515</td>
	 *	 		<td>BRNQKNRB</td>
	 *	 		<td>brnqknrb/pppppppp/8/8/8/8/PPPPPPPP/BRNQKNRB w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>516</td>
	 *	 		<td>RBBNQKNR</td>
	 *	 		<td>rbbnqknr/pppppppp/8/8/8/8/PPPPPPPP/RBBNQKNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>517</td>
	 *	 		<td>RNBBQKNR</td>
	 *	 		<td>rnbbqknr/pppppppp/8/8/8/8/PPPPPPPP/RNBBQKNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>518</td>
	 *	 		<td>RNBQKBNR</td>
	 *	 		<td>rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w HAha - 0 1</td>
	 *		</tr>
	 *	 	<tr>
	 *	 		<td>519</td>
	 *	 		<td>RNBQKNRB</td>
	 *	 		<td>rnbqknrb/pppppppp/8/8/8/8/PPPPPPPP/RNBQKNRB w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>520</td>
	 *	 		<td>RBNQBKNR</td>
	 *	 		<td>rbnqbknr/pppppppp/8/8/8/8/PPPPPPPP/RBNQBKNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>521</td>
	 *	 		<td>RNQBBKNR</td>
	 *	 		<td>rnqbbknr/pppppppp/8/8/8/8/PPPPPPPP/RNQBBKNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>522</td>
	 *	 		<td>RNQKBBNR</td>
	 *	 		<td>rnqkbbnr/pppppppp/8/8/8/8/PPPPPPPP/RNQKBBNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>523</td>
	 *	 		<td>RNQKBNRB</td>
	 *	 		<td>rnqkbnrb/pppppppp/8/8/8/8/PPPPPPPP/RNQKBNRB w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>524</td>
	 *	 		<td>RBNQKNBR</td>
	 *	 		<td>rbnqknbr/pppppppp/8/8/8/8/PPPPPPPP/RBNQKNBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>525</td>
	 *	 		<td>RNQBKNBR</td>
	 *	 		<td>rnqbknbr/pppppppp/8/8/8/8/PPPPPPPP/RNQBKNBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>526</td>
	 *	 		<td>RNQKNBBR</td>
	 *	 		<td>rnqknbbr/pppppppp/8/8/8/8/PPPPPPPP/RNQKNBBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>527</td>
	 *	 		<td>RNQKNRBB</td>
	 *	 		<td>rnqknrbb/pppppppp/8/8/8/8/PPPPPPPP/RNQKNRBB w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>528</td>
	 *	 		<td>BBRNKQNR</td>
	 *	 		<td>bbrnkqnr/pppppppp/8/8/8/8/PPPPPPPP/BBRNKQNR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>529</td>
	 *	 		<td>BRNBKQNR</td>
	 *	 		<td>brnbkqnr/pppppppp/8/8/8/8/PPPPPPPP/BRNBKQNR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>530</td>
	 *	 		<td>BRNKQBNR</td>
	 *	 		<td>brnkqbnr/pppppppp/8/8/8/8/PPPPPPPP/BRNKQBNR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>531</td>
	 *	 		<td>BRNKQNRB</td>
	 *	 		<td>brnkqnrb/pppppppp/8/8/8/8/PPPPPPPP/BRNKQNRB w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>532</td>
	 *	 		<td>RBBNKQNR</td>
	 *	 		<td>rbbnkqnr/pppppppp/8/8/8/8/PPPPPPPP/RBBNKQNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>533</td>
	 *	 		<td>RNBBKQNR</td>
	 *	 		<td>rnbbkqnr/pppppppp/8/8/8/8/PPPPPPPP/RNBBKQNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>534</td>
	 *	 		<td>RNBKQBNR</td>
	 *	 		<td>rnbkqbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBKQBNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>535</td>
	 *	 		<td>RNBKQNRB</td>
	 *	 		<td>rnbkqnrb/pppppppp/8/8/8/8/PPPPPPPP/RNBKQNRB w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>536</td>
	 *	 		<td>RBNKBQNR</td>
	 *	 		<td>rbnkbqnr/pppppppp/8/8/8/8/PPPPPPPP/RBNKBQNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>537</td>
	 *	 		<td>RNKBBQNR</td>
	 *	 		<td>rnkbbqnr/pppppppp/8/8/8/8/PPPPPPPP/RNKBBQNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>538</td>
	 *	 		<td>RNKQBBNR</td>
	 *	 		<td>rnkqbbnr/pppppppp/8/8/8/8/PPPPPPPP/RNKQBBNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>539</td>
	 *	 		<td>RNKQBNRB</td>
	 *	 		<td>rnkqbnrb/pppppppp/8/8/8/8/PPPPPPPP/RNKQBNRB w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>540</td>
	 *	 		<td>RBNKQNBR</td>
	 *	 		<td>rbnkqnbr/pppppppp/8/8/8/8/PPPPPPPP/RBNKQNBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>541</td>
	 *	 		<td>RNKBQNBR</td>
	 *	 		<td>rnkbqnbr/pppppppp/8/8/8/8/PPPPPPPP/RNKBQNBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>542</td>
	 *	 		<td>RNKQNBBR</td>
	 *	 		<td>rnkqnbbr/pppppppp/8/8/8/8/PPPPPPPP/RNKQNBBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>543</td>
	 *	 		<td>RNKQNRBB</td>
	 *	 		<td>rnkqnrbb/pppppppp/8/8/8/8/PPPPPPPP/RNKQNRBB w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>544</td>
	 *	 		<td>BBRNKNQR</td>
	 *	 		<td>bbrnknqr/pppppppp/8/8/8/8/PPPPPPPP/BBRNKNQR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>545</td>
	 *	 		<td>BRNBKNQR</td>
	 *	 		<td>brnbknqr/pppppppp/8/8/8/8/PPPPPPPP/BRNBKNQR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>546</td>
	 *	 		<td>BRNKNBQR</td>
	 *	 		<td>brnknbqr/pppppppp/8/8/8/8/PPPPPPPP/BRNKNBQR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>547</td>
	 *	 		<td>BRNKNQRB</td>
	 *	 		<td>brnknqrb/pppppppp/8/8/8/8/PPPPPPPP/BRNKNQRB w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>548</td>
	 *	 		<td>RBBNKNQR</td>
	 *	 		<td>rbbnknqr/pppppppp/8/8/8/8/PPPPPPPP/RBBNKNQR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>549</td>
	 *	 		<td>RNBBKNQR</td>
	 *	 		<td>rnbbknqr/pppppppp/8/8/8/8/PPPPPPPP/RNBBKNQR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>550</td>
	 *	 		<td>RNBKNBQR</td>
	 *	 		<td>rnbknbqr/pppppppp/8/8/8/8/PPPPPPPP/RNBKNBQR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>551</td>
	 *	 		<td>RNBKNQRB</td>
	 *	 		<td>rnbknqrb/pppppppp/8/8/8/8/PPPPPPPP/RNBKNQRB w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>552</td>
	 *	 		<td>RBNKBNQR</td>
	 *	 		<td>rbnkbnqr/pppppppp/8/8/8/8/PPPPPPPP/RBNKBNQR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>553</td>
	 *	 		<td>RNKBBNQR</td>
	 *	 		<td>rnkbbnqr/pppppppp/8/8/8/8/PPPPPPPP/RNKBBNQR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>554</td>
	 *	 		<td>RNKNBBQR</td>
	 *	 		<td>rnknbbqr/pppppppp/8/8/8/8/PPPPPPPP/RNKNBBQR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>555</td>
	 *	 		<td>RNKNBQRB</td>
	 *	 		<td>rnknbqrb/pppppppp/8/8/8/8/PPPPPPPP/RNKNBQRB w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>556</td>
	 *	 		<td>RBNKNQBR</td>
	 *	 		<td>rbnknqbr/pppppppp/8/8/8/8/PPPPPPPP/RBNKNQBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>557</td>
	 *	 		<td>RNKBNQBR</td>
	 *	 		<td>rnkbnqbr/pppppppp/8/8/8/8/PPPPPPPP/RNKBNQBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>558</td>
	 *	 		<td>RNKNQBBR</td>
	 *	 		<td>rnknqbbr/pppppppp/8/8/8/8/PPPPPPPP/RNKNQBBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>559</td>
	 *	 		<td>RNKNQRBB</td>
	 *	 		<td>rnknqrbb/pppppppp/8/8/8/8/PPPPPPPP/RNKNQRBB w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>560</td>
	 *	 		<td>BBRNKNRQ</td>
	 *	 		<td>bbrnknrq/pppppppp/8/8/8/8/PPPPPPPP/BBRNKNRQ w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>561</td>
	 *	 		<td>BRNBKNRQ</td>
	 *	 		<td>brnbknrq/pppppppp/8/8/8/8/PPPPPPPP/BRNBKNRQ w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>562</td>
	 *	 		<td>BRNKNBRQ</td>
	 *	 		<td>brnknbrq/pppppppp/8/8/8/8/PPPPPPPP/BRNKNBRQ w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>563</td>
	 *	 		<td>BRNKNRQB</td>
	 *	 		<td>brnknrqb/pppppppp/8/8/8/8/PPPPPPPP/BRNKNRQB w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>564</td>
	 *	 		<td>RBBNKNRQ</td>
	 *	 		<td>rbbnknrq/pppppppp/8/8/8/8/PPPPPPPP/RBBNKNRQ w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>565</td>
	 *	 		<td>RNBBKNRQ</td>
	 *	 		<td>rnbbknrq/pppppppp/8/8/8/8/PPPPPPPP/RNBBKNRQ w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>566</td>
	 *	 		<td>RNBKNBRQ</td>
	 *	 		<td>rnbknbrq/pppppppp/8/8/8/8/PPPPPPPP/RNBKNBRQ w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>567</td>
	 *	 		<td>RNBKNRQB</td>
	 *	 		<td>rnbknrqb/pppppppp/8/8/8/8/PPPPPPPP/RNBKNRQB w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>568</td>
	 *	 		<td>RBNKBNRQ</td>
	 *	 		<td>rbnkbnrq/pppppppp/8/8/8/8/PPPPPPPP/RBNKBNRQ w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>569</td>
	 *	 		<td>RNKBBNRQ</td>
	 *	 		<td>rnkbbnrq/pppppppp/8/8/8/8/PPPPPPPP/RNKBBNRQ w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>570</td>
	 *	 		<td>RNKNBBRQ</td>
	 *	 		<td>rnknbbrq/pppppppp/8/8/8/8/PPPPPPPP/RNKNBBRQ w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>571</td>
	 *	 		<td>RNKNBRQB</td>
	 *	 		<td>rnknbrqb/pppppppp/8/8/8/8/PPPPPPPP/RNKNBRQB w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>572</td>
	 *	 		<td>RBNKNRBQ</td>
	 *	 		<td>rbnknrbq/pppppppp/8/8/8/8/PPPPPPPP/RBNKNRBQ w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>573</td>
	 *	 		<td>RNKBNRBQ</td>
	 *	 		<td>rnkbnrbq/pppppppp/8/8/8/8/PPPPPPPP/RNKBNRBQ w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>574</td>
	 *	 		<td>RNKNRBBQ</td>
	 *	 		<td>rnknrbbq/pppppppp/8/8/8/8/PPPPPPPP/RNKNRBBQ w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>575</td>
	 *	 		<td>RNKNRQBB</td>
	 *	 		<td>rnknrqbb/pppppppp/8/8/8/8/PPPPPPPP/RNKNRQBB w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>576</td>
	 *	 		<td>BBQRNKRN</td>
	 *	 		<td>bbqrnkrn/pppppppp/8/8/8/8/PPPPPPPP/BBQRNKRN w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>577</td>
	 *	 		<td>BQRBNKRN</td>
	 *	 		<td>bqrbnkrn/pppppppp/8/8/8/8/PPPPPPPP/BQRBNKRN w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>578</td>
	 *	 		<td>BQRNKBRN</td>
	 *	 		<td>bqrnkbrn/pppppppp/8/8/8/8/PPPPPPPP/BQRNKBRN w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>579</td>
	 *	 		<td>BQRNKRNB</td>
	 *	 		<td>bqrnkrnb/pppppppp/8/8/8/8/PPPPPPPP/BQRNKRNB w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>580</td>
	 *	 		<td>QBBRNKRN</td>
	 *	 		<td>qbbrnkrn/pppppppp/8/8/8/8/PPPPPPPP/QBBRNKRN w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>581</td>
	 *	 		<td>QRBBNKRN</td>
	 *	 		<td>qrbbnkrn/pppppppp/8/8/8/8/PPPPPPPP/QRBBNKRN w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>582</td>
	 *	 		<td>QRBNKBRN</td>
	 *	 		<td>qrbnkbrn/pppppppp/8/8/8/8/PPPPPPPP/QRBNKBRN w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>583</td>
	 *	 		<td>QRBNKRNB</td>
	 *	 		<td>qrbnkrnb/pppppppp/8/8/8/8/PPPPPPPP/QRBNKRNB w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>584</td>
	 *	 		<td>QBRNBKRN</td>
	 *	 		<td>qbrnbkrn/pppppppp/8/8/8/8/PPPPPPPP/QBRNBKRN w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>585</td>
	 *	 		<td>QRNBBKRN</td>
	 *	 		<td>qrnbbkrn/pppppppp/8/8/8/8/PPPPPPPP/QRNBBKRN w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>586</td>
	 *	 		<td>QRNKBBRN</td>
	 *	 		<td>qrnkbbrn/pppppppp/8/8/8/8/PPPPPPPP/QRNKBBRN w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>587</td>
	 *	 		<td>QRNKBRNB</td>
	 *	 		<td>qrnkbrnb/pppppppp/8/8/8/8/PPPPPPPP/QRNKBRNB w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>588</td>
	 *	 		<td>QBRNKRBN</td>
	 *	 		<td>qbrnkrbn/pppppppp/8/8/8/8/PPPPPPPP/QBRNKRBN w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>589</td>
	 *	 		<td>QRNBKRBN</td>
	 *	 		<td>qrnbkrbn/pppppppp/8/8/8/8/PPPPPPPP/QRNBKRBN w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>590</td>
	 *	 		<td>QRNKRBBN</td>
	 *	 		<td>qrnkrbbn/pppppppp/8/8/8/8/PPPPPPPP/QRNKRBBN w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>591</td>
	 *	 		<td>QRNKRNBB</td>
	 *	 		<td>qrnkrnbb/pppppppp/8/8/8/8/PPPPPPPP/QRNKRNBB w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>592</td>
	 *	 		<td>BBRQNKRN</td>
	 *	 		<td>bbrqnkrn/pppppppp/8/8/8/8/PPPPPPPP/BBRQNKRN w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>593</td>
	 *	 		<td>BRQBNKRN</td>
	 *	 		<td>brqbnkrn/pppppppp/8/8/8/8/PPPPPPPP/BRQBNKRN w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>594</td>
	 *	 		<td>BRQNKBRN</td>
	 *	 		<td>brqnkbrn/pppppppp/8/8/8/8/PPPPPPPP/BRQNKBRN w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>595</td>
	 *	 		<td>BRQNKRNB</td>
	 *	 		<td>brqnkrnb/pppppppp/8/8/8/8/PPPPPPPP/BRQNKRNB w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>596</td>
	 *	 		<td>RBBQNKRN</td>
	 *	 		<td>rbbqnkrn/pppppppp/8/8/8/8/PPPPPPPP/RBBQNKRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>597</td>
	 *	 		<td>RQBBNKRN</td>
	 *	 		<td>rqbbnkrn/pppppppp/8/8/8/8/PPPPPPPP/RQBBNKRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>598</td>
	 *	 		<td>RQBNKBRN</td>
	 *	 		<td>rqbnkbrn/pppppppp/8/8/8/8/PPPPPPPP/RQBNKBRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>599</td>
	 *	 		<td>RQBNKRNB</td>
	 *	 		<td>rqbnkrnb/pppppppp/8/8/8/8/PPPPPPPP/RQBNKRNB w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>600</td>
	 *	 		<td>RBQNBKRN</td>
	 *	 		<td>rbqnbkrn/pppppppp/8/8/8/8/PPPPPPPP/RBQNBKRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>601</td>
	 *	 		<td>RQNBBKRN</td>
	 *	 		<td>rqnbbkrn/pppppppp/8/8/8/8/PPPPPPPP/RQNBBKRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>602</td>
	 *	 		<td>RQNKBBRN</td>
	 *	 		<td>rqnkbbrn/pppppppp/8/8/8/8/PPPPPPPP/RQNKBBRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>603</td>
	 *	 		<td>RQNKBRNB</td>
	 *	 		<td>rqnkbrnb/pppppppp/8/8/8/8/PPPPPPPP/RQNKBRNB w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>604</td>
	 *	 		<td>RBQNKRBN</td>
	 *	 		<td>rbqnkrbn/pppppppp/8/8/8/8/PPPPPPPP/RBQNKRBN w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>605</td>
	 *	 		<td>RQNBKRBN</td>
	 *	 		<td>rqnbkrbn/pppppppp/8/8/8/8/PPPPPPPP/RQNBKRBN w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>606</td>
	 *	 		<td>RQNKRBBN</td>
	 *	 		<td>rqnkrbbn/pppppppp/8/8/8/8/PPPPPPPP/RQNKRBBN w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>607</td>
	 *	 		<td>RQNKRNBB</td>
	 *	 		<td>rqnkrnbb/pppppppp/8/8/8/8/PPPPPPPP/RQNKRNBB w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>608</td>
	 *	 		<td>BBRNQKRN</td>
	 *	 		<td>bbrnqkrn/pppppppp/8/8/8/8/PPPPPPPP/BBRNQKRN w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>609</td>
	 *	 		<td>BRNBQKRN</td>
	 *	 		<td>brnbqkrn/pppppppp/8/8/8/8/PPPPPPPP/BRNBQKRN w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>610</td>
	 *	 		<td>BRNQKBRN</td>
	 *	 		<td>brnqkbrn/pppppppp/8/8/8/8/PPPPPPPP/BRNQKBRN w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>611</td>
	 *	 		<td>BRNQKRNB</td>
	 *	 		<td>brnqkrnb/pppppppp/8/8/8/8/PPPPPPPP/BRNQKRNB w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>612</td>
	 *	 		<td>RBBNQKRN</td>
	 *	 		<td>rbbnqkrn/pppppppp/8/8/8/8/PPPPPPPP/RBBNQKRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>613</td>
	 *	 		<td>RNBBQKRN</td>
	 *	 		<td>rnbbqkrn/pppppppp/8/8/8/8/PPPPPPPP/RNBBQKRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>614</td>
	 *	 		<td>RNBQKBRN</td>
	 *	 		<td>rnbqkbrn/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>615</td>
	 *	 		<td>RNBQKRNB</td>
	 *	 		<td>rnbqkrnb/pppppppp/8/8/8/8/PPPPPPPP/RNBQKRNB w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>616</td>
	 *	 		<td>RBNQBKRN</td>
	 *	 		<td>rbnqbkrn/pppppppp/8/8/8/8/PPPPPPPP/RBNQBKRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>617</td>
	 *	 		<td>RNQBBKRN</td>
	 *	 		<td>rnqbbkrn/pppppppp/8/8/8/8/PPPPPPPP/RNQBBKRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>618</td>
	 *	 		<td>RNQKBBRN</td>
	 *	 		<td>rnqkbbrn/pppppppp/8/8/8/8/PPPPPPPP/RNQKBBRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>619</td>
	 *	 		<td>RNQKBRNB</td>
	 *	 		<td>rnqkbrnb/pppppppp/8/8/8/8/PPPPPPPP/RNQKBRNB w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>620</td>
	 *	 		<td>RBNQKRBN</td>
	 *	 		<td>rbnqkrbn/pppppppp/8/8/8/8/PPPPPPPP/RBNQKRBN w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>621</td>
	 *	 		<td>RNQBKRBN</td>
	 *	 		<td>rnqbkrbn/pppppppp/8/8/8/8/PPPPPPPP/RNQBKRBN w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>622</td>
	 *	 		<td>RNQKRBBN</td>
	 *	 		<td>rnqkrbbn/pppppppp/8/8/8/8/PPPPPPPP/RNQKRBBN w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>623</td>
	 *	 		<td>RNQKRNBB</td>
	 *	 		<td>rnqkrnbb/pppppppp/8/8/8/8/PPPPPPPP/RNQKRNBB w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>624</td>
	 *	 		<td>BBRNKQRN</td>
	 *	 		<td>bbrnkqrn/pppppppp/8/8/8/8/PPPPPPPP/BBRNKQRN w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>625</td>
	 *	 		<td>BRNBKQRN</td>
	 *	 		<td>brnbkqrn/pppppppp/8/8/8/8/PPPPPPPP/BRNBKQRN w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>626</td>
	 *	 		<td>BRNKQBRN</td>
	 *	 		<td>brnkqbrn/pppppppp/8/8/8/8/PPPPPPPP/BRNKQBRN w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>627</td>
	 *	 		<td>BRNKQRNB</td>
	 *	 		<td>brnkqrnb/pppppppp/8/8/8/8/PPPPPPPP/BRNKQRNB w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>628</td>
	 *	 		<td>RBBNKQRN</td>
	 *	 		<td>rbbnkqrn/pppppppp/8/8/8/8/PPPPPPPP/RBBNKQRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>629</td>
	 *	 		<td>RNBBKQRN</td>
	 *	 		<td>rnbbkqrn/pppppppp/8/8/8/8/PPPPPPPP/RNBBKQRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>630</td>
	 *	 		<td>RNBKQBRN</td>
	 *	 		<td>rnbkqbrn/pppppppp/8/8/8/8/PPPPPPPP/RNBKQBRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>631</td>
	 *	 		<td>RNBKQRNB</td>
	 *	 		<td>rnbkqrnb/pppppppp/8/8/8/8/PPPPPPPP/RNBKQRNB w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>632</td>
	 *	 		<td>RBNKBQRN</td>
	 *	 		<td>rbnkbqrn/pppppppp/8/8/8/8/PPPPPPPP/RBNKBQRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>633</td>
	 *	 		<td>RNKBBQRN</td>
	 *	 		<td>rnkbbqrn/pppppppp/8/8/8/8/PPPPPPPP/RNKBBQRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>634</td>
	 *	 		<td>RNKQBBRN</td>
	 *	 		<td>rnkqbbrn/pppppppp/8/8/8/8/PPPPPPPP/RNKQBBRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>635</td>
	 *	 		<td>RNKQBRNB</td>
	 *	 		<td>rnkqbrnb/pppppppp/8/8/8/8/PPPPPPPP/RNKQBRNB w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>636</td>
	 *	 		<td>RBNKQRBN</td>
	 *	 		<td>rbnkqrbn/pppppppp/8/8/8/8/PPPPPPPP/RBNKQRBN w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>637</td>
	 *	 		<td>RNKBQRBN</td>
	 *	 		<td>rnkbqrbn/pppppppp/8/8/8/8/PPPPPPPP/RNKBQRBN w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>638</td>
	 *	 		<td>RNKQRBBN</td>
	 *	 		<td>rnkqrbbn/pppppppp/8/8/8/8/PPPPPPPP/RNKQRBBN w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>639</td>
	 *	 		<td>RNKQRNBB</td>
	 *	 		<td>rnkqrnbb/pppppppp/8/8/8/8/PPPPPPPP/RNKQRNBB w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>640</td>
	 *	 		<td>BBRNKRQN</td>
	 *	 		<td>bbrnkrqn/pppppppp/8/8/8/8/PPPPPPPP/BBRNKRQN w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>641</td>
	 *	 		<td>BRNBKRQN</td>
	 *	 		<td>brnbkrqn/pppppppp/8/8/8/8/PPPPPPPP/BRNBKRQN w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>642</td>
	 *	 		<td>BRNKRBQN</td>
	 *	 		<td>brnkrbqn/pppppppp/8/8/8/8/PPPPPPPP/BRNKRBQN w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>643</td>
	 *	 		<td>BRNKRQNB</td>
	 *	 		<td>brnkrqnb/pppppppp/8/8/8/8/PPPPPPPP/BRNKRQNB w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>644</td>
	 *	 		<td>RBBNKRQN</td>
	 *	 		<td>rbbnkrqn/pppppppp/8/8/8/8/PPPPPPPP/RBBNKRQN w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>645</td>
	 *	 		<td>RNBBKRQN</td>
	 *	 		<td>rnbbkrqn/pppppppp/8/8/8/8/PPPPPPPP/RNBBKRQN w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>646</td>
	 *	 		<td>RNBKRBQN</td>
	 *	 		<td>rnbkrbqn/pppppppp/8/8/8/8/PPPPPPPP/RNBKRBQN w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>647</td>
	 *	 		<td>RNBKRQNB</td>
	 *	 		<td>rnbkrqnb/pppppppp/8/8/8/8/PPPPPPPP/RNBKRQNB w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>648</td>
	 *	 		<td>RBNKBRQN</td>
	 *	 		<td>rbnkbrqn/pppppppp/8/8/8/8/PPPPPPPP/RBNKBRQN w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>649</td>
	 *	 		<td>RNKBBRQN</td>
	 *	 		<td>rnkbbrqn/pppppppp/8/8/8/8/PPPPPPPP/RNKBBRQN w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>650</td>
	 *	 		<td>RNKRBBQN</td>
	 *	 		<td>rnkrbbqn/pppppppp/8/8/8/8/PPPPPPPP/RNKRBBQN w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>651</td>
	 *	 		<td>RNKRBQNB</td>
	 *	 		<td>rnkrbqnb/pppppppp/8/8/8/8/PPPPPPPP/RNKRBQNB w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>652</td>
	 *	 		<td>RBNKRQBN</td>
	 *	 		<td>rbnkrqbn/pppppppp/8/8/8/8/PPPPPPPP/RBNKRQBN w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>653</td>
	 *	 		<td>RNKBRQBN</td>
	 *	 		<td>rnkbrqbn/pppppppp/8/8/8/8/PPPPPPPP/RNKBRQBN w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>654</td>
	 *	 		<td>RNKRQBBN</td>
	 *	 		<td>rnkrqbbn/pppppppp/8/8/8/8/PPPPPPPP/RNKRQBBN w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>655</td>
	 *	 		<td>RNKRQNBB</td>
	 *	 		<td>rnkrqnbb/pppppppp/8/8/8/8/PPPPPPPP/RNKRQNBB w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>656</td>
	 *	 		<td>BBRNKRNQ</td>
	 *	 		<td>bbrnkrnq/pppppppp/8/8/8/8/PPPPPPPP/BBRNKRNQ w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>657</td>
	 *	 		<td>BRNBKRNQ</td>
	 *	 		<td>brnbkrnq/pppppppp/8/8/8/8/PPPPPPPP/BRNBKRNQ w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>658</td>
	 *	 		<td>BRNKRBNQ</td>
	 *	 		<td>brnkrbnq/pppppppp/8/8/8/8/PPPPPPPP/BRNKRBNQ w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>659</td>
	 *	 		<td>BRNKRNQB</td>
	 *	 		<td>brnkrnqb/pppppppp/8/8/8/8/PPPPPPPP/BRNKRNQB w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>660</td>
	 *	 		<td>RBBNKRNQ</td>
	 *	 		<td>rbbnkrnq/pppppppp/8/8/8/8/PPPPPPPP/RBBNKRNQ w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>661</td>
	 *	 		<td>RNBBKRNQ</td>
	 *	 		<td>rnbbkrnq/pppppppp/8/8/8/8/PPPPPPPP/RNBBKRNQ w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>662</td>
	 *	 		<td>RNBKRBNQ</td>
	 *	 		<td>rnbkrbnq/pppppppp/8/8/8/8/PPPPPPPP/RNBKRBNQ w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>663</td>
	 *	 		<td>RNBKRNQB</td>
	 *	 		<td>rnbkrnqb/pppppppp/8/8/8/8/PPPPPPPP/RNBKRNQB w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>664</td>
	 *	 		<td>RBNKBRNQ</td>
	 *	 		<td>rbnkbrnq/pppppppp/8/8/8/8/PPPPPPPP/RBNKBRNQ w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>665</td>
	 *	 		<td>RNKBBRNQ</td>
	 *	 		<td>rnkbbrnq/pppppppp/8/8/8/8/PPPPPPPP/RNKBBRNQ w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>666</td>
	 *	 		<td>RNKRBBNQ</td>
	 *	 		<td>rnkrbbnq/pppppppp/8/8/8/8/PPPPPPPP/RNKRBBNQ w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>667</td>
	 *	 		<td>RNKRBNQB</td>
	 *	 		<td>rnkrbnqb/pppppppp/8/8/8/8/PPPPPPPP/RNKRBNQB w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>668</td>
	 *	 		<td>RBNKRNBQ</td>
	 *	 		<td>rbnkrnbq/pppppppp/8/8/8/8/PPPPPPPP/RBNKRNBQ w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>669</td>
	 *	 		<td>RNKBRNBQ</td>
	 *	 		<td>rnkbrnbq/pppppppp/8/8/8/8/PPPPPPPP/RNKBRNBQ w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>670</td>
	 *	 		<td>RNKRNBBQ</td>
	 *	 		<td>rnkrnbbq/pppppppp/8/8/8/8/PPPPPPPP/RNKRNBBQ w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>671</td>
	 *	 		<td>RNKRNQBB</td>
	 *	 		<td>rnkrnqbb/pppppppp/8/8/8/8/PPPPPPPP/RNKRNQBB w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>672</td>
	 *	 		<td>BBQRKNNR</td>
	 *	 		<td>bbqrknnr/pppppppp/8/8/8/8/PPPPPPPP/BBQRKNNR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>673</td>
	 *	 		<td>BQRBKNNR</td>
	 *	 		<td>bqrbknnr/pppppppp/8/8/8/8/PPPPPPPP/BQRBKNNR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>674</td>
	 *	 		<td>BQRKNBNR</td>
	 *	 		<td>bqrknbnr/pppppppp/8/8/8/8/PPPPPPPP/BQRKNBNR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>675</td>
	 *	 		<td>BQRKNNRB</td>
	 *	 		<td>bqrknnrb/pppppppp/8/8/8/8/PPPPPPPP/BQRKNNRB w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>676</td>
	 *	 		<td>QBBRKNNR</td>
	 *	 		<td>qbbrknnr/pppppppp/8/8/8/8/PPPPPPPP/QBBRKNNR w HDhd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>677</td>
	 *	 		<td>QRBBKNNR</td>
	 *	 		<td>qrbbknnr/pppppppp/8/8/8/8/PPPPPPPP/QRBBKNNR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>678</td>
	 *	 		<td>QRBKNBNR</td>
	 *	 		<td>qrbknbnr/pppppppp/8/8/8/8/PPPPPPPP/QRBKNBNR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>679</td>
	 *	 		<td>QRBKNNRB</td>
	 *	 		<td>qrbknnrb/pppppppp/8/8/8/8/PPPPPPPP/QRBKNNRB w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>680</td>
	 *	 		<td>QBRKBNNR</td>
	 *	 		<td>qbrkbnnr/pppppppp/8/8/8/8/PPPPPPPP/QBRKBNNR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>681</td>
	 *	 		<td>QRKBBNNR</td>
	 *	 		<td>qrkbbnnr/pppppppp/8/8/8/8/PPPPPPPP/QRKBBNNR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>682</td>
	 *	 		<td>QRKNBBNR</td>
	 *	 		<td>qrknbbnr/pppppppp/8/8/8/8/PPPPPPPP/QRKNBBNR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>683</td>
	 *	 		<td>QRKNBNRB</td>
	 *	 		<td>qrknbnrb/pppppppp/8/8/8/8/PPPPPPPP/QRKNBNRB w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>684</td>
	 *	 		<td>QBRKNNBR</td>
	 *	 		<td>qbrknnbr/pppppppp/8/8/8/8/PPPPPPPP/QBRKNNBR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>685</td>
	 *	 		<td>QRKBNNBR</td>
	 *	 		<td>qrkbnnbr/pppppppp/8/8/8/8/PPPPPPPP/QRKBNNBR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>686</td>
	 *	 		<td>QRKNNBBR</td>
	 *	 		<td>qrknnbbr/pppppppp/8/8/8/8/PPPPPPPP/QRKNNBBR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>687</td>
	 *	 		<td>QRKNNRBB</td>
	 *	 		<td>qrknnrbb/pppppppp/8/8/8/8/PPPPPPPP/QRKNNRBB w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>688</td>
	 *	 		<td>BBRQKNNR</td>
	 *	 		<td>bbrqknnr/pppppppp/8/8/8/8/PPPPPPPP/BBRQKNNR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>689</td>
	 *	 		<td>BRQBKNNR</td>
	 *	 		<td>brqbknnr/pppppppp/8/8/8/8/PPPPPPPP/BRQBKNNR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>690</td>
	 *	 		<td>BRQKNBNR</td>
	 *	 		<td>brqknbnr/pppppppp/8/8/8/8/PPPPPPPP/BRQKNBNR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>691</td>
	 *	 		<td>BRQKNNRB</td>
	 *	 		<td>brqknnrb/pppppppp/8/8/8/8/PPPPPPPP/BRQKNNRB w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>692</td>
	 *	 		<td>RBBQKNNR</td>
	 *	 		<td>rbbqknnr/pppppppp/8/8/8/8/PPPPPPPP/RBBQKNNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>693</td>
	 *	 		<td>RQBBKNNR</td>
	 *	 		<td>rqbbknnr/pppppppp/8/8/8/8/PPPPPPPP/RQBBKNNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>694</td>
	 *	 		<td>RQBKNBNR</td>
	 *	 		<td>rqbknbnr/pppppppp/8/8/8/8/PPPPPPPP/RQBKNBNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>695</td>
	 *	 		<td>RQBKNNRB</td>
	 *	 		<td>rqbknnrb/pppppppp/8/8/8/8/PPPPPPPP/RQBKNNRB w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>696</td>
	 *	 		<td>RBQKBNNR</td>
	 *	 		<td>rbqkbnnr/pppppppp/8/8/8/8/PPPPPPPP/RBQKBNNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>697</td>
	 *	 		<td>RQKBBNNR</td>
	 *	 		<td>rqkbbnnr/pppppppp/8/8/8/8/PPPPPPPP/RQKBBNNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>698</td>
	 *	 		<td>RQKNBBNR</td>
	 *	 		<td>rqknbbnr/pppppppp/8/8/8/8/PPPPPPPP/RQKNBBNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>699</td>
	 *	 		<td>RQKNBNRB</td>
	 *	 		<td>rqknbnrb/pppppppp/8/8/8/8/PPPPPPPP/RQKNBNRB w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>700</td>
	 *	 		<td>RBQKNNBR</td>
	 *	 		<td>rbqknnbr/pppppppp/8/8/8/8/PPPPPPPP/RBQKNNBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>701</td>
	 *	 		<td>RQKBNNBR</td>
	 *	 		<td>rqkbnnbr/pppppppp/8/8/8/8/PPPPPPPP/RQKBNNBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>702</td>
	 *	 		<td>RQKNNBBR</td>
	 *	 		<td>rqknnbbr/pppppppp/8/8/8/8/PPPPPPPP/RQKNNBBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>703</td>
	 *	 		<td>RQKNNRBB</td>
	 *	 		<td>rqknnrbb/pppppppp/8/8/8/8/PPPPPPPP/RQKNNRBB w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>704</td>
	 *	 		<td>BBRKQNNR</td>
	 *	 		<td>bbrkqnnr/pppppppp/8/8/8/8/PPPPPPPP/BBRKQNNR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>705</td>
	 *	 		<td>BRKBQNNR</td>
	 *	 		<td>brkbqnnr/pppppppp/8/8/8/8/PPPPPPPP/BRKBQNNR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>706</td>
	 *	 		<td>BRKQNBNR</td>
	 *	 		<td>brkqnbnr/pppppppp/8/8/8/8/PPPPPPPP/BRKQNBNR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>707</td>
	 *	 		<td>BRKQNNRB</td>
	 *	 		<td>brkqnnrb/pppppppp/8/8/8/8/PPPPPPPP/BRKQNNRB w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>708</td>
	 *	 		<td>RBBKQNNR</td>
	 *	 		<td>rbbkqnnr/pppppppp/8/8/8/8/PPPPPPPP/RBBKQNNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>709</td>
	 *	 		<td>RKBBQNNR</td>
	 *	 		<td>rkbbqnnr/pppppppp/8/8/8/8/PPPPPPPP/RKBBQNNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>710</td>
	 *	 		<td>RKBQNBNR</td>
	 *	 		<td>rkbqnbnr/pppppppp/8/8/8/8/PPPPPPPP/RKBQNBNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>711</td>
	 *	 		<td>RKBQNNRB</td>
	 *	 		<td>rkbqnnrb/pppppppp/8/8/8/8/PPPPPPPP/RKBQNNRB w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>712</td>
	 *	 		<td>RBKQBNNR</td>
	 *	 		<td>rbkqbnnr/pppppppp/8/8/8/8/PPPPPPPP/RBKQBNNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>713</td>
	 *	 		<td>RKQBBNNR</td>
	 *	 		<td>rkqbbnnr/pppppppp/8/8/8/8/PPPPPPPP/RKQBBNNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>714</td>
	 *	 		<td>RKQNBBNR</td>
	 *	 		<td>rkqnbbnr/pppppppp/8/8/8/8/PPPPPPPP/RKQNBBNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>715</td>
	 *	 		<td>RKQNBNRB</td>
	 *	 		<td>rkqnbnrb/pppppppp/8/8/8/8/PPPPPPPP/RKQNBNRB w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>716</td>
	 *	 		<td>RBKQNNBR</td>
	 *	 		<td>rbkqnnbr/pppppppp/8/8/8/8/PPPPPPPP/RBKQNNBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>717</td>
	 *	 		<td>RKQBNNBR</td>
	 *	 		<td>rkqbnnbr/pppppppp/8/8/8/8/PPPPPPPP/RKQBNNBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>718</td>
	 *	 		<td>RKQNNBBR</td>
	 *	 		<td>rkqnnbbr/pppppppp/8/8/8/8/PPPPPPPP/RKQNNBBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>719</td>
	 *	 		<td>RKQNNRBB</td>
	 *	 		<td>rkqnnrbb/pppppppp/8/8/8/8/PPPPPPPP/RKQNNRBB w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>720</td>
	 *	 		<td>BBRKNQNR</td>
	 *	 		<td>bbrknqnr/pppppppp/8/8/8/8/PPPPPPPP/BBRKNQNR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>721</td>
	 *	 		<td>BRKBNQNR</td>
	 *	 		<td>brkbnqnr/pppppppp/8/8/8/8/PPPPPPPP/BRKBNQNR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>722</td>
	 *	 		<td>BRKNQBNR</td>
	 *	 		<td>brknqbnr/pppppppp/8/8/8/8/PPPPPPPP/BRKNQBNR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>723</td>
	 *	 		<td>BRKNQNRB</td>
	 *	 		<td>brknqnrb/pppppppp/8/8/8/8/PPPPPPPP/BRKNQNRB w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>724</td>
	 *	 		<td>RBBKNQNR</td>
	 *	 		<td>rbbknqnr/pppppppp/8/8/8/8/PPPPPPPP/RBBKNQNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>725</td>
	 *	 		<td>RKBBNQNR</td>
	 *	 		<td>rkbbnqnr/pppppppp/8/8/8/8/PPPPPPPP/RKBBNQNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>726</td>
	 *	 		<td>RKBNQBNR</td>
	 *	 		<td>rkbnqbnr/pppppppp/8/8/8/8/PPPPPPPP/RKBNQBNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>727</td>
	 *	 		<td>RKBNQNRB</td>
	 *	 		<td>rkbnqnrb/pppppppp/8/8/8/8/PPPPPPPP/RKBNQNRB w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>728</td>
	 *	 		<td>RBKNBQNR</td>
	 *	 		<td>rbknbqnr/pppppppp/8/8/8/8/PPPPPPPP/RBKNBQNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>729</td>
	 *	 		<td>RKNBBQNR</td>
	 *	 		<td>rknbbqnr/pppppppp/8/8/8/8/PPPPPPPP/RKNBBQNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>730</td>
	 *	 		<td>RKNQBBNR</td>
	 *	 		<td>rknqbbnr/pppppppp/8/8/8/8/PPPPPPPP/RKNQBBNR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>731</td>
	 *	 		<td>RKNQBNRB</td>
	 *	 		<td>rknqbnrb/pppppppp/8/8/8/8/PPPPPPPP/RKNQBNRB w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>732</td>
	 *	 		<td>RBKNQNBR</td>
	 *	 		<td>rbknqnbr/pppppppp/8/8/8/8/PPPPPPPP/RBKNQNBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>733</td>
	 *	 		<td>RKNBQNBR</td>
	 *	 		<td>rknbqnbr/pppppppp/8/8/8/8/PPPPPPPP/RKNBQNBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>734</td>
	 *	 		<td>RKNQNBBR</td>
	 *	 		<td>rknqnbbr/pppppppp/8/8/8/8/PPPPPPPP/RKNQNBBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>735</td>
	 *	 		<td>RKNQNRBB</td>
	 *	 		<td>rknqnrbb/pppppppp/8/8/8/8/PPPPPPPP/RKNQNRBB w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>736</td>
	 *	 		<td>BBRKNNQR</td>
	 *	 		<td>bbrknnqr/pppppppp/8/8/8/8/PPPPPPPP/BBRKNNQR w HChc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>737</td>
	 *	 		<td>BRKBNNQR</td>
	 *	 		<td>brkbnnqr/pppppppp/8/8/8/8/PPPPPPPP/BRKBNNQR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>738</td>
	 *	 		<td>BRKNNBQR</td>
	 *	 		<td>brknnbqr/pppppppp/8/8/8/8/PPPPPPPP/BRKNNBQR w HBhb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>739</td>
	 *	 		<td>BRKNNQRB</td>
	 *	 		<td>brknnqrb/pppppppp/8/8/8/8/PPPPPPPP/BRKNNQRB w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>740</td>
	 *	 		<td>RBBKNNQR</td>
	 *	 		<td>rbbknnqr/pppppppp/8/8/8/8/PPPPPPPP/RBBKNNQR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>741</td>
	 *	 		<td>RKBBNNQR</td>
	 *	 		<td>rkbbnnqr/pppppppp/8/8/8/8/PPPPPPPP/RKBBNNQR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>742</td>
	 *	 		<td>RKBNNBQR</td>
	 *	 		<td>rkbnnbqr/pppppppp/8/8/8/8/PPPPPPPP/RKBNNBQR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>743</td>
	 *	 		<td>RKBNNQRB</td>
	 *	 		<td>rkbnnqrb/pppppppp/8/8/8/8/PPPPPPPP/RKBNNQRB w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>744</td>
	 *	 		<td>RBKNBNQR</td>
	 *	 		<td>rbknbnqr/pppppppp/8/8/8/8/PPPPPPPP/RBKNBNQR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>745</td>
	 *	 		<td>RKNBBNQR</td>
	 *	 		<td>rknbbnqr/pppppppp/8/8/8/8/PPPPPPPP/RKNBBNQR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>746</td>
	 *	 		<td>RKNNBBQR</td>
	 *	 		<td>rknnbbqr/pppppppp/8/8/8/8/PPPPPPPP/RKNNBBQR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>747</td>
	 *	 		<td>RKNNBQRB</td>
	 *	 		<td>rknnbqrb/pppppppp/8/8/8/8/PPPPPPPP/RKNNBQRB w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>748</td>
	 *	 		<td>RBKNNQBR</td>
	 *	 		<td>rbknnqbr/pppppppp/8/8/8/8/PPPPPPPP/RBKNNQBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>749</td>
	 *	 		<td>RKNBNQBR</td>
	 *	 		<td>rknbnqbr/pppppppp/8/8/8/8/PPPPPPPP/RKNBNQBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>750</td>
	 *	 		<td>RKNNQBBR</td>
	 *	 		<td>rknnqbbr/pppppppp/8/8/8/8/PPPPPPPP/RKNNQBBR w HAha - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>751</td>
	 *	 		<td>RKNNQRBB</td>
	 *	 		<td>rknnqrbb/pppppppp/8/8/8/8/PPPPPPPP/RKNNQRBB w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>752</td>
	 *	 		<td>BBRKNNRQ</td>
	 *	 		<td>bbrknnrq/pppppppp/8/8/8/8/PPPPPPPP/BBRKNNRQ w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>753</td>
	 *	 		<td>BRKBNNRQ</td>
	 *	 		<td>brkbnnrq/pppppppp/8/8/8/8/PPPPPPPP/BRKBNNRQ w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>754</td>
	 *	 		<td>BRKNNBRQ</td>
	 *	 		<td>brknnbrq/pppppppp/8/8/8/8/PPPPPPPP/BRKNNBRQ w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>755</td>
	 *	 		<td>BRKNNRQB</td>
	 *	 		<td>brknnrqb/pppppppp/8/8/8/8/PPPPPPPP/BRKNNRQB w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>756</td>
	 *	 		<td>RBBKNNRQ</td>
	 *	 		<td>rbbknnrq/pppppppp/8/8/8/8/PPPPPPPP/RBBKNNRQ w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>757</td>
	 *	 		<td>RKBBNNRQ</td>
	 *	 		<td>rkbbnnrq/pppppppp/8/8/8/8/PPPPPPPP/RKBBNNRQ w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>758</td>
	 *	 		<td>RKBNNBRQ</td>
	 *	 		<td>rkbnnbrq/pppppppp/8/8/8/8/PPPPPPPP/RKBNNBRQ w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>759</td>
	 *	 		<td>RKBNNRQB</td>
	 *	 		<td>rkbnnrqb/pppppppp/8/8/8/8/PPPPPPPP/RKBNNRQB w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>760</td>
	 *	 		<td>RBKNBNRQ</td>
	 *	 		<td>rbknbnrq/pppppppp/8/8/8/8/PPPPPPPP/RBKNBNRQ w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>761</td>
	 *	 		<td>RKNBBNRQ</td>
	 *	 		<td>rknbbnrq/pppppppp/8/8/8/8/PPPPPPPP/RKNBBNRQ w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>762</td>
	 *	 		<td>RKNNBBRQ</td>
	 *	 		<td>rknnbbrq/pppppppp/8/8/8/8/PPPPPPPP/RKNNBBRQ w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>763</td>
	 *	 		<td>RKNNBRQB</td>
	 *	 		<td>rknnbrqb/pppppppp/8/8/8/8/PPPPPPPP/RKNNBRQB w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>764</td>
	 *	 		<td>RBKNNRBQ</td>
	 *	 		<td>rbknnrbq/pppppppp/8/8/8/8/PPPPPPPP/RBKNNRBQ w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>765</td>
	 *	 		<td>RKNBNRBQ</td>
	 *	 		<td>rknbnrbq/pppppppp/8/8/8/8/PPPPPPPP/RKNBNRBQ w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>766</td>
	 *	 		<td>RKNNRBBQ</td>
	 *	 		<td>rknnrbbq/pppppppp/8/8/8/8/PPPPPPPP/RKNNRBBQ w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>767</td>
	 *	 		<td>RKNNRQBB</td>
	 *	 		<td>rknnrqbb/pppppppp/8/8/8/8/PPPPPPPP/RKNNRQBB w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>768</td>
	 *	 		<td>BBQRKNRN</td>
	 *	 		<td>bbqrknrn/pppppppp/8/8/8/8/PPPPPPPP/BBQRKNRN w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>769</td>
	 *	 		<td>BQRBKNRN</td>
	 *	 		<td>bqrbknrn/pppppppp/8/8/8/8/PPPPPPPP/BQRBKNRN w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>770</td>
	 *	 		<td>BQRKNBRN</td>
	 *	 		<td>bqrknbrn/pppppppp/8/8/8/8/PPPPPPPP/BQRKNBRN w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>771</td>
	 *	 		<td>BQRKNRNB</td>
	 *	 		<td>bqrknrnb/pppppppp/8/8/8/8/PPPPPPPP/BQRKNRNB w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>772</td>
	 *	 		<td>QBBRKNRN</td>
	 *	 		<td>qbbrknrn/pppppppp/8/8/8/8/PPPPPPPP/QBBRKNRN w GDgd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>773</td>
	 *	 		<td>QRBBKNRN</td>
	 *	 		<td>qrbbknrn/pppppppp/8/8/8/8/PPPPPPPP/QRBBKNRN w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>774</td>
	 *	 		<td>QRBKNBRN</td>
	 *	 		<td>qrbknbrn/pppppppp/8/8/8/8/PPPPPPPP/QRBKNBRN w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>775</td>
	 *	 		<td>QRBKNRNB</td>
	 *	 		<td>qrbknrnb/pppppppp/8/8/8/8/PPPPPPPP/QRBKNRNB w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>776</td>
	 *	 		<td>QBRKBNRN</td>
	 *	 		<td>qbrkbnrn/pppppppp/8/8/8/8/PPPPPPPP/QBRKBNRN w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>777</td>
	 *	 		<td>QRKBBNRN</td>
	 *	 		<td>qrkbbnrn/pppppppp/8/8/8/8/PPPPPPPP/QRKBBNRN w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>778</td>
	 *	 		<td>QRKNBBRN</td>
	 *	 		<td>qrknbbrn/pppppppp/8/8/8/8/PPPPPPPP/QRKNBBRN w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>779</td>
	 *	 		<td>QRKNBRNB</td>
	 *	 		<td>qrknbrnb/pppppppp/8/8/8/8/PPPPPPPP/QRKNBRNB w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>780</td>
	 *	 		<td>QBRKNRBN</td>
	 *	 		<td>qbrknrbn/pppppppp/8/8/8/8/PPPPPPPP/QBRKNRBN w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>781</td>
	 *	 		<td>QRKBNRBN</td>
	 *	 		<td>qrkbnrbn/pppppppp/8/8/8/8/PPPPPPPP/QRKBNRBN w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>782</td>
	 *	 		<td>QRKNRBBN</td>
	 *	 		<td>qrknrbbn/pppppppp/8/8/8/8/PPPPPPPP/QRKNRBBN w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>783</td>
	 *	 		<td>QRKNRNBB</td>
	 *	 		<td>qrknrnbb/pppppppp/8/8/8/8/PPPPPPPP/QRKNRNBB w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>784</td>
	 *	 		<td>BBRQKNRN</td>
	 *	 		<td>bbrqknrn/pppppppp/8/8/8/8/PPPPPPPP/BBRQKNRN w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>785</td>
	 *	 		<td>BRQBKNRN</td>
	 *	 		<td>brqbknrn/pppppppp/8/8/8/8/PPPPPPPP/BRQBKNRN w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>786</td>
	 *	 		<td>BRQKNBRN</td>
	 *	 		<td>brqknbrn/pppppppp/8/8/8/8/PPPPPPPP/BRQKNBRN w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>787</td>
	 *	 		<td>BRQKNRNB</td>
	 *	 		<td>brqknrnb/pppppppp/8/8/8/8/PPPPPPPP/BRQKNRNB w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>788</td>
	 *	 		<td>RBBQKNRN</td>
	 *	 		<td>rbbqknrn/pppppppp/8/8/8/8/PPPPPPPP/RBBQKNRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>789</td>
	 *	 		<td>RQBBKNRN</td>
	 *	 		<td>rqbbknrn/pppppppp/8/8/8/8/PPPPPPPP/RQBBKNRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>790</td>
	 *	 		<td>RQBKNBRN</td>
	 *	 		<td>rqbknbrn/pppppppp/8/8/8/8/PPPPPPPP/RQBKNBRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>791</td>
	 *	 		<td>RQBKNRNB</td>
	 *	 		<td>rqbknrnb/pppppppp/8/8/8/8/PPPPPPPP/RQBKNRNB w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>792</td>
	 *	 		<td>RBQKBNRN</td>
	 *	 		<td>rbqkbnrn/pppppppp/8/8/8/8/PPPPPPPP/RBQKBNRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>793</td>
	 *	 		<td>RQKBBNRN</td>
	 *	 		<td>rqkbbnrn/pppppppp/8/8/8/8/PPPPPPPP/RQKBBNRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>794</td>
	 *	 		<td>RQKNBBRN</td>
	 *	 		<td>rqknbbrn/pppppppp/8/8/8/8/PPPPPPPP/RQKNBBRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>795</td>
	 *	 		<td>RQKNBRNB</td>
	 *	 		<td>rqknbrnb/pppppppp/8/8/8/8/PPPPPPPP/RQKNBRNB w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>796</td>
	 *	 		<td>RBQKNRBN</td>
	 *	 		<td>rbqknrbn/pppppppp/8/8/8/8/PPPPPPPP/RBQKNRBN w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>797</td>
	 *	 		<td>RQKBNRBN</td>
	 *	 		<td>rqkbnrbn/pppppppp/8/8/8/8/PPPPPPPP/RQKBNRBN w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>798</td>
	 *	 		<td>RQKNRBBN</td>
	 *	 		<td>rqknrbbn/pppppppp/8/8/8/8/PPPPPPPP/RQKNRBBN w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>799</td>
	 *	 		<td>RQKNRNBB</td>
	 *	 		<td>rqknrnbb/pppppppp/8/8/8/8/PPPPPPPP/RQKNRNBB w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>800</td>
	 *	 		<td>BBRKQNRN</td>
	 *	 		<td>bbrkqnrn/pppppppp/8/8/8/8/PPPPPPPP/BBRKQNRN w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>801</td>
	 *	 		<td>BRKBQNRN</td>
	 *	 		<td>brkbqnrn/pppppppp/8/8/8/8/PPPPPPPP/BRKBQNRN w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>802</td>
	 *	 		<td>BRKQNBRN</td>
	 *	 		<td>brkqnbrn/pppppppp/8/8/8/8/PPPPPPPP/BRKQNBRN w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>803</td>
	 *	 		<td>BRKQNRNB</td>
	 *	 		<td>brkqnrnb/pppppppp/8/8/8/8/PPPPPPPP/BRKQNRNB w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>804</td>
	 *	 		<td>RBBKQNRN</td>
	 *	 		<td>rbbkqnrn/pppppppp/8/8/8/8/PPPPPPPP/RBBKQNRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>805</td>
	 *	 		<td>RKBBQNRN</td>
	 *	 		<td>rkbbqnrn/pppppppp/8/8/8/8/PPPPPPPP/RKBBQNRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>806</td>
	 *	 		<td>RKBQNBRN</td>
	 *	 		<td>rkbqnbrn/pppppppp/8/8/8/8/PPPPPPPP/RKBQNBRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>807</td>
	 *	 		<td>RKBQNRNB</td>
	 *	 		<td>rkbqnrnb/pppppppp/8/8/8/8/PPPPPPPP/RKBQNRNB w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>808</td>
	 *	 		<td>RBKQBNRN</td>
	 *	 		<td>rbkqbnrn/pppppppp/8/8/8/8/PPPPPPPP/RBKQBNRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>809</td>
	 *	 		<td>RKQBBNRN</td>
	 *	 		<td>rkqbbnrn/pppppppp/8/8/8/8/PPPPPPPP/RKQBBNRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>810</td>
	 *	 		<td>RKQNBBRN</td>
	 *	 		<td>rkqnbbrn/pppppppp/8/8/8/8/PPPPPPPP/RKQNBBRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>811</td>
	 *	 		<td>RKQNBRNB</td>
	 *	 		<td>rkqnbrnb/pppppppp/8/8/8/8/PPPPPPPP/RKQNBRNB w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>812</td>
	 *	 		<td>RBKQNRBN</td>
	 *	 		<td>rbkqnrbn/pppppppp/8/8/8/8/PPPPPPPP/RBKQNRBN w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>813</td>
	 *	 		<td>RKQBNRBN</td>
	 *	 		<td>rkqbnrbn/pppppppp/8/8/8/8/PPPPPPPP/RKQBNRBN w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>814</td>
	 *	 		<td>RKQNRBBN</td>
	 *	 		<td>rkqnrbbn/pppppppp/8/8/8/8/PPPPPPPP/RKQNRBBN w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>815</td>
	 *	 		<td>RKQNRNBB</td>
	 *	 		<td>rkqnrnbb/pppppppp/8/8/8/8/PPPPPPPP/RKQNRNBB w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>816</td>
	 *	 		<td>BBRKNQRN</td>
	 *	 		<td>bbrknqrn/pppppppp/8/8/8/8/PPPPPPPP/BBRKNQRN w GCgc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>817</td>
	 *	 		<td>BRKBNQRN</td>
	 *	 		<td>brkbnqrn/pppppppp/8/8/8/8/PPPPPPPP/BRKBNQRN w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>818</td>
	 *	 		<td>BRKNQBRN</td>
	 *	 		<td>brknqbrn/pppppppp/8/8/8/8/PPPPPPPP/BRKNQBRN w GBgb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>819</td>
	 *	 		<td>BRKNQRNB</td>
	 *	 		<td>brknqrnb/pppppppp/8/8/8/8/PPPPPPPP/BRKNQRNB w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>820</td>
	 *	 		<td>RBBKNQRN</td>
	 *	 		<td>rbbknqrn/pppppppp/8/8/8/8/PPPPPPPP/RBBKNQRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>821</td>
	 *	 		<td>RKBBNQRN</td>
	 *	 		<td>rkbbnqrn/pppppppp/8/8/8/8/PPPPPPPP/RKBBNQRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>822</td>
	 *	 		<td>RKBNQBRN</td>
	 *	 		<td>rkbnqbrn/pppppppp/8/8/8/8/PPPPPPPP/RKBNQBRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>823</td>
	 *	 		<td>RKBNQRNB</td>
	 *	 		<td>rkbnqrnb/pppppppp/8/8/8/8/PPPPPPPP/RKBNQRNB w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>824</td>
	 *	 		<td>RBKNBQRN</td>
	 *	 		<td>rbknbqrn/pppppppp/8/8/8/8/PPPPPPPP/RBKNBQRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>825</td>
	 *	 		<td>RKNBBQRN</td>
	 *	 		<td>rknbbqrn/pppppppp/8/8/8/8/PPPPPPPP/RKNBBQRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>826</td>
	 *	 		<td>RKNQBBRN</td>
	 *	 		<td>rknqbbrn/pppppppp/8/8/8/8/PPPPPPPP/RKNQBBRN w GAga - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>827</td>
	 *	 		<td>RKNQBRNB</td>
	 *	 		<td>rknqbrnb/pppppppp/8/8/8/8/PPPPPPPP/RKNQBRNB w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>828</td>
	 *	 		<td>RBKNQRBN</td>
	 *	 		<td>rbknqrbn/pppppppp/8/8/8/8/PPPPPPPP/RBKNQRBN w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>829</td>
	 *	 		<td>RKNBQRBN</td>
	 *	 		<td>rknbqrbn/pppppppp/8/8/8/8/PPPPPPPP/RKNBQRBN w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>830</td>
	 *	 		<td>RKNQRBBN</td>
	 *	 		<td>rknqrbbn/pppppppp/8/8/8/8/PPPPPPPP/RKNQRBBN w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>831</td>
	 *	 		<td>RKNQRNBB</td>
	 *	 		<td>rknqrnbb/pppppppp/8/8/8/8/PPPPPPPP/RKNQRNBB w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>832</td>
	 *	 		<td>BBRKNRQN</td>
	 *	 		<td>bbrknrqn/pppppppp/8/8/8/8/PPPPPPPP/BBRKNRQN w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>833</td>
	 *	 		<td>BRKBNRQN</td>
	 *	 		<td>brkbnrqn/pppppppp/8/8/8/8/PPPPPPPP/BRKBNRQN w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>834</td>
	 *	 		<td>BRKNRBQN</td>
	 *	 		<td>brknrbqn/pppppppp/8/8/8/8/PPPPPPPP/BRKNRBQN w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>835</td>
	 *	 		<td>BRKNRQNB</td>
	 *	 		<td>brknrqnb/pppppppp/8/8/8/8/PPPPPPPP/BRKNRQNB w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>836</td>
	 *	 		<td>RBBKNRQN</td>
	 *	 		<td>rbbknrqn/pppppppp/8/8/8/8/PPPPPPPP/RBBKNRQN w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>837</td>
	 *	 		<td>RKBBNRQN</td>
	 *	 		<td>rkbbnrqn/pppppppp/8/8/8/8/PPPPPPPP/RKBBNRQN w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>838</td>
	 *	 		<td>RKBNRBQN</td>
	 *	 		<td>rkbnrbqn/pppppppp/8/8/8/8/PPPPPPPP/RKBNRBQN w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>839</td>
	 *	 		<td>RKBNRQNB</td>
	 *	 		<td>rkbnrqnb/pppppppp/8/8/8/8/PPPPPPPP/RKBNRQNB w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>840</td>
	 *	 		<td>RBKNBRQN</td>
	 *	 		<td>rbknbrqn/pppppppp/8/8/8/8/PPPPPPPP/RBKNBRQN w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>841</td>
	 *	 		<td>RKNBBRQN</td>
	 *	 		<td>rknbbrqn/pppppppp/8/8/8/8/PPPPPPPP/RKNBBRQN w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>842</td>
	 *	 		<td>RKNRBBQN</td>
	 *	 		<td>rknrbbqn/pppppppp/8/8/8/8/PPPPPPPP/RKNRBBQN w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>843</td>
	 *	 		<td>RKNRBQNB</td>
	 *	 		<td>rknrbqnb/pppppppp/8/8/8/8/PPPPPPPP/RKNRBQNB w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>844</td>
	 *	 		<td>RBKNRQBN</td>
	 *	 		<td>rbknrqbn/pppppppp/8/8/8/8/PPPPPPPP/RBKNRQBN w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>845</td>
	 *	 		<td>RKNBRQBN</td>
	 *	 		<td>rknbrqbn/pppppppp/8/8/8/8/PPPPPPPP/RKNBRQBN w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>846</td>
	 *	 		<td>RKNRQBBN</td>
	 *	 		<td>rknrqbbn/pppppppp/8/8/8/8/PPPPPPPP/RKNRQBBN w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>847</td>
	 *	 		<td>RKNRQNBB</td>
	 *	 		<td>rknrqnbb/pppppppp/8/8/8/8/PPPPPPPP/RKNRQNBB w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>848</td>
	 *	 		<td>BBRKNRNQ</td>
	 *	 		<td>bbrknrnq/pppppppp/8/8/8/8/PPPPPPPP/BBRKNRNQ w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>849</td>
	 *	 		<td>BRKBNRNQ</td>
	 *	 		<td>brkbnrnq/pppppppp/8/8/8/8/PPPPPPPP/BRKBNRNQ w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>850</td>
	 *	 		<td>BRKNRBNQ</td>
	 *	 		<td>brknrbnq/pppppppp/8/8/8/8/PPPPPPPP/BRKNRBNQ w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>851</td>
	 *	 		<td>BRKNRNQB</td>
	 *	 		<td>brknrnqb/pppppppp/8/8/8/8/PPPPPPPP/BRKNRNQB w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>852</td>
	 *	 		<td>RBBKNRNQ</td>
	 *	 		<td>rbbknrnq/pppppppp/8/8/8/8/PPPPPPPP/RBBKNRNQ w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>853</td>
	 *	 		<td>RKBBNRNQ</td>
	 *	 		<td>rkbbnrnq/pppppppp/8/8/8/8/PPPPPPPP/RKBBNRNQ w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>854</td>
	 *	 		<td>RKBNRBNQ</td>
	 *	 		<td>rkbnrbnq/pppppppp/8/8/8/8/PPPPPPPP/RKBNRBNQ w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>855</td>
	 *	 		<td>RKBNRNQB</td>
	 *	 		<td>rkbnrnqb/pppppppp/8/8/8/8/PPPPPPPP/RKBNRNQB w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>856</td>
	 *	 		<td>RBKNBRNQ</td>
	 *	 		<td>rbknbrnq/pppppppp/8/8/8/8/PPPPPPPP/RBKNBRNQ w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>857</td>
	 *	 		<td>RKNBBRNQ</td>
	 *	 		<td>rknbbrnq/pppppppp/8/8/8/8/PPPPPPPP/RKNBBRNQ w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>858</td>
	 *	 		<td>RKNRBBNQ</td>
	 *	 		<td>rknrbbnq/pppppppp/8/8/8/8/PPPPPPPP/RKNRBBNQ w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>859</td>
	 *	 		<td>RKNRBNQB</td>
	 *	 		<td>rknrbnqb/pppppppp/8/8/8/8/PPPPPPPP/RKNRBNQB w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>860</td>
	 *	 		<td>RBKNRNBQ</td>
	 *	 		<td>rbknrnbq/pppppppp/8/8/8/8/PPPPPPPP/RBKNRNBQ w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>861</td>
	 *	 		<td>RKNBRNBQ</td>
	 *	 		<td>rknbrnbq/pppppppp/8/8/8/8/PPPPPPPP/RKNBRNBQ w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>862</td>
	 *	 		<td>RKNRNBBQ</td>
	 *	 		<td>rknrnbbq/pppppppp/8/8/8/8/PPPPPPPP/RKNRNBBQ w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>863</td>
	 *	 		<td>RKNRNQBB</td>
	 *	 		<td>rknrnqbb/pppppppp/8/8/8/8/PPPPPPPP/RKNRNQBB w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>864</td>
	 *	 		<td>BBQRKRNN</td>
	 *	 		<td>bbqrkrnn/pppppppp/8/8/8/8/PPPPPPPP/BBQRKRNN w FDfd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>865</td>
	 *	 		<td>BQRBKRNN</td>
	 *	 		<td>bqrbkrnn/pppppppp/8/8/8/8/PPPPPPPP/BQRBKRNN w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>866</td>
	 *	 		<td>BQRKRBNN</td>
	 *	 		<td>bqrkrbnn/pppppppp/8/8/8/8/PPPPPPPP/BQRKRBNN w ECec - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>867</td>
	 *	 		<td>BQRKRNNB</td>
	 *	 		<td>bqrkrnnb/pppppppp/8/8/8/8/PPPPPPPP/BQRKRNNB w ECec - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>868</td>
	 *	 		<td>QBBRKRNN</td>
	 *	 		<td>qbbrkrnn/pppppppp/8/8/8/8/PPPPPPPP/QBBRKRNN w FDfd - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>869</td>
	 *	 		<td>QRBBKRNN</td>
	 *	 		<td>qrbbkrnn/pppppppp/8/8/8/8/PPPPPPPP/QRBBKRNN w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>870</td>
	 *	 		<td>QRBKRBNN</td>
	 *	 		<td>qrbkrbnn/pppppppp/8/8/8/8/PPPPPPPP/QRBKRBNN w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>871</td>
	 *	 		<td>QRBKRNNB</td>
	 *	 		<td>qrbkrnnb/pppppppp/8/8/8/8/PPPPPPPP/QRBKRNNB w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>872</td>
	 *	 		<td>QBRKBRNN</td>
	 *	 		<td>qbrkbrnn/pppppppp/8/8/8/8/PPPPPPPP/QBRKBRNN w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>873</td>
	 *	 		<td>QRKBBRNN</td>
	 *	 		<td>qrkbbrnn/pppppppp/8/8/8/8/PPPPPPPP/QRKBBRNN w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>874</td>
	 *	 		<td>QRKRBBNN</td>
	 *	 		<td>qrkrbbnn/pppppppp/8/8/8/8/PPPPPPPP/QRKRBBNN w DBdb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>875</td>
	 *	 		<td>QRKRBNNB</td>
	 *	 		<td>qrkrbnnb/pppppppp/8/8/8/8/PPPPPPPP/QRKRBNNB w DBdb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>876</td>
	 *	 		<td>QBRKRNBN</td>
	 *	 		<td>qbrkrnbn/pppppppp/8/8/8/8/PPPPPPPP/QBRKRNBN w ECec - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>877</td>
	 *	 		<td>QRKBRNBN</td>
	 *	 		<td>qrkbrnbn/pppppppp/8/8/8/8/PPPPPPPP/QRKBRNBN w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>878</td>
	 *	 		<td>QRKRNBBN</td>
	 *	 		<td>qrkrnbbn/pppppppp/8/8/8/8/PPPPPPPP/QRKRNBBN w DBdb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>879</td>
	 *	 		<td>QRKRNNBB</td>
	 *	 		<td>qrkrnnbb/pppppppp/8/8/8/8/PPPPPPPP/QRKRNNBB w DBdb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>880</td>
	 *	 		<td>BBRQKRNN</td>
	 *	 		<td>bbrqkrnn/pppppppp/8/8/8/8/PPPPPPPP/BBRQKRNN w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>881</td>
	 *	 		<td>BRQBKRNN</td>
	 *	 		<td>brqbkrnn/pppppppp/8/8/8/8/PPPPPPPP/BRQBKRNN w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>882</td>
	 *	 		<td>BRQKRBNN</td>
	 *	 		<td>brqkrbnn/pppppppp/8/8/8/8/PPPPPPPP/BRQKRBNN w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>883</td>
	 *	 		<td>BRQKRNNB</td>
	 *	 		<td>brqkrnnb/pppppppp/8/8/8/8/PPPPPPPP/BRQKRNNB w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>884</td>
	 *	 		<td>RBBQKRNN</td>
	 *	 		<td>rbbqkrnn/pppppppp/8/8/8/8/PPPPPPPP/RBBQKRNN w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>885</td>
	 *	 		<td>RQBBKRNN</td>
	 *	 		<td>rqbbkrnn/pppppppp/8/8/8/8/PPPPPPPP/RQBBKRNN w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>886</td>
	 *	 		<td>RQBKRBNN</td>
	 *	 		<td>rqbkrbnn/pppppppp/8/8/8/8/PPPPPPPP/RQBKRBNN w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>887</td>
	 *	 		<td>RQBKRNNB</td>
	 *	 		<td>rqbkrnnb/pppppppp/8/8/8/8/PPPPPPPP/RQBKRNNB w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>888</td>
	 *	 		<td>RBQKBRNN</td>
	 *	 		<td>rbqkbrnn/pppppppp/8/8/8/8/PPPPPPPP/RBQKBRNN w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>889</td>
	 *	 		<td>RQKBBRNN</td>
	 *	 		<td>rqkbbrnn/pppppppp/8/8/8/8/PPPPPPPP/RQKBBRNN w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>890</td>
	 *	 		<td>RQKRBBNN</td>
	 *	 		<td>rqkrbbnn/pppppppp/8/8/8/8/PPPPPPPP/RQKRBBNN w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>891</td>
	 *	 		<td>RQKRBNNB</td>
	 *	 		<td>rqkrbnnb/pppppppp/8/8/8/8/PPPPPPPP/RQKRBNNB w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>892</td>
	 *	 		<td>RBQKRNBN</td>
	 *	 		<td>rbqkrnbn/pppppppp/8/8/8/8/PPPPPPPP/RBQKRNBN w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>893</td>
	 *	 		<td>RQKBRNBN</td>
	 *	 		<td>rqkbrnbn/pppppppp/8/8/8/8/PPPPPPPP/RQKBRNBN w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>894</td>
	 *	 		<td>RQKRNBBN</td>
	 *	 		<td>rqkrnbbn/pppppppp/8/8/8/8/PPPPPPPP/RQKRNBBN w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>895</td>
	 *	 		<td>RQKRNNBB</td>
	 *	 		<td>rqkrnnbb/pppppppp/8/8/8/8/PPPPPPPP/RQKRNNBB w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>896</td>
	 *	 		<td>BBRKQRNN</td>
	 *	 		<td>bbrkqrnn/pppppppp/8/8/8/8/PPPPPPPP/BBRKQRNN w FCfc - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>897</td>
	 *	 		<td>BRKBQRNN</td>
	 *	 		<td>brkbqrnn/pppppppp/8/8/8/8/PPPPPPPP/BRKBQRNN w FBfb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>898</td>
	 *	 		<td>BRKQRBNN</td>
	 *	 		<td>brkqrbnn/pppppppp/8/8/8/8/PPPPPPPP/BRKQRBNN w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>899</td>
	 *	 		<td>BRKQRNNB</td>
	 *	 		<td>brkqrnnb/pppppppp/8/8/8/8/PPPPPPPP/BRKQRNNB w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>900</td>
	 *	 		<td>RBBKQRNN</td>
	 *	 		<td>rbbkqrnn/pppppppp/8/8/8/8/PPPPPPPP/RBBKQRNN w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>901</td>
	 *	 		<td>RKBBQRNN</td>
	 *	 		<td>rkbbqrnn/pppppppp/8/8/8/8/PPPPPPPP/RKBBQRNN w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>902</td>
	 *	 		<td>RKBQRBNN</td>
	 *	 		<td>rkbqrbnn/pppppppp/8/8/8/8/PPPPPPPP/RKBQRBNN w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>903</td>
	 *	 		<td>RKBQRNNB</td>
	 *	 		<td>rkbqrnnb/pppppppp/8/8/8/8/PPPPPPPP/RKBQRNNB w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>904</td>
	 *	 		<td>RBKQBRNN</td>
	 *	 		<td>rbkqbrnn/pppppppp/8/8/8/8/PPPPPPPP/RBKQBRNN w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>905</td>
	 *	 		<td>RKQBBRNN</td>
	 *	 		<td>rkqbbrnn/pppppppp/8/8/8/8/PPPPPPPP/RKQBBRNN w FAfa - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>906</td>
	 *	 		<td>RKQRBBNN</td>
	 *	 		<td>rkqrbbnn/pppppppp/8/8/8/8/PPPPPPPP/RKQRBBNN w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>907</td>
	 *	 		<td>RKQRBNNB</td>
	 *	 		<td>rkqrbnnb/pppppppp/8/8/8/8/PPPPPPPP/RKQRBNNB w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>908</td>
	 *	 		<td>RBKQRNBN</td>
	 *	 		<td>rbkqrnbn/pppppppp/8/8/8/8/PPPPPPPP/RBKQRNBN w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>909</td>
	 *	 		<td>RKQBRNBN</td>
	 *	 		<td>rkqbrnbn/pppppppp/8/8/8/8/PPPPPPPP/RKQBRNBN w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>910</td>
	 *	 		<td>RKQRNBBN</td>
	 *	 		<td>rkqrnbbn/pppppppp/8/8/8/8/PPPPPPPP/RKQRNBBN w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>911</td>
	 *	 		<td>RKQRNNBB</td>
	 *	 		<td>rkqrnnbb/pppppppp/8/8/8/8/PPPPPPPP/RKQRNNBB w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>912</td>
	 *	 		<td>BBRKRQNN</td>
	 *	 		<td>bbrkrqnn/pppppppp/8/8/8/8/PPPPPPPP/BBRKRQNN w ECec - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>913</td>
	 *	 		<td>BRKBRQNN</td>
	 *	 		<td>brkbrqnn/pppppppp/8/8/8/8/PPPPPPPP/BRKBRQNN w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>914</td>
	 *	 		<td>BRKRQBNN</td>
	 *	 		<td>brkrqbnn/pppppppp/8/8/8/8/PPPPPPPP/BRKRQBNN w DBdb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>915</td>
	 *	 		<td>BRKRQNNB</td>
	 *	 		<td>brkrqnnb/pppppppp/8/8/8/8/PPPPPPPP/BRKRQNNB w DBdb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>916</td>
	 *	 		<td>RBBKRQNN</td>
	 *	 		<td>rbbkrqnn/pppppppp/8/8/8/8/PPPPPPPP/RBBKRQNN w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>917</td>
	 *	 		<td>RKBBRQNN</td>
	 *	 		<td>rkbbrqnn/pppppppp/8/8/8/8/PPPPPPPP/RKBBRQNN w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>918</td>
	 *	 		<td>RKBRQBNN</td>
	 *	 		<td>rkbrqbnn/pppppppp/8/8/8/8/PPPPPPPP/RKBRQBNN w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>919</td>
	 *	 		<td>RKBRQNNB</td>
	 *	 		<td>rkbrqnnb/pppppppp/8/8/8/8/PPPPPPPP/RKBRQNNB w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>920</td>
	 *	 		<td>RBKRBQNN</td>
	 *	 		<td>rbkrbqnn/pppppppp/8/8/8/8/PPPPPPPP/RBKRBQNN w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>921</td>
	 *	 		<td>RKRBBQNN</td>
	 *	 		<td>rkrbbqnn/pppppppp/8/8/8/8/PPPPPPPP/RKRBBQNN w CAca - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>922</td>
	 *	 		<td>RKRQBBNN</td>
	 *	 		<td>rkrqbbnn/pppppppp/8/8/8/8/PPPPPPPP/RKRQBBNN w CAca - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>923</td>
	 *	 		<td>RKRQBNNB</td>
	 *	 		<td>rkrqbnnb/pppppppp/8/8/8/8/PPPPPPPP/RKRQBNNB w CAca - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>924</td>
	 *	 		<td>RBKRQNBN</td>
	 *	 		<td>rbkrqnbn/pppppppp/8/8/8/8/PPPPPPPP/RBKRQNBN w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>925</td>
	 *	 		<td>RKRBQNBN</td>
	 *	 		<td>rkrbqnbn/pppppppp/8/8/8/8/PPPPPPPP/RKRBQNBN w CAca - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>926</td>
	 *	 		<td>RKRQNBBN</td>
	 *	 		<td>rkrqnbbn/pppppppp/8/8/8/8/PPPPPPPP/RKRQNBBN w CAca - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>927</td>
	 *	 		<td>RKRQNNBB</td>
	 *	 		<td>rkrqnnbb/pppppppp/8/8/8/8/PPPPPPPP/RKRQNNBB w CAca - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>928</td>
	 *	 		<td>BBRKRNQN</td>
	 *	 		<td>bbrkrnqn/pppppppp/8/8/8/8/PPPPPPPP/BBRKRNQN w ECec - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>929</td>
	 *	 		<td>BRKBRNQN</td>
	 *	 		<td>brkbrnqn/pppppppp/8/8/8/8/PPPPPPPP/BRKBRNQN w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>930</td>
	 *	 		<td>BRKRNBQN</td>
	 *	 		<td>brkrnbqn/pppppppp/8/8/8/8/PPPPPPPP/BRKRNBQN w DBdb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>931</td>
	 *	 		<td>BRKRNQNB</td>
	 *	 		<td>brkrnqnb/pppppppp/8/8/8/8/PPPPPPPP/BRKRNQNB w DBdb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>932</td>
	 *	 		<td>RBBKRNQN</td>
	 *	 		<td>rbbkrnqn/pppppppp/8/8/8/8/PPPPPPPP/RBBKRNQN w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>933</td>
	 *	 		<td>RKBBRNQN</td>
	 *	 		<td>rkbbrnqn/pppppppp/8/8/8/8/PPPPPPPP/RKBBRNQN w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>934</td>
	 *	 		<td>RKBRNBQN</td>
	 *	 		<td>rkbrnbqn/pppppppp/8/8/8/8/PPPPPPPP/RKBRNBQN w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>935</td>
	 *	 		<td>RKBRNQNB</td>
	 *	 		<td>rkbrnqnb/pppppppp/8/8/8/8/PPPPPPPP/RKBRNQNB w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>936</td>
	 *	 		<td>RBKRBNQN</td>
	 *	 		<td>rbkrbnqn/pppppppp/8/8/8/8/PPPPPPPP/RBKRBNQN w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>937</td>
	 *	 		<td>RKRBBNQN</td>
	 *	 		<td>rkrbbnqn/pppppppp/8/8/8/8/PPPPPPPP/RKRBBNQN w CAca - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>938</td>
	 *	 		<td>RKRNBBQN</td>
	 *	 		<td>rkrnbbqn/pppppppp/8/8/8/8/PPPPPPPP/RKRNBBQN w CAca - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>939</td>
	 *	 		<td>RKRNBQNB</td>
	 *	 		<td>rkrnbqnb/pppppppp/8/8/8/8/PPPPPPPP/RKRNBQNB w CAca - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>940</td>
	 *	 		<td>RBKRNQBN</td>
	 *	 		<td>rbkrnqbn/pppppppp/8/8/8/8/PPPPPPPP/RBKRNQBN w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>941</td>
	 *	 		<td>RKRBNQBN</td>
	 *	 		<td>rkrbnqbn/pppppppp/8/8/8/8/PPPPPPPP/RKRBNQBN w CAca - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>942</td>
	 *	 		<td>RKRNQBBN</td>
	 *	 		<td>rkrnqbbn/pppppppp/8/8/8/8/PPPPPPPP/RKRNQBBN w CAca - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>943</td>
	 *	 		<td>RKRNQNBB</td>
	 *	 		<td>rkrnqnbb/pppppppp/8/8/8/8/PPPPPPPP/RKRNQNBB w CAca - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>944</td>
	 *	 		<td>BBRKRNNQ</td>
	 *	 		<td>bbrkrnnq/pppppppp/8/8/8/8/PPPPPPPP/BBRKRNNQ w ECec - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>945</td>
	 *	 		<td>BRKBRNNQ</td>
	 *	 		<td>brkbrnnq/pppppppp/8/8/8/8/PPPPPPPP/BRKBRNNQ w EBeb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>946</td>
	 *	 		<td>BRKRNBNQ</td>
	 *	 		<td>brkrnbnq/pppppppp/8/8/8/8/PPPPPPPP/BRKRNBNQ w DBdb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>947</td>
	 *	 		<td>BRKRNNQB</td>
	 *	 		<td>brkrnnqb/pppppppp/8/8/8/8/PPPPPPPP/BRKRNNQB w DBdb - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>948</td>
	 *	 		<td>RBBKRNNQ</td>
	 *	 		<td>rbbkrnnq/pppppppp/8/8/8/8/PPPPPPPP/RBBKRNNQ w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>949</td>
	 *	 		<td>RKBBRNNQ</td>
	 *	 		<td>rkbbrnnq/pppppppp/8/8/8/8/PPPPPPPP/RKBBRNNQ w EAea - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>950</td>
	 *	 		<td>RKBRNBNQ</td>
	 *	 		<td>rkbrnbnq/pppppppp/8/8/8/8/PPPPPPPP/RKBRNBNQ w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>951</td>
	 *	 		<td>RKBRNNQB</td>
	 *	 		<td>rkbrnnqb/pppppppp/8/8/8/8/PPPPPPPP/RKBRNNQB w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>952</td>
	 *	 		<td>RBKRBNNQ</td>
	 *	 		<td>rbkrbnnq/pppppppp/8/8/8/8/PPPPPPPP/RBKRBNNQ w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>953</td>
	 *	 		<td>RKRBBNNQ</td>
	 *	 		<td>rkrbbnnq/pppppppp/8/8/8/8/PPPPPPPP/RKRBBNNQ w CAca - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>954</td>
	 *	 		<td>RKRNBBNQ</td>
	 *	 		<td>rkrnbbnq/pppppppp/8/8/8/8/PPPPPPPP/RKRNBBNQ w CAca - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>955</td>
	 *	 		<td>RKRNBNQB</td>
	 *	 		<td>rkrnbnqb/pppppppp/8/8/8/8/PPPPPPPP/RKRNBNQB w CAca - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>956</td>
	 *	 		<td>RBKRNNBQ</td>
	 *	 		<td>rbkrnnbq/pppppppp/8/8/8/8/PPPPPPPP/RBKRNNBQ w DAda - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>957</td>
	 *	 		<td>RKRBNNBQ</td>
	 *	 		<td>rkrbnnbq/pppppppp/8/8/8/8/PPPPPPPP/RKRBNNBQ w CAca - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>958</td>
	 *	 		<td>RKRNNBBQ</td>
	 *	 		<td>rkrnnbbq/pppppppp/8/8/8/8/PPPPPPPP/RKRNNBBQ w CAca - 0 1</td>
	 *	 	</tr>
	 *	 	<tr>
	 *	 		<td>959</td>
	 *	 		<td>RKRNNQBB</td>
	 *	 		<td>rkrnnqbb/pppppppp/8/8/8/8/PPPPPPPP/RKRNNQBB w CAca - 0 1</td>
	 *	 	</tr>
	 *	 </table>
	 * </pre>
	 * 
	 * @param index - between 0 and 959 (inclusive)
	 * @return A Chess960 {@code Position} by its index
	 */
	public static Position getChess960ByIndex(int index) {
		return CHESS_960_POSITIONS[index].copyOf();
	}

	/**
	 * Used for retrieving all Chess960 {@code Position}s.
	 * 
	 * @return An array of all Chess960 {@code Position}s
	 */
	public static Position[] getAllChess960Positions() {
		Position[] positions = new Position[CHESS_960_POSITIONS.length];
		for (int i = 0; i < CHESS_960_POSITIONS.length; i++) {
			positions[i] = CHESS_960_POSITIONS[i].copyOf();
		}
		return positions;
	}

	/**
	 * Used for retrieving the standard starting {@code Position} of a chess game.
	 * 
	 * @return The standard starting {@code Position} of a chess game
	 */
	public static Position getStandardStartPosition() {
		return STANDARD_START_POSITION.copyOf();
	}

	/**
	 * Used for retrieving the standard starting FEN string of a chess game.
	 * 
	 * @return The standard starting FEN string of a chess game
	 */
	public static String getStandardStartFEN() {
		return STANDARD_FEN;
	}

	/**
	 * Used for generating {@code count} randomized, legal positions. Start from
	 * either the standard
	 * initial setup or a random Chess960 start when {@code chess960} is true,
	 * advance a random
	 * warmup, then sample positions while sometimes branching from earlier states
	 * and occasionally
	 * restarting to increase variety.
	 *
	 * @param count    the number of positions to generate.
	 * @param chess960 whether to use a Chess960 initial position instead of the
	 *                 standard start.
	 * @return a list of randomized, legal positions.
	 */
	public static List<Position> getRandomPositions(int count, boolean chess960) {
		if (count <= 0) {
			return Collections.emptyList();
		}

		final ThreadLocalRandom rnd = ThreadLocalRandom.current();

		final int warmupMinPlies = 5;
		final int warmupMaxPlies = 200;
		final int stepMinPlies = 1;
		final int stepMaxPlies = 8;
		final double branchProb = 0.35;
		final double restartProb = 0.05;

		Supplier<Position> freshStart = freshStartSupplier(chess960);

		Position seed = freshStart.get();
		playRandomPlies(seed, randomRange(rnd, warmupMinPlies, warmupMaxPlies));

		List<Position> positions = new ArrayList<>(count);
		List<Position> pool = new ArrayList<>(Math.max(32, count));
		pool.add(seed.copyOf());

		while (positions.size() < count) {
			Position base = chooseBase(pool, branchProb, rnd);
			playRandomPlies(base, randomRange(rnd, stepMinPlies, stepMaxPlies));

			if (needsRestart(base, restartProb, rnd)) {
				addSnapshot(positions, pool, base);
				Position restart = freshStart.get();
				int kick = randomRange(rnd, warmupMinPlies / 2, warmupMaxPlies / 2);
				playRandomPlies(restart, kick);
				pool.add(restart.copyOf());
				continue;
			}

			addSnapshot(positions, pool, base);
		}

		return positions;
	}

	/**
	 * Used for providing a supplier of fresh start positions, either Chess960 or
	 * the standard start.
	 *
	 * @param chess960 whether to use a Chess960 start; otherwise use the standard
	 *                 initial position.
	 * @return a supplier that yields fresh starting positions on demand.
	 */
	private static Supplier<Position> freshStartSupplier(boolean chess960) {
		return chess960 ? Setup::getRandomChess960 : Setup::getStandardStartPosition;
	}

	/**
	 * Used for applying up to {@code plies} random legal moves to {@code position},
	 * stopping early
	 * when no moves are available or an error occurs.
	 *
	 * @param position the position to mutate by playing random moves.
	 * @param plies    the maximum number of plies to play.
	 */
	private static void playRandomPlies(Position position, int plies) {
		for (int i = 0; i < plies; i++) {
			boolean stop = false;
			MoveList moves = position.getMoves();
			stop = moves == null || sizeIsZeroSafe(moves);
			if (!stop) {
				short move = moves.getRandomMove();
				stop = move == Move.NO_MOVE;
				if (!stop) {
					try {
						position.play(move);
					} catch (Exception e) {
						stop = true;
					}
				}
			}
			if (stop) {
				break;
			}
		}
	}

	/**
	 * Used for checking whether the provided move list reports zero size without
	 * throwing.
	 *
	 * @param moves the move list to inspect.
	 * @return {@code true} if {@code moves.size() == 0}; {@code false} if a call
	 *         fails or returns non-zero.
	 */
	private static boolean sizeIsZeroSafe(MoveList moves) {
		try {
			return moves.size() == 0;
		} catch (Exception ignore) {
			return false;
		}
	}

	/**
	 * Used for selecting a base position from the pool, branching with probability
	 * {@code branchProb}.
	 *
	 * @param pool       the pool of previously seen positions.
	 * @param branchProb the probability of branching to a random prior position.
	 * @param rnd        the random source.
	 * @return a copy of the chosen base position.
	 */
	private static Position chooseBase(List<Position> pool, double branchProb, ThreadLocalRandom rnd) {
		if (pool.size() > 1 && rnd.nextDouble() < branchProb) {
			return pool.get(rnd.nextInt(pool.size())).copyOf();
		}
		return pool.get(pool.size() - 1).copyOf();
	}

	/**
	 * Used for deciding whether to restart from a fresh start based on terminality
	 * or random chance.
	 *
	 * @param p           the position to test for terminality.
	 * @param restartProb the probability to restart even if not terminal.
	 * @param rnd         the random source.
	 * @return {@code true} if a restart is advised; otherwise {@code false}.
	 */
	private static boolean needsRestart(Position p, double restartProb, ThreadLocalRandom rnd) {
		return isTerminal(p) || rnd.nextDouble() < restartProb;
	}

	/**
	 * Used for checking whether the given position is terminal.
	 *
	 * @param position the position to examine.
	 * @return {@code true} if no legal moves or an error occurs; otherwise
	 *         {@code false}.
	 */
	private static boolean isTerminal(Position position) {
		try {
			MoveList moves = position.getMoves();
			return moves == null || sizeIsZeroSafe(moves);
		} catch (Exception ignore) {
			return true;
		}
	}

	/**
	 * Used for sampling an integer uniformly from the inclusive range
	 * {@code [inclusiveMin, inclusiveMax]}.
	 *
	 * @param rnd          the random source.
	 * @param inclusiveMin the inclusive lower bound.
	 * @param inclusiveMax the inclusive upper bound.
	 * @return a random integer within the inclusive range.
	 */
	private static int randomRange(ThreadLocalRandom rnd, int inclusiveMin, int inclusiveMax) {
		return rnd.nextInt(inclusiveMin, inclusiveMax + 1);
	}

	/**
	 * Used for copying {@code src} and appending it to both the output list and the
	 * reusable pool.
	 *
	 * @param out  the list collecting result positions.
	 * @param pool the pool of prior positions to branch from.
	 * @param src  the source position to copy.
	 */
	private static void addSnapshot(List<Position> out, List<Position> pool, Position src) {
		Position copy = src.copyOf();
		out.add(copy);
		pool.add(copy);
	}

}
