package org.migration.placement;

import org.cloudsimplus.distributions.ContinuousDistribution;

public class PlacementPolicyFactory {
    private final ContinuousDistribution random;

    public PlacementPolicyFactory(ContinuousDistribution random) {
        this.random = random;
    }

    public PlacementPolicy createPolicy(String policyName) {
        switch (policyName.toLowerCase()) {
            case "ff": return new FirstFitPlacement();
            case "bf": return new BestFitPlacement();
            case "wf": return new WorstFitPlacement();
            case "rr": return new RoundRobinPlacement();
            case "pbfd": return new PowerAwareBestFitPlacement();
            case "rand": return new RandomPlacement(random);
            case "brute": return new BruteForcePlacement();
            default: return new FirstFitPlacement();
        }
    }
}