package dev.njabulo.spaza.shop.common;

public enum FrequencyType {
    MONTHLY("monthly"),
    ANNUALLY("yearly");

    private final String name;

    FrequencyType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
