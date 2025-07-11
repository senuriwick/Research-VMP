package org.migration;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Runs VM migration simulations using different placement policies and datacenter configurations.
 * Aggregates and logs results such as total migrations, average migration time, and search time.
 * Results are saved to a CSV file for analysis.
 */
public class MigrationRunner {
    public static void main(String[] args) {
        List<String> policies = List.of("ff", "bf", "wf", "pbfd", "rand", "rr", "brute");
//        int[][] configs = {
//                {2, 4},   // {hostCount, totalVmCount}
//                {5, 20},
//                {10, 40},
//                {20, 80},
//                {50, 200},
//                {100, 400},
//                {200, 800},
//                {500, 2400},
//                {1000, 4000},
//                {2000, 8000},
//                {5000, 20000},
//                {10000, 40000},
//                {20000, 80000},
//                {50000, 200000}
//        };
        int[][] configs = {
                {2, 4}  // {hostCount, totalVmCount}
        };
        int runs = 3;

        // Pre-run brute-force 3 times to warm up JVM and mitigate cold start bias
        for (int i = 0; i < 3; i++) {
            new GroupMigrationExample("brute", 2, 4); // no logging, no writing
        }

        try (FileWriter csvWriter = new  FileWriter("../preliminary-data/migration_results.csv")) {
                csvWriter.append("Policy,Hosts,VMs,Total Migrations,Total Migration Time (ms),Total Elapsed Time (ms),Average Elapsed Time (ms)\n");

            for (int[] config : configs) {
                int hostCount = config[0];
                int totalVmCount = config[1];

                for (String policy : policies) {
                    int totalMigrations = 0;
                    double totalMigrationTime = 0;
                    double totalSearchTime = 0;
                    double averageSearchTime = 0;

                    for (int i = 0; i < runs; i++) {
                        GroupMigrationExample simulation = new GroupMigrationExample(policy, hostCount, totalVmCount);
                        totalMigrations += simulation.getMigrationsNumber();
                        totalMigrationTime += simulation.getTotalMigrationTime();
//                        totalDowntime += simulation.getTotalDowntime();
//                        averageDowntime += simulation.getAverageDowntime();
                        totalSearchTime += simulation.getTotalSearchTime();
                        averageSearchTime += simulation.getAverageSearchTime();
                    }

                    // Averages
                    int avgMigrations = totalMigrations / runs;
                    double avgMigrationTime = totalMigrationTime / runs;
                    double avgSearchTime = totalSearchTime / runs;
                    double avgPerSearch = averageSearchTime / runs;

                    csvWriter.append(String.format("%s,%d,%d,%d,%.4f,%.4f,%.4f\n",
                            policy, hostCount, totalVmCount, avgMigrations, avgMigrationTime, avgSearchTime, avgPerSearch));
                }

                csvWriter.append("\n");
            }

            System.out.println("✅ All tests completed. Results saved to migration_results.csv");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
