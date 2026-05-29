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
 * Coarse outcome buckets.
 */
enum Outcome {
    /**
     * The side to move is clearly better.
     */
    WINNING("winning"),

    /**
     * The side to move is clearly worse.
     */
    LOSING("losing"),

    /**
     * The score is close to equality.
     */
    DRAWISH("drawish"),

    /**
     * The score is neither equal nor clearly decisive.
     */
    UNCLEAR("unclear");

    /**
     * CSV-safe label.
     */
    private final String label;

    /**
     * Outcome.
     * @param label label text
     */
    Outcome(String label) {
        this.label = label;
    }

    /**
     * Returns the CSV-safe label.
     * @return label result
     */
    String label() {
        return label;
    }
}
