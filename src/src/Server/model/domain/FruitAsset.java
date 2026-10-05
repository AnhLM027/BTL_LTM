package Server.model.domain;

public record FruitAsset(
        int assetId,
        int fruitId,
        String assetType,
        String assetPath,
        boolean defaultAsset
) {
}
