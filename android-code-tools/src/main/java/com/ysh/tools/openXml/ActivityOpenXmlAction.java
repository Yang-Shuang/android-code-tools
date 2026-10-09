package com.ysh.tools.openXml;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.vfs.VirtualFile;
import com.ysh.tools.FileUtil;
import com.ysh.tools.LogUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ActivityOpenXmlAction extends AnAction {


    @Override
    public void update(AnActionEvent e) {
        // 获取当前选中的文件
        VirtualFile selectedFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);

        if (selectedFile == null) {
            // 如果没有文件被选中，隐藏该菜单
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }

        String fileName = selectedFile.getName();
        // 只有选中 .java 或 .xml 文件时，才让这个右键菜单显示并可用
        if (fileName.endsWith(".java") || fileName.endsWith(".xml")) {
            e.getPresentation().setEnabledAndVisible(true);
        } else {
            // 其他文件类型（如 .txt, .gradle），完全隐藏该菜单
            e.getPresentation().setEnabledAndVisible(false);
        }
    }

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        try {
            this.actionPerformedCustom(anActionEvent);
        } catch (Exception e) {
            LogUtils.toast(e.getMessage());
        }
    }

    private void actionPerformedCustom(AnActionEvent event) {
        Project project = getEventProject(event);
        if (project == null) return;

        VirtualFile selectedFile = event.getData(PlatformDataKeys.VIRTUAL_FILE);
        if (selectedFile == null) return;

        String fileName = selectedFile.getName();
        // 必须是xml或者java文件
        if ((!fileName.endsWith(".xml")) && (!fileName.endsWith(".java"))) {
            LogUtils.toast("文件类型不支持:" + fileName);
            return;
        }

        if (fileName.endsWith(".xml")) {
            // xml文件必须是fragment或者activity的页面文件，其他如listitem等不可以
            if (!fileName.contains("activity") && !fileName.contains("fragment") && !fileName.contains("dialog")) {
                LogUtils.toast("不支持此文件命名:" + fileName);
                return;
            }
            // XML 跳转回 Java 的原有逻辑 (此处根据你原有逻辑提取)
            String rootpath = project.getBasePath();
            VirtualFile javaPackage = project.getBaseDir().findFileByRelativePath(FileUtil.getJavaPackagePath(selectedFile).replace(rootpath, ""));
            VirtualFile openFile = FileUtil.findFile(javaPackage, fileName, project);
            if (openFile != null) {
                FileEditorManager.getInstance(project).openFile(openFile, true);
            } else {
                LogUtils.toast("未找到对应文件");
            }

        } else if (fileName.endsWith(".java")) {
            String name = "";
            // 保留原有特定的跳转逻辑
            if (fileName.endsWith("Activity.java")) {
                name = getXmlForActivity(selectedFile);
                if (name != null) openTargetXml(project, selectedFile, name + ".xml");
            } else if (fileName.endsWith("Fragment.java")) {
                name = getXmlForFragment(selectedFile);
                if (name != null) openTargetXml(project, selectedFile, name + ".xml");
            } else {
                // =============== 新增逻辑：通用处理所有 Java 文件 ===============
                List<String> layoutList = findAllLayoutsInFile(selectedFile);

                if (layoutList.isEmpty()) {
                    LogUtils.toast("当前文件未找到 R.layout 引用");
                } else if (layoutList.size() == 1) {
                    // 只有一个，直接跳转
                    openTargetXml(project, selectedFile, layoutList.get(0) + ".xml");
                } else {
                    // 有多个，展示弹窗让用户选择
                    showLayoutChooserPopup2(event, project, selectedFile, layoutList);
                }
            }
        }
    }

    /**
     * 打开目标 XML 文件
     */
    private void openTargetXml(Project project, VirtualFile selectedFile, String xmlFileName) {
        String rootpath = project.getBasePath();
        String repath = FileUtil.getResPackagePath(selectedFile).replace(rootpath, "");
        VirtualFile openFile = project.getBaseDir().findFileByRelativePath(repath + xmlFileName);

        if (openFile != null) {
            FileEditorManager.getInstance(project).openFile(openFile, true);
        } else {
            LogUtils.toast("未找到对应布局文件: " + xmlFileName);
        }
    }

    /**
     * 检索文件中所有的 R.layout.xxxx 引用
     */
    private List<String> findAllLayoutsInFile(VirtualFile selectedFile) {
        // 使用 LinkedHashSet 既能去重，又能保证添加顺序
        Set<String> layoutSet = new LinkedHashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(selectedFile.getInputStream()))) {
            String line;
            // 正则匹配 R.layout. 后面的标识符，支持大小写字母、数字和下划线
            Pattern pattern = Pattern.compile("R\\.layout\\.([a-zA-Z0-9_]+)");
            while ((line = reader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                while (matcher.find()) {
                    // group(1) 即为捕获到的布局名称 xxxx
                    layoutSet.add(matcher.group(1));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new ArrayList<>(layoutSet);
    }

    /**
     * 弹出 IDEA 风格的列表选择框
     */
    private void showLayoutChooserPopup(AnActionEvent event, Project project, VirtualFile selectedFile, List<String> layouts) {
        JBPopupFactory.getInstance()
                .createPopupChooserBuilder(layouts) // 传入数据源
                .setTitle("Select Layout to Navigate") // 弹窗标题
                .setItemChosenCallback(selectedLayoutName -> {
                    // 当用户点击某一项时的回调
                    openTargetXml(project, selectedFile, selectedLayoutName + ".xml");
                })
                // 3. 开启移动/拖拽 (鼠标可以按住标题拖动弹窗)
                .setMovable(true)
                .createPopup()
                // 让弹窗显示在最合适的位置（通常在鼠标点击位置或光标位置）
                .showInBestPositionFor(event.getDataContext());
    }

    private void showLayoutChooserPopup2(AnActionEvent event, Project project, VirtualFile selectedFile, List<String> layouts) {
        // 使用 BaseListPopupStep，样式与 Generate / Refactor 弹窗完全一致
        BaseListPopupStep<String> step = new BaseListPopupStep<String>("Select Layout to Navigate", layouts) {
            @Override
            public boolean isSpeedSearchEnabled() {
                return true; // 开启搜索框与匹配高亮
            }

            @Override
            public String getTextFor(String value) {
                return value;
            }

            public PopupStep<?> onChosen(String selectedValue, boolean finalChoice) {
                if (selectedValue != null) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        openTargetXml(project, selectedFile, selectedValue + ".xml");
                    });
                }
                return FINAL_CHOICE;
            }
        };
        ListPopup popup = JBPopupFactory.getInstance().createListPopup(step);

        popup.showInBestPositionFor(event.getDataContext());
    }


    private String getXmlForActivity(VirtualFile selectedFile) {
        String layoutName = null;
        try {
            InputStreamReader r = new InputStreamReader(selectedFile.getInputStream());
            BufferedReader reader = new BufferedReader(r);
            String line = null;
            while ((line = reader.readLine()) != null) {
                if (line.contains("setContentView(R.layout.")) {
                    layoutName = line.split("R.layout.")[1];
                    layoutName = layoutName.split("\\)\\;")[0];
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (layoutName == null) {
            String file = selectedFile.getName();
            String xmlFile = file.replace(".java", "");
            xmlFile = xmlFile.substring(0, xmlFile.length() - 8);
            Pattern pattern = Pattern.compile("[A-Z]{1}");
            Matcher matcher = pattern.matcher(xmlFile);
            layoutName = xmlFile;
            while (matcher.find()) {
                String s = matcher.group();
                layoutName = layoutName.replace(s, "_" + s.toLowerCase());
            }
            layoutName = "activity" + layoutName;
        }
        return layoutName;
    }

    private String getXmlForFragment(VirtualFile selectedFile) {
        String layoutName = null;
        try {
            InputStreamReader r = new InputStreamReader(selectedFile.getInputStream());
            BufferedReader reader = new BufferedReader(r);
            String line = null;
            while ((line = reader.readLine()) != null) {
                if (line.contains(".inflate(R.layout.")) {
                    layoutName = line.split(".inflate\\(R.layout.")[1];
                    layoutName = layoutName.split(",")[0];
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (layoutName == null) {
            String file = selectedFile.getName();
            String xmlFile = file.replace(".java", "");
            xmlFile = xmlFile.substring(0, xmlFile.length() - 8);
            Pattern pattern = Pattern.compile("[A-Z]{1}");
            Matcher matcher = pattern.matcher(xmlFile);
            layoutName = xmlFile;
            while (matcher.find()) {
                String s = matcher.group();
                layoutName = layoutName.replace(s, "_" + s.toLowerCase());
            }
            layoutName = "fragment" + layoutName;
        }
        return layoutName;
    }

}
