package Server.model.domain;

public record FruitGroup(
        int groupId,
        String groupCode,
        String groupName,
        String description,
        boolean active
) {
}
