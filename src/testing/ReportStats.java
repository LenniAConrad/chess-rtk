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
 * PDF report-wide statistics.
 */
record ReportStats(
        int rootStacks,
        int changedStacks,
        int changedNodes,
        int reversalStacks,
        int minSwing,
        int p10Swing,
        int medianSwing,
        int p90Swing,
        int p99Swing,
        int maxSwing,
        String scoreSources,
        double bestChangedShare,
        double outcomeChangedShare,
        double medianPvPrefix,
        int minFinalDepth,
        int medianFinalDepth,
        int maxFinalDepth) {
}
