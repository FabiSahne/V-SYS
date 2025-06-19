package aqua.blatt1.client;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.rmi.AquaBroker;
import aqua.blatt1.common.rmi.AquaClient;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collections;
import java.util.Iterator;
import java.util.Observable;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("deprecation")
public class TankModel extends Observable implements Iterable<FishModel>, AquaClient, Serializable {

	public static final int WIDTH = 600;
	public static final int HEIGHT = 350;
	protected static final int MAX_FISHIES = 5;
	protected static final Random rand = new Random();
	protected volatile String id;
	protected final Set<FishModel> fishies;
	protected int fishCounter = 0;
	private final AquaBroker broker;
	private AquaClient leftNeighbor;
	private AquaClient rightNeighbor;
	private boolean hasToken = false;

	// snapshot
	private SnapshotRecordingMode snapshotMode = SnapshotRecordingMode.IDLE;
	private int localSnapshot = 0;
	private boolean snapshotInitiator = false;
	private boolean leftMarkerReceived = false;
	private boolean rightMarkerReceived = false;
	private int inTransitFishLeft = 0;
	private int inTransitFishRight = 0;

	public TankModel(AquaBroker broker) {
		this.broker = broker;
		this.fishies = Collections.newSetFromMap(new ConcurrentHashMap<FishModel, Boolean>());
		try {
			UnicastRemoteObject.exportObject(this, 0);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	private void register() {
		try {
			id = broker.register(this);
			newFish(WIDTH - FishModel.getXSize(), rand.nextInt(HEIGHT - FishModel.getYSize()));
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	public synchronized void newFish(int x, int y) {
		if (fishies.size() < MAX_FISHIES) {
			x = x > WIDTH - FishModel.getXSize() - 1 ? WIDTH - FishModel.getXSize() - 1 : x;
			y = y > HEIGHT - FishModel.getYSize() ? HEIGHT - FishModel.getYSize() : y;

			FishModel fish = new FishModel("fish" + (++fishCounter) + "@" + getId(), x, y,
					rand.nextBoolean() ? Direction.LEFT : Direction.RIGHT);

			fishies.add(fish);
		}
	}

	@Override
	public void handoffFish(FishModel fish) throws RemoteException {
		fish.setToStart();
		fishies.add(fish);
	}

	@Override
	public void updateNeighbor(AquaClient neighbor, Direction direction) throws RemoteException {
		if (direction == Direction.LEFT)
			this.leftNeighbor = neighbor;
		else
			this.rightNeighbor = neighbor;
	}

	public String getId() {
		return id;
	}

	public synchronized int getFishCounter() {
		return fishCounter;
	}

	public synchronized Iterator<FishModel> iterator() {
		return fishies.iterator();
	}

	private synchronized void updateFishies() {
		for (Iterator<FishModel> it = iterator(); it.hasNext();) {
			FishModel fish = it.next();

			fish.update();

			if (fish.hitsEdge()) {
				try {
					if (hasToken()) {
						switch (fish.getDirection()) {
							case Direction.RIGHT: 
								rightNeighbor.handoffFish(fish);
								break;
							case Direction.LEFT:
								leftNeighbor.handoffFish(fish);
								break;
						};
					} else {
						fish.reverse();
					}
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}

			if (fish.disappears())
				it.remove();
		}
	}

	private synchronized void update() {
		updateFishies();
		setChanged();
		notifyObservers();
	}

	protected void run() {
		register();

		try {
			while (!Thread.currentThread().isInterrupted()) {
				update();
				TimeUnit.MILLISECONDS.sleep(10);
			}
		} catch (InterruptedException consumed) {
			// allow method to terminate
		}
	}

	public synchronized void finish() {
		try {
			broker.deregister(id);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	public boolean hasToken() {
		return hasToken;
	}

	@Override
	public void receiveToken() throws RemoteException {
		hasToken = true;
		new Thread(() -> {
			try {
				Thread.sleep(2000);
				if (rightNeighbor != null) {
					rightNeighbor.receiveToken();
					hasToken = false;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}).start();
	}

	@Override
	public void sendSnapshotMarker(Direction direction) {
		if (snapshotMode == SnapshotRecordingMode.IDLE) {
			localSnapshot = getLocalFishCount();
			leftMarkerReceived = (direction == Direction.RIGHT);
			rightMarkerReceived = (direction == Direction.LEFT);
			snapshotMode = leftMarkerReceived ? SnapshotRecordingMode.RIGHT : SnapshotRecordingMode.LEFT;
			try {
				if (leftMarkerReceived) {
					rightNeighbor.sendSnapshotMarker(direction);
				} else {
					leftNeighbor.sendSnapshotMarker(direction);
				}
			} catch (RemoteException e) {
				System.err.println("Exception: " + e);
			}
		} else {
			leftMarkerReceived = (direction == Direction.RIGHT);
			rightMarkerReceived = (direction == Direction.LEFT);
		}
	}

	public synchronized void initiateSnapshot() {
		if (snapshotMode == SnapshotRecordingMode.IDLE) {
			snapshotInitiator = true;
			localSnapshot = getLocalFishCount();
			leftMarkerReceived = false;
			rightMarkerReceived = false;
			inTransitFishLeft = 0;
			inTransitFishRight = 0;
			snapshotMode = SnapshotRecordingMode.BOTH;

			//send marker
			try {
				rightNeighbor.sendSnapshotMarker(Direction.RIGHT);
			} catch (RemoteException e) {
				System.err.println("Exception: " + e);
			}
		}
	}

	private int getLocalFishCount() {
		return (int) fishies.stream().filter(f -> f.disappears()).count();
	}
}