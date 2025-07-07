package org.migration.placement;

import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.vms.Vm;

import java.util.*;
/**
 * Places a VM using a brute-force strategy by evaluating all possible hosts and selecting the one
 * that yields the highest CPU utilization after placing the VM. This simulates adding the VM to
 * each host’s current and pending VMs to determine the best fit for maximizing resource usage.
 */
public class BruteForcePlacement implements PlacementPolicy {

    @Override
    public Optional<Host> findHostForVm(Vm vm, List<Host> hostList, Host currentHost,
                                        Map<Host, List<Vm>> pendingAssignments) {
        Host bestHost = null;
        double bestUtilization = -1;

        for (Host host : hostList) {
            if (host.equals(currentHost)) {
                continue;
            }

            List<Vm> pendingVms = new ArrayList<>(pendingAssignments.getOrDefault(host, List.of()));
            pendingVms.add(vm);  // simulate adding this VM

            if (isHostSuitableForVmWithPending(host, vm, pendingVms)) {
                double totalMips = host.getTotalMipsCapacity();
                double usedMips = host.getVmList().stream()
                        .mapToDouble(Vm::getTotalCpuMipsRequested)
                        .sum();
                usedMips += pendingVms.stream()
                        .mapToDouble(Vm::getTotalCpuMipsRequested)
                        .sum();

                double utilization = totalMips > 0 ? usedMips / totalMips : 0;
                if (utilization > bestUtilization) {
                    bestUtilization = utilization;
                    bestHost = host;
                }
            }
        }
        return Optional.ofNullable(bestHost);
    }
}