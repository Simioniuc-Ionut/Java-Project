package org.example;


import duringMatch.timer.TimeUpdateListener;
import lombok.Setter;
import org.example.exception.GameException;
import org.example.shipsModels.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.Buffer;
import java.util.concurrent.*;

import lombok.Getter;

@Getter
@Setter

public class ClientThread extends Thread {
    //constante
    public Ships CARRIER_LENGTH = new Carrier();
    public Ships BATTLESHIP_LENGTH = new Battleship();
    public Ships DESTROYER_LENGTH = new Destroyer();
    public Ships SUBMARINE_LENGTH = new Submarine();
    public Ships PATROL_BOAT_LENGTH = new PatrolBoat();


    private final Socket clientSocket;
    private PrintWriter out;
    private ClientThread opponent;

    private final GameServer gameServer;
    private static GameState playerTurn;
    private int playerId;
    private boolean shipsPlaced;

    private boolean playerFinishStatus;
    private TimerThread timer;

    //timer
    private int timerPlayer = 30;

    //private AtomicInteger state;



    public ClientThread(Socket clientSocket, GameServer gameServer,Integer playerId) {
        this.clientSocket = clientSocket;
        this.gameServer = gameServer;
        this.playerId = playerId;

        this.shipsPlaced = false;
        playerFinishStatus=false;



        this.timer = new TimerThread(timerPlayer,playerId);
        timer.start();
        //timer.startTimer();
    }
    public void startTimerThread()
    {
        timer.startTimer();
    }
    public void stopTimerThread(){
        timer.pauseTimer();
    }
    public void finishTimerThread(){
        if(timer.isAlive()) {
            timer.interrupt();
        }
    }
    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            this.out = out;
            String inputLine;

            out.println("Player id " + playerId);

