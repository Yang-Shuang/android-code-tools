package com.ysh.tools.generate;


import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import freemarker.template.Configuration;
import freemarker.template.Template;

import java.io.File;
import java.io.StringWriter;
import java.util.Map;

public class SmartTemplateEngine {

    // 全局个人主目录（如 C:\Users\zhangsan\.code_templates）
    private static final String USER_GLOBAL_TEMPLATE_DIR = System.getProperty("user.home") + File.separator + ".code_templates";

    public static String render(Project project, String templateName, Map<String, Object> dataModel) throws Exception {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setDefaultEncoding("UTF-8");

        boolean templateLoaded = false;

        // 【优先级 1】：检查当前工程根目录下的 .code_templates/ 文件夹
        if (project != null && project.getBasePath() != null) {
            File projectTemplateDir = new File(project.getBasePath(), ".code_templates");
            File projectTemplateFile = new File(projectTemplateDir, templateName);
            if (projectTemplateFile.exists()) {
                cfg.setDirectoryForTemplateLoading(projectTemplateDir);
                templateLoaded = true;
            }
        }

        // 【优先级 2】：如果项目根目录没有，检查用户个人主目录 ~/.code_templates/
        if (!templateLoaded) {
            File globalTemplateDir = new File(USER_GLOBAL_TEMPLATE_DIR);
            File globalTemplateFile = new File(globalTemplateDir, templateName);
            if (globalTemplateFile.exists()) {
                cfg.setDirectoryForTemplateLoading(globalTemplateDir);
                templateLoaded = true;
            }
        }

        // 【优先级 3】：兜底，加载插件 JAR 包内置默认模板 /templates/
        if (!templateLoaded) {
            cfg.setClassForTemplateLoading(SmartTemplateEngine.class, "/templates");
        }

        Template template = cfg.getTemplate(templateName);
        StringWriter out = new StringWriter();
        template.process(dataModel, out);

        // 统一格式化换行符，防止 CRLF/LF 导致的 IDE 报错
        return StringUtil.convertLineSeparators(out.toString());
    }
}