package dev.zanex.friendsystem.friends;

import lombok.Getter;

import java.util.UUID;

public class Relation {
    @Getter
    private final UUID uuidA;
    @Getter
    private final UUID uuidB;
    @Getter
    private final RelationType relationType;

    public Relation(UUID a, UUID b, RelationType relationType) {
        this.uuidA = a;
        this.uuidB = b;
        this.relationType = relationType;
    }

    public static RelationType ofType(int i) {
        for (RelationType type : RelationType.values()) {
            if (type.ordinal() == i) return type;
        }

        throw new IllegalArgumentException("Invalid relation type: " + i);
    }

    public enum RelationType {
        FRIEND(0),
        BLOCKED(1),
        PENDING(2);

        @Getter
        private final int id;
        RelationType(int id) {
            this.id = id;
        }
    }
}
