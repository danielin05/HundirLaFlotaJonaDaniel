package com.client;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import javafx.scene.control.Button;
import org.jline.reader.impl.completer.SystemCompleter;
import org.json.JSONObject;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.ArrayList;


import static com.server.Main.PLAYER_NAMES;
import static com.server.Main.readyStatus;

public class CtrlPlay implements Initializable {

    public Button botonListo;
    @FXML
    private Canvas canvas;
    private GraphicsContext gc;
    private Boolean showFPS = false;
    private Boolean readyA = false; // Declaramos la variable ready de player A
    private Boolean readyB = false; // Declaramos la variable ready de player B

    private PlayTimer animationTimer;
    private PlayGrid grid;

    public Map<String, JSONObject> clientMousePositions = new HashMap<>();
    private Boolean mouseDragging = false;
    private double mouseOffsetX, mouseOffsetY;

    public static Map<String, JSONObject> selectableObjects = new HashMap<>();
    private String selectedObject = "";
    private static String clientId;

    public boolean playersReady = false;

    public String turnoDe;

    private Map<String, Integer> remainingHits = new HashMap();

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        // Get drawing context
        this.gc = canvas.getGraphicsContext2D();

        // Set listeners
        UtilsViews.parentContainer.heightProperty().addListener((observable, oldValue, newvalue) -> { onSizeChanged(); });
        UtilsViews.parentContainer.widthProperty().addListener((observable, oldValue, newvalue) -> { onSizeChanged(); });
        
        canvas.setOnMouseMoved(this::setOnMouseMoved);
        canvas.setOnMousePressed(this::onMousePressed);
        canvas.setOnMouseDragged(this::onMouseDragged);
        canvas.setOnMouseReleased(this::onMouseReleased);

        turnoDe = PLAYER_NAMES.get(0);

        remainingHits.put(PLAYER_NAMES.get(0), 17);
        remainingHits.put(PLAYER_NAMES.get(1), 17);

        // Define grid
        grid = new PlayGrid(25, 25, 25, 10, 10);

