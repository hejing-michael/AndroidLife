package com.camnter.gradle.plugin.method.trace.utils

/**
 * @author CaMnter
 */

class CommandUtils {

    static void command(String command) {
        PluginUtils.dispatchSystem {
            commandByOsX(command)
        } {
            commandByWindow(command)
        }
    }

    /**
     * osX | linux
     *
     * @param command
     */
    private static void commandByOsX(String command) {
        def process = ['bash', '-c', command].execute()
        printCommandInfo(process)
    }

    /**
     * windows
     *
     * @param command
     */
    private static void commandByWindow(String command) {
        def process = ("cmd /c start  /b ${command}").execute()
        printCommandInfo(process)
    }

    private static void printCommandInfo(Process process) {
        def output = new StringBuilder()
        def error = new StringBuilder()
        process.consumeProcessOutput(output, error)
        process.waitFor()
        if ('' != output.toString() && 0 != output.length()) {
            printf "%6s:  %s", ['output', output]
        }
        if ('' != error.toString() && 0 != error.length()) {
            printf "%6s:  %s", ['error', error]
        }
    }

    static void chmod(String path) {
        command("chmod 755 $path")
    }

    static void removeDirectory(String path) {
        command("rm -rf $path")
    }

    static void removeFile(String path) {
        command("rm $path")
    }
}