            while ((inputLine = in.readLine()) != null) {
                System.out.println("Server received: " + inputLine);

                if (inputLine.equals("stop") || inputLine.equals("exit"))
                {
                    out.println("Server stopped");
                    gameServer.stop();
                    break;
                } else if(inputLine.equalsIgnoreCase("c") && gameServer.getCurrentState() == GameState.GAME_NOT_CREATED){

                    gameServer.setCurrentState(GameState.WAITING_FOR_PLAYER);
                    //starea de incepere a jocului
                    startGameMessage();
                    joinGame(in);

                } else if(inputLine.equalsIgnoreCase("j")){
                    if(gameServer.getCurrentState() == GameState.WAITING_FOR_PLAYER) {

                        startGameMessage();
                        joinGame(in);


                    }else {
                        sendMessage("Game isn't created.");
                    }
                } else if(gameServer.getCurrentState() == GameState.GAME_READY_TO_MOVE/*inputLine.startsWith("submit move")*/){
                    System.out.println("Player turn " + playerTurn);
                        if(playerId == playerTurn.getStateCode()) {

                            gameServer.startTimer(playerId);

                            submitMove(inputLine);//fac mutarea

                            System.out.println("Player " + playerId + " moved " + inputLine + " status timer " + this.timer.isStart() + " TIMPUL > " + timerPlayer);

                            switchTurn();//schimb turul


                        }else{
                            sendMessage("NOT_YOUR_TURN: " + inputLine);
                        }
              }
                else {
                    out.println("Server received the request: " + inputLine);
                }
            }
        } catch (IOException e) {
            handleDisconnection(e);
        }  finally {
            closeClientSocket();

        }
    }

    private void joinGame(BufferedReader in ) throws IOException{
        sendMessage("Game started. PLace your ships.");

        waititngPlayersForJoining(); //se asteapta sa dea join,inainte sa plaseze pe mapa
        setOpponent();

        gameServer.setCurrentState(GameState.GAME_READY_TO_MOVE);
        //trebuiesc modificate
//        placeShip(in, CARRIER_LENGTH);
//        placeShip(in, BATTLESHIP_LENGTH);
//        placeShip(in, DESTROYER_LENGTH);
        //placeShip(in, SUBMARINE_LENGTH);
        placeShip(in, PATROL_BOAT_LENGTH);
        shipsPlaced = true;

        listenReadyFromClient(in);
        waitingPlayersToFinishPlacingShips();


        checkReadyToStart();

        gameServer.startTimer(playerId);
        //instantiam timerul;
//        this.timer = new TimerThread(timerPlayer,playerId);
//        timer.start();
    }



    private void waitingPlayersToFinishPlacingShips() {
      if(playerId == 1) {

        gameServer.setPlayer1IsReadyToStartGame(true);
        sendMessage("Waiting player 2");
          while (!gameServer.isPlayer2IsReadyToStartGame()) {
              waitingThread();
          }
      }else{
          sendMessage("Waiting player 1");
          gameServer.setPlayer2IsReadyToStartGame(true);
          while (!gameServer.isPlayer1IsReadyToStartGame()) {
              waitingThread();
          }
      }
    }

    private  void makePlayerReadyToPlaceShip(boolean b) {

       // System.out.println("makePlayerReady " + b + " playerid " + playerId);
        if(playerId == 1){
            gameServer.setPlayer1IsReadyToPlaceShips(b);
        }else {
            gameServer.setPlayer2IsReadyToPlaceShips(b);
        }
    }


    private void placeShip(BufferedReader in,Ships ship) throws IOException{
        sendMessage("Place your " + ship.getShipName() + " (length = " + ship.getShipSize() + "): ");
        int placed;
        do {
            try {

                String inputLine = in.readLine();
                placed = gameServer.validateShipPosition(playerId, inputLine, ship);

                sendMessage("Cor:" + "Ship is correctly placed");
            } catch (GameException | StringIndexOutOfBoundsException | NullPointerException e) {
                placed = -1; // ca sa ramana in while

                sendMessage("Err:" + e.getMessage());

            }
        } while (placed < 0);

    }

    private void submitMove(String inputLine) {
        if(isReadyToMove()) {
            String move = inputLine.trim();
            gameServer.handleMove(playerId, move);
           // sendMessage("Move submitted: " + move + ". Waiting for opponent's move.");
            if(gameServer.getCurrentState() != GameState.GAME_OVER){
            opponent.sendMessage("Opponent moved: " + move + ". Your turn.");}
        }
    }
    private boolean isReadyToMove(){
        if ( gameServer.getCurrentState() == GameState.GAME_READY_TO_MOVE && playerId != playerTurn.getStateCode()) {
            sendMessage("It's not your turn or game is not ready yet.");
            return false;
        }else {
            return true;
        }
    }

    private void checkReadyToStart() {
        //  System.out.println("sunt in checlReadyToStart " + gameServer.islayer1IsReadyToPlaceShips() + " al 2 " + gameServer.isPlayer2IsReady());
        if (gameServer.isPlayer2IsReadyToStartGame() && gameServer.isPlayer1IsReadyToStartGame()) {
            //sendMessage(" PlaBoth players have placed their ships.yer 1 starts.");
            //opponent.sendMessage("PlaBoth players have placed their ships.yer 1 starts.");
            gameServer.setCurrentState(GameState.GAME_READY_TO_MOVE);
            playerTurn = GameState.PLAYER1_TURN;

            sendTheGameCouldStart();

        } else {
            // sendMessage("Waiting for opponent to place ships...");
            while (gameServer.getCurrentState() != GameState.GAME_READY_TO_MOVE) {
                waitingThread();
            }
            //sendMessage("waiting is finished");
        }
    }

    private synchronized void switchTurn() {
        if (playerTurn == GameState.PLAYER1_TURN) {
            System.out.println("switchTurn | Player 1 turn ended " + playerTurn);
            //stopGameTimerPlayer1();
            playerTurn = GameState.PLAYER2_TURN;

           // startGameTimerPlayer2();
        } else {
            System.out.println("switchTurn | Player 2 turn ended " + playerTurn);
            //stopGameTimerPlayer2();
            playerTurn = GameState.PLAYER1_TURN;
            //startGameTimerPlayer1();
        }
    }

    private synchronized void listenReadyFromClient(BufferedReader in){

        try {
            String ready = in.readLine();
            System.out.println("mesajul din listenReadyFromClient " + ready + " isReadyplayer1 " + gameServer.isPlayer1IsReadyToStartGame() + " isReadyplayer2 " + gameServer.isPlayer2IsReadyToStartGame());

            if(ready.equals("READY")){
                sendMessage("Player " + playerId + " is ready");

            }else{
                sendMessage("Player is not ready");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void waititngPlayersForJoining(){
        if (playerId == 1){
            gameServer.setPlayer1IsReadyToPlaceShips(true);
            while(!gameServer.isPlayer2IsReadyToPlaceShips()){
                waitingThread();
            }
        }else{
            gameServer.setPlayer2IsReadyToPlaceShips(true);
            while(!gameServer.isPlayer1IsReadyToPlaceShips()){
                waitingThread();
            }
        }
    }
    private void waitingThread(){
        try {
            Thread.sleep(1000);
            sendMessage("wait");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }
    private void handleDisconnection(IOException e) {
        System.out.println("Client disconnected abruptly: " + e.getMessage());
//        if (opponent != null) {
//            opponent.sendMessage("Opponent disconnected. Waiting for another player...");
//            //opponent.setOpponent(null);
//            //gameServer.addWaitingPlayer(opponent);
//        }
    }

    private void closeClientSocket() {
        try {
            System.out.println("Socket was closed for player " + playerId);
            gameServer.playerLeft(this);
            clientSocket.close();

        } catch (IOException e) {
            System.out.println("Error when closing client socket: " + e.getMessage());
        }
    }
    public void notifyHit(String move) {
        sendMessage("You hit at position: " + move + ". Waiting for opponent's move ");
    }
    public void notifyMiss(String move) {
        sendMessage("You missed at position: " + move + ". Waiting for opponent's move");
    }
    public void setOpponent() {
        if(playerId == 1) {
            int player2 = playerId + 1;
            this.opponent = gameServer.getPlayer(player2);

        } else {
            int player1 = playerId - 1;
            this.opponent = gameServer.getPlayer(player1);
        }
    }

//    public void setPlayerId(int id) {
//        this.playerId = id;
//    }
//
//    public ClientThread getOpponent() {
//        return opponent;
//    }

    public void startGameMessage() {
        sendMessage("1-Both players connected. Type 'join game' to start.");
        if (opponent != null) {
            opponent.sendMessage("2-Both players connected. Type 'join game' to start.");
        }

    }

    public void gameIsFinished(){
        System.out.println("func : gameIsFinished() was accessed");
        if(playerTurn == GameState.PLAYER1_TURN){
            gameServer.displayServerBoard();
            gameServer.setCurrentState(GameState.GAME_NOT_CREATED);
        }else{
            gameServer.displayServerBoard();
            gameServer.setCurrentState(GameState.GAME_NOT_CREATED);
        }
    }
    public void notifyGameOver() {
           sendMessage("Game over. You won!");
            opponent.sendMessage("Game over. You lost!");

    }

    public void gameReset() {
        //remainingTimePlayer1 = 30;
        //remainingTimePlayer2 = 30;
        finishTimerThread();
        //Redeschidem timerul
        this.timer = new TimerThread(timerPlayer,playerId);
        timer.start();

        timerPlayer =30;
        playerTurn = GameState.PLAYER1_TURN;
        //sendMessage("Game has reseted");
           CARRIER_LENGTH = new Carrier();
           BATTLESHIP_LENGTH = new Battleship();
           DESTROYER_LENGTH = new Destroyer();
           SUBMARINE_LENGTH = new Submarine();
           this.PATROL_BOAT_LENGTH = new PatrolBoat();

        System.out.println("Am dat finish la threaduri");


    }
    private void sendTheGameCouldStart(){
        sendMessage("START-MOVE");
    }
}