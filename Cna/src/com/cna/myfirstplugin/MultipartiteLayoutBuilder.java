/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cna.myfirstplugin;

import javax.swing.Icon;
import javax.swing.JPanel;
import org.gephi.layout.spi.Layout;
import org.gephi.layout.spi.LayoutBuilder;
import org.gephi.layout.spi.LayoutUI;
import org.openide.util.lookup.ServiceProvider;
/**
 *
 * @author kemalbeskardesler
 */

 
@ServiceProvider(service = LayoutBuilder.class)
public class MultipartiteLayoutBuilder implements LayoutBuilder{

    @Override
    public String getName() {
        return "MultipartiteLayout";
    }

    @Override
    public LayoutUI getUI() {
        return new LayoutUI() {

            @Override
            public String getDescription() {
                return "Minimizes the number of edge crossings for multipartite graphs.";
            }

            @Override
            public Icon getIcon() {
                return null;
            }

            @Override
            public JPanel getSimplePanel(Layout layout) {
                return null;
            }

            @Override
            public int getQualityRank() {
                return -1;
            }

            @Override
            public int getSpeedRank() {
                return -1;
            }
        };
    }

    @Override
    public Layout buildLayout() {
        return new MultipartiteLayout(this);
    }
    
}
