package c8y.trackeragent_it;

import org.junit.Test;

import c8y.trackeragent.device.TrackerDevice;
import c8y.trackeragent.protocol.mapping.TrackingProtocol;
import c8y.trackeragent.protocol.telic.TelicDeviceMessages;
import c8y.trackeragent.utils.Devices;
import c8y.trackeragent.utils.Positions;
import c8y.trackeragent.utils.message.TrackerMessage;

public class TelicReportIT extends TrackerITSupport {
    
    private final TelicDeviceMessages deviceMessages = new TelicDeviceMessages(); 
    
    @Override
    protected TrackingProtocol getTrackerProtocol() {
        return TrackingProtocol.TELIC;
    }

    @Test
    public void shouldBootstrapNewDeviceAndThenChangeItsLocation() throws Exception {
        String imei = Devices.randomImei();
        bootstrapDevice(imei, deviceMessages.positionUpdate(imei, Positions.ZERO));  
        
        TrackerMessage positionUpdate = deviceMessages.positionUpdate(imei, Positions.SAMPLE_4);
        writeInNewConnection(positionUpdate);
        
        Thread.sleep(1000);
        TrackerDevice newDevice = getTrackerDevice(imei);
        Positions.assertEqual(newDevice.getPosition(), Positions.SAMPLE_4);
    }

}
