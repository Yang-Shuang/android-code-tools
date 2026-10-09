package com.ysh.tools.generate;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
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

            // 4. 构建模板 Data Model (isInnerClass = true)
            Map<String, Object> dataModel = new HashMap<>();
            dataModel.put("holderName", holderName);
            dataModel.put("isInnerClass", true); // 标记为内部类


            dataModel.put("fieldsCode", gResult.fieldsCode);
            dataModel.put("initViewsCode", gResult.findViewsStatements);

            try {
                // 5. 渲染内部类 Java 片段
                String innerClassCode = SmartTemplateEngine.render(project, "HolderTemplate.java.ftl", dataModel);

                // 6. 执行 WriteCommandAction：插入内部类节点 + PSI 自动去重导包
                WriteCommandAction.runWriteCommandAction(project, () -> {
                    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);

                    // A. 根据模板字符串生成内部类 PSI 节点
                    PsiClass dummyClass = elementFactory.createClassFromText(innerClassCode, targetClass);
                    PsiClass[] innerClasses = dummyClass.getInnerClasses();
                    if (innerClasses.length > 0) {
                        // 将内部类节点插入到外部类 targetClass 中
                        targetClass.add(innerClasses[0]);
                    }

                    // B. 收集所有需要保证存在的类包路径
                    Set<String> neededImports = new HashSet<>();
                    neededImports.add("android.view.View");
                    neededImports.add("com.huimai365.R");
                    neededImports.add("com.huimai365.compere.bean.ItemBaseBean");
                    neededImports.add("com.huimai365.goods.adapter.holder.BaseRecycleViewHolder");

                    if (gResult.importList != null) {
                        neededImports.addAll(gResult.importList);
                    }

                    // C. 利用 PSI 引擎进行无痛防重导包
                    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
                    GlobalSearchScope scope = GlobalSearchScope.allScope(project);
                    for (String qualifiedName : neededImports) {
                        PsiClass aClass = psiFacade.findClass(qualifiedName, scope);
                        if (aClass != null) {
                            javaFile.importClass(aClass); // IDE 自动忽略重复包、同包及 lang 包
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
