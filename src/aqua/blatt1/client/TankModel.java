package aqua.blatt1.client;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
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
	private static final Timer leaseTimer = new Timer();

	private SnapshotRecordingMode snapshotMode = SnapshotRecordingMode.IDLE;
	private int localSnapshot = 0;
	private boolean snapshotInitiator = false;
	private boolean leftMarkerReceived = false;
	private boolean rightMarkerReceived = false;
	private int inTransitFishLeft = 0;
	private int inTransitFishRight = 0;

	// Forward references for Task 1
	private final Map<String, FishReferenceState> forwardReferences = new ConcurrentHashMap<>();

	public TankModel(ClientCommunicator.ClientForwarder forwarder) {
		this.fishies = Collections.newSetFromMap(new ConcurrentHashMap<FishModel, Boolean>());
		this.forwarder = forwarder;
	}

	synchronized void onRegistration(String id, int leaseDuration) {
		this.id = id;
		newFish(WIDTH - FishModel.getXSize(), rand.nextInt(HEIGHT - FishModel.getYSize()));

		// start lease timer that reregisters with the broker
		leaseTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				forwarder.register();
			}
		}, leaseDuration / 2, leaseDuration / 2);
		
	}

	public synchronized void newFish(int x, int y) {
		if (fishies.size() < MAX_FISHIES) {
			x = x > WIDTH - FishModel.getXSize() - 1 ? WIDTH - FishModel.getXSize() - 1 : x;
			y = y > HEIGHT - FishModel.getYSize() ? HEIGHT - FishModel.getYSize() : y;

			FishModel fish = new FishModel("fish" + (++fishCounter) + "@" + getId(), x, y,
					rand.nextBoolean() ? Direction.LEFT : Direction.RIGHT);

			fishies.add(fish);
			forwardReferences.put(fish.getId(), FishReferenceState.HERE);
		}
	}

	synchronized void receiveFish(FishModel fish) {
		fish.setToStart();
		fishies.add(fish);
		forwardReferences.put(fish.getId(), FishReferenceState.HERE);
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
						case LEFT:
							forwarder.handOff(fish, leftNeigbor);
							forwardReferences.put(fish.getId(), FishReferenceState.LEFT);
							break;
						case RIGHT:
							forwarder.handOff(fish, rightNeighbor);
							forwardReferences.put(fish.getId(), FishReferenceState.RIGHT);
							break;
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
		leaseTimer.cancel();
		tokenTimer.cancel();
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
        forwardReferences.put(fish.getId(), FishReferenceState.HERE);
        // If in recording mode, add to in-transit count
        if (snapshotMode == SnapshotRecordingMode.LEFT && dir == Direction.LEFT) inTransitFishLeft++;
        if (snapshotMode == SnapshotRecordingMode.RIGHT && dir == Direction.RIGHT) inTransitFishRight++;
        if (snapshotMode == SnapshotRecordingMode.BOTH) {
            if (dir == Direction.LEFT) inTransitFishLeft++;
            if (dir == Direction.RIGHT) inTransitFishRight++;
        }
    }

    public synchronized void receiveSnapshotToken(int sum, boolean initiator) {
        int total;
        if (initiator && snapshotInitiator) {
            // Initiator: add nur die inTransitFish, nicht nochmal localSnapshot
            total = sum + inTransitFishLeft + inTransitFishRight;
            // Dialog asynchron, Token-Weitergabe vor Dialog!
            leftNeigbor.ifPresent(addr -> forwarder.sendSnapshotToken(addr, total, initiator));
            new Thread(() -> TankView.showGlobalSnapshot(total)).start();
            snapshotInitiator = false;
        } else {
            total = sum + localSnapshot + inTransitFishLeft + inTransitFishRight;
            leftNeigbor.ifPresent(addr -> forwarder.sendSnapshotToken(addr, total, initiator));
        }
    }

    public Optional<InetSocketAddress> getLeftNeighbor() {
        return leftNeigbor;
    }
    public Optional<InetSocketAddress> getRightNeighbor() {
        return rightNeighbor;
    }

    public void locateFishGlobally(String fishId) {
        FishReferenceState state = forwardReferences.get(fishId);
        if (state == null || state == FishReferenceState.HERE) {
            locateFishLocally(fishId);
        } else if (state == FishReferenceState.LEFT) {
            leftNeigbor.ifPresent(addr -> forwarderSendLocationRequest(addr, fishId));
        } else if (state == FishReferenceState.RIGHT) {
            rightNeighbor.ifPresent(addr -> forwarderSendLocationRequest(addr, fishId));
        }
    }

    private void locateFishLocally(String fishId) {
        for (FishModel fish : fishies) {
            if (fish.getId().equals(fishId)) {
                fish.toggle();
                setChanged();
                notifyObservers();
                break;
            }
        }
    }

    private void forwarderSendLocationRequest(InetSocketAddress addr, String fishId) {
        forwarderSendLocationRequestImpl(addr, fishId);
    }

    private void forwarderSendLocationRequestImpl(InetSocketAddress addr, String fishId) {
        forwarder.sendLocationRequest(addr, fishId);
    }
}