package nasa.jpl.klang.ide.parser

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import nasa.jpl.klang.ide.psi.*

/**
 * Factory for creating PSI elements from AST nodes.
 */
object KPsiFactory {

    fun createElement(node: ASTNode): PsiElement {
        return when (node.elementType) {
            KElementTypes.CLASS_DEFINITION -> KClassDefinitionImpl(node)
            KElementTypes.ASSOC_DEFINITION -> KAssocDefinitionImpl(node)
            KElementTypes.PROPERTY_DEFINITION -> KPropertyDefinitionImpl(node)
            KElementTypes.FUNCTION_DEFINITION -> KFunctionDefinitionImpl(node)
            KElementTypes.PARAMETER -> KParameterImpl(node)
            KElementTypes.CONSTRAINT -> KConstraintImpl(node)
            KElementTypes.TYPE_REFERENCE -> KTypeReferenceImpl(node)
            KElementTypes.PACKAGE_DECLARATION -> KPackageDeclarationImpl(node)
            KElementTypes.IMPORT_DECLARATION -> KImportDeclarationImpl(node)
            else -> ASTWrapperPsiElement(node)
        }
    }
}

/**
 * Implementation of class definition
 */
class KClassDefinitionImpl(node: ASTNode) : ASTWrapperPsiElement(node), KClassDefinition {

    override fun getClassName(): String? {
        val nameNode = node.findChildByType(KTokenTypes.IDENTIFIER)
        return nameNode?.text
    }

    override fun getName(): String? = getClassName()

    override fun setName(name: String): PsiElement {
        // TODO: Implement rename
        return this
    }

    override fun getNameIdentifier(): PsiElement? {
        return node.findChildByType(KTokenTypes.IDENTIFIER)?.psi
    }

    override fun getSuperClasses(): List<String> {
        val extendsList = PsiTreeUtil.findChildOfType(this, KExtendsListImpl::class.java)
        return extendsList?.getTypeNames() ?: emptyList()
    }

    override fun getMembers(): List<KMember> {
        val properties = PsiTreeUtil.findChildrenOfType(this, KPropertyDefinitionImpl::class.java)
        val functions = PsiTreeUtil.findChildrenOfType(this, KFunctionDefinitionImpl::class.java)
        return properties.toList() + functions.toList()
    }

    override fun getTextOffset(): Int {
        return nameIdentifier?.textOffset ?: super.getTextOffset()
    }
}

/**
 * Implementation of association definition
 */
class KAssocDefinitionImpl(node: ASTNode) : ASTWrapperPsiElement(node), KClassDefinition {

    override fun getClassName(): String? {
        val nameNode = node.findChildByType(KTokenTypes.IDENTIFIER)
        return nameNode?.text
    }

    override fun getName(): String? = getClassName()

    override fun setName(name: String): PsiElement = this

    override fun getNameIdentifier(): PsiElement? {
        return node.findChildByType(KTokenTypes.IDENTIFIER)?.psi
    }

    override fun getSuperClasses(): List<String> = emptyList()

    override fun getMembers(): List<KMember> {
        val properties = PsiTreeUtil.findChildrenOfType(this, KPropertyDefinitionImpl::class.java)
        val functions = PsiTreeUtil.findChildrenOfType(this, KFunctionDefinitionImpl::class.java)
        return properties.toList() + functions.toList()
    }

    override fun getTextOffset(): Int {
        return nameIdentifier?.textOffset ?: super.getTextOffset()
    }
}

/**
 * Implementation of property definition
 */
class KPropertyDefinitionImpl(node: ASTNode) : ASTWrapperPsiElement(node), KPropertyDefinition {

    override fun getMemberName(): String? {
        val nameNode = node.findChildByType(KTokenTypes.IDENTIFIER)
        return nameNode?.text
    }

    override fun getName(): String? = getMemberName()

    override fun setName(name: String): PsiElement = this

    override fun getNameIdentifier(): PsiElement? {
        return node.findChildByType(KTokenTypes.IDENTIFIER)?.psi
    }

    override fun getTypeReference(): String? {
        val typeRef = PsiTreeUtil.findChildOfType(this, KTypeReferenceImpl::class.java)
        return typeRef?.getReferencedTypeName()
    }

    override fun getTextOffset(): Int {
        return nameIdentifier?.textOffset ?: super.getTextOffset()
    }
}

/**
 * Implementation of function definition
 */
class KFunctionDefinitionImpl(node: ASTNode) : ASTWrapperPsiElement(node), KFunctionDefinition {

    override fun getMemberName(): String? {
        val nameNode = node.findChildByType(KTokenTypes.IDENTIFIER)
        return nameNode?.text
    }

