package com.ysh.tools.generate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiClass;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.List;

public class CreateAdapterDialog extends DialogWrapper {

    private final ComboBox<BeanItem> beanComboBox;
    private final JBTextField adapterNameField;

    public CreateAdapterDialog(@Nullable Project project, List<PsiClass> beanFiles) {
        super(project);

        setTitle("Create an Adapter with Bean File...");

        beanComboBox = new ComboBox<>();
        adapterNameField = new JBTextField();

        // 1. 填充 ComboBox 数据并定位默认选中项
        BeanItem defaultItem = null;
        if (beanFiles != null && !beanFiles.isEmpty()) {
            for (PsiClass psiClass : beanFiles) {
                BeanItem item = new BeanItem(psiClass);
                beanComboBox.addItem(item);

                // 优先查找类名中包含 "base" (忽略大小写) 的第一个 Bean
                if (defaultItem == null && psiClass.getName() != null
                        && psiClass.getName().toLowerCase().contains("base")) {
                    defaultItem = item;
                }
            }

            // 如果没找到包含 "base" 的类，默认选中集合中的第一个
            if (defaultItem == null) {
                defaultItem = beanComboBox.getItemAt(0);
            }

            beanComboBox.setSelectedItem(defaultItem);
            updateAdapterName(defaultItem);
        }

        // 2. 监听下拉框选择，自动联动更新 Adapter 类名
        beanComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                BeanItem selectedItem = (BeanItem) e.getItem();
                updateAdapterName(selectedItem);
            }
        });

        // 3. 设置 UI 尺寸并初始化对话框
        Dimension cbSize = beanComboBox.getPreferredSize();
        beanComboBox.setPreferredSize(new Dimension(JBUI.scale(550), cbSize.height));

        init();
    }

    /**
     * 自动生成默认的 Adapter 类名 (例如: ItemBaseBean -> ItemBaseAdapter)
     */
    private void updateAdapterName(BeanItem item) {
        if (item == null || item.getPsiClass() == null) return;
        String beanName = item.getPsiClass().getName();
        if (beanName == null) return;

        String adapterName;
        if (beanName.endsWith("Bean")) {
            adapterName = beanName.substring(0, beanName.length() - 4) + "Adapter";
        } else {
            adapterName = beanName + "Adapter";
        }
        adapterNameField.setText(adapterName);
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        // 使用 FormBuilder 构建标准 IntelliJ 风格表单
        JPanel panel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Select Bean:"), beanComboBox, 1, false)
                .addLabeledComponent(new JBLabel("Adapter Name:"), adapterNameField, 1, false)
                .getPanel();

        Dimension preferredSize = panel.getPreferredSize();
        panel.setPreferredSize(new Dimension(JBUI.scale(600), preferredSize.height));

        return panel;
    }

    // --- 对外提供获取表单结果的方法 ---

    /**
     * 获取选中的 PsiClass 对象
     */
    public PsiClass getSelectedBean() {
        BeanItem selected = (BeanItem) beanComboBox.getSelectedItem();
        return selected != null ? selected.getPsiClass() : null;
    }

    /**
     * 获取选中的 Bean 类名（例如: ItemBaseBean）
     */
    public String getSelectedBeanName() {
        PsiClass psiClass = getSelectedBean();
        return psiClass != null ? psiClass.getName() : "";
    }

    /**
     * 获取选中的 Bean 完整类名（例如: com.huimai365.compere.bean.ItemBaseBean）
     */
    public String getSelectedBeanQualifiedName() {
        PsiClass psiClass = getSelectedBean();
        return psiClass != null ? psiClass.getQualifiedName() : "";
    }

    /**
     * 获取用户输入的 Adapter 类名
     */
    public String getAdapterName() {
        return adapterNameField.getText().trim();
    }

    /**
     * 下拉框数据包装类，用于定义在 ComboBox 中的显示格式
     */
    public static class BeanItem {
        private final PsiClass psiClass;

        public BeanItem(PsiClass psiClass) {
            this.psiClass = psiClass;
        }

        public PsiClass getPsiClass() {
            return psiClass;
        }

        @Override
        public String toString() {
            if (psiClass == null) return "";
            String packageName = getPackageName(psiClass);
            // 格式例: ItemBaseBean (com.huimai365.compere.bean)
            return psiClass.getName() + (packageName.isEmpty() ? "" : " (" + packageName + ")");
        }

        private String getPackageName(PsiClass psiClass) {
            String qName = psiClass.getQualifiedName();
            if (qName != null && qName.contains(".")) {
                return qName.substring(0, qName.lastIndexOf('.'));
            }
            return "";
        }
    }
}