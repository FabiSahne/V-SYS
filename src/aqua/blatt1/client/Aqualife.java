package aqua.blatt1.client;

import aqua.blatt1.common.Properties;
import aqua.blatt1.common.rmi.AquaBroker;

import javax.swing.SwingUtilities;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Aqualife {

	public static void main(String[] args) {
		try {
			Registry registry = LocateRegistry.getRegistry(Properties.HOST, Properties.PORT);
			AquaBroker broker = (AquaBroker) registry.lookup(Properties.BROKER_NAME);
			TankModel tankModel = new TankModel(broker);
			SwingUtilities.invokeLater(new AquaGui(tankModel));
			tankModel.run();
		} catch (Exception e) {
			System.err.println("Client exception: " + e.toString());
			e.printStackTrace();
		}
	}
}
