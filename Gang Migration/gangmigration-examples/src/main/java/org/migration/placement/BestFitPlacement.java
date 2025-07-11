package org.migration.placement;

import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.vms.Vm;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Performs Best-Fit placement for a given VM by selecting a suitable host
 * with the least number of free PEs that can still accommodate the VM,
 * excluding the current host and any failed hosts.
 */
public class BestFitPlacement implements PlacementPolicy {
    @Override
    public Optional<Host> findHostForVm(Vm vm, List<Host> hostList, Host currentHost, Map<Host, List<Vm>> pendingAssignments) {
        return hostList.stream()
                .filter(host -> !host.equals(currentHost) && !host.isFailed())
                .filter(host -> {
                    List<Vm> pendingVms = pendingAssignments.getOrDefault(host, List.of());
                    return isHostSuitableForVmWithPending(host, vm, pendingVms);
                })
                .min(Comparator.comparingLong(Host::getFreePesNumber));
    }
}