package com.gucardev.dynamicreportsentity.platform.assistant;

/** One turn of the chat as the UI keeps it; {@code role} is {@code user} or {@code assistant}. */
public record ChatMessage(String role, String content) {
}
