package com.ysh.tools.generate;

public enum GenerateType {
    ACTIVITY("Activity", "this.%s = findViewById(R.id.%s);"),
    DIALOG("Dialog", "this.%s = findViewById(R.id.%s);"),
    FRAGMENT("Fragment", "this.%s = view.findViewById(R.id.%s);"),
    HOLDER("Holder", "this.%s = itemView.findViewById(R.id.%s);");

    private final String displayName;
    private final String pattern;

    GenerateType(String displayName, String pattern) {
        this.displayName = displayName;
        this.pattern = pattern;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 根据不同的类型生成特定的 findViewById 代码行
     */
    public String formatFindView(String fieldName, String idName) {
        return String.format(pattern, fieldName, idName);
    }
}