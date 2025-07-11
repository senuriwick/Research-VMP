package org.migration.placement;

import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.vms.Vm;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Places a VM using the Round Robin (RR) strategy by iterating through hosts in a cyclic manner.
 * Skips the current host of the VM and attempts to find the next suitable host for placement.
 * Updates the round-robin index to ensure fair distribution across hosts.
 */
public class RoundRobinPlacement implements PlacementPolicy {
    private final AtomicInteger index = new AtomicInteger(0);

    @Override
    public Optional<Host> findHostForVm(Vm vm, List<Host> hostList, Host currentHost, Map<Host, List<Vm>> pendingAssignments) {
        int hostCount = hostList.size();
        for (int i = 0; i < hostCount; i++) {
            int currentIndex = index.getAndUpdate(prev -> (prev + 1) % hostCount);
            Host candidateHost = hostList.get(currentIndex);

            if (candidateHost.equals(currentHost)) {
                continue;
            }

            List<Vm> pendingVms = pendingAssignments.getOrDefault(candidateHost, List.of());
            if (isHostSuitableForVmWithPending(candidateHost, vm, pendingVms)) {
                return Optional.of(candidateHost);
            }
        }
        return Optional.empty();
    }
}