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
import com.intellij.psi.search.PsiShortNamesCache;
import com.ysh.tools.AuthorUtil;
import com.ysh.tools.LogUtils;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.*;

public class CreateAdapterAction extends AnAction {

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

        // 2. 扫描模块/项目中的 res/layout 目录下的 XML 文件
        Module module = ModuleUtilCore.findModuleForPsiElement(targetDirectory);
        List<PsiClass> beanClasses = searchBeanClasses(project, module);

        // 3. 弹出 UI 窗口收集输入信息
        CreateAdapterDialog dialog = new CreateAdapterDialog(project, beanClasses);
        if (dialog.showAndGet()) {
            // 用户点击了 OK
            String adapterName = dialog.getAdapterName();
            String beanName = dialog.getSelectedBeanName(); // 例如: ItemBaseBean
            String beanQualifiedName = dialog.getSelectedBeanQualifiedName(); // 例如: com.huimai365.compere.bean.ItemBaseBean

            LogUtils.log("Package: " + packageName);
            LogUtils.log("Adapter: " + adapterName);

            if (adapterName == null || adapterName.isEmpty()) {
                LogUtils.log("Adapter name cannot be empty!");
                return;
            }

            // 防重名判断：如果当前目录下已有同名 Java 文件则提示
            if (targetDirectory.findFile(adapterName + ".java") != null) {
                LogUtils.log(adapterName + ".java already exists!");
                return;
            }

            // 获取作者名
            String author = AuthorUtil.getAuthor(project);

            // 格式化当前日期 (例如 2026-10-8)
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            String date = sdf.format(new Date());

            // 5. 组装送给 FreeMarker 的 Data Model
            Map<String, Object> dataModel = new HashMap<>();
            dataModel.put("packageName", packageName);
            dataModel.put("adapterName", adapterName);
            dataModel.put("author", author);
            dataModel.put("date", date);
            dataModel.put("beanName", beanName);

            // 组装 Import 集合
            Set<String> importList = new TreeSet<>();
//            importList.add("android.view.View");
            if (beanQualifiedName != null && !beanQualifiedName.startsWith(packageName + ".")) {
                importList.add(beanQualifiedName);
            }
            dataModel.put("importList", importList);

            try {
                // 6. 渲染最终 Java 代码文本
                String finalJavaCode = SmartTemplateEngine.render(project, "AdapterTemplate.java.ftl", dataModel);

                // 7. 使用 IntelliJ 事务在线程中创建并写入文件
                WriteCommandAction.runWriteCommandAction(project, () -> {
                    PsiFileFactory fileFactory = PsiFileFactory.getInstance(project);
                    PsiFile javaFile = fileFactory.createFileFromText(
                            adapterName + ".java",
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
     * 检索当前 Module 及依赖 Module 中所有类名包含 "Bean" 的 Java 类
     */
    public List<PsiClass> searchBeanClasses(Project project, Module module) {
        List<PsiClass> result = new ArrayList<>();
        if (project == null || module == null) return result;

        // 1. 限定搜索范围：仅当前 Module + 依赖 Module 的【源码】（排除第三方 jar 包）
        GlobalSearchScope scope = module.getModuleWithDependenciesScope();

        // 2. 从 IDE 索引缓存中获取所有类名
        PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);
        String[] allClassNames = cache.getAllClassNames();

        // 3. 筛选类名包含 "Bean" (忽略大小写) 的类
        for (String className : allClassNames) {
            if (className.toLowerCase().contains("bean")) {
                PsiClass[] classes = cache.getClassesByName(className, scope);
                for (PsiClass psiClass : classes) {
                    // 过滤掉接口、枚举、抽象类，只保留普通的实体类
                    if (isValidBeanClass(psiClass)) {
                        result.add(psiClass);
                    }
                }
            }
        }

        // 4. 按类名字母顺序排序
        result.sort(Comparator.comparing(PsiClass::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static boolean isValidBeanClass(PsiClass psiClass) {
        if (psiClass == null) return false;
        return !psiClass.isInterface()
                && !psiClass.isEnum();
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
