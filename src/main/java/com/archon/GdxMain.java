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
        config.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode());
        config.setBackBufferConfig(8, 8, 8, 8, 16, 0, 4);
        config.useVsync(true);
        config.setForegroundFPS(60);
        config.setAudioConfig(24, 4096, 4);
        long seed = System.currentTimeMillis();
        boolean magicArena = true;
        for (String arg : args) {
            if ("classic".equalsIgnoreCase(arg) || "--classic".equalsIgnoreCase(arg)) {
                magicArena = false;
            } else {
                seed = Long.parseLong(arg);
            }
        }
        new Lwjgl3Application(new GdxView(seed, magicArena), config);
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
