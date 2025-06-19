package aqua.blatt1.common.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;

public interface AquaClient extends Remote {
	void receiveToken() throws RemoteException;

	void handoffFish(FishModel fish) throws RemoteException;

	void updateNeighbor(AquaClient neighbor, Direction direction) throws RemoteException;
}
