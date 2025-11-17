package gov.nasa.jpl.mbee.util;

import java.lang.reflect.Field;

/**
 * Stub implementation of ClassUtils to replace the missing mbee_util dependency.
 * This provides minimal functionality needed by TypeChecker.scala
 */
public class ClassUtils {
    
    /**
     * Get a field from a class, optionally including inherited fields.
     */
    public static Field getField(Class<?> cls, String name, boolean includeInherited) {
        if (cls == null || name == null) {
            return null;
        }
        
        try {
            // First try the class itself
            Field field = cls.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            // If not found and includeInherited, try superclass
            if (includeInherited) {
                Class<?> superClass = cls.getSuperclass();
                if (superClass != null) {
                    return getField(superClass, name, true);
                }
            }
            return null;
        }
    }
    
    /**
     * Check if a string is a valid package name.
     */
    public static boolean isPackageName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        // Basic validation: package names should contain only letters, digits, dots, and underscores
        // and should not start/end with a dot
        return name.matches("^[a-zA-Z_][a-zA-Z0-9_.]*[a-zA-Z0-9_]$|^[a-zA-Z_]$");
    }
    
    /**
     * Get a class by name, returning null if not found.
     */
    public static Class<?> classForName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}

