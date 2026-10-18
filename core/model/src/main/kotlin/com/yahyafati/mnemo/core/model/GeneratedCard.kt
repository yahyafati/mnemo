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

/** Which parts of a source are sent with the end of the part before them (ADR 0015, C3). */
enum class Overlap {
    /** Only a part that goes on with a section an earlier part started. */
    WithinSections,

    /** Every part after the first, across headings too. */
    Always,

    /** None. */
    Off,
}

/** Whether a long source is summarised before its parts are sent (ADR 0015, C4). */
enum class ContextSummary {
    /** One request writes a summary when the source has at least [ExtractOptions.MIN_PARTS_FOR_SUMMARY] parts. */
    Auto,

    /** Never. */
    Off,
}

/** What the user asked Smart Extract for. */
data class ExtractOptions(
    val density: ExtractDensity = ExtractDensity.Balanced,
    val archetypes: Set<CardArchetype> = setOf(CardArchetype.Definition, CardArchetype.Cloze),
    /** The cards' language, as an English name ("Spanish"); null keeps the source's language. */
    val language: String? = null,
    /**
     * Cards are about the ideas, not about the examples, anecdotes and stories that illustrate them (ADR 0015, C1).
     * Off for readers who want the stories, as in history, law or medicine.
     */
    val skipIllustrations: Boolean = true,
    /** Which parts are sent with the end of the part before them, as context only (ADR 0015, C3). */
    val overlap: Overlap = Overlap.WithinSections,
    /** Whether a long source gets a short summary that is sent with every part (ADR 0015, C4). */
    val contextSummary: ContextSummary = ContextSummary.Auto,
) {
    /** At most how many cards [words] of source should give: a ceiling, since a part may hold fewer ideas (ADR 0015). */
    fun targetCards(words: Int): Int =
        (words / density.wordsPerCard).coerceIn(MIN_CARDS_PER_REQUEST, MAX_CARDS_PER_REQUEST)

    /** A source of [parts] parts gets a summary: it is on, and a one- or two-part source is its own context. */
    fun makesSummary(parts: Int): Boolean = contextSummary == ContextSummary.Auto && parts >= MIN_PARTS_FOR_SUMMARY

    /** How many requests a source of [parts] parts takes, the summary's included; 0 for no parts. */
    fun requests(parts: Int): Int = if (parts <= 0) 0 else parts + if (makesSummary(parts)) 1 else 0

    companion object {
        const val MIN_CARDS_PER_REQUEST = 3
        const val MAX_CARDS_PER_REQUEST = 40

        /** The fewest parts for which Auto writes a summary (ADR 0015, C4). */
        const val MIN_PARTS_FOR_SUMMARY = 3

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
