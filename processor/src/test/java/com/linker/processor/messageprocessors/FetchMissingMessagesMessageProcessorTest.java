package com.linker.processor.messageprocessors;

import com.linker.common.*;
import com.linker.common.messages.*;
import com.linker.processor.IntegrationTest;
import com.linker.processor.TestUtils;
import com.linker.processor.configurations.ApplicationConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Slf4j
public class FetchMissingMessagesMessageProcessorTest extends IntegrationTest {

    @Autowired
    ApplicationConfig applicationConfig;

    String masterUserId;

    String messageFrom = "ANZ-123224";

    @Before
    public void setup() {
        this.masterUserId = this.applicationConfig.getClientApps().get(0).getMasterUserId();
    }

    @Test
    public void test_processing() throws TimeoutException {
        prepareMissingMessages();
        Address masterAddress = new Address("domain-01", "connector-01", 12L);
        TestUtils.loginUser(masterUserId, masterAddress);

        Message message = Message.builder()
                .from(masterUserId)
                .content(MessageUtils.createMessageContent(MessageType.FETCH_MISSING_MESSAGES_REQUEST, new FetchMissingMessagesRequest(3), MessageFeature.RELIABLE))
                .meta(new MessageMeta(masterAddress))
                .build();

        givenMessage(message);

        List<Message> deliveredMessages = Arrays.asList(
                kafkaExpressDelivery.getDeliveredMessage(MessageType.USER_CONNECTED),
                kafkaExpressDelivery.getDeliveredMessage(MessageType.MESSAGE),
                kafkaExpressDelivery.getDeliveredMessage(MessageType.MESSAGE),
                kafkaExpressDelivery.getDeliveredMessage(MessageType.FETCH_MISSING_MESSAGES_COMPLETE)
        );


        List<Message> expectedDeliveredMessages = Arrays.asList(
                MessageUtils.touchMessage(
                        Message.builder()
                                .from(Keywords.SYSTEM)
                                .to(masterUserId)
                                .content(MessageUtils.createMessageContent(MessageType.USER_CONNECTED, new UserConnected("ANZ-1232122"), MessageFeature.RELIABLE))
                                .meta(new MessageMeta(new Address("domain-01", "connector-01", 10L), masterAddress))
                                .state(MessageState.ADDRESS_NOT_FOUND)
                                .build(),
                        2),
                MessageUtils.touchMessage(
                        Message.builder()
                                .from(messageFrom)
                                .to(masterUserId)
                                .content(MessageUtils.createMessageContent(MessageType.MESSAGE, new MessageForward(messageFrom, "message 1"), MessageFeature.RELIABLE))
                                .meta(new MessageMeta(new Address("domain-01", "connector-01", 1L), masterAddress))
                                .state(MessageState.ADDRESS_NOT_FOUND)
                                .build()),
                MessageUtils.touchMessage(
                        Message.builder()
                                .from(messageFrom)
                                .to(masterUserId)
                                .content(MessageUtils.createMessageContent(MessageType.MESSAGE, new MessageForward(messageFrom, "message 2"), MessageFeature.RELIABLE))
                                .meta(new MessageMeta(new Address("domain-01", "connector-01", 1L), masterAddress))
                                .state(MessageState.ADDRESS_NOT_FOUND)
                                .build()),
                MessageUtils.touchMessage(
                        Message.builder()
                                .from(Keywords.SYSTEM)
                                .to(masterUserId)
                                .content(MessageUtils.createMessageContent(MessageType.FETCH_MISSING_MESSAGES_COMPLETE, new FetchMissingMessagesComplete(1L), MessageFeature.RELIABLE))
                                .meta(new MessageMeta(masterAddress, masterAddress))
                                .state(MessageState.CREATED)
                                .build()
                )
        );
        expectedDeliveredMessages.forEach(msg -> msg.getMeta().setDeliveryType(DeliveryType.ANY));
        TestUtils.messagesEqual(expectedDeliveredMessages, deliveredMessages);
    }

    void prepareMissingMessages() {
        TestUtils.loginUser("ANZ-1232122");
        createMissingMessage("message 1");
        createMissingMessage("message 2");
        createMissingMessage("message 3");
    }

    void createMissingMessage(String text) {
        Message msg = Message.builder()
                .from(messageFrom)
                .meta(new MessageMeta(new Address("domain-01", "connector-01", 1L)))
                .content(MessageUtils.createMessageContent(MessageType.MESSAGE, new MessageRequest(masterUserId, text), MessageFeature.RELIABLE))
                .build();

        givenMessage(msg);
    }

}
