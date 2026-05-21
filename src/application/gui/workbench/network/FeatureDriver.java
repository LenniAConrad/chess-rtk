package application.gui.workbench.network;

import application.gui.workbench.board.*;
import application.gui.workbench.command.*;
import application.gui.workbench.dashboard.*;
import application.gui.workbench.game.*;
import application.gui.workbench.layout.*;
import application.gui.workbench.mcts.*;
import application.gui.workbench.publish.*;
import application.gui.workbench.session.*;
import application.gui.workbench.ui.*;
import application.gui.workbench.window.*;

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
