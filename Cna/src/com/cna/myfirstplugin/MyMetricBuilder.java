/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cna.myfirstplugin;

import org.gephi.statistics.spi.Statistics;
import org.gephi.statistics.spi.StatisticsBuilder;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author kemalbeskardesler
 */

@ServiceProvider(service = StatisticsBuilder.class)
public class MyMetricBuilder implements StatisticsBuilder{

    @Override
    public String getName() {
        return "MyMetric";
    }

    @Override
    public Statistics getStatistics() {
        return new MyMetric();
    }
    
    @Override
    public Class<? extends Statistics> getStatisticsClass() {
        return MyMetric.class;
    }
}
