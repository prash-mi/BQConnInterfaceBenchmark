package com.google.bqconninterfacebenchmark;

import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.BigQueryResultSet;
import com.google.cloud.bigquery.BigQuerySQLException;
import com.google.cloud.bigquery.Connection;
import com.google.cloud.bigquery.ConnectionSettings;
import com.google.cloud.bigquery.DatasetId;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.ReadClientConnectionConfiguration;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 1)
@Measurement(iterations = 3)
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class ConnImplBenchmark {
    @Param({ "1000000", "10000000", "100000000"})//1M, 10M, and 100M
    public int rowLimit;
    private ConnectionSettings connectionSettingsReadAPIEnabled, connectionSettingsReadAPIDisabled;
    private long numBuffRows = 100000L;
    private final String DATASET = "bigquery_test_dataset";
    private final String QUERY = "SELECT * FROM tlc_yellow_trips_2017_stephwang LIMIT %s";

    @Setup
    public void setUp() throws IOException {
        java.util.logging.Logger.getGlobal().setLevel(Level.ALL);
        ReadClientConnectionConfiguration clientConnectionConfiguration;

        clientConnectionConfiguration =
                ReadClientConnectionConfiguration.newBuilder()
                        .setTotalToPageRowCountRatio(10L)
                        .setMinResultSize(200000L)
                        .setBufferSize(numBuffRows)
                        .build();

        connectionSettingsReadAPIEnabled =
                ConnectionSettings.newBuilder()
                        .setDefaultDataset(DatasetId.of(DATASET))
                        .setNumBufferedRows(numBuffRows) // page size
                        .setPriority(QueryJobConfiguration.Priority.INTERACTIVE) // DEFAULT VALUE - so that isFastQuerySupported returns false
                        .setReadClientConnectionConfiguration(clientConnectionConfiguration)
                        .setUseReadAPI(true)//enable read api
                        .build();
        connectionSettingsReadAPIDisabled =
                ConnectionSettings.newBuilder()
                        .setDefaultDataset(DatasetId.of(DATASET))
                        .setNumBufferedRows(numBuffRows) // page size
                        .setPriority(QueryJobConfiguration.Priority.INTERACTIVE) // so that isFastQuerySupported returns false
                        .setReadClientConnectionConfiguration(clientConnectionConfiguration)
                        .setUseReadAPI(false)//disable read api
                        .build();

    }


    @Benchmark
    public void iterateRecordsUsingReadAPI(Blackhole blackhole) throws InterruptedException, BigQuerySQLException {
        Connection connectionReadAPIEnabled = BigQueryOptions.getDefaultInstance().getService().createConnection(connectionSettingsReadAPIEnabled);
        String selectQuery = String.format(QUERY, rowLimit);
        long hash = 0L;
        try {
            BigQueryResultSet bigQueryResultSet = connectionReadAPIEnabled.executeSelect(selectQuery);
            hash = getResultHash(bigQueryResultSet);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            connectionReadAPIEnabled.cancel(); // IMP to kill the bg workers
        }
        blackhole.consume(hash);
    }

    @Benchmark
    public void iterateRecordsWithoutUsingReadAPI(Blackhole blackhole) throws InterruptedException, BigQuerySQLException {
        Connection connectionReadAPIDisabled = BigQueryOptions.getDefaultInstance().getService().createConnection(connectionSettingsReadAPIDisabled);
        String selectQuery = String.format(QUERY, rowLimit);
        long hash = 0L;
        try {
            BigQueryResultSet bigQueryResultSet = connectionReadAPIDisabled.executeSelect(selectQuery);
            hash = getResultHash(bigQueryResultSet);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            connectionReadAPIDisabled.cancel(); // IMP to kill the bg workers
        }
        blackhole.consume(hash);
    }

    //Hashes all the 20 columns of all the rows
    private long getResultHash(BigQueryResultSet bigQueryResultSet) throws SQLException {
        ResultSet rs = bigQueryResultSet.getResultSet();
        long hash = 0L;
        int cnt=0;
        System.out.print("\n Running");
        while (rs.next()) {
            hash += rs.getString("vendor_id") == null?0: rs.getString("vendor_id").hashCode();
            hash += rs.getString("pickup_datetime") == null?0: rs.getString("pickup_datetime").hashCode();
            hash += rs.getString("dropoff_datetime") == null?0: rs.getString("dropoff_datetime").hashCode();
            hash += rs.getLong("passenger_count");
            hash += rs.getDouble("trip_distance");
            hash += rs.getDouble("pickup_longitude");
            hash += rs.getDouble("pickup_latitude");
            hash += rs.getString("rate_code") == null?0: rs.getString("rate_code").hashCode();
            hash += rs.getString("store_and_fwd_flag") == null?0: rs.getString("store_and_fwd_flag").hashCode();
            hash += rs.getDouble("dropoff_longitude");
            hash += rs.getDouble("dropoff_latitude");
            hash += rs.getString("payment_type") == null?0: rs.getString("payment_type").hashCode();
            hash += rs.getDouble("fare_amount");
            hash += rs.getDouble("extra");
            hash += rs.getDouble("mta_tax");
            hash += rs.getDouble("tip_amount");
            hash += rs.getDouble("tolls_amount");
            hash += rs.getDouble("imp_surcharge");
            hash += rs.getDouble("total_amount");
            hash += rs.getString("pickup_location_id") == null?0: rs.getString("pickup_location_id").hashCode();
            hash += rs.getString("dropoff_location_id") == null?0: rs.getString("dropoff_location_id").hashCode();
            if (++cnt%100000==0){//just to indicate the progress while long running benchmarks
                System.out.print(".");
            }
        }
        return hash;
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(ConnImplBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
