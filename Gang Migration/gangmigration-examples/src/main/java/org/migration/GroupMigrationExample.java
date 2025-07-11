package org.migration;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.builders.tables.TextTableColumn;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.*;
import org.cloudsimplus.listeners.EventInfo;
import org.cloudsimplus.listeners.VmHostEventInfo;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmGroup;
import org.cloudsimplus.vms.VmSimple;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyFirstFit;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerSpaceShared;
import org.cloudsimplus.power.models.PowerModelHostSimple;
import org.cloudsimplus.power.models.PowerModelHost;
import org.cloudsimplus.distributions.ContinuousDistribution;
import org.cloudsimplus.distributions.UniformDistr;
import org.migration.model.MigrationData;
import org.migration.placement.*;

import lombok.extern.slf4j.Slf4j;
import org.migration.model.VmDependencyGraph;

import java.util.*;
import java.util.stream.*;

@Slf4j
public class GroupMigrationExample {
    private int HOSTS;
    private static final int GROUPS = 1;
    private int VMS_BY_GROUP;

    private static final int BASE_HOST_RAM = 10_000;
    private static final int BASE_HOST_BW = 10_000;
    private static final int BASE_HOST_STORAGE = 150_000;
    private static final int HOST_MIPS = 2000;

    private static final int VM_PES = 2;
    private static final int VM_RAM = 1200;
    private static final int VM_BW = 1200;
    private static final int VM_STORAGE = 10_000;

    private static final int CLOUDLET_LENGTH = 10_000;

    private static final int SCHEDULING_INTERVAL = 1;

    private static final double MAX_POWER = 100;
    private static final double STATIC_POWER = 50;
    private static final double HOST_STARTUP_POWER = 50;
    private static final double HOST_SHUTDOWN_POWER = 50;

    private final CloudSimPlus simulation;
    private final DatacenterBroker broker0;

    private final List<VmGroup> vmGroupList;
    private final List<Cloudlet> cloudletList;
    private final Datacenter datacenter0;

    private int vmIdCounter = 0;
    private boolean migrationRequested;
    private int migrationsNumber;
    private int lastHostIndex;
    ContinuousDistribution random = new UniformDistr(0, 1);
    private final Map<String, Integer> policyIndices = new HashMap<>();

    /** key = VM id, value = migration start time */
    private final Map<Long, Double> migStart = new HashMap<>();
    /** key = VM id, value = migration finish time */
    private final Map<Long, Double> migFinish = new HashMap<>();

    private final Map<Long, Double> deallocStart = new HashMap<>();
    private final Map<Long, Double> allocFinish = new HashMap<>();

    private List<MigrationData> migrationDataList = new ArrayList<>();
    private Map<Long, Host> previousHostMap = new HashMap<>();

    private double totalMigrationTime = 0;
    private double totalDownTime = 0;
    private double totalSearchTime = 0;

    private final VmDependencyGraph dependencyGraph = new VmDependencyGraph();

    private final PlacementPolicy placementPolicy;
    private final PlacementPolicyFactory policyFactory;

    public GroupMigrationExample(String policy, int hostCount, int vmsByGroup) {
        this.HOSTS = hostCount;
        this.VMS_BY_GROUP = vmsByGroup;

        // Initialize policy factory
        this.policyFactory = new PlacementPolicyFactory(random);
        this.placementPolicy = policyFactory.createPolicy(policy);

        System.out.println("Starting " + getClass().getSimpleName() + " with policy: " + policy);
        simulation = new CloudSimPlus();

        datacenter0 = createDatacenterAdvance();
        broker0 = new DatacenterBrokerSimple(simulation);

        cloudletList = new ArrayList<>();
        vmGroupList = createVmGroupList();
        for (VmGroup group : vmGroupList) {
            createCloudlets(group);
        }

        //You can submit either a List of Vm or a List of VmGroup.
        broker0.submitVmList(vmGroupList);
        broker0.submitCloudletList(cloudletList);

        simulation.addOnClockTickListener(this::clockTickListener);

        simulation.start();

        final var cloudletFinishedList = broker0.getCloudletFinishedList();
        cloudletFinishedList.sort(Comparator.comparingLong(cl -> cl.getVm().getId()));
        new CloudletsTableBuilder(cloudletFinishedList)
                .addColumn(new TextTableColumn("      VmGroup"), cl -> cl.getVm().getGroup(), 7)
                .build();

        printMigrationSummary();
        final double avgDowntime = totalDownTime / migrationDataList.size();
        printMigrationMetrics(avgDowntime);
        printAlgorithmPerformance();

        System.out.println(getClass().getSimpleName() + " finished!");
    }

