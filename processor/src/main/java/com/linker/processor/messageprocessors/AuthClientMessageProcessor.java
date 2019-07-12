package com.linker.processor.messageprocessors;

import com.linker.common.Keywords;
import com.linker.common.Message;
import com.linker.common.MessageContent;
import com.linker.common.MessageContext;
import com.linker.common.MessageFeature;
import com.linker.common.MessageMeta;
import com.linker.common.MessageProcessor;
import com.linker.common.MessageType;
import com.linker.common.MessageUtils;
import com.linker.common.messages.AuthClient;
import com.linker.common.messages.AuthClientReply;
import com.linker.processor.express.PostOffice;
import com.linker.processor.ProcessorUtils;
import com.linker.processor.models.ClientApp;
import com.linker.processor.services.ClientAppService;
import com.linker.processor.services.HttpService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Objects;

@Service
@Slf4j
public class AuthClientMessageProcessor extends MessageProcessor<AuthClient> {
    @Autowired
    PostOffice postOffice;

    @Autowired
    ProcessorUtils processorUtils;

    @Autowired
    ClientAppService clientAppService;

    @Autowired
    HttpService httpService;

    @Override
    public MessageType getMessageType() {
        return MessageType.AUTH_CLIENT;
    }

    @Override
    public void doProcess(Message message, AuthClient data, MessageContext context) throws IOException {
        ClientApp clientApp = clientAppService.getClientAppByUserId(data.getUserId());
        if (clientApp == null) {
            log.error("client app not found for user {}", data.getUserId());
            fail(message, data);
            return;
        }

        if (!Objects.equals(clientApp.getAppId(), data.getAppId())) {
            log.error("client app id does not match the given app id in request");
            fail(message, data);
            return;
        }

        if (!clientApp.isAuthEnabled()) {
            log.info("client auth not enabled for app [{}]", clientApp.getAppName());
            pass(message, data);
            return;
        }

        if (StringUtils.isBlank(clientApp.getAuthUrl())) {
            log.error("auth URL not provided for app [{}]", clientApp.getAppName());
            fail(message, data);
            return;
        }

        httpService.post(clientApp.getAuthUrl(), data, AuthClientReply.class)
                .thenAccept(authClientReply -> {
                    log.info("get response {} for user [{}]", authClientReply, data.getUserId());
                    if (Objects.equals(data.getAppId(), authClientReply.getAppId())
                            && Objects.equals(data.getUserId(), authClientReply.getUserId())) {
                        sendReply(message, authClientReply);
                    } else {
                        log.info("client app id or user id miss match");
                        fail(message, data);
                    }
                })
                .exceptionally(e -> {
                    log.error("error occurred", e);
                    fail(message, data);
                    return null;
                });
    }

    void fail(Message message, AuthClient data) {
        sendReply(message, new AuthClientReply(data.getAppId(), data.getUserId(), false));
    }

    void pass(Message message, AuthClient data) {
        sendReply(message, new AuthClientReply(data.getAppId(), data.getUserId(), true));
    }

    void sendReply(Message message, AuthClientReply data) {
        String toUser = message.getMeta().getOriginalAddress().getConnectorName();
        MessageMeta meta = processorUtils.getOriginalAddressMeta();
        meta.setNote(message.getMeta().getNote());

        AuthClientReply replyMessageData = new AuthClientReply(data.getAppId(), data.getUserId(), data.getIsAuthenticated());
        MessageContent content = MessageUtils.createMessageContent(MessageType.AUTH_CLIENT_REPLY, replyMessageData,
                MessageFeature.RELIABLE);

        Message replyMessage = Message.builder()
                .content(content)
                .from(Keywords.SYSTEM)
                .to(toUser)
                .meta(meta)
                .build();
        try {
            postOffice.deliveryMessage(replyMessage);
        } catch (IOException e) {
            log.error("occurred in send auth client reply", e);
        }
    }
}
