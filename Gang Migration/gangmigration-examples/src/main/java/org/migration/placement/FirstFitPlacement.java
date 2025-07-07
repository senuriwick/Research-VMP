package org.migration.placement;

import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.vms.Vm;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Performs First-Fit VM placement by scanning the list of hosts and assigning the VM
 * to the first host that can accommodate it (ignoring the current host and failed ones).
 */
public class FirstFitPlacement implements PlacementPolicy {
    @Override
    public Optional<Host> findHostForVm(Vm vm, List<Host> hostList, Host currentHost, Map<Host, List<Vm>> pendingAssignments) {
        for (Host candidateHost : hostList) {
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