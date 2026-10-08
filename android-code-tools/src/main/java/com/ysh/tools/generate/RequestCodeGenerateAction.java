package com.ysh.tools.generate;


import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public class RequestCodeGenerateAction extends AnAction {

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

        try {
            // 渲染 RequestTemplate 模板代码（支持工程根目录 .code_templates/ 覆盖）
            String requestCode = SmartTemplateEngine.render(project, "RequestTemplate.java.ftl", new HashMap<>());

            WriteCommandAction.runWriteCommandAction(project, () -> {
                PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
                psiDocumentManager.doPostponedOperationsAndUnblockDocument(editor.getDocument());

                int offset = editor.getCaretModel().getOffset();
                editor.getDocument().insertString(offset, "\n" + requestCode);

                psiDocumentManager.commitDocument(editor.getDocument());
            });
        } catch (Exception ex) {
            Messages.showErrorDialog(project, "Failed to generate requestCode: " + ex.getMessage(), "Error");
        }
    }
}