package org.migration.placement;

import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.vms.Vm;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Places a VM using the Worst Fit (WF) heuristic by selecting the most underutilized suitable host.
 * This approach prefers active hosts with the most available processing elements (PEs).
 * Skips the current host of the VM and logs the placement time.
 */
public class WorstFitPlacement implements PlacementPolicy {
    @Override
    public Optional<Host> findHostForVm(Vm vm, List<Host> hostList, Host currentHost, Map<Host, List<Vm>> pendingAssignments) {
        return hostList.stream()
                .filter(host -> {
                    List<Vm> pending = pendingAssignments.getOrDefault(host, List.of());
                    return isHostSuitableForVmWithPending(host, vm, pending);
                })
                .max(Comparator.comparing(Host::isActive).reversed()
                        .thenComparingLong(Host::getFreePesNumber));
    }
}