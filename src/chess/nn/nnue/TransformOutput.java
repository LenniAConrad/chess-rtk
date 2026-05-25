package chess.nn.nnue;

import static chess.nn.nnue.UpstreamNetwork.*;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import chess.core.Position;
import chess.nn.nnue.UpstreamNetwork.Size;
import chess.nn.nnue.UpstreamNetwork.Variant;
import utility.Numbers;

/**
 * Feature-transform output.
 */
final class TransformOutput {

    /**
     * Stores transformed features.
     */
    final int[] features;

    /**
     * Stores PSQT output.
     */
    final int psqt;

    /**
     * Creates one feature-transform output snapshot.
     *
     * @param features transformed features
     * @param psqt PSQT output
     */
    TransformOutput(int[] features, int psqt) {
        this.features = features == null ? new int[0] : features.clone();
        this.psqt = psqt;
    }

	        /**
	         * Compares this transform output with another output snapshot.
	         *
	         * @param other object to compare
	         * @return true when the PSQT value and transformed features match
	         */
	        @Override
	        public boolean equals(Object other) {
	            return other instanceof TransformOutput that
	                    && psqt == that.psqt
	                    && Arrays.equals(features, that.features);
	        }

	        /**
	         * Computes a hash over the transformed features and PSQT value.
	         *
	         * @return transform output hash code
	         */
	        @Override
	        public int hashCode() {
	            int result = Arrays.hashCode(features);
        result = 31 * result + Integer.hashCode(psqt);
	            return result;
	        }

	        /**
	         * Formats this transform output for diagnostics.
	         *
	         * @return debug string containing transformed features and PSQT output
	         */
	        @Override
	        public String toString() {
	            return "TransformOutput[features="
                + Arrays.toString(features)
                + ", psqt="
                + psqt
                + "]";
    }
}
