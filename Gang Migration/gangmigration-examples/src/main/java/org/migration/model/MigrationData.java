package org.migration.model;

import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.vms.Vm;

/**
 * Represents detailed information about a single VM migration event.
 * It includes the VM being migrated, the source and target hosts,
 * and various metrics such as migration time, downtime, energy, and cost.
 *
 * This class can be used to collect, track, and display per-VM migration statistics
 * for further analysis or reporting.
 */
public class MigrationData {
    private Vm vm;
    private Host sourceHost;
    private Host targetHost;
    private double migrationTime;
    private double downtime;
    private double migrationEnergy;
    private double migrationCost;

    public MigrationData() {
    }

    public MigrationData(Vm vm, Host sourceHost, Host targetHost, double migrationTime) {
        this.vm = vm;
        this.sourceHost = sourceHost;
        this.targetHost = targetHost;
        this.migrationTime = migrationTime;
//        this.downtime = downtime;
//        this.migrationEnergy = migrationEnergy;
//        this.migrationCost = migrationCost;
    }

    // Getters and Setters
    public Vm getVm() {
        return vm;
    }

    public void setVm(Vm vm) {
        this.vm = vm;
    }

    public Host getSourceHost() {
        return sourceHost;
    }

    public void setSourceHost(Host sourceHost) {
        this.sourceHost = sourceHost;
    }

    public Host getTargetHost() {
        return targetHost;
    }

    public void setTargetHost(Host targetHost) {
        this.targetHost = targetHost;
    }

    public double getMigrationTime() {
        return migrationTime;
    }

    public void setMigrationTime(double migrationTime) {
        this.migrationTime = migrationTime;
    }

    public double getDowntime() {
        return downtime;
    }

    public void setDowntime(double downtime) {
        this.downtime = downtime;
    }

    public double getMigrationEnergy() {
        return migrationEnergy;
    }

    public void setMigrationEnergy(double migrationEnergy) {
        this.migrationEnergy = migrationEnergy;
    }

    public double getMigrationCost() {
        return migrationCost;
    }

    public void setMigrationCost(double migrationCost) {
        this.migrationCost = migrationCost;
    }

    /**
     * Returns the header for a formatted table of migration data.
     *
     * @return Header string formatted as a table row.
     */
    public static String getHeader() {
        return String.format("| %-10s | %-10s | %-10s | %-10s | %-10s | %-10s |",
                "VM",
                "Source Host",
                "Target Host",
                "Migration Time",
                "Migration Energy",
                "Migration Cost");
    }

    /**
     * Converts migration data into a formatted table representation.
     * The resulting string includes the VM ID, source host ID, target host ID,
     * migration time, migration energy, and migration cost.
     *
     * @return a formatted table row representing the migration data.
     */
    public String toTable() {
        return String.format("| %-10s | %-11s | %-11s | %-14s | %-16s | %-14s |",
                vm.getId(),
                sourceHost.getId(),
                targetHost.getId(),
                migrationTime,
                migrationEnergy,
                migrationCost);
    }
}