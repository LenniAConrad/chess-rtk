package chess.nn.t5;

import java.util.stream.IntStream;

/**
 * Math helpers for the T5 forward pass.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class MathOps {
  private static final int MATMUL_PARALLEL_THRESHOLD = 32_768;
  private static final int LAYERNORM_PARALLEL_THRESHOLD = 8_192;
  /**
   * Utility holder; instantiation is not allowed.
   */
  private MathOps() {}

  private static boolean useParallel(int workItems, int threshold) {
    int cores = Runtime.getRuntime().availableProcessors();
    return cores > 1 && workItems >= threshold;
  }

  private static boolean useMatmulParallel(int m, int n) {
    int cores = Runtime.getRuntime().availableProcessors();
    if (cores <= 1) {
      return false;
    }
    if (m < Math.min(cores * 2, 16)) {
      return false;
    }
    return (long) m * n >= MATMUL_PARALLEL_THRESHOLD;
  }

  /**
   * Applies RMS layer normalization in place.
   *
   * @param data flat [rows, cols] array
   * @param rows number of rows
   * @param cols number of columns
   * @param weight scale vector
   * @param eps epsilon for numerical stability
   */
  public static void layerNormInPlace(float[] data, int rows, int cols, float[] weight, float eps) {
    if (useParallel(rows * cols, LAYERNORM_PARALLEL_THRESHOLD)) {
      IntStream.range(0, rows).parallel().forEach(r -> {
        int offset = r * cols;
        float meanSq = 0f;
        for (int c = 0; c < cols; c++) {
          float v = data[offset + c];
          meanSq += v * v;
        }
        meanSq /= cols;
        float inv = (float) (1.0 / Math.sqrt(meanSq + eps));
        for (int c = 0; c < cols; c++) {
          data[offset + c] = data[offset + c] * inv * weight[c];
        }
      });
      return;
    }
    for (int r = 0; r < rows; r++) {
      int offset = r * cols;
      float meanSq = 0f;
      for (int c = 0; c < cols; c++) {
        float v = data[offset + c];
        meanSq += v * v;
      }
      meanSq /= cols;
      float inv = (float) (1.0 / Math.sqrt(meanSq + eps));
      for (int c = 0; c < cols; c++) {
        data[offset + c] = data[offset + c] * inv * weight[c];
      }
    }
  }

  /**
   * GELU activation used in T5 FFN.
   *
   * @param x input value
   * @return activated value
   */
  public static float gelu(float x) {
    return 0.5f * x * (1f + (float) Math.tanh(Math.sqrt(2f / Math.PI) * (x + 0.044715f * x * x * x)));
  }

  /**
   * Applies softmax over contiguous rows in place.
   *
   * @param logits flat logits array
   * @param offsetBase starting offset
   * @param rows number of rows
   * @param cols number of columns
   */
  public static void softmaxInPlace(float[] logits, int offsetBase, int rows, int cols) {
    for (int r = 0; r < rows; r++) {
      int offset = offsetBase + r * cols;
      float max = rowMax(logits, offset, cols);
      float sum = exponentiateRow(logits, offset, cols, max);
      normalizeRow(logits, offset, cols, sum);
    }
  }

  /**
   * Computes the maximum logit in a row slice.
   *
   * @param logits flat array
   * @param offset row start offset
   * @param cols number of columns
   * @return maximum value
   */
  private static float rowMax(float[] logits, int offset, int cols) {
    float max = -Float.MAX_VALUE;
    for (int c = 0; c < cols; c++) {
      float v = logits[offset + c];
      if (v > max) {
        max = v;
      }
    }
    return max;
  }

  /**
   * Exponentiates a row relative to the max logit.
   *
   * @param logits target array
   * @param offset row start offset
   * @param cols number of columns
   * @param max row maximum for stability
   * @return exponential sum
   */
  private static float exponentiateRow(float[] logits, int offset, int cols, float max) {
    float sum = 0f;
    for (int c = 0; c < cols; c++) {
      float exp = (float) Math.exp(logits[offset + c] - max);
      logits[offset + c] = exp;
      sum += exp;
    }
    return sum;
  }

  /**
   * Normalizes a row after softmax exponentiation.
   *
   * @param logits target array
   * @param offset row start offset
   * @param cols number of columns
   * @param sum exponent sum
   */
  private static void normalizeRow(float[] logits, int offset, int cols, float sum) {
    if (sum == 0f) {
      float uniform = 1f / cols;
      for (int c = 0; c < cols; c++) {
        logits[offset + c] = uniform;
      }
      return;
    }
    float inv = 1f / sum;
    for (int c = 0; c < cols; c++) {
      logits[offset + c] *= inv;
    }
  }

  /**
   * Matrix multiply where {@code bT} is already transposed.
   *
   * @param a left matrix [m, k]
   * @param bT right matrix transposed [n, k]
   * @return output matrix [m, n]
   */
  public static Tensor matmul(Tensor a, Tensor bT) {
    int m = a.shape[0];
    int k = a.shape[1];
    int n = bT.shape[0];
    if (m == 0 || n == 0 || k == 0) {
      return Tensor.zeros(m, n);
    }
    Tensor out = Tensor.zeros(m, n);
    float[] outData = out.data;
    float[] aData = a.data;
    float[] bData = bT.data;
    if (useMatmulParallel(m, n)) {
      matmulParallelRows(aData, bData, outData, m, n, k);
    } else {
      matmulNaive(aData, bData, outData, m, n, k);
    }
    return out;
  }

  private static void matmulNaive(float[] aData, float[] bData, float[] outData, int m, int n, int k) {
    int unroll = k - (k % 4);
    for (int i = 0; i < m; i++) {
      int aOffset = i * k;
      int outOffset = i * n;
      for (int j = 0; j < n; j++) {
        int bOffset = j * k;
        float sum = 0f;
        int p = 0;
        for (; p < unroll; p += 4) {
          sum += aData[aOffset + p] * bData[bOffset + p]
              + aData[aOffset + p + 1] * bData[bOffset + p + 1]
              + aData[aOffset + p + 2] * bData[bOffset + p + 2]
              + aData[aOffset + p + 3] * bData[bOffset + p + 3];
        }
        for (; p < k; p++) {
          sum += aData[aOffset + p] * bData[bOffset + p];
        }
        outData[outOffset + j] = sum;
      }
    }
  }

  private static void matmulParallelRows(float[] aData, float[] bData, float[] outData, int m, int n, int k) {
    int unroll = k - (k % 4);
    IntStream.range(0, m).parallel().forEach(i -> {
      int aOffset = i * k;
      int outOffset = i * n;
      for (int j = 0; j < n; j++) {
        int bOffset = j * k;
        float sum = 0f;
        int p = 0;
        for (; p < unroll; p += 4) {
          sum += aData[aOffset + p] * bData[bOffset + p]
              + aData[aOffset + p + 1] * bData[bOffset + p + 1]
              + aData[aOffset + p + 2] * bData[bOffset + p + 2]
              + aData[aOffset + p + 3] * bData[bOffset + p + 3];
        }
        for (; p < k; p++) {
          sum += aData[aOffset + p] * bData[bOffset + p];
        }
        outData[outOffset + j] = sum;
      }
    });
  }

  /**
   * Adds vector {@code b} into {@code a} in place.
   *
   * @param a target array
   * @param b addend array
   */
  public static void addInPlace(float[] a, float[] b) {
    for (int i = 0; i < a.length; i++) {
      a[i] += b[i];
    }
  }
}
