package chess.engine;



/**
 * Captures BT4 canonical transform from activation output.
 */
final class TransformSink implements chess.nn.ActivationSink {
    /**
     * Transform.
     */
    int transform;

    /**
     * {@inheritDoc}
     */
    @Override
    public void put(String key, int[] shape, float[] data) {
        if ("bt4.input.transform".equals(key) && data != null && data.length > 0) {
            transform = Math.round(data[0]);
        }
    }
}
