package chess.nn.t5.rocm;

import chess.nn.t5.Tensor;

/**
 * JNI-backed ROCm kernels for T5 inference.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Kernels {

  /**
   * Utility class, prevents instantiation.
   */
  private Kernels() {}

  /**
   * Runs a ROCm matmul where {@code bT} is already transposed.
   *
   * @param a left matrix [m, k]
   * @param bT right matrix transposed [n, k]
   * @return output tensor [m, n], or {@code null} when ROCm is unavailable
   */
  public static Tensor matmul(Tensor a, Tensor bT) {
    if (a.shape.length != 2 || bT.shape.length != 2) {
      throw new IllegalArgumentException("matmul expects 2D tensors");
    }
    if (!Support.isAvailable()) {
      return null;
    }
    int m = a.shape[0];
    int k = a.shape[1];
    if (bT.shape[1] != k) {
      throw new IllegalArgumentException("matmul shapes mismatch: a=" + m + "x" + k
          + " bT=" + bT.shape[0] + "x" + bT.shape[1]);
    }
    int n = bT.shape[0];
    Tensor out = Tensor.zeros(m, n);
    boolean ok = nativeMatmul(a.data, m, k, bT.data, n, out.data);
    return ok ? out : null;
  }

  /**
   * JNI entry point implemented in {@code native/rocm/t5_rocm_jni.hip} (when provided).
   *
   * @param a input matrix data [m * k]
   * @param m number of rows in {@code a}
   * @param k number of columns in {@code a} / {@code bT}
   * @param bT transposed matrix data [n * k]
   * @param n number of rows in {@code bT}
   * @param out output buffer [m * n]
   * @return {@code true} when the kernel completed successfully
   */
  private static native boolean nativeMatmul(float[] a, int m, int k, float[] bT, int n, float[] out);
}
