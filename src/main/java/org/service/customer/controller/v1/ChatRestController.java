package org.service.customer.controller.v1;

import org.service.customer.models.ChatMessage;
import org.service.customer.service.ChatService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chats")
public class ChatRestController {

    private final ChatService chatService;

    public ChatRestController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/{tenantId}/{customerId}")
    public List<ChatMessage> getChatHistory(@PathVariable String tenantId, @PathVariable String customerId) {
        return chatService.getChatHistory(tenantId, customerId);
    }


   /* @GetMapping("/{tenantId}/{customerId}")
    public List<ChatMessage> getChatHistoryWithPaging(
            @PathVariable String tenantId,
            @PathVariable String customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return chatService.getChatHistory(tenantId, customerId, page, size);
    }*/

}
