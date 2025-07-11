package org.migration;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.core.CloudSimTag;
import org.cloudsimplus.core.Simulation;
import org.cloudsimplus.core.events.PredicateType;
import org.cloudsimplus.core.events.SimEvent;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSuitability;
import org.cloudsimplus.listeners.DatacenterVmMigrationEventInfo;
import org.cloudsimplus.resources.DatacenterStorage;
import org.cloudsimplus.util.InvalidEventDataTypeException;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmAbstract;
import org.cloudsimplus.vms.VmGroup;

import java.util.*;

/**
 * A custom Datacenter that can accept a VmGroup + Host in one call
 * and fan out each Vm’s migration “all at once.”
 * In CloudSimPlus, the method requestVmMigration(Vm, Host) lives on DatacenterSimple,
 * so we subclass DatacenterSimple to add a batch version that takes a VmGroup.
 */
public class DatacenterAdvance extends DatacenterSimple {
    private static final int VM_MIGRATE_BATCH = 1001;

    public DatacenterAdvance(final CloudSimPlus simulation, final List<Host> hostList) {
        super(simulation, hostList);
    }

    public DatacenterAdvance(Simulation simulation, List<? extends Host> hostList, VmAllocationPolicy vmAllocationPolicy) {
        super(simulation, hostList, vmAllocationPolicy, new DatacenterStorage());
    }

    /**
     * Overloaded method: migrate an entire VmGroup to a single targetHost “all at once.”
     * Instead of looping right away, we schedule one SimEvent with tag=VM_MIGRATE_BATCH at delay=0.0.
     * When that event fires, processEvent() will unpack the group and loop over each Vm.
     */
    public void requestVmMigration(final VmGroup vmGroup, final Host targetHost) {
        // schedule exactly one “batch” event at the current simulation time
        schedule(0.0, VM_MIGRATE_BATCH, new AbstractMap.SimpleEntry<>(vmGroup, targetHost));
    }

    @Override
    public void processEvent(final SimEvent ev) {
        switch (ev.getTag()) {
            case VM_MIGRATE_BATCH: {
                @SuppressWarnings("unchecked")
                Map.Entry<VmGroup, Host> entry = (Map.Entry<VmGroup, Host>) ev.getData();
                VmGroup group = entry.getKey();
                Host targetHost = entry.getValue();

                // Loop over each Vm in one single callback. Because we are still in the same processEvent()
                // CloudSimPlus’s clock has not advanced, so all VMs see the same “start time.”
                for (Vm vm : group.getVmList()) {
                    super.requestVmMigration(vm, targetHost);
                }
                break;
            }

            default:
                // For any other tag, fall back to the usual DatacenterSimple behavior
                super.processEvent(ev);
                break;
        }
    }
}