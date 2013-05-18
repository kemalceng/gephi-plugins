/*
Copyright 2008-2010 Gephi
Authors : Mathieu Bastian <mathieu.bastian@gephi.org>
Website : http://www.gephi.org

This file is part of Gephi.

DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.

Copyright 2011 Gephi Consortium. All rights reserved.

The contents of this file are subject to the terms of either the GNU
General Public License Version 3 only ("GPL") or the Common
Development and Distribution License("CDDL") (collectively, the
"License"). You may not use this file except in compliance with the
License. You can obtain a copy of the License at
http://gephi.org/about/legal/license-notice/
or /cddl-1.0.txt and /gpl-3.0.txt. See the License for the
specific language governing permissions and limitations under the
License.  When distributing the software, include this License Header
Notice in each file and include the License files at
/cddl-1.0.txt and /gpl-3.0.txt. If applicable, add the following below the
License Header, with the fields enclosed by brackets [] replaced by
your own identifying information:
"Portions Copyrighted [year] [name of copyright owner]"

If you wish your version of this file to be governed by only the CDDL
or only the GPL Version 3, indicate your decision by adding
"[Contributor] elects to include this software in this distribution
under the [CDDL or GPL Version 3] license." If you do not indicate a
single choice of license, a recipient has the option to distribute
your version of this file under either the CDDL, the GPL Version 3 or
to extend the choice of license to its licensees as provided above.
However, if you add GPL Version 3 code and therefore, elected the GPL
Version 3 license, then the option applies only if the new code is
made subject to such option by the copyright holder.

Contributor(s):

Portions Copyrighted 2011 Gephi Consortium.
Portions Copyrighted 2013 Kemal Beşkardeşler
*/

package com.cna.multipartiteLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.gephi.data.attributes.api.AttributeColumn;
import org.gephi.data.attributes.api.AttributeController;
import org.gephi.data.attributes.api.AttributeModel;
import org.gephi.graph.api.Attributes;
import org.gephi.graph.api.Edge;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.HierarchicalGraph;
import org.gephi.graph.api.Node;
import org.gephi.layout.spi.Layout;
import org.gephi.layout.spi.LayoutBuilder;
import org.gephi.layout.spi.LayoutProperty;
import org.gephi.utils.longtask.spi.LongTask;
import org.gephi.utils.progress.ProgressTicket;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.gephi.ui.propertyeditor.NodeColumnStringEditor;

/**
 *
 * @author kemalbeskardesler
 */
public class MultipartiteLayout implements Layout, LongTask {
    
    public static String COLUMN_LAYER_NO = "__LayerNo";
    public static int MAX_WAITING_TIME = 2000;
    private boolean cancel = false;
    private boolean executing = false;
    private ProgressTicket progressTicket;
    private final LayoutBuilder multipartiteLayoutBuilder;
    private GraphModel graphModel;
    private HierarchicalGraph graph;
    MultiPartiteLayoutSettigsPanel propertiesPanel;
//    LayoutProperty mySpeedProperty;

    private AttributeColumn acLayerAttribute = null;
    private String strLayerAttribute = "";
    float speed = 1.0f;
    private int areaSize;
    int topLayerPosition = 0;
    
    Map layerToNodesMap = new HashMap<Object, ArrayList<Node>>();
    Map layerToLayerConnections = new HashMap<Object, List<Object>>();
    Map positionToLayerMap = new HashMap<Integer, Object>();
    List<Map> orderList = new ArrayList<Map>();
    int iMinExtraEdgeCountOfOrder = Integer.MAX_VALUE;
    ArrayList<Integer[][]> adjacencyMatrixes = new ArrayList<Integer[][]>();
    private boolean bLayersConstructed = false;
    private Object firstLayer = null;
    
    public int FIRST_MATRIX = 0;
    public int MIDDLE_MATRIX = -1;
    public int LAST_MATRIX = 1;
    public int iTotalEdgeCrossings;
    public long sleepWaitTime = MAX_WAITING_TIME;
    private boolean bReverseApplied = false;
    
