package Server.model.domain;

public record Basket(
        int basketId,
        String basketName,
        String assetPath,
        boolean active
) {
}
