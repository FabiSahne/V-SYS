package aqua.blatt1.client;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JMenuItem;

public class ToggleController implements ActionListener {
    private final TankModel tankModel;

    public ToggleController(TankModel tankModel) {
        this.tankModel = tankModel;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() instanceof JMenuItem) {
            JMenuItem menuItem = (JMenuItem) e.getSource();
            String fishId = menuItem.getText();
            // TODO: Fix snapshots
            // tankModel.locateFishGlobally(fishId);
        }
    }
}
