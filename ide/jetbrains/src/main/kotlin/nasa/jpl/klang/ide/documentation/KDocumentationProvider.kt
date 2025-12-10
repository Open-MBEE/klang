package nasa.jpl.klang.ide.documentation

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import nasa.jpl.klang.ide.parser.*
import nasa.jpl.klang.ide.psi.KTokenTypes

/**
 * Provides documentation popups on hover for K language elements.
 * Shows type information, signatures, and doc comments.
 */
class KDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        if (element == null) return null
        
        return when (element) {
            is KClassDefinitionImpl -> generateClassDoc(element)
            is KFunctionDefinitionImpl -> generateFunctionDoc(element)
            is KPropertyDefinitionImpl -> generatePropertyDoc(element)
            is KParameterImpl -> generateParameterDoc(element)
            is KConstraintImpl -> generateConstraintDoc(element)
            else -> null
        }
    }

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        if (element == null) return null
        
        return when (element) {
            is KClassDefinitionImpl -> {
                val name = element.getClassName() ?: "?"
                val supers = element.getSuperClasses()
                if (supers.isNotEmpty()) {
                    "class $name extends ${supers.joinToString(", ")}"
                } else {
                    "class $name"
                }
            }
            is KFunctionDefinitionImpl -> {
                val name = element.getMemberName() ?: "?"
                val params = element.getParameters().map { 
                    "${it.getParameterName() ?: "?"}: ${it.getParameterType() ?: "?"}"
                }.joinToString(", ")
                val ret = element.getReturnType()
                if (ret != null) "fun $name($params) : $ret" else "fun $name($params)"
            }
            is KPropertyDefinitionImpl -> {
                val name = element.getMemberName() ?: "?"
                val type = element.getTypeReference()
                if (type != null) "$name : $type" else name
            }
            is KParameterImpl -> {
                val name = element.getParameterName() ?: "?"
                val type = element.getParameterType()
                if (type != null) "$name : $type" else name
            }
            else -> null
        }
    }

    private fun generateClassDoc(classDef: KClassDefinitionImpl): String {
        val sb = StringBuilder()
        
        // Definition section
        sb.append(DocumentationMarkup.DEFINITION_START)
        sb.append("<b>class</b> ${classDef.getClassName() ?: "?"}")
        val supers = classDef.getSuperClasses()
        if (supers.isNotEmpty()) {
            sb.append(" <b>extends</b> ${supers.joinToString(", ")}")
        }
        sb.append(DocumentationMarkup.DEFINITION_END)
        
        // Doc comment section
        val docComment = extractDocComment(classDef)
        if (docComment != null) {
            sb.append(DocumentationMarkup.CONTENT_START)
            sb.append("<p>$docComment</p>")
            sb.append(DocumentationMarkup.CONTENT_END)
        }

        // Content section - members
        val members = classDef.getMembers()
        if (members.isNotEmpty()) {
            sb.append(DocumentationMarkup.CONTENT_START)
            sb.append("<p><b>Members:</b></p>")
            sb.append("<ul>")
            for (member in members) {
                when (member) {
                    is KPropertyDefinitionImpl -> {
                        val name = member.getMemberName() ?: "?"
                        val type = member.getTypeReference()
                        if (type != null) {
                            sb.append("<li>$name : $type</li>")
                        } else {
                            sb.append("<li>$name</li>")
                        }
                    }
                    is KFunctionDefinitionImpl -> {
                        val name = member.getMemberName() ?: "?"
                        val ret = member.getReturnType()
                        sb.append("<li>fun $name() : ${ret ?: "?"}</li>")
                    }
                }
            }
            sb.append("</ul>")
            sb.append(DocumentationMarkup.CONTENT_END)
        }
        
        // Constraints
        val constraints = PsiTreeUtil.findChildrenOfType(classDef, KConstraintImpl::class.java)
        if (constraints.isNotEmpty()) {
            sb.append(DocumentationMarkup.SECTIONS_START)
            sb.append(DocumentationMarkup.SECTION_HEADER_START)
            sb.append("Constraints:")
            sb.append(DocumentationMarkup.SECTION_SEPARATOR)
            sb.append("<ul>")
            for (constraint in constraints) {
                val name = constraint.getConstraintName()
                if (name != null) {
                    sb.append("<li><b>$name</b></li>")
                }
            }
            sb.append("</ul>")
            sb.append(DocumentationMarkup.SECTION_END)
            sb.append(DocumentationMarkup.SECTIONS_END)
        }
        
        return sb.toString()
    }

    private fun generateFunctionDoc(func: KFunctionDefinitionImpl): String {
        val sb = StringBuilder()
        
        // Definition
        sb.append(DocumentationMarkup.DEFINITION_START)
        val name = func.getMemberName() ?: "?"
        val params = func.getParameters().map { 
            "${it.getParameterName() ?: "?"}: ${it.getParameterType() ?: "?"}"
        }.joinToString(", ")
        val ret = func.getReturnType()
        sb.append("<b>fun</b> $name($params)")
        if (ret != null) {
            sb.append(" : $ret")
        }
        sb.append(DocumentationMarkup.DEFINITION_END)
        
        // Doc comment
        val docComment = extractDocComment(func)

        // Content section with doc comment and parameters
        val parameters = func.getParameters()
        if (docComment != null || parameters.isNotEmpty()) {
            sb.append(DocumentationMarkup.CONTENT_START)

            if (docComment != null) {
                sb.append("<p>$docComment</p>")
            }

            if (parameters.isNotEmpty()) {
                sb.append("<p><b>Parameters:</b></p>")
                sb.append("<ul>")
                for (param in parameters) {
                    val pName = param.getParameterName() ?: "?"
                    val pType = param.getParameterType() ?: "?"
                    sb.append("<li><code>$pName</code> : $pType</li>")
                }
                sb.append("</ul>")
            }

            sb.append(DocumentationMarkup.CONTENT_END)
        }
        
        // Return type
        if (ret != null) {
            sb.append(DocumentationMarkup.SECTIONS_START)
            sb.append(DocumentationMarkup.SECTION_HEADER_START)
            sb.append("Returns:")
            sb.append(DocumentationMarkup.SECTION_SEPARATOR)
            sb.append(ret)
            sb.append(DocumentationMarkup.SECTION_END)
            sb.append(DocumentationMarkup.SECTIONS_END)
        }
        
        return sb.toString()
    }

    private fun generatePropertyDoc(prop: KPropertyDefinitionImpl): String {
        val sb = StringBuilder()
        
        sb.append(DocumentationMarkup.DEFINITION_START)
        val name = prop.getMemberName() ?: "?"
        val type = prop.getTypeReference()
        sb.append(name)
        if (type != null) {
            sb.append(" : <b>$type</b>")
        }
        sb.append(DocumentationMarkup.DEFINITION_END)
        
        // Doc comment
        val docComment = extractDocComment(prop)

        // Find containing class
        val containingClass = PsiTreeUtil.getParentOfType(prop, KClassDefinitionImpl::class.java)

        sb.append(DocumentationMarkup.CONTENT_START)
        if (docComment != null) {
            sb.append("<p>$docComment</p>")
        }
        if (containingClass != null) {
            sb.append("<p>Property of class <code>${containingClass.getClassName()}</code></p>")
        }
        sb.append(DocumentationMarkup.CONTENT_END)

        return sb.toString()
    }

    private fun generateParameterDoc(param: KParameterImpl): String {
        val sb = StringBuilder()
        
        sb.append(DocumentationMarkup.DEFINITION_START)
        val name = param.getParameterName() ?: "?"
        val type = param.getParameterType()
        sb.append(name)
        if (type != null) {
            sb.append(" : <b>$type</b>")
        }
        sb.append(DocumentationMarkup.DEFINITION_END)
        
        // Find containing function
        val containingFunc = PsiTreeUtil.getParentOfType(param, KFunctionDefinitionImpl::class.java)
        if (containingFunc != null) {
            sb.append(DocumentationMarkup.CONTENT_START)
            sb.append("<p>Parameter of function <code>${containingFunc.getMemberName()}</code></p>")
            sb.append(DocumentationMarkup.CONTENT_END)
        }
        
        return sb.toString()
    }

    private fun generateConstraintDoc(constraint: KConstraintImpl): String {
        val sb = StringBuilder()
        
        sb.append(DocumentationMarkup.DEFINITION_START)
        val name = constraint.getConstraintName()
        if (name != null) {
            sb.append("<b>req</b> $name")
        } else {
            sb.append("<b>req</b> (unnamed)")
        }
        sb.append(DocumentationMarkup.DEFINITION_END)
        
        sb.append(DocumentationMarkup.CONTENT_START)
        sb.append("<p>Constraint that must be satisfied by the solver.</p>")
        sb.append(DocumentationMarkup.CONTENT_END)
        
        return sb.toString()
    }

    override fun getDocumentationElementForLookupItem(
        psiManager: com.intellij.psi.PsiManager?,
        `object`: Any?,
        element: PsiElement?
    ): PsiElement? {
        if (`object` is PsiElement) return `object`
        return null
    }

    /**
     * Extract documentation comment from above an element.
     * Supports:
     * - Line comments: --, //
     * - Block comments: /* ... */, === ... ===
     */
    private fun extractDocComment(element: PsiElement): String? {
        val comments = mutableListOf<String>()
        var prev = element.prevSibling

        // Skip whitespace but stop if there's a blank line
        while (prev is PsiWhiteSpace) {
            val text = prev.text
            // If there are multiple newlines, stop - there's a gap
            if (text.count { it == '\n' } > 1) {
                break
            }
            prev = prev.prevSibling
        }

        // Collect consecutive comments going backwards
        while (prev != null) {
            when {
                prev is PsiComment -> {
                    val commentText = extractCommentText(prev.text)
                    if (commentText.isNotBlank()) {
                        comments.add(0, commentText)
                    }
                    prev = prev.prevSibling
                    // Skip whitespace between comments
                    while (prev is PsiWhiteSpace) {
                        val wsText = prev.text
                        // Stop if there's a blank line between comments
                        if (wsText.count { it == '\n' } > 1) {
                            prev = null
                            break
                        }
                        prev = prev.prevSibling
                    }
                }
                else -> break
            }
        }

        return if (comments.isNotEmpty()) {
            comments.joinToString("<br>") { escapeHtml(it) }
        } else {
            null
        }
    }

    /**
     * Strip comment markers from a comment string.
     */
    private fun extractCommentText(comment: String): String {
        return when {
            // Block comment: /* ... */
            comment.startsWith("/*") && comment.endsWith("*/") -> {
                comment.removePrefix("/*").removeSuffix("*/")
                    .lines()
                    .map { it.trim().removePrefix("*").trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString(" ")
            }
            // Line comment: --
            comment.startsWith("--") -> {
                comment.removePrefix("--").trim()
            }
            // Line comment: //
            comment.startsWith("//") -> {
                comment.removePrefix("//").trim()
            }
            // Block comment with equals: ======...======
            comment.startsWith("=") && comment.endsWith("=") && comment.contains("\n") -> {
                // Remove leading/trailing lines of equals
                comment.lines()
                    .filter { !it.matches(Regex("^=+$")) }
                    .joinToString(" ") { it.trim() }
            }
            else -> comment.trim()
        }
    }

    /**
     * Basic HTML escaping for doc comments.
     */
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
