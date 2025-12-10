package nasa.jpl.klang.ide.structure

import com.intellij.ide.structureView.*
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import nasa.jpl.klang.ide.KIcons
import nasa.jpl.klang.ide.parser.KClassDefinitionImpl
import nasa.jpl.klang.ide.parser.KFunctionDefinitionImpl
import nasa.jpl.klang.ide.parser.KPropertyDefinitionImpl
import nasa.jpl.klang.ide.parser.KConstraintImpl
import nasa.jpl.klang.ide.psi.KClassDefinition
import nasa.jpl.klang.ide.psi.KFile
import javax.swing.Icon

/**
 * Factory for creating structure view for K files.
 * This enables the Structure tool window (Alt+7 / ⌘+7).
 */
class KStructureViewFactory : PsiStructureViewFactory {
    
    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder? {
        if (psiFile !is KFile) return null
        
        return object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: Editor?): StructureViewModel {
                return KStructureViewModel(psiFile)
            }
        }
    }
}

/**
 * Structure view model for K files
 */
class KStructureViewModel(psiFile: PsiFile) : 
    StructureViewModelBase(psiFile, KFileStructureElement(psiFile)),
    StructureViewModel.ElementInfoProvider {
    
    override fun isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean {
        return element.value is KFile
    }
    
    override fun isAlwaysLeaf(element: StructureViewTreeElement): Boolean {
        val value = element.value
        return value is KPropertyDefinitionImpl || 
               value is KConstraintImpl
    }
}

/**
 * Structure element for the K file root
 */
class KFileStructureElement(private val file: PsiFile) : StructureViewTreeElement {
    
    override fun getValue(): Any = file
    
    override fun getPresentation(): ItemPresentation {
        return object : ItemPresentation {
            override fun getPresentableText(): String = file.name
            override fun getLocationString(): String? = null
            override fun getIcon(unused: Boolean): Icon = KIcons.FILE
        }
    }
    
    override fun getChildren(): Array<TreeElement> {
        if (file !is KFile) return emptyArray()
        
        val classes = PsiTreeUtil.findChildrenOfType(file, KClassDefinitionImpl::class.java)
        return classes.map { KClassStructureElement(it) }.toTypedArray()
    }
    
    override fun navigate(requestFocus: Boolean) {
        file.navigate(requestFocus)
    }
    
    override fun canNavigate(): Boolean = file.canNavigate()
    
    override fun canNavigateToSource(): Boolean = file.canNavigateToSource()
}

/**
 * Structure element for K classes
 */
class KClassStructureElement(private val classDef: KClassDefinitionImpl) : StructureViewTreeElement {
    
    override fun getValue(): Any = classDef
    
    override fun getPresentation(): ItemPresentation {
        return object : ItemPresentation {
            override fun getPresentableText(): String {
                val name = classDef.getClassName() ?: "<unnamed>"
                val superClasses = classDef.getSuperClasses()
                return if (superClasses.isNotEmpty()) {
                    "$name : ${superClasses.joinToString(", ")}"
                } else {
                    name
                }
            }
            override fun getLocationString(): String? = null
            override fun getIcon(unused: Boolean): Icon = KIcons.CLASS
        }
    }
    
    override fun getChildren(): Array<TreeElement> {
        val children = mutableListOf<TreeElement>()
        
        // Properties
        val properties = PsiTreeUtil.findChildrenOfType(classDef, KPropertyDefinitionImpl::class.java)
        children.addAll(properties.map { KPropertyStructureElement(it) })
        
        // Functions
        val functions = PsiTreeUtil.findChildrenOfType(classDef, KFunctionDefinitionImpl::class.java)
        children.addAll(functions.map { KFunctionStructureElement(it) })
        
        // Constraints (named ones)
        val constraints = PsiTreeUtil.findChildrenOfType(classDef, KConstraintImpl::class.java)
        children.addAll(constraints.filter { it.isNamed() }.map { KConstraintStructureElement(it) })
        
        return children.toTypedArray()
    }
    
    override fun navigate(requestFocus: Boolean) {
        classDef.navigate(requestFocus)
    }
    
    override fun canNavigate(): Boolean = classDef.canNavigate()
    
    override fun canNavigateToSource(): Boolean = classDef.canNavigateToSource()
}

/**
 * Structure element for K properties
 */
class KPropertyStructureElement(private val prop: KPropertyDefinitionImpl) : StructureViewTreeElement {
    
    override fun getValue(): Any = prop
    
    override fun getPresentation(): ItemPresentation {
        return object : ItemPresentation {
            override fun getPresentableText(): String {
                val name = prop.getMemberName() ?: "<unnamed>"
                val type = prop.getTypeReference()
                return if (type != null) "$name : $type" else name
            }
            override fun getLocationString(): String? = null
            override fun getIcon(unused: Boolean): Icon = KIcons.PROPERTY
        }
    }
    
    override fun getChildren(): Array<TreeElement> = emptyArray()
    
    override fun navigate(requestFocus: Boolean) {
        prop.navigate(requestFocus)
    }
    
    override fun canNavigate(): Boolean = prop.canNavigate()
    
    override fun canNavigateToSource(): Boolean = prop.canNavigateToSource()
}

/**
 * Structure element for K functions
 */
class KFunctionStructureElement(private val func: KFunctionDefinitionImpl) : StructureViewTreeElement {
    
    override fun getValue(): Any = func
    
    override fun getPresentation(): ItemPresentation {
        return object : ItemPresentation {
            override fun getPresentableText(): String {
                val name = func.getMemberName() ?: "<unnamed>"
                val params = func.getParameters().map { 
                    "${it.getParameterName() ?: "?"}: ${it.getParameterType() ?: "?"}"
                }.joinToString(", ")
                val returnType = func.getReturnType()
                return if (returnType != null) {
                    "$name($params) : $returnType"
                } else {
                    "$name($params)"
                }
            }
            override fun getLocationString(): String? = null
            override fun getIcon(unused: Boolean): Icon = KIcons.FUNCTION
        }
    }
    
    override fun getChildren(): Array<TreeElement> = emptyArray()
    
    override fun navigate(requestFocus: Boolean) {
        func.navigate(requestFocus)
    }
    
    override fun canNavigate(): Boolean = func.canNavigate()
    
    override fun canNavigateToSource(): Boolean = func.canNavigateToSource()
}

/**
 * Structure element for named constraints
 */
class KConstraintStructureElement(private val constraint: KConstraintImpl) : StructureViewTreeElement {
    
    override fun getValue(): Any = constraint
    
    override fun getPresentation(): ItemPresentation {
        return object : ItemPresentation {
            override fun getPresentableText(): String {
                return "req ${constraint.getConstraintName() ?: "<unnamed>"}"
            }
            override fun getLocationString(): String? = null
            override fun getIcon(unused: Boolean): Icon = KIcons.CONSTRAINT
        }
    }
    
    override fun getChildren(): Array<TreeElement> = emptyArray()
    
    override fun navigate(requestFocus: Boolean) {
        constraint.navigate(requestFocus)
    }
    
    override fun canNavigate(): Boolean = constraint.canNavigate()
    
    override fun canNavigateToSource(): Boolean = constraint.canNavigateToSource()
}
