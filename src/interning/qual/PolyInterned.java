package interning.qual;

import org.checkerframework.framework.qual.PolymorphicQualifier;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A polymorphic qualifier for the Interning type system.
 *
 * <p>Any method written using @PolyInterned conceptually has two versions: one in which every
 * instance of @PolyInterned has been replaced by @Interned, and one in which every instance
 * of @PolyInterned has been erased.
 *
 * @checker_framework.manual #interning-checker Interning Checker
 */
@PolymorphicQualifier(UnknownInterned.class)
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface PolyInterned {}