    private void clockTickListener(EventInfo info) {
        if (!migrationRequested && info.getTime() >= 5) {
            migrateVmGroupByDestination(vmGroupList.get(0));
            this.migrationRequested = true;
        }
    }

    private Datacenter createDatacenterAdvance() {
        final var hostList = new ArrayList<Host>(HOSTS);
        for (int i = 1; i <= HOSTS; i++) {
            final var host = createHost(i, i+VMS_BY_GROUP*4);
            hostList.add(host);
        }

        final var dc = new DatacenterAdvance(simulation, hostList, new VmAllocationPolicyFirstFit());
        dc.setSchedulingInterval(SCHEDULING_INTERVAL);
        return dc;
    }

    private Host createHost(final long id, final int pes) {
        final var peList = new ArrayList<Pe>(pes);

        for (int i = 0; i < pes; i++) {
            peList.add(new PeSimple(HOST_MIPS));
        }

        final Random rand = new Random();

        //Host resources will be defined increasingly, according to the Host id.
        final long ram = BASE_HOST_RAM * id; //in Megabytes
        final long bw = BASE_HOST_BW * id; //in Megabits/s
        final long storage = BASE_HOST_STORAGE * id; //in Megabytes

        final Host host = new HostSimple(ram, bw, storage, peList);
        host.setVmScheduler(new VmSchedulerSpaceShared())
                .setStateHistoryEnabled(true);
        host.setId(id);
        PowerModelHost powerModel = new PowerModelHostSimple(MAX_POWER, STATIC_POWER)
                .setStartupPower(HOST_STARTUP_POWER)
                .setShutDownPower(HOST_SHUTDOWN_POWER);
        host.setPowerModel(powerModel);
        return host;
    }

    private List<VmGroup> createVmGroupList() {
        final var vmGroupList = new ArrayList<VmGroup>(GROUPS);
        for (int i = 0; i < GROUPS; i++) {
            vmGroupList.add(new VmGroup(createVms()));
        }

        return vmGroupList;
    }

    private List<Vm> createVms() {
        final var vmList = new ArrayList<Vm>(VMS_BY_GROUP);
        for (int i = 0; i < VMS_BY_GROUP; i++) {
            final Vm vm = new VmSimple(HOST_MIPS, VM_PES);
            vm.setId(vmIdCounter++);
            vm.setRam(VM_RAM).setBw(VM_BW).setSize(VM_STORAGE);
            vmList.add(vm);
        }

        vmList.forEach(vm -> vm.addOnMigrationStartListener(this::startMigration));
        vmList.forEach(vm -> vm.addOnMigrationFinishListener(this::finishMigration));
//        vmList.forEach(vm -> vm.addOnHostDeallocationListener(this::vmDeallocation));
//        vmList.forEach(vm -> vm.addOnHostAllocationListener(this::vmAllocation));

        // Let's say vm 1 depends on vm 0, and vm 3 depends on vm 2
        dependencyGraph.addDependency(vmList.get(1), vmList.get(0));
        dependencyGraph.addDependency(vmList.get(3), vmList.get(2));
        return vmList;
    }

    private void startMigration(final VmHostEventInfo info) {
        Host sourceHost = previousHostMap.get(info.getVm().getId());
        Host targetHost = info.getHost();
        migStart.put(info.getVm().getId(), simulation.clock());
    }

    private void finishMigration(final VmHostEventInfo info) {
        long   id  = info.getVm().getId();
        Host previousHost = previousHostMap.get(id);

        migFinish.put(id, simulation.clock());
        double duration = (migFinish.get(id) - migStart.get(id)) * 1000;

        totalMigrationTime = Math.max(duration, totalMigrationTime);
//        totalDownTime += vmAllocation(info);
        migrationsNumber++;

        removeVmFromGroup(info.getVm());

        // Create and store migration data
        MigrationData data = new MigrationData(
                info.getVm(),
                previousHost,
                info.getHost(),
                duration
//                vmAllocation(info)
        );

        migrationDataList.add(data);

        System.out.printf("# %.2f ms: %s finished migrating to %s (%.2f ms)%n",
                info.getTime(), info.getVm(), info.getHost(), duration);
    }

    private void vmDeallocation(final VmHostEventInfo info) {
        deallocStart.put(info.getVm().getId(), info.getTime());
    }

    private double vmAllocation(final VmHostEventInfo info) {
        allocFinish.put(info.getVm().getId(), info.getTime());

        Double startTime = deallocStart.get(info.getVm().getId());
        Double finishTime = allocFinish.get(info.getVm().getId());

        if (startTime == null || finishTime == null) {
            return 0.0;
        }

        return (finishTime - startTime) * 1000;
    }

