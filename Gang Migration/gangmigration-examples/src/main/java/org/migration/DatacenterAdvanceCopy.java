package org.migration;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.core.CloudSimTag;
import org.cloudsimplus.core.Simulation;
import org.cloudsimplus.core.events.PredicateType;
import org.cloudsimplus.core.events.SimEvent;
import org.cloudsimplus.datacenters.DatacenterCharacteristics;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSuitability;
import org.cloudsimplus.listeners.DatacenterVmMigrationEventInfo;
import org.cloudsimplus.resources.DatacenterStorage;
import org.cloudsimplus.util.InvalidEventDataTypeException;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmAbstract;
import org.cloudsimplus.vms.VmGroup;
import org.cloudsimplus.listeners.EventListener;

import java.util.*;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

import static org.cloudsimplus.datacenters.DatacenterCharacteristics.Distribution.PRIVATE;
import static org.cloudsimplus.util.BytesConversion.bitsToBytes;

/**
 * A custom Datacenter that can accept a VmGroup + Host in one call
 * and fan out each Vm’s migration “all at once.”
 * In CloudSimPlus, the method requestVmMigration(Vm, Host) lives on DatacenterSimple,
 * so we subclass DatacenterSimple to add a batch version that takes a VmGroup.
 */
@Slf4j
public class DatacenterAdvanceCopy extends DatacenterSimple {
    private static final int VM_MIGRATE_BATCH = 1001;

    private VmAllocationPolicy vmAllocationPolicy;
    private final List<EventListener<DatacenterVmMigrationEventInfo>> onVmMigrationFinishListeners = new ArrayList<>();

    public DatacenterAdvanceCopy(final CloudSimPlus simulation, final List<Host> hostList) {
        super(simulation, hostList);
    }

    public DatacenterAdvanceCopy(Simulation simulation, List<? extends Host> hostList, VmAllocationPolicy vmAllocationPolicy) {
        super(simulation, hostList, vmAllocationPolicy, new DatacenterStorage());
        this.vmAllocationPolicy = vmAllocationPolicy;
    }

    public void requestVmMigration(final VmGroup sourceVmGroup, final Host targetHost) {
        final var sourceHost = sourceVmGroup.getVmList().get(0).getHost();
        double delay = timeToMigrateVmGroup(sourceVmGroup, targetHost);
        final boolean nonLiveMigration = delay < 0;
        final var migrationType = nonLiveMigration ? "Non-live Migration (from/to a public-cloud datacenter)" : "Live Migration (across private-cloud datacenters)";

        delay = Math.abs(delay);
        final String msg1 =
                Host.NULL.equals(sourceHost) ?
                        "%s to %s".formatted(sourceVmGroup.toString(), targetHost) :
                        "%s from %s to %s".formatted(sourceVmGroup.toString(), sourceHost, targetHost);

        final String currentTime = getSimulation().clockStr();
        final var fmt = "It's expected to finish in %.2f seconds, considering the %.0f%% of bandwidth allowed for migration and the VM %s.";
        final var vmResource = nonLiveMigration ? "disk size" : "allocated RAM";
        final String msg2 = fmt.formatted(delay, getBandwidthPercentForMigration()*100, vmResource);
        LOGGER.info("{}: {}: {} of {} is started. {}", currentTime, this, migrationType, msg1, msg2);

        boolean allAdded = true;

        for (Vm vm : sourceVmGroup.getVmList()) {
            if (!targetHost.addMigratingInVm(vm)) {
                allAdded = false;
                break;
            }
        }

        if (allAdded) {
            for (Vm vm : sourceVmGroup.getVmList()) {
                sourceHost.addVmMigratingOut(vm);
                shutdownVmIfNonLiveMigration(vm, targetHost, delay, nonLiveMigration);
            }

            // Send a single batch event for the whole group
            send(this, delay, VM_MIGRATE_BATCH, new AbstractMap.SimpleEntry<>(sourceVmGroup, targetHost));
        }
    }

