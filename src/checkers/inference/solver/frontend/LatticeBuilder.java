package checkers.inference.solver.frontend;

import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.AnnotationMirrorMap;
import org.checkerframework.javacutil.AnnotationMirrorSet;
import org.checkerframework.javacutil.AnnotationUtils;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;

import checkers.inference.InferenceMain;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Slot;

public class LatticeBuilder {

    /** subType maps each type qualifier to its sub types. */
    private final Map<AnnotationMirror, Collection<AnnotationMirror>> subType;

    /** superType maps each type qualifier to its super types. */
    private final Map<AnnotationMirror, Collection<AnnotationMirror>> superType;

    /** incomparableType maps each type qualifier to its incomparable types. */
    private final Map<AnnotationMirror, Collection<AnnotationMirror>> incomparableType;

    /**
     * All type qualifiers in underling type system. Requires annotation ordering, so must created
     * with {@code AnnotationUtils.createAnnotationSet()} when constructing from empty set, even if
     * being converted to {@code UnmodifiableSet} later on TODO remove the dependency to TreeSet
     */
    private Set<? extends AnnotationMirror> allTypes;

    /** Top qualifier of underling type system. */
    private AnnotationMirror top;

    /** Bottom type qualifier of underling type system. */
    private AnnotationMirror bottom;

    /** Number of type qualifier in underling type system. */
    private int numTypes;

    /**
     * All concrete qualifiers extracted from slots collected from the program that CF Inference
     * running on. This field is useful for type systems that has a dynamic number of type
     * qualifiers.
     */
    public final Collection<AnnotationMirror> allAnnotations;

    public LatticeBuilder() {
        subType = new AnnotationMirrorMap<>();
        superType = new AnnotationMirrorMap<>();
        incomparableType = new AnnotationMirrorMap<>();
        allAnnotations = new AnnotationMirrorSet();
    }

    /**
     * Build a normal lattice with all fields configured.
     *
     * @param qualHierarchy of underling type system.
     * @return a new Lattice instance.
     */
    public Lattice buildLattice(QualifierHierarchy qualHierarchy, Collection<Slot> slots) {
        clear();

        Set<AnnotationMirror> supportedAnnos = new AnnotationMirrorSet();
        Set<Class<? extends Annotation>> annoClasses =
                InferenceMain.getInstance().getRealTypeFactory().getSupportedTypeQualifiers();
        for (Class<? extends Annotation> ac : annoClasses) {
            supportedAnnos.add(
                    new AnnotationBuilder(
                                    InferenceMain.getInstance()
                                            .getRealTypeFactory()
                                            .getProcessingEnv(),
                                    ac)
                            .build());
        }

        top = qualHierarchy.getTopAnnotations().iterator().next();
        bottom = qualHierarchy.getBottomAnnotations().iterator().next();

        // this is a workaround for "computed" bottoms. e.g. DataFlow bottom
        if (!AnnotationUtils.containsSame(supportedAnnos, bottom)) {
            supportedAnnos.add(bottom);
        }

        allTypes = Collections.unmodifiableSet(supportedAnnos);
        numTypes = supportedAnnos.size();

        // Calculate subtypes map and supertypes map
        for (AnnotationMirror i : allTypes) {
            Set<AnnotationMirror> subtypeOfi = new HashSet<AnnotationMirror>();
            Set<AnnotationMirror> supertypeOfi = new HashSet<AnnotationMirror>();
            for (AnnotationMirror j : allTypes) {
                if (qualHierarchy.isSubtypeQualifiersOnly(j, i)) {
                    subtypeOfi.add(j);
                }
                if (qualHierarchy.isSubtypeQualifiersOnly(i, j)) {
                    supertypeOfi.add(j);
                }
            }
            subType.put(i, subtypeOfi);
            superType.put(i, supertypeOfi);
        }

        // Calculate incomparable types map
        for (AnnotationMirror i : allTypes) {
            Set<AnnotationMirror> incomparableOfi = new HashSet<AnnotationMirror>();
            for (AnnotationMirror j : allTypes) {
                if (!subType.get(i).contains(j) && !subType.get(j).contains(i)) {
                    incomparableOfi.add(j);
                }
            }
            if (!incomparableOfi.isEmpty()) {
                incomparableType.put(i, incomparableOfi);
            }
        }

        collectConstantAnnotationMirrors(slots);

        return new Lattice(
                subType,
                superType,
                incomparableType,
                allTypes,
                top,
                bottom,
                numTypes,
                allAnnotations,
                qualHierarchy);
    }

    /**
     * Build a two-qualifier lattice with all fields configured.
     *
     * @param top type qualifier of underling type system.
     * @param bottom type qualifier of underling type system.
     * @return a new TwoQualifiersLattice instance.
     */
    public TwoQualifiersLattice buildTwoTypeLattice(AnnotationMirror top, AnnotationMirror bottom) {
        clear();
        Set<AnnotationMirror> tempSet = new AnnotationMirrorSet();
        tempSet.add(top);
        tempSet.add(bottom);
        allTypes = Collections.unmodifiableSet(tempSet);
        this.top = top;
        this.bottom = bottom;
        numTypes = 2;

        // Calculate subertypes map and supertypes map.
        Set<AnnotationMirror> topSet = new AnnotationMirrorSet();
        Set<AnnotationMirror> bottomSet = new AnnotationMirrorSet();
        topSet.add(top);
        bottomSet.add(bottom);
        subType.put(top, Collections.unmodifiableSet(allTypes));
        superType.put(top, topSet);
        subType.put(bottom, bottomSet);
        superType.put(bottom, Collections.unmodifiableSet(allTypes));

        // Incomparable map should be empty in two qualifiers lattice.
        incomparableType.clear();

        // TODO: RuntimeAMs information seems only useful for dynamic lattices.
        // Is there a need to extract runtime annotation mirrors for two qualifiers lattice?

        return new TwoQualifiersLattice(
                subType, superType, incomparableType, allTypes, top, bottom, numTypes);
    }

    /**
     * Clear all fields. Will be called when build a new lattice to make sure the old values are
     * gone.
     */
    private void clear() {
        allAnnotations.clear();
        this.subType.clear();
        this.superType.clear();
        this.incomparableType.clear();
        allTypes = null;
        top = null;
        bottom = null;
        numTypes = 0;
    }

    /**
     * Extract annotation mirrors in constant slots of a given collection of slots.
     *
     * @param slots a collection of slots.
     */
    private void collectConstantAnnotationMirrors(Collection<Slot> slots) {
        for (Slot slot : slots) {
            if (slot instanceof ConstantSlot) {
                allAnnotations.add(((ConstantSlot) slot).getValue());
            }
        }
    }
}
