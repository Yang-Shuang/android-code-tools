package com.ysh.tools.generate;


import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ShowDefaultTemplatesGroup extends ActionGroup {

    @Override
    public AnAction @NotNull [] getChildren(AnActionEvent e) {
        // 1. 动态扫描 /templates 目录下的所有文件
        List<String> templateNames = scanDefaultTemplates();

        // 2. 为每个扫描到的模板动态创建菜单 Action
        List<AnAction> actions = new ArrayList<>();
        for (String templateName : templateNames) {
            actions.add(new ShowTemplateAction(templateName));
        }
        return actions.toArray(new AnAction[0]);
    }

    /**
     * 自动遍历 Classpath 下 /templates 目录中的所有文件
     */
    private List<String> scanDefaultTemplates() {
        // 使用 TreeSet 自动按文件名正序排列（如 A->Z）
        Set<String> filenames = new TreeSet<>();
        try {
            URL url = getClass().getResource("/templates");
            if (url != null) {
                String protocol = url.getProtocol();

                // 情况 1：本地开发调试环境（templates 是一个磁盘目录 file:）
                if ("file".equals(protocol)) {
                    File dir = new File(url.toURI());
                    File[] files = dir.listFiles();
                    if (files != null) {
                        for (File file : files) {
                            if (file.isFile()) {
                                filenames.add(file.getName());
                            }
                        }
                    }
                }
                // 情况 2：正式打包发布环境（templates 存在于 JAR 包内部 jar:）
                else if ("jar".equals(protocol)) {
                    JarURLConnection jarURLConnection = (JarURLConnection) url.openConnection();
                    JarFile jarFile = jarURLConnection.getJarFile();
                    Enumeration<JarEntry> entries = jarFile.entries();

                    while (entries.hasMoreElements()) {
                        String name = entries.nextElement().getName();
                        // 匹配 templates/ 目录下的所有文件（排除子文件夹）
                        if (name.startsWith("templates/") && !name.equals("templates/")) {
                            String fileName = name.substring("templates/".length());
                            if (!fileName.contains("/") && !fileName.isEmpty()) {
                                filenames.add(fileName);
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return new ArrayList<>(filenames);
    }
}