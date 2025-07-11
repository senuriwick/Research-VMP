package org.migration.model;

import org.cloudsimplus.vms.Vm;
import java.util.*;

/**
 * Represents a graph-based model for managing dependencies between VMs.
 * This class allows setting and querying directional relationships between VMs,
 * where one VM (dependent) relies on another VM (prerequisite) for correct operation.
 *
 * The graph maintains both forward (prerequisite) and backward (dependent) mappings
 * to enable fast queries in either direction.
 */
public class VmDependencyGraph {
    private final Map<Vm, List<Vm>> prerequisites = new HashMap<>();
    private final Map<Vm, List<Vm>> dependents = new HashMap<>();

    public void addDependency(Vm dependent, Vm prerequisite){
        prerequisites
                .computeIfAbsent(dependent, k -> new ArrayList<>())
                .add(prerequisite);

        dependents
                .computeIfAbsent(prerequisite, k -> new ArrayList<>())
                .add(dependent);
    }

    public List<Vm> getPrerequisites(Vm vm){
        return prerequisites.getOrDefault(vm, Collections.emptyList());
    }

    public List<Vm> getDependents(Vm vm){
        return dependents.getOrDefault(vm, Collections.emptyList());
    }

    public boolean hasDependencies(Vm vm) {
        return !getPrerequisites(vm).isEmpty() || !getDependents(vm).isEmpty();
    }
}
