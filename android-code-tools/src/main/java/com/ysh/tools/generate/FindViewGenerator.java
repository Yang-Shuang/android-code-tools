package com.ysh.tools.generate;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;
import java.util.Set;

public class FindViewGenerator {

    public static class Result {
        public Set<String> importList;
        public String fieldsCode;
        public String findViewsStatements; // 仅 findViewById 语句组合
        public List<XmlLayoutParser.ViewInfo> viewList;

        public Result(Set<String> importList, String fieldsCode, String findViewsStatements, List<XmlLayoutParser.ViewInfo> viewList) {
            this.importList = importList;
            this.fieldsCode = fieldsCode;
            this.findViewsStatements = findViewsStatements;
            this.viewList = viewList;
        }
    }

    /**
     * 核心解耦方法：传入 xml 文件与生成类型
     */
    public static Result generate(VirtualFile xmlFile, GenerateType type) {
        XmlLayoutParser.ParseResult parseResult = XmlLayoutParser.parse(xmlFile);

        StringBuilder fieldsBuilder = new StringBuilder();
        StringBuilder statementsBuilder = new StringBuilder();

        for (XmlLayoutParser.ViewInfo view : parseResult.viewList) {
            // 1. 生成字段: private TextView create_test_tv;
            fieldsBuilder.append(String.format("    private %s %s;\n", view.getTypeName(), view.getFieldName()));

            // 2. 根据 GenerateType 差异化生成 findViewById 语句
            String statement = type.formatFindView(view.getFieldName(), view.getIdName());
            statementsBuilder.append("        ").append(statement).append("\n");
        }

        return new Result(
                parseResult.importList,
                fieldsBuilder.toString(),
                statementsBuilder.toString(),
                parseResult.viewList
        );
    }
}