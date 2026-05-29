package com.memoryvault.websocket;

import com.memoryvault.dto.TaskProgressDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProgressWebSocketHandler {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Send task progress update to all subscribed clients.
     */
    public void sendProgress(TaskProgressDTO progress) {
        messagingTemplate.convertAndSend("/topic/tasks/" + progress.getTaskId(), progress);
    }

    /**
     * Send broadcast to all connected clients.
     */
    public void broadcast(String topic, Object message) {
        messagingTemplate.convertAndSend("/topic/" + topic, message);
    }
}
