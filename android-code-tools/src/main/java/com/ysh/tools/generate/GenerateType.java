package com.ysh.tools.generate;

public enum GenerateType {
    ACTIVITY("Activity", "this.%s = findViewById(R.id.%s);"),
    DIALOG("Dialog", "this.%s = findViewById(R.id.%s);"),
    FRAGMENT("Fragment", "this.%s = view.findViewById(R.id.%s);"),
    HOLDER("Holder", "this.%s = itemView.findViewById(R.id.%s);"),
    METHOD("Method", "%s %s = %sfindViewById(R.id.%s);"); // 局部变量模式: TextView tv = view.findViewById(R.id.tv);

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
    public String formatFindView(String typeName, String fieldName, String idName, String viewPrefix) {
        if (this == METHOD) {
            String prefix = (viewPrefix != null && !viewPrefix.isEmpty()) ? viewPrefix : "view.";
            // 拼接格式: TextView tvName = view.findViewById(R.id.tv_name);
            return String.format(pattern, typeName, fieldName, prefix, idName);
        }
        return String.format(pattern, fieldName, idName);
    }

    /**
     * 兼容旧接口的重载方法
     */
    public String formatFindView(String fieldName, String idName) {
        return formatFindView("", fieldName, idName, "view.");
    }
}