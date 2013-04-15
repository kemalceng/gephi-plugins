/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cna.myfirstplugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.gephi.data.attributes.api.AttributeController;
import org.gephi.data.attributes.api.AttributeModel;
import org.gephi.data.attributes.api.AttributeType;
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

/**
 *
 * @author kemalbeskardesler
 */
public class MultipartiteLayout implements Layout, LongTask {
    
    public static String COLUMN_LAYER_NO = "LayerNo";
    private boolean cancel = false;
    private boolean executing = false;
    private ProgressTicket progressTicket;
    private final LayoutBuilder multipartiteLayoutBuilder;
    private GraphModel graphModel;
    private HierarchicalGraph graph;
//    LayoutProperty mySpeedProperty;
    float speed;
    private int areaSize;
    int topLayerPosition = 0;
    
    Map layerToNodesMap = new HashMap<Integer, List<Node>>();
    Map layerToLayerConnections = new HashMap<Integer, List<Integer>>();
    Map positionToLayerMap = new HashMap<Integer, Integer>();
    
    ArrayList<Integer[][]> adjacencyMatrixes = new ArrayList<Integer[][]>();
    
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
    }

    @Override
    public void setGraphModel(GraphModel graphModel)
    {
        this.graphModel = graphModel;
        
        // Add LayerNo attribute to nodes
        AttributeModel am = Lookup.getDefault().lookup(AttributeController.class).getModel();
        if(!am.getNodeTable().hasColumn(COLUMN_LAYER_NO))
        {
            am.getNodeTable().addColumn(COLUMN_LAYER_NO, AttributeType.INT);
        }
        graph = this.graphModel.getHierarchicalGraphVisible();
        // Organize graph into layers
        constructLayers();
    }

    @Override
    public void goAlgo() {
        
        graph.readLock();
        
        changeAlignment();
        
        graph.readUnlock();
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

    // Starting from layer 0, find suitable layer for the node.
    public void assignLayerNo(Node node)
    {
        Integer currentLayerNo = 0;
        
        Edge[] edges = graph.getEdges(node).toArray();
        Attributes currentAtts = node.getAttributes();
        
        // ALways null - no need to check
        if(currentAtts.getValue(COLUMN_LAYER_NO) == null)
        {
            for(int i = 0; i<edges.length; i++)
            {
                Edge edge = edges[i];
                Integer adjacentLayerNo = (Integer)edge.getTarget().getAttributes().getValue(COLUMN_LAYER_NO);
                
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
    
    private void constructLayers() {
        
        graph.readLock();
        int nodeCount = graph.getNodeCount();
        Node[] nodes = graph.getNodes().toArray();
        Edge[] edges = graph.getEdges().toArray();
        
        float nextXCoordinate = 0F;
        float nextYCoordinate = 0F;
 
        for (int i = 0; i < nodeCount; i++)
        {
            assignLayerNo(nodes[i]);
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
        graph.readUnlock();
        
    }

    private void constructMatrixes()
    {
        determineLayerConnections();
        setLayerPositions();
        
        // From top to bottom create matrixes
        //(topLayerPosition is the minimum layer position value)
        createDefaultMatrixes();
    }

    private void determineLayerConnections()
    {
        Object[] layersSet = (Object[]) layerToNodesMap.keySet().toArray();

        for(int sourceLayerIndex = 0; sourceLayerIndex < layersSet.length; sourceLayerIndex++)
        {
            Integer sourceLayerNo = (Integer)layersSet[sourceLayerIndex];
            
            List<Node> nodesInSourceLayer = (List<Node>)layerToNodesMap.get(sourceLayerNo);
            
            for(int targetLayerIndex = sourceLayerIndex + 1; targetLayerIndex < layersSet.length; targetLayerIndex++)
            {
                Integer targetLayerNo = (Integer)layersSet[targetLayerIndex];
            
                if(sourceLayerNo == targetLayerNo)
                {
                    continue; // Buraya girmemesi lazim kontrol et
                }
                
                List<Node> nodesInTargetLayer = (List<Node>)layerToNodesMap.get(targetLayerNo);
                
                // Set which layer connects to which layer
                if(isThereAnyConnectionBtwnLayers(nodesInSourceLayer, nodesInTargetLayer))
                {
                    if(!layerToLayerConnections.containsKey(sourceLayerNo))
                    {
                        layerToLayerConnections.put(sourceLayerNo, new ArrayList<Integer>());
                    }
                    ((ArrayList)layerToLayerConnections.get(sourceLayerNo)).add(targetLayerNo);
                }
            }
        }
    }

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
        
//        for(Node sourceNode : nodesInSourceLayer)
//        {
//            for(Edge edge : graph.getEdges(sourceNode).toArray())
//            {
//                if(nodesInTargetLayer.contains(edge.getTarget())
//                        || nodesInTargetLayer.contains(edge.getSource()))
//                {
//                    return true;
//                }
//            }
//        }
        
        return bConnectionExists;
    }

    private void setLayerPositions()
    {    
        setTargetLayerPositions((Integer)0, 0);
    }
    
    private void setTargetLayerPositions(int aiLayerPosition, Integer sourceLayerNo)
    {   
        if(aiLayerPosition < topLayerPosition)
        {
            topLayerPosition = aiLayerPosition;
        }
        positionToLayerMap.put(aiLayerPosition, sourceLayerNo);
                
        List<Integer> targetLayers = (List<Integer>)layerToLayerConnections.get(sourceLayerNo);

        if(targetLayers == null)
        {
            return;
        }
        
        int orientationUpOrDown = aiLayerPosition >= 0 ? 1 : -1;
        for(Integer targetLayerNo : targetLayers)
        {
            setTargetLayerPositions(aiLayerPosition + orientationUpOrDown, targetLayerNo);
            orientationUpOrDown *= -1;
        }
    }

    private void createDefaultMatrixes()
    {
        int nodeYPosition = topLayerPosition;
        int nodeXPosition = 0;
        
        for(int i = 0; i < positionToLayerMap.size(); i++)
        {
            Object[] sourceLayerNodes = ((List<Node>)layerToNodesMap.get((Integer)positionToLayerMap.get(nodeYPosition))).toArray();
            
            // Set nodes' positions
            nodeXPosition = 0;
            for(Object node : sourceLayerNodes)
            {
                ((Node)node).getNodeData().setY(nodeYPosition * -50);
                ((Node)node).getNodeData().setX(nodeXPosition * 50);
                nodeXPosition++;
            }
            
            if(nodeYPosition + 1 < positionToLayerMap.size())
            {
                // Create Matrix
                Object[] targetLayerNodes = ((List<Node>)layerToNodesMap.get((Integer)positionToLayerMap.get(nodeYPosition + 1))).toArray();
                
                Integer[][] matrix = convertLayersToMatrix(sourceLayerNodes, targetLayerNodes);
                
                adjacencyMatrixes.add(matrix);
            }
            
            nodeYPosition++;
        }
    }

    private Integer[][] convertLayersToMatrix(Object[] sourceLayerNodes, Object[] targetLayerNodes) {
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
        for(int i = 0; i < adjacencyMatrixes.size(); i++)
        {
            changeMatrixAlignment(adjacencyMatrixes.get(i), topLayerPosition + i);
        }
    }

    private void changeMatrixAlignment(Integer[][] matrix, int sourceLayerPosition)
    {
        Integer[][] matrixWithNewAlignment = matrix.clone();
        
        Integer[] shiftParameters = findShiftWithLessEdgeCrossings(matrixWithNewAlignment);
        
        // TODO: Total Edge crossing hesaplanınca kaldır burayı
        int edgeCrossings = 1; //calculateTotalEdgeCrossings(matrix);
        
        // TODO: matrixWithNewAlignment değiştirilmeden geliyor
        if(shiftParameters[0] != -1)
        {
            applyShiftToMatrix(matrixWithNewAlignment, shiftParameters);
            
            if(calculateTotalEdgeCrossings(matrixWithNewAlignment) < edgeCrossings)
            {
                // add 1 if shift nodes in target layer
                int sourceOrTargetLayer = shiftParameters[0];
                shiftNodes(sourceLayerPosition + sourceOrTargetLayer, shiftParameters[1], shiftParameters[2]);
                matrix = matrixWithNewAlignment.clone();
                
                // If nodes shifted in target layer and there are more matrixes in lower layers 
                if(sourceOrTargetLayer == 1 && sourceLayerPosition + 1 < adjacencyMatrixes.size())
                {
                    shiftParameters[0] = 0;
                    applyShiftToMatrix(adjacencyMatrixes.get(sourceLayerPosition + 1), shiftParameters);
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
    }

    private Integer[] findShiftWithLessEdgeCrossings(Integer[][] matrix)
    {
        //return generateRandomShift(matrix);
        
        Integer[] shiftParameters = findBetterDiagonalAlignment(matrix);
        
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
        Node node1 = ((List<Node>)layerToNodesMap.get(layerPosition)).get(node1Index);
        Node node2 = ((List<Node>)layerToNodesMap.get(layerPosition)).get(node2Index);
        
        float node1X = node1.getNodeData().x();
        float node2X = node2.getNodeData().x();
        
        node1.getNodeData().setX(node2X);
        node2.getNodeData().setX(node1X);
    }

    private int calculateTotalEdgeCrossings(Integer[][] matrix) {
        return 0;
    }

    // Returns Shift Parameters : [Shift Source/Target/None(0:1:-1), node1 index, node2 index]
    private Integer[] findBetterDiagonalAlignment(Integer[][] matrix)
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
        
        // [Exchange effect, row1No, row2No]
        Integer[] rowsToBeExchanged = findExchange(rowExchangeMatrix);
        
        // [Exchange effect, column1No, column2No]
        Integer[] columnsToBeExchanged = findExchange(columnExchangeMatrix);

        if(rowsToBeExchanged[0] != -1 && columnsToBeExchanged[0] != -1)
        {
            if(rowsToBeExchanged[0] < columnsToBeExchanged[0])
            {
                shiftParameters[0] = 0; // source
                shiftParameters[1] = rowsToBeExchanged[1];
                shiftParameters[2] = rowsToBeExchanged[2];
            }
            else if(columnsToBeExchanged[0] < rowsToBeExchanged[0])
            {
                shiftParameters[0] = 1; // target
                shiftParameters[1] = columnsToBeExchanged[1];
                shiftParameters[2] = columnsToBeExchanged[2];
            }
            else
            {
                if(rows > columns)
                {
                    shiftParameters[0] = 0; // source
                    shiftParameters[1] = rowsToBeExchanged[1];
                    shiftParameters[2] = rowsToBeExchanged[2];
                }
                else
                {
                    shiftParameters[0] = 1; // target
                    shiftParameters[1] = columnsToBeExchanged[1];
                    shiftParameters[2] = columnsToBeExchanged[2];    
                }
            }
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
    }
}
