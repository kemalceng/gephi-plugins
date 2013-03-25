/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cna.myfirstplugin;

import javax.swing.JPanel;
import org.gephi.statistics.spi.*;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author kemalbeskardesler
 */

@ServiceProvider(service = StatisticsUI.class)
public class MyMetricUI implements StatisticsUI{
    
    private MyMetric myMetric;
    
    @Override
    public JPanel getSettingsPanel() {
        return null;
    }

    @Override
    public void setup(Statistics statistics) {
        this.myMetric = (MyMetric) statistics;
    }

    @Override
    public void unsetup() {
        this.myMetric = null;
    }

    @Override
    public Class<? extends Statistics> getStatisticsClass() {
        return MyMetric.class;
    }

    @Override
    public String getValue() {
        return "";
    }

    @Override
    public String getDisplayName() {
        return "My Metric";
    }

    @Override
    public String getShortDescription() {
        return "CNA project metric";
    }

    @Override
    public String getCategory() {
        return StatisticsUI.CATEGORY_NETWORK_OVERVIEW;
    }

    @Override
    public int getPosition() {
        return 800;
    }
    
}
