package com.ysh.tools.generate;


import com.intellij.ide.IdeView;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.ysh.tools.AuthorUtil;
import com.ysh.tools.LogUtils;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.*;

public class CreateHolderAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        // 1. 获取右键选中的目标 PsiDirectory，并提取包名 (packageName)
        PsiElement psiElement = e.getData(CommonDataKeys.PSI_ELEMENT);
        if (!(psiElement instanceof PsiDirectory)) return;

        PsiDirectory targetDirectory = (PsiDirectory) psiElement;
        PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(targetDirectory);
        String packageName = psiPackage != null ? psiPackage.getQualifiedName() : "";

        // 2. 扫描模块中的 res/layout 目录下的 XML 文件
        Module module = ModuleUtilCore.findModuleForPsiElement(targetDirectory);
        List<String> layoutFiles = findLayoutFiles(project, module);

        // 3. 弹出 UI 窗口收集输入信息（包含 Layout 选择与 Bean 类选择）
        CreateHolderDialog dialog = new CreateHolderDialog(project, layoutFiles);
        if (dialog.showAndGet()) {
            String holderName = dialog.getHolderName();
            String layoutName = dialog.getSelectedLayoutName();

            if (holderName == null || holderName.isEmpty()) {
                LogUtils.log("Holder name cannot be empty!");
                return;
            }

            // 防重名判断
            if (targetDirectory.findFile(holderName + ".java") != null) {
                LogUtils.log(holderName + ".java already exists!");
                return;
            }

            // 4. 定位选中的 Layout XML 句柄并解析
            VirtualFile layoutXmlFile = findLayoutVirtualFile(project, module, layoutName + ".xml");
            if (layoutXmlFile == null) {
                LogUtils.log("Layout file not found: " + layoutName + ".xml");
                return;
            }

            FindViewGenerator.Result gResult = FindViewGenerator.generate(layoutXmlFile, GenerateType.HOLDER);

            String author = AuthorUtil.getAuthor(project);
            String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

            // 5. 组装数据模型
            Map<String, Object> dataModel = new HashMap<>();
            dataModel.put("packageName", packageName);
            dataModel.put("holderName", holderName);
            dataModel.put("layoutName", layoutName);
            dataModel.put("author", author);
            dataModel.put("date", date);
            dataModel.put("isInnerClass", false); // 标记为独立类

            // 组装 Import 集合（TreeSet 自动去重和按字母排序）
            Set<String> importList = new TreeSet<>();
            importList.add("android.view.View");
            importList.add("com.huimai365.R"); // 基础 R 文件路径
            importList.add("com.huimai365.compere.bean.ItemBaseBean");
            importList.add("com.huimai365.goods.adapter.holder.BaseRecycleViewHolder");

            if (gResult.importList != null) {
                importList.addAll(gResult.importList); // 加入 XML 中控件的 Package
            }
            dataModel.put("importList", importList);

            // 代码片段
            dataModel.put("fieldsCode", gResult.fieldsCode);
            dataModel.put("initViewsCode", gResult.findViewsStatements);

            try {
                // 6. 渲染最终 Java 代码文本
                String finalJavaCode = SmartTemplateEngine.render(project, "HolderTemplate.java.ftl", dataModel);

                // 7. 创建并写入文件
                WriteCommandAction.runWriteCommandAction(project, () -> {
                    PsiFileFactory fileFactory = PsiFileFactory.getInstance(project);
                    PsiFile javaFile = fileFactory.createFileFromText(
                            holderName + ".java",
                            JavaLanguage.INSTANCE,
                            finalJavaCode
                    );
                    targetDirectory.add(javaFile);
                });

            } catch (Exception ex) {
                ex.printStackTrace();
                LogUtils.log("Failed to generate file: " + ex.getMessage());
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

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }

        boolean isJavaSource = isJavaSourceDirectory(e, project);
        e.getPresentation().setEnabledAndVisible(isJavaSource);
    }

    /**
     * 判断当前右键选中的目录是否为 Java/Kotlin 包目录
     */
    private boolean isJavaSourceDirectory(@NotNull AnActionEvent e, @NotNull Project project) {
        PsiDirectory directory = getTargetDirectory(e);
        if (directory == null) {
            return false;
        }

        VirtualFile virtualFile = directory.getVirtualFile();
        ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();

        // 1. 检查是否处于项目源码路径中（排除 build 目录、工程根目录、.idea 等）
        if (!fileIndex.isInSourceContent(virtualFile)) {
            return false;
        }

        // 2. 检查是否能解析为 Java Package
        // 关键点：res/layout、assets/、resources/ 等资源目录无法解析为 Java Package，会返回 null
        PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(directory);
        return psiPackage != null;
    }

    /**
     * 获取当前选中的 PsiDirectory
     */
    private PsiDirectory getTargetDirectory(@NotNull AnActionEvent e) {
        IdeView ideView = e.getData(LangDataKeys.IDE_VIEW);
        if (ideView != null) {
            PsiDirectory[] directories = ideView.getDirectories();
            if (directories.length > 0) {
                return directories[0];
            }
        }

        PsiElement element = e.getData(CommonDataKeys.PSI_ELEMENT);
        if (element instanceof PsiDirectory) {
            return (PsiDirectory) element;
        } else if (element instanceof PsiPackage) {
            PsiDirectory[] directories = ((PsiPackage) element).getDirectories();
            if (directories.length > 0) {
                return directories[0];
            }
        }
        return null;
    }
}