    public MultipartiteLayout(MultipartiteLayoutBuilder builder)
    {
        this.multipartiteLayoutBuilder = builder;
        this.propertiesPanel = new MultiPartiteLayoutSettigsPanel(this);
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(Float speed) {
        this.speed = speed;
        propertiesPanel.numSpeed.setValue(speed.intValue());
    }

    public AttributeColumn getLayerAttribute()
    {
        return acLayerAttribute;
    }
            
    public void setLayerAttribute(AttributeColumn aacLayerAttribute)
    {
        resetAll();
        acLayerAttribute = aacLayerAttribute;
    }

    public String getLayerAttributeString()
    {
        return strLayerAttribute;
    }
            
    public void setLayerAttributeString(String astrLayerAttribute)
    {
        resetAll();
        strLayerAttribute = astrLayerAttribute;
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
    }

    @Override
    public void setGraphModel(GraphModel graphModel)
    {
        this.graphModel = graphModel;
        
        graph = this.graphModel.getHierarchicalGraphVisible();
    }

    @Override
    public void goAlgo() {
        
        graph.readLock();
        
        try
        {
            // Organize graph into layers
            if(!bLayersConstructed)
            {
                constructLayersFromAttribute();
                bLayersConstructed = true;
            }
        }
        catch(Exception e)
        {
            System.out.println(e.getMessage());
            resetAll();
        }
        
        changeAlignment();
        
        graph.readUnlock();
    }

    @Override
    public boolean canAlgo() {
        return executing;
    }

    @Override
    public void endAlgo()
    {
        graph.readLock();
        
        for(Node node : graph.getNodes())
        {
            node.getNodeData().setLayoutData(null);
        }
        executing = false;
        
        graph.readUnlock();
    }

    @Override
    public LayoutProperty[] getProperties() {

        String strAttributes = "";
        
        // Add attributes to layer choice description
        AttributeModel am = Lookup.getDefault().lookup(AttributeController.class).getModel();
        for(AttributeColumn column : am.getNodeTable().getColumns())
        {
            String columnTitle = column.getTitle();
            if(!columnTitle.equalsIgnoreCase("Id") && !columnTitle.equalsIgnoreCase("Label"))
            {
                if(!strAttributes.isEmpty())
                {
                    strAttributes += ", ";
                }
                strAttributes += columnTitle;
            }
        }
                
        List<LayoutProperty> properties = new ArrayList<LayoutProperty>();

        try {
            properties.add(LayoutProperty.createProperty(
                    this,
                    Float.class,
                    "Speed",
                    "Multipartite",
                    "DescriptionHere",
                    "getSpeed",
                    "setSpeed"));
            
            properties.add(LayoutProperty.createProperty(
                    this,
                    AttributeColumn.class,
                    "LayerSelection",
                    "Multipartite",
                    "Select attribute name for grouping",
                    "getLayerAttribute",
                    "setLayerAttribute",
                    NodeColumnStringEditor.class));
            
            properties.add(LayoutProperty.createProperty(
                    this,
                    String.class,
                    "AttributeName",
                    "Multipartite",
                    "Attribute name for grouping (" + strAttributes + ")",
                    "getLayerAttributeString",
                    "setLayerAttributeString"));
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return properties.toArray(new LayoutProperty[0]);
    }

    @Override
    public void resetPropertiesValues() {
        setSpeed(1.0F);
        setLayerAttribute(null);
        setLayerAttributeString("");
        bLayersConstructed = false;
        firstLayer = null;
        propertiesPanel.txtDescription.setText("");
        propertiesPanel.initializeLayerAttributesComboBox();
    }

    @Override
    public LayoutBuilder getBuilder() {
        return this.multipartiteLayoutBuilder;
    }

    /**
     * Starting from layer 0, find suitable layer for the node.
     * @param node 
     */
    public void assignLayerNo(Node node)
    {
        Integer currentLayerNo = 0;
        
        Edge[] edges = graph.getEdges(node).toArray();
        Attributes currentAtts = node.getAttributes();
        
        if(currentAtts.getValue(COLUMN_LAYER_NO) == null)
        {
            for(int i = 0; i<edges.length; i++)
            {
                Edge edge = edges[i];
                Integer adjacentLayerNo = (Integer)(graph.getOpposite(node, edge)).getAttributes().getValue(COLUMN_LAYER_NO);
                
                // Set adjacent Layer No (Current LayerNo + 1)
                if(adjacentLayerNo != null)
                {
                    // Not suitable for current layer try for next layer
                    if(adjacentLayerNo.equals(currentLayerNo))
                    {
                        currentLayerNo = currentLayerNo + 1;
                        i = -1;
                    }
                }
            }
            
            currentAtts.setValue(COLUMN_LAYER_NO, currentLayerNo);
            if(!layerToNodesMap.containsKey(currentLayerNo))
            {
                layerToNodesMap.put(currentLayerNo, new ArrayList<Node>());
            }
            ((ArrayList)layerToNodesMap.get(currentLayerNo)).add(node);
        }
    }
    
    private void constructLayersFromAttribute()
    {
        if(strLayerAttribute.isEmpty())
        {
            constructLayers();
            return;
        }

        int nodeCount = graph.getNodeCount();
        Node[] nodes = graph.getNodes().toArray();

        String strAttributeUsedAsLayer = strLayerAttribute;
        
        for (Node node : nodes)
        {
            Object layer = node.getAttributes().getValue(strAttributeUsedAsLayer);
            
            if(firstLayer == null)
            {
                firstLayer = layer;
            }
            
            if(!layerToNodesMap.containsKey(layer))
            {
                layerToNodesMap.put(layer, new ArrayList<Node>());
            }
            ((ArrayList)layerToNodesMap.get(layer)).add(node);
        }

        for (Node node : nodes)
        {
            System.out.println((node.getAttributes().getValue(strAttributeUsedAsLayer)).toString()
                    + " " +(String) node.getAttributes().getValue("Label"));
        }

        constructMatrixes();
    }
    
    
    /**
     * If no parameter is specified to construct layers,
     * this default method automatically assigns layers to the nodes.
     */
    private void constructLayers() {
        
        int nodeCount = graph.getNodeCount();
        Node[] nodes = graph.getNodes().toArray();
        Edge[] edges = graph.getEdges().toArray();
        
        float nextXCoordinate = 0F;
        float nextYCoordinate = 0F;
 
        for (int i = 0; i < nodeCount; i++)
        {
            assignLayerNo(nodes[i]);
        }
        
        // Check layers constructed
        for (int i = 0; i < layerToNodesMap.size(); i++)
        {
            List<Node> nodesInLayer = (List<Node>)layerToNodesMap.get(i);
            for (int j = 0; j < nodesInLayer.size(); j++)
            {
                for (int k= j; k < nodesInLayer.size(); k++)
                {
                    if(graph.getEdge(nodesInLayer.get(j), nodesInLayer.get(k)) != null)
                    {
                        Node nodeInWrongLayer = nodesInLayer.get(k);
                        Attributes currentAtts = nodeInWrongLayer.getAttributes();
                        currentAtts.setValue(COLUMN_LAYER_NO, null);
                        nodesInLayer.remove(nodeInWrongLayer);
                        assignLayerNo(nodeInWrongLayer);
                    }
                }
            }
        }
        
        for (int i = 0; i < nodeCount; i++)
        {
            System.out.println(((Integer)nodes[i].getAttributes().getValue(COLUMN_LAYER_NO)).toString()
                    + " " +(String) nodes[i].getAttributes().getValue("Label"));
        }
          
        constructMatrixes();

/*        
        for (int i = 0; i < nodeCount; i++) {
        
            float xPosOfNode = nodes[i].getNodeData().x();
            float yPosOfNode = nodes[i].getNodeData().y();
            
            edges = graph.getEdges(nodes[i]).toArray();
            
            for(int j = 0; j < edges.length; j++)
            {
                EdgeData edgeData = edges[j].getEdgeData();
                
                NodeData adjacentNode = edgeData.getTarget();
                if(adjacentNode.getRootNode() == nodes[i])
                {
                    adjacentNode = edgeData.getSource();
                }
                
                if(adjacentNode.y() == yPosOfNode)
                {
                    adjacentNode.setY(nextYCoordinate + 20);
                    nextYCoordinate += 20;
                }
            }         
        }
*/        
    }

    private void constructMatrixes()
    {
        determineLayerConnections();
        setLayerPositions();
        
        // From top to bottom create matrixes
        //(topLayerPosition is the minimum layer position value)
        createDefaultMatrixes();
    }

    /**
     * Using layerToNodesMap, finds the connections between layers
     * and sets them in layerToLayerConnections map.
     */
    private void determineLayerConnections()
    {
        Object[] layersSet = (Object[]) layerToNodesMap.keySet().toArray();

        for(int sourceLayerIndex = 0; sourceLayerIndex < layersSet.length; sourceLayerIndex++)
        {
            Object sourceLayer = layersSet[sourceLayerIndex];
            
            List<Node> nodesInSourceLayer = (List<Node>)layerToNodesMap.get(sourceLayer);
            
            for(int targetLayerIndex = sourceLayerIndex + 1; targetLayerIndex < layersSet.length; targetLayerIndex++)
            {
                Object targetLayer = layersSet[targetLayerIndex];
            
                if(sourceLayer == targetLayer)
                {
                    continue; // TODO: Buraya girmemesi lazim kontrol et
                }
                
                List<Node> nodesInTargetLayer = (List<Node>)layerToNodesMap.get(targetLayer);
                
                // Set which layer connects to which layer
                if(isThereAnyConnectionBtwnLayers(nodesInSourceLayer, nodesInTargetLayer))
                {
                    if(!layerToLayerConnections.containsKey(sourceLayer))
                    {
                        layerToLayerConnections.put(sourceLayer, new ArrayList<Integer>());
                    }
                    ((ArrayList)layerToLayerConnections.get(sourceLayer)).add(targetLayer);
                    
                    // Add reverse connection
                    if(!layerToLayerConnections.containsKey(targetLayer))
                    {
                        layerToLayerConnections.put(targetLayer, new ArrayList<Integer>());
                    }
                    ((ArrayList)layerToLayerConnections.get(targetLayer)).add(sourceLayer);
                }
            }
        }
    }
    
    /**
     * Checks the existence of connection between two layers.
     * @param nodesInSourceLayer    List of nodes in source layer
     * @param nodesInTargetLayer    List of nodes in target layer
     * @return                      True if connection exists between source and target layer, false otherwise
     */
    private boolean isThereAnyConnectionBtwnLayers(List<Node> nodesInSourceLayer, List<Node> nodesInTargetLayer)
    {
        boolean bConnectionExists = false;
       
        for(Node sourceNode : nodesInSourceLayer)
        {
            for(Node targetNode : nodesInTargetLayer)
            {
                if(graph.getEdge(sourceNode, targetNode) != null)
                {
                    return true;
                }
            }
        }
        
        return bConnectionExists;
    }

    /**
     * From top to bottom, assigns layers' positions
     * according to connections between layers using
     * layerToLayerConnections map.
     * If no parameter is specified to construct layers,
     * initializes the first layer as 0 as the start point of
     * setTargetLayerPositions call.
     */
    private void setLayerPositions()
    {    
        if(firstLayer == null)
        {
            firstLayer = 0;
        }
        
        setMinimalInterconnectionsBetweenLayers();
        
        //setTargetLayerPositions((Integer)0, firstLayer);
    }
    
    /**
     * Sets the sourceLayer's position to the position aiLayerPosition
     * and recursively sets target layers' positions.
     * @param aiLayerPosition   position index of layer
     * @param sourceLayer       layer to be positioned
     */
    private void setTargetLayerPositions(int aiLayerPosition, Object sourceLayer)
    {   
        if(!positionToLayerMap.containsValue(sourceLayer))
        {
            positionToLayerMap.put(aiLayerPosition, sourceLayer);
        }
        else
        {
            return;
        }

        if(aiLayerPosition < topLayerPosition)
        {
            topLayerPosition = aiLayerPosition;
        }       
        
        List<Object> targetLayers = (List<Object>)layerToLayerConnections.get(sourceLayer);

        if(targetLayers == null)
        {
            return;
        }
        
        int orientationUpOrDown = aiLayerPosition >= 0 ? 1 : -1;
        for(Object targetLayer : targetLayers)
        {
            List<Object> nextLayers = (List<Object>)layerToLayerConnections.get(targetLayer);
            List<Object> layersWithoutDuplicates = new ArrayList<Object>();
            
            boolean bDuplicateExists = false;
            for (Object layer : nextLayers)
            {
                if(!positionToLayerMap.containsValue(layer))
                {
                    layersWithoutDuplicates.add(layer);
                }
                else
                {
                    bDuplicateExists = true;
                }
            }
            
            if(bDuplicateExists)
            {
                layerToLayerConnections.remove(targetLayer);
                layerToLayerConnections.put(targetLayer, layersWithoutDuplicates);
            }
            
            setTargetLayerPositions(aiLayerPosition + orientationUpOrDown, targetLayer);
            orientationUpOrDown *= -1;
        }
    }

    private void createDefaultMatrixes()
    {
        createDefaultMatrixes(false);
    }
    
    private void createDefaultMatrixes(boolean abPreserveXPositions)
    {
        int nodeYPosition = topLayerPosition;
        int nodeXPosition = 0;
        
        for(int i = 0; i < positionToLayerMap.size(); i++)
        {
            Object[] sourceLayerNodes = ((List<Node>)layerToNodesMap.get(positionToLayerMap.get(nodeYPosition))).toArray();
            
            // Set nodes' positions
            nodeXPosition = 0;
            for(Object node : sourceLayerNodes)
            {
                ((Node)node).getNodeData().setY(nodeYPosition * -50);
                if(!abPreserveXPositions)
                {
                    ((Node)node).getNodeData().setX(nodeXPosition * 50);
                    nodeXPosition++;
                }
            }
            
            if(nodeYPosition + 1 < positionToLayerMap.size())
            {
                // Create Matrix
                Object[] targetLayerNodes = ((List<Node>)layerToNodesMap.get(positionToLayerMap.get(nodeYPosition + 1))).toArray();
                
                Integer[][] matrix = convertLayersToMatrix(sourceLayerNodes, targetLayerNodes);
                
                adjacencyMatrixes.add(matrix);
            }
            
            nodeYPosition++;
        }
    }

    private Integer[][] convertLayersToMatrix(Object[] sourceLayerNodes, Object[] targetLayerNodes)
    {
        Integer[][] matrix = new Integer[sourceLayerNodes.length][targetLayerNodes.length];
        
        for(int i = 0; i < sourceLayerNodes.length; i++)
        {
            for(int j = 0; j < targetLayerNodes.length; j++)
            {
                if(graph.getEdge((Node)(sourceLayerNodes[i]), (Node)(targetLayerNodes[j])) != null)
                {
                    matrix[i][j] = 1;
                }
                else
                {
                    matrix[i][j] = 0;
                }
            }
        }
        
        return matrix;
    }

    private void changeAlignment()
    {    
        int iCurrentEdgeCrossings = calculateTotalEdgeCrossings();
        
        if(iCurrentEdgeCrossings == 0)
        {
            endAlgo();
        }
        
        for(int i = 0; i < adjacencyMatrixes.size(); i++)
        {
            int iMatrixIndex = i;
            if(bReverseApplied)
            {
                iMatrixIndex = adjacencyMatrixes.size() - 1 - i;
            }
            changeMatrixAlignment(adjacencyMatrixes.get(iMatrixIndex), topLayerPosition + iMatrixIndex);
        }
        
        // No more improvement
        if(iCurrentEdgeCrossings == iTotalEdgeCrossings)
        {
            if(bReverseApplied)
            {
                endAlgo();
            }
            else
            {
                applyReverse();
            }
        }
    }

    private void changeMatrixAlignment(Integer[][] matrix, int sourceLayerPosition)
    {
        printMatrix(matrix);
        
        Integer[][] matrixWithNewAlignment = new Integer[matrix.length][matrix[0].length];
        copyContent(matrixWithNewAlignment, matrix);
        
        int iFirstOrLastMatrix = MIDDLE_MATRIX;
        
        if(sourceLayerPosition == 0)
        {
            iFirstOrLastMatrix = FIRST_MATRIX;
        }
        else if(sourceLayerPosition == adjacencyMatrixes.size() - 1)
        {
            iFirstOrLastMatrix = LAST_MATRIX;
        }
        
        Integer[] shiftParameters = findShiftWithLessEdgeCrossings(matrixWithNewAlignment, iFirstOrLastMatrix);

        int edgeCrossings = calculateEdgeCrossings(matrix);
        
        // TODO: matrixWithNewAlignment değiştirilmeden geliyor
        if(shiftParameters[0] != -1)
        {
            applyShiftToMatrix(matrixWithNewAlignment, shiftParameters);
            printMatrix(matrixWithNewAlignment);
            
            int newEdgeCrossings = calculateEdgeCrossings(matrixWithNewAlignment);
            
            if(newEdgeCrossings < edgeCrossings)
            {
                // add 1 if shift nodes in target layer
                int sourceOrTargetLayer = shiftParameters[0];
                shiftNodes(sourceLayerPosition + sourceOrTargetLayer, shiftParameters[1], shiftParameters[2]);
                
                adjacencyMatrixes.remove(sourceLayerPosition);
                adjacencyMatrixes.add(sourceLayerPosition, matrixWithNewAlignment);
                printMatrix(adjacencyMatrixes.get(sourceLayerPosition));
                
                //Ustteki klonlama yeterli
                //applyShiftToMatrix(adjacencyMatrixes.get(sourceLayerPosition), shiftParameters);
                
                // If nodes shifted in target layer and there are more matrixes in lower layers 
                if(sourceOrTargetLayer == 1 && sourceLayerPosition + 1 < adjacencyMatrixes.size())
                {
                    shiftParameters[0] = 0;
                    applyShiftToMatrix(adjacencyMatrixes.get(sourceLayerPosition + 1), shiftParameters);
                    
                    printMatrix(adjacencyMatrixes.get(sourceLayerPosition + 1));
                
                }
                // If nodes shifted in source layer and there are more matrixes in upper layers 
                else if(sourceOrTargetLayer == 0 && sourceLayerPosition > 0)
                {
                    shiftParameters[0] = 1;
                    applyShiftToMatrix(adjacencyMatrixes.get(sourceLayerPosition - 1), shiftParameters);
                    
                    printMatrix(adjacencyMatrixes.get(sourceLayerPosition - 1));
                
                }
                
                iTotalEdgeCrossings = calculateTotalEdgeCrossings();
                
                if(iTotalEdgeCrossings == 0)
                {
                    endAlgo();
                }
                
                try
                {
                    Thread.sleep(sleepWaitTime);
                }
                catch (InterruptedException ex)
                {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
    }

    private Integer[] findShiftWithLessEdgeCrossings(Integer[][] matrix, int aiFirstOrLastMatrix)
    {
        printMatrix(matrix);
        
        //return generateRandomShift(matrix);
        
        Integer[] shiftParameters = findBetterDiagonalAlignment(matrix, aiFirstOrLastMatrix);
        
        return shiftParameters; 
    }

    public Integer[] generateRandomShift(Integer[][] matrix)
    {
        Integer [] shiftParameters = new Integer[3];
        int min = 0, maxRow = matrix.length, maxColumn = matrix[0].length;
        
        int shiftNode1No, shiftNode2No;
        
        int shiftRowOrColumn = (int)(Math.random() * 2);
        if(shiftRowOrColumn == 0)
        {
            shiftNode1No = (int)(Math.random() * (maxRow));
            shiftNode2No = (int)(Math.random() * (maxRow));

            while(shiftNode1No == shiftNode2No)
            {
                shiftNode2No = (int)(Math.random() * (maxRow));
            }
        }
        else
        {
            shiftNode1No = (int)(Math.random() * (maxColumn));
            shiftNode2No = (int)(Math.random() * (maxColumn));

            while(shiftNode1No == shiftNode2No)
            {
                shiftNode2No = (int)(Math.random() * (maxColumn));
            }
        }
        
        shiftParameters[0] = shiftRowOrColumn;
        shiftParameters[1] = shiftNode1No;
        shiftParameters[2] = shiftNode2No;
        
        return shiftParameters;
    }
    
    private void shiftNodes(int layerPosition, Integer node1Index, Integer node2Index)
    {
        ArrayList<Node> nodes = ((ArrayList<Node>)layerToNodesMap.get(positionToLayerMap.get(layerPosition)));
        Node node1 = nodes.get(node1Index);
        Node node2 = nodes.get(node2Index);
        
        float node1X = node1.getNodeData().x();
        float node2X = node2.getNodeData().x();
        
        node1.getNodeData().setX(node2X);
        node2.getNodeData().setX(node1X);

        // Shift nodes' positions
        nodes.remove(node1);
        nodes.remove(node2);
        if(node1Index < node2Index)
        {
            nodes.add(node1Index, node2);
            nodes.add(node2Index, node1);
        }
        else
        {
            nodes.add(node2Index, node1);
            nodes.add(node1Index, node2);
        }
        
    }

    private int calculateEdgeCrossings(Integer[][] matrix)
    {
        int iNumberOfCrossings = 0;
        
        for(int sourceIndex = 0; sourceIndex < matrix.length; sourceIndex++)
        {
            for(int targetIndex = 0; targetIndex < matrix[0].length; targetIndex++)
            {
                if(matrix[sourceIndex][targetIndex] == 1)
                {
                    for(int nextSourceIndex = sourceIndex + 1; nextSourceIndex < matrix.length; nextSourceIndex++)
                    {
                        for(int smallerTargetIndex = 0; smallerTargetIndex < targetIndex; smallerTargetIndex++)
                        {
                            if(matrix[nextSourceIndex][smallerTargetIndex] == 1)
                            {
                                iNumberOfCrossings++;
                            }
                        }
                    }
                }
            }
        }
        
        return iNumberOfCrossings;
    }

    // Returns Shift Parameters : [Shift Source/Target/None(0:1:-1), node1 index, node2 index]
    private Integer[] findBetterDiagonalAlignment(Integer[][] matrix, int aiFirstOrLastMatrix)
    {
        Integer[] shiftParameters = new Integer[3];

        int rows = matrix.length;
        int columns = matrix[0].length;
        
        // Diagonal distance values of rows, if row is changed to ith row
        // [First row in jth row ]
        // [Second row in jth row]
        // [... .. .. . .. . . . ]
        // [ith row in jth row   ]
        int[][] rowExchangeMatrix = new int[rows][rows];

        // Diagonal distance values of columns, if columns is changed to ith column
        // [First column in jth column ]
        // [Second column in jth column]
        // [... .. .. . .. . . .  . .. ]
        // [ith column in jth column   ]
        int[][] columnExchangeMatrix = new int[columns][columns];
       
        // Calculate diagonal distances
        for(int i = 0; i < rows; i++)
        {
            for(int j = 0; j < columns; j++)
            {
                if(matrix[i][j] == 1)
                {
                    // Calculate diagonal distances of row changes
                    // ith row in kth row
                    for(int k = 0; k < rows; k++)
                    {
                        rowExchangeMatrix[i][k] += Math.abs(k - j);
                    }
                    
                    // Calculate diagonal distances of column changes
                    // ith column in kth column
                    for(int k = 0; k < columns; k++)
                    {
                        columnExchangeMatrix[j][k] += Math.abs(k - i);
                    }
                }
            }
        }
        System.out.println("RowExchange");
        printMatrix(rowExchangeMatrix);
        System.out.println("ColumnExchange");
        printMatrix(columnExchangeMatrix);
        
        // [Exchange effect, row1No, row2No]
        Integer[] rowsToBeExchanged = findExchange(rowExchangeMatrix);
        
        // [Exchange effect, column1No, column2No]
        Integer[] columnsToBeExchanged = findExchange(columnExchangeMatrix);

        if(aiFirstOrLastMatrix == FIRST_MATRIX && (rowsToBeExchanged[0] <= 0 || columnsToBeExchanged[0] <= 0))
        {
            if(rowsToBeExchanged[0] < columnsToBeExchanged[0])
            {
                shiftParameters[0] = 0; // source
                shiftParameters[1] = rowsToBeExchanged[1];
                shiftParameters[2] = rowsToBeExchanged[2];
            }
            else if(columnsToBeExchanged[0] <= rowsToBeExchanged[0])
            {
                shiftParameters[0] = 1; // target
                shiftParameters[1] = columnsToBeExchanged[1];
                shiftParameters[2] = columnsToBeExchanged[2];
            }
//            else
//            {
//                if(rows > columns)
//                {
//                    shiftParameters[0] = 0; // source
//                    shiftParameters[1] = rowsToBeExchanged[1];
//                    shiftParameters[2] = rowsToBeExchanged[2];
//                }
//                else
//                {
//                    shiftParameters[0] = 1; // target
//                    shiftParameters[1] = columnsToBeExchanged[1];
//                    shiftParameters[2] = columnsToBeExchanged[2];    
//                }
//            }
        }
        else if(!bReverseApplied && columnsToBeExchanged[0] <= 0)
        {
            shiftParameters[0] = 1; // target
            shiftParameters[1] = columnsToBeExchanged[1];
            shiftParameters[2] = columnsToBeExchanged[2];
        }
        else if(bReverseApplied && rowsToBeExchanged[0] <= 0)
        {
            shiftParameters[0] = 0; // target
            shiftParameters[1] = rowsToBeExchanged[1];
            shiftParameters[2] = rowsToBeExchanged[2];
        }
        else
        {
            //Both equal to 1 indicating no change
            shiftParameters[0] = -1;
        }
        
        return shiftParameters;
    }

    // Returns [Exchange effect, row/column1No, row/column2No]
    // Exchange effect = 1 if no change 
    private Integer[] findExchange(int[][] exchangeMatrix)
    {    
        Integer [] exchangeInfo = new Integer[3];
        int length = exchangeMatrix.length;
        
        // init as change not applied
        exchangeInfo[0] = 1; // Positive value
        
        for (int i = 0; i < length; i++)
        {
            for (int j = i + 1; j < length; j++)
            {
                int exchangeDiff = exchangeMatrix[i][j] + exchangeMatrix[j][i]
                        - exchangeMatrix[i][i] - exchangeMatrix[j][j];
                if(exchangeDiff < exchangeInfo[0])
                {
                    exchangeInfo[0] = exchangeDiff;
                    exchangeInfo[1] = i;
                    exchangeInfo[2] = j;
                }
            }
        }
       
        return exchangeInfo;
    }

    private void applyShiftToMatrix(Integer[][] matrix, Integer[] shiftParameters)
    {
        int node1Index = shiftParameters[1];
        int node2Index = shiftParameters[2];
        
        if(shiftParameters[0] == 0) // source (row shift)
        {
            Integer[] firstRow = matrix[node1Index].clone();
            matrix[node1Index] = matrix[node2Index].clone();
            matrix[node2Index] = firstRow.clone();
        }
        else // target (column shift)
        {
            Integer node1Value;
            for (int i = 0; i < matrix.length; i++)
            {
                node1Value = matrix[i][node1Index];
                matrix[i][node1Index] = matrix[i][node2Index];
                matrix[i][node2Index] = node1Value;
            }
        }
                
        printMatrix(matrix);
    }

    private void printMatrix(Integer[][] matrix)
    {
        System.out.println("-----------------");
        for (int i = 0; i < matrix.length; i++)
        {
            for (int j = 0; j < matrix[0].length; j++)
            {
                System.out.print(matrix[i][j] + " ");
            }
            System.out.println("");
        }
        System.out.println("-----------------");
    }

    private void printMatrix(int[][] matrix)
    {
        System.out.println("-----------------");
        for (int i = 0; i < matrix.length; i++)
        {
            for (int j = 0; j < matrix[0].length; j++)
            {
                System.out.print(matrix[i][j] + " ");
            }
            System.out.println("");
        }
        System.out.println("-----------------");
    }

    private void resetAll()
    {
        bLayersConstructed = false;
        bReverseApplied = false;
        layerToNodesMap.clear();
        layerToLayerConnections.clear();
        positionToLayerMap.clear();
        adjacencyMatrixes.clear();
        firstLayer = null;
        orderList.clear();
    }

    private int calculateTotalEdgeCrossings()
    {
        int iTotalEdgeCrossings = 0;
        for (Integer[][] matrix : adjacencyMatrixes)
        {
            iTotalEdgeCrossings += calculateEdgeCrossings(matrix);
        }
        return iTotalEdgeCrossings;
    }

    private void copyContent(Integer[][] matrixWithNewAlignment, Integer[][] matrix)
    {
        for(int i = 0; i < matrix.length; i++)
        {
            for(int j = 0; j < matrix[0].length; j++)
            {
                matrixWithNewAlignment[i][j] = matrix[i][j];
            }
        }
    }

    /**
     * To construct matrixes between layers there must be at most two
     * adjacent layers. This method finds the minimal connections which
     * satisfies that all layers have at most two adjacent layers
     * and sets it in positionToLayer map.
     * For layers having more than two adjacent layers the least connected
     * layers are omitted as if they are not connected.
     */
    private void setMinimalInterconnectionsBetweenLayers()
    {
        HashMap<Integer, Object> minExtraEdgeCountOrder = null;
        List<Map> tempPositionToLayerMapList = null;
        
        int iMinExtraEdgeCount = Integer.MAX_VALUE;
        
        Object[] layersSet = (Object[]) layerToLayerConnections.keySet().toArray();
         
        for (Object layer : layersSet)
        {
            tempPositionToLayerMapList = findPossibleOrders(layer);
            
            HashMap<Integer, Object> currentMinExtraEdgeCountOrder = findMinEkstraEdgeCountOrder(tempPositionToLayerMapList);
            
            int iCurrentExtraEdgeCount = countExtraEdges(currentMinExtraEdgeCountOrder);
            if(iCurrentExtraEdgeCount < iMinExtraEdgeCount)
            {
                minExtraEdgeCountOrder = currentMinExtraEdgeCountOrder;
            }
        }
        
        if(minExtraEdgeCountOrder != null)
        {
            positionToLayerMap = minExtraEdgeCountOrder;
        }
    }
    
    /** 
     * Try to find an order that has the given layer parameter on top and can cover
     * all layers. All layers need to have connection between their bottom layer.
     * @param topLayer
     * @return List<HashMap<Integer, Object>> : list of possible orders
     * of layers if can find order, null otherwise
     */
    private List<Map> findPossibleOrders(Object topLayer)
    {
        HashMap<Integer, Object> beginningOrder = new HashMap<Integer, Object>();
        beginningOrder.put(0, topLayer);
        appendToOrder(beginningOrder);
        
        return orderList;
    }

    /**
     * Foreach next available layers called 'nextLayer', appends 'nextLayer' to
     * the end of the currentOrder and if this number of extra edges is less than the the previous orders listed in
     * orderList, adds  .
     * 
     * @param currentOrder 
     */
    private void appendToOrder(HashMap<Integer, Object> currentOrder)
    {
        // Limit possible order list up to 100
        if(orderList.size() >= 100)
        {
            return;
        }
        
        Object currentBottomLayer = (Object)currentOrder.get(currentOrder.size() - 1);
        
        List<Object> nextLayers = (List<Object>)layerToLayerConnections.get(currentBottomLayer);
        
        for(Object nextLayer : nextLayers)
        {
            if(!currentOrder.containsValue(nextLayer))
            {
                HashMap<Integer, Object> layerAddedOrder = (HashMap<Integer, Object>)currentOrder.clone();
                layerAddedOrder.put(currentOrder.size(), nextLayer);
                
                if(layerAddedOrder.size() == layerToNodesMap.size())
                {
                    int iExtraEdgeCount = countExtraEdges(layerAddedOrder);
                    if(iExtraEdgeCount < iMinExtraEdgeCountOfOrder)
                    {
                        orderList.add((Map)layerAddedOrder.clone());
                    }
                }
                else
                {
                    appendToOrder(layerAddedOrder);
                }
            }
        }    
    }

    private HashMap<Integer, Object> findMinEkstraEdgeCountOrder(List<Map> positionToLayerMapList)
    {
        int iMinExtraEdgeCount = Integer.MAX_VALUE;
        int iOrderIndex = 0;
        int iCurrentIndex = 0;
        for(; iCurrentIndex < positionToLayerMapList.size(); iCurrentIndex++)
        {
            HashMap<Integer, Object> currentOrderMap = (HashMap<Integer, Object>) positionToLayerMapList.get(iCurrentIndex);
            
            int iCurrentExtraEdgeCount = countExtraEdges(currentOrderMap);
            if(iCurrentExtraEdgeCount < iMinExtraEdgeCount)
            {
                iMinExtraEdgeCount = iCurrentExtraEdgeCount;
                iOrderIndex = iCurrentIndex;
            }
        }
        return (HashMap<Integer, Object>) positionToLayerMapList.get(iOrderIndex);
    }

    private int countExtraEdges(HashMap<Integer, Object> orderMap)
    {
        int iExtraEdgeCount = 0;
        for (int iCurrentPos = 0; iCurrentPos < orderMap.size(); iCurrentPos++)
        {
            Integer position = (Integer)iCurrentPos;
            Object currentLayer = orderMap.get(position);

            Object upperLayer = orderMap.get(position - 1);
            Object bottomLayer = orderMap.get(position + 1);
            
            List<Object> connectedLayers = (List<Object>) layerToLayerConnections.get(currentLayer);
            for(Object connectedLayer : connectedLayers)
            {
                // If connectedLayer is not adjacent, count its edge with this layer
                if((upperLayer != null && upperLayer.equals(connectedLayer))
                    || (bottomLayer!= null && bottomLayer.equals(connectedLayer)))
                {
                    continue;
                }
                else
                {
                    boolean bEdgesCountedBefore = false;
                    
                    for(int i = 0; i < position; i++)
                    {
                        if(orderMap.get(i).equals(connectedLayer))
                        {
                            bEdgesCountedBefore = true;
                            break;
                        }
                    }
                    
                    if(!bEdgesCountedBefore)
                    {
                        iExtraEdgeCount += countEdgesBetweenLayers(currentLayer, connectedLayer);
                    }
                }
            }
        }
        
        return iExtraEdgeCount;
    }

    private int countEdgesBetweenLayers(Object currentLayer, Object connectedLayer)
    {
        int iEdgeCount = 0;
        for(Node sourceNode : (ArrayList<Node>)layerToNodesMap.get(currentLayer))
        {
            for(Node targetNode : (ArrayList<Node>)layerToNodesMap.get(connectedLayer))
            {
                if(graph.getEdge(sourceNode, targetNode) != null)
                {
                    iEdgeCount++;
                }
            }
        }
        
        return iEdgeCount;
    }

    private void applyReverse()
    {
        bReverseApplied = true;
    }
    
}
