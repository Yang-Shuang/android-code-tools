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

public class ClickViewsGenerateAction extends AnAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        VirtualFile selectedFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
        if (selectedFile == null) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }
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

        Module module = ModuleUtilCore.findModuleForPsiElement(targetClass);
        List<String> targetLayouts = findLayoutNamesForFile(selectedFile);

        if (targetLayouts.isEmpty()) {
            List<String> allLayouts = findAllModuleLayouts(project, module);
            if (allLayouts.isEmpty()) {
                Messages.showWarningDialog(project, "No layout XML files found in module!", "Warning");
                return;
            }
            showLayoutChooserPopup(e, project, module, editor, javaFile, allLayouts);
        } else if (targetLayouts.size() == 1) {
            processGenerate(project, module, editor, javaFile, targetLayouts.get(0));
        } else {
            showLayoutChooserPopup(e, project, module, editor, javaFile, targetLayouts);
        }
    }

    private void processGenerate(Project project, Module module, Editor editor, PsiJavaFile javaFile, String layoutName) {
        VirtualFile layoutXmlFile = findLayoutVirtualFile(project, module, layoutName + ".xml");
        if (layoutXmlFile == null) {
            Messages.showErrorDialog(project, "Layout XML file not found: " + layoutName + ".xml", "Error");
            return;
        }

        OnClickGenerator.Result result = OnClickGenerator.generate(layoutXmlFile);
        if (result.clickViewsCode.isEmpty()) {
            Messages.showWarningDialog(project, "No views found in layout " + layoutName + ".xml", "Warning");
            return;
        }

        WriteCommandAction.runWriteCommandAction(project, () -> {
            // 补充 android.view.View 导包
            PsiClass viewClass = JavaPsiFacade.getInstance(project).findClass("android.view.View", GlobalSearchScope.allScope(project));
            if (viewClass != null) {
                javaFile.importClass(viewClass);
            }

            // 解锁 Document 防止与 PSI 冲突
            PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
            psiDocumentManager.doPostponedOperationsAndUnblockDocument(editor.getDocument());

            // 光标处插入分支代码
            int offset = editor.getCaretModel().getOffset();
            editor.getDocument().insertString(offset, "\n" + result.clickViewsCode);

            psiDocumentManager.commitDocument(editor.getDocument());
        });
    }

    private void showLayoutChooserPopup(AnActionEvent event, Project project, Module module, Editor editor,
                                        PsiJavaFile javaFile, List<String> layouts) {
        ListPopup popup = JBPopupFactory.getInstance().createListPopup(
                new BaseListPopupStep<String>("Select Layout for clickViews", layouts) {
                    @Override
                    public PopupStep<?> onChosen(String selectedValue, boolean finalChoice) {
                        if (selectedValue != null) {
                            processGenerate(project, module, editor, javaFile, selectedValue);
                        }
                        return FINAL_CHOICE;
                    }
                }
        );
        popup.showInBestPositionFor(event.getDataContext());
    }

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
            result.addAll(findAllLayoutsInFile(selectedFile));
        }
        return result;
    }

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

        if (layoutName == null) {
            String className = selectedFile.getName().replace(".java", "");
            if (className.endsWith("Activity")) {
                className = className.substring(0, className.length() - 8);
            }
            layoutName = "activity" + camelToUnderline(className);
        }
        return layoutName;
    }

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