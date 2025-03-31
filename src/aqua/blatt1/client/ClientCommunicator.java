package aqua.blatt1.client;

import java.net.InetSocketAddress;
import java.util.Optional;

import messaging.Endpoint;
import messaging.Message;
import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.Properties;
import aqua.blatt1.common.msgtypes.DeregisterRequest;
import aqua.blatt1.common.msgtypes.HandoffRequest;
import aqua.blatt1.common.msgtypes.NeighborUpdate;
import aqua.blatt1.common.msgtypes.RegisterRequest;
import aqua.blatt1.common.msgtypes.RegisterResponse;

public class ClientCommunicator {
	private final Endpoint endpoint;

	public ClientCommunicator() {
		endpoint = new Endpoint();
	}

	public class ClientForwarder {
		private final InetSocketAddress broker;

		private ClientForwarder() {
			this.broker = new InetSocketAddress(Properties.HOST, Properties.PORT);
		}

		public void register() {
			endpoint.send(broker, new RegisterRequest());
		}

		public void deregister(String id) {
			endpoint.send(broker, new DeregisterRequest(id));
		}

		public void handOff(FishModel fish, Optional<InetSocketAddress> address) {
			if (address.isPresent()) {
				endpoint.send(address.get(), new HandoffRequest(fish));
			} else {
				System.err.println("AAHHHH, no address");
				endpoint.send(broker, new HandoffRequest(fish));
			}
		}
	}

	public class ClientReceiver extends Thread {
		private final TankModel tankModel;

		private ClientReceiver(TankModel tankModel) {
			this.tankModel = tankModel;
		}

		@Override
		public void run() {
			while (!isInterrupted()) {
				Message msg = endpoint.blockingReceive();

				if (msg.getPayload() instanceof RegisterResponse) {
					String id = ((RegisterResponse) msg.getPayload()).getId();
					InetSocketAddress leftNeighbor = ((RegisterResponse) msg.getPayload()).getLeftNeighbor();
					InetSocketAddress rightNeighbor = ((RegisterResponse) msg.getPayload()).getRightNeighbor();
					tankModel.onRegistration(id);
					tankModel.onNewNeighbor(leftNeighbor, Direction.LEFT);
					tankModel.onNewNeighbor(rightNeighbor, Direction.RIGHT);
				}

				if (msg.getPayload() instanceof HandoffRequest)
					tankModel.receiveFish(((HandoffRequest) msg.getPayload()).getFish());

				if (msg.getPayload() instanceof NeighborUpdate) {
					InetSocketAddress address = ((NeighborUpdate) msg.getPayload()).getAddress();
					Direction direction = ((NeighborUpdate) msg.getPayload()).getDirection();
					tankModel.onNewNeighbor(address, direction);
				}

			}
			System.out.println("Receiver stopped.");
		}
	}

	public ClientForwarder newClientForwarder() {
		return new ClientForwarder();
	}

	public ClientReceiver newClientReceiver(TankModel tankModel) {
		return new ClientReceiver(tankModel);
	}

}
