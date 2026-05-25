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
 * Severity group used by the PDF driver panel.
 */
record SwitchGroup(
        String name,
        String shortName,
        int count,
        double medianSwing,
        double avgFirstToFinalLcp,
        double bestChangedShare,
        double outcomeChangedShare) {
}
