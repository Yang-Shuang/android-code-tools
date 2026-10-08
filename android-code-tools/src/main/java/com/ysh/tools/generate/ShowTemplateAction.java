package com.ysh.tools.generate;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ShowTemplateAction extends AnAction {

    private final String templateName;

    public ShowTemplateAction(String templateName) {
        super(templateName); // 菜单项显示的文本（例如 ActivityTemplate.java.ftl）
        this.templateName = templateName;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        try {
            // 1. 从 JAR 包 /templates/ 目录下读取默认模板文本
            InputStream inputStream = getClass().getResourceAsStream("/templates/" + templateName);
            if (inputStream == null) {
                Messages.showErrorDialog(project, "Template not found in plugin jar: " + templateName, "Error");
                return;
            }

            String templateContent = StreamUtil.readText(inputStream, StandardCharsets.UTF_8.name());

            // 2. 创建内存中的只读虚拟文件 (LightVirtualFile)
            LightVirtualFile lightVirtualFile = new LightVirtualFile(
                    "[Default] " + templateName,
                    templateContent
            );
            // 设置为只读模式，方便用户参考和复制
            lightVirtualFile.setWritable(false);

            // 3. 在 IDE 的编辑器 Tab 中直接打开该文件
            FileEditorManager.getInstance(project).openFile(lightVirtualFile, true);

        } catch (Exception ex) {
            ex.printStackTrace();
            Messages.showErrorDialog(project, "Failed to read default template: " + ex.getMessage(), "Error");
        }
    }
}