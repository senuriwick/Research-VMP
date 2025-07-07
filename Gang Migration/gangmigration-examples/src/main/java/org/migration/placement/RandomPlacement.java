package org.migration.placement;

import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.distributions.ContinuousDistribution;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Places a VM using the Random Fit heuristic by selecting hosts at random and checking their suitability.
 * Attempts placement up to the number of available hosts, skipping the current host.
 */
public class RandomPlacement implements PlacementPolicy {
    private final ContinuousDistribution random;

    public RandomPlacement(ContinuousDistribution random) {
        this.random = random;
    }

    @Override
    public Optional<Host> findHostForVm(Vm vm, List<Host> hostList, Host currentHost,
                                        Map<Host, List<Vm>> pendingAssignments) {
        int maxTries = hostList.size();
        Random rand = new Random();

        for (int i = 0; i < maxTries; i++) {
            int hostIndex = (int) (random.sample() * hostList.size());
            Host candidateHost = hostList.get(hostIndex);

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