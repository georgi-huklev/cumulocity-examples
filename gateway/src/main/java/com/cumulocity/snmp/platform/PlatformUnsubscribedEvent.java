package com.cumulocity.snmp.platform;

import com.cumulocity.snmp.model.gateway.Gateway;
import lombok.Data;

@Data
public class PlatformUnsubscribedEvent {
    private final Gateway gateway;
}
