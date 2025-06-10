package messaging;

import javax.crypto.Cipher;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SecureEndpoint extends Endpoint {

    // Define KeyExchangeMessage as a private static inner class
    private static class KeyExchangeMessage implements Serializable {
        private static final long serialVersionUID = 1L; // Good practice for Serializable classes
        private final PublicKey publicKey;

        public KeyExchangeMessage(PublicKey publicKey) {
            this.publicKey = publicKey;
        }

        public PublicKey getPublicKey() {
            return publicKey;
        }
    }

    private final Endpoint internalEndpoint;
    private final KeyPair keyPair;
    private final PublicKey publicKey;
    private final PrivateKey privateKey;
    private final Map<InetSocketAddress, PublicKey> peerPublicKeys;

    public SecureEndpoint() {
        super();
        this.internalEndpoint = new Endpoint();
        this.peerPublicKeys = new ConcurrentHashMap<>();
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            this.keyPair = keyGen.generateKeyPair();
            this.publicKey = this.keyPair.getPublic();
            this.privateKey = this.keyPair.getPrivate();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to initialize SecureEndpoint RSA keys", e);
        }
    }

    public SecureEndpoint(int port) {
        super(port);
        this.internalEndpoint = new Endpoint(port);
        this.peerPublicKeys = new ConcurrentHashMap<>();
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            this.keyPair = keyGen.generateKeyPair();
            this.publicKey = this.keyPair.getPublic();
            this.privateKey = this.keyPair.getPrivate();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to initialize SecureEndpoint RSA keys", e);
        }
    }

    @Override
    public void send(InetSocketAddress receiver, Serializable payload) {
        try {
            PublicKey receiverPublicKey = peerPublicKeys.get(receiver);

            if (receiverPublicKey == null) {
                // Send our public key as KeyExchangeMessage. This is a Serializable object.
                internalEndpoint.send(receiver, new KeyExchangeMessage(this.publicKey));
                System.out.println("Sent KeyExchangeMessage to: " + receiver + ". Original message will not be sent in this call.");
                // The original message is not sent if the key is unknown.
                // Application layer needs to handle retries or queueing.
                return;
            }

            // If key is known, encrypt the payload and send.
            // The payload here is the application's intended serializable data.
            // If the application wrapped its data in a Message object before calling SecureEndpoint.send,
            // that Message object itself is the 'payload' to be encrypted.

            Cipher rsaEncryptCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            rsaEncryptCipher.init(Cipher.ENCRYPT_MODE, receiverPublicKey);
            byte[] encryptedPayloadBytes = rsaEncryptCipher.doFinal(serialize(payload));

            // Send the encrypted byte array. byte[] is Serializable.
            internalEndpoint.send(receiver, encryptedPayloadBytes);

        } catch (Exception e) {
            System.err.println("Error in SecureEndpoint send: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Message handleReceivedMessage(Message message) {
        if (message == null) {
            return null;
        }

        Serializable payload = message.getPayload();
        InetSocketAddress sender = message.getSender();

        if (payload instanceof KeyExchangeMessage) {
            KeyExchangeMessage keyMsg = (KeyExchangeMessage) payload;
            PublicKey peerKey = keyMsg.getPublicKey();
            
            // Store the sender's public key
            if (sender != null && peerKey != null) {
                peerPublicKeys.put(sender, peerKey);
                System.out.println("Received and stored public key from: " + sender);

                // Respond with our public key if this is the first time we've received theirs,
                // or to ensure they have ours.
                // Avoid sending if they just sent us our own key (which shouldn't happen).
                if (!peerPublicKeys.containsKey(sender) || !this.publicKey.equals(peerKey)) {
                     System.out.println("Sending our public key in response to: " + sender);
                     // Send KeyExchangeMessage directly as the payload.
                     internalEndpoint.send(sender, new KeyExchangeMessage(this.publicKey));
                }
            } else {
                 System.err.println("Received KeyExchangeMessage with null sender or null key.");
            }
            return null; // KeyExchangeMessages are not passed to the application
        }

        if (payload instanceof byte[]) { // Assumed to be an encrypted message
            try {
                Cipher rsaDecryptCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                rsaDecryptCipher.init(Cipher.DECRYPT_MODE, this.privateKey);
                byte[] decryptedPayloadBytes = rsaDecryptCipher.doFinal((byte[]) payload);
                Serializable decryptedPayload = deserialize(decryptedPayloadBytes);
                // Return a new Message object with the decrypted payload and original sender
                return new Message(decryptedPayload, sender);
            } catch (Exception e) {
                System.err.println("Error decrypting message from " + sender + ": " + e.getMessage());
                e.printStackTrace();
                return null; // Failed to decrypt
            }
        } else {
            // If the payload is not a KeyExchangeMessage and not a byte[], it's unexpected.
            // It could be an unencrypted message if the other side hasn't implemented encryption yet,
            // or a programming error.
            System.err.println("Received non-encrypted, non-KeyExchange payload from " + sender + ": " + payload.getClass().getName() + ". Passing it up as is.");
            // For robustness, one might choose to pass it up, or discard it.
            // Passing it up as a new Message object.
            return new Message(payload, sender);
        }
    }

    @Override
    public Message blockingReceive() {
        while (true) { // Loop to keep trying to receive until an application message or null
            Message rawMessage = internalEndpoint.blockingReceive();
            if (rawMessage == null) {
                return null; // Internal endpoint indicates no more messages (or error)
            }
            Message appMessage = handleReceivedMessage(rawMessage);
            if (appMessage != null) {
                return appMessage; // Decrypted application message
            }
            // If appMessage is null, it was a KeyExchangeMessage (handled) or an error during handling.
            // Loop continues to get the next message.
        }
    }

    @Override
    public Message nonBlockingReceive() {
        // Try to get a message without blocking
        Message rawMessage = internalEndpoint.nonBlockingReceive();
        if (rawMessage == null) {
            return null; // No message available
        }
        
        // Process the raw message. This might be a KeyExchangeMessage or an encrypted app message.
        Message appMessage = handleReceivedMessage(rawMessage);
        
        // If appMessage is null, it means rawMessage was a KeyExchangeMessage (which was handled)
        // or there was an error processing rawMessage.
        // In a non-blocking scenario, we return null if the first message pulled was for internal use.
        // The caller can try again.
        // If appMessage is not null, it's a decrypted application message.
        return appMessage;
    }

    // Helper methods for serialization
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
