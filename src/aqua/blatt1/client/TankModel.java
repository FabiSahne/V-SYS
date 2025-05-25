package aqua.blatt1.client;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Iterator;
import java.util.Observable;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.Timer;
import java.util.TimerTask;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;

@SuppressWarnings("deprecation")
public class TankModel extends Observable implements Iterable<FishModel> {

	public static final int WIDTH = 600;
	public static final int HEIGHT = 350;
	protected static final int MAX_FISHIES = 5;
	protected static final Random rand = new Random();
	protected volatile String id;
	protected final Set<FishModel> fishies;
	protected int fishCounter = 0;
	protected final ClientCommunicator.ClientForwarder forwarder;
	private Optional<InetSocketAddress> rightNeighbor = Optional.empty();
	private Optional<InetSocketAddress> leftNeigbor = Optional.empty();
	private boolean hasToken = false;
	private static final Timer tokenTimer = new Timer();

	private SnapshotRecordingMode snapshotMode = SnapshotRecordingMode.IDLE;
	private int localSnapshot = 0;
	private boolean snapshotInitiator = false;
	private boolean leftMarkerReceived = false;
	private boolean rightMarkerReceived = false;
	private int inTransitFishLeft = 0;
	private int inTransitFishRight = 0;

	public TankModel(ClientCommunicator.ClientForwarder forwarder) {
		this.fishies = Collections.newSetFromMap(new ConcurrentHashMap<FishModel, Boolean>());
		this.forwarder = forwarder;
	}

	synchronized void onRegistration(String id) {
		this.id = id;
		newFish(WIDTH - FishModel.getXSize(), rand.nextInt(HEIGHT - FishModel.getYSize()));
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

	synchronized void receiveFish(FishModel fish) {
		fish.setToStart();
		fishies.add(fish);
	}

	synchronized void onNewNeighbor(InetSocketAddress address, Direction direction) {
		switch (direction) {
			case Direction.LEFT:
				leftNeigbor = Optional.of(address);
				break;
			case Direction.RIGHT:
				rightNeighbor = Optional.of(address);
				break;
		}
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
				if (hasToken()) {
					switch (fish.getDirection()) {
						case Direction.LEFT:
							forwarder.handOff(fish, leftNeigbor);
							break;
						case Direction.RIGHT:
							forwarder.handOff(fish, rightNeighbor);
					}
				} else {
					fish.reverse();
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
		forwarder.register();

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
		forwarder.deregister(id);
	}

	public boolean hasToken() {
		return hasToken;
	}

	public void receiveToken() {
		hasToken = true;
		tokenTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				InetSocketAddress address = rightNeighbor.get();
				forwarder.handOffToken(address);
				hasToken = false;
			}
		}, 2*1000);
	}

	public synchronized void initiateSnapshot() {
        if (snapshotMode != SnapshotRecordingMode.IDLE) return;
        snapshotInitiator = true;
        localSnapshot = getLocalFishCount();
        leftMarkerReceived = false;
        rightMarkerReceived = false;
        inTransitFishLeft = 0;
        inTransitFishRight = 0;
        snapshotMode = SnapshotRecordingMode.BOTH;
        // send marker to both neighbors
        leftNeigbor.ifPresent(addr -> forwarder.sendSnapshotMarker(addr));
        rightNeighbor.ifPresent(addr -> forwarder.sendSnapshotMarker(addr));
    }

    private int getLocalFishCount() {
        // Only count fish that are not disappearing (not in handoff animation)
        int count = 0;
        for (FishModel fish : fishies) {
            if (!fish.disappears()) count++;
        }
        return count;
    }

    public synchronized void receiveSnapshotMarker(Direction dir) {
        if (snapshotMode == SnapshotRecordingMode.IDLE) {
            // First marker received: record local state, start recording on other channel
            localSnapshot = getLocalFishCount();
            leftMarkerReceived = (dir == Direction.LEFT);
            rightMarkerReceived = (dir == Direction.RIGHT);
            snapshotMode = (dir == Direction.LEFT) ? SnapshotRecordingMode.RIGHT : SnapshotRecordingMode.LEFT;
            // send marker to both neighbors
            leftNeigbor.ifPresent(addr -> forwarder.sendSnapshotMarker(addr));
            rightNeighbor.ifPresent(addr -> forwarder.sendSnapshotMarker(addr));
        } else {
            // Second marker: stop recording on that channel
            if (dir == Direction.LEFT) leftMarkerReceived = true;
            if (dir == Direction.RIGHT) rightMarkerReceived = true;
            if (leftMarkerReceived && rightMarkerReceived) {
                snapshotMode = SnapshotRecordingMode.IDLE;
                // send token if not initiator
                if (!snapshotInitiator) {
                    leftNeigbor.ifPresent(addr -> forwarder.sendSnapshotToken(addr, localSnapshot, false));
                } else {
                    // initiator: start collecting
                    leftNeigbor.ifPresent(addr -> forwarder.sendSnapshotToken(addr, localSnapshot, true));
                }
            }
        }
    }

    public synchronized void receiveFish(FishModel fish, Direction dir) {
        fish.setToStart();
        fishies.add(fish);
        // If in recording mode, add to in-transit count
        if (snapshotMode == SnapshotRecordingMode.LEFT && dir == Direction.LEFT) inTransitFishLeft++;
        if (snapshotMode == SnapshotRecordingMode.RIGHT && dir == Direction.RIGHT) inTransitFishRight++;
        if (snapshotMode == SnapshotRecordingMode.BOTH) {
            if (dir == Direction.LEFT) inTransitFishLeft++;
            if (dir == Direction.RIGHT) inTransitFishRight++;
        }
    }

    public synchronized void receiveSnapshotToken(int sum, boolean initiator) {
        int total = sum + localSnapshot + inTransitFishLeft + inTransitFishRight;
        if (initiator && snapshotInitiator) {
            // Show result
            TankView.showGlobalSnapshot(total);
            snapshotInitiator = false;
        } else {
            leftNeigbor.ifPresent(addr -> forwarder.sendSnapshotToken(addr, total, initiator));
        }
    }

}