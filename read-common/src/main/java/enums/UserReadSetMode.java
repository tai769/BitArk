package enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserReadSetMode {
    SET("set", "Set Mode"),
    ROARING("roaring", "Roaring Bitmap Mode");

    private final String code;
    private final String desc;

    public static UserReadSetMode getByCode(String code) {
        for (UserReadSetMode mode : UserReadSetMode.values()) {
            if (mode.code.equals(code)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown UserReadSetMode code: " + code);
    }
}
