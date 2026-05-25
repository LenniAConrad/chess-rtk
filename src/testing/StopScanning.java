package testing;

import static testing.PuzzleVolatilityReport.*;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import application.Config;
import chess.core.Move;
import chess.core.Position;
import chess.struct.Record;
import chess.uci.Analysis;
import utility.Json;

/**
 * Internal control-flow signal used to stop scanning after a sample cap.
 */
final class StopScanning extends RuntimeException {

    /**
     * Avoids warning noise for this local control-flow exception.
     */
    private static final long serialVersionUID = 1L;
}
