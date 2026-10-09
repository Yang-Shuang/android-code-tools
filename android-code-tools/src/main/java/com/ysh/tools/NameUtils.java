package com.ysh.tools;

import com.intellij.openapi.util.text.StringUtil;

public class NameUtils {

    /**
     * 将 layout 文件名转换为 Activity 类名
     * 规则：activity_create_test.xml -> CreateTestActivity
     */
    public static String layoutToActivityName(String layoutFileName) {
        if (StringUtil.isEmpty(layoutFileName)) {
            return "";
        }

        // 1. 去除 .xml 后缀
        String name = layoutFileName.replace(".xml", "");

        // 2. 去除 activity_ 前缀（如果有）
        if (name.startsWith("activity_")) {
            name = name.substring("activity_".length());
        }

        // 3. 按下划线分割并转为大驼峰 (create_test -> CreateTest)
        StringBuilder camelName = new StringBuilder();
        String[] parts = name.split("_");
        for (String part : parts) {
            if (!part.isEmpty()) {
                camelName.append(StringUtil.capitalize(part.toLowerCase()));
            }
        }

        // 4. 拼接 Activity 后缀
        camelName.append("Activity");
        return camelName.toString();
    }

    /**
     * 将 layout 文件名转换为 Fragment 类名
     * 规则：fragment_create_test.xml -> CreateTestFragment
     */
    public static String layoutToFragmentName(String layoutFileName) {
        if (StringUtil.isEmpty(layoutFileName)) {
            return "";
        }

        // 1. 去除 .xml 后缀
        String name = layoutFileName.replace(".xml", "");

        // 2. 去除 fragment_ 前缀（如果有）
        if (name.startsWith("fragment_")) {
            name = name.substring("fragment_".length());
        }

        // 3. 按下划线分割并转为大驼峰 (create_test -> CreateTest)
        StringBuilder camelName = new StringBuilder();
        String[] parts = name.split("_");
        for (String part : parts) {
            if (!part.isEmpty()) {
                camelName.append(StringUtil.capitalize(part.toLowerCase()));
            }
        }

        // 4. 拼接 Fragment 后缀
        camelName.append("Fragment");
        return camelName.toString();
    }

    /**
     * 将 layout 文件名转换为 Holder 类名
     * 规则：item_create_test.xml -> CreateTestItemHolder
     */
    public static String layoutToHolderName(String layoutFileName) {
        if (StringUtil.isEmpty(layoutFileName)) {
            return "";
        }

        //  去除 .xml 后缀
        String name = layoutFileName.replace(".xml", "");

        if (name.startsWith("item_")) {
            name = name.substring("item_".length()) + "_item";
        }

        // 3. 按下划线分割并转为大驼峰 (create_test -> CreateTest)
        StringBuilder camelName = new StringBuilder();
        String[] parts = name.split("_");
        for (String part : parts) {
            if (!part.isEmpty()) {
                camelName.append(StringUtil.capitalize(part.toLowerCase()));
            }
        }

        // 4. 拼接 Holder 后缀
        camelName.append("Holder");
        return camelName.toString();
    }
}