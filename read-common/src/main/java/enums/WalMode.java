package enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum WalMode {
    SIMPLE_SYNC("simple_sync", "Simple Sync Mode"),
    GROUP_COMMIT("group_commit", "Group Commit Mode"),
    MMAP("mmap", "Memory Mapped File Mode");

    private final String code;
    private final String desc;

    public static WalMode getByCode(String code) {
        for (WalMode mode : WalMode.values()) {
            if (mode.code.equals(code)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown WalMode code: " + code);
    }
}
