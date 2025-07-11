package org.migration.placement;

import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.vms.Vm;

import java.util.*;

public interface PlacementPolicy {
    Optional<Host> findHostForVm(Vm vm, List<Host> hostList, Host currentHost, Map<Host, List<Vm>> pendingAssignments);

    default boolean isHostSuitableForVmWithPending(Host host, Vm vm, List<Vm> pendingVms) {
        if (host.isFailed() || !host.isSuitableForVm(vm)) {
            return false;
        }

        // Get CURRENT available resources
        double availableMips = host.getTotalAvailableMips();
        long availableRam = host.getRam().getAvailableResource();
        long availableBw = host.getBw().getAvailableResource();
        long availableStorage = host.getStorage().getAvailableResource();
        int availablePes = host.getFreePesNumber();

        // Subtract requirements for new VM + pending VMs
        availableMips -= vm.getTotalCpuMipsRequested();
        availableRam -= vm.getRam().getCapacity();
        availableBw -= vm.getBw().getCapacity();
        availableStorage -= vm.getStorage().getCapacity();
        availablePes -= vm.getPesNumber();

        for (Vm pending : pendingVms) {
            availableMips -= pending.getTotalCpuMipsRequested();
            availableRam -= pending.getRam().getCapacity();
            availableBw -= pending.getBw().getCapacity();
            availableStorage -= pending.getStorage().getCapacity();
            availablePes -= pending.getPesNumber();
        }

        // Add 10% buffer for migration overhead
        availableMips -= host.getTotalMipsCapacity() * 0.10;
        availableRam -= host.getRam().getCapacity() * 0.10;

        return availableMips >= 0 &&
                availableRam >= 0 &&
                availableBw >= 0 &&
                availableStorage >= 0 &&
                availablePes >= 0;
    }
}