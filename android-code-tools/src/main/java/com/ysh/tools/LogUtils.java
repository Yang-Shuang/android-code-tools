package com.ysh.tools;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;

public class LogUtils {
    private static final String GROUP_ID = "AndroidCodeTools";
    private static final Logger mLogger = Logger.getInstance("android-code-tools");

    public static void log(String message) {
        if (message == null || message.isEmpty()) return;
        mLogger.info(message);
    }

    public static void toast(String message) {
        if (message == null || message.isEmpty()) return;
        notify(NotificationType.WARNING, message);
        log(message);
    }

    private static void notify(NotificationType type, String message) {
        try {
            NotificationGroupManager groupManager = NotificationGroupManager.getInstance();
            if (groupManager == null) {
                return;
            }

            NotificationGroup group = groupManager.getNotificationGroup(GROUP_ID);
            if (group == null) {
                return;
            }

            Notification notification = group.createNotification(GROUP_ID, message, type);

            Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
            Project targetProject = openProjects.length > 0 ? openProjects[0] : null;

            if (targetProject != null && !targetProject.isDisposed()) {
                notification.notify(targetProject);
            } else {
                Project defaultProject = ProjectManager.getInstance().getDefaultProject();
                if (defaultProject != null && !defaultProject.isDisposed()) {
                    notification.notify(defaultProject);
                }
            }
        } catch (Exception e) {
            mLogger.info(e.getMessage());
        }
    }
}
