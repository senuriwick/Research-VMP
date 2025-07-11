package org.migration.placement;

import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.power.models.PowerModelHost;
import org.cloudsimplus.vms.Vm;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Places a VM using a Power-Aware Best Fit Decreasing (PBFD) heuristic by selecting the host
 * that results in the minimum increase in power consumption after placement.
 * Skips inactive hosts and the current host of the VM.
 * Considers pending assignments for accurate power impact estimation.
 */
public class PowerAwareBestFitPlacement implements PlacementPolicy {
    @Override
    public Optional<Host> findHostForVm(Vm vm, List<Host> hostList, Host currentHost, Map<Host, List<Vm>> pendingAssignments) {
        Host bestHost = null;
        double bestDeltaPower = Double.MAX_VALUE;

        for (Host host : hostList) {
            if (!host.isActive() || host.equals(currentHost)) continue;

            List<Vm> pending = pendingAssignments.getOrDefault(host, List.of());
            if (!isHostSuitableForVmWithPending(host, vm, pending)) continue;

            double delta = estimateDeltaPower(host, vm, pending);
            if (delta < bestDeltaPower) {
                bestDeltaPower = delta;
                bestHost = host;
            }
        }
        return Optional.ofNullable(bestHost);
    }

    private double estimateDeltaPower(Host host, Vm vm, List<Vm> pending) {
        PowerModelHost pm = (PowerModelHost) host.getPowerModel();
        double utilNow = clamp(host.getCpuMipsUtilization(), 0.0, 1.0);
        double powerNow = pm.getPower(utilNow);

        double usedMips = host.getTotalAllocatedMips()
                + pending.stream()
                .mapToDouble(Vm::getTotalCpuMipsRequested)
                .sum()
                + vm.getTotalCpuMipsRequested();
        double newUtil = clamp(usedMips / host.getTotalMipsCapacity(), 0.0, 1.0);
        double powerAfter = pm.getPower(newUtil);

        return powerAfter - powerNow;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }
}