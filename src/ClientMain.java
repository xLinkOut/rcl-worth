// @author Luca Cirillo (545480)

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Scanner;

public class Client {
    private static final String IP_SERVER = "127.0.0.1";
    private static final int PORT_TCP = 6789;

    public static void main(String[] args){
        // Creo un SocketChannel per stabilire una connessione TCP
        SocketChannel socketChannel;

        try {
            // * TCP Setup
            // Apro un nuovo SocketChannel
            socketChannel = SocketChannel.open();
            // Connetto al server sul canale appena creato
            socketChannel.connect(new InetSocketAddress(IP_SERVER, PORT_TCP));
            System.out.println("Connesso a: " + socketChannel.getRemoteAddress());

            Scanner scanner = new Scanner(System.in);
            ByteBuffer msgBuffer;
            String msg;

            while(true){ // condizione di uscita su comando
                // Leggo il messaggio dal terminale
                System.out.print("Scrivi un messaggio da inviare al server: ");
                msg = scanner.nextLine();
                // Lo scrivo in un buffer
                msgBuffer = ByteBuffer.wrap(msg.getBytes());
                // E poi passo tutto sul SocketChannel
                while (msgBuffer.hasRemaining())
                    socketChannel.write(msgBuffer);
                // Quindi svuoto il buffer...
                msgBuffer.clear();
            }

        } catch (IOException ioe) { ioe.printStackTrace(); }
    }
}
