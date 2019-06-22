package com.linker.connector;

import com.linker.common.Message;
import io.netty.channel.ChannelFuture;

public interface SocketHandler {
    ChannelFuture sendMessage(String message);

    ChannelFuture sendMessage(Message message);


    void setUserId(String userId);

    String getUserId();

    ChannelFuture close();
}