    private double timeToMigrateVmGroup(VmGroup sourceVmGroup, Host targetHost) {
        double maxTime = 0.0;

        for (Vm vm : sourceVmGroup.getVmList()) {
            double ramUtilizationMB = (double) vm.getRam().getAllocatedResource();
            double vmMigrationBwMBps = this.getBandwidthForMigration(targetHost);
            Host sourceHost = vm.getHost();

            double migrationTime;

            if (isLiveMigration(sourceHost, targetHost)) {
                // Live migration: use RAM
                migrationTime = ramUtilizationMB / vmMigrationBwMBps;
            } else {
                // Non-live migration: use negative disk size
                migrationTime = -((double) vm.getStorage().getCapacity() / vmMigrationBwMBps);
            }

            // Keep the maximum by absolute value, but preserve the sign (to detect non-live)
            if (Math.abs(migrationTime) > Math.abs(maxTime)) {
                maxTime = migrationTime;
            }
        }

        return maxTime;
    }

    private static boolean isLiveMigration(final Host sourceHost, final Host targetHost) {
        final Function<Host, DatacenterCharacteristics.Distribution> dist = host -> host.getDatacenter().getCharacteristics().getDistribution();
        return dist.apply(sourceHost) == PRIVATE && dist.apply(targetHost) == PRIVATE;
    }

    private double getBandwidthForMigration(final Host targetHost) {
        return bitsToBytes(targetHost.getBw().getCapacity() * getBandwidthPercentForMigration());
    }

    private void shutdownVmIfNonLiveMigration(final Vm sourceVm, final Host targetHost, final double delay, final boolean nonLiveMigration) {
        if(nonLiveMigration) {
            sourceVm.shutdown();
            final var cloudlets = sourceVm.getCloudletScheduler().getCloudletList().stream().toList();
            sourceVm.getCloudletScheduler().clear();
            cloudlets.stream().map(Cloudlet::reset).forEach(c -> c.setVm(sourceVm).setBroker(sourceVm.getBroker()));

            final var targerDc = targetHost.getDatacenter();
            // Request restarting executing the Cloudlets after the VM finishes non-live migration
            cloudlets.forEach(c -> targerDc.schedule(delay + getSimulation().getMinTimeBetweenEvents(), CloudSimTag.CLOUDLET_SUBMIT, c));
        }
    }

    @Override
    public void processEvent(final SimEvent ev) {
        switch (ev.getTag()) {
            case VM_MIGRATE_BATCH -> finishVmMigration(ev, true);
            default -> super.processEvent(ev);
        }
    }

    @SuppressWarnings("unchecked")
    protected boolean finishVmMigration(final SimEvent evt, final boolean ack) {
        if (!(evt.getData() instanceof Map.Entry<?, ?> entry) || !(entry.getKey() instanceof VmGroup group)) {
            throw new InvalidEventDataTypeException(evt, "VM_MIGRATE_BATCH", "Map.Entry<VmGroup, Host>");
        }

        final Host targetHost = (Host) entry.getValue();
        final Host sourceHost = group.getVmList().get(0).getHost();

        updateHostsProcessing();

        boolean allSuccess = true;

        for (Vm vm : group.getVmList()) {
            vmAllocationPolicy.deallocateHostForVm(vm);
            targetHost.removeMigratingInVm(vm);

            final HostSuitability suitability = vmAllocationPolicy.allocateHostForVm(vm, targetHost);

            if (suitability.fully()) {
                ((VmAbstract) vm).updateMigrationFinishListeners(targetHost);
                vm.getBroker().getVmExecList().add(vm);

                if (ack) {
                    sendNow(evt.getSource(), CloudSimTag.VM_CREATE_ACK, vm);
                }

                LOGGER.info("{}: Migration of {} from {} to {} is completed.",
                        getSimulation().clockStr(), vm, sourceHost, targetHost);
            } else {
                LOGGER.error("{}: {}: Allocation of {} to destination {} failed due to {}!",
                        getSimulation().clockStr(), this, vm, targetHost, suitability);

                allSuccess = false;
            }

            onVmMigrationFinishListeners.forEach(listener ->
                    listener.update(DatacenterVmMigrationEventInfo.of(listener, vm, suitability)));
        }

        // If no other pending migration at current time, update processing again
        final var event = getSimulation().findFirstDeferred(this, new PredicateType(CloudSimTag.VM_MIGRATE));
        if (event == null || event.getTime() > clock()) {
            updateHostsProcessing();
        }

        return allSuccess;
    }

    private double clock() {
        return getSimulation().clock();
    }
}
