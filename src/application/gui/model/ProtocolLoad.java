package application.gui.model;

import chess.uci.Protocol;

/**
 * Result for engine protocol loading/validation.
 *
 * Wraps the parsed protocol with any error text so the GUI can show validation hints when loading TOML configs.
 *
 * @param protocol parsed protocol data.
 * @param error validation error message, if any.
  * @since 2026
  * @author Lennart A. Conrad
 */
public record ProtocolLoad(Protocol protocol, String error) {
}
