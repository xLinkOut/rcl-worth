import WorthExceptions.MulticastException;
import WorthExceptions.NoMulticastAddressAvailableException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Multicast {

    private String lastIP;
    private Queue<String> releasedIP;

    // Inizializza un gestore "pulito" degli indirizzi multicast
    public Multicast(){
        this.lastIP = "224.0.0.1";
        this.releasedIP = new LinkedList<>();
    }

    // Getters per Jackson
    public String getLastIP(){return lastIP;}
    public Queue<String> getReleasedIP(){return releasedIP;}

    @JsonIgnore
    public String getIP() throws NoMulticastAddressAvailableException {
        // Se è presente un indirizzo IP precedentemente generato
        // ma rilasciato a seguito della cancellazione del progetto
        // a cui era stato assegnato, lo riassegno togliendolo
        // dalla coda degli indirizzi rilasciati
        if(!releasedIP.isEmpty()) return releasedIP.poll();

        // Altrimenti, provvedo alla generazione di un nuovo indirizzo
        // Range possibile: 224.0.0.1 -> 239.255.255.255

        // Controllo che non sia arrivato all'ultimo IP disponibile (239.255.255.255)
        // Nel caso, vuol dire che tutti gli IP precedentemente generati sono ancora in uso,
        // di conseguenza lancio un'eccezione (non prevedo un reset, altrimenti ci sarebbe un
        // conflitto di indirizzi, perché sarebbero già tutti utilizzati da altri progetto)
        if(lastIP.equals("239.255.255.255"))
            throw new NoMulticastAddressAvailableException();
        // Altrimenti, genero un nuovo indirizzo incrementale partendo
        // dall'ultimo utilizzato
        else {
            // Divido gli ottetti separati dal punto (.) e li casto da String a int
            int[] arrayLastIP = Arrays.stream(
                    lastIP.split("\\."))
                    .mapToInt(Integer::parseInt).toArray();
            // Li parso e li incremento
            for (int i=3;i>=0;i--) {
                if (i>0){
                    // I 3 byte meno significativi hanno limite massimo 255
                    if (arrayLastIP[i] < 255) { arrayLastIP[i]++; break; }
                } else {
                    // Il byte più significativo invece 239
                    if (arrayLastIP[i] < 239) { arrayLastIP[i]++; }
                }
            }
            // Riporto gli ottetti da int a String, in notazione decimale puntata
            // e aggiorno il valore della classe lastIP
            lastIP = Arrays.stream(arrayLastIP)
                    .mapToObj(String::valueOf)
                    .collect(Collectors.joining("."));

            // Ritorno infine l'indirizzo IP generato
            return lastIP;
        }
    }


    public static void main(String[] args) throws IOException, NoMulticastAddressAvailableException {
        ObjectMapper jacksonMapper = new ObjectMapper();

        Multicast readMulticast = jacksonMapper.readValue(
                Files.newBufferedReader(Paths.get("data/Multicast.json")),
                Multicast.class);

        System.out.println(readMulticast.toString());
        for(int i=0;i<10;i++)
            System.out.println(readMulticast.getIP());

        jacksonMapper.writeValue(Files.newBufferedWriter(
                Paths.get("data/Multicast.json")),
                readMulticast);

    }
}