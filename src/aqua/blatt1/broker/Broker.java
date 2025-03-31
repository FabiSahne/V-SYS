package aqua.blatt1.broker;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.swing.JOptionPane;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.msgtypes.*;
import aqua.blatt2.broker.PoisonPill;
import messaging.Endpoint;
import messaging.Message;

public class Broker {

    private class BrokerTask implements Runnable {
        private Message message;

        private BrokerTask(Message message) {
            this.message = message;
        }

        @Override
        public void run() {
            Serializable payload = message.getPayload();
            try {
                @SuppressWarnings("unused")
                RegisterRequest _register = (RegisterRequest) payload;
                register(message.getSender());
            } catch (Exception e) {
            }
            try {
                @SuppressWarnings("unused")
                DeregisterRequest _deregister = (DeregisterRequest) payload;
                deregister(message.getSender());
            } catch (Exception e) {
            }
            try {
                HandoffRequest handoff = (HandoffRequest) payload;
                FishModel fish = handoff.getFish();
                handoffFish(message.getSender(), fish);
            } catch (Exception e) {
            }
            try {
                @SuppressWarnings("unused")
                PoisonPill _poisonPill = (PoisonPill) payload;
                stopRequested = true;
            } catch (Exception e) {
            }
        }

        private void register(InetSocketAddress address) {
            String id = new String("tank" + clientCollection.size());
            writeLock.lock();
            clientCollection.add(id.toString(), address);
            writeLock.unlock();
            endpoint.send(address, new RegisterResponse(id));
        }

        private void deregister(InetSocketAddress address) {
            writeLock.lock();
            clientCollection.remove(clientCollection.indexOf(address));
            writeLock.unlock();
        }

        private void handoffFish(InetSocketAddress address, FishModel fish) {
            Direction direction = fish.getDirection();
            readLock.lock();
            int index = clientCollection.indexOf(address);
            if (direction == Direction.LEFT) {
                InetSocketAddress left = clientCollection.getLeftNeighorOf(index);
                endpoint.send(left, new HandoffRequest(fish));
            } else if (direction == Direction.RIGHT) {
                InetSocketAddress right = clientCollection.getRightNeighorOf(index);
                endpoint.send(right, new HandoffRequest(fish));
            }
            readLock.unlock();
        }
    }

    private static final Endpoint endpoint = new Endpoint(4711);
    private static final int POOL_SIZE = 8;
    private boolean stopRequested;

    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();
    private final ClientCollection<InetSocketAddress> clientCollection;

    private Broker() {
        clientCollection = new ClientCollection<>();
        stopRequested = false;
    }

    private void broker() {
        ExecutorService es = Executors.newFixedThreadPool(POOL_SIZE);

        Thread uiThread = new Thread(() -> {
            JOptionPane.showMessageDialog(null, "Press Ok to stop the Server", "Stop",
                    JOptionPane.INFORMATION_MESSAGE);
            stopRequested = true;
        });
        uiThread.start();

        while (!stopRequested) {
            Message message = endpoint.blockingReceive();
            try {
                es.execute(new BrokerTask(message));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        es.shutdown();
        try {
            if (!es.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                es.shutdownNow();
            }
        } catch (InterruptedException e) {
            es.shutdownNow();
        }
    }

    public static void main(String[] args) {
        Broker broker = new Broker();
        broker.broker();
        System.exit(0);
    }
}
