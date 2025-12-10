package nasa.jpl.klang.ide.highlighting

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import nasa.jpl.klang.ide.KIcons
import javax.swing.Icon

/**
 * Color settings page for K language.
 * Allows users to customize syntax highlighting colors in Preferences → Editor → Color Scheme → K
 */
class KColorSettingsPage : ColorSettingsPage {

    companion object {
        private val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Keywords//Declaration", KHighlightingColors.KEYWORD),
            AttributesDescriptor("Keywords//Constraint", KHighlightingColors.CONSTRAINT),
            AttributesDescriptor("Types", KHighlightingColors.TYPE),
            AttributesDescriptor("Operators", KHighlightingColors.OPERATOR),
            AttributesDescriptor("Literals//Number", KHighlightingColors.NUMBER),
            AttributesDescriptor("Literals//String", KHighlightingColors.STRING),
            AttributesDescriptor("Comments//Line comment", KHighlightingColors.COMMENT),
            AttributesDescriptor("Comments//Block comment", KHighlightingColors.BLOCK_COMMENT),
            AttributesDescriptor("Annotations", KHighlightingColors.ANNOTATION),
            AttributesDescriptor("Braces and Operators//Braces", KHighlightingColors.BRACES),
            AttributesDescriptor("Braces and Operators//Brackets", KHighlightingColors.BRACKETS),
            AttributesDescriptor("Braces and Operators//Parentheses", KHighlightingColors.PARENTHESES),
            AttributesDescriptor("Identifiers", KHighlightingColors.IDENTIFIER),
            AttributesDescriptor("Bad character", KHighlightingColors.BAD_CHARACTER)
        )

        private val ADDITIONAL_HIGHLIGHTING = mapOf(
            "kw" to KHighlightingColors.KEYWORD,
            "con" to KHighlightingColors.CONSTRAINT,
            "type" to KHighlightingColors.TYPE,
            "num" to KHighlightingColors.NUMBER,
            "str" to KHighlightingColors.STRING,
            "ann" to KHighlightingColors.ANNOTATION
        )
    }

    override fun getIcon(): Icon = KIcons.FILE

    override fun getHighlighter(): SyntaxHighlighter = KSyntaxHighlighter()

    override fun getDemoText(): String = """
        |==============================================
        |This is a documentation block comment.
        |It spans multiple lines.
        |==============================================
        |
        |<kw>package</kw> examples.Shapes
        |
        |<kw>import</kw> java.util.List
        |
        |-- This is a line comment
        |// This is also a line comment
        |
        |/* Block comment */
        |
        |<kw>class</kw> Shape {
        |  sides : <type>Int</type>
        |  <kw>fun</kw> area : <type>Real</type>
        |}
        |
        |<kw>class</kw> Triangle <kw>extends</kw> Shape {
        |  base : <type>Int</type>
        |  height : <type>Int</type>
        |  
        |  <con>req</con> sides = <num>3</num>
        |  
        |  <kw>fun</kw> area : <type>Real</type> {
        |    base * height / <num>2</num>
        |  }
        |  
        |  name : <type>String</type> = <str>"triangle"</str>
        |  isValid : <type>Bool</type> = <kw>true</kw>
        |  
        |  <con>req</con> Valid: base > <num>0</num> && height > <num>0</num>
        |  
        |  <con>soft</con> Prefer: area > <num>100</num>
        |  
        |  <con>minimize</con> base + height
        |}
        |
        |<ann>@</ann>constraint
        |<kw>class</kw> Equilateral <kw>extends</kw> Triangle {
        |  <con>req</con> <kw>forall</kw> a, b : sides => a = b
        |}
    """.trimMargin()

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = ADDITIONAL_HIGHLIGHTING

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDisplayName(): String = "K"
}

