/*
 * Copyright (c) 2012-2020 Cumulocity GmbH
 * Copyright (c) 2021 Software AG, Darmstadt, Germany and/or Software AG USA Inc., Reston, VA, USA,
 * and/or its subsidiaries and/or its affiliates and/or their licensors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cumulocity.agent.snmp.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Data
@Configuration
@PropertySource(value = "file:${user.home}/.snmp/snmp-agent-gateway${spring.profiles.active:}.properties", ignoreResourceNotFound = true)
public class GatewayProperties {

	@Autowired
	private BootstrapProperties bootstrapProperties;

	@Value("#{'${gateway.identifier:snmp-agent}'.trim()}")
	private String gatewayIdentifier;

	@Value("#{'${gateway.db.baseDir:${user.home}}'.trim()}")
	private String gatewayDatabaseBaseDir;

	@Value("#{'${gateway.bootstrapFixedDelay:10000}'.trim()}")
	private int bootstrapFixedDelay;

	@Value("#{'${gateway.availability.interval:10}'.trim()}")
	private int gatewayAvailabilityInterval;

	// This property is not exposed to the end user, hence not present
	// in the snmp-agent-gateway.properties file and is also not documented. 
	@Value("#{'${gateway.objects.refresh.interval:1}'.trim()}")
	private int gatewayObjectRefreshIntervalInMinutes;

	@Value("#{'${gateway.threadPool.size:30}'.trim()}")
	private int gatewayThreadPoolSize;

	@Value("#{'${gateway.maxBatch.size:500}'.trim()}")
	private int gatewayMaxBatchSize;

	@Value("#{'${gateway.publish.retryLimit:5}'.trim()}")
	private short gatewayPublishRetryLimit;

	@Value("#{'${gateway.bootstrap.force:false}'.trim()}")
	private boolean forcedBootstrap;

	@Value("#{'${C8Y.baseURL:https://developers.cumulocity.com}'.trim()}")
	private String baseUrl;

	@Value("#{'${C8Y.forceInitialHost:true}'.trim()}")
	private boolean forceInitialHost;

	public int getThreadPoolSizeForTrapProcessing() {
		// Using 20% of the total threads configured for gateway to Trap listening
		int poolSize = getGatewayThreadPoolSize() * 20 / 100;
		return (poolSize <= 0) ? 2 : poolSize;
	}

	public int getThreadPoolSizeForScheduledTasks() {
		/*
		 * Using 80% of the total threads configured for gateway to internal
		 * publish/subscribe service, polling and auto-discovery
		 */
		int poolSize = getGatewayThreadPoolSize() * 80 / 100;
		return (poolSize <= 0) ? 8 : poolSize;
	}


	@Configuration
	@Data
	@ToString
	public class SnmpProperties {
		@Value("#{'${snmp.trapListener.protocol:UDP}'.trim()}")
		private String trapListenerProtocol;

		@Value("#{'${snmp.trapListener.port:6671}'.trim()}")
		private int trapListenerPort;

		@Value("#{'${snmp.trapListener.address:}'.trim()}")
		private String trapListenerAddress;

		@Value("#{'${snmp.community.target}'.trim()}")
		private String communityTarget;

		@Value("#{'${snmp.polling.port:161}'.trim()}")
		private int pollingPort;

		@Value("#{'${snmp.polling.version:0}'.trim()}")
		private int pollingVersion;

		@Value("#{'${snmp.autodiscovery.devicePingTimeoutPeriod:3}'.trim()}")
		private int autoDiscoveryDevicePingTimeoutPeriod;

		public boolean isTrapListenerProtocolUdp() {
			return "UDP".equalsIgnoreCase(trapListenerProtocol);
		}

		public boolean isTrapListenerProtocolTcp() {
			return "TCP".equalsIgnoreCase(trapListenerProtocol);
		}
	}
}