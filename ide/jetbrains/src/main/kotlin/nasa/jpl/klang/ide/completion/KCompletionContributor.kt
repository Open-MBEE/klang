package nasa.jpl.klang.ide.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import nasa.jpl.klang.ide.KLanguage
import nasa.jpl.klang.ide.psi.*

/**
 * Provides code completion for K language files
 */
class KCompletionContributor : CompletionContributor() {

    companion object {
        val KEYWORDS = listOf(
            "package", "import", "class", "assoc", "extends", "fun", "req", "val", "var",
            "type", "annotation", "trait", "if", "then", "else", "forall", "exists",
            "prev", "this", "result", "true", "false", "null", "new", "in", "assert"
        )

        val BUILTIN_TYPES = listOf(
            "Int", "Real", "Bool", "String", "Unit", "Char", "Any", "Nothing",
            "Set", "Bag", "Seq", "Map", "Option", "List", "Tuple"
        )

        val TOP_LEVEL_KEYWORDS = listOf("package", "import", "class", "assoc", "type", "annotation", "trait")
        val CLASS_BODY_KEYWORDS = listOf("fun", "req", "val", "var")

        val BUILTIN_FUNCTIONS = listOf(
            "abs", "min", "max", "sum", "count", "size", "isEmpty", "nonEmpty",
            "head", "tail", "last", "init", "take", "drop", "filter", "map",
            "concat", "flatten", "distinct", "contains", "indexOf", "union"
        )
    }

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withLanguage(KLanguage),
            KCompletionProvider()
        )
    }
}

class KCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val position = parameters.position
        val inClassBody = PsiTreeUtil.getParentOfType(position, KClassDefinition::class.java) != null
        val afterColon = isAfterColon(parameters)
        val afterDot = isAfterDot(parameters)

        when {
            afterDot -> addMemberCompletions(position, result)
            afterColon -> addTypeCompletions(position, result)
            inClassBody -> {
                addKeywordCompletions(KCompletionContributor.CLASS_BODY_KEYWORDS, result)
                addTypeCompletions(position, result)
            }
            else -> addKeywordCompletions(KCompletionContributor.TOP_LEVEL_KEYWORDS, result)
        }

        addBuiltinFunctionCompletions(result)
    }

    private fun isAfterColon(parameters: CompletionParameters): Boolean {
        val text = parameters.position.containingFile.text
        val offset = parameters.offset
        if (offset > 0) {
            return text.substring(0, offset).trimEnd().endsWith(":")
        }
        return false
    }

    private fun isAfterDot(parameters: CompletionParameters): Boolean {
        val text = parameters.position.containingFile.text
        val offset = parameters.offset
        if (offset > 0) {
            return text.substring(0, offset).trimEnd().endsWith(".")
        }
        return false
    }

    private fun addKeywordCompletions(keywords: List<String>, result: CompletionResultSet) {
        for (keyword in keywords) {
            result.addElement(
                LookupElementBuilder.create(keyword)
                    .withIcon(AllIcons.Nodes.Favorite)
                    .withTypeText("keyword")
                    .withBoldness(true)
            )
        }
    }

    private fun addTypeCompletions(position: PsiElement, result: CompletionResultSet) {
        for (type in KCompletionContributor.BUILTIN_TYPES) {
            result.addElement(
                LookupElementBuilder.create(type)
                    .withIcon(AllIcons.Nodes.Class)
                    .withTypeText("built-in type")
            )
        }

        val file = position.containingFile as? KFile ?: return
        for (classElement in PsiTreeUtil.findChildrenOfType(file, KClassDefinition::class.java)) {
            val className = classElement.name ?: continue
            result.addElement(
                LookupElementBuilder.create(className)
                    .withIcon(AllIcons.Nodes.Class)
                    .withTypeText("class")
            )
        }
    }

    private fun addMemberCompletions(position: PsiElement, result: CompletionResultSet) {
        for (func in KCompletionContributor.BUILTIN_FUNCTIONS) {
            result.addElement(
                LookupElementBuilder.create(func)
                    .withIcon(AllIcons.Nodes.Method)
                    .withTypeText("function")
            )
        }
    }

    private fun addBuiltinFunctionCompletions(result: CompletionResultSet) {
        for (func in KCompletionContributor.BUILTIN_FUNCTIONS) {
            result.addElement(
                LookupElementBuilder.create(func)
                    .withIcon(AllIcons.Nodes.Method)
                    .withTypeText("built-in")
                    .withTailText("()", true)
            )
        }
    }
}

