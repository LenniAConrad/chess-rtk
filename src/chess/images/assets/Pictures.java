package chess.images.assets;

import java.awt.image.BufferedImage;

import chess.images.assets.board.ByteBoard;
import chess.images.assets.icon.ByteBishop;
import chess.images.assets.icon.ByteKing;
import chess.images.assets.icon.ByteKnight;
import chess.images.assets.icon.BytePawn;
import chess.images.assets.icon.ByteQueen;
import chess.images.assets.icon.ByteRook;
import chess.images.assets.piece.ByteBlackBishop;
import chess.images.assets.piece.ByteBlackKing;
import chess.images.assets.piece.ByteBlackKnight;
import chess.images.assets.piece.ByteBlackPawn;
import chess.images.assets.piece.ByteBlackQueen;
import chess.images.assets.piece.ByteBlackRook;
import chess.images.assets.piece.ByteWhiteBishop;
import chess.images.assets.piece.ByteWhiteKing;
import chess.images.assets.piece.ByteWhiteKnight;
import chess.images.assets.piece.ByteWhitePawn;
import chess.images.assets.piece.ByteWhiteQueen;
import chess.images.assets.piece.ByteWhiteRook;
import utility.Images;

/**
 * Container class used for loading {@code BufferedImages} from {@code byte}
 * arrays for rendering.
 * 
 * <p>
 * This class contains the {@code BufferedImages} of the following:
 * </p>
 * 
 * <blockquote> Black & White:
 * <ul>
 * <li>bishop</li>
 * <li>king</li>
 * <li>knight</li>
 * <li>pawn</li>
 * <li>queen</li>
 * <li>rook</li>
 * </ul>
 * 
 * Icons depicting:
 * <ul>
 * <li>Best move</li>
 * <li>Excellent move</li>
 * <li>Great move</li>
 * <li>Good move</li>
 * <li>Practical move</li>
 * <li>Inaccurate move</li>
 * <li>Mistake</li>
 * <li>Blunder</li>
 * <li>bishop</li>
 * <li>king</li>
 * <li>knight</li>
 * <li>pawn</li>
 * <li>queen</li>
 * <li>rook</li>
 * </ul>
 * 
 * Other:
 * <ul>
 * <li>Chess board</li>
 * <li>'La Paloma Gordita' icon</li>
 * </ul>
 * </blockquote>
 * 
 * @since 2024
 * @author Lennart A. Conrad
 */
public class Pictures {

	/**
	 * Prevents instantiation of this utility class.
	 */
	private Pictures() {
		// Do nothing
	}

	/**
	 * A {@code BufferedImage} representing the image of a chess board.
	 */
	public static final BufferedImage Board = Images.bufferedImageFromByteArray(ByteBoard.bytes());

	/**
	 * A {@code BufferedImage} representing the image of a black bishop.
	 */
	public static final BufferedImage BlackBishop = Images.bufferedImageFromByteArray(ByteBlackBishop.bytes());

	/**
	 * A {@code BufferedImage} representing the image of a black king.
	 */
	public static final BufferedImage BlackKing = Images.bufferedImageFromByteArray(ByteBlackKing.bytes());

	/**
	 * A {@code BufferedImage} representing the image of a black knight.
	 */
	public static final BufferedImage BlackKnight = Images.bufferedImageFromByteArray(ByteBlackKnight.bytes());

	/**
	 * A {@code BufferedImage} representing the image of a black pawn.
	 */
	public static final BufferedImage BlackPawn = Images.bufferedImageFromByteArray(ByteBlackPawn.bytes());

	/**
	 * A {@code BufferedImage} representing the image of a black queen.
	 */
	public static final BufferedImage BlackQueen = Images.bufferedImageFromByteArray(ByteBlackQueen.bytes());

	/**
	 * A {@code BufferedImage} representing the image of a black rook.
	 */
	public static final BufferedImage BlackRook = Images.bufferedImageFromByteArray(ByteBlackRook.bytes());

	/**
	 * A {@code BufferedImage} representing the image of a white bishop.
	 */
	public static final BufferedImage WhiteBishop = Images.bufferedImageFromByteArray(ByteWhiteBishop.bytes());

	/**
	 * A {@code BufferedImage} representing the image of a white king.
	 */
	public static final BufferedImage WhiteKing = Images.bufferedImageFromByteArray(ByteWhiteKing.bytes());

	/**
	 * A {@code BufferedImage} representing the image of a white knight.
	 */
	public static final BufferedImage WhiteKnight = Images.bufferedImageFromByteArray(ByteWhiteKnight.bytes());

	/**
	 * A {@code BufferedImage} representing the image of a white pawn.
	 */
	public static final BufferedImage WhitePawn = Images.bufferedImageFromByteArray(ByteWhitePawn.bytes());

	/**
	 * A {@code BufferedImage} representing the image of a white queen.
	 */
	public static final BufferedImage WhiteQueen = Images.bufferedImageFromByteArray(ByteWhiteQueen.bytes());

	/**
	 * A {@code BufferedImage} representing the image of a white rook.
	 */
	public static final BufferedImage WhiteRook = Images.bufferedImageFromByteArray(ByteWhiteRook.bytes());

	/**
	 * A {@code BufferedImage} representing the image of a bishop-icon.
	 */
	public static final BufferedImage IconBishop = Images.bufferedImageFromByteArray(ByteBishop.bytes());

	/**
	 * A {@code BufferedImage} representing the image of a king-icon.
	 */
	public static final BufferedImage IconKing = Images.bufferedImageFromByteArray(ByteKing.bytes());

	/**
	 * A {@code BufferedImage} representing the image of a knight-icon.
	 */
	public static final BufferedImage IconKnight = Images.bufferedImageFromByteArray(ByteKnight.bytes());

	/**
	 * A {@code BufferedImage} representing the image of a pawn-icon.
	 */
	public static final BufferedImage IconPawn = Images.bufferedImageFromByteArray(BytePawn.bytes());

	/**
	 * A {@code BufferedImage} representing the image of a queen-icon.
	 */
	public static final BufferedImage IconQueen = Images.bufferedImageFromByteArray(ByteQueen.bytes());

	/**
	 * A {@code BufferedImage} representing the image of a rook-icon.
	 */
	public static final BufferedImage IconRook = Images.bufferedImageFromByteArray(ByteRook.bytes());

}
