package com.cumulocity.agent.snmp.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@Data
@ToString(exclude = "password")
@PropertySource(value = "file:${user.home}/.snmp/snmp-agent-gateway${spring.profiles.active:}.properties", ignoreResourceNotFound = true)
public class BootstrapProperties {

    @Value("#{'${C8Y.bootstrap.tenant:management}'.trim()}")
    private String tenantId;

    @Value("#{'${C8Y.bootstrap.user:devicebootstrap}'.trim()}")
    private String username;

    @Value("#{'${C8Y.bootstrap.password:}'.trim()}")
    private String password;
}