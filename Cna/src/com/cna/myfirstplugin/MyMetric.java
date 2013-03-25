/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cna.myfirstplugin;

import org.gephi.data.attributes.api.AttributeModel;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.Node;
import org.gephi.statistics.spi.Statistics;
//import org.gephi.utils.longtask.spi.LongTask;
import org.gephi.utils.progress.Progress;
import org.gephi.utils.progress.ProgressTicket;

/**
 *
 * @author kemalbeskardesler
 */
public class MyMetric implements Statistics{

    private String report = "<html><Body><h1>Hello World!</h1> </Body></html>";
    private boolean cancel = false;
    private ProgressTicket progressTicket;

    @Override
    public void execute(GraphModel gm, AttributeModel am) {
        actionExecute(gm, am);
    }

    @Override
    public String getReport() {
        return report;
    }

//    @Override
//    public boolean cancel() {
//        cancel = true;
//        return true;
//    }
//
//    @Override
//    public void setProgressTicket(ProgressTicket pt) {
//        this.progressTicket = pt;
//    }

    private void actionExecute(GraphModel graphModel, AttributeModel attributeModel) {
        
        boolean bExecuteEnabled = false;
        if(!bExecuteEnabled)
        {
            return;
        }
        
        //report += "Algorithm started ";
        Graph graph = graphModel.getGraphVisible();
        
        graph.readLock();

        try
        {
            Progress.start(progressTicket, graph.getNodeCount());

            for (Node n : graph.getNodes()) {
                //do something
                Progress.progress(progressTicket);
                
                if (cancel)
                {
                    break;
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            //Unlock graph
        }
        
        graph.readUnlockAll();
    }
    
}
