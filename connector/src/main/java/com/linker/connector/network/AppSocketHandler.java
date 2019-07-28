package com.linker.connector.network;

import com.linker.common.*;
import com.linker.common.messages.UserDisconnected;
import com.linker.connector.AuthStatus;
import com.linker.connector.configurations.ApplicationConfig;
import com.linker.connector.messageprocessors.MessageProcessorService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class AppSocketHandler extends SimpleChannelInboundHandler<MessageContent> implements SocketHandler {

    @Autowired
    MessageProcessorService messageProcessorService;
    @Autowired
    ApplicationConfig applicationConfig;

    ChannelHandlerContext context;

    @Getter
    @Setter
    String userId;

    @Getter
    @Setter
    AuthStatus authStatus = AuthStatus.NOT_AUTHENTICATED;

    @Getter
    Long socketId;
    public static AppSocketHandler instance;

    public AppSocketHandler(Long socketId) {
        instance = this;
        this.socketId = socketId;
    }

    protected void channelRead0(ChannelHandlerContext ctx, MessageContent msgContent) throws Exception {
        MessageMeta meta = new MessageMeta(new Address(applicationConfig.getDomainName(), applicationConfig.getConnectorName(), this.socketId));
        Message message = Message.builder()
                .content(msgContent)
                .from(this.userId)
                .meta(meta)
                .build();

        this.messageProcessorService.processIncomingMessage(message, this);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.context = ctx;
        log.info("channel added");
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        log.info("channel removed");
        if (this.authStatus == AuthStatus.NOT_AUTHENTICATED) {
            return;
        }
        MessageMeta meta = new MessageMeta(new Address(applicationConfig.getDomainName(), applicationConfig.getConnectorName(), socketId));
        Message message = Message.builder()
                .content(
                        MessageUtils.createMessageContent(MessageType.USER_DISCONNECTED, new UserDisconnected(this.userId)
                                , MessageFeature.RELIABLE)
                )
                .from(Keywords.SYSTEM)
                .meta(meta)
                .build();
        this.messageProcessorService.processIncomingMessage(message, this);
    }

    public ChannelFuture sendMessage(MessageContent message) {
        return context.writeAndFlush(message.toContentOutput());
    }

    public ChannelFuture sendMessage(Message message) {
        return sendMessage(message.getContent());
    }

    public ChannelFuture close() {
        return this.context.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("exception received", cause);
    }

    @Override
    public Channel getChannel() {
        return context.channel();
    }
}