    override fun getName(): String? = getMemberName()

    override fun setName(name: String): PsiElement = this

    override fun getNameIdentifier(): PsiElement? {
        return node.findChildByType(KTokenTypes.IDENTIFIER)?.psi
    }

    override fun getTypeReference(): String? = getReturnType()

    override fun getParameters(): List<KParameter> {
        return PsiTreeUtil.findChildrenOfType(this, KParameterImpl::class.java).toList()
    }

    override fun getReturnType(): String? {
        // Find the type reference that's not inside a parameter
        val typeRefs = PsiTreeUtil.findChildrenOfType(this, KTypeReferenceImpl::class.java)
        // Last type reference is usually the return type
        return typeRefs.lastOrNull()?.getReferencedTypeName()
    }

    override fun getTextOffset(): Int {
        return nameIdentifier?.textOffset ?: super.getTextOffset()
    }
}

/**
 * Implementation of parameter
 */
class KParameterImpl(node: ASTNode) : ASTWrapperPsiElement(node), KParameter {

    override fun getParameterName(): String? {
        val nameNode = node.findChildByType(KTokenTypes.IDENTIFIER)
        return nameNode?.text
    }

    override fun getName(): String? = getParameterName()

    override fun setName(name: String): PsiElement = this

    override fun getNameIdentifier(): PsiElement? {
        return node.findChildByType(KTokenTypes.IDENTIFIER)?.psi
    }

    override fun getParameterType(): String? {
        val typeRef = PsiTreeUtil.findChildOfType(this, KTypeReferenceImpl::class.java)
        return typeRef?.getReferencedTypeName()
    }

    override fun getTextOffset(): Int {
        return nameIdentifier?.textOffset ?: super.getTextOffset()
    }
}

/**
 * Implementation of constraint
 */
class KConstraintImpl(node: ASTNode) : ASTWrapperPsiElement(node), KConstraint {

    override fun getConstraintName(): String? {
        // Check if there's a named constraint (name:)
        val children = node.getChildren(null)
        for (i in children.indices) {
            if (children[i].elementType == KTokenTypes.IDENTIFIER) {
                if (i + 1 < children.size && children[i + 1].elementType == KTokenTypes.COLON) {
                    return children[i].text
                }
            }
        }
        return null
    }

    override fun isNamed(): Boolean = getConstraintName() != null
}

/**
 * Implementation of type reference
 */
class KTypeReferenceImpl(node: ASTNode) : ASTWrapperPsiElement(node), KTypeReference {

    override fun getReferencedTypeName(): String? {
        // Get the first identifier or builtin type
        for (child in node.getChildren(null)) {
            when (child.elementType) {
                KTokenTypes.IDENTIFIER,
                KTokenTypes.BOOL_TYPE, KTokenTypes.INT_TYPE, KTokenTypes.REAL_TYPE,
                KTokenTypes.STRING_TYPE, KTokenTypes.CHAR_TYPE, KTokenTypes.UNIT_TYPE,
                KTokenTypes.TIME_TYPE, KTokenTypes.DURATION_TYPE,
                KTokenTypes.SET, KTokenTypes.OSET, KTokenTypes.BAG, KTokenTypes.SEQ -> {
                    return child.text
                }
            }
        }
        return null
    }
}

/**
 * Implementation of package declaration
 */
class KPackageDeclarationImpl(node: ASTNode) : ASTWrapperPsiElement(node), KPackageDeclaration {

    override fun getPackageName(): String? {
        val parts = mutableListOf<String>()
        for (child in node.getChildren(null)) {
            if (child.elementType == KTokenTypes.IDENTIFIER) {
                parts.add(child.text)
            }
        }
        return if (parts.isNotEmpty()) parts.joinToString(".") else null
    }
}

/**
 * Implementation of import declaration
 */
class KImportDeclarationImpl(node: ASTNode) : ASTWrapperPsiElement(node), KImportDeclaration {

    override fun getImportedName(): String? {
        val parts = mutableListOf<String>()
        for (child in node.getChildren(null)) {
            if (child.elementType == KTokenTypes.IDENTIFIER) {
                parts.add(child.text)
            }
        }
        return if (parts.isNotEmpty()) parts.joinToString(".") else null
    }
}

/**
 * Implementation of extends list
 */
class KExtendsListImpl(node: ASTNode) : ASTWrapperPsiElement(node) {

    fun getTypeNames(): List<String> {
        val typeRefs = PsiTreeUtil.findChildrenOfType(this, KTypeReferenceImpl::class.java)
        return typeRefs.mapNotNull { it.getReferencedTypeName() }
    }
}
