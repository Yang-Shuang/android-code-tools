package com.ysh.tools.generate;


import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

public class OnClickGenerator {

    public static class Result {
        public String clickViewsCode;
        public List<XmlLayoutParser.ViewInfo> viewList;

        public Result(String clickViewsCode, List<XmlLayoutParser.ViewInfo> viewList) {
            this.clickViewsCode = clickViewsCode;
            this.viewList = viewList;
        }
    }

    /**
     * 核心解耦方法：传入 xml 句柄，返回拼装好的 onClick 分支代码片段
     */
    public static Result generate(VirtualFile xmlFile) {
        XmlLayoutParser.ParseResult parseResult = XmlLayoutParser.parse(xmlFile);
        List<XmlLayoutParser.ViewInfo> views = parseResult.viewList;

        if (views == null || views.isEmpty()) {
            return new Result("", views);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("        int viewId = v.getId();\n");

        for (int i = 0; i < views.size(); i++) {
            XmlLayoutParser.ViewInfo view = views.get(i);
            if (i == 0) {
                sb.append(String.format("        if (viewId == R.id.%s) {\n", view.getIdName()));
            } else {
                sb.append(String.format("        } else if (viewId == R.id.%s) {\n", view.getIdName()));
            }
            sb.append(String.format("            // TODO: 点击 %s 处理逻辑\n", view.getFieldName()));
        }
        sb.append("        }");

        return new Result(sb.toString(), views);
    }
}