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
            AttributesDescriptor("Types//Built-in types", KHighlightingColors.TYPE),
            AttributesDescriptor("Declarations//Class name", KHighlightingColors.CLASS_NAME),
            AttributesDescriptor("Declarations//Class reference", KHighlightingColors.CLASS_REFERENCE),
            AttributesDescriptor("Declarations//Function name", KHighlightingColors.FUNCTION_NAME),
            AttributesDescriptor("Declarations//Property name", KHighlightingColors.PROPERTY_NAME),
            AttributesDescriptor("Declarations//Constraint name", KHighlightingColors.CONSTRAINT_NAME),
            AttributesDescriptor("Declarations//Parameter", KHighlightingColors.PARAMETER),
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
            "cls" to KHighlightingColors.CLASS_NAME,
            "clsref" to KHighlightingColors.CLASS_REFERENCE,
            "fn" to KHighlightingColors.FUNCTION_NAME,
            "prop" to KHighlightingColors.PROPERTY_NAME,
            "cname" to KHighlightingColors.CONSTRAINT_NAME,
            "param" to KHighlightingColors.PARAMETER,
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
        |<kw>class</kw> <cls>Shape</cls> {
        |  <prop>sides</prop> : <type>Int</type>
        |  <kw>fun</kw> <fn>area</fn> : <type>Real</type>
        |}
        |
        |<kw>class</kw> <cls>Triangle</cls> <kw>extends</kw> <clsref>Shape</clsref> {
        |  <prop>base</prop> : <type>Int</type>
        |  <prop>height</prop> : <type>Int</type>
        |  
        |  <con>req</con> sides = <num>3</num>
        |  
        |  <kw>fun</kw> <fn>area</fn> : <type>Real</type> {
        |    base * height / <num>2</num>
        |  }
        |  
        |  <prop>name</prop> : <type>String</type> = <str>"triangle"</str>
        |  <prop>isValid</prop> : <type>Bool</type> = <kw>true</kw>
        |  
        |  <con>req</con> <cname>Valid</cname>: base > <num>0</num> && height > <num>0</num>
        |  
        |  <con>soft</con> <cname>Prefer</cname>: area > <num>100</num>
        |  
        |  <con>minimize</con> base + height
        |}
        |
        |<kw>class</kw> <cls>Equilateral</cls> <kw>extends</kw> <clsref>Triangle</clsref> {
        |  <con>req</con> <kw>forall</kw> <param>a</param>, <param>b</param> : sides => a = b
        |}
    """.trimMargin()

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = ADDITIONAL_HIGHLIGHTING

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDisplayName(): String = "K"
}
