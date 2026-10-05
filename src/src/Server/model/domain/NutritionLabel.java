package Server.model.domain;

public record NutritionLabel(
        int labelId,
        String labelCode,
        String displayName,
        String description,
        boolean active
) {
}
