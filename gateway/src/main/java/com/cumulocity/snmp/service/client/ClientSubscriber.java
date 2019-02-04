package com.cumulocity.snmp.service.client;

import com.cumulocity.model.idtype.GId;
import com.cumulocity.snmp.annotation.gateway.RunWithinContext;
import com.cumulocity.snmp.model.core.ConfigEventType;
import com.cumulocity.snmp.model.device.DeviceAddedEvent;
import com.cumulocity.snmp.model.device.DeviceRemovedEvent;
import com.cumulocity.snmp.model.gateway.*;
import com.cumulocity.snmp.model.gateway.client.ClientDataChangedEvent;
import com.cumulocity.snmp.model.gateway.device.Device;
import com.cumulocity.snmp.model.gateway.type.core.Register;
import com.cumulocity.snmp.model.type.DeviceType;
import com.cumulocity.snmp.repository.core.Repository;
import com.google.common.base.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.snmp4j.PDU;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.cumulocity.snmp.model.core.ConfigEventType.NO_REGISTERS;
import static com.cumulocity.snmp.model.core.ConfigEventType.URL;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ClientSubscriber {

    private final Repository<DeviceType> deviceTypeRepository;
    private final Repository<Device> deviceRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TrapListener trapListener;
    Map<String,Map<String,PduListener>> mapIPAddressToOid = new HashMap();

    @EventListener
    @RunWithinContext
    public synchronized void refreshSubscription(final DeviceTypeAddedEvent event) {
        final Device device = event.getDevice();
        subscribe(event.getGateway(), device, event.getDeviceType());
    }

    @EventListener
    @RunWithinContext
    public synchronized void refreshSubscription(final DeviceTypeUpdatedEvent event) {
        final Device device = event.getDevice();
        subscribe(event.getGateway(), device, event.getDeviceType());
    }

    @EventListener
    @RunWithinContext
    public synchronized void refreshSubscription(final DeviceAddedEvent event) {
        final Gateway gateway = event.getGateway();
        final Optional<DeviceType> deviceTypeOptional = deviceTypeRepository.get(event.getDevice().getDeviceType());
        if (deviceTypeOptional.isPresent()) {
            final Device device = event.getDevice();
            subscribe(gateway, device, deviceTypeOptional.get());
        }
    }

    @EventListener
    @RunWithinContext
    public synchronized void refreshSubscriptions(final GatewayAddedEvent event) {
        final Gateway gateway = event.getGateway();
        try {
            updateSubscriptions(gateway);
        } catch (final Exception ex) {
            log.error(ex.getMessage(), ex);
            eventPublisher.publishEvent(new GatewayConfigErrorEvent(gateway, new ConfigEventType(ex.getMessage())));
        }
    }

    @EventListener
    @RunWithinContext
    public synchronized void refreshSubscriptions(final GatewayUpdateEvent event) {
        try {
            final Gateway gateway = event.getGateway();
            updateSubscriptions(gateway);
        } catch (final Exception ex) {
            log.error(ex.getMessage(), ex);
        }
    }

    private void updateSubscriptions(Gateway gateway) {
        trapListener.setGateway(gateway);
        final List<GId> currentDeviceIds = gateway.getCurrentDeviceIds();
        if (currentDeviceIds != null) {
            for (final GId gId : currentDeviceIds) {
                final Optional<Device> deviceOptional = deviceRepository.get(gId);
                if (deviceOptional.isPresent()) {
                    final Device device = deviceOptional.get();
                    final Optional<DeviceType> deviceTypeOptional = deviceTypeRepository.get(device.getDeviceType());
                    if (deviceTypeOptional.isPresent()) {
                        subscribe(gateway, device, deviceTypeOptional.get());
                    }
                }
            }
        }
    }

    @EventListener
    @RunWithinContext
    public synchronized void unsubscribe(final DeviceRemovedEvent event) {
        unsubscribe(event.getDevice());
    }

    private void subscribe(final Gateway gateway, final Device device, DeviceType deviceType) {
        try {
            if (!validRegisters(gateway, device, deviceType)) {
                return;
            }

            Map<String,PduListener> mapOidToPduListener = new HashMap();
            clearGatewayValidationErrors(gateway);
            trapListener.setGateway(gateway);
            for (final Register register : deviceType.getRegisters()) {
                mapOidToPduListener.put(register.getOid(),new PduListener() {
                    @Override
                    public void onPduRecived(PDU pdu) {
                        eventPublisher.publishEvent(new ClientDataChangedEvent(gateway, device, register, new DateTime(), pdu.getType()));
                    }
                });
            }

            mapIPAddressToOid.put(device.getIpAddress(), mapOidToPduListener);
            trapListener.subscribe(mapIPAddressToOid);


        } catch (final Exception ex) {
            log.error(ex.getMessage(), ex);
            eventPublisher.publishEvent(new GatewayConfigErrorEvent(gateway, new ConfigEventType(ex.getMessage())));
        }
    }

    private boolean validRegisters(Gateway gateway, Device device, DeviceType deviceType) {
        boolean valid = isNotEmpty(deviceType.getRegisters());
        if (!valid) {
            eventPublisher.publishEvent(new DeviceConfigErrorEvent(gateway, device, null, NO_REGISTERS));
        }
        return valid;
    }

    private void clearGatewayValidationErrors(Gateway gateway) {
        eventPublisher.publishEvent(new GatewayConfigSuccessEvent(gateway, URL));
    }

    private void unsubscribe(Device device){
        trapListener.unsubscribe(device.getIpAddress());

    }

}
