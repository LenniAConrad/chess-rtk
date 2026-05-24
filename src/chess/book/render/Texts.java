package chess.book.render;

import chess.book.model.Language;

/**
 * Supplies localized labels and fallback prose for native chess books.
 *
 * <p>
 * The native renderer keeps structural labels and a compact set of default
 * helper paragraphs inside the Java package.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class Texts {

	/**
	 * Shared English puzzle heading.
	 */
	private static final String PUZZLES = "Puzzles";

	/**
	 * Default English how-to-read paragraphs used when the book JSON does not
	 * provide its own text.
	 */
	private static final String[] DEFAULT_ENGLISH_HOW_TO_READ = {
			"This book offers a collection of chess puzzles, each crafted to challenge and improve your skills while playing as White. Every diagram is shown with White at the bottom: the a1 square is always the lower-left square and h8 is always the upper-right square. This orientation is used consistently on puzzle pages, solution pages, and the special-move examples.",
			"To make navigating this book simple, each puzzle is assigned a unique identification number (ID). On the left-hand side of each spread, you'll find the starting position of the puzzle along with its ID. The side with the solution, which features the final board after White's winning play, is displayed on the right-hand side. This layout allows you to easily compare your solution with the correct answer.",
			"In between the puzzle exercises, there are comprehensive tables that list each puzzle by its unique ID, alongside the solution. Additionally, every puzzle in this book has the 'Halfmove clock' set to zero, meaning the 50-move rule does not apply. This ensures that the puzzles focus purely on tactics and strategy, without the added complexity of time-sensitive scenarios.",
			"Understanding special moves like castling and en passant is crucial for mastering chess. The diagrams below use the same white-side-down layout as the puzzle pages, so arrows and pieces should be read with White's home rank along the bottom edge. This book uses clear illustrations to indicate when these moves are possible.",
			"It's important to remember that the right to castle doesn't automatically mean it is a legal move at that moment; it simply indicates that castling is an option under the right conditions.",
			"This book is designed to be both educational and enjoyable, offering puzzles that challenge you to think critically and improve your game. Whether you're a beginner looking to sharpen your skills or an experienced player seeking fresh challenges, these puzzles will help you take your game to the next level.",
			"With all of this said, you are ready and well-equipped to start the grind!" };

	/**
	 * Prevents instantiation of the text helper.
	 */
	private Texts() {
		// utility
	}

	/**
	 * Returns the localized table-of-contents title.
	 *
	 * @param language requested language
	 * @return localized table-of-contents title
	 */
	static String tableOfContents(Language language) {
		return switch (language) {
			case German -> "Inhalt";
			case Chinese -> "目录";
			case French -> "Table des matières";
			case Spanish -> "Contenido";
			case Russian -> "Содержание";
			case Italian -> "Indice";
			case Turkish -> "İçindekiler";
			case Korean -> "목차";
			case Japanese -> "目次";
			case Hebrew -> "תוכן";
			case Arabic -> "جدول المحتويات";
			case Portuguese -> "Índice";
			case SwissGerman -> "Inhaltsverzeichnis";
			case English -> "Contents";
		};
	}

	/**
	 * Returns the localized introduction heading.
	 *
	 * @param language requested language
	 * @return localized introduction heading
	 */
	static String introduction(Language language) {
		return switch (language) {
			case German -> "Einführung";
			case Chinese -> "简介";
			case French -> "Introduction";
			case Spanish -> "Introducción";
			case Russian -> "Введение";
			case Italian -> "Introduzione";
			case Turkish -> "Giriş";
			case Korean -> "소개";
			case Japanese -> "はじめに";
			case Hebrew -> "מבוא";
			case Arabic -> "مقدمة";
			case Portuguese -> "Introdução";
			case SwissGerman -> "Iifüehrig";
			case English -> "Introduction";
		};
	}

	/**
	 * Returns the localized how-to-read heading.
	 *
	 * @param language requested language
	 * @return localized how-to-read heading
	 */
	static String howToRead(Language language) {
		return switch (language) {
			case German -> "Wie man das Buch liest";
			case Chinese -> "如何阅读本书";
			case French -> "Comment lire le livre";
			case Spanish -> "Cómo leer el libro";
			case Russian -> "Как читать эту книгу";
			case Italian -> "Come leggere il libro";
			case Turkish -> "Kitabı Nasıl Okumalısınız";
			case Korean -> "이 책을 읽는 방법";
			case Japanese -> "本書の使い方";
			case Hebrew -> "איך לקרוא את הספר";
			case Arabic -> "كيفية قراءة الكتاب";
			case Portuguese -> "Como ler o livro";
			case SwissGerman -> "Wie mer s Buech list";
			case English -> "How to Read the Book";
		};
	}

	/**
	 * Returns the localized puzzles heading.
	 *
	 * @param language requested language
	 * @return localized puzzles heading
	 */
	static String puzzles(Language language) {
		return switch (language) {
			case German -> PUZZLES;
			case Chinese -> "棋题";
			case French -> "Puzzles d'échecs";
			case Spanish -> "Rompecabezas";
			case Russian -> "Шахматные задачи";
			case Italian -> "Puzzles di scacchi";
			case Turkish -> "Bulmacalar";
			case Korean -> "퍼즐";
			case Japanese -> "チェスパズル";
			case Hebrew -> "חידות";
			case Arabic -> "ألغاز";
			case Portuguese -> "Problemas";
			case SwissGerman, English -> PUZZLES;
		};
	}

	/**
	 * Returns the localized section prefix used for puzzle blocks.
	 *
	 * @param language requested language
	 * @return localized section prefix with trailing space
	 */
	static String section(Language language) {
		return switch (language) {
			case German -> "Abschnitt ";
			case Chinese -> "章节 ";
			case French -> "Section ";
			case Spanish -> "Sección ";
			case Russian -> "Раздел ";
			case Italian -> "Sezione ";
			case Turkish -> "Bölüm ";
			case Korean -> "섹션 ";
			case Japanese -> "セクション ";
			case Hebrew -> "חלק ";
			case Arabic -> "قسم ";
			case Portuguese -> "Seção ";
			case SwissGerman -> "Abschnitt ";
			case English -> "Section ";
		};
	}

	/**
	 * Returns the localized table column label for puzzle IDs.
	 *
	 * @param language requested language
	 * @return localized ID label
	 */
	static String id(Language language) {
		return switch (language) {
			case Chinese -> "序号";
			case Turkish -> "Kimlik";
			case Korean -> "번호";
			case Japanese -> "番号";
			case Arabic -> "معرّف";
			case Portuguese -> "ID";
			case SwissGerman -> "ID";
			case German, English, French, Spanish, Russian, Italian, Hebrew -> "ID";
		};
	}

	/**
	 * Returns the localized table column label for move strings.
	 *
	 * @param language requested language
	 * @return localized move label
	 */
	static String moves(Language language) {
		return switch (language) {
			case German -> "Züge";
			case Chinese -> "走法";
			case French -> "Coups";
			case Spanish -> "Movimientos";
			case Russian -> "Ходы";
			case Italian -> "Mosse";
			case Turkish -> "Hamleler";
			case Korean -> "수";
			case Japanese -> "手";
			case Hebrew -> "מסעים";
			case Arabic -> "النقلات";
			case Portuguese -> "Lances";
			case SwissGerman -> "Züg";
			case English -> "Moves";
		};
	}

	/**
	 * Returns the localized prefix used for solution-page table references.
	 *
	 * @param language requested language
	 * @return localized prefix with trailing space
	 */
	static String fullSolutions(Language language) {
		return switch (language) {
			case German -> "Vollständige Lösungen auf Seite ";
			case Chinese -> "完整解答见第 ";
			case French -> "Solutions complètes à la page ";
			case Spanish -> "Soluciones completas en la página ";
			case Russian -> "Полные решения на странице ";
			case Italian -> "Soluzioni complete a pagina ";
			case Turkish -> "Tam çözümler sayfa ";
			case Korean -> "전체 해답은 ";
			case Japanese -> "完全な解答は ";
			case Hebrew -> "הפתרונות המלאים בעמוד ";
			case Arabic -> "الحلول الكاملة في الصفحة ";
			case Portuguese -> "Soluções completas na página ";
			case SwissGerman -> "Vollständigi Lösige uf Siite ";
			case English -> "Full solutions at page ";
		};
	}

	/**
	 * Returns fallback how-to-read paragraphs for the requested language.
	 *
	 * @return fallback paragraph array
	 */
	static String[] defaultHowToRead() {
		return DEFAULT_ENGLISH_HOW_TO_READ.clone();
	}

	/**
	 * Returns a fallback afterword paragraph array for the requested language.
	 *
	 * @param language requested language
	 * @return fallback afterword paragraph array
	 */
	static String[] defaultAfterword(Language language) {
		String line = switch (language) {
			case German -> "Danke, dass du die Rätsel gelöst hast. Das bedeutet mir sehr viel.";
			case Chinese -> "非常感谢您完成这些题目，这对我来说意义重大。";
			case French -> "Merci beaucoup d'avoir résolu ces puzzles. Cela compte énormément pour moi.";
			case Spanish -> "Muchas gracias por resolver estos problemas. Significa muchísimo para mí.";
			case Russian -> "Спасибо за решение этих задач. Это очень много для меня значит.";
			case Italian -> "Grazie mille per aver risolto questi puzzle. Significa tantissimo per me.";
			case Turkish -> "Bu bulmacaları çözdüğünüz için çok teşekkür ederim. Bu benim için çok şey ifade ediyor.";
			case Korean -> "이 퍼즐들을 풀어 주셔서 정말 감사합니다. 저에게 큰 의미가 있습니다.";
			case Japanese -> "このパズルを解いてくださって本当にありがとうございます。私にとって大きな意味があります。";
			case Hebrew -> "תודה רבה שפתרתם את החידות האלה. זה אומר לי המון.";
			case Arabic -> "شكرًا جزيلًا لكم على حل هذه الألغاز. هذا يعني لي الكثير.";
			case Portuguese -> "Muito obrigado por resolver estes problemas. Isso significa muito para mim.";
			case SwissGerman -> "Merci viu mau fürs Löse vo dene Puzzles. Das bedütet mir viel.";
			case English -> "Thank you so much for solving,\nit means the world to me.";
		};
		return new String[] { line };
	}
}