    private void createCloudlets(final VmGroup group) {
        //UtilizationModel defining the Cloudlets use only 10% of RAM and BW all the time
        final var utilizationModelRamBw = new UtilizationModelDynamic(0.1);
        final var utilizationModelCpu = new UtilizationModelFull();

        for (Vm vm : group.getVmList()) {
            final var cloudlet = new CloudletSimple(CLOUDLET_LENGTH, VM_PES);
            cloudlet.setSizes(1024)
                    .setUtilizationModelCpu(utilizationModelCpu)
                    .setUtilizationModelRam(utilizationModelRamBw)
                    .setUtilizationModelBw(utilizationModelRamBw)
                    .setVm(vm);
            cloudletList.add(cloudlet);
        }
    }

    /**
     * Performs VM migration by grouping VMs according to their destination hosts using a greedy batch processing approach.
     * Attempts to assign each VM to a suitable host and migrates groups of VMs together to optimize migration efficiency.
     * Logs unassigned VMs that could not be placed on any host.
     *
     * @param originalGroup the original group of VMs to be migrated.
     */
    private void migrateVmGroupByDestination(VmGroup originalGroup) {
        System.out.println("\n--- Grouping VMs by their target Host ---");

        Map<Host, List<Vm>> pendingAssignments = new HashMap<>();
        List<Vm> failedToAssign = new ArrayList<>();

        for (Vm vm : originalGroup.getVmList()) {
            previousHostMap.put(vm.getId(), vm.getHost());
            dispatchVmPlacement(vm, pendingAssignments, failedToAssign);
        }

        // Migrate grouped VMs to their assigned destinations
        int groupCounter = vmGroupList.size() + 1;
        for (Map.Entry<Host, List<Vm>> entry : pendingAssignments.entrySet()) {
            Host destination = entry.getKey();
            List<Vm> vms = entry.getValue();

            VmGroup newGroup = new VmGroup(vms);
            newGroup.setId(groupCounter);
            vmGroupList.add(newGroup);

            System.out.printf("→ Migrating %d VMs to Host %d in one batch (Group %d)%n",
                    vms.size(), destination.getId(), groupCounter);

            ((DatacenterAdvance) datacenter0).requestVmMigration(newGroup, destination);
            groupCounter++;
        }

        if (!failedToAssign.isEmpty()) {
            System.out.println("⚠️ VMs that could not be assigned:");
            for (Vm vm : failedToAssign) {
                System.out.println(" - VM " + vm.getId());
            }
        }
    }

    /**
     * Dispatches the VM placement request to the appropriate placement algorithm
     * based on the configured placement policy.
     * Defaults to First Fit if the policy is unknown.
     *
     * @param vm                 the VM to be placed.
     * @param pendingAssignments a map holding VMs tentatively assigned to each host during the current decision cycle.
     * @param failedToAssign     a list where VMs are added if no suitable host is found.
     */
    private void dispatchVmPlacement(Vm vm, Map<Host, List<Vm>> pendingAssignments, List<Vm> failedToAssign) {
        long startTime = System.nanoTime();
        Host currentHost = vm.getHost();

        Optional<Host> selectedHost = placementPolicy.findHostForVm(
                vm,
                datacenter0.getHostList(),
                currentHost,
                pendingAssignments
        );

        if (selectedHost.isPresent()) {
            Host host = selectedHost.get();
            pendingAssignments.computeIfAbsent(host, h -> new ArrayList<>()).add(vm);
            logPlacementTime(vm, currentHost, host, startTime, placementPolicy.toString());
        } else {
            System.out.printf("No suitable host found for VM %d%n", vm.getId());
            failedToAssign.add(vm);
        }
    }

    /**
     * Computes the average CPU utilization across all hosts in the data center.
     * Uses weighted averaging over the time intervals recorded in each host's CPU usage history.
     * Returns a value between 0 and 1 representing the average utilization.
     *
     * @return the average CPU utilization of all hosts weighted by the duration of each usage interval.
     */
    private double computeAverageCpuUtilisation() {
        double weightedSum = 0, totalTime = 0;

        for (Host h : datacenter0.getHostList()) {
            var history = h.getStateHistory();
            for (int i = 1; i < history.size(); i++) {
                var prev = history.get(i - 1);
                var curr = history.get(i);
                double interval = curr.time() - prev.time();
                weightedSum += prev.percentUsage() * interval;
                totalTime   += interval;
            }
        }
        return totalTime == 0 ? 0 : weightedSum / totalTime;   // value in [0,1]
    }

