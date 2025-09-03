/*
 * The MIT License
 *
 * Copyright (c) 2025, AbsInt Angewandte Informatik GmbH
 * Author: Christian Huembert
 * Email: huembert@absint.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.absint.a3;

import hudson.FilePath;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.List;

public class A3ToolInstaller {

    // private TaskListener listener;

    private FilePath packagepath;
    private FilePath workspace;
    private String target;
    private OS nodeOS;

    public static enum OS {
        WINDOWS,
        UNIX,
        MACOS
    }

    /* These members are accessible through public getter */
    private FilePath selected_installer = null;
    private FilePath toolpath = null;
    private long build = -1;

    /**
     * Constructor (The complex one - searching the right tool package, unpack it and set the toolpath!)
     * @param ws FilePath to Jenkins workspace
     * @param packagepath_str java.lang.String path to the installerpackages
     * @param target java.lang.String Analysis target (e.g. ppc, tricore, arm, etc.)
     * @param nodeOS UNIX or WINDOWS or MACOS
     * @param listener TaskListener for Error/Warning Message output
     */
    public A3ToolInstaller(FilePath ws, String packagepath_str, String target, OS nodeOS, TaskListener listener) {

        // this.listener = listener;
        this.workspace = ws;
        this.nodeOS = nodeOS;
        this.target = target;

        String expected_os = "", expected_suffix = "";
        switch (nodeOS) {
            case UNIX:
                expected_os = "linux64";
                expected_suffix = ".zip";
                break;
            case WINDOWS:
                expected_os = "win64";
                expected_suffix = ".zip";
                break;
            case MACOS:
                expected_os = "macos64";
                expected_suffix = ".zip";
                break;
        }

        this.toolpath =
                null; // If something goes wrong determining the toolpath, the variable keeps its initial value = null.
        this.packagepath = new FilePath(this.workspace.getChannel(), packagepath_str);

        try {
            // Start with initial selection
            this.build = -1;
            this.selected_installer = null;

            List<FilePath> files = this.packagepath.list();
            /* Does not work as Filter is not serializable
            final String match_prefix =  "a3_" + target + "_" + expected_os + "_b";
            final String match_suffix =  "release."+ expected_suffix;
            List<FilePath> files = this.packagepath.list(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return (pathname.getName().startsWith(match_prefix) &&
                        //	pathname.getName().endsWith(match_suffix));
                }
            });
            */

            listener.getLogger()
                    .print("[A3 ToolInstaller Note:] Scanning for a³ " + target + " installation packages in "
                            + packagepath + " ...");

            for (FilePath fp : files) {
                if (fp.isDirectory()) continue; // if the file is a directory skip it
                // typical installer name: a3_arm_win64_b277911_release.exe
                String file_str = fp.getName();
                if (file_str.startsWith("a3_" + target + "_")) {
                    String[] strxs = file_str.split("_"); // leads to: strx = [a3, arm, win64, b277911, release.exe]
                    // consistence checks
                    if (strxs.length >= 5 && strxs[2].equals(expected_os) && strxs[4].endsWith(expected_suffix)) {
                        /* We found one good installer package candidate */
                        /* BUT: Always take the one with the highest build number in the file name! */
                        long current_build = Integer.parseInt(strxs[3].substring(1));
                        if (current_build > build) {
                            /* Yea, we found a better one! */
                            build = current_build;
                            this.selected_installer = fp;
                        }
                    }
                }
            }

            listener.getLogger().println("done");

            /* Now unpack the installer to workspace */
            if (this.selected_installer != null) {
                listener.getLogger()
                        .print("[A3 ToolInstaller Note:] Installer package '" + selected_installer.getName()
                                + "' has been selected and will be unpacked to JS workspace ...");

                String dest_bin = "";
                switch (nodeOS) {
                    case UNIX:
                        dest_bin = "a3_" + target + "_linux64_b" + build + "_release/bin/a3" + target;
                        break;
                    case WINDOWS:
                        dest_bin = "a3_" + target + "_win64_b" + build + "_release/bin/a3" + target + ".exe";
                        break;
                    case MACOS:
                        /* a3_arm_macos64_b11610530_release.app/Contents/MacOS */
                        dest_bin = "a3_" + target + "_macos64_b" + build + "_release.app/Contents/MacOS/a3" + target;
                        break;
                }

                // Prepare the target directory by cleaning it, if already present
                this.toolpath = new FilePath(this.workspace, dest_bin);

                // Unzip it now
                selected_installer.unzip(workspace); // Now all Win/Linux/Mac installers come with .zip files

                listener.getLogger().println("done");
                listener.getLogger().println("[A3 ToolInstaller Note:] Setting tool path to: " + toolpath);

            } else {
                listener.getLogger()
                        .println("[A3 ToolInstaller Info:] No a³ installer package for OS: " + expected_os
                                + " and Target: " + target + " found! Try to locate installed \"alauncher[.exe]\".");
            }

        } catch (IOException | InterruptedException e) {
            /* Do nothing, return value stays NULL */
        }
    }

    /**
     * Constructor (The easy one - we use the preinstalled alauncher, finding the correct analyzer tool automatically)
     * @param ws FilePath to Jenkins workspace
     * @param launcherpath java.lang.String path to the alauncher
     * @param nodeOS UNIX or WINDOWS, MACOS does not support centrally installed alauncher
     * @param listener TaskListener for Error/Warning Message output
     */
    public A3ToolInstaller(FilePath ws, String launcherpath, OS nodeOS, TaskListener listener) {

        // this.listener = listener;
        this.workspace = ws;
        this.nodeOS = nodeOS;
        this.target = null; // not used in that mode

        toolpath = new FilePath(
                this.workspace.getChannel(), launcherpath); // Will generate an object (!= null) in any case,
        // even if the launcherpath does not exist or equals("") =>
        // This case will be catched in the A3Builder:Run
        try {
            if (nodeOS == OS.MACOS) {
                listener.getLogger()
                        .println(
                                "[A3 ToolInstaller Error:] a³ installation mode not supported on MacOS. Use the portable archive mode instead. "
                                        + this.packagepath);
                throw new IOException();
            }
            String alauncherbin = "alauncher" + (nodeOS == OS.WINDOWS ? ".exe" : "");
            if (toolpath.isDirectory()) {
                // new standard mode: User has entered just the path to the alauncher binary
                toolpath = new FilePath(toolpath, alauncherbin); // add the "alauncher[.bin]" to the toolpath directory
            } else {
                // compatibility mode: User directly specified a binary but is it really the alauncher binary?
                // If the toolpath references a file, there must be a Parentdir, ignore the filename and take the one
                // for the current OS
                // to fix null ptr static check
                FilePath parent = toolpath.getParent();
                if (parent != null) {
                    toolpath = new FilePath(parent, alauncherbin);
                } else {
                    listener.getLogger()
                            .println("[A3 ToolInstaller Note:] Unexpected NULL Ptr parent of toolpath: " + toolpath);
                    throw new IOException();
                }
            }
            listener.getLogger().println("[A3 ToolInstaller Note:] Setting tool path to: " + toolpath);
        } catch (IOException | InterruptedException e) {
            // Do nothing, toolpath variable will have an instance in any case
        }
    }

    public FilePath getToolFilePath() {
        // TODO Auto-generated method stub
        return this.toolpath;
    }

    public long getBuildNr() {
        return this.build;
    }

    public String getTarget() {
        return this.target;
    }

    public OS getNodeOS() {
        return this.nodeOS;
    }
}
