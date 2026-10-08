package com.ysh.tools.generate;


import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FindViewsGenerateAction extends AnAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        // 获取当前选中的文件
        VirtualFile selectedFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);

        if (selectedFile == null) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }

        // 只有选中 .java 文件时，才让 Generate 菜单中的 findViews 显示并可用
        boolean isJava = selectedFile.getName().endsWith(".java");
        e.getPresentation().setEnabledAndVisible(isJava);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        if (project == null || editor == null || !(psiFile instanceof PsiJavaFile)) return;

        VirtualFile selectedFile = psiFile.getVirtualFile();
        if (selectedFile == null) return;

        PsiJavaFile javaFile = (PsiJavaFile) psiFile;
        PsiClass targetClass = PsiTreeUtil.findChildOfType(javaFile, PsiClass.class);
        if (targetClass == null) return;

        // 1. 根据文件名/继承关系确定 GenerateType
        GenerateType generateType = detectGenerateType(targetClass, selectedFile.getName());

        // 2. 根据文件匹配规则，寻找布局文件名
        Module module = ModuleUtilCore.findModuleForPsiElement(targetClass);
        List<String> targetLayouts = findLayoutNamesForFile(selectedFile);

        if (targetLayouts.isEmpty()) {
            // 如果代码中未找到 R.layout 引用，展示全模块所有 layout 列表供用户选择
            List<String> allLayouts = findAllModuleLayouts(project, module);
            if (allLayouts.isEmpty()) {
                Messages.showWarningDialog(project, "No layout XML files found in module!", "Warning");
                return;
            }
            showLayoutChooserPopup(e, project, module, targetClass, editor, javaFile, generateType, allLayouts);
        } else if (targetLayouts.size() == 1) {
            // 只有 1 个布局文件，直接执行生成
            processGenerate(project, module, targetClass, editor, javaFile, generateType, targetLayouts.get(0));
        } else {
            // 有多个布局文件引用，弹出列表让用户选择
            showLayoutChooserPopup(e, project, module, targetClass, editor, javaFile, generateType, targetLayouts);
        }
    }

    /**
     * 核心生成逻辑：解析 XML 并插入代码到当前编辑器
     */
    /**
     * 核心生成逻辑：解析 XML 并插入代码到当前编辑器
     */
    private void processGenerate(Project project, Module module, PsiClass targetClass, Editor editor,
                                 PsiJavaFile javaFile, GenerateType generateType, String layoutName) {
        VirtualFile layoutXmlFile = findLayoutVirtualFile(project, module, layoutName + ".xml");
        if (layoutXmlFile == null) {
            Messages.showErrorDialog(project, "Layout XML file not found: " + layoutName + ".xml", "Error");
            return;
        }

        // 调用生成器获取代码片段
        FindViewGenerator.Result result = FindViewGenerator.generate(layoutXmlFile, generateType);

        WriteCommandAction.runWriteCommandAction(project, () -> {
            PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);

            // 1. 补全 View 成员变量声明（PSI 操作）
            for (XmlLayoutParser.ViewInfo view : result.viewList) {
                if (targetClass.findFieldByName(view.getFieldName(), false) == null) {
                    String fieldText = String.format("private %s %s;", view.getTypeName(), view.getFieldName());
                    PsiField field = elementFactory.createFieldFromText(fieldText, targetClass);
                    targetClass.add(field);
                }
            }

            // 2. 自动导入缺失的 View 包类（PSI 操作）
            for (String importClass : result.importList) {
                PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(importClass, GlobalSearchScope.allScope(project));
                if (aClass != null) {
                    javaFile.importClass(aClass);
                }
            }

            // 【核心修复】：提交挂起的 PSI 操作并解锁 Document，防止与后续的 Document 修改冲突
            PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
            psiDocumentManager.doPostponedOperationsAndUnblockDocument(editor.getDocument());

            // 3. 在当前编辑器光标位置插入 findViewById 语句（Document 操作）
            int offset = editor.getCaretModel().getOffset();
            editor.getDocument().insertString(offset, "\n" + result.findViewsStatements);

            // 提交 Document 更改，使 PSI 保持同步
            psiDocumentManager.commitDocument(editor.getDocument());
        });
    }

    /**
     * 弹出 IDE 风格列表供用户选择 Layout
     */
    private void showLayoutChooserPopup(AnActionEvent event, Project project, Module module, PsiClass targetClass,
                                        Editor editor, PsiJavaFile javaFile, GenerateType generateType, List<String> layouts) {
        ListPopup popup = JBPopupFactory.getInstance().createListPopup(
                new BaseListPopupStep<String>("Select Layout for findViews", layouts) {
                    @Override
                    public PopupStep<?> onChosen(String selectedValue, boolean finalChoice) {
                        if (selectedValue != null) {
                            processGenerate(project, module, targetClass, editor, javaFile, generateType, selectedValue);
                        }
                        return FINAL_CHOICE;
                    }
                }
        );
        popup.showInBestPositionFor(event.getDataContext());
    }

    /**
     * 依据文件名和代码特征搜寻目标布局名列表
     */
    private List<String> findLayoutNamesForFile(VirtualFile selectedFile) {
        String fileName = selectedFile.getName();
        List<String> result = new ArrayList<>();

        if (fileName.endsWith("Activity.java")) {
            String layout = getXmlForActivity(selectedFile);
            if (layout != null) result.add(layout);
        } else if (fileName.endsWith("Fragment.java")) {
            String layout = getXmlForFragment(selectedFile);
            if (layout != null) result.add(layout);
        } else {
            // 通用正则搜寻文件中所有 R.layout.xxx
            result.addAll(findAllLayoutsInFile(selectedFile));
        }

        return result;
    }

    /**
     * 解析 Activity 中的 setContentView(R.layout.xxx) 或根据类名推导
     */
    private String getXmlForActivity(VirtualFile selectedFile) {
        String layoutName = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(selectedFile.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("setContentView(R.layout.")) {
                    layoutName = line.split("R.layout.")[1].split("\\);")[0].split(",")[0].trim();
                    break;
                }
            }
        } catch (IOException ignored) {}

        // 如果代码里没写，按大写字母分割 CamelCase -> 下划线 拼出 activity_xxx
        if (layoutName == null) {
            String className = selectedFile.getName().replace(".java", "");
            if (className.endsWith("Activity")) {
                className = className.substring(0, className.length() - 8);
            }
            layoutName = "activity" + camelToUnderline(className);
        }
        return layoutName;
    }

    /**
     * 解析 Fragment 中的 R.layout.xxx 或根据类名推导
     */
    private String getXmlForFragment(VirtualFile selectedFile) {
        String layoutName = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(selectedFile.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(".inflate(R.layout.")) {
                    layoutName = line.split(".inflate\\(R.layout.")[1].split(",")[0].trim();
                    break;
                }
            }
        } catch (IOException ignored) {}

        if (layoutName == null) {
            String className = selectedFile.getName().replace(".java", "");
            if (className.endsWith("Fragment")) {
                className = className.substring(0, className.length() - 8);
            }
            layoutName = "fragment" + camelToUnderline(className);
        }
        return layoutName;
    }

    /**
     * 正则搜寻文件中引用的所有 R.layout.xxx
     */
    private List<String> findAllLayoutsInFile(VirtualFile selectedFile) {
        Set<String> layoutSet = new LinkedHashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(selectedFile.getInputStream()))) {
            String line;
            Pattern pattern = Pattern.compile("R\\.layout\\.([a-zA-Z0-9_]+)");
            while ((line = reader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                while (matcher.find()) {
                    layoutSet.add(matcher.group(1));
                }
            }
        } catch (IOException ignored) {}
        return new ArrayList<>(layoutSet);
    }

    /**
     * 自动转换大驼峰到下划线 (例如 CreateTest -> _create_test)
     */
    private String camelToUnderline(String param) {
        if (param == null || "".equals(param.trim())) {
            return "";
        }
        Pattern pattern = Pattern.compile("[A-Z]");
        Matcher matcher = pattern.matcher(param);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, "_" + matcher.group(0).toLowerCase());
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 自动判断生成类型 (Activity, Fragment, Dialog, Holder)
     */
    private GenerateType detectGenerateType(PsiClass psiClass, String fileName) {
        if (fileName.endsWith("Activity.java")) return GenerateType.ACTIVITY;
        if (fileName.endsWith("Fragment.java")) return GenerateType.FRAGMENT;
        if (fileName.endsWith("Dialog.java")) return GenerateType.DIALOG;
        if (fileName.endsWith("Holder.java") || fileName.endsWith("ViewHolder.java") || fileName.endsWith("Adapter.java")) {
            return GenerateType.HOLDER;
        }

        // 继承关系推导
        for (PsiClass superClass : psiClass.getSupers()) {
            String superName = superClass.getQualifiedName() != null ? superClass.getQualifiedName() : "";
            if (superName.contains("Activity")) return GenerateType.ACTIVITY;
            if (superName.contains("Fragment")) return GenerateType.FRAGMENT;
            if (superName.contains("Dialog")) return GenerateType.DIALOG;
            if (superName.contains("ViewHolder") || superName.contains("Holder")) return GenerateType.HOLDER;
        }

        return GenerateType.ACTIVITY;
    }

    /**
     * 查找模块内所有 Layout XML
     */
    private List<String> findAllModuleLayouts(Project project, Module module) {
        List<String> layoutNames = new ArrayList<>();
        GlobalSearchScope scope = module != null ? GlobalSearchScope.moduleScope(module) : GlobalSearchScope.projectScope(project);
        FilenameIndex.getAllFilesByExt(project, "xml", scope).forEach(file -> {
            if (file.getParent() != null && "layout".equals(file.getParent().getName())) {
                layoutNames.add(file.getName().replace(".xml", ""));
            }
        });
        layoutNames.sort(String::compareTo);
        return layoutNames;
    }

    /**
     * 根据布局名称寻找 VirtualFile 句柄
     */
    private VirtualFile findLayoutVirtualFile(Project project, Module module, String fileName) {
        GlobalSearchScope scope = module != null ? GlobalSearchScope.moduleScope(module) : GlobalSearchScope.projectScope(project);
        for (VirtualFile file : FilenameIndex.getVirtualFilesByName(fileName, scope)) {
            if (file.getParent() != null && "layout".equals(file.getParent().getName())) {
                return file;
            }
        }
        return null;
    }
}