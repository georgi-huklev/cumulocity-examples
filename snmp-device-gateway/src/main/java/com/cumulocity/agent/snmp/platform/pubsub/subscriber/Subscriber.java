package com.cumulocity.agent.snmp.platform.pubsub.subscriber;

import com.cumulocity.agent.snmp.config.ConcurrencyConfiguration;
import com.cumulocity.agent.snmp.platform.model.GatewayDataRefreshedEvent;
import com.cumulocity.agent.snmp.platform.pubsub.service.PubSub;
import com.cumulocity.agent.snmp.platform.service.GatewayDataProvider;
import com.cumulocity.agent.snmp.platform.service.PlatformProvider;
import com.cumulocity.sdk.client.SDKException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.httpclient.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;

import javax.annotation.PreDestroy;
import java.util.Collection;
import java.util.Collections;

/**
 * Subscriber to handle messages,
 * which subscribes itself on BootstrapReadyEvent,
 * refreshes subscription on GatewayDataRefreshedEvent and
 * unsubscribes on PreDestroy Spring callback.
 *
 * @param <PS> PubSub service to subscribe to.
 */
@Slf4j
public abstract class Subscriber<PS extends PubSub> {

    @Autowired
    ConcurrencyConfiguration concurrencyConfiguration;

    @Autowired
    private GatewayDataProvider gatewayDataProvider;

    @Autowired
    private PlatformProvider platformProvider;

    @Autowired
    private PS pubSub;


    private long transmitRateInSeconds = -1;


    public long getTransmitRateInSeconds() {
        return transmitRateInSeconds;
    }

    public boolean isReady() {
        return platformProvider.isPlatformAvailable();
    }

    public boolean isBatchingSupported() {
        return false;
    }

    public int getBatchSize() {
        // 200 is the default value, which should suffice.
        // This can be made configurable if required.
        return 200;
    }

    public abstract int getConcurrentSubscriptionsCount();

    public void onMessage(String message) throws PlatformPublishException {
        try {
            handleMessage(message);
        } catch(SDKException sdke) {
            if (isExceptionDueToInvalidMessage(sdke)) {
                // If the error is caused by an invalid message which is being processed, we will not be able to do much here.
                // Just log the message with the exception details and continue.
                // Log the message and return
                log.error("Skipped publishing the following invalid message to the Platform.\n{}", message, sdke);
            }
            else {
                log.error("{} subscriber failed to publish message to the Platform. May be Platform is unavailable." +
                        "\nThrowing exception so the failed message is put back in the Queue. " +
                        "Will be published when Platform is back online again.", this.getClass().getSimpleName(), sdke);

                // Unable to publish as the platform is unavailable,
                // so mark the platform as unavailable and put the message(s) already read, back into the queue.
                log.debug("{} subscriber is marking the platform as unavailable.", this.getClass().getSimpleName());
                platformProvider.markPlatfromAsUnavailable();

                throw new PlatformPublishException(Collections.singletonList(message), sdke);
            }
        }
    }

    public void onMessages(Collection<String> messageCollection) throws PlatformPublishException {
        try {
            handleMessages(messageCollection);
        } catch(SDKException sdke) {
            if (isExceptionDueToInvalidMessage(sdke)) {
                // If the error is caused by an invalid message which is being processed, we will not be able to do much here.
                // Just log the message with the exception details and continue.
                // Log the messages and return
                for(String oneMessage : messageCollection) {
                    log.error("Skipped publishing the following invalid message to the Platform.\n{}", oneMessage, sdke);
                }
            }
            else {
                log.error("{} subscriber failed to publish messages to the Platform. May be Platform is unavailable." +
                        "\nThrowing exception so the failed messages are put back in the Queue. " +
                        "Will be published when Platform is back online again.", this.getClass().getSimpleName(), sdke);

                // Unable to publish as the platform is unavailable,
                // so mark the platform as unavailable and put the message(s) already read, back into the queue.
                log.debug("{} subscriber is marking the platform as unavailable.", this.getClass().getSimpleName());
                platformProvider.markPlatfromAsUnavailable();

                throw new PlatformPublishException(messageCollection, sdke);
            }
        }
    }

    protected abstract void handleMessage(String message);

    protected void handleMessages(Collection<String> messageCollection) {
        throw new UnsupportedOperationException();
    }

    //TODO: @EventListener(BootstrapReadyEvent.class)
    @EventListener(GatewayDataRefreshedEvent.class)
    void subscribe() {
        // TODO: REMOVE THIS CODE AFTER LISTENING TO BootstrapReadyEvent
        if(transmitRateInSeconds != -1) {
            return;
        }
        // TODO: REMOVE THIS CODE AFTER LISTENING TO BootstrapReadyEvent

        pubSub.subscribe(this); // Subscribing for the first time

        this.transmitRateInSeconds = fetchTransmitRateFromGatewayDevice();

        log.debug("{} subscribed to {}.", this.getClass().getName(), pubSub.getClass().getName());
    }

    @EventListener(GatewayDataRefreshedEvent.class)
    void refreshSubscription() {
        if(isBatchingSupported()) {
            long transmitRateFromGatewayDevice = fetchTransmitRateFromGatewayDevice();
            if(transmitRateInSeconds != transmitRateFromGatewayDevice) {
                // Refresh the subscription only when the Transmit Rate
                // has changed for the Subscribers supporting batching
                pubSub.unsubscribe(this);
                pubSub.subscribe(this);

                this.transmitRateInSeconds = transmitRateFromGatewayDevice;

                log.debug("{} refreshed its subscription as the transmit rate changed.", this.getClass().getName());
            }
        }
    }

    @PreDestroy
    void unsubscribe() {
        pubSub.unsubscribe(this);

        log.debug("{} unsubscribed to {}.", this.getClass().getName(), pubSub.getClass().getName());
    }

    private long fetchTransmitRateFromGatewayDevice() {
        return gatewayDataProvider.getGatewayDevice().getSnmpCommunicationProperties().getTransmitRate();
    }

    private boolean isExceptionDueToInvalidMessage(SDKException sdke) {
        int httpStatus = sdke.getHttpStatus();
        return     httpStatus >= HttpStatus.SC_BAD_REQUEST
                && httpStatus < HttpStatus.SC_INTERNAL_SERVER_ERROR
                && !(   httpStatus == HttpStatus.SC_UNAUTHORIZED
                     || httpStatus == HttpStatus.SC_PAYMENT_REQUIRED
                     || httpStatus == HttpStatus.SC_REQUEST_TIMEOUT
                    );
    }
}