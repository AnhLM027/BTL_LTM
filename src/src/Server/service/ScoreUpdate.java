package Server.service;

public record ScoreUpdate(long matchId, long playerId, int score, int correctCount, int wrongCount,
                          boolean wasCorrect, int comboCount, int comboBonus) {}
