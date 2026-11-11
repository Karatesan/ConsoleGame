package com.archon;

import com.archon.ui.GdxView;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Windowed front end. Run with: mvn exec:exec@gdx [-Dexec.args=seed] */
public final class GdxMain {
    public static void main(String[] args) throws Exception {
        if (relaunchOnMac(args)) return;

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Archon");
        config.setWindowedMode(GdxView.WIN_W, GdxView.WIN_H);
        config.useVsync(true);
        config.setForegroundFPS(60);
        new Lwjgl3Application(new GdxView(args.length > 0 ? Long.parseLong(args[0]) : System.currentTimeMillis()), config);
    }

    /** macOS only lets GLFW run on the first thread, which needs a JVM flag; respawn with it once. */
    private static boolean relaunchOnMac(String[] args) throws Exception {
        if (!System.getProperty("os.name").toLowerCase().contains("mac")) return false;
        if ("1".equals(System.getenv("JAVA_STARTED_ON_FIRST_THREAD_" + ProcessHandle.current().pid()))) return false;
        if (System.getProperty("archon.relaunched") != null) return false;

        List<String> cmd = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-XstartOnFirstThread", "-Djava.awt.headless=true", "-Darchon.relaunched=1", "-cp",
                System.getProperty("java.class.path"), GdxMain.class.getName()));
        cmd.addAll(List.of(args));
        System.exit(new ProcessBuilder(cmd).inheritIO().start().waitFor());
        return true;
    }
}
