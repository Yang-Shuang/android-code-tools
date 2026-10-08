package com.ysh.tools.generate;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.text.StringUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.*;

public class XmlLayoutParser {

    public static class ViewInfo {
        private String idName;     // 例如 create_test_btn
        private String fieldName;  // 例如 btnCreateTest 或 createTestBtn
        private String typeName;   // 例如 Button
        private String fullPackage;// 例如 android.widget.Button

        public ViewInfo(String idName, String fieldName, String typeName, String fullPackage) {
            this.idName = idName;
            this.fieldName = fieldName;
            this.typeName = typeName;
            this.fullPackage = fullPackage;
        }

        // --- 关键修复：添加 FreeMarker 所必需的 Getter 方法 ---

        public String getIdName() {
            return idName;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getTypeName() {
            return typeName;
        }

        public String getFullPackage() {
            return fullPackage;
        }
    }

    public static class ParseResult {
        public Set<String> importList = new TreeSet<>();
        public String fieldsCode;
        public String initViewsCode;
        public List<ViewInfo> viewList = new ArrayList<>();
    }

    public static ParseResult parse(VirtualFile xmlFile) {
        ParseResult result = new ParseResult();
        if (xmlFile == null) {
            result.fieldsCode = "";
            result.initViewsCode = "    private void initViews() {\n        // findView\n    }";
            return result;
        }

        try (InputStream is = xmlFile.getInputStream()) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(is);
            doc.getDocumentElement().normalize();

            // 递归遍历 XML DOM 节点提取所有的 id
            List<ViewInfo> views = new ArrayList<>();
            traverseNodes(doc.getDocumentElement(), views);
            result.viewList = views;

            // 构建字段定义与 initViews() 逻辑
            StringBuilder fieldsBuilder = new StringBuilder();
            StringBuilder initViewsBuilder = new StringBuilder();

            initViewsBuilder.append("    private void initViews() {\n");

            for (ViewInfo view : views) {
                // 收集 import
                if (StringUtil.isNotEmpty(view.fullPackage)) {
                    result.importList.add(view.fullPackage);
                }

                // 1. 生成字段声明: private Button btnCreateTest;
                fieldsBuilder.append(String.format("    private %s %s;\n", view.typeName, view.fieldName));

                // 2. 生成 findViewById 语句: this.btnCreateTest = findViewById(R.id.create_test_btn);
                initViewsBuilder.append(String.format("        this.%s = findViewById(R.id.%s);\n", view.fieldName, view.idName));
            }

            initViewsBuilder.append("    }");

            result.fieldsCode = fieldsBuilder.toString();
            result.initViewsCode = initViewsBuilder.toString();

        } catch (Exception e) {
            e.printStackTrace();
            result.fieldsCode = "";
            result.initViewsCode = "    private void initViews() {\n        // findView\n    }";
        }

        return result;
    }

    private static void traverseNodes(Node node, List<ViewInfo> views) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element element = (Element) node;
            String idAttr = element.getAttribute("android:id");

            if (StringUtil.isNotEmpty(idAttr)) {
                // 提取 id (例如从 "@+id/create_test_tv" 提取出 "create_test_tv")
                String idName = idAttr.replace("@+id/", "").replace("@id/", "");
                String tagName = element.getTagName();

                String typeName;
                String fullPackage;

                if (tagName.contains(".")) {
                    typeName = tagName.substring(tagName.lastIndexOf(".") + 1);
                    fullPackage = tagName;
                } else if ("View".equals(tagName)) {
                    typeName = "View";
                    fullPackage = "android.view.View";
                } else {
                    typeName = tagName;
                    fullPackage = "android.widget." + tagName;
                }

                // 【关键修改点】：不要转换驼峰，直接保持原始下划线名称！
                // 例如 idName 为 "create_test_tv"，变量名也保持 "create_test_tv"
                String fieldName = idName;

                views.add(new ViewInfo(idName, fieldName, typeName, fullPackage));
            }

            NodeList children = node.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                traverseNodes(children.item(i), views);
            }
        }
    }

    private static String idToCamelCase(String idName) {
        String[] parts = idName.split("_");
        StringBuilder camel = new StringBuilder(parts[0].toLowerCase());
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                camel.append(StringUtil.capitalize(parts[i].toLowerCase()));
            }
        }
        return camel.toString();
    }
}