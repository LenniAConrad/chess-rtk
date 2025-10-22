package chess.eco;

import chess.core.Position;
import chess.core.SAN;
import chess.core.Setup;

/**
 * Used for representing an ECO node containing code, name, movetext,
 * parsed moves, and the resulting position.
 * 
 * @since 2025
 * @author Lennart A. Conrad
 */
public class Entry {

   /**
    * Used for storing the ECO code.
    */
   protected String eco;

   /**
    * Used for storing the opening name.
    */
   protected String name;

   /**
    * Used for storing the movetext in SAN format.
    */
   protected String movetext;

   /**
    * Used for storing the position after applying all moves.
    */
   protected Position position = null;

   /**
    * Used for storing the sequence of parsed moves.
    */
   protected short[] moves = null;

   /**
    * Used for creating a Node with ECO code, name, and movetext.
    *
    * @param eco      the ECO code
    * @param name     the opening name
    * @param movetext the movetext string
    * @throws IllegalArgumentException if movetext contains invalid moves
    */
   protected Entry(String eco, String name, String movetext) throws IllegalArgumentException {
      this.eco = eco;
      this.name = name;
      this.movetext = movetext;

      position = Setup.getStandardStartPosition();
      String[] san = SAN.cleanMoveString(movetext).split(" ");
      moves = new short[san.length];
      for (int i = 0; i < san.length; i++) {
         short move = SAN.fromAlgebraic(position, san[i]);
         moves[i] = move;
         position = position.play(move);
      }
   }

   /**
    * Used for retrieving the ECO code.
    *
    * @return the ECO code
    */
   public String getECO() {
      return eco;
   }

   /**
    * Used for retrieving the opening name.
    *
    * @return the name of the opening
    */
   public String getName() {
      return name;
   }

   /**
    * Used for retrieving the movetext.
    *
    * @return the movetext string
    */
   public String getMovetext() {
      return movetext;
   }

   /**
    * Serialises this {@code Entry} as a TOML fragment that can be appended to an
    * ECO (Encyclopaedia of Chess Openings) encyclopedia file.
    * <p>
    *
    * The fragment has the form:
    * 
    * <pre>
    * [[ECO_NUMBER]]
    * name     = "<opening name>"
    * movetext = "<SAN sequence>"
    * </pre>
    *
    * @return the TOML representation of this entry, terminated with a newline
    */
   @Override
   public String toString() {
      // Use String.format for readability and %n for platform-independent EOLs.
      return String.format(
            "[[%s]]%nname     = \"%s\"%nmovetext = \"%s\"%n",
            eco,
            name.replace("\"", "\\\""), // escape embedded quotes
            movetext.replace("\"", "\\\""));
   }

}
