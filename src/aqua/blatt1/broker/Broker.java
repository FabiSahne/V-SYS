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
            if (payload instanceof RegisterRequest) {
                register(message.getSender());
            }
            if (payload instanceof DeregisterRequest) {
                deregister(message.getSender());
            }
            if (payload instanceof HandoffRequest) {
                handoffFish(message.getSender(), ((HandoffRequest) payload).getFish());
            }
            if (payload instanceof PoisonPill) {
                stopRequested = true;
            }
        }

        private void register(InetSocketAddress address) {
            int index = clientCollection.size();
            String id = new String("tank" + index);

            writeLock.lock();
            boolean isFirst = clientCollection.size() == 0;
            clientCollection.add(id, address);
            
            // --- Neighbor Update
            InetSocketAddress leftOfRegistered = clientCollection.getLeftNeighorOf(index);
            InetSocketAddress rightOfRegistered = clientCollection.getRightNeighorOf(index);
            writeLock.unlock();
            
            endpoint.send(address, new RegisterResponse(id, leftOfRegistered, rightOfRegistered));
            endpoint.send(leftOfRegistered, new NeighborUpdate(address, Direction.RIGHT));
            endpoint.send(rightOfRegistered, new NeighborUpdate(address, Direction.LEFT));
            if (isFirst)
                endpoint.send(address, new Token());
        }

        private void deregister(InetSocketAddress address) {

            writeLock.lock();
            int index = clientCollection.indexOf(address);
            InetSocketAddress rightNeigbor = clientCollection.getRightNeighorOf(index);
            InetSocketAddress leftNeighbor = clientCollection.getLeftNeighorOf(index);
            clientCollection.remove(index);
            writeLock.unlock();

            endpoint.send(rightNeigbor, new NeighborUpdate(leftNeighbor, Direction.LEFT));
            endpoint.send(leftNeighbor, new NeighborUpdate(rightNeigbor, Direction.RIGHT));

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
    private static boolean stopRequested = false;

    private static final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private static final Lock readLock = rwLock.readLock();
    private static final Lock writeLock = rwLock.writeLock();
    private final ClientCollection<InetSocketAddress> clientCollection;

    private Broker() {
        clientCollection = new ClientCollection<>();
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
            Message message = endpoint.nonBlockingReceive();
            if (message == null) {
                continue;
            }
            try {
                es.execute(new BrokerTask(message));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        System.out.println("Shutdown broker!");

        es.shutdown();
        try {
            if (!es.awaitTermination(1, TimeUnit.SECONDS)) {
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
