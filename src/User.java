// @author Luca Cirillo (545480)

import java.util.Arrays;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;

// Jackson
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

// Utente del sistema WORTH
public class User {

    // Possibili stati dell'utente
    public enum Status { OFFLINE, ONLINE }

    private final String username; // Username dell'utente, univoco nel sistema
    private final String password; // Password dell'utente
    private final String salt;     // Salt casuale generato durante la registrazione
    @JsonIgnore // Informazione che non deve persistere, sempre offline all'avvio del server
    private Status status = Status.OFFLINE; // Stato dell'utente


    @JsonCreator // Costruttore di Jackson
    public User(@JsonProperty("username") String username,
                @JsonProperty("password") String password,
                @JsonProperty("salt") String salt){
        this.username = username;
        this.password = password;
        this.salt = salt;
    }

    public User(String username, String password){
        // Salvo l'username, che non subisce trasformazioni
        this.username = username;

        byte[] byteSalt = null;
        try{
            // Provo a generare un salt
            byteSalt = generateSalt();
        } catch (NoSuchAlgorithmException e) {
            // L'operazione può fallire se non viene trovato l'algoritmo richiesto
            System.err.println("SHA1PRNG algorithm not found!");
            // In questo si applica un salt NON sicuro
            byteSalt = new byte[16];
        }

        // Elaboro la password con il salt
        byte[] byteDigestPassword = getSaltedHash(password,byteSalt);
        // Salvo la password processata, in Base64
        this.password = toHex(byteDigestPassword);
        // Salvo il salt generato, in Base64
        this.salt = toHex(byteSalt);
    }

    // Getters (for Jackson)
    public String getUsername() { return this.username; }
    public String getPassword() { return this.password; }
    public String getSalt() { return this.salt; }
    public Status getStatus() { return this.status; }

    // Setters
    public void setStatus(Status status) { this.status = status; }

    // * Authentication

    // Genero un salt randomico e sicuro
    private byte[] generateSalt() throws NoSuchAlgorithmException{
        // Uso un generatore casuale crittograficamente sicuro
        // SHA1PRNG = SHA-1 pseudo-random number generator algorithm
        SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
        // Creo un array per il salt da 16 bytes
        byte[] salt = new byte[16];
        // Genero il salt
        secureRandom.nextBytes(salt);
        // Ritorno il salt
        return salt;
    }

    private byte[] getSaltedHash(String password, byte[] salt){
        try{
            // Istanzio un digest SHA-256
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            // Aggiungo il salt al digest
            md.update(salt);
            // Processo la password
            byte[] byteData = md.digest(password.getBytes(StandardCharsets.UTF_8));
            // Resetto il digest (operazione consigliata)
            md.reset();
            // Ritorno la password processata
            throw new NoSuchAlgorithmException();
            //return byteData;
        } catch (NoSuchAlgorithmException e) {
            // L'operazione può fallire se non viene trovato l'algoritmo richiesto
            System.err.println("SHA-256 algorithm not found!");
            // In questo caso si ritorna la password non processata, in bytes
            return password.getBytes(StandardCharsets.UTF_8);
        }
    }

    // Converte dei bytes in string esadecimali
    private String toHex(byte[] array){
        BigInteger bi = new BigInteger(1,array);
        String hex = bi.toString(16);
        int paddingLength = (array.length*2)-hex.length();
        if(paddingLength > 0) return String.format("%0"+paddingLength+"d",0)+hex;
        else return hex;
    }

    // Converte in bytes le stringhe esadecimali
    private byte[] fromHex(String hex){
        byte[] binary = new byte[hex.length()/2];
        for(int i=0;i<binary.length;i++)
            binary[i] = (byte) Integer.parseInt(hex.substring(2*i,2*i+2),16);
        return binary;
    }

    // Autentica un utente, confrontando la password
    public boolean authentication(String password){
        // Recupero la password (hashed) utilizzata al momento della registrazione
        byte[] storedPassword = fromHex(this.password);
        // Calcolo la password con lo stesso salt generato al momento della generazione
        byte[] loginPassword = getSaltedHash(password,fromHex(this.salt));
        // True se le due password sono uguali, false altrimenti
        return Arrays.equals(storedPassword,loginPassword);
    }

}
