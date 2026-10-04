package com.yahyafati.mnemo.core.model

/** The kinds of card Smart Extract can ask for (PROJECT_OVERVIEW §5.3). */
enum class CardArchetype {
    /** Concept → definition, as a Basic card. */
    Definition,

    /** A key sentence with its important terms hidden, as a Cloze card. */
    Cloze,

    /** A question, its answer and three wrong answers, as a Multiple choice card. */
    MultipleChoice,

    /** A short scenario that applies the material, as a Basic card. */
    CaseStudy,
}

/** How many cards to make from the same text. */
enum class ExtractDensity(
    /** About one card per this many words of source. */
    val wordsPerCard: Int,
) {
    /** Only the high-yield facts. */
    Concise(110),
    Balanced(70),

    /** Everything worth remembering. */
    Comprehensive(40),
}

/** What the user asked Smart Extract for. */
data class ExtractOptions(
    val density: ExtractDensity = ExtractDensity.Balanced,
    val archetypes: Set<CardArchetype> = setOf(CardArchetype.Definition, CardArchetype.Cloze),
    /** The cards' language, as an English name ("Spanish"); null keeps the source's language. */
    val language: String? = null,
) {
    /** About how many cards [words] of source should give. */
    fun targetCards(words: Int): Int =
        (words / density.wordsPerCard).coerceIn(MIN_CARDS_PER_REQUEST, MAX_CARDS_PER_REQUEST)

    companion object {
        const val MIN_CARDS_PER_REQUEST = 3
        const val MAX_CARDS_PER_REQUEST = 40

        /** Languages offered in the picker, besides "same as the source". */
        val Languages = listOf(
            "English", "Spanish", "French", "German", "Italian", "Portuguese", "Dutch", "Polish",
            "Russian", "Ukrainian", "Turkish", "Arabic", "Persian", "Hebrew", "Hindi", "Bengali",
            "Chinese", "Japanese", "Korean", "Indonesian", "Vietnamese", "Thai", "Swedish", "Amharic",
        )
    }
}

/**
 * A card an AI model proposed, waiting in the review queue. Nothing is saved until the user
 * accepts it. [id] is local to the queue; [chunkIndex] says which part of the source it came from,
 * so it can be regenerated from the same text.
 */
data class GeneratedCard(
    val id: String,
    val kind: NoteKind,
    /** Basic and multiple choice: the question. Cloze: the text with `{{c1::…}}` deletions. */
    val front: String,
    /** Basic: the answer. Multiple choice: the correct option. Cloze: the optional Extra field. */
    val back: String,
    val tags: List<String> = emptyList(),
    val chunkIndex: Int = 0,
    /** Multiple choice: the wrong options. */
    val wrongAnswers: List<String> = emptyList(),
    /** Cards from page images: the page of the PDF (1-based) the model says the card came from. Queue only, never saved. */
    val page: Int? = null,
) {
    val fields: List<String>
        get() = if (kind == NoteKind.MultipleChoice) listOf(front, back, MultipleChoice.wrongField(wrongAnswers)) else listOf(front, back)

    val sides: CardSides get() = CardSides.of(kind, fields, kind.cardOrdinals(fields).firstOrNull() ?: 0)

    /** How many study cards accepting it makes. */
    val cardCount: Int get() = kind.cardOrdinals(fields).size
}
