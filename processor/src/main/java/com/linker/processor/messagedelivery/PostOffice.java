package com.linker.processor.messagedelivery;


import com.linker.common.Message;
import com.linker.common.Utils;
import com.linker.processor.messageprocessors.MessageProcessorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@Slf4j
public class PostOffice {

    @Autowired
    MessageProcessorService messageProcessor;

    @Autowired
    RabbitMQExpressDelivery rabbitMQExpressDelivery;

    @Autowired
    KafkaExpressDelivery kafkaExpressDelivery;

    public PostOffice() {

    }

    void onMessageArrived(String message, ExpressDelivery expressDelivery) {
        try {
            Message msg = Utils.fromJson(message, Message.class);
            log.info("message arrived from {}:{}", expressDelivery.getType(), message);
            messageProcessor.process(msg);
        } catch (Exception e) {
            log.error("error occurred during message processing", e);
        }
    }

    public void deliveryMessage(Message message) throws IOException {
        ExpressDelivery expressDelivery = getExpressDelivery();
        log.info("delivery message with {}:{}", expressDelivery.getType(), message);
        String json = Utils.toJson(message);
        expressDelivery.deliveryMessage(json);
    }

    ExpressDelivery getExpressDelivery() {
        return kafkaExpressDelivery;
    }

}
