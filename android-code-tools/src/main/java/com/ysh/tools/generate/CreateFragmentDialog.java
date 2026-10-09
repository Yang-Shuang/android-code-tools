package com.ysh.tools.generate;


import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ComboboxSpeedSearch;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.ysh.tools.NameUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.List;

public class CreateFragmentDialog extends DialogWrapper {

    private final ComboBox<String> layoutComboBox;
    private final JBTextField fragmentNameField;

    public CreateFragmentDialog(@Nullable Project project, List<String> layoutFiles) {
        super(project);

        // 1. 设置弹窗标题
        setTitle("Create an Fragment with Layout File...");

        // 2. 初始化控件
        layoutComboBox = new ComboBox<>(layoutFiles.toArray(new String[0]));
        fragmentNameField = new JBTextField();

        ComboboxSpeedSearch.installSpeedSearch(layoutComboBox, item -> item);

        Dimension cbSize = layoutComboBox.getPreferredSize();
        layoutComboBox.setPreferredSize(new Dimension(JBUI.scale(550), cbSize.height));

        // 3. 监听下拉框选择事件：选中 layout 后自动填充 Fragment 名称
        layoutComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                String selectedLayout = (String) e.getItem();
                updateFragmentName(selectedLayout);
            }
        });

        // 4. 默认触发一次第一个选项的名称转换
        if (!layoutFiles.isEmpty()) {
            updateFragmentName(layoutFiles.get(0));
        }

        // 5. 初始化对话框
        init();
    }

    private void updateFragmentName(String layoutFileName) {
        String defaultFragmentName = NameUtils.layoutToFragmentName(layoutFileName);
        fragmentNameField.setText(defaultFragmentName);
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        // 1. 使用 FormBuilder 构建表单
        JPanel panel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Select Layout:"), layoutComboBox, 1, false)
                .addLabeledComponent(new JBLabel("Fragment Name:"), fragmentNameField, 1, false)
                .getPanel();

        // 2. 设置固定的首选宽度（这里设为 480px，可根据偏好微调为 500 或 550）
        // 使用 JBUI.scale() 保证高分屏（Retina/4K）下的显示一致性
        Dimension preferredSize = panel.getPreferredSize();
        panel.setPreferredSize(new Dimension(JBUI.scale(600), preferredSize.height));

        return panel;
    }

    // --- 对外提供获取表单结果的方法 ---

    public String getSelectedLayoutName() {
        String selected = (String) layoutComboBox.getSelectedItem();
        return selected != null ? selected.replace(".xml", "") : "";
    }

    public String getFragmentName() {
        return fragmentNameField.getText().trim();
    }
}