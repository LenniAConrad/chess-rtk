package chess.nn.t5;

import java.util.Arrays;

/**
 * Simple float tensor wrapper for T5 weights and activations.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Tensor {

  /**
   * Flat data buffer in row-major order.
   */
  public final float[] data;

  /**
   * Shape of the tensor.
   */
  public final int[] shape;

  /**
   * Creates a tensor view.
   *
   * @param data flat data array
   * @param shape tensor shape
   */
  public Tensor(float[] data, int[] shape) {
    this.data = data;
    this.shape = shape;
  }

  /**
   * Allocates a zero-filled tensor.
   *
   * @param shape tensor shape
   * @return zero tensor
   */
  public static Tensor zeros(int... shape) {
    int total = 1;
    for (int d : shape) {
      total *= d;
    }
    return new Tensor(new float[total], shape);
  }

  /**
   * Creates a deep copy of the tensor.
   *
   * @return copied tensor
   */
  public Tensor copy() {
    return new Tensor(Arrays.copyOf(data, data.length), Arrays.copyOf(shape, shape.length));
  }
}
