package eu.kanade.tachiyomi.ui.reader.setting

/** A versioned, bounded appearance snapshot. Unknown or damaged presets are ignored. */
internal data class EpubAppearance(
    val font: Int = 0,
    val size: Int = 38,
    val theme: Int = 0,
    val lineSpacing: Int = 150,
    val paragraphSpacing: Int = 50,
    val margin: Int = 80,
    val compact: Boolean = false,
) {
    fun applyTo(prefs: ReaderPreferences) {
        prefs.epubFont.set(font.coerceIn(0, 2))
        prefs.epubFontSize.set(size.coerceIn(26, 64))
        prefs.epubTheme.set(theme.coerceIn(0, 3))
        prefs.epubLineSpacing.set(lineSpacing.coerceIn(110, 200))
        prefs.epubParagraphSpacing.set(paragraphSpacing.coerceIn(0, 100))
        prefs.epubMargin.set(margin.coerceIn(40, 140))
        prefs.epubCompactPages.set(compact)
    }

    fun encode(): String = listOf(
        1, font, size, theme, lineSpacing, paragraphSpacing, margin, if (compact) 1 else 0,
    ).joinToString(",")

    companion object {
        fun capture(prefs: ReaderPreferences) = EpubAppearance(
            prefs.epubFont.get(), prefs.epubFontSize.get(), prefs.epubTheme.get(),
            prefs.epubLineSpacing.get(), prefs.epubParagraphSpacing.get(), prefs.epubMargin.get(),
            prefs.epubCompactPages.get(),
        )

        fun decode(value: String): EpubAppearance? {
            val fields = value.split(',').map { it.toIntOrNull() ?: return null }
            if (fields.size != 8 || fields[0] != 1 || fields[7] !in 0..1) return null
            return EpubAppearance(fields[1], fields[2], fields[3], fields[4], fields[5], fields[6], fields[7] == 1)
        }
    }
}
