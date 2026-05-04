package com.hawatri.pinit.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

// The types of text formats we support
enum class FormatType {
    BOLD, ITALIC, STRIKETHROUGH, HEADING
}

// Data class to track exactly where a format starts and ends
data class FormatRange(
    val type: FormatType,
    val start: Int,
    val end: Int
)

// The transformation layer that paints the styles onto the raw text
class RichTextVisualTransformation(
    private val formatRanges: List<FormatRange>
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text)

        for (range in formatRanges) {
            // Safety bounds check to prevent crashes while actively typing/deleting
            val safeStart = range.start.coerceIn(0, text.length)
            val safeEnd = range.end.coerceIn(0, text.length)

            if (safeStart >= safeEnd) continue

            // Apply the actual visual UI styles
            when (range.type) {
                FormatType.BOLD -> builder.addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold), safeStart, safeEnd
                )
                FormatType.ITALIC -> builder.addStyle(
                    SpanStyle(fontStyle = FontStyle.Italic), safeStart, safeEnd
                )
                FormatType.STRIKETHROUGH -> builder.addStyle(
                    SpanStyle(textDecoration = TextDecoration.LineThrough), safeStart, safeEnd
                )
                FormatType.HEADING -> builder.addStyle(
                    SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold), safeStart, safeEnd
                )
            }
        }

        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

// Helper function to draw formatted text on normal Text() composables
fun buildFormattedString(text: String, formatRanges: List<FormatRange>): AnnotatedString {
    val builder = AnnotatedString.Builder(text)

    for (range in formatRanges) {
        val safeStart = range.start.coerceIn(0, text.length)
        val safeEnd = range.end.coerceIn(0, text.length)
        if (safeStart >= safeEnd) continue

        when (range.type) {
            FormatType.BOLD -> builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), safeStart, safeEnd)
            FormatType.ITALIC -> builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), safeStart, safeEnd)
            FormatType.STRIKETHROUGH -> builder.addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), safeStart, safeEnd)
            FormatType.HEADING -> builder.addStyle(SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold), safeStart, safeEnd)
        }
    }
    return builder.toAnnotatedString()
}