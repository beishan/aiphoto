package com.memoryvault.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String QUEUE_PHOTO_INDEX = "photo.index";
    public static final String QUEUE_TRAINING = "training.task";
    public static final String QUEUE_DEDUP = "dedup.scan";

    @Bean
    public Queue photoIndexQueue() {
        return new Queue(QUEUE_PHOTO_INDEX, true);
    }

    @Bean
    public Queue trainingQueue() {
        return new Queue(QUEUE_TRAINING, true);
    }

    @Bean
    public Queue dedupQueue() {
        return new Queue(QUEUE_DEDUP, true);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
