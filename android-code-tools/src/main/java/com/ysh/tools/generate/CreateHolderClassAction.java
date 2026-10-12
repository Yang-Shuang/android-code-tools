package com.ysh.tools.generate;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.ysh.tools.AuthorUtil;
import com.ysh.tools.LogUtils;
import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;

import java.text.SimpleDateFormat;
import java.util.*;

public class CreateHolderClassAction extends AnAction {

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
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        Project project = anActionEvent.getProject();
        Editor editor = anActionEvent.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = anActionEvent.getData(CommonDataKeys.PSI_FILE);

        if (project == null || editor == null || !(psiFile instanceof PsiJavaFile)) return;
        PsiJavaFile javaFile = (PsiJavaFile) psiFile;

        // 1. 定位当前光标所在的 PsiClass (支持定位到外部类)
        PsiClass targetClass = FindViewsGenerateAction.getTargetClass(psiFile, editor);
        if (targetClass == null) return;

        Module module = ModuleUtilCore.findModuleForPsiElement(targetClass);
        List<String> layoutFiles = findLayoutFiles(project, module);

        // 2. 弹出对话框收集输入信息
        CreateHolderDialog dialog = new CreateHolderDialog(project, layoutFiles);
        if (dialog.showAndGet()) {
            String holderName = dialog.getHolderName();
            String layoutName = dialog.getSelectedLayoutName();

            if (holderName == null || holderName.isEmpty()) return;

            VirtualFile layoutXmlFile = findLayoutVirtualFile(project, module, layoutName + ".xml");
            if (layoutXmlFile == null) return;

            // 3. 解析 XML 生成控件片段
            FindViewGenerator.Result gResult = FindViewGenerator.generate(layoutXmlFile, GenerateType.HOLDER);

            String author = AuthorUtil.getAuthor(project);
            String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

            // 4. 构建模板 Data Model (isInnerClass = true)
            Map<String, Object> dataModel = new HashMap<>();
            dataModel.put("holderName", holderName);
            dataModel.put("isInnerClass", true); // 标记为内部类
            dataModel.put("author", author);
            dataModel.put("date", date);

            dataModel.put("fieldsCode", gResult.fieldsCode);
            dataModel.put("initViewsCode", gResult.findViewsStatements);

            try {
                // 1. 渲染模板得到包含 import 的原始代码字符串
                String rawCode = SmartTemplateEngine.render(project, "HolderTemplate.java.ftl", dataModel);
                // 2. 从原始代码中提取 import 语句，并剥离它们得到干净的类代码
                Set<String> extractedImports = new HashSet<>();
                StringBuilder cleanCodeBuilder = new StringBuilder();

                String[] lines = rawCode.split("\\r?\\n");
                for (String line : lines) {
                    String trimmed = line.trim();
                    // 匹配形如 import android.view.View; 的行
                    if (trimmed.startsWith("import ") && trimmed.endsWith(";")) {
                        // 截取出类全路径，例如: android.view.View
                        String qualifiedName = trimmed.substring("import ".length(), trimmed.length() - 1).trim();
                        if (!qualifiedName.isEmpty()) {
                            extractedImports.add(qualifiedName);
                        }
                    } else {
                        // 保留非 import 行
                        cleanCodeBuilder.append(line).append("\n");
                    }
                }

                String cleanClassCode = cleanCodeBuilder.toString();

                WriteCommandAction.runWriteCommandAction(project, () -> {
                    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);

                    // A. 根据模板字符串生成内部类 PSI 节点并插入到外部类中
                    PsiClass dummyClass = elementFactory.createClassFromText(cleanClassCode, targetClass);
                    PsiClass[] innerClasses = dummyClass.getInnerClasses();
                    if (innerClasses.length > 0) {
                        targetClass.add(innerClasses[0]);
                    }

                    // 3. 【动态 XML 控件】加入 XML 布局解析出来的所有 View 控件导包
                    if (gResult.importList != null) {
                        extractedImports.addAll(gResult.importList);
                    }

                    extractedImports.add("android.view.View");

                    // C. 【去重与导入】遍历全类名，检查是否已存在，不存在则写入 import
                    PsiImportList importList = javaFile.getImportList();
                    if (importList != null) {
                        for (String qualifiedName : extractedImports) {
                            if (qualifiedName == null || qualifiedName.isEmpty()) continue;

                            // 检查当前文件是否已经包含该 import
                            boolean alreadyImported = false;
                            for (PsiImportStatement statement : importList.getImportStatements()) {
                                if (qualifiedName.equals(statement.getQualifiedName())) {
                                    alreadyImported = true;
                                    break;
                                }
                            }

                            if (!alreadyImported) {
                                PsiFileFactory fileFactory = PsiFileFactory.getInstance(project);
                                PsiJavaFile dummy = (PsiJavaFile) fileFactory.createFileFromText(
                                        "_import_dummy.java", JavaFileType.INSTANCE, "import " + qualifiedName + ";\n");
                                PsiImportStatement importStatement =
                                        (PsiImportStatement) dummy.getImportList().getImportStatements()[0];
                                LogUtils.log("import : " + qualifiedName);
                                importList.add(importStatement);
                            }
                        }
                    }
                });

            } catch (Exception ex) {
                ex.printStackTrace();
                LogUtils.log("Failed to insert inner Holder class: " + ex.getMessage());
            }
        }
    }

    /**
     * 查找 res/layout 下所有的 xml 文件列表
     */
    private List<String> findLayoutFiles(Project project, Module module) {
        long time = System.currentTimeMillis();
        List<String> layoutNames = new ArrayList<>();
        GlobalSearchScope scope = module != null ? GlobalSearchScope.moduleScope(module) : GlobalSearchScope.projectScope(project);

        // 获取项目中所有 .xml 文件并过滤位于 layout 目录下的文件
        FilenameIndex.getAllFilesByExt(project, "xml", scope).forEach(file -> {
            if (file.getParent() != null && "layout".equals(file.getParent().getName())) {
                layoutNames.add(file.getName());
            }
        });
        layoutNames.sort(String::compareTo);
        LogUtils.log("查找 Layout  耗时 ： " + (System.currentTimeMillis() - time) + " 毫秒");
        return layoutNames;
    }

    /**
     * 根据文件名查找对应的 Layout VirtualFile
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
