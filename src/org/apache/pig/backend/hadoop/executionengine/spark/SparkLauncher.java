package org.apache.pig.backend.hadoop.executionengine.spark;

import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.pig.backend.hadoop.executionengine.mapReduceLayer.Launcher;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.PhysicalOperator;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.plans.PhysicalPlan;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.relationalOperators.POFilter;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.relationalOperators.POForEach;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.relationalOperators.POLoad;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.relationalOperators.POStore;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.util.PlanHelper;
import org.apache.pig.backend.hadoop.executionengine.spark.converter.FilterConverter;
import org.apache.pig.backend.hadoop.executionengine.spark.converter.ForEachConverter;
import org.apache.pig.backend.hadoop.executionengine.spark.converter.LoadConverter;
import org.apache.pig.backend.hadoop.executionengine.spark.converter.StoreConverter;
import org.apache.pig.data.Tuple;
import org.apache.pig.impl.PigContext;
import org.apache.pig.tools.pigstats.PigStats;
import spark.RDD;
import spark.SparkContext;

/**
 * @author billg
 */
public class SparkLauncher extends Launcher {
    private static final Log LOG = LogFactory.getLog(SparkLauncher.class);

    @Override
    public PigStats launchPig(PhysicalPlan physicalPlan, String grpName, PigContext pigContext) throws Exception {
        LOG.info("!!!!!!!!!!  Launching Spark (woot) !!!!!!!!!!!!");

        // Example of how to launch Spark
        SparkContext sc = new SparkContext("local", "Spork", null, null);

        LinkedList<POLoad> poLoads = PlanHelper.getLoads(physicalPlan);
        for (POLoad poLoad : poLoads) {
            LoadConverter loadConverter = new LoadConverter(pigContext, physicalPlan, sc);
            RDD<Tuple> pigTupleRDD = loadConverter.convert(null, poLoad);

            for (PhysicalOperator successor : physicalPlan.getSuccessors(poLoad)) {
                physicalToRDD(pigContext, physicalPlan, successor, pigTupleRDD);
            }
        }

        return PigStats.get();
    }

    private void physicalToRDD(PigContext pigContext, PhysicalPlan plan,
                               PhysicalOperator physicalOperator, RDD<Tuple> rdd) throws IOException {
        RDD<Tuple> nextRDD = null;

        // TODO: put these converters in a Map and look up which one to invoke
        if (physicalOperator instanceof POStore) {

            StoreConverter storeConverter = new StoreConverter(pigContext);
            storeConverter.convert(rdd, (POStore)physicalOperator);
            return;

        } else if (physicalOperator instanceof POForEach) {

            ForEachConverter filterConverter = new ForEachConverter();
            nextRDD = filterConverter.convert(rdd, (POForEach)physicalOperator);

        } else if (physicalOperator instanceof POFilter) {

            FilterConverter filterConverter = new FilterConverter();
            nextRDD = filterConverter.convert(rdd, (POFilter)physicalOperator);
        }

        if (nextRDD == null) {
            throw new IllegalArgumentException("Spork unsupported PhysicalOperator: " + physicalOperator);
        }

        for (PhysicalOperator succcessor : plan.getSuccessors(physicalOperator)) {
            physicalToRDD(pigContext, plan, succcessor, nextRDD);
        }
    }

    @Override
    public void explain(PhysicalPlan pp, PigContext pc, PrintStream ps, String format, boolean verbose)
            throws IOException { }
}