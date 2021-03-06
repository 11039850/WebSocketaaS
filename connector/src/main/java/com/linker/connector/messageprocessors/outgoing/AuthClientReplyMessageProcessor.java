package com.linker.connector.messageprocessors.outgoing;

import com.linker.common.*;
import com.linker.common.messages.AuthClientReply;
import com.linker.common.messages.UserConnected;
import com.linker.connector.AuthStatus;
import com.linker.connector.NetworkUserService;
import com.linker.connector.configurations.ApplicationConfig;
import com.linker.connector.express.PostOffice;
import com.linker.connector.network.SocketHandler;
import io.netty.channel.ChannelFutureListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AuthClientReplyMessageProcessor extends OutgoingMessageProcessor<AuthClientReply> {
    @Autowired
    NetworkUserService networkUserService;

    @Autowired
    PostOffice postOffice;

    @Autowired
    ApplicationConfig applicationConfig;

    @Override
    public MessageType getMessageType() {
        return MessageType.AUTH_CLIENT_REPLY;
    }

    @Override
    public void doProcess(Message message, AuthClientReply data, MessageContext context) {
        String userId = data.getUserId();
        Long socketId = Long.parseLong(message.getMeta().getNote());
        SocketHandler socketHandler = networkUserService.getPendingUser(userId, socketId);
        if (socketHandler == null) {
            return;
        }

        networkUserService.removePendingUser(userId, socketId);

        if (data.getIsAuthenticated()) {
            log.info("user [{}] is authenticated", userId);
            networkUserService.addUser(userId, socketHandler);
            socketHandler.setAuthStatus(AuthStatus.AUTHENTICATED);
            socketHandler.sendMessage(message);

            MessageMeta meta = new MessageMeta();
            meta.setOriginalAddress(new Address(applicationConfig.getDomainName(), applicationConfig.getConnectorName(), socketId));
            Message userConnectedMessage = Message.builder()
                    .content(
                            MessageUtils.createMessageContent(MessageType.USER_CONNECTED, new UserConnected(userId),
                                    message.getContent().getFeature())
                    )
                    .from(Keywords.SYSTEM)
                    .to(Keywords.PROCESSOR)
                    .meta(meta)
                    .build();
            postOffice.deliverMessage(userConnectedMessage);
        } else {
            socketHandler.setAuthStatus(AuthStatus.NOT_AUTHENTICATED);
            socketHandler.sendMessage(message).addListener((ChannelFutureListener) future -> socketHandler.close());
        }
    }
}
