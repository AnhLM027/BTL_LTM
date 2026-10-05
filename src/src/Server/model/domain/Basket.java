package Server.model.domain;

public record Basket(
        int basketId,
        int groupId,
        String basketName,
        String assetPath,
        boolean active
) {
}
