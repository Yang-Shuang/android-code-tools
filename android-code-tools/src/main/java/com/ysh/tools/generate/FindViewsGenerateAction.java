package com.ysh.tools.generate;


import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
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

    public static class MethodLayoutResult {
        public String layoutName;
        public String viewPrefix;
        public PsiMethod psiMethod;

        public MethodLayoutResult(String layoutName, String viewPrefix, PsiMethod psiMethod) {
            this.layoutName = layoutName;
            this.viewPrefix = viewPrefix;
            this.psiMethod = psiMethod;
        }
    }

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

        // 1. 精准获取当前光标所在的 PsiClass（支持外部类、ViewHolder 内部类等）
        PsiClass targetClass = getTargetClass(psiFile, editor);
        if (targetClass == null) return;

        Module module = ModuleUtilCore.findModuleForPsiElement(targetClass);

        // 2. 最高优先级：检查光标所在的方法内部是否有 R.layout.xxx 引用
        MethodLayoutResult methodLayout = detectMethodLayout(psiFile, editor);

        GenerateType generateType;
        List<String> targetLayouts = new ArrayList<>();
        String viewPrefix = "view.";

        if (methodLayout != null) {
            // 匹配到方法内部布局，直接切换为最高优先级的 METHOD 局部模式
            generateType = GenerateType.METHOD;
            targetLayouts.add(methodLayout.layoutName);
            viewPrefix = methodLayout.viewPrefix;
        } else {
            // 常规判定逻辑
            generateType = detectGenerateType(targetClass, selectedFile.getName());
            targetLayouts = findLayoutNamesForFile(selectedFile);
        }

        if (targetLayouts.isEmpty()) {
            // 如果代码中未找到 R.layout 引用，展示全模块所有 layout 列表供用户选择
            List<String> allLayouts = findAllModuleLayouts(project, module);
            if (allLayouts.isEmpty()) {
                Messages.showWarningDialog(project, "No layout XML files found in module!", "Warning");
                return;
            }
            showLayoutChooserPopup(e, project, module, targetClass, editor, javaFile, generateType, viewPrefix, allLayouts);
        } else if (targetLayouts.size() == 1) {
            // 只有 1 个布局文件，直接执行生成
            processGenerate(project, module, targetClass, editor, javaFile, generateType, viewPrefix, targetLayouts.get(0));
        } else {
            // 有多个布局文件引用，弹出列表让用户选择
            showLayoutChooserPopup(e, project, module, targetClass, editor, javaFile, generateType, viewPrefix, targetLayouts);
        }
    }

    /**
     * 核心生成逻辑：解析 XML 并插入代码到当前编辑器
     */
    private void processGenerate(Project project, Module module, PsiClass targetClass, Editor editor,
                                 PsiJavaFile javaFile, GenerateType generateType, String viewPrefix, String layoutName) {
        VirtualFile layoutXmlFile = findLayoutVirtualFile(project, module, layoutName + ".xml");
        if (layoutXmlFile == null) {
            Messages.showErrorDialog(project, "Layout XML file not found: " + layoutName + ".xml", "Error");
            return;
        }

        // 调用生成器获取代码片段
        FindViewGenerator.Result result = FindViewGenerator.generate(layoutXmlFile, generateType, viewPrefix);

        WriteCommandAction.runWriteCommandAction(project, () -> {
            PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);

            // 1. 补全 View 成员变量声明（METHOD 模式自动跳过补全全局变量）
            if (generateType != GenerateType.METHOD) {
                for (XmlLayoutParser.ViewInfo view : result.viewList) {
                    if (targetClass.findFieldByName(view.getFieldName(), false) == null) {
                        String fieldText = String.format("private %s %s;", view.getTypeName(), view.getFieldName());
                        PsiField field = elementFactory.createFieldFromText(fieldText, targetClass);
                        targetClass.add(field);
                    }
                }
            }

            // 2. 自动导入缺失的 View 包类（PSI 操作）
            for (String importClass : result.importList) {
                PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(importClass, GlobalSearchScope.allScope(project));
                if (aClass != null) {
                    javaFile.importClass(aClass);
                }
            }

            // 提交挂起的 PSI 操作并解锁 Document
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
     * 检测光标是否在方法体内部，并解析是否存在 R.layout.xxx
     */
    private MethodLayoutResult detectMethodLayout(PsiFile psiFile, Editor editor) {
        int offset = editor.getCaretModel().getOffset();
        PsiElement element = psiFile.findElementAt(offset);
        if (element == null) return null;

        PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
        if (method == null || method.getBody() == null) return null;

        Collection<PsiReferenceExpression> references = PsiTreeUtil.collectElementsOfType(
                method.getBody(), PsiReferenceExpression.class);

        for (PsiReferenceExpression ref : references) {
            String text = ref.getText();
            if (text.startsWith("R.layout.")) {
                String layoutName = text.substring("R.layout.".length());
                String viewPrefix = inferViewPrefix(ref, method);
                return new MethodLayoutResult(layoutName, viewPrefix, method);
            }
        }
        return null;
    }

    /**
     * 自动推断方法内部 view 的前缀 (例如 view. / itemView. / "")
     */
    private String inferViewPrefix(PsiReferenceExpression rLayoutRef, PsiMethod method) {
        // 查找 inflate 赋值变量声明: View view = inflater.inflate(...)
        PsiDeclarationStatement declStmt = PsiTreeUtil.getParentOfType(rLayoutRef, PsiDeclarationStatement.class);
        if (declStmt != null && declStmt.getDeclaredElements().length > 0) {
            PsiElement declared = declStmt.getDeclaredElements()[0];
            if (declared instanceof PsiVariable) {
                String varName = ((PsiVariable) declared).getName();
                if (varName != null && !varName.isEmpty()) {
                    return varName + ".";
                }
            }
        }

        // 查找方法形参中的 View 对象名
        for (PsiParameter parameter : method.getParameterList().getParameters()) {
            if (parameter.getType().getPresentableText().contains("View")) {
                return parameter.getName() + ".";
            }
        }

        return "view.";
    }

    /**
     * 获取当前光标所在的 PsiClass（精准支持外部类、静态/非静态内部类、ViewHolder 等）
     */
    public static PsiClass getTargetClass(PsiFile psiFile, Editor editor) {
        if (psiFile == null) return null;

        if (editor != null) {
            int offset = editor.getCaretModel().getOffset();
            PsiElement element = psiFile.findElementAt(offset);
            if (element != null) {
                PsiClass targetClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
                if (targetClass != null) {
                    return targetClass;
                }
            }
        }

        if (psiFile instanceof PsiJavaFile) {
            PsiJavaFile javaFile = (PsiJavaFile) psiFile;
            PsiClass[] classes = javaFile.getClasses();
            if (classes.length > 0) {
                return classes[0];
            }
        }

        return null;
    }

    /**
     * 弹出 IDE 风格列表供用户选择 Layout
     */
    private void showLayoutChooserPopup(AnActionEvent event, Project project, Module module, PsiClass targetClass,
                                        Editor editor, PsiJavaFile javaFile, GenerateType generateType,
                                        String viewPrefix, List<String> layouts) {

        BaseListPopupStep<String> step = new BaseListPopupStep<String>("Select Layout XML", layouts) {

            // ==================== 问题 2：开启 SpeedSearch 即打即搜 ====================
            @Override
            public boolean isSpeedSearchEnabled() {
                return true; // 开启搜索框与匹配高亮
            }

            @Override
            public String getTextFor(String value) {
                return value; // 指定搜索时匹配的字符串（这里即布局名称，如 activity_main）
            }

            // ==================== 问题 1：修复鼠标点击报错/失效 ====================
            @Override
            public PopupStep<?> onChosen(String selectedValue, boolean finalChoice) {
                if (finalChoice && selectedValue != null) {
                    // 延迟到 EDT 下一个循环队列，等待 Popup 窗口彻底销毁后再修改代码
                    ApplicationManager.getApplication().invokeLater(() -> {
                        processGenerate(project, module, targetClass, editor, javaFile, generateType, viewPrefix, selectedValue);
                    });
                }
                return FINAL_CHOICE;
            }
        };

        // 创建并展示 Popup 弹窗
        ListPopup popup = JBPopupFactory.getInstance().createListPopup(step);

        // 优先展示在编辑器光标附近，体验与 Alt+Insert 一致
        if (editor != null) {
            popup.showInBestPositionFor(editor);
        } else {
            popup.showCenteredInCurrentWindow(project);
        }
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
        } catch (IOException ignored) {
        }

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
        } catch (IOException ignored) {
        }

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
        } catch (IOException ignored) {
        }
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