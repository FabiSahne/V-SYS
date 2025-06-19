package aqua.blatt1.broker;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.Properties;
import aqua.blatt1.common.rmi.AquaBroker;
import aqua.blatt1.common.rmi.AquaClient;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Broker implements AquaBroker {
    private final ClientCollection<AquaClient> clientCollection;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();
    private final Timer leaseTimer = new Timer();

    public Broker() {
        this.clientCollection = new ClientCollection<>();
        leaseTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                writeLock.lock();
                try {
                    if (clientCollection.removeInactiveClients(20 * 1000)) {
                        System.out.println("INFO: Removed inactive clients");
                    }
                } finally {
                    writeLock.unlock();
                }
            }
        }, 0, 1000);
    }

    @Override
    public String register(AquaClient client) throws RemoteException {
        String id = "tank" + clientCollection.size();
        writeLock.lock();
        try {
            boolean isFirst = clientCollection.size() == 0;
            clientCollection.add(id, client);
            int index = clientCollection.indexOf(client);
            AquaClient leftNeighbor = clientCollection.getLeftNeighorOf(index);
            AquaClient rightNeighbor = clientCollection.getRightNeighorOf(index);

            if (leftNeighbor != null)
                leftNeighbor.updateNeighbor(client, Direction.RIGHT);
            if (rightNeighbor != null)
                rightNeighbor.updateNeighbor(client, Direction.LEFT);

            client.updateNeighbor(leftNeighbor, Direction.LEFT);
            client.updateNeighbor(rightNeighbor, Direction.RIGHT);

            if (isFirst)
                client.receiveToken();

            return id;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void deregister(String id) throws RemoteException {
        writeLock.lock();
        try {
            int index = clientCollection.indexOf(id);
            if (index == -1) return;

            AquaClient leftNeighbor = clientCollection.getLeftNeighorOf(index);
            AquaClient rightNeighbor = clientCollection.getRightNeighorOf(index);
            clientCollection.remove(index);

            if (leftNeighbor != null)
                leftNeighbor.updateNeighbor(rightNeighbor, Direction.RIGHT);
            if (rightNeighbor != null)
                rightNeighbor.updateNeighbor(leftNeighbor, Direction.LEFT);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void handoffFish(String id, FishModel fish) throws RemoteException {
        readLock.lock();
        try {
            int index = clientCollection.indexOf(id);
            if (index == -1) return;

            AquaClient neighbor;
            if (fish.getDirection() == Direction.LEFT)
                neighbor = clientCollection.getLeftNeighorOf(index);
            else
                neighbor = clientCollection.getRightNeighorOf(index);

            if (neighbor != null)
                neighbor.handoffFish(fish);
        } finally {
            readLock.unlock();
        }
    }

    public static void main(String[] args) {
        try {
            Broker broker = new Broker();
            AquaBroker stub = (AquaBroker) UnicastRemoteObject.exportObject(broker, 0);
            Registry registry = LocateRegistry.createRegistry(Properties.PORT);
            registry.rebind(Properties.BROKER_NAME, stub);
            System.out.println("Broker ready.");
        } catch (Exception e) {
            System.err.println("Server exception: " + e.toString());
            e.printStackTrace();
        }
    }
}
