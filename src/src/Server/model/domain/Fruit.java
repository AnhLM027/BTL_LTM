package Server.model.domain;

public record Fruit(
        int fruitId,
        int groupId,
        String fruitCode,
        String fruitName,
        String defaultAssetPath,
        boolean active
) {
}