    private void removeVmFromGroup(Vm vm) {
        for (VmGroup group : vmGroupList) {
            if (group.getVmList().remove(vm)) {
                System.out.printf("VM %d removed from Group %d after migration%n", vm.getId(), group.getId());

                // Clean up empty groups
                if (group.getVmList().isEmpty()) {
                    vmGroupList.remove(group);
                    System.out.printf("Group %d removed as it became empty%n", group.getId());
                }
                return;
            }
        }
    }

    public int getMigrationsNumber() {
        return migrationsNumber;
    }

    public double getTotalDowntime() {
        return totalDownTime;
    }

    public double getTotalMigrationTime() {
        return totalMigrationTime;
    }

    public double getAverageDowntime() {
        return migrationDataList.isEmpty() ? 0 : totalDownTime / migrationDataList.size();
    }

    public double getTotalSearchTime() {
        return totalSearchTime;
    }

    public double getAverageSearchTime() {
        return (totalSearchTime) / migrationDataList.size();
    }

    private void logPlacementTime(Vm vm, Host currentHost, Host targetHost, double startTime, String policyName) {
        double elapsedTime = (System.nanoTime() - startTime) / 1_000_000.0;
        totalSearchTime += elapsedTime;
        System.out.printf("VM %d currentHost: %d -> newHost (%s): %d (Time: %.4f ms)%n",
                vm.getId(),
                currentHost != null ? currentHost.getId() : -1,
                policyName,
                targetHost.getId(),
                elapsedTime);
    }

    private Host getAssignedHost(Vm vm, Map<Host, List<Vm>> assignments) {
        return assignments.entrySet().stream()
                .filter(e -> e.getValue().contains(vm))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(vm.getHost());
    }

    public void printMigrationSummary() {
        System.out.println("\n                            MIGRATION SUMMARY                                ");
        System.out.printf("| %-5s | %-10s | %-10s | %-10s |%n",
                "VM ID", "Source Host", "Target Host", "Migration Time (ms)");
        System.out.println("|-------|-------------|-------------|---------------------|");

        for (MigrationData data : migrationDataList) {
            System.out.printf("| %-5s | %-11s | %-11s | %-19.4f |%n",
                    data.getVm().getId(), data.getSourceHost().getId(), data.getTargetHost().getId(),
                    data.getMigrationTime());
        }
    }

    /**
     * Prints performance metrics for the placement algorithm
     */
    public void printAlgorithmPerformance() {
        // Calculate average time safely (handle division by zero)
        double avgTime = migrationDataList.isEmpty() ? 0 : totalSearchTime / migrationDataList.size();

        String policyName = placementPolicy.getClass().getSimpleName()
                .replace("Placement", "")
                .toUpperCase();

        System.out.printf("%n%20s ALGORITHM PERFORMANCE (%s policy) %20s%n", "", policyName, "");
        System.out.println("+--------------------------------+-----------------+");
        System.out.printf("| %-30s | %-15s |%n", "METRIC", "VALUE");
        System.out.println("+--------------------------------+-----------------+");
        System.out.printf("| %-30s | %-12f ms |%n", "Total placement time", totalSearchTime);
        System.out.printf("| %-30s | %-12f ms |%n", "Average placement time", avgTime);
        System.out.printf("| %-30s | %-13d   |%n", "Placement attempts", migrationDataList.size());
        System.out.println("+--------------------------------+-----------------+");
        System.out.println();
    }

    /**
     * Prints migration-related metrics
     * @param avgDowntime average downtime (currently unused but kept for future use)
     */
    public void printMigrationMetrics(double avgDowntime) {
        double avgUtil = computeAverageCpuUtilisation();
        double avgMigrationTime = migrationsNumber == 0 ? 0 : totalMigrationTime / migrationsNumber;

        String policyName = placementPolicy.getClass().getSimpleName()
                .replace("Placement", "")
                .toUpperCase();

        System.out.printf("%n%20s MIGRATION METRICS (%s policy) %20s%n", "", policyName, "");
        System.out.println("+--------------------------------+-----------------+");
        System.out.printf("| %-30s | %-15s |%n", "METRIC", "VALUE");
        System.out.println("+--------------------------------+-----------------+");
        System.out.printf("| %-30s | %-13d   |%n", "Total migrations", migrationsNumber);
        System.out.printf("| %-30s | %-12f ms |%n", "Total migration time", totalMigrationTime);
        System.out.printf("| %-30s | %-13f %% |%n", "Avg host CPU utilization", avgUtil * 100);
        System.out.println("+--------------------------------+-----------------+");
    }
}