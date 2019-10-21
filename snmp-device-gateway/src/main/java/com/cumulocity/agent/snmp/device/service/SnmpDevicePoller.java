package com.cumulocity.agent.snmp.device.service;

import java.io.IOException;
import java.util.List;

import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.ScopedPDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.TransportMapping;
import org.snmp4j.UserTarget;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.TcpAddress;
import org.snmp4j.smi.TransportIpAddress;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultTcpTransportMapping;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import com.cumulocity.agent.snmp.config.GatewayProperties;
import com.cumulocity.agent.snmp.platform.model.DeviceManagedObjectWrapper;
import com.cumulocity.agent.snmp.util.IpAddressUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SnmpDevicePoller {

	private DeviceManagedObjectWrapper deviceWrapper;

	private GatewayProperties.SnmpProperties snmpProperties;

	private List<VariableBinding> variableBindingList;

	private Snmp snmp;

	private PDU pdu;

	private TransportMapping<?> transport;

	private Target target;

	public SnmpDevicePoller(GatewayProperties.SnmpProperties snmpProperties, DeviceManagedObjectWrapper deviceWrapper,
			List<VariableBinding> variableBindingList) throws IOException {
		this.deviceWrapper = deviceWrapper;
		this.snmpProperties = snmpProperties;
		this.variableBindingList = variableBindingList;

		init();
	}

	private void init() throws IOException {
		String trapListenerBindingAddress = IpAddressUtil
				.sanitizeIpAddress(deviceWrapper.getProperties().getIpAddress(), true);
		String addressString = trapListenerBindingAddress + "/" + deviceWrapper.getProperties().getPort();
		String protocol = getProtocol();

		TransportIpAddress address;
		if ("TCP".equalsIgnoreCase(protocol)) {
			address = new TcpAddress(addressString);
			transport = new DefaultTcpTransportMapping();
		} else {
			address = new UdpAddress(addressString);
			transport = new DefaultUdpTransportMapping();
		}

		this.snmp = new Snmp(transport);
		target = getTarget(address);
		this.pdu = getPDU();

		log.debug("Starting OID poller on {} using {} protocol", addressString, protocol);
		this.transport.listen();
		log.info("Started OID poller on {} using {} protocol", addressString, protocol);
	}

	public ResponseEvent poll() throws IOException {
		pdu.clear();
		pdu.setType(PDU.GET);

		variableBindingList.forEach((variableBinding) -> {
			pdu.add(variableBinding);
		});

		return snmp.send(pdu, target);
	}

	public void close() {
		if (transport != null) {
			try {
				transport.close();
			} catch (IOException e) {
				log.error("IOException while closing TransportMapping ", e);
			}
		}

		if (snmp != null) {
			try {
				snmp.close();
			} catch (IOException e) {
				log.error("IOException while closing SNMP connection ", e);
			}
		}
	}

	private String getProtocol() {
		String protocol = "UDP";
		if (deviceWrapper.getProperties().isTcp() || snmpProperties.isTrapListenerProtocolTcp()) {
			protocol = deviceWrapper.getProperties().getProtocol().toUpperCase();
		}

		return protocol;
	}

	private Target getTarget(TransportIpAddress address) {
		if (SnmpConstants.version3 == deviceWrapper.getProperties().getVersion()) {
			UserTarget userTarget = new UserTarget();
			userTarget.setVersion(deviceWrapper.getProperties().getVersion());
			userTarget.setSecurityLevel(deviceWrapper.getProperties().getAuth().getSecurityLevel());
			userTarget.setSecurityName(new OctetString(deviceWrapper.getProperties().getAuth().getUsername()));
			userTarget.setAddress(address);
			userTarget.setRetries(3);
			userTarget.setTimeout(1000 * 5);

			return userTarget;
		} else {
			CommunityTarget communityTarget = new CommunityTarget();
			communityTarget.setCommunity(new OctetString(snmpProperties.getCommunityTarget()));
			communityTarget.setAddress(address);
			communityTarget.setRetries(3);
			communityTarget.setTimeout(1000 * 5);
			communityTarget.setVersion(deviceWrapper.getProperties().getVersion());

			return communityTarget;
		}
	}

	private PDU getPDU() {
		if (SnmpConstants.version3 == deviceWrapper.getProperties().getVersion()) {
			ScopedPDU scopedPDU = new ScopedPDU();

			// By now, security model and user configuration is done in
			// DeviceListenerService while handling BootstrapReadyEvent

			if (deviceWrapper.getProperties().getAuth().getEngineId() != null) {
				OctetString engineId = new OctetString(deviceWrapper.getProperties().getAuth().getEngineId());
				scopedPDU.setContextEngineID(engineId);
			}
			return scopedPDU;
		}

		return new PDU();
	}
}
