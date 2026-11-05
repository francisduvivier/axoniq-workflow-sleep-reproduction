package io.axoniq.example.timer;

public record TimerScheduled(String timerId, long seconds) {
}
