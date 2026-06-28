package chess.study;

/**
 * Chapter mode used by the local Study Workspace.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public enum StudyChapterMode {

    /**
     * Normal annotated study chapter.
     */
    NORMAL,

    /**
     * Practice chapter where the user can guess continuations.
     */
    PRACTICE,

    /**
     * Gamebook-style chapter with guided choices.
     */
    GAMEBOOK,

    /**
     * Chapter whose future moves can be concealed during training.
     */
    CONCEAL
}
