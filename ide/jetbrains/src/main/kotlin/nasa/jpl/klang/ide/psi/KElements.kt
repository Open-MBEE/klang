package nasa.jpl.klang.ide.psi

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement

/**
 * Base interface for K named elements (classes, functions, properties)
 */
interface KNamedElement : PsiNamedElement, PsiNameIdentifierOwner

/**
 * Represents a class definition in K
 */
interface KClassDefinition : KNamedElement {
    fun getClassName(): String?
    fun getSuperClasses(): List<String>
    fun getMembers(): List<KMember>
}

/**
 * Represents a member inside a class (property or function)
 */
interface KMember : KNamedElement {
    fun getMemberName(): String?
    fun getTypeReference(): String?
}

/**
 * Represents a property definition
 */
interface KPropertyDefinition : KMember

/**
 * Represents a function definition
 */
interface KFunctionDefinition : KMember {
    fun getParameters(): List<KParameter>
    fun getReturnType(): String?
}

/**
 * Represents a function parameter
 */
interface KParameter : KNamedElement {
    fun getParameterName(): String?
    fun getParameterType(): String?
}

/**
 * Represents a package declaration
 */
interface KPackageDeclaration : PsiElement {
    fun getPackageName(): String?
}

/**
 * Represents an import declaration
 */
interface KImportDeclaration : PsiElement {
    fun getImportedName(): String?
}

/**
 * Represents a constraint (req statement)
 */
interface KConstraint : PsiElement {
    fun getConstraintName(): String?
    fun isNamed(): Boolean
}

/**
 * Represents a named constraint definition
 */
interface KConstraintDefinition : KNamedElement {
    fun getConstraintName(): String?
}

/**
 * Represents a type reference
 */
interface KTypeReference : PsiElement {
    fun getReferencedTypeName(): String?
}

/**
 * Represents an identifier
 */
interface KIdentifier : PsiElement

