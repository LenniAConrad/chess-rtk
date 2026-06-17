/**
 * Engine-free game-review primitives.
 *
 * <p>This package holds deterministic review logic and row contracts that can
 * be shared by the CLI and Workbench without starting engines or reading
 * files. UCI orchestration and file I/O should stay in thin application-layer
 * commands that call these pure helpers.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
package chess.review;
