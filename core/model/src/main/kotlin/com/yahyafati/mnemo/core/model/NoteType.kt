package com.yahyafati.mnemo.core.model

/** How a note becomes cards and how those cards are shown. */
enum class NoteKind {
    /** Front → back. One card. */
    Basic,

    /** Front → back and back → front. Two cards. */
    Reversed,

    /** Text with `{{c1::…}}` deletions. One card per cloze number. */
    Cloze,

    /** Front → the user types the back, which is checked against what they typed. One card. */
    TypeIn,

    /**
     * A question, its answer and wrong answers (one per line), shown as shuffled options. One card.
     * See [MultipleChoice].
     */
    MultipleChoice,
}

/** A note's field layout. The built-in types have fixed ids so every install shares them. */
data class NoteType(
    val id: String,
    val name: String,
    val kind: NoteKind,
    val fields: List<String>,
) {
    companion object {
        val Basic = NoteType("00000000-0000-4000-8000-000000000001", "Basic", NoteKind.Basic, listOf("Front", "Back"))
        val Reversed = NoteType(
            "00000000-0000-4000-8000-000000000002", "Basic + Reversed", NoteKind.Reversed, listOf("Front", "Back"),
        )
        val Cloze = NoteType("00000000-0000-4000-8000-000000000003", "Cloze", NoteKind.Cloze, listOf("Text", "Extra"))
        val TypeIn = NoteType(
            "00000000-0000-4000-8000-000000000004", "Type-in answer", NoteKind.TypeIn, listOf("Front", "Back"),
        )
        val MultipleChoice = NoteType(
            "00000000-0000-4000-8000-000000000005", "Multiple choice", NoteKind.MultipleChoice,
            listOf("Question", "Answer", "Wrong answers"),
        )

        val BuiltIns: List<NoteType> = listOf(Basic, Reversed, Cloze, TypeIn, MultipleChoice)

        fun builtIn(kind: NoteKind): NoteType = BuiltIns.first { it.kind == kind }

        /** The built-in type with [id], or null for an unknown (e.g. future custom) type. */
        fun byId(id: String): NoteType? = BuiltIns.firstOrNull { it.id == id }
    }
}
