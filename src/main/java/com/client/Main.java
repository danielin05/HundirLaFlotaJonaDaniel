package com.client;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.scene.paint.Color;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.util.Duration;

import static com.server.Main.PLAYER_NAMES;

public class Main extends Application {

    public static UtilsWS wsClient;

    public static String clientId = "";
    public static CtrlConfig ctrlConfig;
    public static CtrlWait ctrlWait;
    public static CtrlPlay ctrlPlay;
    public static CtrlPlayGame ctrlPlayGame;
    public static CtrlWinner ctrlWinner;
    public Scene scene;

    public static void main(String[] args) {

        // Iniciar app JavaFX   
        launch(args);
    }
    
    @Override
    public void start(Stage stage) throws Exception {

        final int windowWidth = 400;
        final int windowHeight = 300;

        UtilsViews.parentContainer.setStyle("-fx-font: 14 arial;");
        UtilsViews.addView(getClass(), "ViewConfig", "/assets/viewConfig.fxml"); 
        UtilsViews.addView(getClass(), "ViewWait", "/assets/viewWait.fxml");
        UtilsViews.addView(getClass(), "ViewPlay", "/assets/viewPlay.fxml");
        UtilsViews.addView(getClass(), "ViewPlayGame", "/assets/viewPlayGame.fxml");
        UtilsViews.addView(getClass(), "ViewWinner", "/assets/viewWinner.fxml");

        ctrlConfig = (CtrlConfig) UtilsViews.getController("ViewConfig");
        ctrlWait = (CtrlWait) UtilsViews.getController("ViewWait");
        ctrlPlay = (CtrlPlay) UtilsViews.getController("ViewPlay");
        ctrlPlayGame = (CtrlPlayGame) UtilsViews.getController("ViewPlayGame");
        ctrlWinner = (CtrlWinner) UtilsViews.getController("ViewWinner");

        scene = new Scene(UtilsViews.parentContainer);
        
        stage.setScene(scene);
        stage.onCloseRequestProperty(); // Call close method when closing window
        stage.setTitle("JavaFX");
        stage.setMinWidth(windowWidth);
        stage.setMinHeight(windowHeight);
        stage.show();
        stage.setResizable(false);

        // Add icon only if not Mac
        if (!System.getProperty("os.name").contains("Mac")) {
            Image icon = new Image("file:/icons/icon.png");
            stage.getIcons().add(icon);
        }
    }

    @Override
    public void stop() { 
        if (wsClient != null) {
            wsClient.forceExit();
        }
        System.exit(1); // Kill all executor services
    }

    public static void pauseDuring(long milliseconds, Runnable action) {
        PauseTransition pause = new PauseTransition(Duration.millis(milliseconds));
        pause.setOnFinished(event -> Platform.runLater(action));
        pause.play();
    }

    public static <T> List<T> jsonArrayToList(JSONArray array, Class<T> clazz) {
        List<T> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            T value = clazz.cast(array.get(i));
            list.add(value);
        }
        return list;
    }

    public static void connectToServer() {

        ctrlConfig.txtMessage.setTextFill(Color.WHITE);
        ctrlConfig.txtMessage.setText("Connecting ...");
    
        pauseDuring(1500, () -> { // Give time to show connecting message ...

            String protocol = ctrlConfig.txtProtocol.getText();
            String host = ctrlConfig.txtHost.getText();
            String port = ctrlConfig.txtPort.getText();
            wsClient = UtilsWS.getSharedInstance(protocol + "://" + host + ":" + port);
    
            wsClient.onMessage((response) -> { Platform.runLater(() -> { wsMessage(response); }); });
            wsClient.onError((response) -> { Platform.runLater(() -> { wsError(response); }); });
        });
    }
   
    private static void wsMessage(String response) {
        // System.out.println(response);
        JSONObject msgObj = new JSONObject(response);
        switch (msgObj.getString("type")) {
            case "clients":
                if (clientId == "") {
                    clientId = msgObj.getString("id");
                    CtrlPlay.setClientId(clientId);  // Asignar el clientId a CtrlPlay
                    CtrlPlayGame.setClientId(clientId);  // Asignar el clientId a CtrlPlay
                }
                if (UtilsViews.getActiveView() != "ViewWait") {
                    UtilsViews.setViewAnimating("ViewWait");
                }
                List<String> stringList = jsonArrayToList(msgObj.getJSONArray("list"), String.class);
                if (stringList.size() > 0) { ctrlWait.txtPlayer0.setText(stringList.get(0)); }
                if (stringList.size() > 1) { ctrlWait.txtPlayer1.setText(stringList.get(1)); }
                break;
            case "countdown":
                int value = msgObj.getInt("value");
                String txt = String.valueOf(value);
                if (value == 0) {
                    UtilsViews.setViewAnimating("ViewPlay");
                    txt = "GO";
                }
                ctrlWait.txtTitle.setText(txt);
                break;
            case "serverMouseMoving":
                ctrlPlay.setPlayersMousePositions(msgObj.getJSONObject("positions"));
                break;
            case "serverSelectableObjects":
                ctrlPlay.setSelectableObjects(msgObj.getJSONObject("selectableObjects"));
                break;
            case "playersReady":
                for(String client : PLAYER_NAMES){
                    if(client.equals(clientId)){
                    System.out.println("Todos los jugadores estan listos");
                    }
                }
                if (UtilsViews.getActiveView() != "ViewPlayGame") {
                    UtilsViews.setViewAnimating("ViewPlayGame");
                }
                ctrlPlayGame.playersReady = true;
                break;
            case "updateTurn":

                if(ctrlPlayGame.playerTurn.getText().equals(PLAYER_NAMES.get(0))){
                    ctrlPlayGame.remainingHitsA.setText(msgObj.getString("remainingHitsA"));
                }else if (ctrlPlayGame.playerTurn.getText().equals(PLAYER_NAMES.get(1))){
                    ctrlPlayGame.remainingHitsB.setText(msgObj.getString("remainingHitsB"));
                }

                System.out.println("El jugador: " + ctrlPlayGame.playerTurn.getText() + " ha efectuado su turno");
                ctrlPlayGame.turnoDe = msgObj.getString("turno");
                ctrlPlayGame.playerTurn.setText(msgObj.getString("turno"));
                System.out.println("Le toca a: " + ctrlPlayGame.playerTurn.getText());
                
                break;
            case "sendWinner":
                ctrlWinner.winner.setText(msgObj.getString("gameWinner"));

                if (UtilsViews.getActiveView() != "ViewWinner") {
                    UtilsViews.setViewAnimating("ViewWinner");
                }
                break;
        }
    }

    private static void wsError(String response) {

        String connectionRefused = "Connection refused";
        if (response.indexOf(connectionRefused) != -1) {
            ctrlConfig.txtMessage.setTextFill(Color.RED);
            ctrlConfig.txtMessage.setText(connectionRefused);
            pauseDuring(1500, () -> {
                ctrlConfig.txtMessage.setText("");
            });
        }
    }
}
