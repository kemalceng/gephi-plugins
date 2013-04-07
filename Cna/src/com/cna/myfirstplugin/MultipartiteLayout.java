/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cna.myfirstplugin;

import java.util.ArrayList;
import java.util.List;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.HierarchicalGraph;
import org.gephi.layout.spi.Layout;
import org.gephi.layout.spi.LayoutBuilder;
import org.gephi.layout.spi.LayoutProperty;
import org.gephi.utils.longtask.spi.LongTask;
import org.gephi.utils.progress.ProgressTicket;

/**
 *
 * @author kemalbeskardesler
 */
public class MultipartiteLayout implements Layout, LongTask {
    
    private boolean cancel = false;
    private boolean executing = false;
    private ProgressTicket progressTicket;
    private final LayoutBuilder multipartiteLayoutBuilder;
    private GraphModel graphModel;
    private HierarchicalGraph graph;
//    LayoutProperty mySpeedProperty;
    float speed;
    
    public MultipartiteLayout(MultipartiteLayoutBuilder builder)
    {
        this.multipartiteLayoutBuilder = builder;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(Float speed) {
        this.speed = speed;
    }

    @Override
    public boolean cancel() {
        cancel = true;
        return true;
    }

    @Override
    public void setProgressTicket(ProgressTicket pt) {
        this.progressTicket = pt;
    }

    @Override
    public void initAlgo() {
        executing = true;
        // Graph may be organized into layers here.
    }

    @Override
    public void setGraphModel(GraphModel graphModel) {
        this.graphModel = graphModel;
    }

    @Override
    public void goAlgo() {
        graph = graphModel.getHierarchicalGraphVisible();
    }

    @Override
    public boolean canAlgo() {
        return executing;
    }

    @Override
    public void endAlgo() {
        executing = false;
    }

    @Override
    public LayoutProperty[] getProperties() {
        List<LayoutProperty> properties = new ArrayList<LayoutProperty>();

        try {
            properties.add(LayoutProperty.createProperty(
                    this,
                    Float.class,
                    "Speed",
                    "CategoryHere",
                    "DescriptionHere",
                    "getSpeed",
                    "setSpeed"));
            
        } catch (Exception e) {
            e.printStackTrace();
        }

        return properties.toArray(new LayoutProperty[0]);

    }

    @Override
    public void resetPropertiesValues() {
        setSpeed(1.0F);
    }

    @Override
    public LayoutBuilder getBuilder() {
        return this.multipartiteLayoutBuilder;
    }

}
