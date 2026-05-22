/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.network;

/**
 * One active Half-KP feature ranked by signed centipawn impact.
 *
 * @param row active feature row
 * @param featureIndex sparse feature index
 * @param impact signed centipawn impact
 * @param rank absolute-impact rank
 * @param valid true when this driver points at a real feature
 */
public record FeatureDriver(int row, int featureIndex, float impact, int rank, boolean valid) {

    /**
     * Empty feature-driver sentinel.
     *
     * @return invalid driver
     */
    public static FeatureDriver invalid() {
    return new FeatureDriver(-1, -1, 0.0f, 0, false);
    }
}
