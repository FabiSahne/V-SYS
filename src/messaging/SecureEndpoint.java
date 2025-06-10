package messaging;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;

public class SecureEndpoint extends Endpoint {
    private final Endpoint internalEndpoint;
    private final SecretKey secretKey;
    private final Cipher encryptCipher;
    private final Cipher decryptCipher;

    public SecureEndpoint() {
        super();
        this.internalEndpoint = new Endpoint();
        try {
            byte[] keyBytes = "CAFEBABECAFEBABE".getBytes();
            this.secretKey = new SecretKeySpec(keyBytes, "AES");

            this.encryptCipher = Cipher.getInstance("AES");
            this.encryptCipher.init(Cipher.ENCRYPT_MODE, this.secretKey);

            this.decryptCipher = Cipher.getInstance("AES");
            this.decryptCipher.init(Cipher.DECRYPT_MODE, this.secretKey);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to initialize SecureEndpoint", e);
        }
    }

    public SecureEndpoint(int port) {
        super(port);
        this.internalEndpoint = new Endpoint(port);
        try {
            byte[] keyBytes = "CAFEBABECAFEBABE".getBytes();
            this.secretKey = new SecretKeySpec(keyBytes, "AES");

            this.encryptCipher = Cipher.getInstance("AES");
            this.encryptCipher.init(Cipher.ENCRYPT_MODE, this.secretKey);

            this.decryptCipher = Cipher.getInstance("AES");
            this.decryptCipher.init(Cipher.DECRYPT_MODE, this.secretKey);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to initialize SecureEndpoint", e);
        }
    }

    @Override
    public void send(InetSocketAddress receiver, Serializable payload) {
        try {
            if (payload instanceof Message) {
                Message originalMessage = (Message) payload;
                Serializable originalPayload = originalMessage.getPayload();
                byte[] encryptedPayloadBytes = encryptCipher.doFinal(serialize(originalPayload));
                Message encryptedMessage = new Message(encryptedPayloadBytes, originalMessage.getSender());
                internalEndpoint.send(receiver, encryptedMessage.getPayload());
            } else {
                 //Direct payload encryption if it's not a Message object (though typically it should be wrapped in Message)
                byte[] encryptedPayloadBytes = encryptCipher.doFinal(serialize(payload));
                internalEndpoint.send(receiver, encryptedPayloadBytes);
            }
        } catch (Exception e) {
            System.err.println("Error sending encrypted message: " + e.getMessage());
            // Optionally, rethrow or handle more gracefully
        }
    }

    @Override
    public Message blockingReceive() {
        Message encryptedMessage = internalEndpoint.blockingReceive();
        if (encryptedMessage == null) {
            return null;
        }
        return decryptMessage(encryptedMessage);
    }

    @Override
    public Message nonBlockingReceive() {
        Message encryptedMessage = internalEndpoint.nonBlockingReceive();
        if (encryptedMessage == null) {
            return null;
        }
        return decryptMessage(encryptedMessage);
    }

    private Message decryptMessage(Message encryptedMessage) {
        try {
            Serializable encryptedPayload = encryptedMessage.getPayload();
            byte[] decryptedPayloadBytes;
            if (encryptedPayload instanceof byte[]) {
                decryptedPayloadBytes = decryptCipher.doFinal((byte[]) encryptedPayload);
            } else {
                // This case should ideally not happen if send always encrypts to byte[]
                System.err.println("Warning: Received payload is not byte[], attempting direct deserialization.");
                return encryptedMessage; // Or handle as an error
            }
            Serializable decryptedPayload = deserialize(decryptedPayloadBytes);
            return new Message(decryptedPayload, encryptedMessage.getSender());
        } catch (Exception e) {
            System.err.println("Error decrypting message: " + e.getMessage());
            // Return the original message or null, or throw an exception, depending on desired error handling
            return null; 
        }
    }

    // Helper methods for serialization, assuming they are protected or public in Endpoint or a utility class
    // If not, these need to be implemented or adjusted.
    // For now, let's assume Endpoint has these or similar helper methods, or we add them.

    private byte[] serialize(Serializable obj) throws java.io.IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos);
        oos.writeObject(obj);
        oos.close();
        return baos.toByteArray();
    }

    private Serializable deserialize(byte[] bytes) throws java.io.IOException, ClassNotFoundException {
        java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(bytes);
        java.io.ObjectInputStream ois = new java.io.ObjectInputStream(bais);
        Serializable obj = (Serializable) ois.readObject();
        ois.close();
        return obj;
    }
}
