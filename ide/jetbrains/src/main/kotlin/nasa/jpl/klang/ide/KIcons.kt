package nasa.jpl.klang.ide

import com.intellij.openapi.util.IconLoader

/**
 * Icons for K language plugin
 */
object KIcons {
    @JvmField
    val FILE = IconLoader.getIcon("/icons/k-file.svg", KIcons::class.java)

    @JvmField
    val CLASS = IconLoader.getIcon("/icons/k-class.svg", KIcons::class.java)

    @JvmField
    val FUNCTION = IconLoader.getIcon("/icons/k-function.svg", KIcons::class.java)

    @JvmField
    val PROPERTY = IconLoader.getIcon("/icons/k-property.svg", KIcons::class.java)

    @JvmField
    val CONSTRAINT = IconLoader.getIcon("/icons/k-constraint.svg", KIcons::class.java)

    @JvmField
    val CONSTRAINT_SAT = IconLoader.getIcon("/icons/k-constraint-sat.svg", KIcons::class.java)

    @JvmField
    val CONSTRAINT_UNSAT = IconLoader.getIcon("/icons/k-constraint-unsat.svg", KIcons::class.java)
}

