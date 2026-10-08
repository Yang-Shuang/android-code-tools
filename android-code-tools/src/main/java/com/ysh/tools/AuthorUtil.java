package com.ysh.tools;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;

public class AuthorUtil {

    /**
     * 获取作者名称：优先 Git -> 优先 SVN -> 系统用户名 -> unknown
     */
    public static String getAuthor(Project project) {
        // 1. 尝试获取 Git 用户名
        String gitUser = getGitUserName(project);
        if (StringUtil.isNotEmpty(gitUser)) {
            return gitUser;
        }

        // 2. 尝试获取 SVN 用户名
        String svnUser = getSvnUserName(project);
        if (StringUtil.isNotEmpty(svnUser)) {
            return svnUser;
        }

        // 3. 尝试获取操作系统登录用户名
        String sysUser = System.getProperty("user.name");
        if (StringUtil.isNotEmpty(sysUser)) {
            return sysUser.trim();
        }

        // 4. 兜底
        return "unknown";
    }

    /**
     * 获取 Git 用户名
     */
    private static String getGitUserName(Project project) {
        try {
            File workingDir = project.getBasePath() != null ? new File(project.getBasePath()) : null;
            Process process = Runtime.getRuntime().exec(new String[]{"git", "config", "user.name"}, null, workingDir);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.trim().isEmpty()) {
                    return line.trim();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 获取 SVN 用户名
     */
    private static String getSvnUserName(Project project) {
        // 方式 A：通过命令行执行 svn info 获取最后提交者用户名
        try {
            File workingDir = project.getBasePath() != null ? new File(project.getBasePath()) : null;
            Process process = Runtime.getRuntime().exec(new String[]{"svn", "info"}, null, workingDir);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // 兼容英文 "Last Changed Author: xxx" 与中文 "最后修改的作者: xxx"
                    if (line.contains("Author:") || line.contains("作者:")) {
                        String[] parts = line.split(":");
                        if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                            return parts[1].trim();
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // 方式 B：如果命令行不可用，尝试读取本地 SVN Auth 认证缓存文件
        return getSvnUserFromAuthCache();
    }

    /**
     * 从系统 SVN 缓存目录获取已记住的用户名
     */
    private static String getSvnUserFromAuthCache() {
        try {
            String userHome = System.getProperty("user.home");
            String osName = System.getProperty("os.name").toLowerCase();
            File authDir;

            if (osName.contains("win")) {
                String appData = System.getenv("APPDATA");
                authDir = new File(appData != null ? appData : userHome, "Subversion/auth/svn.simple");
            } else {
                authDir = new File(userHome, ".subversion/auth/svn.simple");
            }

            if (authDir.exists() && authDir.isDirectory()) {
                File[] files = authDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile()) {
                            String content = Files.readString(file.toPath());
                            // SVN Auth 文件的 Key-Value 储存格式例: K 8 \n username \n V 8 \n zhangsan
                            int idx = content.indexOf("username");
                            if (idx != -1) {
                                String sub = content.substring(idx);
                                String[] lines = sub.split("\n");
                                if (lines.length >= 3 && lines[1].trim().startsWith("V")) {
                                    return lines[2].trim();
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}