        // Start run/draw timer bucle
        animationTimer = new PlayTimer(this::run, this::draw, 0);
        start();
    }

    String nameA = PLAYER_NAMES.get(0);
    String nameB = PLAYER_NAMES.get(1);
    @FXML
    private void readyButton() {
        boolean todosDentroA = true; // Para jugador A
        boolean todosDentroB = true; // Para jugador B


        // Verificamos los barcos de cada jugador
        for (String objectId : selectableObjects.keySet()) {
            JSONObject selectableObject = selectableObjects.get(objectId);

            // Verificar barcos del jugador A
            if (selectableObject.getString("player").equals(nameA)) {
                if (selectableObject.getNumber("x").equals(selectableObject.getNumber("initialX")) && 
                    selectableObject.getNumber("y").equals(selectableObject.getNumber("initialY"))) {
                    todosDentroA = false;
                }
            } 
            // Verificar barcos del jugador A
            if (selectableObject.getString("player").equals(nameB)) {
                if (selectableObject.getNumber("x").equals(selectableObject.getNumber("initialX")) && 
                    selectableObject.getNumber("y").equals(selectableObject.getNumber("initialY"))) {
                    todosDentroB = false;
                }
            } 
        }

        // Acciones según el jugador que presiona el botón
        if (nameA.equals(clientId)) {
            if (todosDentroA) {
                readyA = true;
                System.out.println("Cliente A listo");
                enviarMensajeListoAlServidor();
                lockPlayers();
                botonListo.setDisable(true);
            } else {
                System.out.println("Cliente A no listo, hay barcos en su posición inicial");
            }
        } else if (nameB.equals(clientId)) {
            if (todosDentroB) {
                readyB = true;
                System.out.println("Cliente B listo");
                enviarMensajeListoAlServidor();
                lockPlayers();
                botonListo.setDisable(true);
            } else {
                System.out.println("Cliente B no listo, hay barcos en su posición inicial");
            }
        }
    }

    // Método para enviar mensaje de "listo" al servidor en CtrlPlay.java
    private void enviarMensajeListoAlServidor() {
        // Envía el mensaje de "listo" al servidor usando el cliente WebSocket
        Main.wsClient.safeSend(new JSONObject().put("type", "clienteListo").put("clientId", Main.clientId).toString());
    }

    // Este método puede usarse para bloquear la interacción de los jugadores
    private void lockPlayers() {
        canvas.setOnMouseDragged(null);
        canvas.setOnMouseReleased(null);
    }

    // When window changes its size
    public void onSizeChanged() {

        double width = UtilsViews.parentContainer.getWidth();
        double height = UtilsViews.parentContainer.getHeight();
        canvas.setWidth(width);
        canvas.setHeight(height);
    }

    // Start animation timer
    public void start() {
        animationTimer.start();
    }

    // Stop animation timer
    public void stop() {
        animationTimer.stop();
    }

    private void setOnMouseMoved(MouseEvent event) {
        double mouseX = event.getX();
        double mouseY = event.getY();

        JSONObject newPosition = new JSONObject();
        newPosition.put("x", mouseX);
        newPosition.put("y", mouseY);
        if (grid.isPositionInsideGrid(mouseX, mouseY)) {                
            newPosition.put("col", grid.getCol(mouseX));
            newPosition.put("row", grid.getRow(mouseY));
        } else {
            newPosition.put("col", -1);
            newPosition.put("row", -1);
        }
        clientMousePositions.put(Main.clientId, newPosition);

        JSONObject msgObj = clientMousePositions.get(Main.clientId);
        msgObj.put("type", "clientMouseMoving");
        msgObj.put("clientId", Main.clientId);
    
        if (Main.wsClient != null) {
            Main.wsClient.safeSend(msgObj.toString());
        }
    }


    private int initialX, initialY;
    private List<String> clickedCells = new ArrayList<>();

    private void onMousePressed(MouseEvent event) {
        double mouseX = event.getX();
        double mouseY = event.getY();

        selectedObject = "";
        mouseDragging = false;
        
        int col = grid.getCol(mouseX);
        int row = grid.getRow(mouseY);
        
        // Verificar si la casilla está dentro de los límites de la cuadrícula
        if (grid.isPositionInsideGrid(mouseX, mouseY)) {
            String cellKey = col + "," + row;

            if (playersReady){
                // Solo permite clics si es el turno del cliente
                if (!turnoDe.equals(clientId)) {
                    System.out.println("No es el turno del cliente, espere su turno.");
                    return;
                }

                // Verifica si la casilla ya ha sido clicada
                if (clickedCells.contains(cellKey)) {
                    System.out.println("Casilla ya seleccionada: Columna " + col + ", Fila " + row);
                    return; // Salir si ya está en la lista
                }

                // Añadir la casilla a la lista y registrar el clic
                clickedCells.add(cellKey);

                // Imprimir la casilla en la que el cliente ha hecho clic
                System.out.println("Cliente " + clientId + " ha hecho clic en la casilla: Columna " + col + ", Fila " + row);
    
            
                // Verificar si hay un barco del otro cliente en esta casilla
                for (String objectId : selectableObjects.keySet()) {
                    JSONObject obj = selectableObjects.get(objectId);

                    // Asegurarse de que el objeto tenga las propiedades necesarias
                    if (obj.has("player") && obj.has("col") && obj.has("row") && obj.has("cols") && obj.has("rows")) {
                        String player = obj.getString("player");

                        // Solo verifica objetos que pertenecen al otro cliente
                        if (!player.equals(this.clientId)) {
                            int objCol = obj.getInt("col");
                            int objRow = obj.getInt("row");
                            int cols = obj.getInt("cols");
                            int rows = obj.getInt("rows");

                            // Comprobar si la casilla seleccionada cae dentro del área ocupada por el barco del otro cliente
                            if (col >= objCol && col < objCol + cols && row >= objRow && row < objRow + rows) {
                                System.out.println("Cliente " + clientId + " ha hecho clic en una casilla con un barco del cliente " + player);
                                pintarCasillaImpacto(col, row); // Pinta la casilla de naranja al impactar
                                remainingHits.put(clientId, remainingHits.get(clientId) - 1);
                                System.out.println("El cliente: " + clientId + " tiene que tocar " + remainingHits.get(clientId) + " veces mas para ganar!");
                                break;
                            }
                        }
                    }
                }
                enviarMensajeClicAlServidor(col, row);
            }
        }
        // Iterar sobre selectableObjects para detectar si se ha seleccionado un objeto propio
        for (String objectId : selectableObjects.keySet()) {
            JSONObject obj = selectableObjects.get(objectId);
            if (obj.has("x") && obj.has("y") && obj.has("cols") && obj.has("rows") && obj.has("initialX") && obj.has("initialY")) {
                int objX = obj.getInt("x");
                int objY = obj.getInt("y");
                int cols = obj.getInt("cols");
                int rows = obj.getInt("rows");
                initialX = obj.getInt("initialX");
                initialY = obj.getInt("initialY");
    
                if (isPositionInsideObject(mouseX, mouseY, objX, objY, cols, rows)) {
                    if (event.isPrimaryButtonDown() && obj.getString("player").equals(this.clientId)) {
                        selectedObject = objectId;
                        mouseDragging = true;
                        mouseOffsetX = event.getX() - objX;
                        mouseOffsetY = event.getY() - objY;
                        break;
                    }
                }
            }
        }
    }
    
    // Método para enviar el clic y solicitar cambio de turno
    private void enviarMensajeClicAlServidor(int col, int row) {
        JSONObject msgObj = new JSONObject();
        msgObj.put("type", "clientClick");
        msgObj.put("clientId", clientId);

        if (Main.wsClient != null) {
            Main.wsClient.safeSend(msgObj.toString());
        }
    }

    // Método para pintar la casilla de impacto
    private void pintarCasillaImpacto(int col, int row) {
        gc.setFill(Color.ORANGE); // Color de impacto
        gc.fillRect(grid.getCellX(col), grid.getCellY(row), grid.getCellSize(), grid.getCellSize());
    }

    public static void setClientId(String clientId) {
        CtrlPlay.clientId = clientId;
    }

    private void onMouseDragged(MouseEvent event) {
        if (mouseDragging) {
            JSONObject obj = selectableObjects.get(selectedObject);
            double objX = event.getX() - mouseOffsetX;
            double objY = event.getY() - mouseOffsetY;
            
            obj.put("x", objX);
            obj.put("y", objY);
            obj.put("col", grid.getCol(objX));
            obj.put("row", grid.getRow(objY));

            JSONObject msgObj = selectableObjects.get(selectedObject);
            msgObj.put("type", "clientSelectableObjectMoving");
            msgObj.put("objectId", obj.getString("objectId"));
        
            if (Main.wsClient != null) {
                Main.wsClient.safeSend(msgObj.toString());
            }
        }
        setOnMouseMoved(event);
    }

    private void onMouseReleased(MouseEvent event) {
        if (!selectedObject.isEmpty()) {
            JSONObject obj = selectableObjects.get(selectedObject);
            int objCol = obj.getInt("col");
            int objRow = obj.getInt("row");
            int cols = obj.getInt("cols");
            int rows = obj.getInt("rows");
    
            if (isCompletelyInsideGrid(objCol, objRow, cols, rows) &&
                !isOverlapping(objCol, objRow, cols, rows, selectedObject)) {
                obj.put("x", grid.getCellX(objCol));
                obj.put("y", grid.getCellY(objRow));
            } else {
                obj.put("x", initialX);
                obj.put("y", initialY);
            }
    
            JSONObject msgObj = selectableObjects.get(selectedObject);
            msgObj.put("type", "clientSelectableObjectMoving");
            msgObj.put("objectId", obj.getString("objectId"));
    
            if (Main.wsClient != null) {
                Main.wsClient.safeSend(msgObj.toString());
            }
    
            mouseDragging = false;
            selectedObject = "";
        }
    }

    private boolean isCompletelyInsideGrid(int startCol, int startRow, int cols, int rows) {
        // Verifica si el barco está completamente dentro de los límites del grid
        return startCol >= 0 && startRow >= 0 &&
               (startCol + cols) <= grid.getCols() &&
               (startRow + rows) <= grid.getRows();
    }
    
    
    // Método para verificar la superposición
    private boolean isOverlapping(int startCol, int startRow, int cols, int rows, String currentObjectId) {
        for (String objectId : selectableObjects.keySet()) {
            if (!objectId.equals(currentObjectId) && selectableObjects.get(objectId).getString("player").equals(clientId)) {
                JSONObject otherObj = selectableObjects.get(objectId);
                
                // Verificar que 'col' y 'row' existan en el objeto
                if (!otherObj.has("col") || !otherObj.has("row")) {
                    continue; // Saltar este objeto si no tiene coordenadas válidas
                }
    
                int otherCol = otherObj.getInt("col");
                int otherRow = otherObj.getInt("row");
                int otherCols = otherObj.getInt("cols");
                int otherRows = otherObj.getInt("rows");
    
                // Verificar si alguna de las celdas del barco actual se solapa con las del otro barco
                for (int col = startCol; col < startCol + cols; col++) {
                    for (int row = startRow; row < startRow + rows; row++) {
                        if (col >= otherCol && col < otherCol + otherCols &&
                            row >= otherRow && row < otherRow + otherRows) {
                            return true; // Hay superposición
                        }
                    }
                }
            }
        }
        return false;
    }

    public void setPlayersMousePositions(JSONObject positions) {
        clientMousePositions.clear();
        for (String clientId : positions.keySet()) {
            JSONObject positionObject = positions.getJSONObject(clientId);
            clientMousePositions.put(clientId, positionObject);
        }
    }

    public void setSelectableObjects(JSONObject objects) {
        selectableObjects.clear();
        for (String objectId : objects.keySet()) {
            JSONObject positionObject = objects.getJSONObject(objectId);
            selectableObjects.put(objectId, positionObject);
        }
    }

    public Boolean isPositionInsideObject(double positionX, double positionY, int objX, int objY, int cols, int rows) {
        double cellSize = grid.getCellSize();
        double objectWidth = cols * cellSize;
        double objectHeight = rows * cellSize;

        double objectLeftX = objX;
        double objectRightX = objX + objectWidth;
        double objectTopY = objY;
        double objectBottomY = objY + objectHeight;

        return positionX >= objectLeftX && positionX < objectRightX &&
               positionY >= objectTopY && positionY < objectBottomY;
    }

    // Run game (and animations)
    private void run(double fps) {

        if (animationTimer.fps < 1) { return; }

        // Update objects and animations here
    }

    // Draw game to canvas
    public void draw() {

        // Clean drawing area
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Draw colored 'over' cells

        for (String clientId : clientMousePositions.keySet()) {
            JSONObject position = clientMousePositions.get(clientId);

            int col = position.getInt("col");
            int row = position.getInt("row");

            // Comprovar si està dins dels límits de la graella
            if (row >= 0 && col >= 0 && playersReady) {
                if (nameA.equals(clientId)) {
                    gc.setFill(Color.LIGHTBLUE); 
                } else {
                    gc.setFill(Color.LIGHTGREEN); 
                }
                // Emplenar la casella amb el color clar
                gc.fillRect(grid.getCellX(col), grid.getCellY(row), grid.getCellSize(), grid.getCellSize());
            }
        }

        // Draw grid
        drawGrid();

        // Draw selectable objects
        for (String objectId : selectableObjects.keySet()) {
            JSONObject selectableObject = selectableObjects.get(objectId);
            if (selectableObject.getString("player").equals(clientId)){
                drawSelectableObject(objectId, selectableObject);
            }
        }

        // Draw mouse circles
        if (playersReady) {    
            for (String clientId : clientMousePositions.keySet()) {
                JSONObject position = clientMousePositions.get(clientId);
                if (nameA.equals(clientId)) {
                    gc.setFill(Color.BLUE);
                } else if (nameB.equals(clientId)){
                    gc.setFill(Color.GREEN); 
                }
                gc.fillOval(position.getInt("x") - 5, position.getInt("y") - 5, 10, 10);
            }
        }

        // Draw FPS if needed
        if (showFPS) { animationTimer.drawFPS(gc); }   
    }

    public void drawGrid() {
        gc.setStroke(Color.BLACK);

        for (int row = 0; row < grid.getRows(); row++) {
            for (int col = 0; col < grid.getCols(); col++) {
                double cellSize = grid.getCellSize();
                double x = grid.getStartX() + col * cellSize;
                double y = grid.getStartY() + row * cellSize;
                gc.strokeRect(x, y, cellSize, cellSize);
            }
        }
    }

    public void drawSelectableObject(String objectId, JSONObject obj) {
        double cellSize = grid.getCellSize();

        int x = obj.getInt("x");
        int y = obj.getInt("y");
        double width = obj.getInt("cols") * cellSize;
        double height = obj.getInt("rows") * cellSize;

        // Seleccionar un color basat en l'objectId
        Color color;
        if (selectableObjects.get(objectId).getString("player").equals(nameA)) {
            color = Color.LIGHTBLUE;
        }else if (selectableObjects.get(objectId).getString("player").equals(nameB)){
            color = Color.LIGHTGREEN;
        }else{
            color = Color.GRAY;
        }

        // Dibuixar el rectangle
        gc.setFill(color);
        gc.fillRect(x, y, width, height);

        // Dibuixar el contorn
        gc.setStroke(Color.BLACK);
        gc.strokeRect(x, y, width, height);

        // Opcionalment, afegir text (per exemple, l'objectId)
        gc.setFill(Color.BLACK);
        gc.fillText(objectId, x + 5, y + 15);
    }
}
