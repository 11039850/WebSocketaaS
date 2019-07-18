package com.linker.processor.messageprocessors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.linker.common.Keywords;
import com.linker.common.Message;
import com.linker.common.MessageFeature;
import com.linker.common.MessageMeta;
import com.linker.common.MessageState;
import com.linker.common.MessageType;
import com.linker.common.MessageUtils;
import com.linker.common.messages.UserDisconnected;
import com.linker.processor.IntegrationTest;
import com.linker.processor.TestUser;
import com.linker.processor.TestUtils;
import com.linker.processor.models.ClientApp;
import com.linker.processor.models.UserChannel;
import com.linker.processor.services.ClientAppService;
import com.linker.processor.services.UserChannelService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


public class UserDisconnectedMessageProcessorTest extends IntegrationTest {

    @Autowired
    ClientAppService clientAppService;

    @Autowired
    UserChannelService userChannelService;

    @Test
    public void test_normal_user_disconnected_processing() throws JsonProcessingException, TimeoutException {
        ClientApp clientApp = clientAppService.getClientAppByName("ANZ");
        String masterUserId = clientApp.getMasterUserId();
        TestUser masterUser = TestUtils.loginUser(masterUserId, 11L);

        String userId = "ANZ-12345";
        TestUser testUser = TestUtils.loginUser(userId, 10L);
        UserChannel user = userChannelService.getById(userId);
        assertNotNull(user);
        kafkaExpressDelivery.getDeliveredMessage(MessageType.USER_CONNECTED);

        // check message sent to master
        TestUtils.logoutUser(testUser);
        Message deliveredMessage = kafkaExpressDelivery.getDeliveredMessage(MessageType.USER_DISCONNECTED);
        Message expectedDeliveredMessage =
                MessageUtils.touchMessage(
                        Message.builder()
                                .from(Keywords.SYSTEM)
                                .to(masterUserId)
                                .content(MessageUtils.createMessageContent(MessageType.USER_DISCONNECTED, new UserDisconnected(userId), MessageFeature.RELIABLE))
                                .meta(new MessageMeta(testUser.getAddress(), masterUser.getAddress()))
                                .build()
                );
        TestUtils.messageEquals(expectedDeliveredMessage, deliveredMessage);

        // check saved message
        Message savedMessage = messageRepository.findById(deliveredMessage.getId());
        Message expectedSavedMessage = deliveredMessage.clone();
        expectedSavedMessage.setTo(null);
        expectedSavedMessage.getMeta().setTargetAddress(null);
        TestUtils.messageEquals(expectedSavedMessage, savedMessage);
    }

    @Test
    public void test_master_user_disconnected_processing() throws JsonProcessingException {
        ClientApp clientApp = clientAppService.getClientAppByName("ANZ");
        String masterUserId = clientApp.getMasterUserId();
        TestUser masterUser = TestUtils.loginUser(masterUserId, 10L);
        TestUtils.logoutUser(masterUser);
        Message disconnectedMessage = messageRepository.findById(masterUser.getConnectedMessageId());
        assertEquals(MessageState.PROCESSED, disconnectedMessage.getState());
    }
}