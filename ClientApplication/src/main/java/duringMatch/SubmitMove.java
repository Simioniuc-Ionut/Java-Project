package duringMatch;

import org.example.GameState;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.concurrent.Semaphore;

public class SubmitMove extends JPanel {
    final MainFrameBattle frame;
    OpponentBoard opponentBoard;
    ClientBoardBattle clientBoardBattle;
    SettingsBattle settingsBattle;
    JButton submitMoveBtn = new JButton("Submit Move");
    GameState gameState ;
    public SubmitMove(MainFrameBattle frame,OpponentBoard opponentBoard, ClientBoardBattle clientBoardBattle,SettingsBattle settingsBattle) {
        this.frame = frame;
        this.opponentBoard = opponentBoard;
        this.clientBoardBattle = clientBoardBattle;
        this.settingsBattle = settingsBattle;


        init();
    }

    private void init() {

        add(submitMoveBtn);


        //style buton
        Font newFont = new Font("default", Font.BOLD, 20);
        submitMoveBtn.setFont(newFont);
        submitMoveBtn.setPreferredSize(new Dimension(180, 60));
        submitMoveBtn.setBackground(Color.darkGray);
        submitMoveBtn.setForeground(Color.WHITE);//culoare text

        //configurare listeners
        submitMoveBtn.addActionListener(this::listenerAddSubmitMove);

    }
    public void listenerAddSubmitMove(ActionEvent e){

        //trimit pozitia catre client
        sendPositionToClient();

      // if(validateYourTurnToMove()) {
           System.out.println("A colorat");
           // celula selectata dupa submit va ramane rosie doar daca este randul sau
           if (opponentBoard.lastRowClicked != null && opponentBoard.lastColClicked != null) {
               opponentBoard.cellColorsShips[opponentBoard.lastRowClicked][opponentBoard.lastColClicked] = Color.red;
               opponentBoard.lastRowClicked = null;
               opponentBoard.lastColClicked = null;
               opponentBoard.repaint();
           }
      // }else{
//           System.out.println("NU TREBUIE SA FIE COLORAT");
//           if (opponentBoard.lastRowClicked != null && opponentBoard.lastColClicked != null) {
//               opponentBoard.cellColorsShips[opponentBoard.lastRowClicked][opponentBoard.lastColClicked] = Color.black;
//               opponentBoard.lastRowClicked = null;
//               opponentBoard.lastColClicked = null;
//               opponentBoard.repaint();
//           }
//       }
        messageFromServer();

    }
    private String getMessage() {
        Semaphore lock = frame.client.getMessageLock();
        synchronized (lock) {
            try {
                lock.acquire(); // Așteaptă până când primește notify() de la server
                return frame.client.getMessage();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                //System.out.println("Thread intrerupt in validation from SettingsPlaceShip");
                return "Thread intrerupt in validation from SettingsPlaceShip";
            }
        }
    }
//    private  boolean validateYourTurnToMove(){
//        Semaphore lock = frame.client.getMoveTurnLock();
//        synchronized (lock) {
//            try {
//                //Asteapta:
//                lock.acquire(); // Așteaptă până când primește notify() de la server
//                return frame.client.isYourTurnToMakeAMove();
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//                System.out.println("Thread intrerupt in validation");
//                return false;
//            }
//        }
//
//    }
    private void sendPositionToClient(){
        // Obtine valorile din click (tinta)
        if (opponentBoard.rowClick != null && opponentBoard.colClick != null) {
            // Convertire
            String submitRowLetter = String.valueOf((char) ('A' + opponentBoard.rowClick));
            String submitColNumber = String.valueOf(opponentBoard.colClick + 1);

            StringBuilder messageToClient = new StringBuilder();
            messageToClient.append(submitRowLetter);
            messageToClient.append(submitColNumber);
            //trimit catre client mesajul ca sa ajunge dupa la server
            frame.client.setAnswer(messageToClient.toString().trim());

            System.out.println("SUBMITED -> "+messageToClient);
        }
    }
    private void messageFromServer(){
        //imi afiseaza mesajul de la server
        String serverMessage = getMessage();
        System.out.println("SERVER message in GUI Submit:" + serverMessage);
        if (serverMessage != null) {

            //actualizare mesaj primit de la server
            settingsBattle.messageTextAreaBattle.setText(serverMessage);

            //style
            settingsBattle.messageTextAreaBattle.setOpaque(true);
            settingsBattle.messageTextAreaBattle.setBackground(new Color(0, 0, 0, 123));

            //fortare repictare JTextArea
            settingsBattle.messageTextAreaBattle.repaint();

            //revalidare si repaint pt JPanel
            settingsBattle.revalidate();
            settingsBattle.repaint();
        }
    }
}